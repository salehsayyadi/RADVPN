# Actual verification — 2026-10-26

## Scope and release decision

The Windows redesign has real hosted Windows installer/application smoke evidence. It is eligible only for an **experimental, unsigned prerelease**, not a production-ready VPN claim. The immutable source commit and successful CI run approved for publication are recorded in `.github/release-approval.json`; the release tag points at that built source, not a later documentation/approval commit. Test reports and native captures are attached to the release.

The earlier baseline has been superseded by native installation, UI, repair and uninstall checks described below. No physical-device full-tunnel protection has been verified.

## Commands executed and outcomes

| Command | Actual result and scope |
| --- | --- |
| `gofmt` source check, `go vet ./...`, `go mod verify` | PASS; Linux CI and local checks. |
| `go test -race -count=1 -v ./...` | PASS on Linux: 11 named tests, protocol subtests and five fuzz seeds; no race report. |
| `go test -count=1 -v ./...` | PASS on hosted Windows, including real local Xray SOCKS/HTTP traffic and cleanup. |
| `go test ./packages/engine -run '^$' -fuzz FuzzImport -fuzztime 20s -parallel 2` | PASS. Recorded final local run: 94,391 executions; CI also runs bounded fuzzing. Not a security audit. |
| `python3 scripts/check.py` | PASS; version 2.0.0 and 193 matching nonempty English/Persian keys. |
| `dotnet format whitespace apps/windows/RADVPN.csproj --verify-no-changes --no-restore` | PASS with pinned .NET SDK 8.0.427. |
| `./scripts/build-windows.ps1` | PASS on Windows 2022 runner: real self-contained WPF/x64 core build, Wintun checksum and Authenticode, third-party notices and NSIS installer. |
| `./scripts/test-windows-installer.ps1` | PASS: seven acceptance groups below. |
| Installed `RADVPN.exe --self-test` | PASS: real child IPC/version/defaults, invalid/valid imports, DPAPI persistence/atomic backup/corrupt-primary recovery, traffic arithmetic, real loopback TCP measurement, clean core stop. |
| Installed `RADVPN.exe --ui-smoke --capture-dir ...` | PASS: 56 actual compiled WPF page captures plus two native dialog captures. Scope is OFFSCREEN client layout, not physical window/display tests. |
| `bash scripts/build-android.sh` / Gradle `assembleDebug lintDebug` | PASS on Ubuntu CI; native ARM64/x86_64 library and Kotlin debug APK. Android UI was not redesigned in this Windows task. |
| `bash scripts/check-apk.sh` | PASS: debug APK signature/version/package checks. Not Android runtime VPN acceptance. APK remains a CI artifact, not a public asset in this Windows prerelease. |

Toolchains: Go 1.27.3, .NET SDK 8.0.427 / runtime 8.0.33, NSIS 3.12, JDK 17, Gradle 8.11.1, Android SDK/build tools 35 and NDK 28.0.13004108. Local development host is Linux; Windows execution above took place in GitHub's Windows 2022 runner, not locally.

## Seven Windows installer acceptance groups — PASS

1. Silent per-user installation and bundled application/core/vendor driver files.
2. Actual Start Menu shortcut target and installed location.
3. Installed native IPC, DPAPI persistence/recovery and real TCP measurement self-tests.
4. Actual compiled WPF pages rendered offscreen at 1280×720 and 1440×900 CLIENT viewports, English and Persian; layout audit reported no horizontal control overflow.
5. Start Menu shortcut launch, graceful quit and no remaining app-owned core process.
6. Same-version repair/upgrade preserves the encrypted user vault.
7. Uninstall removes application and shortcut, and preserves encrypted user data.

## Native visual review and its limits

Home, Servers, Subscriptions, Configs, Profiles, Traffic, General/Connection/Security/Advanced settings, Advanced outbound, Diagnostics, About and proxy Home were inspected in English and Persian at both client sizes. The native dark blue-black/orange design uses the supplied reference's palette and component language with RADVPN branding, not a claim of pixel-perfect reproduction. Empty states contain no sample servers or invented data. At 720px height, longer pages intentionally scroll while the primary connection action remains visible.

The hosted Windows service-session desktop constrains physical native windows. Capture uses a dedicated native WPF HwndSource to lay out the **actual compiled controls** at the requested client sizes. Reports retain the actual host-window size separately. It is not a browser mockup, but it is also **not evidence of a real monitor at those resolutions or interactive resizing**. Capture-only RTL root transforms and dialog-margin cropping were corrected during review. Real native dialog content is captured without pretending that the offscreen image tests interactive OS chrome.

No recorded screenshots contain credentials or real user profiles. Populated list interactions, keyboard/accessibility coverage and all real user workflows still need interactive Windows acceptance.

## What the automated network tests establish

- `TestProfiles`: supported VLESS/VMess/Trojan/Shadowsocks/SOCKS/HTTP combinations are accepted by the pinned Xray config builder, not merely a local JSON parser.
- `TestRejectUnsafe`, `TestJSONImportBoundary`, `TestSettings`, `TestWindowsIPv6Policy`: unsafe TLS, arbitrary fields/files/listeners, invalid ports/DNS/MTU/rules, trailing JSON and unsupported combinations are rejected. IPv6 policy/config validation does not prove leak prevention.
- `TestImport`, `TestLocalization`, `TestErrorsAreRedacted`: bounded import/deduplication, matching locales, safe error/IPC behavior.
- `TestCoreTrafficAndCleanup`: real local HTTP CONNECT upstream → Xray SOCKS → local HTTP destination, HTTPS-check mechanism with an authorized local test endpoint, real nonzero counters, shutdown and both listener ports closing.
- `TestPortConflict`, `TestStopIdempotent`: occupied local listener produces an error; repeated stop is safe.
- Native self-test TCP probe uses a real loopback listener. Direct endpoint timing is not VPN handshake timing.

These local tests do not exercise a remote encrypted VPN protocol, Internet server or physical TUN adapter. No authorized remote test credentials were supplied or invented.

## Explicitly NOT RUN / not complete

| Acceptance area | Windows | Android |
| --- | --- | --- |
| Physical desktop resizing, native OS dialogs, keyboard/accessibility and populated-screen manual QA | NOT RUN | NOT RUN |
| Interactive installer wizard, SmartScreen, UAC and Windows 10-specific clean-machine QA | NOT RUN | N/A |
| VPN permission, camera, foreground service, revocation and process recreation on a device | N/A | NOT RUN |
| Full-device IPv4/IPv6 traffic capture and DNS/IPv6 leak tests | NOT RUN | NOT RUN |
| Forced-crash route/DNS restoration and physical adapter recovery | NOT RUN | NOT RUN |
| Network switching, sleep/background and always-on blocking | NOT RUN | NOT RUN |
| Remote VLESS/VMess/Trojan/REALITY/XTLS protocol interoperability | NO SERVER | NO SERVER |
| Production signing | UNSIGNED | DEBUG ONLY |

## Issues fixed during implementation

Replaced the old button-stack desktop with native pages; added real endpoint probes/history; hardened atomic vault recovery and failed IPC cleanup; fixed Android SDK bootstrap and API-26 incompatible list calls; fixed CRLF/formatter drift; corrected NSIS Windows paths/UTF-8 and Start Menu checks for non-8.3 paths; removed double TextBox padding that hid values; separated native offscreen layout from unavailable physical-display checks; corrected capture RTL transforms and dialog bounds.

Windows payload notices were checked against all 45 modules linked into the actual Windows Go executable. This is not a comprehensive legal/security audit; complete mobile distribution-notice review is outstanding. See KNOWN_LIMITATIONS.md. Failed intermediate workflows are not represented as passed.
