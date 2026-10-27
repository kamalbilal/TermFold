# Design

## Context

See `proposal.md` (Why). Today automation flows through `BrowserBridge` (loopback HTTP + token), `BrowserDriver` (main-thread WebView + `HELPER` in-page script), `PreviewPane` (WebView, network/console rings, viewport modes), and `termfold-browser.js` (CLI + MCP). Snapshots are viewport-biased with a full list at `/tmp/termfold-browser/snapshot.txt`. Input uses real `MotionEvent`/`KeyEvent`; fill/select use `__tf` JS helpers.

## Goals / Non-Goals

**Goals:**

- Extend `__tf` and driver actions without breaking existing MCP tool names or ref-based flows.
- Keep snapshots token-cheap; push depth (shadow, iframe, full file) to optional scopes and on-disk search.
- Centralize navigation policy in one Kotlin type shared by `open` and user URL bar (user bar may be broader than agent).
- Ship incremental value in phases testable on emulator (helper JS unit tests via WebView-less parsing where possible).

**Non-Goals:**

- CDP/Chrome Custom Tabs automation, Service Worker introspection, WebRTC.
- Automated CAPTCHA bypass (commercial solvers, vision models, scripted token injection).
- Persisting allowlist across app backup restore beyond normal prefs.

## Decisions

### 1. Shadow DOM traversal in `__tf`

**Choice:** Recursive `querySelectorAll` over open shadow roots only, max depth 4, same `SEL` as light DOM.

**Alternatives:** Full accessibility tree (too heavy); pierce closed shadow (impossible without hacks).

**Rationale:** Matches what users see in open components (Lit, Shoelace). Closed roots stay out of scope; snapshot footer mentions cap when `shadowCapped: true` in JSON.

### 2. Iframe scope

**Choice:** `snapshot`/`point`/`fill` args gain optional `frame`: integer index in document order, or `ref` of `<iframe>`, or URL substring match on `contentWindow.location` (same-origin only).

**Alternatives:** Always flatten frames (breaks isolation); CDP target switching (non-goal).

**Implementation:** Resolve frame element → `contentDocument` → run existing helpers with `root` set; cross-origin returns structured error code `cross_origin_frame`.

### 3. Network idle wait

**Choice:** Track in-flight count in `PreviewPane` network list (increment on intercept start, decrement on finish) exposed to driver; idle = zero in-flight for `quietMs` (default 500, max 5000).

**Alternatives:** `PerformanceObserver` in page (misses subresources without hook); fixed delay (flaky).

**Note:** Intercept only sees WebView network; WebSocket long-poll may block idle—document `timeout` escape.

### 4. `browser_health` / extended `status`

**Choice:** New driver action `health` returning JSON fields; MCP adds `browser_health` tool. Existing `status` stays human one-liner for CLI backward compat.

### 5. Navigation allowlist

**Choice:** `BrowserNavPolicy` in app prefs: default allow `localhost`, `127.0.0.1`, `::1`, `[::1]`, `10.*` / `192.168.*` optional toggle, `Preview.FILES_HOST`, and user-editable host set (normalized, no paths). Agent `open` checks before `navigate`; user manual URL entry unchecked (user intent).

**Alternatives:** Block all external (too harsh for personal use); no policy (security gap).

### 6. Action log & shared control

**Choice:** `BrowserBridge` maintains `Deque<ActionEntry>` (max 8) updated on each successful `run`; `PreviewPane` reads via `StateFlow`. Shared control: when `running.get() == 0` and not `paused`, WebView stays touch-enabled; when command runs, optional brief lock (today’s behavior) without forcing pause.

**Pause** still hard-stops agent commands per existing UX.

### 7. Clear site data

**Choice:** `CookieManager.removeAllCookies` scoped via `WebStorage.deleteAllData` + `clearMatches` for origin only (API 22+ patterns); reload after.

### 8. Network `--since`

**Choice:** Monotonic `networkGeneration` bumped per driver action; entries store `gen`. MCP `browser_network` gets `since_generation` optional int.

### 9. Screenshot compare

**Choice:** Reuse `pictureHash` at 96×96; compare tool arg `baseline` path (guest path under project). Return `{ match, distance, path? }`. Mismatch not `isError`.

**Future:** optional baseline dir per project in design only—not v1.

### 10. Screenshot size cap

**Choice:** Max long edge 1280px JPEG quality 82 for MCP `image` payloads; PNG remains for user save-to-project.

### 11. Dev port hint

**Choice:** Lightweight guest probe from app: on preview pane visible, `ss` or parse `/proc/net/tcp` via one-shot shell for ports `{5173,3000,8080,4173,8000}`; show chip “Dev server on :5173”. Remember last URL in `ProjectState` prefs key per project path.

**Alternatives:** Parse agent terminal output (fragile); always auto-open (annoying).

### 12. Stable hints in full snapshot

**Choice:** Add `hint` field in JSON items: `role|name|scrollBucket` where scrollBucket = `floor(scrollY/400)`. Written only to file, not short MCP output.

### 13. Combobox state

**Choice:** In `describe()`, if `role=combobox` or `aria-expanded` present, set `expanded` boolean; when expanded, include visible `[role=option]` in snapshot (cap 20).

### 14. Bundled skill sync

**Choice:** Update `termfold-browser-skill.md` and `TOOLS` array in `termfold-browser.js` together; copy skill to `~/.claude/skills` on setup unchanged path.

### 15. Multi-tab model

**Choice:** `BrowserTabSession` holds ordered tabs `{ id, title, url, webViewState }` with max **8** tabs; one **active** tab drives `BrowserDriver`’s `web` reference. Inactive tabs keep WebViews in memory but call `onPause()` when not visible to limit work.

**Agent API:** MCP tools `browser_tab_list`, `browser_tab_new` (optional url), `browser_tab_switch` (id), `browser_tab_close` (id, default active). `open` gains `new_tab: true` (default false for backward compat). All existing tools accept optional `tab_id` (default active).

**Popups:** Enable `WebChromeClient.onCreateWindow` + `setSupportMultipleWindows(true)` with a handler that creates a new tab and loads the requested URL instead of blocking popups.

**User UI:** Horizontal tab row under address bar; close affordance; long-press reorder out of v1.

**Alternatives:** Single WebView + history only (cannot test multi-page flows); Custom Tabs per tab (no agent control).

### 16. CAPTCHA human handoff

**Choice:** Heuristic detector in `__tf` + URL/host checks:

- iframes/scripts from known challenge hosts (`google.com/recaptcha`, `hcaptcha.com`, `challenges.cloudflare.com`, …)
- visible text matching challenge phrases (“verify you are human”, “complete the security check”, …)
- elements with `aria-label` containing “captcha” (case-insensitive)

When confidence ≥ threshold, set `BrowserBridge` state `captchaPending` on that tab, auto-**pause** agent browser commands (existing pause UX), show banner “Solve the challenge on screen, then tap Continue”.

**Agent flow:** `browser_wait` with `captcha_cleared: true` (or CLI `wait --captcha-cleared`) blocks until heuristics clear for **active tab** or user taps **Continue** after solving. `browser_health` includes `captcha_pending: boolean`.

**Alternatives:** Auto-solve (rejected—ToS, brittle, unsafe); ignore (agents loop forever).

**Skill:** Replace “Do not try to solve CAPTCHAs” with “Ask the user to solve; use wait for captcha cleared.”

### 17. Tab-aware snapshots and policy

**Choice:** Navigation allowlist applies per `open`/`tab_new` URL. Console/network rings are **per tab** so health reflects active tab only. Action log entries include tab id.

## Risks / Trade-offs

- **[Risk] Shadow walk performance on huge trees** → Cap elements at 400 (existing) and depth at 4; prefer `scope` for agents.
- **[Risk] Network idle never fires on SSE** → Document timeout; add `wait` text fallback in skill.
- **[Risk] Allowlist too permissive on LAN** → Default LAN off; show warning in Settings.
- **[Risk] iframe security** → Strict same-origin check on `contentDocument`; no `postMessage` bridge in v1.
- **[Risk] Compare false positives** → Hash-only v1; skill says use human review for critical UI.
- **[Risk] Memory with many WebViews** → Cap at 8 tabs; pause inactive WebViews; close oldest on overflow with user toast.
- **[Risk] CAPTCHA false positive** → User can tap “Not a captcha” to dismiss banner; agent wait uses timeout.
- **[Risk] CAPTCHA inside cross-origin iframe** → Detection via iframe src still works; user solves visually; no automation inside frame.

## Migration Plan

1. Ship helper JS + driver actions (backward compatible).
2. Add MCP tools (old agents ignore).
3. Add Settings allowlist with safe defaults matching today’s de facto behavior (localhost + file host).
4. Introduce tab session (default single tab = today’s behavior).
5. Add captcha detection + handoff banner.
6. Update skill asset; users get new skill on next setup or app update.

Rollback: disable allowlist enforcement via pref flag if needed (keep code path, default on).

## Open Questions

- Default allow private LAN IPs for `npm run dev --host` on phone hotspot setups?
- Should user URL bar respect the same allowlist as agents (stricter product) or stay permissive?
