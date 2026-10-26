# RADVPN 2.0.0 — experimental unsigned testing release

**This is not production-ready or verified full-device privacy protection.** Windows native installer/application smoke testing is separate from the physical-device VPN acceptance tests, which remain NOT RUN. Do not use this build for safety-critical privacy.

## Downloads

- `RADVPN-Setup-2.0.0-x64.exe`: real per-user Windows 10/11 x64 NSIS installer, self-contained .NET runtime, actual Go/Xray core and vendor-signed Wintun DLL. **RADVPN and the installer are unsigned.** SmartScreen may warn.
- Android is **not a public release asset** in this Windows-focused release. Its debug APK builds/lints/signature-verifies in the linked CI run, but Android device acceptance and a complete mobile distribution-notice review remain outstanding. The Android UI was not redesigned here.
- `SHA256SUMS.txt`: SHA-256 for every published asset.
- Native test reports and actual compiled WPF English/Persian **offscreen client-viewport** captures are included for inspection; they are not proof of TUN routing or leak protection.

## Windows redesign and functional scope

Native WPF Home, Servers, Subscriptions, Configs, Profiles, Traffic, Settings, Logs, Advanced and About were rebuilt with centralized blue-black/orange styling, original shield/vector icons, reusable controls and a circular connection button. English/Persian updates immediately with RTL. No sample servers or invented traffic/latency are present.

The pinned embedded Xray core validates supported VLESS, legacy VMess JSON, Trojan, SIP002 Shadowsocks, SOCKS and HTTP profiles. Import link lists, bounded outbound-only JSON or HTTPS subscriptions. Real loopback SOCKS/HTTP, selected proxy HTTPS connectivity check, bounded direct TCP probes, favorites/selection, encrypted daily measured traffic totals, DPAPI atomic vault/backups, tray, explicit startup registration and redacted diagnostics are implemented.

## Install and use

1. Verify the installer SHA-256 against SHA256SUMS.txt. Run setup; choose installation location and optional desktop shortcut. Launch RADVPN from Start Menu. No development server or separate .NET runtime is required.
2. Open Configs, paste your trusted profile URI, or import a bounded supported file. Alternatively add your HTTPS subscription under Subscriptions and refresh. Choose the resulting profile under Profiles/Servers.
3. Tunnel is the default. Windows Tunnel requires quitting and running RADVPN as administrator. Setup does **not** silently install a driver or change networking. Actual physical-device full-tunnel safety has not been verified.
4. To use Local Proxy instead, choose that mode on Home and Connect. Configure each client application for SOCKS5 `127.0.0.1:10808` or HTTP `127.0.0.1:10809`. This is not all-device VPN protection. Connected requires a genuine HTTPS 204 through the selected proxy.
5. To change the local port: disconnect → Settings → Connection → SOCKS port → Save → Connect. HTTP uses the next port. Only loopback binds are accepted, range 1024–65534; occupied listeners fail explicitly.
6. Change EN/FA in the top bar or General settings. Settings persist encrypted.
7. Before repair/upgrade/uninstall, disconnect and quit fully from the tray. Setup refuses while RADVPN runs. Upgrades reuse the installed location; uninstall preserves `%LOCALAPPDATA%\RADVPN` profiles/settings/history. Delete that folder only deliberately. Backups are device/account-bound, not cross-device exports.

## Verification and remaining limits

Published only from a successful immutable CI source run, after review of actual native captures. CI executes Go tests on Windows and Linux (including real local HTTP CONNECT → Xray SOCKS → destination, counters and cleanup), race/vet/fuzz checks, formatter/localization checks, installer build, vendor driver signature verification, silent install, native IPC/DPAPI tests, Start Menu launch, graceful shutdown/no stale core, same-version repair and uninstall. Android APK build, lint and signature verification run separately. Full reports identify their exact scope.

**NOT RUN:** physical-device TUN capture, remote VLESS/VMess/Trojan/REALITY interoperability, DNS/IPv6 leaks, forced-crash route recovery, UAC interaction, Wi-Fi/sleep/network changes, physical desktop/window-resize UI testing, interactive setup/SmartScreen, or Android device VPN lifecycle. No remote test credentials, Windows signing certificate or production Android keystore were supplied. No app-level strict kill switch, automatic reconnect supervisor or privileged Windows service is implemented. TCP latency is direct endpoint reachability, not VPN handshake latency. Daily traffic covers only real samples collected while the app runs.

No account, activation, payment or telemetry. Subscription requests contact your provider; each connection's disclosed HTTPS health check contacts Cloudflare through your selected proxy. Direct server tests contact your endpoints outside the tunnel. Raw core logs are disabled. Read PRIVACY.md, SECURITY.md, THIRD_PARTY_NOTICES.md and docs/KNOWN_LIMITATIONS.md before use.
