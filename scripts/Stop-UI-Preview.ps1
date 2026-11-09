$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$state=Join-Path $root '.runtime\ui-preview\process.json'
if(-not(Test-Path -LiteralPath $state)){Write-Output 'No recorded UI preview.';return}
$entry=Get-Content -LiteralPath $state -Raw | ConvertFrom-Json
$process=Get-Process -Id $entry.id -ErrorAction SilentlyContinue
if($process){
    $details=Get-CimInstance Win32_Process -Filter "ProcessId = $($entry.id)"
    if($process.StartTime.ToUniversalTime().Ticks -ne [long]$entry.startTimeTicks -or $details.Name -ne 'node.exe' -or -not $details.CommandLine.Contains((Join-Path $PSScriptRoot 'ui-preview-server.mjs'))){throw 'Preview process identity did not match; nothing was stopped.'}
    Stop-Process -Id $entry.id
    if(-not $process.WaitForExit(10000)){throw 'Preview did not stop within ten seconds.'}
}
Remove-Item -LiteralPath $state
Write-Output 'UI preview stopped.'
