package com.aichallengekmp.routing

import com.aichallengekmp.di.AppContainer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * API endpoints для командного ассистента
 */
fun Route.teamRoutes() {
    val logger = LoggerFactory.getLogger("TeamRoutes")

    route("/team") {
        /**
         * POST /api/team/ask
         * Задать вопрос командному ассистенту
         */
        post("/ask") {
            logger.info("🤖 Получен запрос к командному ассистенту")

            val request = try {
                call.receive<TeamQuestionRequest>()
            } catch (e: Exception) {
                logger.error("❌ Ошибка парсинга запроса: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    TeamErrorResponse("Неверный формат запроса")
                )
                return@post
            }

            // Валидация
            if (request.question.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    TeamErrorResponse("Вопрос не может быть пустым")
                )
                return@post
            }

            try {
                val response = AppContainer.teamAssistantService.ask(request.question)

                logger.info("✅ Ответ сформирован (использовано ${response.toolsUsed.size} инструментов)")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                logger.error("❌ Ошибка обработки запроса: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    TeamErrorResponse("Ошибка обработки запроса: ${e.message}")
                )
            }
        }

        /**
         * GET /api/team/status
         * Получить статус проекта (задачи, PR, тикеты)
         */
        get("/status") {
            logger.info("📊 Запрос статуса проекта")

            try {
                // Получаем информацию из всех источников
                val tasksCount = AppContainer.trackerTools.executeTool("get_issues_count", emptyMap())
                val gitBranch = AppContainer.gitTools.executeTool("get_git_branch", emptyMap())

                val statusInfo = buildString {
                    appendLine("📊 Статус проекта AI Challenge KMP")
                    appendLine()
                    appendLine("**Задачи:**")
                    appendLine(tasksCount)
                    appendLine()
                    appendLine("**Git:**")
                    appendLine("Текущая ветка: $gitBranch")
                }

                call.respond(HttpStatusCode.OK, ProjectStatusResponse(status = statusInfo))

            } catch (e: Exception) {
                logger.error("❌ Ошибка получения статуса: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    TeamErrorResponse("Ошибка: ${e.message}")
                )
            }
        }

        /**
         * GET /api/team/tools
         * Получить список доступных инструментов
         */
        get("/tools") {
            logger.info("🔧 Запрос списка инструментов")

            val tools = listOf(
                "search_documentation" to "Поиск в документации проекта",
                "search_code" to "Поиск в коде проекта",
                "get_all_tasks" to "Получить все задачи",
                "get_task_info" to "Детали задачи",
                "create_task" to "Создать задачу",
                "update_task_status" to "Обновить статус задачи",
                "get_git_branch" to "Текущая git ветка",
                "search_support_tickets" to "Поиск тикетов поддержки",
                "analyze_task_priorities" to "Анализ приоритетов задач"
            )

            call.respond(HttpStatusCode.OK, AvailableToolsResponse(
                tools = tools.map { (name, description) ->
                    ToolInfo(name = name, description = description)
                }
            ))
        }
    }
}

// ============= Request/Response Models =============

@Serializable
data class TeamQuestionRequest(
    val question: String
)

@Serializable
data class TeamErrorResponse(
    val error: String
)

@Serializable
data class ProjectStatusResponse(
    val status: String
)

@Serializable
data class AvailableToolsResponse(
    val tools: List<ToolInfo>
)

@Serializable
data class ToolInfo(
    val name: String,
    val description: String
)
