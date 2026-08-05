# Stop local backend & frontend services.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File stop-services.ps1

foreach ($port in @(16080, 3000)) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Output "[$port] no listener"
        continue
    }
    foreach ($c in $conns) {
        $p = $c.OwningProcess
        if ($p) {
            try {
                Stop-Process -Id $p -Force -ErrorAction Stop
                Write-Output "[$port] stopped PID=$p"
            } catch {
                Write-Output "[$port] stop failed: $($_.Exception.Message)"
            }
        }
    }
}
Write-Output "Done."
