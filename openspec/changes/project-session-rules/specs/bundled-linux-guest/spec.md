# Spec Delta

## ADDED Requirements

### Requirement: Skill documents project session rules anchor

The system SHALL install bundled agent guidance (Claude skill and the same body in other agents’ instruction notes, following the existing browser-skill install pattern) that describes the `termfold-project-rules` block, states that enabled rules in that block are mandatory on every turn while enabled, and explains that an explicit user instruction on that turn overrides a conflicting rule with a brief note.

#### Scenario: Agent reads bundled TermFold guidance

- **WHEN** `termfold-setup` (or equivalent guest install) writes agent skills and notes from app assets
- **THEN** the installed material SHALL mention the project-rules tag and mandatory-follow semantics
