# MAP

One line per area: `<area>/` — purpose — `<area>/AGENTS.md` (if it has one). Paths are repo-root-relative.
Read this, then open only what your task needs. Too big → split into `<area>/MAP.md` sub-maps listed here.
Agent infrastructure (`.agents/`, `scripts/agent-lint.sh`, tool adapters) is described in root `AGENTS.md`.

## Areas
- `app/` — Application, Hilt, MainActivity, nav host with tabs Главная/Библиотека/Каталог — `app/AGENTS.md`
- `core/model/` — JVM book model and codecs — `core/model/AGENTS.md`
- `core/layout/` — Android pagination, text geometry and Canvas rendering — `core/layout/AGENTS.md`
- `core/format/` — JVM parsers: FB2, EPUB, MOBI, DOCX, RTF, HTML, TXT (PDF is rendered in the reader) — `core/format/AGENTS.md`
- `core/opds/` — JVM OPDS 1/2 parsers, HTTPS-only requests, safe XML; JUnit tests — `core/opds/AGENTS.md`
- `core/data/` — Room, files, settings, library/catalog repositories, catalog list and downloads — `core/data/AGENTS.md`
- `core/designsystem/` — Apple Books theme, fonts, glass, covers, components — `core/designsystem/AGENTS.md`
- `core/speech/` — read aloud: sentence phrases, system TTS engine/voice choice, controller, media service — `core/speech/AGENTS.md`
- github.com/qbitoks/folio-modules (separate repo) — engine apps, module index and catalog list; described in its own AGENTS.md
- `feature/library/` — «Библиотека» and «Главная» screens and ViewModels — `feature/library/AGENTS.md`
- `feature/reader/` — reader: page turning, scroll mode, header/footer, selection — `feature/reader/AGENTS.md`
- `feature/catalog/` — «Каталог»: catalog switcher and «Каталоги» list, OPDS home, search, book sheet, downloads — `feature/catalog/AGENTS.md`
- `baselineprofile/` — baseline profile generator (not generated; it uninstalls the app)
- `scripts/` — PowerShell doctor, build, emulator launch check, release packaging — `scripts/AGENTS.md`
- `fixtures/` — sample OPDS feeds for `core/opds` parser tests
- `.github/workflows/` — CI: `android.yml` (build, emulator, release), `agent-lint.yml`
- `.agents/design/` — design index with focused UI topics; follow the relevant module's links
- `.agents/decisions/` — long-form decisions (former ADRs), indexed in `.agents/DECISIONS.md`
- `.agents/archive/` — dated reports, old plans/specs, the 2026-10-12 review. History only: an old PASS
  proves nothing now; open by path when a task refers to it.

## Rules index
<!-- `path` — trigger: when an agent must load it -->
- `.agents/rules/writing-agent-docs.md` — writing or editing any agent doc
- `.agents/rules/storage.md` — changing import, Room schema, files layout, settings keys, positions, catalog prefs
- `.agents/rules/release.md` — changing CI, signing, version, or cutting a release
