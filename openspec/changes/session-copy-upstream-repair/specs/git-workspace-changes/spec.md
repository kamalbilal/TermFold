# Spec Delta

## MODIFIED Requirements

### Requirement: Sync status separate from file lists

The system SHALL show upstream sync information (outgoing and incoming commit counts relative to the tracking branch) separately from the staged and unstaged file lists, consistent with VS Code’s status bar sync indicator.

#### Scenario: Ahead of upstream

- **WHEN** the branch has commits not on the upstream tracking branch
- **THEN** the changes pane shows an outgoing commit count and does not list those commits as files

#### Scenario: No upstream

- **WHEN** no upstream tracking branch is configured, or upstream was cleared because the remote branch does not exist after fetch in a session copy
- **THEN** the pane omits sync counts and still lists staged and unstaged files

#### Scenario: Behind upstream

- **WHEN** the branch is behind its valid upstream tracking branch
- **THEN** the changes pane shows an incoming commit count
