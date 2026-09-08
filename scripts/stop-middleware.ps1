$ports = 8848, 9848, 9849, 2181, 9092, 7397, 9998
$pids = @()

foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    foreach ($connection in $connections) {
        if ($connection.OwningProcess -and $connection.OwningProcess -ne 0) {
            $pids += $connection.OwningProcess
        }
    }
}

$pids | Sort-Object -Unique | ForEach-Object {
    try {
        Stop-Process -Id $_ -Force -ErrorAction Stop
        Write-Output "Stopped process $_"
    } catch {
        Write-Output "Could not stop process ${_}: $($_.Exception.Message)"
    }
}
