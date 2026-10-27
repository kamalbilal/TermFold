# Spec Delta

## ADDED Requirements

### Requirement: Gecko extension can intercept HTTP(S)

The system SHALL enable the built-in Gecko preview WebExtension to intercept document, XHR, and fetch HTTP(S) requests with blocking webRequest (including response-body filtering) so session network rules can cancel or stub resources without injecting page JavaScript.

#### Scenario: Block without page script

- **WHEN** a Gecko session has an enabled block rule and the page issues a matching fetch
- **THEN** the engine cancels that request even if the page never wraps `window.fetch`

#### Scenario: Stub keeps request URL

- **WHEN** a Gecko session has an override-body rule and the page fetches a matching URL
- **THEN** the page receives the stub at that same URL rather than a redirected extension or data URL as the document’s request URL

### Requirement: Intercept applies only to the bound Gecko profile

The system SHALL apply webRequest rule updates only to the Gecko storage profile for the bound browser session key, not to every open Gecko session in the app.

#### Scenario: Two Gecko profiles

- **WHEN** two bound sessions have different override-body rules for the same URL pattern
- **THEN** each Gecko profile serves only its own session’s stub for that pattern
