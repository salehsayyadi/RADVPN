# RADVPN 2.0.1 publication receipt

- Public release: https://github.com/salehsayyadi/RADVPN/releases/tag/v2.0.1
- GitHub draft: false; prerelease: false. One APK and one Windows installer.
- Immutable source/tag SHA: `569c27a7b6f99a1c949a071869fe1d73fa55e726`.
- Android acceptance: https://github.com/salehsayyadi/RADVPN/actions/runs/40499361210
- Shared/Windows acceptance: https://github.com/salehsayyadi/RADVPN/actions/runs/40499361261
- Publication: https://github.com/salehsayyadi/RADVPN/actions/runs/40501446640

| Installer | Bytes | SHA-256 |
|---|---:|---|
| RADVPN-Android.apk | 71419483 | `20deb69751e090c08802b9c73423e3f3bab03fb2e3595910eca1361556e2fabd` |
| RADVPN-Windows-Setup.exe | 56635141 | `626919c913c068aaa1b9f2d5eea196e018b4b3a47309db8ff8d59eb20c91786a` |

Android: actual installed API35 emulator at two phone sizes, both languages,
64 native captures, actual Go boundary/parser checks, independent subscription
groups, privacy-switch flags, Keystore tamper rejection, signature/lint/license
checks. Shared local proxy/data integration and race tests passed. Windows:
56 native offscreen WPF captures, native self-tests, per-user install/launch,
same-version repair and uninstall checks passed.

**Signing is unchanged in trust level:** Android CI testing/debug certificate;
Windows unsigned. Previous Android CI certificates can differ, preventing an
in-place upgrade; do not uninstall blindly because local Keystore data is lost.
Physical-device VPN leak, recovery, battery, remote-provider interoperability and
physical Windows display acceptance remain NOT RUN. GitHub release status is
not a security certification. See the release notes and evidence scope.
