# Design

## Context

`FilesWorkspace` in `FilesPanel.kt` sets `root = File(folder.path)`, which is always the golden guest project path. `AgentSessionScreen` and `TerminalScreen` already resolve `WorkspaceStore.guestPath(folder, workspaceId)` and `Projects.hostPath` for agent, git, and shell work. `MainActivity` wraps sessions in `FilesWorkspace(folder = workspaceFolder)` without `workspaceId`. Session copies live at `{goldenGuest}/.termfold/sessions/{workspaceId}/` per `ProjectWorkspaces`.

## Goals / Non-Goals

**Goals:**

- Pass resolved **host** filesystem root into `FilesWorkspace` for `Destination.Terminal` (agent and shell).
- Keep golden root for `Destination.Folder` and golden `workspaceId`.
- Idempotent `.termfold/` gitignore maintenance on golden and session copy when `.gitignore` exists.

**Non-Goals:**

- Creating `.gitignore` when absent (per product decision).
- Hiding `.termfold` inside a session copy tree (copy root has no parent `.termfold` sibling in the tree).
- Changing global Files tab (`FileManagerScreen`) behavior.

## Decisions

### 1. Workspace root via `FilesWorkspace` parameter

Add an optional `filesRootHostPath: String?` (or `workspaceId` + folder resolved inside composable). **Preferred:** resolve in `MainActivity` for `Destination.Terminal` using the same helper as `TerminalScreen`:

`Projects.hostPath(context, WorkspaceStore.guestPath(context, folder, workspaceId)!!).absolutePath`

Pass `null` to mean “use `folder.path` host path” for folder detail and golden sessions.

**Alternative:** thread `workspaceId` into `FilesWorkspace` and resolve internally — rejected to keep `FilesWorkspace` free of Android context in tree logic and match one resolution site.

### 2. Folder detail screen unchanged

`Destination.Folder` continues to pass only golden `folder.path` so browsing from the session list still shows the shared project.

### 3. Gitignore helper location

Add `TermfoldGitignore.ensureTermfoldIgnored(hostProjectDir: File)` in `gitworkspace` (e.g. `TermfoldGitignore.kt`):

- If `.gitignore` missing → no-op.
- Read lines; if any line matches `.termfold`, `.termfold/`, or `**/.termfold/**` (normalized trim) → no-op.
- Else append newline + `.termfold/`.

Call from `GitWorkspace.provisionWorkspace` after successful clone/move:

1. `ensureTermfoldIgnored(goldenHost)`
2. `ensureTermfoldIgnored(destHost)`

**Alternative:** only golden — rejected; user asked for copy as well.

### 4. Files panel label

When `filesRootHostPath != folder.path` host equivalent, show subtitle like “Session workspace” or basename of session id in `FileTree` header (string resource).

## Risks / Trade-offs

- **[Risk]** User expects folder-level Files to open session copy → **Mitigation:** only terminal/agent destination gets override; folder screen documented in spec.
- **[Risk]** Appending gitignore touches user repo → **Mitigation:** only when file exists; idempotent; limited to `.termfold/`.
- **[Risk]** `folder.path` is guest string, override is host path → **Mitigation:** `FileTree` already uses host `File` paths; document that both roots must be host absolute paths from `Projects.hostPath`.

## Migration Plan

Ship in one release. No data migration. Existing session copies get gitignore on next provision only; optional follow-up task to run ensure on app upgrade is out of scope unless needed.

## Open Questions

None.
