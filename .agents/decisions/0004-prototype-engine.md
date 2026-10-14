# 0004: Prototype codebase replaces the Readium app

Date: 2026-10-12 · Status: accepted (supersedes 0001, 0002, 0003)

## Context
A separate prototype app had an Apple Books–style design and a richer reader
(own pagination, page curl, highlights, notes, guide line, collections). The user asked to move its
functionality and design into Folio and chose the prototype as the base.

## Decision
The prototype multi-module code (Room, Hilt, own FB2/EPUB parser and layout engine) is the app.
Flibusta's OPDS/HTTPS/XML code lives on as JVM module `core/opds`, used by `core/data/catalog` and
the «Каталог» tab (`feature/catalog`). Kotlin packages are `app.folio`;
applicationId `app.folio` (renamed from `net.flibustareader` 2026-10-14); minSdk stays 34.

## Alternatives rejected
- Port only the design onto Readium — loses curl, highlights, notes, guide line.
- Staged port of library screens first — two half-designs for longer.

## Consequences
- Readium, FB2→EPUB conversion, `library.json`/Locator storage are gone. Per the user, no data
  migration from 0.2.x: the old library is not carried over.
- ADB regression scripts targeted the Readium UI and are stale until rewritten.
