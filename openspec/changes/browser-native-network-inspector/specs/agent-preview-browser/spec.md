# Spec Delta

## ADDED Requirements

### Requirement: Session network capture for inspection

The system SHALL passively record HTTP(S) requests for the bound Gecko preview session in a capture store shared by the native Network UI and agent tools. Each entry SHALL have a stable id, method, URL, resource type, status when known, timing, and rule outcome (ok, failed, blocked, overridden). Request and response headers SHALL be stored for inspection. Request and response bodies SHALL be captured only for Fetch/XHR-style traffic with text or JSON payloads, subject to a per-body size cap; other resource types SHALL appear without bodies.

#### Scenario: XHR with JSON body

- **WHEN** a page performs a fetch that returns JSON
- **THEN** the capture store records headers and response body for that entry up to the cap

#### Scenario: Image without body

- **WHEN** a page loads an image URL
- **THEN** the capture store lists the request with status and timing but does not require a response body payload

#### Scenario: App restart clears capture

- **WHEN** the application process restarts
- **THEN** prior capture entries are not restored from disk

### Requirement: Preserve log for network capture

The system SHALL provide a Preserve log toggle for the network capture store, default **off**. When off, main-frame navigation or reload SHALL clear capture entries for that session before new traffic is recorded. When on, entries SHALL remain across reloads and navigations until the user clears them, the agent clears them, or the session is removed. The user and the agent SHALL both be able to read and set preserve state.

#### Scenario: Default clears on reload

- **WHEN** preserve log is off and the user reloads the page
- **THEN** only requests from after the reload appear in the network list

#### Scenario: Preserve across reload

- **WHEN** preserve log is on and the user reloads the page
- **THEN** requests from before and after the reload appear in the network list

#### Scenario: Agent sets preserve

- **WHEN** the agent enables preserve log for the current session
- **THEN** subsequent reloads keep prior entries until preserve is turned off or capture is cleared

### Requirement: Native network inspector UI

The system SHALL provide a native Network inspector in the preview: a bottom drawer with a virtualized request list, filter chips (including Fetch/XHR and failed), search by URL, Preserve log toggle, and Clear. Tapping a row SHALL open a detail bottom sheet with Overview, Headers, Payload, Response, and Timing (at minimum total duration). The UI SHALL read from the same capture store as agent tools. Network rules management SHALL remain on the separate Network rules screen.

#### Scenario: Open network drawer

- **WHEN** the user opens Network from the preview chrome on a bound session
- **THEN** a drawer shows captured requests newest or ordered per design with filters available

#### Scenario: View response body

- **WHEN** the user selects a captured XHR entry with a stored response body
- **THEN** the detail sheet shows the response content in the Response tab

### Requirement: Agent network list tool (summary)

The system SHALL expose `browser_network` (and CLI equivalent) to return a compact summary list of capture entries for the current session, not full headers or bodies in the list response. The tool SHALL support `since_cursor` incremental read, `failed_only`, `xhr_only`, `url_contains`, and a default row limit suitable for low token use. The tool SHALL support preserve log read and write via dedicated operations on the same tool family.

#### Scenario: Incremental list

- **WHEN** the agent calls network list with a since_cursor from a prior list call
- **THEN** only entries after that cursor are returned

#### Scenario: List excludes bodies

- **WHEN** the agent requests a network list
- **THEN** the response does not include full request or response bodies

### Requirement: Agent network detail tool

The system SHALL expose `browser_network_detail` (and CLI equivalent) accepting an entry id and a part (`overview`, `headers`, `request_body`, `response_body`). Body parts returned to the agent SHALL be truncated to a fixed maximum size with a note when truncated. Headers SHALL be returned in full for debug builds as configured for this product.

#### Scenario: Fetch headers for one entry

- **WHEN** the agent requests detail for a valid entry id with part headers
- **THEN** request and response header fields for that entry are returned

#### Scenario: Truncated body in MCP

- **WHEN** a response body exceeds the agent truncation limit
- **THEN** the detail response includes a prefix of the body and indicates truncation and total size

## MODIFIED Requirements

### Requirement: Network log supports incremental read

The system SHALL allow agents to read network capture summary entries since a prior cursor in addition to filtered views (failed-only, xhr-only, url search). The prior action-generation marker MAY remain for compatibility where still wired, but the canonical incremental marker for capture entries SHALL be `since_cursor` returned from list calls.

#### Scenario: Since cursor

- **WHEN** the agent requests network list with since_cursor from a prior list response
- **THEN** only capture entries after that cursor are returned

#### Scenario: Since last action legacy

- **WHEN** the agent requests network output with only a since_generation marker and no cursor
- **THEN** the system returns entries consistent with the current list tool behavior documented in the bundled skill

### Requirement: Network log includes Gecko and rule outcomes

The system SHALL record HTTP(S) requests for the Gecko preview session in the capture store used by `browser_network`, including blocked and overridden entries, each with a stable entry id suitable for `browser_network_detail`, so agents can inspect traffic and choose URL patterns for rules from real requests.

#### Scenario: Gecko fetch appears

- **WHEN** a Gecko-backed page performs a fetch to an API URL
- **THEN** network list includes that method and URL with an entry id instead of an empty log

#### Scenario: Blocked entry marked

- **WHEN** a request matches a block rule
- **THEN** the capture entry marks that request as blocked

#### Scenario: Overridden entry marked

- **WHEN** a request matches an override-body rule
- **THEN** the capture entry marks that request as overridden
