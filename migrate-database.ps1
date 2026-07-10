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

$script = Join-Path $PSScriptRoot 'src\main\resources\db\migrations\20260710_add_auth_version.sql'
Write-Warning '请确认已完成数据库备份，并且目标是需要升级的已有 permacore_iam 数据库。'
if (-not $PSCmdlet.ShouldProcess(
    "$HostName`:$Port/permacore_iam",
    '执行 20260710 数据库加固迁移（auth_version、外键与继承检查）'
)) {
    return
}
$Password = Read-PermaCoreDatabasePassword -Password $Password -Prompt '请输入数据库密码'
try {
    Write-Host '执行 20260710 数据库加固迁移 ...' -ForegroundColor Cyan
    Invoke-PermaCoreMySqlScript -ScriptPath $script -HostName $HostName -Port $Port `
        -User $User -Password $Password -MySqlCommand $MySqlCommand
    Write-Host '数据库结构迁移完成。下一步执行 update-permissions.ps1，再启动新版应用。' -ForegroundColor Green
}
finally {
    $Password = $null
}
