# Proposal

## Why

Users report that Cursor ACP chats feel missing or merged: **Earlier sessions** does not match what they see in a TermFold tab, and multiple agent tabs in the same workspace interfere with each other. Investigation shows intentional wire-session hiding after checkpoint resync, but also real gaps—workspace-wide hidden-id storage, a four-page cap on `session/list`, and **New session** leaving the old wire thread listable while the tab timeline is cleared.

## What Changes

- Scope **hidden wire session ids** (checkpoint resync retire + user delete) to the **TermFold session tab** (`sessionKey`), not only `agent + workspaceGuestDir`, so one tab’s undo/edit does not hide another tab’s history.
- **Migrate** existing `acp_deleted_sessions` entries: workspace-wide hidden ids apply to all tabs in that workspace until each tab performs a new hide/delete (documented); optional one-time copy to per-tab keys for ids that match each tab’s remembered wire id (design detail).
- Page **`session/list`** until `nextCursor` is empty or a documented safety cap (e.g. max sessions or pages), so older Cursor threads are not silently dropped.
- On **New session** in the agent UI, hide the **previous** wire session id from the past list for **that tab** (same mechanism as resync retire), so the list does not show a ghost of the chat the user just abandoned.
- Clarify **UI copy** in the Earlier sessions dialog: the open tab’s timeline is authoritative; the list is other reopenable Cursor wire threads for this project folder.
- Add **unit tests** for visibility keying and pagination merge behavior (no device-only coverage).

## Capabilities

### New Capabilities

- `acp-past-sessions`: Earlier sessions listing (`session/list`), filtering hidden ids, pagination, reopen/delete, and alignment with **New session**.

### Modified Capabilities

- `acp-session-checkpoints`: Hidden wire ids after resync retire SHALL be recorded per TermFold session tab, not shared across tabs in the same workspace.

## Impact

- `AcpClient.kt`: `deletedKey()`, `listPastSessions()`, `startNewSession()`, `hideFromPastList()` / `retireWireSession()`.
- `AcpSessionResume.kt` / `AcpPastSessions`: visibility helpers and tests.
- `AgentSessionScreen.kt` / `strings.xml`: history dialog subtitle or helper text.
- `SharedPreferences` `acp_deleted_sessions`: key shape change (migration).
- No ACP protocol changes; Cursor `session/list` / `session/delete` usage unchanged.
