---
status: active
---
# STATE

## Current focus
- Work from the user's task; follow-ups below need an explicit task, not autonomous implementation.
- 0.3.x uses the prototype engine; OPDS is the «Каталог» tab. No data migration from 0.2.x (user decision).
- Catalog browse/search, EPUB/FB2 downloads and Range resume were checked against a live OPDS server on an emulator.
- ReaderPhone checks cover upgrade/restart, presets, menu palettes and guide landings in Slide/Curl/Fade.
- Finish on-device default/book reset checks and physical-phone verification before distribution.
- 0.3.10 includes preset paragraph spacing/indent and restored seam leading; verify on a phone before distribution.

## Next steps
- Generate the baseline profile on a disposable emulator; the generator uninstalls the app.
- Replace archived Readium regressions with checks for the current UI; CI uses `scripts/ci-emulator.ps1`.
- Potential reader follow-up: add the roadmap's scroll scrubber with chapter/page bubble.
- Collect dependency licenses before distributing beyond testing.

## Known issues / verification gaps
- Wrapper download of Gradle 9.8 was flaky on this machine (connection resets).
- The emulator's DNS proxy fails behind the host VPN; a fresh AVD with `-dns-server` works.
- No download notification: progress is visible only in the app («Загрузки»).
- Paragraph seam leading is restored locally; verify preset gaps on a physical phone before distribution.
- The archived prototype roadmap is historical: `.agents/archive/prototype-roadmap.md`.
