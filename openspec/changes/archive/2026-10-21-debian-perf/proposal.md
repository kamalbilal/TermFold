# Proposal

## Why

Node-heavy workflows (`npm run lint`, installs, agents) feel disproportionately slow on TermFold compared to a PC. The bottleneck is not missing GPU compute: it is PRoot per-process overhead, guest file I/O on app-private storage, and cold caches on every shell session. TermFold should ship safe defaults and scratch paths that squeeze more throughput from the phone CPU and storage without asking users to hand-tune the guest.

## What Changes

- Set **guest environment defaults** on every PRoot session for common Node/npm tooling (compile cache, npm cache location under a dedicated scratch tree).
- Provide a **fast scratch area** under app `filesDir` (bind-mounted or symlinked into the guest) for `/tmp`, npm cache, and optional tool caches, with bounded size behavior on low-RAM devices.
- Add **`termfold-bench`** (guest command) that prints a short diagnostic report: CPU count, memory, GuestCompat migration status, and timed micro-benchmarks (node spawn, small file read) so users and support can see PRoot vs project cost.
- Document **phone-oriented dev tips** (ESLint cache, type-aware lint split) in the bundled agent skill / setup output—not auto-editing user repos.
- **Out of scope**: GPU passthrough, replacing PRoot, remote lint/build services, or changing ESLint configs inside user projects.

## Capabilities

### New Capabilities

- `bundled-linux-guest`: Performance-oriented guest environment, scratch/cache layout, and diagnostics for the in-app Debian/PRoot shell.

### Modified Capabilities

- (none)

## Impact

- `ProotCommand.kt` — extra bind mounts and `guestEnvironment()` keys.
- `ShellPaths.kt` / `ShellRuntime.kt` — ensure scratch dirs exist at provisioning and session start.
- `ShellSetup.kt` / `termfold-setup.sh` — install `termfold-bench`, mention caches in Ready banner.
- `app/src/main/assets/termfold-browser-skill.md` (or adjacent doc) — lint/install guidance for phone.
- Unit tests for path resolution, env map contents, and bench script smoke (where testable on host).
