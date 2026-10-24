# Proposal

## Why

Users enable **Show live file diffs** in Settings expecting to see file edits as the agent works, without extra taps. Today the **Agent chat** preference only gates whether hunk lines update while a tool is `in_progress`; it does not control tool-row collapse. `syncCompletedToolCollapse` still marks every completed tool call as collapsed by default, so edit/write/search-replace tools hide their per-file diff sections until the user expands each tool header manually. That feels like the setting is broken and conflicts with the unfinished `acp-tool-call-expandable` intent (“expanded by default”).

## What Changes

- Tie default **tool-call row** expand/collapse to `AcpChatPrefs.showLiveToolDiffs` for tools that have structured `fileDiffs` (or other file-changing tools).
- When live diffs are **on**: new file-changing tool calls SHALL default to **expanded** when they appear and when they transition to a terminal status (`completed` / `failed`), unless the user collapsed that row in the session.
- When live diffs are **off**: keep today’s performance-friendly default—collapse completed file-changing tool calls automatically (summary header only until expanded).
- Pass the preference into collapse sync (timeline or screen) and refresh defaults when the user returns from Settings with a changed value (new tools only; never override explicit user toggles).
- Add unit tests for collapse default logic; optional UI test checklist for Settings toggle + completed tool replay.

## Capabilities

### New Capabilities

<!-- None -->

### Modified Capabilities

- `acp-session-chat`: Align default tool-call expansion with the live-diff preference and with thought-style “expanded by default” for users who opt into live diffs.

## Impact

- `AgentChatTimeline.kt` — `syncCompletedToolCollapse` (signature and rules).
- `AgentSessionScreen.kt` — pass `showLiveToolDiffs` into sync; ensure `LifecycleResume` re-runs defaults for newly seen tools after Settings change.
- `AcpChatPrefs` / Settings — no new setting; clarify copy optional (“also keeps file tools expanded”).
- Tests under `app/src/test/...` for collapse policy.
- No ACP protocol, persistence, or diff-ingest changes.

## Out of scope

- Persisting per-tool collapse across app restarts.
- Changing hunk rendering, presentation cache, or git diff styling.
- Auto-expanding non-file tools (shell, grep text-only, etc.) when live diffs are on—only tools with `fileDiffs.isNotEmpty()` unless we later broaden the heuristic.
