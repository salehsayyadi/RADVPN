#!/usr/bin/env bash
set -euo pipefail
mkdir -p dist/android/emulator
trap 'adb pull /sdcard/Android/data/app.radvpn/files/native-evidence/. dist/android/emulator/ >/dev/null 2>&1 || true; adb logcat -d -s AndroidRuntime:E > dist/android/emulator/runtime-errors.txt 2>/dev/null || true' EXIT
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
adb install -r apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for size in 1080x2160 1170x2532; do
  adb shell wm size "$size"
  adb shell wm density 480
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 0
  adb shell am instrument -w -r app.radvpn.test/androidx.test.runner.AndroidJUnitRunner | tee "dist/android/emulator/instrumentation-$size.txt"
  grep -q 'OK (1 test)' "dist/android/emulator/instrumentation-$size.txt"
done
adb pull /sdcard/Android/data/app.radvpn/files/native-evidence/. dist/android/emulator/
adb shell wm size reset
adb shell wm density reset
python3 - <<'PY'
import pathlib,json
p=pathlib.Path('dist/android/emulator')
reports=list(p.glob('report-*.json'))
assert len(reports)==2,'Missing two native phone-size test reports'
assert all(json.loads(r.read_text())['passed'] for r in reports)
assert len(list(p.glob('*.png')))==48,'Missing actual English/Persian native screenshots'
print('PASS installed native APK, Keystore/core checks, 48 actual emulator captures at 360x720 and 390x844 dp')
PY
