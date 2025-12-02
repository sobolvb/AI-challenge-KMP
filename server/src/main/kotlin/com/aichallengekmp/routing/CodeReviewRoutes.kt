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
 * Роуты для автоматического ревью кода
 */
fun Route.codeReviewRoutes() {
    val logger = LoggerFactory.getLogger("CodeReviewRoutes")

    route("/code-review") {
        /**
         * POST /api/code-review/analyze
         *
         * Анализирует Pull Request:
         * - Получает PR info и diff через MCP
         * - Ищет релевантный код через RAG
         * - Ищет code style guidelines через RAG
         * - Вызывает YandexGPT для ревью
         * - Возвращает структурированный результат
         */
        post("/analyze") {
            logger.info("🔍 POST /api/code-review/analyze — запуск анализа PR")

            @Serializable
            data class AnalyzeRequest(
                val prNumber: String,
                val repository: String? = null
            )

            val request = try {
                call.receive<AnalyzeRequest>()
            } catch (e: Exception) {
                logger.warn("⚠️ Некорректный запрос к /api/code-review/analyze: {}", e.message)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "status" to "error",
                        "message" to (e.message ?: "Некорректный формат запроса")
                    )
                )
                return@post
            }

            logger.info("📋 Анализ PR #{} в репозитории {}", request.prNumber, request.repository ?: "текущий")

            try {
                val result = AppContainer.codeReviewService.analyzePR(
                    prNumber = request.prNumber,
                    repository = request.repository
                )

                logger.info("✅ Анализ PR #{} завершен", request.prNumber)
                logger.info("   Критических проблем: {}", result.criticalIssues.size)
                logger.info("   Предупреждений: {}", result.warnings.size)
                logger.info("   Рекомендаций: {}", result.suggestions.size)

                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                logger.error("❌ Ошибка при анализе PR #{}: {}", request.prNumber, e.message, e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to "error",
                        "message" to (e.message ?: "Неизвестная ошибка при анализе PR")
                    )
                )
            }
        }
    }
}
