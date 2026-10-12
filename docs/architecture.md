# Текущая архитектура

Срез реализации: Android 0.2.1, 12 октября 2026 года. Ограничения — в [project-status.md](project-status.md), форматы данных — в [data-storage.md](data-storage.md), команды — в [development.md](development.md).

## Стек и границы

Два Gradle-модуля: `:app` зависит от `:core`. `core` — Kotlin/JVM с JUnit 4, не зависит от Android. `app` — Android, Compose и Readium. Собственного backend, DI-фреймворка и базы Room нет.

| Компонент | Ответственность |
| --- | --- |
| `app/src/main/kotlin/net/flibustareader/MainActivity.kt` | Библиотека, каталог, поиск, карточки, системный импорт и запуск операций |
| `app/src/main/kotlin/net/flibustareader/CatalogState.kt` | ViewModel с наблюдаемыми книгами, результатами каталога и UI-состоянием |
| `app/src/main/kotlin/net/flibustareader/BookRepository.kt` | Источники, HTTP, импорт/конвертация, библиотека и файловое восстановление |
| `app/src/main/kotlin/net/flibustareader/ReaderActivity.kt` | Readium navigator, Android lifecycle, геометрия, Locator, якорь типографики, системные показатели |
| `app/src/main/kotlin/net/flibustareader/ReaderChrome.kt` | Compose-панели чтения, индикаторы, палитры и настройки |
| `app/src/main/kotlin/net/flibustareader/ReaderPreferences.kt` | Преобразование SharedPreferences ↔ ReaderOptions и совместимость старой темы |
| `app/src/main/kotlin/net/flibustareader/ReaderTheme.kt` | Общая Compose-тема главного экрана |
| `core/src/main/kotlin/net/flibustareader/core/OpdsParser.kt` | Лента, книги, навигация, форматы загрузки, next и разрешение ссылок |
| `core/src/main/kotlin/net/flibustareader/core/HttpsRequest.kt` | HTTP-ответ, redirects, ограничения протокола, передача конечного URL |
| `core/src/main/kotlin/net/flibustareader/core/Xml.kt` | Namespace-aware XML и защита от DTD/внешних сущностей |
| `core/src/main/kotlin/net/flibustareader/core/Fb2Converter.kt` | FB2 → EPUB 3, разметка, изображения и оглавление |
| `core/src/main/kotlin/net/flibustareader/core/AtomicTextFile.kt` | Синхронизированное обновление индекса через временный файл |
| `core/src/main/kotlin/net/flibustareader/core/ReaderOptions.kt` | Настройки, нормализация диапазонов и переключение HUD |
| `core/src/main/kotlin/net/flibustareader/core/ReaderInteraction.kt` | Зоны касания, progress/seek с весами ресурсов |
| `core/src/main/kotlin/net/flibustareader/core/ReaderViewport.kt` | Горизонтальная геометрия с учётом inset и максимальной ширины |
| `core/src/main/kotlin/net/flibustareader/core/ReadingProgress.kt` | Выбор доступного процента чтения |

## Каталог и загрузка

`MainActivity → BookRepository.page → HttpsRequest → OpdsParser → CatalogState → Compose UI`.

Сетевые операции, импорт и подготовка демо-книги запускаются через `perform` и `runInterruptible(Dispatchers.IO)` в `CatalogState.viewModelScope`, поэтому работа не привязана к старому экземпляру Activity. Создание BookRepository со стартовой очисткой и чтение локальной библиотеки в onCreate/onResume пока синхронны на UI thread — не считай весь файловый I/O уже вынесенным. ViewModel переживает пересоздание Activity. Небольшие поля и адреса истории сохраняются в Bundle; результаты лент не сериализуются целиком. После смерти процесса адреса истории и текущей ленты повторно загружаются из сети; это не равно сохранению готовых сетевых результатов.

`HttpsRequest` следует redirects вручную, разрешает только HTTPS, устанавливает connect/read timeout 20/30 секунд и передаёт обработчику конечный URL ответа. Цикл ограничен шестью итерациями запроса. Парсер наследует `xml:base` от feed до link и сохраняет только итоговые HTTPS-ссылки. Лента ограничена 5 MiB.

Каталог и сервер скачивания задаются раздельно. `BookRepository.download` берёт путь и query из ссылки OPDS и разрешает их относительно `downloadBase`; это сознательная замена origin на выбранный сервер текстов. Не превращай её незаметно в скачивание с любого host, указанного в каталоге.

Загрузка ограничена 50 MiB и идёт во временный cache-файл. Затем применяется тот же путь подготовки, что для локального импорта. Фонового сервиса/автоматического resume нет.

Связанные тесты: `OpdsParserTest`, `HttpsRequestTest`, `ReviewRegressionTest`; фикстуры — `fixtures/catalog.xml`, `fixtures/search-books.xml`.

## Импорт и библиотека

`Системный выбор файла / download / demo → BookRepository.prepare → EPUB или Fb2Converter → <id>.part → <id>.epub → library.json`.

Все готовые книги хранятся как EPUB, даже если поле исходного формата — FB2. EPUB проверяется на наличие `META-INF/container.xml`; полная валидация формата этой проверкой не обеспечивается. Для ZIP берётся первая FB2-запись; пути архива не используются для распаковки в файловую структуру приложения. Размер извлечённого FB2 ограничен отдельно.

XML запрещает DTD и внешние сущности. FB2 до рекурсивного renderer проверяется итеративно на глубину 128. Генератор служебных якорей учитывает исходные id; это не полная валидация некорректных дублирующихся id входного файла.

Конвертер создаёт один XHTML с текстом, EPUB package, оглавление и поддержанные растровые изображения. Сложная исходная вёрстка и все разновидности FB2 не гарантируются.

При первом создании репозитория для каталога в процессе удаляются `.part` и неиндексированные `.epub` из `files/books`. Индекс обновляется через AtomicTextFile. Подробности порядка записи и его границ — в data-storage.md.

Связанные тесты: `Fb2ConverterTest`, `ReviewRegressionTest`, `AtomicTextFileTest`; Android-восстановление проверяет `reader-regression.ps1`.

## Чтение и настройки

`ReaderActivity → AssetRetriever / PublicationOpener → Publication → EpubNavigatorFactory → EpubNavigatorFragment`.

Читалка пересоздаёт зависимую от публикации fragment factory и восстанавливает Locator из preferences. В `onCreate` используется `super.onCreate(null)`: нельзя менять это на стандартное восстановление fragment без проверки доступности factory и publication.

Подготовка публикации асинхронна. Установка navigator выполняется через `lifecycle.withResumed`, затем проверяется `!supportFragmentManager.isStateSaved`; ошибка также показывается в RESUMED. Публикация освобождается при уничтожении Activity. Не заменяй это транзакцией с потерей состояния.

`fragment.currentLocator` сохраняется как JSON. Прогресс и seek учитывают число Readium-позиций каждого ресурса; это не точное число оставшихся страниц главы.

При смене шрифта/размера/полей/интервала/режима чтения удерживается Locator первого видимого элемента, быстрые запросы объединяются, ожидается стабильная геометрия WebView, затем восстанавливается якорь. Пока якорь активен, промежуточные Locator не записываются и переходы не должны конкурировать с его восстановлением.

Настройки сохраняются через ReaderPreferences; значения нормализуются ReaderOptions. Яркость применяется к окну читалки. Часы используют настройки устройства, батарея обновляется Android broadcast.

### Инварианты экрана

- Верхняя/нижняя панели накладываются на текст; их показ не меняет viewport, pagination и позицию.
- Экран с вырезом заполняется фоном; insets учитываются без повторного применения Readium padding.
- Видимые HUD-подписи занимают 24 dp; области касания — 48 dp и сохраняются после скрытия подписей.
- Ширина колонки текста ограничена 760 dp; при вычислении учитываются боковые insets.
- В постраничном режиме края перелистывают, центр показывает панели. В scroll-режиме короткое касание переключает панели; взаимодействия со ссылками/выделением обрабатывает Readium.
- Одна закладка на книгу. Настройки HUD и типографики переживают перезапуск.

Связанные тесты: `ReaderInteractionTest`, `ReaderViewportTest`, `ReaderOptionsTest`, `ReadingProgressTest`. Android-сценарии: smoke, reader-regression и reader-background-regression.

## Debug и release

`app/src/debug/kotlin/net/flibustareader/ReaderOpeningCheckpoint.kt` — приватная файловая контрольная точка для управляемого завершения подготовки в фоне. Coroutine ждёт удаления `hold` с timeout 60 секунд; markers показывают подготовку и установку fragment.

`app/src/release/kotlin/net/flibustareader/ReaderOpeningCheckpoint.kt` — no-op. Изменения checkpoint проверяются Android-сценарием и компиляцией release; файловое управление и ожидание не должны попадать в release.

## Как расширять

Сначала используй существующую границу `core`/`app`. При появлении новой функции отделяй вычисления, которые можно проверить на JVM, от Android UI/lifecycle. Массовый рефакторинг Activity, новая база данных или обновление Readium — отдельные задачи с критериями совместимости. Причины текущих решений: [ADR](adr/README.md).
