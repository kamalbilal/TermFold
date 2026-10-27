# Spec Delta

## ADDED Requirements

### Requirement: Skill documents session network rules

The system SHALL document in bundled `termfold-browser` skill and MCP tool text how agents block and override responses with URL patterns, that rules are per bound session, and that they must use those tools rather than page `fetch` patches.

#### Scenario: Agent reads skill

- **WHEN** an agent loads the bundled TermFold browser skill
- **THEN** the skill describes block and override-body tools, URL wildcards, session isolation, and reading the network log to pick patterns
