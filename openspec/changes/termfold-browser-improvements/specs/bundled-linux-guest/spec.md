# Spec Delta

## ADDED Requirements

### Requirement: Bundled browser skill documents automation surface

The system SHALL ship bundled agent skill material for `termfold-browser` that documents snapshot scopes (shadow, iframe, changed, find), wait modes (text, selector, URL, network idle, captcha cleared), tab list/switch/new/close, health and network incremental tools, navigation allowlist behavior, screenshot compare, CAPTCHA handoff (user solves on device; no automated bypass), and phone-oriented testing flow (console errors first, element screenshots, viewport modes).

#### Scenario: Agent loads browser skill from assets

- **WHEN** an agent reads the TermFold browser skill installed from app assets
- **THEN** the skill describes the current MCP/CLI commands and recommended verification order after code changes

#### Scenario: Setup points to browser testing

- **WHEN** `termfold-setup` completes successfully
- **THEN** the success output or linked tips mention using the in-app preview browser for local dev verification
