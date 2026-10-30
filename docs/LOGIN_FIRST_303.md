# Mandatory login checkpoint (phase 5)

## Implemented
- Windows and Android render account login before any protected dashboard/navigation on a new launch. Existing saved sessions are revalidated against **https://api.bestget.ir/** before unlocking.
- Login uses the shared code; each successful API login stores its independent session through the existing protected state store. Endpoint editing was removed from the login UI, and noncanonical API endpoints are rejected.
- Telegram registration opens **https://t.me/RadVpn7bot?start=register** with an explicit explanation that the user may need to press Start. Opening the link does not claim that `/rejecterapp` was sent.
- VPN connection entry points (including manually imported profiles) require a valid session. Revocation clears the account and managed account data, disconnects the VPN and returns to login; unrelated manual profiles/subscriptions are retained.
- Existing dark/orange controls and bilingual localization are retained. Account restoration can be retried without issuing another session when the network is unavailable.

## Verification boundaries
- Local locale/version checks: **319 bilingual keys passed**.
- Local Windows Release compilation: **0 warnings, 0 errors**. This is compilation, not a Windows GUI execution or upgrade test.
- Android compilation and Windows runtime/UI/upgrade checks must be read from the candidate GitHub Actions run for this source. No Android SDK was available locally.
- Windows smoke navigation intentionally stays locked without a server-validated session. Its route captures assert the login gate; they do NOT demonstrate authenticated dashboard layout coverage.
- The production API currently has unresolved TLS/deployment dependencies; real account login and real revocation on devices are not verified.
- Original Android production signing identity is unavailable: a production upgrade is blocked. No replacement key was generated, no approval file changed, and no release was published by this change.
