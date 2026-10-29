# Tasks

## 1. Session model and storage

- [x] 1.1 Add `com.termfold.app.pdftools` session model (id, name, job type Inspect|Generate, authz class, hashes, paths, tint, timestamps) plus DataStore `PdfToolsStore` and persistence encode/decode tests.
- [x] 1.2 Add `PdfToolsPaths` (`/root/.termfold/pdf-tools/<id>/` with `input/`, `work/`, `renders/`, `out/`) and `ensureStub`; unit-test guest/host path pairing like `PrReviewPaths`.
- [x] 1.3 Wire `AppViewModel` list/create/delete: delete removes host session dir recursively; test delete clears store + directory.

## 2. Workflows navigation

- [x] 2.1 Add `WorkflowsSection.PDF_TOOLS` (before More), strings, icon, `WorkflowsSectionScreen` branch, and `MainActivity` destination for the section and a session; verify unit/UI list includes PDF Tools and opening it is not More.
- [x] 2.2 Build `PdfToolsSectionScreen`: toolkit block + session list + start Inspect/Generate without a coding folder; empty-state copy; options menu delete.

## 3. Guest pdfsys engine

- [x] 3.1 Scaffold guest `pdfsys` CLI (inspect, toolkit merge/split/rotate/extract-text/extract-images/images-to-pdf, hash, capability probe, generate) installed from `ShellSetup` **with runtime deps** (or a clear probe error); verify `pdfsys --help` and probe JSON on a provisioned guest or host pytest of the same modules.
- [x] 3.2 Implement inspect JSON (pages, encrypted, signatures, acroform, per-page kind, font summary) writing `inspect.json` without mutating input; golden-file test on a small native PDF.
- [x] 3.3 Implement toolkit ops writing new files only; tests assert input SHA-256 unchanged and merge/split/rotate outputs open.
- [x] 3.4 Bundle Liberation + Noto font files into guest setup; verify generate registration path can see them (file exists after setup write).
- [x] 3.5 Inspect/toolkit never execute PDF JS, `/OpenAction`, or `/Launch`; add a fixture PDF or mocked catalog test that actions are not invoked.

## 4. Toolkit UI

- [x] 4.1 Wire toolkit actions to one-shot guest `pdfsys` jobs in ViewModel (progress/error like `PrReviewJob`); confirm no ACP session is started.
- [x] 4.2 Enforce authz on toolkit mutate: missing class or `THIRD_PARTY_ISSUED`/`UNKNOWN` blocks; test the gate in ViewModel/use-case tests.

## 5. Import, eager inspect, preview

- [x] 5.1 Import via SAF / Files copy into `input/`, record SHA-256, never touch source; reject over 50 MB or 100 pages with an error that names the limit and no leftover copy; test copy + hash + reject.
- [x] 5.2 After import on Inspect (or any job with a source PDF), run inspect + thumbnail renders before chat; chips + `PdfRenderer`; Generate with no PDF skips inspect; test inspect invoked on Inspect import and skipped on empty Generate.
- [x] 5.3 Encrypted PDF without password: UI asks password, no crack path; test inspect error type surfaces.

## 6. Generate job

- [x] 6.1 Ship one owned letter (or equivalent) JSON template + form bound to `fields.json`; **ViewModel Generate with no ACP**; reportlab embedded TTF; test fonts embedded and tokens from `fields.json` not from a fake chat string.
- [x] 6.2 Optional CSV: accept one data row or reject >1 row with a clear error; unit test both.
- [x] 6.3 Reject overlay ops in plan schema; unit test plan parser deny list.

## 7. MCP, approval, verify

- [x] 7.1 Add `termfold-pdf` Node MCP (tools inspect/model/plan/apply/verify/refuse), `ShellSetup` install, `McpCatalog` builtin only when PDF Tools session active; catalog test mirrors `McpCatalogPrReviewTest`.
- [x] 7.2 `PdfToolsSessionContext` + `PdfToolsBridge`: apply fails without approved plan id, without `OWN_TEMPLATE`/`OWN_DATA_DOC`, or on overlay; tests for ok/refuse/mismatch.
- [x] 7.3 Plan approval cards only for **agent** ops; form Generate does not wait on cards; `pdf_apply` still blocked without approved plan id; tests for both paths.
- [x] 7.4 Verify + delivery package (out PDF, report, hashes, not-an-original-issued-document label); inspect report notes signatures would be invalidated by edits; tests for report fields and failed verify not marking success.
- [x] 7.5 `AgentMemoryWorkflow.PDF_TOOLS` + prompt: MCP is the mutation path; `PdfToolsPromptTest` asserts tool names and authz/refuse rules.

## 8. Session chrome, share, privacy

- [x] 8.1 `PdfToolsAgentSessionScreen` wrapping agent chat + preview + class picker + form (Generate); share package includes PDF + report.
- [x] 8.2 Extend `ShareReceiverActivity`: PDF chooser **PDF Tools Inspect** vs save to coding project; test both paths and that project share still works.
- [x] 8.3 Confirm logs/MCP responses omit document body text (test fixtures use hashes only) and session delete wipes the directory (covered in 1.3; add a log-redaction assertion if a logger helper exists).

## 9. Integration

- [x] 9.1 Run unit tests for `com.termfold.app.pdftools` and MCP catalog/bridge packages via Gradle; fix failures.
- [x] 9.2 Run `openspec validate pdf-tools-workflow --type change` and confirm it passes.

## Workflow follow-up

- Archive after review.
- Device smoke: Workflows → PDF Tools → toolkit merge; Inspect import chips; Generate from form with no agent → share labeled output; share PDF → Inspect vs project; third-party refuse copy (own template / contact issuer).
