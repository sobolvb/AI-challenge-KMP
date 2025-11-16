package com.aichallengekmp.database.dao

import com.aichallengekmp.database.AppDatabase
import com.aichallengekmp.database.SessionCompression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * DAO для работы с информацией о сжатии истории
 */
class CompressionDao(private val database: AppDatabase) {
    private val logger = LoggerFactory.getLogger(CompressionDao::class.java)
    private val queries = database.sessionCompressionQueries
    
    /**
     * Получить информацию о сжатии сессии
     */
    suspend fun getBySessionId(sessionId: String): SessionCompression? = withContext(Dispatchers.IO) {
        logger.debug("📦 Запрос информации о сжатии сессии: $sessionId")
        queries.selectBySessionId(sessionId).executeAsOneOrNull()
    }
    
    /**
     * Создать информацию о сжатии
     */
    suspend fun insert(compression: SessionCompression) = withContext(Dispatchers.IO) {
        logger.info("📦 Создание информации о сжатии для сессии: ${compression.sessionId}")
        logger.debug("   Индекс: ${compression.lastCompressionIndex}, Summary длина: ${compression.summary.length}")
        queries.insert(
            sessionId = compression.sessionId,
            summary = compression.summary,
            lastCompressionIndex = compression.lastCompressionIndex,
            compressedAt = compression.compressedAt
        )
    }
    
    /**
     * Обновить информацию о сжатии
     */
    suspend fun update(compression: SessionCompression) = withContext(Dispatchers.IO) {
        logger.info("📦 Обновление информации о сжатии сессии: ${compression.sessionId}")
        logger.debug("   Новый индекс: ${compression.lastCompressionIndex}")
        queries.update(
            summary = compression.summary,
            lastCompressionIndex = compression.lastCompressionIndex,
            compressedAt = compression.compressedAt,
            sessionId = compression.sessionId
        )
    }
    
    /**
     * Удалить информацию о сжатии
     */
    suspend fun delete(sessionId: String) = withContext(Dispatchers.IO) {
        logger.warn("🗑️ Удаление информации о сжатии сессии: $sessionId")
        queries.delete(sessionId)
    }
}
