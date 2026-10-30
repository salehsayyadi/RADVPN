#!/usr/bin/env python3
"""Install separate test identity next to actual previous APK; verify login-first without privacy bypass."""
import hashlib,json,os,subprocess,time,urllib.request,xml.etree.ElementTree as ET
from pathlib import Path
OUT=Path('dist/manual-android'); OUT.mkdir(parents=True,exist_ok=True)
APK=OUT/'RADVPN-Android-3.0.3-MANUAL-TEST.apk'
def adb(*args):return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT)
checks=[];stage='baseline';observed={}
try:
 release=json.loads(subprocess.check_output(['gh','api','repos/salehsayyadi/RADVPN/releases/tags/v2.0.3']))
 asset=next(a for a in release['assets'] if a['name']=='RADVPN-Android.apk')
 prior=Path(os.environ['RUNNER_TEMP'])/'radvpn-production-baseline.apk'
 urllib.request.urlretrieve(asset['browser_download_url'],prior)
 assert asset['digest']=='sha256:'+hashlib.sha256(prior.read_bytes()).hexdigest()
 assert 'Success' in adb('install',str(prior))
 assert 'versionName=2.0.3' in adb('shell','dumpsys','package','app.radvpn')
 checks.append('Actual distributed v2.0.3 APK digest verified and installed')
 adb('logcat','-c')
 assert 'Success' in adb('install',str(APK))
 packages=adb('shell','pm','list','packages')
 assert 'package:app.radvpn\n' in packages and 'package:app.radvpn.testing\n' in packages
 assert 'versionName=2.0.3' in adb('shell','dumpsys','package','app.radvpn')
 checks.append('Test identity installs alongside app.radvpn without replacing production app/data')
 stage='launch';result=adb('shell','am','start','-W','-n','app.radvpn.testing/app.radvpn.MainActivity')
 assert 'Status: ok' in result,'Activity launch status not ok';checks.append('Activity launch returned Status ok')
 time.sleep(5);stage='process';assert adb('shell','pidof','app.radvpn.testing').strip();checks.append('Test app process remains alive');stage='ui_dump'
 adb('shell','uiautomator','dump','/sdcard/radvpn-manual-ui.xml')
 root=ET.fromstring(adb('exec-out','cat','/sdcard/radvpn-manual-ui.xml'))
 texts=[n.attrib.get('text','') for n in root.iter('node')]
 observed={'node_count':len(list(root.iter('node'))),'has_code_label':'Shared login code from Telegram' in texts,'has_sign_in':'Sign in' in texts,'has_telegram':'Get / Create Account in Telegram' in texts,'has_password':any(n.attrib.get('password')=='true' for n in root.iter('node')),'packages':sorted({n.attrib.get('package','') for n in root.iter('node')})};stage='ui_assertions'
 assert 'Shared login code from Telegram' in texts
 assert 'Sign in' in texts and 'Get / Create Account in Telegram' in texts
 assert any(n.attrib.get('password')=='true' for n in root.iter('node'))
 checks.append('First launch directly shows masked login-code field, Sign in and Telegram registration button')
 logs=adb('logcat','-d')
 assert 'FATAL EXCEPTION' not in logs and 'UnsatisfiedLinkError' not in logs
 checks.append('No fatal exception/JNI load failure during tested first launch')
 report=dict(passed=True,checks=checks,source_sha=os.environ['GITHUB_SHA'],package='app.radvpn.testing',scope='API35 x86_64 emulator install/first-launch only; not live API/Telegram/payment/VPN, original-key upgrade or physical arm64 device proof',screenshots='Not taken: login capture policy remains protected')
except Exception as exc:
 report=dict(passed=False,checks=checks,error_type=type(exc).__name__,stage=stage,observed=observed);raise
finally:
 (OUT/'ANDROID-MANUAL-INSTALL-REPORT.json').write_text(json.dumps(report,indent=2)+'\n')
