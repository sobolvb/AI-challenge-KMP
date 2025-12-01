# Стиль кода и соглашения

## Общие принципы

1. **Kotlin идиомы** — используйте идиоматичный Kotlin код
2. **Explicit is better than implicit** — явные типы и nullability
3. **Clean code** — понятные имена, короткие функции, минимум комментариев
4. **SOLID принципы** — особенно Single Responsibility и Dependency Inversion

## Именование

### Классы и интерфейсы

- **PascalCase** для классов: `ChatService`, `RagIndexService`
- Суффиксы по назначению:
  - `Service` — бизнес-логика
  - `Dao` — Data Access Object
  - `Provider` — провайдеры внешних сервисов
  - `Executor` — исполнители операций
  - `Registry` — реестры/каталоги

Примеры:
```kotlin
class ChatService { }
interface ToolExecutor { }
class YandexGPTProvider { }
```

### Функции

- **camelCase**: `sendMessage()`, `indexDocument()`
- Глаголы для действий: `get`, `create`, `update`, `delete`, `send`, `execute`
- Suspend функции не требуют специального префикса

```kotlin
suspend fun createSession(name: String): Session { }
fun getSessionList(): List<SessionDto> { }
```

### Переменные

- **camelCase**: `sessionId`, `messageText`, `ragHits`
- Константы — **UPPER_SNAKE_CASE**: `MAX_RETRIES`, `DEFAULT_TIMEOUT`
- Boolean переменные — `is`, `has`, `should` префиксы: `isActive`, `hasRagSources`

```kotlin
private val ragSimilarityThreshold = 0.4
const val MAX_CHUNK_SIZE = 250
val isCompleted = true
```

### Файлы

- Один публичный класс/интерфейс на файл
- Имя файла = имя класса
- Исключение: файлы с extension функциями — `Extensions.kt`, `DTOs.kt`

## Структура файла

```kotlin
package com.aichallengekmp.service

import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory

/**
 * Документация класса в KDoc формате
 */
class ChatService(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    // Константы
    private val ragSimilarityThreshold = 0.4

    // Публичные методы
    suspend fun createSession(name: String): Session { }

    // Приватные методы
    private fun buildPrompt(): String { }
}
```

Порядок:
1. Package declaration
2. Imports (отсортированы, без wildcard `*`)
3. KDoc документация класса
4. Primary constructor
5. Properties (сначала публичные, потом приватные)
6. Init блоки
7. Secondary constructors
8. Публичные методы
9. Приватные методы
10. Companion object (в конце)

## Nullability

Избегайте null где возможно. Используйте:
- `?: defaultValue` для default значений
- `?.let { }` для safe calls
- `requireNotNull()` или `checkNotNull()` для validation

```kotlin
// Bad
if (value != null) {
    doSomething(value)
}

// Good
value?.let { doSomething(it) }

// Good
val nonNull = value ?: return
doSomething(nonNull)
```

## Coroutines

- Все I/O операции должны быть suspend функциями
- Используйте `withContext(Dispatchers.IO)` для I/O
- Используйте `withContext(Dispatchers.Default)` для CPU-intensive операций
- Не создавайте новые корутины внутри suspend функций без необходимости

```kotlin
suspend fun loadData(): Data = withContext(Dispatchers.IO) {
    // I/O операция
    database.getData()
}
```

## Logging

Используйте SLF4J через `LoggerFactory`:

```kotlin
private val logger = LoggerFactory.getLogger(ChatService::class.java)

logger.info("✅ Сессия создана: $sessionId")
logger.warn("⚠️ RAG индекс пуст")
logger.error("❌ Ошибка при обработке: ${e.message}", e)
```

Emoji для уровней:
- 🆕 — новые объекты
- ✅ — успешные операции
- ⚠️ — предупреждения
- ❌ — ошибки
- 🔎 — поиск/запросы
- 📚 — индексация
- 💬 — сообщения
- 🤖 — LLM операции

## Error Handling

Используйте специфичные исключения:

```kotlin
class NotFoundException(message: String) : Exception(message)
class ValidationException(message: String) : Exception(message)
```

В HTTP handlers ловите исключения и возвращайте правильные статусы:

```kotlin
try {
    val result = chatService.sendMessage(sessionId, text)
    call.respond(HttpStatusCode.OK, result)
} catch (e: NotFoundException) {
    call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", e.message))
} catch (e: Exception) {
    logger.error("Unexpected error", e)
    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Unexpected error"))
}
```

## Dependency Injection

Используйте constructor injection через primary constructor:

```kotlin
class ChatService(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val modelRegistry: ModelRegistry
) {
    // ...
}
```

Все зависимости должны быть `private val`.

## DTOs и Data Classes

- Используйте `data class` для DTOs
- Добавляйте `@Serializable` для kotlinx.serialization
- Default значения для опциональных полей

```kotlin
@Serializable
data class MessageDto(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val ragSources: List<RagSourceDto>? = null
)
```

## Extension Functions

Группируйте extension функции в отдельных файлах:

```kotlin
// Extensions.kt
fun Session.toDto(): SessionDto = SessionDto(
    id = id,
    name = name,
    createdAt = createdAt
)

suspend fun Message.toDto(ragSourceDao: RagSourceDao): MessageDto {
    // ...
}
```

## Testing

TODO: Добавить руководство по тестированию когда появятся тесты.

## SQL (SQLDelight)

- Таблицы — PascalCase: `Session`, `MessageRagSource`
- Колонки — camelCase: `sessionId`, `createdAt`
- Foreign keys всегда с `ON DELETE CASCADE` или `ON DELETE SET NULL`
- Индексы для внешних ключей

```sql
CREATE TABLE Message (
    id TEXT PRIMARY KEY NOT NULL,
    sessionId TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    FOREIGN KEY (sessionId) REFERENCES Session(id) ON DELETE CASCADE
);

CREATE INDEX message_sessionId ON Message(sessionId);
```

## Git Commit Messages

- Используйте русский язык
- Формат: `день N. краткое описание`
- Примеры:
  - `день 19. RAG в chat`
  - `день 20. Ассистент разработчика`
  - `fix: исправление ошибки в RAG поиске`

## TODO Comments

Используйте TODO комментарии для будущих улучшений:

```kotlin
// TODO: Добавить кэширование эмбеддингов
// TODO: Реализовать batch индексацию для производительности
```

Не используйте FIXME, HACK и другие маркеры.
