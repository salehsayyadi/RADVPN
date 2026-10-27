#!/usr/bin/env python3
"""Require reviewed immutable Android+Windows builds before creating a stable GitHub release."""
import json,os,pathlib,subprocess
repo=os.environ['GITHUB_REPOSITORY']
def api(path):return json.loads(subprocess.check_output(['gh','api',path],text=True))
a=json.loads(pathlib.Path('.github/unified-release-approval.json').read_text())
assert a['version']=='2.0.2' and a['native_ui_reviewed'] is True
assert a['github_channel']=='release' and a['device_tunnel_verified'] is False
assert a['android_signature']=='persistent-private' and a['android_upgrade_verified'] is True and a['windows_signature']=='unsigned'
for key,path,names in [('android_run','.github/workflows/android-design.yml',['android-native']),('windows_run','.github/workflows/ci.yml',['shared','windows','android'])]:
 run=api(f'repos/{repo}/actions/runs/{int(a[key])}')
 assert run['head_sha']==a['source_sha'] and run['conclusion']=='success'
 assert run['head_branch']=='main' and run['event']!='pull_request' and run['path']==path
 jobs=api(f'repos/{repo}/actions/runs/{int(a[key])}/jobs?per_page=100')['jobs']
 assert all(any(j['name']==name and j['conclusion']=='success' for j in jobs) for name in names)
assert not any(t['ref']=='refs/tags/v2.0.2' for t in api(f'repos/{repo}/git/matching-refs/tags/v2.0.2')),'Never overwrite a tag'
assert not api(f'repos/{repo}')['private']
print('Same-source successful builds and explicit native visual review approved; unused immutable tag.')
