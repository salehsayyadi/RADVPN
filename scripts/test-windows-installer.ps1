$ErrorActionPreference = 'Stop'
Set-Location "$PSScriptRoot/.."
$setup = (Resolve-Path dist/RADVPN-Setup-2.0.0-x64.exe).Path
$install = Join-Path $env:TEMP 'RADVPN-Install-Smoke'
$report = [System.Collections.Generic.List[string]]::new()
function Run-Wait($file,$arguments) { $p = Start-Process -FilePath $file -ArgumentList $arguments -PassThru -Wait; if ($p.ExitCode -ne 0) { throw "$file failed with $($p.ExitCode)" } }
if (Test-Path $install) { throw 'Installer test path already exists; refusing to delete unknown content' }
Run-Wait $setup "/S /D=$install"
if (-not (Test-Path "$install/RADVPN.exe") -or -not (Test-Path "$install/radcore.exe") -or -not (Test-Path "$install/wintun.dll")) { throw 'Installed payload missing' }
$report.Add('PASS silent per-user installation and bundled app/core/driver files')
$link = Join-Path ([Environment]::GetFolderPath('Programs')) 'RADVPN/RADVPN.lnk'
if (-not (Test-Path $link)) { throw 'Start Menu shortcut missing' }
$target = (New-Object -ComObject WScript.Shell).CreateShortcut($link).TargetPath
if ($target -ne "$install\RADVPN.exe") { throw 'Shortcut target mismatch' }
$report.Add('PASS Start Menu shortcut and installed path')
Run-Wait "$install/RADVPN.exe" "--self-test `"$PWD/dist/native-tests.json`""
$report.Add('PASS native IPC, DPAPI persistence/recovery and real TCP measurement self-tests')
Run-Wait "$install/RADVPN.exe" "--ui-smoke --capture-dir `"$PWD/dist/ui-evidence`""
$report.Add('PASS native application pages rendered at 1280x720 and 1440x900 in English/Persian; see screenshots and layout report')
$app = Start-Process -FilePath $link -PassThru
Start-Sleep -Seconds 4
if ($app.HasExited) { throw 'Start Menu application launch exited early' }
Run-Wait "$install/RADVPN.exe" '--quit'
if (-not $app.WaitForExit(15000)) { throw 'Graceful desktop shutdown timed out' }
Start-Sleep -Seconds 2
$leftover = Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -eq "$install\radcore.exe" }
if ($leftover) { throw 'Stale installed core process after desktop shutdown' }
$report.Add('PASS Start Menu launch, graceful shutdown and no stale app-owned core')
$vault = Join-Path $env:LOCALAPPDATA 'RADVPN/vault.bin'
if (-not (Test-Path $vault)) { throw 'Expected app-data vault was not persisted' }
$before = (Get-FileHash $vault -Algorithm SHA256).Hash
Run-Wait $setup "/S /D=$install"
if ((Get-FileHash $vault -Algorithm SHA256).Hash -ne $before) { throw 'Upgrade modified encrypted user vault' }
$report.Add('PASS same-version repair/upgrade preserves user vault')
Run-Wait "$install/Uninstall.exe" "/S _?=$install"
if (Test-Path "$install/RADVPN.exe") { throw 'Uninstall left application payload' }
if (Test-Path $link) { throw 'Uninstall left Start Menu shortcut' }
if (-not (Test-Path $vault) -or (Get-FileHash $vault -Algorithm SHA256).Hash -ne $before) { throw 'Uninstall did not preserve user vault' }
$report.Add('PASS uninstall removes app/shortcut and preserves encrypted user data')
$report.Add('NOT RUN: physical-device TUN/DNS/IPv6 leak, forced-route recovery, real remote Xray credentials, interactive wizard and SmartScreen')
$report | Set-Content dist/installer-test-report.txt
$report
