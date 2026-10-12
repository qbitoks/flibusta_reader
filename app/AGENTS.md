# app

Android entry: Application (Hilt, WorkManager), `MainActivity` (edge-to-edge,
"Open with"/"Share" import), navigation. Screens live in `feature/*`, data in `core/data`.

## Entry points
- `app/src/main/kotlin/net/flibustareader/FlibustaApp.kt` — Hilt app; schedules folder scan
- `app/src/main/kotlin/net/flibustareader/FlibustaNavHost.kt` — tabs Главная/Библиотека/Каталог, reader route, cover transition
- `app/src/main/kotlin/net/flibustareader/MainActivity.kt` — intents → import → open book

## Conventions
- Tabs stay composed (`TabStack`); add a tab by adding a child there and a `TabSpec`.

## Commands
- `./gradlew.bat :app:assembleDebug :app:lintDebug --console=plain`
