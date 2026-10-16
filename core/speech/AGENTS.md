# Read aloud (core/speech)

## Entry points
- `core/speech/src/main/kotlin/app/folio/core/speech/SpeechText.kt` — `Book.utterancesFrom`: sentence phrases with exact `Position` ranges.
- `SpeechEngine` — backend interface; `SelectedEngine` (bound in `SpeechModule`) routes each phrase to `SileroEngine`
  (onnxruntime, Russian only, model installed), `PiperEngine` (sherpa-onnx, a voice for the language installed)
  or `SystemTtsEngine` (Android `TextToSpeech`). Offline engines play through `PcmPlayer`.
- `SpeechVoices` — engine/voice choice in prefs; `VoiceStore` — `OfflineVoice` downloads/unpack/delete;
  `PiperCatalog` — offered Piper voices; `SileroVoices.kt` — `SileroModel`, its 29 `ru_*` speakers, text → token ids.
- Silero model build: `scripts/silero/export_onnx.py` (eager re-implementation → ONNX → int8). The `.onnx` is a
  release asset of the Folio repo (tag `voices-silero-v5`); a new build needs a new URL and `SileroModel.bytes`.
- Reader voice UI: `feature/reader/.../VoiceSheet.kt`.
- `SpeechController` — app-wide singleton: play/pause/skip/rate/stop, audio focus, throttled position saves.
- `SpeechService` — `mediaPlayback` foreground service: media notification, `MediaSession`, headset-unplug pause.
- Reader side: `feature/reader/.../ReaderSpeech.kt` (player shown with the controls, phrase `Mark`), `ReaderViewModel.followSpeech`:
  pages turn with the voice only while the reader is on the spoken phrase; paged away, the player offers «Читать отсюда».
- Canonical tests: `core/speech/src/test/kotlin/app/folio/core/speech/SpeechTextTest.kt`, `SileroTextTest.kt`.

## Rules
- Phrases are layout-independent positions, never page indices; the reader turns pages by `goTo(start, record = false)`.
- New engines implement `SpeechEngine`: `speak` suspends until spoken and must stop the voice on cancellation.
- The service lives exactly while `SpeechState.bookId` is set; `stop()` releases the engine and audio focus.
- `prepare` is a look-ahead hint (the controller passes the next phrase); Piper synthesizes it while the current one plays.
- sherpa-onnx `OfflineTts` is not thread-safe: all its calls go through `PiperEngine`'s single-thread dispatcher.
- Silero reads only its symbol table: `sileroText` spells numbers (`RussianNumbers.kt`) and drops Latin and other
  characters; phrases over `SILERO_MAX_CHARS` are split (the model's position table holds ~60 s of audio).
- One `libonnxruntime.so` ships: sherpa-onnx's (ORT 1.28.2). The `ortJni` task in `build.gradle.kts` takes only
  `classes.jar` + `libonnxruntime4j_jni.so` from the onnxruntime-android AAR (1.28.0, no 1.28.2 on Maven) and re-tags
  the JNI's required symbol version `VERS_1.28.0` → `VERS_1.28.2`; without it, playback dies with
  `UnsatisfiedLinkError: OrtGetApiBase`. Bumping sherpa-onnx: update `onnxruntime`/`onnxruntimeSherpa` together.
- `SelectedEngine` drops an offline engine that throws (link error, bad model) and reads on with the system voice.
- R8: `consumer-rules.pro` keeps `com.k2fsa.sherpa.onnx.**` and `ai.onnxruntime.**` (JNI reads fields by name). Native libs: arm64-v8a + x86_64 only.
- `scripts/package-release.ps1` checks the APK native-code is exactly arm64-v8a + x86_64; change both together.
- Rate is kept in `shared_prefs/speech.xml` (`rate`); see `.agents/rules/storage.md`.
