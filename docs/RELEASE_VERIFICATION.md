# Public release verification — 2026-10-26

- Repository: https://github.com/salehsayyadi/RADVPN (public).
- Default branch: `main`.
- Built source/tag commit: `fd4e39ad150b18f55321d5de0a38ce9468693a48`.
- Tag: `v2.0.0`, created only after confirming there was no existing tag; never moved.
- Successful full source CI: https://github.com/salehsayyadi/RADVPN/actions/runs/40374733104 (shared, Windows and Android all succeeded).
- Explicit approval-only commit: `3c5a17c9dc1c1cd51e80e993c2474ad200348b6a`; it did not modify the built application.
- Successful publication workflow: https://github.com/salehsayyadi/RADVPN/actions/runs/40375641564.
- Release title: RADVPN 2.0.0.
- Public release: https://github.com/salehsayyadi/RADVPN/releases/tag/v2.0.0.
- Channel: **experimental unsigned prerelease**, not production ready. GitHub's release/tag immutability setting is not enabled; the workflow refuses to overwrite an existing tag.
- Installer: https://github.com/salehsayyadi/RADVPN/releases/download/v2.0.0/RADVPN-Setup-2.0.0-x64.exe.
- Installer size: **56,626,918 bytes**.
- Installer SHA-256: `0b2758184e95d5f45b803e8d638f65ada4277a47e2bb2b4dfbbdc0d58479e302`.

After publication, all six asset URLs were downloaded without authentication and returned HTTP 200. The five payload files matched the published SHA256SUMS.txt. The installer digest also matched the original successful CI artifact. The UI evidence ZIP contains 58 native PNG captures. These checks verify delivery/integrity, not VPN privacy protections.

Assets: Windows x64 installer, SHA256SUMS.txt, installer-test-report.txt, native-tests.json, ui-report.json and RADVPN-2.0.0-native-ui-evidence.zip. The Android debug APK remains CI-only, not a public release asset, pending device acceptance and full mobile redistribution-notice review.

See TESTING.md and KNOWN_LIMITATIONS.md for actual test commands and NOT RUN items. Physical-device TUN, DNS/IPv6 leaks, forced-crash route restoration, remote VPN server interoperability, interactive desktop/installer/SmartScreen and Android runtime VPN behavior remain unverified. RADVPN and setup are unsigned; no production signing claim is made.
