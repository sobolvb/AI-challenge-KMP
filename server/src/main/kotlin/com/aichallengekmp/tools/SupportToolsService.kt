package com.aichallengekmp.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Сервис для работы с системой поддержки (CRM)
 * Предоставляет инструменты для получения информации о пользователях и тикетах
 */
class SupportToolsService(
    private val dataFilePath: String = "server/src/main/resources/support-data.json"
) {
    private val logger = LoggerFactory.getLogger(SupportToolsService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private var supportData: SupportData

    init {
        supportData = loadData()
        logger.info("✅ Загружено ${supportData.users.size} пользователей и ${supportData.tickets.size} тикетов")
    }

    /**
     * Загрузить данные из JSON файла
     */
    private fun loadData(): SupportData {
        val file = File(dataFilePath)
        if (!file.exists()) {
            logger.warn("⚠️ Файл с данными не найден: $dataFilePath. Используем пустые данные.")
            return SupportData(emptyList(), emptyList())
        }

        val jsonString = file.readText()
        return json.decodeFromString<SupportData>(jsonString)
    }

    /**
     * Получить список всех доступных инструментов
     */
    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "get_user",
                description = "Получить информацию о пользователе по ID",
                parameters = mapOf(
                    "user_id" to "string - ID пользователя"
                )
            ),
            ToolDefinition(
                name = "get_user_tickets",
                description = "Получить список всех тикетов пользователя",
                parameters = mapOf(
                    "user_id" to "string - ID пользователя",
                    "status" to "string (опционально) - фильтр по статусу (open, in_progress, resolved)"
                )
            ),
            ToolDefinition(
                name = "get_ticket_details",
                description = "Получить детальную информацию о тикете включая историю",
                parameters = mapOf(
                    "ticket_id" to "string - ID тикета"
                )
            ),
            ToolDefinition(
                name = "search_tickets",
                description = "Поиск тикетов по категории или ключевым словам",
                parameters = mapOf(
                    "category" to "string (опционально) - категория (auth, ai, rag, code_review, deployment, general, ui, account, performance)",
                    "keyword" to "string (опционально) - ключевое слово для поиска в теме и описании"
                )
            ),
            ToolDefinition(
                name = "get_similar_tickets",
                description = "Найти похожие решенные тикеты по описанию проблемы",
                parameters = mapOf(
                    "description" to "string - описание проблемы пользователя"
                )
            )
        )
    }

    /**
     * Выполнить инструмент
     */
    suspend fun executeTool(toolName: String, arguments: Map<String, Any?>): String {
        logger.info("🔧 Выполнение инструмента: $toolName")
        logger.debug("   Аргументы: $arguments")

        return try {
            val result = when (toolName) {
                "get_user" -> {
                    val userId = arguments["user_id"]?.toString()
                        ?: return "Ошибка: параметр user_id обязателен"
                    getUser(userId)
                }
                "get_user_tickets" -> {
                    val userId = arguments["user_id"]?.toString()
                        ?: return "Ошибка: параметр user_id обязателен"
                    val status = arguments["status"]?.toString()
                    getUserTickets(userId, status)
                }
                "get_ticket_details" -> {
                    val ticketId = arguments["ticket_id"]?.toString()
                        ?: return "Ошибка: параметр ticket_id обязателен"
                    getTicketDetails(ticketId)
                }
                "search_tickets" -> {
                    val category = arguments["category"]?.toString()
                    val keyword = arguments["keyword"]?.toString()
                    searchTickets(category, keyword)
                }
                "get_similar_tickets" -> {
                    val description = arguments["description"]?.toString()
                        ?: return "Ошибка: параметр description обязателен"
                    getSimilarTickets(description)
                }
                else -> "Ошибка: неизвестный инструмент $toolName"
            }

            logger.info("✅ Инструмент $toolName выполнен успешно")
            result
        } catch (e: Exception) {
            logger.error("❌ Ошибка выполнения инструмента $toolName: ${e.message}", e)
            "Ошибка выполнения инструмента: ${e.message}"
        }
    }

    /**
     * Получить информацию о пользователе
     */
    private fun getUser(userId: String): String {
        val user = supportData.users.find { it.id == userId }
            ?: return "Пользователь с ID $userId не найден"

        return json.encodeToString(User.serializer(), user)
    }

    /**
     * Получить список тикетов пользователя
     */
    private fun getUserTickets(userId: String, status: String?): String {
        // Проверяем существование пользователя
        val user = supportData.users.find { it.id == userId }
            ?: return "Пользователь с ID $userId не найден"

        var tickets = supportData.tickets.filter { it.userId == userId }

        // Фильтруем по статусу если указан
        if (!status.isNullOrBlank()) {
            tickets = tickets.filter { it.status == status }
        }

        if (tickets.isEmpty()) {
            return if (status != null) {
                "У пользователя ${user.name} нет тикетов со статусом $status"
            } else {
                "У пользователя ${user.name} нет тикетов"
            }
        }

        // Возвращаем краткую информацию о тикетах
        val summary = tickets.joinToString("\n") { ticket ->
            "ID: ${ticket.id}, Тема: ${ticket.subject}, Статус: ${ticket.status}, Приоритет: ${ticket.priority}, Категория: ${ticket.category}"
        }

        return summary
    }

    /**
     * Получить детальную информацию о тикете
     */
    private fun getTicketDetails(ticketId: String): String {
        val ticket = supportData.tickets.find { it.id == ticketId }
            ?: return "Тикет с ID $ticketId не найден"

        return json.encodeToString(Ticket.serializer(), ticket)
    }

    /**
     * Поиск тикетов по категории или ключевым словам
     */
    private fun searchTickets(category: String?, keyword: String?): String {
        var tickets = supportData.tickets

        // Фильтр по категории
        if (!category.isNullOrBlank()) {
            tickets = tickets.filter { it.category == category }
        }

        // Фильтр по ключевому слову
        if (!keyword.isNullOrBlank()) {
            val lowerKeyword = keyword.lowercase()
            tickets = tickets.filter {
                it.subject.lowercase().contains(lowerKeyword) ||
                it.description.lowercase().contains(lowerKeyword)
            }
        }

        if (tickets.isEmpty()) {
            return "Тикеты не найдены по заданным критериям"
        }

        // Возвращаем краткую информацию
        val summary = tickets.joinToString("\n") { ticket ->
            "ID: ${ticket.id}, Тема: ${ticket.subject}, Статус: ${ticket.status}, Приоритет: ${ticket.priority}, Категория: ${ticket.category}"
        }

        return summary
    }

    /**
     * Найти похожие решенные тикеты
     */
    private fun getSimilarTickets(description: String): String {
        val lowerDescription = description.lowercase()
        val keywords = lowerDescription.split(" ").filter { it.length > 3 }

        // Ищем решенные тикеты с похожим описанием
        val similarTickets = supportData.tickets
            .filter { it.status == "resolved" }
            .map { ticket ->
                val similarity = calculateSimilarity(
                    keywords,
                    "${ticket.subject} ${ticket.description}".lowercase()
                )
                ticket to similarity
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }

        if (similarTickets.isEmpty()) {
            return "Похожие решенные тикеты не найдены"
        }

        // Возвращаем информацию о похожих тикетах
        val result = similarTickets.joinToString("\n\n") { ticket ->
            val resolution = ticket.history.lastOrNull()?.comment ?: "Нет информации о решении"
            "ID: ${ticket.id}\nТема: ${ticket.subject}\nКатегория: ${ticket.category}\nРешение: $resolution"
        }

        return result
    }

    /**
     * Простой подсчет схожести по количеству совпадающих ключевых слов
     */
    private fun calculateSimilarity(keywords: List<String>, text: String): Int {
        return keywords.count { text.contains(it) }
    }
}

// ============= Data Models =============

@Serializable
data class SupportData(
    val users: List<User>,
    val tickets: List<Ticket>
)

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val plan: String,
    val registeredAt: String,
    val status: String,
    val company: String? = null
)

@Serializable
data class Ticket(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: String,
    val priority: String,
    val category: String,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val assignedTo: String? = null,
    val history: List<HistoryEntry>
)

@Serializable
data class HistoryEntry(
    val timestamp: String,
    val action: String,
    val actor: String,
    val comment: String
)
