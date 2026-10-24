# Tasks

## 1. Collapse policy

- [x] 1.1 Add `defaultToolCallCollapsed(tool: AcpItem.ToolCall, showLiveToolDiffs: Boolean): Boolean` in `AgentChatTimeline.kt` (or small `AcpToolCallCollapse.kt` if preferred)
- [x] 1.2 Update `syncCompletedToolCollapse` to accept `showLiveToolDiffs` and use the helper for first-seen tool ids only (preserve existing map entries)

## 2. Wire preference in UI

- [x] 2.1 Pass `showLiveToolDiffs` from `Timeline` / `AgentSessionScreen` into `syncCompletedToolCollapse` inside `LaunchedEffect(items, showLiveToolDiffs)`
- [x] 2.2 Confirm `LifecycleResume` already refreshes `showLiveToolDiffs`; re-run sync when the flag changes so newly arriving tools pick up the new default

## 3. Tests

- [x] 3.1 Unit tests: in_progress → always expanded default
- [x] 3.2 Unit tests: completed + fileDiffs + live diffs on → collapsed false
- [x] 3.3 Unit tests: completed + fileDiffs + live diffs off → collapsed true
- [x] 3.4 Unit tests: completed + no fileDiffs → collapsed true regardless of setting
- [x] 3.5 Unit tests: `syncCompletedToolCollapse` does not overwrite existing map keys

## 4. Copy and manual QA

- [x] 4.1 (Optional) Update Settings string for live diffs to mention expanded file tools in chat
- [ ] 4.2 Manual: live diffs on → run agent edit → tool stays expanded after complete; collapse one → stays collapsed
- [ ] 4.3 Manual: live diffs off → completed file tool collapsed by default; expand shows hunks

## 5. OpenSpec hygiene

- [x] 5.1 After ship, archive change and sync delta into `openspec/specs/acp-session-chat/spec.md` when that main spec exists (or fold into first archive of session-chat specs)
