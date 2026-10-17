# Settings

«Настройки», a pushed screen opened by the gear on «Главная» (route in `app/.../FolioNavHost.kt`).

## Entry points
- `feature/settings/src/main/kotlin/app/folio/feature/settings/SettingsScreen.kt` — `SettingsViewModel` (appearance via
  `SettingsRepository`, update check via `ModuleInstaller.checkApp`) and the grouped screen.

## Invariants
- App-wide settings and app info go here; actions tied to one screen (library view, folder, catalogs) stay there.
- Sheets of other features open through host callbacks (`onReadingSettings`); no dependency on other `feature/*`.
- Use `GroupHeader`/`GroupedCard`/`GroupedRow` on `groupedBackground`.
- Add text to `values/strings.xml` and `values-ru/strings.xml`.
- «Лицензии» (`Licenses.kt`): a new shipped library or font needs an `OpenSourceList` entry and its text in
  `assets/licenses/`; `OpenSourceListTest` fails otherwise.
