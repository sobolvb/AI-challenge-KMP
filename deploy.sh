#!/bin/bash
set -e

# Цвета для вывода
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   AI Challenge KMP - Deploy Script   ${NC}"
echo -e "${BLUE}========================================${NC}"

# Проверка Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker не установлен${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Docker найден${NC}"

# 1. Сборка JAR
echo -e "\n${BLUE}📦 Шаг 1: Сборка Ktor сервера...${NC}"
./gradlew :server:buildFatJar

if [ ! -f "server/build/libs/server-all.jar" ]; then
    echo -e "${RED}❌ JAR файл не найден${NC}"
    exit 1
fi

echo -e "${GREEN}✅ JAR собран: server/build/libs/server-all.jar${NC}"

# 2. Остановка старых контейнеров
echo -e "\n${BLUE}🛑 Шаг 2: Остановка старых контейнеров...${NC}"
docker-compose down || true

# 3. Сборка Docker образов
echo -e "\n${BLUE}🐳 Шаг 3: Сборка Docker образов...${NC}"
docker-compose build --no-cache

# 4. Запуск контейнеров
echo -e "\n${BLUE}🚀 Шаг 4: Запуск контейнеров...${NC}"
docker-compose up -d

# 5. Ожидание готовности Ollama
echo -e "\n${BLUE}⏳ Шаг 5: Ожидание запуска Ollama...${NC}"
sleep 10

# 6. Загрузка моделей в Ollama
echo -e "\n${BLUE}📥 Шаг 6: Загрузка моделей в Ollama...${NC}"
echo "Это может занять несколько минут при первом запуске..."

docker exec ai-ollama ollama pull qwen2.5:14b
docker exec ai-ollama ollama pull nomic-embed-text

# 7. Проверка статуса
echo -e "\n${BLUE}📊 Шаг 7: Проверка статуса сервисов...${NC}"
docker-compose ps

# 8. Проверка health check
echo -e "\n${BLUE}🏥 Шаг 8: Проверка health check...${NC}"
sleep 5

# Проверка Ollama
if curl -s http://localhost:11434/api/tags > /dev/null; then
    echo -e "${GREEN}✅ Ollama работает${NC}"
else
    echo -e "${RED}❌ Ollama не отвечает${NC}"
fi

# Проверка Ktor сервера
if curl -s http://localhost:8080/health > /dev/null; then
    echo -e "${GREEN}✅ Ktor сервер работает${NC}"
else
    echo -e "${RED}❌ Ktor сервер не отвечает${NC}"
fi

# 9. Вывод логов
echo -e "\n${BLUE}📋 Логи контейнеров:${NC}"
echo -e "${BLUE}--------------------${NC}"
docker-compose logs --tail=20

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}   ✅ Деплой завершен!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${BLUE}📍 Доступные эндпоинты:${NC}"
echo -e "   • Ktor Server: ${GREEN}http://localhost:8080${NC}"
echo -e "   • Ollama API:  ${GREEN}http://localhost:11434${NC}"
echo -e "   • Health:      ${GREEN}http://localhost:8080/health${NC}"
echo -e "\n${BLUE}📝 Полезные команды:${NC}"
echo -e "   • Логи:        ${GREEN}docker-compose logs -f${NC}"
echo -e "   • Остановка:   ${GREEN}docker-compose down${NC}"
echo -e "   • Перезапуск:  ${GREEN}docker-compose restart${NC}"
echo -e "   • Статус:      ${GREEN}docker-compose ps${NC}"
