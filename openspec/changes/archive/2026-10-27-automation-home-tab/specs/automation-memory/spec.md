# Spec Delta

## Purpose

Stores automation session history and secrets locally, shares navigation knowledge across sessions via a global database, and retrieves relevant memory with on-device semantic ranking so agents stay informed without loading full logs into every prompt.

## ADDED Requirements

### Requirement: Session event log records agent browser activity

The system SHALL append structured events to the active automation session’s private store for each material browser driver action and automation-relevant tool outcome.

#### Scenario: Browser click logged

- **WHEN** the agent performs a successful browser click in an automation session
- **THEN** the private store SHALL record event type, URL when known, summary text, and success outcome

#### Scenario: Tool failure logged

- **WHEN** a browser or automation memory tool fails
- **THEN** the private store SHALL record the failure with error summary

### Requirement: Private session store may hold credentials

The private session store SHALL allow storing named credential records with encrypted payloads and searchable metadata. Only that session’s agents SHALL retrieve decrypted values through dedicated tools.

#### Scenario: Store credential

- **WHEN** the agent stores a named credential for the session
- **THEN** the system SHALL persist encrypted fields and SHALL index metadata for search without embedding secret values in global memory

#### Scenario: Retrieve credential via tool

- **WHEN** the agent requests a named credential through the session secret tool
- **THEN** the system SHALL return decrypted fields to the agent runtime and SHALL NOT automatically paste them into the user-visible chat composer

### Requirement: Global navigation memory excludes secrets

The global navigation store SHALL accept only navigation-oriented records (flows, selectors, pitfalls, landmarks, URL patterns). Writes containing credentials, tokens, cookies, or similar secrets SHALL be rejected.

#### Scenario: Global write rejected for password

- **WHEN** an agent attempts to write a global memory row that includes a password or API key
- **THEN** the write SHALL fail and the agent SHALL be directed to session secret storage

#### Scenario: Global navigation write succeeds

- **WHEN** an agent writes a pitfall describing a broken checkout selector for a host
- **THEN** the global store SHALL persist the row without human approval

### Requirement: Semantic vector index on device

The system SHALL compute text embeddings on-device for memory chunks in both global navigation and session private stores and SHALL use vector similarity as the primary ranking step for memory search tools and prompt injection selection.

#### Scenario: Search uses embeddings

- **WHEN** the agent searches session memory with a natural-language query
- **THEN** the system SHALL rank chunks by embedding similarity within that session before returning results

#### Scenario: Global search scoped by host

- **WHEN** the agent searches global navigation memory while a page with host H is active
- **THEN** results SHALL prefer rows associated with host H after vector ranking

### Requirement: Thin mandatory memory inject per prompt

Before each agent prompt in an automation session, the system SHALL inject a bounded context block containing session constraint documents, a short session-state summary, recent structured events, and top-ranked non-secret memory chunks per documented token caps.

#### Scenario: Constraints always present

- **WHEN** the user sends a message in an automation session with a constraints document
- **THEN** the outbound prompt SHALL include that document’s content regardless of chat compression state when anchoring rules apply

#### Scenario: Credentials not bulk-injected

- **WHEN** the session store contains decrypted credential material
- **THEN** the mandatory inject SHALL NOT include decrypted secret values

### Requirement: Memory search tools for agents

The system SHALL expose agent-callable tools to search session memory, fetch recent events, read and write session secrets, and search or write global navigation memory subject to the global secret rejection rule.

#### Scenario: Session search returns capped results

- **WHEN** the agent calls session memory search
- **THEN** the response SHALL return at most the documented number of chunks with optional expansion for a single id

#### Scenario: Autonomous global write

- **WHEN** the agent writes global navigation memory after a failed flow
- **THEN** the write SHALL succeed without user confirmation when content passes validation

### Requirement: Similar navigation rows merge

When a new global navigation write is semantically similar to an existing row for the same site key, the system SHALL update the existing row’s counts and timestamps instead of creating unbounded duplicates.

#### Scenario: Repeated pitfall

- **WHEN** the agent writes a pitfall substantially matching an existing global row for the same host
- **THEN** the system SHALL increment failure or verification metadata on the existing row
