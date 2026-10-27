# Spec Delta

## Purpose

Defines how TermFold identifies itself on Android so a development build can coexist with the published app without sharing install slot or app-private data.

## ADDED Requirements

### Requirement: Distinct development application id

The development build produced from this repository SHALL declare an Android `applicationId` of `com.termfold.new` so it can be installed alongside the published TermFold app (`com.termfold.app`).

#### Scenario: Side-by-side install

- **WHEN** the user installs the published TermFold APK (`com.termfold.app`) and then installs a debug or release APK built from this fork with the dev application id
- **THEN** Android reports two separate installed applications and both appear in the launcher

#### Scenario: No upgrade over release id

- **WHEN** the user installs a build with application id `com.termfold.new`
- **THEN** Android does not treat that install as an update to `com.termfold.app`

### Requirement: Isolated app-private data

The system SHALL store Android app-private data (preferences, databases, Debian rootfs, projects, and downloads) under the installing package’s private storage for `com.termfold.new`, independent of `com.termfold.app`.

#### Scenario: Fresh dev install

- **WHEN** the user opens TermFold for the first time after installing `com.termfold.new` while `com.termfold.app` is already set up
- **THEN** the dev app runs its own Linux provisioning and project list without reading the release app’s private files

### Requirement: Recognizable launcher label

The system SHALL show a launcher label that distinguishes the `com.termfold.new` build from the published TermFold app so the user can open the intended install.

#### Scenario: Launcher shows two TermFold entries

- **WHEN** both `com.termfold.app` and `com.termfold.new` are installed
- **THEN** the two launcher entries have visibly different labels (for example one includes “Dev” or “New”)

### Requirement: Release updates scoped to matching application id

When the in-app update feature offers a GitHub release APK, the system SHALL only install that APK when it is intended for the currently running application id, and SHALL NOT prompt the user to install a release APK that would replace or conflict with a different installed TermFold package.

#### Scenario: Dev build does not hijack release update

- **WHEN** the user runs the `com.termfold.new` build and the update check finds a GitHub APK built for `com.termfold.app`
- **THEN** the app does not start an install that would upgrade `com.termfold.app` from the dev build’s update UI

#### Scenario: Release build can still self-update

- **WHEN** a future build still uses `com.termfold.app` as its application id and the update APK matches that id
- **THEN** the existing in-app update install flow remains available for that build
