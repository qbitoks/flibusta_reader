# core/opds

Pure Kotlin/JVM OPDS client logic: feed parsing, HTTPS-only requests, safe XML. Does NOT own
downloads, files or UI (`core/data/.../catalog/CatalogRepository.kt`, `feature/catalog/`).

## Entry points
- `core/opds/src/main/kotlin/app/folio/core/opds/OpdsParser.kt` — entries, navigation, EPUB/FB2 downloads,
  cover, related links, genres/year/size, `next`; server HTML `<content>` → plain paragraphs (`Description`)
- `core/opds/src/main/kotlin/app/folio/core/opds/Opds2Parser.kt` — OPDS 2 JSON → the same `CatalogPage`; `OpdsParser.parse` picks it for `{`
- `core/opds/src/main/kotlin/app/folio/core/opds/Links.kt` — link resolving (http→https), `SearchTemplate.fill`, `OpenSearch.template`
- `core/opds/src/main/kotlin/app/folio/core/opds/HttpsRequest.kt` — manual redirects (HTTPS only, max 6),
  timeouts (connect 20 s, read 30 s by default), request headers, `HttpStatusException` for non-2xx
- `core/opds/src/main/kotlin/app/folio/core/opds/Resume.kt` — resume plan for `Range` downloads; HTML-instead-of-book check
- `core/opds/src/main/kotlin/app/folio/core/opds/Xml.kt` — namespace-aware XML; rejects DTD and external entities

## Conventions
- No Android or Compose imports.
- Inherit `xml:base` from feed down to link; resolve against the final response URL; `http` links become `https`, other schemes are dropped.
- Feed capabilities (search, sort/new, sort/popular, facets) come from feed-level links; sort links also from home entries (iknigi).
- Feed size limit (5 MiB) is enforced by `CatalogRepository`, book limit (50 MiB) by `DownloadManager`.
- A book entry has `navigation == null`; PDF-only books have empty `downloads` and non-empty `otherFormats`.

## Gotchas
- Some servers build EPUB on request: 15–90 s before the first byte, so downloads use a 150 s read timeout.
  FB2 is served as a static `.fb2.zip` from a static host with `Accept-Ranges`.
- Server search often answers 502/504 or stalls on the first try; author results are alphabetical.

## Commands
- `./gradlew.bat :core:opds:test --console=plain`; fixtures: `fixtures/catalog.xml`, `fixtures/search-books.xml`

## Dependencies
- Used by: `core/data` · Must not depend on: Android, Compose
