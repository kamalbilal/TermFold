# Spec Delta

## ADDED Requirements

### Requirement: Automation session browser profile isolation

Each automation session SHALL use an isolated WebView storage profile so cookies, local storage, and cache for one session do not leak into another automation session or into generic coding-project preview use.

#### Scenario: Two sessions different login state

- **WHEN** the user logs into a site in automation session A and opens automation session B for the same origin
- **THEN** session B SHALL NOT inherit session A’s authenticated storage unless the user repeats login in B

### Requirement: Browser automation targets active automation session

When an automation session is active, agent browser commands and MCP browser tools SHALL drive the WebView and profile bound to that session id.

#### Scenario: Driver uses session WebView

- **WHEN** the agent issues a browser snapshot for an open automation session
- **THEN** the snapshot SHALL reflect that session’s active tab and profile

#### Scenario: No cross-session command

- **WHEN** automation session A is foreground and the agent for session B issues a browser command without switching sessions
- **THEN** the command SHALL fail or queue per documented single-active-session policy and SHALL NOT mutate session A’s page

### Requirement: Preview project directory follows link

When the automation project has a linked coding folder, preview file hosting for that session SHALL use the linked project guest path; when unlinked, preview SHALL use the automation session guest root or documented default.

#### Scenario: Linked preview files

- **WHEN** a linked folder exists and the agent opens a project HTML file via the file host
- **THEN** the URL SHALL resolve under the linked project tree

#### Scenario: Unlinked preview scope

- **WHEN** no folder is linked
- **THEN** file-host preview SHALL be limited to automation session-owned paths
