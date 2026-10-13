# Reader design

- Apply `.agents/design/apple-books-guidelines.md` for scope and `.agents/design/design-system.md`
  for shared controls; follow `.agents/DECISIONS.md` when a user choice differs from Apple.

## Page and deliberate deviations
- Use justified, hyphenated body text, first-line indent about 1.6 em, equal line pitch within/across
  paragraphs and no extra paragraph gap; fit inline images to the text column.
- Apple reference: horizontal margins 39 dp, column about 314 dp, line pitch 23.5 dp;
  «Оригинал» dark uses black/white. Adapt measurements to current font and user settings.
- Preserve the user decisions from 2026-10-13:
  - Configure lines in «Темы и настройки» → «Страница»: header «Ничего» by default, book title or chapter title.
  - With no header, start text below the status-bar inset; when controls are shown, put a page-color
    strip behind the top buttons. With no footer content, let text extend to the bottom.
  - Put clock at footer left, page/percent plus «ещё N стр.» at center, battery at right.
    Fade header/footer out with controls shown; retain the chosen number format and stable title margins.
  - Offer Quiet/Classic/Book/Clear visual presets separately for upper and lower labels; keep content controls.
    Inherit each line from reader defaults unless the book overrides it; provide a reset for each line.
    Reuse `readerChromeTextStyle` in cards and pages; derive reserved space from scaled line height.
  - In scroll mode, fix the header on a solid background slightly higher, with text fading over
    about 18 dp before it; use a 12 dp lower fade or «Полоска» tinted nearly opaque page color.
    In paged mode, reserve space below the footer.
  - Show history «↶ / ↷» with controls; briefly show «↶» after a jump for 3 s or until first turn.
  - Use left/right thirds for tap turning; an edge tap with controls visible turns and hides them.
- Keep the header/footer inside each paged page so they travel with it; fix them in scroll mode.
- Use `ChapterProgress` TOC ranges for title, contents selection and remaining pages across all levels.
  Use the current reading position for current labels and page starts for neighboring layers; omit unknown/pre-TOC remainder.
  Do not restore Apple's always-visible book title or 10% edge tap zones.

## Chrome, menus and navigation
- On book opening, show status bar, top-right × and bottom-right menu; a central tap toggles controls
  and status bar. With reader menu open, hide × and replace the menu button with the menu.
- Size round reader controls about 47 dp, right inset 34, bottom inset 33 for menu;
  align them with header/footer. A page-number tap also toggles controls.
- Open the glass reader menu above the footer, 17 dp from right, width 265; leave the page undimmed.
- Use 45 dp capsule rows on 50 dp pitch: «Оглавление • N %», conditional «Закладки и фрагменты N»,
  «Поиск в книге», «Темы и настройки»; text left (15 dp inset), icon right.
- Add four 62 × 44 dp controls below: «Поделиться», rotation lock, «Направляющая линия», «Закладка».
- Mark bookmarks red (`#FF3B30`), including menu badge and hidden-mode bookmark circle;
  reveal «Закладки и фрагменты» when bookmarks/highlights exist.
- Use semantic interface text/icon colours in reader menus, including low-contrast reading palettes.
- Reserve footer side space for the guide/bookmark controls; size equal side columns from actual label widths.
- Record jumps from search, contents and links in history; display neighboring page numbers with
  «↶ / ↷», supporting both directions. Use history for returning from footnotes.
- Navigate ordinary note/internal links to their section with history; open a popup when the parser
  extracted a dedicated footnote body (FB2 notes or EPUB footnote/asides). Style note links as blue superscripts.
- Keep horizontal swipes for turning and swipe down for animated close to cover; no swipe-up action.
- Keep the last book page as the end, with no separate completion screen; mark completion through
  the existing reader logic.

## Contents, bookmarks and search
- Use contents sheet top near 61 dp, corners 23, dark `#242424`; header has 46 × 68 dp cover,
  20 sp Semibold title, «Страница X из Y» and ×.
- Set chapter rows in their book capitalization, 16 sp Semibold, with starting page at right;
  use 50/60 dp for one/two lines, 24 dp divider inset, and rounded current-row highlight (radius 11, inset 7).
- Keep contents separate from «Закладки и фрагменты»; do not add bookmark segments to contents.
- In bookmark/highlight sheet, use left selection circle, centered book title, right ✓,
  and «Закладки / Выделенное» segments.
- Show bookmark chapter («Без заголовка» fallback), date and page; show highlighted passage in book font,
  its highlight color, date and page. Support selection for deletion.
- Use the «Поиск в книге» sheet with centered 17 sp Semibold title and divider; autofocus keyboard.
- Put 45 dp search capsule above the keyboard, near x=12, with magnifier, «В этой книге», clear ⓧ,
  and separate round × at right. Keep the body empty until results exist.
- Show bold chapter title, secondary serif page number and book-font snippet with bold match;
  finish with «Поиск завершен / Найдено N совпадений». Highlight the jumped-to match blue on the page.

## Themes and customization
- Use one `FlibustaSheet` for «Темы и настройки», with a fixed header, close control and
  «Оформление / Текст / Страница» tabs. Preserve each tab scroll position; keep the body scrollable.
- Apply edits immediately through existing settings setters; use inline disclosures instead of
  opening «Настроить» or a separate «Колонтитулы» sheet. Close never discards edits.
- Use semantic interface colors, shared grouped cards with 16 dp insets, adaptive rows and labelled controls.
  Keep the reading palette inside theme tiles and previews; keep selected states accessible.
- In «Оформление», show six theme tiles, book appearance (follow app/light/dark), and window brightness.
  Resolve tile palettes from appearance, not from the current theme darkness; Quiet stays low contrast.
- In «Текст», show a text preview, size, font samples, bold, and a collapsible custom-spacing group.
  Keep ranges: size 13–42, line spacing 0.80–2.50, letter spacing ±10%, word spacing ±20%, margins ±10%.
  Keep justification in the spacing group and hyphenation enabled without a toggle.
- Reset only the selected theme typography; label that scope and leave the shared font size unchanged.
- In «Страница», show page-turn choice, a schematic preview, then upper and lower line groups.
  Keep content and appearance together. Hide upper style when its content is None, lower style when empty,
  and scrolling-background choices outside Scroll mode.
- Put inherited/default style in each line's inline style selector, alongside four compact font samples;
  selecting default removes the book override. Reuse `readerChromeTextStyle` for samples and preview.
- Explain scope: theme, text, page turning and line content are shared; appearance and line-style overrides
  belong to the current book. Preserve all existing preference keys and independent line inheritance.
- Use licensed Cyrillic fonts from `ReaderFonts`; EPUB publisher `@font-face` remains unsupported.

## Turning and scroll reference
- Step the guide by visual row with left/right-half taps; a tap on its row toggles controls.
- Land on the first row forward and the last row backward, for taps and swipes; no-op/canceled turns retain the row.
- Hide the guide during drag, animation, rollback and waits; clip it to the text viewport.
- Reset the guide after jumps or pagination changes; keep links, selection dismissal and footer taps ahead of row stepping.
- For «Скольжение», slide the current page left over a stationary next page; reverse by bringing the
  previous page from the left. Use overlapping pages without gaps or gray background between them.
- While moving, round the upper page about 27 dp; add a weak right-edge shadow about 7 dp wide.
  Dim the lower page about `0.25 × coveredFraction` (23% at start, 11% midway, 3% near end).
- For «Быстрое выцветание», crossfade about 100 ms; for «Загиб», deform from edge in both directions,
  with mirrored text showing through the back and a soft shadow; follow the dragging finger.
- Keep scroll positions and page numbers consistent with paged mode, including chapter jumps and seams.
- Treat the right-edge draggable scroll scrubber with «Глава … / с. N» bubble as a remaining reference
  detail; it is not present in the current `ScrollReader`.

## Selection, highlights, notes and images
- Long-press a word to select blue with circular blue handles; allow range dragging within a page.
- Show glass «Выделить │ Добавить заметку │ ›» capsule, then expanded actions «Выделить»,
  «Добавить заметку», «Найти», «Скопировать», «Поделиться…»; omit «Перевести» and web «Искать».
- On «Выделить» or tapping a saved highlight, show five color circles plus «U», separated by thin lines:
  yellow `#EFD038`, green `#9ADC82`, blue `#94B9FF`, pink `#FF9BB5`, purple `#C5AFFA`.
- Darken the highlight while its palette is open; render rounded per-line fills by multiplying with
  page color, keeping text legible; underline in red `#EC3B35`, about 2 dp, round ends below descenders.
- Add red «Убрать» after note action when selection overlaps saved highlights.
- Edit notes in a near-full sheet: «Заметка» plus gray time at left, dark round ✓ at right;
  show the excerpt in book font with highlight-color left bar, then 17 sp Semibold note text.
- Put a 22 dp highlight-color note circle at highlight top-left, outlined in page color, with small
  page-color square inside. Use haptics for selection start, moving handles and bookmarking.
- For «Направляющая линия», brighten a rounded line, dim other text, move line by taps, and offer
  «Затемнение фона: Высокая / Средняя / Низкая / Нет» and disable from the bottom-left circle.
- Open tapped images full-screen as «Изображение», with dark round ✓, gesture zoom and background
  tap cycling white/black; hide navbar on black. Omit Live Text.
