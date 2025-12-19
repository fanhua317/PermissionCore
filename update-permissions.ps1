# 更新数据库权限脚本
# 用于快速执行 init-permissions.sql 更新超级管理员权限

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PermaCore 权限更新工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 数据库连接配置（请根据实际情况修改）
$DB_HOST = "localhost"
$DB_PORT = "3306"
$DB_NAME = "permacore_iam"
$DB_USER = "root"

# 获取密码
$DB_PASSWORD = Read-Host "请输入数据库密码" -AsSecureString
$BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($DB_PASSWORD)
$DB_PASS_PLAIN = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)

Write-Host ""
Write-Host "正在执行权限更新..." -ForegroundColor Yellow

# 获取 SQL 文件路径
$SQL_FILE = Join-Path $PSScriptRoot "src\main\resources\db\init-permissions.sql"

if (-not (Test-Path $SQL_FILE)) {
    Write-Host "错误: 找不到 SQL 文件: $SQL_FILE" -ForegroundColor Red
    exit 1
}

# 执行 MySQL 命令
try {
    $mysqlCmd = "mysql"
    $mysqlArgs = @(
        "-h", $DB_HOST,
        "-P", $DB_PORT,
        "-u", $DB_USER,
        "-p$DB_PASS_PLAIN",
        $DB_NAME
    )
    
    Get-Content $SQL_FILE | & $mysqlCmd $mysqlArgs 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ 权限更新成功!" -ForegroundColor Green
        Write-Host ""
        Write-Host "已添加的权限包括:" -ForegroundColor Cyan
        Write-Host "  - 用户管理权限 (user:add, user:edit, user:delete, etc.)" -ForegroundColor Gray
        Write-Host "  - 角色管理权限 (role:add, role:edit, role:delete, etc.)" -ForegroundColor Gray
        Write-Host "  - 权限管理权限 (permission:add, permission:edit, etc.)" -ForegroundColor Gray
        Write-Host "  - 部门管理权限 (dept:add, dept:edit, dept:delete)" -ForegroundColor Gray
        Write-Host "  - 日志管理权限 (log:delete, etc.)" -ForegroundColor Gray
        Write-Host ""
        Write-Host "下一步操作:" -ForegroundColor Yellow
        Write-Host "  1. 如果后端正在运行，请重启应用" -ForegroundColor White
        Write-Host "  2. 或清除 Redis 缓存（如果使用 Redis）" -ForegroundColor White
        Write-Host "  3. 重新登录 admin 账户即可获得所有权限" -ForegroundColor White
        Write-Host ""
    } else {
        throw "MySQL 命令执行失败"
    }
} catch {
    Write-Host "✗ 权限更新失败: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "请检查:" -ForegroundColor Yellow
    Write-Host "  1. MySQL 是否已安装并在 PATH 中" -ForegroundColor White
    Write-Host "  2. 数据库连接信息是否正确" -ForegroundColor White
    Write-Host "  3. 数据库 '$DB_NAME' 是否存在" -ForegroundColor White
    Write-Host ""
    Write-Host "手动执行方法:" -ForegroundColor Cyan
    Write-Host "  mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p $DB_NAME < src\main\resources\db\init-permissions.sql" -ForegroundColor Gray
    exit 1
}
