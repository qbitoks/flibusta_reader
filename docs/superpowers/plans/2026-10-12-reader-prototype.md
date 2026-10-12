# Reader Prototype Implementation Plan

**Goal:** Installable Android 14+ prototype for OPDS search, local FB2/EPUB library and Readium reading.

**Architecture:** Pure JVM core for OPDS and FB2 conversion; Android Compose shell; Readium navigator in a dedicated activity. App-private files and persisted locators.

**Tech Stack:** Java 17, Gradle 8.6, AGP 8.4.2, Kotlin 1.9.24, Compose, Readium 3.0.0, JUnit 4.

Execution is inline in this chat, authorized by the user's request to start the prototype. Empty workspace: no existing checkout requires isolation.

## Tasks
- [x] Bootstrap `settings.gradle.kts`, root/module builds, wrapper, manifest; verify `gradlew.bat tasks`.
- [x] Write `core/src/test/kotlin/.../OpdsParserTest.kt`: fixture containing acquisition, navigation and next links. Run `:core:test` and observe missing parser, implement `OpdsParser.kt`, rerun.
- [x] Write `Fb2ConverterTest.kt`: inspect EPUB ZIP mimetype, package, XHTML, notes and image references; reject DTD and non-FB2 XML. Observe failure, implement `Fb2Converter.kt`, rerun.
- [x] Implement `BookRepository.kt`: HTTPS catalog, size-bounded downloads/import, JSON library and synthetic demo FB2. Reject unsupported formats and partial downloads.
- [x] Implement `MainActivity.kt`: responsive library, search type, navigation, card and download, import and editable HTTPS sources. Check compile.
- [x] Implement `ReaderActivity.kt`: Readium open, restore/save locator, scroll/page settings, themes/font size and TOC. Check compile.
- [x] Run `:core:test :app:assembleDebug :app:lintDebug`; inspect reports. Exercise emulator if available, document what was verified in `README.md` and `docs/prototype-verification.md`, copy APK to `artifacts/`.

