# Spec Delta

## ADDED Requirements

### Requirement: Automation session browser profile isolation

Each automation session SHALL use an isolated browser storage profile so cookies, local storage, and cache for one session do not leak into another automation session or into generic coding-project preview use.

#### Scenario: Two sessions different login state

- **WHEN** the user logs into a site in automation session A and opens automation session B for the same origin
- **THEN** session B SHALL NOT inherit session A’s authenticated storage unless the user repeats login in B

### Requirement: Browser automation targets active automation session

When an automation session is active, agent browser commands and MCP browser tools SHALL drive the browser session and profile bound to that session id.

#### Scenario: Driver uses session profile

- **WHEN** the agent issues a browser snapshot for an open automation session
- **THEN** the snapshot SHALL reflect that session’s active tab and profile

#### Scenario: No cross-session command

- **WHEN** automation session A is foreground and the agent for session B issues a browser command without switching sessions
- **THEN** the command SHALL fail or queue per documented single-active-session policy and SHALL NOT mutate session A’s page

### Requirement: User can use the page while agent is idle

The system SHALL allow touch and scroll in the preview surface when no agent browser command is in progress, without requiring the user to resume from a full pause state.

#### Scenario: Idle interaction

- **WHEN** the agent is not running a browser command and the user scrolls the page
- **THEN** the page scroll position updates and the next agent snapshot reflects the new viewport

### Requirement: Snapshot after resume from pause

The system SHALL not return a stale snapshot while the preview surface is paused for display-only reasons until layout for the bound session is current.

#### Scenario: Minimized during command

- **WHEN** the preview UI was hidden and an agent requests snapshot for a registered session
- **THEN** the system returns snapshot data from the current document without requiring the preview pane to be on screen

### Requirement: Coding agent browser binding uses isolated profile

When a coding agent run binds browser tools to a workspace project, the system SHALL use a dedicated browser profile for that binding distinct from automation sessions and casual preview.

#### Scenario: Separate from automation

- **WHEN** automation session A and a coding agent on project P both use browser tools
- **THEN** storage and cookies for P’s binding SHALL NOT leak into session A’s profile

### Requirement: Engine-agnostic automation tools

Snapshot, click, fill, wait, screenshot, console, network, and tab tools SHALL operate against the active preview engine implementation without exposing engine-specific APIs to agents.

#### Scenario: Same MCP after engine change

- **WHEN** the agent uses existing `termfold-browser` tools after Gecko ships
- **THEN** tool names and short response shapes remain compatible with prior skill documentation aside from documented error codes
