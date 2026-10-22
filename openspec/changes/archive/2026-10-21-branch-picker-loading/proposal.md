# Proposal

## Why

Branch pickers (“Switch branch” and “Start a new copy from branch”) can sit blank with no feedback while git runs in PRoot, or stay empty after a failed or premature cache write. That reads as a broken UI and blocks Play-quality release polish.

## What Changes

- Add a **visible loading state** (spinner in the list area and/or title row) for all branch-picker surfaces: `BranchPickerDialog`, `WorkspaceBranchPickDialog`, and the branch section in `NewWorkspaceDialog`.
- Fix **stuck empty list**: do not treat a cached empty list as a successful load; track explicit load lifecycle (`idle` / `loading` / `success` / `error`) and always allow refresh to retry.
- Show **error + retry** when branch enumeration fails or times out, instead of an infinite blank list.
- Give branch lists in dialogs a **bounded height** so `LazyColumn` reliably renders rows inside `AlertDialog`.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `git-workspace-changes`: Branch picker loading, error, and non-stuck empty-state requirements.

## Impact

- `WorkspaceDialogs.kt` — shared branch-list loader UI, all three picker entry points.
- `BranchListCache.kt` — optional metadata (success vs failed) or stop caching failed/empty-first loads.
- `strings.xml` — loading and error copy for branch lists.
