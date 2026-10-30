#!/usr/bin/env bash
set -euo pipefail
python3 scripts/test-manual-android303.py
adb install -r apps/android/app/build/outputs/apk/androidTest/sandbox/app-sandbox-androidTest.apk
COMPONENT=$(python3 - <<'PY_COMPONENT'
import re,subprocess
s=subprocess.check_output(['adb','shell','pm','list','instrumentation'],text=True)
rows=re.findall(r'^instrumentation:([^ ]+) \(target=app\.radvpn\.testing\)$',s,re.M)
assert len(rows)==1 and rows[0].endswith('/androidx.test.runner.AndroidJUnitRunner'), 'Expected one actual sandbox test runner'
print(rows[0])
PY_COMPONENT
)
adb shell am instrument -w -r -e class app.radvpn.ManualInstallGateTest "$COMPONENT" | tee dist/manual-android/native-mask-instrumentation.txt
grep -q 'OK (1 test)' dist/manual-android/native-mask-instrumentation.txt
python3 - <<'PY'
import json,pathlib
p=pathlib.Path('dist/manual-android/ANDROID-MANUAL-INSTALL-REPORT.json')
d=json.loads(p.read_text());assert d['passed']
d['native_password_test_passed']=True
d['checks'].append('Installed sandbox APK native test: password input variation and PasswordTransformationMethod mask a fixture; FLAG_SECURE stays set; no dashboard PowerControl before login')
d['accessibility_note']='API35 accessibility password flag was false; masking verified directly on the actual native EditText, not inferred from XML'
p.write_text(json.dumps(d,indent=2)+'\n')
PY
