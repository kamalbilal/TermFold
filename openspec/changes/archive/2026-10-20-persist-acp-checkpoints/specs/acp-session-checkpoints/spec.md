# Spec Delta

## ADDED Requirements

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
