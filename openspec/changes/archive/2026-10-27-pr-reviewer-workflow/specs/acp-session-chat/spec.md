# Spec Delta

## ADDED Requirements

### Requirement: PR review turns include PR identity

For a PR review session, each user-initiated agent turn SHALL include the current pull request identity (repository, number, and head branch) in the prompt context sent to the agent.

#### Scenario: First review turn

- **WHEN** the user sends a message in a review session bound to a resolved GitHub PR
- **THEN** the agent turn includes that PR’s repository, number, and head branch

#### Scenario: PR replaced in same chat

- **WHEN** the user binds a different PR URL to the same review session
- **THEN** subsequent turns include the new PR identity and not the previous PR as current

### Requirement: Review chat survives workspace deletion

Deleting a PR review temp workspace SHALL NOT clear the session’s saved chat timeline. Reopening the same review session SHALL show the previous messages.

#### Scenario: Done then reopen

- **WHEN** the user taps Done (temp copy deleted) and later opens the same review session
- **THEN** the chat timeline from before Done is still shown
