#!/usr/bin/env python3
import pathlib,json,hashlib,shutil,zipfile
root=pathlib.Path('release/android-input');out=pathlib.Path('release/android-assets');out.mkdir(parents=True,exist_ok=True)
def one(name):
 found=list(root.rglob(name));assert len(found)==1,(name,len(found));return found[0]
build=one('build.txt').read_text();assert 'BUILD SUCCESSFUL' in build and 'BUILD FAILED' not in build
apk=one('RADVPN-2.0.0-android-design1-debug.apk')
expected=one('SHA256SUMS.txt').read_text().split()[0]
assert hashlib.sha256(apk.read_bytes()).hexdigest()==expected
metadata=json.loads(one('metadata.json').read_text());assert metadata['applicationId']=='app.radvpn'
assert metadata['elements'][0]['versionCode']==20001 and metadata['elements'][0]['versionName']=='2.0.0'
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in one('signature.txt').read_text()
reports=list(root.rglob('report-*.json'));assert len(reports)==2
for p in reports:
 r=json.loads(p.read_text());assert r['passed'] and len(r['captures'])==24
images=list(root.rglob('*.png'));assert len(images)==48
with zipfile.ZipFile(apk) as z:
 names=z.namelist()
 for abi in ['arm64-v8a','x86_64']:assert any(n.startswith(f'lib/{abi}/') and n.endswith('.so') for n in names)
 modules=json.loads(z.read('assets/licenses/go/index.json'));assert len(modules)>20
 for m in modules:
  assert m['files']
  for n in m['files']:assert 'assets/licenses/go/'+n in names
 assert 'assets/licenses/ANDROID-NOTICES.txt' in names
 assert len([n for n in names if n.startswith('assets/licenses/maven/') and n.endswith('.pom.txt')])>2
shutil.copy2(apk,out/apk.name)
for p in reports:shutil.copy2(p,out/p.name)
shutil.copy2(one('metadata.json'),out/'metadata.json');shutil.copy2(one('signature.txt'),out/'signature.txt')
with zipfile.ZipFile(out/'RADVPN-Android-native-ui-evidence.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in images:z.write(p,'screenshots/'+p.name)
with zipfile.ZipFile(apk) as z,zipfile.ZipFile(out/'RADVPN-Android-third-party-notices.zip','w',zipfile.ZIP_DEFLATED) as dest:
 for n in z.namelist():
  if n.startswith('assets/licenses/') and not n.endswith('/'):dest.writestr(n,z.read(n))
sums=[f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}' for p in sorted(out.iterdir())]
(out/'SHA256SUMS.txt').write_text('\n'.join(sums)+'\n')
print('Verified actual APK digest/signature record, ABIs, notice bundle and 48 installed-app emulator images. Debug testing only, not device VPN protection.')
