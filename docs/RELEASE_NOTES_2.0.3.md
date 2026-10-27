# RADVPN 2.0.3 — Native Glass and Readable Profiles

Owner-requested test release. Windows executables remain unsigned. Physical-device VPN operation and leak protection have NOT been verified.

## Same-version hotfix replacement
The owner requested replacement of the download assets within this existing v2.0.3 release. The version number and existing tag are unchanged. Download the packages again; do not reuse an earlier cached copy. `build-provenance.json` and `SHA256SUMS.txt` identify the replacement binaries. The tag retains the original source snapshot; use the explicit reviewed source commit below for the replacement sources.

- REALITY `pqv` share-link parameters are validated and preserved as Xray `mldsa65Verify`, including Base64 subscription imports. Unknown fields are still rejected rather than silently discarded.
- Windows resolves the selected upstream hostname before TUN route/DNS changes, temporarily pins only the runtime endpoint and preserves TLS SNI, Host headers, REALITY verification and stored profiles.
- HTTPS connectivity verification can use a second provider when the first is blocked; both attempts use the selected proxy, validate TLS and require a 204 response. Failure is not represented as a successful connection.
- Windows updates the Connect/Disconnect controls after a failed verification. Subscription pages show a rejected-entry count when unsupported entries remain.
- A hosted-runner TUN test is not a physical-device or remote-server guarantee. Device-specific connection failures still require safe diagnostics.

## Changes
- Repairs recoverable UTF-8/Windows-1252 mojibake in previously saved profile names, retaining the original name and profiles.
- Windows Home and Servers now use compact native tables, offline flag images and subscription tabs rather than large profile cards.
- Direct VLESS, VMess, Trojan and Shadowsocks links in the redesigned Configs page; subscription name and URL have their own form. Base64/JSON import is no longer the primary Windows form.
- Two-column protocol-aware native editor with transport and security selectors. QR-image import is local.
- More translucent Windows panels and brighter shared lightning artwork.
- Explicit administrator-consent flow, Wintun-load and port checks, and credential-free startup error categories rather than one generic message.

## Verified build evidence
- The existing release tag is preserved. Replacement application binaries are built from the reviewed source identified below.
- Same source: `d797fe168bfbd2f97a1f992eb8abeb2fa7e1fc9d`.
- Windows/shared/Android build run: https://github.com/salehsayyadi/RADVPN/actions/runs/40548757936
- Android native emulator run: https://github.com/salehsayyadi/RADVPN/actions/runs/40548757975
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
