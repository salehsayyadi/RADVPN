# RADVPN 3.0.3 — implementation candidate, release blocked

This branch is **not an accepted end-to-end release** and must not be published as stable/Latest.

## Architecture / changes

Windows remains .NET 8 WPF + existing Go/Xray engine, NSIS per-user installer and portable ZIP. Android remains Kotlin/native Android + the existing Go AAR. Dark/orange visual components and existing manual VPN/subscription workflows are retained.

Both versions are 3.0.3; Android keeps `app.radvpn` and increases versionCode from 20004 to 30003. Accounts use a central HTTPS endpoint supplied by the bot owner, shared login code, independent bearer session, traffic/subscription/capacity dashboard and Telegram purchase link. Tokens/account data are persisted in the existing Windows DPAPI and Android Keystore AES-GCM vaults, not ordinary preferences. Login code is not persisted. Account-managed profiles are distinct from pre-existing manual profiles. Revocation/logout removes managed profiles and stops their connection; manual profiles are preserved. Native clients validate before connection; background checks periodically enforce revocation/short offline lease. This does not invalidate exported raw panel configurations.

Windows update checks now inspect GitHub release metadata, distinguish testing releases with explicit consent, require SHA-256 asset digests, verify complete bounded downloads, then launch the installer or reveal the portable ZIP with safe extraction instructions. Android adds bounded HTTPS downloads, SHA-256, package/version/signing-identity checks and PackageInstaller with installation permission handling; no uninstall/data-reset path is used. These new update download/install flows are **implemented but not live-tested**.

## Android update root cause and blocker

Existing build scripts produced Debug APKs; the signing configuration did not persist the CI runner's private debug keystore. Different CI runs can sign the same `app.radvpn` with incompatible keys. This is the documented likely cause of Android refusing overwrite, **not a diagnosis of every user's installed APK**. An APK contains the certificate, not the signing private key.

The new release signing config requires the existing keystore and fails closed without it. The candidate CI compiles Kotlin/lint without generating any signing key; it builds a signed APK only if all four existing signing secrets are configured. It then compares the actual previous APK certificate/package/version to the candidate. No replacement key is generated. **Static matching certificates are not an actual upgrade test.** Android `adb install -r`, encrypted-vault preservation, launch, VPN and updater installation still require verification with the original key and a device/emulator. Release is blocked until that is done.

## Native build/test evidence

- PASS: Windows cross-compilation using repository-pinned SDK 8.0.427; zero errors/warnings.
- PASS: Go `go test -race -count=1 ./...`; locale/version consistency checks.
- PASS: actual GitHub Windows CI run 41023421920 at source 9e7898d0d58fc5841e4b12eac68e05b428dee2f8. Actual distributed v2.0.3 installer -> v3.0.3 in-place installation and previous portable ZIP overwrite succeeded. Encrypted fixture was byte-identical across installation; native launch retained existing profile, selection, subscription, language and connection settings. 72 offscreen WPF captures (four account-page captures), zero layout audit failures. No physical VPN, interactive installer consent or live API upgrade claim.
- PASS: Android Go AAR generation, Kotlin compilation and lint in the same actual CI run. BLOCKED: at least one original-key signing secret is absent; signed APK build and certificate-continuity steps were skipped. There is no Android release APK from this run.
- NOT RUN: live central API/native-client E2E, physical-device VPN regression, real Android upgrade or in-app update installation.

Candidate CI is read-only and cannot publish. Previous stable approval files, tags and release assets are untouched. A future v3.0.3 release must be `prerelease=true`, `make_latest=false`, contain only actually tested artifacts with checksums, and prominently disclose remaining limitations. **No v3.0.3 release has been published by this source change.**

## Coordinated sources

- `salehsayyadi/vpn-bot-maker`, feature/radvpn-3.0.3 (draft PR #2; main unchanged): per-instance branch-aware updater, source/image isolation and preserved env.
- `salehsayyadi/vpn-sell-bot`, RADVPN: independent API, additive schemas, opt-in Telegram integration, separate Mini Apps and existing-wallet checkout. See that branch's `docs/RADVPN_303.md` for endpoints, deployment, recovery, tests and unsupported adapters.
- `salehsayyadi/RADVPN`, feature/radvpn-3.0.3: this candidate. Do not merge/release as completed while blockers remain.

## Independently downloaded earlier Windows candidate (historical, not a release)

CI: https://github.com/salehsayyadi/RADVPN/actions/runs/41023421920

- RADVPN-Setup-3.0.3-x64.exe — 57,520,323 bytes; SHA-256 `40cf89e6e554b05e220ca3d36b030a85cae8a06024ffbd5e052f8510c44f64f4`
- RADVPN-Windows-Portable-3.0.3-x64.zip — 81,287,561 bytes; SHA-256 `794d30329a1a87fb53f28ea707b0ec055921d2db21de379ab58452a6ac958145`

Executable PE FileVersion verified as 3.0.3.0. Installer remains unsigned/experimental. Artifacts were retrieved through a public Actions mirror only after checking the complete archive against GitHub's official artifact SHA-256 (`2e8cacca4672d33d332ed42b593fca9991e50d66040973c14e588e9751b129c4`), then each asset against CI SHA256SUMS. These hashes describe this run only.

The first candidate run was rejected: installer labelled 3.0.3 contained executable 2.0.3.0 due to an omitted csproj edit; actual upgrade validation caught it. Committed csproj correction, actual-file-version gate and explicit UTF-8 Windows validation fixed subsequent runs. Do not use the first failed run's files.

## Final themed candidate

Final source `3c810acab5bcc907d7874a74a7c40b05fa7465ab` also corrects the dark account-code field styling and audits PasswordBox layout. CI https://github.com/salehsayyadi/RADVPN/actions/runs/41024307785 completed successfully: Windows build/actual installer and portable upgrade; Android AAR/Kotlin/lint. Android signed APK and certificate comparison were skipped, not passed.

Official Windows artifact: https://github.com/salehsayyadi/RADVPN/actions/runs/41024307785/artifacts/12963365843 (193,549,765 bytes). GitHub-reported archive SHA-256: `dda013860e0fdd2c8d6f30a0a2d96ae4bc80e00b0ccd544ed10de6f2496c9b36`. The public artifact mirror returned HTTP 500 service-wide at final handoff; this final archive was not locally retrieved/re-hashed and its individual asset hashes were not read. Use its included SHA256SUMS after official GitHub download. The earlier independently verified hashes above MUST NOT be used for this final themed run. No older binary is represented as the final artifact.
