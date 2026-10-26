$ErrorActionPreference = 'Stop'
Set-Location "$PSScriptRoot/.."
$env:GOOS = 'windows'; $env:GOARCH = 'amd64'; $env:CGO_ENABLED = '0'
go build -trimpath -ldflags='-s -w' -o dist/windows/radcore.exe ./cmd/radcore
if ($LASTEXITCODE -ne 0) { throw 'Go build failed' }
dotnet publish apps/windows/RADVPN.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o dist/windows
if ($LASTEXITCODE -ne 0) { throw 'Windows publish failed' }
python scripts/fetch-wintun.py
if ($LASTEXITCODE -ne 0) { throw 'Wintun checksum failed' }
$signature = Get-AuthenticodeSignature dist/windows/wintun.dll
if ($signature.Status -ne 'Valid') { throw 'Wintun Authenticode signature is not valid' }
Copy-Item third_party/*LICENSE* dist/windows/
Copy-Item docs/INSTALL.md dist/windows/INSTALL.md
Copy-Item docs/KNOWN_LIMITATIONS.md dist/windows/KNOWN_LIMITATIONS.md
go mod download
if ($LASTEXITCODE -ne 0) { throw 'Module verification download failed' }
python scripts/collect-licenses.py
if ($LASTEXITCODE -ne 0) { throw 'License collection failed' }
Copy-Item dist/THIRD_PARTY_LICENSES dist/windows/THIRD_PARTY_LICENSES -Recurse -Force
Copy-Item LICENSE dist/windows/RADVPN-LICENSE.txt
Copy-Item THIRD_PARTY_NOTICES.md dist/windows/
$nuget = Join-Path $HOME '.nuget/packages'
foreach ($module in @('microsoft.netcore.app.runtime.win-x64','microsoft.windowsdesktop.app.runtime.win-x64')) {
  foreach ($version in Get-ChildItem (Join-Path $nuget $module) -Directory) {
    foreach ($notice in @('LICENSE.TXT','THIRD-PARTY-NOTICES.TXT')) {
      $source = Join-Path $version.FullName $notice
      if (Test-Path $source) { Copy-Item $source "dist/windows/$module-$notice" }
    }
  }
}
Compress-Archive -Path dist/windows/* -DestinationPath dist/RADVPN-2.0.0-windows-x64-testing.zip -Force
