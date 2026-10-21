# Spec Delta

## Purpose

VS Code–style source control for TermFold git session workspaces: staged and unstaged file lists with per-file diffs, using the same git rules as the CLI (including gitignore).

## ADDED Requirements

### Requirement: Open changes pane from agent header

The system SHALL provide a Changes action in the agent session header overflow menu when the session workspace is a git repository, opening a dedicated changes pane without leaving the session.

#### Scenario: Git workspace

- **WHEN** the user opens the overflow menu on an agent session backed by a git work tree
- **THEN** a Changes menu item is available and opens the changes pane for that workspace

#### Scenario: Non-repository

- **WHEN** the session workspace is not inside a git work tree
- **THEN** the Changes menu item is not shown

### Requirement: VS Code staged and unstaged sections

The system SHALL list repository changes in two sections, **Staged Changes** and **Changes**, matching VS Code Source Control: staged files from the index and unstaged modifications plus non-ignored untracked files in Changes.

#### Scenario: Staged file

- **WHEN** a file is added to the index with `git add`
- **THEN** it appears under Staged Changes and not under Changes

#### Scenario: Unstaged modification

- **WHEN** a tracked file is modified but not staged
- **THEN** it appears under Changes

#### Scenario: Ignored path

- **WHEN** a path is ignored by gitignore
- **THEN** it does not appear in either section

### Requirement: File row shows status and line stats

The system SHALL show each changed file path with a status indicator (added, modified, deleted, renamed) and SHALL show added and removed line counts when git reports them.

#### Scenario: Modified file with diff

- **WHEN** a tracked file has line changes
- **THEN** the row shows the path and non-negative added and removed line counts

### Requirement: Sidebar file list with status colors

The system SHALL show changed files in a sidebar with distinct visual treatment for added (green), deleted (red), and modified (amber) files, plus basename and optional parent path.

#### Scenario: Added file in sidebar

- **WHEN** an untracked or newly added file appears under Changes
- **THEN** the sidebar row uses a green status indicator for that file

#### Scenario: Deleted file in sidebar

- **WHEN** a deleted file appears in the change list
- **THEN** the sidebar row uses a red status indicator for that file

### Requirement: Collapsible per-file diff cards

The system SHALL show each changed file in the main area as a collapsible container whose title is the filename and whose body is the diff content for that file.

#### Scenario: Expand file card

- **WHEN** the user expands a file container or selects that file in the sidebar
- **THEN** the diff for that file loads if not already loaded and displays inside the expanded container

#### Scenario: Collapse file card

- **WHEN** the user collapses a file container
- **THEN** the diff body is hidden while the filename header remains visible

### Requirement: GitHub-style diff highlighting

The system SHALL render diffs with side-by-side panes when space allows, line numbers in gutters, and line backgrounds where removals use a reddish tone and additions use a greenish tone.

#### Scenario: Removed line

- **WHEN** a diff hunk contains a removed line
- **THEN** that line is shown with a reddish background in the old (left) column

#### Scenario: Added line

- **WHEN** a diff hunk contains an added line
- **THEN** that line is shown with a greenish background in the new (right) column

### Requirement: Lazy per-file diff

The system SHALL fetch unified diff text only when a file card is expanded or selected, using staged diff for staged files and working-tree diff for unstaged files.

#### Scenario: Select unstaged file

- **WHEN** the user selects a file listed under Changes
- **THEN** the pane shows a diff of that file against HEAD inside that file's container

#### Scenario: Select staged file

- **WHEN** the user selects a file listed under Staged Changes
- **THEN** the pane shows a diff of the index version against HEAD inside that file's container

### Requirement: Sync status separate from file lists

The system SHALL show upstream sync information (outgoing and incoming commit counts relative to the tracking branch) separately from the staged and unstaged file lists, consistent with VS Code’s status bar sync indicator.

#### Scenario: Ahead of upstream

- **WHEN** the branch has commits not on the upstream tracking branch
- **THEN** the changes pane shows an outgoing commit count and does not list those commits as files

#### Scenario: No upstream

- **WHEN** no upstream tracking branch is configured
- **THEN** the pane omits sync counts and still lists staged and unstaged files
