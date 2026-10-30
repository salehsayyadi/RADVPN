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
 $fixtureImport=('{"method":"import","text":"http://fixture-only@127.0.0.1:3128#upgrade-fixture"}' | & "$target/radcore.exe" | ConvertFrom-Json).data
 $fixtureProfile=$fixtureImport.profiles[0]
 if(-not $fixtureProfile.id){throw 'Previous engine fixture import failed'}
 $fixtureSubscription=@{id='upgrade-sub-fixture';name='Upgrade fixture';url='https://upgrade-fixture.example.invalid/subscription'}
 $state=@{profiles=@($fixtureProfile);subscriptions=@($fixtureSubscription);lang='fa';selected=$fixtureProfile.id;settings=$defaults;tray=$false;upgradeFixture='radvpn303-preserve-fixture';trafficDays=@{};radvpn=@{endpoint='https://api.bestget.ir/';token='LOCAL-NONAUTHENTIC-UPGRADE-SESSION-FIXTURE';session_id='upgrade-fixture-session'}} | ConvertTo-Json -Depth 30 -Compress
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
 if($decoded.profiles.Count -ne 1 -or $decoded.profiles[0].id -ne $fixtureProfile.id -or $decoded.selected -ne $fixtureProfile.id){throw 'Existing profile/selection not retained'}
 if($decoded.subscriptions.Count -ne 1 -or $decoded.subscriptions[0].url -ne $fixtureSubscription.url){throw 'Existing subscription not retained'}
 if($decoded.settings.mode -ne $defaults.mode -or $decoded.settings.port -ne $defaults.port){throw 'Existing connection settings not retained'}
 if($decoded.radvpn.token -ne 'LOCAL-NONAUTHENTIC-UPGRADE-SESSION-FIXTURE' -or $decoded.radvpn.session_id -ne 'upgrade-fixture-session'){throw 'Encrypted session fixture not retained'}
 $report.Add('PASS encrypted non-authentic session fixture retained across installed upgrade and launch; no live session-validity claim')
 $report.Add('PASS upgraded native UI launches; existing profile, selection, subscription, language and connection settings retained')
 $portable=Join-Path $env:RUNNER_TEMP 'RADVPN-portable-upgrade-303'
 $oldZip=$release.assets|Where-Object name -eq 'RADVPN-Windows-Portable.zip'|Select-Object -First 1
 if(-not $oldZip){throw 'Previous portable package missing'}
 $zip=Join-Path $env:RUNNER_TEMP 'RADVPN-old-portable.zip';Invoke-WebRequest $oldZip.browser_download_url -OutFile $zip
 if($oldZip.digest -notmatch '^sha256:([a-f0-9]{64})$'){throw 'Previous ZIP digest missing'}
 if((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -ne $Matches[1]){throw 'Previous ZIP digest mismatch'}
 Expand-Archive $zip $portable
 Expand-Archive dist/RADVPN-Windows-Portable-3.0.3-x64.zip $portable -Force
 Run-Wait "$portable/RADVPN.exe" "--self-test `"$PWD/dist/portable-tests-303.json`""
 $portableState=[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($vault),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)) | ConvertFrom-Json
 if($portableState.selected -ne $fixtureProfile.id -or $portableState.profiles.Count -ne 1 -or $portableState.subscriptions[0].url -ne $fixtureSubscription.url){throw 'Portable upgrade lost profile/subscription data'}
 $report.Add('PASS previous portable ZIP overwritten by v3.0.3; new native executable self-tests pass; user vault retained')
 $report.Add('NOT RUN: physical remote VPN, live central API account upgrade, interactive installer consent, Android upgrade')
} catch { $report.Add('FAIL: '+$_.Exception.Message);throw }
finally { New-Item dist -ItemType Directory -Force | Out-Null; $report | Set-Content dist/upgrade-303-report.txt; $report }
