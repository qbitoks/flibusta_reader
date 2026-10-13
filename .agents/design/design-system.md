# Shared visual system

- Apply `.agents/design/apple-books-guidelines.md` for scope and measurement; reuse `core/designsystem`
  components instead of adding a separate style for each screen.
- Keep covers large and chrome sparse; use bold serif screen/section titles and sans-serif controls.
- Inset «Главная», «Библиотека» and «Каталог» content by 32 dp; inset sheet cards by 16 dp.
- Keep selected icons/checkmarks monochrome; reserve color for covers, reading goals and status badges.

## Typography
| Role | Family | Size, sp | Weight |
|---|---|---|---|
| Large screen title | Serif | 34–36 | Bold |
| Section title | Serif | 24 | Bold |
| Centered reading-goal title | Serif | 26 | Bold |
| «Коллекции» sheet title | Serif | 22 | Bold |
| «Темы и настройки» panel title | Sans | 22 | Bold |
| Sheet navbar | Sans | 17 | Semibold |
| Settings group title | Sans | 20 | Bold |
| Body/menu/list row | Sans | 17 | Regular |
| Card book title | Sans | 17 | Semibold |
| Section subtitle | Sans | 17 | Regular, secondary |
| Cover caption | Sans | 15 | Regular, secondary |
| «НОВАЯ» badge | Sans | 13 | Semibold, uppercase |
| Slider label | Sans | 15 | Regular, uppercase, secondary |
| Tab label | Sans | 11–12 | Medium |
| Reader header/footer | Sans | 15 | Semibold, secondary |

## Measured colors
| Token | Light | Dark |
|---|---|---|
| Screen background | `#FFFFFF` | `#000000` |
| Section band, top → bottom | `#FFFFFF` → `#F1F1F1` | `#1E1E1E` → `#010101` |
| Grouped settings background | `#F2F1F7` | Match dark sheet |
| Sheet | White | `#1C1C1E`; contents `#242424` |
| Grouped card | `#FFFFFF` | `#2C2C2E` |
| Primary text | `#000000` | `#FFFFFF` |
| Secondary text | `#7E7E7E`–`#939393` | `#A5A5A5`–`#A7A7A9` |
| Uppercase caption | `#787878` | Secondary text |
| Disabled text | `#B6B6B6` | Secondary text |
| Preview panel | `#EDEDED` | Follow appearance |
| Selected contents row | Follow appearance | `#414143` |
| Contents row text | Follow appearance | `#CACACA` |
| Continue-reading capsule | Follow appearance | `#343434` |
| «Править» capsule | Follow appearance | `#252527` |

- Use `#2FC557` for enabled switches, `#009BE8` for reading goals, and `#FF3B30` for bookmarks.
- Use dark «НОВАЯ» badge background/text `#1C416E` / `#D8F5FF`; outline selections in text color.
- Give each home/catalog section its own vertical gradient band; keep the first dark home band black.
  Let the band boundary separate sections without divider lines.

## Shapes, glass and icons
| Element | Measured dp | Shape/radius, dp |
|---|---|---|
| Round glass control | Diameter 43–47 | Circle |
| Full-width capsule button | Height 45–49 | Capsule |
| Reader menu row | 265 × 45 | Capsule |
| «Продолжить» card | 242 × 78 | 10 |
| Reading-theme tile | 104 × 92 | 16 |
| Sheet card | Width minus 16 per side | 20 |
| Sheet | Top near 61, below status bar | Top corners 23 |
| Floating themes panel | Screen inset 10 | 17 |
| Pull-down menu | Width 242–243 | 19 |
| Book cover | Preserve aspect ratio | 2–3, soft shadow |

- Use glass for floating tabs, search, round buttons, reader menus and pull-downs; tint/blur the content below.
- Use `RenderEffect`/the existing glass implementation on supported Android; avoid opaque substitutes
  when a floating control should show its backdrop.
- Dim the page about 40% under the themes panel (white becomes about `#999999`); leave reader menu undimmed.
- Use filled tab icons and outlined menu/list icons; approximate size 26 dp in tabs, 24 dp in rows.
- Structure pull-downs as a separate checkmark column, icon, 17 sp text, gray group headings and dividers.
- Use grouped cards with 48–50 dp rows and dividers starting at the text; sheets close with round ×,
  or × on the left and ✓ on the right when changes need applying.
- Shade every real/generated cover with a narrow spine shadow/highlight, light edges and soft shadow;
  keep one shared `BookCover` treatment.

## App navigation
- Keep «Главная · Библиотека · Каталог» in a floating glass capsule with a separate round search control.
- Use a gray capsule behind the selected tab; keep its icon/text the same monochrome color as other tabs.
- Keep the tab bar unchanged while scrolling; let content scroll underneath and blur through it.
- Reference bar: height 58 dp, left/bottom inset 21 dp; search diameter 59 dp, gap 9 dp, right inset 21 dp.
  Adapt width for the app's three tabs and available screen space.
- Place Large Title at x=32 dp, baseline near y=95 dp; right controls are about 43 dp with 12–17 dp gaps
  and 16–19 dp right inset.
- In «Библиотека», use «≡» for «Коллекции» and «•••» for view/sort/actions.
- Morph the search control/tab area into a bottom search field with a library/back circle; keep search
  results and recent requests in `.agents/design/home-library.md`.