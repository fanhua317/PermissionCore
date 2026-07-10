[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [string]$HostName = '127.0.0.1',
    [ValidateRange(1, 65535)]
    [int]$Port = 3306,
    [string]$User = 'root',
    [string]$Password = $env:DB_PASSWORD,
    [string]$MySqlCommand = 'mysql'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools\Invoke-PermaCoreMySqlScript.ps1')

$script = Join-Path $PSScriptRoot 'src\main\resources\db\init-permissions.sql'
if (-not $PSCmdlet.ShouldProcess(
    "$HostName`:$Port/permacore_iam",
    '执行内置权限初始化脚本并更新 RBAC 基线'
)) {
    return
}
$Password = Read-PermaCoreDatabasePassword -Password $Password -Prompt '请输入数据库密码'
try {
    Write-Host '执行 init-permissions.sql ...' -ForegroundColor Cyan
    Invoke-PermaCoreMySqlScript -ScriptPath $script -HostName $HostName -Port $Port `
        -User $User -Password $Password -MySqlCommand $MySqlCommand
    Write-Host '内置权限更新完成，旧 Token 已持久撤销。请让用户重新登录；若同时部署新版，再按发布计划重启后端实例。' -ForegroundColor Green
}
finally {
    $Password = $null
}
