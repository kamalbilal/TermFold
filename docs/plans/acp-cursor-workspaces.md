# ACP Cursor — isolated workspaces, branches, and session copies

Status: **planned** (agreed in product interview, not implemented).

Audience: TermFold contributors implementing git-aware Cursor ACP on the Debian guest.

## Problem

Users run multiple Cursor ACP sessions on the same project. Today every session shares one guest directory (`~/projects/<name>`). Parallel work collides: same branch, same uncommitted files, same `node_modules` mutations.

Users also need desktop-like branch switching in the chat UI without leaving the agent screen.

## Goals

1. **Optional isolated workspace** per Cursor ACP instance when the user explicitly asks (not automatic on every new chat).
2. **Clean git state** in each new workspace: last commit on a chosen branch, no uncommitted files, no stash from the golden tree.
3. **`node_modules` included** in the copy so new workspaces do not run `npm install` on the phone.
4. **Branch control** in the input bar: current branch, searchable list, recent branches first, safe checkout with stash or force.
5. **Copy progress** during workspace creation: **0–100%** based on bytes copied, with a visible **Cancel** control.
6. **Cancel mid-copy** deletes the partial workspace directory and drops any provisional registry entry so no orphan copies remain.

## Non-goals (v1)

- Git worktrees as the isolation mechanism (same branch cannot exist in two worktrees; user chose full tree copies for same-branch parallelism).
- Fast reflink / hardlink / `cp -al` cloning on TermFold storage (Android app data + PRoot: `link(2)` denied; uv/Bun already force plain copies — see `docs/SHELL-NOTES.md`).
- Automatic new workspace on every new ACP chat session.
- Carrying uncommitted or stashed work from the golden project into a new workspace.

## Terminology

| Term | Meaning |
|------|---------|
| **Golden project** | The user’s primary project directory under `/root/projects/<name>`. May have local edits; used as the source for `node_modules` and as the `git` remote for cloning. |
| **Workspace** | A guest directory used as `cwd` for one or more ACP sessions. Default: the golden path. Optional: a session copy under `.termfold/sessions/<id>/`. |
| **ACP session** | Chat transcript + agent process identity (`sessionId`). Multiple sessions can share one workspace, or each can use its own workspace after manual creation. |
| **Instance (UI)** | A workspace copy the user created via **New workspace**, listed in recent instances with its current branch. |

## User flows

### New workspace (manual)

1. User opens **New workspace** from the Cursor ACP project UI (exact entry point TBD; not tied to “New session” chat reset).
2. Dialog top: **Recent instances** — last N workspace copies for this golden project, each showing guest path label and **current checked-out branch** (live `git` read). Tap row → open that workspace (existing or new ACP session as today’s navigation allows).
3. Dialog body: **Start a new copy from branch** — search + list (recent branches first). User picks a branch.
4. App provisions workspace:
   - Create `/<golden>/.termfold/sessions/<uuid>/` (exact layout below).
   - Populate from **committed** tree at `origin` or local branch tip (see Provisioning).
   - Copy `node_modules` from golden (physical copy, with progress).
   - Show blocking progress **0–100%** with **Cancel** until done or cancelled.
5. User continues in ACP with `cwd` = new workspace path.

If the user taps **Cancel** during step 4, stop the copy job, **recursively delete** the target session directory (only if it stays under `/<golden>/.termfold/sessions/`), remove any **incomplete** `WorkspaceRecord`, and return to the new-workspace dialog with no navigation change.

### Branch chip (every workspace)

1. Bottom input row: **`[<branch>] [model] [textarea…]`** — branch label immediately **left of** the model name.
2. Tap branch → modal: search, **recent branches first**, then full list.
3. On select:
   - If working tree clean → `git switch` / checkout.
   - If dirty → prompt: **Stash and switch** or **Force checkout** (discard local changes; destructive action clearly labeled).

Recent branches: persisted per workspace (guest prefs or app storage keyed by workspace path).

### Default behavior unchanged

- New ACP **chat** session (“New session” in session history UI) does **not** clone the repo unless the user created a new workspace and navigated there.
- Golden project sessions keep using the golden path until the user switches workspace.

## Provisioning algorithm (v1)

**Requirements:** New tree must not contain golden’s uncommitted files or stash. Must include `node_modules` from golden.

Recommended sequence (implementer may adjust if guest `git` version differs):

1. **Measure total bytes** for progress (see Progress).
2. `git clone --local <golden> <sessionDir>` (or equivalent) then `git switch <branch>` **or** clone with `-b <branch>` if branch exists locally.
3. `git reset --hard` at branch tip; `git clean -fd` for untracked (do not delete `node_modules` if already copied — order matters).
4. **Stash:** ensure new repo has no stash (`git stash clear` if clone copied stash refs; verify clone behavior).
5. **Copy `node_modules`** from golden into session dir if not present or if clone excluded it (`rsync -a` or recursive file copy with byte progress). Skip `.git` duplication if step 2 already created `.git`.

Alternative: `git archive` at branch tip for tracked files + separate `node_modules` tree copy. Prefer one approach in code; document in checklist.

**Golden dirty tree:** Only **committed** files appear in the new workspace. Golden’s working copy is untouched.

## Storage and platform constraints

Verified on target device class (TECNO CL8, Android 15):

- App data on **f2fs**; ample free space is typical but each full copy is large.
- **No reliable fast clone:** hardlinks become full copies (`libtermfold-compat`); reflink not dependable (`UV_LINK_MODE=copy` in `AcpInstaller.kt`).

Plan for **slow, honest full copy** with progress UI.

## Data model (proposed)

```
Golden:  /root/projects/TermFold/
Session: /root/projects/TermFold/.termfold/sessions/<uuid>/
Meta:    app-side registry per golden project id
```

**`WorkspaceRecord`** (Kotlin, persisted):

| Field | Type | Notes |
|-------|------|--------|
| `id` | UUID | Session workspace id |
| `goldenProjectId` | string | TermFold project / folder id |
| `guestPath` | string | Absolute guest path to workspace root |
| `createdAt` | instant | |
| `sourceBranch` | string | Branch chosen at creation |
| `lastKnownBranch` | string | Updated after successful checkout |
| `label` | string? | Optional user-facing short name |

**`BranchPrefs`** per `guestPath`:

- `recentBranchNames: List<String>` (cap e.g. 10)

**Navigation and session keys (decided):**

Include **workspace id** in routing and in every key that must not collide across copies of the same project.

| Concept | Key / route shape |
|---------|-------------------|
| Terminal / ACP session | `folderId` + **`workspaceId`** + `sessionId` (extend `TerminalHost.sessionKey` or equivalent) |
| `AcpSessions` map | Same composite key; `AcpClient.workspaceGuestDir` resolved from `workspaceId` via registry |
| Drafts, bells, presence | Include `workspaceId` in prefs keys (migrate golden as a stable id e.g. `golden` or the project’s default workspace uuid) |

Opening a chat always implies **which workspace folder** `cwd` uses. Do not rely on `sessionId` alone to infer path.

**ACP wiring:**

- `AcpClient.workspaceGuestDir` drives `cwd` and MCP paths from the workspace record for `workspaceId`.
- Golden project uses the default workspace record (guest path = project root).

## UI surfaces

| Surface | Change |
|---------|--------|
| Agent session bottom bar | Branch chip left of model |
| Branch picker modal | Search, recent, stash/force |
| New workspace dialog | Recent instances (top) + branch picker + create |
| Progress overlay / dialog | Percent 0–100, **Cancel** button (always visible while copying) |

Strings: `app/src/main/res/values/strings.xml`.

## Progress calculation

```
totalBytes = size(golden node_modules) + size(git objects to duplicate) + size(tracked tree at HEAD)
           — implement via single pre-scan or staged totals
copiedBytes += per-file bytes during copy
percent = (copiedBytes * 100) / totalBytes.coerceAtLeast(1)
```

Emit on main thread for Compose UI. Run copy on background dispatcher; guest commands via existing `ShellRuntime` / proot patterns.

## Cancel mid-copy (decided)

1. Provision runs in a **cancellable** coroutine (`Job`); copy loop checks cancellation between files (or chunks).
2. UI **Cancel** calls `job.cancel()` and awaits cleanup on a background dispatcher.
3. **Cleanup:** delete `dest` only after validating it is under `golden/.termfold/sessions/`; remove incomplete `WorkspaceRecord`; do not touch golden tree.
4. UI returns to new-workspace flow; show a short message that creation was cancelled (optional).
5. If delete fails, show error and leave path in registry as **failed** so user can retry delete from a future “manage workspaces” surface (v1 may log + toast only).

## Security and safety

- Force checkout must use strong confirmation (second tap or explicit “discard changes”).
- Workspace deletion (future): only under `.termfold/sessions/` with path validation.
- No path traversal out of project root.

## Testing (when implemented)

- Unit: branch recent list ordering; path validation; progress math.
- Guest integration (device or CI if available): clone + clean tree has no golden dirty file; `node_modules` present; two workspaces same branch independent files.
- Manual: progress bar moves on large project; recent instances show correct branch after switch.
- Manual: Cancel mid-copy removes session folder; golden unchanged; no ghost row in recent instances.

## Related code today

| Area | File |
|------|------|
| ACP `cwd` | `AcpClient.kt` |
| Session UI / model row | `AgentSessionScreen.kt` |
| Projects root | `Projects.kt` |
| Filesystem notes | `docs/SHELL-NOTES.md` |
| Past ACP chats | `listPastSessions` in session history UI (distinct from workspace copies) |

## Open decisions (minor)

| Item | Status |
|------|--------|
| Navigation keys include `workspaceId` | **Decided:** yes (Option B) |
| Cancel mid-copy | **Decided:** stop job, delete partial dir, drop incomplete record |
| Max recent instances shown | 5 |
| Workspace delete UI | v1.1 |
| `git clone --local` vs archive | Implementer chooses; must meet clean-tree requirement |

## Document map

- **This file** — product + technical plan.
- **`acp-cursor-workspaces-checklist.md`** — implementation checklist and throughput checkpoint.
- **`acp-cursor-workspaces-throughput.md`** — parallel workstreams for implementation phase.
