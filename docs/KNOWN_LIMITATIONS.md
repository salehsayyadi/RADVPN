# Release blockers and unsupported features

This is a substantive implementation, **not the completed product requested in the specification**. In particular, the absence of the following is not disguised by an untested toggle.

## Verification blockers

- No local Windows desktop. Hosted Windows install/launch/repair/uninstall and offscreen native UI tests passed; see their recorded results. Physical-adapter TUN, UAC, IPv4/IPv6 capture, WFP leak behavior, DNS/route restoration, sleep and forced-crash recovery remain unverified.
- No Android device/emulator: runtime consent, QR camera permission, foreground lifecycle, UI/RTL/insets, service revocation, screen-off/background behavior, network switching and always-on/block-without-VPN are unverified.
- No authorized remote Xray test server credentials: remote VLESS/VMess/Trojan/REALITY/XTLS and real Internet tunneling are unverified. Local integration tests are not a substitute.
- No release-signing keys: no production-signed Android APK/AAB or Windows installer is provided.
- A reviewed experimental prerelease may distribute only actually built and native installer-tested artifacts. It must not be described as production-ready or as verified full-device protection.

## Current scope and omissions

- Windows WPF was redesigned against the supplied multi-screen reference. Native captures and installer acceptance are generated on Windows CI; their actual results must be read in TESTING.md, not inferred from workflow source.
- No portable cross-device/password-encrypted backup or desktop QR image decoding. Backups are device/account bound. Android retains its scanner.
- Import supported links or a bounded outbound-only JSON object/list. Advanced single-outbound editing is whitelisted and validated by the pinned core; arbitrary full Xray configs, file paths, API and extra inbounds are rejected.
- No automatic reconnection, network-change supervisor or persistent failure-history database. Windows supports explicit **proxy-only** auto-connect at startup; Tunnel requires a deliberate elevated connection. No silent UAC or driver installation.
- Windows real direct TCP endpoint probes are bounded to 256 profiles, concurrency 4, 3-second timeout, with cancellation; measured best TCP does not mean best VPN handshake or Internet path.
- Android subscription refresh remains manual. Windows intervals run only while the app is open and disconnected; no OS background scheduler.
- Android has no user-editable per-app routing or app kill switch. Use system always-on/block-without-VPN only after device verification.
- Windows is a per-user desktop app, not a privileged persistent VPN service or independent crash-recovery watchdog. Native core/OS cleanup after forced termination remains unverified.
- Dark theme only. Raw core logs are disabled for privacy; safe localized lifecycle event codes are available. Historical traffic totals cover samples actually collected by the running app, not traffic while closed or unknowable server history.
- Custom DNS accepts resolver IPs, not a full DoH/DoT bootstrap editor. Split direct rules intentionally bypass the proxy. IPv6 tunnel/block policies are implemented but require actual Windows leak verification; IPv6-block mode requires IPv4 DNS.
- Android supports API 26+, ARM64/x86_64; no 32-bit APK or production signing identity. Windows installer is unsigned; SmartScreen warnings are expected.
- Bundled Wintun has pinned vendor ZIP SHA256 and Windows packaging validates Authenticode. App-owned driver state must never be removed blindly. Complete dependency/license and security audits remain outstanding.

## Gate before a production-ready release

1. Complete the functional gaps above or explicitly reduce the release scope with the product owner.
2. Pass all platform rows in TESTING.md, including forced-crash and network-switch tests with evidence.
3. Test every advertised remote protocol/transport against an authorized endpoint; never use example credentials.
4. Review dependencies, licensing, leaks, logging, privilege boundaries and installer/signing policy.
5. Build from the final commit, inspect signatures, run artifact smoke tests and record checksums.
6. Only then publish a production-ready follow-up version. The v2.0.0 experimental prerelease is explicitly not that certification.
