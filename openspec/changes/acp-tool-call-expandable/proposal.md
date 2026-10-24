# Proposal

## Why

Tool-call rows in the agent chat truncate titles and output in the header (`maxLines` 1–4) and many calls cannot be expanded at all when they lack structured `detail` or `fileDiffs`. Users cannot read full tool titles, paths, or plain-text output after ellipsis. Thought blocks already use an expand/collapse pattern (expanded by default, one-line preview when collapsed); tool calls should behave the same so every step in the timeline is readable.

## What Changes

- Align **all** `AcpItem.ToolCall` rows with the Thought-row interaction model: expanded by default, tap header to collapse to a single-line preview, chevron always visible.
- Move the **full** tool label (title + paths) into the expanded body so the header never clips multi-line titles; collapsed header shows a one-line preview only.
- Allow expansion for **every** tool call (including those with only a title/path and no diff or `detail`), showing at minimum the full label and status.
- Keep existing per-file diff sections and `detail` monospace output inside the expanded body (unchanged data sources).
- Remove `clickable(enabled = hasDetail)` gating and `LaunchedEffect` that forces `open = true` when detail arrives (user collapse state should stick per row).

## Capabilities

### New Capabilities

- `acp-session-chat`: Agent session chat timeline presentation, including expandable tool-call rows consistent with thought blocks.

### Modified Capabilities

<!-- No paths under openspec/specs/ yet for session chat; delta lives in this change. -->

## Impact

- `AgentSessionScreen.kt` — `ToolCallRow` layout and interaction (primary).
- `strings.xml` — optional label for collapsed tool preview if needed.
- No ACP protocol or persistence changes.
- Composes with existing tool-call file diffs, nested scroll, and presentation cache.
