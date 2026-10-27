# Spec Delta

## ADDED Requirements

### Requirement: Skill documents native network inspector

The system SHALL document in bundled `termfold-browser` skill and MCP tool text how agents use summary `browser_network` list with `since_cursor`, filters, and preserve log; `browser_network_detail` for headers and bodies; low-token debugging order (failed/search then one or two details); and that capture is cleared on app restart and on navigation when preserve is off.

#### Scenario: Agent reads skill

- **WHEN** an agent loads the bundled TermFold browser skill
- **THEN** the skill describes network list, detail, preserve log, and session-scoped capture

#### Scenario: MCP tool descriptions

- **WHEN** an agent lists MCP browser tools
- **THEN** network and network_detail descriptions state that list output excludes bodies and detail truncates large bodies
