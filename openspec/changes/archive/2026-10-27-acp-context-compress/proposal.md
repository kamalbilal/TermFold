# Proposal

## Why

Cursor `/summarize` in TermFold only adds a summary message to the chat; the agent wire session still holds the full turn history, so context is not actually reduced. Users expect summarize to **compress agent memory** while **keeping the visible transcript** and **still undoing earlier workspace checkpoints** on old user messages.

## What Changes

- After a **summarize** turn completes successfully, TermFold SHALL **automatically** run **context compress** (no second confirmation tap).
- **Wire:** Retire the current ACP session and open a new one; inject the summarize reply as `pendingWireContext` on the next user prompt (same family as checkpoint `wireResync`).
- **UI timeline:** SHALL **not** remove, fold, or replace prior chat rows; the summary agent message stays in place; later user messages append normally.
- **Checkpoints:** `preTurnByMessageId`, `undoableMessageIds`, and per-message **Undo/Redo** SHALL keep working for turns that existed before compress (full `items` retained).
- Optional **undo compress** (conversation-level): restore previous wire session while UI unchanged; may be a follow-up if not in v1.
- Update built-in menu copy for summarize to reflect auto-compress behavior.
- **Cursor-only** for v1 (same gate as `/summarize` today).

## Capabilities

### New Capabilities

- `acp-context-compress`: Auto compress after summarize, wire rotation + summary injection, UI preservation, checkpoint compatibility.

### Modified Capabilities

- `acp-session-checkpoints`: Clarify that `wireResync` summary source may be agent summarize text; per-message undo after compress uses full timeline; resync banner/busy rules apply to compress.

## Impact

- `AcpClient.kt`: detect summarize completion, `compressContextAfterSummarize()`, extend `wireResync` to accept custom summary text.
- `AgentSessionScreen.kt` / composer: no extra compress button for v1; strings for summarize label.
- `AcpCheckpointUiState` / timeline codec: optional compress metadata (wire id before, compress anchor item id) for undo-compress later.
- Tests: summarize triggers compress, items unchanged, undo still callable, summary injected on next prompt.
