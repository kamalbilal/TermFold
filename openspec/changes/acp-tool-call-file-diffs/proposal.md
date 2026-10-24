# Proposal

## Why

ACP agents already send structured file diffs on tool calls (`diff` content with `git_patch` or v1 `oldText`/`newText`), but TermFold flattens them to a one-line `"Edited <path>"` string and shows plain monospace text when expanded. Users cannot see line-level adds and removes like Cursor, and scrolling a long chat degrades when large tool outputs are composed inside a single list item. The app should preserve patch data, render per-file unified diffs, and stay smooth while the agent is still editing.

## What Changes

- Parse and retain structured tool-call diff content from ACP `tool_call` / `tool_call_update` instead of discarding patch text.
- Show **per-file** collapsible diff sections (path, operation, `+`/`-` counts) using the same unified diff presentation as the git changes UI.
- Support **ACP v2** `patch.format == git_patch` and **v1** `oldText`/`newText` via **full synthetic unified diff** when no patch is provided.
- Parse and cache diff presentations **off the main thread**; expanded file bodies use lazy line rendering with bounded height so the chat `LazyColumn` stays responsive.
- Add an app **Settings** toggle (default **on**) to show **live** line-level diffs while a tool call is `in_progress`; when off, suppress live hunk updates during `in_progress` but still allow final diffs after the tool completes.
- Extend timeline persistence so new sessions replay file diffs; older saved timelines keep text-only `detail` (no retroactive recovery).
- Fix v2 diff path discovery for collapsed tool labels (`changes[].path`, not only top-level `path`).

## Capabilities

### New Capabilities

- `acp-session-chat`: Presentation of the TermFold-owned agent session chat timeline, including tool-call file diffs, live-diff preference, and scroll-safe diff rendering.

### Modified Capabilities

_None._

## Impact

- `app/src/main/java/com/termfold/app/acp/AcpClient.kt` — ingest structured diff parts; path extraction for v2
- `app/src/main/java/com/termfold/app/acp/AcpTimelineCodec.kt` — persist diff payloads
- `app/src/main/java/com/termfold/app/acp/AcpItem.ToolCall` (or companion models) — structured content
- `app/src/main/java/com/termfold/app/gitworkspace/` — synthetic unified diff helper (new)
- `app/src/main/java/com/termfold/app/ui/screens/AgentSessionScreen.kt` — `ToolCallRow` diff UI
- `app/src/main/java/com/termfold/app/ui/screens/SettingsScreen.kt` + new prefs object + `strings.xml`
- Unit tests: diff synthesis, ACP content parsing, presentation stats
