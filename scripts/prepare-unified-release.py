#!/usr/bin/env python3
import pathlib,json,hashlib,shutil,zipfile,base64
root=pathlib.Path('release');out=root/'assets';out.mkdir(parents=True,exist_ok=True)
a=root/'android';w=root/'windows';e=root/'evidence'
def one(folder,name):
 files=list(folder.rglob(name));assert len(files)==1,(name,len(files));return files[0]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
apk=one(a,'RADVPN-Android.apk');installer=one(w,'RADVPN-Setup-2.0.2-x64.exe');portable=one(w,'RADVPN-Windows-Portable-2.0.2-x64.zip')
assert json.loads(one(a,'signing-policy.json').read_text())['persistent_key'] is True
for folder,file in [(a,apk),(w,installer),(w,portable)]:
 sums=one(folder,'SHA256SUMS.txt').read_text(encoding='utf-8-sig').splitlines()
 assert any(line.split()[0].lower()==digest(file) and line.split()[-1].lstrip('*')==file.name for line in sums)
 assert file.stat().st_size>10000000
build=one(a,'build.txt').read_text();assert 'BUILD SUCCESSFUL' in build and 'BUILD FAILED' not in build
meta=json.loads(one(a,'metadata.json').read_text());assert meta['applicationId']=='app.radvpn'
assert meta['elements'][0]['versionCode']==20003 and meta['elements'][0]['versionName']=='2.0.2'
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in one(a,'signature.txt').read_text()
reports=list(a.rglob('report-*.json'));assert len(reports)==2
for p in reports:
 r=json.loads(p.read_text());assert r['passed'] is True and len(r['captures'])==32
 assert all(one(a,c['file']).is_file() for c in r['captures'])
images=list(a.rglob('*.png'));assert len(images)==64
with zipfile.ZipFile(apk) as z:
 names=z.namelist()
 for abi in ['arm64-v8a','x86_64']:assert any(n.startswith(f'lib/{abi}/') and n.endswith('.so') for n in names)
 modules=json.loads(z.read('assets/licenses/go/index.json'));assert len(modules)>20
 for m in modules:
  assert m['files']
  for n in m['files']:assert 'assets/licenses/go/'+n in names
 assert 'assets/licenses/ANDROID-NOTICES.txt' in names
 assert len([n for n in names if n.startswith('assets/licenses/maven/') and n.endswith('.pom.txt')])>2
 artwork=base64.b64decode(b''.join(z.read(n) for n in sorted(names) if n.startswith('assets/lightning/') and n.endswith('.b64')))
 assert hashlib.sha256(artwork).hexdigest()=='4e105c50bdc9c657793e7fcfb197bca3413d688169bf9c0172a058f5ecdf96ee'
 with zipfile.ZipFile(out/'RADVPN-Android-notices.zip','w',zipfile.ZIP_DEFLATED) as dest:
  for n in names:
   if n.startswith('assets/licenses/') and not n.endswith('/'):dest.writestr(n,z.read(n))
ui=json.loads(one(e,'ui-report.json').read_text());assert ui['passed'] and len(ui['captures'])==56 and not ui['failures']
assert json.loads(one(e,'native-tests.json').read_text())['passed']
assert one(e,'installer-test-report.txt').read_text(encoding='utf-8-sig').count('PASS ')>=7
shutil.copy2(apk,out/'RADVPN-Android.apk');shutil.copy2(installer,out/'RADVPN-Windows-Setup.exe');shutil.copy2(portable,out/'RADVPN-Windows-Portable.zip')
for name in ['metadata.json','signature.txt']:shutil.copy2(one(a,name),out/('Android-'+name))
with zipfile.ZipFile(out/'RADVPN-native-acceptance.zip','w',zipfile.ZIP_DEFLATED) as z:
 for p in images+reports:z.write(p,'android/'+p.name)
 for p in e.rglob('*'):
  if p.is_file() and p.suffix in ['.png','.json','.txt']:z.write(p,'windows/'+str(p.relative_to(e)))
approval=json.loads(pathlib.Path('.github/unified-release-approval.json').read_text())
(out/'build-provenance.json').write_text(json.dumps(approval,indent=2)+'\n')
assert len(list(out.glob('*.apk')))==1 and len(list(out.glob('*.exe')))==1
(out/'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in sorted(out.iterdir()) if p.name!='SHA256SUMS.txt'))
print('Verified one actual APK + one installed Windows installer, matching checksums, 64 Android and 56 Windows captures, native tests, license inventory and exact background hash.')
