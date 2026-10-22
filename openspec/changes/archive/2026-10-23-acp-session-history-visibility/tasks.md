# Tasks

## 1. Hidden wire id storage

- [x] 1.1 Change `deletedKey()` in `AcpClient` to include `sessionKey` and add lazy migration from legacy workspace-only key; verify with a new JVM test that tab A and tab B in the same workspace maintain independent hidden sets.
- [x] 1.2 Extend `AcpPastSessions.visible` or add a small helper test file documenting filter behavior with per-tab hidden sets; run `./gradlew :app:testDebugUnitTest --tests "*PastSession*"` and confirm pass.

## 2. Session list pagination

- [x] 2.1 Replace the fixed four-page loop in `listPastSessions()` with cursor loop until empty or `MAX_LIST_PAGES`; verify with a unit test that feeds multiple synthetic pages and asserts merged, deduped count.

## 3. New session alignment

- [x] 3.1 In `startNewSession()`, hide the previous `agentSessionId` for the tab before `openSession()`; verify via unit test or package-visible test hook that hide is invoked when an id was present.

## 4. UI copy

- [x] 4.1 Add `acp_history_explainer` to `values/strings.xml` (and existing locale files if the project requires parity for new strings) and show it in `PastSessionsDialog`; manually confirm text appears when opening Earlier sessions on a Cursor tab.

## 5. Integration check

- [x] 5.1 Run `./gradlew :app:testDebugUnitTest` for the `acp` package and fix any regressions; on device, open two Cursor agent tabs in one workspace, undo in one, and confirm the other's Earlier sessions still lists unrelated wire threads.

## Workflow follow-up

- Archive the change after review with `/opsx-archive` when implementation is merged.
