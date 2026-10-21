# Tasks

## 1. Live composer branch chip

- [x] 1.1 Add host `.git/HEAD` branch reader in `GitWorkspace` with unit tests for `ref:` and detached SHA; verify `./gradlew :app:testDebugUnitTest --tests "*GitWorkspace*"` passes for new cases.
- [x] 1.2 Wire `AgentSessionScreen` to refresh `branchLabel` on HEAD read when `guestDir` changes, on `agentBusy` false edge, and on a 3s visible poll; verify manually by `git checkout` in aux terminal without opening picker and chip updates.

## 2. Branch picker search, cache, refresh

- [x] 2.1 Add in-memory `BranchListCache` and refresh affordance in `BranchPickerDialog` title row; verify reopening picker shows list instantly without spinner on second open.
- [x] 2.2 Fuzzy-rank branches with recents boost (reuse `ComposerFuzzyMatch` or shared helper); verify typing partial name surfaces expected branch at top.
- [x] 2.3 Add refresh strings/icons in `strings.xml`; verify refresh button triggers reload and updates list after remote/local branch change.

## 3. Checkout performance and feedback

- [x] 3.1 Skip `git fetch` in `switchToBranch` when local `refs/heads/<name>` exists; add unit or instrumentation test for fast-path branch selection logic.
- [x] 3.2 Add `checkingOutBranch` UI state in picker (spinner, disable duplicate taps); verify failed checkout keeps dialog open and chip unchanged.
- [x] 3.3 Optional: batch fast-path checkout into one `runCapture` script; verify single switch still succeeds on device for local branch.

## 4. Aux terminal tabs — confirm + header menu

- [x] 4.1 Extract `AuxTerminalTabsStore` (or equivalent) exposing tab list and close for a session key; verify dialog behavior unchanged except confirm gate.
- [x] 4.2 Add confirmation dialog before close from in-dialog tab chips; verify dismiss leaves tab running.
- [x] 4.3 Extend `SessionHeaderOverflowMenu` with per-tab rows (open + close with same confirm) under Terminal; verify selecting row opens dialog on that tab and confirmed close kills session via `TerminalHost.closeSession`.
- [x] 4.4 Add confirm title/body strings; verify last remaining tab has no close affordance.
- [x] 4.5 Register `TerminalHost.onTap` in `AgentAuxTerminalDialog` (same pattern as `TerminalScreen`) so tap after IME hide calls `requestTerminalFocus`; verify on device: open aux terminal, dismiss keyboard, tap terminal, keyboard returns without new tab.

## Workflow follow-up

- Run `/opsx-apply cursor-workspace-git-ux` to implement.
- Archive with `/opsx-archive cursor-workspace-git-ux` after manual QA and sync `openspec/specs/git-workspace-changes/spec.md` and new `agent-aux-terminals` spec.
