# Proposal

## Why

TermFold can isolate agent work in session copies, but only after the user picks a branch in **New workspace**. Reviewing a GitHub pull request on the phone still means manual checkout, risk of editing the golden tree, and no dedicated place that reports issues and waits for which ones to fix. A Workflows **PR Reviewer** lane should take a pasted PR link, bind to the matching local project and PR head branch automatically, and keep chat after the temp copy is deleted.

## What Changes

- Add a **PR Reviewer** section on the Workflows home list (alongside Automation and More).
- v1 start is **paste a GitHub pull request URL** only (no PR browser, no `#123` picker, no branch picker).
- Resolve `owner/repo` and PR number from the URL; match a coding folder whose git `origin` is that repo (HTTPS, `.git` suffix, and SSH `git@github.com:owner/repo.git` count as the same repo).
- If the URL is not a GitHub PR link, or no local folder matches, **fail with a clear error**. Do not clone a new golden project.
- Fetch the PR head branch (from GitHub metadata, then git fetch into golden as needed) and **auto-provision** a temp session copy on that branch using existing `GitWorkspace.provisionWorkspace`. Never write the golden checkout.
- Open an ACP review chat on that copy. The agent reviews deeply, lists every issue found, and **asks which to fix** before applying changes.
- Reuse the same temp copy for another PR in that review session (same repo: fetch + switch to the new PR head). Do not provision a second copy while one still exists for the session.
- **Done** on the review session deletes the temp copy (with confirm if dirty or unpushed) and **keeps** the ACP session and chat. A new pasted PR after Done auto-creates a new copy.
- v1 does **not** push commits; GitHub auth UI for private PRs is out of scope (unauthenticated fetch; private/API failure is a clear error).

## Capabilities

### New Capabilities

- `pr-reviewer`: Workflows PR Reviewer section, paste-URL start, origin matching, auto temp workspace on PR head, reuse/Done lifecycle, and review chat that reports issues then waits for which to apply.

### Modified Capabilities

- `workspace-files`: While a review copy exists, Files roots at that copy; after Done, Files must not browse a deleted tree.
- `acp-session-chat`: Review sessions inject PR identity into turns and persist the timeline across workspace delete.

## Impact

- Workflows UI (`AutomationScreens.kt` section enum, `MainActivity` destinations)
- New review session store (parallel to `AutomationStore`) and navigation
- GitHub PR URL parse + public API fetch; origin URL normalize + match against folder git remotes
- `GitWorkspace.provisionWorkspace` / `WorkspaceStore` for ephemeral copies keyed by review session
- ACP session binding (`sessionKey`, guest cwd), prompt assembly, header **Done**
- Tests: URL parse, origin match, fail paths, reuse vs new copy after Done
- No Gradle dependency expected if GitHub JSON is fetched like `UpdateManager` (`HttpURLConnection`)
