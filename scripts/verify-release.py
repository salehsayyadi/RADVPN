#!/usr/bin/env python3
"""Refuse publication without reviewed successful native evidence and an unused tag."""
import json, pathlib, subprocess, os

def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', path], text=True))
repo = os.environ['GITHUB_REPOSITORY']
a = json.loads(pathlib.Path('.github/release-approval.json').read_text())
assert a['version'] == '2.0.0' and a['native_ui_reviewed'] is True
assert a['channel'] == 'experimental-unsigned' and a['device_tunnel_verified'] is False
run = api(f'repos/{repo}/actions/runs/{int(a["run_id"])}')
assert run['head_sha'] == a['source_sha'] and run['conclusion'] == 'success'
assert run['head_branch'] == 'main' and run['event'] != 'pull_request'
assert run['path'] == '.github/workflows/ci.yml'
jobs = api(f'repos/{repo}/actions/runs/{int(a["run_id"])}/jobs')['jobs']
assert all(any(j['name'] == name and j['conclusion'] == 'success' for j in jobs) for name in ('shared','windows','android'))
tags = api(f'repos/{repo}/git/matching-refs/tags/v2.0.0')
assert not any(t['ref'] == 'refs/tags/v2.0.0' for t in tags), 'Tag already exists: never overwrite'
assert not api(f'repos/{repo}')['private']
print('Approved successful source run; no existing v2.0.0 tag; experimental channel only.')
