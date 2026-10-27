## Purpose

Define how TermFold presents the local agent session chat timeline, including expandable tool-call rows, structured file diffs with Cursor-style per-file hunks, and settings that control live diff updates and default tool-row expansion.

## Requirements

### Requirement: Tool calls use thought-style expand and collapse

Every tool-call row in the agent session chat SHALL be expandable and collapsible using the same interaction pattern as thought blocks: tap the header to toggle, chevron indicates state. Default expansion for file-changing tools SHALL follow the live-diff preference (see **Live diff updates preference**).

#### Scenario: Expanded by default for file tools when live diffs on

- **WHEN** the live-diff setting is enabled and a tool call with structured file diffs appears
- **THEN** the UI SHALL show the expanded body unless the user has collapsed that row in the current session

#### Scenario: Collapsed preview

- **WHEN** the user collapses a tool call
- **THEN** the UI SHALL show a single-line preview in the header and SHALL hide diffs and detail text until expanded again

#### Scenario: Always expandable

- **WHEN** a tool call has only a title or path and no structured diffs or detail text
- **THEN** the UI SHALL still allow expand and collapse and SHALL show the full tool label when expanded

### Requirement: Full tool label is readable when expanded

The UI SHALL NOT rely on multi-line ellipsis in the header to show complete tool titles or paths.

#### Scenario: Long title in body

- **WHEN** a tool call title or path label exceeds one line
- **THEN** the expanded body SHALL show the complete label without truncation and the collapsed header SHALL show at most one line with ellipsis

#### Scenario: Header while expanded

- **WHEN** a tool call is expanded
- **THEN** the header SHALL show the tool kind icon, status indicator when applicable, and chevron without clipping the full label in the header row

### Requirement: Tool call body content unchanged

Expanding a tool call SHALL continue to show structured file diffs and monospace detail text when present, using the same data already stored on the tool-call item.

#### Scenario: Diffs and detail together

- **WHEN** a tool call has both file diffs and non-diff detail text
- **THEN** the expanded body SHALL show file diff sections and detail text in the same order as today

#### Scenario: In-progress tools

- **WHEN** a tool call status is `in_progress`
- **THEN** the UI SHALL show the running indicator and SHALL still allow collapse and expansion without losing streamed content when expanded

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

The system SHALL provide a user setting, default enabled, that controls whether line-level diffs update while a tool call status is `in_progress` **and** SHALL control default expansion of tool-call rows that include structured file diffs.

#### Scenario: Default shows live diffs

- **WHEN** the setting is at its default and a tool call receives `tool_call_update` with growing patch text
- **THEN** the UI MAY update visible line-level diff content for expanded files, subject to reasonable throttling so the chat remains scrollable

#### Scenario: Disabled suppresses live hunks

- **WHEN** the setting is disabled and a tool call is `in_progress`
- **THEN** the UI SHALL NOT show updating hunk lines for that tool call and SHALL show at most non-hunk summary (path and counts) until the tool reaches a terminal status

#### Scenario: Completed tools always allow final diff

- **WHEN** a tool call status is `completed` or `failed` and the user expands it
- **THEN** the UI SHALL show the final per-file diff content regardless of the live-diff setting

#### Scenario: Live diffs on expands the active tool and the latest turn

- **WHEN** the live-diff setting is enabled and a tool call with structured file diffs is `in_progress`
- **THEN** the tool-call row SHALL default to expanded so live hunks are visible unless the user collapsed that row in the current session
- **WHEN** the agent is still working on the current user turn and a file-changing tool has reached a terminal status
- **THEN** that completed tool-call row SHALL default to collapsed so only the active tool stays expanded
- **WHEN** the turn has ended (the agent is no longer busy for that prompt) and a file-changing tool belongs to the latest user turn
- **THEN** that tool-call row SHALL default to expanded unless the user collapsed it; tools from earlier turns SHALL default to collapsed

#### Scenario: Diffs arrive after a collapsed auto-default

- **WHEN** the live-diff setting is enabled, a tool call is `in_progress` with no structured file diffs yet, and structured file diffs are later attached
- **THEN** the tool-call row SHALL remain expanded and show live hunks without an extra tap
- **WHEN** a completed tool in the latest user turn receives structured file diffs after the agent is no longer busy for that prompt
- **THEN** the tool-call row SHALL expand automatically unless the user collapsed that row in the current session
- **WHEN** a completed tool in the current turn receives file diffs while the agent is still busy
- **THEN** the row MAY stay collapsed until the turn ends, then SHALL follow the latest-turn expansion rule above

#### Scenario: Live diffs off collapses completed file tools by default

- **WHEN** the live-diff setting is disabled and a file-changing tool call reaches `completed` or `failed`
- **THEN** the tool-call row SHALL default to collapsed so the chat stays compact, while the user MAY expand the row to see final per-file diffs

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

### Requirement: Timeline persistence includes diff payloads

The system SHALL persist structured diff data for tool calls in the local session timeline so diffs survive process restart for sessions saved after this behavior ships.

#### Scenario: Round-trip save and load

- **WHEN** a session timeline containing tool calls with structured diffs is saved and reloaded
- **THEN** the restored tool calls SHALL include the same diff payloads sufficient to render per-file diffs without contacting the agent again

#### Scenario: Legacy timelines remain readable

- **WHEN** a timeline saved before structured diff persistence is loaded
- **THEN** the system SHALL display the legacy `detail` text and SHALL NOT fail to load the session

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

### Requirement: Product Manager session chat kind

The system SHALL support a distinct **Product Manager** ACP session kind whose chat timeline uses the same message and tool-call presentation as coding sessions, with PM-specific prompt injection and deliverable formatting.

#### Scenario: Open PM session chat

- **WHEN** the user opens an active Product Manager session
- **THEN** the chat screen uses the PM session kind and shows PM session header context (mode, view branch or release branch)

### Requirement: PM per-turn context injection

For Product Manager sessions, the system SHALL inject session brief documents (vision, constraints, competitors, release brief when applicable), a capped summary of the latest research pack, and a capped set of top-ranked non-secret chunks from that session’s private memory store on every user turn sent to the agent.

#### Scenario: Follow-up after research

- **WHEN** the user sends a follow-up message after a research pass completed
- **THEN** the agent prompt includes the research pack summary and relevant session memory chunks without requiring the user to repeat prior context

### Requirement: PM structured deliverable cards

Product Manager agent replies that include a decision memo SHALL be renderable as structured in-chat sections (recommendation, landscape matrix, options, scope in/out, risks, gaps) when the agent emits the agreed PM response format.

#### Scenario: Decision memo visible

- **WHEN** the agent completes a Research and decide response
- **THEN** the user can read the decision memo and evidence tags in the chat without opening external files

### Requirement: PM agent memory tools

Product Manager sessions SHALL expose agent-callable tools to search, write, and fetch by id records in the current session’s private memory store only.

#### Scenario: Search is session-scoped

- **WHEN** the agent searches PM memory
- **THEN** results are only chunks from the current PM session id

#### Scenario: Write from another session id rejected

- **WHEN** a tool call attempts to write memory for a different session id
- **THEN** the write fails and the current session store is unchanged

### Requirement: PR review turns include PR identity

For a PR review session, each user-initiated agent turn SHALL include the current pull request identity (repository, number, and head branch) in the prompt context sent to the agent.

#### Scenario: First review turn

- **WHEN** the user sends a message in a review session bound to a resolved GitHub PR
- **THEN** the agent turn includes that PR’s repository, number, and head branch

#### Scenario: PR replaced in same chat

- **WHEN** the user binds a different PR URL to the same review session
- **THEN** subsequent turns include the new PR identity and not the previous PR as current

### Requirement: Review chat survives workspace deletion

Deleting a PR review temp workspace SHALL NOT clear the session’s saved chat timeline. Reopening the same review session SHALL show the previous messages.

#### Scenario: Done then reopen

- **WHEN** the user taps Done (temp copy deleted) and later opens the same review session
- **THEN** the chat timeline from before Done is still shown

### Requirement: Idle disconnect is visible in chat

When an ACP session was disconnected because of the idle disconnect policy, the chat timeline SHALL show a short inline notice explaining that the agent was stopped to save battery and that sending a message or reopening will reconnect.

#### Scenario: Notice after idle disconnect

- **WHEN** the user opens a session that was disconnected due to idle background timeout
- **THEN** the UI SHALL show an inline notice describing idle disconnect and SHALL show the agent as not running until reconnect

#### Scenario: Reconnect on send

- **WHEN** the user sends a message in a session that was idle-disconnected
- **THEN** the system SHALL start or reconnect the agent and SHALL continue the existing timeline without removing prior messages
