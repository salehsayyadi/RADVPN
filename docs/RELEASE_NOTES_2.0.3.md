# RADVPN 2.0.3 — Native Glass and Readable Profiles

Owner-requested test release. Windows executables remain unsigned. Physical-device VPN operation and leak protection have NOT been verified.

## Changes
- Repairs recoverable UTF-8/Windows-1252 mojibake in previously saved profile names, retaining the original name and profiles.
- Windows Home and Servers now use compact native tables, offline flag images and subscription tabs rather than large profile cards.
- Direct VLESS, VMess, Trojan and Shadowsocks links in the redesigned Configs page; subscription name and URL have their own form. Base64/JSON import is no longer the primary Windows form.
- Two-column protocol-aware native editor with transport and security selectors. QR-image import is local.
- More translucent Windows panels and brighter shared lightning artwork.
- Explicit administrator-consent flow, Wintun-load and port checks, and credential-free startup error categories rather than one generic message.

## Verified build evidence
- The release tag points to publication-only metadata changes; application binaries are unchanged from the reviewed source.
- Same source: `68b35cfd4ae33dc62030718ad9f59a72af40da67`.
- Windows/shared/Android build run: https://github.com/salehsayyadi/RADVPN/actions/runs/40533105960
- Android native emulator run: https://github.com/salehsayyadi/RADVPN/actions/runs/40533106638
- Windows installer and Portable native self-tests, encrypted legacy-name migration, installation/repair/uninstall and 68 automated native page captures passed.
- Real Wintun startup, route/DNS/WFP setup, a local response routed through TUN and shutdown passed on a disposable hosted Windows runner. This is NOT evidence of a working remote server, physical-device leak protection or resolution of every user-device startup failure.
- Representative compiled Windows screenshots were manually reviewed; full manual review of every capture and interactive Windows UAC were not performed.
- Android emulator acceptance passed at two viewport sizes in English and Persian.

## Important Android upgrade warning
**In-place Android upgrade compatibility is NOT verified.** This release publishes the tested CI Debug APK at the owner's request. A persistent private signing key has not been established for this artifact. Android can refuse installation over a previously installed RADVPN APK if signing certificates differ. This release must not be described as fixing that signature mismatch. A matching original private key is required for a compatible update. Uninstalling may remove profiles and Android Keystore keys; do not uninstall expecting an encrypted backup to remain automatically recoverable.

Windows executables are unsigned; Windows SmartScreen may warn. Portable mode does not bypass UAC requirements for full-device tunneling.

## Installation packages
- `RADVPN-Android.apk`: Android 8+ (ARM64 and x86_64).
- `RADVPN-Windows-Setup.exe`: self-contained Windows x64 installer.
- `RADVPN-Windows-Portable.zip`: extract all files, then run RADVPN.exe; no installation required. Encrypted settings remain in the current user's LocalAppData, not on the USB drive.

## Testing on your device
Back up important settings before testing. Do not uninstall an existing Android installation expecting its encrypted backup to remain recoverable. Share only safe Diagnostics output, never server links or credentials, when reporting connection failures.
