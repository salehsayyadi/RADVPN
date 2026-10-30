"""Mocked publication contract only; no real build/release is claimed by these tests."""
import hashlib
import importlib.util
import io
import json
import os
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/publish-manual-test303.py'
spec = importlib.util.spec_from_file_location('candidate_publisher', SCRIPT)
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)
HEAD = 'f' * 40

def digest(value):
    return hashlib.sha256(value).hexdigest()

def archive(files):
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w') as package:
        for name, value in files.items(): package.writestr(name, value)
    return data.getvalue()

class PublisherContract(unittest.TestCase):
    def exercise(self, mode='happy'):
        win = {'RADVPN-Setup-3.0.3-x64.exe': b'fixture-exe', 'RADVPN-Windows-Portable-3.0.3-x64.zip': b'fixture-zip', 'upgrade-303-report.txt': b'fixture', 'native-tests-303.json': json.dumps({'passed': mode != 'native_failed'}).encode()}
        win['SHA256SUMS.txt'] = ''.join(digest(win[n]) + '  ' + n + '\n' for n in list(win)[:2]).encode()
        apk = b'fixture-apk'
        android = {'RADVPN-Android-3.0.3-MANUAL-TEST.apk': apk, 'ANDROID-MANUAL-PROVENANCE.json': json.dumps(dict(source_sha=HEAD, package='app.radvpn.testing', production_signing_compatible=False, sha256=digest(apk))).encode(), 'ANDROID-MANUAL-INSTALL-REPORT.json': json.dumps(dict(passed=True, native_password_test_passed=True, apk_sha256=digest(apk))).encode(), 'ANDROID-TEST-README.txt': b'fixture'}
        archives = {1: archive(win), 2: archive(android)}
        uploads, created, reports, requests = {}, [], [], []
        def api(path, method='GET', body=None, binary=None):
            requests.append((method, path))
            if path.startswith('actions/workflows/'):
                return {'workflow_runs': [dict(id=901, head_sha=HEAD, head_branch='feature/radvpn-3.0.3', status='completed', conclusion='failure' if mode == 'build_failed' else 'success')]}
            if '/jobs?' in path:
                return {'jobs': [dict(name=n, conclusion='success') for n in (['build-android-manual'] if '/900/' in path else ['windows', 'android'])]}
            if '/artifacts?' in path:
                aid = 1 if '/901/' in path else 2
                return {'artifacts': [dict(id=aid, name='RADVPN-3.0.3-Windows-candidate' if aid == 1 else 'RADVPN-303-Android-manual-test', expired=False, workflow_run={'head_sha': HEAD}, digest='sha256:' + ('bad' if mode == 'tampered' and aid == 1 else digest(archives[aid])))]}
            if path.startswith('actions/artifacts/'):
                Path(binary).write_bytes(archives[int(path.split('/')[2])]); return
            if path.startswith('actions/runs/'):
                return dict(head_sha='a' * 40 if mode == 'wrong_source' and path.endswith('901') else HEAD)
            if path == 'releases/latest': return {'id': 100}
            if path == 'releases' and method == 'POST': created.append(body); return {'id': 101}
            if path == 'releases/101':
                if method == 'PATCH':
                    self.assertEqual(body, dict(draft=False, prerelease=True, make_latest='false'))
                    return dict(prerelease=True, draft=False, html_url='https://example.invalid/rc.2', assets=list(uploads.values()))
                return {'assets': list(uploads.values())}
            if path.startswith('contents/'):
                if method == 'GET': raise subprocess.CalledProcessError(1, ['gh'])
                import base64
                reports.append(json.loads(base64.b64decode(body['content']))); return {}
            self.fail((path, method))
        def upload(args, **kwargs):
            self.assertEqual(args[:3], ['gh', 'release', 'upload'])
            for filename in args[4:args.index('--repo')]:
                path = Path(filename)
                uploads[path.name] = dict(name=path.name, size=path.stat().st_size, digest='sha256:' + digest(path.read_bytes()), browser_download_url='https://example.invalid/' + path.name)
            return subprocess.CompletedProcess(args, 0)
        previous = Path.cwd()
        with tempfile.TemporaryDirectory() as directory:
            try:
                os.chdir(directory); Path('.github').mkdir()
                Path('.github/manual-test303-request.json').write_text(json.dumps({'release_tag': 'v3.0.3' if mode == 'stable_tag' else 'v3.0.3-rc.2'}))
                with patch.dict(os.environ, GITHUB_REPOSITORY=publisher.REPO, GITHUB_SHA=HEAD, GITHUB_RUN_ID='900'), patch.object(publisher, 'api', side_effect=api), patch.object(publisher.subprocess, 'run', side_effect=upload):
                    if mode == 'happy': publisher.publish()
                    else:
                        with self.assertRaises(AssertionError): publisher.publish()
            finally: os.chdir(previous)
        self.assertFalse(any(method == 'DELETE' for method, _ in requests))
        if mode == 'happy':
            self.assertTrue(reports[-1]['published']); self.assertTrue(created[0]['draft']); self.assertTrue(created[0]['prerelease']); self.assertEqual(created[0]['make_latest'], 'false')
            self.assertTrue({'RADVPN-3.0.3-Android-Test.apk', 'RADVPN-3.0.3-Windows-x64-Setup.exe', 'RADVPN-3.0.3-Windows-x64-Portable.zip', 'SHA256SUMS.txt', 'RELEASE-PROVENANCE.json'} <= uploads.keys())
            self.assertEqual(reports[-1]['windows_source_sha'], HEAD)
        else:
            self.assertFalse(created); self.assertFalse(uploads)
            if reports: self.assertFalse(reports[-1]['published'])
    def test_unique_draft_prerelease_both_current_sources_named_assets(self): self.exercise()
    def test_failed_current_build_does_not_fallback_to_old_windows(self): self.exercise('build_failed')
    def test_wrong_source_not_published(self): self.exercise('wrong_source')
    def test_tampered_archive_not_published(self): self.exercise('tampered')
    def test_failed_windows_native_smoke_not_published(self): self.exercise('native_failed')
    def test_stable_tag_not_allowed(self): self.exercise('stable_tag')

if __name__ == '__main__': unittest.main()
