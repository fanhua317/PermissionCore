# PermaCore 代码生成工具

代码生成器位于独立的 codegen Maven 模块。它只允许读取已经按项目真实 SQL 初始化的 MySQL 数据库，不再支持 useH2，也不会用伪造的通用列创建内存表。

## 1. 为什么禁止 H2 伪表

sys_user、sys_role、sys_permission 等表的字段、索引和类型并不相同。用统一的 id、name、del_flag 等假字段生成代码，会得到与 schema.sql 不匹配的实体、Mapper 和 XML，并可能覆盖正确源码。

生成器的唯一数据库事实源是：

1. src/main/resources/db/schema.sql
2. src/main/resources/db/init-permissions.sql

生成前必须在隔离的真实 MySQL 数据库执行这两个脚本。

## 2. 连接变量与输出目录

生成器读取：

| 变量 | 说明 |
|---|---|
| CODEGEN_DB_URL | 真实 MySQL JDBC URL |
| CODEGEN_DB_USERNAME | 只读或最小权限数据库用户 |
| CODEGEN_DB_PASSWORD | 数据库密码 |
| CODEGEN_OUTPUT_DIR | 可选输出根目录；相对路径按项目根目录解析 |

生成器不提供数据库 URL、用户名或密码默认值，缺少这三项时立即失败。CODEGEN_OUTPUT_DIR 可省略，默认使用项目根目录下的 target/codegen-preview。

推荐把输出目录设为 target/codegen-preview，先审阅差异，再人工选择需要合并的文件。不要直接输出到 src/main/java 或 src/main/resources。

## 3. PowerShell 运行

在项目根目录设置当前会话变量：

    $env:CODEGEN_DB_URL = "jdbc:mysql://localhost:3306/permacore_iam?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    $env:CODEGEN_DB_USERNAME = "<代码生成专用用户>"
    $env:CODEGEN_DB_PASSWORD = "<数据库密码>"
    $env:CODEGEN_OUTPUT_DIR = (Join-Path (Get-Location) "target\codegen-preview")

运行：

    mvn -f .\codegen\pom.xml -DskipTests org.codehaus.mojo:exec-maven-plugin:3.1.0:java

完成后移除密码：

    Remove-Item Env:CODEGEN_DB_PASSWORD -ErrorAction SilentlyContinue

不要把密码写入 README、pom.xml、Java 常量或命令行 -D 参数；命令行参数可能被进程列表和终端历史记录。

## 4. Bash 运行

    export CODEGEN_DB_URL='jdbc:mysql://localhost:3306/permacore_iam?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
    export CODEGEN_DB_USERNAME='<代码生成专用用户>'
    export CODEGEN_DB_PASSWORD='<数据库密码>'
    export CODEGEN_OUTPUT_DIR="$PWD/target/codegen-preview"
    mvn -f ./codegen/pom.xml -DskipTests org.codehaus.mojo:exec-maven-plugin:3.1.0:java
    unset CODEGEN_DB_PASSWORD

## 5. 数据库权限

生成器只需要读取元数据和目标表。推荐创建专用账户，仅授予 permacore_iam 的 SELECT 与元数据读取能力；不要使用生产 root 账户。

禁止让生成器：

- CREATE、ALTER 或 DROP 业务表。
- 自动创建“最小元数据表”。
- 连接包含真实个人数据的生产库。
- 把凭据打印到日志。

最安全的流程是在一次性 MySQL 容器或隔离开发库中执行 canonical SQL，再运行生成器。

## 6. 审阅与合并

生成结束后：

1. 确认输出只位于 target/codegen-preview。
2. 对照 schema.sql 检查字段、主键、逻辑删除和时间列。
3. 与现有 src/main/java、src/main/resources/mapper 做目录差异比较。
4. 不要覆盖包含业务逻辑的 Service、Controller 或手写 Mapper。
5. 只复制明确需要的文件。
6. 删除预览目录后运行完整验证。

验证：

    mvn clean verify

如果合并了前端相关类型，再执行：

    cd permacore-ui
    npm ci
    npm run build

## 7. 模块边界

- 根 pom.xml 负责可执行后端。
- codegen/pom.xml 只负责生成工具。
- 根项目不提供 -Pcodegen profile。
- 不使用 mvn -pl codegen；根项目不是包含 codegen 的聚合 POM。
- src/main/java 下不应保留同名占位 CodeGeneratorMain，根 POM 也不应保留指向占位类的 exec 配置。

只保留本文这一种、从项目根目录执行的调用方式，避免 user.dir 不同导致输出位置漂移。
