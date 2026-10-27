## Purpose

Define how TermFold presents the local agent session chat timeline, including tool calls that modify workspace files, with Cursor-style per-file diffs and settings that control live updates during in-progress tools.

## ADDED Requirements

### Requirement: Tool calls preserve structured file diff content

The system SHALL parse ACP tool-call `content` entries of type `diff` into structured file-change records and SHALL NOT discard renderable patch text when the agent provides it.

#### Scenario: v2 git patch retained

- **WHEN** a `tool_call` or `tool_call_update` includes a `diff` entry with `patch.format` of `git_patch` and non-empty `patch.text`
- **THEN** the client SHALL store that patch text associated with the affected paths from `changes`

#### Scenario: v1 text synthesized to unified diff

- **WHEN** a `diff` entry provides `path` and `newText` without a usable `git_patch`
- **THEN** the client SHALL synthesize a unified diff suitable for the same line-oriented diff renderer used for git workspace changes

#### Scenario: Non-diff content coexists

- **WHEN** tool-call `content` includes both `diff` and non-diff entries (for example `content` text blocks)
- **THEN** the client SHALL retain diff records separately and SHALL still expose non-diff text for display alongside or below diffs

### Requirement: Expanded tool calls show per-file collapsible diffs

The system SHALL render each affected file in an expanded tool call as its own collapsible section showing relative path (when under the workspace), operation summary, and added/removed line counts before expansion.

#### Scenario: Collapsed file shows counts only

- **WHEN** the user expands a tool call but a file section within it is collapsed
- **THEN** the UI SHALL show the file path and net `+`/`-` line counts and SHALL NOT compose the full hunk line list for that file

#### Scenario: Expanded file shows unified diff lines

- **WHEN** the user expands a file section within a tool call
- **THEN** the UI SHALL show unified diff lines with the same add/remove/context styling as the git changes diff view

#### Scenario: Create and delete without patch text

- **WHEN** a diff record describes `add` or `delete` and no text patch is available
- **THEN** the UI SHALL still show a clear per-file summary (for example “new file” or “deleted”) and SHALL show line-level diff when synthesis or patch text is available

### Requirement: Chat scrolling stays responsive with large tool diffs

The system SHALL prepare diff presentations off the main thread and SHALL NOT render an unbounded scrollable column of every diff line for all files inside a single chat list item.

#### Scenario: Background preparation

- **WHEN** structured diff content arrives or updates for a tool call
- **THEN** parsing or synthesis and preparation of display rows SHALL run outside the UI frame callback

#### Scenario: Lazy line composition for expanded files

- **WHEN** a file section is expanded inside the chat timeline
- **THEN** diff lines SHALL be composed through a lazy, height-bounded list so scrolling the chat does not require laying out every line of every tool call in the session

### Requirement: Live diff updates preference

The system SHALL provide a user setting, default enabled, that controls whether line-level diffs update while a tool call status is `in_progress`.

#### Scenario: Default shows live diffs

- **WHEN** the setting is at its default and a tool call receives `tool_call_update` with growing patch text
- **THEN** the UI MAY update visible line-level diff content for expanded files, subject to reasonable throttling so the chat remains scrollable

#### Scenario: Disabled suppresses live hunks

- **WHEN** the setting is disabled and a tool call is `in_progress`
- **THEN** the UI SHALL NOT show updating hunk lines for that tool call and SHALL show at most non-hunk summary (path and counts) until the tool reaches a terminal status

#### Scenario: Completed tools always allow final diff

- **WHEN** a tool call status is `completed` or `failed` and the user expands it
- **THEN** the UI SHALL show the final per-file diff content regardless of the live-diff setting

### Requirement: Timeline persistence includes diff payloads

The system SHALL persist structured diff data for tool calls in the local session timeline so diffs survive process restart for sessions saved after this behavior ships.

#### Scenario: Round-trip save and load

- **WHEN** a session timeline containing tool calls with structured diffs is saved and reloaded
- **THEN** the restored tool calls SHALL include the same diff payloads sufficient to render per-file diffs without contacting the agent again

#### Scenario: Legacy timelines remain readable

- **WHEN** a timeline saved before structured diff persistence is loaded
- **THEN** the system SHALL display the legacy `detail` text and SHALL NOT fail to load the session
