#!/usr/bin/env python3
"""User-authorized manual-test prerelease ONLY. Verify artifact/source digests, never replace historical assets."""
import base64,hashlib,json,os,subprocess,zipfile
from pathlib import Path
REPO='salehsayyadi/RADVPN'; TAG='v3.0.3-manual-test.1'
BASE_SOURCE='707d42daec2b3be8d0cb206cf6c3bc4cea685f30'; WINDOWS_RUN=41059435484
assert os.environ['GITHUB_REPOSITORY']==REPO
ROOT=Path('manual-release-assets')
def api(path,method='GET',body=None,binary=None):
 args=['gh','api','--method',method,f'repos/{REPO}/{path}'];payload=None
 if body is not None:args+=['--input','-'];payload=json.dumps(body).encode()
 if binary:
  with Path(binary).open('wb') as out:subprocess.run(args,input=payload,stdout=out,check=True)
 else:return json.loads(subprocess.check_output(args,input=payload))
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def artifact(run_id,name,required,out):
 run=api(f'actions/runs/{run_id}')
 artifacts=api(f'actions/runs/{run_id}/artifacts')['artifacts'];a=next(a for a in artifacts if a['name']==name)
 assert not a['expired'] and a['workflow_run']['head_sha']==run['head_sha']
 archive=Path(name+'.zip');api(f'actions/artifacts/{a["id"]}/zip',binary=archive)
 assert a['digest']=='sha256:'+sha(archive),'Artifact archive digest mismatch'
 with zipfile.ZipFile(archive) as z:
  for n in required:
   found=[i for i in z.infolist() if i.filename==n or i.filename.endswith('/'+n)]
   assert len(found)==1 and found[0].file_size<400_000_000
   (out/n).write_bytes(z.read(found[0]))
 return run,a
report=dict(tag=TAG,published=False,source_sha=os.environ['GITHUB_SHA'],run_id=int(os.environ['GITHUB_RUN_ID']),scope='User-requested manual-test prerelease; NOT final/stable/production acceptance')
try:
 jobs=api(f'actions/runs/{os.environ["GITHUB_RUN_ID"]}/jobs')['jobs']
 assert any(j['name']=='build-android-manual' and j['conclusion']=='success' for j in jobs),'Android build/install test has not passed'
 ROOT.mkdir()
 win_names=['RADVPN-Setup-3.0.3-x64.exe','RADVPN-Windows-Portable-3.0.3-x64.zip','SHA256SUMS.txt','upgrade-303-report.txt','native-tests-303.json']
 win_run,wa=artifact(WINDOWS_RUN,'RADVPN-3.0.3-Windows-candidate',win_names,ROOT)
 assert win_run['head_sha']==BASE_SOURCE and win_run['conclusion']=='success'
 assert all(j['conclusion']=='success' for j in api(f'actions/runs/{WINDOWS_RUN}/jobs')['jobs'])
 # Check the existing candidate's own checksums before generating a combined manifest.
 checksums=(ROOT/'SHA256SUMS.txt').read_text()
 for n in win_names[:2]:assert sha(ROOT/n) in checksums,'Windows build checksum missing/mismatch'
 (ROOT/'SHA256SUMS.txt').unlink()
 android_names=['RADVPN-Android-3.0.3-MANUAL-TEST.apk','ANDROID-MANUAL-PROVENANCE.json','ANDROID-MANUAL-INSTALL-REPORT.json','ANDROID-TEST-README.txt']
 arun,aa=artifact(os.environ['GITHUB_RUN_ID'],'RADVPN-303-Android-manual-test',android_names,ROOT)
 assert arun['head_sha']==os.environ['GITHUB_SHA']
 ap=json.loads((ROOT/'ANDROID-MANUAL-PROVENANCE.json').read_text());assert ap['source_sha']==os.environ['GITHUB_SHA'] and ap['package']=='app.radvpn.testing' and ap['production_signing_compatible'] is False
 assert ap['sha256']==sha(ROOT/android_names[0])
 assert json.loads((ROOT/'ANDROID-MANUAL-INSTALL-REPORT.json').read_text())['passed'] is True
 provenance=dict(windows_source_sha=BASE_SOURCE,windows_run_id=WINDOWS_RUN,windows_artifact_id=wa['id'],windows_artifact_digest=wa['digest'],android_source_sha=os.environ['GITHUB_SHA'],android_run_id=os.environ['GITHUB_RUN_ID'],android_artifact_digest=aa['digest'],production_api_deployed_verified=False,android_production_upgrade_verified=False,final_acceptance_passed=False)
 (ROOT/'MANUAL-TEST-PROVENANCE.json').write_text(json.dumps(provenance,indent=2)+'\n')
 body='''# RADVPN 3.0.3 — manual testing only

**Pre-release, not stable/latest and NOT a fully deployed or production-accepted system.** Published at the user's explicit request for hands-on checks. Do not confuse this with the older v3.0.3 prerelease.

## Downloads
- Windows installer: RADVPN-Setup-3.0.3-x64.exe
- Windows portable: RADVPN-Windows-Portable-3.0.3-x64.zip
- Android: RADVPN-Android-3.0.3-MANUAL-TEST.apk — separate app.radvpn.testing identity, label RADVPN Test 3.0.3. Install alongside the existing app. This is NOT an in-place update to app.radvpn. Future test builds may use a different test signer. Do NOT uninstall the production app to install this test APK.
- SHA256SUMS.txt and MANUAL-TEST-PROVENANCE.json identify the actual assets/sources.

## Verified, scoped tests
Windows: existing successful candidate run, runtime/DPAPI/login lease/paging helpers and previous-release installer/portable upgrade tests. Android: real engine compiled, sandbox lint/build/signature/package verified, installed beside the actual v2.0.3 baseline on API35 x86_64 emulator; first launch shows login directly, masked code field and Telegram registration button. No login capture protection was disabled. This does not verify live authenticated dashboard/device UI, physical arm64 devices or real VPN connectivity.

## Known blockers — expected during manual checks
The central API is NOT confirmed installed on your server. Normal TLS verification for api.bestget.ir/bestget.ir previously failed hostname matching. Account login requires a correctly deployed API, valid HTTPS and a configured bot tenant/panel. Publishing these files does NOT install the API, fix TLS or enable the bot. Do not bypass TLS validation. Original Android production signer is unavailable; compatible production upgrade was NOT tested. Live Telegram/payment/panel/full E2E acceptance and safe legacy API adoption remain incomplete/unverified.

## Bot source (private repositories remain private)
- Bot Maker branch main: https://github.com/salehsayyadi/vpn-bot-maker/tree/main — tested source de76ba7631927554c7bbbabeb451e80429018d25
- Sales bot branch RADVPN: https://github.com/salehsayyadi/vpn-sell-bot/tree/RADVPN — tested source da962059a89061f351d816eea8955a76e7e0b303
No private bot source, configuration or credentials are copied into this public release. See the private Maker docs/RADVPN_DELIVERY_STATUS.md and full acceptance matrix for remaining work.
'''
 body+='\nWindows source: `'+BASE_SOURCE+'`; Android packaging source: `'+os.environ['GITHUB_SHA']+'`. Build/install: https://github.com/'+REPO+'/actions/runs/'+os.environ['GITHUB_RUN_ID']+'\n'
 (ROOT/'MANUAL-TEST-README.md').write_text(body)
 (ROOT/'SHA256SUMS.txt').write_text(''.join(sha(p)+'  '+p.name+'\n' for p in sorted(ROOT.iterdir()) if p.is_file()))
 latest=api('releases/latest')['id']
 # A unique tag and draft prevent replacement of historical release assets.
 release=api('releases',method='POST',body=dict(tag_name=TAG,target_commitish=os.environ['GITHUB_SHA'],name='RADVPN 3.0.3 — Manual Test 1 (NOT final)',body=body,draft=True,prerelease=True,make_latest='false'))
 subprocess.run(['gh','release','upload',TAG,*[str(p) for p in sorted(ROOT.iterdir())],'--repo',REPO],check=True)
 check=api('releases/'+str(release['id']));uploaded={a['name']:a for a in check['assets']}
 assert set(uploaded)=={p.name for p in ROOT.iterdir()}
 for p in ROOT.iterdir():assert uploaded[p.name]['digest']=='sha256:'+sha(p)
 published=api('releases/'+str(release['id']),method='PATCH',body=dict(draft=False,prerelease=True,make_latest='false'))
 assert published['prerelease'] and not published['draft'] and api('releases/latest')['id']==latest
 report.update(published=True,release_url=published['html_url'],assets=[dict(name=a['name'],size=a['size'],digest=a['digest'],url=a['browser_download_url']) for a in published['assets']],windows_source_sha=BASE_SOURCE)
 print('Verified manual-test prerelease:',published['html_url'])
except Exception as exc:
 report['error_type']=type(exc).__name__;raise
finally:
 # Bounded public metadata report, never logs/credentials. GITHUB_TOKEN commits do not trigger recursive runs.
 path='contents/docs/manual-test303/latest.json'
 content=base64.b64encode((json.dumps(report,indent=2)+'\n').encode()).decode()
 payload=dict(message='docs: record manual-test release verification',content=content,branch='feature/radvpn-3.0.3')
 try:
  previous=api(path+'?ref=feature/radvpn-3.0.3');payload['sha']=previous['sha']
 except subprocess.CalledProcessError:pass
 api(path,method='PUT',body=payload)
