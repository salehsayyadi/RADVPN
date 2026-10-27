# Android update signing

Android 2.0.2 keeps applicationId `app.radvpn` and increments versionCode to 20003. Previous public builds used the CI runner's Debug signing identity; no keystore persistence was configured. An APK contains a public certificate, not the private signing key.

Repository Actions secrets for a persistent private identity:
- `RADVPN_ANDROID_KEYSTORE_BASE64`: base64-encoded existing keystore (never commit the key).
- `RADVPN_ANDROID_STORE_PASSWORD`
- `RADVPN_ANDROID_KEY_ALIAS`
- `RADVPN_ANDROID_KEY_PASSWORD`

Use the **same key as the installed 2.0.1 APK** to support in-place updates. If that private key is unavailable, an in-place update cannot be promised. A new persistent key requires a one-time migration and cannot fix the old signature mismatch. Keep encrypted backups before any uninstall; do not assume Android restores the app's Keystore key after uninstall.

Without configured secrets, CI still creates disposable Debug APKs for testing, but the public-release gate refuses them. Do not publish those as an upgrade fix.

Before setting `android_upgrade_verified` in an approval, compare the old and new APK signing certificate SHA-256 with apksigner, and run `adb install -r` over the prior APK with non-sensitive fixture state. Verify both installation and fixture preservation. Never set this field based only on versionCode.
