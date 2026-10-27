# Tasks

## 1. Upstream reconciliation core

- [x] 1.1 Add `reconcileBranchUpstream` in `GitWorkspace` (managed session paths only) and call it after successful `switchToBranch` and at end of `provisionWorkspace`; verify with a JVM git test that clones golden with local-only `main-dev` tracking and asserts upstream is unset after repair.

## 2. Lazy repair for existing copies

- [x] 2.1 Invoke reconciliation once per guest path on first `loadChangeSummary` (or dedicated hook) for managed session copies; verify test or manual script that a pre-broken config is fixed without reprovisioning.

## 3. UI and changes pane

- [x] 3.1 Confirm `GitChangesPane` sync row hides when upstream is null after repair; add or adjust unit test on `GitChangeSummary` if needed.

## 4. Integration

- [x] 4.1 Run `./gradlew :app:testDebugUnitTest --tests "com.termfold.app.gitworkspace.*"` and fix regressions; on device, create session copy from a local-only branch and confirm `git pull` no longer reports missing `main-dev` ref (may report no tracking branch instead).

## Workflow follow-up

- Archive with `/opsx-archive` after review and merge.
