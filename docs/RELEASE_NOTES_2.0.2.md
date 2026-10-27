# RADVPN 2.0.2 — Glass Lightning

## Installation packages
- `RADVPN-Android.apk`: Android 8+ (ARM64 and x86_64).
- `RADVPN-Windows-Setup.exe`: self-contained Windows x64 installer.
- `RADVPN-Windows-Portable.zip`: extract all files, then run RADVPN.exe; no installation required. Encrypted settings remain in the current user's LocalAppData, not on the USB drive.

## Changes
- Explicit UTF-8 Windows core IPC; offline country flag images independent of Windows emoji fonts.
- Both clients exclude UI-only profile metadata from strict core requests, fixing Invalid request during validation, testing and connection.
- Real isolated Xray configuration tests on both platforms, requiring a data response rather than just a reachable TCP port.
- Shared approved lightning artwork behind translucent cards and fields.
- Windows subscription tabs and full server list, sort dropdown and compact tools menu; duplicate Profiles navigation removed.
- Protocol-aware field editors instead of editing outbound JSON.
- Windows tunnel Connect requests elevation through Windows UAC and continues connection after restart; cancellation keeps the existing app open.
- REALITY RAW alias, REALITY gRPC/XHTTP parser compatibility and SIP002 Shadowsocks regression coverage.

## Signing and validation
Publication is blocked until a persistent private Android signing identity is configured and upgrade compatibility has been verified. Android updates require the same application ID and signing identity as the installed APK; a lost prior key cannot be reconstructed from an APK. Do not uninstall without an encrypted backup.
Windows executables are not Authenticode-signed. Wintun retains its vendor signature. Full-device leak protection, real remote servers and interactive UAC need physical-device validation; a passing parser or local proxy test does not prove these.
