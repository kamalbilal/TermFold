# Spec Delta

## Purpose

Defines how TermFold lists, filters, reopens, and deletes earlier Cursor ACP wire sessions for a workspace folder, separate from the TermFold-owned chat timeline on each session tab.

## ADDED Requirements

### Requirement: Earlier sessions lists agent wire threads for the workspace

The system SHALL populate the Earlier sessions UI from the agent's `session/list` for the current workspace guest directory, newest first as the agent orders them, excluding wire session ids hidden for that TermFold session tab.

#### Scenario: Dialog opened

- **WHEN** the user opens Earlier sessions on an agent session tab that supports `session/list`
- **THEN** the system requests `session/list` for that tab's workspace cwd and shows the filtered results

#### Scenario: Agent does not support list

- **WHEN** the connected agent does not advertise session list capability
- **THEN** Earlier sessions shows the empty state without error

### Requirement: Session list pagination is exhaustive within a safety cap

The system SHALL follow `nextCursor` on `session/list` until the agent returns no cursor or until a documented maximum number of pages or sessions is reached, whichever comes first.

#### Scenario: Multiple pages

- **WHEN** the agent returns a non-empty `nextCursor` and the safety cap is not reached
- **THEN** the system requests the next page and merges results without duplicate session ids

#### Scenario: Cap reached

- **WHEN** the safety cap is reached while `nextCursor` is still non-empty
- **THEN** the system shows all sessions collected so far and does not fail the dialog

### Requirement: New session hides the previous wire id for this tab

The system SHALL record the outgoing wire session id as hidden for the current TermFold session tab when the user starts a new session from the agent UI, before opening a replacement wire session.

#### Scenario: New session from history dialog

- **WHEN** the user chooses New session and a wire session was active for that tab
- **THEN** that wire id no longer appears in Earlier sessions for that tab and the local timeline is cleared

### Requirement: Earlier sessions explains the TermFold tab timeline

The system SHALL show brief helper text in the Earlier sessions dialog stating that the open tab's chat is saved locally and that the list is other reopenable agent threads for this project.

#### Scenario: Non-empty list

- **WHEN** at least one past session is shown
- **THEN** helper text is visible above or below the search field

### Requirement: Reopen and delete behavior unchanged

The system SHALL reopen a selected past session with agent history replay and SHALL delete a past session using the existing hide-and-remove flow, scoped to hidden ids for that TermFold session tab.

#### Scenario: Reopen

- **WHEN** the user selects a past session that is not the current wire id
- **THEN** the tab loads that wire session with replay and persists the resulting timeline for that tab

#### Scenario: Delete

- **WHEN** the user confirms delete on a past session
- **THEN** that wire id is hidden for the tab and removed from the list immediately
