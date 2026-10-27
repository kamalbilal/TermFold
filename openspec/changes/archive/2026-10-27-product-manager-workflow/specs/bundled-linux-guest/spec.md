# Spec Delta

## ADDED Requirements

### Requirement: Product Manager agent skill

The system SHALL ship a bundled agent skill that documents the Product Manager workflow: discovery vs release modes, read-only view branch, research and refresh controls, competitor list and bounded web fetch, citation rules (repo, web, brief, gap), decision memo structure, and session-scoped memory tools for findings and prior answers.

#### Scenario: Agent loads PM skill

- **WHEN** a Product Manager session starts an ACP agent with access to bundled skills
- **THEN** the skill text describes how to run repo-at-ref reads and competitive fetches without writing to golden

#### Scenario: Citation rule in skill

- **WHEN** the agent prepares a market or competitor claim
- **THEN** the skill instructs the agent to cite a URL from the research log or mark the claim as a gap
