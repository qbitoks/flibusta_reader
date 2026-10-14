# 0002: Convert FB2 to EPUB locally

Date: 2026-10-12 (retrospective) · Status: accepted

## Context
FB2 and EPUB must work without a server or account; the EPUB navigator is already chosen (0001).

## Decision
Convert FB2/FB2.ZIP locally to EPUB 3 with `core/src/main/kotlin/app/folio/core/Fb2Converter.kt`,
then read it with the same Readium navigator. Store the result as EPUB; keep the source format in the
library entry. The converter emits one XHTML plus TOC.

## Alternatives rejected
- Separate FB2 renderer — a second reading and position-restore path.

## Consequences
- Conversion is JVM-testable and Android-free.
- Complex FB2 layout is partial; large books need performance checks. Not MOBI/PDF/DRM support.
- Changing XHTML structure or anchors must re-check TOC, internal links and position restore;
  already imported EPUBs are not regenerated.
