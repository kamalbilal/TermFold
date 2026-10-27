# Spec Delta

## Purpose

Lets users review a GitHub pull request from Workflows: paste a PR link, bind automatically to the matching local project and PR head branch in an isolated temp copy, report issues before any fix, and delete that copy with Done while keeping the chat.

## ADDED Requirements

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
