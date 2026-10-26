# Architecture and trade-offs

## Decision

Use one Go engine embedding Xray-core and two native frontends: WPF/.NET 8 on Windows; Kotlin Views on Android. No WebView, HTTP management API, shell commands derived from imports, or external service is needed. Native frontends make real `VpnService`, secure storage and desktop lifecycle integration possible. Duplicated presentation code is a conscious trade-off; protocol parsing, validation, settings, configuration generation, core state, counters and error codes are shared.

Xray-core v26.10.21 is pinned to commit `1b1647cd9ddf0abc34b981d4611fc03bcac1ca74`, Go module `v1.260327.1-0.20261021115930-1b1647cd9ddf`. `go.sum` authenticates module content using Go's public checksum database. Its native TUN supports Windows Wintun and Android file descriptors. This avoids coupling two independently embedded Go runtimes. Upstream source: https://github.com/XTLS/Xray-core/tree/v26.10.21 . libXray was reviewed but not selected: its wrapper adds another moving interface; this application invokes the documented core config builder and lifecycle directly.

## Boundaries

- `packages/engine/profiles.go`: strict link parsing, bounded import, content identity, no profile execution.
- `packages/engine/config.go`: typed settings, generated inbounds/rules, outbound whitelist and core validation.
- `packages/engine/engine.go`: serialized lifecycle, HTTPS probe, actual counters, protected dialer and bounded HTTPS subscription fetch.
- `packages/mobile`: small gomobile-safe JSON boundary; a single Go runtime.
- `cmd/radcore`: inherited stdin/stdout JSON IPC. EOF stops the engine. There is no unauthenticated control listener.
- `packages/localization`: 101 matching English and Persian keys; native direction changes without process restart.
- `apps/windows`: async sequential IPC, DPAPI vault, native WPF UI, session-only redacted diagnostic events, tray and size persistence.
- `apps/android`: native Activity, service-owned TUN descriptor, foreground notification, AES-GCM Android Keystore vault, file/share/camera imports.

## Networking

Tunnel is the persisted initial default; local SOCKS/HTTP listeners exist in both modes. Switching mode first closes the previous instance. Both listeners bind only to loopback; the allowed SOCKS port is 1024–65534 because the next port is reserved for HTTP. Actual Xray bind errors handle the unavoidable check/bind race; preflight port checks alone would not be sufficient.

On Windows, Xray configures app-owned adapter `RADVPN`, IPv4/IPv6 gateways and two half-default routes per family. `autoOutboundsInterface: auto` prevents outbound loops. Native Windows WFP DNS/misconfigured-family filters are requested; core refuses startup if it cannot install them. DNS applies to the temporary adapter, not permanent replacement of the user's physical-interface settings. Close is delegated to upstream's route/filter/adapter teardown. **Forced termination recovery has not been verified and is a release blocker.** Elevation is explicit: non-admin Tunnel start is rejected with instructions; no silent UAC or arbitrary privileged commands.

Android uses `VpnService.prepare`, the official consent Activity, `Builder.establish`, both default routes, DNS and MTU. Xray duplicates the provided descriptor; service closes its own descriptor after stopping Xray. `internet.RegisterDialerController` calls `VpnService.protect` on native outbound TCP/UDP sockets. Only RADVPN's own package is excluded from capture to prevent bootstrap DNS and subscription loops. Other applications remain eligible for capture. Per-user application exclusions are not yet implemented. Always-on/block-without-VPN must be configured in Android system VPN settings; there is no app-level kill-switch guarantee. The foreground service stops on revocation and uses START_NOT_STICKY, not surprise reconnect after process recreation.

Domain/CIDR direct/proxy/block rules are evaluated inside Xray after TUN capture. This is not an OS-level CIDR route exclusion. The health-check domain `cp.cloudflare.com` is deliberately forced through the proxy before user rules so a direct rule cannot create false server-success status. No firewall-only whole-device kill switch is advertised.

## Truthful state and diagnostics

Disconnected → Connecting → Connected requires a successful 204 HTTPS response through the local SOCKS inbound. Failure closes the core and reports an error. The latency is one request timing including TCP, proxy handshake and TLS; it is not a ping and is not periodically retested. Counters are outbound proxy bytes (including health probes); direct bypass bytes are excluded. UI rates are differences between actual samples. TUN interface existence, core start and proxy reachability are distinct facts; the probe does not validate that operating-system routes capture other applications.

Raw Xray errors and access logs are disabled rather than heuristically redacted. Error codes are localized and cannot echo subscription secrets. This sacrifices detailed upstream diagnostics until an audited structured redaction pipeline exists. No automatic reconnect, network-change supervisor or connectivity-history persistence is represented as implemented.

## Persistence and trust

Windows DPAPI CurrentUser and Android AES-GCM Keystore protect vaults. There is no plaintext fallback. Backups are the same device/account-bound encryption, not password-portable exports. Android disables OS cloud backup and screenshot capture. URL lists never leave the client except direct HTTPS refresh to their actual provider. Input is capped at 2 MiB, HTTPS downloads at 20 seconds/three redirects and 2 MiB; imported input cannot add env, listeners, file-based TLS keys, process commands or outbound socket options.

Android builds use the upstream anet-required linker flag `-checklinkname=0` (see https://github.com/wlynxg/anet). This dependency accesses private Go networking symbols; every Go upgrade therefore requires Android ABI/runtime regression testing. It is not an application privilege or TLS check bypass.

Native dependency upgrades require a new core schema, licensing, TUN teardown and device regression review, not simply changing a version string. All release artifacts require platform smoke and network tests and a license bundle before redistribution.
