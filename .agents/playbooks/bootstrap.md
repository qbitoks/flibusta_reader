# Playbook: bootstrap

Trigger: the user starts or builds a project while `.agents/STATE.md` has `status: uninitialized`,
or adopts this template in an existing repo.

1. **Understand.** Existing code: read only manifests and the top-level layout (`ls`, `package.json`,
   `build.gradle*`, `pom.xml`, `pyproject.toml`, `go.mod`, `Cargo.toml`, `Podfile`, `.obsidian/`…).
   New project: ask in ONE message only what's unknown: what and for whom; stack (or propose one);
   hard constraints (platforms, versions, hosting, language of notes).
2. **Existing repo:** move rules from old agent files (`.cursorrules`, `.windsurfrules`, an old CLAUDE.md,
   copilot instructions, Aider conventions) into `AGENTS.md` or `.agents/rules/`; keep adapters as pointers.
   Don't document every module now: list top-level areas in MAP; module docs are written lazily
   (root `AGENTS.md` §5).
3. **Layout:** the stack's conventional one; don't invent. Examples:
   - Android/iOS/Spring/Node: standard scaffold; `AGENTS.md` per top-level module that has its own rules.
   - Monorepo: `apps/*`, `packages/*`, each with its own `AGENTS.md`.
   - Obsidian: vault in a subfolder (e.g. `vault/`) so agent files stay outside Obsidian; folders by
     domain (`00-inbox/`, `10-areas/`, `20-projects/`, `90-archive/`). Note conventions (frontmatter,
     links, tags, naming) go to a rule in `.agents/rules/`, not to `AGENTS.md` inside note folders:
     Obsidian would show those as notes.
4. **Scaffold** with the official generator (`gradle init`, `npm create …`, Spring Initializr, `flutter create`).
   Generate into a temp dir and merge: generators refuse non-empty dirs or overwrite `README.md`,
   `.gitignore`, `.editorconfig`. Merge their `.gitignore`/`.gitattributes`/`.editorconfig` with ours.
5. **Fill agent docs:**
   - Root `AGENTS.md` → Project: what, stack, exact commands. Make Check one command that also runs
     agent-lint where the stack allows (`make check`, `npm run check`); otherwise list both.
   - `.agents/STATE.md`: focus, next steps; set `status: active`.
   - `.agents/MAP.md`: one line per area. Module `AGENTS.md` from `.agents/templates/module-AGENTS.md`
     only for areas with their own rules.
   - `.agents/ignore.md`: add stack paths, drop patterns that hit real sources.
   - Stack-wide conventions (> 5 lines) → `.agents/rules/<topic>.md`, indexed in MAP.
6. **Clean up:** replace `README.md` with the project's README. Delete what doesn't apply: Obsidian lines
   in a Spring project, adapters for tools nobody uses, `.github/` when not on GitHub (run lint in your CI).
7. **Enable checks:** `git config core.hooksPath .githooks` (pre-commit runs agent-lint; with Husky or
   lefthook call `sh scripts/agent-lint.sh` from there). Run `sh scripts/agent-lint.sh --fix`; fix errors.
8. **Record** the stack and layout choice in `.agents/DECISIONS.md`. Offer the commit `chore: bootstrap <project>`.
