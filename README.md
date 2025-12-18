# PermaCore IAM 权限管理系统

> 基于角色访问控制（RBAC3）的网络空间安全权限管理系统，实现用户、角色、权限的统一管理，支持 JWT 无状态认证、缓存加速和审计日志。

## 一、项目简介

PermaCore IAM 是一个面向企业/组织内部管理场景的权限管理子系统，实现了基于角色访问控制（RBAC）的用户认证与细粒度授权。系统提供了完整的用户管理、角色管理、权限管理能力，并通过 Web 管理界面和开放的 REST API 方便集成到其他业务系统中。

本项目可作为《网络空间安全》《软件工程》《数据库系统》等课程的大作业或毕业设计选题，题目示例：**“基于角色访问控制的网络空间安全权限管理软件(或系统)”**。

## 二、系统特性概览

- **RBAC3 权限模型**：支持用户–角色–权限三层关系，扩展角色继承和职责分离（SoD）约束；
- **JWT 无状态认证**：基于 Spring Security 6 + JWT，实现后端无 Session 的分布式认证；
- **JWT 强制失效机制**：结合 Redis/Caffeine 缓存与 JWT 版本号，实现登出/禁用用户后的 Token 立即失效；
- **二级缓存架构**：本地 Caffeine + Redis（可选）组合缓存热点数据，显著降低数据库压力；
- **审计日志**：记录登录日志与操作日志，为安全审计和问题追踪提供依据；
- **前后端分离**：后端 Spring Boot + MyBatis-Plus，前端 Vue3 + Vite + Element Plus；
- **接口文档**：集成 Springdoc / Knife4j，提供可交互的在线 API 文档。

## 三、技术栈

### 后端
- Java 17+
- Spring Boot 3.x
- Spring Security 6
- MyBatis-Plus + MyBatis
- Redis（可选）+ Redisson
- Caffeine 本地缓存
- MySQL 8.x
- Springdoc / Knife4j（OpenAPI 文档）

### 前端
- Node.js 18+ / 20+
- Vue 3
- TypeScript
- Vite
- Element Plus
- Axios

## 四、功能模块

1. **用户管理**
   - 用户信息的新增、删除、修改、查询；
   - 用户账号启用/禁用；
   - 重置密码等操作；

2. **角色管理**
   - 角色的创建、编辑、删除；
   - 配置角色继承（父子角色关系）；

3. **权限管理**
   - 权限点定义（如 `user:view`、`user:create` 等）；
   - 角色与权限绑定，形成可复用的权限集合；

4. **身份认证与授权**
   - 基于用户名 + 密码的登录；
   - 签发访问 Token 与刷新 Token；
   - 通过 `@PreAuthorize` 等注解进行方法级访问控制；

5. **审计日志**
   - 登录日志：记录登录时间、IP、浏览器等信息；
   - 操作日志：记录敏感操作的操作者、请求参数与结果；

6. **权限演示与测试接口**
   - 提供若干测试接口演示不同权限下的访问效果，便于调试与教学演示。

## 五、环境准备

- JDK：17 或更高版本（已在项目中按 Java 17 配置）
- Maven：3.8+ 建议
- Node.js：建议 18+（你当前环境的 24.x 也可，但若遇到兼容问题建议切换到 LTS 18/20）
- MySQL：8.0+（数据库名默认为 `permacore_iam`）
- Redis：可选，如需启用二级缓存和分布式锁建议安装 Redis 7.x

## 六、数据库初始化

1. 在 MySQL 中创建数据库：
   ```sql
   CREATE DATABASE IF NOT EXISTS permacore_iam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 执行 `src/main/resources/db/init-permissions.sql`：
   - 该脚本中包含：
     - 用户、角色、权限及其关联表结构；
     - 测试数据（如超级管理员账户及其角色/权限绑定）；

> 实际使用时，可根据需要调整初始数据，例如修改默认管理员密码或增加业务角色。

## 七、后端启动步骤

1. **配置数据库与 Redis（可选）**
   - 修改 `src/main/resources/application.yml` 或 `application-dev.yml` 中的数据库连接信息：
     - `spring.datasource.url`
     - `spring.datasource.username`
     - `spring.datasource.password`

   - 是否启用 Redis：
     - 在 `application.yml` 中：
       ```yaml
       app:
         redis:
           enabled: false  # 若需要启用 Redis + Redisson，请改为 true 并确保 redis 服务正常
       ```

2. **构建后端**
   在项目根目录执行：
   ```bash
   mvn clean package -DskipTests
   ```
   成功后，会在 `target/` 目录生成 `permacore-iam-1.0.0-SNAPSHOT.jar`。

3. **运行后端应用**
   方式一：IDE 中直接运行 `PermaCoreApplication` 主类；

   方式二：命令行运行 jar：
   ```bash
   # 如需指定 profile：
   java -jar target/permacore-iam-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
   ```
   应用默认监听端口配置参考 `application.yml`（如 `server.port: 54321`）。

4. **访问接口文档**
   - 启动成功后，可通过浏览器访问：
     - Knife4j/Swagger 文档地址（示例）：`http://localhost:54321/doc.html`

## 八、前端启动步骤

1. 进入前端目录：
   ```bash
   cd permacore-ui
   ```

2. 安装依赖：
   ```bash
   npm install
   # 或
   pnpm install
   ```

3. 启动开发服务：
   ```bash
   npm run dev
   ```
   默认开发地址通常为：`http://localhost:5173`。

> 注意：前端请求的后端 API 基础地址通常在 `src/utils/request.ts` 中配置，请确保与后端真实地址一致（如 `http://localhost:54321`）。

## 九、默认账号信息

根据初始化 SQL 脚本，系统默认创建了一个超级管理员账号（可在 `init-permissions.sql` 中确认）：

- 用户名：`admin`
- 密码：`Admin@123456`  或你脚本中实际配置的初始密码（建议在脚本中保持与文档一致）

> 密码在数据库中以 BCrypt 形式存储，登录逻辑通过 Spring Security 提供的 `BCryptPasswordEncoder` 验证。

首次登录后，建议立即在系统中修改管理员密码，并根据课程要求设置不同的角色和权限以进行实验。

## 十、常见问题 FAQ

1. **后端无法连接数据库**
   - 检查 `application.yml` 中的数据源 URL、用户名、密码；
   - 确认 MySQL 服务已启动，端口正确（如 3306）；
   - 如果是远程数据库，注意防火墙及权限设置。

2. **Redis 相关错误**
   - 若未安装 Redis，建议将 `app.redis.enabled` 保持为 `false`；
   - 若已安装 Redis，确保端口和密码配置正确，并在日志中观察 Redisson 初始化信息。

3. **前端访问接口出现 401/403**
   - 确保登录成功后前端已保存 Token，并在请求头中携带 `Authorization: Bearer <token>`；
   - 401 通常表示未登录或 Token 失效；
   - 403 通常表示当前账号权限不足，请检查角色与权限配置。

4. **前端无法访问后端（CORS 或跨域问题）**
   - 检查后端是否配置了允许前端源的 CORS 策略；
   - 确认前端请求的 URL 与后端监听地址、端口一致。

## 十一、项目结构简要

```text
Permission Core/
├─ pom.xml                # 后端 Maven 根配置
├─ src/
│  ├─ main/java/com/permacore/iam/
│  │   ├─ PermaCoreApplication.java   # 启动类
│  │   ├─ config/                     # 安全、MyBatis、Swagger 等配置
│  │   ├─ controller/                 # 控制器（用户、角色、权限等）
│  │   ├─ domain/entity/              # 实体类（用户、角色、权限、日志等）
│  │   ├─ mapper/                     # Mapper 接口
│  │   ├─ security/                   # JWT 过滤器、异常处理等
│  │   └─ utils/                      # JwtUtil、RedisCacheUtil 等工具
│  └─ main/resources/
│      ├─ application.yml             # 配置文件
│      ├─ db/init-permissions.sql     # 初始化 SQL
│      └─ mapper/*.xml                # MyBatis 映射文件
├─ permacore-ui/
│  ├─ package.json                    # 前端依赖与脚本
│  ├─ vite.config.ts                  # Vite 配置
│  └─ src/
│      ├─ main.ts                     # 前端入口
│      ├─ App.vue
│      ├─ router/index.ts             # 路由
│      ├─ store/user.ts               # 用户状态管理
│      └─ views/
│          ├─ Login.vue               # 登录页面
│          └─ UserManage.vue          # 用户管理页面
└─ ...
```

## 十二、后续扩展

- 引入更细粒度的数据权限控制（如按部门、区域划分的数据可见性）；
- 支持多租户场景（不同租户拥有独立的用户/角色/权限空间）；
- 集成 OAuth2/OIDC 协议，作为统一身份认证服务（IdP）；
- 增加更多前端管理功能页面（角色管理、权限管理、日志查询可视化等）。

