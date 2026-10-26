#!/usr/bin/env python3
"""Generate exact app-owned uninstall paths; never recursively remove user files."""
import pathlib
root=pathlib.Path(__file__).resolve().parents[1];stage=root/'dist/windows';lines=[]
def quote(value): return value.replace('$','$$').replace('"','$\\"')
for p in sorted(stage.rglob('*')):
    if p.is_file():
        relative=str(p.relative_to(stage)).replace('/','\\')
        lines.append('Delete "$INSTDIR\\'+quote(relative)+'"')
for p in sorted([x for x in stage.rglob('*') if x.is_dir()],key=lambda x:len(x.parts),reverse=True):
    lines.append('RMDir "$INSTDIR\\'+quote(str(p.relative_to(stage)).replace('/','\\'))+'"')
(root/'dist/UninstallFiles.nsh').write_text('\n'.join(lines)+'\n')
print('Generated exact uninstall manifest:',len(lines),'entries')
