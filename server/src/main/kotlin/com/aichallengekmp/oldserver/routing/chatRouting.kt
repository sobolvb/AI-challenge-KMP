package local.routing

import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import local.data.*
import local.engine.ReasoningEngine
import local.service.YandexAiService

fun Routing.chatRouting() {
    val yandex = YandexAiService()
    val engine = ReasoningEngine(yandex)

    // Эндпоинт для выполнения одной задачи
    post("/chat/solve") {
        try {
            val req = call.receive<ChatRequest>()
            println("🎯 Запрос на выполнение задачи: ${req.task}")

            val response = engine.solveTask(req.task)

            call.respond(response)
        } catch (e: Exception) {
            println("Ошибка в /chat/solve: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }

    // Эндпоинт для сравнения разных типов запросов
    post("/chat/compare-tokens") {
        try {
            println("🔬 Запуск сравнения токенов")

            val response = engine.compareRequestTypes()

            call.respond(response)
        } catch (e: Exception) {
            println("Ошибка в /chat/compare-tokens: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }

    // Эндпоинт для диалогового режима
    post("/chat/dialog") {
        try {
            val req = call.receive<DialogRequest>()
            println("💬 Диалоговое сообщение от сессии: ${req.sessionId}")

            val response = engine.processDialog(req.sessionId, req.message)

            call.respond(response)
        } catch (e: Exception) {
            println("Ошибка в /chat/dialog: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }

    // Эндпоинт для получения статистики сжатия
    get("/chat/compression-stats/{sessionId}") {
        try {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("sessionId is required")
            println("📊 Запрос статистики для сессии: $sessionId")

            val stats = engine.getCompressionStats(sessionId)
            if (stats != null) {
                call.respond(stats)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Сессия не найдена"))
            }
        } catch (e: Exception) {
            println("Ошибка в /chat/compression-stats: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }

    // Эндпоинт для анализа эффективности сжатия
    get("/chat/analyze-compression/{sessionId}") {
        try {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("sessionId is required")
            println("🔍 Запрос анализа сжатия для сессии: $sessionId")

            val analysis = engine.analyzeCompression(sessionId)
            call.respond(mapOf("sessionId" to sessionId, "analysis" to analysis))
        } catch (e: Exception) {
            println("Ошибка в /chat/analyze-compression: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }

    // Эндпоинт для получения информации о сессии
    get("/chat/session-info/{sessionId}") {
        try {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("sessionId is required")

            val info = engine.getSessionInfo(sessionId)
            if (info != null) {
                call.respond(info)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Сессия не найдена"))
            }
        } catch (e: Exception) {
            println("Ошибка в /chat/session-info: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }

    // Эндпоинт для удаления сессии
    delete("/chat/session/{sessionId}") {
        try {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("sessionId is required")

            engine.deleteSession(sessionId)
            call.respond(mapOf("success" to true, "message" to "Сессия удалена"))
        } catch (e: Exception) {
            println("Ошибка в /chat/session: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.message))
        }
    }
}
