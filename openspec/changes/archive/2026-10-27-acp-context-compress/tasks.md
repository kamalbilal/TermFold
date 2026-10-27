# Tasks

## 1. Wire compress

- [x] 1.1 Refactor `wireResync` to accept optional `summaryOverride: String?`; use when non-null instead of `ConversationSummary.fromItems`
- [x] 1.2 Add `compressContextAfterSummarize(summaryText, anchorUserMessageId)` sharing retire/open/hide/pendingWireContext with resync
- [x] 1.3 Persist idempotency field (e.g. `compressedForUserMessageId`) in checkpoint/timeline state; skip re-compress on restore

## 2. Trigger

- [x] 2.1 On turn complete, detect `/summarize` user message (normalize whitespace) on Cursor agent
- [x] 2.2 Extract summary from agent `AgentText` after that message; fallback to `ConversationSummary` if empty
- [x] 2.3 Run compress automatically; set `wireResyncing` during operation; do not truncate `items`

## 3. UI & copy

- [x] 3.1 Update `acp_cmd_summarize` (and locales) to describe auto compress + preserved chat
- [x] 3.2 Ensure composer/queue respects `wireResyncing` during compress (same as undo resync)

## 4. Tests

- [x] 4.1 Unit: summary extraction + summarize prompt detection
- [x] 4.2 Unit: `wireResync` uses override when provided
- [x] 4.3 Unit or client test hook: compress does not remove items; `preTurn` maps unchanged
- [x] 4.4 Regression: `AcpPastSessionVisibilityTest` / checkpoint tests still pass

## 5. Verify

- [ ] 5.1 Manual: long chat → `/summarize` → summary appears → brief resync → new message → agent responds with context; full history still scrollable; undo on old user message still works
