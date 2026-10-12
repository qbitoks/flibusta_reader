# core

Pure Kotlin/JVM logic: OPDS, HTTPS, safe XML, FB2→EPUB, atomic index file, reader settings and math.
Does NOT own Android UI, lifecycle, files layout or Readium: that is `app/`.

## Entry points
- `core/src/main/kotlin/net/flibustareader/core/OpdsParser.kt` — feed, books, navigation, download formats, `next`, link resolution
- `core/src/main/kotlin/net/flibustareader/core/HttpsRequest.kt` — manual redirects (HTTPS only, max 6 hops), timeouts 20/30 s, passes final URL
- `core/src/main/kotlin/net/flibustareader/core/Xml.kt` — namespace-aware XML; rejects DTD and external entities
- `core/src/main/kotlin/net/flibustareader/core/Fb2Converter.kt` — FB2 → EPUB 3: one XHTML, package, TOC, raster images
- `core/src/main/kotlin/net/flibustareader/core/AtomicTextFile.kt` — index update via temp file + replace, per-canonical-path lock
- `core/src/main/kotlin/net/flibustareader/core/ReaderOptions.kt` — settings ranges, normalization, HUD toggles
- `core/src/main/kotlin/net/flibustareader/core/ReaderInteraction.kt` — tap zones, progress/seek weighted by resource positions
- `core/src/main/kotlin/net/flibustareader/core/ReaderViewport.kt` — horizontal geometry with insets and 760 dp max column
- `core/src/main/kotlin/net/flibustareader/core/ReadingProgress.kt` — picks the available reading percentage

## Conventions
- No Android, Compose or Readium imports. Move JVM-testable logic here from `app/`.
- OPDS: inherit `xml:base` from feed down to link; resolve against the final response URL; keep only HTTPS links.
- Limits: OPDS feed 5 MiB; FB2 depth 128, checked iteratively before the recursive renderer.
- Converter anchor generator reserves source ids; this is not full validation of duplicate ids.
- AtomicTextFile lock is in-process only, not cross-process; falls back to REPLACE_EXISTING if ATOMIC_MOVE fails.
- Fixtures for parser tests: `fixtures/catalog.xml`, `fixtures/search-books.xml`.

## Commands
- All: `./gradlew.bat :core:test --console=plain`
- One class: `./gradlew.bat :core:test --tests 'net.flibustareader.core.<Class>Test' --console=plain`
- Reports: `core/build/reports/tests/test/index.html`

## Tests map
- OPDS/HTTPS: `OpdsParserTest`, `HttpsRequestTest` (fake HttpURLConnection, no live endpoint)
- FB2/XML: `Fb2ConverterTest`; index: `AtomicTextFileTest`
- Reader math: `ReaderInteractionTest`, `ReaderViewportTest`, `ReaderOptionsTest`, `ReadingProgressTest`
- `ReviewRegressionTest` — regressions R01, R03–R05, R08 from the 2026-10-12 review (depth, anchors,
  xml:base, final URL, CDATA mentioning DOCTYPE). Keep them green when touching XML/FB2/OPDS.

## Dependencies
- Used by: `app/` · Must not depend on: Android, Compose, Readium
