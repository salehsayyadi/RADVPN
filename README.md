# RADVPN 2.0.1

Native Windows x64 and Android Xray client, with English/Persian UI and Tunnel selected by default.

> **Public GitHub release, not device-security certification.** Android uses a CI testing/debug signature; Windows is unsigned. Physical-device tunnel/leak/recovery acceptance remains NOT RUN. See [release notes](docs/RELEASE_NOTES_2.0.1.md), [testing](docs/TESTING.md) and [limitations](docs/KNOWN_LIMITATIONS.md).

## Downloads / دانلود

**[RADVPN 2.0.1 — Android and Windows together](https://github.com/salehsayyadi/RADVPN/releases/tag/v2.0.1)** — published non-prerelease GitHub release.

- [Android APK](https://github.com/salehsayyadi/RADVPN/releases/download/v2.0.1/RADVPN-Android.apk) — the only APK in this release; Android 8+, ARM64/x86_64; exact approved lightning artwork and native English/Persian UI.
- [Windows x64 installer](https://github.com/salehsayyadi/RADVPN/releases/download/v2.0.1/RADVPN-Windows-Setup.exe).
- [Checksums](https://github.com/salehsayyadi/RADVPN/releases/download/v2.0.1/SHA256SUMS.txt) · [verified publication receipt](docs/UNIFIED_RELEASE_RECEIPT.md).

Other release assets contain notices, provenance and native acceptance evidence, not alternative installers. Historical releases are retained. An older Android CI build may have a different signing certificate: do not uninstall blindly, because uninstall removes the Keystore keys for locally encrypted profiles/backups.

### Lightning Android update

Four core screens: Home, Servers, Configs, Settings. Only Servers has the contextual overflow menu: real configuration/data tests, sorting and a separate Favorites page. All and named subscription tabs preserve provider membership. Direct VLESS/VMess/Trojan/SS links, REALITY none-header/rich-text import fixes, and separate subscription metadata are supported. Screenshots/Recents are allowed by default; connected notifications show measured proxy rates. [Design and test scope](docs/LIGHTNING_DESIGN.md).

## What is implemented

- Shared native Go engine embedding immutable Xray `v26.10.21` source, not a remotely downloaded executable.
- Redesigned native WPF desktop: reusable blue-black/orange tokens, circular power control, Home, Servers, Subscriptions, Configs, Profiles, Traffic, Settings, Logs, Advanced and About; immediate English/Persian RTL.
- Real NSIS per-user installer/uninstaller, self-contained desktop runtime, Start Menu and optional desktop shortcut; DPAPI atomic vault with recovery backup, tray and explicit current-user startup registration.
- Android: bounded, cancellable real configuration/data tests with four isolated Xray workers and an HTTPS response with bidirectional traffic. Windows retains explicitly labeled TCP endpoint tests; TCP reachability is distinct from the real connected-session proxy HTTPS check.
- Windows encrypted daily traffic totals from real counter deltas; Android current-session counters and foreground samples. Native live graphs without sample traffic.
- Redesigned Android native Kotlin client: dark/orange panels, circular power control, bottom navigation, native server/configuration/subscription/settings/traffic/diagnostic pages, immediate Persian RTL; Keystore AES-GCM storage, official VPN consent, foreground `VpnService`, native TUN FD handoff and socket protection.
- Tunnel/proxy switching with explicit shutdown. Loopback SOCKS5 defaults to `127.0.0.1:10808`; HTTP uses `10809` (SOCKS port + 1).
- VLESS, legacy VMess Base64 JSON, Trojan, SIP002 Shadowsocks, SOCKS and HTTP links; TCP, WS, gRPC and bounded XHTTP settings. Unsupported combinations are rejected rather than silently approximated.
- Plain/Base64 link-list import, clipboard/file import, deduplication, search, favorites, rename/duplicate/enable/delete, safe advanced outbound editing, HTTPS subscriptions, encrypted same-device backups.
- Immediate English/Persian direction and language switching, dark interface, real byte counters and sampled transfer rates. No sample servers, credentials, fake speed or fake latency.
- Connection becomes Connected only after an actual HTTPS request through the selected proxy to `https://cp.cloudflare.com/generate_204` succeeds. This is an HTTPS timing, not ICMP latency or proof of TUN routing.

## Build / run

Read [installation and build instructions](docs/INSTALL.md). Toolchains: Go 1.27.3, .NET 8 SDK, JDK 17, Gradle 8.11.1, Android SDK 35 / NDK 28.0.13004108. Android supports API 26+ (Android 8+) and currently builds arm64-v8a and x86_64 only.

```sh
go test -race ./...
go vet ./...
python3 scripts/check.py
# Android: set ANDROID_HOME, ANDROID_NDK_HOME, JAVA_HOME; Gradle 8.11.1 on PATH
bash scripts/build-android.sh
```

Windows, in PowerShell with Go, .NET, Python and NSIS 3.12 on PATH:

```powershell
./scripts/build-windows.ps1
```

CI builds the installer and runs native installation, WPF capture, repair and uninstall checks. A separate reviewed publication step creates the shared **non-prerelease GitHub release** only from successful same-source builds; no workflow claims physical-device network QA. Android debug APKs are development-key signed, not production-signed. No production signing secrets are included or required for tests.

## Security / privacy

No account, activation, payment backend or telemetry. Configurations are stored encrypted by the native platform. Subscription providers receive HTTPS requests only when refreshed. The explicit connection check contacts Cloudflare through your server. See [privacy](PRIVACY.md), [security](SECURITY.md), [architecture](docs/ARCHITECTURE.md), [third-party notices](THIRD_PARTY_NOTICES.md) and [limitations](docs/KNOWN_LIMITATIONS.md).

**Do not rely on this build for safety-critical privacy, censorship evasion or unattended full-tunnel operation until the platform acceptance matrix passes.**
