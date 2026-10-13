# feature/reader

Reader screen: page turning, scroll mode, header/footer lines, selection, sheets. UI rules:
`.agents/design/apple-books-guidelines.md` (§ 14 lists where we differ from Apple Books).

## Entry points
- `ReaderScreen.kt` — `ReaderContent`: text insets, tap handling (`tapOnPage`, `onTap`), overlays, header/footer.
- `ReaderViewModel.kt` — book, `PaginationEngine`, `state` (snapshot state, not a flow), jumps and history.
- `Pager.kt` — `HorizontalPageTurner` (Slide/Curl/Fade). Gesture math lives in `ReaderGestures.kt` (JVM-tested).
- `ScrollReader.kt` — scroll mode list; chapter window in `ScrollWindow.kt`.
- `PageChrome.kt` — header/footer text (`footerCenter`, `headerText`), clock, battery, scroll fades/strip.
- `ThemesSheet.kt` — «Темы и настройки», «Настройка темы», «Колонтитулы».
- Settings: `core/data/.../settings/SettingsRepository.kt` (`ReaderSettings.header`, `.footer`).

## Rules
- Header/footer settings change the text insets, so they re-paginate (new `layoutGeneration`) like a font
  change. Keep the position (`ReaderViewModel.readingPosition`), never the page index.
- Page title and footer are part of the page in paged mode (they slide with it); in scroll mode they are fixed.
- Pure logic goes into small top-level functions with tests in `src/test` (`ReaderGesturesTest` is the example).
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
