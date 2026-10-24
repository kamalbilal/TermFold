# Proposal

## Why

Users who enable **Show live file diffs** still see edit and other file-change tool calls collapsed by default and must expand each row manually. The prior `acp-live-diffs-expand-tools` work added `defaultToolCallCollapsed` and first-seen sync, but collapse state is frozen on the first timeline frame. ACP often delivers `completed` tool rows before structured `fileDiffs` are parsed, so the UI records `collapsed = true` and never revises it when diffs arrive—violating the shipped `acp-session-chat` requirement that live diffs on keeps file tools expanded by default.

## What Changes

- Extend collapse sync so **non-user-toggled** tool rows re-apply the live-diff default when structured `fileDiffs` become available or when a tool transitions to a terminal status with diffs present.
- Track **explicit user expand/collapse** per tool-call row id so automatic policy updates never override a deliberate tap.
- Add unit tests for late-arriving `fileDiffs`, completion transitions, and user-toggle stickiness.
- Optionally tighten Settings copy so “Show live file diffs” mentions expanded file tools in chat (copy-only).

## Capabilities

### New Capabilities

<!-- None -->

### Modified Capabilities

- `acp-session-chat`: Clarify that default expansion applies when file diffs appear asynchronously, not only on the first frame the tool id is seen.

## Impact

- `AgentChatTimeline.kt` — collapse sync policy and helpers.
- `AgentSessionScreen.kt` — user-toggle tracking alongside `collapsedTools`; wire into sync `LaunchedEffect`.
- `app/src/test/.../AgentChatTimelineTest.kt` — new cases for late diffs and user overrides.
- `strings.xml` — optional description tweak for the live-diffs setting.
- No ACP protocol, timeline codec, or diff-ingest changes.

## Out of scope

- Bulk re-expanding every historical tool when the user turns live diffs on mid-session (only tools without a user toggle may pick up new defaults).
- Auto-expanding non-file tools (shell, grep-only) when live diffs are on.
- Persisting collapse preferences across app restarts.
