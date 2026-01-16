# PermaCore IAM 权限管理系统

> 基于角色访问控制（RBAC3）的网络空间安全权限管理系统，实现用户、角色、权限的统一管理，支持 JWT 无状态认证、缓存加速和审计日志。

## 一、项目简介

PermaCore IAM 是一个面向企业/组织内部管理场景的权限管理子系统，实现了基于角色访问控制（RBAC）的用户认证与细粒度授权。系统提供了完整的用户管理、角色管理、权限管理能力，并通过 Web 管理界面和开放的 REST API 方便集成到其他业务系统中。

本项目可作为《网络空间安全》《软件工程》《数据库系统》等课程的大作业或毕业设计选题，题目示例：**“基于角色访问控制的网络空间安全权限管理软件(或系统)”**。

## 二、RBAC3 权限模型详解

### 2.1 RBAC 模型层次

本系统实现了完整的 **RBAC3（Role-Based Access Control Level 3）** 权限模型，RBAC3 = RBAC1 + RBAC2，包含以下核心特性：

```
┌─────────────────────────────────────────────────────────────┐
│                        RBAC3                                │
│  ┌─────────────────────┐  ┌─────────────────────────────┐  │
│  │      RBAC1          │  │          RBAC2              │  │
│  │   (角色继承)         │  │    (静态/动态职责分离)       │  │
│  │                     │  │                             │  │
│  │  高级角色            │  │   SSD: 静态互斥约束         │  │
│  │     ↑               │  │   DSD: 动态互斥约束         │  │
│  │     │ 继承           │  │                             │  │
│  │  基础角色            │  │                             │  │
│  └─────────────────────┘  └─────────────────────────────┘  │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    RBAC0 (核心)                      │   │
│  │           用户 ←→ 角色 ←→ 权限                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 角色继承 (RBAC1)

角色继承允许创建角色层次结构，子角色自动继承父角色的所有权限。

**示例场景：**
```
超级管理员 (ROLE_ADMIN)
    ↑
部门经理 (ROLE_MANAGER)  ←── 继承 ──→  普通用户 (ROLE_USER)
    ↑
开发人员 (ROLE_DEVELOPER) ←── 继承 ──→  普通用户 (ROLE_USER)
```

**实现方式：**
- 数据库表：`sys_role_inheritance`（存储角色继承关系）
- 前端操作：角色管理页面 → 点击"继承"按钮 → 选择父角色
- API：`PUT /api/role-inheritance/{roleId}` 配置角色的父角色

### 2.3 职责分离约束 (RBAC2)

职责分离（Separation of Duty, SoD）是安全管理的重要原则，防止用户拥有相互冲突的权限。

#### 2.3.1 静态职责分离 (SSD - Static Separation of Duty)

**定义**：在角色分配阶段进行约束，禁止将互斥角色同时分配给同一用户。

**示例：**
| 约束名称 | 互斥角色 | 说明 |
|---------|---------|------|
| 审计员与开发人员互斥 | ROLE_AUDITOR, ROLE_DEVELOPER | 审计人员不能同时是开发人员 |
| 财务与审计互斥 | ROLE_FINANCE, ROLE_AUDITOR | 财务人员不能同时是审计员 |

**实现效果**：当管理员尝试给用户同时分配"审计员"和"开发人员"角色时，系统会拒绝并提示约束冲突。

#### 2.3.2 动态职责分离 (DSD - Dynamic Separation of Duty)

**定义**：在会话激活阶段进行约束，允许用户拥有互斥角色，但在同一会话中不能同时激活。

**示例：**
| 约束名称 | 互斥角色 | 说明 |
|---------|---------|------|
| 经理与HR动态互斥 | ROLE_MANAGER, ROLE_HR | 同一会话不能同时以经理和HR身份操作 |

**数据库表结构：**
```sql
-- sys_sod_constraint 表
CREATE TABLE sys_sod_constraint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    constraint_name VARCHAR(100) NOT NULL COMMENT '约束名称',
    role_set TEXT NOT NULL COMMENT '互斥角色ID数组（JSON格式）',
    constraint_type TINYINT NOT NULL COMMENT '约束类型：1-静态互斥 2-动态互斥',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

**管理接口：**
- `GET /api/sod-constraint/list` - 获取所有约束
- `POST /api/sod-constraint` - 创建约束
- `PUT /api/sod-constraint/{id}` - 更新约束
- `DELETE /api/sod-constraint/{id}` - 删除约束

## 三、系统特性概览

### 近期更新
- 升级运行环境到 **Java 21**（已在 pom 配置与启动验证）；
- 启用 Redis 分布式缓存 + L1 本地缓存广播失效，避免多节点本地脏读；
- 角色继承查询改为数据库递归 CTE，一次性计算全量祖先角色，降低鉴权查询延迟。

- **RBAC3 权限模型**：支持用户–角色–权限三层关系，扩展角色继承和职责分离（SoD）约束；
- **JWT 无状态认证**：基于 Spring Security 6 + JWT，实现后端无 Session 的分布式认证；
- **JWT 强制失效机制**：结合 Redis/Caffeine 缓存与 JWT 版本号，实现登出/禁用用户后的 Token 立即失效；
- **二级缓存架构**：本地 Caffeine（5分钟 TTL） + Redis（可选）组合缓存热点数据，显著降低数据库压力；
- **审计日志**：
  - 登录日志：自动记录登录成功/失败、IP、浏览器、操作系统等信息；
  - 操作日志：通过 `@OperLog` 注解实现 AOP 切面自动记录增删改操作；
- **部门管理**：支持树形组织架构，用户归属部门，按部门查询成员；
- **前后端分离**：后端 Spring Boot + MyBatis-Plus，前端 Vue3 + Vite + Element Plus；
- **接口文档**：集成 Springdoc / Knife4j，提供可交互的在线 API 文档。

## 四、技术栈

### 后端
- Java 21+
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

## 五、功能模块

1. **用户管理**
   - 用户信息的新增、删除、修改、查询；
   - 用户账号启用/禁用；
   - 重置密码等操作；
   - **SSD 约束校验**：分配角色时自动检查静态职责分离约束；

2. **角色管理**
   - 角色的创建、编辑、删除；
   - **角色继承配置**：设置角色的父角色，实现权限继承（RBAC1）；
   - 配置角色权限绑定；

3. **权限管理**
   - 权限点定义（如 `user:view`、`user:create` 等）；
   - **层级权限树**：支持菜单/按钮/API三级权限结构；
   - 角色与权限绑定，形成可复用的权限集合；

4. **职责分离约束管理（RBAC2）**
   - SSD 静态互斥约束配置；
   - DSD 动态互斥约束配置；
   - 约束冲突实时校验；

5. **身份认证与授权**
   - 基于用户名 + 密码的登录；
   - 签发访问 Token 与刷新 Token；
   - 通过 `@PreAuthorize` 等注解进行方法级访问控制；

6. **审计日志**
   - 登录日志：记录登录时间、IP、浏览器等信息；
   - 操作日志：记录敏感操作的操作者、请求参数与结果；

7. **权限演示与测试接口**
   - 提供若干测试接口演示不同权限下的访问效果，便于调试与教学演示。

## 六、环境准备

- JDK：21（已在项目中按 Java 21 配置）
- Maven：3.8+ 建议
- Node.js：建议 18+（你当前环境的 24.x 也可，但若遇到兼容问题建议切换到 LTS 18/20）
- MySQL：8.0+（数据库名默认为 `permacore_iam`）
- Redis：可选，如需启用二级缓存和分布式锁建议安装 Redis 7.x

## 七、数据库初始化

1. 在 MySQL 中创建数据库：
   ```sql
   CREATE DATABASE IF NOT EXISTS permacore_iam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 执行 `src/main/resources/db/init-permissions.sql`：
   - 该脚本中包含：
     - 用户、角色、权限及其关联表结构；
     - 测试数据（如超级管理员账户及其角色/权限绑定）；

> 实际使用时，可根据需要调整初始数据，例如修改默认管理员密码或增加业务角色。

## 八、后端启动步骤

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
   应用端口配置参考 `application.yml`（生产默认 54321，开发 dev profile 为 8081）。

4. **访问接口文档**
   - 启动成功后，可通过浏览器访问：
     - Knife4j/Swagger 文档地址（示例）：`http://localhost:8081/doc.html`（dev 环境）

## 九、前端启动步骤

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

> 注意：前端请求的后端 API 基础地址在 `vite.config.ts` 中通过代理配置，开发环境代理到 `http://localhost:8081`。

## 十、默认账号信息

根据初始化 SQL 脚本，系统默认创建以下账号：

**超级管理员**
- 用户名：`admin`
- 密码：`Admin@123456`

**默认角色**
| 角色标识 | 角色名称 | 说明 |
|---------|---------|------|
| ROLE_ADMIN | 超级管理员 | 拥有所有权限 |
| ROLE_USER | 普通用户 | 基础权限 |
| ROLE_MANAGER | 部门经理 | 部门级管理权限 |
| ROLE_HR | 人力资源 | 人事管理权限 |
| ROLE_AUDITOR | 审计员 | 只读审计权限 |
| ROLE_DEVELOPER | 开发人员 | 开发相关权限 |
| ROLE_GUEST | 访客 | 仅查看权限 |

**默认部门**
- 总公司
  - 技术部（后端开发组、前端开发组、测试组）
  - 人力资源部
  - 财务部
  - 市场部

> 密码在数据库中以 BCrypt 形式存储，登录逻辑通过 Spring Security 提供的 `BCryptPasswordEncoder` 验证。

首次登录后，建议立即在系统中修改管理员密码，并根据课程要求设置不同的角色和权限以进行实验。

## 十一、常见问题 FAQ

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

## 十二、项目结构简要

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
│      ├─ router/index.ts             # 路由配置
│      ├─ store/user.ts               # 用户状态管理 (Pinia)
│      ├─ layout/
│      │   └─ MainLayout.vue          # 主布局框架（侧边栏+顶栏+内容区）
│      └─ views/
│          ├─ Login.vue               # 登录页面
│          ├─ Dashboard.vue           # 控制台/首页
│          ├─ UserManage.vue          # 用户管理
│          ├─ RoleManage.vue          # 角色管理（含权限分配、角色继承）
│          ├─ PermissionManage.vue    # 权限管理（树形结构）
│          ├─ DeptManage.vue          # 部门管理
│          ├─ LoginLog.vue            # 登录日志
│          └─ OperLog.vue             # 操作日志
└─ ...
```

## 十三、前端功能模块

本项目前端采用 **Vue 3 + TypeScript + Element Plus** 技术栈，提供完整的管理界面：

| 模块 | 功能说明 |
|------|----------|
| **控制台** | 展示系统概览、统计数据、快捷操作入口、最近操作日志 |
| **用户管理** | 用户CRUD、状态切换、部门关联、角色分配 |
| **角色管理** | 角色CRUD、权限分配（树形选择）、角色继承配置 |
| **权限管理** | 权限树展示、支持菜单/按钮/API三种类型、层级管理 |
| **部门管理** | 部门树形结构、部门详情、部门成员列表 |
| **登录日志** | 登录记录查询、多条件筛选、日志清理 |
| **操作日志** | 操作记录查询、详情查看（请求参数/返回结果） |

### 前端技术亮点

- **响应式布局**：支持侧边栏折叠，适配不同屏幕尺寸
- **动态路由**：基于权限的路由守卫
- **状态管理**：使用 Pinia 管理用户认证状态
- **Token 自动刷新**：401 时自动使用 refreshToken 刷新
- **统一请求封装**：Axios 拦截器处理认证和错误
- **页面过渡动画**：流畅的路由切换体验

## 十四、用户操作手册

详细的系统操作指南请参阅 **[用户操作手册 (USER_GUIDE.md)](USER_GUIDE.md)**，包含：

- 系统登录与账户管理
- 用户、角色、权限的完整操作流程
- 部门管理与组织架构配置
- 日志查询与审计功能
- 常见问题与故障排除

## 十五、后续扩展

- 引入更细粒度的数据权限控制（如按部门、区域划分的数据可见性）；
- 支持多租户场景（不同租户拥有独立的用户/角色/权限空间）；
- 集成 OAuth2/OIDC 协议，作为统一身份认证服务（IdP）；
- Docker 容器化部署，提供 docker-compose 一键启动方案；
- 增加单元测试覆盖率，特别是角色继承和互斥约束的核心逻辑。

