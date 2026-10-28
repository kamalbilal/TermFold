# TermFold agent memory (all agent sessions)

TermFold attaches **session-private** and **global navigation** memory in coding chats, automation, and PR review. PM sessions use **termfold-pm-memory** instead.

## Read before you guess

1. `session_memory_search` — query from the user's goal.
2. On a known site: `nav_memory_search` with `site_key` = hostname.

## Write what you learned

- **This session** → `session_memory_write` (goals, findings, decisions, errors).
- **Reusable site UI** → `nav_memory_write` (never passwords).
- **Secrets** → `session_secret_put` / `session_secret_get` only.

Each turn may include a short memory preview; still search when the task is new or specific.
