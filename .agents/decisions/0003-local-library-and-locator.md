# 0003: Local library index and Readium Locator positions

Date: 2026-10-12 (retrospective) · Status: accepted

## Context
No backend, accounts or sync. Books and reading positions must survive APK upgrades and restarts.

## Decision
EPUBs in app-private files, index in `files/books/library.json`, settings and positions in
SharedPreferences `reader`. Positions/bookmarks are Readium Locator JSON, not page numbers or percent.
Prepare in `.part`, publish the EPUB before writing the index, clean leftovers at process start.
Schema and write order: `.agents/rules/storage.md`.

## Alternatives rejected
- Page number or percent only — breaks on re-pagination and typography changes.

## Consequences
- Simple, but no single transaction and no sync; SharedPreferences `apply` isn't crash-proof.
- No auto-repair of a corrupt index and no user export yet.
- Room, new paths, multiple bookmarks or sync need a new decision and a migration.
