# Release blockers and unsupported features

This is a substantive implementation, **not the completed product requested in the specification**. In particular, the absence of the following is not disguised by an untested toggle.

## Verification blockers

- No Windows host: WPF launch/UI/RTL/keyboard/tray, UAC flow, Wintun installation, IPv4/IPv6 traffic capture, WFP leak protection, route/DNS restoration, suspend and forced-crash recovery are unverified.
- No Android device/emulator: runtime consent, QR camera permission, foreground lifecycle, UI/RTL/insets, service revocation, screen-off/background behavior, network switching and always-on/block-without-VPN are unverified.
- No authorized remote Xray test server credentials: remote VLESS/VMess/Trojan/REALITY/XTLS and real Internet tunneling are unverified. Local integration tests are not a substitute.
- No release-signing keys: no production-signed Android APK/AAB or Windows installer is provided.
- `v2.0.0` tag/public Release must not be created until the acceptance gate is satisfied. All current builds are testing builds with app version 2.0.0.

## Incomplete specification items

- Native interfaces are implemented but not visually reviewed on the actual platforms; no claim of production polish or complete localization of third-party scanner/system permission screens.
- No portable encrypted cross-device/password backup, secure share-link export or QR image decoding. Backups are same-device/account only.
- No standalone manual outbound creation screen (import a link, then use advanced outbound editing). Arbitrary full Xray JSON is intentionally unsupported; advanced single-outbound JSON is whitelisted. No profile-supplied file paths, API, env, extra inbounds or unsafe TLS flags.
- No automatic reconnection, startup/autoconnect/automatic server choice, persistent connection history or network-change retry supervisor. A failed probe stops the core; a later outage is not proactively detected.
- No server batch latency tests, cancellation UI, latency/availability sorting or fastest-server selection. Alphabetic/favorites search and a real connected-session HTTPS timing are available.
- Android subscription refresh is manual only. Windows scheduling runs only while the app is running and disconnected, at one-minute resolution. No OS background scheduler. No full subscription edit/group-management UI.
- No user-configurable Android per-app routing or strict app-level kill switch; own-UID bypass is explicit. Use Android's system always-on and block-without-VPN after manual validation.
- No native Windows notifications, startup registration, installer/uninstaller or persistent VPN service. Portable removal: disconnect, quit, then remove the extracted folder. Vault removal is separate and explicit. Windows position is not persisted; window size is.
- No light theme. Dark is the only theme, so it is implicitly persistent; language is stored explicitly.
- Core log verbosity is fixed to none for privacy; only safe session diagnostic events are shown. No raw core-log viewer or complete traffic-history charts.
- DNS choices are validated resolver IPs, not a full DoH/DoT bootstrap policy editor. User-defined core rules may intentionally bypass; do not assume all traffic is proxied in split mode.
- Android targets API 26+ on arm64/x86_64 only; 32-bit Android is not built.
- Crash cleanup is delegated to native core/OS adapter lifetime. No independent recovery watchdog/journal has been implemented; never assume routes and physical adapter state always recover after hard termination.
- Wintun's ZIP checksum is pinned from its HTTPS vendor source; independent signature validation is implemented in Windows packaging CI, not performed on Linux. Full transitive license obligations and signed-runtime packaging still require release review.

## Gate before a public release

1. Complete the functional gaps above or explicitly reduce the release scope with the product owner.
2. Pass all platform rows in TESTING.md, including forced-crash and network-switch tests with evidence.
3. Test every advertised remote protocol/transport against an authorized endpoint; never use example credentials.
4. Review dependencies, licensing, leaks, logging, privilege boundaries and installer/signing policy.
5. Build from the final commit, inspect signatures, run artifact smoke tests and record checksums.
6. Only then tag `v2.0.0` and publish **only those verified artifacts**.
