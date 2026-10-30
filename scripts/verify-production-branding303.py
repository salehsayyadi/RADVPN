#!/usr/bin/env python3
"""Validate the actual APK identity; a renamed sandbox file is NOT production."""
import re
import subprocess
import sys
from pathlib import Path


def verify_badging(text):
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", text, re.M)
    if not package or package[1] != 'app.radvpn' or package[3] != '3.0.3' or int(package[2]) < 30003:
        raise ValueError('production_package_or_version_required')
    labels = re.findall(r"^application-label(?:-[^:]+)?:'([^']*)'", text, re.M)
    if not labels or any(label != 'RADVPN' for label in labels):
        raise ValueError('production_app_label_required')
    if re.search(r'^application-debuggable', text, re.M):
        raise ValueError('non_debuggable_release_required')
    return dict(package=package[1], version_name=package[3], version_code=int(package[2]), label='RADVPN')


if __name__ == '__main__':
    import json
    if len(sys.argv) != 3:
        raise SystemExit('Usage: verify-production-branding303.py APK ANDROID_BUILD_TOOLS')
    output = subprocess.check_output([str(Path(sys.argv[2])/'aapt'), 'dump', 'badging', sys.argv[1]], text=True)
    try:
        print(json.dumps(verify_badging(output)))
    except ValueError as error:
        raise SystemExit(str(error)) from None
