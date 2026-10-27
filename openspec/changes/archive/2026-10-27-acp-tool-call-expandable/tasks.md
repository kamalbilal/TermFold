# Tasks

## 1. ToolCallRow interaction

- [x] 1.1 Refactor `ToolCallRow` to use `collapsed` default `false` (expanded), always-enabled header `clickable`, and chevron on every tool call; verify tap toggles body visibility
- [x] 1.2 Remove `LaunchedEffect(hasDetail)` that forces expand when detail arrives; verify user-collapsed row stays collapsed during streaming updates

## 2. Header and body layout

- [x] 2.1 Split header vs body: collapsed header shows one-line preview (reuse `tickerText` on first line of label or ellipsis); expanded body shows full `toolLabel()` with wrap and no header `maxLines` clip; verify long path/title is fully readable when expanded
- [x] 2.2 When expanded, keep icon and in-progress/failed styling in header without repeating full label in header row; verify layout matches Thought-row density

## 3. Body content

- [x] 3.1 Render `ToolCallDiffSection` and detail `Text` only when `!collapsed`, unchanged ordering; verify tools with only title still expand to show full label
- [x] 3.2 Add or reuse strings for tool row label if needed; verify no missing `stringResource` at compile time

## 4. Verification

- [x] 4.1 Run `./gradlew :app:compileDebugKotlin` and fix any errors
- [x] 4.2 Manually verify on device: collapsed tool shows one line, expanded shows full output/diffs; run `openspec validate acp-tool-call-expandable --strict`
