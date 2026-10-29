# TermFold PDF Tools — Complete Agent & Pipeline Specification

**Purpose:** Replace all previous PDF agent prompts, memory blocks, and default “replace_text only” flows with this document. Give this file to the app-building AI and wire its contents into every PDF Tools session (system prompt, project rules, or `<termfold-agent-memory-context>`).

**Last reviewed:** 2026-10-29 (tool names verified against PyPI/GitHub/docs listed below).

---

## 0. Research verification (read before implementing)

This spec is **accurate for building the pipeline**, with these **honest limits**:

| Claim | Verified? | Notes |
|-------|-----------|--------|
| `diffpdf` compares PDFs (pages, text, pixels) | Yes | [PyPI diffpdf](https://pypi.org/project/diffpdf/), [GitHub JustusRijke/DiffPDF](https://github.com/JustusRijke/DiffPDF). MIT. Python ≥3.11 on latest. Uses PyMuPDF + pixelmatch-fast. |
| `pdf-perceptual-compare` SSIM per page | Yes | [PyPI pdf-perceptual-compare](https://pypi.org/project/pdf-perceptual-compare/). MIT. pypdfium2 render. Good when fonts differ slightly. |
| `scikit-image` SSIM for PNG pairs | Yes | [`skimage.metrics.structural_similarity`](https://scikit-image.org/docs/stable/api/skimage.metrics.html). Standard for masked region compare. |
| PyMuPDF `get_pixmap(dpi=300)` | Yes | [PyMuPDF images recipe](https://pymupdf.readthedocs.io/en/latest/recipes-images.html). |
| WeasyPrint `dpi` / `image-resolution` for embedded images | Yes | [WeasyPrint API](https://doc.courtbouillon.org/weasyprint/stable/api_reference.html), [issue #248](https://github.com/Kozea/WeasyPrint/issues/248). `dpi` caps embedded raster resolution; CSS `image-resolution: 300dpi` on `img`. |
| Recreated PDF will **not** pixel-match original at 100% | Expected | Different generator (HTML/ReportLab) + fonts → use **SSIM + masks**, not raw byte identity. |
| `diffpdf` text stage on **name-change recreate** | Will fail | Use `--skip-compare-text` and compare **visual only** on masked regions (see §5). |
| Handwriting ML (Graves synthesis) | Optional, heavy | [sjvasquez/handwriting-synthesis](https://github.com/sjvasquez/handwriting-synthesis) — not required for v1; prefer **SVG/Bezier ink** (see §6). |
| `localtools-pdf-diff` | Yes, newer | [PyPI localtools-pdf-diff](https://pypi.org/project/localtools-pdf-diff/) — PDFium visual + word diff + JSON; good for UI reports. |
| `compare-pdf` (OpenCV) | Yes | [Formartha/compare-pdf](https://github.com/Formartha/compare-pdf). MIT. |

**Extra tools you may add later (not required for v1):**

- **Font matching:** `fonttools`, `whatthefont`-style ID is unreliable; prefer measuring from original PDF or fixed template CSS.
- **Layout OCR:** Tesseract / docTR for bbox discovery only (internal; never mention to end user).
- **Vision QA:** Optional multimodal model on crop PNGs (“is this a valid logo crop?”) — supplement, not replace numeric QA.

**No single tool auto-clones arbitrary PDFs.** Path B always needs a **session-specific template** (HTML/CSS or ReportLab) derived from the current upload.

---

## 1. What to remove from the old product prompts

Delete or stop injecting these as **primary** instructions:

- “Always `pdf_fields` → `pdf_plan` `replace_text` → Approve → apply” as the **only** path.
- Defaulting to server **text-only recreate** on image/scanned pages (drops letterhead — caused user-facing failures).
- Encouraging paint-over / OCR patch on page bitmaps for names.
- `fill_form` / `apply_fields` to change printed names on letters.

**Keep (optional integration with existing MCP):**

- `fields.json` + `employee_name` for confirmed values.
- User **Approve** step before writing final `out/output.pdf` if the product already has it.
- Session memory search/write for goals.
- Do not paste document body in chat; do not mention internal filenames to end users.

---

## 2. End-user agent behavior (inject every PDF session)

```markdown
You help the user change their uploaded PDF. Short, plain language (Urdu/English OK). Never mention internal files, MCP, or tool names in chat. Do not paste document body.

Examples in these rules are illustrative only. Always derive page count, layout, and fields from the **current upload** and the **user’s message**.

### Step 0 — Goal
Recall session memory when starting or when unsure. Store confirmed names and decisions in session memory.

**Guest libraries first:** Before inspect, model, apply, generate, or QA, ensure required Python PDF libraries are installed in the Linux guest. Install all missing packages for your path (basic for Path A; full stack for Path B), confirm with probe, then start work.

### Step 1 — Strategy (mandatory)

**Path A — In-place text replace**  
Only if the PDF has a **real text layer** on designed pages (clean select/copy of body text; not picture-only pages).

**Path B — Full layout-faithful recreate (DEFAULT)**  
Use when any of: picture/scanned pages; user wants same design; letter + slips / letterhead; prior output was plain text, wrong page count, or visible edit patches; **when in doubt → Path B**.

Never patch names with white boxes on page images. Never form-fill over a printed name on a letter.

### Path A (text-native only)
1. Confirmed values in fields (`employee_name` for person name).
2. Plan: replace_text, find = exact on-page words, replace = new words.
3. User Approve (PDF ⋮) → apply → verify.
4. One–two sentences: file ready.

Skip re-inspect if upload unchanged and inspect data already exists.

### Path B (recreate — preferred for designed docs)
1. Visually analyze upload: page count and types.
2. Recreate **entire PDF** to match layout (fonts, colors, rules, boxes, tables). Only change user-confirmed fields.
3. Assets: crop logo/unchanged sigs from page 1 at **300 DPI**; redraw changed employee signature as **SVG or high-DPI transparent PNG** (see pipeline §6).
4. Write `out/output.pdf` (and `out/letter.pdf` if product expects it).
5. Run session QA script (§5). If fail, fix template/assets and retry (max 5).
6. One–two sentences: file ready.

Do **not** use plain text-only rebuild that removes letterhead. Do **not** paint-over old pages for Path B deliverables.

### User phrases → Path B immediately
“same design”, “recreate”, “dobara banao”, “edit mat karo”, “original jaisa”
```

---

## 3. Session directory layout

Each PDF Tools session root (example):

```
{session_id}/
  input/source.pdf          # user upload (immutable reference)
  fields.json               # user-confirmed fields
  out/output.pdf            # deliverable
  out/letter.pdf            # optional alias
  work/
    golden/                 # renders of source @ DPI (QA reference)
    candidate/              # renders of output @ same DPI
    diff/                   # diff overlays
    assets/                 # logo_bling.png, ceo_sig.png, emp_sig.svg, ...
    crops/                  # raw crops + qa_crop.json each
    layout_spec.json        # compare masks, page roles, colors (optional v1)
    pack.html               # HTML source for WeasyPrint
    generate.py             # builds output.pdf
    qa_loop.py              # runs §5, writes work/qa/report.json
  plan.json                 # optional; Path A only
```

**DPI standard:** `300` for page renders and QA; embedded logos/signatures at **≥300 DPI effective** or **vector SVG**.

---

## 4. Path B generator (recommended stack)

| Layer | Tool | License | Role |
|-------|------|---------|------|
| HTML → PDF | [WeasyPrint](https://doc.courtbouillon.org/weasyprint/) | BSD | Letter + slip pages from `pack.html` + CSS |
| Raster assets | Pillow | HPND | Crops, sig PNG |
| Vector sig | svgwrite or inline SVG | MIT | Smooth ink, zoom-safe |
| PDF read/render | PyMuPDF (`pymupdf`) | AGPL/commercial | `get_pixmap(dpi=300)`, optional merge |
| Alt render | pypdfium2 | Apache/BSD | Same as Chrome PDFium; use **one engine consistently** in QA |

**WeasyPrint write example (HD assets):**

```python
HTML(filename="work/pack.html").write_pdf(
    "out/output.pdf",
    optimize_images=False,
    # optional: dpi=300 caps max embedded image resolution
)
```

**CSS (embedded images):**

```css
img.logo, img.sig { image-resolution: 300dpi; }
```

**Path A (when applicable):** keep existing MCP `pdf_plan` / `pdf_apply` / `pdf_verify` with `replace_text` only.

---

## 5. QA loop (mandatory after Path B generate)

Implement `work/qa_loop.py` that writes `work/qa/report.json` and exits `0` or `1`.

### 5.1 Hard gates

1. `pages(source) == pages(output)` else fail.
2. Render every page of **both** PDFs to PNG at **same DPI** (300) into `work/golden/` and `work/candidate/`.
3. For each page, compute **SSIM** (scikit-image) on **full page** or **masked regions** from `layout_spec.json`.

### 5.2 Name-change recreate (important)

When the user changed text (e.g. employee name):

- Do **not** require text equality between source and output.
- Run visual compare with **ignore masks** over regions that **must** change (name fields, employee signature).
- Pass if **non-mask SSIM ≥ 0.93** (tune per product) and **masked change regions** contain expected new string (optional text read on candidate render via Tesseract — internal only).

For strict regression of **unchanged** templates, use [diffpdf](https://pypi.org/project/diffpdf/):

```bash
diffpdf input/source.pdf out/output.pdf --skip-compare-text --dpi 300 --output-dir work/diff/
```

Interpret exit code: `0` pass, `1` fail, `2` error. On recreate with intentional edits, prefer **SSIM + masks** over raw `diffpdf` pass.

Alternative: [pdf-perceptual-compare](https://pypi.org/project/pdf-perceptual-compare/) for SSIM-native PDF compare.

### 5.3 Crop QA (`work/crops/`)

For each crop (logo, ceo_sig, emp_sig):

| Check | Fail condition |
|-------|----------------|
| Empty | Mean luminance > 250 and low variance |
| Too small | width or height < 32 px at 300 DPI scale |
| No ink (signatures) | Dark pixel ratio ∉ [0.005, 0.45] |
| Bad logo | SSIM vs same bbox from golden page 1 < 0.85 |

Write `qa_crop.json`: `{ "ok": true/false, "ink_ratio", "ssim", "reasons": [] }`.

### 5.4 Agent retry loop

```
generate → qa_loop.py
  if fail: read report.json + diff PNGs → adjust CSS/bboxes/assets → retry (max 5)
  if pass: notify user
```

### 5.5 Visual review gate (deliverable PNGs)

Before telling the user the PDF is ready:

1. `pdfsys render-deliverable --session …` → `work/renders/deliverable/page_NNN.png` @ 150 DPI (PyMuPDF or pdftoppm).
2. Agent opens/reviews **every** PNG (layout, names, letterhead — no white boxes).
3. MCP `pdf_visual_confirm` with `ok: true` and `pages_reviewed` → writes `work/visual_review.json` (bound to output sha256).
4. `pdf_verify` fails if visual review is missing, incomplete, or stale after output changes.

---

## 6. Signatures and non-copyable parts

**Priority:**

1. **SVG paths** (Bezier strokes, variable width) → embed in HTML or PDF vector.
2. **Transparent PNG** at 2× display size (e.g. sig line 400×120 px for ~200 pt display).
3. Do **not** ship low-DPI JPEG of signatures.

**Redraw employee signature when name changes:** do not crop old name ink. Generate new SVG/PNG ink for the new name.

Optional smooth curves: Inkscape-style auto-smooth Bezier ([reference gist](https://gist.github.com/aconz2/b7fbb5770031fced9eaeb86b3f364b07)) + `svgpathtools`.

---

## 7. Python dependencies (suggested `requirements-pdf-tools.txt`)

```text
# Core
pymupdf>=1.24
weasyprint>=62
pillow>=10
numpy>=1.26

# QA
scikit-image>=0.22
opencv-python-headless>=4.8

# PDF compare (pick one primary + SSIM)
diffpdf>=1.2
# pdf-perceptual-compare>=1.0.2
# pypdfium2>=4.0

# Optional Path A (existing product MCP backend)
# + server-side pdf tools as already deployed

# Optional crop text check (internal)
# pytesseract + system tesseract-ocr
```

System packages for WeasyPrint: follow [WeasyPrint install docs](https://doc.courtbouillon.org/weasyprint/stable/first_steps.html) (Pango, Cairo, etc.).

---

## 8. Product integration checklist

- [ ] Replace old PDF agent memory block with **§2** verbatim (or merge into TermFold project rules).
- [ ] On session open: classify upload → set default path **B** if `page_kinds` are empty/scanned or model says no reliable text layer.
- [ ] Ship `qa_loop.py` template in session scaffold; agent must run it before “ready”.
- [ ] Block deliverable if `qa/report.json` → `"pass": false`.
- [ ] Surface diff PNGs to agent only (not end user unless debug).
- [ ] Document for users: “Same design → say **recreate, edit mat karo**.”

---

## 9. Phased rollout

| Phase | Scope |
|-------|--------|
| **1** | §2 agent rules + Path B `generate.py` + §5 QA + 300 DPI |
| **2** | `layout_spec.json` masks per template family (Bling letter+slips, etc.) |
| **3** | Template library reused across sessions (same employer layout) |
| **4** | Optional vision review on crops; optional `localtools-pdf-diff` UI |

---

## 10. Lessons from production session (why this spec exists)

- **Wrong:** `replace_text` / text-only rebuild on image-based 7-page pack → plain 13-page text PDF, no logo.
- **Wrong:** Painting new names on page PNGs → visible grey boxes.
- **Right:** HTML/CSS recreate + real logo asset + QA page count + visual sanity.

---

## 11. References (for implementers)

- DiffPDF: https://github.com/JustusRijke/DiffPDF  
- pdf-perceptual-compare: https://pypi.org/project/pdf-perceptual-compare/  
- localtools-pdf-diff: https://pypi.org/project/localtools-pdf-diff/  
- compare-pdf: https://github.com/Formartha/compare-pdf  
- PyMuPDF images: https://pymupdf.readthedocs.io/en/latest/recipes-images.html  
- WeasyPrint API: https://doc.courtbouillon.org/weasyprint/stable/api_reference.html  
- SSIM: https://scikit-image.org/docs/stable/api/skimage.metrics.html  

---

*End of specification — this document supersedes prior PDF Tools agent instructions.*
