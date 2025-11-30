package com.aichallengekmp.database.dao

import com.aichallengekmp.database.AppDatabase
import com.aichallengekmp.database.MessageRagSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * DAO для работы с источниками RAG
 */
class RagSourceDao(private val database: AppDatabase) {
    private val logger = LoggerFactory.getLogger(RagSourceDao::class.java)
    private val queries = database.messageRagSourceQueries

    /**
     * Получить все источники для сообщения
     */
    suspend fun getByMessageId(messageId: String): List<MessageRagSource> = withContext(Dispatchers.IO) {
        logger.debug("📚 Запрос источников RAG для сообщения: $messageId")
        queries.selectByMessageId(messageId).executeAsList()
    }

    /**
     * Добавить источник для сообщения
     */
    suspend fun insert(
        messageId: String,
        sourceId: String,
        chunkIndex: Long,
        score: Double,
        chunkText: String
    ) = withContext(Dispatchers.IO) {
        logger.debug("📖 Добавление источника: $sourceId#$chunkIndex для сообщения $messageId (score=$score)")
        queries.insertSource(
            messageId = messageId,
            sourceId = sourceId,
            chunkIndex = chunkIndex,
            score = score,
            chunkText = chunkText
        )
    }

    /**
     * Добавить несколько источников для сообщения (batch insert)
     */
    suspend fun insertBatch(
        messageId: String,
        sources: List<RagSourceInfo>
    ) = withContext(Dispatchers.IO) {
        logger.info("📚 Добавление ${sources.size} источников для сообщения $messageId")
        database.transaction {
            sources.forEach { source ->
                queries.insertSource(
                    messageId = messageId,
                    sourceId = source.sourceId,
                    chunkIndex = source.chunkIndex,
                    score = source.score,
                    chunkText = source.chunkText
                )
            }
        }
    }

    /**
     * Удалить все источники для сообщения
     */
    suspend fun deleteByMessageId(messageId: String) = withContext(Dispatchers.IO) {
        logger.warn("🗑️ Удаление источников RAG для сообщения: $messageId")
        queries.deleteByMessageId(messageId)
    }

    data class RagSourceInfo(
        val sourceId: String,
        val chunkIndex: Long,
        val score: Double,
        val chunkText: String
    )
}
