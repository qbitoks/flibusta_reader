# Rule: book storage and saved data

Trigger: changing import, library index, cleanup, delete, SharedPreferences keys, Locator handling or
catalog state saving (`BookRepository.kt`, `AtomicTextFile.kt`, `ReaderPreferences.kt`, `MainActivity.kt`).
Why: users upgrade with `install -r`; their books, positions and bookmarks must survive.

## Files (app-private)
- `files/books/library.json` — JSON array, no schema version. Entry:
  `{"id":"<uuid>","title":"…","author":"…","path":"/data/user/0/net.flibustareader/files/books/<uuid>.epub","format":"FB2"}`
- `id`, `title`, `path`, `format` required; `author` optional. `path` is absolute: don't make it relative
  without a migration. `format` is the source format; the stored file is always EPUB.
- `files/books/<uuid>.epub`, `<uuid>.part` (unfinished), `index-*.part` (unfinished index write).
- `cache/import*.part`, `cache/download*.part`, `cache/fb2*.fb2` — temp inputs.

## Write order
1. Copy/download input to a cache temp file with a size limit.
2. Copy EPUB or convert FB2 into `files/books/<uuid>.part`.
3. After the cancellation check, ATOMIC_MOVE to `<uuid>.epub` (no fallback for this move).
4. Add the entry to the index via AtomicTextFile; new book goes first.
5. On a regular exception delete the result; temp files are cleaned in `finally`.

## Recovery and delete
- Once per canonical dir per process, BookRepository deletes `.part` and `.epub` not in the index. A crash
  between publish and index leaves an unindexed book; the next start deletes it. Cache is not the library.
- Reading the library filters rows whose file is missing but doesn't rewrite the index.
- Corrupt JSON is not replaced with an empty library; there is no auto-repair. Don't claim one.
- Delete: index first, then the file and `position:`/`bookmark:`. `lastBook` isn't cleared; the main
  screen only offers it if the book exists.

## SharedPreferences `shared_prefs/reader.xml`
- `catalog`, `downloads`: HTTPS origins, default flibusta.is. `lastBook`: book id.
- `position:<id>`, `bookmark:<id>`: Readium Locator JSON (`toJSON`/`fromJSON`; keep extra fields for the
  text anchor). One bookmark per book; missing key = none. A bad Locator is read under runCatching.
- `readerColor` WHITE/SEPIA/GRAY/BLACK (SEPIA); `readerFont` BOOK/SANS/MONO (BOOK).
- Floats: `fontSize` 1.1 [0.7–2.5], `lineHeight` 1.5 [1.1–2.2], `pageMargins` 1.2 [0.3–2.5],
  `brightness` 0.65 [0.05–1.0]. Non-finite → default; unknown enum → fallback.
- Booleans: `scroll` false, `systemBrightness` true, `hudClock`/`hudProgress`/`hudBattery` true.
- `theme` DARK/LIGHT: legacy, read as fallback only, never written.
- No shared transaction between files and prefs; `apply` is not crash-proof.

## Catalog state
- ViewModel keeps books, tab, query, authors mode, feed and history across rotation.
- Bundle keys: `tab` Int, `query` String, `authors` Boolean, `address` String, `history` list. No full feeds.

## Changing any of this
- Renaming a key/field: define old-value migration, default and when it's written; never reset data.
- Changing paths, ids, cleanup or order: verify books, index, Locators and bookmarks on an existing
  test-emulator profile (`scripts/reader-regression.ps1`) plus `AtomicTextFileTest`.
- Changing applicationId or APK signature needs a separate decision about access to old data.
