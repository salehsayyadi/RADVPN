$ErrorActionPreference = 'Stop'
Set-Location "$PSScriptRoot/.."
python scripts/materialize-assets.py
if ($LASTEXITCODE -ne 0) { throw 'Icon generation failed' }
if (Test-Path dist/windows) { Remove-Item dist/windows -Recurse -Force }
$env:GOOS = 'windows'; $env:GOARCH = 'amd64'; $env:CGO_ENABLED = '0'
go build -trimpath -ldflags='-s -w' -o dist/windows/radcore.exe ./cmd/radcore
if ($LASTEXITCODE -ne 0) { throw 'Go build failed' }
dotnet publish apps/windows/RADVPN.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o dist/windows
if ($LASTEXITCODE -ne 0) { throw 'Windows publish failed' }
python scripts/fetch-wintun.py
if ($LASTEXITCODE -ne 0) { throw 'Wintun checksum failed' }
if ((Get-AuthenticodeSignature dist/windows/wintun.dll).Status -ne 'Valid') { throw 'Wintun Authenticode signature is not valid' }
Copy-Item third_party/*LICENSE* dist/windows/
Copy-Item LICENSE dist/windows/RADVPN-LICENSE.txt
Copy-Item THIRD_PARTY_NOTICES.md dist/windows/
Copy-Item docs/INSTALL.md,docs/KNOWN_LIMITATIONS.md,PRIVACY.md dist/windows/
go mod download
if ($LASTEXITCODE -ne 0) { throw 'Module download failed' }
python scripts/collect-licenses.py
if ($LASTEXITCODE -ne 0) { throw 'License collection failed' }
Copy-Item dist/THIRD_PARTY_LICENSES dist/windows/THIRD_PARTY_LICENSES -Recurse -Force
$nuget = Join-Path $HOME '.nuget/packages'
foreach ($module in @('microsoft.netcore.app.runtime.win-x64','microsoft.windowsdesktop.app.runtime.win-x64')) {
  foreach ($version in Get-ChildItem (Join-Path $nuget $module) -Directory) {
    foreach ($notice in @('LICENSE.TXT','THIRD-PARTY-NOTICES.TXT')) {
      $source = Join-Path $version.FullName $notice
      if (Test-Path $source) { Copy-Item $source "dist/windows/$module-$notice" }
    }
  }
}
Get-ChildItem dist/windows -Filter *.pdb | Remove-Item
Get-ChildItem dist/windows | Select-Object Name,Length | Format-Table
python scripts/installer-manifest.py
if ($LASTEXITCODE -ne 0) { throw 'Uninstall manifest generation failed' }
$compilerPath = (Get-Command makensis.exe -ErrorAction SilentlyContinue).Source
if (-not $compilerPath) { $candidate = "${env:ProgramFiles(x86)}\NSIS\makensis.exe"; if (Test-Path $candidate) { $compilerPath = $candidate } }
if (-not $compilerPath) { throw 'Install NSIS 3.12 (or compatible Unicode 3.x) and put makensis.exe on PATH.' }
& $compilerPath /V3 /INPUTCHARSET UTF8 "/DREPO_ROOT=$((Get-Location).Path)" installer/RADVPN.nsi
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed' }
Get-FileHash dist/RADVPN-Setup-2.0.0-x64.exe -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLower())  RADVPN-Setup-2.0.0-x64.exe" } | Set-Content dist/SHA256SUMS.txt
