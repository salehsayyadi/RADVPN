#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK 28.0.13004108}"
export PATH="${JAVA_HOME:?Set JAVA_HOME to JDK 17}/bin:$(go env GOPATH)/bin:$PATH"
version=$(cat scripts/gomobile-version.txt)
go install "golang.org/x/mobile/cmd/gomobile@$version"
go install "golang.org/x/mobile/cmd/gobind@$version"
mkdir -p apps/android/app/libs
gomobile bind -ldflags='-checklinkname=0 -s -w' -target=android/arm64,android/amd64 -androidapi=26 -o apps/android/app/libs/radengine.aar ./packages/mobile
gradle -p apps/android --no-daemon assembleDebug lintDebug
