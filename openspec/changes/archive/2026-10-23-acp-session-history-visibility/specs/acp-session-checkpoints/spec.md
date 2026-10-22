# Spec Delta

## ADDED Requirements

### Requirement: Hidden wire ids are per TermFold session tab

The system SHALL store hidden wire session ids (from checkpoint wire resync retire, user delete, or new session) keyed by the TermFold session tab identifier together with agent and workspace, so hiding in one tab does not remove entries from another tab's Earlier sessions list.

#### Scenario: Two tabs same workspace

- **WHEN** tab A retires wire session ids during checkpoint resync and tab B has a different remembered wire id in the same workspace
- **THEN** tab B's Earlier sessions list still includes wire ids that tab B has not hidden

#### Scenario: Same tab after resync

- **WHEN** tab A completes wire resync and retires the old and new clone wire ids for that tab
- **THEN** those ids are hidden only when listing past sessions for tab A
