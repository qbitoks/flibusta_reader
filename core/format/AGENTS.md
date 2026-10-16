# core/format

Pure Kotlin/JVM book parsers into `core/model`, no Android: FB2 / FB2.ZIP (SAX), EPUB, HTML, DOCX (Jsoup),
MOBI/PalmDOC, RTF, plain text; zipped TXT/HTML/RTF as catalogs serve them.

## Entry points
- `core/format/src/main/kotlin/app/folio/core/format/BookParser.kt` — `detect()` by content (`%PDF-x.y` in the
  first KiB → `BookFormat.Pdf`, never parsed: `feature/reader` `PdfViewer` shows its pages), `parse()`;
  `unzipPdf()` — a zipped PDF (LitRes `a6.pdf`), unpacked on import so the book ID is the PDF's SHA-1
- `core/format/src/main/kotlin/app/folio/core/format/fb2/Fb2Parser.kt` — streaming FB2, repair retry
- `core/format/src/main/kotlin/app/folio/core/format/epub/EpubParser.kt` — OPF, spine, CSS, XHTML
- `core/format/src/main/kotlin/app/folio/core/format/text/TextParser.kt` — any text file (detect fallback:
  no NULs); UTF-8 → windows-1251, prose joined vs line-kept configs, 64 KiB chapters
- `html/HtmlBookParser.kt` (one HTML page via EPUB's `HtmlConverter`), `mobi/MobiParser.kt` (PalmDB, LZ77,
  EXTH; HTML into `HtmlBookParser`; no HUFF/CDIC, no DRM), `rtf/RtfParser.kt`, `docx/DocxParser.kt`
- `Chapters.kt` — `splitChapters()` (at h1/h2 and every 64 KiB, headings → TOC), `readBounded()` 50 MiB

## Conventions
- No DTD, no external entities; enforced by our own checks, not only by parser features.
- Limits stay: unpacked FB2 50 MiB, nesting depth 128.

## Gotchas
- Agent Write/Edit tools turn `\uXXXX` in source into the literal character; build such strings from
  char codes and check bytes with `od -c` (RTF `\uN` test fixtures hit this).
- Android's SAX (Harmony/expat) throws on Xerces-only features (`disallow-doctype-decl`): set every
  feature in `runCatching`. JVM tests won't notice; `AndroidSaxTest` swaps in such a factory.

## Commands
- `./gradlew.bat :core:format:test --console=plain`
