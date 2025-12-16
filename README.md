# PermaCore IAM 权限管理系统
- 增加更多前端管理功能页面（角色管理、权限管理、日志查询可视化等）。
- 集成 OAuth2/OIDC 协议，作为统一身份认证服务（IdP）；
- 支持多租户场景（不同租户拥有独立的用户/角色/权限空间）；
- 引入更细粒度的数据权限控制（如按部门、区域划分的数据可见性）；

## 十二、后续扩展

```
└─ ...
│          └─ UserManage.vue          # 用户管理页面
│          ├─ Login.vue               # 登录页面
│      └─ views/
│      ├─ store/user.ts               # 用户状态管理
│      ├─ router/index.ts             # 路由
│      ├─ App.vue
│      ├─ main.ts                     # 前端入口
│  └─ src/
│  ├─ vite.config.ts                  # Vite 配置
│  ├─ package.json                    # 前端依赖与脚本
├─ permacore-ui/
│      └─ mapper/*.xml                # MyBatis 映射文件
│      ├─ db/init-permissions.sql     # 初始化 SQL
│      ├─ application.yml             # 配置文件
│  └─ main/resources/
│  │   └─ utils/                      # JwtUtil、RedisCacheUtil 等工具
│  │   ├─ security/                   # JWT 过滤器、异常处理等
│  │   ├─ mapper/                     # Mapper 接口
│  │   ├─ domain/entity/              # 实体类（用户、角色、权限、日志等）
│  │   ├─ controller/                 # 控制器（用户、角色、权限等）
│  │   ├─ config/                     # 安全、MyBatis、Swagger 等配置
│  │   ├─ PermaCoreApplication.java   # 启动类
│  ├─ main/java/com/permacore/iam/
├─ src/
├─ pom.xml                # 后端 Maven 根配置
Permission Core/
```text

## 十一、项目结构简要

   - 确认前端请求的 URL 与后端监听地址、端口一致。
   - 检查后端是否配置了允许前端源的 CORS 策略；
4. **前端无法访问后端（CORS 或跨域问题）**

   - 403 通常表示当前账号权限不足，请检查角色与权限配置。
   - 401 通常表示未登录或 Token 失效；
   - 确保登录成功后前端已保存 Token，并在请求头中携带 `Authorization: Bearer <token>`；
3. **前端访问接口出现 401/403**

   - 若已安装 Redis，确保端口和密码配置正确，并在日志中观察 Redisson 初始化信息。
   - 若未安装 Redis，建议将 `app.redis.enabled` 保持为 `false`；
2. **Redis 相关错误**

   - 如果是远程数据库，注意防火墙及权限设置。
   - 确认 MySQL 服务已启动，端口正确（如 3306）；
   - 检查 `application.yml` 中的数据源 URL、用户名、密码；
1. **后端无法连接数据库**

## 十、常见问题 FAQ

首次登录后，建议立即在系统中修改管理员密码，并根据课程要求设置不同的角色和权限以进行实验。

> 密码在数据库中以 BCrypt 形式存储，登录逻辑通过 Spring Security 提供的 `BCryptPasswordEncoder` 验证。

- 密码：`Admin@123456`  或你脚本中实际配置的初始密码（建议在脚本中保持与文档一致）
- 用户名：`admin`

根据初始化 SQL 脚本，系统默认创建了一个超级管理员账号（可在 `init-permissions.sql` 中确认）：

## 九、默认账号信息

> 注意：前端请求的后端 API 基础地址通常在 `src/utils/request.ts` 中配置，请确保与后端真实地址一致（如 `http://localhost:54321`）。

默认开发地址通常为：`http://localhost:5173`。

```
npm run dev
```bash

3. 启动开发服务：

```
# pnpm install
# 或
npm install
```bash

2. 安装依赖：

```
cd permacore-ui
```bash

1. 进入前端目录：

## 八、前端启动步骤

  - Knife4j/Swagger 文档地址（示例）：`http://localhost:54321/doc.html`
- 启动成功后，可通过浏览器访问：

4. **访问接口文档**

应用默认监听端口配置参考 `application.yml`（如 `server.port: 54321`）。

```
# java -jar target/permacore-iam-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
# 如需指定 profile：
java -jar target/permacore-iam-1.0.0-SNAPSHOT.jar
```bash

方式二：命令行运行 jar：

方式一：IDE 中直接运行 `PermaCoreApplication` 主类；

3. **运行后端应用**

成功后，会在 `target/` 目录生成 `permacore-iam-1.0.0-SNAPSHOT.jar`。

```
mvn clean package -DskipTests
```bash

在项目根目录执行：

2. **构建后端**

```
    enabled: false  # 若需要启用 Redis + Redisson，请改为 true 并确保 redis 服务正常
  redis:
app:
```yaml

  - 在 `application.yml` 中：
- 是否启用 Redis：

  - `spring.datasource.password`
  - `spring.datasource.username`
  - `spring.datasource.url`
- 修改 `src/main/resources/application.yml` 或 `application-dev.yml` 中的数据库连接信息：

1. **配置数据库与 Redis（可选）**

## 七、后端启动步骤

> 实际使用时，可根据需要调整初始数据，例如修改默认管理员密码或增加业务角色。

  - 测试数据（如超级管理员账户及其角色/权限绑定）；
  - 用户、角色、权限及其关联表结构；
- 该脚本中包含：

2. 执行 `src/main/resources/db/init-permissions.sql`：

```
CREATE DATABASE IF NOT EXISTS permacore_iam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```sql

1. 在 MySQL 中创建数据库：

## 六、数据库初始化

- Redis：可选，如需启用二级缓存和分布式锁建议安装 Redis 7.x
- MySQL：8.0+（数据库名默认为 `permacore_iam`）
- Node.js：建议 18+（你当前环境的 24.x 也可，但若遇到兼容问题建议切换到 LTS 18/20）
- Maven：3.8+ 建议
- JDK：17 或更高版本（已在项目中按 Java 17 配置）

## 五、环境准备

   - 提供若干测试接口演示不同权限下的访问效果，便于调试与教学演示。
6. **权限演示与测试接口**
   - 操作日志：记录敏感操作的操作者、请求参数与结果；
   - 登录日志：记录登录时间、IP、浏览器等信息；
5. **审计日志**
   - 通过 `@PreAuthorize` 等注解进行方法级访问控制；
   - 签发访问 Token 与刷新 Token；
   - 基于用户名 + 密码的登录；
4. **身份认证与授权**
   - 角色与权限绑定，形成可复用的权限集合；
   - 权限点定义（如 `user:view`、`user:create` 等）；
3. **权限管理**
   - 配置角色继承（父子角色关系）；
   - 角色的创建、编辑、删除；
2. **角色管理**
   - 重置密码等操作；
   - 用户账号启用/禁用；
   - 用户信息的新增、删除、修改、查询；
1. **用户管理**

## 四、功能模块

- Axios
- Element Plus
- Vite
- TypeScript
- Vue 3
- Node.js 18+ / 20+

### 前端

- Springdoc / Knife4j（OpenAPI 文档）
- MySQL 8.x
- Caffeine 本地缓存
- Redis（可选）+ Redisson
- MyBatis-Plus + MyBatis
- Spring Security 6
- Spring Boot 3.x
- Java 17+

### 后端

## 三、技术栈

- **接口文档**：集成 Springdoc / Knife4j，提供可交互的在线 API 文档。
- **前后端分离**：后端 Spring Boot + MyBatis-Plus，前端 Vue3 + Vite + Element Plus；
- **审计日志**：记录登录日志与操作日志，为安全审计和问题追踪提供依据；
- **二级缓存架构**：本地 Caffeine + Redis（可选）组合缓存热点数据，显著降低数据库压力；
- **JWT 强制失效机制**：结合 Redis/Caffeine 缓存与 JWT 版本号，实现登出/禁用用户后的 Token 立即失效；
- **JWT 无状态认证**：基于 Spring Security 6 + JWT，实现后端无 Session 的分布式认证；
- **RBAC3 权限模型**：支持用户–角色–权限三层关系，扩展角色继承和职责分离（SoD）约束；

## 二、系统特性概览

本项目可作为《网络空间安全》《软件工程》《数据库系统》等课程的大作业或毕业设计选题，题目示例：**“基于角色访问控制的网络空间安全权限管理软件(或系统)”**。

PermaCore IAM 是一个面向企业/组织内部管理场景的权限管理子系统，实现了基于角色访问控制（RBAC）的用户认证与细粒度授权。系统提供了完整的用户管理、角色管理、权限管理能力，并通过 Web 管理界面和开放的 REST API 方便集成到其他业务系统中。

## 一、项目简介

> 基于角色访问控制（RBAC3）的网络空间安全权限管理系统，实现用户、角色、权限的统一管理，支持 JWT 无状态认证、缓存加速和审计日志，适合作为课程设计/毕业设计项目。


