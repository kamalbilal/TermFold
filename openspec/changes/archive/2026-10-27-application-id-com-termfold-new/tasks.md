# Tasks

## 1. Android application id

- [x] 1.1 Set `applicationId = "com.termfold.new"` in `app/build.gradle.kts` (keep `namespace = "com.termfold.app"`) and verify `./gradlew :app:compileDebugKotlin` succeeds
- [x] 1.2 Add a distinct launcher label for this build (for example `app/src/debug/res/values/strings.xml` with `TermFold Dev`) and verify the merged `app_name` differs from release in a debug resource merge / installed launcher

## 2. In-app updates

- [x] 2.1 When `BuildConfig.APPLICATION_ID != "com.termfold.app"`, hide or disable the Settings update install path so GitHub release APKs for the store id cannot be triggered from the dev app; verify with a unit test or small test hook that the update UI/install gate is off for `com.termfold.new`
- [x] 2.2 Confirm release-id behavior unchanged: with `applicationId` temporarily set to `com.termfold.app` in a local test, update flow still compiles and existing `UpdateManagerTest` passes

## 3. Tests and fixtures

- [x] 3.1 Replace hardcoded `/data/user/0/com.termfold.app/` (and similar) in unit tests with `BuildConfig.APPLICATION_ID` or the test application id constant; verify `./gradlew :app:testDebugUnitTest` passes

## 4. Project setup documentation

- [x] 4.1 Add a **Developing this fork** section to `README.md`: run fetch scripts, `assembleDebug`, package names (`com.termfold.app` vs `com.termfold.new`), side-by-side install, and separate Debian/projects data; verify commands in the section match the repo scripts

## 5. Device verification

- [x] 5.1 On a device with store/GitHub `com.termfold.app` installed, install debug APK and confirm two launcher entries and `adb shell pm list packages | grep termfold` shows both ids

## Workflow follow-up

- Archive the change after review and sync `openspec/specs/android-app-identity/spec.md` via the project's OpenSpec archive workflow.
