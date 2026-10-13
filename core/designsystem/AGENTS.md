# Shared visual system

## Entry points
- Read `.agents/design/design-system.md` before changing shared theme, typography, components or navigation visuals.
- Use `core/designsystem/src/main/kotlin/net/flibustareader/core/designsystem/Theme.kt` for semantic colours and `FlibustaType`.
- Use `core/designsystem/src/main/kotlin/net/flibustareader/core/designsystem/ReaderPalette.kt` for reading-surface colours.
- Use `core/designsystem/src/main/kotlin/net/flibustareader/core/designsystem/ReaderFonts.kt` for font keys and `TypefaceProvider`.
- Use `core/designsystem/src/main/kotlin/net/flibustareader/core/designsystem/BookCover.kt` for local-file and generated covers.
- Use `core/designsystem/src/main/kotlin/net/flibustareader/core/designsystem/components/Sections.kt` as the canonical themed component example.
- Use `core/designsystem/src/main/kotlin/net/flibustareader/core/designsystem/components/Glass.kt` for backdrop source and glass fallback.

## Invariants
- Reuse semantic `Flibusta.colors` and `FlibustaType` in feature UI; add shared visual roles here.
- Keep app theme colours separate from `ReaderPalettes`; book appearance can differ from the app.
- Keep reader font keys stable; coordinate new families with settings and both Compose previews and Android typefaces.
- Supply local cover files to `BookCover`; catalog network loading belongs to `core/data/`.
- Provide `GlassScope` and its source modifier for backdrop blur; preserve fallback fills for separate popup windows.
- Keep navigation actions and repository mutations in callers; shared components expose callbacks.
