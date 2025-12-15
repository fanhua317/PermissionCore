$headers = @{
    "Content-Type" = "application/json"
}
$body = @{
    username = "admin"
    password = "Admin@123456"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:54321/api/auth/login" -Method Post -Headers $headers -Body $body
    $token = $response.data.accessToken
    Write-Host "Login Success. Token: $token"

    $headers["Authorization"] = "Bearer $token"
    $userListResponse = Invoke-RestMethod -Uri "http://localhost:54321/api/user/page?pageNo=1&pageSize=10" -Method Get -Headers $headers
    Write-Host "User List Response:"
    $userListResponse | ConvertTo-Json -Depth 5
} catch {
    Write-Host "Error:"
    $_.Exception.Response
    $_.Exception.Message
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.ReadToEnd()
    }
}

