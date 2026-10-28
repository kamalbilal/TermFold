# Design

## Context

See `proposal.md` (Why). Today the chat timeline inserts a `TurnFooter` after every user turn (`AgentChatTimeline.kt`), but PR Reviewer resolves card data with a single `lastReportJson` on `PrReviewSession` and applies `session.lastReportJson` fallback only when `userMessageId == lastUserMessageId` (`PrReviewAgentSessionScreen.kt`). Turn completion calls `parseTurn(items)`, which parses only the last turn. Product Manager already uses an in-memory `memosByUserMessageId` map plus `pmMemoJsonForTurn`; PR Reviewer has no per-turn store.

## Goals / Non-Goals

**Goals:**

- Stable cards under each completed review turn for the lifetime of the session (including after later messages and process restart).
- Turn-scoped fallback while the **active** turn streams (no global “latest report” bleeding into the wrong footer).
- Backward-compatible persistence: existing `lastReportJson` / `lastParseError` fields keep working for single-turn sessions and existing tests.

**Non-Goals:**

- Changing the structured report schema, prompt, or facts pack.
- Persisting parse errors per turn (only successful reports need turn keys; missing/invalid on a finished turn keeps current yellow error behavior for that turn).
- PM workflow changes (reference only).

## Decisions

### 1. Per-turn storage shape

**Decision:** Add `reportsByUserMessageId: Map<Long, String>` on `PrReviewSession`, serialized in `PrReviewPersistence` as a JSON object keyed by stringified user message ids (values = encoded report JSON, same shape as today’s `lastReportJson`).

**Alternatives:** Compose-only map (like PM) — rejected because PR already persists reports and users expect cards after restart; re-parse-only from ACP items — rejected because cards often relied on `lastReportJson` when chat parse is Missing.

### 2. Resolution order in `turnFooter`

**Decision:** Introduce `prReviewJsonForTurn` (mirror `pmMemoJsonForTurn`):

1. `reportsByUserMessageId[userMessageId]` (from session + in-memory updates)
2. `parseTurnSlice(turnItems, turnFinished)` when Ok
3. For **latest user message only**, if turn has agent reply and streaming: turn-scoped saved json for that id (same as step 1) via `prReviewChromeFromParse` with Skipped/Missing — **not** another turn’s `lastReportJson`

**Alternatives:** Keep global `lastReportJson` fallback — rejected (causes hide/flash/wrong-turn display verified in exploration).

### 3. When to persist

**Decision:** On `agentWasBusy && !busy` in `onAcpSnapshot`, compute `turnUserId = last user message id`, `turnItems = agentChatItemsForUserTurn(items, turnUserId)`, run `parseTurnSlice(turnItems, turnFinished = true)`, and on `Ok` upsert `reportsByUserMessageId[turnUserId]` and set `lastReportJson` / clear `lastParseError` via extended `PrReviewTurnSync`.

**Alternatives:** Continue `parseTurn(items)` — rejected (drops earlier turns on save).

### 4. UI state vs session state

**Decision:** Hold `reportsByUserMessageId` in `remember(session.id)` updated optimistically when save completes, initialized from `session.reportsByUserMessageId` on first composition; ViewModel persistence remains source of truth after restart.

### 5. `prReviewChromeFromParse` + active turn

**Decision:** Pass fallback report only when it comes from **that turn’s** saved json (map lookup), not global latest. During active turn `Skipped` with no saved entry for that turn → empty chrome (current streaming UX). When turn completes, map entry exists → stable cards.

## Risks / Trade-offs

- **[Risk] User message ids not stable across timeline truncate/undo** → Mitigation: save on turn complete only; truncate removes later user ids — prune map keys to remaining user message ids when adopting timeline snapshots (same pattern as checkpoint maps in ACP if needed; initial scope: save keys only, prune on explicit truncate if already handled elsewhere).
- **[Risk] Persistence blob size grows with many turns** → Mitigation: reports are one JSON per turn; acceptable for review sessions; no migration of old sessions beyond empty map default.
- **[Trade-off] Follow-up turn without new fence** → Older turn cards stay from map; latest turn may show missing until agent emits report — matches spec scenarios.

## Migration Plan

1. Ship with decode default `reportsByUserMessageId = emptyMap()`; optional one-time hydrate: if map empty and `lastReportJson` present, do not auto-backfill all turns (ambiguous) — cards for old single-turn sessions still work via parse + `lastReportJson` on last footer until user sends a second message; after deploy, new saves populate map.
2. No server migration; local `PrReviewStore` JSON only.
3. Rollback: revert code; persisted extra field ignored by older builds if rolled back (forward-compatible empty keys).

## Open Questions

_None — approach follows existing PM pattern and verified PR code paths._
