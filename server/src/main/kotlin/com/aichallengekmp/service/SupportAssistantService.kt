package com.aichallengekmp.service

import com.aichallengekmp.ai.AIMessage
import com.aichallengekmp.ai.CompletionRequest
import com.aichallengekmp.ai.ModelRegistry
import com.aichallengekmp.rag.RagSearchService
import com.aichallengekmp.tools.SupportToolsService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Сервис для автоматических ответов на вопросы пользователей
 * Использует MCP (данные CRM) + RAG (FAQ/документация) + AI
 */
class SupportAssistantService(
    private val supportTools: SupportToolsService,
    private val ragSearchService: RagSearchService,
    private val modelRegistry: ModelRegistry
) {

    private val logger = LoggerFactory.getLogger(SupportAssistantService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Ответить на вопрос пользователя
     */
    suspend fun answerQuestion(
        question: String,
        userId: String? = null,
        ticketId: String? = null
    ): SupportResponse {
        logger.info("❓ Получен вопрос от пользователя ${userId ?: "неизвестный"}")
        logger.debug("   Вопрос: $question")
        ticketId?.let { logger.debug("   Связан с тикетом: $it") }

        // 1. Получить контекст пользователя через MCP (если указан userId)
        var userContext: String? = null
        var userTickets: String? = null

        if (userId != null) {
            logger.info("👤 Получение информации о пользователе через MCP...")
            userContext = supportTools.executeTool("get_user", mapOf("user_id" to userId))
            userTickets = supportTools.executeTool("get_user_tickets", mapOf("user_id" to userId))
            logger.info("✅ Контекст пользователя получен")
        }

        // 2. Получить детали тикета через MCP (если указан ticketId)
        var ticketContext: String? = null
        if (ticketId != null) {
            logger.info("🎫 Получение деталей тикета через MCP...")
            ticketContext = supportTools.executeTool("get_ticket_details", mapOf("ticket_id" to ticketId))
            logger.info("✅ Детали тикета получены")
        }

        // 3. Поиск похожих решенных тикетов через MCP
        logger.info("🔍 Поиск похожих решенных тикетов...")
        val similarTickets = supportTools.executeTool("get_similar_tickets", mapOf("description" to question))
        logger.info("✅ Найдены похожие тикеты")

        // 4. Поиск в FAQ и документации через RAG
        logger.info("📚 Поиск в FAQ и документации через RAG...")
        val faqChunks = ragSearchService.search(question, topK = 3)
        logger.info("✅ Найдено ${faqChunks.size} релевантных фрагментов в документации")

        // 5. Дополнительный поиск в документации по ключевым словам
        val keywords = extractKeywords(question)
        val additionalDocs = if (keywords.isNotEmpty()) {
            ragSearchService.search(keywords.joinToString(" "), topK = 2)
        } else {
            emptyList()
        }

        // 6. Формирование system prompt
        val systemPrompt = buildSupportSystemPrompt()

        // 7. Формирование user prompt с контекстом
        val userPrompt = buildSupportUserPrompt(
            question = question,
            userContext = userContext,
            userTickets = userTickets,
            ticketContext = ticketContext,
            similarTickets = similarTickets,
            faqChunks = faqChunks.map { it.text },
            additionalDocs = additionalDocs.map { it.text }
        )

        logger.info("📋 Промпты сформированы")

        // 8. Вызов AI модели для генерации ответа
        val request = CompletionRequest(
            modelId = "yandexgpt-lite",
            messages = listOf(
                AIMessage(role = "user", content = userPrompt)
            ),
            temperature = 0.5,  // Средняя temperature для баланса между консистентностью и креативностью
            maxTokens = 2000,
            systemPrompt = systemPrompt,
            tools = null
        )

        val response = modelRegistry.complete(request)
        logger.info("✅ Ответ от AI получен (tokens in=${response.tokenUsage.inputTokens}, out=${response.tokenUsage.outputTokens})")

        // 9. Формирование ответа
        val usedSources = extractUsedSources(faqChunks, additionalDocs)
        val relatedTickets = extractRelatedTickets(similarTickets)

        return SupportResponse(
            answer = response.text,
            usedSources = usedSources,
            relatedTickets = relatedTickets,
            userContext = userContext,
            confidence = calculateConfidence(faqChunks, similarTickets)
        )
    }

    /**
     * Построить system prompt для ассистента поддержки
     */
    private fun buildSupportSystemPrompt(): String {
        return """
Ты — ассистент технической поддержки для AI Challenge KMP.

Твоя задача — помогать пользователям решать проблемы, используя:
- FAQ и документацию (предоставлена в контексте)
- Историю похожих решенных тикетов
- Информацию о профиле пользователя и его предыдущих обращениях

Принципы работы:
1. Давай четкие, практичные ответы с конкретными шагами
2. Если проблема уже решалась - ссылайся на предыдущие тикеты
3. Используй примеры кода и команды там, где уместно
4. Если не уверен в ответе - честно скажи об этом и предложи обратиться к специалисту
5. Будь вежлив и понятен

Формат ответа:
- Краткое объяснение проблемы
- Пошаговое решение
- Дополнительные рекомендации (если есть)
""".trimIndent()
    }

    /**
     * Построить user prompt с контекстом
     */
    private fun buildSupportUserPrompt(
        question: String,
        userContext: String?,
        userTickets: String?,
        ticketContext: String?,
        similarTickets: String,
        faqChunks: List<String>,
        additionalDocs: List<String>
    ): String {
        val prompt = StringBuilder()

        prompt.appendLine("# ВОПРОС ПОЛЬЗОВАТЕЛЯ:")
        prompt.appendLine(question)
        prompt.appendLine()

        // Контекст пользователя
        if (userContext != null) {
            prompt.appendLine("# ИНФОРМАЦИЯ О ПОЛЬЗОВАТЕЛЕ:")
            prompt.appendLine(userContext)
            prompt.appendLine()
        }

        // Тикеты пользователя
        if (userTickets != null && !userTickets.contains("нет тикетов")) {
            prompt.appendLine("# ПРЕДЫДУЩИЕ ТИКЕТЫ ПОЛЬЗОВАТЕЛЯ:")
            prompt.appendLine(userTickets)
            prompt.appendLine()
        }

        // Контекст текущего тикета
        if (ticketContext != null) {
            prompt.appendLine("# КОНТЕКСТ ТЕКУЩЕГО ТИКЕТА:")
            prompt.appendLine(ticketContext)
            prompt.appendLine()
        }

        // Похожие решенные тикеты
        if (!similarTickets.contains("не найдены")) {
            prompt.appendLine("# ПОХОЖИЕ РЕШЕННЫЕ ПРОБЛЕМЫ:")
            prompt.appendLine(similarTickets)
            prompt.appendLine()
        }

        // FAQ и документация
        if (faqChunks.isNotEmpty()) {
            prompt.appendLine("# РЕЛЕВАНТНАЯ ДОКУМЕНТАЦИЯ И FAQ:")
            faqChunks.forEachIndexed { index, chunk ->
                prompt.appendLine("## Фрагмент ${index + 1}:")
                prompt.appendLine(chunk)
                prompt.appendLine()
            }
        }

        // Дополнительная документация
        if (additionalDocs.isNotEmpty()) {
            prompt.appendLine("# ДОПОЛНИТЕЛЬНЫЕ МАТЕРИАЛЫ:")
            additionalDocs.forEachIndexed { index, doc ->
                prompt.appendLine("## Материал ${index + 1}:")
                prompt.appendLine(doc)
                prompt.appendLine()
            }
        }

        prompt.appendLine("# ТВОЯ ЗАДАЧА:")
        prompt.appendLine("На основе предоставленного контекста дай развернутый и полезный ответ на вопрос пользователя.")

        return prompt.toString()
    }

    /**
     * Извлечь ключевые слова из вопроса
     */
    private fun extractKeywords(question: String): List<String> {
        val stopWords = setOf(
            "не", "и", "в", "на", "с", "как", "что", "почему", "где", "когда",
            "можно", "нужно", "ли", "мне", "у", "из", "для", "при", "за"
        )

        return question.lowercase()
            .split(" ", ",", "?", "!", ".")
            .map { it.trim() }
            .filter { it.length > 3 && !stopWords.contains(it) }
            .distinct()
    }

    /**
     * Извлечь использованные источники
     */
    private fun extractUsedSources(
        faqChunks: List<com.aichallengekmp.rag.RagHit>,
        additionalDocs: List<com.aichallengekmp.rag.RagHit>
    ): List<String> {
        val sources = mutableSetOf<String>()

        faqChunks.forEach { chunk ->
            sources.add(chunk.sourceId)
        }

        additionalDocs.forEach { chunk ->
            sources.add(chunk.sourceId)
        }

        return sources.toList()
    }

    /**
     * Извлечь связанные тикеты
     */
    private fun extractRelatedTickets(similarTickets: String): List<String> {
        if (similarTickets.contains("не найдены")) {
            return emptyList()
        }

        // Простой парсинг - ищем id тикетов в формате ticket###
        val ticketPattern = Regex("ticket\\d+")
        return ticketPattern.findAll(similarTickets)
            .map { it.value }
            .distinct()
            .toList()
    }

    /**
     * Рассчитать уверенность ответа
     */
    private fun calculateConfidence(
        faqChunks: List<com.aichallengekmp.rag.RagHit>,
        similarTickets: String
    ): Double {
        var confidence = 0.0

        // Наличие релевантных FAQ
        if (faqChunks.isNotEmpty()) {
            confidence += 0.3
            // Высокая релевантность первого чанка
            if (faqChunks.first().score > 0.7) {
                confidence += 0.2
            }
        }

        // Наличие похожих решенных тикетов
        if (!similarTickets.contains("не найдены")) {
            confidence += 0.5
        }

        return confidence.coerceIn(0.0, 1.0)
    }
}

// ============= Response Model =============

@Serializable
data class SupportResponse(
    val answer: String,
    val usedSources: List<String>,
    val relatedTickets: List<String>,
    val userContext: String? = null,
    val confidence: Double
)
