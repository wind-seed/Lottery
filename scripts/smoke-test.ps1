param(
    [string]$BaseUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"
$Failures = 0

function Test-Endpoint {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    try {
        $Result = & $Action
        Write-Output "[OK] $Name"
        return $Result
    } catch {
        $script:Failures++
        Write-Output "[FAIL] $Name - $($_.Exception.Message)"
        return $null
    }
}

Test-Endpoint "health" { Invoke-RestMethod -Uri "$BaseUrl/api/lottery/health" -TimeoutSec 5 } | Out-Null
Test-Endpoint "diagnostics" { Invoke-RestMethod -Uri "$BaseUrl/api/lottery/diagnostics" -TimeoutSec 10 } | Out-Null
Test-Endpoint "activities" { Invoke-RestMethod -Uri "$BaseUrl/api/lottery/activities?page=1&rows=5" -TimeoutSec 10 } | Out-Null
Test-Endpoint "strategies" { Invoke-RestMethod -Uri "$BaseUrl/api/lottery/strategies" -TimeoutSec 10 } | Out-Null
Test-Endpoint "awards" { Invoke-RestMethod -Uri "$BaseUrl/api/lottery/awards" -TimeoutSec 10 } | Out-Null
Test-Endpoint "rules" { Invoke-RestMethod -Uri "$BaseUrl/api/lottery/rules" -TimeoutSec 10 } | Out-Null
Test-Endpoint "rule decision" {
    Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/lottery/rules/decision" `
        -ContentType "application/json" `
        -Body '{"uId":"test_user","treeId":2110081902,"valMap":{"gender":"man","age":"25"}}' `
        -TimeoutSec 10
} | Out-Null
Test-Endpoint "home page" { Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 5 } | Out-Null

if ($Failures -gt 0) {
    throw "$Failures smoke test(s) failed."
}

Write-Output "All smoke tests passed."
