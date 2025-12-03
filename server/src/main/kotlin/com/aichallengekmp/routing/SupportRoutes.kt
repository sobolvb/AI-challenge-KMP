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
 * API endpoints для системы поддержки
 */
fun Route.supportRoutes() {
    val logger = LoggerFactory.getLogger("SupportRoutes")

    route("/support") {
        /**
         * POST /api/support/ask
         * Задать вопрос ассистенту поддержки
         */
        post("/ask") {
            logger.info("📨 Получен запрос на вопрос пользователя")

            val request = try {
                call.receive<SupportQuestionRequest>()
            } catch (e: Exception) {
                logger.error("❌ Ошибка парсинга запроса: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Неверный формат запроса")
                )
                return@post
            }

            // Валидация
            if (request.question.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Вопрос не может быть пустым")
                )
                return@post
            }

            try {
                val response = AppContainer.supportAssistantService.answerQuestion(
                    question = request.question,
                    userId = request.userId,
                    ticketId = request.ticketId
                )

                logger.info("✅ Ответ сформирован (confidence=${response.confidence})")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                logger.error("❌ Ошибка обработки вопроса: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Ошибка обработки вопроса: ${e.message}")
                )
            }
        }

        /**
         * GET /api/support/user/{userId}
         * Получить информацию о пользователе
         */
        get("/user/{userId}") {
            val userId = call.parameters["userId"]
            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId обязателен"))
                return@get
            }

            try {
                val userInfo = AppContainer.supportTools.executeTool(
                    "get_user",
                    mapOf("user_id" to userId)
                )

                if (userInfo.contains("не найден")) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(userInfo))
                } else {
                    call.respond(HttpStatusCode.OK, RawJsonResponse(userInfo))
                }
            } catch (e: Exception) {
                logger.error("❌ Ошибка получения пользователя: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Ошибка: ${e.message}")
                )
            }
        }

        /**
         * GET /api/support/user/{userId}/tickets
         * Получить список тикетов пользователя
         */
        get("/user/{userId}/tickets") {
            val userId = call.parameters["userId"]
            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("userId обязателен"))
                return@get
            }

            val status = call.request.queryParameters["status"]

            try {
                val tickets = AppContainer.supportTools.executeTool(
                    "get_user_tickets",
                    buildMap {
                        put("user_id", userId)
                        status?.let { put("status", it) }
                    }
                )

                call.respond(HttpStatusCode.OK, RawJsonResponse(tickets))
            } catch (e: Exception) {
                logger.error("❌ Ошибка получения тикетов: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Ошибка: ${e.message}")
                )
            }
        }

        /**
         * GET /api/support/ticket/{ticketId}
         * Получить детали тикета
         */
        get("/ticket/{ticketId}") {
            val ticketId = call.parameters["ticketId"]
            if (ticketId == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("ticketId обязателен"))
                return@get
            }

            try {
                val ticketDetails = AppContainer.supportTools.executeTool(
                    "get_ticket_details",
                    mapOf("ticket_id" to ticketId)
                )

                if (ticketDetails.contains("не найден")) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(ticketDetails))
                } else {
                    call.respond(HttpStatusCode.OK, RawJsonResponse(ticketDetails))
                }
            } catch (e: Exception) {
                logger.error("❌ Ошибка получения тикета: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Ошибка: ${e.message}")
                )
            }
        }

        /**
         * POST /api/support/tickets/search
         * Поиск тикетов
         */
        post("/tickets/search") {
            val request = try {
                call.receive<SearchTicketsRequest>()
            } catch (e: Exception) {
                logger.error("❌ Ошибка парсинга запроса: ${e.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Неверный формат запроса")
                )
                return@post
            }

            try {
                val tickets = AppContainer.supportTools.executeTool(
                    "search_tickets",
                    buildMap {
                        request.category?.let { put("category", it) }
                        request.keyword?.let { put("keyword", it) }
                    }
                )

                call.respond(HttpStatusCode.OK, RawJsonResponse(tickets))
            } catch (e: Exception) {
                logger.error("❌ Ошибка поиска тикетов: ${e.message}", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Ошибка: ${e.message}")
                )
            }
        }
    }
}

// ============= Request/Response Models =============

@Serializable
data class SupportQuestionRequest(
    val question: String,
    val userId: String? = null,
    val ticketId: String? = null
)

@Serializable
data class SearchTicketsRequest(
    val category: String? = null,
    val keyword: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class RawJsonResponse(
    val data: String
)
