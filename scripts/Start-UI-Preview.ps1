param([string]$BindAddress)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$runtime=Join-Path $root '.runtime\ui-preview'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
$state=Join-Path $runtime 'process.json'
if(Test-Path -LiteralPath $state){throw 'A preview session is recorded. Run Stop-UI-Preview.ps1 first.'}
if(-not $BindAddress){$BindAddress=(Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -and $_.InterfaceAlias -like '*Wi-Fi*' } | Select-Object -First 1).IPv4Address.IPAddress}
if(-not $BindAddress -or $BindAddress -notmatch '^(192\.168\.|10\.|172\.(1[6-9]|2\d|3[01])\.)'){throw 'Pass this laptop''s private Wi-Fi IPv4 address with -BindAddress.'}
$oldPreview=$env:VITE_UI_PREVIEW
try {
    $env:VITE_UI_PREVIEW='true'
    & npm.cmd --prefix (Join-Path $root 'frontend') run build -- --outDir ../.runtime/ui-preview/dist
    if($LASTEXITCODE -ne 0){throw 'UI preview build failed.'}
} finally {if($null -eq $oldPreview){Remove-Item Env:VITE_UI_PREVIEW -ErrorAction SilentlyContinue}else{$env:VITE_UI_PREVIEW=$oldPreview}}
$node=(Get-Command node.exe -ErrorAction Stop).Source
$server=Join-Path $PSScriptRoot 'ui-preview-server.mjs'
$dist=Join-Path $runtime 'dist'
$process=Start-Process -FilePath $node -ArgumentList @(('"'+$server+'"'),('"'+$dist+'"'),$BindAddress) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'server.log') -RedirectStandardError (Join-Path $runtime 'server.error.log')
try {
    $ready=$false
    for($i=0;$i -lt 15;$i++){if($process.HasExited){throw 'Preview exited; see .runtime/ui-preview/server.error.log.'};try{if((Invoke-RestMethod "http://${BindAddress}:4173/api/health" -TimeoutSec 2).preview){$ready=$true;break}}catch{};Start-Sleep -Seconds 1}
    if(-not $ready){throw 'Preview startup timed out.'}
    @{id=$process.Id;startTimeTicks=$process.StartTime.ToUniversalTime().Ticks.ToString();address=$BindAddress} | ConvertTo-Json | Set-Content -LiteralPath $state
    Write-Output "Phone preview (same Wi-Fi): http://${BindAddress}:4173/"
    Write-Output 'Sample data only; no backend connection. Stop: scripts/Stop-UI-Preview.ps1'
}catch{if(-not $process.HasExited){Stop-Process -Id $process.Id};throw}
