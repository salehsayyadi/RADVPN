#!/usr/bin/env python3
"""Fail CI for drift in locales or version metadata. No runtime profile output."""
import json, pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
en, fa = [json.loads((root / f'packages/localization/{lang}.json').read_text(encoding="utf-8")) for lang in ('en', 'fa')]
assert en.keys() == fa.keys(), 'Locale key mismatch'
assert all(str(x).strip() for locale in (en, fa) for x in locale.values())
for path, marker in [('apps/windows/RADVPN.csproj', '<Version>3.0.3</Version>'), ('apps/android/app/build.gradle.kts', 'versionName = "3.0.3"'), ('packages/engine/profiles.go', 'Version = "3.0.3"')]:
    assert marker in (root / path).read_text(encoding="utf-8"), f'Version drift: {path}'
# Both clients may only request keys that exist in both locale dictionaries.
for path in [*root.glob('apps/windows/**/*.cs'), *root.glob('apps/android/app/src/main/java/**/*.kt')]:
    if any(x in path.parts for x in ('bin','obj')): continue
    content = path.read_text(encoding="utf-8")
    for key in re.findall(r'\b(?:T|t|button|Btn|input|Input|report|Report)\("([a-z_]+)"', content):
        assert key in en, f'Unknown locale key {key} in {path}'
print(f'PASS version metadata and {len(en)} English/Persian keys')
