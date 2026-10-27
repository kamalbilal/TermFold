# Design

## Context

See `proposal.md`. Today `/summarize` sends a normal prompt (`AgentSessionScreen` → `onSend("/summarize")`). Checkpoint undo uses `restoreCheckpoint` + `wireResync(ghostFromItemId)` with `ConversationSummary.fromItems`. Summarize does not call `wireResync`. User requirements: **auto compress after summarize**, **full UI history**, **append-only new messages**, **new wire with injected summary**, **per-message undo/redo unchanged**.

## Goals / Non-Goals

**Goals:**

- Detect when the last completed user turn was `/summarize` (trimmed, Cursor tab only) and agent turn succeeded.
- Extract summary text from the following `AgentText` (and optionally `Thought` excluded); use as wire injection body.
- Reuse `retireWireSession` + `openSession` + `hideFromPastList(newSession)` pattern from `wireResync`.
- Set `pendingWireContext` to formatted summary (header + agent text); consumed on next `buildPromptContent`.
- Do **not** mutate `items` for compress (no truncate); optional `Notice` or state flag only.
- Show existing `wireResyncing` UX during compress.
- Push optional `AcpRedoFrame` or compress-specific frame storing `wireSessionId` before compress for future undo-compress.

**Non-Goals:**

- Folding/collapsing chat UI.
- Replacing `/summarize` with a TermFold-only mechanical summary (agent text is source of truth).
- Non-Cursor agents in v1.
- `session/delete` on old wire beyond existing retire behavior.
- Fixing redo wire `session/load` for all checkpoint redos (note gap; out of scope unless needed for compress undo).

## Decisions

**1. Trigger: auto after summarize**

- **Choice:** When `agentBusy` transitions true→false, if the last `UserMessage` text normalizes to `/summarize` and the next substantive agent output is `AgentText`, run `compressContext(summaryText)`.
- **Alternative:** Hook inside `completeUserTurn` when prompt was summarize — tighter, preferred to avoid race with queued messages.
- **Guard:** Skip if compress already ran for that user message id (`compressedForUserMessageId` in state).

**2. Summary text source**

- **Choice:** Concatenate or take last `AgentText` after that user message until turn end; strip or cap length (e.g. 24k chars) with ellipsis.
- **Fallback:** `ConversationSummary.fromItems(items, null)` if agent returned empty text.

**3. Wire payload**

- **Choice:** `pendingWireContext = "Session context (compressed from /summarize):\n\n" + summary` (align with `ConversationSummary` header style).
- **Inject:** On next user send only (existing behavior); no silent auto-prompt after compress unless product asks later.

**4. UI timeline**

- **Choice:** Zero removal of `AcpItem` rows; summarize user + agent summary remain visible; new messages append.
- **Rationale:** `restoreCheckpoint` and `wireResync(ghost)` require full `items` for summary generation and redo frames.

**5. Checkpoints**

- **Choice:** Do not clear `preTurnByMessageId` or `undoableMessageIds` on compress.
- **Undo after compress:** `restoreCheckpoint(messageId)` unchanged; may call `wireResync(ghost)` which uses full items — correct.

**6. Busy / queue**

- **Choice:** Treat compress like resync: set `wireResyncing`, block or queue new sends until `openSession` completes (match edit/undo).

**7. Refactor `wireResync`**

- **Choice:** Add parameter `summaryOverride: String? = null`; when non-null, use instead of `ConversationSummary.fromItems`.
- **Compress path:** `wireResync(ghostFromItemId = null, summaryOverride = agentSummary)`.

**8. Persistence**

- **Choice:** Persist `lastCompressUserMessageId` or `contextCompress` in timeline JSON for resume idempotency (do not re-compress on restore).

## Risks / Trade-offs

- **[Risk] Agent summary is huge** → cap chars; wire may still be heavy once.
- **[Risk] User queues message during summarize** → complete summarize+compress before processing queue, or queue until compress done.
- **[Risk] Summarize fails / refusal** → no compress.
- **[Risk] Double compress on replay** → idempotent guard by user message id.

## Migration Plan

- Ship in app update; no prefs migration.
- Update `acp_cmd_summarize` string.

## Open Questions

- v1 **undo compress** UI: defer unless frame + `session/load` is small add.
