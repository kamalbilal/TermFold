# Proposal

## Why

PR Reviewer today only injects a short chat prompt (“list every issue, then ask which to fix”). The agent returns freeform prose, so findings mix nits with blockers, in-diff bugs with pre-existing ones, and the user cannot tap a structured list or say “fix A-1 and G-2”. Reviews need a facts pack (real PR diff + description + comments) and a parseable report the app can show as cards.

## What Changes

- After the temp copy is ready, TermFold writes a **facts pack** into that copy: merge-base…HEAD diff, changed-file list, GitHub title/body, and existing review comments. The agent is told to treat that pack as the in-diff boundary.
- The review prompt requires a **structured report** (stable finding ids, severity, TermFold surfaces, coverage, then wait for which ids to fix). Web/docs lookup runs **only if the user asks** in chat.
- TermFold parses that report from the **ACP chat stream** (not a new MCP server) and shows **cards** in the PR Reviewer session: summary, risk, in-diff findings by surface, out-of-diff, coverage, missing tests / questions.
- No line-by-line live checklist, no automatic web verify, no extra test/lint preflight in this change.

## Capabilities

### New Capabilities

- `pr-reviewer`: Facts pack on the temp copy, structured report schema, cards in the review session, wait for finding ids before fixes, web verify only when the user asks. (Main specs do not have this capability yet; the earlier workflow change is still unarchived.)

### Modified Capabilities

- (none)

## Impact

- `GitHubPrApi` / bind path: fetch PR body and comments; write facts files after provision/switch.
- `PrReviewPrompt` and unit tests.
- Parse + model for the report JSON; PR Reviewer UI cards on `PrReviewAgentSessionScreen`.
- `AcpClient` / timeline only as needed to detect a completed agent turn and extract a fenced report (no new MCP catalog entry).
