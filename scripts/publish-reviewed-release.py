#!/usr/bin/env python3
"""Publish reviewed immutable packages; errors expose only allowlisted categories."""
import hashlib, json, os, pathlib, subprocess, sys
approval=json.loads(pathlib.Path('.github/unified-release-approval.json').read_text())
repo=os.environ['GITHUB_REPOSITORY']
sha=approval['source_sha']
version=approval['version']
tag='v'+version
def run(stage,args):
 result=subprocess.run(['gh']+args,text=True,capture_output=True)
 if result.returncode:
  text=(result.stderr+result.stdout).lower()
  category=next((label for needle,label in [('403','http403'),('404','http404'),('422','http422'),('already exists','already_exists'),('workflow','workflow_permission'),('permission','permission'),('validation','validation'),('authentication','authentication')] if needle in text),'unclassified_cli_error')
  print('::error::Publication failed: '+stage+' / '+category,flush=True)
  sys.exit(1)
 print('Completed '+stage,flush=True)
 return result.stdout
# The owner requested asset replacement inside v2.0.3, not a new tag or release.
assert approval.get('replace_existing_assets') is True
existing=json.loads(run('inspect_existing_release',['api',f'repos/{repo}/releases/tags/{tag}']))
assert existing['id']==approval['existing_release_id'] and not existing['draft']
ref=json.loads(run('inspect_existing_tag',['api',f'repos/{repo}/git/ref/tags/{tag}']))
assert ref['object']['sha']==approval['existing_tag_sha']
comparison=subprocess.run(['gh','api',f'repos/{repo}/compare/{sha}...'+os.environ['GITHUB_SHA']],text=True,capture_output=True,check=True)
allowed={'.github/unified-release-approval.json','.github/workflows/unified-release.yml','scripts/verify-unified-release.py','scripts/prepare-unified-release.py','scripts/publish-reviewed-release.py','docs/RELEASE_NOTES_2.0.3.md'}
assert all(f['filename'] in allowed for f in json.loads(comparison.stdout)['files']), 'Publication commit changed unreviewed application sources'
assets=[str(p) for p in sorted(pathlib.Path('release/assets').iterdir()) if p.is_file()]
assert assets
# Upload all verified packages, then the checksum manifest last.
for asset in [p for p in assets if pathlib.Path(p).name!='SHA256SUMS.txt']:
 run('replace_verified_asset',['release','upload',tag,asset,'--repo',repo,'--clobber'])
run('replace_checksums',['release','upload',tag,'release/assets/SHA256SUMS.txt','--repo',repo,'--clobber'])
run('update_release_notes',['release','edit',tag,'--repo',repo,'--notes-file','docs/RELEASE_NOTES_'+version+'.md'])
updated=json.loads(run('verify_replacement',['api',f'repos/{repo}/releases/tags/{tag}']))
assert updated['id']==approval['existing_release_id']
remote={a['name']:a for a in updated['assets']}
for path in map(pathlib.Path,assets):
 item=remote[path.name]
 assert item['size']==path.stat().st_size
 assert item.get('digest')=='sha256:'+hashlib.sha256(path.read_bytes()).hexdigest(), 'Published asset digest mismatch'
ref=json.loads(run('verify_unchanged_tag',['api',f'repos/{repo}/git/ref/tags/{tag}']))
assert ref['object']['sha']==approval['existing_tag_sha']
print('PASS replaced verified assets in existing release; unchanged version and tag',flush=True)
