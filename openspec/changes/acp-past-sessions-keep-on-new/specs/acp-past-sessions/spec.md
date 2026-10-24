# acp-past-sessions — Delta

## MODIFIED Requirements

### Requirement: New session keeps the previous wire thread listable

The system SHALL NOT record the outgoing wire session id as hidden when the user starts a new session from the agent UI. The prior wire session SHALL remain eligible to appear in Earlier sessions for that TermFold tab (subject to agent `session/list` and any ids hidden by explicit delete or checkpoint wire resync).

#### Scenario: New session from history dialog

- **WHEN** the user chooses New session and a wire session was active for that tab
- **THEN** the local timeline for that tab is cleared, a new wire session is opened, and the previous wire id is not added to the tab’s hidden-id set

#### Scenario: Previous thread appears on next list fetch

- **WHEN** the user opens Earlier sessions after starting a new session
- **THEN** the previous wire session appears in the list if the agent still returns it from `session/list` and it was not hidden by delete or resync retire

### Requirement: Earlier sessions explains the TermFold tab timeline

The system SHALL show brief helper text in the Earlier sessions dialog stating that the open tab’s chat is saved locally, that **New session** clears this tab’s chat and starts a new agent thread, and that the list below is other reopenable agent threads for this project (including threads left behind by New session).

#### Scenario: Non-empty list

- **WHEN** at least one past session is shown
- **THEN** helper text is visible above or below the search field

#### Scenario: Empty list after new session

- **WHEN** the list has loaded and only the current thread would qualify
- **THEN** helper text still explains that prior threads appear here after New session when the agent lists them

## REMOVED Requirements

### Requirement: New session hides the previous wire id for this tab

**Reason:** Users need to reopen prior agent threads after starting a new chat in the same TermFold tab.

**Migration:** Outgoing wire ids hidden solely by New session on older builds remain hidden until the user clears app data or we add no migration (accepted).
