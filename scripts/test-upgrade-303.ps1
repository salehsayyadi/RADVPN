$ErrorActionPreference = 'Stop'
Set-Location "$PSScriptRoot/.."
$report = [System.Collections.Generic.List[string]]::new()
function Run-Wait($file, $arguments) { $p=Start-Process -FilePath $file -ArgumentList $arguments -PassThru -Wait; if($p.ExitCode -ne 0){throw "Native process failed: $($p.ExitCode)"} }
$target=Join-Path $env:LOCALAPPDATA 'Programs/RADVPN'
$vault=Join-Path $env:LOCALAPPDATA 'RADVPN/vault.bin'
if(Test-Path $target){throw 'Refusing to overwrite an existing runner installation'}
if(Test-Path $vault){throw 'Refusing to overwrite existing runner user data'}
try {
 $release=Invoke-RestMethod 'https://api.github.com/repos/salehsayyadi/RADVPN/releases/tags/v2.0.3'
 $asset=$release.assets | Where-Object name -eq 'RADVPN-Windows-Setup.exe' | Select-Object -First 1
 if(-not $asset){throw 'Previous distributed installer missing'}
 $prior=Join-Path $env:RUNNER_TEMP 'RADVPN-previous-2.0.3.exe'
 Invoke-WebRequest $asset.browser_download_url -OutFile $prior
 if($asset.digest -notmatch '^sha256:([a-f0-9]{64})$'){throw 'Previous installer digest missing'}
 if((Get-FileHash $prior -Algorithm SHA256).Hash.ToLower() -ne $Matches[1]){throw 'Previous installer integrity mismatch'}
 Run-Wait $prior "/S /D=$target"
 if(-not (Test-Path "$target/RADVPN.exe")){throw 'Previous installation failed'}
 $defaults=('{"method":"defaults"}' | & "$target/radcore.exe" | ConvertFrom-Json).data
 $state=@{profiles=@();subscriptions=@();lang='fa';selected='';settings=$defaults;tray=$false;upgradeFixture='radvpn303-preserve-fixture';trafficDays=@{}} | ConvertTo-Json -Depth 30 -Compress
 Add-Type -AssemblyName System.Security.Cryptography.ProtectedData
 New-Item (Split-Path $vault) -ItemType Directory -Force | Out-Null
 [IO.File]::WriteAllBytes($vault,[Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes($state),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))
 $before=(Get-FileHash $vault -Algorithm SHA256).Hash
 Run-Wait (Resolve-Path 'dist/RADVPN-Setup-3.0.3-x64.exe') "/S /D=$target"
 if((Get-FileHash $vault -Algorithm SHA256).Hash -ne $before){throw 'Upgrade changed user vault'}
 if((Get-Item "$target/RADVPN.exe").VersionInfo.FileVersion -ne '3.0.3.0'){throw 'Installed version incorrect'}
 $report.Add('PASS actual distributed v2.0.3 installer -> v3.0.3 in-place upgrade; no uninstall; encrypted fixture byte-for-byte preserved')
 Run-Wait "$target/RADVPN.exe" "--self-test `"$PWD/dist/native-tests-303.json`""
 $report.Add('PASS upgraded executable launch, native IPC, DPAPI and core self-tests')
 Run-Wait "$target/RADVPN.exe" "--ui-smoke --capture-dir `"$PWD/dist/upgrade-ui`""
 $decoded=[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($vault),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)) | ConvertFrom-Json
 if($decoded.upgradeFixture -ne 'radvpn303-preserve-fixture' -or $decoded.lang -ne 'fa'){throw 'User fixture/settings were not retained at launch'}
 $report.Add('PASS upgraded native UI launches and retained fixture/language are readable')
 $portable=Join-Path $env:RUNNER_TEMP 'RADVPN-portable-upgrade-303'
 $oldZip=$release.assets|Where-Object name -eq 'RADVPN-Windows-Portable.zip'|Select-Object -First 1
 if(-not $oldZip){throw 'Previous portable package missing'}
 $zip=Join-Path $env:RUNNER_TEMP 'RADVPN-old-portable.zip';Invoke-WebRequest $oldZip.browser_download_url -OutFile $zip
 if($oldZip.digest -notmatch '^sha256:([a-f0-9]{64})$'){throw 'Previous ZIP digest missing'}
 if((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -ne $Matches[1]){throw 'Previous ZIP digest mismatch'}
 Expand-Archive $zip $portable
 Expand-Archive dist/RADVPN-Windows-Portable-3.0.3-x64.zip $portable -Force
 Run-Wait "$portable/RADVPN.exe" "--self-test `"$PWD/dist/portable-tests-303.json`""
 if((Get-FileHash $vault -Algorithm SHA256).Hash -eq ''){throw 'User vault missing'}
 $report.Add('PASS previous portable ZIP overwritten by v3.0.3; new native executable self-tests pass; user vault retained')
 $report.Add('NOT RUN: physical remote VPN, live central API account upgrade, interactive installer consent, Android upgrade')
} catch { $report.Add('FAIL: '+$_.Exception.Message);throw }
finally { New-Item dist -ItemType Directory -Force | Out-Null; $report | Set-Content dist/upgrade-303-report.txt; $report }
