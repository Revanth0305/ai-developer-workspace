$ErrorActionPreference = 'Stop'
$script:WorkspaceRoot = Split-Path -Parent $PSScriptRoot
$jdkCandidate = $env:JAVA_HOME
if (-not $jdkCandidate -or -not (Test-Path -LiteralPath (Join-Path $jdkCandidate 'bin\javac.exe'))) {
    $jdkCandidate = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
}
if (-not $jdkCandidate -or -not (Test-Path -LiteralPath (Join-Path $jdkCandidate 'bin\javac.exe'))) {
    throw 'A JDK is required. Install JDK 21 and set JAVA_HOME.'
}
$env:JAVA_HOME = $jdkCandidate
$env:Path = "$(Join-Path $jdkCandidate 'bin');$env:Path"
$env:GRADLE_USER_HOME = Join-Path $script:WorkspaceRoot '.cache\gradle'
$env:WORKSPACE_PLATFORM_ROOT = $script:WorkspaceRoot
$env:WORKSPACE_DATA_DIR = (Join-Path $script:WorkspaceRoot 'data').Replace('\', '/')
if (-not $env:WORKSPACE_CODEX_EXECUTABLE) {
    $codexCommand = Get-Command codex.exe -ErrorAction SilentlyContinue
    if ($codexCommand) { $env:WORKSPACE_CODEX_EXECUTABLE = $codexCommand.Source }
}

if (-not $env:WORKSPACE_DOCKER_EXECUTABLE) {
    $dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($dockerCommand) { $env:WORKSPACE_DOCKER_EXECUTABLE = $dockerCommand.Source }
    else {
        $dockerCandidates = @(
            (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'),
            (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe')
        )
        foreach ($dockerCandidate in $dockerCandidates) {
            if (Test-Path -LiteralPath $dockerCandidate) { $env:WORKSPACE_DOCKER_EXECUTABLE = $dockerCandidate; break }
        }
    }
}
