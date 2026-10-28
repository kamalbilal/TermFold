# Design

## Context

CodeGraph / codebase facts (verified):

- Cards render only through `PrReviewReportChrome` (`PrReviewReportCards.kt`) using `Palette` accent colors (`riskColor`, `severityColor`, `surfaceColor`, `CardBlock`). **This change must not edit that composable or theme tokens.**
- Report data flows: parse → `PrReviewParse` → `reportsByUserMessageId` / `PrReviewTurnSync` → `AppViewModel.saveReviewParse` / `syncPrReviewReportsToTimeline` (`PrReviewAgentSessionScreen`, `PrReviewTurnReports`, `PrReviewTurnSync`).
- TermFold does **not** register ACP client-side tools (`acpInitializeParams` disables host fs tools). Agent tools arrive via **`session/new` `mcpServers`** (`McpCatalog.acpServers` ← `AcpClient.mcpServers`).
- Proven builtin MCP pattern: Node stdio script → read `~/.termfold/browser.json` → HTTP POST to `BrowserBridge` → Kotlin bridge (`termfold-pm-memory.js` + `PmMemoryBridge`, `pm_session_id` in config).
- PR Review today uses `agentMemoryWorkflow = PR_REVIEW` and cursor `agentMemoryTools`, but **no PR-specific MCP**. Submission is chat fence only (`PrReviewReportParser.FENCE`, `PrReviewPrompt`).

Archived decision (2026-10-27) chose fence to avoid MCP; reliability requirements now override that for submission only—not for card UI.

## Goals / Non-Goals

**Goals:**

- Make **`submit_pr_review_report`** the primary, validated submission path.
- Reuse **`PrReviewReportParser.parseJson`** as the single schema gate (same enums, `schemaVersion` 1).
- Store accepted JSON in existing **per-turn** persistence; preserve multi-turn footers, streaming footer behavior, prune/backfill semantics from `pr-review-per-turn-reports`.
- Return **actionable MCP errors** (parser `Invalid.reason`) so the agent can retry without user intervention.

**Non-Goals:**

- Changing card visuals, strings layout, or color mapping.
- Replacing Cursor’s internal model structured-output APIs.
- New ACP protocol client tools.
- Removing fence support in the first implementation (deprecate in prompt only).

## Decisions

### 1. Dedicated MCP server `termfold-pr-review` (not memory MCP)

**Choice:** New small stdio MCP (like PM memory), enabled only when `prReviewMode` is true on `AcpClient`.

**Why:** Keeps PR submission separate from `termfold-automation-memory` (coding + PR_REVIEW guidance today). PM already uses `pmMode` + dedicated server—mirror that.

**Alternative rejected:** Add tool to automation-memory MCP—wrong coupling and would expose submit to non–PR Review sessions if flags slip.

### 2. HTTP bridge + session context (PM pattern)

**Choice:**

- `PrReviewSessionContext.activeSessionId` set from `PrReviewAgentSessionScreen` (like `PmSessionContext` in `PmAgentSessionScreen`).
- `PrReviewSessionContext.activeUserMessageId` updated on each ACP snapshot (latest user message id) so submit can attach to the **current review turn** without the agent knowing TermFold ids.
- Extend `BrowserBridge.writeBrowserConfig` with `pr_review_session_id` (and reuse `agent_session_key` / ACP key checks as needed).
- `PrReviewReportBridge.handle(context, action, args)`:
  - `pr_review_submit_report`: require active PR session; optional `user_message_id` in args must match active if present; body field `report` (object) or `report_json` (string)—normalize to string → `PrReviewReportParser.parseJson`; on `Ok` call into app layer to **`saveReviewParse(sessionId, userMessageId, parse)`**; on failure return `{ "error": "<reason>" }`.

**Why:** Same transport as existing MCP tools; strict validation on Android where persistence already lives.

**Turn attribution:** Tool may run before `session/prompt` returns. On success, update in-memory map in the screen **and** persist via ViewModel immediately so footers update without waiting for turn-end fence parse. Turn-end handler should **not overwrite** a successful MCP save with `Missing` from fence.

### 3. Tool input schema

**Choice:** MCP `inputSchema` documents the report object (top-level fields matching encoder output). Implementation validates via existing parser, not a second schema definition long-term—optional JSON Schema in JS for MCP listing only.

**Why:** Avoid drift between prompt, parser, and tool docs; parser remains source of truth.

### 4. Prompt and agent instructions

**Choice:** `PrReviewPrompt.prefix`:

- Require **`submit_pr_review_report`** once per review turn before asking which ids to fix.
- De-emphasize fence (`legacy fallback only`).
- After success, plain-language “which ids to fix” in chat; **do not** duplicate full report JSON in chat (optional: strip fence still applies if agent disobeys).

Add **`AgentMemoryGuidance`** block for PR_REVIEW pointing at **`termfold-pr-review`** MCP (separate from memory MCP bullets).

### 5. UI / cards

**Choice:** No changes to `PrReviewReportChrome`. Footers still call `prReviewJsonForTurn` + `prReviewChromeFromParse`; data may come from map populated by MCP **or** fence parse/backfill.

**Why:** User requirement—same design and colors.

### 6. Legacy fence + backfill

**Choice:** Keep `parseTurnSlice`, `prReviewReportsBackfillFromChat`, fence strip in chat. Resolution order for a turn:

1. `reportsByUserMessageId[userMessageId]` (includes MCP writes)
2. Live fence parse in turn slice
3. Legacy `lastReportJson` rules unchanged

Optional enhancement: if turn slice contains completed MCP tool row for `submit_pr_review_report`, extract args from `rawInput` when map missing (defense in depth)—only if Cursor exposes args on wire.

### 7. Wiring flags

**Choice:**

- `AgentSessionScreen` / `AcpClient`: new parameter `prReviewMode: Boolean` (true only from `PrReviewAgentSessionScreen`).
- `McpCatalog.list`: `if (prReviewMode) builtinPrReview(context)`
- Recreate client when `prReviewMode` mismatches (same as `usesAgentMemoryTools` check)—or always pass flag on existing PR client.

**MCP reload:** PR Review MCP added/removed requires `reloadMcpServersNow` when entering screen if session already open—document in tasks if needed.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Agent skips tool | Strong prompt + existing missing-report chrome; MCP errors on retry |
| MCP reload mid-session | Enable server at session start; reload on PR screen attach |
| Duplicate fence + tool | Idempotent save: same turn id overwrites with last valid parse |
| Bridge called outside PR screen | Session id mismatch errors (PM pattern) |

## Migration

- Ship MCP + dual-read (MCP primary, fence fallback).
- Later change: remove fence from prompt and optionally drop fence backfill after telemetry/usage confidence.
