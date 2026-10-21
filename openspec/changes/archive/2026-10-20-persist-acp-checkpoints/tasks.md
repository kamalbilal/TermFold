# Tasks

## 1. Codec and store

- [x] 1.1 Extend `AcpTimelineCodec` tests to round-trip `preTurnByMessageId`, `undoableMessageIds`, `ghostFromItemId`, and a redo frame (including nested items); verify items-only JSON still decodes with empty checkpoint fields
- [x] 1.2 Implement encode/decode of a `checkpoints` object next to `items`; verify those tests pass
- [x] 1.3 Change `AcpTimelineStore` load/save to carry checkpoint UI state with items (empty defaults when missing); verify an items-only file still loads chat rows

## 2. Client restore and persist

- [x] 2.1 Make `persistTimeline` write current `checkpoints` (excluding `busy` and `wireResyncing`) with items
- [x] 2.2 Make `adoptTimeline` restore checkpoint fields and keep `nextId` continuity; verify handshake after a saved undoable turn would populate `undoableMessageIds`
- [x] 2.3 Confirm persist still runs after send (including `finalizeUndoableForTurn`), undo, redo, truncate, and resync so a kill after those actions does not drop chips

## 3. Verify

- [x] 3.1 Run `./gradlew.bat :app:testDebugUnitTest --tests com.termfold.app.acp.AcpTimelineCodecTest --tests com.termfold.app.acp.AcpSessionResumeTest` and `:app:compileDebugKotlin`; both succeed
- [x] 3.2 On device after install: file-changing turn, force-stop, reopen same tab — Undo still on that message; undo then force-stop — ghost dimming and Redo still on that head

## Workflow follow-up

- Archive with `/opsx-archive` after device QA.
