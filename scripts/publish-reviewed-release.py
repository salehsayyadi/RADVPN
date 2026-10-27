#!/usr/bin/env python3
"""Publish reviewed immutable packages; errors expose only allowlisted categories."""
import json, os, pathlib, subprocess, sys
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
# Tag the publication commit, while binaries remain from the approved immutable source.
# GitHub Actions cannot tag an older commit with a different workflow tree using GITHUB_TOKEN.
comparison=subprocess.run(['gh','api',f'repos/{repo}/compare/{sha}...'+os.environ['GITHUB_SHA']],text=True,capture_output=True,check=True)
allowed={'.github/unified-release-approval.json','.github/workflows/unified-release.yml','scripts/verify-unified-release.py','scripts/prepare-unified-release.py','scripts/publish-reviewed-release.py','docs/RELEASE_NOTES_2.0.3.md'}
assert all(f['filename'] in allowed for f in json.loads(comparison.stdout)['files']), 'Publication commit changed unreviewed application sources'
run('create_tag',['api' ,'--method','POST',f'repos/{repo}/git/refs','-f','ref=refs/tags/'+tag,'-f','sha='+os.environ['GITHUB_SHA']])
assets=[str(p) for p in sorted(pathlib.Path('release/assets').iterdir()) if p.is_file()]
assert assets
run('create_release',['release','create',tag,*assets,'--repo',repo,'--verify-tag','--latest','--title','RADVPN '+version+' — Lightning','--notes-file','docs/RELEASE_NOTES_'+version+'.md'])
run('verify_release',['release','view',tag,'--repo',repo,'--json','url,isPrerelease,assets'])
