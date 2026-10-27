# Tasks

## 1. Data model and store

- [x] 1.1 Add `PmSession` types (mode, folderId, viewBranch, workspaceId, guestPath, competitors, flags) and `PmSessionStore` with list/create/update/delete; unit tests for serialization
- [x] 1.2 Persist session brief markdown files under app files dir per session id (vision, constraints, competitors name+URL, release brief)
- [x] 1.3 Add per-session PM memory store (separate BoxStore or equivalent, keyed by session id); unit tests that session A cannot read session B

## 2. Git-at-ref research (Discovery)

- [x] 2.1 Add `PmRepoResearch` helpers on top of `GitWorkspace.runGit`: `rev-parse`, `show ref:path`, bounded `log`, optional `diff`; no checkout on golden path
- [x] 2.2 Unit tests for path quoting and ref validation errors

## 3. Branch picker read-only

- [x] 3.1 Extend `BranchPickerDialog` with `readOnly` mode: on select call `onViewBranch` without dirty/stash/checkout flows
- [x] 3.2 Wire Discovery session header chip **Viewing: {branch}** and persist `viewBranch` on session

## 4. Release mode lifecycle

- [x] 4.1 Implement Release session start: folder + branch → `GitWorkspace.provisionWorkspace` → bind ACP guest path (mirror PR Reviewer patterns)
- [x] 4.2 Add **Done** on PM Release screen: confirm if dirty → delete copy → keep chat; tests for copy removal

## 5. Workflows UI

- [x] 5.1 Add `WorkflowsSection.PRODUCT_MANAGER`, strings, icons, `MainActivity` destinations
- [x] 5.2 PM session list screen + **New session** flow (mode picker → folder → branch step per mode)
- [x] 5.3 PM chat screen shell (header, research log collapse, competitors editor, include-competitive toggle)
- [x] 5.4 Header overflow **Session memory** → PM memory manager (search, kind filter, semantic search optional, list/read/add/edit/delete with confirm); reuse Automation manager UX, bind PM store not `automation_memory`

## 6. Research orchestration

- [x] 6.1 Implement research pack builder (repo facts with path@ref citations, byte caps, progress callbacks)
- [x] 6.2 Implement bounded competitive fetch (allowlisted domains + user URLs) and research log entries
- [x] 6.3 **Research and decide** / first-message trigger + **Refresh research**; cache pack on session; invalidate pack when `viewBranch` changes
- [x] 6.4 After each research/decision pass, append system chunks (pack summary + memo) to the session memory DB

## 7. ACP integration

- [x] 7.1 Wire Cursor ACP with PM profile: system prompt, git-at-ref reads, fetch bounds, deny file writes on Discovery golden, stub guest path after Release Done
- [x] 7.2 Render decision memo sections from structured JSON when present, else show markdown; do not fail the turn if schema is missing
- [x] 7.3 Add bundled PM skill (guest skill path) including citation rules and `pm_memory_*` tools
- [x] 7.4 Expose `pm_memory_search` / `pm_memory_write` / `pm_memory_get`; inject top-K chunks each turn; auto-write findings do not replace agent writes

## 8. Files panel integration

- [x] 8.1 When active session is PM Discovery on golden, Files roots at golden and shows view branch + read-only hint in header

## 9. Verification

- [ ] 9.1 Manual: Discovery — change view branch, confirm golden branch unchanged via shell; ask PM question, see citations; open header Session memory, search/filter, delete a chunk, confirm agent no longer recalls it
- [ ] 9.2 Manual: Release — provision copy, Done deletes copy, chat **and memory** persist; competitive toggle with 1–2 homepage URLs
- [x] 9.3 Run unit tests; assemble debug APK

## Workflow follow-up

- Archive change after implementation and sync deltas into main specs (`product-manager` new main spec on archive).
