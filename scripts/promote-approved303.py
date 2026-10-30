#!/usr/bin/env python3
"""Owner-approved release metadata promotion. Exact tested bytes, no rebuild/key/tag rewrite."""
import base64
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

REPO = 'salehsayyadi/RADVPN'
OUT = Path('stable-release-assets')
AUDIT = Path('stable-release-audit')
REQUEST = Path('.github/stable303-approval.json')


def api(path, method='GET', body=None, binary=None):
    args = ['gh', 'api', '--method', method, f'repos/{REPO}/{path}']
    payload = None
    if body is not None:
        args += ['--input', '-']; payload = json.dumps(body).encode()
    if binary is not None:
        args += ['-H', 'Accept: application/octet-stream']
        with Path(binary).open('wb') as target:
            subprocess.run(args, input=payload, stdout=target, check=True)
    else:
        raw = subprocess.check_output(args, input=payload)
        return json.loads(raw) if raw.strip() else None


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def fetch_asset(source, name, destination, expected=None):
    matches = [a for a in source['assets'] if a['name'] == name and a['state'] == 'uploaded']
    assert len(matches) == 1, 'Missing or ambiguous source asset'
    asset = matches[0]
    assert re.fullmatch(r'sha256:[a-f0-9]{64}', asset.get('digest', '')), 'Source asset digest unavailable'
    if expected is not None:
        assert asset['digest'] == 'sha256:' + expected, 'Owner-approved source asset changed'
    api('releases/assets/' + str(asset['id']), binary=destination)
    assert destination.stat().st_size == asset['size'] and asset['digest'] == 'sha256:' + sha(destination), 'Source download integrity failed'
    return asset


def verify_destination(release_id, paths):
    release = api('releases/' + str(release_id))
    assets = {a['name']: a for a in release['assets']}
    assert set(assets) == {p.name for p in paths}, 'Destination asset set mismatch'
    for path in paths:
        assert assets[path.name]['state'] == 'uploaded'
        assert assets[path.name]['size'] == path.stat().st_size
        assert assets[path.name]['digest'] == 'sha256:' + sha(path), 'Destination asset digest mismatch'
    return release


def record(report):
    (AUDIT / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    path = 'contents/docs/stable303/latest.json'
    payload = dict(message='docs: record owner-approved stable publication and prerelease cleanup', branch='feature/radvpn-3.0.3', content=base64.b64encode((json.dumps(report, indent=2) + '\n').encode()).decode())
    try:
        payload['sha'] = api(path + '?ref=feature/radvpn-3.0.3')['sha']
    except subprocess.CalledProcessError:
        pass
    api(path, method='PUT', body=payload)


def promote():
    assert os.environ['GITHUB_REPOSITORY'] == REPO
    approval = json.loads(REQUEST.read_text())
    assert approval['owner_test_accepted'] is True
    assert approval['promote_to_latest'] is True and approval['delete_listed_prereleases'] is True
    assert approval['target_tag'] == '3.0.3'  # New unprefixed semver tag; historical v3.0.3 ref is not moved.
    assert approval['android_identity_policy'] == 'preserve_exact_tested_standalone_apk_no_upgrade_claim'
    source_sha = approval['source_sha']
    assert re.fullmatch(r'[a-f0-9]{40}', source_sha)
    OUT.mkdir(); AUDIT.mkdir()
    report = dict(published=False, cleanup_complete=False, target_tag=approval['target_tag'], binary_source_sha=source_sha, automation_sha=os.environ['GITHUB_SHA'], owner_test_accepted=True, android_package='app.radvpn.testing', android_version_name='3.0.3-testing', android_in_place_production_upgrade_verified=False, deleted_prerelease_ids=[], preserved_git_tags=True)
    try:
        source = api('releases/' + str(approval['source_release_id']))
        assert source['tag_name'] == approval['source_tag'] and source['prerelease'] is True and not source['draft']
        assert source['target_commitish'] == source_sha
        snapshot = api('releases?per_page=100')
        assert len(snapshot) < 100, 'Paginate/review the release inventory before cleanup'
        assert not any(r['tag_name'] == approval['target_tag'] for r in snapshot), 'Target release already exists; do not replace it'
        # A new tag is created explicitly at the tested source. No existing ref is updated/deleted.
        refs = api('git/matching-refs/tags/' + approval['target_tag'])
        assert not any(r['ref'] == 'refs/tags/' + approval['target_tag'] for r in refs), 'Target tag already exists; do not rewrite it'
        inventory = {r['id']: r for r in snapshot}
        cleanup = approval['prereleases_to_delete']
        assert len({r['id'] for r in cleanup}) == len(cleanup)
        for item in cleanup:
            actual = inventory[item['id']]
            assert actual['prerelease'] is True and actual['tag_name'] == item['tag']
            assert actual['id'] != approval['preserve_release_id']
        assert approval['preserve_release_id'] in inventory and inventory[approval['preserve_release_id']]['prerelease'] is False
        assert api('releases/latest')['id'] == approval['preserve_release_id'], 'Latest changed since owner review'
        (AUDIT / 'prior-release-metadata.json').write_text(json.dumps([api('releases/' + str(r['id'])) for r in cleanup], indent=2) + '\n')
        for item in approval['binary_assets']:
            assert item['destination'] in ('RADVPN-Android.apk', 'RADVPN-Windows-Setup.exe', 'RADVPN-Windows-Portable.zip')
            fetch_asset(source, item['source'], OUT / item['destination'], item['sha256'])
        assert {p.name for p in OUT.iterdir()} == {'RADVPN-Android.apk', 'RADVPN-Windows-Setup.exe', 'RADVPN-Windows-Portable.zip'}
        for name in ('ANDROID-MANUAL-PROVENANCE.json', 'ANDROID-MANUAL-INSTALL-REPORT.json', 'native-tests-303.json', 'RELEASE-PROVENANCE.json'):
            fetch_asset(source, name, AUDIT / name)
        android = json.loads((AUDIT / 'ANDROID-MANUAL-PROVENANCE.json').read_text())
        assert android['source_sha'] == source_sha and android['package'] == 'app.radvpn.testing'
        assert android['production_signing_compatible'] is False and android['sha256'] == sha(OUT / 'RADVPN-Android.apk')
        install = json.loads((AUDIT / 'ANDROID-MANUAL-INSTALL-REPORT.json').read_text())
        assert install['passed'] is True and install['native_password_test_passed'] is True and install['apk_sha256'] == android['sha256']
        assert json.loads((AUDIT / 'native-tests-303.json').read_text())['passed'] is True
        provenance = json.loads((AUDIT / 'RELEASE-PROVENANCE.json').read_text())
        assert provenance['windows_source_sha'] == source_sha and provenance['android_source_sha'] == source_sha
        # Preserve truth about the tested Android flavor. Filename/Latest changes cannot fix signing/package identity.
        metadata = dict(source_sha=source_sha, package='app.radvpn.testing', version_name='3.0.3-testing', filename='RADVPN-Android.apk', signer_sha256=android['signer_sha256'], production_signing_compatible=False, production_in_place_upgrade_verified=False)
        (OUT / 'Android-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
        build = dict(source_sha=source_sha, release_tag=approval['target_tag'], source_release_id=source['id'], source_release_tag=source['tag_name'], source_validation=provenance, owner_manual_acceptance=True, no_rebuild=True, android_identity_unchanged=True, android_production_in_place_upgrade_verified=False, binary_assets=approval['binary_assets'])
        (OUT / 'build-provenance.json').write_text(json.dumps(build, indent=2) + '\n')
        body = f'''# RADVPN 3.0.3

نسخهٔ مورد تأیید مالک، به‌عنوان انتشار اصلی GitHub و Latest. فایل‌ها دقیقاً همان باینری‌های RC2 تأییدشده هستند؛ بیلد یا امضای جدید تولید نشده است.

## دانلود / Downloads
- `RADVPN-Android.apk`
- `RADVPN-Windows-Setup.exe`
- `RADVPN-Windows-Portable.zip`
- `SHA256SUMS.txt`, `Android-metadata.json`, `build-provenance.json`

## Changes
- Login-first RADVPN account flow and Telegram registration entry point.
- Automatically import the existing account subscription after code login/session restore; no manual account import step, duplicate subscription or automatic VPN connection.
- Guard stale asynchronous imports after logout/account changes; retry transient failures.

## Android installation warning — unchanged tested package
**The Android APK remains the exact owner-tested standalone `app.radvpn.testing` package, version `3.0.3-testing`, app label `RADVPN Test 3.0.3`.** Its filename is now `RADVPN-Android.apk`, but this does NOT turn it into an in-place update to the older `app.radvpn` installation. Production signing continuity is NOT verified. Keep the existing production app; do not uninstall it expecting encrypted settings to remain recoverable. A future normal-identity production upgrade still requires a compatible persistent signing identity and a separate build/upgrade verification. GitHub's non-prerelease/Latest classification is not a claim that this limitation has been fixed.

## Validation and provenance
Owner reported manual acceptance of RC2. Both platforms use source `{source_sha}` with verified published-file SHA-256 digests. Existing Windows native/previous-release installer tests, Android installed login/Keystore fixtures, and sandbox install/password-mask tests passed. This promotion reuses those exact tested bytes; it is not an independent live VPN/payment/leak-protection certification. Windows executables remain unsigned.

Historical `v3.0.3` test Git refs are not rewritten. This release uses the new semver tag `3.0.3` pointing to the actual tested source. Earlier Pre-release objects are removed only after this replacement and its uploaded files are verified. The `v2.0.3` release remains available.
'''
        (OUT / 'RELEASE-NOTES.md').write_text(body)
        (OUT / 'SHA256SUMS.txt').write_text(''.join(sha(p) + '  ' + p.name + '\n' for p in sorted(OUT.iterdir())))
        # Explicit new ref, never force/update an existing historical tag.
        api('git/refs', method='POST', body=dict(ref='refs/tags/' + approval['target_tag'], sha=source_sha))
        release = api('releases', method='POST', body=dict(tag_name=approval['target_tag'], target_commitish=source_sha, name='RADVPN 3.0.3', body=body, draft=True, prerelease=False, make_latest='false'))
        report['replacement_release_id'] = release['id']
        paths = sorted(OUT.iterdir())
        subprocess.run(['gh', 'release', 'upload', approval['target_tag'], *[str(p) for p in paths], '--repo', REPO], check=True)
        verify_destination(release['id'], paths)
        tag = api('git/ref/tags/' + approval['target_tag'])
        assert tag['object']['sha'] == source_sha, 'Release source ref mismatch'
        published = api('releases/' + str(release['id']), method='PATCH', body=dict(draft=False, prerelease=False, make_latest='true'))
        assert not published['draft'] and not published['prerelease']
        assert api('releases/latest')['id'] == release['id'], 'Replacement is not Latest'
        verified = verify_destination(release['id'], paths)
        report.update(published=True, release_url=published['html_url'], assets=[dict(name=a['name'], size=a['size'], digest=a['digest'], url=a['browser_download_url']) for a in verified['assets']])
        # Destructive cleanup is deliberately last and restricted to the reviewed prerelease IDs.
        for item in cleanup:
            actual = api('releases/' + str(item['id']))
            assert actual['prerelease'] is True and actual['tag_name'] == item['tag']
            assert actual['id'] not in (release['id'], approval['preserve_release_id'])
            api('releases/' + str(item['id']), method='DELETE')
            report['deleted_prerelease_ids'].append(item['id'])
        remaining = api('releases?per_page=100')
        assert not any(r['prerelease'] for r in remaining), 'Unreviewed/new prerelease remains; do not silently delete it'
        assert any(r['id'] == approval['preserve_release_id'] for r in remaining)
        assert api('releases/latest')['id'] == release['id']
        report['cleanup_complete'] = True
        print('Verified owner-approved Latest release:', published['html_url'])
    except Exception as exc:
        report['error_type'] = type(exc).__name__
        raise
    finally:
        record(report)


if __name__ == '__main__':
    promote()
