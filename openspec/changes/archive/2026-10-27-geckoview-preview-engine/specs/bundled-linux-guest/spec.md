# Spec Delta

## ADDED Requirements

### Requirement: Browser skill documents long automation token playbook

The system SHALL ship bundled agent skill material that documents token-efficient long browser automations: prefer `wait` and incremental snapshot modes, use `outline` and `console --errors` before full snapshots, use `network --failed`, avoid repeated screenshots unless `if_changed`, and read full snapshot files from disk when grep is needed.

#### Scenario: Skill lists token order

- **WHEN** an agent reads the TermFold browser skill from app assets
- **THEN** the material SHALL describe a recommended verification order that minimizes MCP payload size

#### Scenario: Setup mentions efficiency

- **WHEN** `termfold-setup` completes successfully
- **THEN** linked tips SHALL mention snapshot `--changed` and wait tools for SPA workflows

### Requirement: Browser skill documents background preview expectations

The system SHALL document that agent browser tools MAY run while the preview UI is hidden when a session is bound, and that the user may need to keep the agent run active per foreground notification policy.

#### Scenario: Background automation documented

- **WHEN** an agent reads automation or browser skill material
- **THEN** it SHALL state that opening the preview bubble is not required for every browser command during an active bound session
