# core/opds

Pure Kotlin/JVM OPDS client logic: feed parsing, HTTPS-only requests, safe XML. Does NOT own
downloads, files or UI (`core/data/.../catalog/CatalogRepository.kt`, `feature/catalog/`).

## Entry points
- `core/opds/src/main/kotlin/net/flibustareader/core/opds/OpdsParser.kt` — entries, navigation, EPUB/FB2 downloads, `next`
- `core/opds/src/main/kotlin/net/flibustareader/core/opds/HttpsRequest.kt` — manual redirects (HTTPS only, max 6), timeouts 20/30 s, final URL
- `core/opds/src/main/kotlin/net/flibustareader/core/opds/Xml.kt` — namespace-aware XML; rejects DTD and external entities

## Conventions
- No Android or Compose imports.
- Inherit `xml:base` from feed down to link; resolve against the final response URL; keep only HTTPS links.
- Feed size limit (5 MiB) and book limit (50 MiB) are enforced by `CatalogRepository`.

## Commands
- `./gradlew.bat :core:opds:test --console=plain`; fixtures: `fixtures/catalog.xml`, `fixtures/search-books.xml`

## Dependencies
- Used by: `core/data` · Must not depend on: Android, Compose
