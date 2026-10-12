# Playbook: upgrade from the template

Trigger: the user asks to pull agent-infrastructure updates from the upstream template.

1. Once: `git remote add template https://github.com/qbitoks/agent_template.git`. Then `git fetch template`.
2. Compare the `agent_template version` line in `scripts/agent-lint.sh` with
   `git show template/main:scripts/agent-lint.sh`. Equal → stop and tell the user.
3. Review `git diff HEAD template/main -- .agents/playbooks .agents/templates scripts .githooks`.
4. Template-owned paths → take upstream, skipping any the project deleted on purpose:
   `.agents/playbooks/`, `.agents/templates/`, `.agents/rules/writing-agent-docs.md`, `scripts/`,
   `.githooks/`, adapters (`CLAUDE.md`, `GEMINI.md`, `.aider.conf.yml`, `.github/copilot-instructions.md`).
   Use `git checkout template/main -- <paths>`; delete files upstream removed from those dirs
   (compare with `git ls-tree -r --name-only template/main`).
5. Root `AGENTS.md`: take upstream above the `PROJECT-OWNED BELOW` marker, keep ours below it.
6. `.agents/ignore.md`, `.gitignore`, `.gitattributes`, `.editorconfig`: merge new upstream lines by hand.
7. Never overwrite project-owned files: STATE, MAP, DECISIONS, module docs, other rules, README.
8. Run `sh scripts/agent-lint.sh --fix`; fix errors. Add a DECISIONS line: upgraded to template <version>.
   Offer the commit `docs(agents): upgrade template to <version>`.
