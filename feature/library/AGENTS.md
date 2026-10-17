# Home and library

## Entry points
- Read `.agents/design/home-library.md` before changing Home, shelves, library or collections visuals.
- Use `feature/library/src/main/kotlin/app/folio/feature/library/LibraryViewModel.kt` for derived shelves, sorting and mutations.
- Use `feature/library/src/main/kotlin/app/folio/feature/library/LibraryScreen.kt` as the canonical screen/host-callback example.
- Use `feature/library/src/main/kotlin/app/folio/feature/library/HomeScreen.kt` for continue-reading and cover shelves.
- Use `feature/library/src/main/kotlin/app/folio/feature/library/Common.kt` for context-sensitive book menus and confirmation dialogs.
- Use `feature/library/src/main/kotlin/app/folio/feature/library/CollectionsSheet.kt` for built-in/user collection editing.
- Use `feature/library/src/main/kotlin/app/folio/feature/library/SearchScreen.kt` for local search and recent queries.
- The gear on «Главная» opens «Настройки» (`feature/settings`); app appearance and «Настройки чтения» live there, not in «•••».

## Invariants
- Keep picker and navigation actions in host callbacks; import and scan through the ViewModel repositories.
- Keep `Shelf.key`/`fromKey` compatible with navigation routes; want-to-read/finished shelves use flags, user shelves use collection IDs.
- Derive Home sections in the ViewModel: opened unfinished visible books continue; hidden/finished opened books go to previous.
- Preserve search across display title, original title and authors, including the existing Cyrillic normalization.
- Store selection by book ID and lazy-item keys; drag reordering switches to persisted manual sorting.
- Route shared book actions through `BookMenu` and `MenuContext`; preserve each shelf's removal semantics.
- Preserve delete confirmation; remove book appearance and chrome style overrides when deleting a book.
- Pair `bookTransition` source keys with `BookTransition.open` when adding an entry point to the reader.
- Add text to `feature/library/src/main/res/values/strings.xml` and `feature/library/src/main/res/values-ru/strings.xml`.
