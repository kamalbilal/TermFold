# Design

## Context

Workflows home is an enum-driven list (`WorkflowsSection`: Automation, PR Reviewer, Product Manager, More). PR Reviewer and PM are the pattern to copy: DataStore-backed session list, `MainActivity` destination, dedicated session screen wrapping `AgentSessionScreen`, builtin MCP only while that session is active, structured cards from a validated tool payload.

PDF work cannot live in the Android JVM as a full engine. The Linux guest already runs Node MCP helpers (`termfold-pr-review`, memory servers) via `ShellSetup` + `McpCatalog`. Python PDF libraries may be present in some guests; this change **probes** them and ships `pdfsys` plus fonts rather than pinning another sandbox’s package list.

See proposal.md for product motivation. Specs in `specs/pdf-tools/spec.md` are the behavior contract.

## Goals / Non-Goals

**Goals:**

- Same navigation and session lifecycle as PR Reviewer/PM, without git workspace provisioning.
- One guest CLI (`pdfsys`) for toolkit, inspect, generate, verify; Android UI never overwrites inputs.
- MCP as the only agent mutation path; Kotlin + CLI both enforce authz and “no overlay”.
- Fast page preview on device (`PdfRenderer`) while inspect JSON and diffs come from `pdfsys`.

**Non-Goals:**

- Content-stream surgery, overlay, OCR reconstruction, Edit-own / Redact UI (later changes).
- PyMuPDF unless a later change accepts AGPL.
- Binding PDF sessions to coding folders or golden git trees.
- Making More contain PDF Tools.

## Decisions

### 1. Session storage like PR stubs, not git copies

**Choice:** `PdfToolsPaths.GUEST_ROOT = /root/.termfold/pdf-tools/<sessionId>/` with `input/` (immutable copies), `work/` (attempts), `renders/`, `inspect.json`, `plan.json`, `report.md`, `out/`. Metadata in DataStore (`PdfToolsStore`), same JSON persistence style as `PrReviewStore`.

**Why:** Spec forbids requiring a coding folder. PR Reviewer already uses a guest session dir plus optional git copy; PDF skips the git copy entirely.

**Alternative:** Reuse Automation project trees — mixes browser automation with documents and implies a linked folder.

### 2. Two surfaces, one engine

**Choice:**

- **Toolkit** composables on the section screen call `pdfsys` via a one-shot guest command (no ACP). Progress on the UI thread via a ViewModel job (same idea as `PrReviewJob`).
- **Jobs** use ACP + `PdfToolsAgentSessionScreen` with `AgentMemoryWorkflow.PDF_TOOLS`.

**Why:** Merge/split must not pay LLM latency. Spec requires toolkit without an agent.

**Alternative:** Everything is a job — worse UX and costs.

### 3. `pdfsys` CLI + Node MCP shim

**Choice:** Python package `pdfsys` (inspect, classify, extract/model, generate from page spec, toolkit, verify). Builtin MCP `termfold-pdf` is a small Node stdio server (same install pattern as `termfold-pr-review.js`) that:

- Runs `pdfsys` in the session directory.
- For plan/apply/verify/refuse, talks to the app bridge (`PdfToolsBridge` / `BrowserBridge` HTTP) so approval state and authz live in Kotlin.

Tools: `pdf_inspect`, `pdf_model`, `pdf_plan`, `pdf_apply`, `pdf_verify`, `pdf_refuse`.

`pdf_apply` checks: active session, class in `{OWN_TEMPLATE, OWN_DATA_DOC}`, user-approved plan id, no overlay ops, then `pdfsys apply`. `pdf_refuse` records a structured refusal for third-party / unknown.

**Why:** PR Review MCP already validates in the app. PDF mutations are more dangerous; the CLI must not trust the model.

**Alternative:** Agent shells `pdfsys` directly — skips approval and is hard to audit.

**Alternative:** Pure Kotlin PDF — cannot match inspect/generate quality.

### 4. Eager inspect is a guest job on import, not an agent turn

**Choice:** After copy+hash on **Inspect** (or any job that imported a PDF), ViewModel runs `pdfsys inspect` (and render PNGs). Session screen reads `inspect.json` immediately. **Generate with no source PDF skips this.** Agent prompt treats inspect files as facts when present.

**Why:** Matches PR facts pack timing. Spec: chips before chat.

**Capability probe:** if required Python modules or poppler/qpdf are missing, inspect returns a typed error; UI shows setup guidance. Do not bake a laptop sandbox package list into the app.

### 5. Generate = reportlab templates, form is source of truth

**Choice:** Ship 1–2 owned templates as JSON page specs (letter/invoice-style) plus TTFs registered with reportlab. UI form fields map 1:1 to `{{tokens}}`. Confirm Generate calls `pdfsys generate` from the ViewModel **with no ACP turn**. Optional CSV: **one row** or reject `>1` with a clear error.

Chat cannot change field values. Agent `pdf_apply` is only for extra ops after plan cards; it still reads `fields.json` for any field tokens, not tool arguments for those keys.

**Why:** Stops the model from “helpfully” rewriting amounts.

**Alternative:** Agent fills reportlab in a scratch script — unreviewable.

### 6. Preview and verify

**Choice:** Android `PdfRenderer` for the in-session pager (input and `out/`). `pdfsys verify` uses guest renders + text extract + qpdf check; thresholds from the engine note (unchanged pages high SSIM; text diff equals approved ops). UI shows before/after + report cards from `pdf_plan` / verify payload (turn footers like PR Reviewer).

**Why:** Native pager is the right phone UX; pixel math stays in guest.

### 7. Authz and overlay in two places

**Choice:** Enum on `PdfToolsSession`. Toolkit mutate and `pdf_apply` both refuse `THIRD_PARTY_ISSUED` and `UNKNOWN`. Heuristic preflight (filename/keywords) may **suggest** a class but never auto-set `OWN_*`. Overlay ops are a closed deny list in plan schema.

**Why:** Prompt-only gates fail.

### 8. Workflow enum placement

**Choice:** Insert `PDF_TOOLS` before `MORE` in `WorkflowsSection` so More stays last. New destination `Destination.PdfToolsSession`. Session options/delete mirror PR/PM (close ACP, delete host session dir).

Share-in: extend `ShareReceiverActivity` so a PDF can start a PDF Tools Inspect job **or** copy into a coding project (existing behavior). Do not replace project share.

### 9. Fonts and RTL

**Choice:** Bundle Liberation + Noto (Latin + Naskh/Nastaliq) into guest on setup. Generate uses those families. Complex RTL layout bugs are accepted for v1 templates if glyphs embed; do not add WeasyPrint as a hard dependency.

### 10. Privacy

**Choice:** `pdfsys` and MCP logs: hashes, page counts, op ids — not extracted body. `removePdfToolsSession` deletes the directory recursively (same as `PrReviewPaths.hostSessionDir` delete).

## Risks / Trade-offs

- **[Guest Python missing]** → Vendor or install `pdfsys` runtime deps in `ShellSetup`; probe at inspect; setup error if still missing. Toolkit may use qpdf/pypdf when those exist.
- **[Large PDFs / many pages]** → Reject import over **50 MB** or **100 pages** (error names the limit); thumbs at 150 DPI for accepted files.
- **[Untrusted PDF]** → Parse only; never honor `/JS`, `/OpenAction`, `/Launch`.
- **[Model bypasses MCP]** → Prompt + no other write permission to `out/` except `pdfsys apply`; still possible via raw shell — PDF Tools prompt and session cwd should discourage it; do not grant a general “edit any pdf” skill.
- **[Forgery misuse]** → Product copy and refuse paths; cannot prevent a determined user from lying about `OWN_TEMPLATE`. UI confirmation text is explicit.
- **[AGPL PyMuPDF]** → Stay on pikepdf/pypdf/reportlab; redaction later.
- **[Phone CPU]** → Inspect at 150 DPI thumbs, 300 DPI only for verify of changed pages.

## Migration Plan

- New section and empty session list; no data migration.
- `ShellSetup` writes `pdfsys` + MCP script + fonts; existing guests pick this up on next setup/compat pass (follow current `GuestCompat` / setup patterns).
- Rollback: hide section behind the same shipping of the APK; leftover `/root/.termfold/pdf-tools` dirs are inert.

## Open Questions

- Exact first bundled template set (one letter vs letter+invoice) can be chosen at apply time without changing specs.
