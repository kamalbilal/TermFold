# product-manager Specification

## Purpose
Lets users run evidence-backed product discovery and release planning from Workflows: link a coding project, view the repo at a chosen branch without checking out on golden, optionally research competitors on the public web, and receive structured decision memos with citations.

## Requirements

### Requirement: Workflows lists Product Manager

The system SHALL show a **Product Manager** section on the Workflows home list alongside Automation, PR Reviewer, and More.

#### Scenario: Open Product Manager

- **WHEN** the user opens Workflows and selects Product Manager
- **THEN** the app shows the PM session list and a way to start a new PM session

### Requirement: PM session modes Discovery and Release

The system SHALL support creating a PM session in one of two modes: **Discovery** (golden-linked) or **Release planning** (temp workspace copy on a branch).

#### Scenario: Start Discovery session

- **WHEN** the user starts a new PM session and chooses Discovery and selects a coding folder
- **THEN** the session links to that folder’s golden guest path, sets `viewBranch` to the golden HEAD branch name, and does not provision a temp copy

#### Scenario: Start Release session

- **WHEN** the user starts a new PM session and chooses Release planning, selects a folder, and selects a branch
- **THEN** the system provisions a temp session workspace copy on that branch and binds the PM chat to that copy’s guest path

### Requirement: Discovery read-only view branch

In Discovery mode, the system SHALL let the user pick a branch with a read-only branch picker that sets a **view branch** ref for research only. The system SHALL NOT run `git checkout` on the golden project tree to change the view branch.

#### Scenario: Change view branch

- **WHEN** the user selects branch `main-prod` in the read-only picker on a Discovery session
- **THEN** the session stores `viewBranch = main-prod` and subsequent repo research reads git objects at that ref

#### Scenario: Golden working tree unchanged

- **WHEN** the user changes view branch on Discovery
- **THEN** the golden checkout’s current branch and working tree are unchanged by the picker

### Requirement: Release Done deletes copy and keeps chat

Release planning sessions SHALL provide **Done**. Activating Done SHALL delete the temp workspace copy and SHALL keep the PM ACP session and chat timeline.

#### Scenario: Done on release session

- **WHEN** the user taps Done on a Release PM session with a provisioned copy
- **THEN** the temp copy is removed and the chat remains available in the session list

### Requirement: Competitor list and optional web research

The system SHALL let the user maintain a competitor name list per PM session and SHALL support an **include competitive research** toggle. When enabled and the list is non-empty, the system SHALL fetch bounded public pages for those competitors before synthesizing a decision.

#### Scenario: Competitive pass with homepage URLs

- **WHEN** competitive research is enabled and at least one competitor has a homepage URL
- **THEN** the research phase includes a competitive snapshot for those competitors with cited URLs

#### Scenario: Name without URL

- **WHEN** competitive research is enabled and a competitor has a name but no homepage URL
- **THEN** the system does not invent a domain and records that competitor as a gap instead of fetching

#### Scenario: No competitors

- **WHEN** competitive research is enabled but the competitor list is empty
- **THEN** the system skips the web competitive pass and MAY prompt the user to add competitors with URLs

### Requirement: Decision memo with evidence classes

PM responses that answer a product question SHALL include a **Decision memo** section with a clear recommendation and SHALL tag supporting points as **verified in repo** (path and ref), **from web** (URL), **from session brief**, or **gap** (needs user input). The system SHALL NOT present unsourced competitor or market claims as verified.

#### Scenario: Repo-backed recommendation

- **WHEN** the user asks what to build next and repo research finds an existing module
- **THEN** the decision memo cites the repo path at the view branch or copy branch

#### Scenario: Missing evidence

- **WHEN** the user asks for market sizing and no web or brief source exists
- **THEN** the memo lists the topic under gaps and does not invent figures

### Requirement: Research before synthesis on explicit research action

The system SHALL run a combined repo (and optional competitive) research pass when the user sends the first message in a thread or taps an explicit **Research and decide** control, and SHALL reuse the cached research pack for follow-up messages until the user requests refresh.

#### Scenario: First message triggers research

- **WHEN** the user sends the first prompt in a new PM thread
- **THEN** the UI shows research progress before the decision memo is shown

#### Scenario: Refresh research

- **WHEN** the user requests refresh research
- **THEN** the system rebuilds the research pack and updates the collapsed research log

### Requirement: Isolated session memory database

Each Product Manager session SHALL have a private memory store keyed only by that session id. Other PM sessions, Automation memory, and coding chats SHALL NOT read or write that store. The agent SHALL be able to search and write findings and answers in that store. The system SHALL also record completed research packs and decision memos into it.

#### Scenario: Agent writes a finding

- **WHEN** the PM agent writes a finding chunk for the current session
- **THEN** the chunk is persisted in that session’s store and is not visible to another PM session’s search

#### Scenario: Later question recalls prior decision

- **WHEN** the user asks a follow-up in the same PM session after a decision was recorded
- **THEN** memory search or prompt injection can surface that prior decision without the user restating it

#### Scenario: Done keeps memory

- **WHEN** the user taps Done on a Release session and the temp copy is deleted
- **THEN** the session memory store remains and is available when the chat is reopened

#### Scenario: Session delete removes memory

- **WHEN** the user deletes a PM session
- **THEN** that session’s memory store is removed

### Requirement: Header session memory manager

The Product Manager chat screen SHALL expose a header overflow item that opens a manager for **this session’s** private memory only. The manager SHALL let the user search, filter by kind, browse chunks, open a chunk to read it, add, edit, and delete chunks (delete requires confirmation). Changes SHALL be visible to later agent memory search and prompt injection.

#### Scenario: Open from overflow

- **WHEN** the user opens the PM session overflow menu and chooses session memory
- **THEN** the app shows that session’s memory chunks, not another session’s and not Automation global navigation memory

#### Scenario: Search and filter

- **WHEN** the user types a search query or selects a kind filter in the manager
- **THEN** the list shows only matching chunks from the current PM session

#### Scenario: User deletes a chunk

- **WHEN** the user confirms delete on a chunk
- **THEN** that chunk is removed from the session store and no longer appears in agent memory search for that session
