$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$transfer = Join-Path $root '.transfer'
New-Item -ItemType Directory -Force -Path $transfer | Out-Null
$exportName = 'AI-Developer-Workspace-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$packageRoot = Join-Path $transfer $exportName
if (Test-Path -LiteralPath $packageRoot) { throw 'This export directory already exists. Retry with a new timestamp.' }
$source = Join-Path $packageRoot 'project'
New-Item -ItemType Directory -Path $source -Force | Out-Null
# Shut down the app before copying the file-backed database.
if (Test-Path -LiteralPath (Join-Path $root '.runtime\processes.json')) {
    & (Join-Path $PSScriptRoot 'Stop-Workspace.ps1')
}
$files = & git -C $root ls-files --cached --others --exclude-standard
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate project source.' }
foreach ($relative in $files) {
    if ($relative -notmatch '^(frontend/|backend/|scripts/|documentation/|README\.md$|AGENTS\.md$|\.gitignore$)') { continue }
    $original = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $original -PathType Leaf)) { continue }
    if ((Get-Item -LiteralPath $original -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing linked source file: $relative" }
    $target = Join-Path $source $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath $original -Destination $target
}
$dataDir = Join-Path $root 'data'
if (Test-Path -LiteralPath $dataDir) {
    $databases = Get-ChildItem -LiteralPath $dataDir -Filter '*.mv.db' -File
    if ($databases) {
        New-Item -ItemType Directory -Path (Join-Path $source 'data') -Force | Out-Null
        $databases | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $source 'data') }
    }
}
& git -C $root bundle create (Join-Path $packageRoot 'repository-history.bundle') --branches --tags
if ($LASTEXITCODE -ne 0) { throw 'Offline history export failed.' }
& git -C $root bundle verify (Join-Path $packageRoot 'repository-history.bundle')
if ($LASTEXITCODE -ne 0) { throw 'Offline history validation failed.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Restore-Transfer.ps1') -Destination (Join-Path $packageRoot 'Restore-Workspace.ps1')
$evidence = Join-Path $packageRoot 'verification'
New-Item -ItemType Directory -Path $evidence | Out-Null
foreach ($name in @('workspace-preview.png', 'verified-task-history.png')) {
    $original = Join-Path $root ".runtime\$name"
    if (Test-Path -LiteralPath $original) { Copy-Item -LiteralPath $original -Destination $evidence }
}
$reports = Join-Path $root 'backend\build\test-results\test'
if (Test-Path -LiteralPath $reports) { Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml' | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $evidence } }
$manifest = @(Get-ChildItem -LiteralPath $source -Recurse -File -Force | ForEach-Object {
    @{ path = $_.FullName.Substring($source.Length + 1).Replace('\', '/'); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
})
$manifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $packageRoot 'MANIFEST.json')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = Join-Path $transfer ($exportName + '.zip')
[IO.Compression.ZipFile]::CreateFromDirectory($packageRoot, $zipPath)
$checksum = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
Set-Content -LiteralPath ($zipPath + '.sha256') -Value "$checksum  $exportName.zip"
Write-Output "Transfer package: $zipPath"
Write-Output "Project files: $($manifest.Count)"
Write-Output "SHA256: $checksum"
Write-Output 'The source application is stopped. Authentication and dependency caches were excluded.'
