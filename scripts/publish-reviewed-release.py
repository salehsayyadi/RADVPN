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
run('create_tag',['api','--method','POST',f'repos/{repo}/git/refs','-f','ref=refs/tags/'+tag,'-f','sha='+sha])
assets=[str(p) for p in sorted(pathlib.Path('release/assets').iterdir()) if p.is_file()]
assert assets
run('create_release',['release','create',tag,*assets,'--repo',repo,'--verify-tag','--latest','--title','RADVPN '+version+' — Lightning','--notes-file','docs/RELEASE_NOTES_'+version+'.md'])
run('verify_release',['release','view',tag,'--repo',repo,'--json','url,isPrerelease,assets'])
