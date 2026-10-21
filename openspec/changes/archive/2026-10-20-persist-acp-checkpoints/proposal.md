# Proposal

## Why

TermFold now saves chat bubbles across process death, but undo/redo UI state lives only in RAM. After a restart, **Undo changes** / **Redo changes** and ghost dimming disappear even though shadow-git snapshots still exist, because message ids are not mapped back to those hashes.

## What Changes

- Persist checkpoint UI state with the existing per-tab timeline JSON: pre-turn hashes, undoable message ids, ghost branch head, and redo stack.
- Restore that state on handshake when the local timeline is adopted, so undo/redo chips and ghost dimming match the session the user left.
- Keep wire-session identity and file snapshots as they are (prefs + guest shadow git). Do not invent a second store.
- Older timeline files that only contain `items` remain valid: missing checkpoint fields mean empty undo/redo (same as today).

## Capabilities

### New Capabilities

<!-- none — extend the checkpoint capability already introduced by acp-checkpoint-inline-redo -->

### Modified Capabilities

- `acp-session-checkpoints`: Checkpoint undo/redo and ghost-branch UI must survive process restart for the same TermFold session tab.

## Impact

- `AcpTimelineCodec` / `AcpTimelineStore`: encode/decode checkpoint fields next to `items`.
- `AcpClient.persistTimeline` / `adoptTimeline`: save and restore `AcpCheckpointUiState` (minus in-flight `busy` / `wireResyncing`).
- Unit tests for codec round-trip of hashes, undoable ids, ghost, and redo frames.
- Manual: undo on a file-changing turn, force-stop, reopen same tab — Undo (and Redo if a ghost was active) still present.
