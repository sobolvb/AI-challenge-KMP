# Деплой AI Challenge KMP на удаленный сервер

## День 27: Локальная LLM на VPS/удаленном ПК

Этот документ описывает процесс развертывания Ktor сервера с Ollama на удаленной машине (VPS или второй ПК в локальной сети).

---

## Архитектура

```
Ноутбук (Desktop Client)
    ↓ HTTP
Удаленный сервер (VPS/второй ПК)
    ├─ Docker: Ktor Server (:8080)
    │   └─ База данных SQLite
    └─ Docker: Ollama (:11434)
        ├─ Qwen 2.5 14B
        └─ nomic-embed-text
```

---

## Вариант 1: Автоматический деплой (рекомендуется)

### На удаленном сервере:

```bash
# 1. Клонировать репозиторий
git clone <your-repo-url>
cd AIChallengeKMP

# 2. Запустить автоматический деплой
./deploy.sh
```

Скрипт автоматически:
- ✅ Соберет JAR файл
- ✅ Создаст Docker образы
- ✅ Запустит Ollama и Ktor сервер
- ✅ Загрузит необходимые модели

---

## Вариант 2: Ручной деплой

### Шаг 1: Установка Docker (если еще не установлен)

```bash
# На Ubuntu/Debian
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# Выйти и войти заново для применения прав

# Проверка
docker --version
docker-compose --version
```

### Шаг 2: Сборка JAR файла

**На локальной машине (где проект):**

```bash
./gradlew :server:buildFatJar
```

JAR будет создан: `server/build/libs/server-all.jar`

### Шаг 3: Копирование проекта на сервер

```bash
# Замените USER и SERVER_IP на свои значения
scp -r . user@192.168.1.100:~/AIChallengeKMP
```

Или через git:

```bash
# На сервере
git clone <your-repo-url>
cd AIChallengeKMP
```

### Шаг 4: Запуск через Docker Compose

**На удаленном сервере:**

```bash
# Запуск
docker-compose up -d

# Загрузка моделей в Ollama (первый запуск)
docker exec ai-ollama ollama pull qwen2.5:14b
docker exec ai-ollama ollama pull nomic-embed-text

# Проверка статуса
docker-compose ps
docker-compose logs -f
```

---

## Настройка клиента для подключения к удаленному серверу

### Вариант A: Через переменную окружения

```bash
# Установить URL сервера
export SERVER_URL=http://192.168.1.100:8080

# Запустить клиент
./gradlew :composeApp:run
```

### Вариант B: Через System property

```bash
./gradlew :composeApp:run -DSERVER_URL=http://192.168.1.100:8080
```

### Вариант C: Постоянно (изменить код)

В `shared/src/jvmMain/kotlin/com/aichallengekmp/chat/ServerUrl.jvm.kt`:

```kotlin
actual fun getServerUrl(): String {
    return "http://192.168.1.100:8080"  // IP вашего сервера
}
```

---

## Конфигурация

### Environment Variables (docker-compose.yml)

Можно настроить через `docker-compose.yml` или создать `.env` файл:

```env
# .env файл в корне проекта
SERVER_URL=http://192.168.1.100:8080
OLLAMA_BASE_URL=http://ollama:11434
YANDEX_GPT_API_KEY=your_key_here
YANDEX_GPT_FOLDER_ID=your_folder_id
```

### Порты

По умолчанию используются:
- **8080** - Ktor сервер
- **11434** - Ollama

Для изменения отредактируйте `docker-compose.yml`:

```yaml
services:
  ktor-server:
    ports:
      - "9090:8080"  # Внешний:Внутренний
```

---

## Firewall и безопасность

### Открытие портов (Ubuntu/Debian)

```bash
# Разрешить доступ к портам
sudo ufw allow 8080/tcp
sudo ufw allow 11434/tcp
sudo ufw enable

# Проверка
sudo ufw status
```

### Доступ только из локальной сети

Если хотите ограничить доступ только локальной сетью:

```bash
sudo ufw allow from 192.168.1.0/24 to any port 8080
sudo ufw allow from 192.168.1.0/24 to any port 11434
```

---

## Мониторинг и управление

### Полезные команды

```bash
# Просмотр логов
docker-compose logs -f                    # Все сервисы
docker-compose logs -f ktor-server       # Только Ktor
docker-compose logs -f ollama            # Только Ollama

# Статус контейнеров
docker-compose ps

# Перезапуск
docker-compose restart

# Остановка
docker-compose down

# Остановка с удалением volumes (БД и модели)
docker-compose down -v

# Обновление после изменений кода
./deploy.sh
```

### Health Checks

Проверка работоспособности:

```bash
# Ktor сервер
curl http://localhost:8080/health

# Ollama
curl http://localhost:11434/api/tags

# Список моделей в Ollama
docker exec ai-ollama ollama list
```

---

## Требования к серверу

### Минимальные

- **CPU**: 4 ядра
- **RAM**: 16 GB (для Qwen 2.5 14B)
- **Диск**: 20 GB свободного места
- **ОС**: Linux (Ubuntu 22.04+ рекомендуется)

### Рекомендуемые

- **CPU**: 8+ ядер или GPU (CUDA)
- **RAM**: 32 GB
- **Диск**: 50 GB SSD
- **GPU**: NVIDIA с 8GB+ VRAM (опционально, но ускорит в 10-100 раз)

---

## С GPU (опционально)

Для использования GPU в Docker:

### 1. Установить NVIDIA Container Toolkit

```bash
distribution=$(. /etc/os-release;echo $ID$VERSION_ID)
curl -s -L https://nvidia.github.io/nvidia-docker/gpgkey | sudo apt-key add -
curl -s -L https://nvidia.github.io/nvidia-docker/$distribution/nvidia-docker.list | \
  sudo tee /etc/apt/sources.list.d/nvidia-docker.list

sudo apt-get update
sudo apt-get install -y nvidia-container-toolkit
sudo systemctl restart docker
```

### 2. Изменить docker-compose.yml

```yaml
services:
  ollama:
    image: ollama/ollama:latest
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
```

---

## Troubleshooting

### Проблема: Ollama не загружается

```bash
# Проверить логи
docker-compose logs ollama

# Проверить доступную память
free -h

# Попробовать меньшую модель
docker exec ai-ollama ollama pull qwen2.5:7b
```

### Проблема: Ktor сервер не подключается к Ollama

```bash
# Проверить что Ollama работает
docker exec ai-ollama ollama list

# Проверить сеть Docker
docker network inspect aichallengekmp_ai-network
```

### Проблема: Клиент не может подключиться

```bash
# Проверить firewall
sudo ufw status

# Проверить что порт открыт
netstat -tlnp | grep 8080

# Проверить из клиента
curl http://SERVER_IP:8080/health
```

---

## VPS провайдеры (с GPU)

Если нужен настоящий VPS:

### Бюджетные с GPU

- **RunPod** - от $0.20/час, гибкая аренда
- **Vast.ai** - от $0.15/час, маркетплейс GPU
- **Lambda Labs** - от $0.60/час, специализация на ML

### Стандартные VPS (без GPU)

- **Hetzner Cloud** - от €5/мес, 16GB RAM
- **DigitalOcean** - от $48/мес, 16GB RAM
- **Linode** - от $48/мес, 16GB RAM

**⚠️ Без GPU модель будет работать МЕДЛЕННО (5-10x медленнее)**

---

## Следующие шаги

1. ✅ Развернуть на удаленной машине
2. ⬜ Настроить HTTPS (Let's Encrypt + Nginx)
3. ⬜ Добавить аутентификацию
4. ⬜ Настроить автоматические бэкапы БД
5. ⬜ Мониторинг (Prometheus + Grafana)

---

## Поддержка

При проблемах проверьте:
1. Логи: `docker-compose logs -f`
2. Health checks: `curl http://localhost:8080/health`
3. Доступность портов: `sudo ufw status`
4. Память: `free -h` и `docker stats`
