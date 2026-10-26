#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?}"
apk=apps/android/app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "$apk"
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$apk" | grep -E "package:|sdkVersion:|targetSdkVersion:|native-code:"
