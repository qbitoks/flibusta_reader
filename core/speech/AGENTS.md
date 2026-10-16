# Read aloud (core/speech)

## Entry points
- `core/speech/src/main/kotlin/app/folio/core/speech/SpeechText.kt` — `Book.utterancesFrom`: sentence phrases with exact `Position` ranges.
- `SpeechEngine` — backend interface; `SelectedEngine` (bound in `SpeechModule`) routes each phrase to `PiperEngine`
  (sherpa-onnx, when chosen and a voice for the language is installed) or `SystemTtsEngine` (Android `TextToSpeech`).
- `SpeechVoices` — engine/voice choice in prefs; `VoiceStore` — Piper downloads/unpack/delete; `PiperCatalog` — offered voices.
- Reader voice UI: `feature/reader/.../VoiceSheet.kt`.
- `SpeechController` — app-wide singleton: play/pause/skip/rate/stop, audio focus, throttled position saves.
- `SpeechService` — `mediaPlayback` foreground service: media notification, `MediaSession`, headset-unplug pause.
- Reader side: `feature/reader/.../ReaderSpeech.kt` (player shown with the controls, phrase `Mark`), `ReaderViewModel.followSpeech`:
  pages turn with the voice only while the reader is on the spoken phrase; paged away, the player offers «Читать отсюда».
- Canonical test: `core/speech/src/test/kotlin/app/folio/core/speech/SpeechTextTest.kt`.

## Rules
- Phrases are layout-independent positions, never page indices; the reader turns pages by `goTo(start, record = false)`.
- New engines implement `SpeechEngine`: `speak` suspends until spoken and must stop the voice on cancellation.
- The service lives exactly while `SpeechState.bookId` is set; `stop()` releases the engine and audio focus.
- `prepare` is a look-ahead hint (the controller passes the next phrase); Piper synthesizes it while the current one plays.
- sherpa-onnx `OfflineTts` is not thread-safe: all its calls go through `PiperEngine`'s single-thread dispatcher.
- R8: `consumer-rules.pro` keeps `com.k2fsa.sherpa.onnx.**` (JNI reads fields by name). Native libs: arm64-v8a + x86_64 only.
- `scripts/package-release.ps1` checks the APK native-code is exactly arm64-v8a + x86_64; change both together.
- Rate is kept in `shared_prefs/speech.xml` (`rate`); see `.agents/rules/storage.md`.
