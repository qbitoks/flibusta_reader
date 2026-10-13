# feature/reader

- Read `.agents/design/reader.md` for reader UI; use `.agents/design/design-system.md` only for shared components.

## Entry points
- Resolve symbols below in `feature/reader/src/main/kotlin/net/flibustareader/feature/reader/`; search exact names first.
- `ReaderRoute`, `ReaderScreen` — appearance, ink, lifecycle and screen composition; `ReaderControls` — local/saveable UI state.
- `ReaderSurface` — viewport, insets, guide geometry and layer order; `ReaderPages` — paged/scroll rendering.
- `readerInteractions` — tap targets, tap zones, long-press selection; `ReaderSelectionOverlays` — selection, notes, palette.
- `ReaderChrome` — fixed controls, history, footer; `ReaderMenu` — capsules/buttons; `ReaderSheets` — sheet dispatch.
- `WindowEffects`, `ImmersiveMode`, `ReadingSessionEffect` — orientation, brightness, bars, session lifecycle.
- `ReaderViewModel` — snapshot `state`, `PaginationEngine`, navigation/history, eager flows and coroutine scope.
- `ReaderSearch` / `searchBook` — search job/state, normalization and snippets; `ReaderAnnotations` — range/text and persistence.
- `HorizontalPageTurner` — Slide/Curl/Fade; `tapZone` — pure gesture math; `ScrollReader` / `planScrollWindow` — scroll/window.
- `footerCenter`, `headerText`, `rememberClock`, `rememberBattery` — page chrome; `ThemesSheet` — appearance/header/footer settings.
- Settings: `core/data/src/main/kotlin/net/flibustareader/core/data/settings/SettingsRepository.kt`.

## Rules
- Header/footer settings change the text insets, so they re-paginate (new `layoutGeneration`) like a font
  change. Keep the position (`ReaderViewModel.readingPosition`), never the page index.
- Page title and footer are part of the page in paged mode (they slide with it); in scroll mode they are fixed.
- Pure logic goes into small top-level functions with tests in `src/test` (`ReaderGesturesTest` is the example).
- Search example: `feature/reader/src/test/kotlin/net/flibustareader/feature/reader/ReaderSearchTest.kt`.
- Keep UI state ownership in `ReaderControls`; only chrome visibility, rotation lock and brightness are saveable.
- Keep annotation flows and `viewModelScope.launch` in the ViewModel; helpers must preserve persistence order and read timing.
- Tests: `./gradlew.bat :feature:reader:testDebugUnitTest --console=plain`.

## Gotchas
- Scroll list: resolve pages by item key (`Page.key`), not by index. Right after chapters are prepended,
  `firstVisibleItemIndex` and the page list disagree for a frame.
- Scroll reports carry the last shown `ScrollJump.id`; `onScrolledTo` drops older ones, or a report from the
  old list position lands after a jump and undoes it.
- `scrollToItem` right after replacing the list's items clamps to the old item count: wait for
  `layoutInfo` to show the new items first (see `show()` in `ScrollReader`).
- Reading `ScrollLocator.originOf` in composition subscribes to every scroll frame; only do it while a
  selection or highlight palette is open.
- `Page.lead` is the spacing a page break swallowed; paged mode ignores it, scroll mode adds it on top.
- `PageView` draws text in its own layer so title/clock/footer recompositions don't redraw the text.
- On the emulator the cold start takes 4–5 s; `adb` taps sent earlier land on the home screen or trigger
  an input ANR. Open the test book with `am start -W -a android.intent.action.VIEW -d file://...`.
