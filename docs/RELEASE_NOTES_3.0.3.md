# RADVPN 3.0.3 — Windows testing pre-release

**Testing only. This is NOT a completed/production-verified multi-platform ecosystem release. Do not deploy to all customers.**

## Included
- Windows x64 installer and portable ZIP, version 3.0.3.0. Installer remains unsigned/experimental.
- Shared-code RADVPN account entry/dashboard, encrypted session persistence, managed subscription import, Telegram purchase link and session-revocation checks.
- Verified updater implementation (its live install/download flow has not been exercised).
- SHA256SUMS, verified artifact provenance and actual native upgrade/test reports.

## Actually tested
Pinned source: `3c810acab5bcc907d7874a74a7c40b05fa7465ab`.
CI: https://github.com/salehsayyadi/RADVPN/actions/runs/41024307785

- Actual distributed v2.0.3 installer -> v3.0.3 in-place upgrade, without uninstall.
- Existing encrypted fixture unchanged at installation; profile, selection, subscription, language and connection settings retained after native launch.
- Actual old portable ZIP overwritten with candidate; native checks and retained state verified.
- Native IPC/DPAPI/core checks and 72 offscreen WPF layout captures including the account page.
- Complete Actions artifact digest, individual binary checksums and uploaded release asset digests verified before publication.

These tests do NOT establish physical remote VPN safety, interactive installer consent, live account migration or production E2E functionality.

## Coordinated bot/API source and test order
1. Back up the Maker registry, existing bot databases/configuration and user data first.
2. Bot Maker per-bot branch updater is merged into `salehsayyadi/vpn-bot-maker/main` (PR #2). Fetch through the normal Maker source update, then use a STAGING bot only. Actual Docker two-bot deployment/rollback and production registry migration are not tested.
3. Sales-bot integration and separate owner/customer Mini Apps are on `salehsayyadi/vpn-sell-bot`, branch `RADVPN`; ordinary `main` intentionally remains the non-RADVPN source.
4. Deploy the independent central API and additive tables in the approved existing MySQL schema BEFORE enabling integration. Updating Maker or the sales bot does not deploy that service.
5. Configure the staging bot's private API URL, per-bot key and supported panel; choose its `RADVPN` source branch and update ONLY that bot.
6. Install this Windows candidate manually, use the operator's HTTPS endpoint and `/rejecterapp` code; perform real Telegram/panel/payment/native-client acceptance tests.

Detailed deployment/contracts/recovery: https://github.com/salehsayyadi/vpn-sell-bot/blob/RADVPN/docs/RADVPN_303.md

## Explicit blockers / exclusions
- **NO Android APK.** Go AAR/Kotlin/lint passed, but at least one original-key signing secret is missing. Original-key compatible signing and actual Android in-place upgrade are not verified. No replacement signing key was generated.
- Central API is implemented but not deployed to the operator's server. Live Telegram delivery, panel create/usage/traffic, existing gateway/card top-up, cross-repository E2E and native in-app updater installation remain untested.
- RADVPN panel provisioning currently supports 3x-ui only. Other RADVPN panel adapters are not implemented.
- Central device capacity limits app sessions, not exported raw panel configurations. API revocation is immediate; native disconnect follows periodic validation/offline-lease policy.

This publication uses `prerelease=true` and `make_latest=false`. Existing Stable/Latest is not replaced. No deployed bot, database or production service is modified by publishing these assets.
