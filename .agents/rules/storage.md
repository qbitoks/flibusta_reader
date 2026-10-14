# Rule: book storage and saved data

Trigger: changing import, Room entities/DAOs, `BookFiles`, settings DataStore keys, `Position` encoding,
catalog prefs.
Why: users upgrade with `install -r`; books, positions, highlights and collections must survive.

## Layout (app-private, see `core/data/.../library/BookFiles.kt`)
- `files/books/<sha1>.<ext>` — original file; book id = SHA-1 of its bytes. `files/covers/<id>.jpg`.
- `cache/books/<id>/book.bin` + `res/` — parsed model; rebuilt from the original if the cache is cleared.
- Room `folio.db` (`FolioDatabase`, v2): books, collections, bookmarks, highlights, sessions, search
  history, `downloads` (v2, auto-migration from v1). Schemas in `core/data/schemas/`; bump the version with
  a migration (auto-migration where possible).
- `files/downloads/<sha1 of link>.part` — catalog download in progress; resumed with HTTP Range, deleted
  after import. `cache/catalog-covers/<sha1 of url>` — OPDS cover thumbnails (safe to clear).
- DataStore `settings`: reader/library prefs; `app_appearance` (app-wide), `book_appearance_<book id>`
  (absent = follow the app; removed on delete). Old `appearance` key is no longer read (no migration).
- Shared reader settings: `ReaderPreferences` (existing keys plus `reader_appearance`, `header_layout`,
  `footer_layout`); visual presets stay in `ChromeStylePreferences` keys;
  `footer_under` (None/Clip/Fade; removed Strip reads as Clip) and `header_under` (None/Clip/Fade) = what scrolling text does
  under the lines (default None); legacy `footer_style` and book `footer_scroll` carry over only Strip.
- A book's own settings: `book_reader_<book id>`, `name=value` lines, only fields that differ from shared
  (`BookOverrides`); removed on reset/delete. Old `book_appearance_<id>` and `book_*_chrome_style_<id>`
  are read as part of it and dropped on the next write of that book. Unknown fields are ignored.
- A missing `footer_layout` starts customised when the old footer content differs from the standard one.
- `shared_prefs/catalog.xml`: `catalog`, `downloads` HTTPS origins; `recent` — catalog searches, newline-joined.

## Changing any of this
- Never drop a table/column or rename a key without a migration; never reset data.
- `Position.encode()` format is persisted: keep old strings decodable.
- Changing applicationId or APK signature needs a separate decision.
