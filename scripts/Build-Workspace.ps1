param([switch]$SkipInstall)
. (Join-Path $PSScriptRoot 'Use-ProjectEnvironment.ps1')
Push-Location $script:WorkspaceRoot
try {
    if (-not $SkipInstall) {
        & npm.cmd --prefix frontend ci
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    }
    & npm.cmd --prefix frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    & .\backend\gradlew.bat -p backend test bootJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Backend checks failed.' }
    Write-Output 'Frontend production build and backend tests/build passed.'
} finally { Pop-Location }
