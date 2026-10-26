#!/usr/bin/env python3
"""Bundle license text for modules selected by the Android build, not the Windows graph."""
import json, os, pathlib, shutil, subprocess
root = pathlib.Path(__file__).resolve().parents[1]
out = root/'apps/android/app/src/main/assets/licenses/go'
out.mkdir(parents=True, exist_ok=True)
env = os.environ | {'GOOS':'android','GOARCH':'arm64','CGO_ENABLED':'1'}
raw = subprocess.check_output(['go','list','-deps','-json','./packages/mobile'], cwd=root, env=env, text=True)
decoder = json.JSONDecoder(); modules = {}
while raw.strip():
    obj, end = decoder.raw_decode(raw.lstrip()); raw = raw.lstrip()[end:]
    m = obj.get('Module')
    if m and not m.get('Main'): modules[m['Path']] = m
mobile = json.loads(subprocess.check_output(['go','list','-m','-json','golang.org/x/mobile'],cwd=root,text=True))
modules[mobile['Path']] = mobile
index=[];missing=[]
for name,m in sorted(modules.items()):
    directory=pathlib.Path(m['Dir']); found=[]
    for p in directory.iterdir():
        if p.is_file() and p.name.upper().startswith(('LICENSE','COPYING','NOTICE','PATENTS')):
            target=name.replace('/','_')+'@'+m['Version']+'__'+p.name
            shutil.copyfile(p,out/target);found.append(target)
    if not any('__LICENSE' in n.upper() or '__COPYING' in n.upper() for n in found):missing.append(name)
    index.append({'module':name,'version':m['Version'],'files':found})
assert not missing, 'Missing Android selected-module license text: '+', '.join(missing)
goroot=pathlib.Path(subprocess.check_output(['go','env','GOROOT'],text=True).strip())
shutil.copyfile(goroot/'LICENSE',out/'Go-LICENSE.txt')
(out/'index.json').write_text(json.dumps(index,indent=2)+'\n')
shutil.copyfile(root/'go.mod',out/'go.mod.txt');shutil.copyfile(root/'go.sum',out/'go.sum.txt')
print('Bundled Android selected-module licenses:',len(index))
