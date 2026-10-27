# Design

## Context

See `proposal.md` (Why). Today `BrowserDriver` is bound to `WebView` (`evaluateJavascript`, touch events, `draw` screenshots). `BrowserBridge.execute` waits for `Preview.open()` and fails when the app is backgrounded and no controller is registered. Automation design assumed destroying the WebView when not visible. `termfold-browser` MCP already caps snapshot output and writes the full list to `/tmp/termfold-browser/snapshot.txt`. Prior change deltas (`termfold-browser-improvements`, `automation-home-tab`) define rich browser automation requirements not yet merged to main specs.

## Goals / Non-Goals

**Goals:**

- GeckoView as the engine for `http`/`https` preview and agent automation (A + B profiles).
- Session registry: profiles survive UI hide and background agent runs; no cold reload on return.
- Headless command path: MCP works without preview visible when an agent run is active.
- Preserve token economics of existing MCP tools (no second browser server).
- Tiered power: wake lock only during active browser commands; warm idle between turns.
- Built-in WebExtension + native messaging to run the same `__tf` page script as today.

**Non-Goals:**

- Debian guest Playwright/Chromium or `@playwright/mcp`.
- Driving Chrome/Firefox as separate installed apps.
- Immortal casual preview tabs (C).
- Full parity for `files.termfold` on Gecko in v1 (hybrid WebView acceptable).
- Guaranteed survival if Android kills the process (best-effort with FGS + existing `SessionWatcher`).

## Decisions

### 1. Single shared `GeckoRuntime`, many `GeckoSession`s

**Choice:** One runtime per app process; each A/B profile gets `GeckoSessionSettings.contextId` (e.g. `auto-<sessionId>`, `coding-<key>`).

**Alternatives:** One runtime per session (too heavy); keep WebView (fails React fidelity).

### 2. `BrowserSessionController` + `BrowserSurface` interface

**Choice:** Kotlin interface abstracts load URL, evaluate JS, input, screenshot, console/network hooks. `GeckoBrowserSurface` implements via extension RPC; optional `WebViewBrowserSurface` for `files.termfold` only in phase 1.

**Alternatives:** Fork `BrowserDriver` entirely (duplicate snapshot logic).

### 3. Built-in WebExtension for `__tf`

**Choice:** Ship `assets/gecko-termfold/` extension; `ensureBuiltIn` on startup; native `MessageDelegate` maps to existing `BrowserPageScript.HELPER` operations (snapshot, click via coords, etc.).

**Alternatives:** Public Gecko one-off JS API (not offered); reimplement snapshot in Kotlin (token drift).

### 4. Session registry vs preview UI lifecycle

**Choice:** Register controller when automation session is created or when coding agent binds browser; unregister on session end. `PreviewPane` **attaches** to an existing session for display (mirror) or detaches without destroying session.

**Alternatives:** Tie session life to Compose `AndroidView` (current bug: destroy on tab key).

### 5. Background `BrowserBridge` path

**Choice:** If `session_key` resolves to a registered controller, run driver without `Preview.open()` wait loop. Use existing `SessionWatcher` / partial wake lock when agent has in-flight browser work. Notify user via existing foreground notification, not “open browser” blocker.

**Alternatives:** Require bubble open (rejected by product).

### 6. Power tiers

| Tier | When | Behavior |
|------|------|----------|
| `RUNNING` | Browser MCP command in flight or explicit wait | Full JS, extension RPC, wake lock if agent already holds |
| `IDLE_WARM` | Agent between turns, session exists | Session stays in memory; throttle painting; no per-command wake |
| `IDLE_COLD` | Memory pressure or user pauses automation hours | Persist profile on disk; release session; next command warms (documented) |

**Choice:** Default A/B = warm while session exists; cold only under documented caps (max N warm sessions, LRU evict non-agent casual only first).

### 7. Token policy (platform)

**Choice:** No new verbose tools. Enforce short tool responses in driver; document in skill: prefer `wait`, `snapshot --changed`, `outline`, `console --errors`; discourage screenshot unless `if_changed`. Optional session digest file on disk for long runs (automation guest dir)—agent reads via file tools, not repeated snapshots.

**Alternatives:** Ship Playwright MCP (rejected—token and duplicate tools).

### 8. MCP reliability

**Choice:** Keep 90s command timeout; add extension RPC timeout; single-flight per session; cancel on agent turn cancel; clear JSON errors (`timeout`, `session_busy`, `engine_not_ready`).

### 9. Dependency

**Choice:** `org.mozilla.geckoview:geckoview` stable channel from `https://maven.mozilla.org/maven2/`, arm64-v8a primary ABI in release splits.

## Risks / Trade-offs

- **[Risk] APK + RAM** → Mitigation: one runtime, cap warm sessions, optional cold tier; Play feature delivery later if needed.
- **[Risk] Extension bridge complexity** → Mitigation: spike in phase 0; reuse `__tf` verbatim.
- **[Risk] Background kill by OEM** → Mitigation: FGS during agent run; honest docs; no “100% without app process.”
- **[Risk] Battery on long warm sessions** → Mitigation: IDLE_WARM throttling; wake lock only RUNNING.
- **[Risk] Two engines (Gecko + WebView files)** → Mitigation: phase 1 hybrid; route by URL scheme/host in `Preview.open`.

## Migration Plan

1. Phase 0: Gecko spike loads localhost Vite URL; verify paint + extension eval.
2. Phase 1: Registry + headless bridge; Gecko for http(s); WebView for `files.termfold`.
3. Phase 2: Preview UI attaches to registry; remove destroy-on-tab-switch.
4. Phase 3: Power tiers + warm session caps; skill update.
5. Rollback: feature flag `previewEngine=webview` for http(s) if spike fails (short-term).

## Open Questions

- Exact max warm sessions (default proposal: 3 agent-bound)?
- Whether IDLE_COLD is user-visible in v1 or only under memory pressure?
