# Spec Delta

## Purpose

Gecko-based in-app browsing for agent automation and dev preview: isolated session profiles, background command execution, and power-aware session lifetime without changing the external browser MCP tool contract.

## ADDED Requirements

### Requirement: HTTP preview uses Gecko engine

The system SHALL render `http` and `https` preview pages, including loopback dev servers, with the Gecko-based in-app engine rather than System WebView.

#### Scenario: Local dev server loads

- **WHEN** the user or agent opens an allowed `http://127.0.0.1` or `http://localhost` URL in preview
- **THEN** the page paints interactive content consistent with a desktop Gecko-class browser for the same URL

#### Scenario: HTTPS allowed host

- **WHEN** the user opens an HTTPS URL on an allowlisted host
- **THEN** navigation completes in the Gecko engine

### Requirement: Browser session registry

The system SHALL maintain a registry of browser session controllers keyed by automation session id and by active coding-agent browser binding, each with an isolated storage profile.

#### Scenario: Register on automation start

- **WHEN** an automation session is created that supports browser tools
- **THEN** a registry entry exists for that session id until the session ends

#### Scenario: Register on coding agent bind

- **WHEN** a coding agent run binds browser automation to a project
- **THEN** a registry entry exists for that binding key until the run ends or binding is released

#### Scenario: Casual preview not registered

- **WHEN** the user opens preview without an agent-bound session
- **THEN** no long-lived registry profile is required beyond normal tab disposal policy

### Requirement: Agent browser commands without visible preview

The system SHALL execute agent browser MCP commands against a registered session controller when the preview UI is hidden, minimized, or the app is in the background, subject to existing agent foreground and notification policy.

#### Scenario: Background snapshot

- **WHEN** an agent with an active bound session requests a snapshot while the preview pane is not visible
- **THEN** the command completes with current page data or a documented error, and SHALL NOT block indefinitely waiting for the user to open preview

#### Scenario: No registered session

- **WHEN** an agent requests a browser command and no registry entry matches the session key
- **THEN** the tool returns a clear error that names missing session binding

### Requirement: Preview UI attaches without destroying session

The system SHALL allow the preview UI to attach to an existing registry session for display and detach when the user navigates away from preview without destroying the underlying browser profile for agent-bound sessions.

#### Scenario: Return to preview after hide

- **WHEN** the user reopens preview for an automation session that was driving a page in the background
- **THEN** the same URL and in-page state are shown without a full cold reload unless the process was killed

#### Scenario: Tab switch does not cold-reset agent session

- **WHEN** the user switches away from the preview tab and back during an active agent-bound session
- **THEN** the agent-bound browser profile remains loaded

### Requirement: Power tiers for browser sessions

The system SHALL classify each registry session into power tiers: active command execution, warm idle between agent turns, and optional cold release under memory pressure, documented in user-facing help.

#### Scenario: Warm between turns

- **WHEN** an agent completes a browser command and remains in an active run with a bound session
- **THEN** the session MAY remain warm without requiring the preview UI to be visible

#### Scenario: Cold under pressure

- **WHEN** the system evicts a warm session due to memory limits
- **THEN** the next browser command SHALL either restore from persisted profile or return a documented re-warm error

### Requirement: Built-in page automation bridge

The system SHALL expose the same in-page automation surface used for snapshots and actions to the Gecko engine via a built-in extension bridge with bounded RPC timeouts.

#### Scenario: Snapshot via bridge

- **WHEN** the agent requests a snapshot on a Gecko-loaded page
- **THEN** the response uses the same short MCP snapshot format as before the engine change

#### Scenario: Bridge timeout

- **WHEN** the extension bridge does not respond within the configured timeout
- **THEN** the tool returns an error naming timeout and SHALL NOT hang the MCP session indefinitely

### Requirement: Phase-one file host on legacy engine

Until a unified HTTP or file URL path is available on Gecko, the system SHALL continue to render `files.termfold` project HTML on System WebView and SHALL use Gecko for `http` and `https` preview.

#### Scenario: Project file preview

- **WHEN** the agent opens a project file URL on the TermFold file host
- **THEN** the page loads using the documented phase-one engine while localhost HTTP uses Gecko

### Requirement: Single-flight browser commands per session

The system SHALL allow at most one in-flight browser automation command per registry session key at a time.

#### Scenario: Concurrent commands

- **WHEN** a second browser command arrives for the same session while one is running
- **THEN** the second command fails fast with a session-busy error or queues per documented policy
