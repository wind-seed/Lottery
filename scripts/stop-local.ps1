param(
    [int]$Port = 8081
)

$ErrorActionPreference = "Stop"
$Connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (!$Connections) {
    Write-Output "No process is listening on port $Port."
    exit 0
}

$Stopped = 0
$ProcessIds = $Connections | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($ProcessId in $ProcessIds) {
    $Command = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue
    if ($Command -and ($Command.CommandLine -like "*Lottery.war*" -or $Command.CommandLine -like "*lottery-interfaces*")) {
        Stop-Process -Id $ProcessId -Force
        Wait-Process -Id $ProcessId -Timeout 10 -ErrorAction SilentlyContinue
        Write-Output "Stopped Lottery process PID=$ProcessId on port $Port."
        $Stopped++
    } else {
        Write-Output "Port $Port is used by PID=$ProcessId, but it does not look like Lottery. Left it running."
    }
}

if ($Stopped -eq 0) {
    Write-Output "No Lottery process was stopped."
}
