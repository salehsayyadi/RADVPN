#!/usr/bin/env python3
"""Actual clean install + side-by-side account-screen launch; no production upgrade claim."""
import hashlib,json,os,re,subprocess,time,urllib.request,xml.etree.ElementTree as ET
from pathlib import Path
OUT=Path('dist/android-testing');OUT.mkdir(parents=True,exist_ok=True)
APK=OUT/'RADVPN-Android-3.0.3-TEST-standalone.apk'
def adb(*args):return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT)
def xml():
 adb('shell','uiautomator','dump','/sdcard/radvpn-test-ui.xml')
 return ET.fromstring(adb('exec-out','cat','/sdcard/radvpn-test-ui.xml'))
checks=[]
try:
 release=json.loads(subprocess.check_output(['gh','api','repos/salehsayyadi/RADVPN/releases/tags/v2.0.3']))
 asset=next(a for a in release['assets'] if a['name']=='RADVPN-Android.apk')
 prior=Path(os.environ['RUNNER_TEMP'])/'radvpn-production-baseline.apk';urllib.request.urlretrieve(asset['browser_download_url'],prior)
 assert 'sha256:'+hashlib.sha256(prior.read_bytes()).hexdigest()==asset['digest'],'Baseline APK integrity mismatch'
 assert 'Success' in adb('install',str(prior)),'Baseline install failed'
 baseline=adb('shell','dumpsys','package','app.radvpn')
 assert 'versionName=2.0.3' in baseline,'Wrong distributed baseline'
 checks.append('Installed actual distributed app.radvpn v2.0.3 baseline, digest verified')
 adb('logcat','-c')
 assert 'Success' in adb('install',str(APK)),'Separate test APK install failed'
 packages=adb('shell','pm','list','packages')
 assert 'package:app.radvpn\n' in packages and 'package:app.radvpn.testing\n' in packages,'Side-by-side packages missing'
 assert 'versionName=2.0.3' in adb('shell','dumpsys','package','app.radvpn'),'Existing app was replaced'
 checks.append('Separate app.radvpn.testing installs alongside baseline; original app/version remains installed')
 result=adb('shell','am','start','-W','-n','app.radvpn.testing/app.radvpn.MainActivity')
 assert 'Status: ok' in result,'MainActivity launch failed'
 time.sleep(5);assert adb('shell','pidof','app.radvpn.testing').strip(),'App process exited'
 nodes=xml();buttons=[n for n in nodes.iter('node') if n.attrib.get('text')=='RADVPN account']
 assert len(buttons)==1,'Account entry unavailable'
 bounds=list(map(int,re.findall(r'\d+',buttons[0].attrib['bounds'])))
 adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2));time.sleep(2)
 tree=xml();texts=[n.attrib.get('text','') for n in tree.iter('node')]
 assert 'Shared login code from Telegram' in texts and 'Sign in' in texts,'Account login screen unavailable'
 assert any(n.attrib.get('password')=='true' for n in tree.iter('node')),'Code input not masked'
 (OUT/'android-test-account.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p']))
 checks.append('Actual installed test APK launches, account entry works, code field is masked')
 logs=adb('logcat','-d')
 assert not any(x in logs for x in ['FATAL EXCEPTION','UnsatisfiedLinkError']),'Runtime crash/JNI load failure'
 checks.append('No fatal crash or native library load failure during launch/account navigation')
 report=dict(passed=True,checks=checks,package='app.radvpn.testing',version='3.0.3-testing',
  scope='Actual API35 x86_64 emulator install/launch; no production upgrade, remote VPN or live account E2E claim')
except Exception as exc:
 report=dict(passed=False,checks=checks,error=type(exc).__name__+': '+str(exc));raise
finally:
 (OUT/'android-test-install-report.json').write_text(json.dumps(report,indent=2)+'\n')
