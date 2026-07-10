[CmdletBinding()]
param(
    [ValidateSet('Prepare', 'Smoke', 'Baseline', 'Login', 'Auth', 'Writes', 'Consistency', 'Soak', 'Spike', 'All', 'Down')]
    [string]$Action = 'Smoke',

    [ValidateSet('10k', '100k')]
    [string]$Scale = '10k',

    [string]$ProjectName = 'permacore-perf',
    [string]$K6Image = 'grafana/k6:2.0.0',
    [string]$AdminUsername = 'admin',
    [int[]]$Rates = @(50, 100, 200, 400, 800, 1200, 1600, 2400, 3200),
    [int[]]$LoginVUs = @(1, 5, 10, 20, 40),
    [ValidateRange(1, 10)]
    [int]$Repeats = 3,
    [ValidateRange(1, 3600)]
    [int]$WarmupSeconds = 30,
    [ValidateRange(1, 7200)]
    [int]$SampleSeconds = 120,
    [ValidateRange(1, 7200)]
    [int]$LoginSeconds = 120,
    [ValidateRange(1, 1440)]
    [int]$SoakMinutes = 30,
    [ValidateRange(1, 100000)]
    [int]$HealthyRps = 50,
    [ValidateRange(1, 60)]
    [int]$MonitoringIntervalSeconds = 5,
    [switch]$SkipBuild,
    [switch]$KeepEnvironment
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

# Docker CLI is a Go binary. Bound each short-lived collector/orchestration
# process so host-side tooling cannot exhaust Windows commit memory during a
# long matrix; these variables are not forwarded into the application or k6.
if ([string]::IsNullOrWhiteSpace($env:GOMAXPROCS)) { $env:GOMAXPROCS = '2' }
if ([string]::IsNullOrWhiteSpace($env:GOMEMLIMIT)) { $env:GOMEMLIMIT = '128MiB' }
if ([string]::IsNullOrWhiteSpace($env:COMPOSE_PARALLEL_LIMIT)) { $env:COMPOSE_PARALLEL_LIMIT = '2' }

$performanceRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $performanceRoot
$composeFile = Join-Path $repoRoot 'docker-compose.perf.yml'
$collectorScript = Join-Path $performanceRoot 'Start-Monitoring.ps1'
$generatorScript = Join-Path $performanceRoot 'Generate-SeedSql.ps1'
$environmentScript = Join-Path $performanceRoot 'Export-Environment.ps1'
$consistencyScript = Join-Path $performanceRoot 'Test-CrossNodeConsistency.ps1'
$runId = (Get-Date).ToString('yyyyMMdd-HHmmss') + '-' + $Scale
$runDirectory = Join-Path $performanceRoot (Join-Path 'results' $runId)
$summaryDirectory = Join-Path $runDirectory 'summary'
$rawDirectory = Join-Path $runDirectory 'raw'
$summaryCsv = Join-Path $runDirectory 'summary.csv'

function Write-Step {
    param([string]$Message)
    Write-Host ("[performance] {0}" -f $Message) -ForegroundColor Cyan
}

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command was not found: $Name"
    }
}

function Assert-Secrets {
    $required = @(
        'PERF_MYSQL_ROOT_PASSWORD',
        'PERF_MYSQL_APP_PASSWORD',
        'PERF_REDIS_PASSWORD',
        'PERF_JWT_SECRET',
        'PERF_ADMIN_PASSWORD'
    )
    $missing = @()
    foreach ($name in $required) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ([string]::IsNullOrWhiteSpace($value)) {
            $missing += $name
        }
    }
    if ($missing.Count -gt 0) {
        throw ('Missing required process environment variables: ' + ($missing -join ', '))
    }
}

function Invoke-Docker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [int[]]$AllowedExitCodes = @(0)
    )
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& docker @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    foreach ($line in $output) {
        Write-Host $line
    }
    if ($AllowedExitCodes -notcontains $exitCode) {
        throw "docker command failed with exit code $exitCode"
    }
    return $exitCode
}

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [int[]]$AllowedExitCodes = @(0)
    )
    $dockerArguments = @('compose', '--project-name', $ProjectName, '-f', $composeFile) + $Arguments
    return Invoke-Docker -Arguments $dockerArguments -AllowedExitCodes $AllowedExitCodes
}

function Wait-HttpReady {
    param([string]$Url, [int]$TimeoutSeconds = 240)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 4
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Url"
}

function Start-Environment {
    Assert-Command -Name 'docker'
    Assert-Secrets
    if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
        throw "Performance compose file not found: $composeFile"
    }
    Invoke-Docker -Arguments @('info', '--format', '{{.ServerVersion}}') | Out-Null
    $upArguments = @('up', '-d')
    if (-not $SkipBuild) {
        $upArguments += '--build'
    }
    Invoke-Compose -Arguments $upArguments | Out-Null
    Wait-HttpReady -Url 'http://127.0.0.1:15432/api/health'
    Wait-HttpReady -Url 'http://127.0.0.1:15433/actuator/prometheus'
}

function Seed-Database {
    Write-Step "Generating deterministic $Scale seed"
    $seedPath = & $generatorScript -Scale $Scale -AdminUsername $AdminUsername
    if (-not (Test-Path -LiteralPath $seedPath -PathType Leaf)) {
        throw "Generated seed was not found: $seedPath"
    }
    Push-Location $repoRoot
    try {
        $relativeSeed = "performance/raw/seed-$Scale.sql"
        Invoke-Compose -Arguments @('cp', $relativeSeed, 'mysql:/tmp/perf-seed.sql') | Out-Null
        $shellCommand = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=tcp --host=127.0.0.1 --user=root --database="$MYSQL_DATABASE" --show-warnings < /tmp/perf-seed.sql'
        Invoke-Compose -Arguments @('exec', '-T', 'mysql', 'sh', '-lc', $shellCommand) | Out-Null
    } finally {
        Pop-Location
    }
}

function Append-SummaryCsv {
    param([object]$Summary)
    $row = [pscustomobject]@{
        runId = $Summary.runId
        scenario = $Summary.scenario
        scale = $Summary.scale
        targetRps = $Summary.targetRps
        requestCount = $Summary.metrics.requestCount
        requestRate = $Summary.metrics.requestRate
        iterationCount = $Summary.metrics.iterationCount
        iterationRate = $Summary.metrics.iterationRate
        droppedIterations = $Summary.metrics.droppedIterations
        errorRate = $Summary.metrics.errorRate
        checkRate = $Summary.metrics.checkRate
        p50Ms = $Summary.metrics.p50Ms
        p90Ms = $Summary.metrics.p90Ms
        p95Ms = $Summary.metrics.p95Ms
        p99Ms = $Summary.metrics.p99Ms
        avgMs = $Summary.metrics.avgMs
        maxMs = $Summary.metrics.maxMs
        receivedBytes = $Summary.metrics.receivedBytes
        sentBytes = $Summary.metrics.sentBytes
    }
    if (Test-Path -LiteralPath $summaryCsv) {
        $row | Export-Csv -LiteralPath $summaryCsv -NoTypeInformation -Encoding UTF8 -Append
    } else {
        $row | Export-Csv -LiteralPath $summaryCsv -NoTypeInformation -Encoding UTF8
    }
}

function Reset-MetricsState {
    $sql = 'TRUNCATE TABLE performance_schema.events_statements_summary_by_digest; FLUSH STATUS;'
    $sqlBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($sql))
    $mysqlCommand = 'echo ' + $sqlBase64 + ' | base64 -d | MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --protocol=tcp --host=127.0.0.1 --user=root --batch --skip-column-names'
    Invoke-Compose -Arguments @('exec', '-T', 'mysql', 'sh', '-lc', $mysqlCommand) | Out-Null
    $redisCommand = 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli --no-auth-warning CONFIG RESETSTAT'
    Invoke-Compose -Arguments @('exec', '-T', 'redis', 'sh', '-lc', $redisCommand) | Out-Null
}

function Invoke-K6Run {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Script,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [hashtable]$Environment = @{}
    )
    $summaryHostPath = Join-Path $summaryDirectory ($Name + '.json')
    $monitorDirectory = Join-Path $rawDirectory $Name
    $stopFile = Join-Path $monitorDirectory 'collector.stop'
    New-Item -ItemType Directory -Path $summaryDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $monitorDirectory -Force | Out-Null
    if (Test-Path -LiteralPath $stopFile) {
        Remove-Item -LiteralPath $stopFile -Force
    }

    Reset-MetricsState

    $collectorJob = Start-Job -ScriptBlock {
        param($ScriptPath, $Project, $Output, $Stop, $Compose, $Interval)
        & $ScriptPath -ProjectName $Project -OutputDirectory $Output -StopFile $Stop -ComposeFile $Compose -IntervalSeconds $Interval
    } -ArgumentList $collectorScript, $ProjectName, $monitorDirectory, $stopFile, $composeFile, $MonitoringIntervalSeconds

    $dockerArguments = @(
        'run', '--rm',
        '--add-host', 'host.docker.internal:host-gateway',
        '--env', 'PERF_ADMIN_PASSWORD',
        '--env', "PERF_ADMIN_USERNAME=$AdminUsername",
        '--env', 'PERF_TEST_USERNAME=perf_login_0001',
        '--env', 'BASE_URL=http://host.docker.internal:15432',
        '--env', "PERF_SCALE=$Scale",
        '--env', "RUN_ID=$Name",
        '--env', "SCENARIO_NAME=$($Script.Replace('.js', ''))",
        '--env', "SUMMARY_PATH=/results/summary/$Name.json",
        '--mount', "type=bind,source=$($performanceRoot.Replace('\', '/'))/k6,target=/scripts,readonly",
        '--mount', "type=bind,source=$($runDirectory.Replace('\', '/')),target=/results",
        $K6Image
    )
    foreach ($key in ($Environment.Keys | Sort-Object)) {
        $dockerArguments += @('--env', ("{0}={1}" -f $key, $Environment[$key]))
    }
    $dockerArguments += @(
        'run', '--quiet',
        '--summary-trend-stats', 'avg,min,med,max,p(90),p(95),p(99)',
        ("/scripts/{0}" -f $Script)
    )

    Write-Step "Running $Name"
    try {
        Invoke-Docker -Arguments $dockerArguments -AllowedExitCodes @(0, 99) | Out-Null
    } finally {
        $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($stopFile, 'stop', $utf8WithoutBom)
        Wait-Job -Job $collectorJob -Timeout ($MonitoringIntervalSeconds + 10) | Out-Null
        Receive-Job -Job $collectorJob 2>&1 | ForEach-Object { Write-Host $_ }
        Remove-Job -Job $collectorJob -Force
    }

    if (-not (Test-Path -LiteralPath $summaryHostPath -PathType Leaf)) {
        throw "k6 summary was not created: $summaryHostPath"
    }
    $summary = Get-Content -LiteralPath $summaryHostPath -Raw | ConvertFrom-Json
    Append-SummaryCsv -Summary $summary
    return $summary
}

function Get-Median {
    param([double[]]$Values)
    if ($Values.Count -eq 0) { return [double]::NaN }
    $sorted = @($Values | Sort-Object)
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}

function Test-ConsecutiveAbove {
    param([double[]]$Values, [double]$Limit, [int]$RequiredCount = 3)
    $consecutive = 0
    foreach ($value in $Values) {
        if ($value -gt $Limit) {
            $consecutive++
            if ($consecutive -ge $RequiredCount) { return $true }
        } else {
            $consecutive = 0
        }
    }
    return $false
}

function Test-ResourceHealth {
    param([string[]]$Names)
    $cpu = New-Object System.Collections.Generic.List[double]
    $pending = New-Object System.Collections.Generic.List[double]
    foreach ($name in $Names) {
        $path = Join-Path (Join-Path $rawDirectory $name) 'prometheus.csv'
        if (-not (Test-Path -LiteralPath $path)) { continue }
        foreach ($row in (Import-Csv -LiteralPath $path)) {
            $value = 0.0
            if (-not [double]::TryParse($row.value, [ref]$value)) { continue }
            if ($row.metric -eq 'process_cpu_usage') {
                $cpu.Add($value)
            } elseif ($row.metric -eq 'hikaricp_connections_pending') {
                $pending.Add($value)
            }
        }
    }
    if ($cpu.Count -eq 0 -or $pending.Count -eq 0) { return $false }
    return (-not (Test-ConsecutiveAbove -Values $cpu.ToArray() -Limit 0.85)) -and
           (-not (Test-ConsecutiveAbove -Values $pending.ToArray() -Limit 0.0))
}

function Invoke-ReadLadder {
    $ladderPath = Join-Path $runDirectory 'ladder.csv'
    $consecutiveUnhealthy = 0
    $lastHealthy = 0
    foreach ($rate in $Rates) {
        Invoke-K6Run -Script 'read-mix.js' -Name ("read-{0}-warmup" -f $rate) -Environment @{
            RATE = $rate; DURATION = "${WarmupSeconds}s"
        } | Out-Null
        $summaries = @()
        $sampleNames = @()
        for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
            $sampleName = "read-$rate-r$repeat"
            $sampleNames += $sampleName
            $summaries += Invoke-K6Run -Script 'read-mix.js' -Name $sampleName -Environment @{
                RATE = $rate; DURATION = "${SampleSeconds}s"
            }
        }
        $p95 = Get-Median -Values @($summaries | ForEach-Object { [double]$_.metrics.p95Ms })
        $p99 = Get-Median -Values @($summaries | ForEach-Object { [double]$_.metrics.p99Ms })
        $errors = Get-Median -Values @($summaries | ForEach-Object { [double]$_.metrics.errorRate })
        $checks = Get-Median -Values @($summaries | ForEach-Object { [double]$_.metrics.checkRate })
        $actualRps = Get-Median -Values @($summaries | ForEach-Object { [double]$_.metrics.requestRate })
        $dropped = Get-Median -Values @($summaries | ForEach-Object { [double]$_.metrics.droppedIterations })
        $resourceHealthy = Test-ResourceHealth -Names $sampleNames
        $healthy = $errors -lt 0.01 -and $checks -gt 0.99 -and $p95 -le 200 -and $p99 -le 500 -and $resourceHealthy
        $row = [pscustomobject]@{
            targetRps = $rate; actualRpsMedian = $actualRps
            p95MsMedian = $p95; p99MsMedian = $p99
            errorRateMedian = $errors; checkRateMedian = $checks; droppedIterationsMedian = $dropped
            resourceHealthy = $resourceHealthy; healthy = $healthy; repeats = $Repeats
        }
        if (Test-Path -LiteralPath $ladderPath) {
            $row | Export-Csv -LiteralPath $ladderPath -NoTypeInformation -Encoding UTF8 -Append
        } else {
            $row | Export-Csv -LiteralPath $ladderPath -NoTypeInformation -Encoding UTF8
        }
        if ($healthy) {
            $lastHealthy = $rate; $consecutiveUnhealthy = 0
        } else {
            $consecutiveUnhealthy++
            if ($consecutiveUnhealthy -ge 2) {
                Write-Step 'Stopping ladder after two consecutive unhealthy tiers'
                break
            }
        }
    }
    return $lastHealthy
}

function Invoke-LoginMatrix {
    foreach ($vus in $LoginVUs) {
        for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
            Invoke-K6Run -Script 'login.js' -Name ("login-{0}-r{1}" -f $vus, $repeat) -Environment @{
                VUS = $vus; DURATION = "${LoginSeconds}s"
            } | Out-Null
        }
    }
}

function Invoke-AuthMatrix {
    foreach ($vus in $LoginVUs) {
        for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
            Invoke-K6Run -Script 'auth-flow.js' -Name ("auth-{0}-r{1}" -f $vus, $repeat) -Environment @{
                VUS = $vus; DURATION = "${LoginSeconds}s"
            } | Out-Null
        }
    }
}

function Invoke-ConsistencyTest {
    Invoke-Compose -Arguments @('--profile', 'consistency', 'up', '-d', 'backend-b') | Out-Null
    Wait-HttpReady -Url 'http://127.0.0.1:15434/api/health'
    & $consistencyScript -OutputPath (Join-Path $runDirectory 'consistency.json') | ForEach-Object { Write-Host $_ }
}

if ($Action -eq 'Down') {
    Assert-Command -Name 'docker'
    foreach ($name in @('PERF_MYSQL_ROOT_PASSWORD', 'PERF_MYSQL_APP_PASSWORD', 'PERF_REDIS_PASSWORD', 'PERF_JWT_SECRET', 'PERF_ADMIN_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            [Environment]::SetEnvironmentVariable($name, 'unused-for-down', 'Process')
        }
    }
    Invoke-Compose -Arguments @('--profile', 'consistency', 'down', '--volumes', '--remove-orphans') | Out-Null
    Write-Step 'Isolated performance environment removed'
    return
}

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$cleanup = ($Action -ne 'Prepare' -and -not $KeepEnvironment)

try {
    Start-Environment
    Seed-Database
    & $environmentScript -OutputPath (Join-Path $runDirectory 'environment.json') -Scale $Scale -ProjectName $ProjectName -K6Image $K6Image | Out-Null
    switch ($Action) {
        'Prepare' { Write-Step 'Environment is ready and intentionally left running' }
        'Smoke' {
            Invoke-K6Run -Script 'read-mix.js' -Name 'smoke-read' -Environment @{ RATE = 10; DURATION = '10s' } | Out-Null
            Invoke-K6Run -Script 'auth-flow.js' -Name 'smoke-auth' -Environment @{ VUS = 2; DURATION = '10s' } | Out-Null
        }
        'Baseline' {
            $measuredHealthyRps = Invoke-ReadLadder
            Write-Step "Highest healthy sustained tier: $measuredHealthyRps RPS"
        }
        'Login' { Invoke-LoginMatrix }
        'Auth' { Invoke-AuthMatrix }
        'Writes' {
            Invoke-K6Run -Script 'write-contention.js' -Name 'write-contention' -Environment @{ VUS = 20; DURATION = "${SampleSeconds}s" } | Out-Null
            Invoke-K6Run -Script 'global-permission.js' -Name 'global-permission' -Environment @{ ITERATIONS = 10 } | Out-Null
        }
        'Consistency' { Invoke-ConsistencyTest }
        'Soak' { Invoke-K6Run -Script 'soak.js' -Name 'soak' -Environment @{ HEALTHY_RPS = $HealthyRps; DURATION = "${SoakMinutes}m" } | Out-Null }
        'Spike' { Invoke-K6Run -Script 'spike.js' -Name 'spike' -Environment @{ HEALTHY_RPS = $HealthyRps } | Out-Null }
        'All' {
            $measuredHealthyRps = Invoke-ReadLadder
            if ($measuredHealthyRps -le 0) { throw 'No healthy RPS tier was found; soak and spike tests were not started.' }
            Invoke-LoginMatrix
            Invoke-AuthMatrix
            Invoke-K6Run -Script 'write-contention.js' -Name 'write-contention' -Environment @{ VUS = 20; DURATION = "${SampleSeconds}s" } | Out-Null
            Invoke-K6Run -Script 'global-permission.js' -Name 'global-permission' -Environment @{ ITERATIONS = 10 } | Out-Null
            Invoke-ConsistencyTest
            Invoke-K6Run -Script 'soak.js' -Name 'soak' -Environment @{ HEALTHY_RPS = $measuredHealthyRps; DURATION = "${SoakMinutes}m" } | Out-Null
            Invoke-K6Run -Script 'spike.js' -Name 'spike' -Environment @{ HEALTHY_RPS = $measuredHealthyRps } | Out-Null
        }
    }
    Write-Step "Results: $runDirectory"
} finally {
    if ($cleanup) {
        Write-Step 'Removing isolated containers and volumes'
        try { Invoke-Compose -Arguments @('--profile', 'consistency', 'down', '--volumes', '--remove-orphans') | Out-Null }
        catch { Write-Warning "Performance cleanup failed: $($_.Exception.Message)" }
    }
}
