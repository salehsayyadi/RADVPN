#!/usr/bin/env python3
import json,os,pathlib,subprocess

def api(path):return json.loads(subprocess.check_output(['gh','api',path],text=True))
a=json.loads(pathlib.Path('.github/android-release-approval.json').read_text());repo=os.environ['GITHUB_REPOSITORY']
assert a['native_ui_reviewed'] is True and a['channel']=='debug-testing' and a['device_tunnel_verified'] is False
assert a['version']=='2.0.0' and a['version_code']==20001
run=api(f'repos/{repo}/actions/runs/{int(a["run_id"])}')
assert run['head_sha']==a['source_sha'] and run['conclusion']=='success' and run['head_branch']=='main'
assert run['path']=='.github/workflows/android-design.yml' and run['event']!='pull_request'
assert any(j['name']=='android-native' and j['conclusion']=='success' for j in api(f'repos/{repo}/actions/runs/{a["run_id"]}/jobs')['jobs'])
assert not any(t['ref']=='refs/tags/v2.0.0-android.1' for t in api(f'repos/{repo}/git/matching-refs/tags/v2.0.0-android.1'))
assert not api(f'repos/{repo}')['private']
print('Successful native Android emulator run approved; Windows v2.0.0 remains unchanged.')
