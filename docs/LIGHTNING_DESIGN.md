# Approved lightning design — 2.0.1

The Android native UI implements the user-approved English four-screen proposal:
Home, Servers, Configs and Settings. Only Servers has a contextual overflow menu.
Testing, sorting and Favorites are there; Favorites opens a separate page. Named
subscription tabs preserve membership even when two providers contain the same
endpoint. Direct protocol links remain supported alongside legacy import formats.

The exact approved background is stored losslessly as Base64 of the JPEG in
`apps/android/app/src/main/assets/lightning/*.b64`, concatenated in filename order, decoded and center-cropped behind
native Android views. SHA-256 of decoded JPEG:
`4e105c50bdc9c657793e7fcfb197bca3413d688169bf9c0172a058f5ecdf96ee`.
This is decorative artwork, not a screenshot used in place of functional controls.

Connection tests use isolated real Xray instances and a TLS-validated HTTPS 204
response with nonzero proxy uplink/downlink counters. They do not substitute TCP
reachability for a working profile or alter the active session/system routes.
Tests are limited to 256 enabled profiles, four workers; cancellation is available.
No test result is fabricated. Tests contact the configured provider and the fixed
HTTPS probe host. They refuse to run while the app's VPN session is active.

Unspecified-address Shadowsocks subscription entries are account/expiry/quota
information, never connectable servers. Their labels are preserved separately.
Rich-text Markdown query wrappers are normalized locally, never followed.

Screenshots and Recents previews are allowed by default, with an explicit privacy
switch. Notifications show measured proxy counter deltas, not device-wide or
invented historical totals. Core operations run off the Android main thread.

Release status is separate from device-security certification. Android currently
uses a CI testing signature; Windows is unsigned. Physical-device VPN leak,
recovery, battery and remote-provider interoperability acceptance remain NOT RUN.
