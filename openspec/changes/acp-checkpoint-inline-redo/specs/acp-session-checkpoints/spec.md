# Spec Delta

## Purpose

Defines how TermFold agent sessions expose workspace checkpoint undo and redo on the conversation timeline, including ghost-branch visualization and stacked redo after multiple undos.

## ADDED Requirements

### Requirement: Restore checkpoint on kept user turns

The system SHALL show a restore-checkpoint control on each user message that has a saved pre-turn workspace checkpoint, is not the in-flight last user message while the agent is busy, and is not blocked by wire resync.

#### Scenario: Kept turn with checkpoint

- **WHEN** a user message has a pre-turn checkpoint and its id is strictly less than the current ghost branch head id (or there is no ghost branch)
- **THEN** the user message row shows a restore-checkpoint control

#### Scenario: Agent busy on latest user turn

- **WHEN** the agent is processing and the user message is the last user message in the timeline
- **THEN** restore-checkpoint is not shown on that message

### Requirement: No restore inside ghost tail

The system SHALL NOT show restore-checkpoint on user messages whose id is greater than the ghost branch head id.

#### Scenario: Dimmed tail after undo

- **WHEN** a ghost branch head is set and a user message id is greater than that head id
- **THEN** restore-checkpoint is not shown on that message

### Requirement: Redo on ghost branch head only

The system SHALL show a redo control on the user message whose id equals the ghost branch head id when the redo stack is non-empty and wire resync is not in progress.

#### Scenario: After single undo

- **WHEN** the user restores checkpoint on a user message and the redo stack has at least one frame
- **THEN** redo appears on that same user message (the ghost head) and not elsewhere

#### Scenario: Wire resync in progress

- **WHEN** wire resync is in progress
- **THEN** redo is not shown on any message

### Requirement: Ghost head shows redo not restore

The system SHALL show redo instead of restore-checkpoint on the user message that is the ghost branch head when redo is available.

#### Scenario: Branch head controls

- **WHEN** a user message is both the ghost branch head and redo is available
- **THEN** only redo is shown on that message, not restore-checkpoint

### Requirement: Header has no redo control

The system SHALL NOT expose a session-header redo control for checkpoint redo.

#### Scenario: Redo stack non-empty

- **WHEN** the redo stack is non-empty
- **THEN** the session header does not show a checkpoint redo action

### Requirement: Stacked redo order

The system SHALL apply redo in last-in-first-out order matching the sequence of restore-checkpoint actions, restoring timeline, ghost head, checkpoint map, and workspace files per frame.

#### Scenario: Undo two turns then redo twice

- **WHEN** the user restores checkpoint on turn A then on an earlier turn B (with B before A in the conversation)
- **THEN** the first redo restores the branch state that existed after undo on A only, and the second redo restores the full pre-undo timeline before any of those undos

### Requirement: Ghost timeline dimming unchanged

The system SHALL render conversation items with id greater than or equal to the ghost branch head at reduced visual prominence until the ghost branch is committed or cleared.

#### Scenario: Ghost visible after undo

- **WHEN** ghost branch head is set to a user message id
- **THEN** that message and all later items render dimmed relative to kept items above the branch

### Requirement: Wire resync retires the previous session

The system SHALL cancel and attempt to delete the active wire session before opening a replacement session during checkpoint wire resync.

#### Scenario: Resync after undo or edit revert

- **WHEN** wire resync runs for undo or for edit with revert files
- **THEN** the prior wire session is cancelled before `session/new` and updates tagged with that old session id are ignored

### Requirement: Edit with revert files uses fresh wire context

The system SHALL truncate the timeline, resync the wire session, and send the edited prompt only on the new session using summarized kept turns plus the new user text.

#### Scenario: Edit revert files

- **WHEN** the user edits a message and chooses revert files
- **THEN** the agent turn runs on a new wire session whose context matches the truncated local timeline, not the pre-edit wire session

### Requirement: Wire resync stays on the TermFold session

The system SHALL keep the user on the same TermFold chat after undo or edit-with-revert. ACP `session/new` is only a background wire rotation. The agent's list SHALL NOT gain a second conversation that starts with the injected summary, and after process restart the same TermFold chat SHALL show the saved local timeline rather than the summary-only clone.

#### Scenario: Restart after edit revert files

- **WHEN** the user edits a message, chooses revert files, then restarts the app and opens the same TermFold session
- **THEN** the visible timeline is the truncated original turns plus the edited continuation, and `session/list` does not show the retired wire session or the resync clone as a separate past chat
