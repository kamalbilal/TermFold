# Design

## Context

TermFold runs Debian 13 under PRoot with projects on app-private storage (`files/linux/rootfs/...`). Every guest `execve` is traced; `GuestCompat` copies files when hard links fail. Today `TMPDIR` is `/tmp` inside the rootfs disk tree, `/dev/shm` is a host dir bind at `files/tmp/shm`, and there are no Node/npm cache defaults. Users report slow **every** `npm run lint`—mostly CPU, I/O, and process spawn cost, not GPU.

See `proposal.md` for motivation and scope.

## Goals / Non-Goals

**Goals:**

- Reduce repeated lint/install latency via faster scratch and stable cache env vars.
- Make performance debugging one command (`termfold-bench`).
- Keep changes inside existing `shell` package boundaries (`ProotCommand`, `ShellPaths`, `ShellSetup`, `ShellRuntime`).

**Non-Goals:**

- GPU/Vulkan/OpenCL passthrough to the guest.
- Replacing PRoot or moving projects to `/sdcard`.
- Auto-editing user `package.json` / ESLint configs.
- Guaranteed speedup floors (PRoot remains the ceiling).

## Decisions

### 1. Scratch layout under `files/tmp/`

**Choice:** Add `ShellPaths.guestScratchDir(context)` (e.g. `files/tmp/guest-scratch`) with subdirs `tmp`, `npm-cache`, `node-compile-cache`, created in `ShellRuntime` alongside existing `shm`.

**Rationale:** Reuses the same app-private tree as `/dev/shm`; avoids W^X and shared-storage limits documented in `Projects.kt`.

**Alternatives:**

- *Bind only `/tmp` to `cacheDir`* — rejected: Android `cacheDir` may be cleared by the system; user caches would vanish unpredictably.
- *tmpfs in guest* — not available without kernel support in PRoot; fake with RAM disk not portable on Android.

### 2. Bind mounts in `ProotCommand.build`

**Choice:**

- Bind `guestScratchDir/tmp` → guest `/tmp` (overrides rootfs `/tmp` for sessions).
- Optionally bind `guestScratchDir/npm-cache` → a stable guest path such as `/var/cache/termfold/npm` (not `/root/.npm` on rootfs disk) and set `npm_config_cache` to that path.

**Rationale:** Lint and npm hit `/tmp` and npm cache heavily; separating them from the multi-GB rootfs reduces contention with dpkg state and projects.

**Alternative:** Symlink `/tmp` during setup only — rejected: bind mount is already the pattern for `/dev/shm` and survives without mutating the rootfs image.

### 3. Guest environment keys

**Choice:** Extend `guestEnvironment()`:

| Variable | Value |
|----------|--------|
| `TMPDIR` | `/tmp` (after bind) |
| `NODE_COMPILE_CACHE` | `/var/cache/termfold/node-compile-cache` (bind target) |
| `npm_config_cache` | `/var/cache/termfold/npm` |

Use `extraEnv` merge order unchanged so agents can override.

**Rationale:** Documented Node 22+ compile cache; npm respects `npm_config_cache`.

### 4. `termfold-bench` as shell script in assets

**Choice:** Add `termfold-bench.sh` to assets; install via `ShellSetup` to `/usr/local/bin/termfold-bench` like `termfold-setup`.

**Contents (approx):**

- `nproc`, `free -h` (or `/proc/meminfo` one-liner if `free` missing)
- Test `-f /var/lib/termfold/l2s-migrated`
- `/usr/bin/time` or bash `SECONDS` around `node -e ''` and a 1000× read of a small file in scratch

**Rationale:** No Kotlin JNI; easy to iterate; runs in user's environment.

### 5. Documentation touchpoints

**Choice:** One paragraph in `termfold-setup.sh` success output + a subsection in `termfold-browser-skill.md` ("Phone performance: ESLint `--cache`, avoid type-aware rules in daily `lint`, try `turbo --concurrency=1`").

### 6. Low-RAM guard (soft)

**Choice:** Do not cap cache size in v1; document that clearing `files/tmp/guest-scratch` is safe. Revisit if OOM reports arrive.

**Alternative:** Max size with `du` prune in setup — deferred.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Scratch grows large (npm cache) | Document clear path; optional Settings "Clear tool caches" later |
| Bind over `/tmp` breaks maintainer scripts expecting rootfs `/tmp` | `/tmp` is meant for ephemeral data; rootfs `/tmp` unused after bind |
| Bench timings noisy on thermally throttled phones | Print disclaimer; run twice suggestion in help text |
| Extra bind mounts slow PRoot startup slightly | Only two mounts; measure in bench before/after |

## Migration Plan

1. Ship scratch dirs + binds + env in app update; existing rootfs needs no re-extract.
2. `ShellSetup.install` re-run on upgrade path in `ShellRuntime` ensures `termfold-bench` exists.
3. No user data migration; npm may repopulate cache on first run after path change.
4. Rollback: remove binds and env keys; caches left on disk harmless.

## Open Questions

- Whether to bind `eslint` default cache location under project `node_modules` (project-specific; stay documented only).
- Settings UI for "Clear guest caches" vs terminal-only `rm -rf` instructions in v1.
