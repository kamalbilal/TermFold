# TermFold automation memory

Use with **termfold-browser** during Automation sessions. Never use Cursor built-in web search or WebFetch — open pages in the in-app browser instead.

## Memory workflow (mandatory habit)

1. **Read before you guess** — On a new task or when context is thin:
   - `session_memory_search` with a query from the user's goal.
   - If you know the site hostname, also `nav_memory_search` with that `site_key` and a UI/flow query.
2. **Write what you learned** — After a useful discovery or decision:
   - **This session only** → `session_memory_write` (kinds: goal, finding, decision, error, note, …).
   - **Reusable clicks/menus on a site** → `nav_memory_write` with `site_key` = hostname (navigation only).
3. **Secrets** → `session_secret_put` / `session_secret_get` only. Never put passwords in memory chunks or global nav.

TermFold also prepends a short memory summary each turn; still run search when the task is new or specific.

## Tools (MCP `termfold-automation-memory`)

- `session_memory_search` / `session_memory_write` / `session_memory_get` — private to this automation session
- `session_log_recent` — recent browser events
- `nav_memory_search` / `nav_memory_write` — global navigation hints (never store passwords globally)
- `session_secret_put` / `session_secret_get` — session-only credentials

Linked coding projects are **read-only**; keep writes under the automation session tree.

## `/spec` (composer)

When the user sends `/spec …` with their request, rewrite these session files in full:

- `docs/requirements.md` — high-level requirements
- `docs/prd.md` — PRD (stories, flows, acceptance criteria)
- `docs/constraints.md` — constraints and must-nots; optional `<!-- termfold-guardrails ... -->` block (enforced on tool permissions)

### Enforced guardrails (`termfold-guardrails` in constraints)

TermFold denies Cursor tool permissions (including auto-allow) when they break:

- `legal_sources_only: true` — blocks torrent/piracy/leak URL patterns in browser tools
- `allow_hosts` — only listed hosts for navigation URLs
- `deny_url_contains` — extra URL substring denials
- `protect_legal_policy: true` — agent cannot edit `docs/constraints.md` to turn off `legal_sources_only`

Chat instructions cannot override these blocks.
