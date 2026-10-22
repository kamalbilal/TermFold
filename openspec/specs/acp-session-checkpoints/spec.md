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

### Requirement: Hidden wire ids are per TermFold session tab

The system SHALL store hidden wire session ids (from checkpoint wire resync retire, user delete, or new session) keyed by the TermFold session tab identifier together with agent and workspace, so hiding in one tab does not remove entries from another tab's Earlier sessions list.

#### Scenario: Two tabs same workspace

- **WHEN** tab A retires wire session ids during checkpoint resync and tab B has a different remembered wire id in the same workspace
- **THEN** tab B's Earlier sessions list still includes wire ids that tab B has not hidden

#### Scenario: Same tab after resync

- **WHEN** tab A completes wire resync and retires the old and new clone wire ids for that tab
- **THEN** those ids are hidden only when listing past sessions for tab A
