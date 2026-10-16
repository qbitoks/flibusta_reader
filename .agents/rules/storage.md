# Rule: book storage and saved data

Trigger: changing import, Room entities/DAOs, `BookFiles`, settings DataStore keys, `Position` encoding,
catalog prefs.
Why: users upgrade with `install -r`; books, positions, highlights and collections must survive.

## Layout (app-private, see `core/data/.../library/BookFiles.kt`)
- `files/books/<sha1>.<ext>` — original file (PDF: `.pdf`, no `cache/books` entry; position = `Position(page)`); book id = SHA-1 of its bytes. `files/covers/<id>.jpg`.
- `cache/books/<id>/book.bin` + `res/` — parsed model; rebuilt from the original if the cache is cleared.
- Room `folio.db` (`FolioDatabase`, v2): books, collections, bookmarks, highlights, sessions, search
  history, `downloads`. Schemas in `core/data/schemas/`; v1 upgrades are unsupported (user decision).
  Keep the current v2 schema; do not add destructive fallback or restore removed migrations.
- `files/downloads/<sha1 of link>.part` — catalog download in progress; resumed with HTTP Range, deleted
  after import. `cache/catalog-covers/<sha1 of url>` — OPDS cover thumbnails (safe to clear).
- DataStore `settings`: reader/library prefs; `app_appearance` (app-wide), `book_appearance_<book id>`
  (absent = follow the app; removed on delete). Old `appearance` key is no longer read (no migration).
- Shared reader settings: `ReaderPreferences` (existing keys plus `reader_appearance`, `header_layout`,
  `footer_layout`, `guide_window` = `spread;extra;falloff`, junk → single line); visual presets stay in `ChromeStylePreferences` keys;
  `footer_under` and `header_under` (None/Clip/Fade) = what scrolling text does
  under each line independently; resolve None to Fade in Scroll without rewriting saved values.
- Decode only current twelve-field line layouts; obsolete nine-field layouts fall back to factory defaults.
- A book's own settings: `book_reader_<book id>`, `name=value` lines, only fields that differ from shared
  (`BookOverrides`); removed on reset/delete. Ignore obsolete separate book appearance/style keys and unknown fields.
- Store shared typography in the current nine-field `reading_text` codec; reject old text formats to factory defaults.
  Store margins as 0–15 percent of viewport width on each side; do not migrate old dp margins or linked text presets.
- Store book warmth/contrast as separate `warmth`/`contrast` fields and inherit untouched controls.
- Ignore obsolete footer-style, page-turn aliases and book settings-version markers; do not rewrite unopened books on shared edits.
- `shared_prefs/catalog.xml`: `catalogs` — JSON array of `{id,title,address,downloads?,searchBooks?,searchAuthors?,newBooks?}`
  (`CatalogCodec`), `current` — selected catalog id; `recent` — catalog searches, newline-joined;
  `download_issues` — `host<TAB>code` lines of catalogs whose last download was refused. The old `catalog`/`downloads`
  origins migrate once into a Flibusta-style catalog and are removed; with neither key the starter set is seeded.

## Changing any of this
- Preserve books and database contents; unsupported old schemas must fail instead of silently resetting data.
- `Position.encode()` format is persisted: keep old strings decodable.
- Changing applicationId or APK signature needs a separate decision.
