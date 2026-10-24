# Spec Delta

## Purpose

Clarify how the **Show live file diffs** setting interacts with tool-call row expansion in agent session chat.

## MODIFIED Requirements

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

#### Scenario: Live diffs on keeps file tools expanded by default

- **WHEN** the live-diff setting is enabled and a tool call with one or more structured file diffs appears in the timeline (including when it reaches a terminal status)
- **THEN** the tool-call row SHALL default to expanded so per-file sections are visible without an extra tap, unless the user has collapsed that tool call in the current session

#### Scenario: Live diffs off collapses completed file tools by default

- **WHEN** the live-diff setting is disabled and a file-changing tool call reaches `completed` or `failed`
- **THEN** the tool-call row SHALL default to collapsed so the chat stays compact, while the user MAY expand the row to see final per-file diffs

## ADDED Requirements

### Requirement: Tool collapse defaults respect user toggles

The system SHALL apply default expand/collapse for tool calls only when no per-session user preference exists for that tool-call row id.

#### Scenario: User collapse sticks

- **WHEN** the user collapses a tool call and later the tool status or live-diff setting changes
- **THEN** the UI SHALL keep that row collapsed until the user expands it again

#### Scenario: User expand sticks

- **WHEN** the user expands a tool call that was collapsed by default
- **THEN** the UI SHALL keep that row expanded until the user collapses it again
