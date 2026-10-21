# Spec Delta

## ADDED Requirements

### Requirement: Cursor composer branch chip stays current

The system SHALL keep the branch name shown on the Cursor agent composer in sync with the session workspace HEAD without requiring the user to reopen the session or manually refresh.

#### Scenario: Agent changes branch

- **WHEN** the workspace HEAD branch changes while the user remains on the Cursor agent session (for example after the agent or a shell command checks out another branch)
- **THEN** the composer branch chip updates to the new branch name within a short interval

#### Scenario: Picker switch updates chip

- **WHEN** the user successfully checks out a branch from the branch picker
- **THEN** the composer branch chip shows that branch immediately

### Requirement: Branch picker fuzzy search and recents

The system SHALL provide fuzzy branch search in the branch picker, ranking better matches ahead of weaker ones, and SHALL continue to show recently used branches ahead of other matches when they satisfy the query.

#### Scenario: Fuzzy query

- **WHEN** the user types a partial or out-of-order branch name in the picker search field
- **THEN** matching branches are ordered by match quality with recent branches still preferred among ties

### Requirement: Branch list cache and manual refresh

The system SHALL cache the branch list per workspace catalog key, show the cached list immediately when reopening the picker, and SHALL provide a refresh control that reloads the list from git on demand.

#### Scenario: Reopen picker uses cache

- **WHEN** the user opens the branch picker again within the same app session after a successful load
- **THEN** the previous branch list appears without blocking on a full reload

#### Scenario: User forces refresh

- **WHEN** the user taps the picker refresh control
- **THEN** the system reloads branches from git and replaces the cached list when the reload completes

### Requirement: Responsive branch checkout

The system SHALL avoid unconditional remote fetch on every branch switch when a local checkout is sufficient, SHALL show visible progress while checkout runs, and SHALL surface errors without leaving the picker in an ambiguous state.

#### Scenario: Local branch exists

- **WHEN** the user selects a branch that already exists as a local branch in the session workspace
- **THEN** the system checks out that branch without fetching from the remote first

#### Scenario: Checkout in progress

- **WHEN** a branch checkout is running
- **THEN** the picker shows that a switch is in progress and prevents duplicate checkout attempts for the same selection

#### Scenario: Checkout failure

- **WHEN** checkout fails
- **THEN** the picker remains open, shows the error, and the composer branch chip remains on the previous branch
