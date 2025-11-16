package com.aichallengekmp.service

import com.aichallengekmp.ai.*
import com.aichallengekmp.database.Message
import com.aichallengekmp.database.SessionCompression
import com.aichallengekmp.database.dao.CompressionDao
import com.aichallengekmp.database.dao.MessageDao
import com.aichallengekmp.models.CompressionInfoDto
import com.aichallengekmp.models.SessionSettingsDto
import org.slf4j.LoggerFactory

/**
 * Сервис для управления сжатием истории диалогов
 */
class CompressionService(
    private val messageDao: MessageDao,
    private val compressionDao: CompressionDao,
    private val modelRegistry: ModelRegistry
) {
    private val logger = LoggerFactory.getLogger(CompressionService::class.java)
    
    companion object {
        // Сколько последних сообщений оставлять несжатыми
        private const val RECENT_MESSAGES_COUNT = 10
    }
    
    /**
     * Проверить нужно ли сжатие для сессии
     */
    suspend fun shouldCompress(sessionId: String, compressionThreshold: Long): Boolean {
        val messageCount = messageDao.countBySessionId(sessionId)
        val compression = compressionDao.getBySessionId(sessionId)
        
        // Если сжатия еще не было
        if (compression == null) {
            val needsCompression = messageCount > compressionThreshold
            logger.debug("📦 Проверка сжатия для $sessionId: сообщений=$messageCount, порог=$compressionThreshold, нужно=$needsCompression")
            return needsCompression
        }
        
        // Если сжатие было, проверяем количество новых сообщений после последнего сжатия
        val newMessagesCount = messageCount - compression.lastCompressionIndex
        val needsCompression = newMessagesCount > compressionThreshold
        
        logger.debug("📦 Проверка сжатия для $sessionId: новых сообщений=$newMessagesCount, порог=$compressionThreshold, нужно=$needsCompression")
        
        return needsCompression
    }
    
    /**
     * Сжать историю диалога
     */
    suspend fun compressHistory(sessionId: String, settings: SessionSettingsDto) {
        logger.info("📦 Начало сжатия истории для сессии: $sessionId")
        
        val allMessages = messageDao.getBySessionId(sessionId)
        
        if (allMessages.isEmpty()) {
            logger.warn("⚠️ Нет сообщений для сжатия")
            return
        }
        
        // Определяем какие сообщения сжимать
        val existingCompression = compressionDao.getBySessionId(sessionId)
        val startIndex = existingCompression?.lastCompressionIndex?.toInt() ?: 0
        val endIndex = maxOf(0, allMessages.size - RECENT_MESSAGES_COUNT)
        
        if (startIndex >= endIndex) {
            logger.warn("⚠️ Недостаточно сообщений для сжатия")
            return
        }
        
        val messagesToCompress = allMessages.subList(startIndex, endIndex)
        
        logger.info("📦 Сжатие ${messagesToCompress.size} сообщений (индексы $startIndex-$endIndex)")
        
        // Генерируем summary
        val summary = generateSummary(messagesToCompress, settings.modelId)
        
        logger.info("✅ Summary сгенерирован, длина: ${summary.length} символов")
        
        val now = System.currentTimeMillis()
        val compression = SessionCompression(
            sessionId = sessionId,
            summary = summary,
            lastCompressionIndex = endIndex.toLong(),
            compressedAt = now
        )
        
        if (existingCompression == null) {
            compressionDao.insert(compression)
        } else {
            // Объединяем старый и новый summary
            val combinedSummary = """
                Предыдущий контекст:
                ${existingCompression.summary}
                
                Продолжение диалога:
                $summary
            """.trimIndent()
            
            compressionDao.update(compression.copy(summary = combinedSummary))
        }
        
        logger.info("✅ Сжатие завершено успешно")
    }
    
    /**
     * Получить контекст для AI с учетом сжатия
     * Возвращает: [summary] + [последние несжатые сообщения]
     */
    suspend fun getContextForAI(sessionId: String): List<Message> {
        logger.debug("🔍 Получение контекста для AI: $sessionId")
        
        val allMessages = messageDao.getBySessionId(sessionId)
        val compression = compressionDao.getBySessionId(sessionId)
        
        if (compression == null) {
            // Сжатия нет, возвращаем все сообщения
            logger.debug("   Сжатия нет, возвращаем все ${allMessages.size} сообщений")
            return allMessages
        }
        
        // Берем только несжатые сообщения
        val uncompressedMessages = allMessages.drop(compression.lastCompressionIndex.toInt())
        
        // Создаем системное сообщение с summary
        val summaryMessage = Message(
            id = "summary-${compression.sessionId}",
            sessionId = sessionId,
            role = "system",
            content = "Контекст предыдущего диалога:\n${compression.summary}",
            modelId = null,
            inputTokens = 0,
            outputTokens = 0,
            createdAt = compression.compressedAt
        )
        
        logger.debug("   Возвращаем: 1 summary + ${uncompressedMessages.size} несжатых сообщений")
        
        return listOf(summaryMessage) + uncompressedMessages
    }
    
    /**
     * Получить информацию о сжатии для сессии
     */
    suspend fun getCompressionInfo(sessionId: String): CompressionInfoDto? {
        logger.debug("ℹ️ Запрос информации о сжатии: $sessionId")
        
        val compression = compressionDao.getBySessionId(sessionId) ?: return null
        
        val totalMessages = messageDao.countBySessionId(sessionId).toInt()
        val compressedCount = compression.lastCompressionIndex.toInt()
        
        // Приблизительная оценка сэкономленных токенов
        val estimatedTokensSaved = estimateTokensSaved(compression.summary, compressedCount)
        
        return CompressionInfoDto(
            hasSummary = true,
            compressedMessagesCount = compressedCount,
            summary = compression.summary,
            tokensSaved = estimatedTokensSaved
        )
    }
    
    // ============= Private Helper Methods =============
    
    /**
     * Генерация summary из списка сообщений
     */
    private suspend fun generateSummary(messages: List<Message>, modelId: String): String {
        logger.debug("✨ Генерация summary из ${messages.size} сообщений")
        
        val dialogText = messages.joinToString("\n\n") { msg ->
            val role = if (msg.role == "user") "Пользователь" else "Ассистент"
            "$role: ${msg.content}"
        }
        
        val prompt = """
            Создай краткое, но информативное резюме следующего диалога.
            Сохрани все ключевые факты, имена, даты и важные детали.
            Структурируй информацию логично.
            
            Диалог:
            $dialogText
            
            Резюме:
        """.trimIndent()
        
        val request = CompletionRequest(
            modelId = modelId,
            messages = listOf(AIMessage(role = "user", content = prompt)),
            temperature = 0.3,
            maxTokens = 1000
        )
        
        val result = modelRegistry.complete(request)
        return result.text.trim()
    }
    
    /**
     * Оценка сэкономленных токенов
     * Примерно: сжатые сообщения занимали бы ~4 токена на символ
     * Summary занимает меньше
     */
    private fun estimateTokensSaved(summary: String, compressedMessagesCount: Int): Int {
        // Примерная оценка: каждое сообщение ~100 токенов
        val originalTokens = compressedMessagesCount * 100
        val summaryTokens = summary.length / 4
        return maxOf(0, originalTokens - summaryTokens)
    }
}
