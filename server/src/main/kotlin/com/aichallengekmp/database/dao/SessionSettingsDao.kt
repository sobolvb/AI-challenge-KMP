package com.aichallengekmp.database.dao

import com.aichallengekmp.database.AppDatabase
import com.aichallengekmp.database.SessionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * DAO для работы с настройками сессии
 */
class SessionSettingsDao(private val database: AppDatabase) {
    private val logger = LoggerFactory.getLogger(SessionSettingsDao::class.java)
    private val queries = database.sessionSettingsQueries
    
    /**
     * Получить настройки сессии
     */
    suspend fun getBySessionId(sessionId: String): SessionSettings? = withContext(Dispatchers.IO) {
        logger.debug("⚙️ Запрос настроек сессии: $sessionId")
        queries.selectBySessionId(sessionId).executeAsOneOrNull()
    }
    
    /**
     * Создать настройки для сессии
     */
    suspend fun insert(settings: SessionSettings) = withContext(Dispatchers.IO) {
        logger.info("➕ Создание настроек для сессии: ${settings.sessionId}")
        logger.debug("   Model: ${settings.modelId}, Temp: ${settings.temperature}, MaxTokens: ${settings.maxTokens}")
        queries.insert(
            sessionId = settings.sessionId,
            modelId = settings.modelId,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            compressionThreshold = settings.compressionThreshold,
            systemPrompt = settings.systemPrompt
        )
    }
    
    /**
     * Обновить настройки сессии
     */
    suspend fun update(settings: SessionSettings) = withContext(Dispatchers.IO) {
        logger.info("✏️ Обновление настроек сессии: ${settings.sessionId}")
        logger.debug("   Model: ${settings.modelId}, Temp: ${settings.temperature}, MaxTokens: ${settings.maxTokens}")
        queries.update(
            modelId = settings.modelId,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            compressionThreshold = settings.compressionThreshold,
            systemPrompt = settings.systemPrompt,
            sessionId = settings.sessionId
        )
    }
    
    /**
     * Удалить настройки сессии
     */
    suspend fun delete(sessionId: String) = withContext(Dispatchers.IO) {
        logger.warn("🗑️ Удаление настроек сессии: $sessionId")
        queries.delete(sessionId)
    }
}
