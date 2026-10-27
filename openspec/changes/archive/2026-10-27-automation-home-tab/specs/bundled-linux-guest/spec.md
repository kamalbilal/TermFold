# Spec Delta

## ADDED Requirements

### Requirement: Bundled skill documents automation memory and session workflow

The system SHALL ship bundled agent skill material that describes the Automation home workflow, per-session browser profiles, read-only linked coding projects, session versus global memory tools, semantic search usage, and the rule that global memory never stores credentials.

#### Scenario: Agent loads automation skill from assets

- **WHEN** an agent reads TermFold automation documentation installed from app assets
- **THEN** the material SHALL list session memory search, session secret tools, and global navigation memory write constraints

#### Scenario: Setup references automation

- **WHEN** `termfold-setup` completes successfully
- **THEN** success output or linked tips SHALL mention the Automation tab for browser testing with persistent sessions
