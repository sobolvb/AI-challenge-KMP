# Архитектура проекта AI Challenge KMP

## Обзор

AI Challenge KMP — это Kotlin Multiplatform проект, состоящий из трёх основных модулей:

1. **server** — Ktor 3.x сервер с REST API
2. **composeApp** — Compose Multiplatform клиент (Desktop)
3. **shared** — общий код между клиентом и сервером

## Архитектурные принципы

### Dependency Injection

Проект использует **manual DI** через объект `AppContainer` (файл `server/src/main/kotlin/com/aichallengekmp/di/AppModule.kt`).

Отказались от Koin из-за несовместимости с Ktor 3.x. Все зависимости инициализируются lazy в `AppContainer`:

```kotlin
val database by lazy { DatabaseFactory.createDatabase(...) }
val sessionDao by lazy { SessionDao(database) }
val chatService by lazy { ChatService(...) }
```

### Слои приложения

#### 1. Routing Layer (HTTP endpoints)

Находится в `server/src/main/kotlin/com/aichallengekmp/routing/`:
- `ChatRoutes.kt` — эндпоинты для работы с чатом
- `RagRoutes.kt` — эндпоинты для RAG индексации

#### 2. Service Layer (бизнес-логика)

Находится в `server/src/main/kotlin/com/aichallengekmp/service/`:
- `ChatService` — основная логика чата, обработка сообщений, вызов LLM
- `CompressionService` — сжатие истории сообщений
- `ReminderService` — управление напоминаниями

#### 3. Data Layer (DAO + Database)

Находится в `server/src/main/kotlin/com/aichallengekmp/database/`:
- SQLDelight + SQLite для персистентности
- DAO паттерн: `SessionDao`, `MessageDao`, `RagChunkDao` и др.

#### 4. AI Integration Layer

Находится в `server/src/main/kotlin/com/aichallengekmp/ai/`:
- `ModelRegistry` — реестр доступных LLM моделей
- `YandexGPTProvider` — интеграция с YandexGPT API
- Поддержка function calling (tools)

#### 5. RAG Layer

Находится в `server/src/main/kotlin/com/aichallengekmp/rag/`:
- `RagIndexService` — индексация документов
- `RagSearchService` — векторный поиск
- `OllamaEmbeddingsProvider` — генерация эмбеддингов через Ollama

#### 6. Tools Layer

Находится в `server/src/main/kotlin/com/aichallengekmp/tools/`:
- `ToolExecutor` — интерфейс для исполнения инструментов
- `TrackerToolsService` — инструменты для работы с Яндекс.Трекером и напоминаниями
- Поддержка MCP (Model Context Protocol)

## Поток обработки сообщения

1. Клиент отправляет POST `/api/chat/send` с текстом сообщения
2. `ChatRoutes` получает запрос и передает в `ChatService.sendMessage()`
3. `ChatService` сохраняет сообщение пользователя в БД
4. Выполняется автоматический RAG-поиск по локальной документации
5. Формируется system prompt с RAG-контекстом
6. `ChatService` вызывает `ModelRegistry.complete()` с историей сообщений
7. `YandexGPTProvider` делает запрос к YandexGPT API
8. Если модель вызывает tools — выполняются через `ToolExecutor`
9. Результат сохраняется в БД и возвращается клиенту

## Конфигурация

Все переменные окружения загружаются в `AppContainer.config`:

- `YANDEX_API_KEY` — API ключ для YandexGPT
- `YANDEX_MODEL_URI` — folder ID для YandexGPT
- `DB_PATH` — путь к SQLite базе (по умолчанию `chat.db`)
- `OLLAMA_BASE_URL` — URL Ollama сервера (по умолчанию `http://localhost:11434`)
- `OLLAMA_EMBED_MODEL` — модель эмбеддингов (по умолчанию `nomic-embed-text`)

## База данных

### Основные таблицы:

- **Session** — чат-сессии
- **Message** — сообщения в сессиях
- **SessionSettings** — настройки сессии (temperature, max tokens, system prompt)
- **SessionCompression** — сжатые резюме истории
- **Reminder** — напоминания
- **DocumentChunk** — RAG индекс (векторы документов)
- **MessageRagSource** — связь сообщений с источниками RAG

### Миграции

SQLDelight не поддерживает автоматические миграции. При изменении схемы:
1. Обновите `.sq` файлы в `server/src/main/sqldelight/`
2. Добавьте логику миграции в `DatabaseFactory.createDatabase()`
3. Для dev среды можно удалить `chat.db` для пересоздания схемы

## Особенности

### RAG интегрирован в чат

RAG не используется только по требованию — он **автоматически выполняется на каждое сообщение пользователя**.

Результаты фильтруются по порогу similarity (0.4) и добавляются в system prompt **в начало**.

### Function Calling

YandexGPT может вызывать инструменты (tools) во время генерации ответа:
- Получает список доступных tools в формате JSON Schema
- Решает, когда и какой tool вызвать
- Получает результат и продолжает генерацию

Цикл может повторяться несколько раз до финального ответа.

### MCP Support

Проект поддерживает MCP (Model Context Protocol) для экспорта инструментов:
- MCP серверы для tracker и reminders
- HTTP + SSE транспорт
- Интеграция через `McpClientRegistry`

Сейчас используется `LocalToolExecutor`, но можно переключиться на `McpAwareToolExecutor`.
