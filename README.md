# RADVPN 2.0.0

Native Windows x64 and Android Xray client, with English/Persian UI and Tunnel selected by default.

> **Development source, NOT a completed production release.** No `v2.0.0` release is authorized by this tree's current verification record. Compiling a TUN integration is not evidence of leak protection. See [testing](docs/TESTING.md) and the [remaining release blockers](docs/KNOWN_LIMITATIONS.md).

## What is implemented

- Shared native Go engine embedding immutable Xray `v26.10.21` source, not a remotely downloaded executable.
- Windows WPF desktop client with DPAPI storage, tray, profiles, subscriptions, settings, diagnostics and About.
- Android native Kotlin client with Keystore AES-GCM storage, official VPN consent, foreground `VpnService`, native TUN FD handoff and socket protection.
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

Windows, in PowerShell with Go, .NET and Python on PATH:

```powershell
./scripts/build-windows.ps1
```

CI builds testing artifacts; it does **not** create a public release or claim device-network QA. Android debug APKs are development-key signed, not production-signed. No production signing secrets are included or required for tests.

## Security / privacy

No account, activation, payment backend or telemetry. Configurations are stored encrypted by the native platform. Subscription providers receive HTTPS requests only when refreshed. The explicit connection check contacts Cloudflare through your server. See [privacy](PRIVACY.md), [security](SECURITY.md), [architecture](docs/ARCHITECTURE.md), [third-party notices](THIRD_PARTY_NOTICES.md) and [limitations](docs/KNOWN_LIMITATIONS.md).

**Do not rely on this build for safety-critical privacy, censorship evasion or unattended full-tunnel operation until the platform acceptance matrix passes.**
