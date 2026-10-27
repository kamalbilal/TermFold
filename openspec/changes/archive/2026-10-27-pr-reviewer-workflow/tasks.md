# Tasks

## 1. PR URL, origin match, GitHub fetch

- [x] 1.1 Add GitHub PR URL parser (owner, repo, number; extra path/query allowed) and verify unit tests accept `https://github.com/owner/repo/pull/123` and reject non-PR text
- [x] 1.2 Add origin normalizer to `github.com/owner/repo` and verify HTTPS, `.git`, and `git@github.com:owner/repo.git` match; no-match and two-folder clash covered
- [x] 1.3 Add unauthenticated GitHub PR JSON fetch (`head.ref`, title) and verify 401/404 maps to a clear error object without creating sessions

## 2. Review session store and keys

- [x] 2.1 Add `PrReviewSession` model + DataStore JSON CRUD (url, folderId, optional workspaceId) and verify encode/decode round-trip
- [x] 2.2 Add stable ACP key `pr-review/{id}` and durable stub guest dir under `/root/.termfold/pr-reviews/{id}/`; verify key does not include workspace id
- [x] 2.3 Ensure stub dir is created on session create; verify it exists on disk after create

## 3. Auto copy, reuse, Done

- [x] 3.1 Orchestrate unique origin match → fetch PR → fetch missing head on golden → `provisionWorkspace` on head branch; verify a JVM/git test copy is on that branch and golden working tree is unchanged
- [x] 3.2 Reuse existing copy for a second PR on the same repo (`switchToBranch` after fetch); verify no second `WorkspaceStore` id is created
- [x] 3.3 Refuse switch when the copy is dirty or the new PR is a different repo; verify error messages, copy id unchanged
- [x] 3.4 Implement Done: delete copy + `WorkspaceStore.remove` when clean; confirm dialog when dirty/unpushed; no-op when copy already gone; verify timeline key still `pr-review/{id}`

## 4. Workflows UI

- [x] 4.1 Add `WorkflowsSection.PR_REVIEWER` list row and section screen with session list + paste-URL field; verify it is distinct from Automation and More
- [x] 4.2 Add destinations for review list and review session in `MainActivity`; verify back stack returns to PR Reviewer
- [x] 4.3 Show parse/match/API/provision errors inline; verify invalid URL and missing local repo never open chat
- [x] 4.4 Show clone progress with cancel (same pattern as New workspace); verify cancel deletes a partial copy

## 5. ACP review session

- [x] 5.1 Open review chat with stable session key, cwd = temp copy (never golden), prompt prefix with PR identity + “list issues then wait”; verify prefix unit test contains repo, number, head branch
- [x] 5.2 Hide composer branch picker on review sessions; verify the chip/picker is not shown
- [x] 5.3 Recreate `AcpClient` on cwd change without changing session key; verify saved timeline reloads after Done then a new PR
- [x] 5.4 Files roots at the copy while it exists and at the stub (not a deleted path) after Done; verify both roots
- [x] 5.5 Header **Done** wired to 3.4; verify cancel on dirty confirm leaves the copy

## 6. Integration

- [x] 6.1 `./gradlew :app:testDebugUnitTest` for new tests plus assembleDebug with aapt2 override; verify compile and unit tests pass
