---
status: active
---
# STATE

Volatile status only. Rewrite it (don't append) and keep it short. Stable facts and commands live in
root `AGENTS.md` → Project; history lives in git and `.agents/DECISIONS.md`.
Branch- or task-specific progress goes to the PR or issue, not here: parallel branches would conflict.

## Current focus
- Version 0.2.1 prototype. Review findings R01–R09 are fixed in code (details: `.agents/archive/code-review-2026-10-12.md`).
- Pick work from the user's task; the lists below are not a backlog to implement on your own.

## Next steps (product gaps)
- Background download service with resume; real covers; multiple bookmarks (new schema + UX).
- No MOBI, PDF, DRM, accounts, sync, user export/backup, corrupt-index repair.
- FB2 in one XHTML: big books need memory/perf checks; tables, SVG, popup notes, external links unsupported.
- EPUB fixed layout limits typography; no exact "pages left in chapter".
- Library cleanup and reads run on the UI thread (`BookRepository` in onCreate/onResume).
- Collect all dependency licenses before distributing beyond testing (only Readium's is kept).

## Known issues / verification gaps
- No physical phones, vivo firmware or Android 16 checks; emulator runs were API 35. Min API 34 needs
  its own smoke when platform/lifecycle behaviour changes.
- Unverified: network search, pagination and history after process death; killing a running import;
  corrupt index; background file error on open; deep FB2 import on Android; live catalogs of all sources.
- CI push/PR runs no ADB regressions (only manual runs and tags).
