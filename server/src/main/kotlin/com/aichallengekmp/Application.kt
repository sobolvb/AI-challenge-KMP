package com.aichallengekmp

import com.aichallengekmp.di.AppContainer
import com.aichallengekmp.routing.chatRoutes
import com.aichallengekmp.routing.ragRoutes
import com.aichallengekmp.routing.codeReviewRoutes
import com.aichallengekmp.routing.supportRoutes
import com.aichallengekmp.routing.teamRoutes
import com.aichallengekmp.mcp.configureMcpServer
import com.aichallengekmp.mcp.configureTrackerMcpServer
import com.aichallengekmp.mcp.configureRemindersMcpServer
import com.aichallengekmp.mcp.configureGitMcpServer
import com.aichallengekmp.mcp.configureSupportMcpServer
import com.aichallengekmp.scheduler.ReminderScheduler
import com.aichallengekmp.scheduler.ReminderNotifications
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.WebSockets
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Ответ health check endpoint'а
 */
@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val timestamp: Long
)

@ExperimentalSerializationApi
fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")

    logger.info("🚀 Запуск AI Challenge KMP Server")

    // Инициализируем DI container
    AppContainer.chatService
    AppContainer.reminderService

    // Content Negotiation
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }
    
    // SSE для MCP
    install(SSE)

    // WebSockets для MCP (нужен для WebSocketClientTransport)
    install(WebSockets)

    // MCP Server для Яндекс.Трекер + напоминания (единый, для обратной совместимости)
    configureMcpServer(
        trackerTools = AppContainer.trackerTools,
        reminderService = AppContainer.reminderService
    )

    // Отдельный MCP сервер только для трекера
    configureTrackerMcpServer(
        trackerTools = AppContainer.trackerTools
    )

    // Отдельный MCP сервер только для напоминаний
    configureRemindersMcpServer(
        reminderService = AppContainer.reminderService
    )

    // Отдельный MCP сервер для Git/GitHub
    configureGitMcpServer(
        gitTools = AppContainer.gitTools
    )

    // Отдельный MCP сервер для системы поддержки
    configureSupportMcpServer(
        supportTools = AppContainer.supportTools
    )

    // Фоновый планировщик напоминаний, работает 24/7 после старта сервера
    ReminderScheduler(
        reminderService = AppContainer.reminderService,
        intervalMinutes = 100L // можно вынести в конфиг/ENV
    ) { summary ->
//        // Рассылаем всем подписанным клиентам по SSE
//        ReminderNotifications.broadcast(summary)
//
//        // ⚠️ ВРЕМЕННЫЙ УПРОЩЁННЫЙ ВАРИАНТ: продублируем напоминание в последнюю активную сессию,
//        // чтобы клиент точно увидел его как новое сообщение без SSE.
//        try {
//            val sessions = AppContainer.sessionDao.getAll()
//            val lastSession = sessions.maxByOrNull { it.updatedAt }
//            if (lastSession != null) {
//                val now = System.currentTimeMillis()
//                val message = com.aichallengekmp.database.Message(
//                    id = java.util.UUID.randomUUID().toString(),
//                    sessionId = lastSession.id,
//                    role = "assistant",
//                    content = "Сводка напоминаний:\n$summary",
//                    modelId = "reminder-system",
//                    inputTokens = 0,
//                    outputTokens = 0,
//                    createdAt = now
//                )
//                AppContainer.messageDao.insert(message)
//                AppContainer.sessionDao.updateTimestamp(lastSession.id, now)
//            }
//        } catch (e: Exception) {
//            logger.error("❌ Ошибка при записи напоминания в чат: ${e.message}", e)
//        }
    }.start()

    // Routing (без дублирования ContentNegotiation)
    routing {
        // Health check endpoint
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "OK",
                    service = "AI Challenge KMP",
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // Chat API routes
        route("/api") {
            chatRoutes()
            ragRoutes()
            codeReviewRoutes()
            supportRoutes()
            teamRoutes()

            // SSE-стрим с напоминаниями для клиента KMP (Ktor 3.x API)
            sse("/reminders/stream") {
                val unsubscribe = ReminderNotifications.subscribe { summary ->
                    send(ServerSentEvent(data = summary))
                }
                try {
                    awaitCancellation() // держим соединение открытым, пока клиент не отключится
                } finally {
                    unsubscribe()
                }
            }
        }
    }

    logger.info("✅ Сервер успешно запущен и готов к работе")
}

fun main() {
    val logger = LoggerFactory.getLogger("Main")
    logger.info("🌟 AI Challenge KMP - Starting...")

    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0"
    ) {
        module()
    }.start(wait = true)
}

/*

 */