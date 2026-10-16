---
status: active
---
# STATE

## Current focus
- Work from the user's task; follow-ups below need an explicit task, not autonomous implementation.
- 0.3.x–0.4.x (now 0.4.0) uses the prototype engine; OPDS is the «Каталог» tab. No data migration from 0.2.x (user decision).
- Checked on an emulator: live OPDS browse/search, EPUB/FB2 downloads, Range resume; upgrade/restart, presets,
  menu palettes and guide landings in Slide/Curl/Fade.

## Phone verification before distribution
- Read aloud (stage 1): «Слушать» in the navigation panel, mini-player, phrase band, page following in Slide/Scroll,
  notification/lock-screen/headset controls, pause on headphone unplug, no-voice hint.
- Reader settings: Basic/Advanced tabs, palette cards, Advanced-only reset, default/book reset, preset paragraph
  gaps and seam leading, cyclic interval/margin cards and percentage margins, footer capsules, 36 dp Scroll lines.
- Reader controls: themed scrubber, five-page history arrows, position sheet, bottom search, corner bookmark.
- Import: text files (txt/conf/no extension, windows-1251, open-with, folder scan); real Flibusta MOBI/RTF/HTML/TXT
  downloads and a DOCX (parsers are tested only on synthetic files).
- Catalogs: migration of old sources, switching, Open Library web links, Gutenberg search, the unified «Каталоги»
  list (stars, switcher menu, long-press edit, add by address, preset reset).

## Next steps
- Read aloud stage 2: sherpa-onnx + Piper voices behind `SpeechEngine` (models downloaded on demand); stage 3 Silero only after an on-device probe (CC BY-NC).
- Generate the baseline profile on a disposable emulator; the generator uninstalls the app.
- Replace archived Readium regressions with checks for the current UI; CI uses `scripts/ci-emulator.ps1`.
- Potential reader follow-up: add the roadmap's scroll scrubber with chapter/page bubble.
- Collect dependency licenses before distributing beyond testing.

## Known issues / verification gaps
- Wrapper download of Gradle 9.8 was flaky on this machine (connection resets).
- The emulator's DNS proxy fails behind the host VPN; a fresh AVD with `-dns-server` works.
- No download notification: progress is visible only in the app («Загрузки»).
- The archived prototype roadmap is historical: `.agents/archive/prototype-roadmap.md`.
