# RADVPN Android 2.0.0 — Design update 1

**APK آزمایشی اندروید با طراحی تیره/نارنجی مرجع؛ امضای debug، نه نسخهٔ production.**

Download `RADVPN-2.0.0-android-design1-debug.apk` from Assets below. Application version 2.0.0, versionCode 20001, Android 8+ (API26), ARM64 / x86_64. New Android-specific tag `v2.0.0-android.1` preserves the previously published Windows `v2.0.0` tag and installer.

Native, not WebView: circular ring power button, RADVPN shield, premium dark panels, bottom navigation, Servers/favorites/real TCP testing, configuration/QR/file import, subscriptions, profiles, measured session traffic, settings, safe diagnostics and About. English and Persian switch immediately with RTL. Actual VpnService/gomobile Xray integration and Keystore encryption remain in place; no fabricated servers, ping or connected status.

## Install / استفاده

1. Download APK and compare SHA-256 with SHA256SUMS.txt. Allow installation from the source you deliberately chose. This is debug-signed testing software, not Google Play or a production certificate.
2. Open Configs / کانفیگ‌ها. Import your own supported profile link/file or scan a trusted QR. Alternatively add your HTTPS subscription from the overflow menu → Subscriptions / اشتراک‌ها.
3. Select an enabled profile under Servers. Tunnel is the default; Connect requests Android's official VPN consent. Grant the notification permission when desired for the ongoing service notification. Test protection independently before sensitive use.
4. Local Proxy is separate. Configure client apps for SOCKS5 `127.0.0.1:10808`, HTTP `127.0.0.1:10809`. It is NOT whole-device VPN. Change ports while disconnected in Settings → Connection → Save; HTTP uses the next port.
5. EN/FA is available under Settings or the overflow menu. Advanced offers validated routing JSON and device-bound encrypted backup/restore.

## Verification scope

Published only after successful real APK/native engine build, Android lint, APK signature verification, selected Android Go/Maven notice collection, native emulator core/Keystore/import/DNS/port-conflict tests, and visual review of actual installed-app captures in both languages at two phone sizes. Attached reports and images show the actual scope; they are not a screenshot-only prototype.

**NOT RUN:** physical-device VPN/QR camera interaction, remote VLESS/VMess/Trojan/REALITY interoperability, DNS/IPv6 leaks, network switching, background/sleep/battery/OEM behavior and always-on blocking. No authorized remote test server or physical device was supplied. No automatic reconnect supervisor, independent kill switch or background subscription scheduler is claimed. Android refresh is manual; traffic graph shows only measured foreground samples. Normal app screens block screenshots; instrumentation-only captures contain no user data.

A debug signing identity can differ between builds: an existing APK may reject in-place upgrade. Do not uninstall blindly: uninstall removes the Android Keystore key and may make device-bound encrypted exports unreadable. There is no cross-device backup promise. Production signing and a broader legal/security review remain outstanding.

No accounts/payments/telemetry. Subscriptions contact their provider. A real connection health check contacts Cloudflare through your selected proxy; explicit TCP tests contact your server directly. Raw core logs remain disabled. Bundled runtime license texts and exact Go module versions are inside the APK and attached notices ZIP. See PRIVACY.md and docs/ANDROID_DESIGN.md.
