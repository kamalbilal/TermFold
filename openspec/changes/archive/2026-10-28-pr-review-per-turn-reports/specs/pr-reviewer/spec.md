# Spec Delta

## MODIFIED Requirements

### Requirement: Review report cards

The PR Reviewer session SHALL parse the structured report from each review turn's agent message stream and SHALL show it as cards anchored immediately below that turn's agent reply: summary and risk, in-diff findings grouped by surface, out-of-diff findings, coverage, and missing tests or questions. When a turn completes with a parseable report, that turn's cards SHALL remain at that turn when the user sends later messages in the same session. The session SHALL persist enough per-turn report data to restore those cards after the app process restarts.

#### Scenario: Valid report in the chat stream

- **WHEN** a completed agent turn contains a parseable structured report
- **THEN** the review session shows those cards without requiring the user to expand a tool row

#### Scenario: Missing or invalid report

- **WHEN** a review turn completes without a parseable structured report
- **THEN** the session shows that the structured report is missing or invalid and still keeps the agent's chat text

#### Scenario: Earlier turn cards after a new message

- **WHEN** the user completed a review turn with parseable cards and then sends another user message in the same session
- **THEN** the earlier turn's cards remain visible below that turn's agent reply

#### Scenario: Multiple review turns

- **WHEN** the user completes two or more review turns that each produce a parseable structured report
- **THEN** each turn shows its own cards below that turn without removing or replacing earlier turns' cards

#### Scenario: Latest turn still streaming

- **WHEN** the agent is still responding on the latest user turn and that turn has no parseable report yet
- **THEN** the latest turn's footer does not display another turn's report as if it belonged to the active turn

#### Scenario: Restore saved session

- **WHEN** the user reopens a PR Reviewer session that had one or more saved turn reports
- **THEN** cards for each saved turn appear at the correct turn footers without requiring a new agent message
