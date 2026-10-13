# Home and library

- Apply `.agents/design/apple-books-guidelines.md` for scope and `.agents/design/design-system.md`
  for shared typography, bands, glass and controls.

## «Главная»
- Order the active shelves «Продолжить», «Хочу прочесть ›», «Предыдущие ›»; omit store recommendations.
- Use horizontal «Продолжить» cards, 242 × 78 dp, radius 10, gap 16; show part of the next card.
  Tint the background from a blurred/darkened cover and keep text white, including with light covers.
- Put a 39 × 60 dp cover 9 dp from the left; begin text 12 dp after it: title 17 Semibold,
  author 17 Regular with ellipsis, then «книга • 1 %»; put «•••» near the right edge.
- Show «Хочу прочесть» as bottom-aligned covers, width 156 dp, gap 17, preserving original aspect ratio;
  put progress at lower left and «•••» at lower right. Open the user-managed collection from its title.
- Use the same compact card form with a neutral background for «Предыдущие» and «✓ Закончена» for finished books.
- Put started, unfinished, non-hidden books in «Продолжить»; put other previously opened books in
  «Предыдущие», ordered by last opening. Do not include unopened books there.
- Open «Предыдущие» with an inline centered serif title and left back control; use 42 dp covers,
  semibold title, secondary author, «Книга • ✓ Закончено» or «Книга • 1 % Завершено», and «•••».
- Animate opening/closing a book from/to its cover; keep equivalent transitions for generated covers.

## «Библиотека»
- Use two bottom-aligned cover columns, approximately 152 dp wide, 32 dp outer insets and 24 dp gap;
  preserve aspect ratios, with row pitch about 288 dp and captions about 17 dp below covers.
- Caption with progress or «НОВАЯ» at left and «•••» at right; omit iCloud icons.
- Use list rows about 78 dp high: 52 dp cover, one/two-line 17 sp title, secondary author,
  progress/«НОВАЯ», and «•••»; start dividers at the cover edge. Use «Неизвестный автор» when absent.
- Open view/actions in a glass «•••» pull-down, width 243 dp, radius 19:
  «Выбор»; «Сетка / Список»; «Сортировка по…» with «По недавним / По названию / По автору / Вручную».
- Put «Добавить книги», «Следить за папкой…» and «О приложении» in that menu, not in a profile/avatar.
- Switch to «Вручную» after dragging a book; use drag order as the persisted manual sort.
- In selection mode, truncate Large Title if needed; show «Выбрать все» and round ✓ at top right,
  circles at cover bottom right (white checkmark when selected), and bottom glass actions:
  delete circle, «Добавить в...» capsule, share circle.

## Book menus
- Open the menu from «•••»; a long press on a cover may enlarge it without opening the menu.
- Start with icon-above-label actions «Поделиться» and «Хочу прочесть»; then «Добавить в коллекцию»,
  «Отметить как „Закончена“», divider, «Переименовать…», divider, red «Удалить…».
- Add «Удалить из списка „Продолжить“» for a continue card.
- In a collection, add «Удалить из „<коллекция>“» first and keep only «Поделиться» in the top action row.
- Keep destructive actions red and separated from regular actions.

## Collections
- Use the «Коллекции» sheet: serif Bold title, «Править» capsule, round ×, grouped cards inset 16 dp.
- Use 48 dp rows: 24 dp outline icon, title near x=55, secondary count and chevron at right,
  with dividers starting at the text.
- Keep system «Хочу прочесть», «Законченные», «Книги» in the first card; put custom collections
  and «+ Новая коллекция…» in the second. Apply exclusions from the design index.
- In «Править», center the title and show ✓; put red ⊖ at left, custom collection drag handles at right,
  and allow hiding system collections. Exact Apple duplication is unnecessary here.
- Use the same cards in «Добавить в коллекцию», with centered serif title and right ×;
  disable collections that cannot accept additions, such as «Книги».
- Show a collection with left round ‹, right «•••», Large Title and a large list:
  127 dp cover, 20 sp serif Semibold title, secondary author, white «ЧИТАТЬ» capsule and «•••».
- Center gray serif Bold «Нет книг» in an empty collection and explain adding through «Добавить в коллекцию».

## Library search
- Use Large Title «Искать», a bottom search capsule and a library/back circle; show recent requests
  (magnifier + text) with «Очистить» before entry, then library books as list rows while typing.
- Keep catalog search in `feature/catalog`; do not substitute OPDS results for local library search.

## Deferred reading goals
- Keep goals/year summaries deferred; the following reference is for an explicitly requested future task.
- Center «Цели по чтению», a secondary two-line subtitle and a blue `#009BE8` arc open at the bottom.
  Inside: «Чтение сегодня» serif Bold 20, elapsed time serif 64, daily target and chevron.
- Use a 303 × 49 dp «Продолжить чтение» capsule with book title below; add seven 28 dp weekday circles:
  past gray, current partial blue ring, future outline/dim text, completed blue fill.
- Show «Начать новый период чтения ›» and the reading streak record; on goal completion show
  blue circle with ✓/minutes and «Изменить цель ›».
- Edit the daily target through a bottom wheel «Дневная цель по чтению» with «Готово»;
  show a blue streak-count modal and «ПОДЕЛИТЬСЯ» when opening the goal block.
- Keep «Книжные итоги года» as a yearly book target reference; do not add a profile avatar for it.