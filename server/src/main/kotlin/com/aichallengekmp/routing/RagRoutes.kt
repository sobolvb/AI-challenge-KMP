package com.aichallengekmp.routing

import com.aichallengekmp.ai.AIMessage
import com.aichallengekmp.ai.CompletionRequest
import com.aichallengekmp.di.AppContainer
import com.aichallengekmp.models.RagAnswerVariantDto
import com.aichallengekmp.models.RagAskRequest
import com.aichallengekmp.models.RagAskResponse
import com.aichallengekmp.models.RagChunkDto
import com.aichallengekmp.models.TokenUsageDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
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

        /**
         * POST /api/rag/ask
         *
         * Экспериментальный endpoint для сравнения ответов модели
         *  - с использованием RAG (поиск по локальному индексу документации)
         *  - и без RAG (baseline)
         *
         * Не создаёт сессий и не использует tools/function calling.
         */
        post("/ask") {
            val logger = LoggerFactory.getLogger("RagAskRoute")
            logger.info("❓ POST /api/rag/ask — сравнение режимов RAG / baseline")

            val request = try {
                call.receive<RagAskRequest>()
            } catch (e: Exception) {
                logger.warn("⚠️ Некорректный запрос к /api/rag/ask: {}", e.message)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "status" to "error",
                        "message" to (e.message ?: "Некорректный формат запроса")
                    )
                )
                return@post
            }

            logger.info("   Вопрос: '{}' (topK={})", request.question.take(120), request.topK)

            try {
                // 1. Гарантированно выполняем RAG-поиск по локальному индексу
                val hits = AppContainer.ragSearchService.search(request.question, request.topK)
                logger.info("🔎 RAG-поиск для /api/rag/ask вернул {} фрагментов", hits.size)

                val usedChunks = hits.map { hit ->
                    RagChunkDto(
                        sourceId = hit.sourceId,
                        chunkIndex = hit.chunkIndex,
                        score = hit.score,
                        text = hit.text
                    )
                }

                // Базовый system prompt (общие инструкции), без RAG-контекста
                val baseSystemPrompt = request.systemPrompt?.takeIf { it.isNotBlank() }
                    ?: "Ты — помощник разработчика. Отвечай кратко и по делу на русском языке."

                // 2. Формируем запрос с RAG-контекстом (явно включаем найденные чанки)
                val ragUserContent = buildString {
                    if (hits.isNotEmpty()) {
                        appendLine("Ниже приведены фрагменты документации проекта, которые могут быть полезны для ответа на вопрос.")
                        appendLine("Используй их содержание при формировании ответа.")
                        appendLine()

                        hits.forEachIndexed { index, hit ->
                            appendLine("### Фрагмент ${index + 1} (sourceId=${hit.sourceId}, score=${hit.score})")
                            appendLine(hit.text)
                            appendLine()
                        }

                        appendLine("--- ВОПРОС ПОЛЬЗОВАТЕЛЯ ---")
                    } else {
                        appendLine("Для этого вопроса релевантных фрагментов документации в локальном индексе не найдено.")
                        appendLine("Ответь максимально полезно, опираясь на свои знания о Kotlin, Ktor и данном типе приложения.")
                        appendLine()
                        appendLine("--- ВОПРОС ПОЛЬЗОВАТЕЛЯ ---")
                    }

                    appendLine(request.question)
                }

                val withRagRequest = CompletionRequest(
                    modelId = request.modelId,
                    messages = listOf(
                        AIMessage(role = "user", content = ragUserContent)
                    ),
                    temperature = request.temperature,
                    maxTokens = request.maxTokens,
                    systemPrompt = baseSystemPrompt,
                    tools = null // Явно отключаем tools для чистого эксперимента RAG
                )

                val withRagResult = AppContainer.modelRegistry.complete(withRagRequest)
                logger.info("✅ Ответ с RAG получен (tokens in={}, out={})",
                    withRagResult.tokenUsage.inputTokens,
                    withRagResult.tokenUsage.outputTokens
                )

                val withRagDto = RagAnswerVariantDto(
                    answer = withRagResult.text,
                    modelId = withRagResult.modelId,
                    tokenUsage = TokenUsageDto(
                        inputTokens = withRagResult.tokenUsage.inputTokens,
                        outputTokens = withRagResult.tokenUsage.outputTokens,
                        totalTokens = withRagResult.tokenUsage.totalTokens
                    )
                )

                // 3. Формируем baseline-запрос (тот же вопрос, но БЕЗ RAG и без контекста)
                val baselineRequest = CompletionRequest(
                    modelId = request.modelId,
                    messages = listOf(
                        AIMessage(role = "user", content = request.question)
                    ),
                    temperature = request.temperature,
                    maxTokens = request.maxTokens,
                    systemPrompt = baseSystemPrompt,
                    tools = null // Без инструментов и без RAG
                )

                val baselineResult = AppContainer.modelRegistry.complete(baselineRequest)
                logger.info("✅ Baseline-ответ без RAG получен (tokens in={}, out={})",
                    baselineResult.tokenUsage.inputTokens,
                    baselineResult.tokenUsage.outputTokens
                )

                val withoutRagDto = RagAnswerVariantDto(
                    answer = baselineResult.text,
                    modelId = baselineResult.modelId,
                    tokenUsage = TokenUsageDto(
                        inputTokens = baselineResult.tokenUsage.inputTokens,
                        outputTokens = baselineResult.tokenUsage.outputTokens,
                        totalTokens = baselineResult.tokenUsage.totalTokens
                    )
                )

                val response = RagAskResponse(
                    question = request.question,
                    withRag = withRagDto,
                    withoutRag = withoutRagDto,
                    usedChunks = usedChunks
                )

                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                logger.error("❌ Ошибка при обработке /api/rag/ask: {}", e.message, e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to "error",
                        "message" to (e.message ?: "Неизвестная ошибка при RAG-запросе")
                    )
                )
            }
        }
    }
}
