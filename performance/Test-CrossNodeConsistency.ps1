[CmdletBinding()]
param(
    [string]$NodeA = 'http://127.0.0.1:15432',
    [string]$NodeB = 'http://127.0.0.1:15434',
    [string]$Username = 'perf_login_0001',
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

$password = [Environment]::GetEnvironmentVariable('PERF_ADMIN_PASSWORD', 'Process')
if ([string]::IsNullOrWhiteSpace($password)) {
    throw 'PERF_ADMIN_PASSWORD is required in the process environment.'
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body,
        [string]$AccessToken
    )
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($AccessToken)) {
        $headers.Authorization = "Bearer $AccessToken"
    }
    $parameters = @{
        UseBasicParsing = $true
        Method = $Method
        Uri = $Url
        Headers = $headers
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = ($Body | ConvertTo-Json -Depth 5 -Compress)
    }
    try {
        $response = Invoke-WebRequest @parameters
        $parsed = $null
        if (-not [string]::IsNullOrWhiteSpace($response.Content)) {
            $parsed = $response.Content | ConvertFrom-Json
        }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = $parsed }
    } catch [System.Net.WebException] {
        $status = 0
        $content = $null
        if ($null -ne $_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            $stream = $_.Exception.Response.GetResponseStream()
            if ($null -ne $stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
            }
        }
        $parsed = $null
        if (-not [string]::IsNullOrWhiteSpace($content)) {
            try { $parsed = $content | ConvertFrom-Json } catch { $parsed = $null }
        }
        return [pscustomobject]@{ Status = $status; Body = $parsed }
    }
}

function Login {
    param([string]$BaseUrl)
    $response = Invoke-JsonRequest -Method Post -Url "$BaseUrl/api/auth/login" -Body @{
        username = $Username
        password = $password
    } -AccessToken $null
    if ($response.Status -ne 200 -or $null -eq $response.Body -or $response.Body.code -ne 200) {
        throw "Login failed on $BaseUrl (HTTP $($response.Status))."
    }
    return $response.Body.data.accessToken
}

function Assert-Status {
    param([object]$Response, [int]$Expected, [string]$Operation)
    if ($Response.Status -ne $Expected) {
        throw "$Operation expected HTTP $Expected but received $($Response.Status)."
    }
}

$firstToken = Login -BaseUrl $NodeA
$crossNodeRead = Invoke-JsonRequest -Method Get -Url "$NodeB/api/auth/info" -Body $null -AccessToken $firstToken
Assert-Status -Response $crossNodeRead -Expected 200 -Operation 'Cross-node token read'

$rolesResponse = Invoke-JsonRequest -Method Get -Url "$NodeB/api/role/list" -Body $null -AccessToken $firstToken
$permissionsResponse = Invoke-JsonRequest -Method Get -Url "$NodeB/api/permission/list" -Body $null -AccessToken $firstToken
Assert-Status -Response $rolesResponse -Expected 200 -Operation 'Role list on node B'
Assert-Status -Response $permissionsResponse -Expected 200 -Operation 'Permission list on node B'
$roles = @($rolesResponse.Body.data | Where-Object { $_.roleKey -like 'PERF_ROLE_*' })
$permission = $permissionsResponse.Body.data | Where-Object { $_.permKey -like 'perf:generated:*' } | Select-Object -First 1
if ($roles.Count -lt 2 -or $null -eq $permission) {
    throw 'Consistency test requires deterministic performance roles and permissions.'
}

$mutation = Invoke-JsonRequest -Method Put -Url "$NodeB/api/role/$($roles[1].id)/permissions" -Body @{
    permissionIds = @([long]$permission.id)
} -AccessToken $firstToken
Assert-Status -Response $mutation -Expected 200 -Operation 'Authorization mutation on node B'
$revokedAfterMutation = Invoke-JsonRequest -Method Get -Url "$NodeA/api/auth/info" -Body $null -AccessToken $firstToken
Assert-Status -Response $revokedAfterMutation -Expected 401 -Operation 'Old token after node B mutation'

$beforeRemoteLogin = Login -BaseUrl $NodeA
$afterRemoteLogin = Login -BaseUrl $NodeB
$oldSession = Invoke-JsonRequest -Method Get -Url "$NodeA/api/auth/info" -Body $null -AccessToken $beforeRemoteLogin
$newSessionAcrossNodes = Invoke-JsonRequest -Method Get -Url "$NodeA/api/auth/info" -Body $null -AccessToken $afterRemoteLogin
Assert-Status -Response $oldSession -Expected 401 -Operation 'Old session after remote login'
Assert-Status -Response $newSessionAcrossNodes -Expected 200 -Operation 'New node B session used on node A'

$result = [ordered]@{
    schemaVersion = 1
    capturedAt = (Get-Date).ToString('o')
    crossNodeTokenRead = $crossNodeRead.Status
    mutationOnNodeB = $mutation.Status
    oldTokenAfterMutationOnNodeA = $revokedAfterMutation.Status
    oldTokenAfterRemoteLogin = $oldSession.Status
    newTokenAcrossNodes = $newSessionAcrossNodes.Status
    passed = $true
}
$json = $result | ConvertTo-Json -Depth 4
if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
    $directory = Split-Path -Parent $resolvedOutput
    if (-not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($resolvedOutput, $json + [Environment]::NewLine, $utf8WithoutBom)
}
Write-Output $json
