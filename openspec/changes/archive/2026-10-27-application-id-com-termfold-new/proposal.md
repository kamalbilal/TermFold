# Proposal

## Why

This fork is used for day-to-day development and experiments while the published TermFold build (`com.termfold.app`) stays installed on the same device. Android only allows one installed app per `applicationId`, so the dev build needs a distinct id. Documenting the one-time project setup makes it repeatable on a new machine or clone.

## What Changes

- Set the Android `applicationId` to `com.termfold.new` (release id remains `com.termfold.app` on the store/GitHub APK).
- Keep the Kotlin/Java `namespace` and source packages as `com.termfold.app` so the diff stays small and upstream merges stay practical.
- Differentiate the dev install in the launcher (for example `TermFold Dev` via `app_name` or a debug-only resource overlay).
- Ensure in-app GitHub update/install flows do not replace or conflict with the store build when ids differ (hide, disable, or clearly scope updates to the matching package).
- Add a short **Developing this fork** section to the README: fetch scripts, `assembleDebug`, installing `com.termfold.new` beside `com.termfold.app`, and that data/Linux state are per-app (no shared storage with the release install).
- Fix any tests that hardcode `com.termfold.app` as the installed package when asserting paths tied to `applicationId`.

**BREAKING** for this branch only: local debug APKs are no longer upgrades over `com.termfold.app`; they install as a second app with empty app-private storage.

## Capabilities

### New Capabilities

- `android-app-identity`: Distinct Android application id for dev builds, launcher labeling, and update/install behavior when not on the release id.

### Modified Capabilities

_None._

## Impact

- `app/build.gradle.kts` (`applicationId`, optional `resValue` / build-type labels)
- `app/src/main/res/values/strings.xml` (or debug `src/debug` resources) for launcher name
- `app/src/main/java/com/termfold/app/update/UpdateManager.kt` and Settings update UI if GitHub APK targets `com.termfold.app`
- Unit tests with hardcoded `/data/user/0/com.termfold.app/...` paths
- README **Building** / new **Developing this fork** section
- OpenSpec main spec `openspec/specs/android-app-identity/spec.md` on archive
