param(
    [string]$Mysql = "mysql.exe",
    [string]$HostName = "127.0.0.1",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = "123456",
    [switch]$Reset
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$SqlFiles = @(
    Join-Path $Root "doc\assets\sql\lottery.sql",
    Join-Path $Root "doc\assets\sql\lottery_01.sql",
    Join-Path $Root "doc\assets\sql\lottery_02.sql"
)

$MysqlCommand = Get-Command $Mysql -ErrorAction SilentlyContinue
if (!$MysqlCommand) {
    throw "mysql client not found. Install MySQL client or pass -Mysql C:\path\to\mysql.exe"
}

if (!$Reset) {
    Write-Output "Database scripts contain DROP TABLE statements."
    Write-Output "Run with -Reset only when you want to recreate lottery, lottery_01 and lottery_02."
    Write-Output "Example: .\scripts\init-db.ps1 -Reset -User root -Password 123456"
    exit 0
}

$ArgsBase = @(
    "--host=$HostName",
    "--port=$Port",
    "--user=$User",
    "--password=$Password",
    "--default-character-set=utf8mb4",
    "--force"
)

foreach ($File in $SqlFiles) {
    if (!(Test-Path $File)) {
        throw "SQL file not found: $File"
    }
    $SqlPath = (Resolve-Path $File).Path.Replace("\", "/")
    Write-Output "Importing $SqlPath"
    & $MysqlCommand.Source @ArgsBase "--execute=source $SqlPath"
    if ($LASTEXITCODE -ne 0) {
        throw "mysql import failed for $File"
    }
}

Write-Output "Database reset completed."
