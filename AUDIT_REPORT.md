# PermissionCore 全面审阅报告

审阅日期：2026-07-10 至 2026-07-11
基线提交：7a75b3d（fix: 收紧权限边界与清理遗留入口）
审阅目录：仓库根目录

## 1. 结论摘要

本轮已完成代码、SQL、前端、Docker、脚本和文档的全量审阅与修复。基线中的 P0/P1 阻断项均已处理：认证改为 access/refresh 严格分型与单次轮换，用户级变化通过持久化 `auth_version` 撤销个人 Token，全局 RBAC 变化通过单例 `global_auth_version` 以 O(1) 写入撤销全部旧 Token；ROLE_USER/ROLE_GUEST 默认不再获得后台管理查询权限；初始化事实源、数据库迁移、最小账户授权、Redis 边界、Swagger、头像代理、异常状态码和容器依赖顺序已经统一。

当前未发现仍会阻断发布的已知代码问题。保留的非阻断项是 Element Plus 生产 chunk 约 862 kB 的构建告警、本轮只验证了备份文件完整性而没有执行破坏性的“从备份覆盖恢复现有库”演练，以及登录失败限流尚未按网关或多实例部署架构实现。恢复演练应在隔离环境完成；登录限流应在公开部署前使用共享状态或网关能力实现，避免引入只能保护单节点的伪限流。

隔离压测已经建立可复现证据：固定资源下，10k 用户读混合场景确认的最高健康档为 200 RPS；100k 用户读混合仍受 `%term%` 前导通配符模糊查询限制，因此没有把旧的瞬时峰值或未通过健康门槛的数据写成容量结论。完整方法与限制见 [PERFORMANCE_REPORT.md](PERFORMANCE_REPORT.md)。

## 2. 严重度

| 等级 | 定义 |
|---|---|
| P0 | 可能造成越权、秘密泄露、数据破坏或发布即不安全，阻断发布 |
| P1 | 核心流程错误、部署不可复现或文档会直接导致失败，发布前必须修复 |
| P2 | 维护性、兼容性或局部体验问题，应在本轮处理 |
| P3 | 清理、命名和低风险改进 |

## 3. 审阅范围

- 后端 Maven 配置、Spring profile、认证与缓存相关配置。
- 前端 package.json、package-lock.json、Vite、Nginx 与 Dockerfile。
- Dockerfile、docker-compose.yml、.dockerignore。
- schema.sql、init-permissions.sql、migrations 与数据库修复入口。
- migrate-database.ps1、update-permissions.ps1、已删除的 update-permissions.bat、测试和辅助脚本。
- README.md、DOCKER_GUIDE.md、USER_GUIDE.md、README-codegen.md、权限更新指南.md。
- .gitignore、已追踪生成物、uploads、空占位文件和模板遗留。
- 代码生成器的模块边界、数据库来源和输出路径。
- `docker-compose.perf.yml`、`application-perf.yml`、k6 场景、确定性数据生成、监控采样与压测结果。

实现阶段实际执行了：现有库备份后升级、旧版/当前空库各两轮 migration 与 init 幂等验证、多次 Token 撤销验证、临时权限/用户回归、全新 Docker 数据卷初始化、10k/100k 隔离压测及测试容器/卷清理。没有删除现有业务数据卷，也没有重置现有管理员密码。

## 4. 基线验证

审阅开始时：

- Git 工作区干净，分支 master。
- 已追踪文件 186 个。
- target、permacore-ui/node_modules、permacore-ui/dist 为本地忽略生成物。
- docker compose config 可解析，但报告 version 字段已过时。
- Compose 解析结果把 MySQL/JWT 的固定回退值和空 Redis 密码带入运行配置。
- package-lock.json 中 Vite 7.2.7 明确要求 Node ^20.19.0 或 >=22.12.0。

| 检查 | 结果 |
|---|---|
| `mvn clean verify` | 通过，65 项测试，0 失败/错误/跳过 |
| Codegen `mvn clean package` | 通过 |
| 前端 `npm ci` / `npm run build` | 通过；仅有 Element Plus 大 chunk 告警 |
| `npm audit` / `npm audit --omit=dev` | 均为 0 vulnerabilities |
| `docker compose config -q` | 完整秘密时通过；缺少必填秘密时按预期拒绝 |
| Docker 镜像 | backend/frontend 从源码构建通过；backend 镜像构建包含 Maven 验证 |
| Docker 新空卷 | MySQL、Redis、backend healthy；frontend HTTP 200；db-access-init exited/0 |
| MySQL 8.0.46 | 现有库先备份再升级通过；旧版/当前空库 migration/init 各连续执行两遍通过；个人/全局授权版本、查询索引、6 个外键和 CHECK 正确 |
| 登录、刷新、退出 | 200；refresh 重放、旧 access、退出后 access/refresh 均为 401 |
| 普通用户权限边界 | 本人信息 200；用户/角色管理 403；默认权限数 0 |
| SSD/DSD、角色继承 | 单元测试与真实 SQL 守卫通过；现有 SSD 违规数 0 |
| Swagger | 默认 401；可信 dev 显式 opt-in 后 doc/OpenAPI 均为 200 |
| 头像上传与读取 | Docker 经 Nginx 上传 200、读取 200、`image/png` |
| Bootstrap | 首次创建成功；关闭后重启仍可用原密码登录，不自动重置 |
| 隔离压测 | 10k 读混合最高健康档 200 RPS；100k 前导通配符模糊查询限制已记录，未形成健康容量结论 |

## 5. 主要发现

以下行号按基线提交记录，后续修复可能移动。

### P0-1：权限更新会破坏自定义 API 类型

位置：src/main/resources/db/init-permissions.sql:201-203

基线脚本把除六个内置菜单和 admin:* 之外的全部权限改为 resource_type=2。用户创建的 resource_type=3 API 权限也会被改成按钮，脚本注释“修正旧库”实际越过了内置数据边界。

修复：

- 只更新明确列出的内置 perm_key。
- 为自定义 resource_type=3 增加回归测试。
- 更新前后比较自定义权限的类型、父节点、名称和状态。

### P0-2：默认角色会自动吸收未来业务查询权限

位置：src/main/resources/db/init-permissions.sql:216-238

ROLE_USER 使用 LIKE '%:query'，ROLE_AUDITOR 使用查询/日志通配条件。数据库中后来新增的敏感业务查询权限会在脚本重跑时自动授予默认角色，形成权限升级。

修复：

- ROLE_USER/ROLE_GUEST 改为无默认后台管理权限，并明确撤销旧脚本自动授予的六项内置查询权限；ROLE_AUDITOR 保留内置只读白名单。
- 新增“自定义 secret:query 不得授予 ROLE_USER/AUDITOR”的数据库测试。
- 对其他历史或自定义关联不盲删。更新前后执行下列检测，保存结果并由权限负责人逐条确认；只有确认属于误授权后才通过受审计流程撤销：

      SELECT r.role_key, p.id, p.perm_key, p.perm_name, p.resource_type, p.status
      FROM sys_role_permission rp
      JOIN sys_role r ON r.id = rp.role_id
      JOIN sys_permission p ON p.id = rp.permission_id
      WHERE r.del_flag = 0 AND p.status = 1 AND (
        r.role_key IN ('ROLE_USER', 'ROLE_GUEST')
        OR (r.role_key = 'ROLE_AUDITOR' AND p.perm_key NOT IN (
          'system:user:query', 'system:role:query', 'system:permission:query',
          'system:sod:query', 'system:dept:query', 'system:log', 'system:log:query'
        ))
      )
      ORDER BY r.role_key, p.perm_key;

### P0-3：启动修复器覆盖管理员配置

位置：

- src/main/java/com/permacore/iam/config/StartupDbFixer.java:32-56
- src/main/java/com/permacore/iam/config/StartupDbFixer.java:85-100
- src/main/java/com/permacore/iam/config/StartupDbFixer.java:148-162

每次启动都会检查并更新基线，强制内置角色/权限 status=1，同时改写名称、排序和父节点。管理员在 UI 中停用或调整内置记录后，重启会静默恢复。

修复：

- 删除无版本 StartupDbFixer。
- schema.sql → init-permissions.sql 是唯一数据事实源；update-permissions.ps1 仅作为执行包装。
- 后续基线变更以显式、可审阅、可回滚的 SQL 更新处理。

### P0-4：多套数据库初始化实现已漂移

位置：

- src/main/resources/db/schema.sql
- src/main/resources/db/init-permissions.sql
- StartupDbFixer.java
- DbInitController.java
- update-permissions.ps1
- update-permissions.bat
- AdminPasswordResetRunner.java

SQL、启动 Runner、管理接口、PowerShell 和 bat 都能改变基线。DbInitController 只保证少量权限，其 SQL 拆分和 IN 参数实现也存在失败风险；AdminPasswordResetRunner 若配置长期存在，会在每次启动重置 admin 密码。

修复：

- 只保留 canonical SQL 和 PowerShell 包装器。
- 删除数据库初始化/修复 HTTP 接口。
- 首次管理员只允许 APP_BOOTSTRAP_ENABLED=true + APP_BOOTSTRAP_ADMIN_PASSWORD，一旦创建成功即关闭；不能每次启动重置。

### P0-5：仓库和 Compose 存在可猜测秘密

位置：

- application.yml:13-15,58-62
- application-dev.yml:14-16,46-50
- application-docker.yml:14-16,57-61
- docker-compose.yml:9-10,49-53
- codegen/CodeGeneratorMain.java:17-19
- update-permissions.bat:27,36,60,64
- 前端登录与重置密码表单

基线包含固定数据库密码、JWT 密钥和管理员密码。Compose 无需 .env 也能带弱回退值启动。

目标：

- 本地必须提供 DB_PASSWORD、JWT_SECRET。
- 首次管理员必须提供 APP_BOOTSTRAP_ENABLED=true、APP_BOOTSTRAP_ADMIN_PASSWORD。
- Docker 运行必须提供 MYSQL_ROOT_PASSWORD、MYSQL_APP_PASSWORD、REDIS_PASSWORD、JWT_SECRET；应用固定使用非 root 的 permacore_app 账户。
- 仅当 APP_BOOTSTRAP_ENABLED=true 时必须提供 ADMIN_INITIAL_PASSWORD；bootstrap 默认关闭。
- 用户与管理员密码必须为 8-72 位；JWT_SECRET 至少 32 个随机字节。
- 缺失秘密时启动失败，不设默认回退。

### P0-6：Redis 无认证并发布到宿主机

位置：docker-compose.yml:27-40,49-53

Redis 空密码且映射 6379:6379。它包含权限缓存和 JWT 版本状态，暴露后可被读取、清空或篡改。

修复：

- Redis requirepass 与后端统一使用 REDIS_PASSWORD。
- healthcheck 认证后 PING。
- 默认不发布 6379；调试时只绑定 127.0.0.1。
- MySQL 同样优先只在 Compose 内网可见。

### P1-1：README 的空库步骤漏执行 schema.sql

位置：基线 README.md“数据库初始化”

只执行 init-permissions.sql 会在空数据库上报表不存在。USER_GUIDE 与 Docker 又描述为两个脚本，文档互相冲突。

已修文档：统一为 schema.sql 后 init-permissions.sql；已有库使用 update-permissions.ps1。

### P1-2：清 Redis 不能可靠刷新权限

位置：

- 基线 权限更新指南.md:108-117
- update-permissions.ps1:54-57

旧方案依赖各节点 Caffeine 失效与人工重启，无法持久证明外部 SQL 更新后旧 token 已撤销；FLUSHDB 还可能删除共享 Redis 的其他数据。

修复：

- 用户状态、密码和用户角色变化在同一事务中递增相应用户的 `auth_version`；角色、权限、继承、SoD 与 `init-permissions.sql` 等全局 RBAC 变化递增单例 `global_auth_version`，避免全表更新用户。
- JWT 同时携带个人与全局版本；每次受保护请求读取当前用户状态并联查全局单例，任一版本不匹配都会拒绝旧 access/refresh token，不依赖缓存广播或重启。
- Redis 旧键无法绕过数据库门禁，无需清理；禁止把 FLUSHDB/FLUSHALL 写入权限更新步骤。

### P1-3：Docker 头像读取与上传大小不一致

位置：

- permacore-ui/nginx.conf:1-39
- AuthController.java:239-276

后端返回 /uploads/avatars/...，Nginx 没有 /uploads/ 代理，路径会落入 SPA fallback。后端允许 2 MB，Nginx 默认 client_max_body_size 为 1 MB，1 至 2 MB 文件会先被 Nginx 拒绝。

修复：

- Nginx 增加 /uploads/ 到 backend 的代理。
- 显式配置 client_max_body_size，与后端限制一致。
- 加入 Docker 头像上传/读取联调。

### P1-4：Docker profile 实际没有关闭 SQL 输出

位置：

- application.yml:45-50,64-69
- application-docker.yml:44-67

Spring profile 按扁平属性叠加。docker profile 未覆盖 base 的 StdOutImpl 和更具体的 mapper DEBUG，因此“Docker 关闭 SQL 日志”的注释与实际配置冲突，可能把查询参数写入日志。

修复：

- default/docker 显式使用 NoLogging；dev 默认同样不输出 SQL 参数和结果，避免密码哈希与个人字段进入终端。
- 仅允许开发者通过 `MYBATIS_LOG_IMPL` 在本机临时 opt-in，排查后立即恢复安全默认值。

### P1-5：Swagger 在部署配置中匿名公开

位置：

- SecurityConfig.java:124-137
- application-docker.yml:69-76
- permacore-ui/nginx.conf:24-33

基线同时 permitAll 并通过 Nginx 公开 API 文档，暴露接口结构。

目标：

- Swagger 默认需要认证。
- 仅 dev 显式 app.security.public-docs=true 时匿名开放。
- Docker/生产不得默认开启公开文档。

### P1-6：代码生成器基于伪造 H2 表生成错误模型

位置：

- codegen/CodeGeneratorMain.java:17-53
- 基线 README-codegen.md

useH2 分支给全部业务表创建相同的通用列，再生成实体和 Mapper，输出与 schema.sql 不一致；输出位置依赖 user.dir，还可能写进主源码。根项目另有空占位类和失效 exec 配置。

目标：

- 删除 H2 依赖和伪表分支。
- 只连接执行过 canonical SQL 的真实隔离 MySQL。
- 使用必填的 CODEGEN_DB_URL、CODEGEN_DB_USERNAME、CODEGEN_DB_PASSWORD，以及可选的 CODEGEN_OUTPUT_DIR。
- 未指定输出目录时使用 target/codegen-preview，人工审阅后合并。
- 删除根占位类和指向它的 exec 配置。

### P1-7：.env 和 uploads 没有正确隔离

位置：

- .gitignore:1-40
- .dockerignore:1-27
- uploads/avatars/*

.gitignore 不包含 .env、数据库备份、日志和 uploads。仓库已追踪两个内容完全相同的头像文件；后端 Docker 构建上下文也未排除运行时 uploads 和 .env。

修复：

- 增加 .env、.env.*、uploads/*、backups/、*.bak、*.dump、*.sql.gz、*backup*.sql、*-before-*.sql 和日志规则，保留 !.env.example；不使用会误伤 canonical SQL 的全局 `*.sql` 规则。
- 从 Git 索引移除已上传头像，但迁移时先确认当前数据库引用，不能直接破坏本地运行状态。
- .dockerignore 排除秘密、上传和备份。

### P1-8：脚本泄露或固化敏感信息

位置：

- test_login_and_list.ps1:5-12
- update-permissions.bat:27-64
- update-permissions.ps1:31-42

测试脚本打印完整 accessToken；bat 固定 root 密码；PowerShell 将明文密码放入原生命令参数并吞掉 MySQL 错误输出。

修复：

- 测试参数化 URL/用户/密码，令牌只输出前后少量字符或完全不输出，失败返回非零退出码。
- 删除 bat。
- PowerShell 仅执行 canonical init，直接把原始 UTF-8 字节写入 mysql 标准输入，兼容 Windows PowerShell 5.1 和可选 BOM；密码通过进程环境传递，保留错误详情并在 finally 清理引用。

### P2-1：Node 版本和安装命令过时

位置：

- package-lock.json:1980-2000
- 基线 README.md“环境准备”

文档说 Node 18 可用，但 Vite 7 要求 ^20.19.0 或 >=22.12.0；文档推荐 npm install/pnpm install，仓库实际只维护 package-lock.json。

已修文档：统一 Node 要求并使用 npm ci。

### P2-2：Docker 不可复现且不利于并行环境

位置：

- Dockerfile:6,19
- permacore-ui/Dockerfile:6,19
- docker-compose.yml:6-7,29-30,47,69

基础镜像使用可漂移标签，Compose 固定 container_name，version:3.8 已被当前 Compose 标记过时。并行项目和 CI 容易发生容器名冲突。

修复：

- 移除 version 和 container_name。
- 运维命令用 docker compose exec 服务名。
- 固定 MySQL、Redis、Maven、Temurin/Alpine、Node/Alpine 与 Nginx 的版本化标签；稳定发布验证后可进一步锁多架构 manifest digest。

### P2-3：数据库缺少版本化升级和关系完整性

位置：src/main/resources/db/schema.sql:82-167

关联表没有外键；schema.sql 使用 CREATE TABLE IF NOT EXISTS，无法为已有表补列或索引。Docker FAQ 原方案通过删除数据卷重建，存在数据丢失风险。

修复：

- schema.sql 已为新库包含 `sys_user.auth_version`、`sys_authorization_state.global_auth_version`、关系外键、禁止自继承检查和经压测确认的查询索引；已有库依次运行幂等的 `20260710_add_auth_version.sql` 与 `20260710_optimize_user_queries.sql` 对齐同一契约。孤儿关系会阻断第一份迁移，禁止靠删除数据卷碰运气。
- `migrate-database.ps1` 在一次确认和一次密码生命周期中固定执行上述两份结构迁移，逐项失败即停；`update-permissions.ps1` 仍只执行 init 权限，避免结构与权限数据变更相互掩盖。
- 两个 PowerShell 入口均确认目标、通过进程环境传递密码，并以原始 UTF-8 字节调用 mysql，兼容 Windows PowerShell 5.1 与可选 BOM。
- 后续引入 Flyway/Liquibase 时只能保留一个迁移事实源。
- 为用户角色、角色权限、继承等关系补外键或明确应用层完整性策略与清理测试。

### P2-4：文档和 UI 字段漂移

基线问题包括：

- 昵称在 UI/后端必填，旧手册写可选。
- 旧手册写密码 6 至 20 位，当前后端与前端统一为 8-72 位。
- 角色编码示例不带 ROLE_，与内置角色不一致。
- 部门手册写“负责人”却漏“邮箱”，当前 UI 相反。
- 用户手册缺 SoD 管理章节。
- README 漏 ROLE_FINANCE、SodManage，并把已有 Docker 写成未来扩展。
- “Springdoc / Knife4j”与 pom.xml 只有 Springdoc 不一致。
- 用户手册包含无效邮箱、电话和不存在的内置工单系统。

已修文档：README.md 与 USER_GUIDE.md 已按当前功能和目标安全基线重写。

### P3-1：生成物和模板遗留

文件级问题：

- META-INF/MANIFEST.MF 是 Maven 生成物，仍写 Build-Jdk-Spec: 17，与 Java 21 基线冲突。
- src/main/java/com/permacore/iam/mapper/xml 是 0 字节占位文件。
- permacore-ui/public/vite.svg、src/assets/vue.svg、HelloWorld.vue、默认 Vite README/样式属于模板遗留。
- tools/convert_md_to_docx.py 固定读取不存在的 ProjectReport.md，且没有依赖说明。
- 根 CodeGeneratorMain 是空占位类。

修复：删除无引用生成物/模板；convert_md_to_docx.py 改为显式输入/输出参数，说明 python-docx 依赖，默认拒绝覆盖现有 DOCX，并对缺文件/坏输出路径返回非零。

### 实现阶段追加发现与修复

- JWT：删除重复认证过滤器和 Token 日志；引入强密钥、access/refresh 类型、sessionId、单次 CAS 刷新与个人/全局授权双版本门禁。Redis 故障返回 503，不能伪装成凭据 401；前端遇到 503 不再清空会话。
- 授权一致性：用户状态、密码和用户角色分配递增个人 `auth_version`；角色权限、继承、SoD、角色/权限状态和权限初始化递增单例 `global_auth_version`。旧 Redis 键无法绕过任一数据库门禁，全局撤销不再更新全部用户行。
- RBAC3：角色继承使用可收敛的递归闭包；Token 构建阶段对继承后的 SSD fail closed；DSD 保留显式空激活角色；角色图读写增加共享/排他锁，避免签发 Token 时读取半完成配置。
- 最小权限：ROLE_USER/ROLE_GUEST 不再默认获得后台管理查询权限，ROLE_AUDITOR 使用固定只读白名单；临时自定义 API 回归证明 resource_type、父节点、资源 ID、排序和状态未被 init 改写，且只自动授予 ROLE_ADMIN。
- 写入安全：用户、角色、部门和权限改为白名单 DTO 与稀疏更新，防止旧对象覆盖新状态；部门启停校验祖先链，删除/角色继承/SoD 引用均在事务内验证。
- HTTP 契约：业务错误使用真实 400/401/403/404/500/503；畸形 JSON、绑定错误、缺失参数和 SSD 角色冲突统一返回 400；角色/权限分配增加非空元素和单次集合上限。
- 文件与日志：头像只接受可解码并重新编码的 PNG/JPEG，限制 2 MB 与尺寸，文件名由服务端生成；操作日志递归脱敏 password/token/secret；dev 默认也不输出 MyBatis SQL 参数和结果。
- 前端：修复并发 401 刷新、refresh 递归、Token/Pinia 不同步、退出跳转、用户头像串号、SoD 分页、树节点自包含和失败确认框静默吞错；删除 Vite 模板遗留并补真实浏览器登录/菜单渲染验证。
- Docker：修复 MySQL 健康探针引用不存在字段、backend 启动窗口 502、frontend 依赖时序；新增无敏感信息且检查 DB/Redis 的 `/api/health`，并支持保持锁定默认值的镜像仓库覆盖参数。
- 查询与压测：用户深分页先用覆盖索引取 ID 再回表，用户/日志列表使用稳定双列排序并避免读取敏感或大字段；增加仅 `perf` profile 启用的 Prometheus 指标、固定资源隔离栈和 k6 复现脚本，默认业务 API 与健康检查契约不变。
- 启动噪音：排除不使用的默认 UserDetails 服务，移除随机 Spring Security 开发密码和误导性的固定 Swagger 启动地址。

## 6. 推荐的单一初始化方案

本轮目标方案：

1. schema.sql 是唯一结构基线。
2. init-permissions.sql 是唯一内置 RBAC 数据基线。
3. 空库严格按 schema → init 顺序。
4. 已有库严格按备份 → `20260710_add_auth_version.sql` → `20260710_optimize_user_queries.sql` → init 权限 → 启动新版顺序。
5. migrate-database.ps1 只负责按固定顺序安全调用两份 migration；update-permissions.ps1 只负责安全调用 init，两者都不复制 SQL。
6. Docker 首次空卷只挂载两份 canonical SQL；db-access-init 一次性服务独立重建 permacore_app，清除历史角色/多余 grant 后只授予四项 DML 权限，兼容已有数据卷且不复制业务数据。
7. 删除 StartupDbFixer、DbInitController 的初始化能力和固定密码重置入口。
8. init-permissions.sql 不创建 admin 或绑定用户角色；首次 admin 及 ROLE_ADMIN 绑定由一次性 bootstrap 完成，密码来自环境且必须为 8-72 位，成功后关闭。
9. 后续 schema 变化继续使用显式版本化升级脚本；若引入 Flyway，迁移文件随即成为唯一事实源，Docker entrypoint 与手工迁移不能并存。

对已有数据库：

1. 先备份并记录 schema 版本、Git 提交。
2. 停止写入流量。
3. 依次执行幂等的 `20260710_add_auth_version.sql`（个人/全局授权版本、关系约束）和 `20260710_optimize_user_queries.sql`（用户、日志和反向关联索引）。
4. 保存 ROLE_USER/ROLE_GUEST 管理授权与 ROLE_AUDITOR 非基线授权的检测结果，再执行 init-permissions.sql；脚本仅撤销六项已知旧基线，其他关联前后人工比较。
5. 启动全部新版后端节点，重新登录并验证旧 JWT 已撤销及权限边界。
6. 失败时恢复备份，不删除数据卷碰运气。Docker 已有 volume 不会重跑 initdb，必须通过 docker compose cp 加容器内 mysql 重定向保留 SQL 原始字节。

## 7. Docker 目标配置

### .env.example

模板只保留变量名和说明，不包含可运行示例秘密：

    MYSQL_ROOT_PASSWORD=
    MYSQL_APP_PASSWORD=
    REDIS_PASSWORD=
    JWT_SECRET=
    APP_BOOTSTRAP_ENABLED=false
    ADMIN_INITIAL_PASSWORD=

.gitignore：

    .env
    .env.*
    !.env.example

四项运行秘密使用必填表达式，缺失时 docker compose config 就失败。ADMIN_INITIAL_PASSWORD 是条件必填：开关为 false 时应为空；开关为 true 但密码为空或不在 8-72 位范围时，应用拒绝启动。

### Redis 与端口

- Redis 启用 requirepass，后端与 healthcheck 使用同一 REDIS_PASSWORD。
- MySQL 应用连接使用 permacore_app 与独立密码；db-access-init 在 backend 前重建该账户，清除历史角色/多余 grant 并只授予 SELECT、INSERT、UPDATE、DELETE，不把 root 凭据交给后端。
- Redis/MySQL 默认不发布宿主端口。
- 临时调试只绑定 127.0.0.1。
- 只对外发布前端；后端端口是否发布由部署模式决定。
- Nginx 代理 /api、/uploads 和受控文档路径。

### 可复现性

- 移除 Compose version 和固定 container_name。
- 固定基础镜像版本。
- MySQL healthcheck 验证 `sys_user.auth_version`、`sys_authorization_state` 单例行与启用的 ROLE_ADMIN/admin:* 基线；它不是完整迁移校验，已有卷仍必须按固定顺序执行两份 migration。Redis 使用带认证 PING；backend 的 `/api/health` 检查 DB 与 Redis 且不暴露详情；frontend 等待 backend healthy 后启动并检查本机静态首页。这些默认健康检查和业务接口契约未因压测 profile 改变。
- Docker 构建执行测试或在构建前由 CI 强制通过测试。
- PowerShell 下的数据库备份、恢复和权限更新不使用文本管道：由容器内工具直接写文件并通过 docker compose cp 复制原始字节。

## 8. 文档重写状态

代码、配置与实测结果已同步回填：

- README.md：安全基线、真实初始化顺序、Node 版本、profile、Swagger、项目结构和验证。
- DOCKER_GUIDE.md：必填 .env、Redis 认证/端口、备份恢复、数据卷危险操作、uploads 代理。
- USER_GUIDE.md：真实字段、8-72 位密码、角色命名、SoD、激活角色、日志边界和无虚假支持信息。
- README-codegen.md：真实 MySQL、三个必填连接变量、可选输出目录、预览输出和模块边界。
- 权限更新指南.md：单一事实源、Token 撤销、Docker 现有卷、字节安全执行，以及普通用户/访客管理授权与审计员非基线授权的前后人工审阅。
- performance/README.md 与 PERFORMANCE_REPORT.md：隔离压测复现、受控资源、结果证据、100k 模糊查询限制及可安全引用口径。
- AUDIT_REPORT.md：审阅证据、严重度、修复结果、实测数字和剩余非阻断项。

## 9. 发布前验收清单

- [x] 仓库搜索不到固定 DB/JWT/admin/Redis 秘密。
- [x] 缺少必填秘密时本地与 Docker 均拒绝启动。
- [x] 首次 bootstrap 成功后关闭，重启不会重置 admin 密码。
- [x] init-permissions.sql 不修改自定义 API 权限。
- [x] ROLE_USER/ROLE_GUEST 无默认管理权限，ROLE_AUDITOR 不吸收未来自定义查询权限。
- [x] UI 停用内置角色/权限后，应用重启不会被启动修复器恢复。
- [x] Swagger 默认不能匿名访问，dev opt-in 可访问。
- [x] Redis 需要认证且默认不发布 6379。
- [x] Docker 头像上传和 /uploads 读取成功。
- [x] Docker 与默认 dev 配置不输出 MyBatis SQL DEBUG。
- [x] mvn clean verify 通过（65 项测试，0 失败/错误/跳过）。
- [x] npm ci 与 npm run build 通过。
- [x] docker compose config -q 静默通过；未记录或分享展开真实秘密的完整 config 输出。
- [x] 新空卷和已有卷升级各完成一次演练。
- [x] 已有库按备份 → 两份固定顺序 migration → init 权限 → 新版启动顺序完成，个人/全局授权版本与查询索引验证通过且旧 JWT 失效。
- [ ] 数据库与 uploads 完成备份/恢复演练。
- [ ] 在网关或共享存储层配置登录失败限流与监控告警。
- [x] Git 不再追踪运行时头像、生成 manifest、空占位和模板遗留。
