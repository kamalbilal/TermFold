# Design

## Context

See `proposal.md` (Why). TermFold today lists coding **Projects** on `NavTab.FOLDERS`, stores folders in DataStore, and runs agents via ACP with optional git workspace copies. Preview + `BrowserBridge` + `BrowserDriver` provide MCP browser automation but use a shared preview surface and do not isolate WebView storage per logical “run.” `termfold-browser-improvements` (complete on branch) extends driver capabilities; this change adds product structure and memory around that stack.

## Goals / Non-Goals

**Goals:**

- Home **Projects | Automation** segment; automation projects and sessions with guest storage under `~/.termfold/automation/`.
- Per-session WebView profile + bridge binding; optional **read-only** link to existing `Folder`.
- Private session DB (events, secrets, chunks + embeddings) and global nav DB (navigation-only, autonomous agent writes).
- On-device **semantic ranking v1** for retrieval; bounded prompt inject; tool-gated credential decrypt.
- Backup/restore of automation + memory DBs.

**Non-Goals:**

- Git workspace copies for automation sessions; write access to linked coding trees.
- Human approval queues for global memory; cloud embedding APIs.
- Scheduled headless test runs; CAPTCHA bypass.

## Decisions

### 1. Data stores (ObjectBox)

**Choice:** Use **[ObjectBox](https://objectbox.io/)** (Java/Kotlin SDK + Gradle plugin) as the single on-device database for automation memory. ObjectBox provides production **HNSW approximate nearest-neighbor (ANN) vector search**, metadata filters (e.g. `automationSessionId`, `siteKey`, `kind`), and fast object CRUD—better fit than hand-rolled SQLite for semantic recall at scale.

Entity layout (illustrative):

- `MemoryChunk` — `@Entity` with `@HnswIndex` float vector, `searchText`, `kind`, `siteKey`, `automationSessionId` (null = global nav row), scores, timestamps, `sourceEventId`
- `SessionEvent` — append-only audit rows (no vector required; linked from chunks)
- `SessionSecret` — `name`, metadata, `encryptedPayload` bytes (Android Keystore–wrapped key); **not** vector-indexed on secret values

Store file under app private storage (e.g. `filesDir/objectbox/automation_memory`). Guest filesystem still holds WebView profile dirs, markdown docs, large blob files.

**Alternatives considered:**

- **Room + sqlite-vec / room-vec** — familiar SQLite stack, but sqlite-vec is still **brute-force** vector scan on device (no ANN); more glue code for the same features.
- **Raw SQLite + manual cosine** — unacceptable for growing session logs.
- **kromus** — attractive hybrid BM25+vector in pure Kotlin, but **pre-1.0**; higher risk for a shipping TermFold feature.
- **Cloud vector DB** — violates offline-first.

**Backup:** Include ObjectBox store directory in `TermFoldBackup` (copy store files while app not writing, or use ObjectBox admin backup API if needed).

### 2. Automation metadata

**Choice:** Extend or mirror `FolderStore` pattern: `AutomationStore` in DataStore JSON for project/session list + `linkedFolderId`. Heavy data in ObjectBox + guest dirs, not in DataStore.

### 3. Home UI

**Choice:** Segment control at top of `Destination.Tabs` / `FoldersScreen` column when `NavTab.FOLDERS` selected; separate `AutomationProjectsScreen` composable. Tablet grid patterns match folders.

### 4. WebView isolation

**Choice:** `WebView.setDataDirectorySuffix("auto-" + sessionId)` per automation session WebView instance. Maintain map `sessionId -> PreviewSessionController` with own tab host. Only one automation session may be **foreground** for driver commands; background sessions keep profile on disk but release WebView when not visible (reload on return).

**Alternatives:** Single WebView + manual cookie jar (error-prone).

### 5. BrowserBridge binding

**Choice:** `BrowserBridge` gains active `automationSessionId: String?`. When non-null, HTTP driver routes to that session’s controller; coding preview uses `null` + legacy global behavior. MCP `termfold-browser` receives session id via env or bridge token scoped to session.

### 6. Linked folder

**Choice:** Store `linkedFolderId` on automation project. Resolve guest path via existing `Folder.path`. File tool policy layer: paths under linked guest root → read-only; paths under `~/.termfold/automation/...` → read-write. `Preview.open` `projectDir` = linked guest path if set, else session guest root.

### 7. Mandatory docs

**Choice:** Default templates on session create: `docs/prd.md`, `docs/requirements.md`, `docs/constraints.md`. `AutomationPromptAssembler` reads files from host-mapped session dir; inject block keyed for compress anchor in `AcpTimelineCodec` (same pattern as compressed summary anchor).

### 8. Embeddings + query path (v1)

**Choice:**

- **Inference:** **[ONNX Runtime Android](https://onnxruntime.ai/docs/get-started/with-android.html)** (`com.microsoft.onnxruntime:onnxruntime-android`) running a small bundled model (default: **all-MiniLM-L6-v2** int8 ONNX, ~25MB class) from APK assets. `EmbeddingService` (coroutine + single-thread executor) produces `float[]` of fixed dimension matching the ObjectBox `@HnswIndex` property.
- **Index:** On chunk upsert, write vector into ObjectBox; HNSW handles ANN. Query: embed `(userMessage + " " + currentHost)` → `box.query(MemoryChunk.class).nearestNeighbors(vector, maxResults).build()` with **Query filters** for `automationSessionId` (session search) or `automationSessionId == null` + `siteKey` (global nav).
- **Merge / upsert:** For global nav writes, run a low-`maxResults` neighbor query on the candidate embedding; if distance below threshold for same `siteKey`, update existing entity counts instead of inserting.

**Fallback:** If ONNX fails to load or embed, use ObjectBox `contains` / token-split matching on `searchText` plus recency ordering (degraded semantic, not a separate DB).

**Alternatives:** MediaPipe text embedders (heavier integration); API embeddings (offline violation).

### 9. Chunking

**Choice:** Log raw events always. Async roll-up: consecutive events on same URL → `episode` chunk with generated summary text → embed episode. Failures and assertions also become `highlight` chunks. Credentials: embed only `name + description + host`, never password field.

### 10. Global nav writes

**Choice:** `nav_memory_write` validates kind enum + regex/entropy secret detectors. Upsert: ObjectBox neighbor query + same `siteKey` threshold merge. Rows with high `fail_count` excluded from inject but remain searchable.

**No human gate** per product decision.

### 11. Credential tools

**Choice:** `session_secret_put`, `session_secret_get`, `session_memory_search`, `session_log_recent`, `nav_memory_search`, `nav_memory_write`. Register in `McpCatalog` for automation agent preset only (or Cursor with automation cwd).

Decrypt only in tool handler; redact in action log lines.

### 12. ACP preset

**Choice:** New `SessionPreset` or parallel automation session type with `acpAgentId = cursor` (or dedicated id), cwd = session guest `docs/`, MCP includes browser + automation memory servers.

### 13. Backup

**Choice:** Add paths to `TermFoldBackup` manifest: DataStore automation JSON, ObjectBox store directory under `filesDir`, guest `~/.termfold/automation/` trees via existing rootfs walk rules.

## Risks / Trade-offs

- **[Risk] Embedding model size/APK** → Use quantized mini model; download optional later.
- **[Risk] RAM with multiple WebViews** → Foreground one session; destroy WebView on navigate away, persist profile on disk.
- **[Risk] False global nav memories** → Merge similar rows; fail_count demotion; session search still authoritative for current run.
- **[Risk] Secret leak via chat** → Tool-gated decrypt; inject excludes secrets; log redaction.
- **[Risk] Overlap with unarchived `agent-preview-browser` main spec** → Archive `termfold-browser-improvements` before or with this change; deltas merge at archive time.

## Migration Plan

- New users: empty Automation segment.
- Existing users: no migration from coding folders; optional “Create automation project from folder” shortcut (nice-to-have in tasks, not required for launch).
- Rollback: hide segment via feature flag if needed; data remains on disk.

## Open Questions

- Single foreground automation session policy when user opens two sessions in split view (tablet): prefer explicit “active session” chip.
- If APK size is tight, optional **Play Feature Delivery** or first-run download for the ONNX asset (behavior unchanged; same model).
