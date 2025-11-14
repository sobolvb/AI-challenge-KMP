package local.engine

import local.promt.PromptTemplates
import local.service.YandexAiService
import local.service.DialogSessionManager
import local.data.*

class ReasoningEngine(
    private val yandex: YandexAiService,
    private val sessionManager: DialogSessionManager = DialogSessionManager()
) {
    // Простой метод для выполнения задачи
    suspend fun solveTask(task: String, requestType: String = "normal", maxTokens: Int = 2000): TaskResponse {
        val prompt = PromptTemplates.direct(task)
        println("🎯 Выполнение задачи ($requestType): $task")
        println("📊 Максимальное количество токенов: $maxTokens")

        val result = yandex.complete(prompt, maxTokens)

        println("✅ Ответ получен")
        println("📊 Токены - Вход: ${result.tokenUsage.inputTokens}, Выход: ${result.tokenUsage.outputTokens}, Всего: ${result.tokenUsage.totalTokens}")

        return TaskResponse(
            task = task,
            answer = result.text,
            tokenUsage = result.tokenUsage,
            requestType = requestType
        )
    }

    // Метод для сравнения разных типов запросов
    suspend fun compareRequestTypes(): TokenComparisonResponse {
        println("🔬 Запуск сравнения разных типов запросов")

        // 1. Короткий запрос
        val shortTask = "Привет!"
        val shortResult = solveTask(shortTask, "short", 2000)
        println(shortResult.answer)
        // 2. Длинный запрос
        val longTask = buildString {
            append("Напиши подробный анализ следующей темы: ")
            append("Как работают нейронные сети? ")
            append("Опиши основные принципы, архитектуру, ")
            append("процесс обучения, основные типы нейронных сетей ")
            append("(сверточные, рекуррентные, трансформеры), ")
            append("примеры применения и преимущества/недостатки.")
        }
        val longResult = solveTask(longTask, "long", 2000)
        println(longResult.answer)
        // 3. Запрос, превышающий лимит (создаем очень длинный текст)
        val exceedingTask = buildString {
            append("Напиши подробную статью на тему: ")
            repeat(1000) {
                append("История развития искусственного интеллекта, ")
                append("основные вехи, достижения, ")
            }
            append("и в конце напиши 2000 чисел начиная с 1 и до 2000 через пробел")
        }
        val exceedingResult = solveTask(exceedingTask, "exceeds_limit", 1000)
        println(exceedingResult.answer)

        // Анализ результатов
        val analysisPrompt = PromptTemplates.tokenAnalyzer(
            shortTokens = shortResult.tokenUsage,
            longTokens = longResult.tokenUsage,
            exceedingTokens = exceedingResult.tokenUsage
        )

        println("🔍 Запуск анализа результатов...")
        val analysisResult = yandex.complete(analysisPrompt, 2000)

        println("🎉 Сравнение завершено")

        return TokenComparisonResponse(
            shortRequest = shortResult,
            longRequest = longResult,
            exceedingRequest = exceedingResult,
            analysis = analysisResult.text
        )
    }

    // Метод для обработки диалогового сообщения
    suspend fun processDialog(sessionId: String, userMessage: String): DialogResponse {
        println("💬 Обработка диалога для сессии: $sessionId")

        // Добавляем сообщение пользователя
        sessionManager.addMessage(
            sessionId,
            DialogMessage(role = "user", content = userMessage)
        )

        // Проверяем, нужно ли сжатие
        val needsCompression = sessionManager.needsCompression(sessionId)
        var summaryGenerated = false

        if (needsCompression) {
            println("🚨 Требуется сжатие истории!")
            val messagesToCompress = sessionManager.getMessagesForCompression(sessionId)

            // Создаем summary
            val summaryPrompt = PromptTemplates.dialogSummary(messagesToCompress)
            val summaryResult = yandex.complete(summaryPrompt, 1000)

            // Сохраняем summary
            sessionManager.compressHistory(sessionId, summaryResult.text)
            summaryGenerated = true

            println("✅ Summary создан: ${summaryResult.text.take(100)}...")
        }

        // Получаем контекст для AI (summary + последние сообщения)
        val contextMessages = sessionManager.getContextForAI(sessionId)

        // Формируем промпт для AI
        val prompt = buildDialogPrompt(contextMessages)

        println("🤖 Отправка запроса в AI...")
        val aiResponse = yandex.complete(prompt, 2000)

        // Добавляем ответ ассистента
        sessionManager.addMessage(
            sessionId,
            DialogMessage(role = "assistant", content = aiResponse.text)
        )

        val session = sessionManager.getSessionInfo(sessionId)

        return DialogResponse(
            sessionId = sessionId,
            message = userMessage,
            answer = aiResponse.text,
            tokenUsage = aiResponse.tokenUsage,
            messageCount = session?.messages?.size ?: 0,
            compressionApplied = needsCompression,
            summaryGenerated = summaryGenerated
        )
    }

    // Получить статистику сжатия
    suspend fun getCompressionStats(sessionId: String): CompressionStats? {
        val session = sessionManager.getSessionInfo(sessionId) ?: return null

        // Считаем токены до и после сжатия
        val tokensBeforeCompression = estimateTokens(session.messages)

        // Для после - считаем summary + оставшиеся сообщения
        val contextMessages = sessionManager.getContextForAI(sessionId)
        val tokensAfterCompression = estimateTokens(contextMessages)

        val tokensSaved = tokensBeforeCompression.totalTokens - tokensAfterCompression.totalTokens
        val compressionRatio = if (tokensBeforeCompression.totalTokens > 0) {
            tokensSaved.toDouble() / tokensBeforeCompression.totalTokens
        } else 0.0

        return CompressionStats(
            sessionId = sessionId,
            totalMessages = session.messages.size,
            messagesBeforeCompression = session.lastCompressionAt + session.messages.size,
            messagesAfterCompression = session.messages.size + (if (session.summary != null) 1 else 0),
            tokensBeforeCompression = tokensBeforeCompression,
            tokensAfterCompression = tokensAfterCompression,
            tokensSaved = tokensSaved,
            compressionRatio = compressionRatio,
            summary = session.summary ?: "Нет summary"
        )
    }

    // Анализ эффективности сжатия
    suspend fun analyzeCompression(sessionId: String): String {
        val stats = getCompressionStats(sessionId) ?: return "Сессия не найдена"

        val analysisPrompt = PromptTemplates.compressionAnalysis(
            messagesBeforeCount = stats.messagesBeforeCompression,
            messagesAfterCount = stats.messagesAfterCompression,
            tokensBeforeCount = stats.tokensBeforeCompression.totalTokens,
            tokensAfterCount = stats.tokensAfterCompression.totalTokens,
            tokensSaved = stats.tokensSaved,
            summary = stats.summary
        )

        val analysisResult = yandex.complete(analysisPrompt, 1500)
        return analysisResult.text
    }

    // Вспомогательные методы

    private fun buildDialogPrompt(messages: List<DialogMessage>): String {
        return buildString {
            appendLine("Ты — полезный AI ассистент. Отвечай на вопросы пользователя, учитывая контекст диалога.")
            appendLine()

            messages.forEach { msg ->
                when (msg.role) {
                    "system" -> appendLine("📝 ${msg.content}")
                    "user" -> appendLine("👤 Пользователь: ${msg.content}")
                    "assistant" -> appendLine("🤖 Ассистент: ${msg.content}")
                }
                appendLine()
            }

            appendLine("Ответь на последнее сообщение пользователя:")
        }
    }

    private fun estimateTokens(messages: List<DialogMessage>): TokenUsage {
        // Приблизительный подсчет: 1 токен ≈ 4 символа
        val totalChars = messages.sumOf { it.content.length }
        val estimatedTokens = totalChars / 4

        return TokenUsage(
            inputTokens = estimatedTokens,
            outputTokens = 0,
            totalTokens = estimatedTokens
        )
    }

    fun getSessionInfo(sessionId: String): SessionInfo? {
        val session = sessionManager.getSessionInfo(sessionId) ?: return null
        return SessionInfo(
            sessionId = session.sessionId,
            messageCount = session.messages.size,
            hasSummary = session.summary != null,
            lastCompressionAt = session.lastCompressionAt,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt
        )
    }

    fun deleteSession(sessionId: String) {
        sessionManager.deleteSession(sessionId)
    }
}
