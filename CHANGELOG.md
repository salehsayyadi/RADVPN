# Changelog

## 2.0.0 — experimental unsigned prerelease

- Redesigned native Windows WPF Home, Servers, Subscriptions, Configs, Profiles, Traffic, Settings, Diagnostics, Advanced and About using centralized dark/orange tokens and English/Persian RTL.
- Added real bounded/cancellable TCP endpoint measurements, favorites/best TCP selection and encrypted daily measured traffic history.
- Added hardened DPAPI atomic vault backup/recovery, explicit per-user startup, tray/window state and strict outbound JSON import/editing.
- Preserved embedded pinned Xray, real loopback SOCKS5/HTTP, Tunnel integration, safe connection state/counters and native Android VpnService sources.
- Added self-contained NSIS x64 setup/uninstaller, Start Menu/optional desktop shortcut, same-version repair, safe user-data preservation and vendor driver verification.
- Passed hosted Windows installer/native IPC/storage/launch/repair/uninstall acceptance, actual compiled WPF offscreen English/Persian captures and Linux/Windows core tests; Android debug build/lint/signature checks pass separately.
- Windows-only release assets: actual tested installer, checksums and evidence. Android debug APK remains CI-only pending device/distribution-notice review.

This is not a completed production VPN. Physical-device TUN, DNS/IPv6 leaks, forced-crash route recovery, real remote protocols and interactive desktop/installer QA remain NOT RUN. See docs/TESTING.md and docs/KNOWN_LIMITATIONS.md.
