# workspace-files Specification

## Purpose
The in-session Files sidebar and editor must browse the same workspace tree the agent and terminal use, so users never edit files outside their active session copy.

## Requirements

### Requirement: Files panel roots at active workspace

The system SHALL use the golden project directory as the Files tree root only when the open session uses the golden workspace. When the session uses an isolated session workspace copy, the Files tree root SHALL be that copy’s project root and SHALL NOT include sibling paths from the golden repository (including other session copies or golden-only files outside the copy).

#### Scenario: Golden workspace session

- **WHEN** the user opens Files on a session whose workspace is golden
- **THEN** the file tree is rooted at the golden project directory for that folder

#### Scenario: Session copy workspace

- **WHEN** the user opens Files on a session bound to a completed session workspace copy
- **THEN** the file tree is rooted at the session copy root and does not show the golden project tree above that copy

#### Scenario: Folder list without session

- **WHEN** the user opens Files from the folder detail screen (no active terminal or agent session)
- **THEN** the file tree remains rooted at the golden project directory

### Requirement: Files header reflects workspace scope

The system SHALL show which project tree is being browsed so a session copy is distinguishable from the golden tree.

#### Scenario: Session copy label

- **WHEN** Files is open for a session workspace copy
- **THEN** the Files panel title or subtitle indicates a session workspace (not only the folder display name)

### Requirement: PM Discovery Files scope

When the user opens Files from an active Product Manager **Discovery** session linked to a golden folder, the Files tree SHALL root at that golden project directory. The Files header SHALL indicate the PM **view branch** when set and SHALL indicate read-only browsing when the PM lane does not grant write access to golden.

#### Scenario: Discovery with view branch

- **WHEN** Files is open from a Discovery PM session with `viewBranch` set
- **THEN** the header or subtitle shows the view branch name and that the tree is the golden project

#### Scenario: No write via PM on golden

- **WHEN** the user attempts file edits from Files while only a Discovery PM session is active
- **THEN** the system does not treat PM as authorization to mutate the golden tree beyond normal Files rules for non-agent sessions

### Requirement: Review session Files follow temp copy lifetime

When a PR review session is bound to a completed temp workspace copy, the Files tree SHALL root at that copy. After the copy is deleted (Done), Files SHALL NOT present the deleted directory as a browsable workspace.

#### Scenario: Copy present

- **WHEN** the user opens Files during a PR review whose temp copy still exists
- **THEN** the file tree is rooted at that copy’s project root

#### Scenario: After Done

- **WHEN** the user opens Files on a PR review session after Done deleted the temp copy
- **THEN** the UI does not browse the removed copy path as if it were still the session workspace
