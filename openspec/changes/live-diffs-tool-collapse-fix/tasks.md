# Tasks

## 1. Collapse sync policy

- [ ] 1.1 Extend `syncCompletedToolCollapse` to accept `userToggledToolIds: Set<Long>` and re-apply `defaultToolCallCollapsed` for ids not in that set when `items` or `showLiveToolDiffs` change; verify with new unit tests in `AgentChatTimelineTest` (late `fileDiffs`, user toggle blocks auto-expand).
- [ ] 1.2 Add unit test: completed tool with empty `fileDiffs` defaults collapsed, then same id with `fileDiffs` + live diffs on → sync sets expanded when not user-toggled.

## 2. UI wiring

- [ ] 2.1 Add `userToggledToolIds` state in `AgentSessionScreen` `Timeline`; record id on `ToolCallHeaderRow.onToggle` only.
- [ ] 2.2 Pass `userToggledToolIds` into `syncCompletedToolCollapse` inside existing `LaunchedEffect(items, showLiveToolDiffs)`; manually verify on device: enable live diffs, run agent edit tool, rows expand without per-tool taps.

## 3. Settings copy (optional)

- [ ] 3.1 Update `settings_agent_chat_live_diffs` string to mention expanded file tools in chat; verify string resource lint / preview in Settings.

## Workflow follow-up

- Archive the change after review and sync delta into main `openspec/specs/acp-session-chat/spec.md`.
