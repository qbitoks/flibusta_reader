# Read aloud (core/speech)

## Entry points
- `core/speech/src/main/kotlin/app/folio/core/speech/SpeechText.kt` — `Book.utterancesFrom`: sentence phrases with exact `Position` ranges.
- `SpeechEngine` — backend interface, bound to `SystemTtsEngine` (Android `TextToSpeech`) in `SpeechModule`.
- `SpeechVoices` — engine package and per-language voice name in prefs; lists installed engines and an engine's voices.
- Offline voices are separate engine apps (Folio Piper, Folio Silero) from `github.com/qbitoks/folio-modules`; Folio
  installs them through `core/data/.../modules/ModuleInstaller.kt`. Engine code, models and voice downloads live there.
- Reader voice UI: `feature/reader/.../VoiceSheet.kt` + `VoiceGroups.kt`, state in `VoiceViewModel`: the voice reading
  now, rate/pitch sliders, sleep timer, «Избранное», then installed engines by name (folded except the reading one; a
  Folio engine carries «Скачать голоса…» / «Обновить»), Folio engines not installed last. No separate «Модули Folio»
  section, no engine picker, no «Удалить» (user decisions). A tap picks engine + voice, long press stars it.
- `TtsVoice` is one speaker: its `-local` and `-network` voices merge into one row; the chosen one shows
  «На устройстве | Онлайн». `VoicePreview` says «▶» samples through its own `TextToSpeech`.
- `SpeechController` — app-wide singleton: play/pause/skip/rate/pitch/stop, sleep timer (minutes or chapter end, not
  saved), audio focus, throttled position saves.
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
- A chosen online voice reads with its on-device twin (same name without `-network`) while the network is not
  validated, or for a minute after it failed mid-phrase (the phrase is said again); `SpeechVoices.offline` tells the UI.
  A voice change flushes the phrase queued ahead.
- Voice names of Folio engines are `piper-<id>` / `silero-ru_<name>` (`voiceTitle` shows the speaker name).
- Rate, engine and voices are kept in `shared_prefs/speech.xml`; see `.agents/rules/storage.md`.
