#!/usr/bin/env python3
"""Append only the passed separate-identity test APK to existing testing prerelease."""
import hashlib,json,os,subprocess,zipfile
from pathlib import Path
REPO='salehsayyadi/RADVPN';assert os.environ['GITHUB_REPOSITORY']==REPO
APK_NAME='RADVPN-Android-3.0.3-TEST-standalone.apk'
def api(path,method='GET',body=None,binary=None):
 args=['gh','api','--method',method,f'repos/{REPO}/{path}'];payload=None
 if body is not None:args+=['--input','-'];payload=json.dumps(body).encode()
 if binary:
  with Path(binary).open('wb') as out:subprocess.run(args,input=payload,stdout=out,check=True)
 else:return json.loads(subprocess.check_output(args,input=payload))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
try:
 run=api('actions/runs/'+os.environ['GITHUB_RUN_ID']);assert run['head_sha']==os.environ['GITHUB_SHA']
 jobs=api('actions/runs/'+os.environ['GITHUB_RUN_ID']+'/jobs')['jobs'];assert any(j['name']=='build-test' and j['conclusion']=='success' for j in jobs)
 listed=api('actions/runs/'+os.environ['GITHUB_RUN_ID']+'/artifacts')['artifacts'];items=[a for a in listed if a['name']=='RADVPN-303-Android-standalone-test'];assert len(items)==1
 item=items[0];assert not item['expired'] and item['workflow_run']['head_sha']==os.environ['GITHUB_SHA']
 api('actions/artifacts/'+str(item['id'])+'/zip',binary='android-testing-input.zip');assert 'sha256:'+sha('android-testing-input.zip')==item['digest']
 names={APK_NAME,'ANDROID-TEST-README.txt','ANDROID-TEST-SHA256SUMS.txt','ANDROID-TEST-PROVENANCE.json','android-test-install-report.json','android-test-account.png'}
 out=Path('android-testing-assets');out.mkdir()
 with zipfile.ZipFile('android-testing-input.zip') as z:
  for name in names:
   found=[i for i in z.infolist() if i.filename==name or i.filename.endswith('/'+name)];assert len(found)==1
   (out/name).write_bytes(z.read(found[0]))
 assert json.loads((out/'android-test-install-report.json').read_text())['passed'] is True
 prov=json.loads((out/'ANDROID-TEST-PROVENANCE.json').read_text());assert prov['source_sha']==os.environ['GITHUB_SHA'] and prov['package']=='app.radvpn.testing' and prov['production_signing_compatible'] is False
 digest,filename=(out/'ANDROID-TEST-SHA256SUMS.txt').read_text().split();assert filename==APK_NAME and digest==sha(out/APK_NAME)
 release=api('releases/tags/v3.0.3');assert release['prerelease'] and not release['draft']
 latest=api('releases/latest')['id'];windows={a['name']:a['digest'] for a in release['assets']}
 assert not (names & set(windows)),'Test asset names already exist; refusing to overwrite'
 subprocess.run(['gh','release','upload','v3.0.3',*map(str,sorted(out.iterdir())),'--repo',REPO],check=True)
 updated=api('releases/'+str(release['id']));uploaded={a['name']:a for a in updated['assets']}
 for p in out.iterdir():assert uploaded[p.name]['digest']=='sha256:'+sha(p)
 for n,d in windows.items():assert uploaded[n]['digest']==d,'Existing Windows asset changed'
 body=release['body'].replace('# RADVPN 3.0.3 — Windows testing pre-release','# RADVPN 3.0.3 — Windows + standalone Android testing').replace('**NO Android APK.**','**No production/update-compatible Android APK.** A separately installable testing APK is now attached; see the Android testing section below.')
 body+='\n\n## Standalone Android testing APK — not an update to the installed production app\n\nUser-requested clean-install testing build: `'+APK_NAME+'`. Package `app.radvpn.testing`, label `RADVPN Test 3.0.3`, version `3.0.3-testing`. It installs alongside `app.radvpn`, has separate data and a test-only debug signing identity; it is NOT a replacement production key, compatible upgrade or migration of existing data. No uninstall of the existing app is needed. The test signing identity is not promised to persist across later test builds.\n\nActual API35 emulator test passed: verified distributed v2.0.3 baseline remains installed, separate test APK installs/launches, account screen opens and code field is masked. This does not verify remote VPN, live API/Telegram/payment or original-key upgrades. See ANDROID-TEST-PROVENANCE.json, ANDROID-TEST-SHA256SUMS.txt and android-test-install-report.json.\n\nAndroid test source: `'+os.environ['GITHUB_SHA']+'`; build run: https://github.com/'+REPO+'/actions/runs/'+os.environ['GITHUB_RUN_ID']+'. The v3.0.3 tag remains pinned to the already verified Windows source; it has not been moved.\n'
 api('releases/'+str(release['id']),method='PATCH',body=dict(name='RADVPN 3.0.3 — Windows + standalone Android testing',body=body,prerelease=True,make_latest='false'))
 assert api('releases/latest')['id']==latest
 print('Published separate Android testing APK:',release['html_url']);print(digest,filename)
except Exception as exc:
 print('::error title=Android testing publication blocked::'+(type(exc).__name__+': '+str(exc)).replace('%','%25').replace('\n','%0A').replace('\r','%0D'));raise
