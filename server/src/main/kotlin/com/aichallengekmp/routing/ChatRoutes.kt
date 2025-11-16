package com.aichallengekmp.routing

import com.aichallengekmp.models.*
import com.aichallengekmp.service.ChatService
import com.aichallengekmp.service.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

/**
 * Роутинг для Chat API
 */
fun Route.chatRoutes() {
    val logger = LoggerFactory.getLogger("ChatRoutes")
    val chatService by inject<ChatService>()

    // ============= Sessions =============

    /**
     * GET /api/sessions - Получить список всех сессий
     */
    get("/sessions") {
        logger.info("📋 GET /api/sessions")
        try {
            val sessions = chatService.getSessionList()
            logger.debug("   Возвращено сессий: ${sessions.size}")
            call.respond(HttpStatusCode.OK, sessions)
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении списка сессий: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", e.message ?: "Неизвестная ошибка")
            )
        }
    }

    /**
     * GET /api/sessions/:id - Получить детали сессии
     */
    get("/sessions/{id}") {
        val sessionId = call.parameters["id"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("bad_request", "Не указан ID сессии")
        )

        logger.info("🔍 GET /api/sessions/$sessionId")

        try {
            val session = chatService.getSessionDetail(sessionId)
            logger.debug("   Возвращено сообщений: ${session.messages.size}")
            call.respond(HttpStatusCode.OK, session)
        } catch (e: NotFoundException) {
            logger.warn("⚠️ Сессия не найдена: $sessionId")
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", e.message ?: "Сессия не найдена")
            )
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении сессии: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", e.message ?: "Неизвестная ошибка")
            )
        }
    }

    /**
     * POST /api/sessions - Создать новую сессию
     */
    post("/sessions") {
        logger.info("🆕 POST /api/sessions")

        try {
            val request = call.receive<CreateSessionRequest>()
            logger.debug("Получил запрос: $request")
            val session = chatService.createSession(
                name = request.name,
                initialMessage = request.initialMessage,
                settings = request.settings
            )

            logger.info("✅ Сессия создана: ${session.id}")
            call.respond(HttpStatusCode.Created, session)
        } catch (e: Exception) {
            logger.error("❌ Ошибка при создании сессии: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", e.message ?: "Неизвестная ошибка")
            )
        }
    }

    /**
     * POST /api/sessions/:id/messages - Отправить сообщение в сессию
     */
    post("/sessions/{id}/messages") {
        val sessionId = call.parameters["id"] ?: return@post call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("bad_request", "Не указан ID сессии")
        )

        logger.info("💬 POST /api/sessions/$sessionId/messages")

        try {
            val request = call.receive<SendMessageRequest>()
            logger.debug("   Сообщение: ${request.message.take(50)}...")

            val session = chatService.sendMessage(sessionId, request.message)

            logger.info("✅ Сообщение обработано")
            call.respond(HttpStatusCode.OK, session)
        } catch (e: NotFoundException) {
            logger.warn("⚠️ Сессия не найдена: $sessionId")
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", e.message ?: "Сессия не найдена")
            )
        } catch (e: Exception) {
            logger.error("❌ Ошибка при отправке сообщения: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", e.message ?: "Ошибка при обработке сообщения: ${e.message}")
            )
        }
    }

    /**
     * PUT /api/sessions/:id/settings - Обновить настройки сессии
     */
    put("/sessions/{id}/settings") {
        val sessionId = call.parameters["id"] ?: return@put call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("bad_request", "Не указан ID сессии")
        )

        logger.info("⚙️ PUT /api/sessions/$sessionId/settings")

        try {
            val request = call.receive<UpdateSettingsRequest>()
            logger.debug("   Новая модель: ${request.settings.modelId}")
            logger.debug("   Температура: ${request.settings.temperature}")

            val settings = chatService.updateSessionSettings(sessionId, request.settings)

            logger.info("✅ Настройки обновлены")
            call.respond(HttpStatusCode.OK, settings)
        } catch (e: NotFoundException) {
            logger.warn("⚠️ Сессия не найдена: $sessionId")
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", e.message ?: "Сессия не найдена")
            )
        } catch (e: Exception) {
            logger.error("❌ Ошибка при обновлении настроек: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", e.message ?: "Неизвестная ошибка")
            )
        }
    }

    /**
     * DELETE /api/sessions/:id - Удалить сессию
     */
    delete("/sessions/{id}") {
        val sessionId = call.parameters["id"] ?: return@delete call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("bad_request", "Не указан ID сессии")
        )

        logger.info("🗑️ DELETE /api/sessions/$sessionId")

        try {
            chatService.deleteSession(sessionId)
            logger.info("✅ Сессия удалена")
            call.respond(HttpStatusCode.OK, SuccessResponse(success = true, message = "Сессия удалена"))
        } catch (e: NotFoundException) {
            logger.warn("⚠️ Сессия не найдена: $sessionId")
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("not_found", e.message ?: "Сессия не найдена")
            )
        } catch (e: Exception) {
            logger.error("❌ Ошибка при удалении сессии: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", e.message ?: "Неизвестная ошибка")
            )
        }
    }

    // ============= Models =============

    /**
     * GET /api/models - Получить список доступных моделей
     */
    get("/models") {
        logger.info("📋 GET /api/models")

        try {
            val models = chatService.getAvailableModels()
            logger.debug("   Доступно моделей: ${models.size}")
            call.respond(HttpStatusCode.OK, models)
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении списка моделей: ${e.message}", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", e.message ?: "Неизвестная ошибка")
            )
        }
    }
}