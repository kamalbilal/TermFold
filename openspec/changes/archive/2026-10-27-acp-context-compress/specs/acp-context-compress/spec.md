# acp-context-compress Specification

## Purpose

Defines automatic agent context compression after `/summarize` on Cursor agent tabs: wire session rotation with injected summary, preserved chat timeline, and continued workspace checkpoint undo/redo.

## Requirements

### Requirement: Summarize automatically compresses agent context

On Cursor agent tabs, when the user completes a turn whose prompt is `/summarize` and the agent produces a text summary, the system SHALL automatically compress agent context without an additional user confirmation.

#### Scenario: Successful summarize turn

- **WHEN** the agent finishes a turn triggered by `/summarize` with a non-empty summary in agent text
- **THEN** the system runs context compress before accepting the next user turn

#### Scenario: No extra compress control in v1

- **WHEN** the user selects the built-in summarize command
- **THEN** the system does not require a separate compress button or dialog to complete compression

### Requirement: Compress rotates wire session and injects summary

The system SHALL retire the active wire session, open a new wire session, hide the resync clone from Earlier sessions for this tab, and set pending prompt context to the summarize summary for injection on the next user message.

#### Scenario: Next message after compress

- **WHEN** the user sends a message after compress completes
- **THEN** the first prompt to the new wire session includes the compressed summary block followed by the user message

#### Scenario: Resync in progress

- **WHEN** context compress is running
- **THEN** the UI shows the same wire-resync busy indication used for checkpoint resync

### Requirement: Chat timeline remains visually continuous

The system SHALL NOT remove, replace, or collapse prior timeline items when compress runs. The summarize user message and agent summary SHALL remain in the list; subsequent messages SHALL append after them.

#### Scenario: Timeline after compress

- **WHEN** compress completes
- **THEN** all pre-compress chat rows remain visible in the same order as before compress

### Requirement: Per-message checkpoint undo and redo after compress

The system SHALL retain checkpoint maps and the full timeline item list through compress so per-message workspace undo and redo for user turns that existed before compress continue to work.

#### Scenario: Undo before summarize on compressed chat

- **WHEN** the user invokes undo on a user message that had an undoable workspace checkpoint before compress
- **THEN** workspace restore and timeline ghost behavior work as before compress

#### Scenario: New undoable turns after compress

- **WHEN** the user sends new messages after compress and earns undoable checkpoints
- **THEN** those turns are undoable independently of compress

### Requirement: Summarize menu text matches behavior

The built-in summarize description SHALL state that summarize compresses agent context while keeping chat history on screen.

#### Scenario: Slash menu

- **WHEN** the user opens the summarize entry on a Cursor tab
- **THEN** the description reflects auto compress and preserved chat history
