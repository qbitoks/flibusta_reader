# DECISIONS

Append-only, one line per decision, newest at the bottom:
`YYYY-MM-DD | scope | decision | why (≤ 1 line) | details link (optional)`
Read it (grep by scope or keyword) before changing architecture, a library, a data format or the layout.
Long rationale → `.agents/decisions/NNNN-title.md` (template: `.agents/templates/adr.md`).
Reversed decision → append the new line and delete the old one (git keeps history).
Over the limit → move entries that no longer constrain anything to `.agents/archive/decisions-YYYY.md`;
keep foundational ones (stack, layout) here however old. Parallel appends merge via `merge=union`.

2026-10-12 | repo | AGENTS.md is the single source of truth; tool files (CLAUDE.md, GEMINI.md, …) only point to it | one rule set for every agent, no drift
2026-10-12 | docs | Agent docs in English, chat in the user's language | Cyrillic costs ~1.5–3× tokens (tokenizer-dependent); English is unambiguous
2026-10-12 | docs | Root rules + MAP index + per-area AGENTS.md loaded on demand | always-loaded context stays constant as the project grows
2026-10-12 | docs | Stable facts and commands in root AGENTS.md → Project; STATE holds only volatile status | commands survive STATE rewrites, tools get them free
2026-10-12 | claude | CLAUDE.md stub (@AGENTS.md) next to every AGENTS.md, made by lint --fix | Claude Code skips native AGENTS.md when any CLAUDE.md exists
2026-10-12 | ignore | Tool ignore files generated from .agents/ignore.md; .cursorignore = secrets only | hand-kept mirrors drifted; hard blocks hid lockfiles
2026-10-12 | tooling | agent-lint runs from .githooks/pre-commit and CI | PowerShell has no sh on PATH, but git runs hooks with its own sh
2026-10-12 | process | finish-task inlined into root AGENTS.md §5 | needed on every task: saves a read and removes a duplicate
2026-10-12 | repo | Adopted agent_template; Russian AGENTS.md contract moved into root AGENTS.md (English) + .agents/rules (since archived) | one entry point for all agents
2026-10-12 | docs | docs/ dissolved: module AGENTS.md (core, app, scripts) + .agents/rules (storage, release) + STATE | one doc system, loaded on demand
2026-10-12 | docs | Dated reports, review, plans/specs moved to .agents/archive/ | history, not instructions; kept out of search
2026-10-12 | docs | README.md stays the Russian user-facing product description | humans read it; agents read AGENTS.md
2026-10-12 | cursor | Removed .cursor/rules/project.mdc | Cursor reads AGENTS.md natively
2026-10-12 | git | Template .gitattributes (`* text=auto eol=lf`) adopted as is | index was already all-LF; stops CRLF drift on Windows
2026-10-12 | repo | Prototype codebase replaces the Readium app; OPDS kept in core/opds + «Каталог» tab | user chose the prototype as base | .agents/decisions/0004-prototype-engine.md
2026-10-12 | catalog | Downloads: Room row + unique expedited WorkManager work + HTTP Range resume | survives drops and restarts without a foreground service
2026-10-12 | catalog | Download read timeout 150 s, feed 60 s, one quick feed retry | Flibusta converts EPUB on request (15–90 s silence) and often 502s the first search
2026-10-12 | catalog | Catalog home mirrors «Главная»: bands, «Новинки» cover shelf, sections list | user asked for one design
2026-10-12 | catalog | OPDS covers via own HTTPS loader into BookCover, not Coil network | HTTPS-only redirects kept, no OkHttp
