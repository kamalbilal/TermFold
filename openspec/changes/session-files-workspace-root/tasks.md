# Tasks

## 1. Gitignore hygiene

- [x] 1.1 Add `TermfoldGitignore.ensureTermfoldIgnored(File)` with idempotent `.termfold/` append; unit tests for missing file, already present, and append cases
- [x] 1.2 Call ensure from `GitWorkspace.provisionWorkspace` on golden host and dest host after successful provision; verify tests pass

## 2. Files workspace root

- [x] 2.1 Extend `FilesWorkspace` to accept optional host root override (default golden `folder.path` via `Projects.hostPath`); verify compile
- [x] 2.2 Resolve session host root in `MainActivity` for `Destination.Terminal` from `workspaceId` + `WorkspaceStore.guestPath`; pass into `FilesWorkspace`; verify agent session Files tree excludes golden siblings on device or emulator
- [x] 2.3 Add session-workspace label in Files header when override is active; add string resource; verify UI on session copy

## 3. Regression checks

- [x] 3.1 Golden workspace session and folder detail Files still root at golden project; manual or UI test note in PR
- [x] 3.2 Run `:app:testDebugUnitTest` for new gitignore tests and any path helper tests

## Workflow follow-up

- Archive with `/opsx-archive session-files-workspace-root` after device QA on a session copy.
