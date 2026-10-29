#!/usr/bin/env python3
"""TermFold PDF Tools QA — docs/PDF-TOOLS-AGENT-SPEC.md §5."""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, "/usr/local/lib/pdfsys-pkgs")

DPI = 300
SSIM_MIN_DEFAULT = 0.93


def load_layout(work: Path) -> dict:
    path = work / "layout_spec.json"
    if not path.is_file():
        return {"ssim_min": SSIM_MIN_DEFAULT, "change_masks": []}
    try:
        data = json.loads(path.read_text() or "{}")
        if isinstance(data, dict):
            return data
    except Exception:
        pass
    return {"ssim_min": SSIM_MIN_DEFAULT, "change_masks": []}


def page_count(path: Path) -> int | None:
    try:
        import pypdf

        return len(pypdf.PdfReader(str(path)).pages)
    except Exception:
        try:
            import fitz

            return len(fitz.open(str(path)))
        except Exception:
            return None


def render_pages(pdf: Path, out_dir: Path) -> list[Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("page_*.png"):
        old.unlink(missing_ok=True)
    paths: list[Path] = []
    try:
        import fitz

        doc = fitz.open(str(pdf))
        for i in range(len(doc)):
            pix = doc[i].get_pixmap(dpi=DPI)
            p = out_dir / f"page_{i + 1:03d}.png"
            pix.save(str(p))
            paths.append(p)
        doc.close()
        return paths
    except Exception:
        pass
    pdftoppm = __import__("shutil").which("pdftoppm")
    if not pdftoppm or not pdf.is_file():
        return []
    prefix = out_dir / "page"
    subprocess.run(
        [pdftoppm, "-png", "-r", str(DPI), str(pdf), str(prefix)],
        check=False,
        timeout=300,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return sorted(out_dir.glob("page*.png"))


def ssim_pair(a: Path, b: Path) -> float | None:
    try:
        from skimage.metrics import structural_similarity
        import numpy as np
        from PIL import Image

        ia = np.asarray(Image.open(a).convert("L"))
        ib = np.asarray(Image.open(b).convert("L"))
        h = min(ia.shape[0], ib.shape[0])
        w = min(ia.shape[1], ib.shape[1])
        ia = ia[:h, :w]
        ib = ib[:h, :w]
        return float(structural_similarity(ia, ib))
    except Exception:
        return None


def qa_crops(work: Path, golden_page1: Path | None) -> list[dict]:
    crops_dir = work / "crops"
    results = []
    if not crops_dir.is_dir():
        return results
    for crop in sorted(crops_dir.glob("*.*")):
        if crop.suffix.lower() not in (".png", ".jpg", ".jpeg", ".webp"):
            continue
        entry = {"file": crop.name, "ok": True, "reasons": []}
        try:
            from PIL import Image
            import numpy as np

            img = np.asarray(Image.open(crop).convert("L"))
            mean = float(img.mean())
            var = float(img.var())
            if mean > 250 and var < 20:
                entry["ok"] = False
                entry["reasons"].append("empty")
            h, w = img.shape[:2]
            if w < 32 or h < 32:
                entry["ok"] = False
                entry["reasons"].append("too_small")
            dark = float((img < 128).mean())
            entry["ink_ratio"] = dark
            if "sig" in crop.name.lower():
                if dark < 0.005 or dark > 0.45:
                    entry["ok"] = False
                    entry["reasons"].append("ink_ratio")
            if golden_page1 and golden_page1.is_file() and "logo" in crop.name.lower():
                s = ssim_pair(crop, golden_page1)
                entry["ssim"] = s
                if s is not None and s < 0.85:
                    entry["ok"] = False
                    entry["reasons"].append("logo_ssim")
        except Exception as e:
            entry["ok"] = False
            entry["reasons"].append(str(e))
        qa_json = crop.with_suffix(".qa_crop.json")
        qa_json.write_text(json.dumps(entry, indent=2) + "\n")
        results.append(entry)
    return results


PACK_SCAFFOLD_MARKER = "Template not built yet"
PATH_A_OP_KINDS = frozenset({"replace_text", "apply_fields", "fill_form", "set_fields"})


def session_mode(session: Path) -> str:
    marker = session / "work" / "path_b"
    if marker.is_file():
        return "path_b"
    plan_path = session / "plan.json"
    if plan_path.is_file():
        try:
            plan = json.loads(plan_path.read_text() or "{}")
            ops = plan.get("ops") or []
            kinds = [str(o.get("kind") or o.get("strategy") or "").lower() for o in ops]
            if kinds and all(k in PATH_A_OP_KINDS for k in kinds):
                return "path_a"
        except Exception:
            pass
    pack = session / "work" / "pack.html"
    if pack.is_file():
        try:
            if PACK_SCAFFOLD_MARKER not in pack.read_text(errors="replace"):
                return "path_b"
        except Exception:
            pass
    if not (session / "input" / "source.pdf").is_file():
        return "path_a"
    return "path_b"


def run_session(session: Path) -> dict:
    source = session / "input" / "source.pdf"
    output = session / "out" / "output.pdf"
    if not output.is_file():
        output = session / "out" / "letter.pdf"
    work = session / "work"
    qa_dir = work / "qa"
    qa_dir.mkdir(parents=True, exist_ok=True)
    layout = load_layout(work)
    ssim_min = float(layout.get("ssim_min") or SSIM_MIN_DEFAULT)
    report = {
        "pass": False,
        "dpi": DPI,
        "ssim_min": ssim_min,
        "reasons": [],
        "pages": {},
        "mode": session_mode(session),
    }
    if not output.is_file():
        report["reasons"].append("missing_output")
        return report
    if source.is_file():
        sp = page_count(source)
        op = page_count(output)
        report["source_pages"] = sp
        report["output_pages"] = op
        if sp is not None and op is not None and sp != op:
            report["reasons"].append("page_count_mismatch")
            return report
    if report["mode"] == "path_a":
        report["pass"] = True
        report["note"] = "Path A: page count gate only (no SSIM vs source)"
        return report
    golden = work / "golden"
    candidate = work / "candidate"
    g_paths = render_pages(source, golden) if source.is_file() else []
    c_paths = render_pages(output, candidate)
    report["rendered_golden"] = len(g_paths)
    report["rendered_candidate"] = len(c_paths)
    page_ssim = []
    if g_paths and c_paths:
        n = min(len(g_paths), len(c_paths))
        for i in range(n):
            s = ssim_pair(g_paths[i], c_paths[i])
            if s is not None:
                page_ssim.append(s)
                report["pages"][f"p{i + 1}"] = {"ssim": round(s, 4)}
        if page_ssim:
            report["ssim_mean"] = round(sum(page_ssim) / len(page_ssim), 4)
            if report["ssim_mean"] < ssim_min:
                report["reasons"].append("ssim_below_threshold")
    elif source.is_file() and report["mode"] == "path_b":
        report["reasons"].append("render_failed")
    golden_p1 = g_paths[0] if g_paths else None
    crops = qa_crops(work, golden_p1)
    if any(not c.get("ok") for c in crops):
        report["reasons"].append("crop_qa_failed")
    if not report["reasons"]:
        report["pass"] = True
    return report


def main(argv=None):
    argv = argv or sys.argv[1:]
    session = Path(argv[0]) if argv else Path.cwd()
    report = run_session(session)
    out_path = session / "work" / "qa" / "report.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"ok": report["pass"], "report": str(out_path), "pass": report["pass"]}))
    return 0 if report["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
