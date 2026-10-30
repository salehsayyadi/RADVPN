# RADVPN 3.0.3 — implementation candidate, release blocked

This branch is **not an accepted end-to-end release** and must not be published as stable/Latest.

## Architecture / changes

Windows remains .NET 8 WPF + existing Go/Xray engine, NSIS per-user installer and portable ZIP. Android remains Kotlin/native Android + the existing Go AAR. Dark/orange visual components and existing manual VPN/subscription workflows are retained.

Both versions are 3.0.3; Android keeps `app.radvpn` and increases versionCode from 20004 to 30003. Accounts use a central HTTPS endpoint supplied by the bot owner, shared login code, independent bearer session, traffic/subscription/capacity dashboard and Telegram purchase link. Tokens/account data are persisted in the existing Windows DPAPI and Android Keystore AES-GCM vaults, not ordinary preferences. Login code is not persisted. Account-managed profiles are distinct from pre-existing manual profiles. Revocation/logout removes managed profiles and stops their connection; manual profiles are preserved. Native clients validate before connection; background checks periodically enforce revocation/short offline lease. This does not invalidate exported raw panel configurations.

Windows update checks now inspect GitHub release metadata, distinguish testing releases with explicit consent, require SHA-256 asset digests, verify complete bounded downloads, then launch the installer or reveal the portable ZIP with safe extraction instructions. Android adds bounded HTTPS downloads, SHA-256, package/version/signing-identity checks and PackageInstaller with installation permission handling; no uninstall/data-reset path is used. These new update download/install flows are **implemented but not live-tested**.

## Android update root cause and blocker

Existing build scripts produced Debug APKs; the signing configuration did not persist the CI runner's private debug keystore. Different CI runs can sign the same `app.radvpn` with incompatible keys. This is the documented likely cause of Android refusing overwrite, **not a diagnosis of every user's installed APK**. An APK contains the certificate, not the signing private key.

The new release signing config requires the existing keystore and fails closed without it. The candidate CI compiles Kotlin/lint without generating any signing key; it builds a signed APK only if all four existing signing secrets are configured. It then compares the actual previous APK certificate/package/version to the candidate. No replacement key is generated. **Static matching certificates are not an actual upgrade test.** Android `adb install -r`, encrypted-vault preservation, launch, VPN and updater installation still require verification with the original key and a device/emulator. Release is blocked until that is done.

## Native build/test evidence at initial source commit

- PASS: Windows cross-compilation using repository-pinned SDK 8.0.427; zero errors/warnings.
- PASS: Go `go test -race -count=1 ./...`; locale/version consistency checks.
- PENDING: candidate GitHub CI for actual Windows installer/portable builds and previous v2.0.3 -> v3.0.3 upgrades with encrypted fixture preservation.
- PENDING: Android Kotlin compile/lint in candidate CI; signing availability check. Do not infer success from committed source.
- NOT RUN: live central API/native-client E2E, physical-device VPN regression, real Android upgrade or in-app update installation.

Candidate CI is read-only and cannot publish. Previous stable approval files, tags and release assets are untouched. A future v3.0.3 release must be `prerelease=true`, `make_latest=false`, contain only actually tested artifacts with checksums, and prominently disclose remaining limitations. **No v3.0.3 release has been published by this source change.**

## Coordinated sources

- `salehsayyadi/vpn-bot-maker`, main: per-instance branch-aware updater, source/image isolation and preserved env.
- `salehsayyadi/vpn-sell-bot`, RADVPN: independent API, additive schemas, opt-in Telegram integration, separate Mini Apps and existing-wallet checkout. See that branch's `docs/RADVPN_303.md` for endpoints, deployment, recovery, tests and unsupported adapters.
- `salehsayyadi/RADVPN`, feature/radvpn-3.0.3: this candidate. Do not merge/release as completed while blockers remain.
