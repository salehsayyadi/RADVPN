#!/usr/bin/env python3
"""Expose credential-free compiler output in public Actions annotations."""
from pathlib import Path
import sys
p=Path(sys.argv[1]);lines=p.read_text(errors='replace').splitlines()[-55:] if p.exists() else ['Build log unavailable']
text='\n'.join(lines)[-18000:].replace('%','%25').replace('\r','%0D').replace('\n','%0A')
print('::error title=Standalone Android compiler failure::'+text,flush=True)
