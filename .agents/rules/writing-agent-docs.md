# Rule: writing agent docs

Trigger: writing or editing AGENTS.md, STATE, MAP, DECISIONS, a playbook or a rule.

- English, imperative, bullets. No intros, motivation paragraphs or emojis.
- Every line must change agent behaviour. If deleting it changes nothing → delete it.
- Concrete: exact paths, commands, names. `Run ./gradlew :app:test`, not "run tests".
- Paths are repo-root-relative, in backticks; placeholders use `<angle>` brackets.
  Lint fails when a referenced .md or .sh file doesn't exist.
- Point to a canonical example file instead of describing a pattern in prose.
- A prohibition comes with its alternative: "don't X, use Y".
- Plain wording. Keep "never" and capitals for real hard rules; overuse makes models overreact.
- Give a one-line why for non-obvious rules; it lets agents judge edge cases.
- Don't document what code or config already says (versions, dependency lists, file listings).
- One fact, one place; link instead of copying. Deepest scope that covers it: module > rules/ > root.
- Stable anchors: paths and symbol names, not line numbers.
- New rule file → one line in `.agents/MAP.md` → Rules index, with its trigger.
- Tool adapters (`CLAUDE.md`, `GEMINI.md`, `.github/copilot-instructions.md`, `.aider.conf.yml`) only
  point to `AGENTS.md`. Tools that "generate instructions" write there: move such content to AGENTS.md.

## Limits (enforced by `scripts/agent-lint.sh`; change both together)

| File | Max lines |
|---|---|
| root `AGENTS.md` | 120 |
| `.agents/STATE.md` | 40 |
| `.agents/MAP.md` and each sub-map | 100 |
| module `AGENTS.md` | 60 |
| playbook, rule | 80 |
| `.agents/DECISIONS.md` | 150 |
| tool adapter | 5 |
| any line of an agent doc | 200 bytes |

Over a limit → split (`.agents/playbooks/maintenance.md`), don't squeeze.
