# Design

## Context

See proposal.md for why. `AcpTimelineStore` already writes `acp-timelines/<sessionKey>.json` with `{ items }` via `AcpTimelineCodec`. `AcpClient.adoptTimeline` restores items on handshake; `AcpCheckpointUiState` is not included. Undo chips require `undoableMessageIds`; ghost/redo require `ghostFromItemId` and `redoStack`. Shadow git already persists file snapshots in the guest.

## Goals / Non-Goals

**Goals:**

- One JSON document per TermFold session tab holds items plus durable checkpoint UI fields.
- Restore those fields whenever the local timeline is adopted.
- Keep encode backward compatible with items-only files.

**Non-Goals:**

- Changing shadow-git layout or when commits are taken.
- Persisting in-flight `busy` or `wireResyncing`.
- Rebuilding undoability by scanning git history without saved message-id maps.
- Cross-device sync.

## Decisions

### 1. Same file, extra keys

**Choice:** Extend the timeline JSON:

```
{ "items": [...], "checkpoints": { "preTurnByMessageId", "undoableMessageIds", "ghostFromItemId", "redoStack" } }
```

`persistTimeline` writes the full object. `load` returns items + checkpoints (default empty maps/lists).

**Rationale:** One write, one key (`sessionKey`), already called after send/truncate/resync.

**Alternative:** Separate prefs file — two sources of truth and easier desync of item ids vs hashes.

### 2. Redo frames include timeline snapshots

**Choice:** Encode each `AcpRedoFrame` the same way as live state: items (via existing item codec), ghost id, maps, undoable ids, worktree tip, optional wireSessionId.

**Rationale:** Redo after restart must restore dimmed tail items that may not be in the live `items` list if... wait. After undo, live `items` still include the ghost tail (dimmed). Truncate on send-after-ghost drops them. Redo stack holds the pre-undo timeline. Must persist those copies.

**Alternative:** Persist only hashes and rebuild UI — cannot rebuild dropped ghost tails after commitGhostBranch. After undo without send, items still on disk in live timeline; redo stack still needed for worktree tip + previous ghost.

### 3. Restore only durable fields

**Choice:** On adopt, copy checkpoints into `AcpUiState` with `busy = null` and `wireResyncing = false`.

**Rationale:** Process death cannot leave a restore in flight.

### 4. Id continuity already handled

**Choice:** Keep `AcpTimelineCodec.nextCounter(maxItemId)` on adopt. Checkpoint maps use the same item ids.

**Rationale:** If we reset ids, persisted hashes would point at the wrong rows.

## Risks / Trade-offs

- **[Large redo JSON]** Frames duplicate full item lists → Mitigation: cap already `MAX_REDO_FRAMES = 20`; accept size for correctness.
- **[Items-only files]** Old installs have no checkpoints → Mitigation: decode defaults; matches spec "older timeline file".
- **[Hash without guest repo]** Undo restore can fail after distro wipe → Mitigation: existing "Could not restore workspace files" path; still show the chip if ids were saved.

## Migration Plan

Ship in next debug build. No migration job. First persist after a turn writes the new keys. Rollback: older app ignores extra JSON keys if it only reads `items` (current codec reads `items` only — new codec must still read `items` if `checkpoints` missing).

## Open Questions

None.
