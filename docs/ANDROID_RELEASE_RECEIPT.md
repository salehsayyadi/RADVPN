# Android 2.0.0 design update 1 — publication receipt

## Public artifact

- Release: https://github.com/salehsayyadi/RADVPN/releases/tag/v2.0.0-android.1
- APK: https://github.com/salehsayyadi/RADVPN/releases/download/v2.0.0-android.1/RADVPN-2.0.0-android-design1-debug.apk
- Source tag `v2.0.0-android.1`: `1adc48804f02b8900baaeaa16ed2e542c871f3d5`.
- Version `2.0.0`, versionCode `20001`; Android API26+, ARM64 and x86_64.
- Size: **70,909,321 bytes** (67.62 MiB).
- SHA-256: `5522e41368112e935c792045a60c992850fb88772d833a00aa35e70dd52b97b9`.
- Experimental prerelease, **debug/testing signature**, not production/Play signing.

## Actual verification

Native build/lint/emulator run: https://github.com/salehsayyadi/RADVPN/actions/runs/40390344459 (successful attempt2). Attempt1 failed while downloading the Android Emulator SDK ZIP, before running the app. Same source was retried; no acceptance checks were bypassed.

Publication: https://github.com/salehsayyadi/RADVPN/actions/runs/40393189653 (success). The gate verified source/run identity, unused Android tag, strict build-log success, APK digest/metadata/signature record, two native test reports,48 screenshots, both native ABIs and bundled notices.

- Real gomobile Xray AAR + APK + instrumentation APK built; **lintDebug completed, BUILD SUCCESSFUL**. This does not mean zero warnings.
- Actual debug APK installed on API35 x86_64 emulator,360×720dp and390×844dp.
- Native core version/defaults, invalid import/DNS rejection, real occupied loopback port refusing startup, native stop, Android Keystore AES-GCM roundtrip/tamper rejection and locale parity passed.
-11 native pages in both languages plus English/Persian native dialogs at each size:48 actual captures. All initial captures reviewed individually; final unchanged page bodies pixel-compared, changed bodies/dialogs re-inspected. Dark/orange tokens, central power ring, native navigation, readable labels, RTL chevrons and LTR technical inputs checked. Longer forms scroll beneath persistent navigation; not every form action is above the fold.
- Public APK downloaded independently after publication; ZIP integrity, digest, native libraries and46 selected Go-module license entries checked. All8 public assets downloaded; every published checksum matched. Public screenshot ZIP entries matched all48 accepted images byte-for-byte. Public metadata, signature record and both reports checked.
- No sample servers, invented ping/counters or simulated Connected state. Capture instrumentation contains no user profiles and does not request real VPN consent.

The earlier run40387538302 was NOT approved for publication: its pipeline masked a lint failure. Pipeline strict error propagation and explicit build-log verification were added, then the final source was rebuilt and re-tested successfully.

## Design / functionality

Native Kotlin Views, not WebView or screenshot backgrounds. Adapts supplied reference to phone navigation: blue-black panels, orange accents, original RADVPN shield/lightning, ring power control, Home/Servers/Configs/Settings tabs, subscriptions, profiles, session traffic, safe diagnostics and About. Persian/English switch immediately. VpnService consent, native engine integration and encrypted vault remain real. Android subscription refresh is manual; historical traffic totals and unsupported kill-switch/background scheduler controls are not fabricated.

## Explicit limits

**NOT RUN:** physical-device VPN/consent/camera use, authorized remote VLESS/VMess/Trojan/REALITY connections, DNS/IPv6 leaks, switching networks, OEM battery/sleep/background recovery and always-on blocking. Emulator UI/local-boundary checks are not proof of whole-device privacy. Production signing and broader legal/security review remain outstanding.

An existing APK with a different debug signing key may reject in-place upgrade. Do not uninstall blindly: uninstall can destroy the Keystore key and make device-bound encrypted exports unreadable. No portable backup promise.

Windows is unchanged: `v2.0.0` remains `fd4e39ad150b18f55321d5de0a38ce9468693a48`; its previously published installer was not replaced. Android has its own tag/assets.
