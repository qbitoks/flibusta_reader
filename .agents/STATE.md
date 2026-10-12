---
status: active
---
# STATE

Volatile status only. Rewrite it (don't append) and keep it short. Stable facts and commands live in
root `AGENTS.md` → Project; history lives in git and `.agents/DECISIONS.md`.
Branch- or task-specific progress goes to the PR or issue, not here: parallel branches would conflict.

## Current focus
- 0.3.0: Prototype codebase merged in as the app; OPDS catalog is the «Каталог» tab.
  No data migration from 0.2.x (user decision). Builds, JVM tests and lint pass; not yet checked on a device.
- Pick work from the user's task; the lists below are not a backlog to implement on your own.

## Next steps
- Check on a device: catalog search/browse/download → opens in reader; Room DB fresh install.
- Rewrite ADB scripts (`smoke-test`, `reader-regression`, `reader-background-regression`) for the new UI;
  CI uses only the launch check (`scripts/ci-emulator.ps1`) meanwhile.
- Prototype roadmap leftovers: `.agents/design/prototype-roadmap.md`; baseline profile not generated.
- Collect dependency licenses before distributing beyond testing.

## Known issues / verification gaps
- Wrapper download of Gradle 9.8 was flaky on this machine (connection resets).
