function Read-PermaCoreDatabasePassword {
    [CmdletBinding()]
    param(
        [string]$Password,
        [string]$Prompt = 'Database password'
    )

    if (-not [string]::IsNullOrWhiteSpace($Password)) {
        return $Password
    }

    $securePassword = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $securePassword.Dispose()
    }
}

function Invoke-PermaCoreMySqlScript {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptPath,
        [string]$HostName = '127.0.0.1',
        [ValidateRange(1, 65535)]
        [int]$Port = 3306,
        [string]$User = 'root',
        [Parameter(Mandatory = $true)]
        [string]$Password,
        [string]$MySqlCommand = 'mysql'
    )

    if ($HostName -notmatch '^[A-Za-z0-9_.:-]+$') {
        throw 'HostName contains unsupported characters.'
    }
    if ($User -notmatch '^[A-Za-z0-9_.@-]+$') {
        throw 'User contains unsupported characters.'
    }
    if (-not (Test-Path -LiteralPath $ScriptPath -PathType Leaf)) {
        throw "SQL file not found: $ScriptPath"
    }

    $mysql = Get-Command $MySqlCommand -CommandType Application -ErrorAction Stop |
        Select-Object -First 1
    $sqlBytes = [IO.File]::ReadAllBytes($ScriptPath)
    $offset = 0
    if ($sqlBytes.Length -ge 3 -and
        $sqlBytes[0] -eq 0xEF -and $sqlBytes[1] -eq 0xBB -and $sqlBytes[2] -eq 0xBF) {
        $offset = 3
    }

    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = $mysql.Source
    $startInfo.Arguments = "--host=$HostName --port=$Port --user=$User --database=permacore_iam --default-character-set=utf8mb4 --binary-mode=1 --show-warnings"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.EnvironmentVariables['MYSQL_PWD'] = $Password

    $process = New-Object Diagnostics.Process
    $process.StartInfo = $startInfo
    $started = $false
    try {
        if (-not $process.Start()) {
            throw 'Unable to start the mysql client.'
        }
        $started = $true

        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.BaseStream.Write($sqlBytes, $offset, $sqlBytes.Length - $offset)
        $process.StandardInput.BaseStream.Flush()
        $process.StandardInput.Close()
        $process.WaitForExit()

        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw "MySQL failed (exit=$($process.ExitCode)):`n$stderr"
        }
        if (-not [string]::IsNullOrWhiteSpace($stdout)) {
            Write-Verbose $stdout.Trim()
        }
        if (-not [string]::IsNullOrWhiteSpace($stderr)) {
            Write-Warning $stderr.Trim()
        }
    }
    finally {
        if ($started -and -not $process.HasExited) {
            $process.Kill()
        }
        $process.Dispose()
        [void]$startInfo.EnvironmentVariables.Remove('MYSQL_PWD')
        $sqlBytes = $null
    }
}
