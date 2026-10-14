# Reader design

- Apply `.agents/design/apple-books-guidelines.md` for scope and `.agents/design/design-system.md`
  for shared controls; follow `.agents/DECISIONS.md` when a user choice differs from Apple.

## Page and deliberate deviations
- Use justified, hyphenated body text, first-line indent about 1.6 em, equal line pitch within/across
  paragraphs and no extra paragraph gap; fit inline images to the text column.
- Apple reference: horizontal margins 39 dp, column about 314 dp, line pitch 23.5 dp;
  «Оригинал» dark uses black/white. Adapt measurements to current font and user settings.
- Preserve the user decisions from 2026-10-13:
  - Configure lines in «Темы и настройки» → «Страница»: header none, book or chapter title (default);
    footer «42» (page only), clock/battery off; Slide.
  - With no header, start text below the status-bar inset; when controls are shown, put a page-color
    strip behind the top buttons. With no footer content, let text extend to the bottom.
  - Put clock at footer left, page/percent plus «ещё N стр.» at center, battery at right.
    Fade header/footer out with controls shown; retain the chosen number format and stable title margins.
  - Offer descriptive Small muted/Large regular/Book font/Bold contrast choices for upper and lower labels.
    Reuse `readerChromeTextStyle` in cards and pages; derive reserved space from scaled line height.
  - In scroll mode the text runs under the lines unless «Под строкой при прокрутке» says otherwise:
    «Обрезка»/«Затухание» mask the text itself (`scrollEdges`, eased 18 dp fade), nothing painted on
    the page; the cut follows «Отступ до текста» and, with a rule, reaches it at the smallest gap
    (`scrollRuleInsets`).
  - «Свои параметры» in one list, no captions: content, look, edge; in scroll mode «Под строкой при
    прокрутке», then gap and rule only when it isn't «Ничего» — what it reveals sits below it.
  - The settings preview mirrors this: lines overlay the text, scroll mode scrolls; a tap on a line
    opens its group in «Страница», on the text the «Текст» tab.
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
- Use a fixed dialog for «Темы и настройки», with X/Back dismissal and no drag or outside dismissal.
  Keep the header and «Оформление / Текст / Страница» tabs fixed; scroll each tab independently.
- Pin a fixed sample text above controls (beside them in landscape), the same in every book; reuse `Paginator`
  and `PageView`. Keep it short (heading, dialogue, long words, digits) so it re-paginates at once off-thread.
  Edits show instantly; saves are debounced (`SettingsEditor`) so the book behind doesn't re-paginate per tick.
  Show header/footer with actual settings; keep the preview visible while any setting is edited.
- Apply edits immediately through `SettingsEditor`; use inline disclosures instead of
  opening «Настроить» or a separate «Колонтитулы» sheet. Close never discards edits.
- Under the title, «Для всех книг ⌄ / Только для этой книги ⌄» opens a pull-down menu: the edit target,
  a summary of what the book changes, «Сделать общими для всех книг» and «Вернуть общие настройки»
  (both confirmed). The panel opens on the book's own settings when it has any, else on shared ones.
  In the book scope a dot marks groups the book sets itself. Library → «…» → «Настройки чтения» opens
  the same panel for all books over a sample page, without brightness.
- Use semantic interface colors, shared grouped cards with 16 dp insets, adaptive rows and labelled controls.
  Keep the reading palette inside theme tiles and previews; keep selected states accessible.
- In «Оформление», show one row of six «Aa» theme swatches (selected name in the group header) and a
  «Авто / Светлое / Тёмное» segment in the same card, then window brightness.
  Use a device-brightness switch and disable the manual slider while on; retain the last manual value.
  Resolve tile palettes from appearance, not from the current theme darkness; Quiet stays low contrast.
- In «Текст», show size, font samples, bold, and a collapsible custom-spacing group below the pinned preview.
  Keep ranges: size 13–42, line spacing 0.80–2.50, letter spacing ±10%, word spacing ±20%, margins ±10%.
  Keep justification in the spacing group and hyphenation enabled without a toggle.
- Reset only the selected theme typography (for a book: «Вернуть общий текст»); leave the font size unchanged.
- «Страница» ends with «Сбросить настройки страницы» (for a book: «Вернуть общие настройки страницы»): both lines with «Свои параметры» back to defaults; page turn stays.
- In «Страница», use three cards: page turning (`PageTurnIcons` above captions), upper line and lower line.
  Both lines: content segment (top «Нет» hides the line; bottom «Нет» drops only the number), four style chips, «Свои параметры» switch.
  Short choices are segments whose labels shrink to fit; radio list only above 130 % text (never for icon rows).
- «Свои параметры» (off draws the standard line, values kept): size 80–140 %, visibility 40–100 %,
  position (top: left/centre/right; bottom: spread/centre), edge offset 0…+24, space to text −12…+24,
  rule; top adds capitals and «Не показывать в начале главы»; bottom adds «осталось в главе», time,
  battery and, in Scroll mode, the background under scrolling text.
- Style chips sit on the page colour and draw their short name in their own style (`readerChromeTextStyle`);
  spoken names stay descriptive.
- Everything in the panel except brightness can be set per book; guide line and highlight colour stay shared.
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
