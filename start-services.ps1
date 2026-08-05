# Start local backend & frontend services for manual acceptance review.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File start-services.ps1
# Stop:  powershell -NoProfile -ExecutionPolicy Bypass -File stop-services.ps1

$ErrorActionPreference = 'Continue'
$root = 'D:\Project\code-error-explainerdemo'
$logDir = Join-Path $root '.agent_context'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# Load .env (DB_PASSWORD / AI_API_KEY / AI_PROVIDER)
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') {
            $k = $Matches[1].Trim()
            $v = $Matches[2].Trim().Trim('"')
            Set-Item -Path ("Env:$k") -Value $v
        }
    }
    Write-Output "[env] loaded $envFile"
}

# ---------- Backend Spring Boot :16080 ----------
$backendLog = Join-Path $logDir 'backend.log'
$backendErr = Join-Path $logDir 'backend.err.log'
$jar = Join-Path $root 'target\code-error-explainerdemo-0.0.1-SNAPSHOT.jar'
$existing = Get-NetTCPConnection -LocalPort 16080 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Output "[backend] :16080 already listening (PID $($existing.OwningProcess)), skip start"
} else {
    $proc = Start-Process -FilePath 'java' `
        -ArgumentList @('-jar', $jar) `
        -WorkingDirectory $root `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError $backendErr `
        -WindowStyle Hidden -PassThru
    Set-Content -Path (Join-Path $logDir 'backend.pid') -Value $proc.Id
    Write-Output "[backend] started PID=$($proc.Id) log=$backendLog"
}

# ---------- Frontend Vite :3000 ----------
$frontLog = Join-Path $logDir 'frontend-dev.log'
$frontErr = Join-Path $logDir 'frontend-dev.err.log'
$existingF = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue
if ($existingF) {
    Write-Output "[frontend] :3000 already listening (PID $($existingF.OwningProcess)), skip start"
} else {
    $frontDir = Join-Path $root 'frontend'
    $procF = Start-Process -FilePath 'npm.cmd' `
        -ArgumentList @('run', 'dev') `
        -WorkingDirectory $frontDir `
        -RedirectStandardOutput $frontLog `
        -RedirectStandardError $frontErr `
        -WindowStyle Hidden -PassThru
    Set-Content -Path (Join-Path $logDir 'frontend.pid') -Value $procF.Id
    Write-Output "[frontend] started PID=$($procF.Id) log=$frontLog"
}

Write-Output "Done. PIDs saved to .agent_context/backend.pid and .agent_context/frontend.pid"
