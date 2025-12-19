@echo off
chcp 65001 >nul
echo ========================================
echo   PermaCore 权限快速更新工具
echo ========================================
echo.

echo 正在更新数据库权限...
echo.

REM 查找 MySQL 可能的安装路径
set MYSQL_EXE=
if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" (
    set MYSQL_EXE="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
)
if exist "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe" (
    set MYSQL_EXE="C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
)
if exist "C:\xampp\mysql\bin\mysql.exe" (
    set MYSQL_EXE="C:\xampp\mysql\bin\mysql.exe"
)

if "%MYSQL_EXE%"=="" (
    echo [错误] 未找到 MySQL 安装路径
    echo.
    echo 请手动执行以下命令:
    echo mysql -uroot -p123456 permacore_iam ^< src\main\resources\db\init-permissions.sql
    echo.
    pause
    exit /b 1
)

echo 找到 MySQL: %MYSQL_EXE%
echo.

%MYSQL_EXE% -uroot -p123456 permacore_iam < src\main\resources\db\init-permissions.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   权限更新成功！
    echo ========================================
    echo.
    echo 已添加完整的权限配置:
    echo   - 用户管理权限
    echo   - 角色管理权限
    echo   - 权限管理权限
    echo   - 部门管理权限
    echo   - 日志管理权限
    echo.
    echo [重要] 下一步操作:
    echo   1. 重启后端应用
    echo   2. 重新登录 admin 账户
    echo.
) else (
    echo.
    echo [错误] 权限更新失败
    echo.
    echo 请检查:
    echo   1. 数据库密码是否正确 (当前: 123456)
    echo   2. 数据库 permacore_iam 是否存在
    echo.
    echo 手动执行方法:
    echo   mysql -uroot -p123456 permacore_iam ^< src\main\resources\db\init-permissions.sql
    echo.
)

pause
