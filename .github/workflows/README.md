# GitHub Actions Workflows

## Auto RAG Reindex (rag-reindex.yml)

Автоматическая переиндексация RAG документации при изменении файлов в `project/docs/`.

### Требования

1. **Сервер должен быть запущен и доступен**
   - Локально: `./gradlew :server:run` (порт 8080)
   - Production: ваш деплой сервер должен быть доступен по URL

2. **GitHub Secret: `RAG_SERVER_URL`**
   - Настройки репозитория → Settings → Secrets and variables → Actions
   - Нажмите "New repository secret"
   - Name: `RAG_SERVER_URL`
   - Value: `http://localhost:8080` (dev) или `https://your-server.com` (prod)

### Тестирование локально

Перед запуском workflow на GitHub, протестируйте вручную:

```bash
# 1. Запустите сервер
./gradlew :server:run

# 2. В другом терминале проверьте health
curl http://localhost:8080/health

# Ожидаемый ответ:
# {"status":"OK","service":"AI Challenge KMP","timestamp":1234567890}

# 3. Протестируйте индексацию
curl -X POST http://localhost:8080/api/rag/index/docs

# Ожидаемый ответ:
# {"status":"ok","indexed":[{"sourceId":"docs/faq","chunks":12},...]}
```

### Тестирование workflow на GitHub

**Вариант 1: Ручной запуск**

1. Перейдите в **Actions** → **Auto RAG Reindex**
2. Нажмите **Run workflow** → выберите ветку → **Run workflow**
3. Наблюдайте за выполнением в логах

**Вариант 2: Автоматический (триггер на изменения)**

1. Измените любой `.md` файл в `project/docs/`
2. Закоммитьте и запушьте изменения
3. Workflow запустится автоматически
4. Проверьте результат в **Actions**

### Проверка результатов

После успешного выполнения:

1. Логи покажут список проиндексированных файлов:
   ```
   ✓ docs/faq → 12 chunks
   ✓ docs/architecture → 8 chunks
   ```

2. Артефакт `rag-reindex-results` будет доступен 7 дней
   - Actions → конкретный run → Artifacts → скачайте JSON

3. На сервере проверьте что RAG работает:
   ```bash
   # Через ChatService с tool search_docs
   # или напрямую через /api/rag/ask
   ```

### Устранение проблем

**Ошибка: "Сервер недоступен (HTTP 000)"**
- Проверьте что сервер запущен
- Проверьте правильность URL в `RAG_SERVER_URL`
- Проверьте сетевую доступность (firewall, VPN)

**Ошибка: "HTTP 500" при индексации**
- Проверьте что Ollama запущен (`ollama serve`)
- Проверьте что модель загружена (`ollama pull nomic-embed-text`)
- Проверьте логи сервера на наличие ошибок

**Workflow не запускается автоматически**
- Проверьте что изменения в `project/docs/**/*.md`
- Проверьте что workflow файл в `.github/workflows/` корректен
- Проверьте что Actions включены в настройках репозитория

### Следующие шаги

1. ✅ Workflow создан и задокументирован
2. ⏺️ Добавьте `RAG_SERVER_URL` в GitHub Secrets
3. ⏺️ Протестируйте локально
4. ⏺️ Протестируйте на GitHub через ручной запуск
5. ⏺️ Измените любой .md файл → проверьте автоматический триггер
