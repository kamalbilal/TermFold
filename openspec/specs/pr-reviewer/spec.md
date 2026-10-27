# pr-reviewer Specification

## Purpose
Lets users review a GitHub pull request with a facts pack, a structured finding report (ids, severity, surfaces), and cards in the PR Reviewer session, then choose which ids to fix.

## Requirements

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

### Requirement: Workflows lists PR Reviewer

The system SHALL show a **PR Reviewer** section on the Workflows home list alongside Automation and More.

#### Scenario: Open PR Reviewer

- **WHEN** the user opens Workflows and selects PR Reviewer
- **THEN** the app shows the PR Reviewer section (not Automation projects)

### Requirement: Paste GitHub PR URL to start

The system SHALL start a review only from a pasted GitHub pull request URL. It SHALL NOT require a branch picker or a list of open PRs.

#### Scenario: Valid PR URL

- **WHEN** the user pastes `https://github.com/owner/repo/pull/123` (optional extra path after the number) and confirms
- **THEN** the system parses owner, repo, and PR number and continues resolution

#### Scenario: Not a GitHub PR URL

- **WHEN** the user pastes text that is not a GitHub pull request URL
- **THEN** the system shows a clear error and does not create a review session or workspace copy

### Requirement: Match local project by origin

The system SHALL match the PR repository to a coding folder whose git `origin` URL identifies the same GitHub `owner/repo`, treating HTTPS, a trailing `.git`, and SSH `git@github.com:owner/repo.git` as equivalent.

#### Scenario: Origin matches

- **WHEN** a local folder’s origin is `git@github.com:owner/repo.git` and the PR URL is `https://github.com/owner/repo/pull/123`
- **THEN** that folder is the review’s golden project

#### Scenario: Repo not cloned

- **WHEN** no coding folder origin matches the PR’s `owner/repo`
- **THEN** the system shows a clear error that the project is not on the device, does not clone a golden repo, and does not provision a temp copy

### Requirement: Auto temp copy on PR head branch

When resolution succeeds, the system SHALL fetch the PR’s head branch and provision a temp session workspace copy of the matching golden project checked out to that branch, without a user branch picker. Agent file changes SHALL target that copy only, never the golden checkout.

#### Scenario: First PR in a session

- **WHEN** the user starts a review from a valid PR URL whose repo is cloned locally and the session has no temp copy
- **THEN** the system creates one temp copy on the PR head branch and opens the review chat on that copy

#### Scenario: Head branch missing locally

- **WHEN** the PR head branch is not present on the golden checkout
- **THEN** the system fetches it from the PR remote and still provisions the copy on that branch

### Requirement: Reuse temp copy in the same session

While a review session still has a temp copy, the system SHALL reuse that copy for another PR in the same session instead of provisioning a second copy. For the same repository, it SHALL fetch and check out the new PR’s head branch in the existing copy.

#### Scenario: Second PR same repo

- **WHEN** the user pastes another PR URL for the same repo while this session’s temp copy still exists
- **THEN** the system does not create a new copy and switches the existing copy to that PR’s head branch

#### Scenario: Copy missing after Done

- **WHEN** the user pastes a PR URL in a session whose temp copy was already deleted
- **THEN** the system provisions a new temp copy on that PR’s head branch and keeps the same chat session

### Requirement: Done deletes copy and keeps chat

The system SHALL provide a **Done** control on the review session. Activating Done SHALL delete the temp workspace copy and SHALL keep the ACP session and chat timeline.

#### Scenario: Clean copy

- **WHEN** the user taps Done and the temp copy has no uncommitted changes and no unpushed local commits
- **THEN** the copy directory is deleted and the chat remains

#### Scenario: Dirty or unpushed

- **WHEN** the user taps Done and the temp copy has uncommitted files or commits not on the PR remote
- **THEN** the system asks for confirmation before deleting and does not delete if the user cancels

#### Scenario: Already deleted

- **WHEN** the user taps Done and no temp copy exists
- **THEN** the chat remains and no error is treated as a failed review

### Requirement: Review reports issues before fixes

After loading the PR, the agent SHALL produce a review that lists the issues it found and SHALL ask the user which issues to fix (all, a subset, or none) before applying code changes.

#### Scenario: Issues found

- **WHEN** the review turn completes with one or more issues
- **THEN** the chat shows the full issue list and waits for the user’s choice before applying fixes

#### Scenario: User declines fixes

- **WHEN** the user chooses not to fix any listed issues
- **THEN** the system does not apply code changes on the temp copy

### Requirement: Private or API failure is explicit

When GitHub metadata cannot be loaded (including private repositories without credentials), the system SHALL fail with a clear error and SHALL NOT provision a temp copy.

#### Scenario: Private PR without auth

- **WHEN** the GitHub API rejects the PR fetch as unauthorized or not found
- **THEN** the user sees a clear error and no temp copy is created
