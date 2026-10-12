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
2026-10-12 | repo | Adopted agent_template; Russian AGENTS.md contract moved into root AGENTS.md (English) + .agents/rules/reader-invariants.md | one entry point for all agents | docs/README.md
2026-10-12 | docs | Human docs stay in docs/ (Russian); ADRs stay in docs/adr/, DECISIONS links them | existing references and history kept
2026-10-12 | cursor | Removed .cursor/rules/project.mdc | Cursor reads AGENTS.md natively
2026-10-12 | git | No `* text=auto eol=lf` from the template | avoid renormalizing the whole repo; existing .gitattributes kept
