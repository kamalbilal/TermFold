# Spec Delta

## Purpose

The in-session Files sidebar and editor must browse the same workspace tree the agent and terminal use, so users never edit files outside their active session copy.

## ADDED Requirements

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
