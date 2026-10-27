# Design

## Context

See `proposal.md` for motivation. Workflows is a section list (`WorkflowsSection`: Automation, More) in `AutomationScreens.kt`, with destinations in `MainActivity`. Isolated git copies already exist: `GitWorkspace.provisionWorkspace` clones golden onto a branch; `WorkspaceStore` records them. Coding ACP keys are `SessionKeys.compose(folderId, workspaceId, sessionId)`, and `AgentSessionScreen` bails out if `WorkspaceStore.guestPath` is null. Automation already uses a **stable** extra key (`AutomationSessionContext.key`) plus `automationPromptPrefix`. GitHub HTTP exists only in `UpdateManager` (unauthenticated `HttpURLConnection`). Session-copy `origin` is often a filesystem path; `SessionCopyOriginSync` already maps that back to golden’s network origin.

## Goals / Non-Goals

**Goals:**

- Wire a Workflows **PR Reviewer** list and session screen onto existing ACP + session-copy machinery.
- Keep one ACP `sessionKey` (and timeline) for a review session across copy create / switch / Done / recreate.
- Never point the agent cwd at golden.

**Non-Goals:**

- Changing how coding **New workspace** / branch picker works.
- Push, GitHub OAuth, non-GitHub hosts, PR inbox UI.
- A structured issue-picker widget (v1 is chat: the agent lists issues and the user replies).

## Decisions

### 1. Review sessions are a flat list, not nested “projects”

**Choice:** Persist `PrReviewSession` records (id, title, pasted URL, owner/repo, number, head branch, coding `folderId`, optional `workspaceId`) in a DataStore JSON store parallel to `AutomationStore`.

**Why:** Automation’s project/session nesting is for many browser profiles. A review is one chat bound to PRs. Extra project grouping adds UI without a user request.

**Alternative:** Reuse `AutomationProject` with a type flag — mixes browser isolation, read-only links, and git copies.

### 2. Stable ACP session key, ephemeral workspace id

**Choice:** Review chat key is `pr-review/{reviewSessionId}` (constant). `workspaceId` on the record is only the current `WorkspaceStore` copy (or null after Done). Extend `AgentSessionScreen` / `AcpClient` so cwd can change without changing `sessionKey`: dispose and recreate the client with the same key and new `workspaceGuestDir`, reloading the saved timeline.

After Done, cwd is a small durable stub dir (`/root/.termfold/pr-reviews/{id}/`) so the screen does not show “workspace unavailable” and Files does not open a deleted clone. Next PR provisions a new copy, updates `workspaceId`, recreates the client onto that guest path.

**Why:** Today `sessionKey` includes `workspaceId`. Done + new copy would otherwise be a new chat. Golden fallback is forbidden.

**Alternative:** Keep the copy directory forever and only “logically” Done — violates delete-on-Done.

### 3. Auto-provision without the New workspace dialog

**Choice:** After a successful GitHub PR fetch and unique origin match, call `GitWorkspace.provisionWorkspace(golden, headBranch, newSessionId)` with the same progress UI pattern as `WorkspaceDialogs` (blocking percent + cancel). No branch list. Hide the composer branch picker on review sessions so the user cannot switch off the PR head.

Fetch missing head: `git fetch` on golden (network origin), then provision. Match origin by normalizing to `github.com/owner/repo` lowercase (strip `.git`, `https://`, `git@`, `ssh://git@`).

**Ambiguous match:** If two folders share that origin, fail with a clear error (do not pick silently).

**Second PR, same session, same repo, copy still present:** `switchToBranch` on the existing copy (fetch first). If the copy is dirty, **refuse** the switch and tell the user to Done or discard; do not stash silently.

**Second PR, different repo, copy still present:** refuse; session stays bound to the matched folder until Done.

### 4. GitHub metadata without auth UI

**Choice:** Parse `https://github.com/{owner}/{repo}/pull/{n}` (allow extra path/query). `GET https://api.github.com/repos/{owner}/{repo}/pulls/{n}` like `UpdateManager`. Read `head.ref`, `head.repo`, `title`. 401/404 → clear error, no copy.

**Alternative:** `gh` in the guest — needs auth and a binary; worse for v1 public PRs.

### 5. Review protocol is prompt, not a new tool

**Choice:** `prReviewPromptPrefix` injects owner/repo, number, URL, head branch, and standing instructions: read the full diff/tree, list every issue, ask which to fix, do not apply until the user chooses. User replies in chat.

**Why:** Matches the spec without a second UI. Fixes stay on the copy; no push.

### 6. Done

**Choice:** Header action **Done**. If copy missing, no-op besides staying on chat. If dirty or `git status`/`rev-list` shows unpushed commits vs upstream, confirm then delete host tree + `WorkspaceStore.remove`. Timeline files stay keyed by `pr-review/{id}`.

## Risks / Trade-offs

- **[Full-tree clone on phone]** → Same cost as New workspace; show progress and allow cancel (delete partial copy).
- **[Unauthenticated API rate limits / private PRs]** → Explicit error; no silent retry loop.
- **[ACP client restart on cwd change]** → Must reload timeline by stable key; test Done then continue chat.
- **[Clone origin is golden path]** → Reuse `SessionCopyOriginSync` so fetch/unpushed checks use GitHub, not the local golden URL.
- **[User uses branch chip]** → Hide it on review sessions.

## Migration Plan

New store and UI only. No migration of existing folders or automation data. Rollback is unused UI + unused DataStore key.

## Open Questions

None that affect specs or this approach. GitHub token settings can be a later change.
