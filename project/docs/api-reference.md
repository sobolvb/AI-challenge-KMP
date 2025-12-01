# API Reference

Все эндпоинты находятся под префиксом `/api`.

## Chat API

### GET /api/sessions

Получить список всех чат-сессий.

**Response:**
```json
[
  {
    "id": "uuid",
    "name": "Название сессии",
    "createdAt": 1234567890,
    "updatedAt": 1234567890
  }
]
```

### GET /api/sessions/:id

Получить детали сессии со всеми сообщениями.

**Response:**
```json
{
  "session": {
    "id": "uuid",
    "name": "Название сессии",
    "createdAt": 1234567890,
    "updatedAt": 1234567890
  },
  "messages": [
    {
      "id": "uuid",
      "role": "user",
      "content": "Текст сообщения",
      "modelId": null,
      "modelName": null,
      "tokenUsage": null,
      "timestamp": 1234567890,
      "ragSources": null
    },
    {
      "id": "uuid",
      "role": "assistant",
      "content": "Ответ ассистента",
      "modelId": "yandexgpt/latest",
      "modelName": "YandexGPT",
      "tokenUsage": {
        "inputTokens": 100,
        "outputTokens": 50,
        "totalTokens": 150
      },
      "timestamp": 1234567891,
      "ragSources": [
        {
          "sourceId": "README.md",
          "chunkIndex": 2,
          "score": 0.85,
          "chunkText": "Текст фрагмента..."
        }
      ]
    }
  ],
  "settings": {
    "modelId": "yandexgpt/latest",
    "temperature": 0.7,
    "maxTokens": 2000,
    "systemPrompt": "Ты умный помощник..."
  }
}
```

### POST /api/sessions

Создать новую сессию с первым сообщением.

**Request:**
```json
{
  "name": "Новая сессия",
  "initialMessage": "Привет!",
  "settings": {
    "modelId": "yandexgpt/latest",
    "temperature": 0.7,
    "maxTokens": 2000,
    "systemPrompt": "Ты умный помощник..."
  }
}
```

**Response:** Аналогичен GET /api/sessions/:id

### POST /api/chat/send

Отправить сообщение в существующую сессию.

**Request:**
```json
{
  "sessionId": "uuid",
  "message": "Текст сообщения"
}
```

**Response:** Аналогичен GET /api/sessions/:id

### PUT /api/sessions/:id/settings

Обновить настройки сессии.

**Request:**
```json
{
  "modelId": "yandexgpt/latest",
  "temperature": 0.5,
  "maxTokens": 1000,
  "systemPrompt": "Новый system prompt"
}
```

**Response:**
```json
{
  "status": "ok"
}
```

### DELETE /api/sessions/:id

Удалить сессию со всеми сообщениями.

**Response:**
```json
{
  "status": "ok"
}
```

## RAG API

### POST /api/rag/index/readme

Индексировать файл README.md из корня проекта.

**Response:**
```json
{
  "status": "ok",
  "sourceId": "README.md",
  "chunks": "15"
}
```

### POST /api/rag/index/docs

Индексировать все документы из папки `project/docs`.

**Response:**
```json
{
  "status": "ok",
  "indexed": [
    {
      "sourceId": "docs/architecture.md",
      "chunks": 8
    },
    {
      "sourceId": "docs/api-reference.md",
      "chunks": 12
    }
  ]
}
```

### POST /api/rag/ask

Экспериментальный эндпоинт для сравнения ответов с RAG и без RAG.

**Request:**
```json
{
  "question": "Как создать новую сессию?",
  "modelId": "yandexgpt/latest",
  "topK": 5,
  "temperature": 0.7,
  "maxTokens": 1000,
  "similarityThreshold": 0.4,
  "systemPrompt": "Ты помощник разработчика"
}
```

**Response:**
```json
{
  "question": "Как создать новую сессию?",
  "withRag": {
    "answer": "Ответ с использованием RAG...",
    "modelId": "yandexgpt/latest",
    "tokenUsage": { ... }
  },
  "withRagFiltered": {
    "answer": "Ответ с фильтрованным RAG...",
    "modelId": "yandexgpt/latest",
    "tokenUsage": { ... }
  },
  "withoutRag": {
    "answer": "Ответ без RAG...",
    "modelId": "yandexgpt/latest",
    "tokenUsage": { ... }
  },
  "usedChunks": [ ... ],
  "usedChunksFiltered": [ ... ],
  "similarityThreshold": 0.4
}
```

## Error Responses

Все ошибки возвращаются в формате:

```json
{
  "code": "error_code",
  "message": "Описание ошибки"
}
```

Возможные HTTP статусы:
- **400 Bad Request** — некорректный запрос
- **404 Not Found** — ресурс не найден
- **500 Internal Server Error** — внутренняя ошибка сервера

## Available Tools (для YandexGPT)

YandexGPT может вызывать следующие инструменты через function calling:

### get_issues_count

Получить количество задач в Яндекс.Трекере.

**Arguments:** нет

**Returns:** `{ "count": 42 }`

### get_all_issue_names

Получить список всех задач с ключами и названиями.

**Arguments:** нет

**Returns:**
```json
{
  "issues": [
    { "key": "PROJ-123", "summary": "Название задачи" }
  ]
}
```

### get_issue_info

Получить подробную информацию о задаче.

**Arguments:**
- `issue_key` (string, required) — ключ задачи (например, "PROJ-123")

**Returns:**
```json
{
  "key": "PROJ-123",
  "summary": "Название",
  "description": "Описание",
  "status": "Open",
  "assignee": "user@example.com"
}
```

### create_reminder

Создать напоминание.

**Arguments:**
- `text` (string, required) — текст напоминания
- `due_timestamp` (integer, required) — Unix timestamp

**Returns:** `{ "id": "uuid", "text": "...", "dueAt": 1234567890 }`

### list_reminders

Получить список всех напоминаний.

**Arguments:** нет

**Returns:**
```json
{
  "reminders": [
    { "id": "uuid", "text": "...", "dueAt": 1234567890 }
  ]
}
```

### delete_reminder

Удалить напоминание.

**Arguments:**
- `id` (string, required) — ID напоминания

**Returns:** `{ "status": "deleted" }`

### search_docs (deprecated)

**ВНИМАНИЕ:** Этот tool больше не используется для основного чата. RAG теперь выполняется автоматически на каждое сообщение пользователя.

Поиск по локальной документации (RAG).

**Arguments:**
- `query` (string, required) — поисковый запрос

**Returns:**
```json
{
  "results": [
    {
      "source": "README.md",
      "chunk": 2,
      "text": "Фрагмент документа...",
      "score": 0.85
    }
  ]
}
```

### get_git_branch

Получить название текущей git ветки.

**Arguments:** нет

**Returns:** `{ "branch": "main" }`
