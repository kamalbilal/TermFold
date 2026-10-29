#!/usr/bin/env python3
"""TermFold pdfsys: inspect, toolkit, generate, verify. Never execute PDF JS."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import textwrap
from pathlib import Path

sys.path.insert(0, "/usr/local/lib/pdfsys-pkgs")


def load_pypdf():
    try:
        import pypdf as mod  # noqa: F401
        return mod
    except Exception:
        import PyPDF2 as mod  # noqa: F401
        return mod


def out(data, path=None):
    text = json.dumps(data, indent=2, sort_keys=True)
    if path:
        Path(path).write_text(text)
    print(text)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def probe():
    mods = {}
    for name in ("pypdf", "reportlab", "pikepdf", "pdfplumber", "fitz", "weasyprint", "skimage", "PIL", "diffpdf"):
        try:
            if name == "fitz":
                __import__("fitz")
            elif name == "skimage":
                __import__("skimage.metrics")
            else:
                __import__(name)
            mods[name] = True
        except Exception:
            mods[name] = False
    return {"ok": True, "modules": mods, "python": sys.version.split()[0], "pipeline": "pdf-tools-agent-spec-v1"}


def require_pypdf():
    try:
        load_pypdf()
        return True
    except Exception:
        return False


def inspect_pdf(path: Path, password: str | None, dest: Path | None):
    if not require_pypdf():
        return out({"ok": False, "error": "pypdf is not installed", "code": "MISSING_DEPS"}, dest)
    PdfReader = load_pypdf().PdfReader

    try:
        reader = PdfReader(str(path), strict=False)
    except Exception as e:
        return out({"ok": False, "error": str(e), "code": "READ_ERROR"})
    encrypted = bool(getattr(reader, "is_encrypted", False))
    if encrypted:
        if not password:
            return out({"ok": False, "error": "encrypted", "code": "ENCRYPTED", "encrypted": True})
        try:
            reader.decrypt(password)
        except Exception:
            return out({"ok": False, "error": "wrong password", "code": "ENCRYPTED", "encrypted": True})
    root = reader.trailer.get("/Root") if reader.trailer else None
    has_js = False
    signed = False
    acro = False
    try:
        if root:
            acro = "/AcroForm" in root
            if "/Names" in root or "/OpenAction" in root or "/AA" in root:
                has_js = True
            # Detect without executing
            raw = path.read_bytes()
            if b"/JavaScript" in raw or b"/JS" in raw or b"/Launch" in raw or b"/OpenAction" in raw:
                has_js = True
            if b"/Sig" in raw:
                signed = True
    except Exception:
        pass
    pages = []
    for i, page in enumerate(reader.pages):
        text = ""
        try:
            text = page.extract_text() or ""
        except Exception:
            text = ""
        kind = "native" if len(text.strip()) >= 20 else "scanned_or_empty"
        pages.append({"index": i, "kind": kind, "chars": len(text)})
    fonts = []
    try:
        if reader.pages:
            fonts = sorted({str(k) for k in (reader.pages[0].get("/Resources") or {}).get("/Font", {})})
    except Exception:
        fonts = []
    report = {
        "ok": True,
        "sha256": sha256(path),
        "pages": len(reader.pages),
        "encrypted": encrypted,
        "signed": signed,
        "acroform": acro,
        "untrusted_actions_present": has_js,
        "actions_executed": False,
        "fonts": fonts,
        "page_kinds": pages,
        "signature_note": (
            "This file is signed. Modifying it would invalidate the signatures."
            if signed
            else None
        ),
    }
    dest_path = dest
    return out(report, str(dest_path) if dest_path else None)


NAME_KEYS = ("employee_name", "name", "full_name", "recipient")
NAME_LINE = re.compile(r"(?im)^(\s*(?:employee\s*)?name\s*[:\-]\s*)(.*)$")


def ocr_pdf(src: Path, work: Path) -> list[str]:
    pdftoppm = shutil.which("pdftoppm")
    tesseract = shutil.which("tesseract")
    if not pdftoppm or not tesseract or not src.is_file():
        return []
    img_dir = work / "ocr"
    if img_dir.exists():
        shutil.rmtree(img_dir)
    img_dir.mkdir(parents=True, exist_ok=True)
    prefix = img_dir / "page"
    try:
        subprocess.run(
            [pdftoppm, "-png", "-r", "200", str(src), str(prefix)],
            check=False,
            timeout=180,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    except Exception:
        return []
    pages = []
    for png in sorted(img_dir.glob("page*.png")):
        try:
            r = subprocess.run(
                [tesseract, str(png), "stdout", "-l", "eng"],
                capture_output=True,
                timeout=90,
            )
            pages.append((r.stdout or b"").decode("utf-8", "replace"))
        except Exception:
            pages.append("")
    return pages


def apply_text_replacements(pages: list[str], fields: dict, ops: list) -> list[str]:
    pairs = []
    new_name = ""
    for op in ops or []:
        kind = str(op.get("kind") or op.get("strategy") or "").lower()
        if kind != "replace_text":
            continue
        find = str(op.get("find") or op.get("old") or "")
        repl = str(op.get("replace") or op.get("new") or "")
        if find:
            pairs.append((find, repl))
        elif repl:
            new_name = repl
    for key, value in (fields or {}).items():
        pairs.append(("{{%s}}" % key, str(value)))
    if not new_name:
        for key in NAME_KEYS:
            if str((fields or {}).get(key) or "").strip():
                new_name = str(fields[key]).strip()
                break
    out_pages = []
    for raw in pages:
        text = raw
        for old, new in pairs:
            if old:
                text = text.replace(old, new)
        if new_name:
            def _name_sub(m):
                return m.group(1) + new_name
            text, n = NAME_LINE.subn(_name_sub, text, count=1)
            if n == 0 and new_name not in text:
                for old, new in pairs:
                    if old and old in text:
                        break
                else:
                    # Do not prepend a "Name:" banner — that paints a fake field on top of the letter.
                    pass
        out_pages.append(text)
    return out_pages


def write_recreated_pdf(pages: list[str], fonts_dir: Path, dest: Path):
    try:
        from reportlab.pdfgen import canvas
        from reportlab.lib.pagesizes import letter
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont
    except Exception as e:
        raise SystemExit("reportlab is not installed: %s" % e)
    regular = fonts_dir / "LiberationSans-Regular.ttf"
    if regular.is_file():
        pdfmetrics.registerFont(TTFont("LetterSans", str(regular)))
        font = "LetterSans"
    else:
        font = "Helvetica"
    dest.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(dest), pagesize=letter)
    w, h = letter
    for page_text in pages or [""]:
        y = h - 54
        c.setFont(font, 11)
        for line in (page_text or "").splitlines() or [""]:
            wrapped = textwrap.wrap(line, 88) or [""]
            for part in wrapped:
                if y < 54:
                    c.setFont(font, 8)
                    c.drawString(72, 36, "Not an original issued document.")
                    c.showPage()
                    y = h - 54
                    c.setFont(font, 11)
                c.drawString(54, y, part[:120])
                y -= 14
        c.setFont(font, 8)
        c.drawString(72, 36, "Not an original issued document.")
        c.showPage()
    c.save()


def recreate_from_source(src: Path, fields: dict, ops: list, fonts_dir: Path, dest: Path, work: Path) -> dict | None:
    pages = ocr_pdf(src, work)
    if not any((p or "").strip() for p in pages):
        return None
    updated = apply_text_replacements(pages, fields, ops)
    write_recreated_pdf(updated, fonts_dir, dest)
    letter = dest.with_name("letter.pdf")
    if dest.name != "letter.pdf" and dest.is_file():
        shutil.copy2(dest, letter)
    return {
        "ok": True,
        "output": str(dest),
        "method": "recreate",
        "pages": len(updated),
        "output_sha256": sha256(dest),
    }


OVERLAY_KINDS = {
    "overlay",
    "cover",
    "whiteout",
    "redact_overlay",
    "background_overlay",
    "strategy_d",
}


def form_field_names(reader) -> list[str]:
    try:
        fields = reader.get_fields() or {}
        return sorted(str(k) for k in fields.keys())
    except Exception:
        return []


def page_elements(page, page_id: str, limit: int = 40) -> tuple[str, list[dict], int]:
    text = ""
    try:
        text = page.extract_text() or ""
    except Exception:
        text = ""
    kind = "native" if len(text.strip()) >= 20 else "scanned_or_empty"
    elements = []
    for i, line in enumerate(text.splitlines()):
        if len(elements) >= limit:
            break
        stripped = line.strip()
        if not stripped:
            continue
        elements.append(
            {
                "id": f"{page_id}.e{len(elements)}",
                "chars": len(stripped),
                "preview": stripped[:80],
            }
        )
    return kind, elements, len(text)


def model_pdf(src: Path, session_dir: Path):
    if not require_pypdf():
        return out({"ok": False, "error": "pypdf is not installed", "code": "MISSING_DEPS"})
    PdfReader = load_pypdf().PdfReader
    work = session_dir / "work"
    work.mkdir(parents=True, exist_ok=True)
    (session_dir / "renders").mkdir(parents=True, exist_ok=True)
    try:
        reader = PdfReader(str(src), strict=False)
    except Exception as e:
        return out({"ok": False, "error": str(e), "code": "READ_ERROR"})
    pages = []
    summary_pages = []
    native = 0
    ocr_pages = []
    for i, page in enumerate(reader.pages):
        page_id = f"p{i + 1}"
        kind, elements, chars = page_elements(page, page_id)
        if kind == "native":
            native += 1
        pages.append(
            {
                "id": page_id,
                "index": i,
                "kind": kind,
                "chars": chars,
                "elements": elements,
            }
        )
    if native < len(pages):
        ocr_pages = ocr_pdf(src, work)
        (work / "ocr.json").write_text(json.dumps({"pages": [{"chars": len(p)} for p in ocr_pages]}))
        for i, page in enumerate(pages):
            if page["kind"] == "native":
                continue
            raw = ocr_pages[i] if i < len(ocr_pages) else ""
            if not raw.strip():
                continue
            page_id = page["id"]
            elements = []
            for line in raw.splitlines():
                stripped = line.strip()
                if not stripped:
                    continue
                if len(elements) >= 40:
                    break
                elements.append(
                    {
                        "id": f"{page_id}.e{len(elements)}",
                        "chars": len(stripped),
                        "preview": stripped[:80],
                    }
                )
            page["kind"] = "ocr"
            page["chars"] = len(raw)
            page["elements"] = elements
    for page in pages:
        summary_pages.append(
            {
                "id": page["id"],
                "index": page["index"],
                "kind": page["kind"],
                "chars": page["chars"],
                "element_ids": [e["id"] for e in page["elements"]],
            }
        )
    readable = any(p["kind"] in ("native", "ocr") and p["chars"] >= 20 for p in pages)
    all_native = all(p["kind"] == "native" and p["chars"] >= 20 for p in pages)
    recommended = "A" if all_native and readable else "B"
    fields = form_field_names(reader)
    fields_path = session_dir / "fields.json"
    existing: dict = {}
    if fields_path.is_file():
        try:
            loaded = json.loads(fields_path.read_text() or "{}")
            if isinstance(loaded, dict):
                existing = loaded
        except Exception:
            existing = {}
    for name in fields:
        existing.setdefault(name, "")
    fields_path.write_text(json.dumps(existing, indent=2, sort_keys=True))
    model = {
        "ok": True,
        "sha256": sha256(src),
        "pages": pages,
        "fields": fields,
        "acroform": bool(fields),
        "renders": [],
        "renders_skipped": True,
        "note": None,
    }
    model_path = work / "model.json"
    model_path.write_text(json.dumps(model, indent=2, sort_keys=True))
    return out(
        {
            "ok": True,
            "action": "pdf_tools_model",
            "model": str(model_path),
            "sha256": model["sha256"],
            "pages": summary_pages,
            "fields": fields,
            "acroform": bool(fields),
            "can_replace_text": all_native and readable,
            "recommended_path": recommended,
            "default_path": "B",
            "renders_skipped": True,
        }
    )


def replace_in_page(page, old: str, new: str) -> bool:
    if not old:
        return False
    contents = page.get_contents()
    if contents is None:
        return False
    streams = contents if isinstance(contents, list) else [contents]
    changed = False
    variants = [
        (old.encode("latin-1", "replace"), new.encode("latin-1", "replace")),
        (old.encode("utf-8", "replace"), new.encode("utf-8", "replace")),
        (b"\xfe\xff" + old.encode("utf-16-be"), b"\xfe\xff" + new.encode("utf-16-be")),
    ]
    for st in streams:
        try:
            data = st.get_data()
        except Exception:
            continue
        orig = data
        for old_b, new_b in variants:
            if old_b and old_b in data:
                data = data.replace(old_b, new_b)
        if data != orig:
            try:
                st.set_data(data)
                changed = True
            except Exception:
                pass
    return changed


def collect_text_runs(page) -> list[dict]:
    runs = []

    def visitor(text, cm, tm, font_dict, font_size):
        raw = text or ""
        if not raw.strip():
            return
        try:
            x = float(tm[4])
            y = float(tm[5])
        except Exception:
            return
        size = float(font_size or 12)
        runs.append({"text": raw, "x": x, "y": y, "size": size})

    try:
        page.extract_text(visitor_text=visitor)
    except TypeError:
        try:
            page.extract_text()
        except Exception:
            pass
    except Exception:
        pass
    return runs


def find_run_for_text(runs: list[dict], needle: str) -> dict | None:
    if not needle or not runs:
        return None
    needle = needle.strip()
    for run in runs:
        if needle in (run.get("text") or ""):
            return run
    by_y: dict[float, list] = {}
    for run in runs:
        by_y.setdefault(round(float(run["y"]), 1), []).append(run)
    for _y, group in by_y.items():
        group = sorted(group, key=lambda r: r["x"])
        joined = "".join(r["text"] for r in group)
        if needle not in joined:
            continue
        prefix = joined.split(needle, 1)[0]
        # Walk to the run that contains the start of the needle.
        seen = 0
        hit = group[0]
        for run in group:
            nxt = seen + len(run["text"])
            if seen <= len(prefix) < nxt:
                hit = run
                break
            seen = nxt
        width = max(hit["size"] * 0.52 * len(needle), 8.0)
        return {
            "text": needle,
            "x": hit["x"],
            "y": hit["y"],
            "size": hit["size"],
            "width": width,
        }
    return None


def stamp_replacement(page, find: str, new: str, fonts_dir: Path) -> bool:
    """Paint new words on the same baseline as the old words. No form-field widget."""
    if not find or not new:
        return False
    try:
        extracted = page.extract_text() or ""
    except Exception:
        extracted = ""
    if find not in extracted:
        return False
    run = find_run_for_text(collect_text_runs(page), find)
    if run is None:
        return False
    try:
        from io import BytesIO
        from reportlab.pdfgen import canvas as rl_canvas
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont
    except Exception:
        return False
    x = float(run["x"])
    y = float(run["y"])
    size = max(8.0, float(run["size"] or 12))
    width = float(run.get("width") or size * 0.52 * max(len(find), len(new)))
    width = max(width, size * 0.52 * len(new), size * 0.52 * len(find))
    box_h = size * 1.25
    w = float(page.mediabox.width)
    h = float(page.mediabox.height)
    bold = fonts_dir / "LiberationSans-Bold.ttf"
    regular = fonts_dir / "LiberationSans-Regular.ttf"
    font_name = "Helvetica-Bold" if size >= 13 else "Helvetica"
    try:
        if size >= 13 and bold.is_file():
            pdfmetrics.registerFont(TTFont("TfBold", str(bold)))
            font_name = "TfBold"
        elif regular.is_file():
            pdfmetrics.registerFont(TTFont("TfReg", str(regular)))
            font_name = "TfReg"
    except Exception:
        pass
    buf = BytesIO()
    c = rl_canvas.Canvas(buf, pagesize=(w, h))
    pad = max(0.6, size * 0.08)
    c.setFillColorRGB(1, 1, 1)
    c.rect(x - pad, y - pad * 0.6, width + pad * 2, box_h, fill=1, stroke=0)
    c.setFillColorRGB(0.08, 0.08, 0.08)
    c.setFont(font_name, size)
    c.drawString(x, y, new)
    c.save()
    buf.seek(0)
    try:
        stamp = load_pypdf().PdfReader(buf)
        page.merge_page(stamp.pages[0])
        return True
    except Exception:
        return False


def fill_form(writer, fields: dict, allowed: set[str] | None = None):
    if not fields:
        return False
    usable = {}
    for key, value in fields.items():
        if allowed is not None and key not in allowed:
            continue
        if str(value).strip():
            usable[key] = str(value)
    if not usable:
        return False
    try:
        for page in writer.pages:
            writer.update_page_form_field_values(page, usable)
        return True
    except Exception:
        try:
            writer.update_page_form_field_values(writer.pages[0], usable)
            return True
        except Exception:
            return False


def apply_plan(src: Path | None, fields_path: Path, plan_path: Path, fonts_dir: Path, dest: Path):
    if fields_path.is_file():
        fields = json.loads(fields_path.read_text() or "{}")
        if not isinstance(fields, dict):
            fields = {}
    else:
        fields = {}
        fields_path.parent.mkdir(parents=True, exist_ok=True)
        fields_path.write_text("{}")
    plan = {}
    if plan_path.is_file():
        plan = json.loads(plan_path.read_text() or "{}")
    ops = plan.get("ops") or []
    for op in ops:
        kind = str(op.get("kind") or op.get("strategy") or "").lower()
        if kind in OVERLAY_KINDS or op.get("overlay"):
            raise SystemExit("Overlay cover-up of existing text is forbidden")
    dest.parent.mkdir(parents=True, exist_ok=True)
    has_source = bool(src and src.is_file())
    source_edit_kinds = {"replace_text", "fill_form", "apply_fields", "set_fields"}
    op_kinds = [str(op.get("kind") or op.get("strategy") or "").lower() for op in ops]
    wants_source_edit = has_source and (
        not op_kinds or any(k in source_edit_kinds for k in op_kinds) or "generate" not in op_kinds
    )
    if has_source and "generate" in op_kinds and not any(k in source_edit_kinds for k in op_kinds):
        wants_source_edit = True
    if not wants_source_edit:
        generate(src or Path("."), fields_path, fonts_dir, dest)
        letter = dest.with_name("letter.pdf")
        if dest.name != "letter.pdf" and dest.is_file():
            shutil.copy2(dest, letter)
        return

    _pdf = load_pypdf()
    PdfReader, PdfWriter = _pdf.PdfReader, _pdf.PdfWriter
    before = sha256(src)
    reader = PdfReader(str(src), strict=False)
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    kinds = []
    for i, page in enumerate(reader.pages):
        text = ""
        try:
            text = page.extract_text() or ""
        except Exception:
            text = ""
        kinds.append("native" if len(text.strip()) >= 20 else "scanned_or_empty")
    scanned = [i + 1 for i, k in enumerate(kinds) if k != "native"]
    applied = []
    errors = []
    acro = set(form_field_names(reader))
    if not ops:
        ops = [{"kind": "replace_text"}] if any(str(v).strip() for v in fields.values()) else []
    for op in ops:
        kind = str(op.get("kind") or op.get("strategy") or "").lower()
        if kind in ("generate", "fill_template", "letter"):
            continue
        if kind in ("fill_form", "set_fields"):
            mapping = dict(fields)
            extra = op.get("fields") or op.get("values") or {}
            if isinstance(extra, dict):
                mapping.update({str(k): str(v) for k, v in extra.items()})
            filled = fill_form(writer, mapping, allowed=acro)
            token_hits = 0
            for key, value in mapping.items():
                token = "{{%s}}" % key
                for page in writer.pages:
                    if replace_in_page(page, token, str(value)):
                        token_hits += 1
            if filled or token_hits:
                applied.append({"kind": kind, "form": filled, "tokens": token_hits, "keys": sorted(mapping.keys())})
            else:
                errors.append({"code": "NO_MATCH", "keys": sorted(mapping.keys())})
            continue
        if kind in ("apply_fields", "replace_text"):
            find = str(op.get("find") or op.get("old") or "")
            repl = str(op.get("replace") or op.get("new") or "")
            if not find or not repl:
                for key in NAME_KEYS:
                    val = str(fields.get(key) or "").strip()
                    if val:
                        repl = repl or val
                        break
            if not find and repl:
                # Infer the old visible name from the page instead of painting a new widget.
                for page in writer.pages:
                    try:
                        text = page.extract_text() or ""
                    except Exception:
                        text = ""
                    m = NAME_LINE.search(text)
                    if m and m.group(2).strip() and m.group(2).strip() != repl:
                        find = m.group(2).strip()
                        break
            if not find or not repl:
                if kind == "apply_fields":
                    token_hits = 0
                    for key, value in fields.items():
                        token = "{{%s}}" % key
                        for page in writer.pages:
                            if replace_in_page(page, token, str(value)):
                                token_hits += 1
                    if token_hits:
                        applied.append({"kind": kind, "tokens": token_hits})
                    else:
                        errors.append({"code": "NO_MATCH", "hint": "Need find+replace of the words on the page"})
                continue
            page_no = op.get("page") or op.get("page_index")
            targets = list(range(len(writer.pages)))
            if page_no is not None:
                idx = int(page_no)
                if idx >= 1:
                    idx -= 1
                targets = [idx]
            n = 0
            stamped = 0
            for i in targets:
                if not (0 <= i < len(writer.pages)):
                    continue
                page = writer.pages[i]
                if replace_in_page(page, find, repl):
                    n += 1
                    continue
                if stamp_replacement(page, find, repl, fonts_dir):
                    stamped += 1
            if n or stamped:
                applied.append({"kind": "replace_text", "pages": n, "stamped": stamped, "find_chars": len(find)})
            else:
                errors.append({"code": "TEXT_NOT_FOUND", "find_chars": len(find), "pages": [i + 1 for i in targets]})
    if errors and not applied:
        if any(e.get("code") == "TEXT_NOT_FOUND" for e in errors) and any(k == "native" for k in kinds):
            return out(
                {
                    "ok": False,
                    "code": "TEXT_NOT_FOUND",
                    "error": "Could not change those words in the original layout. The find text must match the page exactly.",
                    "errors": errors,
                }
            )
        if dest.is_file():
            dest.unlink()
        code = "NEED_RECREATE"
        hint = (
            "Use Path B: layout-faithful recreate (work/pack.html + WeasyPrint generate.py), not plain text rebuild or paint-over."
        )
        if scanned:
            hint += " Scanned or mixed pages require full recreate."
        return out(
            {
                "ok": False,
                "code": code,
                "error": hint,
                "errors": errors,
                "scanned_pages": scanned,
            }
        )
    with dest.open("wb") as f:
        writer.write(f)
    letter = dest.with_name("letter.pdf")
    if dest.name != "letter.pdf":
        shutil.copy2(dest, letter)
    if sha256(src) != before:
        raise SystemExit("input mutated")
    return out(
        {
            "ok": True,
            "output": str(dest),
            "input_sha256": before,
            "output_sha256": sha256(dest),
            "applied": applied,
            "errors": errors,
            "pages": len(writer.pages),
        }
    )


def copy_only(src: Path, dest: Path):
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)
    return dest


def toolkit_merge(inputs: list[Path], dest: Path):
    if not require_pypdf():
        raise SystemExit("pypdf is not installed")
    _pdf = load_pypdf()
    PdfReader, PdfWriter = _pdf.PdfReader, _pdf.PdfWriter

    w = PdfWriter()
    originals = [sha256(p) for p in inputs]
    for p in inputs:
        r = PdfReader(str(p))
        if r.is_encrypted:
            raise SystemExit("encrypted")
        for page in r.pages:
            w.add_page(page)
    dest.parent.mkdir(parents=True, exist_ok=True)
    with dest.open("wb") as f:
        w.write(f)
    after = [sha256(p) for p in inputs]
    if after != originals:
        raise SystemExit("input mutated")
    out({"ok": True, "output": str(dest), "input_sha256": after})


def toolkit_split(src: Path, dest: Path, start: int, end: int):
    _pdf = load_pypdf()
    PdfReader, PdfWriter = _pdf.PdfReader, _pdf.PdfWriter

    before = sha256(src)
    r = PdfReader(str(src))
    w = PdfWriter()
    n = len(r.pages)
    for i in range(max(0, start - 1), min(n, end)):
        w.add_page(r.pages[i])
    dest.parent.mkdir(parents=True, exist_ok=True)
    with dest.open("wb") as f:
        w.write(f)
    if sha256(src) != before:
        raise SystemExit("input mutated")
    out({"ok": True, "output": str(dest)})


def toolkit_rotate(src: Path, dest: Path, degrees: int):
    _pdf = load_pypdf()
    PdfReader, PdfWriter = _pdf.PdfReader, _pdf.PdfWriter

    before = sha256(src)
    r = PdfReader(str(src))
    w = PdfWriter()
    for page in r.pages:
        page.rotate(degrees)
        w.add_page(page)
    dest.parent.mkdir(parents=True, exist_ok=True)
    with dest.open("wb") as f:
        w.write(f)
    if sha256(src) != before:
        raise SystemExit("input mutated")
    out({"ok": True, "output": str(dest)})


def extract_text(src: Path, dest: Path):
    PdfReader = load_pypdf().PdfReader

    before = sha256(src)
    r = PdfReader(str(src))
    parts = []
    for p in r.pages:
        parts.append(p.extract_text() or "")
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text("\n".join(parts))
    if sha256(src) != before:
        raise SystemExit("input mutated")
    out({"ok": True, "output": str(dest)})


def extract_images(src: Path, dest_dir: Path):
    PdfReader = load_pypdf().PdfReader

    before = sha256(src)
    r = PdfReader(str(src))
    dest_dir.mkdir(parents=True, exist_ok=True)
    n = 0
    for pi, page in enumerate(r.pages):
        try:
            images = page.images
        except Exception:
            images = []
        for ii, img in enumerate(images):
            name = dest_dir / f"p{pi}_{ii}_{img.name}"
            name.write_bytes(img.data)
            n += 1
    if sha256(src) != before:
        raise SystemExit("input mutated")
    out({"ok": True, "count": n, "output": str(dest_dir)})


def images_to_pdf(images: list[Path], dest: Path):
    try:
        from reportlab.pdfgen import canvas
        from reportlab.lib.pagesizes import letter
        from reportlab.lib.utils import ImageReader
    except Exception as e:
        raise SystemExit("reportlab is not installed: %s" % e)
    dest.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(dest), pagesize=letter)
    w, h = letter
    for img in images:
        c.drawImage(ImageReader(str(img)), 36, 36, width=w - 72, height=h - 72, preserveAspectRatio=True, anchor="c")
        c.showPage()
    c.save()
    out({"ok": True, "output": str(dest)})


def generate(template: Path, fields_path: Path, fonts_dir: Path, dest: Path):
    try:
        from reportlab.pdfgen import canvas
        from reportlab.lib.pagesizes import letter
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont
    except Exception as e:
        raise SystemExit("reportlab is not installed: %s" % e)
    fields = json.loads(fields_path.read_text())
    regular = fonts_dir / "LiberationSans-Regular.ttf"
    bold = fonts_dir / "LiberationSans-Bold.ttf"
    if regular.is_file():
        pdfmetrics.registerFont(TTFont("LetterSans", str(regular)))
        font = "LetterSans"
    else:
        font = "Helvetica"
    if bold.is_file():
        pdfmetrics.registerFont(TTFont("LetterSans-Bold", str(bold)))
        font_b = "LetterSans-Bold"
    else:
        font_b = "Helvetica-Bold"
    dest.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(dest), pagesize=letter)
    w, h = letter
    y = h - 72
    c.setFont(font_b, 16)
    c.drawString(72, y, fields.get("subject", "Letter"))
    y -= 36
    c.setFont(font, 11)
    c.drawString(72, y, "To: " + fields.get("recipient", ""))
    y -= 24
    c.drawString(72, y, fields.get("body", ""))
    y -= 48
    c.drawString(72, y, "From: " + fields.get("sender", ""))
    c.setFont(font, 8)
    c.drawString(72, 36, "Not an original issued document.")
    c.save()
    out({"ok": True, "output": str(dest), "fields_used": sorted(fields.keys()), "font": font})


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


def verify_path_a(session_dir: Path | None, orig: Path | None, generated: Path) -> tuple[bool, list[str]]:
    reasons: list[str] = []
    if not generated.is_file():
        return False, ["missing_output"]
    try:
        out_pages = len(load_pypdf().PdfReader(str(generated)).pages)
    except Exception:
        return False, ["output_unreadable"]
    if orig and orig.is_file():
        try:
            in_pages = len(load_pypdf().PdfReader(str(orig)).pages)
            if in_pages != out_pages:
                reasons.append("page_count_mismatch")
        except Exception:
            pass
    if session_dir:
        plan_path = session_dir / "plan.json"
        if plan_path.is_file():
            try:
                plan = json.loads(plan_path.read_text() or "{}")
                text = "\n".join(
                    (p.extract_text() or "")
                    for p in load_pypdf().PdfReader(str(generated)).pages
                )
                for op in plan.get("ops") or []:
                    kind = str(op.get("kind") or "").lower()
                    if kind != "replace_text":
                        continue
                    repl = str(op.get("replace") or op.get("new") or "")
                    if repl and repl not in text:
                        reasons.append("replace_not_in_output")
            except Exception:
                pass
    return (len(reasons) == 0, reasons)


DELIVERABLE_REVIEW_DPI = 150


def _load_render_module():
    import importlib.util

    for rp in (
        Path("/usr/local/lib/pdfsys/render_pages.py"),
        Path(__file__).resolve().parent / "render_pages.py",
    ):
        if not rp.is_file():
            continue
        spec = importlib.util.spec_from_file_location("tf_render", str(rp))
        if spec and spec.loader:
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            return mod
    return None


def render_deliverable(session_dir: Path, which: str = "output", dpi: int = DELIVERABLE_REVIEW_DPI):
    session_dir = Path(session_dir)
    if which == "source":
        pdf = session_dir / "input" / "source.pdf"
    else:
        pdf = session_dir / "out" / "output.pdf"
        if not pdf.is_file():
            pdf = session_dir / "out" / "letter.pdf"
    if not pdf.is_file():
        return out({"ok": False, "error": "PDF missing", "code": "MISSING_PDF"})
    mod = _load_render_module()
    if mod is None:
        return out({"ok": False, "error": "render_pages.py missing", "code": "MISSING_RENDER"})
    dest = session_dir / "work" / "renders" / "deliverable"
    paths = mod.render_pdf_pages(pdf, dest, dpi=dpi)
    if not paths:
        return out({"ok": False, "error": "Render failed", "code": "RENDER_FAILED"})
    rel = [str(p.relative_to(session_dir)) for p in paths]
    manifest = session_dir / "work" / "renders" / "manifest.json"
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(
        json.dumps(
            {"ok": True, "dpi": dpi, "pdf": str(pdf), "pages": rel, "page_count": len(paths)},
            indent=2,
            sort_keys=True,
        )
        + "\n",
    )
    # New render invalidates prior visual sign-off.
    review = session_dir / "work" / "visual_review.json"
    if review.is_file():
        review.unlink()
    return out(
        {
            "ok": True,
            "pages": len(paths),
            "dpi": dpi,
            "manifest": str(manifest),
            "images": rel,
            "hint": "Open each PNG under work/renders/deliverable/ and review layout before pdf_visual_confirm.",
        }
    )


def visual_review_status(session_dir: Path) -> tuple[bool, list[str]]:
    session_dir = Path(session_dir)
    manifest = session_dir / "work" / "renders" / "manifest.json"
    review = session_dir / "work" / "visual_review.json"
    reasons: list[str] = []
    if not manifest.is_file():
        reasons.append("render_manifest_missing")
        return False, reasons
    if not review.is_file():
        reasons.append("visual_review_missing")
        return False, reasons
    try:
        rdata = json.loads(review.read_text() or "{}")
        mdata = json.loads(manifest.read_text() or "{}")
    except Exception:
        reasons.append("visual_review_invalid")
        return False, reasons
    if not rdata.get("ok"):
        reasons.append("visual_review_not_ok")
        return False, reasons
    expected = int(mdata.get("page_count") or 0)
    reviewed = int(rdata.get("pages_reviewed") or 0)
    if expected and reviewed < expected:
        reasons.append("visual_review_incomplete")
        return False, reasons
    out_sha = sha256(session_dir / "out" / "output.pdf") if (session_dir / "out" / "output.pdf").is_file() else None
    if out_sha and rdata.get("output_sha256") and rdata.get("output_sha256") != out_sha:
        reasons.append("visual_review_stale_output")
        return False, reasons
    return True, []


def run_qa_session(session_dir: Path) -> dict:
    script = session_dir / "work" / "qa_loop.py"
    if not script.is_file():
        bundled = Path("/usr/local/lib/pdfsys/qa_loop.py")
        if bundled.is_file():
            script = bundled
        else:
            return {"ok": False, "pass": False, "error": "qa_loop.py missing", "code": "MISSING_QA"}
    proc = subprocess.run(
        [sys.executable, str(script), str(session_dir)],
        capture_output=True,
        text=True,
        timeout=600,
    )
    report_path = session_dir / "work" / "qa" / "report.json"
    report = {}
    if report_path.is_file():
        try:
            report = json.loads(report_path.read_text() or "{}")
        except Exception:
            report = {}
    passed = bool(report.get("pass"))
    payload = {
        "ok": passed and proc.returncode == 0,
        "pass": passed,
        "report": str(report_path),
        "qa_exit": proc.returncode,
        "reasons": report.get("reasons") or [],
    }
    if proc.stdout.strip():
        try:
            payload["stdout"] = json.loads(proc.stdout.strip().splitlines()[-1])
        except Exception:
            payload["stdout_raw"] = proc.stdout.strip()[:400]
    if not payload["ok"]:
        payload["error"] = "QA failed: " + ", ".join(payload["reasons"]) if payload["reasons"] else "QA failed"
    return payload


def verify(orig: Path | None, generated: Path, report_path: Path, session_dir: Path | None = None):
    PdfReader = load_pypdf().PdfReader

    gen_hash = sha256(generated)
    orig_hash = sha256(orig) if orig and orig.is_file() else None
    r = PdfReader(str(generated))
    qa_payload = None
    qa_pass = True
    mode = session_mode(session_dir) if session_dir else "path_a"
    if session_dir and session_dir.is_dir() and mode == "path_b":
        qa_payload = run_qa_session(session_dir)
        qa_pass = bool(qa_payload.get("pass"))
    elif session_dir and session_dir.is_dir():
        qa_pass, reasons = verify_path_a(session_dir, orig, generated)
        qa_payload = {"pass": qa_pass, "mode": "path_a", "reasons": reasons}
    elif orig and orig.is_file():
        tmp = generated.parent.parent
        if (tmp / "input" / "source.pdf").is_file():
            sub_mode = session_mode(tmp)
            if sub_mode == "path_b":
                qa_payload = run_qa_session(tmp)
                qa_pass = bool(qa_payload.get("pass"))
            else:
                qa_pass, reasons = verify_path_a(tmp, orig, generated)
                qa_payload = {"pass": qa_pass, "mode": "path_a", "reasons": reasons}
    text = "\n".join((p.extract_text() or "") for p in r.pages)
    ok = qa_pass
    visual_payload = None
    if session_dir and session_dir.is_dir():
        v_ok, v_reasons = visual_review_status(session_dir)
        visual_payload = {"pass": v_ok, "reasons": v_reasons}
        if not v_ok:
            ok = False
    md = [
        "# PDF Tools report",
        "",
        "This file is **not an original issued document**.",
        "",
        f"- output sha256: `{gen_hash}`",
    ]
    orig_pages = None
    if orig and orig.is_file():
        try:
            orig_pages = len(PdfReader(str(orig)).pages)
        except Exception:
            orig_pages = None
    if orig_hash:
        md.append(f"- input sha256: `{orig_hash}`")
    md.append(f"- pages: {len(r.pages)}")
    if orig_pages is not None:
        md.append(f"- source pages: {orig_pages}")
        if orig_pages != len(r.pages):
            md.append("- warning: output page count differs from source")
    if qa_payload is not None:
        md.append(f"- qa pass: `{qa_pass}`")
        if qa_payload.get("report"):
            md.append(f"- qa report: `{qa_payload.get('report')}`")
        for reason in qa_payload.get("reasons") or []:
            md.append(f"- qa reason: {reason}")
    if visual_payload is not None:
        md.append(f"- visual review pass: `{visual_payload.get('pass')}`")
        for reason in visual_payload.get("reasons") or []:
            md.append(f"- visual reason: {reason}")
    report_path.write_text("\n".join(md) + "\n")
    result = {
        "ok": ok,
        "output_sha256": gen_hash,
        "input_sha256": orig_hash,
        "report": str(report_path),
        "qa_pass": qa_pass,
        "visual_pass": visual_payload.get("pass") if visual_payload else None,
    }
    if qa_payload is not None:
        result["qa"] = qa_payload
    if visual_payload is not None:
        result["visual"] = visual_payload
    out(result)
    if not ok:
        raise SystemExit(1)
    return result


def main(argv=None):
    p = argparse.ArgumentParser(prog="pdfsys")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("probe")
    h = sub.add_parser("hash")
    h.add_argument("file")
    ins = sub.add_parser("inspect")
    ins.add_argument("file")
    ins.add_argument("--out")
    ins.add_argument("--password")
    m = sub.add_parser("merge")
    m.add_argument("files", nargs="+")
    m.add_argument("-o", required=True)
    s = sub.add_parser("split")
    s.add_argument("file")
    s.add_argument("--start", type=int, default=1)
    s.add_argument("--end", type=int, default=1)
    s.add_argument("-o", required=True)
    rot = sub.add_parser("rotate")
    rot.add_argument("file")
    rot.add_argument("--degrees", type=int, default=90)
    rot.add_argument("-o", required=True)
    et = sub.add_parser("extract-text")
    et.add_argument("file")
    et.add_argument("-o", required=True)
    ei = sub.add_parser("extract-images")
    ei.add_argument("file")
    ei.add_argument("-o", required=True)
    i2p = sub.add_parser("images-to-pdf")
    i2p.add_argument("files", nargs="+")
    i2p.add_argument("-o", required=True)
    mdl = sub.add_parser("model")
    mdl.add_argument("file")
    mdl.add_argument("--session", required=True)
    ap = sub.add_parser("apply")
    ap.add_argument("--source")
    ap.add_argument("--fields", required=True)
    ap.add_argument("--plan", required=True)
    ap.add_argument("--fonts", required=True)
    ap.add_argument("-o", required=True)
    g = sub.add_parser("generate")
    g.add_argument("--template")
    g.add_argument("--fields", required=True)
    g.add_argument("--fonts", required=True)
    g.add_argument("-o", required=True)
    v = sub.add_parser("verify")
    v.add_argument("--orig")
    v.add_argument("file")
    v.add_argument("--report", required=True)
    v.add_argument("--session")
    q = sub.add_parser("qa")
    q.add_argument("--session", required=True)
    rd = sub.add_parser("render-deliverable")
    rd.add_argument("--session", required=True)
    rd.add_argument("--which", choices=("output", "source"), default="output")
    rd.add_argument("--dpi", type=int, default=DELIVERABLE_REVIEW_DPI)
    args = p.parse_args(argv)
    if args.cmd == "probe":
        out(probe())
    elif args.cmd == "hash":
        out({"ok": True, "sha256": sha256(Path(args.file))})
    elif args.cmd == "inspect":
        inspect_pdf(Path(args.file), args.password, Path(args.out) if args.out else None)
    elif args.cmd == "model":
        model_pdf(Path(args.file), Path(args.session))
    elif args.cmd == "apply":
        apply_plan(
            Path(args.source) if args.source else None,
            Path(args.fields),
            Path(args.plan),
            Path(args.fonts),
            Path(args.o),
        )
    elif args.cmd == "merge":
        toolkit_merge([Path(f) for f in args.files], Path(args.o))
    elif args.cmd == "split":
        toolkit_split(Path(args.file), Path(args.o), args.start, args.end)
    elif args.cmd == "rotate":
        toolkit_rotate(Path(args.file), Path(args.o), args.degrees)
    elif args.cmd == "extract-text":
        extract_text(Path(args.file), Path(args.o))
    elif args.cmd == "extract-images":
        extract_images(Path(args.file), Path(args.o))
    elif args.cmd == "images-to-pdf":
        images_to_pdf([Path(f) for f in args.files], Path(args.o))
    elif args.cmd == "generate":
        generate(Path(args.template or "."), Path(args.fields), Path(args.fonts), Path(args.o))
    elif args.cmd == "verify":
        session = Path(args.session) if getattr(args, "session", None) else None
        if session is None and args.orig:
            session = Path(args.orig).parent.parent
        verify(
            Path(args.orig) if args.orig else None,
            Path(args.file),
            Path(args.report),
            session,
        )
    elif args.cmd == "qa":
        payload = run_qa_session(Path(args.session))
        if not payload.get("ok"):
            out(payload)
            raise SystemExit(1)
        out(payload)
    elif args.cmd == "render-deliverable":
        render_deliverable(Path(args.session), which=args.which, dpi=args.dpi)
    else:
        p.error("unknown")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except Exception as e:
        print(json.dumps({"ok": False, "error": str(e)}), file=sys.stderr)
        raise SystemExit(1)
