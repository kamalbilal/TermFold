# Proposal

## Why

On Cursor agent sessions, the composer branch label, branch picker, and checkout path feel stale and slow compared to desktop git UX. Branch changes from the agent or an aux terminal do not update the chip; opening the picker re-runs expensive git work; every switch fetches from origin. Aux terminal tabs can be killed accidentally from the dialog or are hard to manage from the header menu.

## What Changes

- **Live branch chip** on the Cursor composer: keep the displayed branch in sync when HEAD changes (agent checkout, terminal `git checkout`, picker switch) without requiring the user to leave the session.
- **Branch picker polish**: fuzzy search (best match first), recents still prioritized, in-memory cache of branch lists with a manual refresh control so reopening the modal is instant.
- **Faster, smoother checkout**: avoid unconditional network fetch when a local switch is enough; batch git steps where practical; show in-dialog progress and update the chip optimistically on success.
- **Aux terminal tab lifecycle**: confirm before closing a tab (kills PTY/PRoot for that tab); expose open tabs under the header **Terminal** menu with the same close+confirm behavior, in addition to the existing in-dialog tab bar.
- **Aux terminal keyboard**: after the user dismisses the soft keyboard, tapping the terminal surface SHALL focus the terminal and show the keyboard again (same behavior as the main terminal screen), without creating a new tab.

## Capabilities

### New Capabilities

- `agent-aux-terminals`: Header menu access to open aux terminal tabs, close with confirmation, and consistent kill-session behavior from menu and dialog.

### Modified Capabilities

- `git-workspace-changes`: Live branch display on Cursor sessions, branch picker search/cache/refresh, and checkout performance and feedback requirements.

## Impact

- `AgentSessionScreen.kt` — branch refresh wiring, header overflow terminal submenu.
- `WorkspaceDialogs.kt` — `BranchPickerDialog` fuzzy ranking, cache, refresh UI, checkout progress.
- `GitWorkspace.kt` — host `.git/HEAD` read for live branch, conditional fetch, optional batched shell for switch.
- `AgentAuxTerminals.kt` — shared tab registry API, confirm dialog, close hooks used from header and dialog.
- `ComposerFuzzyMatch.kt` or shared rank helper — reuse for branch names.
- Strings in `res/values/strings.xml` for confirm copy and refresh affordances.
