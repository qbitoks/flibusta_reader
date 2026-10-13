# Data and repositories

## Entry points
- Use `core/data/src/main/kotlin/net/flibustareader/core/data/library/LibraryRepository.kt` as the canonical import/load/mutation example.
- Use `core/data/src/main/kotlin/net/flibustareader/core/data/library/BookFiles.kt` for original, parsed cache and image paths.
- Use `core/data/src/main/kotlin/net/flibustareader/core/data/db/FlibustaDatabase.kt` for Room schema registration and migrations.
- Use `core/data/src/main/kotlin/net/flibustareader/core/data/settings/SettingsRepository.kt` for persisted reader/library preferences.
- Use `core/data/src/main/kotlin/net/flibustareader/core/data/catalog/CatalogRepository.kt` for feeds, covers and catalog sources.
- Use `core/data/src/main/kotlin/net/flibustareader/core/data/catalog/DownloadManager.kt` for download rows, resume and workers.
- Use `core/data/src/main/kotlin/net/flibustareader/core/data/library/FolderScanner.kt` for SAF folder scans and scheduled imports.
- Bind repositories in `core/data/src/main/kotlin/net/flibustareader/core/data/di/DataModule.kt`; keep UI in feature modules.

## Invariants
- Load `.agents/rules/storage.md` before changing files, Room schemas, IDs, position strings or preference keys.
- Import through `LibraryRepository.import`; retain its mutex and byte-based deduplication for concurrent imports.
- Rebuild parsed caches through `LibraryRepository.load` from retained originals; caches are disposable.
- Model new persisted mutations after repository suspend functions and DAO flows, rather than screen-owned files or tables.
- Validate both catalog origins before saving either; route network requests through `core/opds` HTTPS helpers.
- Keep acquisition IDs distinct from imported book IDs; download rows use link hashes and retain the returned `bookId`.
- Preserve unique worker scheduling and partial files for pause/resume; removing a download keeps the imported book.
- Keep want-to-read/finished shelves as book flags; user collection membership belongs to Room cross-reference rows.
- Preserve SAF source URI tracking and depth limits when extending folder scans; use the existing scanner worker.
