# core/format

Pure Kotlin/JVM book parsers: FB2 / FB2.ZIP (SAX) and EPUB (Jsoup) into `core/model`. No Android.

## Entry points
- `core/format/src/main/kotlin/net/flibustareader/core/format/BookParser.kt` — `detect()` by content, `parse()`
- `core/format/src/main/kotlin/net/flibustareader/core/format/fb2/Fb2Parser.kt` — streaming FB2, repair retry
- `core/format/src/main/kotlin/net/flibustareader/core/format/epub/EpubParser.kt` — OPF, spine, CSS, XHTML

## Conventions
- No DTD, no external entities; enforced by our own checks, not only by parser features.
- Limits stay: unpacked FB2 50 MiB, nesting depth 128.

## Gotchas
- Android's SAX (Harmony/expat) throws on Xerces-only features (`disallow-doctype-decl`): set every
  feature in `runCatching`. JVM tests won't notice; `AndroidSaxTest` swaps in such a factory.

## Commands
- `./gradlew.bat :core:format:test --console=plain`
