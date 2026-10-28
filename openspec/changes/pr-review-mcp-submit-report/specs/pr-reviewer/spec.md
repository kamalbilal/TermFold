# Spec Delta

## MODIFIED Requirements

### Requirement: Structured review report

The review agent SHALL emit one structured report per review turn that includes: a short summary of what the PR does, a risk rating, in-diff findings with stable ids and severity, out-of-diff findings in a separate list, file-level coverage, and missing tests or open questions. The agent SHALL submit that report by calling the TermFold **`submit_pr_review_report`** MCP tool with arguments that pass the app's schema validation before asking which finding ids to fix. The agent MAY still include a legacy fenced report in chat only as a fallback; the app SHALL treat a successful MCP submission as authoritative for that turn when both are present. The agent SHALL then ask which finding ids to fix and SHALL NOT apply code changes until the user chooses.

#### Scenario: Report with findings

- **WHEN** the agent finishes a review turn and found in-diff issues
- **THEN** the agent submits the structured report through **`submit_pr_review_report`**, the tool accepts it, and the structured report lists those findings with ids and severity, keeps out-of-diff items out of that list, and asks which ids to fix before changing files

#### Scenario: Invalid report arguments

- **WHEN** the agent calls **`submit_pr_review_report`** with arguments that fail schema validation
- **THEN** the tool returns a clear error describing the validation failure and the app does not show cards from that submission until a valid submit succeeds

#### Scenario: User picks finding ids

- **WHEN** the user replies with a subset of finding ids (or none)
- **THEN** the agent applies fixes only for the chosen in-diff ids, or applies none if the user chose none

### Requirement: Review report cards

The PR Reviewer session SHALL obtain the structured report for each review turn from a validated submission (MCP **`submit_pr_review_report`** and/or legacy parse of the agent message stream) and SHALL show it as cards anchored immediately below that turn's agent reply: summary and risk, in-diff findings grouped by surface, out-of-diff findings, coverage, and missing tests or questions. Card presentation SHALL use the same visual design as before this change (layout and color semantics unchanged). When a turn completes with a parseable report, that turn's cards SHALL remain at that turn when the user sends later messages in the same session. The session SHALL persist enough per-turn report data to restore those cards after the app process restarts.

#### Scenario: Valid report via MCP tool

- **WHEN** a completed agent turn includes a successful **`submit_pr_review_report`** call for that turn
- **THEN** the review session shows cards from that submission without requiring the user to expand a tool row or read JSON from chat

#### Scenario: Valid report in the chat stream

- **WHEN** a completed agent turn contains a parseable legacy fenced structured report and no valid MCP submission was stored for that turn
- **THEN** the review session shows those cards without requiring the user to expand a tool row

#### Scenario: Missing or invalid report

- **WHEN** a review turn completes without a valid MCP submission or parseable legacy structured report
- **THEN** the session shows that the structured report is missing or invalid and still keeps the agent's chat text

#### Scenario: Earlier turn cards after a new message

- **WHEN** the user completed a review turn with parseable cards and then sends another user message in the same session
- **THEN** the earlier turn's cards remain visible below that turn's agent reply

#### Scenario: Multiple review turns

- **WHEN** the user completes two or more review turns that each produce a valid structured report
- **THEN** each turn shows its own cards below that turn without removing or replacing earlier turns' cards

#### Scenario: Latest turn still streaming

- **WHEN** the agent is still responding on the latest user turn and that turn has no parseable report yet
- **THEN** the latest turn's footer does not display another turn's report as if it belonged to the active turn

#### Scenario: Restore saved session

- **WHEN** the user reopens a PR Reviewer session that had one or more saved turn reports
- **THEN** cards for each saved turn appear at the correct turn footers without requiring a new agent message
