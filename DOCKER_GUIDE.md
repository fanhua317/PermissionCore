# PermaCore IAM Docker 部署指南

## 前提条件

在新电脑上安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows/Mac）或 Docker Engine（Linux）。

## 快速启动

```bash
# 1. 将整个项目目录拷贝到新电脑（U盘/Git Clone 均可）

# 2. 进入项目根目录
cd "Permission Core"

# 3. 一键启动所有服务
docker compose up -d --build
```

首次启动需要下载镜像和编译，大约 5-15 分钟。之后再启动只需几秒。

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端界面 | http://localhost |
| 后端 API | http://localhost:54321 |
| Swagger 文档 | http://localhost/doc.html |
| MySQL | localhost:3306（用户: root，密码: 123456） |
| Redis | localhost:6379 |

**默认管理员账号**：`admin` / `admin123`

## 常用命令

```bash
# 查看所有服务状态
docker compose ps

# 查看后端日志
docker compose logs -f backend

# 停止所有服务
docker compose down

# 停止并删除数据（重新初始化数据库）
docker compose down -v

# 重新构建某个服务
docker compose up -d --build backend
```

## 数据备份与恢复

### 备份 MySQL 数据

```bash
docker exec permacore-mysql mysqldump -uroot -p123456 permacore_iam > backup.sql
```

### 恢复 MySQL 数据

```bash
docker exec -i permacore-mysql mysql -uroot -p123456 permacore_iam < backup.sql
```

### 备份上传文件

项目根目录的 `uploads/` 文件夹直接挂载到容器，拷贝该文件夹即可。

## 自定义配置

在项目根目录创建 `.env` 文件来覆盖默认配置：

```env
MYSQL_ROOT_PASSWORD=your_secure_password
JWT_SECRET=your_custom_jwt_secret
```

## 常见问题

### Q: 端口被占用？
修改 `docker-compose.yml` 中的端口映射，例如把 `"80:80"` 改为 `"8080:80"`。

### Q: MySQL 初始化脚本没有执行？
初始化脚本仅在 **首次创建数据卷** 时执行。如需重新初始化：
```bash
docker compose down -v
docker compose up -d --build
```

### Q: 后端启动失败？
查看日志排查：
```bash
docker compose logs backend
```
常见原因：MySQL 还没完全启动（已通过 healthcheck 解决），或 Java 内存不足（可在 Dockerfile 中添加 JVM 参数）。
