# Tasks

## 1. ACP wire protocol

- [x] 1.1 Add `AcpPendingAskQuestion`, extend `AcpUiState`, parse helper for `cursor/ask_question` (accept `allowMultiple` / `allow_multiple`) with unit tests — verify `:app:test` passes.
- [x] 1.2 Handle `cursor/ask_question` in `AcpClient.handleLine` for Cursor agent only; set pending + latest user message id; never `Unknown method` — verify unit test feeds sample JSON-RPC line.
- [x] 1.3 Implement `respondAskQuestion` with Cursor outcome shapes; skip reason `Questions skipped by user` — verify JSON shape in unit test.
- [x] 1.4 Reject or cancel when a second ask arrives while pending; clear pending on response — verify unit test.
- [x] 1.5 Guard `send` / `sendNow` while `pendingAskQuestion != null` — verify unit test that send is ignored.

## 2. Persistence, prune, wake policy

- [x] 2.1 Add `AskQuestionTurnSnapshot` + `askQuestionsPrunedToTimeline` (mirror `prReviewReportsPrunedToTimeline`) with unit tests for prune on removed user messages.
- [x] 2.2 Extend `AcpPersistedTimeline` + `AcpTimelineCodec` encode/decode for `askQuestionsByUserMessageId`; terminal phases only — verify codec round-trip test.
- [x] 2.3 Wire map through `persistTimeline` / `adoptTimeline`; prune ask map on checkpoint undo alongside `prunePreTurnMap` — verify test or targeted unit test on prune helper from client undo path.
- [x] 2.4 Extend `AcpWakePolicy.keepsCpuAwake` + `AcpClient.keepsCpuAwake()` for pending ask; update `AcpWakePolicyTest` — verify test passes.

## 3. Chat UI — turn footers (codegraph-critical)

- [x] 3.1 Add `AskQuestionCard` composable (single/multi, Other + text, Recommended badge, Continue/Skip, summary) using `Palette` card tokens — verify `assembleDebug`.
- [x] 3.2 In `AgentSessionScreen`, for Cursor: set `turnFooters = isCursor || turnFooter != null`; wrap footer as `Column { AskQuestion… ; external turnFooter? }` — verify `AgentChatTimelineTest` or new test that Cursor coding session builds `TurnFooter` entries.
- [x] 3.3 Connect card to `respondAskQuestion` + update persisted map on answer/skip; show interactive only for latest turn pending — verify unit test on footer visibility logic.
- [x] 3.4 Composer: disable send/queue while `pendingAskQuestion != null` (and optionally `pendingPermission != null`) — verify manual or small composable state test.
- [x] 3.5 String resources for Skip, Continue, Other, Questions — verify `assembleDebug`.

## 4. Integration smoke

- [ ] 4.1 Plain **coding** Cursor session: plan/ambiguous prompt triggers ask → card under user message → Continue → agent resumes — verify on debug APK.
- [ ] 4.2 Two-turn session: first turn answered card stays above; second turn ask only under second message — verify on device.
- [ ] 4.3 PR or PM session: ask card stacks above existing report/PM footer without hiding it — verify on device.
- [ ] 4.4 Freeform Other path against real agent — verify acceptance; fix encoder if smoke fails.

## Workflow follow-up

- After smoke OK: `/opsx-archive cursor-ask-question-cards` (optional `/opsx-sync` for `acp-session-chat`).
