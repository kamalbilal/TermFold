# agent-preview-browser Specification

## Purpose
TBD - created by archiving change browser-session-network-rules. Update Purpose after archive.

## Requirements

### Requirement: Session-scoped network rule store

The system SHALL store block and override-body rules only on the bound browser session. Rules for one coding or automation session MUST NOT apply to another. Reopening the same session restores its rules. Deleting the session SHALL delete its rules. The system SHALL NOT keep an app-global rule set.

#### Scenario: Isolation across sessions

- **WHEN** session A has a block pattern for `https://api.example.com/*` and session B has none
- **THEN** only session A’s browser cancels matching requests; session B loads them normally

#### Scenario: Same session restored

- **WHEN** the user closes and reopens the same bound session that had an override-body rule
- **THEN** that override is still listed and still applies to matching requests

#### Scenario: Session deleted

- **WHEN** the bound session is deleted
- **THEN** its network rules are gone and MUST NOT apply to a later session with a new id

### Requirement: URL pattern matching for network rules

The system SHALL match block and override-body rules with Chrome-style URL patterns: exact URLs, and `*` (any run of characters) and `?` (one character). When more than one rule matches, the system SHALL apply the first rule in list order.

#### Scenario: Base URL wildcard

- **WHEN** an override-body rule uses `https://api.foo.com/v1/*`
- **THEN** both `https://api.foo.com/v1/user` and `https://api.foo.com/v1/cart` receive that stub body

#### Scenario: Exact URL

- **WHEN** a block rule is the exact URL `https://cdn.example.com/track.js`
- **THEN** only that URL is blocked; other paths on the same host are not

#### Scenario: First match wins

- **WHEN** a block rule for `https://api.foo.com/*` is listed above an override for `https://api.foo.com/v1/user`
- **THEN** a request to `https://api.foo.com/v1/user` is blocked, not overridden

### Requirement: Block request like Chrome DevTools

The system SHALL cancel matching requests so the page sees a failed fetch, the network log marks them as blocked by DevTools-style rules, and the origin is not used as the resource. Block matching SHALL use URL patterns only, not request headers.

#### Scenario: Agent blocks a pattern

- **WHEN** the agent adds a block rule for `*://api.foo.com/*` on the current session and the page then requests `https://api.foo.com/v1/user`
- **THEN** the request fails for the page and the network log records it as blocked

#### Scenario: Reload applies block

- **WHEN** a block rule is already enabled and the user or agent reloads the page
- **THEN** matching subresources and XHR/fetch calls fail the same way

### Requirement: Override response body like Chrome local overrides

The system SHALL serve a configured response body for matching URL patterns at the original request URL so the page treats it as that resource. An override MAY include HTTP status and response headers (including CORS). Matching SHALL use URL patterns only, not request headers. The system SHALL NOT implement this by rewriting `window.fetch` in the page.

#### Scenario: Agent overrides JSON

- **WHEN** the agent sets an override body for `https://api.foo.com/v1/user` to `{"role":"admin"}` and the page fetches that URL
- **THEN** the page parses that JSON as the response for that URL

#### Scenario: Wildcard body override

- **WHEN** the agent sets one override body on `https://api.foo.com/v1/*`
- **THEN** every matching path under that prefix receives that body

#### Scenario: Response header override for CORS

- **WHEN** an override includes `Access-Control-Allow-Origin: *` (or equivalent) for a cross-origin fetch
- **THEN** the page can read the stub body subject to normal CORS rules using those response headers

### Requirement: Agent tools for network rules

The system SHALL provide agent-callable tools to add, list, and remove block and override-body rules for the current bound session only, and SHALL reject rule changes that target another session.

#### Scenario: Add and list

- **WHEN** the agent adds a block pattern and an override-body rule on the current session then lists rules
- **THEN** the list includes both rules with their patterns and a short override-body preview

#### Scenario: Clear all

- **WHEN** the agent clears all network rules for the current session
- **THEN** subsequent matching requests behave as if no rules existed

#### Scenario: Other session untouched

- **WHEN** the agent clears rules while bound to session A
- **THEN** session B’s rules remain unchanged

### Requirement: Network log includes Gecko and rule outcomes

The system SHALL record HTTP(S) requests for the Gecko preview session in the agent network log, including blocked and overridden entries, so agents can choose URL patterns from real traffic.

#### Scenario: Gecko fetch appears

- **WHEN** a Gecko-backed page performs a fetch to an API URL
- **THEN** `browser_network` lists that method and URL instead of an empty log

#### Scenario: Blocked entry marked

- **WHEN** a request matches a block rule
- **THEN** the network log marks that entry as blocked

#### Scenario: Overridden entry marked

- **WHEN** a request matches an override-body rule
- **THEN** the network log marks that entry as overridden

### Requirement: Network rules screen from preview menu

The system SHALL add a preview More-menu item that opens a full screen listing the current session’s blocked URL patterns and body overrides (pattern plus truncated body). The user SHALL be able to delete one rule or clear all. The screen SHALL NOT be the place to create new rules.

#### Scenario: Open from menu

- **WHEN** the user chooses Network rules from the preview More menu on a bound session
- **THEN** a full screen shows that session’s blocked patterns and body overrides

#### Scenario: Empty state

- **WHEN** the current session has no rules
- **THEN** the screen states that there are no blocks or overrides

#### Scenario: Delete one rule

- **WHEN** the user deletes one override row
- **THEN** that rule is removed from the list and no longer applies; other rules remain

#### Scenario: Clear all from screen

- **WHEN** the user clears all rules on that screen
- **THEN** the list is empty and the session’s browser applies no block or override rules

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
