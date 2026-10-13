# Page layout

## Entry points
- Use `core/layout/src/main/kotlin/net/flibustareader/core/layout/Paginator.kt` for `LayoutSpec`, pages and chapter pagination.
- Use `core/layout/src/main/kotlin/net/flibustareader/core/layout/TextStyler.kt` for paragraph styles and Android text spans.
- Use `core/layout/src/main/kotlin/net/flibustareader/core/layout/PageRenderer.kt` for drawing and selection geometry.
- Use `core/layout/src/main/kotlin/net/flibustareader/core/layout/ReaderStyle.kt` for typography and injected font/image providers.
- Use `core/layout/src/main/kotlin/net/flibustareader/core/layout/TextGeometry.kt` as the canonical justified-text geometry example.

## Invariants
- Keep this Android `StaticLayout`/Canvas layer free of Compose; screen state belongs to `feature/reader/`.
- Paginate off the main thread and cache per `LayoutSpec`; include every new layout-affecting setting in the spec.
- Keep colours in `Ink`; theme changes repaint existing pages through `applyInk` without changing page breaks.
- Preserve source `Position` values and block offsets when splitting text into pages.
- Preserve `Page.lead` across breaks; scroll mode restores swallowed paragraph/separator spacing.
- Use content-box pixels for renderer coordinates; subtract screen insets and margins at the caller.
- Use `TextGeometry` for hit tests, carets and marks; raw framework offsets drift on justified lines.
- Read `.agents/design/reader.md` for text layout rules and `feature/reader/AGENTS.md` for callers and verification.
