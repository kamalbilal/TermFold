# Spec Delta

## ADDED Requirements

### Requirement: Review session Files follow temp copy lifetime

When a PR review session is bound to a completed temp workspace copy, the Files tree SHALL root at that copy. After the copy is deleted (Done), Files SHALL NOT present the deleted directory as a browsable workspace.

#### Scenario: Copy present

- **WHEN** the user opens Files during a PR review whose temp copy still exists
- **THEN** the file tree is rooted at that copy’s project root

#### Scenario: After Done

- **WHEN** the user opens Files on a PR review session after Done deleted the temp copy
- **THEN** the UI does not browse the removed copy path as if it were still the session workspace
