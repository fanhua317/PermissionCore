[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:54321',
    [string]$Username = 'admin',
    [string]$Password = $env:PERMACORE_TEST_PASSWORD
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw '请通过 -Password 或 PERMACORE_TEST_PASSWORD 提供测试密码。'
}
if ($Password.Length -lt 8 -or $Password.Length -gt 72) {
    throw '测试密码必须为 8-72 位。'
}

$BaseUrl = $BaseUrl.TrimEnd('/')
$body = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
    -ContentType 'application/json; charset=utf-8' -Body $body -TimeoutSec 15
if ($login.code -ne 200 -or [string]::IsNullOrWhiteSpace($login.data.accessToken)) {
    throw "登录失败: $($login.msg)"
}

$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
$users = Invoke-RestMethod -Uri "$BaseUrl/api/user/page?pageNo=1&pageSize=10" `
    -Method Get -Headers $headers -TimeoutSec 15
if ($users.code -ne 200) {
    throw "用户列表请求失败: $($users.msg)"
}

$recordCount = @($users.data.records).Count
Write-Host "登录与用户列表验证通过，返回用户数: $recordCount" -ForegroundColor Green
