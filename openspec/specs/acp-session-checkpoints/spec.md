# acp-session-checkpoints Specification

## Purpose

TBD — Update Purpose after archive: persistence of checkpoint undo/redo UI state across process restarts for TermFold agent sessions.

## Requirements

### Requirement: Checkpoint UI survives process restart

The system SHALL persist, for each TermFold agent session tab, the mapping of user-message ids to pre-turn workspace snapshots, which of those turns are undoable, the ghost branch head, and the redo stack, together with the saved conversation timeline.

#### Scenario: Restart after file-changing turn

- **WHEN** a user turn completed with workspace file changes, then the app process is killed and the same TermFold session is opened again
- **THEN** Undo changes is shown on that user message the same as before the kill

#### Scenario: Restart while ghosted

- **WHEN** the user has undone a turn so a ghost branch is visible with Redo on the ghost head, then the app process is killed and the same TermFold session is opened again
- **THEN** later messages remain dimmed and Redo appears on that same ghost-head user message

### Requirement: Missing checkpoint fields stay empty

The system SHALL treat a saved timeline that has no checkpoint fields as having no undoable turns, no ghost branch, and an empty redo stack.

#### Scenario: Older timeline file

- **WHEN** the saved timeline contains conversation items but no checkpoint fields
- **THEN** the chat restores and undo/redo chips are absent until new turns record checkpoints

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
