---
status: active
---
# STATE

## Current focus
- Work from the user's task; follow-ups below need an explicit task, not autonomous implementation.
- 0.3.x–0.5.x (now 0.5.0) uses the prototype engine; OPDS is the «Каталог» tab. No data migration from 0.2.x (user decision).
- Checked on an emulator: live OPDS browse/search, EPUB/FB2 downloads, Range resume; upgrade/restart, presets,
  menu palettes and guide landings in Slide/Curl/Fade.

## Phone verification before distribution
- «Настройки» (gear on «Главная»): appearance, «Настройки чтения», update check/up-to-date/offline, project link.
- Read aloud (stage 1): «Слушать» in the navigation panel, mini-player, phrase band, page following in Slide/Scroll,
  notification/lock-screen/headset controls, pause on headphone unplug, no-voice hint.
- Voice sheet (0.7.1): samples, rate/pitch, sleep timer; Google «Онлайн-голос» switch + offline fallback unchecked.
- Read aloud via engine modules (0.6.0): install Folio Piper/Silero from «Голос» (unknown-apps permission, system
  installer confirm), voice download in the engine, reading with the chosen voice, Folio self-update card on Home.
  Emulator-checked: engine detection, Piper voice download, gapless reading with Irina; the in-app install was not
  run there (the unknown-apps permission was left to the user).
- Reader settings: Basic/Advanced tabs, palette cards, Advanced-only reset, default/book reset, preset paragraph
  gaps and seam leading, cyclic interval/margin cards and percentage margins, footer capsules, 36 dp Scroll lines.
- Reader controls: themed scrubber, five-page history arrows, position sheet, bottom search, corner bookmark.
- Import: text files (txt/conf/no extension, windows-1251, open-with, folder scan); real Flibusta MOBI/RTF/HTML/TXT
  downloads and a DOCX (parsers are tested only on synthetic files).
- Catalogs: migration of old sources, switching, Open Library web links, Gutenberg search, the unified «Каталоги»
  list (stars, switcher menu, long-press edit, add by address, preset reset).

## Next steps
- Add secret `ANDROID_DEBUG_KEYSTORE_BASE64` (same preview keystore as Folio) to `qbitoks/folio-modules`, so engine
  tags build in CI; 1.0.0 engines were published by hand from a local build signed with that key.
- Cut Folio v0.6.0: 0.5.0 users get the update card only from 0.6.0 on (0.5.0 has no updater).
- Generate the baseline profile on a disposable emulator; the generator uninstalls the app.
- Replace archived Readium regressions with checks for the current UI; CI uses `scripts/ci-emulator.ps1`.
- Potential reader follow-up: add the roadmap's scroll scrubber with chapter/page bubble.
- Collect dependency licenses before distributing beyond testing.

## Known issues / verification gaps
- The emulator's DNS proxy fails behind the host VPN; a fresh AVD with `-dns-server` works.
- No download notification: progress is visible only in the app («Загрузки»).
