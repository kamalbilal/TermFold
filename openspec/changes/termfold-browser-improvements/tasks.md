# Tasks

## 1. In-page helper (`__tf`)

- [x] 1.1 Extend `HELPER` with shadow-root traversal (depth 4, `shadowCapped` flag) and verify snapshot JSON includes a control inside an open shadow root on a local test HTML fixture via `BrowserDriver` or JS eval test
- [x] 1.2 Add same-origin `frame` resolution to `snapshot`, `point`, `fill`, and related helpers; verify cross-origin frame returns `cross_origin_frame` error in driver response
- [x] 1.3 Add combobox `expanded` state and option listing when open; verify snapshot lines on a fixture with `role=combobox`
- [x] 1.4 Add `hint` on snapshot items and write hints in full `snapshot.txt`; verify grep finds control by hint after simulated reorder

## 2. Driver actions and waits

- [x] 2.1 Implement `health` action and wire `browser_health` MCP tool; verify JSON fields match spec on loaded about:blank and error page fixtures
- [x] 2.2 Extend `wait` for URL pattern and network idle using in-flight counter from `PreviewPane`; verify unit or instrumentation test for idle after fetch completes
- [x] 2.3 Pass `frame` through driver args to `call()` helpers; verify click/fill inside iframe fixture
- [x] 2.4 Defer snapshot until WebView resumed after minimize/pause; verify snapshot not empty/stale in manual test checklist

## 3. Network and screenshots

- [x] 3.1 Add `networkGeneration` on entries and `since_generation` on network tool; verify only new entries returned after a click
- [x] 3.2 Cap MCP screenshot JPEG size/quality per design; verify returned base64 decodes under size budget on large WebView
- [x] 3.3 Implement `screenshot` compare with baseline path using `pictureHash`; verify match/mismatch responses without `isError` on mismatch

## 4. Navigation policy and site data

- [x] 4.1 Add `BrowserNavPolicy` prefs and Settings UI for allowlist hosts; verify blocked external host on agent `open` and allowed localhost
- [x] 4.2 Enforce policy in `BrowserDriver.open` only (user bar unchanged per design); verify error message mentions Settings
- [x] 4.3 Add clear site data for current origin in preview menu; verify cookies cleared with follow-up `eval` on `document.cookie`

## 5. Multi-tab

- [x] 5.1 Introduce `BrowserTabSession` with active tab, max 8, and wire `PreviewPane` tab strip UI; verify user can open two tabs and switch between them
- [x] 5.2 Implement per-tab WebView lifecycle (pause inactive, destroy on close); verify memory stays bounded when closing tabs
- [x] 5.3 Handle `onCreateWindow` / `target=_blank` as new tab within nav policy; verify popup link opens second tab
- [x] 5.4 Add driver actions and MCP tools for tab list, new, switch, close, and optional `tab_id` on existing tools; verify snapshot URL changes after `browser_tab_switch`
- [x] 5.5 Extend `open` with `new_tab` flag in JS CLI and MCP; verify first tab unchanged when opening second URL with new tab

## 6. CAPTCHA handoff

- [x] 6.1 Add `__tf` captcha heuristics and `captchaPending` state per tab; verify fixture page with recaptcha iframe sets pending
- [x] 6.2 Show handoff banner, refuse agent commands while pending, and “Not a captcha” dismiss; verify pause/resume matches existing agent browser UX
- [x] 6.3 Add `wait` captcha_cleared mode and include `captcha_pending` in health JSON; verify wait completes after heuristics clear on fixture
- [x] 6.4 Update skill text to require user solve (remove “do not try” in favor of handoff flow); verify skill asset contains captcha section

## 7. Preview UX

- [x] 7.1 Expose rolling agent action log via `BrowserBridge` and show in preview toolbar; verify log updates after MCP click
- [x] 7.2 Allow touch/scroll when no command in flight without requiring resume from pause; verify user scroll changes snapshot viewport
- [x] 7.3 Prominent “Open in system browser” for current URL; verify intent launches external browser
- [x] 7.4 Remember last preview URL per project and dev-port chip (5173/3000/8080 probe); verify chip appears when port listens in guest

## 8. CLI, MCP, and bundled skill

- [x] 8.1 Update `termfold-browser.js` TOOLS and CLI flags (tabs, health, network since, wait modes including captcha, compare, frame scope); verify `termfold-browser mcp` tools/list includes new tools
- [x] 8.2 Update `termfold-browser-skill.md` and setup banner pointer; verify asset packaged in debug APK and skill text matches tools
- [x] 8.3 Add `bundled-linux-guest` setup tip line for preview browser; verify `termfold-setup` success output includes tip

## 9. Tests and build

- [x] 9.1 Add JVM tests for nav policy normalization, tab id parsing, and captcha heuristic helpers where testable without WebView; verify `./gradlew :app:testDebugUnitTest` passes for new tests
- [x] 9.2 Run `./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride=/root/projects/Termfold/.tools/aapt2` and verify both debug APK outputs build

## Workflow follow-up

- Archive with `openspec archive` after implementation review and sync main spec `agent-preview-browser`.
- Resolve open questions in `design.md` (LAN allowlist defaults) before shipping Settings copy.
