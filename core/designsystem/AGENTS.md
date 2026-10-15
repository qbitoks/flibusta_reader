# Shared visual system

## Entry points
- Read `.agents/design/design-system.md` before changing shared theme, typography, components or navigation visuals.
- Use `core/designsystem/src/main/kotlin/app/folio/core/designsystem/Theme.kt` for semantic colours and `FolioType`.
- Use `core/designsystem/src/main/kotlin/app/folio/core/designsystem/ReaderPalette.kt` for reading-surface colours.
- Use `core/designsystem/src/main/kotlin/app/folio/core/designsystem/ReaderFonts.kt` for font keys and `TypefaceProvider`.
- Use `readerChromeTextStyle` for page labels and their style chips; `PageTurnIcons` for page-turn modes.
- Use `ReaderSpacingIcons` with the stacked `SegmentedControl` for spacing; preserve page-turn geometry and outline weight.
- Use `core/designsystem/src/main/kotlin/app/folio/core/designsystem/BookCover.kt` for local-file and generated covers.
- Use `core/designsystem/src/main/kotlin/app/folio/core/designsystem/components/Sections.kt` as the canonical themed component example.
- Use `core/designsystem/src/main/kotlin/app/folio/core/designsystem/components/Glass.kt` for backdrop source and glass fallback.

## Invariants
- Reuse semantic `Folio.colors` and `FolioType` in feature UI; add shared visual roles here.
- Keep app theme colours separate from `ReaderPalettes`; book appearance can differ from the app.
- Keep reader font keys stable; coordinate new families with settings and both Compose previews and Android typefaces.
- Supply local cover files to `BookCover`; catalog network loading belongs to `core/data/`.
- Provide `GlassScope` and its source modifier for backdrop blur; preserve fallback fills for separate popup windows.
- Keep navigation actions and repository mutations in callers; shared components expose callbacks.
- Use `IosSlider(enabled = false)` for inactive controls; omit adjustable accessibility actions at the caller.
- Slider gestures use current callbacks, clamp values to the range and omit repeated unchanged values at endpoints.
- Own slider touches through release on both axes; retain grab offset and report cancellation separately from release.
