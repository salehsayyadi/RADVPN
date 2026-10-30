"""Static identity gate, NOT a substitute for adb install -r and data-preservation tests."""
import pathlib,re,subprocess,sys
old,new,tools=map(pathlib.Path,sys.argv[1:])
def output(*args):return subprocess.check_output(list(map(str,args)),text=True,stderr=subprocess.STDOUT)
def certificate(apk):
 text=output(tools/'apksigner','verify','--verbose','--print-certs',apk)
 return re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)',text)
def package(apk):
 text=output(tools/'aapt','dump','badging',apk)
 return re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",text).groups()
a,b=package(old),package(new)
assert a[0]==b[0]=='app.radvpn','Package identity changed'
assert int(b[1])>int(a[1]),'versionCode did not increase'
assert b[2]=='3.0.3','Wrong target version'
assert certificate(old) and certificate(old)==certificate(new),'Original signing identity mismatch: release blocked'
pathlib.Path('dist').mkdir(exist_ok=True)
pathlib.Path('dist/android-upgrade-303-report.txt').write_text('PASS package app.radvpn; increased versionCode; versionName 3.0.3; matching previous signer.\nNOT RUN: real adb install -r, encrypted data preservation, launch and VPN upgrade tests.\n')
print('Static APK compatibility passed; actual installed upgrade remains mandatory.')
