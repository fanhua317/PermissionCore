# PermaCore IAM 前端

PermaCore IAM 的 Vue 3 + TypeScript 管理端，提供用户、角色、权限、部门、职责分离（SoD）和审计日志等界面。

## 环境要求

- Node.js 20.19+ 或 22.12+
- npm 10+
- PermaCore 后端默认运行在 `http://localhost:54321`

## 本地开发

```powershell
npm ci
npm run dev
```

开发服务器默认地址为 `http://localhost:5173`。`/api` 与 `/uploads` 会代理到后端；如需修改目标地址，可在本地环境文件中设置：

```dotenv
VITE_API_PROXY_TARGET=http://localhost:54321
```

## 构建与校验

```powershell
npm run build
npm audit
```

构建产物生成在 `dist/`。生产镜像使用仓库中的 `Dockerfile` 构建，并由 Nginx 提供静态资源及后端反向代理。

## 目录说明

- `src/views/`：业务页面
- `src/layout/`：主布局与个人设置
- `src/router/`：路由与登录/权限守卫
- `src/store/`：Pinia 用户会话状态
- `src/utils/request.ts`：统一 HTTP 请求、Token 刷新与错误处理

账号密码不写入前端源码。首次部署的管理员凭据及修改方式请以项目根目录的部署文档和实际环境配置为准。
