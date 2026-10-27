# Spec Delta

## ADDED Requirements

### Requirement: Product Manager session chat kind

The system SHALL support a distinct **Product Manager** ACP session kind whose chat timeline uses the same message and tool-call presentation as coding sessions, with PM-specific prompt injection and deliverable formatting.

#### Scenario: Open PM session chat

- **WHEN** the user opens an active Product Manager session
- **THEN** the chat screen uses the PM session kind and shows PM session header context (mode, view branch or release branch)

### Requirement: PM per-turn context injection

For Product Manager sessions, the system SHALL inject session brief documents (vision, constraints, competitors, release brief when applicable), a capped summary of the latest research pack, and a capped set of top-ranked non-secret chunks from that session’s private memory store on every user turn sent to the agent.

#### Scenario: Follow-up after research

- **WHEN** the user sends a follow-up message after a research pass completed
- **THEN** the agent prompt includes the research pack summary and relevant session memory chunks without requiring the user to repeat prior context

### Requirement: PM structured deliverable cards

Product Manager agent replies that include a decision memo SHALL be renderable as structured in-chat sections (recommendation, landscape matrix, options, scope in/out, risks, gaps) when the agent emits the agreed PM response format.

#### Scenario: Decision memo visible

- **WHEN** the agent completes a Research and decide response
- **THEN** the user can read the decision memo and evidence tags in the chat without opening external files

### Requirement: PM agent memory tools

Product Manager sessions SHALL expose agent-callable tools to search, write, and fetch by id records in the current session’s private memory store only.

#### Scenario: Search is session-scoped

- **WHEN** the agent searches PM memory
- **THEN** results are only chunks from the current PM session id

#### Scenario: Write from another session id rejected

- **WHEN** a tool call attempts to write memory for a different session id
- **THEN** the write fails and the current session store is unchanged
