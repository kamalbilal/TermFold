# Proposal

## Why

PR Reviewer report cards are anchored in the chat timeline per user turn, but report state is stored as a single `lastReportJson` with session fallback only on the latest turn. After a second message, earlier cards disappear or jump to the bottom while the active turn streams, which feels broken and contradicts multi-turn review follow-ups.

## What Changes

- Persist structured review reports **per user message id** (turn), not only the latest turn.
- Resolve each turn footer from: saved turn report → live parse of that turn’s ACP slice → (for the active turn only) streaming-safe fallback scoped to that turn.
- On turn completion, save `parseTurnSlice` for the finishing turn’s user message id (not `parseTurn` over the whole chat).
- Keep `lastReportJson` as a mirror of the most recent successful report for backward compatibility and existing persistence tests.
- Add unit tests for multi-turn card resolution and persistence round-trip.

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `pr-reviewer`: Clarify and extend **Review report cards** so each completed review turn keeps its cards under that turn in the timeline across later messages and app restarts.

## Impact

- **UI**: `PrReviewAgentSessionScreen.kt`, `PrReviewReportCards.kt` (footer resolution only).
- **Models / persistence**: `PrReviewSession`, `PrReviewPersistence`, `PrReviewTurnSync`.
- **ViewModel**: `AppViewModel.saveReviewParse` (may accept turn-scoped updates).
- **Tests**: `PrReviewPersistenceTest`, `PrReviewTurnSyncTest`, new multi-turn footer tests (parser/timeline patterns already exist in `PrReviewReportParserTest`, `AgentChatTimelineTest`).
- **Reference pattern**: PM `memosByUserMessageId` / `pmMemoJsonForTurn` in `PmAgentSessionScreen.kt` (PR goes further with disk persistence).
