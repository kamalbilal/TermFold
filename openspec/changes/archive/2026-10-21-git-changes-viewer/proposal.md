# Proposal

## Why

Agent sessions change files in the git workspace, but reviewing those changes needs to feel like VS Code Source Control plus a GitHub-style diff viewer. The first pane layout (single column, tap-to-navigate unified diff) is hard to scan and does not show add/delete/edit at a glance.

## What Changes

- Keep **Source Control** entry in the agent session header overflow (⋮) and guest-git data layer (staged / Changes sections, gitignore, sync counts).
- **Redesign the pane UI:**
  - **Sidebar** listing changed files (staged and unstaged sections), with **status color** per row (green added, red deleted, amber modified, neutral renamed).
  - **Main area** showing one or more **collapsible file cards**. Each card header is the **filename** (and path hint), expand/collapse, and +/- stats; body is the **diff**.
  - **Diff presentation** matches GitHub / VS Code split review: **side-by-side** panes where practical, line numbers, **reddish background** on removed lines, **greenish background** on added lines, dim context lines, `@@` hunk headers.
- Lazy-load diff text per file when a card is expanded (or when selected in sidebar on narrow layouts).
- Wide layout: sidebar + main diff column together. Narrow layout: sidebar list with selection opening the collapsible diff (back to list).
- Hide menu entry when guest path is not a git work tree.

## Capabilities

### New Capabilities

- `git-workspace-changes`: VS Code–like source control listing and GitHub-style diff viewing for the active session workspace.

### Modified Capabilities

<!-- none — capability not yet merged to main specs -->

## Impact

- Rework `GitChangesPane` layout and diff rendering; add unified-diff parser → side-by-side model (pure Kotlin).
- Optional `Palette` diff tokens (add/remove/hunk header).
- Existing `GitWorkspace` summary APIs stay; may add `loadUnifiedDiff` options if needed for rename/binary.
- `MainActivity` shell unchanged; inner pane UX changes.
