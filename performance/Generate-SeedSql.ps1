[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('10k', '100k')]
    [string]$Scale,

    [string]$AdminUsername = 'admin',

    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$performanceRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $performanceRoot ("raw\seed-{0}.sql" -f $Scale)
}

$resolvedRoot = [System.IO.Path]::GetFullPath($performanceRoot).TrimEnd('\') + '\'
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
if (-not $resolvedOutput.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputPath must stay inside the performance directory.'
}

$profiles = @{
    '10k' = @{
        Users = 10000
        Depts = 100
        Roles = 100
        Permissions = 300
        Logs = 20000
    }
    '100k' = @{
        Users = 100000
        Depts = 500
        Roles = 200
        Permissions = 1000
        Logs = 200000
    }
}

$profile = $profiles[$Scale]
$templatePath = Join-Path $performanceRoot 'seed\seed-template.sql'
if (-not (Test-Path -LiteralPath $templatePath -PathType Leaf)) {
    throw "Seed template not found: $templatePath"
}

$escapedAdminUsername = $AdminUsername.Replace("'", "''")
$sql = [System.IO.File]::ReadAllText($templatePath)
$sql = $sql.Replace('__USER_TARGET__', [string]$profile.Users)
$sql = $sql.Replace('__DEPT_TARGET__', [string]$profile.Depts)
$sql = $sql.Replace('__ROLE_TARGET__', [string]$profile.Roles)
$sql = $sql.Replace('__PERMISSION_TARGET__', [string]$profile.Permissions)
$sql = $sql.Replace('__LOG_TARGET__', [string]$profile.Logs)
$sql = $sql.Replace('__ADMIN_USERNAME__', $escapedAdminUsername)

$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($resolvedOutput, $sql, $utf8WithoutBom)

Write-Output $resolvedOutput
