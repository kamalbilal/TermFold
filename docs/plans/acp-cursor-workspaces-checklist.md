# ACP Cursor workspaces — implementation checklist

Use with **`acp-cursor-workspaces.md`**. Check boxes when done.

## Phase 0 — Readiness

- [x] Read `docs/SHELL-NOTES.md` § `link(2)` and copy implications.
- [x] Trace `AgentSessionScreen` → `guestDir` → `AcpClient.workspaceGuestDir`.
- [x] Extend `TerminalHost.sessionKey` (and callers) to **`folderId/workspaceId/sessionId`**; golden uses default `workspaceId`.

## Phase 1 — Domain and persistence (blocking)

- [x] Add `WorkspaceRecord` + persistence (DataStore or existing prefs pattern).
- [x] Add `BranchRecentsStore` keyed by guest workspace path.
- [x] API: list workspaces for golden project, get/set active workspace for navigation context.
- [x] Path helper: `ProjectWorkspaces.sessionDir(goldenGuestPath, id)` under `.termfold/sessions/<uuid>/`.
- [x] Unit tests for path safety and record serialization.

## Phase 2 — Git guest operations (blocking for UI)

- [x] `GitWorkspace` (or shell module): list branches (local + remote-tracking), current branch.
- [x] `checkoutBranch(path, branch, Stash | Force | FailIfDirty)`.
- [x] `provisionWorkspace(..., onProgress, cancelSignal)`:
  - [x] Committed-only tree at branch tip.
  - [x] No stash in result.
  - [x] `node_modules` copied from golden with byte progress.
  - [x] Cooperative cancel between files; on cancel throw `CancellationException` after cleanup hook.
- [x] `deletePartialWorkspace(dest)` with path guard under `.termfold/sessions/`.
- [ ] Integration test or scripted guest test for two workspaces, same branch, independent file edit. (deferred to Phase 7 on device)

## Phase 3 — Copy progress UI

- [x] Pre-scan total bytes (or rolling total) before copy starts.
- [x] Modal / full-screen progress with **integer 0–100%** and **Cancel** button (enabled for whole copy).
- [x] Cancel → cancel job → delete partial dir → remove incomplete `WorkspaceRecord` → dismiss to new-workspace dialog.
- [x] Error surface: failed copy uses same delete partial dir policy where safe.
- [x] Strings for preparing workspace, cancelled, failure, retry.

## Phase 4 — New workspace dialog

- [x] Entry action **New workspace** (not conflated with ACP **New session** chat reset).
- [x] Top section: recent instances (path label + **current branch**), tap to open workspace.
- [x] Branch picker for new copy (search, recent branches).
- [x] Create triggers Phase 2 + 3, then navigate with `workspaceId` + `guestPath`.

## Phase 5 — Branch chip (bottom bar)

- [x] Layout: branch chip **left of** model name on bottom textarea row.
- [x] Branch modal (search, recent first).
- [x] Dirty-tree dialog: stash + switch vs force checkout.
- [x] Update `lastKnownBranch` + branch recents on success.
- [x] Refresh chip when checkout completes.

## Phase 6 — Wire ACP

- [x] Navigation `Destination` carries `workspaceId`; resolve `guestPath` from registry.
- [x] `AcpSessions` / drafts / notifications use composite key including `workspaceId`.
- [x] Pass workspace `guestPath` into `AcpClient` construction.
- [x] MCP catalog + `mcp.json` resolution still relative to workspace `cwd`.
- [x] Session reload / MCP apply unchanged except `cwd` correctness.
- [x] `SessionRegistry` closes and busy-checks all `folderId/workspaceId/sessionId` keys (folder delete, session delete, session list spinner).

## Phase 7 — Verify (device)

- [ ] `assembleDebug` or release install on device.
- [ ] Golden project with dirty file → new workspace → dirty file absent.
- [ ] Two workspaces, same branch, edit different files → no cross-visible change.
- [ ] Progress bar reaches 100% on non-trivial `node_modules`.
- [ ] Cancel during copy removes `.termfold/sessions/<id>` and does not list workspace in recents.
- [ ] Recent instances update after branch switch.

## Phase 8 — Ship prep

- [x] `deslop` pass on touched Kotlin (workspace paths; no fake progress jumps).
- [x] No stray comments (poteto / no-comments).
- [ ] Update user-facing docs if README mentions ACP (optional).

---

## Throughput checkpoint (feature playbook)

| Dimension | Plan |
|-----------|------|
| **Blocking first steps** | Phase 1 persistence + Phase 2 `provisionWorkspace` before UI fan-out. |
| **Independent workstreams** | Phase 3 progress UI ∥ Phase 4 dialog layout after Phase 2 API exists; Phase 5 branch chip ∥ Phase 4 after git ops shared. |
| **Shared mutable state** | Single writer for filesystem copy; UI reads progress via `StateFlow`. Golden tree never mutated by provision (read-only source for copy). |
| **Smallest safe decomposition** | One `GitWorkspace` module owns all guest git + copy; UI only calls suspend APIs. |

---

## Definition of done

- User can manually create an isolated workspace from a chosen branch with percent progress.
- New workspace has clean commit at tip + `node_modules`, no stash/uncommitted from golden.
- User sees branch on bottom bar and can switch with stash/force.
- New workspace dialog shows recent instances with live branch names.
- User can cancel copy; partial workspace is removed.
- Navigation and ACP keys include `workspaceId`.
- Verified on a physical Android device with TermFold’s Linux guest.
