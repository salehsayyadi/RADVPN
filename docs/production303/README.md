# Correct Android name requires the correct production build

The old released APK was the sandbox flavor, package app.radvpn.testing and label
RADVPN Test 3.0.3. Changing its download filename cannot change its manifest label.
The main release build already defines package app.radvpn and label RADVPN.

production303.yml now builds ONLY assembleRelease with the original configured
persistent signing identity. The actual APK is inspected using aapt and must have
package app.radvpn, version 3.0.3, label RADVPN in every locale and no debug flag.
The original v2.0.3 APK signer/package/version checks must also pass. Renaming a
sandbox APK will fail these gates. No substitute signing key is generated.

Required existing repository Actions secrets:
- RADVPN_ANDROID_KEYSTORE_BASE64
- RADVPN_ANDROID_STORE_PASSWORD
- RADVPN_ANDROID_KEY_ALIAS
- RADVPN_ANDROID_KEY_PASSWORD

Use the EXISTING compatible signing identity, not a freshly generated key. Do
not commit/upload these secrets as source or release assets. Configure them in
GitHub repository Settings > Secrets and variables > Actions, not in chat.

If the signer is missing, docs/production303/latest.json reports Blocked and no
APK is built/published. A verified build is an Actions artifact only; an installed
runtime/in-place upgrade test is still required before replacing the public APK.
The existing public testing-identity APK is NOT silently relabeled as production.
