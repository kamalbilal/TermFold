# Spec Delta

## ADDED Requirements

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
