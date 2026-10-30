import copy
import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('promotion', Path(__file__).resolve().parents[1] / 'scripts/promote-approved303.py')
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)
APPROVAL = json.loads((Path(__file__).resolve().parents[1] / '.github/stable303-approval.json').read_text())


class PromotionContract(unittest.TestCase):
    def test_delete_accepts_empty_204(self):
        with patch.object(m.subprocess, 'check_output', return_value=b''):
            self.assertIsNone(m.api('releases/123', method='DELETE'))

    def test_destination_must_match_digest(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / 'one'; p.write_bytes(b'tested')
            asset = dict(name='one', state='uploaded', size=6, digest='sha256:' + '0' * 64)
            with patch.object(m, 'api', return_value={'assets': [asset]}):
                with self.assertRaises(AssertionError):
                    m.verify_destination(5, [p])

    def exercise(self, failure=None):
        events = []
        approval = copy.deepcopy(APPROVAL)
        source_sha = approval['source_sha']
        source = dict(id=approval['source_release_id'], tag_name=approval['source_tag'], target_commitish=source_sha, prerelease=True, draft=False, assets=[])
        old = [dict(id=i['id'], tag_name=i['tag'], prerelease=True) for i in approval['prereleases_to_delete']]
        stable = dict(id=approval['preserve_release_id'], tag_name='v2.0.3', prerelease=False)
        new = dict(id=900, tag_name='3.0.3', prerelease=False, draft=True, html_url='https://github.com/salehsayyadi/RADVPN/releases/tag/3.0.3')
        published = False
        deleted = []
        reports = []
        def api(path, method='GET', body=None, binary=None):
            nonlocal published
            events.append((method, path))
            if path == 'releases/' + str(source['id']) and method == 'GET':
                return source
            if path == 'releases?per_page=100':
                return [r for r in old if r['id'] not in deleted] + [stable] + ([new] if published else [])
            if path.startswith('git/matching-refs'):
                return [{'ref': 'refs/tags/3.0.3'}] if failure == 'existing_tag' else []
            if path == 'releases/latest':
                return new if published else stable
            if path == 'git/refs':
                self.assertEqual(body['sha'], source_sha)
                self.assertEqual(method, 'POST')
                return {}
            if path == 'releases' and method == 'POST':
                self.assertTrue(body['draft'])
                self.assertIn('app.radvpn.testing', body['body'])
                self.assertIn('NOT verified', body['body'])
                return new
            if path == 'git/ref/tags/3.0.3':
                return {'object': {'sha': source_sha}}
            if path == 'releases/900' and method == 'PATCH':
                published = True
                new['draft'] = False
                return new
            if path.startswith('releases/'):
                rid = int(path.split('/')[1])
                if method == 'DELETE':
                    self.assertTrue(published)
                    self.assertIn(('verify', '900'), events)
                    self.assertNotEqual(rid, stable['id'])
                    deleted.append(rid)
                    return None
                return next(r for r in old if r['id'] == rid)
            self.fail('Unexpected API request: ' + path)
        def fetch(source, name, destination, expected=None):
            if name.endswith(('.apk', '.exe', '.zip')):
                destination.write_bytes(b'exact bytes')
            else:
                data = {
                    'ANDROID-MANUAL-PROVENANCE.json': dict(source_sha=source_sha, package='app.radvpn.testing', production_signing_compatible=False, sha256=m.sha(m.OUT/'RADVPN-Android.apk'), signer_sha256='test-signer'),
                    'ANDROID-MANUAL-INSTALL-REPORT.json': dict(passed=True, native_password_test_passed=True, apk_sha256=m.sha(m.OUT/'RADVPN-Android.apk')),
                    'native-tests-303.json': dict(passed=failure != 'native'),
                    'RELEASE-PROVENANCE.json': dict(windows_source_sha=source_sha, android_source_sha='0'*40 if failure == 'source' else source_sha),
                }[name]
                destination.write_text(json.dumps(data))
        def verify(rid, paths):
            events.append(('verify', str(rid)))
            if failure == 'upload':
                raise AssertionError('asset digest mismatch')
            new['assets'] = [dict(name=p.name, size=p.stat().st_size, digest='sha256:'+m.sha(p), browser_download_url='https://example.com/'+p.name) for p in paths]
            return new
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            request = root/'approval.json'; request.write_text(json.dumps(approval))
            with patch.object(m, 'REQUEST', request), patch.object(m, 'OUT', root/'out'), patch.object(m, 'AUDIT', root/'audit'), patch.object(m, 'api', side_effect=api), patch.object(m, 'fetch_asset', side_effect=fetch), patch.object(m, 'verify_destination', side_effect=verify), patch.object(m.subprocess, 'run'), patch.object(m, 'record', side_effect=lambda r: reports.append(copy.deepcopy(r))), patch.dict(os.environ, GITHUB_REPOSITORY=m.REPO, GITHUB_SHA='1'*40):
                if failure:
                    with self.assertRaises(AssertionError):
                        m.promote()
                else:
                    m.promote()
        if failure:
            self.assertEqual(deleted, [])
            self.assertFalse(reports[-1]['cleanup_complete'])
        else:
            self.assertEqual(deleted, [i['id'] for i in approval['prereleases_to_delete']])
            self.assertTrue(reports[-1]['published'])
            self.assertTrue(reports[-1]['cleanup_complete'])
        self.assertFalse(any(method in ('DELETE','PATCH') and path.startswith('git/') for method,path in events))

    def test_publish_verify_then_cleanup(self): self.exercise()
    def test_no_cleanup_on_native_test_failure(self): self.exercise('native')
    def test_no_cleanup_on_wrong_binary_source(self): self.exercise('source')
    def test_no_cleanup_on_upload_integrity_failure(self): self.exercise('upload')
    def test_no_existing_tag_rewrite(self): self.exercise('existing_tag')


if __name__ == '__main__': unittest.main()
