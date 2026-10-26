# Contributing

Use Go 1.27.3, .NET 8, Kotlin 2.1.20/AGP 8.9.1, Gradle 8.11.1 and JDK 17. Run `gofmt -w packages cmd`, `go vet ./...`, `go test -race ./...`, and `python3 scripts/check.py`. Run both native builds and Android lint before proposing networking changes. Add test coverage for every new protocol field and matching English/Persian messages. Do not silently ignore unsupported security parameters.

Never contribute real credentials, public test-subscription URLs, APK signing keystores, tokens, user vaults or binary engine downloads. Redact imported input from failures. Document actual executed tests and exact untested platform behavior. Core upgrades require pinned checksums, license review and device TUN/cleanup regression evidence. Do not turn a development build into a public release by merely adding a tag.
