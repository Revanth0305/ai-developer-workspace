param([Parameter(Mandatory = $true)][string]$Destination)
$ErrorActionPreference = 'Stop'
$packageRoot = $PSScriptRoot
$source = Join-Path $packageRoot 'project'
$manifestPath = Join-Path $packageRoot 'MANIFEST.json'
if (-not (Test-Path -LiteralPath $manifestPath) -or -not (Test-Path -LiteralPath $source)) {
    throw 'Run the Restore-Workspace.ps1 copy from the extracted transfer package, not this template in the source repository.'
}
$target = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $target) { throw 'Choose a new destination directory that does not already exist.' }
foreach ($entry in (Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)) {
    $file = [IO.Path]::GetFullPath((Join-Path $source $entry.path))
    if (-not $file.StartsWith($source + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid manifest path.' }
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256) { throw "Transfer checksum mismatch: $($entry.path)" }
}
& git clone --branch codex/workspace-foundation (Join-Path $packageRoot 'repository-history.bundle') $target
if ($LASTEXITCODE -ne 0) { throw 'Offline Git history restore failed.' }
& git -C $target remote remove origin
if ($LASTEXITCODE -ne 0) { throw 'Could not remove the temporary local bundle origin.' }
Get-ChildItem -LiteralPath $source -Force | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force }
foreach ($entry in (Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)) {
    if ((Get-FileHash -LiteralPath (Join-Path $target $entry.path) -Algorithm SHA256).Hash -ne $entry.sha256) { throw "Restored checksum mismatch: $($entry.path)" }
}
Write-Output "Restored and verified: $target"
Write-Output 'No Git hosting account was connected. Open this folder in Codex and read documentation/MIGRATION_HANDOFF.md.'
