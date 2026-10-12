# MAP

One line per area: `<area>/` — purpose — `<area>/AGENTS.md` (if it has one). Paths are repo-root-relative.
Read this, then open only what your task needs. Too big → split into `<area>/MAP.md` sub-maps listed here.
Agent infrastructure (`.agents/`, `scripts/agent-lint.sh`, tool adapters) is described in root `AGENTS.md`.

## Areas
- `core/` — JVM-only logic: OPDS, HTTPS, XML, FB2→EPUB, atomic index, reader math; JUnit tests — `core/AGENTS.md`
- `app/` — Android UI, lifecycle, import, files, Readium; debug/release checkpoint — `app/AGENTS.md`
- `scripts/` — PowerShell doctor, build, emulator regressions, release packaging — `scripts/AGENTS.md`
- `fixtures/` — sample OPDS feeds for `core/` parser tests
- `third-party/` — bundled dependency licenses
- `.github/workflows/` — CI: `android.yml` (build, emulator, release), `agent-lint.yml`
- `.agents/decisions/` — long-form decisions (former ADRs), indexed in `.agents/DECISIONS.md`
- `.agents/archive/` — dated reports, old plans/specs, the 2026-10-12 review. History only: an old PASS
  proves nothing now; open by path when a task refers to it.

## Rules index
<!-- `path` — trigger: when an agent must load it -->
- `.agents/rules/writing-agent-docs.md` — writing or editing any agent doc
- `.agents/rules/reader-invariants.md` — changing reader UI/lifecycle, catalog/HTTPS or debug/release checkpoint
- `.agents/rules/storage.md` — changing import, library index, cleanup, prefs keys, Locators, catalog state
- `.agents/rules/release.md` — changing CI, signing, version, or cutting a release
