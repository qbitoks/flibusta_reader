# Read aloud (core/speech)

## Entry points
- `core/speech/src/main/kotlin/app/folio/core/speech/SpeechText.kt` — `Book.utterancesFrom`: sentence phrases with exact `Position` ranges.
- `SpeechEngine` — backend interface, bound to `SystemTtsEngine` (Android `TextToSpeech`) in `SpeechModule`.
- `SpeechVoices` — engine package and per-language voice name in prefs; lists installed engines and an engine's voices.
- Offline voices are separate engine apps (Folio Piper, Folio Silero) from `github.com/qbitoks/folio-modules`; Folio
  installs them through `core/data/.../modules/ModuleInstaller.kt`. Engine code, models and voice downloads live there.
- Reader voice UI: `feature/reader/.../VoiceSheet.kt` (engine, voice, «Модули озвучки Folio»).
- `SpeechController` — app-wide singleton: play/pause/skip/rate/stop, audio focus, throttled position saves.
- `SpeechService` — `mediaPlayback` foreground service: media notification, `MediaSession`, headset-unplug pause.
- Reader side: `feature/reader/.../ReaderSpeech.kt` (player shown with the controls, phrase `Mark`), `ReaderViewModel.followSpeech`:
  pages turn with the voice only while the reader is on the spoken phrase; paged away, the player offers «Читать отсюда».
- Canonical test: `core/speech/src/test/kotlin/app/folio/core/speech/SpeechTextTest.kt`.

## Rules
- Phrases are layout-independent positions, never page indices; the reader turns pages by `goTo(start, record = false)`.
- `speak` suspends until spoken and must stop the voice on cancellation.
- The service lives exactly while `SpeechState.bookId` is set; `stop()` releases the engine and audio focus.
- `prepare` names the next phrase; `SystemTtsEngine` queues it (`QUEUE_ADD`) behind the current one, so the engine
  synthesizes it while this one sounds, and the next `speak` of the same text only waits for it.
- An engine or voice change recreates/re-applies `TextToSpeech` on the next phrase (`SpeechController.voiceChanged`).
- Voice names of Folio engines are `piper-<id>` / `silero-ru_<name>` (`voiceTitle` shows the speaker name).
- Rate, engine and voices are kept in `shared_prefs/speech.xml`; see `.agents/rules/storage.md`.
