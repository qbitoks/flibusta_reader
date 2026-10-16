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
- Book sheet texts stay catalog-neutral (no «сервер готовит EPUB», no warnings from past refusals); a real failure shows
  its error under the capsule.
- Download formats come ordered from `OpdsParser` (EPUB, FB2, conversions, PDF last); `CatalogViewModel.formatsOf` puts
  the open catalog's `Catalog.format` (EPUB/FB2, Flibusta FB2) first. `bookFormats` (`BookFormats.kt`, tested) splits
  them: the capsule takes the last picked format (newest download `createdAt`, which `enqueue` renews on every pick;
  a done copy deleted from the library doesn't count), else the catalog's; it goes progress → «Читать» («Читать · TXT»
  when not the catalog's format or several copies are in). ••• is always there when it has something: the other
  EPUB/FB2, conversions under «Другие форматы» (only when the book has EPUB/FB2), «Отменить загрузку» for every
  pending download. Another format lands as a separate library book (book id = file hash). Flibusta really offers
  six formats; all but FB2 are converted from it on the fly.
- Books the app can't open (DJVU-only) are hidden from lists with a count note, never shown as downloadable.
- Show only what the catalog declares (`CatalogPage.search/newBooks/popular/facets`); a `Catalog` field overrides it
  (Flibusta: its `/opds/new` is a menu and authors are searched separately). «Все/Книги/Авторы» only with `searchAuthors`.
- A book with no downloads but a `web` page (Open Library loans) is listed and offers «Открыть на сайте».
- Refusals (`NetworkErrors.isUnavailable`: sign-in, another site, 4xx, HTML page) are final: the capsule's download
  tries the next EPUB/FB2 (a format picked in the menu does not), then the sheet offers «Открыть на сайте»;
  `CatalogRepository.downloadIssues` marks the catalog until a success.
- «Каталоги»: tap opens any catalog (no add step), ★ toggles favorite, long press edits. Favorites feed the switcher
  menu under the title; presets are never deleted, only unstarred or reset; only own catalogs are removed.
- Downloads open nothing by themselves: the book sheet and «Загрузки» show «Читать» when done.
- Sign-in: «Логин»/«Пароль» in `EditCatalog` (`vm.saveCatalog(catalog, login, password)`; empty password keeps the
  saved one, blank login signs out). A `LOGIN` error offers «Войти» via `LocalSignIn`, which opens that sheet.
  Never prefill or show the saved password. Agents don't type real passwords; the user tests sign-in on the phone.

## Gotchas
- `Feed` objects hold Compose state and their `LazyListState`; keep them in the ViewModel, not in composables.
- Cancelling a download disconnects its socket; that error must stay a stop (`stopped()`), not a failure.
- Librusec book links return an HTML page, iKnigi redirects to a LitRes trial (403 from a foreign IP/VPN,
  works from a Russian one; an empty `hub_id` gives an HTML page), EKNIGA sections
  answer a MySQL error page; PROFILIB declares search on dead host profilib.me (checked 2026-10-16).
