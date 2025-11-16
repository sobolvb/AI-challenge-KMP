package com.aichallengekmp.service

import com.aichallengekmp.ai.*
import com.aichallengekmp.database.Message
import com.aichallengekmp.database.Session
import com.aichallengekmp.database.SessionSettings
import com.aichallengekmp.database.dao.*
import com.aichallengekmp.models.*
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Основной сервис для работы с чатами
 * Реализует всю бизнес-логику приложения
 */
class ChatService(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val settingsDao: SessionSettingsDao,
    private val compressionService: CompressionService,
    private val modelRegistry: ModelRegistry
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    
    /**
     * Создать новую сессию с первым сообщением
     */
    suspend fun createSession(
        name: String,
        initialMessage: String,
        settings: SessionSettingsDto
    ): SessionDetailResponse {
        logger.info("🆕 Создание новой сессии: $name")
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        // Создаем сессию
        val session = Session(
            id = sessionId,
            name = name,
            createdAt = now,
            updatedAt = now
        )
        sessionDao.insert(session)
        
        // Сохраняем настройки
        val dbSettings = settings.toDbModel(sessionId)
        settingsDao.insert(dbSettings)
        
        logger.info("💬 Отправка первого сообщения в сессию")
        
        // Добавляем сообщение пользователя
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "user",
            content = initialMessage,
            modelId = null,
            inputTokens = 0,
            outputTokens = 0,
            createdAt = now
        )
        messageDao.insert(userMessage)
        
        // Получаем ответ от AI
        val aiResponse = generateResponse(sessionId, settings, listOf(userMessage))
        
        // Добавляем ответ AI
        val assistantMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "assistant",
            content = aiResponse.text,
            modelId = aiResponse.modelId,
            inputTokens = aiResponse.tokenUsage.inputTokens.toLong(),
            outputTokens = aiResponse.tokenUsage.outputTokens.toLong(),
            createdAt = System.currentTimeMillis()
        )
        messageDao.insert(assistantMessage)
        
        // Генерируем умное название на основе диалога
        generateSessionName(sessionId, initialMessage, aiResponse.text, settings.modelId)
        
        // Обновляем timestamp сессии
        sessionDao.updateTimestamp(sessionId, System.currentTimeMillis())
        
        logger.info("✅ Сессия создана успешно: $sessionId")
        
        return getSessionDetail(sessionId)
    }
    
    /**
     * Отправить сообщение в существующую сессию
     */
    suspend fun sendMessage(sessionId: String, messageText: String): SessionDetailResponse {
        logger.info("💬 Отправка сообщения в сессию: $sessionId")
        
        // Проверяем что сессия существует
        sessionDao.getById(sessionId)
            ?: throw NotFoundException("Сессия не найдена: $sessionId")
        
        val settings = settingsDao.getBySessionId(sessionId)
            ?: throw NotFoundException("Настройки сессии не найдены: $sessionId")
        
        val now = System.currentTimeMillis()
        
        // Добавляем сообщение пользователя
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "user",
            content = messageText,
            modelId = null,
            inputTokens = 0,
            outputTokens = 0,
            createdAt = now
        )
        messageDao.insert(userMessage)
        
        // Проверяем нужно ли сжатие
        val shouldCompress = compressionService.shouldCompress(sessionId, settings.compressionThreshold)
        if (shouldCompress) {
            logger.info("📦 Запуск сжатия истории для сессии: $sessionId")
            compressionService.compressHistory(sessionId, settings.toDto())
        }
        
        // Получаем контекст для AI (с учетом сжатия)
        val contextMessages = compressionService.getContextForAI(sessionId)
        
        // Получаем ответ от AI
        val aiResponse = generateResponse(sessionId, settings.toDto(), contextMessages)
        
        // Добавляем ответ AI
        val assistantMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "assistant",
            content = aiResponse.text,
            modelId = aiResponse.modelId,
            inputTokens = aiResponse.tokenUsage.inputTokens.toLong(),
            outputTokens = aiResponse.tokenUsage.outputTokens.toLong(),
            createdAt = System.currentTimeMillis()
        )
        messageDao.insert(assistantMessage)
        
        // Обновляем timestamp сессии
        sessionDao.updateTimestamp(sessionId, System.currentTimeMillis())
        
        logger.info("✅ Сообщение обработано успешно")
        
        return getSessionDetail(sessionId)
    }
    
    /**
     * Получить список всех сессий
     */
    suspend fun getSessionList(): List<SessionListItem> {
        logger.debug("📋 Запрос списка всех сессий")
        
        val sessions = sessionDao.getAll()
        
        return sessions.map { session ->
            val lastMessage = messageDao.getLastMessage(session.id)
            val totalMessages = messageDao.countBySessionId(session.id)
            
            SessionListItem(
                id = session.id,
                name = session.name,
                lastMessage = lastMessage?.content,
                lastMessageTime = lastMessage?.createdAt ?: session.createdAt,
                totalMessages = totalMessages.toInt()
            )
        }
    }
    
    /**
     * Получить детали сессии со всеми сообщениями
     */
    suspend fun getSessionDetail(sessionId: String): SessionDetailResponse {
        logger.debug("🔍 Запрос деталей сессии: $sessionId")
        
        val session = sessionDao.getById(sessionId)
            ?: throw NotFoundException("Сессия не найдена: $sessionId")
        
        val settings = settingsDao.getBySessionId(sessionId)
            ?: throw NotFoundException("Настройки сессии не найдены: $sessionId")
        
        val messages = messageDao.getBySessionId(sessionId)
        val compressionInfo = compressionService.getCompressionInfo(sessionId)
        
        val messageDtos = messages.map { it.toDto() }
        
        return SessionDetailResponse(
            id = session.id,
            name = session.name,
            messages = messageDtos,
            settings = settings.toDto(),
            compressionInfo = compressionInfo
        )
    }
    
    /**
     * Обновить настройки сессии
     */
    suspend fun updateSessionSettings(sessionId: String, newSettings: SessionSettingsDto): SessionSettingsDto {
        logger.info("⚙️ Обновление настроек сессии: $sessionId")
        logger.debug("   Новая модель: ${newSettings.modelId}")
        logger.debug("   Температура: ${newSettings.temperature}")
        
        // Проверяем что сессия существует
        sessionDao.getById(sessionId)
            ?: throw NotFoundException("Сессия не найдена: $sessionId")
        
        val dbSettings = newSettings.toDbModel(sessionId)
        settingsDao.update(dbSettings)
        
        logger.info("✅ Настройки обновлены")
        
        return newSettings
    }
    
    /**
     * Удалить сессию
     */
    suspend fun deleteSession(sessionId: String) {
        logger.warn("🗑️ Удаление сессии: $sessionId")
        
        // Проверяем что сессия существует
        sessionDao.getById(sessionId)
            ?: throw NotFoundException("Сессия не найдена: $sessionId")
        
        // Cascade delete удалит messages, settings и compression автоматически
        sessionDao.delete(sessionId)
        
        logger.info("✅ Сессия удалена")
    }
    
    /**
     * Получить список доступных моделей
     */
    suspend fun getAvailableModels(): List<ModelInfoDto> {
        logger.debug("📋 Запрос списка доступных моделей")
        
        return modelRegistry.getAllModels().map {
            ModelInfoDto(
                id = it.id,
                name = it.name,
                displayName = it.displayName
            )
        }
    }
    
    // ============= Private Helper Methods =============
    
    /**
     * Генерация ответа от AI
     */
    private suspend fun generateResponse(
        sessionId: String,
        settings: SessionSettingsDto,
        messages: List<Message>
    ): CompletionResult {
        logger.debug("🤖 Генерация ответа AI для сессии: $sessionId")
        
        val aiMessages = messages.map { msg ->
            AIMessage(role = msg.role, content = msg.content)
        }
        
        val request = CompletionRequest(
            modelId = settings.modelId,
            messages = aiMessages,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            systemPrompt = settings.systemPrompt
        )
        
        return modelRegistry.complete(request)
    }
    
    /**
     * Генерация умного названия сессии на основе первого диалога
     */
    private suspend fun generateSessionName(
        sessionId: String,
        userMessage: String,
        aiResponse: String,
        modelId: String
    ) {
        logger.info("✨ Генерация названия для сессии: $sessionId")
        
        try {
            val prompt = """
                На основе следующего диалога придумай краткое название (максимум 5-7 слов):
                
                Пользователь: $userMessage
                Ассистент: ${aiResponse.take(200)}
                
                Название должно отражать тему разговора. Верни только название без кавычек и дополнительных слов.
            """.trimIndent()
            
            val request = CompletionRequest(
                modelId = modelId,
                messages = listOf(AIMessage(role = "user", content = prompt)),
                temperature = 0.7,
                maxTokens = 50
            )
            
            val result = modelRegistry.complete(request)
            val generatedName = result.text.trim().take(50)
            
            sessionDao.updateName(sessionId, generatedName, System.currentTimeMillis())
            
            logger.info("✅ Название сгенерировано: $generatedName")
        } catch (e: Exception) {
            logger.error("❌ Ошибка при генерации названия: ${e.message}", e)
            // Не критично, оставляем старое название
        }
    }
}

// ============= Extensions =============

private fun SessionSettings.toDto() = SessionSettingsDto(
    modelId = modelId,
    temperature = temperature,
    maxTokens = maxTokens.toInt(),
    compressionThreshold = compressionThreshold.toInt(),
    systemPrompt = systemPrompt
)

private fun SessionSettingsDto.toDbModel(sessionId: String) = SessionSettings(
    sessionId = sessionId,
    modelId = modelId,
    temperature = temperature,
    maxTokens = maxTokens.toLong(),
    compressionThreshold = compressionThreshold.toLong(),
    systemPrompt = systemPrompt
)

private suspend fun Message.toDto(): MessageDto {
    return MessageDto(
        id = id,
        role = role,
        content = content,
        modelId = modelId,
        modelName = modelId?.let { "YandexGPT Lite" }, // TODO: получать из registry
        tokenUsage = if (role == "assistant") {
            TokenUsageDto(
                inputTokens = inputTokens.toInt(),
                outputTokens = outputTokens.toInt(),
                totalTokens = (inputTokens + outputTokens).toInt()
            )
        } else null,
        timestamp = createdAt
    )
}

// ============= Exceptions =============

class NotFoundException(message: String) : Exception(message)
