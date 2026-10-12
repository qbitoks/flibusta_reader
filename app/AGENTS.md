# app

Android layer: Compose UI, lifecycle, network/import, private files, Readium integration.
Pure logic belongs in `core/`. Reader/storage invariants: `.agents/rules/reader-invariants.md`.

## Entry points
- `app/src/main/kotlin/net/flibustareader/MainActivity.kt` — library, catalog, search, cards, system import, launching operations
- `app/src/main/kotlin/net/flibustareader/CatalogState.kt` — ViewModel: books, catalog results, UI state; runs `perform` jobs
- `app/src/main/kotlin/net/flibustareader/BookRepository.kt` — sources, HTTP, import/convert, library index, startup cleanup
- `app/src/main/kotlin/net/flibustareader/ReaderActivity.kt` — Readium navigator, lifecycle, geometry, Locator, typography anchor, HUD data
- `app/src/main/kotlin/net/flibustareader/ReaderChrome.kt` — Compose reader panels, indicators, palettes, settings sheet
- `app/src/main/kotlin/net/flibustareader/ReaderPreferences.kt` — SharedPreferences ↔ ReaderOptions, legacy `theme` fallback
- `app/src/main/kotlin/net/flibustareader/ReaderTheme.kt` — Compose theme of the main screen
- Checkpoint: `app/src/debug/kotlin/net/flibustareader/ReaderOpeningCheckpoint.kt` (file hold, 60 s
  timeout, markers) vs no-op `app/src/release/kotlin/net/flibustareader/ReaderOpeningCheckpoint.kt`

## Flows
- Catalog: MainActivity → BookRepository.page → HttpsRequest → OpdsParser → CatalogState → UI.
- Import: picker / download / demo → BookRepository.prepare → EPUB or Fb2Converter → `.part` → `.epub`
  → `library.json`. Details: `.agents/rules/storage.md`.
- Reading: ReaderActivity → AssetRetriever/PublicationOpener → EpubNavigatorFactory → EpubNavigatorFragment.

## Conventions
- Network/import/demo jobs: `perform` + `runInterruptible(Dispatchers.IO)` in `viewModelScope`, not the Activity.
- `download` resolves the OPDS link path+query against `downloadBase` on purpose (separate text server);
  don't turn it into downloading from any host named in the catalog. Download limit 50 MiB, to cache temp file.
- ZIP: take the first FB2 entry; never extract archive paths into app storage. EPUB check is only
  `META-INF/container.xml`, not full validation.
- `ReaderActivity.onCreate` uses `super.onCreate(null)`: the fragment factory depends on the publication.
  Don't switch to default fragment restore without making factory and publication available.
- Typography change: hold the first visible Locator, coalesce fast requests, wait for stable WebView
  geometry, restore. While the anchor is active, don't save intermediate Locators.
- Progress/seek weigh Readium positions per resource; it is not an exact page count of the chapter.
- Paged mode: edges turn pages, centre toggles panels. Scroll mode: short tap toggles; links/selection → Readium.
- Manifest: `allowBackup=false`; `app/src/main/res/xml/data_extraction_rules.xml` excludes data from backup.

## Commands
- Build+lint: `./scripts/build.ps1`; release compile (checkpoint changes): `./gradlew.bat :app:compileReleaseKotlin --console=plain`
- Lint report: `app/build/reports/lint-results-debug.html`

## Gotchas
- BookRepository creation (with cleanup) and library reads in onCreate/onResume still run on the UI thread.
- After process death, history/current feed addresses come from the Bundle and are refetched; feeds aren't saved.
- Version lives in `app/build.gradle.kts` (`versionName`, `versionCode`).

## Dependencies
- Uses: `core/`, Readium 3.0.0 · Don't guess Readium API from newer docs; check the 3.0.0 sources.
