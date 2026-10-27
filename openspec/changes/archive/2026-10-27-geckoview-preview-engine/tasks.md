# Tasks

## 1. Gecko dependency and spike

- [ ] 1.1 Add Mozilla Maven repo and `org.mozilla.geckoview:geckoview` to `app/build.gradle.kts`; verify `./gradlew :app:assembleDebug` succeeds with AAPT2 override
- [ ] 1.2 Create minimal `GeckoRuntime` + `GeckoSession` composable or activity spike that loads `http://127.0.0.1:5173` (or bundled test page); verify interactive paint on device (not blank WebView-class failure)
- [ ] 1.3 Document spike result and feature flag `previewEngine` default in code comments or internal README snippet in change folder if needed

## 2. Browser surface abstraction

- [ ] 2.1 Introduce `BrowserSessionController` / `BrowserSurface` interface mirroring load, eval RPC, input, screenshot, console/network hooks used by `BrowserDriver`
- [ ] 2.2 Refactor `BrowserDriver` to depend on `BrowserSurface` instead of `WebView` directly; verify existing WebView preview path still passes manual smoke on `files.termfold` URL
- [ ] 2.3 Add JVM unit tests for session key resolution, URL routing (http/https vs file host), and timeout helper behavior without Gecko on JVM

## 3. Built-in WebExtension bridge

- [ ] 3.1 Package `BrowserPageScript` / `__tf` helper as built-in WebExtension under `app/src/main/assets/gecko-termfold/`
- [ ] 3.2 Wire `WebExtensionController` native messaging to existing snapshot/click/fill operations; verify extension RPC timeout returns structured error
- [ ] 3.3 Implement `GeckoBrowserSurface` using the extension bridge; verify `browser_snapshot` MCP on localhost returns non-empty control list on device

## 4. Session registry (A + B)

- [ ] 4.1 Implement `BrowserSessionRegistry` with keys `auto-<sessionId>` and coding bind keys; isolated `GeckoSessionSettings` context per entry
- [ ] 4.2 Register on automation session create/destroy and coding agent browser bind/release; verify two automation sessions do not share cookies (login scenario)
- [ ] 4.3 Enforce single-flight per session key in `BrowserBridge`; verify concurrent MCP calls get `session_busy` fast failure

## 5. Headless `BrowserBridge` and preview attach

- [ ] 5.1 Route `BrowserBridge.execute` to registry without `Preview.open()` wait when session key resolves; verify snapshot succeeds while preview pane is not visible during active agent run
- [ ] 5.2 Change `PreviewPane` / tab host to attach/detach Gecko session for display without destroying registry entry on tab switch; verify return to preview preserves URL/state
- [ ] 5.3 Keep phase-one `files.termfold` on WebView via URL router; verify project HTML preview still loads

## 6. Power tiers and reliability

- [ ] 6.1 Implement `RUNNING` / `IDLE_WARM` state on registry entries; throttle painting when UI detached; verify no extra wake lock beyond existing agent policy between turns
- [ ] 6.2 Add warm session cap (default 3) with LRU eviction for non-agent casual tabs first; verify eviction returns documented re-warm behavior on next command
- [ ] 6.3 Align MCP command timeout, cancellation on agent turn cancel, and bridge timeout errors with existing 90s policy; verify hung page does not block MCP indefinitely

## 7. Skills and bundled guest docs

- [ ] 7.1 Update `termfold-browser-skill.md` / assets with token playbook and background browser expectations per delta spec; verify text appears in packaged assets
- [ ] 7.2 Extend automation skill or setup tips for snapshot `--changed` and wait-first SPA flow; verify `termfold-setup` success output mentions efficiency tips

## 8. Integration verification

- [ ] 8.1 On-device: Vite/React localhost route renders and agent can click through a multi-step flow with preview minimized
- [ ] 8.2 On-device: automation session A/B storage isolation and coding bind profile isolation spot-check
- [ ] 8.3 Run `./gradlew :app:testDebugUnitTest` and fix any failures from registry/routing tests

## Workflow follow-up

- Archive `termfold-browser-improvements` and `automation-home-tab` before or with this change if `agent-preview-browser` main spec is still missing, so deltas merge cleanly at archive time.
- Archive `geckoview-preview-engine` after review and sync specs.
