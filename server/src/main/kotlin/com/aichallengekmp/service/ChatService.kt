package com.aichallengekmp.service

import com.aichallengekmp.ai.*
import com.aichallengekmp.database.Message
import com.aichallengekmp.database.Session
import com.aichallengekmp.database.SessionSettings
import com.aichallengekmp.database.dao.*
import com.aichallengekmp.models.*
import com.aichallengekmp.tools.TrackerToolsService
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
    private val modelRegistry: ModelRegistry,
    private val trackerTools: TrackerToolsService,
    private val ragSearchService: com.aichallengekmp.rag.RagSearchService,
    private val ragSourceDao: RagSourceDao,
    private val gitTools: com.aichallengekmp.tools.GitToolsService,
    private val teamToolExecutor: com.aichallengekmp.tools.TeamToolExecutor? = null,
    private val supportTools: com.aichallengekmp.tools.SupportToolsService? = null
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    // Порог similarity для фильтрации RAG результатов (0.0 - 1.0)
    // 0.5 - достаточно высокий порог чтобы отсеять нерелевантные фрагменты
    private val ragSimilarityThreshold = 0.5

    // Количество чанков для поиска
    private val ragTopK = 5
    
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

        // === АВТОМАТИЧЕСКИЙ ПОИСК RAG ===
        logger.info("🔎 Выполняем RAG-поиск для первого сообщения: ${initialMessage.take(50)}...")
        val ragHits = try {
            ragSearchService.search(initialMessage, topK = ragTopK)
        } catch (e: Exception) {
            logger.warn("⚠️ Ошибка при поиске RAG: ${e.message}")
            emptyList()
        }

        // Фильтруем по порогу similarity
        val filteredHits = ragHits.filter { it.score >= ragSimilarityThreshold }
        logger.info("📊 RAG: найдено ${ragHits.size} чанков, после фильтрации (threshold=$ragSimilarityThreshold): ${filteredHits.size}")
        filteredHits.forEachIndexed { idx, hit ->
            logger.info("  [$idx] ${hit.sourceId}#${hit.chunkIndex} (score=${String.format("%.3f", hit.score)}): ${hit.text.take(80)}...")
        }

        // Получаем ответ от AI (с поддержкой инструментов и RAG-контекстом)
        val aiResponse = generateResponseWithTools(
            sessionId = sessionId,
            settings = settings,
            messages = listOf(userMessage),
            ragContext = buildRagContext(filteredHits)
        )

        // Используем ответ как есть - источники будут видны в UI, но не в тексте
        val finalContent = aiResponse.text

        // Добавляем ответ AI
        val assistantMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "assistant",
            content = finalContent,
            modelId = aiResponse.modelId,
            inputTokens = aiResponse.tokenUsage.inputTokens.toLong(),
            outputTokens = aiResponse.tokenUsage.outputTokens.toLong(),
            createdAt = System.currentTimeMillis()
        )
        messageDao.insert(assistantMessage)

        // Сохраняем источники RAG в базу
        if (filteredHits.isNotEmpty()) {
            logger.info("💾 Сохраняем ${filteredHits.size} источников RAG для сообщения ${assistantMessage.id}")
            val ragSources = filteredHits.map { hit ->
                RagSourceDao.RagSourceInfo(
                    sourceId = hit.sourceId,
                    chunkIndex = hit.chunkIndex.toLong(),
                    score = hit.score,
                    chunkText = hit.text
                )
            }
            ragSourceDao.insertBatch(assistantMessage.id, ragSources)
        }
        
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

        // === ПРОВЕРКА НА SLASH КОМАНДУ ===
        if (messageText.trim().startsWith("/")) {
            logger.info("🔨 Обнаружена slash команда: ${messageText.take(20)}")
            return handleCommand(sessionId, messageText.trim(), settings.toDto())
        }

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

        // === АВТОМАТИЧЕСКИЙ ПОИСК RAG ===
        logger.info("🔎 Выполняем RAG-поиск для сообщения: ${messageText.take(50)}...")
        val ragHits = try {
            ragSearchService.search(messageText, topK = ragTopK)
        } catch (e: Exception) {
            logger.warn("⚠️ Ошибка при поиске RAG: ${e.message}")
            emptyList()
        }

        // Фильтруем по порогу similarity
        val filteredHits = ragHits.filter { it.score >= ragSimilarityThreshold }
        logger.info("📊 RAG: найдено ${ragHits.size} чанков, после фильтрации (threshold=$ragSimilarityThreshold): ${filteredHits.size}")
        filteredHits.forEachIndexed { idx, hit ->
            logger.info("  [$idx] ${hit.sourceId}#${hit.chunkIndex} (score=${String.format("%.3f", hit.score)}): ${hit.text.take(80)}...")
        }

        // Проверяем нужно ли сжатие
        val shouldCompress = compressionService.shouldCompress(sessionId, settings.compressionThreshold)
        if (shouldCompress) {
            logger.info("📦 Запуск сжатия истории для сессии: $sessionId")
            compressionService.compressHistory(sessionId, settings.toDto())
        }

        // Получаем контекст для AI (с учетом сжатия)
        val contextMessages = compressionService.getContextForAI(sessionId)

        // Получаем ответ от AI (с поддержкой инструментов и RAG-контекстом)
        val aiResponse = generateResponseWithTools(
            sessionId = sessionId,
            settings = settings.toDto(),
            messages = contextMessages,
            ragContext = buildRagContext(filteredHits)
        )

        // Используем ответ как есть - источники будут видны в UI, но не в тексте
        val finalContent = aiResponse.text

        // Добавляем ответ AI
        val assistantMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "assistant",
            content = finalContent,
            modelId = aiResponse.modelId,
            inputTokens = aiResponse.tokenUsage.inputTokens.toLong(),
            outputTokens = aiResponse.tokenUsage.outputTokens.toLong(),
            createdAt = System.currentTimeMillis()
        )
        messageDao.insert(assistantMessage)

        // Сохраняем источники RAG в базу
        if (filteredHits.isNotEmpty()) {
            logger.info("💾 Сохраняем ${filteredHits.size} источников RAG для сообщения ${assistantMessage.id}")
            val ragSources = filteredHits.map { hit ->
                RagSourceDao.RagSourceInfo(
                    sourceId = hit.sourceId,
                    chunkIndex = hit.chunkIndex.toLong(),
                    score = hit.score,
                    chunkText = hit.text
                )
            }
            ragSourceDao.insertBatch(assistantMessage.id, ragSources)
        }

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

        val messageDtos = messages.map { it.toDto(ragSourceDao) }

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
     * Строит контекст из RAG hits для добавления в системный промпт
     */
    private fun buildRagContext(hits: List<com.aichallengekmp.rag.RagHit>): String {
        if (hits.isEmpty()) return ""

        return buildString {
            appendLine("=== ДОПОЛНИТЕЛЬНАЯ СПРАВОЧНАЯ ИНФОРМАЦИЯ ===")
            appendLine()
            appendLine("Ниже приведены фрагменты из документации проекта, найденные автоматически.")
            appendLine()
            appendLine("ВАЖНО:")
            appendLine("- Эти фрагменты могут быть НЕ релевантны текущему вопросу")
            appendLine("- Если вопрос о погоде, общих знаниях, науке и т.д. - ИГНОРИРУЙ эти фрагменты ПОЛНОСТЬЮ")
            appendLine("- Отвечай на вопрос используя СВОИ знания")
            appendLine("- Используй фрагменты ТОЛЬКО если вопрос явно про этот конкретный проект/код")
            appendLine("- НЕ говори 'К сожалению, у меня нет доступа' если можешь ответить сам")
            appendLine()
            hits.forEachIndexed { index, hit ->
                appendLine("Фрагмент ${index + 1} (${hit.sourceId}#${hit.chunkIndex}, score=${String.format("%.2f", hit.score)}):")
                appendLine(hit.text.trim())
                appendLine()
            }
            appendLine("=== КОНЕЦ СПРАВОЧНОЙ ИНФОРМАЦИИ ===")
            appendLine()
            appendLine("Повторяю: отвечай на вопрос пользователя используя СВОИ знания. Фрагменты выше - только справка на случай вопроса о проекте.")
        }
    }

    /**
     * Базовый system prompt для агента, который умеет работать с задачами и напоминаниями
     * через инструменты, но при этом ведёт себя как обычный чат-ассистент.
     */
    private val defaultAgentSystemPrompt: String = """
        Ты — умный помощник, который общается с пользователем на естественном языке.

        ТЫ МОЖЕШЬ ОТВЕЧАТЬ НА ЛЮБЫЕ ВОПРОСЫ: о погоде, науке, программировании, истории, культуре и т.д.
        Используй свои знания для ответов на общие вопросы.

        Дополнительно ты имеешь доступ к специальным инструментам:

        📋 Управление задачами:
        - get_issues_count: получить количество задач в трекере.
        - get_all_issue_names: получить список задач с их ключами и названиями.
        - get_issue_info: получить подробную информацию о задаче по ключу.

        ⏰ Напоминания:
        - list_reminders: получить список всех существующих напоминаний пользователя.
        - create_reminder: создать новое напоминание на указанное время.
        - delete_reminder: удалить существующее напоминание.

        🔍 Поиск в проекте (RAG):
        - search_documentation: поиск информации в документации проекта (FAQ, архитектура, API).
        - search_code: поиск фрагментов кода в проекте (Kotlin файлы).
        - search_docs: выполнить поиск по локальному индексу документации проекта и вернуть релевантные фрагменты.

        🔧 Git/GitHub:
        - get_git_branch: получить название текущей git ветки проекта.

        🎯 Анализ и приоритеты:
        - analyze_task_priorities: проанализировать задачи по приоритетам, срокам и дать умные рекомендации (что делать первым).

        💬 Поддержка пользователей:
        - search_support_tickets: поиск тикетов поддержки по категории или ключевым словам.
        - get_similar_tickets: найти похожие решенные тикеты поддержки по описанию проблемы.

        Общие правила:
        - По умолчанию отвечай на вопросы используя СВОИ знания (о мире, науке, программировании и т.д.)
        - Если в промпте есть "ДОПОЛНИТЕЛЬНАЯ СПРАВОЧНАЯ ИНФОРМАЦИЯ" и вопрос про этот конкретный проект - используй её
        - Если вопрос НЕ про проект (погода, наука и т.д.) - игнорируй справочную информацию и отвечай сам
        - Если пользователь просит разобраться с задачами, сделать сводку или проверить напоминания,
          используй инструменты, чтобы сначала собрать нужные данные, а затем сформировать ответ.
        - Если пользователь спрашивает про код или архитектуру проекта - используй search_code или search_documentation.
        - Если пользователь просит "что делать первым" или "какие задачи приоритетные" - используй analyze_task_priorities.
        - Если пользователь спрашивает про ошибку или проблему - попробуй найти похожие решенные тикеты через get_similar_tickets.
        - Если нужно понять, есть ли напоминание про важную задачу, сначала получи список задач
          (например, get_all_issue_names или get_issue_info), затем список напоминаний (list_reminders)
          и сравни их содержимое по смыслу.
        - Если по действительно важной задаче нет напоминания, в явном виде предупреди об этом пользователя
          и предложи создать напоминание.
        - Если пользователь ПРЯМО и однозначно просит создать напоминание (указывает, о какой задаче напомнить
          и когда именно), ты можешь сразу вызывать create_reminder, при необходимости аккуратно уточнив детали
          (дату/время/формулировку).
        - Если пользователь только рассуждает о возможности напоминания (например, "можно было бы напомнить"),
          сначала уточни, хочет ли он действительно создать напоминание, и только после явного согласия
          вызывай create_reminder.
        - В ответах всегда объясняй пользователю, какие выводы ты сделал и на основании каких данных (инструментов).
    """.trimIndent()
    
    /**
     * Генерация ответа от AI с поддержкой инструментов (через function calling)
     */
    private suspend fun generateResponseWithTools(
        sessionId: String,
        settings: SessionSettingsDto,
        messages: List<Message>,
        ragContext: String = ""
    ): CompletionResult {
        logger.debug("🤖 Генерация ответа AI с инструментами для сессии: $sessionId")

        val aiMessages = messages.map { msg ->
            AIMessage(role = msg.role, content = msg.content)
        }

        // Получаем доступные инструменты из всех источников
        val baseTools = trackerTools.getAvailableTools() + gitTools.getAvailableTools()

        // Добавляем инструменты командного ассистента если доступны
        val teamTools = if (teamToolExecutor != null && supportTools != null) {
            getTeamAssistantTools()
        } else {
            emptyList()
        }

        val availableTools = baseTools + teamTools
        logger.info("🔧 Передаем YandexGPT ${availableTools.size} инструментов (base: ${baseTools.size}, team: ${teamTools.size})")

        // Строим итоговый system prompt: RAG-контекст в НАЧАЛЕ (самое важное), потом основной промпт
        val effectiveSystemPrompt = buildString {
            // Добавляем RAG-контекст ПЕРВЫМ, если есть
            if (ragContext.isNotBlank()) {
                append(ragContext)
                append("\n\n")
                logger.info("📖 RAG-контекст добавлен В НАЧАЛО системного промпта (${ragContext.length} символов)")
            } else {
                logger.warn("⚠️ RAG-контекст пустой, ничего не добавлено в промпт")
            }

            append(defaultAgentSystemPrompt)

            settings.systemPrompt?.takeIf { it.isNotBlank() }?.let { userPrompt ->
                append("\n\nДополнительные инструкции пользователя:\n")
                append(userPrompt)
            }
        }

        val request = CompletionRequest(
            modelId = settings.modelId,
            messages = aiMessages,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            systemPrompt = effectiveSystemPrompt,
            tools = availableTools  // YandexGPT сам решит какие вызвать и как построить цепочку
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

    /**
     * Обработка slash команд
     */
    private suspend fun handleCommand(
        sessionId: String,
        command: String,
        settings: SessionSettingsDto
    ): SessionDetailResponse {
        val parts = command.trim().split(" ", limit = 2)
        val commandName = parts[0].lowercase()
        val args = parts.getOrNull(1) ?: ""

        logger.info("🔨 Обработка команды: $commandName, аргументы: ${args.take(50)}")

        return when (commandName) {
            "/help" -> handleHelpCommand(sessionId, args, settings)
            else -> {
                // Неизвестная команда - обрабатываем как обычное сообщение
                logger.warn("⚠️ Неизвестная команда: $commandName, обрабатываем как обычное сообщение")

                // Сохраняем исходное сообщение пользователя
                val now = System.currentTimeMillis()
                val userMessage = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "user",
                    content = command,
                    modelId = null,
                    inputTokens = 0,
                    outputTokens = 0,
                    createdAt = now
                )
                messageDao.insert(userMessage)

                // Создаем ответ о неизвестной команде
                val responseMessage = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "assistant",
                    content = "Неизвестная команда: $commandName\n\nДоступные команды:\n- /help <вопрос> - получить помощь по проекту",
                    modelId = settings.modelId,
                    inputTokens = 0,
                    outputTokens = 0,
                    createdAt = System.currentTimeMillis()
                )
                messageDao.insert(responseMessage)

                sessionDao.updateTimestamp(sessionId, System.currentTimeMillis())
                getSessionDetail(sessionId)
            }
        }
    }

    /**
     * Команда /help - помощь по проекту на основе документации
     */
    private suspend fun handleHelpCommand(
        sessionId: String,
        question: String,
        settings: SessionSettingsDto
    ): SessionDetailResponse {
        logger.info("📚 Команда /help: $question")

        val now = System.currentTimeMillis()

        // Сохраняем сообщение пользователя с командой
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "user",
            content = "/help $question",
            modelId = null,
            inputTokens = 0,
            outputTokens = 0,
            createdAt = now
        )
        messageDao.insert(userMessage)

        if (question.isBlank()) {
            // Если вопрос не указан - показываем справку
            val helpText = buildString {
                appendLine("Команда /help используется для получения помощи по проекту на основе локальной документации.")
                appendLine()
                appendLine("Использование: /help <ваш вопрос>")
                appendLine()
                appendLine("Примеры:")
                appendLine("- /help как создать новую сессию?")
                appendLine("- /help какие есть API endpoints?")
                appendLine("- /help стиль кода для классов")
                appendLine("- /help как работает RAG?")
            }

            val responseMessage = Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "assistant",
                content = helpText,
                modelId = settings.modelId,
                inputTokens = 0,
                outputTokens = 0,
                createdAt = System.currentTimeMillis()
            )
            messageDao.insert(responseMessage)

            sessionDao.updateTimestamp(sessionId, System.currentTimeMillis())
            return getSessionDetail(sessionId)
        }

        // Выполняем RAG поиск с параметрами для /help
        val helpRagTopK = 10  // Больше чанков
        val helpRagThreshold = 0.3  // Более низкий порог

        logger.info("🔎 RAG-поиск для /help (topK=$helpRagTopK, threshold=$helpRagThreshold)")
        val ragHits = try {
            ragSearchService.search(question, topK = helpRagTopK)
        } catch (e: Exception) {
            logger.warn("⚠️ Ошибка при поиске RAG: ${e.message}")
            emptyList()
        }

        val filteredHits = ragHits.filter { it.score >= helpRagThreshold }
        logger.info("📊 RAG для /help: найдено ${ragHits.size} чанков, после фильтрации: ${filteredHits.size}")

        if (filteredHits.isEmpty()) {
            val noDocsMessage = Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "assistant",
                content = "К сожалению, в локальной документации не найдено информации по вашему вопросу.\n\nПопробуйте переформулировать вопрос или задать более общий вопрос.",
                modelId = settings.modelId,
                inputTokens = 0,
                outputTokens = 0,
                createdAt = System.currentTimeMillis()
            )
            messageDao.insert(noDocsMessage)

            sessionDao.updateTimestamp(sessionId, System.currentTimeMillis())
            return getSessionDetail(sessionId)
        }

        // Формируем специальный system prompt для /help
        val helpSystemPrompt = buildString {
            appendLine("=== ДОКУМЕНТАЦИЯ ПРОЕКТА ===")
            appendLine()
            appendLine("Ты — ассистент разработчика для проекта AI Challenge KMP.")
            appendLine("Твоя задача — помогать разработчикам, отвечая на вопросы о проекте на основе предоставленной документации.")
            appendLine()
            appendLine("ВАЖНЫЕ ПРАВИЛА:")
            appendLine("1. Отвечай ТОЛЬКО на основе фрагментов документации ниже")
            appendLine("2. Если в документации нет ответа на вопрос - честно скажи об этом")
            appendLine("3. НЕ придумывай информацию, которой нет в документации")
            appendLine("4. Используй примеры кода из документации если они есть")
            appendLine("5. Отвечай кратко и по делу")
            appendLine()
            appendLine("=== ФРАГМЕНТЫ ДОКУМЕНТАЦИИ ===")
            appendLine()

            filteredHits.forEachIndexed { index, hit ->
                appendLine("### Фрагмент ${index + 1} (${hit.sourceId}, score=${String.format("%.2f", hit.score)})")
                appendLine(hit.text.trim())
                appendLine()
            }

            appendLine("=== КОНЕЦ ДОКУМЕНТАЦИИ ===")
            appendLine()
            appendLine("Вопрос разработчика: $question")
        }

        // Генерируем ответ с помощью LLM
        val request = CompletionRequest(
            modelId = settings.modelId,
            messages = listOf(AIMessage(role = "user", content = question)),
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            systemPrompt = helpSystemPrompt,
            tools = null  // Для /help не используем tools
        )

        val aiResponse = modelRegistry.complete(request)

        // Сохраняем ответ ассистента
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

        // Сохраняем источники RAG
        if (filteredHits.isNotEmpty()) {
            logger.info("💾 Сохраняем ${filteredHits.size} источников RAG для /help")
            val ragSources = filteredHits.map { hit ->
                RagSourceDao.RagSourceInfo(
                    sourceId = hit.sourceId,
                    chunkIndex = hit.chunkIndex.toLong(),
                    score = hit.score,
                    chunkText = hit.text
                )
            }
            ragSourceDao.insertBatch(assistantMessage.id, ragSources)
        }

        sessionDao.updateTimestamp(sessionId, System.currentTimeMillis())
        logger.info("✅ Команда /help обработана успешно")

        return getSessionDetail(sessionId)
    }

    /**
     * Получить список инструментов командного ассистента
     */
    private fun getTeamAssistantTools(): List<com.aichallengekmp.tools.ToolDefinition> {
        return listOf(
            com.aichallengekmp.tools.ToolDefinition(
                name = "search_documentation",
                description = "Поиск информации в документации проекта (FAQ, архитектура, API). Возвращает релевантные фрагменты документации.",
                parameters = mapOf(
                    "query" to "Поисковый запрос"
                )
            ),
            com.aichallengekmp.tools.ToolDefinition(
                name = "search_code",
                description = "Поиск фрагментов кода в проекте (Kotlin файлы). Возвращает релевантные фрагменты кода с указанием источников.",
                parameters = mapOf(
                    "query" to "Что искать в коде"
                )
            ),
            com.aichallengekmp.tools.ToolDefinition(
                name = "analyze_task_priorities",
                description = "Проанализировать задачи по приоритетам и дать рекомендации по очередности выполнения. Учитывает срочность, важность, просроченные задачи.",
                parameters = emptyMap()
            ),
            com.aichallengekmp.tools.ToolDefinition(
                name = "search_support_tickets",
                description = "Поиск тикетов поддержки по ключевому слову. Ищет в теме и описании тикетов.",
                parameters = mapOf(
                    "keyword" to "Ключевое слово для поиска"
                )
            ),
            com.aichallengekmp.tools.ToolDefinition(
                name = "get_similar_tickets",
                description = "Найти похожие решенные тикеты поддержки по описанию проблемы",
                parameters = mapOf(
                    "description" to "Описание проблемы"
                )
            )
        )
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

private suspend fun Message.toDto(ragSourceDao: RagSourceDao): MessageDto {
    // Загружаем источники RAG для этого сообщения
    val ragSources = if (role == "assistant") {
        try {
            val sources = ragSourceDao.getByMessageId(id)
            sources.map { source ->
                RagSourceDto(
                    sourceId = source.sourceId,
                    chunkIndex = source.chunkIndex.toInt(),
                    score = source.score,
                    chunkText = source.chunkText
                )
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

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
        timestamp = createdAt,
        ragSources = ragSources
    )
}

// ============= Exceptions =============

class NotFoundException(message: String) : Exception(message)
