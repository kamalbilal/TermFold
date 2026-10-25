# TermFold automation memory

Use with **termfold-browser** during Automation sessions. Never use Cursor built-in web search or WebFetch — open pages in the in-app browser instead.

## Tools (MCP `termfold-automation-memory`)

- `session_memory_search` — recall prior steps in this session
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
