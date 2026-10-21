# Design

## Context

Cursor sessions show a branch chip on the composer (`AgentSessionScreen`) backed by `branchLabel`, loaded once per `guestDir`. Git operations run through `GitWorkspace.runGit` → `AcpInstaller.runCapture` (PRoot per invocation). `BranchPickerDialog` reloads branches on every open and uses substring filter; checkout always runs `git fetch origin --prune` in `switchToBranch`. Aux terminal tabs live in `auxTabsByParent` inside `AgentAuxTerminals.kt`; the header menu only opens the full dialog and in-dialog close skips confirmation.

See `proposal.md` for motivation and scope.

## Goals / Non-Goals

**Goals:**

- Near-real-time composer branch label with low overhead (prefer host filesystem over PRoot polling).
- Picker UX on par with quick-open: fuzzy rank, cached lists, explicit refresh.
- Faster common case checkout (local branch) with clear in-flight UI.
- Aux tab close guarded by confirmation from dialog chips and header menu rows.
- Reliable soft-keyboard refocus when tapping the aux terminal after IME dismiss.

**Non-Goals:**

- Changing golden/session workspace provisioning or git changes pane behavior.
- Remote-only branch discovery without fetch (refresh still uses full git list).
- Replacing PRoot or running host-native git binaries outside the guest.

## Decisions

### 1. Live branch: read `.git/HEAD` on host

**Choice:** Add `GitWorkspace.readBranchFromHost(context, guestPath)` parsing `Projects.hostPath(...)/.git/HEAD` (`ref: refs/heads/<name>` or detached SHA).

**Why:** `rev-parse` via PRoot is too heavy for periodic sync.

**Also refresh when:**

- `state.agentBusy` transitions true → false (agent may have run git).
- User returns to session / `Lifecycle.Event.ON_RESUME` on the hosting activity (optional, cheap).
- Optional light poll (e.g. 3s) only while Cursor session composable is visible; compare string to `branchLabel` and update on change.

**Alternatives:** FileObserver on HEAD (more complex on Android); poll `currentBranch()` only (rejected: cost).

### 2. Branch list cache

**Choice:** Process-wide `BranchListCache` keyed by `checkoutGuestPath + "|" + catalogGuestPath`, holding `List<String>` and `loadedAt`. Dialog reads cache synchronously on compose; `LaunchedEffect` loads only if cache miss unless user tapped refresh.

**Refresh control:** Icon button in dialog title row; sets `refreshing` flag, invalidates cache entry, reruns `listBranchesForPicker`.

**Fuzzy rank:** Reuse `ComposerFuzzyMatch.rank(query, branches)` (or extract shared `fuzzyRankStrings`). Merge with `BranchRecentsStore`: boost score for recent names, then sort descending.

### 3. Checkout performance and UX

**Choice:** Split `switchToBranch`:

- **Fast path:** If `refs/heads/<local>` exists on session copy, run checkout attempts only (no fetch).
- **Slow path:** When local and remote refs missing, run fetch + import fetch commands (current behavior).

**Batching (optional follow-up in same change):** Single `runCapture` script chaining `cd && git ...` for the fast path to cut PRoot spawns from N to 1.

**UI:** `checkingOutBranch: String?` state; disable list clicks; show `CircularProgressIndicator` in dialog title or on row; on success call `onSwitched` and dismiss; on failure show `checkoutError` and leave chip unchanged.

### 4. Aux terminals: shared controller + confirm

**Choice:** Extract tab list access from `AgentAuxTerminals.kt` into a small `AuxTerminalTabsStore` (same map keys: `SessionKeys.compose(folderId, workspaceId, acpSessionId)`):

- `tabs(parentKey): List<AuxTerminalTab>`
- `requestClose(parentKey, tabId, onConfirmed: () -> Unit)` — shows shared confirm dialog composable or callback to parent.

**Header menu:** Extend `SessionHeaderOverflowMenu` / `AgentSessionScreen` to pass `acpSessionId`, folder, workspace; under Terminal item render submenu rows: label + trailing close icon. Row click → open dialog with `initialTabId`. Close icon → confirm → `removeTab` logic.

**Dialog:** Wrap existing `onClose` on `AuxTerminalTabChip` with same confirm helper.

**Confirm copy:** New strings `agent_aux_terminal_close_confirm_title/body`.

### 5. Aux terminal keyboard after IME hide

**Root cause:** `TerminalScreen` registers `TerminalHost.onTap` to call `requestTerminalFocus(terminalView, keyboard)` on terminal surface tap (`TerminalBridge` → `onTap`). `AgentAuxTerminalDialog` only raises the keyboard in `LaunchedEffect(sessionKey, generation, terminalView)` and never sets `onTap`, so after the user hides the IME a tap does not re-show it until a new tab changes `sessionKey` and retriggers the effect.

**Choice:** In `AgentAuxTerminalDialog`, mirror `TerminalScreen`'s `DisposableEffect(sessionKey)` hook: set `TerminalHost.onTap` to `requestTerminalFocus` for the current `terminalView`, clear on dispose, and call `TerminalHost.claimDisplay` when the dialog is shown if needed for display ownership.

**Alternatives:** `pointerInput` overlay on `EmbeddedTerminal` (risks stealing events from `TerminalView`); only rely on `TerminalView.requestFocus()` without IME show (insufficient on some devices).

## Risks / Trade-offs

- **[Detached HEAD]** Host HEAD may be a raw SHA → chip shows short SHA or "detached"; acceptable for rare case.
- **[Cache staleness]** New remote branches won't appear until refresh; mitigated by refresh button and post-checkout cache update.
- **[Skip fetch]** User may need refresh to see brand-new remote branch; document in picker empty state if needed.
- **[Header menu density]** Many tabs (max 8) in dropdown is acceptable; use scroll if needed.

## Migration Plan

Ship in one release; no data migration. Branch recents prefs unchanged. Cache is memory-only (cold start refetches on first open).

## Open Questions

None blocking implementation.
