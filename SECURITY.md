# Security

Version 2.0.0 is development source, not a security-audited public release. No supported-production-version promise is made yet.

Do not post real UUIDs, passwords, private keys, subscription URLs or private server data to public issues. Use sanitized reproduction data. Enable GitHub private vulnerability reporting before accepting sensitive reports; if it is unavailable, request a private reporting channel without posting the exploit or credentials publicly. No monitored security email is invented here.

Security boundaries: loopback-only unauthenticated local proxies (other local users/processes can still access them); inherited Windows IPC; app-private Android bindings; native encrypted vault; no profile-controlled process execution; bounded HTTPS/imports; strict advanced outbound restrictions; raw logs disabled. Windows Tunnel requires explicit elevation of the application, not just a least-privilege service — this is an architectural limitation requiring additional security review.

CI must never receive real VPN credentials or production signing keys in the source tree. Use GitHub Secrets for future release signing. Never publish generated debug keystores. Always review TUN teardown, WFP behavior, imported URI combinations, DNS bootstrap and dependency licensing before release. See docs/KNOWN_LIMITATIONS.md and docs/TESTING.md.
