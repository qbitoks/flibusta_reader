# Reader background regression implementation plan

> Completed historical plan. Current commands: [development.md](../../development.md); observed results and limits: [reader-ui-verification.md](../../reader-ui-verification.md).

**Goal:** Reproduce completion of EPUB preparation while ReaderActivity is stopped and verify safe fragment attachment on resume.

**Architecture:** A debug source-set checkpoint suspends immediately before the existing `lifecycle.withResumed` block. A release source-set implementation is a no-op. An ADB script owns checkpoint files in the app's private directory, checks Activity state and returns through Recents. No exported component or network dependency is added.

**Stack:** Kotlin coroutines, Android lifecycle, PowerShell, ADB, existing demo EPUB.

Work is executed inline in the existing checkout at the user's request to continue.

- [x] Create `scripts/reader-background-regression.ps1`: install debug APK, open demo, create hold file, reopen demo, await reached marker, press Home, confirm STOPPED, remove hold, await released marker, assert no attached marker while stopped, resume via Recents, verify attachment, actual WebView text and working panels. Remove all checkpoint files in `finally`, including on failure.
- [x] Run against the existing APK: fail because the checkpoint is not reached. Do not interpret this as an application defect.
- [x] Add debug/release `ReaderOpeningCheckpoint.kt` with `suspend fun beforeAttach(context: Context)` and `fun afterAttach(context: Context)`. Debug uses cancellable polling with a 60-second timeout and marker files. Release methods do nothing. Call immediately before and after the guarded fragment transaction in `ReaderActivity.kt`.
- [x] Build JVM tests, debug APK and lint through `scripts/build.ps1`; run the background scenario to completion.
- [x] Temporarily bypass the resumed transaction guard, rebuild debug APK and run the same scenario: require failure when preparation completes after state is saved. Restore the guard even if the check fails.
- [x] Rebuild the restored implementation; repeat the background scenario and existing regression script. Compile release to verify its no-op source-set implementation.
- [x] Document observed results and remaining gaps in `docs/reader-ui-verification.md`, describe test invocation in README, inspect diff and commit the completed change.
