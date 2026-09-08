param(
    [string]$JavaHome = "C:\Users\hi\.jdks\corretto-1.8.0_492",
    [string]$MavenHome = "D:\down_app\apache-maven-3.9.16-bin\apache-maven-3.9.16",
    [string]$Mysql = "D:\mysql-8.0.46-winx64\mysql-8.0.46-winx64\bin\mysql.exe",
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = "123456"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Middleware = Join-Path $Root ".local-middleware"
$LogDir = Join-Path $Root "data\middleware-logs"
$DataDir = Join-Path $Middleware "data"
$Maven = Join-Path $MavenHome "bin\mvn.cmd"
$Java = Join-Path $JavaHome "bin\java.exe"

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

if (!(Test-Path $Java)) { throw "Java 8 not found: $Java" }
if (!(Test-Path $Maven)) { throw "Maven not found: $Maven" }

$env:JAVA_HOME = $JavaHome
$env:Path = (Join-Path $JavaHome "bin") + ";" + (Join-Path $MavenHome "bin") + ";" + $env:Path

function Wait-Port {
    param([int]$Port, [string]$Name, [int]$TimeoutSeconds = 90)
    for ($i = 1; $i -le $TimeoutSeconds; $i++) {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($conn) {
            Write-Output "$Name started on port $Port."
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "$Name did not start on port $Port in $TimeoutSeconds seconds."
}

function Start-ProcessIfPortFree {
    param(
        [int]$Port,
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [string]$OutLog,
        [string]$ErrLog
    )
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        Write-Output "$Name already listening on port $Port."
        return
    }
    Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $OutLog `
        -RedirectStandardError $ErrLog `
        -WindowStyle Hidden | Out-Null
    Wait-Port -Port $Port -Name $Name
}

$NacosHome = Join-Path $Middleware "nacos-2.0.3"
Start-ProcessIfPortFree `
    -Port 8848 `
    -Name "Nacos" `
    -FilePath (Join-Path $NacosHome "bin\startup.cmd") `
    -ArgumentList @("-m", "standalone") `
    -WorkingDirectory $NacosHome `
    -OutLog (Join-Path $LogDir "nacos.out.log") `
    -ErrLog (Join-Path $LogDir "nacos.err.log")

$KafkaHome = Join-Path $Middleware "kafka_2.13-2.8.0"
$KafkaClasspath = (Join-Path $KafkaHome "libs\*")
Start-ProcessIfPortFree `
    -Port 2181 `
    -Name "ZooKeeper" `
    -FilePath $Java `
    -ArgumentList @("-cp", "`"$KafkaClasspath`"", "org.apache.zookeeper.server.quorum.QuorumPeerMain", "`"$(Join-Path $KafkaHome "config\zookeeper.properties")`"") `
    -WorkingDirectory $KafkaHome `
    -OutLog (Join-Path $LogDir "zookeeper.out.log") `
    -ErrLog (Join-Path $LogDir "zookeeper.err.log")

Start-ProcessIfPortFree `
    -Port 9092 `
    -Name "Kafka" `
    -FilePath $Java `
    -ArgumentList @("-cp", "`"$KafkaClasspath`"", "kafka.Kafka", "`"$(Join-Path $KafkaHome "config\server.properties")`"") `
    -WorkingDirectory $KafkaHome `
    -OutLog (Join-Path $LogDir "kafka.out.log") `
    -ErrLog (Join-Path $LogDir "kafka.err.log")

& $Java -cp $KafkaClasspath kafka.admin.TopicCommand --bootstrap-server 127.0.0.1:9092 --create --if-not-exists --replication-factor 1 --partitions 3 --topic lottery_invoice | Out-Host
& $Java -cp $KafkaClasspath kafka.admin.TopicCommand --bootstrap-server 127.0.0.1:9092 --create --if-not-exists --replication-factor 1 --partitions 3 --topic lottery_activity_partake | Out-Host

if (Test-Path $Mysql) {
    $Sql = Join-Path $Middleware "xxl-job-2.3.0\doc\db\tables_xxl_job.sql"
    & $Mysql "-u$MysqlUser" "-p$MysqlPassword" -e "CREATE DATABASE IF NOT EXISTS xxl_job DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    cmd /c "`"$Mysql`" -u$MysqlUser -p$MysqlPassword xxl_job < `"$Sql`""
} else {
    Write-Output "MySQL client not found, skip xxl_job database initialization: $Mysql"
}

$XxlRoot = Join-Path $Middleware "xxl-job-2.3.0"
$XxlJar = Join-Path $XxlRoot "xxl-job-admin\target\xxl-job-admin-2.3.0.jar"
if (!(Test-Path $XxlJar)) {
    Push-Location $XxlRoot
    try {
        & $Maven -pl xxl-job-admin -am -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "XXL-Job Admin build failed." }
    } finally {
        Pop-Location
    }
}

Start-ProcessIfPortFree `
    -Port 7397 `
    -Name "XXL-Job Admin" `
    -FilePath $Java `
    -ArgumentList @("-jar", "`"$XxlJar`"") `
    -WorkingDirectory $XxlRoot `
    -OutLog (Join-Path $LogDir "xxl-job-admin.out.log") `
    -ErrLog (Join-Path $LogDir "xxl-job-admin.err.log")

Write-Output "Middleware ready."
Write-Output "Nacos: http://localhost:8848/nacos"
Write-Output "XXL-Job Admin: http://localhost:7397/xxl-job-admin"
Write-Output "Kafka: 127.0.0.1:9092"
