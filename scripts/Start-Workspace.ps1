param([switch]$Build, [string]$DataDirectory)
. (Join-Path $PSScriptRoot 'Use-ProjectEnvironment.ps1')
$root = $script:WorkspaceRoot
if ($DataDirectory) { $env:WORKSPACE_DATA_DIR = [IO.Path]::GetFullPath($DataDirectory).Replace('\', '/') }
$runtime = Join-Path $root '.runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$statePath = Join-Path $runtime 'processes.json'
if (Test-Path -LiteralPath $statePath) {
    throw 'A workspace session is recorded. Run scripts\Stop-Workspace.ps1 before starting again.'
}
$jar = Get-ChildItem -LiteralPath (Join-Path $root 'backend\build\libs') -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object Name -NotLike '*-plain.jar' | Select-Object -First 1
if ($Build -or -not $jar -or -not (Test-Path -LiteralPath (Join-Path $root 'frontend\node_modules\vite\bin\vite.js'))) {
    & (Join-Path $PSScriptRoot 'Build-Workspace.ps1')
    $jar = Get-ChildItem -LiteralPath (Join-Path $root 'backend\build\libs') -Filter '*.jar' | Where-Object Name -NotLike '*-plain.jar' | Select-Object -First 1
}
foreach ($port in @(8080, 5173)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "Port $port is already in use. Stop its existing service first." }
}
$started = @()
try {
    $backend = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') -ArgumentList @('-jar', ('"' + $jar.FullName + '"')) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'backend.log') -RedirectStandardError (Join-Path $runtime 'backend.error.log')
    $started += $backend
    $node = (Get-Command node.exe -ErrorAction Stop).Source
    $vite = Join-Path $root 'frontend\node_modules\vite\bin\vite.js'
    # Detached Vite must not shut down when this launcher's stdin closes.
    $frontend = Start-Process -FilePath $node -ArgumentList @(('"' + $vite + '"'), '--host', '127.0.0.1', '--port', '5173', '--strictPort') -Environment @{ CI = 'true' } -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'frontend.log') -RedirectStandardError (Join-Path $runtime 'frontend.error.log')
    $started += $frontend
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($backend.HasExited -or $frontend.HasExited) { throw 'A server exited. See .runtime logs.' }
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:5173/api/health' -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Workspace startup timed out. See .runtime logs.' }
    $started | ForEach-Object { @{ id = $_.Id; startTimeTicks = $_.StartTime.ToUniversalTime().Ticks.ToString() } } | ConvertTo-Json | Set-Content -LiteralPath $statePath
    Write-Output 'Workspace ready: http://127.0.0.1:5173'
    Write-Output 'Backend health: http://127.0.0.1:8080/api/health'
    Write-Output 'Logs: .runtime/ | Stop: .\scripts\Stop-Workspace.ps1'
} catch {
    foreach ($process in $started) { if (-not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue } }
    throw
}
