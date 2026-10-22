# Proposal

## Why

Agent session undo/redo for workspace checkpoints works, but redo lives only in the session header. That disconnects redo from the user message that defines the ghost branch, so it is unclear which undo step will be reversed—especially after multiple undos on different turns in a long chat.

## What Changes

- Move **Redo** from the session header to the **ghost branch head** user message (the user bubble whose id equals `ghostFromItemId`).
- Show **Redo** only when the redo stack is non-empty and wire resync is not in progress; tapping it calls the existing `redoCheckpoint()` (LIFO stack unchanged).
- On the ghost branch head, show **Redo** instead of **Restore checkpoint** on that same bubble; kept user messages above the ghost still show **Restore checkpoint** when they have a pre-turn checkpoint.
- Hide **Restore checkpoint** on user messages inside the ghost region (`id > ghostFromItemId`), not only on the head.
- Remove the header redo icon (no duplicate control).
- Document multi-undo redo order: nested undos push frames; redo pops in reverse order (e.g. undo turn 6, undo turn 3 → redo restores turn 3 branch first, then turn 6).

## Capabilities

### New Capabilities

- `acp-session-checkpoints`: Per-message undo/redo UI and ghost-branch interaction for ACP agent sessions (shadow git checkpoints, redo stack, dimmed ghost timeline).

### Modified Capabilities

<!-- No existing specs in repo yet -->

## Impact

- `AgentSessionScreen.kt`: `UserMessageRow`, timeline `canUndo` / `showRedo`, `SessionHeader` `onRedo` removal.
- `app/src/main/res/values/strings.xml`: redo label on message row if not already suitable for inline use.
- `AcpClient.kt`: no stack semantics change expected; verify send-after-undo still clears redo per existing rules.
- Manual QA on device: multi-message undo/redo sequence and edit/resend while ghosted.
- Wire resync: cancel/delete previous ACP session before `session/new`; edit + revert files must continue only on the new session with summarized kept turns.
