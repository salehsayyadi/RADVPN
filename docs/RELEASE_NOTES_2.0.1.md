# RADVPN 2.0.1 — Lightning

A unified, non-prerelease GitHub release for Android and Windows.

## Install
- **Android:** `RADVPN-Android.apk` — the only APK in this release; Android 8+; ARM64 and x86_64.
- **Windows:** `RADVPN-Windows-Setup.exe` — x64 installer.
- Other assets are checksums, license notices, build provenance and actual native acceptance evidence, not alternative installers.

## Android
- Exact approved orange-lightning artwork behind functional native Home, Servers, Configs and Settings screens; English and Persian RTL.
- Servers-only overflow menu for real connection tests, sorting, search and a separate Favorites page; named subscription tabs beside All.
- Paste VLESS, VMess, Trojan and Shadowsocks links directly; no protocol-selection chips required. Paste, QR and file import remain available.
- REALITY `headerType=none`, Markdown-wrapped SNI query values and percent-encoded Shadowsocks credentials import correctly. Unspecified-address quota/expiry/account entries are displayed as subscription details, **not connectable servers**.
- Screenshots, recording and Recents previews allowed by default, with a privacy preference.
- Foreground notification shows measured proxy download/upload rates when connected.
- Configuration tests start isolated real Xray instances and require an actual HTTPS response with traffic in both directions. They are **not bare TCP pings**. Run them while disconnected; up to four workers/256 enabled profiles. Tests contact your provider and Cloudflare's HTTPS probe endpoint.

## Windows and shared engine
- Matching 2.0.1 installer and shared parsing fixes. Windows keeps its existing desktop interface.
- License notices included in applications; Android notice bundle is also attached.

## Signing, upgrades and verification — please read
- **GitHub release status is not a production-security certification.** Android is signed with a CI testing/debug certificate; Windows installer is unsigned. This is not a Play Store production-signed release.
- Previous CI APKs may have a different testing certificate. An in-place upgrade can be rejected. **Do not uninstall an existing installation without considering its locally stored profiles:** uninstall removes Android Keystore keys, and device-bound encrypted backups may then be unusable. A continuous signed upgrade requires the original signing key.
- Acceptance covers local shared-engine integration, native APK build/lint/signature, installed Android API35 emulator checks in English/Persian at two sizes, and actual Windows installer/native UI acceptance. Screenshots use sanitized example servers and show no fabricated successful latency.
- **Physical-device VPN leak, recovery, battery/background endurance and remote-provider interoperability acceptance remain NOT RUN.** Do not rely on this build for safety-critical privacy. Android has no independent kill switch.
- Historical releases/tags are retained for audit; this latest release contains only one APK and one Windows installer.
