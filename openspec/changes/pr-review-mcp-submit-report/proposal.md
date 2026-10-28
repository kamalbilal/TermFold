# Proposal

## Why

PR Reviewer cards depend on the agent embedding a fenced `pr-review-report` JSON block in chat. That format is easy to omit or break, so turns often show missing or invalid reports even when the review prose is fine. We need a reliable submission channel the app can validate before accepting, without redesigning the card UI users already have.

## What Changes

- Add a **PR Reviewer–only** builtin MCP server (`termfold-pr-review`) with one tool: **`submit_pr_review_report`**, whose arguments match the existing `schemaVersion` 1 report shape validated by `PrReviewReportParser`.
- On successful tool call, persist the report into the existing **per-turn map** (`reportsByUserMessageId`) via the same `PrReviewTurnSync` / `saveReviewParse` path used today.
- Update **`PrReviewPrompt`** and agent guidance so the **primary** submission path is the MCP tool; ask which finding ids to fix in plain chat after a successful submit.
- Keep **` ```pr-review-report` fence parsing** as a **legacy fallback** for one release cycle (turn end + backfill), then optional removal in a follow-up.
- **No change** to card layout, colors, or typography (`PrReviewReportChrome` / `Palette` tokens stay as-is).

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `pr-reviewer`: Structured report submission via MCP tool with server-side validation; cards still driven by the same parsed model and per-turn persistence; legacy chat fence remains supported temporarily.

## Impact

- New guest asset: `termfold-pr-review.js`, `ShellSetup` install path, `McpCatalog` builtin when PR Review session is active.
- `BrowserBridge` HTTP handler + `PrReviewReportBridge` (pattern: `PmMemoryBridge` / `PmSessionContext`).
- `AcpClient` / `AgentSessionScreen`: `prReviewMode` flag to attach MCP only in PR Reviewer (not coding/automation/PM).
- `PrReviewAgentSessionScreen`: consume MCP-submitted reports for live footers and turn-end persistence; extend backfill to recognize successful submit tool calls in the turn slice when useful.
- Tests: MCP wire/catalog, bridge validation, turn map integration; existing `PrReviewReportParser*` tests remain authoritative for schema.
- Out of scope: ACP native client tools, card visual redesign, changing review semantics or facts pack.
