# Reader design

- Apply `.agents/design/apple-books-guidelines.md` for scope and `.agents/design/design-system.md`
  for shared controls; follow `.agents/DECISIONS.md` when a user choice differs from Apple.

## Page and deliberate deviations
- Use justified, hyphenated body text; Original keeps a 1.6 em first-line indent and no paragraph gap.
  Other presets supply their paragraph gap and indent; keep equal line pitch and fit images to the column.
- Restore the omitted final-line leading between body blocks before adding their paragraph gap;
  zero paragraph gap must retain the normal line pitch. Carry the gap across page breaks with `Page.lead`.
- Apple reference: horizontal margins 39 dp, column about 314 dp, line pitch 23.5 dp;
  «Оригинал» dark uses black/white. Adapt measurements to current font and user settings.
- Preserve the user decisions from 2026-10-13:
  - Configure lines in «Темы и настройки» → «Дополнительно»: header none, book or chapter title (default);
    footer «42» (page only), clock/battery off; Slide.
  - With no header, start text below the status-bar inset; when controls are shown, put a page-color
    strip behind the top buttons. With no footer content, let text extend to the bottom.
  - Put clock at footer left, page/percent plus «ещё N стр.» at center, battery at right.
    Fade header/footer out with controls shown; retain the chosen number format and stable title margins.
  - Keep saved label styles compatible but omit preset buttons; derive reserved space from scaled line height.
  - Protect scroll label blocks with `scrollEdges`; fade inward from the boundary, never under labels.
    Use `ReaderLineBlock` for shared book/preview geometry and rules fixed to the text-facing block edge.
  - Put independent «Граница текста: Чёткая / Плавная», block height and vertical label position
    inside each line's «Подробнее» in Scroll. Absolute text gap 0 reaches the rule or invisible block edge.
  - Keep preview taps inert; use the tabs to navigate settings, and preserve manual preview scrolling.
    In paged mode, reserve space below the footer.
  - Keep history arrows inside the controls only; expire after five reading pages, with no floating return icon.
  - Use left/right thirds for tap turning only with controls hidden; any plain tap with controls visible hides them.
- Keep the header/footer inside each paged page so they travel with it; fix them in scroll mode.
- Use `ChapterProgress` TOC ranges for title, contents selection and remaining pages across all levels.
  Use the current reading position for current labels and page starts for neighboring layers; omit unknown/pre-TOC remainder.
  Do not restore Apple's always-visible book title or 10% edge tap zones.

## Chrome, panels and navigation
- Open books with the controls visible; use two states, reading and controls, without a floating reader menu.
- Overlay both panels without changing viewport/pagination. Fade page labels out under controls and restore them on dismissal.
- Use page-tinted backdrop glass and semantic interface colours; keep buttons accessible at low reading contrast.
  Everything opened from a book (buttons, search, position, contents, notes, menus) takes the page tone via
  `ReaderPalette.uiColors()` (`core/designsystem/ReaderUiColors.kt`); no fixed white/black fills in reader UI.
- Top: 48 dp X closes the book; centred title/author shares it. Keep the right slot empty.
- Panel rows (title, chapter, «N% от всей книги») have no fill; clip them to `CircleShape` so the press ripple is rounded.
- Bottom: whole chapter/remainder row opens contents; omit unknown remainder and use TOC ranges for titles/counts.
- Keep a local progress-slider draft: preview chapter/page while dragging; record one jump only on release.
  Clear drafts on cancellation; reuse the position resolver for preview/navigation and show unknown counts honestly.
- Centre tappable book percent between equal reserved history slots: back at left, forward at right.
- Hide arrows after five natural page crossings; count successful turns/scroll distance, preserve history on expiry.
- Reset arrow visibility after a landed jump/history action; retain the window across repagination.
- Open `ReaderPositionSheet` from percent: validate exact page/percent input and list recent history positions.
- Use the settings dialog geometry for position: fixed header, root system/IME insets, weighted scroll body.
  Reuse `SettingsSegments`, `GroupedCard`, `CapsuleButton` and grouped history rows; omit Material outlined fields.
- Cap the lower panel below the measured top panel; scroll navigation details when needed and pin the tool row.
- Show five fixed tools: bookmarks/fragments even when empty, bookmark, themes/settings, guide, search.
  Mark an active bookmark red; disable guide in Scroll. Guide: tap only toggles (no long press); the panels stay open.
- Put rotation lock in the reader settings Page tab; keep it a saveable window preference, outside book/shared settings.
- Show a small red bookmark at the upper-right corner while controls are hidden; tapping removes it directly.
- Use `ReaderProgressSlider`: thin themed track, round thumb, 48 dp touch target, ink contrast at least 3:1.
- With controls visible, plain text taps only dismiss them, including side thirds and guide taps.
  Keep links, images, notes and selection/highlight handling ahead of plain text tap zones.
- With controls hidden, page with outer-thirds taps and toggle with the centre/footer; Scroll toggles on any plain text tap.
- Keep swipes for page turning and hide panels on drag; swipe down closes to the cover, with no swipe-up action.
- Navigate ordinary note/internal links with history; use a popup for dedicated FB2/EPUB footnote bodies.
  Style note links as blue superscripts; retain the last book page as the end without a completion screen.

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
- Show «Темы и настройки» in a full-height `FolioSheet` without grabber or half detent: X, Back or strict drag-to-close.
  Keep the header and «Основные / Дополнительно» tabs fixed; scroll each tab independently.
- Pin a fixed sample text above controls (beside them in landscape), the same in every book; reuse `Paginator`
  and `PageView`. Keep it short (heading, dialogue, long words, digits) so it re-paginates at once off-thread.
  Edits show instantly; saves are debounced (`SettingsEditor`) so the book behind doesn't re-paginate per tick.
  Show header/footer with actual settings; keep the preview visible while any setting is edited.
- Apply edits immediately through `SettingsEditor`; use inline disclosures instead of
  opening «Настроить» or a separate «Колонтитулы» sheet. Close never discards edits.
- Under the title, «Для всех книг ⌄ / Только для этой книги ⌄» opens a pull-down menu: the edit target,
  a summary of what the book changes, «Сделать общими для всех книг» and «Вернуть общие настройки»
  (both confirmed). The panel opens on the book's own settings when it has any, else on shared ones.
  In the book scope an accent «Книга» badge after the heading marks groups the book sets itself. Library → «…» → «Настройки чтения» opens
  the same panel for all books over a sample page, without brightness.
- Use semantic interface colors, shared grouped cards with 16 dp insets, adaptive rows and labelled controls.
  Keep the reading palette inside theme tiles and previews; keep selected states accessible.
- Show five palette-only cards in fixed order: Original («Классика»), Focus («Уют»), Calm («Тепло»), Paper, Quiet.
  Use identical Literata samples and accessible palette names. Resolve light/dark variants from appearance, including Android Auto.
  Keep typography unchanged when selecting a palette; preserve independent tone/contrast and inheritance of untouched controls. Omit custom/add/edit tiles and background circles.
- Keep two tabs: Basic («Основные») and Advanced («Дополнительно»), with independent scrolling and a pinned preview.
  In Basic, use `SettingsGroupHeader` sections: Theme (palette tiles with names under them, accent ring on the selected one;
  appearance segments), Text (size slider framed by small/large «A»), Screen (brightness, rotation-lock toggle), Turning.
  Palette changes cross-fade the preview (~220 ms) and give a light haptic tick.
  Brightness Auto (accent-tinted capsule when on) follows the device and disables its slider; manual takeover starts at the current device value.
- Start Advanced with upper/lower line cards, then the font card (one scrolling row of «Аа» tiles in each font, bold as a «Ж» toggle in its group header), visible spacing cards with collapsed details, then the tone/contrast card.
  Preserve local line disclosures; keep Advanced card expansion across tab changes. Collapsing never changes settings.
- Use `ReaderStyle` for factory typography and `TextSet.DEFAULT` for settings; ignore obsolete linked-text settings without migration.
- Tone/contrast: a choice row of `TonePreset` swatches over the current theme, sliders in «Подробнее»; values outside presets add a selected «Свой» tile that stays while the tab lives.
- Show two cyclic spacing cards above their «Подробнее»: line spacing 1.0/1.2/1.4, side margins 3/5/8% of viewport width.
  Start at the middle values. Derive each card from the current value; custom values show a fourth sliders glyph without emphasis.
  Keep custom cards tappable: restart at the minimum. Details edit the same values; opening or collapsing changes no settings.
  Title it with `SettingsGroupHeader` (own badge) like the line cards; share `SettingsDetailsRow` + `SettingsDetails`.
  Draw them as two equal `SpacingTile` buttons (fill, 14 dp corners, no raised selection): 24 dp icon and the current step
  («Своё» for custom); the title is only spoken. Not a segmented control: nothing is "selected", a tap advances the step.
  `ReaderSpacingIcons`: 24-unit grid, 1.7-unit stroke, whole-unit coordinates, glyphs centred on y = 12; one property
  changes per state (line step 4/6/8, page inset 3/5/6); custom is a two-slider glyph.
- Palette, font and tone tiles are one `ChoiceTile`: hairline swatch, `selectionRing` (accent ring with a gap), caption name below, haptic tick.
  «Подробнее» expands with `SettingsDetails` (~220 ms) and a rotating chevron. Spacing details are split by
  `SettingsSubheader`: lines and paragraphs, letters and words, page (margins, justify); line spacing and margins sliders
  carry `ReaderSpacingIcons` at their ends. Header segments have icons; hidden-line hints are tertiary with an eye-off icon.
  Inside every «Подробнее»: uppercase `SettingsSubheader` groups, dividers only between groups, compact sliders with
  icons at both ends (none where no clear glyph exists), plain numbers without «dp», `SegmentsTitle` in secondary subheadline.
  The reset row is centred destructive red text, grey when nothing differs.
  Brightness repeats the text-size layout: title row with the Auto capsule at the trailing edge, then the slider between icons.
- Put one «Сбросить дополнительные настройки» action at the end of Advanced; confirm its book/shared scope.
  Use `withAdvancedDefaults` to reset only Advanced; preserve Basic values, window controls and annotation state; the guide resets with Advanced.
  In book scope store factory values as differences from current shared settings; keep the scope-menu action for inheriting shared settings.
  Preserve books, reading position and annotations; shared reset leaves other books' own fields intact.
- Show scope only under the sheet title; omit the bottom scope explanation and separate line-only reset.
- Keep page turning on Basic (`PageTurnIcons` above captions); keep upper and lower line cards on Advanced.
  Top: content segment («Нет» hides the line), then «Подробнее». Bottom: four labelled content capsules, then «Подробнее».
  Cycle progress on tap; keep chapter/time/battery independent. Use the settings segment track and raised selections.
  Show fixed progress button examples (42/300, 7%, 42, None); keep book values in the page preview.
  Replace footer details with a hidden-line hint when all content is off; preserve saved formatting for re-enabling.
  Show the same hidden-line hint for the header when its content is None.
  Disclosures only reveal fields; collapsing them never disables saved settings. First edits start from visible values.
  Short choices are segments whose labels shrink to fit; radio list only above 130 % text (never for icon rows).
- «Подробнее»: size 80–140 %, visibility 40–100 %,
  position (top: left/centre/right; bottom: spread/centre), edge offset 0…+24, space to text −12…+24,
  rule; top adds capitals and «Не показывать в начале главы».
- In Scroll, keep each boundary in its line disclosure; use nonnegative absolute gaps and protected blocks.
  Align labels at the screen edge, block centre or text edge; enlarge blocks to fit measured labels with 8 dp padding.
- Default blocks to 36 dp without overriding explicit heights; order details by look, position, separation (text boundary), behaviour.
  Name the rule «Разделительная линия» and the absolute Scroll gap «Зазор до текста».
- Everything in the panel except brightness can be set per book, the guide included; highlight colour stays shared.
- Use licensed Cyrillic fonts from `ReaderFonts`; EPUB publisher `@font-face` remains unsupported.

## Turning and scroll reference
- Step the guide by one visual row with left/right-half taps, even with a multi-line plate; a tap on the plate toggles controls.
- Land on the first row forward and the last row backward, for taps and swipes; no-op/canceled turns retain the row.
- Draw the guide inside each page (`GuidePage`): dimming and plate turn with the page and never vanish;
  an arriving page shows its landing row. Plate = text column + ≤ half margin; dim only the text area,
  the shadow may leave it. Controls visible → dimming stays whole. The window (`GuideWindow`) lights extra lines
  around/below/above with even or fading light; the plate frames only fully lit lines, and the whole plate is the tap target that toggles controls. Settings: Advanced only; the panel just toggles.
- Reset the guide after jumps or pagination changes; keep links, selection dismissal and footer taps ahead of row stepping.
- For «Скольжение», slide the current page left over a stationary next page; reverse by bringing the
  previous page from the left. Use overlapping pages without gaps or gray background between them.
- While moving, round the upper page about 27 dp; add a weak right-edge shadow about 7 dp wide.
  Dim the lower page about `0.25 × coveredFraction` (23% at start, 11% midway, 3% near end).
- For «Без анимации» (stored as `PageTurn.Fade`, listed first), replace the page at once; a swipe only picks the direction; for «Загиб», deform from edge in both directions,
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
- «Направляющая линия» is the last Advanced card (`GuideSettingsCard`): no toggle (the panel button turns it on), scrolling ready cards
  «Строка / Луч / С переходом / Абзац / Полоса» (attention, deep focus, line return, calm context, skimming; «Луч» also sets High dimming) and «Своя»
  for other windows; «Подробнее» holds direction, extra lines, fading and «Затемнение фона». No tips, no long-press menu.
- Open tapped images full-screen as «Изображение», with dark round ✓, gesture zoom and background
  tap cycling white/black; hide navbar on black. Omit Live Text.
