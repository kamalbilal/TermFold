# Proposal

## Why

Session workspaces are isolated git copies under `.termfold/sessions/<id>/`, but the Files sidebar still roots at the golden project folder. Users edit and browse the wrong tree and can accidentally commit TermFold session metadata. The Files panel must match the active workspace, and `.termfold` must stay out of version control when the project already uses `.gitignore`.

## What Changes

- Scope the Files sidebar and editor to the **active session workspace** host path when `workspaceId` is not golden; golden sessions keep today’s project root.
- When creating or using a session copy, **idempotently** ensure `.termfold/` is listed in the repo’s `.gitignore` if that file already exists (golden tree and session copy each checked on their own root).
- No change to agent cwd, Source Control pane, or terminal workspace resolution (they already use `guestDir`).

## Capabilities

### New Capabilities

- `workspace-files`: Files panel roots at the active workspace (golden vs session copy) and related UI labels.
- `git-workspace-sessions`: TermFold session storage hygiene so `.termfold/` is not committed when `.gitignore` is present.

### Modified Capabilities

- (none)

## Impact

- `MainActivity.kt` — pass workspace id or resolved host root into `FilesWorkspace` for terminal/agent destinations.
- `FilesPanel.kt` (`FilesWorkspace`, `FileTree`) — optional workspace root override instead of always `folder.path`.
- `GitWorkspace.kt` / `ProjectWorkspaces.kt` — gitignore helper invoked from session provisioning (and golden when sessions dir is first ensured).
- Unit tests for gitignore idempotency and files-root resolution.
