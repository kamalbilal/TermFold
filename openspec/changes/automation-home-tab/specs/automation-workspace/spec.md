# Spec Delta

## Purpose

Gives users a dedicated Automation area on the home screen to organize browser-automation projects and sessions with isolated storage, optional links to coding projects, and a session workspace tuned for preview-first agent work.

## ADDED Requirements

### Requirement: Home shows Projects and Automation segment

The system SHALL show a segmented control on the home destination that switches between the existing coding **Projects** list and an **Automation** list without changing bottom navigation tabs.

#### Scenario: Switch to Automation

- **WHEN** the user selects Automation on the home segment
- **THEN** the UI SHALL list automation projects instead of coding folders

#### Scenario: Switch back to Projects

- **WHEN** the user selects Projects on the home segment
- **THEN** the UI SHALL show the existing folder list unchanged in behavior

### Requirement: Automation projects contain sessions

The system SHALL let users create automation projects and, within each project, create any number of automation sessions with distinct names and metadata.

#### Scenario: Create automation project

- **WHEN** the user creates a new automation project
- **THEN** the system SHALL persist it and show it in the Automation list

#### Scenario: Add session to project

- **WHEN** the user adds a session inside an automation project
- **THEN** the system SHALL assign a stable session id and open or offer entry into that session workspace

### Requirement: Session storage layout on guest filesystem

Each automation session SHALL own a dedicated directory under TermFold-managed guest storage containing at minimum profile data, documentation files, encrypted secrets, and memory database files.

#### Scenario: New session directories

- **WHEN** an automation session is created
- **THEN** the system SHALL create its session directory before the first browser or agent use

### Requirement: Optional linked coding project

An automation project MAY reference zero or one coding folder by id. The user SHALL be able to link, change the link, or unlink at any time.

#### Scenario: Link folder

- **WHEN** the user links a coding folder to an automation project
- **THEN** the system SHALL store the folder id and expose linked guest path context to that automation project’s sessions

#### Scenario: Unlink folder

- **WHEN** the user unlinks the coding folder
- **THEN** session browser profiles and automation-owned files SHALL remain intact and agent file access to the former linked tree SHALL stop on subsequent turns

#### Scenario: Linked folder deleted

- **WHEN** the linked coding folder no longer exists
- **THEN** the UI SHALL show a broken-link state and SHALL offer unlink or re-link without deleting automation data

### Requirement: Linked coding tree is read-only for agents

When a coding folder is linked, agent file tools SHALL read and list files under that folder’s guest project path but SHALL reject write, patch, and delete operations targeting that path.

#### Scenario: Read linked README

- **WHEN** an agent reads a file under the linked project guest path during an automation session
- **THEN** the operation SHALL succeed

#### Scenario: Write blocked on linked tree

- **WHEN** an agent attempts to modify a file under the linked project guest path
- **THEN** the tool SHALL fail with an error that the linked project is read-only

### Requirement: Session screen is preview-first

Opening an automation session SHALL present agent chat and in-app preview as primary surfaces, with access to session documentation and run history.

#### Scenario: Enter session

- **WHEN** the user opens an automation session
- **THEN** the UI SHALL show agent chat and SHALL bind preview to that session’s browser profile

### Requirement: Automation data included in backup

TermFold backup and restore SHALL include automation project metadata, per-session directories (including profile and private memory), and the global navigation memory database.

#### Scenario: Backup after automation use

- **WHEN** the user creates a full TermFold backup
- **THEN** the archive SHALL contain automation projects and session stores created on device
