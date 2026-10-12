# MAP

One line per area: `<area>/` — purpose — `<area>/AGENTS.md` (if it has one). Paths are repo-root-relative.
Read this, then open only what your task needs. Too big → split into `<area>/MAP.md` sub-maps listed here.
Agent infrastructure (`.agents/`, `scripts/agent-lint.sh`, tool adapters) is described in root `AGENTS.md`.
Full Kotlin file paths per feature: `docs/architecture.md`.

## Areas
- `core/` — JVM-only logic: XML/OPDS, HTTPS, FB2→EPUB, atomic index, reader settings and math; tests in `core/src/test/`
- `app/` — Android UI, lifecycle, network/import, files, Readium integration; `app/src/debug/` vs `app/src/release/` checkpoint
- `scripts/` — PowerShell build, doctor, emulator regressions, release packaging
- `docs/` — human reference docs (index `docs/README.md`), ADRs in `docs/adr/`, dated history
- `fixtures/` — sample OPDS catalogs
- `.github/workflows/` — CI: `android.yml` (build, emulator, release), `agent-lint.yml`

## Rules index
<!-- `path` — trigger: when an agent must load it -->
- `.agents/rules/writing-agent-docs.md` — writing or editing any agent doc
- `.agents/rules/reader-invariants.md` — changing reader UI/lifecycle, catalog/HTTPS, book storage or checkpoints
