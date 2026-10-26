# Third-party notices and distribution review

Original RADVPN source: MIT (LICENSE).

## Xray-core

Pinned upstream release v26.10.21 / commit 1b1647cd9ddf0abc34b981d4611fc03bcac1ca74.
Source: https://github.com/XTLS/Xray-core/tree/v26.10.21
License: Mozilla Public License 2.0, reproduced in third_party/Xray-LICENSE.
Xray is linked from Go module source, not an unauthenticated downloaded binary. No upstream Xray files are modified in this repository. When distributing binaries, preserve notices and make corresponding covered source and changes available in accordance with MPL 2.0. The exact source revision and module checksum are recorded in go.mod/go.sum. Transitive dependencies retain their own licenses.

## Wintun

Vendor: https://www.wintun.net/
Package: https://www.wintun.net/builds/wintun-0.14.1.zip
Pinned SHA256: 07c256185d6ee3652e09fa55c0b673e2624b565e02c4b9091c79ca7d2f24ef51
Redistribution terms: third_party/Wintun-LICENSE.txt (vendor's prebuilt-binary license, not RADVPN's MIT license). Build script preserves the package's LICENSE and validates the DLL signature on Windows. The hash was pinned from the HTTPS vendor download; it is not an independent audit or signature verification on Linux.

## Frontend/runtime

- .NET runtime / WPF: Microsoft upstream license notices must accompany self-contained distributions: https://github.com/dotnet/runtime and https://github.com/dotnet/wpf . Native runtime dependencies have additional notices.
- Kotlin: Apache 2.0. Android platform APIs subject to Android SDK terms for build tools.
- ZXing Android Embedded 4.3.0 and ZXing core: Apache 2.0. Sources https://github.com/journeyapps/zxing-android-embedded and https://github.com/zxing/zxing . Preserve notices when distributing.
- golang.org/x/mobile and x/*: BSD-style licenses; all module versions/checksums are recorded in go.mod/go.sum.

No production artifact release is approved until the full transitive license/notice bundle is reviewed. Generated module-license copies are included in testing packages when available, but automated file collection is not a legal review. Do not assume a dependency falls under MIT just because this application's own source does.
