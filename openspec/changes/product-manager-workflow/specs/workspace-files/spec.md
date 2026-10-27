# Spec Delta

## ADDED Requirements

### Requirement: PM Discovery Files scope

When the user opens Files from an active Product Manager **Discovery** session linked to a golden folder, the Files tree SHALL root at that golden project directory. The Files header SHALL indicate the PM **view branch** when set and SHALL indicate read-only browsing when the PM lane does not grant write access to golden.

#### Scenario: Discovery with view branch

- **WHEN** Files is open from a Discovery PM session with `viewBranch` set
- **THEN** the header or subtitle shows the view branch name and that the tree is the golden project

#### Scenario: No write via PM on golden

- **WHEN** the user attempts file edits from Files while only a Discovery PM session is active
- **THEN** the system does not treat PM as authorization to mutate the golden tree beyond normal Files rules for non-agent sessions
