# ============================================================
# PermaCore UI 前端 Dockerfile（多阶段构建）
# ============================================================

ARG NODE_IMAGE=node:22.23.1-alpine3.24
ARG NGINX_IMAGE=nginx:1.30.3-alpine

# ---------- 第一阶段：Node 构建 ----------
FROM ${NODE_IMAGE} AS builder

WORKDIR /build

# 先拷贝依赖描述文件（利用缓存层）
COPY package.json package-lock.json ./
RUN npm ci

# 拷贝源代码并构建
COPY . .
RUN npm run build

# ---------- 第二阶段：Nginx 运行 ----------
FROM ${NGINX_IMAGE}

# 移除默认配置
RUN rm /etc/nginx/conf.d/default.conf

# 拷贝自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/permacore.conf

# 从构建阶段拷贝前端产物
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
