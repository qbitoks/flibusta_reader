# Rule: reader, catalog and storage invariants

Trigger: changing `ReaderActivity.kt`, `ReaderChrome.kt`, `ReaderViewport.kt`, `ReaderInteraction.kt`,
`ReaderPreferences.kt`, OPDS/HTTPS code, or the debug/release checkpoint.
Area details: `app/AGENTS.md`, `core/AGENTS.md`. Storage: `.agents/rules/storage.md`. Why: `.agents/decisions/0001-readium-navigator.md`.

## Reader
- Readium owns EPUB navigation. Panels draw over the text; never change the viewport or pagination.
- Hidden HUD indicators keep their tap areas: visible row 24 dp, tap area 48 dp. Column width max 760 dp.
- Fast typography changes keep the text anchor; while the anchor is restoring, no navigation may race it.
  A small change of the percentage is not a lost position.
- Install the Readium fragment and show the error dialog only in `RESUMED`, honouring `isStateSaved`.
  Don't bypass lifecycle with `commitAllowingStateLoss()`.
- The debug checkpoint (`app/src/debug/`) must not add file control or delays to release (`app/src/release/`).

- Insets are applied once: don't re-add Readium padding. The notch area is filled with the background.

## Catalog
- OPDS resolves against the final response URL and inherited `xml:base`.
- Catalog URL and download URL are separate settings.
