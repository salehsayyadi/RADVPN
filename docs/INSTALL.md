# Building and testing RADVPN 2.0.0

**Testing only. Read KNOWN_LIMITATIONS.md before enabling Tunnel mode.**

## Windows 10/11 x64

Prerequisites for building: Go 1.27.3, .NET 8 SDK, Python 3, PowerShell and Unicode NSIS 3.12. Run `./scripts/build-windows.ps1`. It generates the original RADVPN icon from its checked-in source, builds the native core and self-contained WPF runtime, verifies pinned Wintun SHA256 plus vendor Authenticode, collects notices, builds `dist/RADVPN-Setup-2.0.0-x64.exe` and writes `dist/SHA256SUMS.txt`.

Run the actual installer from a verified GitHub prerelease (when available), check its SHA256, and follow the installation wizard. **Unsigned / experimental:** Windows SmartScreen may warn; no digital signature is claimed. Setup installs only for the current user under `%LOCALAPPDATA%\Programs\RADVPN`, registers an uninstaller and Start Menu shortcuts, and offers a desktop shortcut. Setup does not change routes or install a network driver silently. No development server or separately installed .NET runtime is required.

Launch from Start Menu. Proxy mode does not require elevation. Tunnel is the default; quit fully using the tray, then Run as administrator for an explicit Tunnel connection. A failed permission check is not Connected. The TUN safety acceptance matrix is not yet passed.

Before upgrade or uninstall, disconnect and choose Quit. Setup refuses while the application mutex is active; a newer installed version cannot be downgraded. The same installation location is reused. Uninstall removes only listed app-owned payload/shortcuts/startup registration; `%LOCALAPPDATA%\RADVPN` encrypted profiles, settings and sampled history are deliberately preserved. Remove that data folder yourself only if you intentionally want to erase it. Never remove shared Windows networking drivers blindly.

For reproducible native smoke checks after building: `./scripts/test-windows-installer.ps1`. It uses an isolated install folder, real DPAPI and child IPC, actual WPF captures at two sizes in both languages, Start Menu launch, graceful shutdown, same-version repair and uninstall. It is not a physical-device TUN/leak test.

## Android 8+ (API 26), arm64 / x86_64

Install JDK 17, Go 1.27.3, Gradle 8.11.1, SDK platform/build-tools 35, NDK 28.0.13004108. Set `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_NDK_HOME`, with Go/Gradle on PATH. Run:

```sh
bash scripts/build-android.sh
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

The APK is automatically **debug-signed**, not production-signed. Do not use a debug signing identity as your public release identity. No AAB or fake signed release is generated. Android may require allowing installation from the chosen file source; do so only if you trust the exact build and its checksum.

Launch RADVPN, allow notifications if desired. Select Tunnel and press Connect; accept Android's official VPN permission dialog. Persistent notification and Android's VPN indicator reflect service/interface activity, not a guarantee that remote traffic is flowing. Connected in the application additionally requires the real HTTPS probe.

The app does not silently request battery-optimization exemption. Device power-management behavior must be tested. System VPN Settings can provide always-on / block-without-VPN after compatibility verification; do not assume the app implements its own kill switch.

## Profiles and connection (both platforms)

1. Open Profiles. Paste your own supported `vless://`, legacy `vmess://Base64(JSON)`, `trojan://`, SIP002 `ss://`, `socks://`, or `http://` URI into Import links / Base64 and press Import. No activation key or RADVPN account is required. Use correct percent-encoding for credentials.
2. Alternatively import a bounded local text file or clipboard. Android can receive shared text or scan a QR code. No sample server is included; `example.com` in tests is deliberately not a usable endpoint.
3. On the desired profile choose Selected profile. Use Rename, Favorite, Enable/disable, Duplicate or Delete as needed. Advanced editing accepts only one whitelisted Xray outbound and validates it with the pinned core. It is not arbitrary full-config execution.
4. Open Overview. Tunnel is the initial default. Press Connect. Windows requires administrator rights and the verified Wintun DLL; Android requires VPN consent.
5. Connecting is not Connected: wait for an actual policy-routed HTTPS 204 result. An error closes the core. A successful result is **not** evidence that every other application was captured by the device routes.
6. Press Disconnect before changing network configuration. To switch modes, select Tunnel or Local proxy on Overview; the old engine is shut down first. In Proxy mode, configure each application to use `127.0.0.1:10808` SOCKS5, or `127.0.0.1:10809` HTTP. The app does not change the operating-system proxy globally.
7. To change the port: disconnect → Settings → Connection → SOCKS port → Save → reconnect. Range: 1024–65534. HTTP uses the next port. A conflict on either listener fails startup with a localized error. Only loopback addresses are allowed; `0.0.0.0` and public interfaces are rejected.
8. Settings supports validated DNS IPs, MTU and domain/IP routing JSON. Example:

```json
[{"domains":["domain:example.org"],"ips":[],"action":"direct"},{"domains":[],"ips":["10.0.0.0/8"],"action":"block"}]
```

Rules with both domain and IP match according to Xray's AND semantics; use separate rules for OR. The health-check domain is reserved to proxy routing. Physical-interface DNS is not permanently overwritten.

9. For subscriptions open Subscriptions, enter your own HTTPS URL and Add. Refresh is manual on Android; Windows offers an hours interval (0 = manual) while the app runs disconnected. Providers receive the URL, including their own subscription token. Failed refresh keeps the old profiles. Refresh may remove profiles no longer present in that subscription.
10. Change English / فارسی in Settings. Interface direction and labels update immediately. Export encrypted backup is tied to this Windows account/device or Android Keystore; it cannot restore on a new device or after Android uninstall/Keystore removal.

Only import profiles and subscription URLs from trusted sources. Bypass rules intentionally send matching traffic directly. No untested privacy or full-tunnel guarantee is made.
