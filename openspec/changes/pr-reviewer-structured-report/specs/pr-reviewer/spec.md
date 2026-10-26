# Spec Delta

## Purpose

Lets users review a GitHub pull request with a facts pack, a structured finding report (ids, severity, surfaces), and cards in the PR Reviewer session, then choose which ids to fix.

## ADDED Requirements

### Requirement: Facts pack on the review copy

When a temp review copy is ready for a PR, the system SHALL write a facts pack into that copy: the merge-base…HEAD diff, the changed-file list, the GitHub PR title and body, and existing review comments. The review agent SHALL be instructed to treat that pack as the in-diff boundary.

#### Scenario: Pack after first copy

- **WHEN** the system provisions a new temp copy for a pasted PR
- **THEN** the copy contains the facts pack for that PR before the review chat is expected to produce a report

#### Scenario: Pack after switching PR

- **WHEN** the same session reuses the temp copy for another PR in the same repo
- **THEN** the facts pack is replaced so it matches the newly checked-out PR

### Requirement: Structured review report

The review agent SHALL emit one structured report per review turn that includes: a short summary of what the PR does, a risk rating, in-diff findings with stable ids and severity, out-of-diff findings in a separate list, file-level coverage, and missing tests or open questions. It SHALL then ask which finding ids to fix and SHALL NOT apply code changes until the user chooses.

#### Scenario: Report with findings

- **WHEN** the agent finishes a review turn and found in-diff issues
- **THEN** the structured report lists those findings with ids and severity, keeps out-of-diff items out of that list, and asks which ids to fix before changing files

#### Scenario: User picks finding ids

- **WHEN** the user replies with a subset of finding ids (or none)
- **THEN** the agent applies fixes only for the chosen in-diff ids, or applies none if the user chose none

### Requirement: Finding surfaces

Each in-diff finding SHALL be tagged with exactly one surface: Android, Linux guest, Shared, or CI/docs. iOS SHALL be used only when the facts pack includes iOS paths; otherwise that surface SHALL be omitted.

#### Scenario: Android-only repo

- **WHEN** the changed files have no iOS paths
- **THEN** the report does not include an iOS findings group

### Requirement: Review report cards

The PR Reviewer session SHALL parse the structured report from the ACP agent message stream and SHALL show it as cards: summary and risk, in-diff findings grouped by surface, out-of-diff findings, coverage, and missing tests or questions.

#### Scenario: Valid report in the chat stream

- **WHEN** a completed agent turn contains a parseable structured report
- **THEN** the review session shows those cards without requiring the user to expand a tool row

#### Scenario: Missing or invalid report

- **WHEN** a review turn completes without a parseable structured report
- **THEN** the session shows that the structured report is missing or invalid and still keeps the agent’s chat text

### Requirement: Web verify only on request

The review agent SHALL NOT look up docs or the public web while producing the structured report unless the user asked for web verification in that session.

#### Scenario: Default review

- **WHEN** the user has not asked to verify online
- **THEN** the report is produced without requiring web or browser lookups

#### Scenario: User asks to verify

- **WHEN** the user asks to verify findings on the web
- **THEN** the agent may use the browser for those findings and record quote plus URL on the items it checked
