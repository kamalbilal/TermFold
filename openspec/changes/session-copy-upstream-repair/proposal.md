# Proposal

## Why

Session workspace copies are created with `git clone` from the golden project tree. That copies branch config from the golden repo, including `branch.<name>.merge` pointing at `refs/heads/<name>` on `origin`. When `<name>` exists only as a **local** branch on the golden checkout (for example `main-dev` never pushed, or deleted on the remote), `git pull` in the copy fails with:

> Your configuration specifies to merge with the ref 'refs/heads/main-dev' from the remote, but no such ref was fetched.

Users hit this in shells under `~/.termfold/project-sessions/...` and assume TermFold broke the repo; the copy is valid but upstream tracking is wrong.

## What Changes

- After provisioning or switching a **managed session copy**, reconcile upstream tracking for the active branch: set `origin/<branch>` only when that remote-tracking ref exists after `git fetch origin --prune`; otherwise **unset** upstream so `git pull` fails clearly or no-ops until the user sets tracking.
- Apply the same reconciliation when opening an existing session copy whose branch is already checked out (lazy repair on first git status / changes load), so older copies self-heal without re-provisioning.
- Show sync UI (incoming/outgoing counts) only when upstream is valid; treat missing or stale upstream like “no upstream” today.
- Add JVM tests reproducing clone-from-golden + local-only branch + broken pull, then assert repair clears or fixes tracking.
- Document in user-facing error hint (optional string): local-only branches need `git push -u` from golden or manual upstream on the copy.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `git-workspace-sessions`: Session copy git remote/tracking behavior after clone and on use.
- `git-workspace-changes`: Upstream sync indicator when tracking is invalid or unset after repair.

## Impact

- `GitWorkspace.provisionWorkspace`, `switchToBranch` / post-switch hook
- `GitWorkspace.loadChangeSummary` (optional lazy repair)
- `GitChangesPane` / sync row (already hides when no upstream — verify after unset)
- Tests alongside `GitRemoteOnlyBranchImportTest`
- No change to golden workspace or ACP session storage paths
