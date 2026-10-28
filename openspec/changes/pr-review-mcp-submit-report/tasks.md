# Tasks

## 1. Guest MCP server

- [x] 1.1 Add `app/src/main/assets/termfold-pr-review.js`: stdio MCP with tool `submit_pr_review_report`, HTTP call to BrowserBridge `/v1` (read `browser.json`, include `pr_review_session_id` / session key from config).
- [x] 1.2 Install to guest in `ShellSetup` (`PR_REVIEW_TOOL` constant, same pattern as `PM_MEMORY_TOOL`).
- [x] 1.3 Unit or wire test for MCP tool list / minimal `tools/call` mock (follow `McpWireTest` / JS patterns if present).

## 2. Android bridge and session context

- [x] 2.1 Add `PrReviewSessionContext` (active session id + active user message id) set/cleared from `PrReviewAgentSessionScreen`.
- [x] 2.2 Extend `BrowserBridge.writeBrowserConfig` with `pr_review_session_id`; route `pr_review_submit_report` in `execute()` to new `PrReviewReportBridge`.
- [x] 2.3 Implement `PrReviewReportBridge.handle`: session mismatch guards, `PrReviewReportParser.parseJson`, on success invoke `AppViewModel.saveReviewParse` (or thin callback registered from ViewModel) with active user message id.
- [x] 2.4 Tests for bridge: ok payload, invalid schema, wrong session id, missing active session.

## 3. MCP catalog and ACP wiring

- [x] 3.1 Add `McpCatalog.builtinPrReview` and `prReviewMode` flag through `list` / `acpServers`.
- [x] 3.2 Thread `prReviewMode` on `AcpClient` and `AgentSessionScreen`; pass `prReviewMode = true` only from `PrReviewAgentSessionScreen`.
- [x] 3.3 Ensure client recreation or MCP reload when PR Review screen attaches so `termfold-pr-review` is on the wire for `session/new` / reload.
- [x] 3.4 Test catalog lists PR MCP only when `prReviewMode` is true (not PM/automation-only modes).

## 4. Prompts and submission policy

- [x] 4.1 Update `PrReviewPrompt.prefix`: MCP submit required primary path; fence legacy fallback; no duplicate JSON in chat after successful submit.
- [x] 4.2 Add PR-specific MCP guidance in `AgentMemoryGuidance` for `PR_REVIEW` (tool name + when to call).
- [x] 4.3 Update `PrReviewPromptTest` expectations.

## 5. Turn persistence and UI integration (no card redesign)

- [x] 5.1 On MCP success, update local `reportsByUserMessageId` in `PrReviewAgentSessionScreen` immediately (mirror turn-end fence path).
- [x] 5.2 Turn-end handler: do not replace a stored valid report with `Missing` when fence absent; prefer map entry from MCP.
- [x] 5.3 Optional: extend `prReviewReportsBackfillFromChat` or turn-slice helper to recover from completed `submit_pr_review_report` tool `rawInput` when map empty. (Deferred: tool args are not stored on [AcpItem.ToolCall]; fence backfill unchanged.)
- [x] 5.4 Confirm **no edits** to `PrReviewReportChrome` / color helpers except if needed for non-visual bugfix; snapshot or manual note in PR that palette mapping unchanged.
- [x] 5.5 Tests: turn map + MCP path integration (`PrReviewTurnReportsTest` / new bridge integration test).

## 6. Verification

- [x] 6.1 Run `./gradlew :app:testAppDebugUnitTest --tests 'com.termfold.app.prreview.*' --tests 'com.termfold.app.acp.mcp.*'` (adjust package for new tests).
- [ ] 6.2 Device smoke: review turn → agent calls submit → cards appear with same colors; invalid args → tool error + retry; second message → turn-1 cards fixed; fence-only legacy still works once.
