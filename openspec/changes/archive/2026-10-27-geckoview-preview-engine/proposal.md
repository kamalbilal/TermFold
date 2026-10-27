# Proposal

## Why

The in-app preview uses Android System WebView, which often mis-renders modern SPAs (React state, layout) while still running JavaScript. Agents already drive the browser through `termfold-browser` / MCP with token-cheap snapshots; that contract must stay, but the engine must be reliable for long-running automations on localhost without requiring the user to keep the preview open or Debian-side browsers.

## What Changes

- Replace the primary preview **rendering engine** with **GeckoView** (Mozilla) for `http`/`https` pages, including localhost dev servers, while keeping the **same MCP tool surface** (`termfold-browser` only—no Playwright MCP).
- Introduce a **browser session registry** with isolated storage profiles for **automation sessions (A)** and **coding sessions while an agent is actively using the browser (B)**. Profiles stay loaded across preview hide, tab switches, and app background during agent work—not destroyed on navigation away.
- **Casual preview** (user browsing without an agent-bound session) remains **out of scope for persistence** and may be disposed when closed.
- Enable **headless agent driving**: browser commands SHALL work when the preview UI is minimized or the app is in the background, tied to existing agent foreground / wake-lock policy—not blocked by “open TermFold on screen.”
- **Power tiers** (`RUNNING`, `IDLE_WARM`, optional `IDLE_COLD` under memory pressure) to balance **low battery and RAM** with alive sessions.
- Preserve and document **token efficiency**: capped snapshots, `--changed` / `--find`, `outline`, `console --errors`, `network --failed`, `screenshot --if-changed`; action results stay short; no second browser MCP.
- **Reliability**: per-command timeouts, cancellation, single-flight per session, extension bridge timeouts—MCP must not hang indefinitely.
- **Phase 1 hybrid**: `files.termfold` static project HTML may continue on WebView until unified HTTP serving or Gecko file handling ships.
- Update bundled **termfold-browser** skill with long-automation and token playbook.

**Out of scope**: Play Store “MCP browser” apps, guest Linux Playwright/Chromium, automating external Chrome via Appium, automated CAPTCHA bypass, persisting casual preview tabs indefinitely.

## Capabilities

### New Capabilities

- `gecko-preview-engine`: GeckoView runtime, session registry, headless/background agent browser, power tiers, and engine abstraction behind existing browser automation.

### Modified Capabilities

- `agent-preview-browser`: Storage isolation, session lifetime, background command routing, and engine-agnostic requirements (supersedes WebView-only assumptions in prior deltas when archived).
- `bundled-linux-guest`: Skill text for token-cheap long automations, background browser expectations, and when to use `wait` vs snapshot.

## Impact

- `PreviewPane.kt`, `PreviewTabHost.kt`, `BrowserDriver.kt`, `BrowserBridge.kt`, `BrowserPageScript.kt` / built-in WebExtension assets
- New: `GeckoSession` controllers, Mozilla Maven (`org.mozilla.geckoview`), session registry
- `app/build.gradle.kts` (APK size increase for Gecko natives)
- `termfold-browser.js`, `termfold-browser-skill.md`
- Automation session binding (`BrowserBridge.automationSessionKey`, `browser.json`)
- Unit tests: registry policy, URL routing (http vs file host), timeout helpers where testable without Gecko on JVM
- **BREAKING** (behavioral): Browser automation no longer requires preview on screen; automation design note “release WebView when not visible” is retired for A/B profiles
