# Rule: book storage and saved data

Trigger: changing import, Room entities/DAOs, `BookFiles`, settings DataStore keys, `Position` encoding,
catalog prefs.
Why: users upgrade with `install -r`; books, positions, highlights and collections must survive.

## Layout (app-private, see `core/data/.../library/BookFiles.kt`)
- `files/books/<sha1>.<ext>` — original file; book id = SHA-1 of its bytes. `files/covers/<id>.jpg`.
- `cache/books/<id>/book.bin` + `res/` — parsed model; rebuilt from the original if the cache is cleared.
- Room `flibusta.db` (`FlibustaDatabase`): books, collections, bookmarks, highlights, sessions, search history.
  Schemas in `core/data/schemas/`; bump the version with a migration (auto-migration where possible).
- `shared_prefs/catalog.xml`: `catalog`, `downloads` HTTPS origins.

## Changing any of this
- Never drop a table/column or rename a key without a migration; never reset data.
- `Position.encode()` format is persisted: keep old strings decodable.
- Changing applicationId or APK signature needs a separate decision.
