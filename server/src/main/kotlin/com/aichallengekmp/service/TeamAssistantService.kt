package com.aichallengekmp.service

import com.aichallengekmp.ai.AIMessage
import com.aichallengekmp.ai.CompletionRequest
import com.aichallengekmp.ai.ModelRegistry
import com.aichallengekmp.rag.RagSearchService
import com.aichallengekmp.tools.GitToolsService
import com.aichallengekmp.tools.SupportToolsService
import com.aichallengekmp.tools.ToolDefinition
import com.aichallengekmp.tools.TrackerToolsService
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Командный ассистент - объединяет RAG, MCP и AI для работы с проектом
 *
 * Умеет:
 * - Искать информацию в коде и документации (RAG)
 * - Управлять задачами (TrackerTools через MCP)
 * - Работать с Git/GitHub (GitTools через MCP)
 * - Работать с поддержкой (SupportTools через MCP)
 * - Анализировать приоритеты и давать рекомендации
 *
 * Использует YandexGPT с function calling - модель сама решает какие инструменты вызывать
 */
class TeamAssistantService(
    private val ragSearchService: RagSearchService,
    private val trackerTools: TrackerToolsService,
    private val gitTools: GitToolsService,
    private val supportTools: SupportToolsService,
    private val modelRegistry: ModelRegistry
) {

    private val logger = LoggerFactory.getLogger(TeamAssistantService::class.java)

    /**
     * Обработать запрос к командному ассистенту
     */
    suspend fun ask(question: String): TeamAssistantResponse {
        logger.info("🤖 Получен запрос к командному ассистенту: $question")

        // 1. Подготовка инструментов для AI
        val tools = buildToolDefinitions()
        logger.info("🔧 Подготовлено ${tools.size} инструментов для AI")

        // 2. Формирование промпта
        val systemPrompt = buildSystemPrompt()

        // 3. Вызов AI с инструментами (function calling)
        val request = CompletionRequest(
            modelId = "yandexgpt-lite",
            messages = listOf(
                AIMessage(role = "user", content = question)
            ),
            temperature = 0.7,  // Выше для креативных рекомендаций
            maxTokens = 3000,
            systemPrompt = systemPrompt,
            tools = tools
        )

        val response = modelRegistry.complete(request)
        logger.info("✅ Ответ от AI получен (tokens in=${response.tokenUsage.inputTokens}, out=${response.tokenUsage.outputTokens})")

        // 4. Формирование ответа
        return TeamAssistantResponse(
            answer = response.text,
            toolsUsed = extractToolsUsed(response.text),
            tokenUsage = mapOf(
                "input" to response.tokenUsage.inputTokens,
                "output" to response.tokenUsage.outputTokens,
                "total" to response.tokenUsage.totalTokens
            )
        )
    }

    /**
     * Построить список инструментов для AI
     */
    private fun buildToolDefinitions(): List<ToolDefinition> {
        return listOf(
            // RAG инструменты
            ToolDefinition(
                name = "search_documentation",
                description = "Поиск информации в документации проекта (FAQ, архитектура, API)",
                parameters = mapOf(
                    "query" to "string - поисковый запрос",
                    "top_k" to "number (опционально) - количество результатов (по умолчанию 3)"
                )
            ),
            ToolDefinition(
                name = "search_code",
                description = "Поиск фрагментов кода в проекте (Kotlin файлы)",
                parameters = mapOf(
                    "query" to "string - что искать в коде",
                    "top_k" to "number (опционально) - количество результатов (по умолчанию 3)"
                )
            ),

            // Трекер задач
            ToolDefinition(
                name = "get_all_tasks",
                description = "Получить список всех задач в трекере",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "get_task_info",
                description = "Получить детальную информацию о конкретной задаче",
                parameters = mapOf(
                    "task_id" to "string - ID задачи"
                )
            ),
            ToolDefinition(
                name = "create_task",
                description = "Создать новую задачу в трекере",
                parameters = mapOf(
                    "title" to "string - название задачи",
                    "description" to "string - описание задачи",
                    "priority" to "string - приоритет (low, medium, high)"
                )
            ),
            ToolDefinition(
                name = "update_task_status",
                description = "Обновить статус задачи",
                parameters = mapOf(
                    "task_id" to "string - ID задачи",
                    "status" to "string - новый статус (open, in_progress, done)"
                )
            ),

            // Git/GitHub
            ToolDefinition(
                name = "get_git_branch",
                description = "Получить текущую git ветку",
                parameters = emptyMap()
            ),

            // Поддержка
            ToolDefinition(
                name = "search_support_tickets",
                description = "Поиск тикетов поддержки по категории или ключевым словам",
                parameters = mapOf(
                    "category" to "string (опционально) - категория тикета",
                    "keyword" to "string (опционально) - ключевое слово"
                )
            ),

            // Анализ и рекомендации
            ToolDefinition(
                name = "analyze_task_priorities",
                description = "Проанализировать задачи по приоритетам и дать рекомендации по очередности выполнения",
                parameters = emptyMap()
            )
        )
    }

    /**
     * Построить system prompt для командного ассистента
     */
    private fun buildSystemPrompt(): String {
        return """
Ты — командный ассистент AI Challenge KMP проекта.

Твои возможности:
1. **Знание проекта**: поиск в коде и документации через RAG
2. **Управление задачами**: создание, просмотр, обновление задач в трекере
3. **Работа с Git**: информация о ветках, PR, статусе репозитория
4. **Поддержка**: доступ к тикетам поддержки и их анализ
5. **Анализ приоритетов**: рекомендации по очередности задач

Принципы работы:
- Используй доступные инструменты для получения актуальной информации
- Давай конкретные, действенные рекомендации
- При анализе приоритетов учитывай: срочность, важность, зависимости, техдолг
- Будь проактивен: если видишь проблему, предложи решение
- Структурируй ответы: заголовки, списки, выделение ключевых моментов

Ты помогаешь команде быть продуктивнее!
""".trimIndent()
    }

    /**
     * Извлечь использованные инструменты из ответа
     */
    private fun extractToolsUsed(responseText: String): List<String> {
        // Простая эвристика - извлечение упоминаний инструментов
        val tools = mutableListOf<String>()
        val toolNames = listOf(
            "search_documentation", "search_code", "get_all_tasks",
            "get_task_info", "create_task", "update_task_status",
            "get_git_branch", "search_support_tickets", "analyze_task_priorities"
        )

        toolNames.forEach { toolName ->
            if (responseText.contains(toolName, ignoreCase = true)) {
                tools.add(toolName)
            }
        }

        return tools
    }
}

// ============= Response Model =============

@Serializable
data class TeamAssistantResponse(
    val answer: String,
    val toolsUsed: List<String>,
    val tokenUsage: Map<String, Int>
)
