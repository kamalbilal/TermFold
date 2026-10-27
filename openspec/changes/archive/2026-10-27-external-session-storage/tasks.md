# Tasks



## 1. Path resolution



- [x] 1.1 Add guest constants and `projectKey(goldenGuestPath)` (normalized path → stable key) in `ProjectWorkspaces` and verify unit tests for key stability and distinct golden paths

- [x] 1.2 Implement `sessionGuestPath` → external `/root/.termfold/project-sessions/<key>/<id>` and `sessionsRoot` for the key directory; verify tests assert new paths are not under the golden guest path

- [x] 1.3 Extend `isManagedSessionPath` to accept legacy `<golden>/.termfold/sessions/<id>` and external paths; verify tests for golden, legacy session, external session, and unrelated paths



## 2. Provisioning and deletion



- [x] 2.1 Update `GitWorkspace` provision/move/delete to use new host paths via `Projects.hostPath`; verify `GitWorkspaceCloneTest` / provision tests still pass with updated expected guest strings

- [x] 2.2 Ensure `hostSessionDir` and `WorkspaceDialogs` session creation use the resolver (no hard-coded `.termfold/sessions`); verify compile and existing workspace dialog tests if any

- [x] 2.3 Keep `TermfoldGitignore.ensureTermfoldIgnored` on golden + session copy after provision; verify `TermfoldGitignoreTest` unchanged behavior



## 3. Legacy migration (optional spec)



- [x] 3.1 On workspace record load/open, if guest path matches legacy in-repo pattern and external target is free, move host tree and persist updated `guestPath`; verify unit test with temp dirs for success and failure (legacy path retained on failure)

- [x] 3.2 Add test that a record already pointing at external path is not migrated again



## 4. Verification



- [x] 4.1 Run `./gradlew :app:testDebugUnitTest --tests "com.termfold.app.gitworkspace.*"` and confirm all gitworkspace tests pass

- [ ] 4.2 Manual smoke on device: create session workspace, confirm guest path under `/root/.termfold/project-sessions/`, run `npm run lint` from golden and confirm file count does not double when a session clone exists



## Workflow follow-up



- On archive: sync delta into `openspec/specs/git-workspace-sessions/spec.md` and update **Purpose** to describe external session storage (not in-repo `.termfold/sessions` for new copies).


