# Tasks

## 1. Load state and cache fix

- [x] 1.1 Add `BranchListLoadState` and `rememberBranchListLoad` (or equivalent) with explicit loading/success/error; verify failed load does not write to `BranchListCache`.
- [x] 1.2 Fix cache-hit logic so empty or failed results never skip a network/git reload on open; add unit test for cache + load decision if testable without device.

## 2. Shared picker UI

- [x] 2.1 Add `BranchPickerListBody` with spinner, error+retry, empty message, and `LazyColumn` `heightIn(max = ~320.dp)`; verify list rows visible in `AlertDialog` on device.
- [x] 2.2 Wire load state into `BranchPickerDialog`, `WorkspaceBranchPickDialog`, and `NewWorkspaceDialog` branch section; verify all three show loader on cold open.

## 3. Strings and timeout

- [x] 3.1 Add `workspace_branch_loading`, `workspace_branch_load_error`, `workspace_branch_empty` strings; verify error path shows readable message.
- [x] 3.2 Wrap branch list git calls with timeout in load helper; verify timeout shows error state and refresh recovers.

## Workflow follow-up

- Run `/opsx-apply branch-picker-loading` to implement.
- Manual QA on TECNO: Switch branch + Start new copy from branch — cold open, refresh, airplane mode failure, recovery.
