#!/usr/bin/env bash
# Disposable emulator debug identity only. This never validates a production APK upgrade.
set -euo pipefail
mkdir -p dist/android/account-gate
trap 'adb pull /sdcard/Android/data/app.radvpn/files/account-evidence/. dist/android/account-gate/ >/dev/null 2>&1 || true; adb logcat -d -s AndroidRuntime:E > dist/android/account-gate/runtime-errors.txt 2>/dev/null || true; adb shell wm size reset >/dev/null 2>&1 || true; adb shell wm density reset >/dev/null 2>&1 || true' EXIT
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
adb install -r apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for size in 1080x2160 1170x2532; do
 adb shell wm size "$size"
 adb shell wm density 480
 adb shell am instrument -w -r -e class app.radvpn.AccountGateTest app.radvpn.test/androidx.test.runner.AndroidJUnitRunner | tee "dist/android/account-gate/instrumentation-$size.txt"
 grep -q 'OK (1 test)' "dist/android/account-gate/instrumentation-$size.txt"
done
adb pull /sdcard/Android/data/app.radvpn/files/account-evidence/. dist/android/account-gate/
python3 - <<'PY'
import json,pathlib
p=pathlib.Path('dist/android/account-gate');reports=list(p.glob('report-*.json'))
assert len(reports)==2 and all(json.loads(f.read_text())['passed'] for f in reports)
assert len(list(p.glob('login-*.png')))==4
print('PASS installed debug APK login gate, secure storage and four bilingual phone-size captures; not production upgrade')
PY
