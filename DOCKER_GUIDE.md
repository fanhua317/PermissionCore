# PermaCore IAM Docker 部署指南

本指南适用于 Docker Compose 本地演示和受控内网部署。默认 Compose 不是完整的公网生产方案；公网部署还需要 TLS、反向代理、主机防火墙、秘密管理、监控和定期备份。

## 1. 前提条件

- Docker Desktop（Windows/macOS）或 Docker Engine + Compose Plugin（Linux）。
- 可用端口：前端 80、后端 54321。数据库与 Redis 通常不应暴露到宿主机。
- 至少 4 GB 可用内存。

在项目根目录先确认：

    docker version
    docker compose version

## 2. 必填秘密

仓库不提供默认数据库密码、Redis 密码、JWT 密钥或管理员密码。先复制模板：

PowerShell：

    Copy-Item .env.example .env

Bash：

    cp .env.example .env

编辑本地 .env，并填写以下四项运行秘密：

    MYSQL_ROOT_PASSWORD=<随机且唯一的 MySQL root 密码>
    MYSQL_APP_PASSWORD=<16-128 位的随机且唯一应用数据库密码>
    REDIS_PASSWORD=<随机且唯一的 Redis 密码>
    JWT_SECRET=<至少 32 个随机字节>

默认 APP_BOOTSTRAP_ENABLED=false，ADMIN_INITIAL_PASSWORD 可以留空。只有首次创建或恢复 admin 时，才临时设置：

    APP_BOOTSTRAP_ENABLED=true
    ADMIN_INITIAL_PASSWORD=<8-72 位的首次管理员密码>

建议用密码管理器生成密码。JWT_SECRET 可用以下命令生成 32 字节随机值：

    openssl rand -hex 32

注意：

- .env 只能保留在部署主机，不能提交到 Git、发到聊天或放入镜像。
- 四项运行秘密均无默认值；缺失时 Compose 直接报错，而不是使用弱口令继续启动。
- .env 值若包含 `$`、`#`、空格或反斜杠，使用单引号包住完整值，防止 Compose 插值或注释截断；不要把引号本身算进密码。
- 后端使用固定的 permacore_app 数据库账户和独立的 MYSQL_APP_PASSWORD，不使用 root。db-access-init 一次性服务会在后端启动前重新创建该账户，以清除任何历史角色或多余授权，再只授予 SELECT、INSERT、UPDATE、DELETE；这也兼容已有 MySQL 数据卷。
- ADMIN_INITIAL_PASSWORD 只在 APP_BOOTSTRAP_ENABLED=true 时必填。若开关为 true 但密码为空或不在 8-72 位范围，应用会拒绝启动；开关为 false 时该值应留空。
- bootstrap 创建或恢复 admin 后会绑定 ROLE_ADMIN，但不会重置一个仍有效的既有 admin 密码。成功后立即关闭 bootstrap 并从 .env 移除首次密码。

## 3. 启动前检查

先解析 Compose 配置：

    docker compose config -q

该命令必须静默成功。不要把不带 `-q` 的完整解析结果保存或分享，因为它会展开 .env 中的真实秘密。然后检查端口占用：

PowerShell：

    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
      Where-Object LocalPort -In 80,54321,3306,6379

如果只是运行系统，MySQL 和 Redis 不需要发布到宿主机。推荐网络边界：

- frontend：按需发布 80。
- backend：仅本机调试时绑定 127.0.0.1:54321；生产流量通过前端反向代理。
- mysql：仅 Compose 内部网络可见。
- redis：仅 Compose 内部网络可见，且必须启用 REDIS_PASSWORD。

临时需要从宿主机调试 Redis 时，只允许绑定 127.0.0.1:6379:6379，完成后立即移除端口映射。禁止把 6379 发布到 0.0.0.0。

## 4. 首次启动

构建并启动：

    docker compose up -d --build

查看状态：

    docker compose ps

查看后端启动日志：

    docker compose logs --tail=200 backend

首次数据卷先按唯一顺序执行两份 canonical SQL：

1. src/main/resources/db/schema.sql
2. src/main/resources/db/init-permissions.sql
随后 db-access-init 调用 docker/mysql/ensure-app-user.sh，以 root 重新创建 permacore_app，再只授予业务所需的四项 DML 权限。重新创建会清掉可能遗留的角色和额外 grant；该脚本只管理 Docker 数据库账户，不复制业务数据。init-permissions.sql 不创建 admin，也不绑定用户角色。不要在容器启动后调用数据库修复接口。

`docker compose ps -a` 中 db-access-init 正常状态为 Exited (0)，其他四项服务应持续运行。若它非零退出，backend 不会启动，应先查看 `docker compose logs db-access-init`，不能绕过依赖顺序。

backend 的 `/api/health` 是无敏感信息的编排探针：它只返回统一成功/失败，并同时检查数据库与启用时的 Redis。frontend 必须等待 backend healthy 后才启动，避免应用启动窗口出现 502；Nginx 对该精确路径返回 404，不把内部探针暴露到前端入口。该端点不会返回版本、主机、连接串或运行时详情。

## 5. 访问与首次登录

| 服务 | 地址 |
|---|---|
| 前端 | http://localhost |
| 后端 API | http://localhost:54321 |
| Swagger | /doc.html，默认需要认证 |

仅在本次启动显式开启 bootstrap 时，首次管理员为：

- 用户名：admin
- 密码：.env 中的 ADMIN_INITIAL_PASSWORD

仓库不存在默认管理员密码。bootstrap 默认关闭；空库若未显式开启，不会生成 admin。首次登录后应立即修改密码，再把 APP_BOOTSTRAP_ENABLED 改回 false 并清空 ADMIN_INITIAL_PASSWORD。Swagger 默认不得公开；只有可信 dev 环境可以显式设置 app.security.public-docs=true，Docker 部署不要开启。

## 6. Redis 配置

Redis 必须同时满足：

- 服务端通过 requirepass 使用 REDIS_PASSWORD。
- 后端使用同一个 REDIS_PASSWORD。
- healthcheck 使用认证后的 PING。
- 不向宿主机或公网发布 6379，除非临时绑定 127.0.0.1 调试。
- Redis 数据写入命名卷，重启容器不应丢失。

验证容器内 Redis：

    docker compose exec redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'

预期结果是 PONG。密码只通过容器环境传入，不放在 redis-cli 参数中。

不要执行 `FLUSHDB` 或 `FLUSHALL` 来“刷新权限”。规范的全局权限初始化会在同一事务内递增单例 `global_auth_version`，用户状态、密码或用户角色变化则递增个人 `auth_version`；旧 token 任一版本不匹配都会被数据库门禁拒绝。Redis 中的旧会话键无法绕过检查，无需清理，清空 Redis 反而可能误删共享数据库中的其他数据。

## 7. 头像与反向代理

后端把头像保存在 /app/uploads，并通过 /uploads/avatars/ 提供访问。Docker 部署必须保证：

- uploads 挂载到 Compose 命名卷 uploads-data。
- Nginx 将 /uploads/ 代理到 backend，不能让 SPA fallback 返回 index.html。
- Nginx 的 client_max_body_size 与后端 2 MB 限制一致；建议显式设置为 2m 或略高，并由后端继续执行最终校验。

验证流程应包含一次头像上传和随后通过返回的 /uploads/avatars/... URL 读取图片。

## 8. 常用运维命令

查看全部服务：

    docker compose ps

查看日志：

    docker compose logs -f backend
    docker compose logs -f mysql
    docker compose logs -f redis

只重建后端：

    docker compose up -d --build backend

停止但保留数据：

    docker compose down

重新启动：

    docker compose up -d

轮换 MYSQL_APP_PASSWORD 时先更新 .env，再让一次性账户服务应用新密码，随后重建后端连接池：

    docker compose stop backend
    docker compose run --rm db-access-init
    docker compose up -d --force-recreate backend

不要把固定 container_name 当作脚本接口。运维命令使用 docker compose exec 服务名，这样不同目录、CI 和并行环境不会因容器名冲突。

## 9. 数据备份

### 9.1 MySQL

先创建备份目录：

    New-Item -ItemType Directory -Force .\backups | Out-Null

PowerShell 5.1 的原生命令重定向可能把 SQL 输出转成 UTF-16，因此不要使用 `docker ... > backup.sql`。让 mysqldump 在容器内直接写文件，再用 docker compose cp 原样复制字节：

    docker compose exec -T mysql sh -c 'umask 077; MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --result-file=/tmp/permacore_iam.sql permacore_iam'
    docker compose cp mysql:/tmp/permacore_iam.sql .\backups\permacore_iam.sql
    docker compose exec -T mysql rm -f /tmp/permacore_iam.sql

Bash 也可使用同一组命令，只需把目标路径写为 backups/permacore_iam.sql。备份后检查文件非空，并至少定期做一次隔离环境恢复演练。backup.sql、backups/ 和其他数据库导出文件不得提交到 Git。

### 9.2 上传文件

同时备份 uploads-data 命名卷。下面的命令让容器直接生成 gzip 文件，不经过 PowerShell 文本管道：

    docker compose run --rm --no-deps -v ./backups:/backup --entrypoint sh backend -c 'umask 077; tar -C /app/uploads -czf /backup/uploads.tar.gz .'

数据库备份与 uploads 备份应使用同一时间点标签，否则用户头像记录与文件可能不一致。

## 10. 数据恢复

恢复前：

1. 确认目标环境和数据库名。
2. 再做一次当前数据备份。
3. 停止写入流量。

用 docker compose cp 把 SQL 原样复制进容器，再在容器内部重定向；不要用 Get-Content 或 PowerShell 的 `>` 处理 SQL 字节：

    docker compose cp .\backups\permacore_iam.sql mysql:/tmp/permacore_iam-restore.sql
    docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot permacore_iam < /tmp/permacore_iam-restore.sql'
    docker compose exec -T mysql rm -f /tmp/permacore_iam-restore.sql

恢复 uploads 时先停止写入流量，再把归档覆盖解压到命名卷。若要求严格删除归档中不存在的旧文件，应先在隔离的新卷演练，不要直接清空生产卷：

    docker compose run --rm --no-deps -v ./backups:/backup --entrypoint sh backend -c 'tar -C /app/uploads -xzf /backup/uploads.tar.gz'

恢复业务库不包含 MySQL 系统账户。恢复后显式重放最小权限账户步骤，再启动后端：

    docker compose run --rm db-access-init
    docker compose up -d backend frontend

随后重新登录并验证用户、角色、权限、SoD、日志和头像。

## 11. 已有数据卷升级

MySQL 官方镜像只在空数据目录首次创建时执行 /docker-entrypoint-initdb.d。已有数据卷不会因为重新构建镜像而重跑 schema、migration 或 init-permissions.sql。旧数据库可能缺少 `sys_user.auth_version`、单例 `sys_authorization_state.global_auth_version`、关系约束或查询索引，必须在新版 backend 启动前显式执行两份迁移；若存在孤儿关联，第一份 migration 会失败并要求先修复数据。Compose 的 MySQL healthcheck 会检查 `auth_version` 列、全局授权单例行及 ROLE_ADMIN/admin:* 基线，但它不是完整迁移校验；仍需按下述固定顺序升级。

严格顺序是：备份 → 停止写入 → 运行 `20260710_add_auth_version.sql` → 运行 `20260710_optimize_user_queries.sql` → 运行 `init-permissions.sql` → 启动新版。先停止外部入口：

    docker compose stop frontend backend
    docker compose up -d mysql

按第 9 节完成备份后，用 docker compose cp 保留 SQL 原始字节，再在 MySQL 容器内以 utf8mb4 执行。不要用 Get-Content、PowerShell 管道或宿主机 `>` 处理 SQL：

    docker compose cp .\src\main\resources\db\migrations\20260710_add_auth_version.sql mysql:/tmp/20260710_add_auth_version.sql
    docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --binary-mode=1 permacore_iam < /tmp/20260710_add_auth_version.sql'
    docker compose exec -T mysql rm -f /tmp/20260710_add_auth_version.sql

    docker compose cp .\src\main\resources\db\migrations\20260710_optimize_user_queries.sql mysql:/tmp/20260710_optimize_user_queries.sql
    docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --binary-mode=1 permacore_iam < /tmp/20260710_optimize_user_queries.sql'
    docker compose exec -T mysql rm -f /tmp/20260710_optimize_user_queries.sql

两份 migration 均成功后，先按《权限更新指南》第 8 节保存 ROLE_USER/ROLE_GUEST 管理授权与 ROLE_AUDITOR 非基线授权的检测结果，再执行 canonical 权限脚本：

    docker compose cp .\src\main\resources\db\init-permissions.sql mysql:/tmp/init-permissions.sql
    docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --binary-mode=1 permacore_iam < /tmp/init-permissions.sql'
    docker compose exec -T mysql rm -f /tmp/init-permissions.sql

再次运行检测 SQL并人工审阅差异；不要自动删除历史自定义授权。随后创建或轮换受限数据库账户，并启动新版：

    docker compose run --rm db-access-init
    docker compose up -d backend frontend

让所有用户重新登录，并验证旧 access/refresh token 已失效。若 MySQL 曾临时安全绑定到本机，也可依次运行 `.\migrate-database.ps1` 和 `.\update-permissions.ps1`；前者在一次确认和密码生命周期中固定执行上述两份 migration，逐项失败即停，后者只执行权限初始化。

## 12. 危险操作

以下命令会永久删除 Compose 数据卷：

    docker compose down -v

只有在确认目标项目、完成备份并明确需要全量重建时才能执行。端口冲突、后端构建失败或初始化脚本未重放，都不是直接删除数据卷的充分理由。

## 13. 更新与验收

仓库为 MySQL、Redis、Maven、Temurin/Alpine、Node/Alpine 和 Nginx 提供锁定的版本化默认标签，避免同一提交在不同日期拉到不同主版本或基础发行版。受限网络可通过 `.env.example` 中列出的六个可选变量改用经过审阅的镜像仓库或本地标签，禁止为了“能拉取”改成 `latest`。升级默认标签或覆盖镜像都必须单独审阅发布说明、重新构建并完成本节验收；更严格的生产发布可在验证后继续锁定多架构 manifest digest。

更新前：

1. 备份数据库和 uploads。
2. 记录当前 Git 提交和镜像版本。
3. 阅读 AUDIT_REPORT.md 与版本变更说明。

更新后至少验证：

- docker compose config -q 成功。
- mysql、redis、backend、frontend 状态正常。
- admin 和普通用户登录。
- JWT 刷新、退出和强制失效。
- 角色继承、SSD、DSD、会话角色切换。
- 用户、角色、权限、部门 CRUD。
- 头像上传与读取。
- 数据重启后仍存在。
- Swagger 在未显式开启 public-docs 时不能匿名访问。

## 14. 隔离性能环境

`docker-compose.perf.yml` 是独立压测栈，不连接宿主机常规 MySQL、不复用默认 Compose 数据卷，也不改变默认部署的 `/api/health` 或业务 API。它以 `docker,perf` profile 启动后端，仅在宿主回环地址发布：

| 用途 | 地址 |
|---|---|
| 压测业务 API | http://127.0.0.1:15432 |
| Prometheus 指标 | http://127.0.0.1:15433/actuator/prometheus |

固定资源边界为后端 4 vCPU/2 GiB（JVM Xms/Xmx 1 GiB）、MySQL 2 vCPU/2 GiB（Buffer Pool 1 GiB）、Redis 1 vCPU/512 MiB。五项 `PERF_*` 秘密只放在当前进程环境中，随后可执行：

    .\performance\Invoke-PerformanceTest.ps1 -Action Smoke -Scale 10k
    .\performance\Invoke-PerformanceTest.ps1 -Action Baseline -Scale 10k -Rates 50,100,200,400,800 -Repeats 3 -WarmupSeconds 30 -SampleSeconds 120

除 `Prepare` 外，编排器默认清理隔离容器和卷；保留环境调试后必须执行 `.\performance\Invoke-PerformanceTest.ps1 -Action Down`。复现说明见 [performance/README.md](performance/README.md)，经审阅结果和限制见 [PERFORMANCE_REPORT.md](PERFORMANCE_REPORT.md)。当前可公开引用的受控结论仅为 10k 用户读混合最高健康档 200 RPS；100k 用户读混合受前导通配符模糊查询限制，不能据此推导生产 SLA。
