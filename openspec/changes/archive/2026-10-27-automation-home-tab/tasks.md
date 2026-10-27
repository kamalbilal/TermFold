# Tasks

## 1. Automation metadata and storage

- [x] 1.1 Add `AutomationProject` / `AutomationSession` models and `AutomationStore` (DataStore) with CRUD; verify create/list survives app restart
- [x] 1.2 Implement guest host path layout `~/.termfold/automation/<projectId>/sessions/<sessionId>/` with `docs/` templates; verify directories exist on session create
- [x] 1.3 Add optional `linkedFolderId` with link/change/unlink APIs and broken-link resolution when folder missing; verify unlink clears link without deleting session dir

## 2. Home UI

- [x] 2.1 Add Projects | Automation segment on home (`Destination.Tabs` + FOLDERS); verify switching lists without changing bottom nav
- [x] 2.2 Build automation project list and project detail (sessions list + link UI); verify create project/session navigation end-to-end
- [x] 2.3 Add strings/resources for Automation tab; verify segment labels on phone and tablet layouts

## 3. Session screen and ACP

- [x] 3.1 Add automation session destination (chat + preview entry) distinct from coding terminal; verify opens ACP with automation MCP catalog
- [x] 3.2 Implement `AutomationPromptAssembler` + timeline anchor for mandatory docs and post-compress reinject; verify constraints file content appears on wire after compress (unit test or logged prompt fixture)
- [x] 3.3 Enforce read-only file tool policy for linked folder guest paths; verify write attempt returns documented error and read succeeds

## 4. Browser profile and bridge binding

- [x] 4.1 Introduce per-session preview controller with `WebView.setDataDirectorySuffix`; verify two sessions keep separate cookies for same origin
- [x] 4.2 Scope `BrowserBridge` / driver to active `automationSessionId`; verify snapshot URL matches foreground session only
- [x] 4.3 Wire `Preview.projectDir` to linked folder guest path or session root per link state; verify `files.termfold` URL resolves correctly when linked vs unlinked

## 5. Memory database and embeddings (ObjectBox + ONNX)

- [x] 5.1 Add ObjectBox Gradle plugin and dependencies; define `@Entity` types (`MemoryChunk` with `@HnswIndex`, `SessionEvent`, `SessionSecret`) and open store; verify put/query in JVM/Android unit test
- [x] 5.2 Add `onnxruntime-android` and bundle quantized all-MiniLM-L6-v2 (or equivalent) ONNX; implement `EmbeddingService` with fixed vector dimension matching ObjectBox index; verify deterministic embed of fixture string
- [x] 5.3 Wire browser driver → `SessionEvent` append and async episode/highlight chunk upsert with embeddings; verify HNSW `nearestNeighbors` returns relevant chunk above irrelevant fixture for same session
- [x] 5.4 Implement text `contains`/recency fallback when ONNX unavailable; verify search still returns results with embedding disabled
- [x] 5.5 Add global `nav_memory_write` validation (reject secrets) and neighbor-threshold merge on same `siteKey`; verify password-like write fails and duplicate pitfall updates one row

## 6. MCP tools and prompt inject

- [x] 6.1 Register session/global memory and secret MCP tools in `McpCatalog` for automation sessions; verify `tools/list` includes them when opening automation chat
- [x] 6.2 Implement bounded per-prompt inject (recent events + top semantic chunks, no decrypted secrets); verify inject size caps in unit test
- [x] 6.3 Redact secrets in session event log lines; verify log row never contains raw password from `session_secret_put`

## 7. Backup and documentation

- [x] 7.1 Include automation DataStore, session dirs, and ObjectBox store directory in `TermFoldBackup` restore round-trip; verify automation project and memory search work after restore on same device
- [x] 7.2 Add bundled automation skill asset + setup tip; verify skill text in debug APK assets matches tool names
- [x] 7.3 Add run log UI tab (filter by URL/error) reading session events; verify list updates after browser actions

## 8. Integration

- [x] 8.1 End-to-end manual script: create automation project, link folder, run browser login in session A, confirm session B not logged in; document in PR test plan
- [x] 8.2 Run `./gradlew :app:testDebugUnitTest` for new automation/memory tests and fix failures

## Workflow follow-up

- Archive `termfold-browser-improvements` (if not already) so `agent-preview-browser` main spec exists before archiving this change.
- Run `openspec validate automation-home-tab --strict` and archive when implementation review passes.
