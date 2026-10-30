# Windows phase 13 — own-device controls and account-preserving upgrade

## Implemented

The authenticated account page now exposes **Manage devices**, separate from the unauthenticated login-first screen. The native dialog loads `/v1/account/sessions`, lists active device name/platform/last-seen/current-device status and offers a real, confirmed per-device revoke action. Requests use only the current bearer session against the canonical API; no frontend tenant/user identity is trusted. A stale account-state reference cannot authorize an action after account replacement. API rejection clears the current revoked account; successful self-revocation returns to login, while other-device revocation refreshes capacity metrics. No owner/global operations are exposed.

`AccountSessionData.ClearManaged` is used by real logout/revocation and native tests. It removes only account-managed profiles/subscriptions and bearer state; manual profiles/subscriptions and user settings are retained. Connections are stopped and authentication flags reset before returning to login. The password control is cleared before starting a login request, including a failed request.

An additional restoration defect is fixed: decrypting a backup no longer preserves the current `accountReady` authorization flag. Restore stops the connection, resets the authentication lease, loads the backup, and validates any restored bearer against the canonical API before unlocking protected pages. Network failure leaves the user gated with retry available. Actual interactive backup-restore plus live-account verification remains untested without a working API.

## Executed local checks

- Release cross-target build with .NET8.0.427: **0 warnings, 0 errors**.
- Version/locale validation: **324 keys passed**.
- Diff whitespace validation passed.

Actual Windows-only DPAPI/UI/installer tests cannot run on the local Linux workspace. Their result must be checked for the resulting source SHA in the candidate workflow, not assumed from a local compile.

## New native and upgrade assertions

The existing actual native self-test now saves/reloads a non-authentic session fixture in an isolated DPAPI vault, verifies no plaintext fixture token is present and exercises the same managed-data cleanup used by the real client. Manual profile/favorite/subscription/language retention is checked. No real credential is used or printed.

The actual published v2.0.3 installer → candidate upgrade test additionally retains an encrypted non-authentic session fixture across installer upgrade and native launch. Existing directory/identity/profile/subscription/language/settings preservation and portable ZIP overwrite tests remain unchanged. This is evidence of **encrypted data preservation**, not evidence that a live session is valid after upgrade; `--ui-smoke` remains authentication-gated and never grants a test session access to protected dashboards.

Prior phase12 run41048271042 passed the actual Windows installer/portable upgrade and runtime tests and Android installed debug login-gate/Keystore tests. The new phase13 source still needs its own run. The original Android production signing build/continuity steps were skipped and remain blocked; no replacement production identity, publication approval, immutable tag or release is changed.

## Remaining external acceptance

Production canonical TLS has a hostname mismatch and there is no authorized server connection. Real authenticated native dialog, persisted live-session restoration/revocation and remote VPN connectivity cannot be claimed passed. The Windows installer remains explicitly unsigned/experimental; a successful installer test is not a production security certification or a stable/latest release.
