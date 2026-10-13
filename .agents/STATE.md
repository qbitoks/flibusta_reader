---
status: active
---
# STATE

Volatile status only. Rewrite it (don't append) and keep it short. Stable facts and commands live in
root `AGENTS.md` → Project; history lives in git and `.agents/DECISIONS.md`.
Branch- or task-specific progress goes to the PR or issue, not here: parallel branches would conflict.

## Current focus
- 0.3.x: Prototype codebase merged in as the app; OPDS catalog is the «Каталог» tab.
  No data migration from 0.2.x (user decision). Builds, JVM tests and lint pass.
- Catalog reworked (2026-10-12): home in the «Главная» style, book+author search, book sheet, download
  manager with Range resume (Room v2). Checked on an emulator against flibusta.is: browse, search, EPUB and
  FB2 download, resume after network loss, open in reader. FB2 import on Android was broken (SAX feature), fixed.
- Pick work from the user's task; the lists below are not a backlog to implement on your own.

## Next steps
- v0.3.2 (app-wide + per-book appearance) published as pre-release; checked on an emulator only.
- Rewrite ADB scripts (`smoke-test`, `reader-regression`, `reader-background-regression`) for the new UI;
  CI uses only the launch check (`scripts/ci-emulator.ps1`) meanwhile.
- Prototype roadmap leftovers: `.agents/design/prototype-roadmap.md`; baseline profile not generated.
- Collect dependency licenses before distributing beyond testing.

## Known issues / verification gaps
- Wrapper download of Gradle 9.8 was flaky on this machine (connection resets).
- The `ReaderPhone` emulator's DNS proxy fails behind the host VPN; a fresh AVD with `-dns-server` works.
- No download notification: progress is visible only in the app («Загрузки»).
