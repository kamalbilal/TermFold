# Spec Delta

## Purpose

Manage multiple shell sessions attached to an agent workspace from the session header and terminal dialog, with explicit confirmation before terminating a tab and its processes.

## ADDED Requirements

### Requirement: Header menu lists open aux terminal tabs

The system SHALL list each open aux terminal tab for the current agent session under the session header overflow menu Terminal section, showing the tab label and allowing the user to focus that tab by opening the terminal UI on the selected tab.

#### Scenario: Multiple tabs open

- **WHEN** the user opens the overflow menu on a Cursor agent session with two or more aux terminal tabs
- **THEN** each tab appears as a selectable row under Terminal and choosing a row opens the terminal dialog with that tab selected

#### Scenario: Single default tab

- **WHEN** only the default aux terminal tab exists
- **THEN** the menu still offers Terminal to open the dialog and shows the single tab row

### Requirement: Close aux tab requires confirmation

The system SHALL ask for confirmation before closing an aux terminal tab from either the header menu or the in-dialog tab bar, and SHALL terminate that tab's shell session and processes only after the user confirms.

#### Scenario: Confirm close from dialog tab bar

- **WHEN** the user taps close on an aux terminal tab chip in the terminal dialog and confirms the prompt
- **THEN** that tab is removed, its PTY session is closed, and another open tab becomes selected if the closed tab was active

#### Scenario: Dismiss close prompt

- **WHEN** the user taps close on a tab and dismisses the confirmation without confirming
- **THEN** the tab remains open and its session keeps running

#### Scenario: Confirm close from header menu

- **WHEN** the user chooses close on a tab row in the header overflow menu and confirms
- **THEN** the tab is removed and its session is closed the same way as from the dialog

#### Scenario: Last tab cannot be closed

- **WHEN** only one aux terminal tab remains
- **THEN** close actions are not offered for that tab

### Requirement: Soft keyboard returns on terminal tap

The system SHALL show the soft keyboard again when the user taps the aux terminal surface after previously hiding the keyboard, without requiring a new terminal tab.

#### Scenario: Keyboard dismissed then tap terminal

- **WHEN** the aux terminal dialog is open, the soft keyboard was visible and the user dismissed it, and the user taps inside the terminal output area
- **THEN** the terminal regains input focus and the soft keyboard is shown again

#### Scenario: Same tab after dismiss

- **WHEN** the user repeats dismiss and tap on the same aux terminal tab
- **THEN** the keyboard shows on each tap, matching the main in-app terminal screen behavior
