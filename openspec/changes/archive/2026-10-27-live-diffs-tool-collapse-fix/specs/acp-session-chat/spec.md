# Spec Delta

## ADDED Requirements

### Requirement: Live-diff default expansion tracks asynchronous file diffs

When the live-diff setting is enabled, the system SHALL keep tool-call row collapse state aligned with the live-diff default as structured file diffs arrive or update, until the user explicitly toggles that row.

#### Scenario: Diffs arrive after a collapsed auto-default

- **WHEN** the live-diff setting is enabled, a tool call is shown in a terminal status with no structured file diffs yet, and the row defaulted to collapsed
- **AND** structured file diffs are later attached to the same tool call
- **THEN** the tool-call row SHALL expand automatically so per-file sections are visible without an extra tap

#### Scenario: Diffs arrive after in-progress completion

- **WHEN** the live-diff setting is enabled, a tool call was expanded while `in_progress`, then reaches `completed` or `failed` with structured file diffs
- **THEN** the tool-call row SHALL remain expanded unless the user collapsed it during the session

#### Scenario: No automatic expand after user collapse

- **WHEN** the user has explicitly collapsed a tool-call row in the current session
- **AND** structured file diffs arrive or grow for that tool call while live diffs are enabled
- **THEN** the row SHALL stay collapsed until the user expands it again

## MODIFIED Requirements

### Requirement: Tool collapse defaults respect user toggles

The system SHALL apply default expand/collapse for tool calls only when the user has not explicitly toggled that tool-call row in the current session. Automatic defaults MAY be revised when structured file diffs become available or when live-diff policy applies, provided the user has not toggled that row.

#### Scenario: User collapse sticks

- **WHEN** the user collapses a tool call and later the tool status, structured file diffs, or live-diff setting changes
- **THEN** the UI SHALL keep that row collapsed until the user expands it again

#### Scenario: User expand sticks

- **WHEN** the user expands a tool call that was collapsed by default
- **THEN** the UI SHALL keep that row expanded until the user collapses it again

#### Scenario: Automatic default without user toggle

- **WHEN** a tool-call row collapse state was set only by automatic policy (no explicit user toggle for that row id)
- **AND** structured file diffs later become available while live diffs are enabled
- **THEN** the UI MAY update collapse state to match the live-diff default without treating that as a user preference
