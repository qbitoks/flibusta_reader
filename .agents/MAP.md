# MAP

One line per area: `<area>/` — purpose — `<area>/AGENTS.md` (if it has one). Paths are repo-root-relative.
Read this, then open only what your task needs. Too big → split into `<area>/MAP.md` sub-maps listed here.
Agent infrastructure (`.agents/`, `scripts/agent-lint.sh`, tool adapters) is described in root `AGENTS.md`.

## Areas
- `app/` — Application, Hilt, MainActivity, nav host with tabs Главная/Библиотека/Каталог — `app/AGENTS.md`
- `core/model`, `core/layout` — JVM book model, pagination/text layout
- `core/format/` — JVM FB2/EPUB parsers — `core/format/AGENTS.md`
- `core/opds/` — JVM OPDS parser, HTTPS-only requests, safe XML; JUnit tests — `core/opds/AGENTS.md`
- `core/data/` — Room, files, settings, LibraryRepository, CatalogRepository, DownloadManager (catalog downloads)
- `core/designsystem/` — Apple Books–style theme, fonts, glass, covers, components
- `feature/library` — «Библиотека» and «Главная» screens and ViewModels
- `feature/reader/` — reader: page turning, scroll mode, header/footer, selection — `feature/reader/AGENTS.md`
- `feature/catalog/` — «Каталог»: OPDS home, search, book sheet, downloads — `feature/catalog/AGENTS.md`
- `baselineprofile/` — baseline profile generator (not generated; it uninstalls the app)
- `scripts/` — PowerShell doctor, build, emulator regressions, release packaging — `scripts/AGENTS.md`
- `fixtures/` — sample OPDS feeds for `core/opds` parser tests
- `.github/workflows/` — CI: `android.yml` (build, emulator, release), `agent-lint.yml`
- `.agents/design/` — Apple Books design guideline and the prototype roadmap (open by path for UI work)
- `.agents/decisions/` — long-form decisions (former ADRs), indexed in `.agents/DECISIONS.md`
- `.agents/archive/` — dated reports, old plans/specs, the 2026-10-12 review. History only: an old PASS
  proves nothing now; open by path when a task refers to it.

## Rules index
<!-- `path` — trigger: when an agent must load it -->
- `.agents/rules/writing-agent-docs.md` — writing or editing any agent doc
- `.agents/rules/storage.md` — changing import, Room schema, files layout, settings keys, positions, catalog prefs
- `.agents/rules/release.md` — changing CI, signing, version, or cutting a release
