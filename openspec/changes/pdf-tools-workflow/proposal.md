# Proposal

## Why

TermFold Workflows already has Automation, PR Reviewer, and Product Manager. Users need a first-class **PDF Tools** lane to inspect, generate, and run deterministic PDF operations on device—without turning the app into an arbitrary PDF forger. A long Python engine spec exists from exploration; the product gap is a Workflows section, session model, authorization, and a guest engine the agent cannot skip.

## What Changes

- Add a **PDF Tools** section on the Workflows home list (not under More).
- Split the section into **Toolkit** (no agent: merge, split, rotate, extract text/images, images-to-PDF) and **Jobs** (sessions with inspect + generate).
- Jobs are **document-centric**: session dirs under guest storage, not git workspace copies. Import via SAF, share-in, or the Files tab.
- On import, run **eager local inspect** (structure, page class, fonts, renders) before any LLM turn—same idea as the PR Reviewer facts pack.
- **Generate** jobs: named templates the user owns + form/CSV field data → new PDF with embedded fonts. Confirming the form generates **without an agent**. No source PDF required. Chat is optional for layout; field values live in the form/CSV.
- **Inspect** jobs: import a PDF, eager inspect chips, page preview, shareable inspect report. No silent edits.
- **Share** of a PDF offers PDF Tools Inspect **or** the existing save-to-project path.
- **Authorization class** is a UI control (`OWN_TEMPLATE` | `OWN_DATA_DOC` | `THIRD_PARTY_ISSUED` | `UNKNOWN`). Third-party issued financial, identity, legal, or credential documents cannot be edited. Overlay-style “cover old text” is forbidden.
- **Plan approval cards** only for **agent** operations beyond form Generate (PR Reviewer finding-ids pattern). Form Generate is approved by confirming the form. Delivery: hashes, “not an original issued document”; inspect states that signatures would be invalidated by edits.
- Import **size and page limits**; PDFs are untrusted (no JS / OpenAction / Launch). Third-party refuse copy points at own-template Generate or contacting the issuer — not a request-letter job.
- Guest **`pdfsys` CLI** plus builtin MCP **`termfold-pdf`** (`pdf_inspect`, `pdf_model`, `pdf_plan`, `pdf_apply`, `pdf_verify`, `pdf_refuse`). Apply refuses missing class, third-party issued, or unverified plans.
- **Out of this change:** arbitrary PDF surgery / stream edit, overlay strategy, OCR that reconstructs a scan to look original, template capture from a sample PDF, batch CSV beyond one generate form, PDF/A-UA as a hard requirement, Edit-own and Redact job types in the UI.

## Capabilities

### New Capabilities

- `pdf-tools`: Workflows PDF Tools section, toolkit, document sessions, inspect/generate jobs, authz, plan cards, guest engine + MCP, preview and delivery package.

### Modified Capabilities

- (none)

## Impact

- `WorkflowsSection` + `WorkflowsScreen` / `WorkflowsSectionScreen` / `MainActivity` destinations, mirroring PR Reviewer / PM.
- New `com.termfold.app.pdftools` models, store, paths, prompt, session screen, plan/report chrome.
- `AgentMemoryWorkflow.PDF_TOOLS`, `McpCatalog` builtin `termfold-pdf` when a PDF Tools session is active.
- Guest: `pdfsys` installed via `ShellSetup` (Python stack already used elsewhere in guest; probe capabilities at runtime). Bundled fonts (Liberation + Noto coverage for Latin and Arabic/Urdu).
- Android `PdfRenderer` (or equivalent) for in-app page preview; original bytes never overwritten.
- Tests: store/session, inspect JSON schema, authz refuse, MCP catalog/wire, toolkit operations on golden PDFs, generate embeds fonts.
- Privacy: do not log document body text; session wipe deletes work copies.
