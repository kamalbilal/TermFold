# Proposal

## Why

Per-session git workspaces are full clones stored under `golden/.termfold/sessions/`. Commands run from the golden project root (`npm run lint`, tests, search tools) can traverse that tree even when `.gitignore` hides it from git. That doubles work, skews tooling, and breaks the expectation that the project directory is a single checkout. Session copies should live outside the golden tree so any command scoped to the project only sees one codebase.

## What Changes

- Store new session workspace directories under a TermFold-managed guest root (e.g. `/root/.termfold/project-sessions/<project-key>/`) instead of `<golden>/.termfold/sessions/`.
- Derive a stable `project-key` from the golden guest path so paths stay deterministic across app restarts.
- Keep recognizing legacy in-repo session paths for existing `WorkspaceRecord` entries until migrated or deleted.
- **Optional one-time migration**: move on-disk trees from in-repo `.termfold/sessions/<id>` to the external root when safe (same session id, update stored `guestPath`).
- Continue ensuring `.termfold/` is ignored in golden and session `.gitignore` when present (for any leftover metadata or legacy layouts).
- Add unit tests for path resolution, managed-session detection, and migration/idempotency rules.

## Capabilities

### New Capabilities

_None — behavior stays under git workspace sessions._

### Modified Capabilities

- `git-workspace-sessions`: Session storage location, isolation from golden project tree, legacy path compatibility, and updated purpose text.

## Impact

- `ProjectWorkspaces` (`SESSIONS_DIR`, `sessionGuestPath`, `sessionsRoot`, `isManagedSessionPath`)
- `GitWorkspace` provisioning and delete paths
- `WorkspaceStore` / `WorkspaceRecord` persisted `guestPath` values
- `WorkspaceDialogs` and any UI that composes session paths
- Tests: `ProjectWorkspacesTest`, `WorkspaceRecordTest`, clone/provision tests
- OpenSpec main spec `openspec/specs/git-workspace-sessions/spec.md` on archive
- **Not breaking** for users who only use golden workspace; **behavior change** for where new session disks are created (old in-repo folders may remain until migration or manual cleanup)
