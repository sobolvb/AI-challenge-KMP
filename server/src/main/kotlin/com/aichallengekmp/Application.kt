package com.aichallengekmp

import com.aichallengekmp.di.appModule
import com.aichallengekmp.routing.chatRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

@ExperimentalSerializationApi
fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")

    logger.info("🚀 Запуск AI Challenge KMP Server")

    // Koin DI
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    // Content Negotiation
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            explicitNulls = false
        })
    }

    // Routing
    routing {
        // Health check endpoint
        get("/health") {
            call.respond(mapOf(
                "status" to "OK",
                "service" to "AI Challenge KMP",
                "timestamp" to System.currentTimeMillis()
            ))
        }

        // Chat API routes
        route("/api") {
            chatRoutes()
        }
    }

    logger.info("✅ Сервер успешно запущен и готов к работе")
}

fun main() {
    val logger = LoggerFactory.getLogger("Main")
    logger.info("🌟 AI Challenge KMP - Starting...")

    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}