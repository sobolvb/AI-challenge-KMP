package com.aichallengekmp.chat

/**
 * JVM реализация получения URL сервера
 * Читает из переменной окружения SERVER_URL или System property
 * Если не задано - использует localhost
 */
actual fun getServerUrl(): String {
    // Приоритет: System property > Environment variable > Default
    return System.getProperty("SERVER_URL")
        ?: System.getenv("SERVER_URL")
        ?: "http://localhost:8080"
}
