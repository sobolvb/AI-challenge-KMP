# День 21: Автоматизация ревью кода

## Описание

Реализована система автоматического ревью кода в Pull Request с использованием:
- **MCP (Model Context Protocol)** - для получения данных о PR через Git/GitHub
- **RAG (Retrieval Augmented Generation)** - для поиска релевантного кода и code style guidelines
- **YandexGPT** - для генерации структурированного ревью кода
- **GitHub Actions** - для автоматического запуска при создании/обновлении PR

## Архитектура решения

### 1. Git MCP Server (`/mcp/git`)

Реализован MCP сервер с инструментами для работы с Git/GitHub:

- `git_get_pr_diff` - получить diff изменений PR
- `git_get_changed_files` - список измененных файлов
- `git_get_file_content` - содержимое файла из репозитория
- `github_get_pr_info` - метаданные PR (title, description, author, status)

**Расположение**: `server/src/main/kotlin/com/aichallengekmp/mcp/McpServerSetup.kt:433`

**Реализация**: `server/src/main/kotlin/com/aichallengekmp/tools/GitToolsService.kt`

### 2. RAG индексация кода

Добавлен endpoint для индексации исходного кода проекта:

**Endpoint**: `POST /api/rag/index/code`

**Функционал**:
- Индексирует `.kt` файлы из `server/src/main/kotlin` и `shared/src/commonMain/kotlin`
- Исключает `build/`, `.gradle/`, test файлы
- Чанкует код по ~100-150 строк (оптимально для code review)
- Использует embeddings от Ollama для векторного поиска

**Реализация**:
- `server/src/main/kotlin/com/aichallengekmp/routing/RagRoutes.kt:208`
- `server/src/main/kotlin/com/aichallengekmp/rag/RagIndexService.kt:91` (метод `indexCodeFile`)

### 3. CodeReviewService

Центральный сервис для анализа PR:

**Расположение**: `server/src/main/kotlin/com/aichallengekmp/service/CodeReviewService.kt`

**Процесс анализа**:

1. Получение PR data через MCP:
   - Метаданные PR (github_get_pr_info)
   - Diff изменений (git_get_pr_diff)
   - Список измененных файлов (git_get_changed_files)

2. Поиск релевантного контекста через RAG:
   - Релевантные фрагменты кода из проекта (по именам измененных файлов)
   - Code style guidelines из документации

3. Формирование промптов:
   - System prompt с инструкциями для ревью
   - User prompt с PR данными + RAG контекстом

4. Вызов YandexGPT:
   - Temperature = 0.3 (низкая для консистентности)
   - MaxTokens = 4000
   - Без tools (только контекст + генерация)

5. Парсинг и структурирование результата:
   - Общая оценка
   - Критические проблемы
   - Предупреждения
   - Рекомендации
   - Использованные источники

**System prompt** проверяет:
- Баги (null safety, race conditions, resource leaks)
- Безопасность (SQL injection, XSS, command injection)
- Архитектуру (SOLID, separation of concerns)
- Performance (inefficient algorithms, blocking operations)
- Code style (naming, readability, guidelines compliance)
- Testability

### 4. Code Review API

**Endpoint**: `POST /api/code-review/analyze`

**Request**:
```json
{
  "prNumber": "123",
  "repository": "owner/repo"  // опционально
}
```

**Response**:
```json
{
  "summary": "Общая оценка PR",
  "criticalIssues": [
    {
      "file": "path/to/File.kt",
      "line": 42,
      "description": "Описание проблемы"
    }
  ],
  "warnings": [...],
  "suggestions": [...],
  "usedSources": ["code/server/...", "docs/code-style"],
  "rawReview": "Полный текст ревью"
}
```

**Расположение**: `server/src/main/kotlin/com/aichallengekmp/routing/CodeReviewRoutes.kt`

### 5. GitHub Actions Workflow

**Расположение**: `.github/workflows/code-review.yml`

**Триггеры**:
- `pull_request` (opened, synchronize, reopened)

**Шаги**:
1. Checkout кода
2. Вызов API `/api/code-review/analyze`
3. Парсинг результата
4. Создание комментария в PR с результатами ревью

**Требуемые secrets**:
- `CODE_REVIEW_SERVER_URL` - URL сервера (например, `https://your-server.com` или ngrok URL)
- `GITHUB_TOKEN` - автоматически предоставляется GitHub Actions

## Настройка и запуск

### 1. Переменные окружения

Добавьте в `.env` или настройте через систему:

```bash
# Обязательные
YANDEX_API_KEY=your_api_key
YANDEX_MODEL_URI=your_model_uri
GITHUB_TOKEN=your_github_token  # Personal Access Token с правами repo

# Опциональные
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_EMBED_MODEL=nomic-embed-text
DB_PATH=chat.db
```

### 2. Запуск Ollama

Для RAG нужен Ollama с моделью embeddings:

```bash
# Установить Ollama
brew install ollama  # или с официального сайта

# Запустить сервер
ollama serve

# Загрузить модель embeddings (в другом терминале)
ollama pull nomic-embed-text
```

### 3. Индексация кода и документации

Перед первым использованием проиндексируйте код и документацию:

```bash
# Запустить сервер
./gradlew :server:run

# В другом терминале:

# Индексировать code style guidelines
curl -X POST http://localhost:8080/api/rag/index/docs

# Индексировать исходный код проекта
curl -X POST http://localhost:8080/api/rag/index/code
```

**Примечание**: Индексация кода может занять несколько минут в зависимости от размера проекта.

### 4. Настройка GitHub Actions

#### Локальное тестирование (ngrok)

Для локального тестирования используйте ngrok:

```bash
# Запустить ngrok
ngrok http 8080

# Скопируйте публичный URL (например, https://abc123.ngrok.io)
```

Добавьте в GitHub Secrets:
- `CODE_REVIEW_SERVER_URL` = `https://abc123.ngrok.io`

#### Production deployment

Для production разверните сервер на хостинге (AWS, GCP, DigitalOcean и т.д.) и укажите его URL в секрете.

### 5. Тестирование

#### Ручное тестирование через API

```bash
# Анализ конкретного PR
curl -X POST http://localhost:8080/api/code-review/analyze \
  -H "Content-Type: application/json" \
  -d '{"prNumber": "1", "repository": "owner/repo"}'
```

#### Тестирование через GitHub Actions

1. Создайте тестовый PR в вашем репозитории
2. GitHub Action автоматически запустится
3. Проверьте результаты в комментариях PR

## Примеры использования

### Пример 1: Локальный анализ PR

```bash
curl -X POST http://localhost:8080/api/code-review/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "prNumber": "42",
    "repository": "vlad/AIChallengeKMP"
  }' | jq
```

### Пример 2: Интеграция с CI/CD

```yaml
- name: Code Review
  run: |
    RESULT=$(curl -X POST $SERVER_URL/api/code-review/analyze \
      -H "Content-Type: application/json" \
      -d '{"prNumber": "${{ github.event.pull_request.number }}"}')

    CRITICAL_COUNT=$(echo "$RESULT" | jq '.criticalIssues | length')

    if [ "$CRITICAL_COUNT" -gt 0 ]; then
      echo "Found $CRITICAL_COUNT critical issues!"
      exit 1
    fi
```

## Ограничения и рекомендации

### Ограничения

1. **Размер diff**: Большие PR (>10000 символов diff) обрезаются для экономии токенов
2. **GitHub API rate limits**: При частых PR может достигаться лимит GitHub API
3. **Ollama performance**: Индексация большого кодебейса может быть медленной
4. **YandexGPT context window**: Очень большие PR могут не поместиться в context window

### Рекомендации

1. **Переиндексация**: Переиндексируйте код после значительных изменений
2. **Code style guidelines**: Создайте файл `docs/code-style.md` с вашими guidelines
3. **Chunking strategy**: Для больших файлов можно увеличить размер чанка (параметр `maxLinesPerChunk`)
4. **Temperature tuning**: Для более креативных ревью увеличьте temperature (сейчас 0.3)
5. **TopK adjustment**: Измените количество RAG результатов (сейчас topK=2-3)

## Troubleshooting

### Проблема: "GITHUB_TOKEN not set"

**Решение**: Установите переменную окружения:
```bash
export GITHUB_TOKEN=your_token
```

### Проблема: "Ollama not available"

**Решение**:
1. Проверьте что Ollama запущен: `curl http://localhost:11434/api/tags`
2. Проверьте что модель загружена: `ollama list`

### Проблема: "No indexed code found"

**Решение**: Проиндексируйте код:
```bash
curl -X POST http://localhost:8080/api/rag/index/code
```

### Проблема: GitHub Action не запускается

**Решение**:
1. Проверьте что workflow файл находится в `.github/workflows/`
2. Проверьте что секрет `CODE_REVIEW_SERVER_URL` установлен
3. Проверьте логи Actions в GitHub

## Дальнейшее развитие

Возможные улучшения:

1. **Кэширование**: Кэшировать индекс RAG для ускорения поиска
2. **Incremental indexing**: Индексировать только измененные файлы
3. **Multi-model support**: Добавить поддержку других LLM (GPT-4, Claude)
4. **Custom rules**: Позволить пользователям определять custom правила ревью
5. **Review history**: Сохранять историю ревью для анализа
6. **Auto-fix suggestions**: Генерировать конкретные fix коммиты
7. **Slack/Discord integration**: Отправлять уведомления о ревью
8. **Quality metrics**: Собирать метрики качества кода с течением времени

## Структура файлов

```
server/src/main/kotlin/com/aichallengekmp/
├── mcp/
│   └── McpServerSetup.kt         # Git MCP Server (configureGitMcpServer)
├── tools/
│   └── GitToolsService.kt        # Реализация Git инструментов
├── service/
│   └── CodeReviewService.kt      # Сервис анализа PR
├── routing/
│   ├── RagRoutes.kt              # RAG endpoints (включая /index/code)
│   └── CodeReviewRoutes.kt       # Code review endpoints
├── rag/
│   ├── RagIndexService.kt        # Индексация (indexCodeFile)
│   └── RagSearchService.kt       # Поиск по индексу
└── di/
    └── AppModule.kt              # DI контейнер (codeReviewService)

.github/workflows/
└── code-review.yml               # GitHub Actions workflow
```

## Заключение

Система автоматического ревью кода полностью реализована и готова к использованию. Она объединяет MCP для получения данных о PR, RAG для контекстуального поиска и YandexGPT для генерации ревью.

Ключевые преимущества:
- Автоматизация рутинного ревью
- Консистентность проверок
- Использование контекста проекта (code + guidelines)
- Структурированные результаты
- Интеграция с GitHub workflow
