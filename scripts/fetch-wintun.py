#!/usr/bin/env python3
"""Download only the vendor-pinned Wintun package; never execute profile files."""
import hashlib, io, pathlib, urllib.request, zipfile
URL = 'https://www.wintun.net/builds/wintun-0.14.1.zip'
SHA256 = '07c256185d6ee3652e09fa55c0b673e2624b565e02c4b9091c79ca7d2f24ef51'
def main():
    with urllib.request.urlopen(URL, timeout=30) as response:
        data = response.read(4 * 1024 * 1024 + 1)
    if len(data) > 4 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit('Vendor Wintun package checksum mismatch')
    output = pathlib.Path(__file__).resolve().parents[1] / 'dist/windows'
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for source, target in [('wintun/bin/amd64/wintun.dll', 'wintun.dll'), ('wintun/LICENSE.txt', 'Wintun-LICENSE.txt')]:
            (output / target).write_bytes(archive.read(source))
if __name__ == '__main__':
    main()
