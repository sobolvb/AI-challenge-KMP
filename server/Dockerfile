# Dockerfile для Ktor сервера
FROM eclipse-temurin:21-jre-alpine

# Установка необходимых пакетов
RUN apk add --no-cache curl

# Рабочая директория
WORKDIR /app

# Копируем собранный JAR
COPY build/libs/*-all.jar app.jar

# Переменные окружения
ENV JAVA_OPTS="-Xmx2g -Xms512m"
ENV KTOR_ENV="production"

# Открываем порт
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
