# Tasks

## 1. Diff synthesis and parsing

- [x] 1.1 Add `synthesizeUnifiedDiff(path, oldText, newText)` with add/delete/modify cases and unit tests in `app/src/test/java/com/termfold/app/gitworkspace/` verifying `parseUnifiedDiff` accepts the output
- [x] 1.2 Add helper to split multi-file `git_patch` text into per-file patch strings (by `diff --git` sections) with unit tests for two-file patches

## 2. ACP model and ingest

- [x] 2.1 Introduce `AcpToolFileDiff` (or equivalent) and add `fileDiffs` to `AcpItem.ToolCall` while keeping `detail` for plain text
- [x] 2.2 Replace `toolContentText` diff branch with structured parsing for v2 (`changes`, `patch`) and v1 (`path`, `oldText`, `newText`) including synthesis when patch missing; verify with unit tests using sample JSON fixtures
- [x] 2.3 Update `toolPaths()` to collect v2 `changes[].path` and `oldPath`; add test or fixture coverage
- [x] 2.4 Wire `tool_call` / `tool_call_update` handlers to merge `fileDiffs` and debounce presentation cache updates while `in_progress`

## 3. Timeline persistence

- [x] 3.1 Extend `AcpTimelineCodec` encode/decode for `fileDiffs`; verify round-trip test loads identical tool-call diffs
- [x] 3.2 Confirm legacy tool JSON without `fileDiffs` still decodes and displays `detail` only

## 4. Presentation cache

- [x] 4.1 Add session-scoped cache that runs `prepareDiffPresentation` off the main thread keyed by tool call and file; expose `+`/`-` counts for headers
- [x] 4.2 Apply patch size cap and truncation marker consistent with design

## 5. Chat UI

- [x] 5.1 Refactor `ToolCallRow` expanded state: per-file collapsible headers; inner bounded `LazyColumn` of `GitDiffDisplayRow` for expanded files only
- [x] 5.2 Respect `AcpChatPrefs.showLiveToolDiffs`: suppress hunk lists for `in_progress` when off; show final diffs when completed
- [x] 5.3 Remove full-patch `verticalScroll` composition for diff content; manually verify smooth scroll with a session containing many tool calls (or add a lightweight UI test if feasible)

## 6. Settings and strings

- [x] 6.1 Add `AcpChatPrefs` with default `showLiveToolDiffs = true`
- [x] 6.2 Add Agent chat `SwitchRow` on `SettingsScreen` and strings in `values/strings.xml`; verify toggle persists across restart

## 7. Verification

- [x] 7.1 Run `./gradlew :app:testDebugUnitTest` and fix any failures
- [x] 7.2 Run `openspec validate acp-tool-call-file-diffs --strict` and resolve validation issues
