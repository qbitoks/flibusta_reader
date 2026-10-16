# Read aloud (core/speech)

## Entry points
- `core/speech/src/main/kotlin/app/folio/core/speech/SpeechText.kt` — `Book.utterancesFrom`: sentence phrases with exact `Position` ranges.
- `SpeechEngine` — backend interface; `SystemTtsEngine` (Android `TextToSpeech`) is the only one now, bound in `SpeechModule`.
- `SpeechController` — app-wide singleton: play/pause/skip/rate/stop, audio focus, throttled position saves.
- `SpeechService` — `mediaPlayback` foreground service: media notification, `MediaSession`, headset-unplug pause.
- Reader side: `feature/reader/.../ReaderSpeech.kt` (mini-player, phrase `Mark`), `ReaderViewModel.followSpeech`.
- Canonical test: `core/speech/src/test/kotlin/app/folio/core/speech/SpeechTextTest.kt`.

## Rules
- Phrases are layout-independent positions, never page indices; the reader turns pages by `goTo(start, record = false)`.
- New engines implement `SpeechEngine`: `speak` suspends until spoken and must stop the voice on cancellation.
- The service lives exactly while `SpeechState.bookId` is set; `stop()` releases the engine and audio focus.
- Rate is kept in `shared_prefs/speech.xml` (`rate`); see `.agents/rules/storage.md`.
