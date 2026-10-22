# Spec Delta

## ADDED Requirements

### Requirement: Branch picker loading and error feedback

The system SHALL show a visible loading indicator in every branch picker modal while branch names are being loaded from git.

The system SHALL show an error state with a retry path when branch enumeration fails or times out, instead of a blank list with no feedback.

The system SHALL NOT cache a failed load in a way that prevents the next open from attempting a fresh load.

#### Scenario: Switch branch modal loading

- **WHEN** the user opens the Switch branch picker and branches are not yet available from a valid cache
- **THEN** a loading indicator is shown until the list is ready or an error is shown

#### Scenario: New copy from branch modal loading

- **WHEN** the user opens Start a new copy from branch and branches are not yet available from a valid cache
- **THEN** a loading indicator is shown until the list is ready or an error is shown

#### Scenario: Load failure with retry

- **WHEN** branch enumeration fails or times out
- **THEN** the picker shows an error message and the user can tap refresh to retry

#### Scenario: No stuck blank after bad cache

- **WHEN** a prior load did not complete successfully
- **THEN** reopening the picker does not show an empty list without loading or error feedback
