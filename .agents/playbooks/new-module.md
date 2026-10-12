# Playbook: new module / area

Trigger: adding a module, package, service, feature area, or a top-level vault section.

1. Check `.agents/MAP.md`: does an existing area already own this? Prefer extending it.
2. Create the area in the stack's conventional place.
3. Area with its own rules or more than ~5 files: create `<area>/AGENTS.md` from
   `.agents/templates/module-AGENTS.md`. Fill only what is true now; delete empty sections.
   Trivial area: the MAP line is enough. Obsidian vault: folder conventions go to `.agents/rules/`.
4. Add one line to `.agents/MAP.md`: `` `<area>/` — purpose — `<area>/AGENTS.md` ``.
5. Run `sh scripts/agent-lint.sh --fix`: it adds the `CLAUDE.md` stub next to the new `AGENTS.md`
   (Claude Code loads it when it works in that folder; other tools read `AGENTS.md` natively).
6. Cross-cutting convention → `.agents/rules/<topic>.md` instead of repeating it in several modules.
7. Non-obvious choice (library, boundary, data format) → one line in `.agents/DECISIONS.md`.

Nested modules: the parent `AGENTS.md` lists its children (repo-root-relative paths); MAP lists only
the parent. MAP stays small regardless of project size.
