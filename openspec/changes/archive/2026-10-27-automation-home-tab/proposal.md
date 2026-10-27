# Proposal

## Why

TermFold’s preview browser and `termfold-browser` MCP are built for ad hoc agent use inside coding projects. There is no dedicated place to run **governed, repeatable browser automation**: isolated profiles per run, mandatory PRD/constraints every turn, optional ties to a repo, and memory that survives context compression without stuffing full logs into each prompt. A home **Automation** lane fixes that while reusing ACP and the existing WebView driver.

## What Changes

- **Home UX**: On the Projects home destination, add a **Projects | Automation** segment (bottom nav unchanged). Automation lists **automation projects**; each project holds **automation sessions**.
- **Session runtime**: Opening an automation session opens **ACP chat + in-app preview** bound to that session’s **persistent WebView profile** (cookies/storage isolated per session).
- **Session bundle**: Each session owns `docs/` (PRD, requirements, constraints), encrypted **secrets**, run artifacts, and a **private memory database** (full audit log + credentials + agent notes). Mandatory doc/constraints content is **injected on every prompt** (survives compress via timeline anchoring).
- **Optional linked coding project**: Automation project may **link, change, or unlink** a `Folder` at any time. Linked tree is **read-only** for agent file tools; previews may use the linked guest path for `files.termfold`. Unlink does not delete session profile or private DB.
- **Global navigation memory**: App-wide SQLite store for **navigation-only** facts (flows, selectors, pitfalls). **No human approval** for agent writes; server rejects credential-shaped content. **Semantic vector ranking on read and write** (v1, on-device).
- **Session memory retrieval**: Private DB indexes **chunks** (episodes, highlights, secrets metadata, notes) with **on-device embeddings**; agents recall prior work via **thin prompt inject** + **search tools**, not full log dumps. Decrypted credentials are **tool-gated**, never bulk-injected.
- **Browser bridge**: `BrowserBridge` / preview driver target the **active automation session** (not a single global anonymous profile).
- **Backup**: Automation profiles, private DBs, and global nav DB participate in TermFold backup/restore.
- **Testing-oriented UX** (phased in tasks): run log UI, optional test cases/assertions and screenshot baselines under session storage.

**Out of scope (initial change)**: Scheduled/headless runs without UI, git workspace copies for automation sessions, automated CAPTCHA bypass, cloud sync of memory DBs, write access to linked coding projects.

## Capabilities

### New Capabilities

- `automation-workspace`: Home Automation segment, automation project/session model, storage layout, optional linked folder (read-only), session screen shell, backup inclusion.
- `automation-memory`: Global nav knowledge DB and per-session private memory DB, event logging, ONNX (or equivalent) embeddings, semantic search, MCP/agent tools, prompt injection caps, autonomous global writes with credential rejection on global store.
- `agent-preview-browser`: Per-session browser binding, WebView data-directory isolation, and automation-mode integration with preview (delta spec; capability not yet in main `openspec/specs/`—archive/sync with `termfold-browser-improvements` when ready).

### Modified Capabilities

- `acp-session-chat`: Automation sessions use ACP with per-turn mandatory context injection and memory tool surface (delta where behavior differs from generic coding sessions).
- `bundled-linux-guest`: Bundled skill/docs for automation memory tools and browser-first session workflow.

## Impact

- `MainActivity.kt`, `FoldersScreen.kt` (or new automation screens), `FolderStore` / new automation store, `Models.kt` or parallel types
- `preview/Preview.kt`, `BrowserBridge.kt`, `BrowserDriver.kt`, `PreviewPane.kt` (session binding)
- New packages: automation storage, memory DB, embedding worker, MCP tools
- `AcpClient.kt` / prompt assembly, timeline anchor for automation context
- `TermFoldBackup.kt`
- Gradle: **ObjectBox** (vector + entities), **ONNX Runtime Android** (embeddings); bundled MiniLM-class ONNX asset
- Unit tests: memory write rejection, link/unlink, semantic search fallback, read-only linked path enforcement
