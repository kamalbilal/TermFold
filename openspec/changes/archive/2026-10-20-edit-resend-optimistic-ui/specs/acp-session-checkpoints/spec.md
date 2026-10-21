# Spec Delta

## ADDED Requirements

### Requirement: Edit resend shows user message before wire prep

The system SHALL add the edited user message to the conversation timeline immediately after the user confirms edit resend and the chat is truncated from the edit point, without waiting for workspace file restore or agent wire session refresh.

#### Scenario: Revert files on edit

- **WHEN** the user submits an edited message and chooses to revert workspace files to the pre-turn checkpoint
- **THEN** the timeline shows the new user message text right after confirmation, before file restore and wire session refresh complete

#### Scenario: Keep files on edit

- **WHEN** the user submits an edited message and chooses to keep current workspace files
- **THEN** the timeline shows the new user message text right after confirmation, without waiting for the agent prompt to start

### Requirement: Edit resend shows working indicator during prep

The system SHALL show the same working indicator used during agent turns (stepping dots with elapsed time) directly below the timeline while edit resend preparation and the subsequent agent turn are in progress.

#### Scenario: Revert files prep

- **WHEN** the user confirmed edit resend with file revert and the edited user message is already visible
- **THEN** the working indicator appears below the message while restore, wire session refresh, and the agent prompt are in flight

#### Scenario: Same indicator as normal send

- **WHEN** edit resend is in progress with the working indicator visible
- **THEN** the indicator matches the appearance and placement of the working row shown after a normal user send

### Requirement: Edit resend failure after optimistic message

The system SHALL show a clear error in the conversation if file restore, wire session refresh, or the agent prompt fails after the optimistic user message was added.

#### Scenario: Wire refresh fails

- **WHEN** wire session refresh fails after the edited user message was shown
- **THEN** the user sees an error notice in the chat and the working indicator stops

#### Scenario: Restore fails before message

- **WHEN** workspace file restore fails before the optimistic user message is added
- **THEN** the user sees an error notice and the edit resend does not add a new user message
