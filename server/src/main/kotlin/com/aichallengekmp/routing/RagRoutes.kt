package com.aichallengekmp.routing

import com.aichallengekmp.di.AppContainer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Роуты для работы с RAG-индексом документов
 */
fun Route.ragRoutes() {
    val logger = LoggerFactory.getLogger("RagRoutes")

    route("/rag") {
        /**
         * POST /api/rag/index/readme
         *
         * Индексирует файл README.md из корня репозитория.
         * Путь к файлу задаётся относительно модуля server:
         *  - основной вариант: ../README.md (из каталога server/ в корень монорепо)
         */
        post("/index/readme") {
            logger.info("📚 POST /api/rag/index/readme — индексация README.md")

            val candidatePaths = listOf(
                "../README.md",   // из server/ в корень репозитория
                "README.md"       // на случай запуска из корня проекта
            )

            val readmeFile = candidatePaths
                .map { File(it) }
                .firstOrNull { it.exists() && it.isFile }

            if (readmeFile == null) {
                logger.error("❌ README.md не найден ни по одному из путей: {}", candidatePaths)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to "error",
                        "message" to "README.md не найден. Ожидались пути: ${candidatePaths.joinToString()}"
                    )
                )
                return@post
            }

            val text = try {
                readmeFile.readText()
            } catch (e: Exception) {
                logger.error("❌ Ошибка чтения README.md: {}", e.message, e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to "error",
                        "message" to "Не удалось прочитать README.md: ${e.message}"
                    )
                )
                return@post
            }

            try {
                val sourceId = "README.md"
                AppContainer.ragIndexService.indexDocument(sourceId, text)

                // Подсчёт количества чанков этого документа в индексе
                val totalChunks = AppContainer.ragChunkDao
                    .getAllChunks()
                    .count { it.sourceId == sourceId }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "ok",
                        "sourceId" to sourceId,
                        // chunks как строка, чтобы избежать проблем сериализации разнородных типов в Map
                        "chunks" to totalChunks.toString()
                    )
                )
            } catch (e: Exception) {
                logger.error("❌ Ошибка индексации README.md: {}", e.message, e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to "error",
                        "message" to (e.message ?: "Неизвестная ошибка при индексации README.md")
                    )
                )
            }
        }
    }
}
