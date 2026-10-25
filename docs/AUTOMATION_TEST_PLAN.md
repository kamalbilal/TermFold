# Automation manual test plan

1. Home → **Automation** → create project and session.
2. Optional: link a coding project; confirm link label and **Unlink**.
3. Open session A, browse to a site and sign in; open session B for same origin — not signed in.
4. In session A, use agent browser tools; **Run log** tab shows events.
5. Try saving a file under the linked project in the editor — blocked with read-only message.
6. Backup `.termfold` and restore; automation project and sessions still listed.
7. Open **Files** → `docs/constraints.md` — confirm `<!-- termfold-guardrails` with `legal_sources_only: true` (legacy sessions get this appended on open).
8. In the agent composer, try a browser tool to a blocked URL (e.g. a torrent site name in `browser_open`) — permission is denied with a guardrail message (even with auto-allow on).
9. `/spec …` then confirm all three docs updated and the guardrails block remains in `constraints.md`.
