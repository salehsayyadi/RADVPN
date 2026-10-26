# Building and testing RADVPN 2.0.0

**Testing only. Read KNOWN_LIMITATIONS.md before enabling Tunnel mode.**

## Windows 10/11 x64

Prerequisites: Go 1.27.3, .NET 8 SDK, Python 3, PowerShell, network access to authenticated dependency sources. Run `./scripts/build-windows.ps1` from the checkout. The script builds the native engine and self-contained WPF app, verifies the pinned official Wintun ZIP SHA256 and validates its Authenticode signature, then creates `dist/RADVPN-2.0.0-windows-x64-testing.zip`.

Extract the **whole folder**, not just RADVPN.exe: the desktop runtime's native DLLs, `radcore.exe`, `wintun.dll`, localization files and license notices must stay together. Run `RADVPN.exe`. For Tunnel, close it completely via tray → Quit and start **Run as administrator**. Proxy mode does not require elevation. This is a portable build, not an installed service. Close/minimize behavior defaults to tray; disconnect before quitting.

To remove: disconnect, choose Quit, remove extracted application folder. To remove saved profiles intentionally, delete `%LOCALAPPDATA%\RADVPN` after exiting. Never remove a shared Windows network driver blindly; inspect Device Manager if an app-owned adapter persists after a crash and record the failure for QA.

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
7. To change the port: disconnect → Settings → SOCKS port → Save → reconnect. Range: 1024–65534. HTTP uses the next port. A conflict on either listener fails startup with a localized error. Only loopback addresses are allowed; `0.0.0.0` and public interfaces are rejected.
8. Settings supports validated DNS IPs, MTU and domain/IP routing JSON. Example:

```json
[{"domains":["domain:example.org"],"ips":[],"action":"direct"},{"domains":[],"ips":["10.0.0.0/8"],"action":"block"}]
```

Rules with both domain and IP match according to Xray's AND semantics; use separate rules for OR. The health-check domain is reserved to proxy routing. Physical-interface DNS is not permanently overwritten.

9. For subscriptions open Subscriptions, enter your own HTTPS URL and Add. Refresh is manual on Android; Windows offers an hours interval (0 = manual) while the app runs disconnected. Providers receive the URL, including their own subscription token. Failed refresh keeps the old profiles. Refresh may remove profiles no longer present in that subscription.
10. Change English / فارسی in Settings. Interface direction and labels update immediately. Export encrypted backup is tied to this Windows account/device or Android Keystore; it cannot restore on a new device or after Android uninstall/Keystore removal.

Only import profiles and subscription URLs from trusted sources. Bypass rules intentionally send matching traffic directly. No untested privacy or full-tunnel guarantee is made.
