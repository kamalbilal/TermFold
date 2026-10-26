# Tasks

## 1. GitHub facts for the pack

- [x] 1.1 Extend `GitHubPrInfo` / `GitHubPrApi` to include PR body and base branch (`gh pr view --json` and HTTP parse) and verify unit tests cover both parsers
- [x] 1.2 Fetch issue + review comments (`gh api` or REST) into a bounded markdown blob and verify tests for empty, truncated, and mixed comment lists

## 2. Facts pack on the temp copy

- [x] 2.1 Add a facts-pack writer: `.termfold-pr-review/` with `pr.diff`, `files.txt`, `pr.md`, `comments.md`, plus `.git/info/exclude`, and verify a temp git repo test: files exist and `git status` is clean
- [x] 2.2 After provision and after same-session PR switch, write/replace the pack using GitHub `base.ref` for merge-base, and verify bind/integration coverage that the pack is refreshed for the new PR
- [x] 2.3 If the diff is empty or merge-base fails, still write the files with an explicit note and verify that case in the writer test

## 3. Structured report parse

- [x] 3.1 Add report model + parser for the `pr-review-report` JSON fence (schemaVersion 1, surfaces, severity, inDiff) and verify valid JSON, missing fence, invalid JSON, and thought-text ignored
- [x] 3.2 Persist the last valid parsed report on `PrReviewSession` and verify encode/decode round-trip

## 4. Prompt protocol

- [x] 4.1 Expand `PrReviewPrompt.prefix` with facts-pack paths, schema, surfaces (ios only if needed), wait-for-ids, no web unless the user asks, and verify the unit test asserts those rules

## 5. Cards in PR Reviewer

- [x] 5.1 Render cards on `PrReviewAgentSessionScreen` (summary/risk, in-diff by surface, out-of-diff, coverage, missing tests/questions) from the parsed report and verify Compose/logic tests or a screenshot-level preview of grouping
- [x] 5.2 When a review turn completes, parse ACP non-thought agent text, save a valid report, or show missing/invalid without dropping chat, and verify both paths

## 6. Wire-up

- [x] 6.1 Hook facts-pack write into the existing PR bind job (after copy ready, before the user is expected to review) and verify a debug build (`assembleDebug`) compiles
