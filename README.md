# PermaCore IAM 权限管理系统

PermaCore IAM 是一个基于 Spring Boot 与 Vue 3 的 RBAC3 权限管理项目，覆盖用户、角色、权限、部门、角色继承、职责分离、会话角色切换和审计日志。项目适合作为课程设计、权限中台原型或 RBAC3 教学示例；上线前仍应按本文的安全边界完成密钥、网络、备份和监控配置。

## 1. 已实现能力

- RBAC0：用户、角色、权限及其关联关系。
- RBAC1：角色可继承多个父角色，服务端校验继承环。
- RBAC2：支持 SSD 静态职责分离和 DSD 动态职责分离。
- 会话角色：用户只激活本次会话需要的角色，切换时重新校验 DSD。
- JWT 认证：访问令牌、刷新令牌、单次原子轮换，以及个人 `auth_version` + 全局 `global_auth_version` 双版本主动失效。
- 管理功能：用户、角色、权限、部门、SoD、登录日志和操作日志。
- 缓存：Caffeine 本地缓存；default/docker profile 可接入 Redis，dev profile 默认不依赖 Redis。
- 前后端分离：REST API、Vue 3 管理界面和 Springdoc OpenAPI。

项目中的“企业级”表示功能设计目标，不代表默认配置可直接用于公网生产环境。

## 2. 安全基线

项目不提供默认数据库密码、JWT 密钥或管理员密码。启动前必须由部署者提供：

| 场景 | 必需配置 |
|---|---|
| 本地后端 | DB_PASSWORD、JWT_SECRET |
| 首次创建管理员 | APP_BOOTSTRAP_ENABLED=true、APP_BOOTSTRAP_ADMIN_PASSWORD |
| Docker 运行 | MYSQL_ROOT_PASSWORD、MYSQL_APP_PASSWORD、REDIS_PASSWORD、JWT_SECRET |
| Docker 首次创建管理员 | APP_BOOTSTRAP_ENABLED=true、ADMIN_INITIAL_PASSWORD |

要求：

- 用户密码和首次管理员密码必须为 8-72 位；生产环境应使用密码管理器生成的长随机口令。
- Docker 的 MYSQL_APP_PASSWORD 必须为 16-128 位；后端只使用受限的 permacore_app 账户，不持有 MySQL root 凭据。
- JWT_SECRET 至少使用 32 个随机字节，不能使用示例文本、项目名或可猜测字符串。
- 首次管理员创建成功后，应关闭 bootstrap，并从运行环境中移除首次密码。
- 不要提交 .env、数据库备份、日志、令牌或 uploads 中的运行时文件。
- Swagger 默认需要认证；仅可信 dev 环境可以临时设置 app.security.public-docs=true。

## 3. 技术栈与环境要求

### 后端

- JDK 21
- Maven 3.8 或更高版本
- Spring Boot 3.2
- Spring Security 6
- MyBatis-Plus / MyBatis
- MySQL 8
- Caffeine
- Redis 7（dev profile 默认不启用）
- Springdoc OpenAPI

### 前端

- Node.js ^20.19.0 或 >=22.12.0
- npm（仓库以 package-lock.json 为依赖锁文件）
- Vue 3、TypeScript、Vite 7、Pinia、Element Plus、Axios

Node 18 和早期 Node 20 不满足当前 Vite 版本的运行要求。

## 4. RBAC3 模型

### 4.1 角色继承

sys_role_inheritance 保存直接继承边，ancestor_id 是父角色，descendant_id 是继承该角色的子角色。例如：

    ROLE_USER
      ├─ ROLE_MANAGER
      └─ ROLE_DEVELOPER

ROLE_MANAGER 和 ROLE_DEVELOPER 可获得 ROLE_USER 的有效权限。管理接口为：

- GET /api/role-inheritance/parents/{roleId}
- GET /api/role-inheritance/children/{roleId}
- PUT /api/role-inheritance/{roleId}

### 4.2 职责分离

- SSD：分配角色时拒绝互斥角色组合。
- DSD：用户可以拥有相关角色，但同一会话不能同时激活互斥角色。

默认基线包含演示约束；实际业务必须根据组织制度重新审阅，不能把演示数据直接视为生产策略。

## 5. 数据库初始化

数据库只有一套受支持的初始化顺序：

1. 执行 src/main/resources/db/schema.sql。
2. 执行 src/main/resources/db/init-permissions.sql。

在项目根目录启动 MySQL 客户端后，可依次执行：

    mysql --host=localhost --port=3306 --user=root --password

进入 MySQL 客户端：

    SOURCE src/main/resources/db/schema.sql;
    SOURCE src/main/resources/db/init-permissions.sql;

schema.sql 负责数据库和表结构，init-permissions.sql 负责内置部门、角色、权限、角色继承和 SoD 约束。管理员账号创建及 ROLE_ADMIN 绑定由显式的一次性 bootstrap 完成；初始化 SQL 不写管理员密码或用户绑定。不能只在空数据库上执行第二个脚本。

当前 schema.sql 已包含 `sys_user.auth_version` 和单例表 `sys_authorization_state.global_auth_version`。升级已有数据库时，必须在启动新版后端前按以下顺序处理：

1. 备份数据库并停止写入。
2. 执行幂等结构迁移。
3. 执行内置权限更新。
4. 启动新版后端，重新登录并验证旧 JWT 已失效。

PowerShell 入口将结构迁移与权限数据初始化分开：

    .\migrate-database.ps1
    .\update-permissions.ps1

两个入口都会显示高风险目标确认并交互式读取数据库密码；它们以原始 UTF-8 字节调用 mysql，兼容 Windows PowerShell 5.1 和带 BOM 的 SQL。`migrate-database.ps1` 在同一次确认和密码生命周期中固定先执行 `20260710_add_auth_version.sql`，再执行 `20260710_optimize_user_queries.sql`，任一步失败即停止；随后才可执行 `update-permissions.ps1`。Docker 已有数据卷不会重跑 initdb，必须按 DOCKER_GUIDE.md 的容器内字节安全步骤显式执行相同的两份 migration，再执行 `init-permissions.sql`。不要复制旧文档中的手写 INSERT，不要调用内部数据库修复接口，也不要用 `FLUSHDB` 代替数据库授权版本门禁。

## 6. 本地启动

### 6.1 后端

dev profile 默认不输出 MyBatis SQL 参数与结果，避免密码哈希和个人字段进入终端日志。确需本机临时排查时，可显式设置 `MYBATIS_LOG_IMPL=org.apache.ibatis.logging.stdout.StdOutImpl`；完成后立即恢复默认，并且不要分享包含业务数据的日志。

先在当前 PowerShell 会话中提供必要配置。以下值均由部署者自行生成：

    $env:DB_PASSWORD = "<本机 MySQL 密码>"
    $env:JWT_SECRET = "<至少 32 个随机字节>"

首次创建管理员时再设置：

    $env:APP_BOOTSTRAP_ENABLED = "true"
    $env:APP_BOOTSTRAP_ADMIN_PASSWORD = "<8-72 位的首次管理员密码>"

构建并验证：

    mvn clean verify

启动 dev profile：

    java -jar target/permacore-iam-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

首次管理员创建成功后停止应用，关闭 bootstrap 并移除首次密码：

    $env:APP_BOOTSTRAP_ENABLED = "false"
    Remove-Item Env:APP_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue

后续启动仍需 DB_PASSWORD 与 JWT_SECRET。

若只在可信的本机开发环境查看 Swagger，可临时启动：

    java -jar target/permacore-iam-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev --app.security.public-docs=true

不要在 Docker 或公网环境开启公开文档。

### 6.2 前端

在另一个终端执行：

    cd permacore-ui
    npm ci
    npm run dev

开发服务器默认为 http://localhost:5173，并将 /api 代理到 http://localhost:54321。需要临时改变代理目标时设置 VITE_API_PROXY_TARGET。

## 7. 访问地址

| 模式 | 前端 | 后端 |
|---|---|---|
| 本地开发 | http://localhost:5173 | http://localhost:54321 |
| Docker | http://localhost | http://localhost:54321 |

Swagger 地址为 /doc.html，但默认受认证保护。开发环境显式开启 public-docs 后，地址为 http://localhost:54321/doc.html。

首次管理员用户名为 admin，密码是部署者通过 APP_BOOTSTRAP_ADMIN_PASSWORD 或 Docker 的 ADMIN_INITIAL_PASSWORD 提供的值；仓库中没有默认管理员密码。

## 8. Docker

Docker 部署必须先从 .env.example 创建本地 .env，填写四项运行秘密。首次需要创建 admin 时，再把 APP_BOOTSTRAP_ENABLED 改为 true 并填写 8-72 位的 ADMIN_INITIAL_PASSWORD：

    Copy-Item .env.example .env
    docker compose config -q
    docker compose up -d --build

完整步骤、Redis 网络边界、备份、恢复和数据卷注意事项见 DOCKER_GUIDE.md。

## 9. 权限标识约定

当前内置权限遵循以下约定：

| 类型 | 示例 |
|---|---|
| 菜单 | system:user |
| 查询操作 | system:user:query |
| 写操作 | user:add、user:edit、user:delete |
| 超级权限 | admin:* |

新增权限时不要混用 user:view、user:create 等旧演示命名。API 类型权限的 resource_type 为 3，权限更新脚本不得把自定义 API 权限改成按钮类型。

## 10. 项目结构

    PermissionCore/
    ├─ pom.xml
    ├─ Dockerfile
    ├─ docker-compose.yml
    ├─ migrate-database.ps1       # 已有数据库结构迁移入口
    ├─ update-permissions.ps1     # 内置权限更新入口
    ├─ README.md
    ├─ DOCKER_GUIDE.md
    ├─ USER_GUIDE.md
    ├─ 权限更新指南.md
    ├─ codegen/                    # 独立代码生成工具
    ├─ src/
    │  ├─ main/java/com/permacore/iam/
    │  ├─ main/resources/
    │  │  ├─ application.yml
    │  │  ├─ application-dev.yml
    │  │  ├─ application-docker.yml
    │  │  ├─ application-perf.yml
    │  │  ├─ db/schema.sql
    │  │  ├─ db/init-permissions.sql
    │  │  ├─ db/migrations/20260710_add_auth_version.sql
    │  │  ├─ db/migrations/20260710_optimize_user_queries.sql
    │  │  └─ mapper/
    │  └─ test/
    ├─ docker-compose.perf.yml      # 隔离压测栈
    ├─ performance/                 # k6 场景、数据生成、监控与编排
    ├─ PERFORMANCE_REPORT.md        # 经审阅的压测结论
    ├─ permacore-ui/
    │  ├─ package.json
    │  ├─ package-lock.json
    │  ├─ nginx.conf
    │  └─ src/
    └─ uploads/                    # 运行时数据，不应提交实际上传文件

前端主要页面包括 Dashboard、UserManage、RoleManage、PermissionManage、SodManage、DeptManage、LoginLog 和 OperLog。

## 11. 常用验证

后端：

    mvn clean verify

前端：

    cd permacore-ui
    npm ci
    npm run build

Docker 配置：

    docker compose config -q

最小联调应覆盖：前端访问、后端健康、首次管理员登录、刷新令牌、会话角色切换、SSD/DSD 拒绝路径、用户/角色/权限 CRUD、头像上传与读取、审计日志及重启后的数据持久化。

## 12. 可复现性能测试

压测使用独立的 `docker-compose.perf.yml`、项目名和数据卷，不连接宿主机常规 MySQL，也不复用默认 Compose 卷。`perf` profile 仅为隔离压测启用 Prometheus 指标：业务 API 与 `/api/health` 契约不变；后端绑定 `127.0.0.1:15432`，指标绑定 `127.0.0.1:15433/actuator/prometheus`。

固定资源边界为：后端 4 vCPU/2 GiB（JVM 1 GiB）、MySQL 2 vCPU/2 GiB（Buffer Pool 1 GiB）、Redis 1 vCPU/512 MiB。准备好五项 `PERF_*` 运行秘密后，可运行：

    .\performance\Invoke-PerformanceTest.ps1 -Action Smoke -Scale 10k
    .\performance\Invoke-PerformanceTest.ps1 -Action Baseline -Scale 10k -Rates 50,100,200,400,800 -Repeats 3 -WarmupSeconds 30 -SampleSeconds 120

在上述受控资源、数据和健康判定下，10k 用户读混合场景确认的最高健康档为 200 RPS；它是本机受控容量边界，不是生产 SLA。100k 用户读混合仍受前导通配符 `%term%` 模糊查询限制，尚不能给出可用于简历或容量规划的健康 RPS。方法、证据和限制见 [PERFORMANCE_REPORT.md](PERFORMANCE_REPORT.md)，复现参数见 [performance/README.md](performance/README.md)。

## 13. 文档

- USER_GUIDE.md：面向使用者的操作说明。
- DOCKER_GUIDE.md：Docker 启动、网络、安全、备份和恢复。
- 权限更新指南.md：数据库基线与权限更新故障处理。
- README-codegen.md：真实 MySQL 上的代码生成流程。
- AUDIT_REPORT.md：本轮全面审阅发现、修复状态与待验证项。
- PERFORMANCE_REPORT.md：隔离压测环境、结果、瓶颈和可安全引用的性能口径。
- performance/README.md：压测数据、场景、复现命令和结果产物约定。
- tools/convert_md_to_docx.py：可选 Markdown→DOCX 工具；运行 `python -m pip install python-docx` 后，以 `python tools/convert_md_to_docx.py INPUT.md -o OUTPUT.docx` 调用，已有输出默认不会被覆盖。
