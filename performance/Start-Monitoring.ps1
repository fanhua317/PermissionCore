[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectName,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,

    [Parameter(Mandatory = $true)]
    [string]$StopFile,

    [string]$ComposeFile,
    [string]$ManagementUrl = 'http://127.0.0.1:15433/actuator/prometheus',
    [ValidateRange(1, 300)]
    [int]$IntervalSeconds = 5,
    [ValidateRange(0, 86400)]
    [int]$MaximumDurationSeconds = 0
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

if ([string]::IsNullOrWhiteSpace($env:GOMAXPROCS)) { $env:GOMAXPROCS = '2' }
if ([string]::IsNullOrWhiteSpace($env:GOMEMLIMIT)) { $env:GOMEMLIMIT = '128MiB' }

if ([string]::IsNullOrWhiteSpace($ComposeFile)) {
    $ComposeFile = Join-Path (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)) 'docker-compose.perf.yml'
}
$ComposeFile = [System.IO.Path]::GetFullPath($ComposeFile)
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$StopFile = [System.IO.Path]::GetFullPath($StopFile)

if (-not (Test-Path -LiteralPath $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
}

function Append-CsvRows {
    param(
        [AllowEmptyCollection()]
        [object[]]$Rows,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )
    if ($null -eq $Rows -or $Rows.Count -eq 0) {
        return
    }
    if (Test-Path -LiteralPath $Path) {
        $Rows | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8 -Append
    } else {
        $Rows | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
    }
}

function Invoke-ComposeCapture {
    param([string[]]$Arguments)
    $allArguments = @('compose', '--project-name', $ProjectName, '-f', $ComposeFile) + $Arguments
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & docker @allArguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    if ($exitCode -ne 0) {
        throw "docker compose command failed with exit code $exitCode"
    }
    return @($output)
}

function Collect-Prometheus {
    param([datetime]$Timestamp)
    $response = Invoke-WebRequest -UseBasicParsing -Uri $ManagementUrl -TimeoutSec 4
    $rows = New-Object System.Collections.Generic.List[object]
    $metricPattern = '^(process_cpu_usage|jvm_memory_used_bytes|jvm_gc_pause_seconds_(count|sum|max)|jvm_threads_live_threads|tomcat_threads_busy_threads|tomcat_threads_current_threads|hikaricp_connections_(active|idle|pending|max|min)|hikaricp_connections_acquire_seconds_(count|sum|max)|http_server_requests_seconds_(count|sum|max))$'
    foreach ($line in ($response.Content -split "`n")) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        if ($trimmed -match '^([A-Za-z_:][A-Za-z0-9_:]*)(?:\{(.*)\})?\s+([^\s]+)$') {
            $metric = $Matches[1]
            $labels = $Matches[2]
            $rawValue = $Matches[3]
            if ($metric -notmatch $metricPattern) {
                continue
            }
            $value = 0.0
            if (-not [double]::TryParse(
                $rawValue,
                [System.Globalization.NumberStyles]::Float,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [ref]$value)) {
                continue
            }
            $rows.Add([pscustomobject]@{
                timestamp = $Timestamp.ToString('o')
                metric = $metric
                labels = $labels
                value = $value.ToString('R', [System.Globalization.CultureInfo]::InvariantCulture)
            })
        }
    }
    Append-CsvRows -Rows $rows.ToArray() -Path (Join-Path $OutputDirectory 'prometheus.csv')
}

function Collect-DockerStats {
    param([datetime]$Timestamp)
    $ids = @(& docker ps --filter "label=com.docker.compose.project=$ProjectName" --format '{{.ID}}' 2>$null)
    if ($LASTEXITCODE -ne 0 -or $ids.Count -eq 0) {
        return
    }
    $statsLines = @(& docker stats --no-stream --format '{{json .}}' @ids 2>$null)
    if ($LASTEXITCODE -ne 0) {
        return
    }
    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($line in $statsLines) {
        if ([string]::IsNullOrWhiteSpace([string]$line)) {
            continue
        }
        try {
            $item = $line | ConvertFrom-Json
            $rows.Add([pscustomobject]@{
                timestamp = $Timestamp.ToString('o')
                container = $item.Name
                cpu = $item.CPUPerc
                memory = $item.MemUsage
                memoryPercent = $item.MemPerc
                networkIO = $item.NetIO
                blockIO = $item.BlockIO
                pids = $item.PIDs
            })
        } catch {
            Write-Warning "Could not parse docker stats line: $line"
        }
    }
    Append-CsvRows -Rows $rows.ToArray() -Path (Join-Path $OutputDirectory 'docker.csv')
}

function Collect-MySqlStatus {
    param([datetime]$Timestamp)
    $query = "SHOW GLOBAL STATUS WHERE Variable_name IN ('Queries','Threads_connected','Threads_running','Com_select','Com_insert','Com_update','Com_delete','Innodb_row_lock_current_waits','Innodb_row_lock_time','Innodb_row_lock_waits','Innodb_deadlocks','Created_tmp_tables','Created_tmp_disk_tables','Innodb_buffer_pool_read_requests','Innodb_buffer_pool_reads','Slow_queries')"
    $queryBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($query))
    $shellCommand = 'echo ' + $queryBase64 + ' | base64 -d | MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --protocol=tcp --host=127.0.0.1 --user=root --database=$MYSQL_DATABASE --batch --skip-column-names'
    $lines = Invoke-ComposeCapture -Arguments @('exec', '-T', 'mysql', 'sh', '-lc', $shellCommand)
    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($line in $lines) {
        $parts = ([string]$line) -split "`t", 2
        if ($parts.Count -eq 2) {
            $rows.Add([pscustomobject]@{
                timestamp = $Timestamp.ToString('o')
                metric = $parts[0]
                value = $parts[1]
            })
        }
    }
    Append-CsvRows -Rows $rows.ToArray() -Path (Join-Path $OutputDirectory 'mysql.csv')
}

function Collect-RedisInfo {
    param([datetime]$Timestamp)
    $shellCommand = 'REDISCLI_AUTH=$REDIS_PASSWORD redis-cli --no-auth-warning INFO stats memory clients'
    $lines = Invoke-ComposeCapture -Arguments @('exec', '-T', 'redis', 'sh', '-lc', $shellCommand)
    $wanted = '^(instantaneous_ops_per_sec|total_commands_processed|rejected_connections|keyspace_hits|keyspace_misses|used_memory|used_memory_rss|connected_clients|blocked_clients):(.+)$'
    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($line in $lines) {
        if (([string]$line).Trim() -match $wanted) {
            $rows.Add([pscustomobject]@{
                timestamp = $Timestamp.ToString('o')
                metric = $Matches[1]
                value = $Matches[2].Trim()
            })
        }
    }
    Append-CsvRows -Rows $rows.ToArray() -Path (Join-Path $OutputDirectory 'redis.csv')
}

function Collect-MySqlDiagnostics {
    $query = @"
SELECT
  COALESCE(DIGEST, ''),
  REPLACE(REPLACE(COALESCE(DIGEST_TEXT, ''), CHAR(9), ' '), CHAR(10), ' '),
  COUNT_STAR,
  ROUND(SUM_TIMER_WAIT / 1000000000000, 6),
  ROUND(AVG_TIMER_WAIT / 1000000000, 3),
  SUM_ROWS_EXAMINED,
  SUM_ROWS_SENT,
  SUM_CREATED_TMP_DISK_TABLES,
  SUM_NO_INDEX_USED
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'permacore_iam'
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 20
"@.Trim()
    $queryBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($query))
    $shellCommand = 'echo ' + $queryBase64 + ' | base64 -d | MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --protocol=tcp --host=127.0.0.1 --user=root --database=$MYSQL_DATABASE --batch --skip-column-names'
    $lines = Invoke-ComposeCapture -Arguments @('exec', '-T', 'mysql', 'sh', '-lc', $shellCommand)
    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($line in $lines) {
        $parts = ([string]$line) -split "`t", 9
        if ($parts.Count -eq 9) {
            $rows.Add([pscustomobject]@{
                digest = $parts[0]
                sql = $parts[1]
                executions = $parts[2]
                totalSeconds = $parts[3]
                averageMilliseconds = $parts[4]
                rowsExamined = $parts[5]
                rowsSent = $parts[6]
                temporaryDiskTables = $parts[7]
                noIndexUsed = $parts[8]
            })
        }
    }
    if ($rows.Count -gt 0) {
        $rows.ToArray() | Export-Csv -LiteralPath (Join-Path $OutputDirectory 'mysql-top-statements.csv') -NoTypeInformation -Encoding UTF8
    }
}

$startedAt = Get-Date
while (-not (Test-Path -LiteralPath $StopFile)) {
    if ($MaximumDurationSeconds -gt 0 -and ((Get-Date) - $startedAt).TotalSeconds -ge $MaximumDurationSeconds) {
        break
    }
    $timestamp = Get-Date
    foreach ($collector in @('Prometheus', 'Docker', 'MySql', 'Redis')) {
        try {
            switch ($collector) {
                'Prometheus' { Collect-Prometheus -Timestamp $timestamp }
                'Docker' { Collect-DockerStats -Timestamp $timestamp }
                'MySql' { Collect-MySqlStatus -Timestamp $timestamp }
                'Redis' { Collect-RedisInfo -Timestamp $timestamp }
            }
        } catch {
            Write-Warning ("{0} collection failed: {1}" -f $collector, $_.Exception.Message)
        }
    }
    Start-Sleep -Seconds $IntervalSeconds
}

try {
    Collect-MySqlDiagnostics
} catch {
    Write-Warning ("MySql diagnostics collection failed: {0}" -f $_.Exception.Message)
}
