# feature/catalog

«Каталог» tab: several OPDS catalogs (switcher, «Каталоги» sheet), home, folders, search, book sheet, downloads list. Network, cache and
downloads live in `core/data/.../catalog/` (`CatalogRepository`, `DownloadManager`, `NetworkErrors`).

## Entry points
- `feature/catalog/src/main/kotlin/app/folio/feature/catalog/CatalogScreen.kt` — home (bands like «Главная»),
  search mode and results, pushed folder screen
- `feature/catalog/src/main/kotlin/app/folio/feature/catalog/CatalogViewModel.kt` — `Feed` stack, `SearchResults`,
  author ranking, saved state
- `feature/catalog/src/main/kotlin/app/folio/feature/catalog/CatalogParts.kt` — rows, remote covers, error/empty
  blocks, `feedItems` (endless scroll)
- `BookSheet.kt`, `DownloadsSheet.kt`, `CatalogsSheet.kt` (one list: favorites, presets by language, «Мои»; add by address / edit) — the sheets
- `core/data/src/main/kotlin/app/folio/core/data/catalog/Catalogs.kt` — `Catalog`, `CatalogPresets`, `CatalogDirectory`
  (saved rows merged with presets, star/reset rules), prefs codec
- `core/data/src/main/kotlin/app/folio/core/data/catalog/DownloadManager.kt` — Room row + unique
  WorkManager work per download; Range resume, retries, import

## Conventions
- Layout follows `.agents/design/design-system.md`: `LargeTitleHeader`, `SectionBand`, `SectionTitle`
  from `core/designsystem` (shared with «Главная»); plain rows with 32 dp inset; sheets via `FolioSheet`.
- Errors travel as `NetworkErrors` codes and become text in `errorText()`; no raw exception text for known cases.
- Download formats come ordered from `OpdsParser`; the sheet shows FB2/EPUB and folds conversions into «Другие форматы».
- PDF downloads last and sits in «Другие форматы». Books the app can't open (DJVU-only) are hidden from lists with a count note, never shown as downloadable.
- Show only what the catalog declares (`CatalogPage.search/newBooks/popular/facets`); a `Catalog` field overrides it
  (Flibusta: its `/opds/new` is a menu and authors are searched separately). «Все/Книги/Авторы» only with `searchAuthors`.
- A book with no downloads but a `web` page (Open Library loans) is listed and offers «Открыть на сайте».
- Refusals (`NetworkErrors.isUnavailable`: sign-in, another site, 4xx, HTML page) are final: the VM tries the next
  FB2/EPUB, then the sheet offers «Открыть на сайте»; `CatalogRepository.downloadIssues` marks the catalog until a success.
- «Каталоги»: tap opens any catalog (no add step), ★ toggles favorite, long press edits. Favorites feed the switcher
  menu under the title; presets are never deleted, only unstarred or reset; only own catalogs are removed.
- Downloads open nothing by themselves: the book sheet and «Загрузки» show «Читать» when done.

## Gotchas
- `Feed` objects hold Compose state and their `LazyListState`; keep them in the ViewModel, not in composables.
- Cancelling a download disconnects its socket; that error must stay a stop (`stopped()`), not a failure.
- Librusec book links return an HTML page, iKnigi redirects to a LitRes trial (403), EKNIGA sections
  answer a MySQL error page; PROFILIB declares search on dead host profilib.me (checked 2026-10-16).
