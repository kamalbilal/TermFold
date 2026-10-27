# Proposal

## Why

Users choose **New session** expecting the conversation they just left to appear under **Sessions in this folder** so they can reopen it later. Today `startNewSession()` records the outgoing Cursor wire id in the tab’s hidden-id set, so that thread vanishes from `session/list` filtering and never returns unless the user finds it in Cursor elsewhere. That contradicts how multi-session chat products work and matches reports that only the current thread remains in the list.

## What Changes

- **Stop hiding** the previous wire session id when the user starts a new session from the agent UI (history dialog or `/new`). The prior thread SHALL remain listable via `session/list` for that tab’s workspace.
- **Keep** hiding wire ids only for intentional cases: user **Delete** on a past session, and **checkpoint wire resync** (retired session + resync clone), unchanged.
- Update **Earlier sessions** helper copy so it states that **New session** clears the open tab’s local chat but **does not remove** prior threads from the reopenable list.
- **Refresh** the session list when the history dialog opens (already once per open) and after **New session** completes so the previous thread appears without restarting the app.
- **Unit tests**: adjust expectations for `startNewSession` (no hide of outgoing id); add regression test that visible list still contains the old id after a simulated new session.
- **BREAKING (spec)**: Reverses the requirement added in `acp-past-sessions` that mandated hiding the outgoing wire id on New session.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `acp-past-sessions`: Replace “New session hides previous wire id” with “New session keeps previous wire id listable”; clarify UI explainer and list refresh behavior.
- `acp-session-checkpoints`: Confirm wire-resync retire + clone hiding remains per-tab and is unchanged (cross-reference only if needed in delta).

## Impact

- `AcpClient.startNewSession()` — remove `hideFromPastList` for outgoing wire id.
- `AcpPastSessions` / tests in `AcpSessionResumeTest.kt` (or dedicated past-session tests).
- `PastSessionsDialog` — optional reload after new session; `strings.xml` explainer.
- `openspec/specs/acp-past-sessions/spec.md` — requirement text updated when change is archived/synced.
- No ACP protocol changes; `session/list` / `session/delete` usage unchanged.
