# Development Rules — СетевыеВесыПечатьЭтикетки

**Основной стек:** Android (Kotlin + Jetpack Compose), вспомогательный — 1С:Предприятие.

---

# Android / Kotlin Разработка

## Стек и версии

- **Язык:** Kotlin 2.x, null-safety обязательна
- **UI:** Jetpack Compose (Material 3), минимум XML-раскладок
- **DI:** Hilt
- **Архитектура:** MVVM + Clean (ViewModel → UseCase → Repository → DataSource)
- **Асинхронность:** Kotlin Coroutines + Flow
- **Сеть:** Retrofit + OkHttp + kotlinx.serialization (или Moshi)
- **БД:** Room
- **Навигация:** Compose Navigation (type-safe routes)
- **Тесты:** JUnit5, Turbine (Flow), Compose UI Test, MockK

## Структура проекта

```
app/src/main/java/<package>/
├── di/                 — Hilt-модули
├── data/
│   ├── local/          — Room DAO, entities
│   ├── remote/         — Retrofit API, DTO
│   └── repository/     — реализции репозиториев
├── domain/
│   ├── model/          — бизнес-модели
│   ├── repository/     — интерфейсы репозиториев
│   └── usecase/        — юзкейсы
├── presentation/
│   ├── theme/          — тема, цвета, типографика
│   ├── navigation/     — NavHost, routes
│   ├── screens/        — экраны (package per screen)
│   │   └── <feature>/
│   │       ├── <Feature>Screen.kt
│   │       ├── <Feature>ViewModel.kt
│   │       └── <Feature>UiState.kt
│   └── components/     — переиспользуемые Compose-компоненты
└── util/               — extensions, helpers
```

## Правила кода (Kotlin)

### Обязательные

- **Корутины:** только через `viewModelScope` / `lifecycleScope`, без `GlobalScope`
- **State:** UI-состояние — immutable data class + `StateFlow<UiState>`
- **Compose:** stability — все модели в `UiState` должны быть `@Stable` или `data class` с immutable полями
- **Compose:** без副作用 в composition — `LaunchedEffect`, `DisposableEffect` по правилам
- **Null-safety:** без `!!`, без `lateinit var` в data-классах; `?.let` вместо проверок
- **Имена:** camelCase для всего, PascalCase только для классов/Composables
- **String resources:** все пользовательские строки в `strings.xml`, кроме логов
- **Hard-coded values:** запрещены — цвета в теме, размеры в `dimens.xml`/`Spacing`

### Запрещено

- `GlobalScope`, `runBlocking` в UI-слое
- Логика в Composable — только в ViewModel
- Прямой доступ к БД/сети из ViewModel — только через Repository
- `MutableStateFlow` снаружи ViewModel
- God-Activity / God-ViewModel — разбивать по фичам
- `try/catch` без логирования и обработки

### Compose-конвенции

```kotlin
// Composable начинается с PascalCase, без return
@Composable
fun ProductCard(
    product: ProductUiModel,
    onPrint: (String) -> Unit,   // callback в конце
    modifier: Modifier = Modifier
)

// Preview обязателен для каждого нетривиального Composable
@Preview(showBackground = true)
@Composable
fun ProductCardPreview() { ... }
```

- `Modifier` — всегда первый не-позиционный параметр
- События — callback-и вниз, состояние — вверх
- `remember` / `derivedStateOf` где уместно, без пересоздания state

## Архитектура (MVVM + Clean)

```
Screen (Compose)
    ↓ collect
ViewModel (StateFlow<UiState>)
    ↓ calls
UseCase (чистая логика, suspend fun)
    ↓
Repository (интерфейс в domain, реализация в data)
    ↓
Room / Retrofit / SharedPreferences
```

**Правила:**
- ViewModel не знает про Android SDK (кроме `SavedStateHandle`)
- UseCase — один метод = одна бизнес-операция, `operator fun invoke`
- Repository — single source of truth, кэширует через Room
- Side effects — через `SharedFlow` (одноразовые события: toast, navigate)

## Сетевые весы и печать этикеток

### Связь с весами

```kotlin
// Протокол: TCP/IP (socket) или Bluetooth (SPP)
// Типичные протоколы: CAS, Mettler-Toledo, Shinko
interface ScalesClient {
    suspend fun readWeight(): Flow<WeightData>
    suspend fun tare()
    suspend fun zero()
}
```

- Переподключение с exponential backoff
- Таймауты на каждый запрос (по умолчанию 3 сек)
- Данные весов — `StateFlow<WeightData?>` в ViewModel

### Печать этикеток

```kotlin
// Принтеры: ESC/POS (USB/Bluetooth/Network), ZPL (Zebra)
interface LabelPrinter {
    suspend fun print(label: LabelData): Result<PrintStatus>
    fun connectionState(): Flow<PrinterState>
}
```

- Формат этикетки — конфигурируемый (шир×выс в мм)
- Шаблоны — в `domain/model/LabelTemplate`
- Retry при обрыве связи, но не больше 3 раз

## Сборка и качество

```bash
./gradlew assembleDebug          # сборка
./gradlew test                   # unit-тесты
./gradlew connectedCheck         # instrumented
./gradlew lint                   # Android Lint
./gradlew ktlintCheck            # стиль Kotlin
./gradlew detekt                 # статический анализ
```

**Definition of Done:**
1. Код компилируется без ошибок
2. Линтеры проходят (`ktlint`, `detekt`, `lint`)
3. Unit-тесты на ViewModel и UseCase
4. Compose Preview для новых экранов
5. Нет hard-coded строк/цветов

---

# 1С:Предприятие (вспомогательный)

Если в проекте появляется 1С-часть (сервер, обмен, ЭДО) — применяются эти правила:

## Основные принципы

- **Язык:** BSL, ВерблюжьяНотация для идентификаторов
- **Комментарии:** русские, содержательные
- **Запросы:** оптимизировать, не тянуть лишнее, использовать временные таблицы
- **Транзакции:** исключения через `Попытка/Исключение` с откатом

## Стандарты

- Проверять API через `bsl-platform-context` перед использованием
- Стандарты 1С — через `v8std` MCP
- Валидация BSL — `1c-validate` скилл
- Метаданные — скилл `1c-metadata-manage`

## Структура

- Модули менеджера — экспортные методы, бизнес-логика
- Модули объектов — обработчики событий, проверки
- Общие модули — stateless, без переменных модуля

---

# Процесс разработки

## Триаж задач

1. **Quick-fix** — одна логика, один файл, до 40 строк
2. **Full-cycle** — всё остальное: план → код → тесты → проверка

## Перед началом

- Для Android: уточнить экран/фичу, данные, источник
- Для 1С: уточнить объект, операцию, правила валидации

## CONFUSION — если есть сомнения

```text
CONFUSION: <конфликт / неоднозначность>
Варианты:
  A) <вариант> — <последствия>
  B) <вариант> — <последствия>
→ Какой выбрать?
```

Триггеры: архитектура, безопасность, необратимые изменения, конфликт с существующим кодом.

## Память проекта

- Android-решения → `.opencode/rules/project-memory.md`
- 1С-изменения → туда же
- Сохранять корректировки в тот же ход

---

# Язык

- Комментарии в коде — **русский**
- Строки для пользователя — **русский** (через `strings.xml`)
- Commit messages — **русский** или **English** (по договорённости)
- Документация — **English**
