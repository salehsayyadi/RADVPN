#!/usr/bin/env python3
"""Reconstruct the checked-in, original brand icon without external downloads."""
import base64, hashlib, pathlib
root=pathlib.Path(__file__).resolve().parents[1]
data=base64.b64decode((root/'assets/RADVPN.ico.b64').read_text(),validate=False)
if data[:4]!=b'\x00\x00\x01\x00': raise SystemExit('Invalid icon asset')
(root/'assets/RADVPN.ico').write_bytes(data)
print('RADVPN icon generated:',hashlib.sha256(data).hexdigest())
parts=sorted((root/'apps/android/app/src/main/assets/lightning').glob('*.b64'))
(root/'assets/lightning.jpg').write_bytes(base64.b64decode(''.join(p.read_text() for p in parts)))

from PIL import Image, ImageEnhance
import io
artwork=Image.open(root/'assets/lightning.jpg').convert('RGB')
ImageEnhance.Brightness(artwork).enhance(1.9).save(root/'assets/lightning-glass.jpg',quality=94)
