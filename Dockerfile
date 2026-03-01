# ============================================================
# PermaCore IAM 后端 Dockerfile（多阶段构建）
# ============================================================

# ---------- 第一阶段：Maven 编译打包 ----------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先拷贝 pom.xml 以利用 Docker 缓存层（依赖不变时不会重新下载）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再拷贝源代码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- 第二阶段：运行环境（仅 JRE，体积更小） ----------
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="PermaCore Team"

WORKDIR /app

# 从构建阶段拷贝 jar
COPY --from=builder /build/target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads

# 暴露端口
EXPOSE 54321

# 设置时区
ENV TZ=Asia/Shanghai

# 启动命令，使用 docker profile
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
