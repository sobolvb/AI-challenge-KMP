# AI Challenge KMP

Kotlin Multiplatform‑проект с чат‑клиентом (Compose Desktop) и Ktor‑сервером, который использует:

- **YandexGPT** как LLM (генерация текста + function calling),
- **SQLDelight + SQLite** как хранилище сессий и RAG‑индекса,
- **Ollama** как локальный/удаленный провайдер LLM (Qwen 2.5 14B) и эмбеддингов (nomic-embed-text),
- **RAG‑слой** для поиска по локальной документации (в т.ч. по этому `README.md`),
- **инструменты (tools)** для работы с Яндекс.Трекером, напоминаниями и поиском по документам.
- **Docker + Docker Compose** для развертывания на VPS/удаленном сервере.

## Структура проекта

- [`/composeApp`](./composeApp/src)
  - KMP‑код клиентского приложения (Compose Multiplatform, Desktop и др. таргеты).
  - Основная бизнес‑логика UI и сетевое общение с сервером.

- [`/server`](./server/src/main/kotlin)
  - Ktor 3.x‑сервер, точка входа: `com.aichallengekmp.ApplicationKt`.
  - DI без Koin – manual DI через `AppContainer` (`AppModule.kt`).
  - Основные модули:
    - `ai/` – интеграция с YandexGPT (`YandexGPTProvider`, `ModelRegistry`).
    - `database/` – SQLDelight, SQLite, DAO.
    - `service/` – доменные сервисы (`ChatService`, `CompressionService`, `ReminderService`).
    - `routing/` – HTTP‑роуты (`chatRoutes`, `ragRoutes`).
    - `tools/` – описание и исполнение инструментов (tools) для LLM.
    - `rag/` – RAG‑слой, работающий поверх локальных эмбеддингов Ollama и SQLite.
    - `mcp/` – MCP‑серверы, экспортирующие инструменты наружу.

- [`/shared`](./shared/src)
  - Общие модели и логику между клиентом и сервером.
  - Основная точка интереса: `commonMain`.

## Сервер: архитектура

### DI и конфигурация

- Конфигурация и DI находятся в `server/src/main/kotlin/com/aichallengekmp/di/AppModule.kt` (`AppContainer`).
- Здесь инициализируются:
  - `httpClient` (Ktor client) – общий для YandexGPT и Ollama.
  - `database` (SQLite через SQLDelight).
  - DAO: `SessionDao`, `MessageDao`, `SessionSettingsDao`, `CompressionDao`, `ReminderDao`, `RagChunkDao`.
  - Сервисы: `ChatService`, `CompressionService`, `ReminderService`, `RagIndexService`, `RagSearchService`.
  - `modelRegistry` и `YandexGPTProvider` с поддержкой function calling.
  - `toolExecutor` – сейчас используется `LocalToolExecutor` (инструменты выполняются локально, без MCP‑клиента).

### База данных

Используется **SQLDelight + SQLite**. Схема описана в `server/src/main/sqldelight/com/aichallengekmp/database`.

Основные таблицы:

- `Session`, `Message`, `SessionSettings` – структура чат‑сессий и сообщений.
- `SessionCompression` – хранение сжатых резюме истории.
- `Reminder` – напоминания.
- `DocumentChunk` – RAG‑индекс (см. раздел ниже).

Инициализация БД – в `DatabaseFactory`. При старте:

- Если файл БД не существует – создаётся схема SQLDelight.
- Если схема обновилась – выполняется временная миграция, в том числе создающая `Reminder` и `DocumentChunk`, если их нет.

### ChatService и YandexGPT

`ChatService` реализует API чата (создание сессий, отправка сообщений, изменение настроек). 

YandexGPT используется через `YandexGPTProvider`:

- Коммуникация идёт на `https://llm.api.cloud.yandex.net/foundationModels/v1/completion`.
- Поддерживается **function calling**: провайдер получает список `tools` и может несколько раз вызывать инструменты до финального ответа.
- Результаты tool‑вызовов добавляются в историю сообщений и учитываются при генерации окончательного ответа.

Список инструментов формируется в `TrackerToolsService.getAvailableTools()` и передаётся в `ChatService.generateResponseWithTools()`.

### Инструменты (tools) для YandexGPT

На данный момент доступны инструменты:

- `get_issues_count` – получить количество задач в Яндекс.Трекере.
- `get_all_issue_names` – список задач с ключами и названиями.
- `get_issue_info` – подробная информация о задаче по ключу.
- `create_reminder` – создать напоминание.
- `list_reminders` – список напоминаний.
- `delete_reminder` – удалить напоминание.
- `search_docs` – **поиск по локальному индексу документов (RAG) и возврат релевантных фрагментов**.

Выполнение этих инструментов на сервере реализовано через `LocalToolExecutor`.

## RAG‑слой

RAG‑слой полностью живёт на сервере и использует:

- Ollama `/api/embeddings` (модель `nomic-embed-text`) для генерации эмбеддингов.
- SQLite (таблица `DocumentChunk`) как хранилище векторного индекса.
- YandexGPT знает только про инструмент `search_docs` и не взаимодействует с Ollama напрямую.

### Таблица DocumentChunk

Определена в `server/src/main/sqldelight/com/aichallengekmp/database/DocumentChunk.sq`:

- `id INTEGER PRIMARY KEY AUTOINCREMENT`
- `sourceId TEXT NOT NULL` – идентификатор документа (например, `"README.md"`).
- `chunkIndex INTEGER NOT NULL` – порядковый номер чанка в документе.
- `text TEXT NOT NULL` – текст чанка.
- `embedding TEXT NOT NULL` – JSON‑строка с массивом `Float` (`"[0.12,-0.34,...]"`).

Работу с таблицей инкапсулирует `RagChunkDao`.

### OllamaEmbeddingsProvider

`EmbeddingsProvider` реализован через `OllamaEmbeddingsProvider`:

- Делает POST на `http://localhost:11434/api/embeddings` с телом вида:

  ```json
  { "model": "nomic-embed-text", "prompt": "<текст>" }
  ```

- Парсит ответ (поддерживает разные варианты структуры, где есть поле `embedding` или `embeddings`).
- Возвращает `FloatArray` эмбеддингов.

Ollama‑URL и имя модели можно переопределить через ENV:

- `OLLAMA_BASE_URL` (по умолчанию `http://localhost:11434`)
- `OLLAMA_EMBED_MODEL` (по умолчанию `nomic-embed-text`)

### Индексация документов: RagIndexService

`RagIndexService` отвечает за индексацию (RAG‑индекс):

1. Удаляет старые чанки документа по `sourceId`.
2. Разбивает текст на перекрывающиеся чанки по ~200–300 слов с overlap ~50 слов.
3. Для каждого чанка запрашивает эмбеддинг у Ollama.
4. Сохраняет чанки в `DocumentChunk` через `RagChunkDao`.

#### HTTP‑эндпоинт индексации README

Роуты для RAG находятся в `server/src/main/kotlin/com/aichallengekmp/routing/RagRoutes.kt`.

Эндпоинт:

- `POST /api/rag/index/readme`
  - Читает файл `README.md` из корня репозитория.
  - Вызывает `AppContainer.ragIndexService.indexDocument("README.md", readmeText)`.
  - Считает количество чанков по этому `sourceId` и возвращает JSON с полями `status`, `sourceId`, `chunks`.

### Поиск по документам: RagSearchService и tool search_docs

`RagSearchService` выполняет сам поиск:

1. Получает эмбеддинг запроса у Ollama.
2. Загружает все чанки через `RagChunkDao`.
3. Для каждого чанка считает косинусное сходство с запросом.
4. Сортирует по убыванию и возвращает `topK` лучших результатов (`RagHit`).

Инструмент `search_docs` в `LocalToolExecutor` использует `RagSearchService` и форматирует ответ:

- Если ничего не найдено – возвращает фразу вида: «По вашему запросу ничего не найдено в локальном индексе документов».
- Если фрагменты есть – перечисляет источники, номера чанков и сами тексты.

YandexGPT видит только сам инструмент `search_docs` и его описание, а о реализации RAG/векторах/Ollama не знает.

## Запуск локально

### Предварительные требования

- JDK 17+.
- Gradle Wrapper (идёт в репозитории).
- Установленный **Ollama** и загруженная модель эмбеддингов:

  ```shell
  ollama pull nomic-embed-text
  ollama serve
  ```

### Запуск сервера

Из корня проекта:

```shell
./gradlew :server:run
```

Сервер по умолчанию поднимается на `http://localhost:8080`.

Полезные эндпоинты:

- Чат‑API: `/api/sessions/...` (используется клиентом, протокол менять не нужно).
- Индексация README для RAG:

  ```shell
  curl -X POST http://localhost:8080/api/rag/index/readme
  ```

### Запуск Desktop‑клиента

```shell
./gradlew :composeApp:run
```

Desktop приложение автоматически определяет режим работы:

- **Online Mode**: Если сервер доступен на `http://localhost:8080` – клиент общается с сервером по REST `/api/sessions/...` и получает доступ ко всем функциям (YandexGPT, RAG, tools, история сессий).
- **Offline Mode**: Если сервер недоступен – приложение переключается в офлайн режим с прямым подключением к Ollama (см. ниже).

### Офлайн режим (без сервера)

Приложение может работать **полностью автономно** без сервера, напрямую подключаясь к локальной Ollama.

#### Запуск в офлайн режиме

1. Убедитесь что Ollama запущена:
   ```bash
   ollama serve
   ```

2. Проверьте что модель установлена:
   ```bash
   ollama list
   # Должна быть qwen2.5:14b
   ```

3. Запустите ТОЛЬКО Desktop приложение (БЕЗ сервера):
   ```bash
   ./gradlew :composeApp:run
   ```

4. Приложение автоматически определит что сервер недоступен и включит **Offline Mode** ✅

#### Что работает в Offline режиме

- ✅ Прямой чат с Qwen 2.5 14B (локальная модель)
- ✅ История сообщений в рамках текущей сессии
- ✅ Полная автономность (работает без интернета)

#### Что НЕ работает в Offline режиме

- ❌ YandexGPT и другие облачные модели
- ❌ RAG поиск по документации
- ❌ Tools (задачи из Яндекс.Трекера, напоминания, поиск по документам)
- ❌ Сохранение сессий в БД (история не сохраняется между перезапусками)

#### Архитектура офлайн режима

```
Desktop App (Compose)
  ├─ Online Mode (если localhost:8080 доступен)
  │   └─ HTTP клиент → Ktor Server → все фичи
  │
  └─ Offline Mode (если localhost:8080 НЕ доступен)
      └─ Прямой HTTP клиент → Ollama (localhost:11434)
          - Простой чат с одной сессией
          - Без RAG, без tools, без истории
```

Офлайн режим полезен когда:
- Нужно быстро протестировать локальную модель без настройки сервера
- Отсутствует подключение к интернету (для YandexGPT)
- Требуется максимально простой режим работы

### Развертывание на VPS/удаленном сервере (День 27)

Проект поддерживает развертывание на удаленной машине через Docker Compose.

#### Быстрый старт

**На удаленном сервере/втором ПК:**

```bash
# 1. Клонировать проект
git clone <repo-url>
cd AIChallengeKMP

# 2. Запустить автоматический деплой
./deploy.sh
```

Скрипт автоматически:
- Соберет JAR сервера
- Создаст Docker образы для Ktor сервера и Ollama
- Запустит контейнеры
- Загрузит необходимые модели

**На клиенте (ноутбук):**

```bash
# Указать URL удаленного сервера
export SERVER_URL=http://192.168.1.100:8080

# Запустить клиент
./gradlew :composeApp:run
```

#### Архитектура с удаленным сервером

```
Ноутбук (Desktop Client)
    ↓ HTTP по сети
Удаленный ПК/VPS (192.168.1.100)
    ├─ Docker: Ktor Server (:8080)
    │   └─ SQLite база данных
    └─ Docker: Ollama (:11434)
        ├─ Qwen 2.5 14B
        └─ nomic-embed-text
```

**Подробная документация:** См. [DEPLOY.md](./DEPLOY.md)

**Что получаем:**
- ✅ Удаленная работа с мощной машиной
- ✅ Централизованное хранилище сессий
- ✅ Доступ с любого устройства
- ✅ Простое развертывание через Docker

## Как это работает вместе (пример)

1. Ты обновляешь `README.md` (этот файл) под актуальную архитектуру.
2. Вызываешь `POST /api/rag/index/readme` – сервер разбивает README на чанки, получает эмбеддинги из Ollama и сохраняет их в SQLite.
3. В чате пишешь запрос, например: «Найди в документации информацию про RAG в этом проекте и объясни мне её».
4. YandexGPT видит в system‑prompt и списке tools инструмент `search_docs` и решает его вызвать.
5. `search_docs` вызывает `RagSearchService.search(...)`, который возвращает релевантные чанки из README.
6. Эти фрагменты подставляются в контекст диалога как результат инструмента, и на их основе YandexGPT формирует финальный ответ.

Таким образом, после обновления этого README и повторной индексации, LLM сможет давать ответы, опираясь на актуальное описание проекта.

## GitHub Actions: Автоматизация

### Автоматическая переиндексация RAG документации

Проект использует GitHub Actions для автоматической переиндексации документации при её изменении.

**Workflow:** `.github/workflows/rag-reindex.yml`

**Триггеры:**
- Автоматически при push изменений в `project/docs/**/*.md` (любая ветка)
- Ручной запуск через `workflow_dispatch` (вкладка Actions на GitHub)

**Что делает:**
1. Проверяет здоровье сервера через `GET /health`
2. Вызывает `POST /api/rag/index/docs` для переиндексации всех `.md` файлов из `project/docs/`
3. Отображает результаты (количество проиндексированных файлов и chunks)
4. Сохраняет результаты как артефакт (доступен 7 дней)

**Настройка:**

1. Добавьте GitHub Secret `RAG_SERVER_URL` в настройках репозитория:
   - Перейдите в **Settings → Secrets and variables → Actions → New repository secret**
   - Имя: `RAG_SERVER_URL`
   - Значение: URL вашего сервера (например, `http://localhost:8080` для dev или `https://your-server.com` для production)

2. Убедитесь что сервер доступен и запущен (должен отвечать на `/health`)

**Ручной запуск:**

```bash
# Через GitHub UI: Actions → Auto RAG Reindex → Run workflow

# Или через GitHub CLI:
gh workflow run rag-reindex.yml
```

**Доступные RAG endpoints:**
- `POST /api/rag/index/readme` – индексация README.md
- `POST /api/rag/index/docs` – индексация всех .md файлов из project/docs/
- `POST /api/rag/index/code` – индексация исходного кода (.kt файлы)

**Пример ручного вызова индексации документации:**

```shell
curl -X POST http://localhost:8080/api/rag/index/docs
```

Ответ:
```json
{
  "status": "ok",
  "indexed": [
    {"sourceId": "docs/faq", "chunks": 12},
    {"sourceId": "docs/architecture", "chunks": 8}
  ]
}
```