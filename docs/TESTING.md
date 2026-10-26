# Actual verification — 2026-10-26

## Redesign verification (current work)

Local Linux: redesigned native WPF cross-publish succeeded, Go race tests including JSON boundaries and IPv6 policy passed, and a real NSIS installer compiled (56,745,919 bytes at this intermediate build). This is not Windows execution evidence. Hosted Windows installation/UI/repair/uninstall tests are being run; results are pending and must not be presumed passed. The original record below documents the earlier baseline, not the redesigned UI.

New tests: `TestJSONImportBoundary`, `TestWindowsIPv6Policy`; new CI native test commands: `./scripts/build-windows.ps1`, `./scripts/test-windows-installer.ps1`, installed `RADVPN.exe --self-test`, installed `RADVPN.exe --ui-smoke --capture-dir`. Native UI screenshots are rendered by actual compiled WPF, not a HTML mockup.

## Historical baseline record


## Release decision

**Development implementation and testing artifacts only. `v2.0.0` is NOT tagged or published as a release.** Source and native builds are real, but native tunnel safety/cleanup and the full product specification have not passed acceptance. A successful cross-build is not a Windows launch test; an APK signature check is not an Android VPN test.

## Environment

Linux x86_64 sandbox; Go 1.27.3; .NET SDK 8.0.427 (Windows runtime pinned 8.0.33); JDK 17 installed separately; Gradle 8.11.1; Android SDK 35/build-tools 35.0.0; NDK 28.0.13004108. No Windows runtime/desktop, Android device/emulator or authorized remote Xray server was available. There was no supplied logo; an original temporary shield/lightning vector was created and visually inspected. Native app UI screenshots/visual QA were **not** performed.

## Commands actually executed

Toolchain directories were prepended to PATH; commands were run from the repository root unless stated otherwise.

| Command | Result / scope |
| --- | --- |
| `gofmt -w packages cmd` and source formatting check | PASS; Go sources formatted. |
| `go test -race -count=1 -v ./...` | PASS on Linux; nine named tests plus five fuzz seeds and protocol subtests; no race report. |
| `go vet ./...` | PASS; no diagnostics. |
| `go mod verify` | PASS; all modules verified. |
| `go test ./packages/engine -run '^$' -fuzz FuzzImport -fuzztime 20s -parallel 2` | PASS; final run 27,142 executions, 56 additional interesting inputs, no crash. Actual elapsed 21.127 s including shutdown. |
| `python scripts/check.py` | PASS; version 2.0.0 metadata and 101 nonempty matching English/Persian keys; native hardcoded locale-key references checked. |
| `dotnet format whitespace apps/windows/RADVPN.csproj --verify-no-changes --no-restore` | PASS after formatting. |
| `go build -trimpath -o dist/radcore-linux ./cmd/radcore` | PASS; Linux IPC smoke test described below. Linux Tunnel is intentionally unsupported. |
| `GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o dist/windows/radcore.exe ./cmd/radcore` | PASS; actual Windows engine executable produced. |
| `dotnet publish apps/windows/RADVPN.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o dist/windows` | PASS; actual WPF Windows app and native desktop-runtime DLLs produced. WFAC010 warning remains for mixed WPF/WinForms DPI manifest handling; no native launch test. |
| `gomobile bind -ldflags='-checklinkname=0 -s -w' -target=android/arm64,android/amd64 -androidapi=26 -o apps/android/app/libs/radengine.aar ./packages/mobile` | PASS; actual Android ARM64/x86_64 native library. Version in scripts/gomobile-version.txt. |
| `gradle -p apps/android --no-daemon assembleDebug lintDebug` | PASS after fixes; native Kotlin APK compiled and Android lint succeeded. Third-party IntentIntegrator and legacy Activity navigation deprecation compiler warnings remain. |
| `gradle -p apps/android --no-daemon clean assembleDebug lintDebug` | PASS; clean rebuild completed before delivering the testing APK. See docs/verification/android-clean-build.txt and ARTIFACT_MANIFEST.json. |
| `bash scripts/check-apk.sh` | PASS on built APK: v2 APK signature valid, one debug signer; package app.radvpn, versionName 2.0.0, versionCode 20000, min SDK 26, target 35, arm64-v8a/x86_64. Not production-signed. |
| `python scripts/fetch-wintun.py` equivalent vendor ZIP checksum/extraction | Official Wintun 0.14.1 ZIP downloaded, SHA256 matched pinned 07c25618…24ef51. Windows Authenticode check is implemented in packaging CI but **not executed on Linux**. |
| `python scripts/collect-licenses.py` | Collected available Go module notices (63 of 120 module entries found with license files in the local module directories); complete transitive license review remains a release blocker. |

## Tests and what they prove

- **TestProfiles**: generated configurations for VLESS WS/TLS and TCP/Vision, Trojan gRPC/TLS, Shadowsocks SIP002, authenticated SOCKS/HTTP and legacy VMess JSON were accepted by the **pinned Xray core config builder**, not merely a local JSON validator.
- **TestRejectUnsafe**: malformed UUID/ports/Base64, insecure TLS parameters, unknown transports/duplicate security options, invalid flow combinations, missing REALITY data, arbitrary outbound fields and unsafe advanced TLS settings are rejected.
- **TestImport**: plain/Base64 lists, valid/invalid mixtures, content deduplication and input-size limit.
- **TestSettings**: Tunnel/10808 defaults; loopback-only binds; port/MTU/DNS/routing validation; generated Windows TUN config accepted by core schema; unsupported Linux TUN refused.
- **TestLocalization**: every English/Persian key exists and is nonempty. This does **not** prove on-device RTL rendering or translation quality.
- **TestErrorsAreRedacted**: rejected input does not echo credentials/host; unknown JSON request fields rejected.
- **TestCoreTrafficAndCleanup**: starts a real local HTTP CONNECT upstream and HTTP destination, starts Xray's SOCKS5/HTTP listeners, performs a real 204 connectivity probe through the upstream, passes actual HTTP payload through the SOCKS5 listener, checks nonzero real counters, stops the core and verifies both listening ports close.
- **TestPortConflict**: a real occupied port causes startup Error rather than fabricated Connected state.
- **TestStopIdempotent**: repeated/disconnected stop request succeeds.
- **FuzzImport**: bounded malformed-input fuzz smoke; no panic/crash in the recorded run, not a security audit.

The local network test is deliberately self-contained and authorized. It does **not** test remote VLESS, VMess, Trojan, REALITY, Internet connectivity, TUN capture, leak behavior or driver cleanup.

## Additional executable/metadata checks

Executed the built Linux `radcore` as a child process and exchanged real newline-delimited JSON `version`, `defaults`, `validate` and `stop` requests. Observed app version `2.0.0`, core version `26.10.21`, initial Tunnel mode and port 10808. Confirmed validation emits no pre-start core log lines into the JSON channel and stdin EOF exits with code 0. Parsed Windows executable PE headers and confirmed x64 machine 0x8664 for both RADVPN.exe and radcore.exe. These checks do not execute Windows GUI code.

The staged source-only tree was scanned for unexpected binary files and common GitHub-token/private-key patterns; none found. This is a targeted check, not a comprehensive secret-scanner audit. No user VPN credentials or signing keys were supplied, invented or included.

## Issues found and corrected

1. Initial WPF compiler error: CaretBrush belongs to TextBox, not Control; corrected and rebuilt.
2. Xray uses calendar release tags that are not valid `/v26` module paths; pinned the exact release commit/pseudo-version instead.
3. Android anet dependency references private Go networking symbols. Added the upstream-documented `-checklinkname=0` linker flag. This creates an Android ABI regression obligation for future Go upgrades; it is not an OS security or TLS bypass.
4. Corrected JDK compiler PATH during gomobile build.
5. Android lint found two Java List.removeLast API-35 calls incompatible with min SDK 26. Replaced with removeAt(lastIndex), rebuilt, reran lint.
6. Disabled pre-start Xray logs as well as runtime logs so config-parser warnings cannot corrupt Windows JSON IPC or disclose input.
7. Added Android data-extraction exclusions, launcher icon and bounded API-26-compatible file reads. Legacy scanner/navigation deprecations remain explicitly documented.
8. Fixed Android socket-protector callback qualification, service-start error preservation, and secure-vault failure handling; Windows no longer overwrites an unreadable vault.

## Required platform acceptance matrix — NOT RUN

| Acceptance area | Windows | Android |
| --- | --- | --- |
| Native launch, keyboard/back navigation, English/Persian RTL and all dialogs | NOT RUN | NOT RUN |
| Tray/window lifecycle or permission/foreground-service flow | NOT RUN | NOT RUN |
| SOCKS5/HTTP on target OS, bind isolation and conflicts | NOT RUN | NOT RUN |
| Full-device TUN traffic capture, IPv4 and IPv6 | NOT RUN | NOT RUN |
| DNS/route cleanup after disconnect/error/forced crash | NOT RUN | NOT RUN |
| Physical-adapter state recovery / no loss of connectivity | NOT RUN | NOT RUN |
| Network switching, sleep/background and process recreation | NOT RUN | NOT RUN |
| DNS/IPv6 leak tests and strict traffic blocking | NOT RUN | NOT RUN |
| Remote Xray protocol/transport compatibility | NO SERVER | NO SERVER |
| Installer/signing/distribution validation | NO INSTALLER/SIGNING | DEBUG SIGNED ONLY |

Do not mark these rows passed from a source review, local proxy test, compilation, APK signature check or unexecuted CI configuration. Missing features and product-scope gaps are listed in KNOWN_LIMITATIONS.md. GitHub Actions results, when available, are separate evidence; adding a workflow is not evidence that a hosted run succeeded.
