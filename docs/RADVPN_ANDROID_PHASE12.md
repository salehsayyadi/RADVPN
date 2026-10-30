# Android phase 12 — account/session controls and acceptance

## Implemented

The client retains `app.radvpn`, versionName `3.0.3`, versionCode `30003`, Android 8+ and arm64-v8a/x86_64. Release packaging still refuses to build without the original compatible signing configuration. No replacement production signing key is generated or committed.

Existing login-first, canonical `https://api.bestget.ir/`, Telegram registration, shared code/independent session, Keystore storage, validated restoration, heartbeat/revocation gates and shared account metrics remain intact. The code input now uses the existing dark design tokens, masked text, a 48dp minimum height and disabled autofill/personalized learning. Codes are not persisted.

Account rendering is extracted to `AccountScreen.kt`; there is no new authentication bypass or alternate navigation path. Authenticated accounts have a visible **Manage devices** action. It lists active device names/platforms, identifies the current session, confirms individual revocation and calls the new bearer-authorized `/v1/account/sessions` API routes. Revoking the current session clears account-managed data and returns to login; revoking another device refreshes account/capacity information. API identity, never frontend bot/user parameters, determines scope.

The corresponding API authorization regression tests passed against disposable MariaDB: customers cannot list/revoke another account's or tenant's sessions; bot API keys and Telegram owner proofs do not authorize these native bearer routes; selected/self revocation and capacity recovery work without duplicating subscriptions. Source delivery is on `vpn-sell-bot` branch `RADVPN`.

## Executed local checks

- `python scripts/check.py`: version metadata and 324 English/Persian keys passed.
- `bash -n scripts/test-android-account-gate.sh`: passed.
- `git diff --check`: passed.
- Sales API/command/wallet suite including native device authorization: 93 tests passed with disposable MariaDB.

No Android SDK/emulator is present locally. Kotlin compilation, installed first-launch/UI tests and actual Keystore tests must be confirmed from the resulting GitHub Actions source SHA before reporting them as passed.

## Added installed debug-APK acceptance

The read-only, non-publishing candidate workflow now builds Android instrumentation and runs **AccountGateTest only** on an API35 emulator at two phone sizes. It exercises the actual packaged native engine, first-screen lock (including incoming shared text), all protected capture targets remaining locked in both languages, masked code input, horizontal control bounds, canonical origin rejection, exact Telegram registration link, real Keystore encryption/tamper rejection and save/load/managed-data cleanup in an isolated test directory. Four actual login-screen captures and structured reports are uploaded.

The standard disposable **debug** signer is test-only and is not represented as the missing original production identity. This is not an in-place production upgrade, live authenticated session test, physical-device VPN test or real Telegram opening. The historical full-dashboard `NativeUiTest` expects unauthenticated pages from the old design and is not the candidate gate; its authenticated dashboard acceptance still needs a proper authenticated test environment, not a test backdoor.

## Blocked external acceptance

Original compatible production signing material is unavailable. Previous native run 41038911838 explicitly skipped both the production build and old/new signer continuity checks. Therefore a real old-production-APK → new-production-APK `adb install -r` test, retained production settings/login and signed production APK are **BLOCKED BY EXTERNAL REQUIREMENT**, not passed. The release key must be restored securely and its certificate compared with the actually published APK before upgrading; do not uninstall the old app as a workaround.

Production TLS is still invalid for the canonical host and there is no authorized server deployment connection. Live login/session revocation/API connectivity and actual app session-management UI with production data remain unverified. No release, immutable tag, existing signing identity or publication approval file is changed here.
