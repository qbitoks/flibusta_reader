# feature/catalog

«Каталог» tab: OPDS home, folders, search, book sheet, downloads list, sources. Network, cache and
downloads live in `core/data/.../catalog/` (`CatalogRepository`, `DownloadManager`, `NetworkErrors`).

## Entry points
- `feature/catalog/src/main/kotlin/net/flibustareader/feature/catalog/CatalogScreen.kt` — home (bands like «Главная»),
  search mode and results, pushed folder screen
- `feature/catalog/src/main/kotlin/net/flibustareader/feature/catalog/CatalogViewModel.kt` — `Feed` stack, `SearchResults`,
  author ranking, saved state
- `feature/catalog/src/main/kotlin/net/flibustareader/feature/catalog/CatalogParts.kt` — rows, remote covers, error/empty
  blocks, `feedItems` (endless scroll)
- `BookSheet.kt`, `DownloadsSheet.kt`, `SourcesSheet.kt` — the three sheets
- `core/data/src/main/kotlin/net/flibustareader/core/data/catalog/DownloadManager.kt` — Room row + unique
  WorkManager work per download; Range resume, retries, import

## Conventions
- Layout follows `.agents/design/apple-books-guidelines.md`: `LargeTitleHeader`, `SectionBand`, `SectionTitle`
  from `core/designsystem` (shared with «Главная»); plain rows with 32 dp inset; sheets via `FlibustaSheet`.
- Errors travel as `NetworkErrors` codes and become text in `errorText()`; no raw exception text for known cases.
- Books the app can't open (PDF-only) are hidden from lists with a count note, never shown as downloadable.
- Downloads open nothing by themselves: the book sheet and «Загрузки» show «Читать» when done.

## Gotchas
- `Feed` objects hold Compose state and their `LazyListState`; keep them in the ViewModel, not in composables.
- Cancelling a download disconnects its socket; that error must stay a stop (`stopped()`), not a failure.
