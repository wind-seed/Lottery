param(
    [string]$MavenHome = "D:\down_app\apache-maven-3.9.16-bin\apache-maven-3.9.16",
    [string]$JavaHome = "C:\Users\hi\.jdks\corretto-1.8.0_492",
    [int]$Port = 8081,
    [switch]$SkipBuild,
    [switch]$StopExisting
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Maven = Join-Path $MavenHome "bin\mvn.cmd"
$Java = Join-Path $JavaHome "bin\java.exe"
$War = Join-Path $Root "lottery-interfaces\target\Lottery.war"
$LogStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutLog = Join-Path $Root "run-lottery-ui-$LogStamp.log"
$ErrLog = Join-Path $Root "run-lottery-ui-$LogStamp.err.log"

if (!(Test-Path $Maven)) {
    throw "Maven not found: $Maven"
}
if (!(Test-Path $Java)) {
    throw "Java 8 not found: $Java"
}

if ($StopExisting) {
    & (Join-Path $PSScriptRoot "stop-local.ps1") -Port $Port
}

if (!$SkipBuild) {
    $env:JAVA_HOME = $JavaHome
    $env:Path = (Join-Path $JavaHome "bin") + ";" + (Join-Path $MavenHome "bin") + ";" + $env:Path
    Push-Location $Root
    try {
        & $Maven -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "Maven build failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

if (!(Test-Path $War)) {
    throw "WAR not found. Build first: $War"
}

$Arguments = @(
    "-jar",
    "`"$War`"",
    "--spring.profiles.active=local",
    "--server.port=$Port"
)

$Process = Start-Process `
    -FilePath $Java `
    -ArgumentList $Arguments `
    -WorkingDirectory $Root `
    -RedirectStandardOutput $OutLog `
    -RedirectStandardError $ErrLog `
    -WindowStyle Hidden `
    -PassThru

$HealthUrl = "http://localhost:$Port/api/lottery/health"
for ($i = 1; $i -le 40; $i++) {
    Start-Sleep -Seconds 1
    try {
        $Health = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 2
        if ($Health.success -eq $true) {
            Write-Output "Lottery started. PID=$($Process.Id)"
            Write-Output "UI: http://localhost:$Port/"
            Write-Output "Log: $OutLog"
            exit 0
        }
    } catch {
        if ($Process.HasExited) {
            throw "Lottery exited early. Check $OutLog and $ErrLog"
        }
    }
}

throw "Lottery did not become healthy in time. PID=$($Process.Id). Check $OutLog"
