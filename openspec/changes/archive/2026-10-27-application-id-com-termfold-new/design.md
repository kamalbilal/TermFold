# Design

## Context

See `proposal.md` — Why. Today `app/build.gradle.kts` sets `applicationId` and `namespace` to `com.termfold.app`. Manifest providers already use `${applicationId}` for authorities; most runtime code uses `context.packageName` / `BuildConfig.APPLICATION_ID`. A full Kotlin package rename is unnecessary for side-by-side install and would touch hundreds of files.

Published releases and GitHub APKs are built with `com.termfold.app`. This fork targets local development with a second install slot.

## Goals / Non-Goals

**Goals:**

- One-line (plus docs) switch of `applicationId` to `com.termfold.new` for all variants built from this branch, or at minimum for `debug` if we need release parity with upstream later.
- Clear launcher differentiation.
- Safe behavior for Settings → Updates when the running id is not `com.termfold.app`.
- README steps for first-time clone setup (existing fetch scripts + Gradle) and installing beside the store app.

**Non-Goals:**

- Renaming Kotlin packages from `com.termfold.app` to `com.termfold.new`.
- Migrating data from `com.termfold.app` into `com.termfold.new` (separate apps by design).
- Changing upstream release `applicationId` or Play/GitHub distribution for the main product.
- Product flavors for both ids in one artifact (single dev id on this branch is enough unless requirements change).

## Decisions

1. **Change `applicationId` only, keep `namespace = "com.termfold.app"`**  
   Android distinguishes installs by `applicationId`; R class and manifest class names stay under the existing namespace.  
   *Alternative:* Rename all packages — rejected due to merge cost and no user-visible benefit.

2. **Use `com.termfold.new` exactly as requested**  
   Not `com.termfold.new.app`, to maximize distance from `com.termfold.app` and match user intent.  
   *Alternative:* `com.termfold.app.dev` — rejected; user asked for `com.termfold.new`.

3. **Launcher label via `app_name` override**  
   Prefer `app/src/debug/res/values/strings.xml` with `TermFold Dev` so release builds on this branch (if any) can keep `TermFold`, or set `app_name` in `defaultConfig` if every variant from this fork should show Dev.  
   *Alternative:* Different launcher icon — optional follow-up; label is enough for v1.

4. **Gate in-app updates when `applicationId != "com.termfold.app"`**  
   GitHub release APKs are built for the release id. `UpdateManager` signature check compares against the running package; installing a release APK from the dev app would still target the wrong package if we triggered install. Hide or disable the update card when `BuildConfig.APPLICATION_ID != "com.termfold.app"`, or skip install with a short explanation in Settings.  
   *Alternative:* Repoint updates to a dev release channel — out of scope until such releases exist.

5. **Project setup documentation in README**  
   Consolidate existing **Building** steps plus: install both APKs, expect duplicate Debian download, adb package names for debugging. No new tooling unless fetch scripts are missing on a clean clone (document `tools/fetch-shell-runtime.py` and `tools/fetch-ca-bundle.py` as prerequisites).

6. **Tests**  
   Replace hardcoded `com.termfold.app` in path fixtures with `BuildConfig.APPLICATION_ID` or a test constant aligned with the test runner’s application id.

## Risks / Trade-offs

- **[Risk] Double storage and setup** — User runs Debian bootstrap twice. → Document clearly in README; acceptable for dev.
- **[Risk] Accidentally shipping `com.termfold.new` in a public release** — Wrong Play/GitHub identity. → Mitigation: keep release CI on upstream id; if this branch publishes APKs, label them dev-only in docs.
- **[Risk] Obtainium / share intents** — User may pick wrong TermFold in share sheet. → Distinct launcher label; optional distinct `share_title` in debug resources later.
- **[Trade-off] No data sync** between installs — By Android design; backups are per-package.

## Migration Plan

1. Merge implementation on this branch.
2. Uninstall any previous debug APK that used `com.termfold.app` only if the user wants a single dev copy (optional); installing `com.termfold.new` does not remove `com.termfold.app`.
3. `./gradlew :app:assembleDebug` and install; verify two apps in launcher.
4. Rollback: revert `applicationId` in Gradle and reinstall over the old id (dev data for `.new` remains until uninstalled).

## Open Questions

- Should **release** builds on this fork also use `com.termfold.new`, or only **debug**? Proposal assumes all variants from this branch use the dev id unless we split flavors later.
