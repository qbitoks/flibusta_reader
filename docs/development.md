# Разработка и проверка

Команды выполняются из корня репозитория. Используй Gradle wrapper, не системный Gradle. Персональные skills, плагины и история чатов не нужны.

## Подключение любого агента

Общие правила находятся в [AGENTS.md](../AGENTS.md). В репозитории есть короткие адаптеры для Claude Code (`CLAUDE.md`), Gemini CLI (`GEMINI.md`), Cursor (`.cursor/rules/project.mdc`) и GitHub Copilot (`.github/copilot-instructions.md`). Поддержка автоматического чтения зависит от версии и настроек инструмента; наличие файла не гарантирует его загрузку.

Для любого другого агента либо при отключённой автоматической загрузке начни с такого запроса:

```text
Прочитай AGENTS.md в корне репозитория и следуй ему в рамках своей системы инструкций.
Найди нужные справочники через docs/README.md и прочитай только относящиеся к задаче.
Проверь правила по текущему коду. Затем выполни задачу: <описание и критерии приёмки>.
В результате перечисли выполненные проверки и оставшиеся ограничения.
```

Чтобы проверить подключение, попроси нового агента назвать источник правил, путь к коду для заданного сценария и подходящую команду проверки. Это проверка загрузки контекста, а не требование читать все документы перед любой правкой.

Официальные форматы: [Claude imports](https://code.claude.com/docs/en/memory), [Gemini context](https://geminicli.com/docs/cli/gemini-md/), [Cursor rules](https://cursor.com/docs/rules), [Copilot repository instructions](https://docs.github.com/en/copilot/how-tos/configure-custom-instructions/add-repository-instructions). Общие правила меняются в AGENTS.md; адаптеры только направляют к нему.

## Закреплённый toolchain

| Компонент | Версия / место |
| --- | --- |
| JDK | 17; JAVA_HOME указывает на JDK, не JRE |
| Gradle | 8.6; `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.4.2; корневой `build.gradle.kts` |
| Kotlin | 1.9.24; корневой `build.gradle.kts` |
| Android SDK | platform 34, build-tools 34.0.0, platform-tools (ADB) |
| Android API | min/target/compile 34; `app/build.gradle.kts` |
| Compose | BOM 2024.06.00, compiler extension 1.5.14 |
| Readium | 3.0.0 |
| JVM tests | JUnit 4.13.2; `core/build.gradle.kts` |

Полный перечень зависимостей находится в Gradle-файлах. Это существующий toolchain прототипа; подготовка агентов не обновляет версии.

## Настройка окружения

1. Установи JDK 17 и Android SDK (например, через Android Studio SDK Manager).
2. Установи platform 34, build-tools 34.0.0 и platform-tools. Для UI-сценариев дополнительно создай тестовый Android emulator API 34+ с русской локалью. Исторические проверки использовали API 35.
3. Настрой JAVA_HOME и ANDROID_HOME для текущей машины. Не копируй пути другого разработчика.
4. При первом запуске build.ps1 создаст local.properties, если его нет. При существующем sdk.dir Gradle использует его; исправь устаревший путь вручную.

Для установленного SDK с доступным sdkmanager:

```powershell
sdkmanager 'platforms;android-34' 'build-tools;34.0.0' 'platform-tools'
sdkmanager --licenses
```

SDK можно установить через GUI; doctor ничего не скачивает и не принимает лицензии. Первая сборка требует доступа к Gradle distribution, Google Maven, Maven Central и Gradle Plugin Portal. Offline работает только после загрузки нужных зависимостей. Локальные `.gradle/`, `.tools/`, `artifacts/`, local.properties и outputs не коммитятся.

## Windows / PowerShell

```powershell
./scripts/doctor.ps1
./scripts/build.ps1
```

doctor проверяет выбранный Java/JDK, wrapper 8.6, platform 34, build-tools и SDK ADB. SDK выбирается из `-SdkPath`, `local.properties`, ANDROID_HOME, ANDROID_SDK_ROOT, затем стандартного Windows LOCALAPPDATA/Android/Sdk. Явный -SdkPath должен совпадать с существующим sdk.dir. Код завершения 0 означает пригодность установленных инструментов, 1 — обнаруженные проблемы; это ещё не успешная сборка.

build выбирает sdk.dir из существующего local.properties либо создаёт его по ANDROID_HOME, ANDROID_SDK_ROOT или Windows default. Команда выполняет:

```powershell
./gradlew.bat :core:test :app:assembleDebug :app:lintDebug --console=plain
```

После успеха APK копируется в `artifacts/FlibustaReader-0.2.1-debug.apk`. Скрипт не устанавливает его на устройство. PowerShell 5.1/7 поддерживаются; если локальная политика запрещает scripts, используй разрешённый вашей средой запуск либо прямые Gradle-команды, не меняй политику всей системы ради проекта.

## Linux / macOS

JDK 17 и SDK те же. Настрой ANDROID_HOME. Создай игнорируемый local.properties с `sdk.dir=/absolute/path/to/Android/sdk`, если он требуется среде. Windows local.properties не переносится между машинами.

```sh
bash ./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
bash ./gradlew :app:compileReleaseKotlin --console=plain
```

Результат debug: `app/build/outputs/apk/debug/app-debug.apk`. Прямой wrapper не копирует APK в artifacts. При наличии PowerShell 7 doctor можно запускать через `pwsh -File ./scripts/doctor.ps1`; ADB-сценарии имеют Windows default пути, для другой среды передай `-Adb` явно. Их работа на Linux/macOS в рамках подготовки не подтверждена.

## Выбор проверок

| Изменение | Команды и смысл |
| --- | --- |
| Один алгоритм/парсер `core` | `./gradlew.bat :core:test --tests 'net.flibustareader.core.OpdsParserTest' --console=plain` (подставь класс); затем `:core:test` при изменении общего контракта |
| Android-код, ресурсы, зависимости, scripts/CI | `./scripts/build.ps1`: JVM tests, debug APK, lint |
| HUD, темы, настройки и перелистывание | `./scripts/smoke-test.ps1 -Serial <serial> -Adb <adb-path>` |
| Очистка остатков, поворот, process death, viewport, типографика | `./scripts/reader-regression.ps1 -Serial <serial> -Adb <adb-path>` |
| Открытие в фоне, lifecycle/checkpoint | `./scripts/reader-background-regression.ps1 -Serial <serial> -Adb <adb-path>` и `:app:compileReleaseKotlin` |
| Только справочник | Сверить ссылки/пути/формулировки с кодом; не выдавать исторический PASS за новую проверку |

Для исправления значимой ошибки сначала воспроизведи сценарий, добавь подходящую регрессионную проверку, затем исправляй. Не нужно запускать все Android-сценарии для текстовой правки. Расширяй проверку по затронутым рискам, а не по количеству команд.

## UI-проверки на тестовом эмуляторе

Используй SDK platform-tools ADB. Проверка окружения не выбирает устройство автоматически:

```powershell
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $taskAdb devices
```

Ниже `emulator-5556` — пример; замени serial реальным из списка. `-Adb` используй для своего SDK; по умолчанию scripts смотрят Windows LOCALAPPDATA.

```powershell
./scripts/doctor.ps1 -RequireEmulator -Serial emulator-5556
./scripts/build.ps1
& $taskAdb -s emulator-5556 install -r ./artifacts/FlibustaReader-0.2.1-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed' }
./scripts/smoke-test.ps1 -Serial emulator-5556 -Adb $taskAdb
./scripts/reader-regression.ps1 -Serial emulator-5556 -Adb $taskAdb
./scripts/reader-background-regression.ps1 -Serial emulator-5556 -Adb $taskAdb
```

Сценарии открывают демо-книгу, меняют типографику/индикаторы и завершают процесс. Regression также создаёт искусственные остатки; настройки поворота восстанавливает в finally. Smoke подменяет батарею и сбрасывает её в finally. Запускай на выделенном тестовом профиле; не используй uninstall или pm clear для проверки обновления. Скрипты отклоняют serial физического устройства.

Smoke предполагает установленное приложение. Оба regression устанавливают APK сами и принимают `-Apk`; по умолчанию используют `artifacts/FlibustaReader-0.2.1-debug.apk`. Сначала пересобирай, чтобы проверка не прошла на старом бинарнике.

`smoke-test.ps1 -FailAfterBatteryOverride` намеренно падает: ожидается сообщение `Injected battery cleanup test failure`. После этого проверь `adb -s <serial> shell dumpsys battery`: отсутствие `UPDATES STOPPED` подтверждает сброс подмены. Это специальная негативная проверка, не обычный PASS smoke.

Часть сценариев использует UI hierarchy, русские подписи, координаты и допуски геометрии. Произвольная локаль/размер/навигация эмулятора могут потребовать адаптации сценария; это не основание менять UI ради теста. UI PASS не доказывает работу живого каталога и физического телефона.

## Результаты и диагностика

- JVM XML: `core/build/test-results/test/TEST-*.xml`; HTML: `core/build/reports/tests/test/index.html`.
- Android lint: `app/build/reports/lint-results-debug.html` и `.xml`.
- APK: `app/build/outputs/apk/debug/app-debug.apk` и копия в artifacts после build.ps1.
- Smoke screenshots/UI dump: `artifacts/reader-0.2-*.png`, `artifacts/smoke-ui.xml`; regression диагностика: `.tools/`.

Если Java/SDK не найдены, начни с doctor и исправления пути. Если зависимость не скачивается, отдельно проверь доступ к репозиторию: сетевой отказ не является ошибкой Kotlin. При UI-падении сохраняй и изучай сообщение, hierarchy, настройки и logcat; повторный PASS без понимания причины не закрывает установленную регрессию.

## CI

[android.yml](../.github/workflows/android.yml) — общий CI и конвейер preview-выпусков, добавленный параллельно с подготовкой документации. Он устанавливает JDK 17/SDK 34 и выполняет те же Gradle-задачи `:core:test :app:assembleDebug :app:lintDebug`, что локальный build.ps1. Дополнительно проверяет метаданные и упаковывает APK; сохраняет пакет и JVM/lint reports на 14 дней. Отдельный дублирующий workflow сборки не нужен.

Push/PR в main запускают сборку. Ручной запуск и теги позволяют выполнить две Android-регрессии на API 35; тег дополнительно готовит draft pre-release. Эти операции и постоянная preview-подпись описаны в [release-pipeline.md](release-pipeline.md). Изменение документации само по себе не разрешает агенту отправлять тег или публиковать выпуск.

doctor и compileReleaseKotlin — отдельные локальные проверки; текущий build job их не выполняет. Успешный compileReleaseKotlin не является сборкой/подписью release APK. Удалённый результат проверяй в конкретном Actions run; локальная сборка не доказывает работу инфраструктуры GitHub.

Использованы [setup-java](https://github.com/actions/setup-java) и [upload-artifact](https://github.com/actions/upload-artifact), версии actions закреплены commit SHA в workflow. Изменение версий actions — часть сопровождения CI, а не обновление зависимостей приложения.
