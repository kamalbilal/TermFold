#!/usr/bin/env python3
"""Shared PDF page rasterization for QA and agent visual review."""
from __future__ import annotations

import subprocess
from pathlib import Path

sys_path_done = False


def render_pdf_pages(pdf: Path, out_dir: Path, dpi: int = 300) -> list[Path]:
    if not pdf.is_file():
        return []
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("page_*.png"):
        old.unlink(missing_ok=True)
    for old in out_dir.glob("page-*.png"):
        old.unlink(missing_ok=True)
    paths: list[Path] = []
    try:
        import fitz

        doc = fitz.open(str(pdf))
        for i in range(len(doc)):
            pix = doc[i].get_pixmap(dpi=dpi)
            p = out_dir / f"page_{i + 1:03d}.png"
            pix.save(str(p))
            paths.append(p)
        doc.close()
        return paths
    except Exception:
        pass
    pdftoppm = __import__("shutil").which("pdftoppm")
    if not pdftoppm:
        return []
    prefix = out_dir / "page"
    subprocess.run(
        [pdftoppm, "-png", "-r", str(dpi), str(pdf), str(prefix)],
        check=False,
        timeout=300,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    found = sorted(out_dir.glob("page*.png"))
    # poppler names page-1.png; normalize to page_001.png
    for i, src in enumerate(found):
        dest = out_dir / f"page_{i + 1:03d}.png"
        if src != dest:
            src.rename(dest)
        paths.append(dest)
    return paths
