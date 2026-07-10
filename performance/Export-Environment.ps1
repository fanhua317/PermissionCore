[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [Parameter(Mandatory = $true)]
    [ValidateSet('10k', '100k')]
    [string]$Scale,

    [string]$ProjectName = 'permacore-perf',
    [string]$K6Image = 'grafana/k6:2.0.0'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$performanceRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $performanceRoot
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$gitCommit = (& git -C $repoRoot rev-parse HEAD 2>$null | Select-Object -First 1)
$gitStatus = @(& git -C $repoRoot status --porcelain 2>$null)
$dockerVersion = (& docker version --format '{{.Server.Version}}' 2>$null | Select-Object -First 1)
$k6Version = (& docker run --rm $K6Image version 2>$null | Select-Object -First 1)
$containerImages = @()
$containerIds = @(& docker ps -a --filter "label=com.docker.compose.project=$ProjectName" --format '{{.ID}}' 2>$null)
foreach ($containerId in $containerIds) {
    if ([string]::IsNullOrWhiteSpace([string]$containerId)) {
        continue
    }
    $descriptor = (& docker inspect --format '{{json .Config.Labels}}|{{.Config.Image}}|{{.Image}}' $containerId 2>$null | Select-Object -First 1)
    $parts = ([string]$descriptor) -split '\|', 3
    if ($parts.Count -ne 3) {
        continue
    }
    $labels = $parts[0] | ConvertFrom-Json
    $repoDigestsJson = (& docker image inspect --format '{{json .RepoDigests}}' $parts[2] 2>$null | Select-Object -First 1)
    $repoDigests = @()
    if (-not [string]::IsNullOrWhiteSpace([string]$repoDigestsJson) -and $repoDigestsJson -ne 'null') {
        $parsedDigests = ConvertFrom-Json -InputObject ([string]$repoDigestsJson)
        $repoDigests = @($parsedDigests | ForEach-Object { [string]$_ })
    }
    $containerImages += [ordered]@{
        service = $labels.'com.docker.compose.service'
        configuredImage = $parts[1]
        imageId = $parts[2]
        repoDigests = $repoDigests
    }
}

$cpuName = $null
$logicalProcessors = [Environment]::ProcessorCount
$memoryBytes = $null
try {
    # Avoid Win32_CIM here: a degraded WMI provider can block an otherwise
    # healthy benchmark indefinitely. Both calls below are local and bounded.
    $cpuName = (Get-ItemProperty -LiteralPath 'HKLM:\HARDWARE\DESCRIPTION\System\CentralProcessor\0' `
            -ErrorAction Stop).ProcessorNameString.Trim()
    Add-Type -AssemblyName Microsoft.VisualBasic -ErrorAction Stop
    $computerInfo = New-Object Microsoft.VisualBasic.Devices.ComputerInfo
    $memoryBytes = [long]$computerInfo.TotalPhysicalMemory
} catch {
    Write-Warning "Could not read bounded host information: $($_.Exception.Message)"
}

$environment = [ordered]@{
    schemaVersion = 1
    capturedAt = (Get-Date).ToString('o')
    project = $ProjectName
    scale = $Scale
    git = [ordered]@{
        commit = [string]$gitCommit
        dirty = ($gitStatus.Count -gt 0)
    }
    host = [ordered]@{
        os = [System.Environment]::OSVersion.VersionString
        # Hostnames are irrelevant to capacity and may identify a developer's
        # workstation. Keep the hardware/OS facts while making summaries safe
        # to commit and share.
        machine = 'local-windows-host'
        cpu = $cpuName
        logicalProcessors = $logicalProcessors
        memoryBytes = $memoryBytes
        powershell = $PSVersionTable.PSVersion.ToString()
    }
    tools = [ordered]@{
        docker = [string]$dockerVersion
        k6Image = $K6Image
        k6Version = [string]$k6Version
    }
    images = $containerImages
    endpoints = [ordered]@{
        backend = 'http://127.0.0.1:15432'
        management = 'http://127.0.0.1:15433/actuator/prometheus'
    }
    resources = [ordered]@{
        backend = '4 vCPU, 2 GiB, JVM Xms/Xmx 1 GiB'
        mysql = '2 vCPU, 2 GiB, InnoDB buffer pool 1 GiB'
        redis = '1 vCPU, 512 MiB, maxmemory 384 MiB'
    }
    data = if ($Scale -eq '100k') {
        [ordered]@{ users = 100000; departments = 500; roles = 200; permissions = 1000; loginLogs = 200000; operationLogs = 200000; loginAccounts = 200 }
    } else {
        [ordered]@{ users = 10000; departments = 100; roles = 100; permissions = 300; loginLogs = 20000; operationLogs = 20000; loginAccounts = 200 }
    }
}

$json = $environment | ConvertTo-Json -Depth 6
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($resolvedOutput, $json + [Environment]::NewLine, $utf8WithoutBom)
Write-Output $resolvedOutput
