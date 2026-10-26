#!/usr/bin/env python3
import hashlib,json,pathlib,shutil,zipfile
r=pathlib.Path('release');w=r/'windows';e=r/'evidence'
installer=w/'RADVPN-Setup-2.0.0-x64.exe'
assert installer.is_file() and installer.stat().st_size > 10_000_000
expected=(w/'SHA256SUMS.txt').read_text(encoding='utf-8-sig').split()[0].lower()
assert hashlib.sha256(installer.read_bytes()).hexdigest()==expected,'Installer checksum mismatch'
def one(root,name):
    found=list(root.rglob(name));assert len(found)==1,(name,len(found));return found[0]
ui=json.loads(one(e,'ui-report.json').read_text());assert ui['passed'] and len(ui['captures'])==56 and not ui['failures']
assert json.loads(one(e,'native-tests.json').read_text())['passed']
text=one(e,'installer-test-report.txt').read_text(encoding='utf-8-sig');assert text.count('PASS ')>=7
apk=one(r/'android','app-debug.apk');assert apk.stat().st_size>10_000_000
out=r/'assets';out.mkdir(parents=True,exist_ok=True)
shutil.copy2(installer,out/installer.name);shutil.copy2(apk,out/'RADVPN-2.0.0-android-debug-testing.apk')
for name in ('ui-report.json','native-tests.json','installer-test-report.txt'):shutil.copy2(one(e,name),out/name)
with zipfile.ZipFile(out/'RADVPN-2.0.0-native-ui-evidence.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(e.rglob('*.png')): z.write(p,'screenshots/'+p.name)
sums=[]
for p in sorted(out.iterdir()):sums.append(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}')
(out/'SHA256SUMS.txt').write_text('\n'.join(sums)+'\n')
print('Verified installer checksum, actual native reports and 56 WPF captures. APK is debug-signed/testing only.')
