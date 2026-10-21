# Tasks

## 1. Scratch paths and provisioning

- [x] 1.1 Add `ShellPaths.guestScratchDir` with subpaths for `tmp`, `npm-cache`, and `node-compile-cache`; ensure directories are created in `ShellRuntime` provisioning alongside `shmDir` and verify dirs exist on a cold start via unit test or small `ShellPaths` test.
- [x] 1.2 Add guest-side cache mount points under `var/cache/termfold/` in rootfs setup if needed (mkdir in provisioning) and verify host paths resolve under `files/tmp/guest-scratch`.

## 2. PRoot binds and guest environment

- [x] 2.1 Bind guest scratch `tmp` over `/tmp` and cache dirs over `/var/cache/termfold/npm` and `node-compile-cache` in `ProotCommand.build`; verify bind list includes new `-b` entries without breaking existing `/dev/shm` binds.
- [x] 2.2 Extend `guestEnvironment()` with `TMPDIR`, `NODE_COMPILE_CACHE`, and `npm_config_cache` per design; add JVM unit test asserting keys and that `extraEnv` overrides win.
- [x] 2.3 Run `./gradlew :app:testDebugUnitTest` for new tests and confirm green.

## 3. termfold-bench

- [x] 3.1 Add `app/src/main/assets/termfold-bench.sh` with CPU, memory, migration marker, and timed node/file micro-benchmarks; install to `/usr/local/bin/termfold-bench` via `ShellSetup` and verify script is listed in rootfs after setup install path runs.
- [x] 3.2 Manual/device check: run `termfold-bench` in TermFold shell and confirm exit 0 and readable timing output (record sample in PR notes).

## 4. User-facing documentation

- [x] 4.1 Add phone lint/install tips to `termfold-setup.sh` success banner (ESLint `--cache`, type-aware split, turbo concurrency).
- [x] 4.2 Add matching subsection to `termfold-browser-skill.md` and verify asset is packaged in debug APK.

## Workflow follow-up

- Archive with `/opsx-archive debian-perf` after device verification and sync `openspec/specs/bundled-linux-guest/spec.md`.
