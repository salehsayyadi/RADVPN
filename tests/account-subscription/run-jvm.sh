#!/usr/bin/env bash
set -euo pipefail
# Java 17 + Kotlin 1.9.24 and org.json 20240303 (or compatible) are test prerequisites.
: "${KOTLIN_STDLIB_JAR:?Set KOTLIN_STDLIB_JAR to kotlin-stdlib.jar}"
: "${JSON_JAR:?Set JSON_JAR to an org.json jar; do not use the Android throwing stub}"
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
SRC="$ROOT/apps/android/app/src"
kotlinc -no-stdlib -no-reflect -classpath "$JSON_JAR:$KOTLIN_STDLIB_JAR" -d "$OUT/tests.jar" \
  "$SRC/main/java/app/radvpn/AccountSubscriptionSync.kt" \
  "$SRC/main/java/app/radvpn/AccountRequestGuard.kt" \
  "$SRC/main/java/app/radvpn/SubscriptionUpdates.kt" \
  "$SRC/androidTest/java/app/radvpn/AccountSubscriptionFixtures.kt" \
  "$ROOT/tests/account-subscription/Stubs.kt"
java -cp "$OUT/tests.jar:$JSON_JAR:$KOTLIN_STDLIB_JAR" app.radvpn.StubsKt
