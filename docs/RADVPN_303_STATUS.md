# RADVPN 3.0.3 — Windows + standalone Android testing; full E2E not accepted

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

Candidate CI is read-only and cannot publish. Previous stable approval files, tags and release assets are untouched. A future v3.0.3 release must be `prerelease=true`, `make_latest=false`, contain only actually tested artifacts with checksums, and prominently disclose remaining limitations. **The v3.0.3 testing Pre-release is published, including Windows and a later separately installable Android testing APK. This does not complete the full ecosystem or original-key Android upgrade.**

## Coordinated sources

- `salehsayyadi/vpn-bot-maker`, main (PR #2 merged as 364ef9303f78121b79e1b5a0fce18c71b14a3d6e for controlled testing): per-instance branch-aware updater, source/image isolation and preserved env.
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

## Publication update — supersedes earlier artifact-download limitations

Published Windows-only testing Pre-release: https://github.com/salehsayyadi/RADVPN/releases/tag/v3.0.3

Publisher CI https://github.com/salehsayyadi/RADVPN/actions/runs/41026664372 downloaded the pinned final Actions artifact through authenticated GitHub access, verified its complete digest, binary checksums, native/UI/actual upgrade reports, uploaded assets into a private draft, verified uploaded asset digests, then published with prerelease=true and make_latest=false. Stable/Latest remained v2.0.3 (release ID 425695186). The immutable v3.0.3 tag points to tested source 3c810acab5bcc907d7874a74a7c40b05fa7465ab.

Final published binary SHA-256:
- RADVPN-Setup-3.0.3-x64.exe: `e5c812e57173ba4daafcb23b938271b8f9c506ac3985c82eb243631d0ba41e0c`
- RADVPN-Windows-Portable-3.0.3-x64.zip: `d6d6c7fb573656acb18cbb835b8f2da127f63b9a2334cc2c55196b18eaeb4c6f`

Published SHA256SUMS, VERIFIED_BUILD.json and upgrade report were independently downloaded and checked against GitHub asset digests after publication. The initial Windows publication had no Android APK; the later separate-install testing APK is documented below. Android original-key/device-upgrade, server API deployment and live Telegram/panel/payment/native E2E remain blocked or untested. Updating Bot Maker and a bot does NOT deploy the central API. Use a backed-up staging bot, not a customer-wide rollout.

## Standalone Android APK — user-requested follow-up

The same testing Pre-release now includes `RADVPN-Android-3.0.3-TEST-standalone.apk` (71,459,635 bytes), package `app.radvpn.testing`, label `RADVPN Test 3.0.3`, version `3.0.3-testing`. Production default identity `app.radvpn` and its original-key release gate remain unchanged. This test APK uses an isolated debug signing identity, NOT a replacement production key; future test signer persistence is not guaranteed. It installs alongside the existing app with separate data and does not require uninstalling it. Original-key update compatibility is explicitly NOT claimed.

Source: `a18ad6f46eb0867daf763eb4f1b7842c53804a08`; actual build/install/publication CI: https://github.com/salehsayyadi/RADVPN/actions/runs/41028222584 (success). Verified distributed v2.0.3 APK was installed on an API35 x86_64 emulator; the test APK then installed alongside it, original package/version remained present, test app launched, account page opened, masked login input was verified and no fatal crash/JNI load failure occurred. Remote VPN/live API/Telegram/payment E2E and production upgrade remain untested.

APK SHA-256: `95b284945796fa5d638fc4e3466c68c6265a947e2fd4900de0361f055c8dd2e4`. Release includes separate ANDROID-TEST-SHA256SUMS.txt, ANDROID-TEST-PROVENANCE.json, install report and an actual account-page screenshot. These reports were independently downloaded and checked against GitHub asset digests after publication. Existing Windows asset digests are unchanged; Latest remains v2.0.3. The v3.0.3 tag still points to the already verified Windows source, not the later Android sandbox variant; Android source provenance is explicit in its separate JSON/report.

Download: https://github.com/salehsayyadi/RADVPN/releases/download/v3.0.3/RADVPN-Android-3.0.3-TEST-standalone.apk

Central API deployment is still required for account login. Publishing apps or updating a bot does not deploy that service. Manual VPN configuration can be tested separately without an account.
