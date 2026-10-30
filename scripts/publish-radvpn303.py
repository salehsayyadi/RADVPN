#!/usr/bin/env python3
"""Publish only the pinned, passed Windows candidate as an explicitly limited prerelease."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.request
import zipfile

REPO = 'salehsayyadi/RADVPN'
ROOT = Path(__file__).resolve().parents[1]
EXPECTED_SOURCE = '3c810acab5bcc907d7874a74a7c40b05fa7465ab'
EXPECTED_RUN = 41024307785
EXPECTED_ARTIFACT = 12963365843
EXPECTED_DIGEST = 'sha256:dda013860e0fdd2c8d6f30a0a2d96ae4bc80e00b0ccd544ed10de6f2496c9b36'
BINARIES = {'RADVPN-Setup-3.0.3-x64.exe', 'RADVPN-Windows-Portable-3.0.3-x64.zip'}

def gh(path, method='GET', body=None, binary_path=None, octet_stream=False):
    args = ['gh', 'api', '--method', method, '-H', 'X-GitHub-Api-Version: 2022-11-28', f'repos/{REPO}/{path}']
    if octet_stream: args += ['-H', 'Accept: application/octet-stream']
    payload = None
    if body is not None:
        args += ['--input', '-']; payload = json.dumps(body).encode()
    if binary_path:
        with Path(binary_path).open('wb') as output:
            subprocess.run(args, input=payload, stdout=output, check=True)
        return None
    return json.loads(subprocess.check_output(args, input=payload))

def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        while chunk := stream.read(1024 * 1024): h.update(chunk)
    return h.hexdigest()

def absent_public(path):
    try:
        with urllib.request.urlopen(f'https://api.github.com/repos/{REPO}/{path}', timeout=30):
            raise RuntimeError('Publication target already exists; refusing to replace it')
    except urllib.error.HTTPError as exc:
        if exc.code != 404: raise

def prepare(archive, destination, digest):
    if 'sha256:' + sha(archive) != digest:
        raise RuntimeError('Complete GitHub artifact digest mismatch')
    destination = Path(destination); destination.mkdir(parents=True, exist_ok=False)
    selected = BINARIES | {'SHA256SUMS.txt', 'upgrade-303-report.txt', 'native-tests-303.json', 'ui-report.json'}
    with zipfile.ZipFile(archive) as package:
        for name in selected:
            matches = [i for i in package.infolist() if i.filename == name or i.filename.endswith('/' + name)]
            if len(matches) != 1: raise RuntimeError('Missing or ambiguous verified asset: ' + name)
            info = matches[0]
            if info.is_dir() or info.file_size > 536870912: raise RuntimeError('Invalid asset bounds')
            # Write an explicit basename, never extract an untrusted archive path.
            with package.open(info) as source, (destination / name).open('wb') as target:
                while chunk := source.read(1024 * 1024): target.write(chunk)
    declared = {}
    for line in (destination / 'SHA256SUMS.txt').read_text(encoding='utf-8-sig').splitlines():
        if not line.strip(): continue
        digest_value, name = line.split()
        if name not in BINARIES or name in declared: raise RuntimeError('Unexpected or duplicate checksum entry')
        declared[name] = digest_value
    if set(declared) != BINARIES: raise RuntimeError('Incomplete binary checksums')
    for name, expected in declared.items():
        if sha(destination / name) != expected: raise RuntimeError('Binary checksum mismatch')
    report = (destination / 'upgrade-303-report.txt').read_text(encoding='utf-8-sig')
    if 'FAIL' in report or not all(x in report for x in ['PASS actual distributed v2.0.3', 'PASS upgraded native UI', 'PASS previous portable ZIP', 'existing profile, selection, subscription, language and connection settings retained']):
        raise RuntimeError('Actual upgrade evidence is incomplete')
    if json.loads((destination / 'native-tests-303.json').read_text())['passed'] is not True:
        raise RuntimeError('Native test evidence failed')
    ui = json.loads((destination / 'ui-report.json').read_text())
    if ui['passed'] is not True or ui['failures'] or len(ui['captures']) != 72:
        raise RuntimeError('UI evidence failed')
    if sum(x['page'] == 'account' for x in ui['captures']) != 4:
        raise RuntimeError('Missing native account captures')
    return {p.name: {'sha256': sha(p), 'bytes': p.stat().st_size} for p in destination.iterdir()}

def main():
    if os.environ.get('GITHUB_REPOSITORY') != REPO: raise RuntimeError('Wrong repository')
    approval = json.loads((ROOT / '.github/radvpn303-release-approval.json').read_text())
    assert approval == dict(version='3.0.3', source_sha=EXPECTED_SOURCE, run_id=EXPECTED_RUN,
                           artifact_id=EXPECTED_ARTIFACT, artifact_digest=EXPECTED_DIGEST,
                           prerelease=True, make_latest=False, platform_scope='windows-only')
    run = gh(f'actions/runs/{EXPECTED_RUN}')
    if (run['head_sha'], run['status'], run['conclusion']) != (EXPECTED_SOURCE, 'completed', 'success'):
        raise RuntimeError('Pinned source run did not pass')
    jobs = gh(f'actions/runs/{EXPECTED_RUN}/jobs')['jobs']
    for name in ('windows', 'android'):
        matching = [j for j in jobs if j['name'] == name]
        if len(matching) != 1 or matching[0]['conclusion'] != 'success': raise RuntimeError('Candidate job failed')
    artifact = gh(f'actions/artifacts/{EXPECTED_ARTIFACT}')
    if artifact['expired'] or artifact['digest'] != EXPECTED_DIGEST or artifact['workflow_run']['head_sha'] != EXPECTED_SOURCE:
        raise RuntimeError('Artifact provenance mismatch')
    absent_public('releases/tags/v3.0.3'); absent_public('git/ref/tags/v3.0.3')
    latest_before = gh('releases/latest')['id']
    work = ROOT / 'release303'; work.mkdir(exist_ok=False)
    archive = work / 'candidate.zip'
    gh(f'actions/artifacts/{EXPECTED_ARTIFACT}/zip', binary_path=archive)
    assets = work / 'assets'; manifest = prepare(archive, assets, EXPECTED_DIGEST)
    (assets / 'VERIFIED_BUILD.json').write_text(json.dumps(dict(source_sha=EXPECTED_SOURCE,
        run_id=EXPECTED_RUN, artifact_id=EXPECTED_ARTIFACT, verified_archive_digest=EXPECTED_DIGEST,
        platform_scope='windows-only', android_signed_apk=False, production_e2e_passed=False,
        assets=manifest), indent=2) + '\n')
    # New release/tag only. Leave any partial upload as a non-public draft, never a partial public release.
    drafts = [x for x in gh('releases?per_page=100') if x['tag_name'] == 'v3.0.3']
    if not drafts:
        subprocess.run(['gh', 'release', 'create', 'v3.0.3', *map(str, sorted(assets.iterdir())),
            '--repo', REPO, '--target', EXPECTED_SOURCE, '--draft', '--prerelease', '--latest=false',
            '--title', 'RADVPN 3.0.3 — Windows testing pre-release', '--notes-file', str(ROOT / 'docs/RELEASE_NOTES_3.0.3.md')], check=True)
        drafts = [x for x in gh('releases?per_page=100') if x['tag_name'] == 'v3.0.3']
    # A newly created draft has no published tag yet; get-by-tag can return 404.
    # Resume only this publisher's exact source/title/notes; never overwrite another release.
    notes = (ROOT / 'docs/RELEASE_NOTES_3.0.3.md').read_text().strip()
    if not drafts or any(not x['draft'] or x['target_commitish'] != EXPECTED_SOURCE
                         or x['name'] != 'RADVPN 3.0.3 — Windows testing pre-release'
                         or x['body'].strip() != notes for x in drafts):
        raise RuntimeError('Unexpected existing draft; manual review required')
    draft = gh('releases/' + str(max(drafts, key=lambda x: x['id'])['id']))
    if not draft['draft'] or not draft['prerelease'] or draft['target_commitish'] != EXPECTED_SOURCE:
        raise RuntimeError('Unexpected draft state; refusing publication')
    uploaded = {a['name']: a for a in draft['assets']}
    if set(uploaded) != {p.name for p in assets.iterdir()}: raise RuntimeError('Incomplete uploaded release assets')
    for path in assets.iterdir():
        compare_path = path
        if path.name == 'VERIFIED_BUILD.json':
            # JSON object order varied in an earlier draft; verify semantic provenance AND actual bytes.
            compare_path = work / 'uploaded-provenance.json'
            gh('releases/assets/' + str(uploaded[path.name]['id']), binary_path=compare_path, octet_stream=True)
            if json.loads(compare_path.read_text()) != json.loads(path.read_text()):
                raise RuntimeError('Uploaded build provenance mismatch')
        if uploaded[path.name].get('digest') != 'sha256:' + sha(compare_path) or uploaded[path.name]['size'] != compare_path.stat().st_size:
            raise RuntimeError('Uploaded asset integrity mismatch; draft left unpublished')
    result = gh(f"releases/{draft['id']}", method='PATCH', body=dict(draft=False, prerelease=True, make_latest='false'))
    if result['draft'] or not result['prerelease']: raise RuntimeError('Unexpected publication state')
    if gh('releases/latest')['id'] != latest_before: raise RuntimeError('Latest changed during publication; inspect immediately')
    print('Published WINDOWS-ONLY testing prerelease:', result['html_url'])
    print('Android APK absent; live ecosystem deployment/E2E remains blocked. Latest unchanged.')
    for path in assets.iterdir(): print(sha(path), path.name)

if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        # Only fixed validation messages / public API paths; never dump environment or credentials.
        message = (type(exc).__name__ + ': ' + str(exc)).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print('::error title=RADVPN publication blocked::' + message, flush=True)
        raise

