# Tasks

## 1. Git summary and parsing

- [x] 1.1 Add porcelain/numstat parsers and tests in `app/src/test/.../GitChangeSummaryTest.kt`; verify staged vs unstaged split matches VS Code rules
- [x] 1.2 Add `GitWorkspace.loadChangeSummary` and `loadUnifiedDiff` using guest git; verify with unit tests for parsers and compile

## 2. Pane shell (v1)

- [x] 2.1 Add `GitChanges` state object and `GitChangesPane` composable (sections, rows, sync counts, refresh, close); verify compile
- [x] 2.2 Wire `GitChangesPane` into `MainActivity` like `PreviewPane` (wide + overlay); verify pane opens/closes

## 3. Agent entry (v1)

- [x] 3.1 Add Changes item to `SessionHeaderOverflowMenu` when repo is git; hide when not; verify menu visibility logic
- [x] 3.2 Lazy diff on row tap with +/- line styling; add strings; device QA on TECNO: staged/unstaged lists and diff match `git status` / VS Code on same repo

## 4. GitHub-style UI (follow-up)

- [x] 4.1 Add `UnifiedDiffParser` + unit tests (hunks, add/remove/context lines); verify parser tests pass
- [x] 4.2 Refactor `GitChangesPane` to sidebar (colored status rows) + main column with collapsible per-file cards; wide/narrow layouts; verify compile
- [x] 4.3 Implement side-by-side diff rows with red/green line backgrounds, gutters, and `@@` hunk headers; unified fallback on narrow width; verify on device against GitHub/VS Code reference for same file

## 5. Diff performance (GitHub-style)

- [x] 5.1 Cache parsed diff display rows per file on load; avoid re-parse on scroll/recompose
- [x] 5.2 Fold long unchanged context runs with tap-to-expand rows
- [x] 5.3 Bound concurrent `git diff` loads when many files are expanded

## Workflow follow-up

- Archive with `/opsx-archive git-changes-viewer` after section 4 device QA.
