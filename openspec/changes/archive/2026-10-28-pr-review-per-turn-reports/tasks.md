# Tasks

## 1. Session model and persistence

- [x] 1.1 Add `reportsByUserMessageId: Map<Long, String>` to `PrReviewSession` with empty default; verify `PrReviewModels` compiles.
- [x] 1.2 Extend `PrReviewPersistence` encode/decode for `reportsByUserMessageId` (string keys in JSON); verify `PrReviewPersistenceTest` round-trip includes a multi-key map.
- [x] 1.3 Extend `PrReviewTurnSync.apply` to upsert map entry on `Ok` for a given user message id and mirror `lastReportJson` / `lastParseError`; verify `PrReviewTurnSyncTest` covers map upsert plus Missing unchanged map entries.

## 2. Turn-scoped save from chat

- [x] 2.1 Change `PrReviewAgentSessionScreen.onAcpSnapshot` to save via `parseTurnSlice(turnItems, turnFinished = true)` for the finishing turn's user message id (not `parseTurn(items)`); extend `saveReviewParse` / ViewModel if needed to pass user message id; verify unit test that two-turn item list saves turn-2 report without dropping turn-1 map entry when both were saved.

## 3. Footer resolution (UI)

- [x] 3.1 Add `prReviewJsonForTurn` (same resolution order as design) and `remember` map seeded from session; wire `turnFooter` to use turn-scoped fallback only; verify manual or unit test: turn-1 footer still has report after simulated second user message when turn-1 json is in map.
- [x] 3.2 Ensure active latest turn does not show another turn's report during `Skipped` (no global `lastReportJson` fallback except as mirror on save); verify parser test `unfinishedTurnWithoutFenceIsSkipped` still passes and add footer logic test if extracted.

## 4. Regression tests

- [x] 4.1 Add test for `prReviewChromeFromParse` / json-for-turn helper covering map hit, live parse, and Skipped-without-map; verify `./gradlew :app:testDebugUnitTest --tests '*PrReview*'` passes.
- [x] 4.2 Run `./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride=/root/projects/Termfold/.tools/aapt2` and smoke multi-turn PR Reviewer on device (cards stay under turn 1 after turn 2 send/complete).

## Workflow follow-up

- Archive the change after review with `/opsx-archive` when implementation matches spec scenarios.
