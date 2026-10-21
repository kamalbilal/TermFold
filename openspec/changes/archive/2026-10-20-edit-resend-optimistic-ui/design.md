# Design

## Context

See `proposal.md` — Why. Today `editAndResend` runs sequentially: optional restore, truncate, `wireResync` (`session/delete` + `session/new`), then `sendNowSuspend`, which is the only step that appends `UserMessage` and sets `agentBusy`. During `wireResync`, the UI sets `wireResyncing` and shows a small resync label at the top of the timeline, not `WorkingRow`.

Normal `sendNowSuspend` already appends the user row and sets `agentBusy` before `session/prompt`, and starts an async pre-turn checkpoint commit for the new message id.

## Goals / Non-Goals

**Goals:**

- Match normal-send perceived latency for edit resend (message + Working immediately after confirm).
- Preserve existing ordering for correctness: resolve pre-turn hash and restore **before** truncate when reverting files; wire resync before prompt when reverting files.
- Reuse `agentBusy` + existing `WorkingRow` in `AgentSessionScreen` rather than a new visual state.
- Single user message id for the edited turn through prompt completion and checkpoint finalization.

**Non-Goals:**

- Changing undo/redo chip placement or ghost semantics.
- Parallelizing restore and truncate in ways that lose the correct checkpoint hash.
- Removing `wireResyncing` entirely (may keep for debugging or hide if redundant with Working).

## Decisions

### 1. Reorder UI updates inside `editAndResend`

**Choice:** After successful restore (or immediately when keep-files), call `truncateFromUserMessage`, then **optimistic append** (new `UserMessage` + `agentBusy = true` + `persistTimeline`), then `wireResync` (if revert), then wire prompt only.

**Rationale:** Truncate must happen before append so ids and tail state stay consistent. Restore must stay before truncate when reverting so the hash keyed to the edited message id is still available.

**Alternative:** Set `agentBusy` before truncate — rejected because the timeline would still show the old tail until truncate runs.

### 2. Split `sendNowSuspend` into append + prompt

**Choice:** Extract shared logic:

- `beginUserTurn(text, images, files) → messageId` — ghost commit if needed, allocate id, start async pre-turn commit, append `UserMessage`, set `agentBusy`, persist.
- `completeUserTurn(messageId, text, images, files)` — build wire payload (including `pendingWireContext`), `session/prompt`, finalize undoable, clear busy, queue drain.

`sendNowSuspend` calls both in sequence. `editAndResend` calls `beginUserTurn` after truncate/resync prep point, then `completeUserTurn` after `wireResync`.

**Alternative:** Duplicate append block in `editAndResend` — rejected for drift risk.

### 3. `agentBusy` during wire resync

**Choice:** Leave `agentBusy` true from optimistic append through prompt completion. `wireResync` may still toggle `wireResyncing`; timeline can show Working from `agentBusy` (primary) and optionally drop or keep the top resync item.

**Rationale:** User asked for the same Working row as agent turns; `wireResyncing` alone does not trigger it today.

### 4. Failure handling

**Choice:**

- Restore fails → no truncate, no optimistic message (current behavior).
- Resync or prompt fails after optimistic message → `appendSystemError`, clear `agentBusy`, do not remove the user message (user can edit again or send another turn).

**Alternative:** Roll back truncate on failure — rejected; losing the user’s edited text is worse than an error notice.

### 5. `awaitTurnIdle` at start

**Choice:** Keep `awaitTurnIdle` before any truncate/append (unchanged). Only the gap *after* confirm is addressed.

**Alternative:** Show message before idle — deferred; risks overlapping with an in-flight agent reply.

## Risks / Trade-offs

- **[Risk] Prompt runs before resync completes** → Mitigation: keep strict suspend order; never call `completeUserTurn` until `agentSessionId` is non-null after resync.
- **[Risk] Double persist / duplicate items** → Mitigation: `completeUserTurn` must not re-append; guard with existing id in items.
- **[Risk] `turnInFlight` false during long resync** → Mitigation: set `turnInFlight = true` when starting optimistic turn (same as `sendNowSuspend`) so queue/cancel semantics stay coherent.
- **[Trade-off] User message visible before files restored** → Brief window where chat shows new text but disk still catching up; acceptable if restore immediately follows truncate in the same coroutine.

## Migration Plan

Ship in app release only; no data migration. Timeline on disk may contain a user message without a completed agent reply if the app is killed mid-prep — existing resume behavior should show the message; next send may need idle/resync rules (verify in QA).

## Open Questions

None blocking implementation.
