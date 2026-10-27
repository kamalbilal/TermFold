# Spec Delta

## ADDED Requirements

### Requirement: Automation sessions identify as automation mode

When chat runs inside an automation session workspace, the client SHALL configure the agent session so browser automation tools and automation memory tools are available and coding-git workspace mutations are not implied.

#### Scenario: Open automation chat

- **WHEN** the user opens agent chat from an automation session
- **THEN** the client SHALL start or resume ACP with automation-appropriate MCP servers and working directories

### Requirement: Per-turn automation context assembly

For automation sessions, the client SHALL assemble mandatory per-prompt context from session documentation files, optional linked-folder stanza, memory inject slices, and timeline anchors so content survives context compression according to the same anchoring mechanism used for other injected session state.

#### Scenario: New user message

- **WHEN** the user sends a chat message in an automation session
- **THEN** the outbound prompt SHALL include the assembled automation context in addition to the user text

#### Scenario: After compress

- **WHEN** context compression runs mid-session
- **THEN** subsequent prompts SHALL still include mandatory automation documents and refreshed session-state summary per anchor rules
