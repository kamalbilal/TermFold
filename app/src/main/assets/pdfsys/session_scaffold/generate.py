#!/usr/bin/env python3
"""Path B generator stub — agent fills work/pack.html then runs this script."""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, "/usr/local/lib/pdfsys-pkgs")

SESSION = Path(__file__).resolve().parent.parent
WORK = SESSION / "work"
PACK = WORK / "pack.html"
OUT = SESSION / "out" / "output.pdf"
FIELDS = SESSION / "fields.json"


def main():
    if not PACK.is_file():
        raise SystemExit("Missing work/pack.html — build HTML/CSS from the upload first.")
    fields = {}
    if FIELDS.is_file():
        try:
            fields = json.loads(FIELDS.read_text() or "{}")
        except Exception:
            fields = {}
    try:
        from weasyprint import HTML
    except Exception as e:
        raise SystemExit("WeasyPrint is not installed: %s" % e)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    HTML(filename=str(PACK)).write_pdf(str(OUT), optimize_images=False)
    letter = OUT.parent / "letter.pdf"
    if OUT.name != "letter.pdf":
        letter.write_bytes(OUT.read_bytes())
    print(json.dumps({"ok": True, "output": str(OUT), "fields_keys": sorted(fields.keys())}))


if __name__ == "__main__":
    main()
