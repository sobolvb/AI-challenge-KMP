package local

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import local.routing.chatRouting
import local.data.*
import local.engine.ReasoningEngine
import local.service.YandexAiService

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    
    routing {
        // Тестовый эндпоинт для проверки работы
        get("/test") {
            call.respond(mapOf("status" to "OK", "message" to "Сервер работает!"))
        }
        
        chatRouting()
    }
}

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}
