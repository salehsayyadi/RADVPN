#!/usr/bin/env python3
"""Collect exact Go module license text for testing distributions (not legal review)."""
import json, pathlib, subprocess
root=pathlib.Path(__file__).resolve().parents[1]
raw=subprocess.check_output(['go','list','-m','-json','all'],cwd=root,text=True)
decoder=json.JSONDecoder();modules=[]
while raw.strip():
    item,end=decoder.raw_decode(raw.lstrip());modules.append(item);raw=raw.lstrip()[end:]
out=root/'dist/THIRD_PARTY_LICENSES';out.mkdir(parents=True,exist_ok=True)
index=[]
for m in modules:
    if m.get('Main'): continue
    directory=pathlib.Path(m.get('Dir','/nonexistent'))
    found=[]
    for p in directory.iterdir() if directory.is_dir() else []:
        if p.is_file() and (p.name.upper().startswith(('LICENSE','COPYING','NOTICE')) or p.name.lower()=='patents'):
            name=m['Path'].replace('/','_')+'@'+m['Version']+'__'+p.name
            (out/name).write_bytes(p.read_bytes());found.append(name)
    index.append({'module':m['Path'],'version':m['Version'],'files':found})
(out/'index.json').write_text(json.dumps(index,indent=2)+'\n')
print(f'Collected notices for {sum(bool(m["files"]) for m in index)} of {len(index)} modules; review missing entries before release')
