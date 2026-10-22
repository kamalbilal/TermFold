# Design

## Context

See `proposal.md`. Branch lists load via `GitWorkspace.listBranches` / `listBranchesForPicker` (PRoot). Recent work added `BranchListCache` and refresh icons but no loading indicator.

**Likely stuck-empty bug** (code review):

```kotlin
BranchListCache.get(cacheKey)?.let { branches = cached }  // [] is valid
if (branches == null || force) { load... }               // skips load when cached []
```

A failed or in-progress empty cache entry prevents reload until refresh. While `branches == null`, UI renders `ordered` as empty with **no spinner**.

`LazyColumn` inside `AlertDialog` `text` without `heightIn` can collapse to zero height on some devices, also appearing “stuck empty.”

## Goals / Non-Goals

**Goals:**

- One shared load helper for golden and session-catalog keys.
- Loading, success, error UI in every branch picker modal.
- Cache only **successful** non-stale results; failed loads do not block retry.
- Bounded list height (~280–360dp) consistent across dialogs.

**Non-Goals:**

- Speeding up git itself (separate perf work).
- Replacing AlertDialog with full-screen picker (unless height fix insufficient).

## Decisions

### 1. `BranchListLoadState` sealed class

```kotlin
sealed class BranchListLoadState {
  data object Loading : BranchListLoadState()
  data class Ready(val branches: List<String>) : BranchListLoadState()
  data class Error(val message: String) : BranchListLoadState()
}
```

`rememberBranchListLoad(cacheKey, refreshNonce, loader: suspend () -> List<String>): BranchListLoadState`

- On start: if valid cache hit → `Ready` immediately, optional background refresh.
- Else → `Loading` until IO completes.
- On failure: `Error`, **do not** `put` empty list to cache.
- `refreshNonce++` always forces `Loading` then reload.

### 2. Cache shape

Change `BranchListCache` to store `CachedBranchList(branches, loadedAt)` or only `put` when `branches.isNotEmpty()` OR always put on explicit success after git exit 0. **Never** cache on exception.

Invalidate on checkout success (already done in switch picker).

### 3. UI component

`BranchPickerListBody(state, ordered, onPick, ...)`:

- `Loading` → centered `CircularProgressIndicator` + short label
- `Error` → message + “Retry” (same as refresh)
- `Ready` + empty → “No branches found” + refresh hint
- `Ready` + items → `LazyColumn(Modifier.heightIn(max = 320.dp))`

Wire into `BranchSearchList` and `BranchPickerDialog` text column.

### 4. Timeout

Wrap list git calls with `withTimeout(120_000)` (or match `runCapture`); on timeout show error state. Prevents infinite loading without feedback.

## Risks / Trade-offs

- **[Background refresh]** Showing cache then updating list may flicker — acceptable vs blank stall.
- **[120s timeout]** Very large repos might need longer; user can tap refresh.

## Open Questions

None blocking.
