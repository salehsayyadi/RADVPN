# Windows redesign and installer — implementation checklist

- [x] Inspect the existing WPF client, native Go/Xray boundary, Android module, tests, scripts and failed hosted CI.
- [x] Inspect the supplied RED VPN multi-screen image; retain RADVPN branding.
- [x] Suspend legacy automatic prerelease publication while repairing the application.
- [x] Replace the button-stack UI with reusable themed native WPF navigation, panels, inputs, dialogs and circular connection control.
- [x] Implement functional Home, Servers, Subscriptions, Configs, Profiles, Traffic, Settings, Logs, Advanced and About pages without fake data.
- [x] Preserve the tested Xray native engine, local SOCKS/HTTP, encrypted vault and Android sources.
- [x] Add bounded real TCP endpoint tests, cancellation and measured best-server selection; label TCP vs proxy HTTPS separately.
- [x] Add durable measured traffic totals, diagnostics, atomic vault backup/recovery and explicit user startup registration.
- [x] Add a real NSIS x64 installer, shortcuts, app icon, upgrades and safe uninstall preserving user profiles.
- [ ] Fix cross-platform CI line endings and Android SDK bootstrap; build and exercise Windows installer/UI on a hosted Windows runner.
- [ ] Inspect actual WPF screenshots at 1280x720 / 1440x900 in English/Persian.
- [ ] Publish only genuinely produced artifacts with explicit signing, network-test and device-test limitations.

Baseline: shared Go and Windows core tests passed in the first hosted run; the original Windows job failed at formatting and Android failed during SDK setup. No public tag or Release exists at redesign start. Native full-tunnel/leak/crash-recovery acceptance remains NOT RUN until there is actual evidence.
