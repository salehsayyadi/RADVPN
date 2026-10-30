#!/usr/bin/env python3
"""Publish a new prerelease only, with both platform artifacts from this exact commit."""
import base64
import hashlib
import json
import os
import re
import subprocess
import time
import zipfile
from pathlib import Path

REPO = 'salehsayyadi/RADVPN'
ROOT = Path('manual-release-assets')

def api(path, method='GET', body=None, binary=None):
    args = ['gh', 'api', '--method', method, f'repos/{REPO}/{path}']
    payload = None
    if body is not None:
        args += ['--input', '-']
        payload = json.dumps(body).encode()
    if binary:
        with Path(binary).open('wb') as out:
            subprocess.run(args, input=payload, stdout=out, check=True)
    else:
        return json.loads(subprocess.check_output(args, input=payload))

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def candidate_run(source, attempts=24):
    """Never substitute the previous successful build when the current one fails."""
    for attempt in range(attempts):
        runs = api(f'actions/workflows/radvpn303.yml/runs?head_sha={source}&event=push&per_page=20')['workflow_runs']
        matching = [r for r in runs if r['head_sha'] == source and r.get('head_branch') == 'feature/radvpn-3.0.3']
        if matching:
            run = max(matching, key=lambda r: r['id'])
            if run['status'] == 'completed':
                assert run['conclusion'] == 'success', 'Current candidate validation did not pass'
                jobs = api(f'actions/runs/{run["id"]}/jobs?per_page=100')['jobs']
                assert all(any(j['name'] == name and j['conclusion'] == 'success' for j in jobs) for name in ('windows', 'android')), 'Both native platform checks must pass'
                return run
        if attempt + 1 < attempts:
            time.sleep(30)
    raise AssertionError('Current candidate validation is not ready; no older artifacts will be published')

def artifact(run_id, source, name, required, out):
    run = api(f'actions/runs/{run_id}')
    assert run['head_sha'] == source, 'Artifact run source mismatch'
    rows = api(f'actions/runs/{run_id}/artifacts?per_page=100')['artifacts']
    matches = [a for a in rows if a['name'] == name and not a['expired']]
    assert len(matches) == 1, 'Expected one unexpired platform artifact'
    found = matches[0]
    assert found['workflow_run']['head_sha'] == source
    archive = Path(name + '.zip')
    api(f'actions/artifacts/{found["id"]}/zip', binary=archive)
    assert found['digest'] == 'sha256:' + sha(archive), 'Artifact archive digest mismatch'
    with zipfile.ZipFile(archive) as package:
        for filename in required:
            entries = [entry for entry in package.infolist() if entry.filename == filename or entry.filename.endswith('/' + filename)]
            assert len(entries) == 1 and 0 < entries[0].file_size < 400_000_000, 'Missing/ambiguous artifact file'
            (out / filename).write_bytes(package.read(entries[0]))
    return found

def checksums_match(directory, manifest, names):
    entries = {}
    for line in manifest.splitlines():
        match = re.fullmatch(r'([a-fA-F0-9]{64})\s+\*?(.+)', line.strip())
        if match:
            entries[match[2]] = match[1].lower()
    assert all(entries.get(name) == sha(directory / name) for name in names), 'Platform file checksum mismatch'

def record(report):
    path = 'contents/docs/manual-test303/latest.json'
    content = base64.b64encode((json.dumps(report, indent=2) + '\n').encode()).decode()
    payload = dict(message='docs: record release candidate verification', content=content, branch='feature/radvpn-3.0.3')
    try:
        payload['sha'] = api(path + '?ref=feature/radvpn-3.0.3')['sha']
    except subprocess.CalledProcessError:
        pass
    api(path, method='PUT', body=payload)

def publish():
    assert os.environ['GITHUB_REPOSITORY'] == REPO
    source = os.environ['GITHUB_SHA']
    assert re.fullmatch(r'[a-f0-9]{40}', source)
    request = json.loads(Path('.github/manual-test303-request.json').read_text())
    tag = request['release_tag']
    assert re.fullmatch(r'v3\.0\.3-rc\.[1-9][0-9]*', tag), 'Only uniquely named release candidates are allowed'
    report = dict(tag=tag, published=False, source_sha=source, run_id=int(os.environ['GITHUB_RUN_ID']), scope='User-requested prerelease; stable promotion and old-test deletion require user acceptance')
    try:
        jobs = api(f'actions/runs/{os.environ["GITHUB_RUN_ID"]}/jobs?per_page=100')['jobs']
        assert any(j['name'] == 'build-android-manual' and j['conclusion'] == 'success' for j in jobs), 'Android build/install did not pass'
        validation = candidate_run(source)
        ROOT.mkdir()
        windows = ['RADVPN-Setup-3.0.3-x64.exe', 'RADVPN-Windows-Portable-3.0.3-x64.zip', 'SHA256SUMS.txt', 'upgrade-303-report.txt', 'native-tests-303.json']
        wa = artifact(validation['id'], source, 'RADVPN-3.0.3-Windows-candidate', windows, ROOT)
        checksums_match(ROOT, (ROOT / 'SHA256SUMS.txt').read_text(), windows[:2])
        assert json.loads((ROOT / 'native-tests-303.json').read_text())['passed'] is True, 'Windows native smoke did not pass'
        (ROOT / 'SHA256SUMS.txt').unlink()
        android = ['RADVPN-Android-3.0.3-MANUAL-TEST.apk', 'ANDROID-MANUAL-PROVENANCE.json', 'ANDROID-MANUAL-INSTALL-REPORT.json', 'ANDROID-TEST-README.txt']
        aa = artifact(os.environ['GITHUB_RUN_ID'], source, 'RADVPN-303-Android-manual-test', android, ROOT)
        ap = json.loads((ROOT / android[1]).read_text())
        assert ap['source_sha'] == source and ap['package'] == 'app.radvpn.testing' and ap['production_signing_compatible'] is False
        assert ap['sha256'] == sha(ROOT / android[0])
        install = json.loads((ROOT / android[2]).read_text())
        assert install['passed'] is True and install['native_password_test_passed'] is True and install['apk_sha256'] == ap['sha256']
        names = {
            windows[0]: 'RADVPN-3.0.3-Windows-x64-Setup.exe',
            windows[1]: 'RADVPN-3.0.3-Windows-x64-Portable.zip',
            android[0]: 'RADVPN-3.0.3-Android-Test.apk',
        }
        for old, new in names.items():
            (ROOT / old).rename(ROOT / new)
        provenance = dict(source_sha=source, windows_source_sha=source, android_source_sha=source, windows_run_id=validation['id'], android_run_id=int(os.environ['GITHUB_RUN_ID']), windows_artifact_digest=wa['digest'], android_artifact_digest=aa['digest'], filenames=names, android_production_upgrade_verified=False, live_account_vpn_acceptance_verified=False, stable_release_approved=False)
        (ROOT / 'RELEASE-PROVENANCE.json').write_text(json.dumps(provenance, indent=2) + '\n')
        body = f'''# RADVPN 3.0.3 — Release Candidate {tag.rsplit('.', 1)[1]}

نسخهٔ آزمایشی برای تست دستی؛ نسخهٔ اصلی نیست و جایگزین Latest نمی‌شود.

## Download / دانلود
- Android: **RADVPN-3.0.3-Android-Test.apk**
- Windows installer: **RADVPN-3.0.3-Windows-x64-Setup.exe**
- Windows portable: **RADVPN-3.0.3-Windows-x64-Portable.zip**

## Change to test
پس از ورود با کد ربات، اشتراک همان حساب باید خودکار دریافت و سرورها اضافه شوند؛ نیازی به افزودن دستی از بخش حساب نیست. اشتراک تکراری ساخته نمی‌شود و پس از خطای موقت شبکه، تلاش دوباره انجام می‌شود. VPN خودکار متصل نمی‌شود.

## Build verification (scoped, not live acceptance)
Both platform artifacts are built from `{source}`. Candidate validation passed Windows native smoke/previous-release upgrade checks and Android installed login-gate/Keystore checks including managed-subscription fixtures. The downloadable separate-identity Android sandbox APK also passed build, lint, package/signature verification, install alongside baseline and native password-mask checks. Artifact digests and per-file SHA-256 checksums are verified before publication.

## Important installation limits
Android uses **app.radvpn.testing**, display name **RADVPN Test 3.0.3**. It installs separately from the production app; it is **not** an in-place production upgrade. The test signer can differ from an older test build. Do not uninstall the production app to install this candidate. Windows binaries are experimental/unsigned. Live account login, physical-device VPN connectivity and user acceptance are not proven by build tests.

Confirm login automatically adds servers, session persistence and actual VPN operation on your devices. Stable/latest promotion, production-compatible Android signing, and removal of older test releases remain separate, post-acceptance steps. Existing releases and assets are preserved by this publisher.

Build: https://github.com/{REPO}/actions/runs/{os.environ['GITHUB_RUN_ID']}
Candidate validation: https://github.com/{REPO}/actions/runs/{validation['id']}
'''
        (ROOT / 'ANDROID-TEST-README.txt').write_text('Install RADVPN-3.0.3-Android-Test.apk. Separate app.radvpn.testing identity, not a production in-place upgrade. Preserve your production app. Test signer may differ from older test APKs. See release notes and RELEASE-PROVENANCE.json.\n')
        (ROOT / 'RELEASE-NOTES.md').write_text(body)
        (ROOT / 'SHA256SUMS.txt').write_text(''.join(sha(path) + '  ' + path.name + '\n' for path in sorted(ROOT.iterdir()) if path.is_file()))
        latest = api('releases/latest')['id']
        release = api('releases', method='POST', body=dict(tag_name=tag, target_commitish=source, name='RADVPN 3.0.3 — Release Candidate ' + tag.rsplit('.', 1)[1], body=body, draft=True, prerelease=True, make_latest='false'))
        subprocess.run(['gh', 'release', 'upload', tag, *[str(p) for p in sorted(ROOT.iterdir())], '--repo', REPO], check=True)
        uploaded = {a['name']: a for a in api('releases/' + str(release['id']))['assets']}
        assert set(uploaded) == {p.name for p in ROOT.iterdir()}
        assert all(uploaded[p.name]['digest'] == 'sha256:' + sha(p) for p in ROOT.iterdir()), 'Uploaded asset digest mismatch'
        published = api('releases/' + str(release['id']), method='PATCH', body=dict(draft=False, prerelease=True, make_latest='false'))
        assert published['prerelease'] and not published['draft'] and api('releases/latest')['id'] == latest
        report.update(published=True, release_url=published['html_url'], windows_source_sha=source, validation_run_id=validation['id'], assets=[dict(name=a['name'], size=a['size'], digest=a['digest'], url=a['browser_download_url']) for a in published['assets']])
        print('Verified release candidate:', published['html_url'])
    except Exception as exc:
        report['error_type'] = type(exc).__name__
        raise
    finally:
        record(report)

if __name__ == '__main__':
    publish()
