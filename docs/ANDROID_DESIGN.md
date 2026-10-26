# Android design update 1 — 2.0.0 / versionCode 20001

Native Android Views, not WebView or a screenshot background. Follows the supplied multi-screen reference: near-black/blue-black panels, restrained orange accents, RADVPN shield/lightning vector, central ring power control, bottom Home/Servers/Configs/Settings navigation, server cards, subscription import, settings and diagnostics. Additional pages are in the top overflow menu. No sample servers or fabricated traffic/latency.

Preserves the actual gomobile Xray engine, Android VpnService consent and foreground lifecycle, protected sockets, IPv4/IPv6 TUN request, encrypted Keystore vault, clipboard/file/QR imports and profile management. Adds bounded four-worker direct TCP endpoint tests (3s timeout, max256) with real best-TCP selection. These probes are not VPN handshake measurements; direct tests contact the chosen endpoints outside the tunnel. Traffic is current-session measured counters; no historical totals are implied.

Language switches immediately between English and Persian with a mirrored native RTL layout, LTR protocol/URL inputs and an unmirrored RADVPN mark. Settings validate through the actual shared core before saving. No unsupported theme/kill-switch/auto-reconnect toggles are shown.

## Verification gate

`.github/workflows/android-design.yml` builds the actual APK + instrumentation APK, collects Android-selected Go licenses and resolved Maven runtime notices, runs lint/signature checks, and exercises the installed native APK on an API35 x86_64 emulator at two phone viewports (360×720 and 390×844 dp). It captures all 11 pages in both languages and tests core import/defaults, invalid DNS, real local port conflict and Android Keystore tamper rejection. Successful compilation is not a substitute for those reports or visual inspection.

The final strict build/lint and installed-emulator gate passed at source `1adc48804f02b8900baaeaa16ed2e542c871f3d5`, run40390344459 attempt2. The reviewed APK is published under the separate `v2.0.0-android.1` prerelease. See [publication receipt](ANDROID_RELEASE_RECEIPT.md) for actual checksums, test scope and public-asset verification. Windows v2.0.0 was not moved or replaced.

## Limits

Debug/testing signature only; no production Android keystore is available. A debug key from another build may prevent an in-place upgrade. Back up device-bound data before uninstalling; uninstall may destroy the Keystore key, making that backup unreadable. No cross-device restore promise is made.

Emulator UI/Keystore/local-core checks do not prove physical-device VPN consent, full-device traffic protection, remote VLESS/REALITY interoperability, DNS/IPv6 leak resistance, Wi-Fi changes, screen-off/background/battery behavior or always-on blocking. These remain NOT RUN without an authorized device/server. No telemetry/account/payment. The connection health check contacts Cloudflare through the selected proxy; subscriptions contact only their provider. See PRIVACY.md.
