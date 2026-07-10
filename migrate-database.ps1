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

$migrations = @(
    @{
        Name = '授权版本、全局授权状态与关系约束'
        Path = Join-Path $PSScriptRoot 'src\main\resources\db\migrations\20260710_add_auth_version.sql'
    },
    @{
        Name = '用户、日志与反向关联查询索引'
        Path = Join-Path $PSScriptRoot 'src\main\resources\db\migrations\20260710_optimize_user_queries.sql'
    }
)

Write-Warning '请确认已完成数据库备份，并且目标是需要升级的已有 permacore_iam 数据库。'
if (-not $PSCmdlet.ShouldProcess(
    "$HostName`:$Port/permacore_iam",
    '按固定顺序执行 20260710 数据库迁移（个人/全局授权版本、关系约束与查询索引）'
)) {
    return
}
$Password = Read-PermaCoreDatabasePassword -Password $Password -Prompt '请输入数据库密码'
try {
    foreach ($migration in $migrations) {
        Write-Host "执行迁移：$($migration.Name) ..." -ForegroundColor Cyan
        Invoke-PermaCoreMySqlScript -ScriptPath $migration.Path -HostName $HostName -Port $Port `
            -User $User -Password $Password -MySqlCommand $MySqlCommand
    }
    Write-Host '数据库结构迁移完成。下一步执行 update-permissions.ps1，再启动新版应用。' -ForegroundColor Green
}
finally {
    $Password = $null
}
