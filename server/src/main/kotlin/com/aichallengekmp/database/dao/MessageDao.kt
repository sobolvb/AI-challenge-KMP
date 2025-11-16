package com.aichallengekmp.database.dao

import com.aichallengekmp.database.AppDatabase
import com.aichallengekmp.database.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * DAO для работы с сообщениями
 */
class MessageDao(private val database: AppDatabase) {
    private val logger = LoggerFactory.getLogger(MessageDao::class.java)
    private val queries = database.messageQueries
    
    /**
     * Получить все сообщения сессии
     */
    suspend fun getBySessionId(sessionId: String): List<Message> = withContext(Dispatchers.IO) {
        logger.debug("💬 Запрос сообщений сессии: $sessionId")
        queries.selectBySessionId(sessionId).executeAsList()
    }
    
    /**
     * Получить сообщения сессии с пагинацией
     * TODO: Реализовать пагинацию на клиенте в будущем
     */
    suspend fun getBySessionIdPaginated(
        sessionId: String,
        limit: Long,
        offset: Long
    ): List<Message> = withContext(Dispatchers.IO) {
        logger.debug("📄 Запрос сообщений сессии (пагинация): $sessionId, limit=$limit, offset=$offset")
        queries.selectBySessionIdPaginated(sessionId, limit, offset).executeAsList()
    }
    
    /**
     * Получить количество сообщений в сессии
     */
    suspend fun countBySessionId(sessionId: String): Long = withContext(Dispatchers.IO) {
        queries.countBySessionId(sessionId).executeAsOne()
    }
    
    /**
     * Добавить сообщение
     */
    suspend fun insert(message: Message) = withContext(Dispatchers.IO) {
        logger.info("💬 Добавление сообщения: ${message.role} в сессию ${message.sessionId}")
        queries.insert(
            id = message.id,
            sessionId = message.sessionId,
            role = message.role,
            content = message.content,
            modelId = message.modelId,
            inputTokens = message.inputTokens,
            outputTokens = message.outputTokens,
            createdAt = message.createdAt
        )
    }
    
    /**
     * Удалить все сообщения сессии
     */
    suspend fun deleteBySessionId(sessionId: String) = withContext(Dispatchers.IO) {
        logger.warn("🗑️ Удаление всех сообщений сессии: $sessionId")
        queries.deleteBySessionId(sessionId)
    }
    
    /**
     * Получить последнее сообщение сессии
     */
    suspend fun getLastMessage(sessionId: String): Message? = withContext(Dispatchers.IO) {
        logger.debug("📝 Запрос последнего сообщения сессии: $sessionId")
        queries.getLastMessage(sessionId).executeAsOneOrNull()
    }
    
    /**
     * Получить суммарное использование токенов для сессии
     */
    suspend fun getTotalTokens(sessionId: String): TokenStats = withContext(Dispatchers.IO) {
        logger.debug("🔢 Запрос статистики токенов сессии: $sessionId")
        val result = queries.getTotalTokensBySessionId(sessionId).executeAsOne()
        TokenStats(
            totalInput = result.totalInput ?: 0,
            totalOutput = result.totalOutput ?: 0,
            total = result.total ?: 0
        )
    }
    
    data class TokenStats(
        val totalInput: Long,
        val totalOutput: Long,
        val total: Long
    )
}
