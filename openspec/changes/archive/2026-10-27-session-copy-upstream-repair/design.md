# Design

## Context

See `proposal.md`. Session copies use `sessionCloneScript`: `git clone --no-hardlinks [-b <branch>] <goldenGuestPath> <tmp>`, then `switchToBranch`. Clone copies `.git/config` branch.* entries from golden. Golden may track `origin/main-dev` while only `main-dev` exists locally (stale merge config) or while `origin` in the copy still points at the golden path until rewritten.

## Goals / Non-Goals

**Goals:**

- Deterministic upstream after provision and branch switch on managed session paths only (`ProjectWorkspaces.isManagedSessionPath`).
- Lazy repair for legacy copies.
- Tests with real `git` on JVM (same pattern as `GitRemoteOnlyBranchImportTest`).

**Non-Goals:**

- Auto-push local branches to origin.
- Changing how golden clone/remotes are configured.
- Fixing user-run `git remote` edits after repair.

## Decisions

**1. Repair function**

- **Choice:** `reconcileBranchUpstream(context, guestPath, branchName)` in `GitWorkspace`:
  1. Skip if not a git repo or not a managed session path.
  2. `git fetch origin --prune` (ignore failure if no origin).
  3. If `refs/remotes/origin/<branch>` verifies: `branch --set-upstream-to=origin/<branch> <branch>`.
  4. Else: `branch --unset-upstream` for `<branch>` (only if current branch matches).
- **Rationale:** Matches git’s own rules; pull then either works or git says no tracking branch.

**2. Call sites**

- **Choice:** End of `provisionWorkspace` after `switchToBranch`; end of `switchToBranch` on success; optional once per session in `loadChangeSummary` guarded by a simple in-memory or prefs flag per `guestPath` to avoid fetch on every refresh.
- **Alternative:** Only on provision — rejected; leaves broken existing copies.

**3. Remote URL**

- **Choice:** Do not rewrite `remote.origin.url` in this change; clone-from-golden may leave origin as file path. If pull still fails for URL reasons, separate change. Upstream unset fixes the reported error for missing `main-dev` on remote.
- **Note:** If golden’s origin is correct HTTPS URL, clone inherits it; fetch uses real remote.

## Risks / Trade-offs

- **[Risk] Extra `git fetch` on changes pane open** → Mitigation: lazy repair once per app session per guest path.
- **[Risk] User wanted tracking to missing branch** → Mitigation: they can `git branch -u` manually; local commits still on branch.

## Migration Plan

Ship in app update; repair on use. No data migration.

## Open Questions

None blocking — whether to show a one-line Notice in Changes pane when upstream was cleared can be decided in implementation.
