# Automatic account subscription loading — 3.0.3 candidate

## Behaviour

Android and Windows now fetch the existing authenticated account's subscription
immediately after code login, after persisted-session validation, and on later
successful account heartbeats if import is still pending. No new server account,
login code, device session or VPN subscription is created by this importer.
The first usable imported profile is selected when there is no valid selection;
the VPN is **not** automatically connected. Existing user selections are retained.

Managed group identity remains `radvpn-account-{account_id}`. Repeated logins and
restores upsert that group, update a changed subscription URL and avoid duplicate
subscriptions/profiles. An exact-URL manual copy is adopted into the managed group;
unrelated manual subscriptions and profiles are preserved. Names and profile
preferences are retained. Empty/failed parses do not create empty subscriptions or
erase previously usable profiles. A partial parse preserves previous profiles.

Import fetches run asynchronously. Results are committed only while the original
account/session and data object are current, the subscription URL is unchanged,
and the VPN is idle. Logout, revocation and data replacement win over late results.
Network/provisioning failures preserve the authenticated session and retry after a
later successful account heartbeat (existing interval: approximately 30 seconds).
The existing manual account import button remains a safe retry/refresh action.

## Validation evidence and limits

**Tested and passed:** compiled Kotlin/JVM fixture tests (Kotlin 1.9.24, Java 17,
org.json 20240303) and .NET 8 fixture/orchestration tests using controlled local
subscription results, not real credentials. Coverage:

- first import and usable profile selection;
- idempotent repeated restore/import;
- pending provisioning and subsequent retry;
- empty response / staged failure preservation;
- changed subscription URL;
- exact-URL adoption, duplicate cleanup, custom names and unrelated profiles;
- single-flight concurrent requests;
- logout during fetch and stale-result rejection;
- failed fetch followed by automatic retry;
- already-loaded skip and active VPN preservation (Windows coordinator fixture).

Android fixtures are also invoked by the installed `AccountGateTest`; Windows
fixtures are invoked by `NativeSmoke.Checks`. Those platform-native executions,
full APK/Windows builds, live authenticated production API login, real VPN traffic
and upgrade installation have **not been run in the local Linux fixture harness**.
Do not label those checks passed based on these unit results. The existing
`radvpn303.yml` feature-branch pipeline remains the platform validation route.
This change does not itself publish a release or replace already downloaded apps.

## Reproduce isolated tests

Windows coordinator/model test (also runs on Linux with .NET 8):

```sh
dotnet run --project tests/account-subscription/Regression.csproj
```

Android importer/model test without an Android runtime (Java 17, `kotlinc` 1.9.24,
and the org.json library):

```sh
JSON_JAR=/path/to/json-20240303.jar \
KOTLIN_STDLIB_JAR=/path/to/kotlin-stdlib.jar \
bash tests/account-subscription/run-jvm.sh
```

These harnesses substitute only transport/display boundaries. They do not bypass
application authentication and do not make a live-backend or native-UI test claim.
