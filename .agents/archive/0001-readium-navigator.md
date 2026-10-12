# 0001: Readium as the EPUB navigator

Date: 2026-10-12 (retrospective) · Status: accepted

## Context
The app needs EPUB navigation, TOC, reflow, text settings and a restorable position.

## Decision
Use pinned Readium Kotlin Toolkit 3.0.0: Readium opens the Publication, `EpubNavigatorFragment` shows it,
the app draws Compose panels over it. Integration and lifecycle stay in `app/`; pure math in `core/`.

## Alternatives rejected
- Own renderer — would reimplement pagination, TOC, settings and positions.

## Consequences
- Must handle the fragment factory, Activity states, insets and async re-pagination.
- Positions are stored as Readium Locator JSON.
- A Readium upgrade must re-verify old Locators, viewport, typography and background opening.
  Don't infer 3.0.0 API from newer docs.
- License BSD-3-Clause, copy in `third-party/readium-LICENSE`; full dependency licenses are still to collect.
