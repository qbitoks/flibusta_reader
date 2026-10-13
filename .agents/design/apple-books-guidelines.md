# Apple Books design reference

- Use Apple Books iOS 26 as the visual and interaction reference; apply user decisions from
  `.agents/DECISIONS.md` before copying a reference detail.
- Treat the topic files as design intentions, not a claim that every detail is implemented.
- Open only the topic needed for the task:
  - `.agents/design/design-system.md` — typography, colors, glass, shapes, app navigation.
  - `.agents/design/home-library.md` — shelves, lists, collections, book menus, library search.
  - `.agents/design/reader.md` — page, chrome, gestures, themes, navigation, annotations.
- Keep prototype milestones and historical checks in `.agents/archive/prototype-roadmap.md`;
  use `.agents/STATE.md` for verified remaining work, not the archived checklist.

## Measurement and references
- Interpret measured pt as Android dp; use sp for text and respect font scaling.
- Measurements came from a 1177 × 2560 px iPhone image, about 392 × 853 pt at 3×, with ±2 pt precision.
- Treat glass colors as observed composites; tint depends on the underlying content.
- Reference screenshots are optional, contain personal data, and are absent from git and other machines.
  A local copy may exist in `.agents/tmp/design-screenshots/`; do not require it for builds or checks.
- If available, use #1–18 for screens, #19–60 for interaction details, #61–63 for animations,
  #64–65 for cover lighting, #69–71 for slide turning, and #72–78 for highlights and notes.

## Android scope
- Keep the app's own name, icons and licensed fonts; use OFL substitutes with verified Cyrillic coverage
  or system fonts instead of shipping Apple fonts or SF Symbols.
- Omit Apple store recommendations, account/avatar, iCloud status, audiobooks, store excerpts and Live Text.
- Keep «Каталог» and its «Загрузки» as OPDS features; the omitted Apple store/cloud UI does not exclude them.
- Keep PDF, reading goals and year summaries deferred; do not revive them from the archived roadmap.
- Use window brightness and activity orientation controls; offer «Светлое / Тёмное / Как на устройстве»
  without an ambient-light appearance mode.