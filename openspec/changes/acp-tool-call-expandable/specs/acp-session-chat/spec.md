# Spec Delta

## Purpose

Define how TermFold presents the local agent session chat timeline, including tool calls users can expand to read full titles, paths, output, and file diffs.

## ADDED Requirements

### Requirement: Tool calls use thought-style expand and collapse

Every tool-call row in the agent session chat SHALL be expandable and collapsible using the same interaction pattern as thought blocks: expanded by default, tap the header to toggle, chevron indicates state.

#### Scenario: Expanded by default

- **WHEN** a tool call appears in the chat timeline
- **THEN** the UI SHALL show the expanded body (full label and any diffs or text output) unless the user has collapsed that row in the current session

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
