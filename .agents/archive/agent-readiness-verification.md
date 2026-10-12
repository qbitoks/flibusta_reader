# Проверка подготовки проекта для coding agents

Дата: 12 октября 2026 года. Проверено рабочее дерево с универсальными инструкциями; HEAD во время проверки — `76efc19` (общий конвейер выпуска добавлен параллельной задачей).

## Результаты

| Проверка | Фактический результат |
| --- | --- |
| `./scripts/doctor.ps1` | Код 0: JDK 17.0.18, wrapper 8.6, SDK/platform 34, build-tools 34.0.0, ADB |
| `doctor -SdkPath C:/sdk-does-not-exist-agent-readiness` | Ожидаемый код 1; диагностика отсутствующего SDK и несовпадения sdk.dir |
| `doctor -Serial physical-device` | Ожидаемый код 1; serial отклонён без обращения к физическому устройству |
| `doctor -RequireEmulator` без serial | Ожидаемый код 1; требуется явный выбор устройства |
| `doctor -RequireEmulator -Serial emulator-5556` | Код 0; выбранный эмулятор доступен, API 35 |
| `./scripts/build.ps1` | Код 0, BUILD SUCCESSFUL; debug APK создан в artifacts |
| `./gradlew.bat :core:test --rerun-tasks :app:compileReleaseKotlin --console=plain` | Код 0, BUILD SUCCESSFUL; 30 JVM tests, 0 failures/errors; release Kotlin компилируется |
| Android lint report | 0 errors; 6 предупреждений о версиях зависимостей |
| PowerShell parser для scripts/*.ps1 | Ошибок синтаксиса нет |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/doctor.ps1` | Код 0 под Windows PowerShell 5.1; политика задана только для дочернего процесса |
| Doctor с JAVA_HOME, обёрнутым в кавычки | Код 0 под PowerShell 7; соответствует обработке пути в gradlew.bat |
| Markdown links/anchors | 80 локальных ссылок, 0 ошибок на момент проверки |
| Разбор android.yml через SnakeYAML 2.0 из установленного Gradle | PASS; обнаружены jobs build, emulator, release; это проверка YAML, не удалённый Actions run |
| `git diff --check` и `git diff --cached --check` | Код 0; ошибок пробелов нет |

Первый запуск сборки в sandbox не получил доступ к lock-файлу пользовательского Gradle cache. Повторный запуск с доступом к установленным инструментам прошёл; исходники для обхода ограничения не менялись.

Компилятор release сообщил два предупреждения delicate API в существующем ReaderActivity (`currentLocator`, строки могут смещаться). Это не ошибка сборки и не новый результат изменения UI.

## Ревью

Независимое read-only ревью сверило инструкции и справочники с исходниками и android.yml. Найденное завышенное утверждение о переносе всего I/O на Dispatchers.IO исправлено: architecture/project-status теперь явно описывают синхронное стартовое чтение/очистку библиотеки на UI thread. Других существенных противоречий не выявлено.

Общий CI — `.github/workflows/android.yml`; созданный в ходе подготовки дублирующий verify.yml удалён. Документация учитывает раздельные build, emulator и preview release jobs; doctor и release Kotlin проверены локально.

## Границы проверки

- Загрузка AGENTS/адаптеров реальными Claude Code, Gemini CLI, Cursor и Copilot в этом проходе не проверялась. Предусмотрен универсальный ручной запрос, форматы адаптеров сверены с официальной документацией.
- Удалённый GitHub Actions run, подпись и публикация выпуска в этом проходе не проверялись; соответствующий конвейер ведётся отдельной задачей. Локальная сборка не является подтверждением remote CI.
- Linux/macOS локально не запускались. Doctor проверен под Windows PowerShell 5.1/7. Обычный запуск через powershell.exe был отклонён локальной ExecutionPolicy; проверка с политикой только для дочернего процесса прошла. Глобальная политика системы не менялась.
- Android smoke/regression в этом проходе не повторялись: приложение не изменялось, выполнялась только проверка доступности/API эмулятора. Исторические Android PASS остаются в reader-ui-verification.md.

Ссылка на общий контракт: [AGENTS.md](../AGENTS.md); навигация: [docs/README.md](README.md).
