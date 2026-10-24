# Spec Delta

## Purpose

Defines how agents and users interact with TermFold’s in-app preview browser: reading pages, acting with real input, waiting for conditions, staying within navigation policy, and giving humans visibility and override.

## ADDED Requirements

### Requirement: Snapshot includes shadow DOM controls

The system SHALL include interactable elements inside open shadow roots when building a snapshot, up to a documented depth limit, using the same numbering scheme as light-DOM controls.

#### Scenario: Button inside shadow root

- **WHEN** a page exposes a clickable control only inside an open shadow root and it is visible in the viewport
- **THEN** `browser_snapshot` lists that control with a ref usable by click, fill, and scroll tools

#### Scenario: Depth limit reached

- **WHEN** shadow nesting exceeds the configured depth limit
- **THEN** deeper controls are omitted and the snapshot text notes that shadow depth was capped

### Requirement: Snapshot and actions support same-origin iframes

The system SHALL allow scoping snapshot and element actions to a same-origin iframe identified by index, URL substring, or a frame element ref.

#### Scenario: Form inside iframe

- **WHEN** the agent snapshots with a frame scope matching a same-origin iframe containing a text field
- **THEN** the field appears in the snapshot and fill succeeds using its ref

#### Scenario: Cross-origin iframe

- **WHEN** the agent scopes to a cross-origin iframe
- **THEN** the tool returns a clear error that cross-origin frames cannot be automated in-app

### Requirement: Wait supports URL and network idle

The system SHALL extend wait tooling to block until a URL matches a pattern, until network activity is idle for a configurable interval, or until existing text/selector conditions are met.

#### Scenario: SPA route change

- **WHEN** the agent waits for a URL substring after a click
- **THEN** wait completes when the main frame location matches before timeout

#### Scenario: Network idle after fetch

- **WHEN** the agent waits for network idle after an action that triggers fetch requests
- **THEN** wait completes when no in-flight requests remain for the configured quiet period

#### Scenario: Wait timeout

- **WHEN** the condition is not met within the timeout
- **THEN** the tool returns an error naming the condition and elapsed time

### Requirement: Structured browser health summary

The system SHALL provide a single agent-callable summary of page health including at minimum: URL, title, viewport mode, count of console errors since load, count of failed network requests, and count of visible snapshot controls.

#### Scenario: Healthy local dev page

- **WHEN** the agent requests health on a loaded localhost page with no console errors and no failed requests
- **THEN** the summary reports zero errors and zero failed requests

#### Scenario: Broken page

- **WHEN** the page logged a console error and a request returned HTTP 404
- **THEN** the summary reports non-zero error and failed-request counts

### Requirement: Navigation allowlist for agent open

The system SHALL restrict agent-initiated navigation to URLs allowed by user settings: loopback and local dev hosts, the project file-serving host, and explicitly user-approved hostnames.

#### Scenario: Allowed localhost

- **WHEN** the agent opens `http://127.0.0.1:5173` and allowlist defaults apply
- **THEN** navigation proceeds

#### Scenario: Blocked external host

- **WHEN** the agent opens an HTTPS URL on a host not on the allowlist
- **THEN** navigation is refused with an error that names the host and points the user to Settings

#### Scenario: User approves host

- **WHEN** the user adds a hostname to the allowlist in Settings
- **THEN** subsequent agent opens to that host succeed

### Requirement: Clear site data for current origin

The system SHALL let the user clear cookies and web storage for the preview’s current origin without wiping other sites.

#### Scenario: Clear after auth test

- **WHEN** the user chooses clear site data while viewing a given origin
- **THEN** the next load of that origin does not reuse prior cookies or DOM storage for that origin

### Requirement: Preview shows recent agent actions

The system SHALL show a short rolling log of the last agent browser commands (action label and target summary) in the preview UI while an agent session is active.

#### Scenario: After agent click

- **WHEN** the agent clicks a snapshotted control
- **THEN** the preview log includes a click entry with the control description

### Requirement: User can use the page while agent is idle

The system SHALL allow touch and scroll in the preview WebView when no agent browser command is in progress, without requiring the user to resume from a full pause state.

#### Scenario: Idle interaction

- **WHEN** the agent is not running a browser command and the user scrolls the page
- **THEN** the page scroll position updates and the next agent snapshot reflects the new viewport

### Requirement: Open in system browser escape hatch

The system SHALL offer opening the current preview URL in the device’s default browser when in-app automation or rendering is insufficient.

#### Scenario: User opens externally

- **WHEN** the user chooses open in system browser for the current URL
- **THEN** the system browser opens that URL and the in-app preview may remain on the same page

### Requirement: Network log supports incremental read

The system SHALL allow agents to read network entries since a prior action or timestamp in addition to full and failed-only views.

#### Scenario: Since last action

- **WHEN** the agent requests network output with a since marker from a prior command
- **THEN** only entries after that marker are returned

### Requirement: Screenshot compare reports visual drift

The system SHALL support comparing a screenshot to a user- or project-supplied baseline image and return whether they match within a tolerance, without treating mismatch as a hard tool error.

#### Scenario: Match baseline

- **WHEN** the page appearance matches the baseline within tolerance
- **THEN** compare reports success

#### Scenario: Visual regression

- **WHEN** the page appearance differs beyond tolerance
- **THEN** compare reports mismatch and may include a fresh screenshot path for inspection

### Requirement: Agent screenshots respect size limits

The system SHALL encode agent-facing screenshots with bounded dimensions and quality so MCP image payloads stay suitable for mobile sessions.

#### Scenario: Large viewport

- **WHEN** the agent captures a full-pane screenshot on a high-resolution device
- **THEN** the returned image is downscaled or compressed within configured bounds

### Requirement: Snapshot after resume from pause

The system SHALL not return a stale snapshot while the preview WebView is paused or minimized until the view has resumed and layout is stable.

#### Scenario: Minimized during command

- **WHEN** the browser was minimized and an agent requests snapshot
- **THEN** the system resumes the WebView and returns snapshot data from the current visible content

### Requirement: Combobox state in snapshot

The system SHALL include expanded or collapsed state for elements with combobox/listbox roles when present in the snapshot list.

#### Scenario: Closed combobox

- **WHEN** a combobox is collapsed
- **THEN** the snapshot line indicates collapsed state

#### Scenario: Open listbox

- **WHEN** a combobox is expanded and options are visible
- **THEN** the snapshot includes option elements or notes that the list is open

### Requirement: Full snapshot file includes stable find hints

The system SHALL write extended find hints (role, accessible name, and coarse region) for each element in the full snapshot file used for grep and search, without changing short MCP snapshot line format.

#### Scenario: Re-find after DOM reorder

- **WHEN** the agent greps the full snapshot file for a label after a soft navigation
- **THEN** hints help locate the same control via find or text arguments even if numeric refs changed

### Requirement: Dev preview affordances

The system SHALL remember the last preview URL per workspace project and SHALL surface a one-tap open when a common dev-server port is listening in the guest.

#### Scenario: Port listening

- **WHEN** port 5173 accepts connections in the guest and the user opens the preview pane
- **THEN** the UI offers to open that URL in the preview

#### Scenario: Return to last preview

- **WHEN** the user reopens preview for a project that had a prior preview URL
- **THEN** the UI can restore that URL without retyping

### Requirement: Multiple preview tabs

The system SHALL support multiple in-app browser tabs per preview session, with one active tab for display and agent automation, up to a documented maximum count.

#### Scenario: User opens second tab

- **WHEN** the user opens a new tab from the preview chrome
- **THEN** a new tab appears in the tab strip and becomes active with a blank or default start page

#### Scenario: Agent opens URL in new tab

- **WHEN** the agent opens a URL with new-tab semantics
- **THEN** the URL loads in a new tab without unloading the previous tab

#### Scenario: Tab limit

- **WHEN** the number of open tabs would exceed the maximum
- **THEN** the system refuses the new tab with a clear error or closes the oldest tab per product policy stated in Settings help text

### Requirement: Agent tab management

The system SHALL provide agent-callable operations to list open tabs (id, title, url, active flag), switch the active tab, open a tab with an optional URL, and close a tab by id.

#### Scenario: List tabs

- **WHEN** the agent requests the tab list with two tabs open
- **THEN** the response includes both tabs and marks which is active

#### Scenario: Switch tab

- **WHEN** the agent switches to a non-active tab id
- **THEN** that tab becomes visible and subsequent snapshots target its document

#### Scenario: Close tab

- **WHEN** the agent closes the active tab and another tab remains
- **THEN** the closed tab is destroyed and another tab becomes active

### Requirement: Popups open as new tabs

The system SHALL load `target=_blank` and script-opened windows as new in-app tabs when they stay within navigation policy, instead of discarding the navigation.

#### Scenario: Link opens new window

- **WHEN** the user or agent activates a link that requests a new window to an allowed URL
- **THEN** a new tab opens with that URL and the original tab remains unchanged

### Requirement: Automation targets active tab by default

The system SHALL run snapshot, click, fill, wait, screenshot, console, and network tools against the active tab unless the call specifies a tab id.

#### Scenario: Snapshot after switch

- **WHEN** the agent switches tabs and snapshots without a tab id
- **THEN** the snapshot reflects the newly active tab URL and content

### Requirement: CAPTCHA detection and user handoff

The system SHALL detect likely CAPTCHA or bot-challenge UI on the active tab, pause agent browser automation, and prompt the user to solve the challenge on screen.

#### Scenario: Challenge appears

- **WHEN** a page shows a known challenge widget or challenge copy on the active tab
- **THEN** the preview shows a prominent handoff message and agent browser commands are refused until the challenge is cleared or the user dismisses a false positive

#### Scenario: Agent told not to auto-solve

- **WHEN** an agent receives documentation for the browser tools
- **THEN** it instructs the agent to ask the user to solve CAPTCHAs manually and not use automated solvers

### Requirement: Wait for CAPTCHA cleared

The system SHALL allow agents to wait until challenge heuristics no longer apply on the active tab or the user confirms continuation after solving.

#### Scenario: Wait succeeds after user solves

- **WHEN** the user completes a challenge and the page no longer matches captcha heuristics
- **THEN** a captcha-cleared wait completes successfully

#### Scenario: Wait times out

- **WHEN** a captcha-cleared wait exceeds its timeout while a challenge remains
- **THEN** the tool returns an error suggesting the user solve the challenge or dismiss a false detection

### Requirement: Health reports captcha state

The system SHALL include whether a captcha handoff is pending on the active tab in the structured browser health summary.

#### Scenario: Pending challenge

- **WHEN** captcha heuristics are active on the active tab
- **THEN** health reports captcha pending as true
