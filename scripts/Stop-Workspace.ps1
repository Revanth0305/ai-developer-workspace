$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$statePath = Join-Path $root '.runtime\processes.json'
if (-not (Test-Path -LiteralPath $statePath)) { Write-Output 'No recorded workspace session.'; return }
$entries = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
foreach ($entry in $entries) {
    $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    $expectedStart = if ($entry.startTimeTicks) { [long]$entry.startTimeTicks } else { ([datetime]$entry.startTime).ToUniversalTime().Ticks }
    if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq $expectedStart) {
        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($entry.id)"
        if ($details.CommandLine -and $details.CommandLine.Contains($root) -and ($details.Name -eq 'java.exe' -or $details.Name -eq 'node.exe')) {
            Stop-Process -Id $entry.id
            if (-not $process.WaitForExit(10000)) { throw 'A workspace server did not stop within ten seconds.' }
        } else { throw 'Process identity could not be verified. No stop was performed for this entry.' }
    }
}
Remove-Item -LiteralPath $statePath
Write-Output 'Workspace servers stopped.'
