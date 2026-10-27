# Spec Delta

## ADDED Requirements

### Requirement: Extension passive network capture

The built-in Gecko WebExtension SHALL observe HTTP(S) requests for tabs bound to a session and report capture events to native code, including request and response headers and optional body data for Fetch/XHR traffic via a pass-through response filter. Capture SHALL NOT block or modify traffic except where existing session network rules apply. Capture events SHALL include the session key, stable request correlation id, resource type, status, timing boundaries, and outcome.

#### Scenario: Headers reach native

- **WHEN** a page issues an XHR with custom request headers
- **THEN** native capture receives those request headers for the entry

#### Scenario: Tee does not break page

- **WHEN** capture tees an XHR response body
- **THEN** the page still receives the unmodified response bytes

#### Scenario: Internal URLs skipped

- **WHEN** a request targets extension or internal browser URLs
- **THEN** capture does not pollute the session store with those entries
