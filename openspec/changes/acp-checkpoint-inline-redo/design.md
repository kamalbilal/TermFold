# Design

## Context

Checkpoint undo/redo is implemented in `AcpClient` with a LIFO `redoStack` of `AcpRedoFrame`, `ghostFromItemId` for dimmed tail UI, and per-message `preTurnByMessageId`. `AgentSessionScreen` shows **Restore checkpoint** under user bubbles and **Redo** in `SessionHeader` when `canRedo`. See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**

- Bind redo UI to `ghostFromItemId` (ghost branch head user message).
- Tighten undo visibility so ghost tail messages do not offer restore.
- Remove header redo to avoid duplicate, ambiguous controls.
- Reuse existing `redoCheckpoint()` and stack semantics without protocol changes.

**Non-Goals:**

- Changing shadow-git storage, pre-turn commit timing, or wire resync summary logic.
- Per-message redo stacks or redo buttons on multiple rows simultaneously.
- Desktop/web clients (Android Compose only).

## Decisions

### 1. Redo visibility predicate

**Choice:** `showRedo = ghostFrom != null && item.id == ghostFrom && checkpoints.canRedo && !wireResyncing`

**Rationale:** Matches user mental model: "I undid at this bubble; redo brings the branch back from here." The top of `redoStack` always corresponds to the latest undo, which set `ghostFrom` to that message id.

**Alternative:** Show redo on every user message with a frame in the stack — rejected as confusing and not representable without tagging frames with anchor ids in UI state.

### 2. Undo visibility predicate

**Choice:** Extend `canUndo` with `ghostFrom == null || item.id < ghostFrom` (and existing pre-turn / busy / resync checks).

**Rationale:** Prevents restore on dimmed tail user turns while keeping restore on earlier kept turns.

**On ghost head:** When `item.id == ghostFrom` and redo is available, hide restore (redo only). When redo stack is empty but ghost remains (edge case after partial failure), keep current restore behavior if pre-turn hash still exists — unlikely in normal flow.

### 3. UserMessageRow API

**Choice:** Add `showRedo: Boolean` and `onRedo: () -> Unit` parallel to undo; mirror undo styling (icon + label using `R.string.acp_checkpoint_redo`).

**Alternative:** Single combined "Branch actions" menu — heavier UX for a rare action pair.

### 4. Header

**Choice:** Pass `onRedo = null` always (remove conditional). Delete unused header button block if no other callers need it.

### 5. Logic layer

**Choice:** No changes to `restoreCheckpoint` / `redoCheckpoint` unless QA finds a mismatch. Stack push on undo and pop on redo already align with spec scenarios.

## Risks / Trade-offs

- **[Long chat, ghost head off-screen]** User must scroll to branch head to redo → Mitigation: existing "jump to latest" unaffected; optional future: scroll-to-ghost on undo (out of scope).
- **[UserMessage id is not UserMessage]** `ghostFromItemId` is item id; undo sets it to user message id — consistent today; if non-user items could become head, redo placement would break → Mitigation: unchanged contract from checkpoint feature.
## Migration Plan

Ship in next debug/release build. No data migration. Users who relied on header redo use inline redo on the dimmed user bubble at branch head.

## Open Questions

None — exploration confirmed LIFO redo order and inline placement.

## Wire resync (edit revert fix)

**Choice:** Before `session/new`, call `session/cancel` and best-effort `session/delete` on the outgoing wire session id. Drop `session/update` notifications whose `params.sessionId` does not match [agentSessionId].

**Rationale:** Abandoning the id without cancel left the agent serving the old thread while the client opened a new session — especially visible after edit + revert files.

**Edit + revert:** Truncate clears `redoStack`; do not call `sendNowSuspend` if `openSession` fails after resync. Block composer while the revert/keep dialog is open.

## TermFold session vs ACP wire session

**Choice:** The folder session (`sessionKey`) is the user-facing conversation. `session/new` after undo/edit-revert only rotates `agentSessionId`. Persist the Compose timeline on disk; on process restart restore it and `session/load` the latest wire id with replay suppressed. Hide both the retired wire id and the resync clone from `session/list` (same store as user-deleted past sessions).

**Rationale:** Cursor titles and lists every `session/new`. Remembering the clone made restart replay "Session context (summarized…)" as a second chat, while the original listed session stayed at the undo point.
