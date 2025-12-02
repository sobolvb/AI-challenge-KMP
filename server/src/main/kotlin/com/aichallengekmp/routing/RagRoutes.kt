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
import kotlinx.serialization.Serializable
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
         * POST /api/rag/index/docs
         *
         * Индексирует все .md файлы из папки project/docs.
         * Путь к папке задаётся относительно модуля server:
         *  - основной вариант: ../project/docs (из каталога server/ в корень монорепо)
         */
        post("/index/docs") {
            logger.info("📚 POST /api/rag/index/docs — индексация папки project/docs")

            val candidatePaths = listOf(
                "../project/docs",   // из server/ в корень репозитория
                "project/docs"       // на случай запуска из корня проекта
            )

            val docsDir = candidatePaths
                .map { File(it) }
                .firstOrNull { it.exists() && it.isDirectory }

            if (docsDir == null) {
                logger.error("❌ Папка project/docs не найдена ни по одному из путей: {}", candidatePaths)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to "error",
                        "message" to "Папка project/docs не найдена. Ожидались пути: ${candidatePaths.joinToString()}"
                    )
                )
                return@post
            }

            try {
                // Рекурсивно собираем все .md файлы
                val mdFiles = docsDir.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() == "md" }
                    .toList()

                @Serializable
                data class IndexedDocResult(val sourceId: String, val chunks: Int)

                @Serializable
                data class IndexDocsResponse(val status: String, val indexed: List<IndexedDocResult>, val message: String? = null)

                if (mdFiles.isEmpty()) {
                    logger.warn("⚠️ В папке project/docs не найдено .md файлов")
                    call.respond(
                        HttpStatusCode.OK,
                        IndexDocsResponse(
                            status = "ok",
                            indexed = emptyList(),
                            message = "В папке project/docs не найдено .md файлов"
                        )
                    )
                    return@post
                }

                logger.info("📂 Найдено {} .md файлов для индексации", mdFiles.size)

                val indexedResults = mdFiles.map { file ->
                    val relativePath = file.relativeTo(docsDir.parentFile).path
                    val sourceId = "docs/${file.nameWithoutExtension}"

                    logger.info("  📄 Индексируем: $relativePath -> $sourceId")

                    val text = file.readText()
                    AppContainer.ragIndexService.indexDocument(sourceId, text)

                    // Подсчёт количества чанков этого документа в индексе
                    val totalChunks = AppContainer.ragChunkDao
                        .getAllChunks()
                        .count { it.sourceId == sourceId }

                    IndexedDocResult(
                        sourceId = sourceId,
                        chunks = totalChunks
                    )
                }

                logger.info("✅ Индексация завершена. Проиндексировано файлов: {}", indexedResults.size)

                call.respond(
                    HttpStatusCode.OK,
                    IndexDocsResponse(
                        status = "ok",
                        indexed = indexedResults
                    )
                )
            } catch (e: Exception) {
                logger.error("❌ Ошибка при индексации project/docs: {}", e.message, e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "status" to "error",
                        "message" to (e.message ?: "Неизвестная ошибка при индексации project/docs")
                    )
                )
            }
        }

        /**
         * POST /api/rag/index/code
         *
         * Индексирует .kt файлы из server/src/main/kotlin и shared/src/commonMain/kotlin.
         * Исключает build/, .gradle/, test файлы.
         * Разбивает код по ~100-150 строк.
         */
        post("/index/code") {
            logger.info("💻 POST /api/rag/index/code — индексация исходного кода проекта")

            @Serializable
            data class IndexedCodeResult(val sourceId: String, val chunks: Int)

            @Serializable
            data class IndexCodeResponse(val status: String, val indexed: List<IndexedCodeResult>, val message: String? = null)

            try {
                val candidatePaths = listOf(
                    listOf("../server/src/main/kotlin", "../shared/src/commonMain/kotlin"),  // из server/
                    listOf("server/src/main/kotlin", "shared/src/commonMain/kotlin")          // из корня
                )

                val sourceDirs = candidatePaths
                    .firstNotNullOfOrNull { paths ->
                        val dirs = paths.map { File(it) }.filter { it.exists() && it.isDirectory }
                        if (dirs.isNotEmpty()) dirs else null
                    }

                if (sourceDirs == null || sourceDirs.isEmpty()) {
                    logger.error("❌ Директории с исходным кодом не найдены")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        IndexCodeResponse(
                            status = "error",
                            indexed = emptyList(),
                            message = "Директории с исходным кодом не найдены"
                        )
                    )
                    return@post
                }

                logger.info("📂 Найдены директории для индексации: {}", sourceDirs.map { it.absolutePath })

                // Собираем все .kt файлы, исключая ненужные
                val ktFiles = sourceDirs.flatMap { dir ->
                    dir.walkTopDown()
                        .filter { it.isFile }
                        .filter { it.extension.lowercase() == "kt" }
                        .filter { file ->
                            val path = file.absolutePath
                            // Исключаем build/, .gradle/, test файлы
                            !path.contains("/build/") &&
                                    !path.contains("/.gradle/") &&
                                    !path.contains("/test/", ignoreCase = true) &&
                                    !path.contains("Test.kt", ignoreCase = true)
                        }
                }.toList()

                if (ktFiles.isEmpty()) {
                    logger.warn("⚠️ Не найдено .kt файлов для индексации")
                    call.respond(
                        HttpStatusCode.OK,
                        IndexCodeResponse(
                            status = "ok",
                            indexed = emptyList(),
                            message = "Не найдено .kt файлов для индексации"
                        )
                    )
                    return@post
                }

                logger.info("📄 Найдено {} .kt файлов для индексации", ktFiles.size)

                val indexedResults = ktFiles.mapIndexed { index, file ->
                    // Вычисляем относительный путь для sourceId
                    val relativePath = sourceDirs
                        .firstOrNull { dir -> file.absolutePath.startsWith(dir.absolutePath) }
                        ?.let { dir -> file.relativeTo(dir.parentFile.parentFile.parentFile).path }
                        ?: file.name

                    val sourceId = "code/$relativePath"

                    logger.info("  📄 Индексируем [{}/{}]: $relativePath", index + 1, ktFiles.size)

                    try {
                        val code = file.readText()
                        AppContainer.ragIndexService.indexCodeFile(sourceId, code)

                        // Подсчёт количества чанков этого файла в индексе
                        val totalChunks = AppContainer.ragChunkDao
                            .getAllChunks()
                            .count { it.sourceId == sourceId }

                        // Небольшая задержка между файлами, чтобы не перегружать Ollama
                        kotlinx.coroutines.delay(100)

                        IndexedCodeResult(
                            sourceId = sourceId,
                            chunks = totalChunks
                        )
                    } catch (e: Exception) {
                        logger.error("❌ Ошибка при индексации файла $relativePath: ${e.message}")
                        // Пропускаем файл с ошибкой
                        IndexedCodeResult(
                            sourceId = sourceId,
                            chunks = 0
                        )
                    }
                }

                logger.info("✅ Индексация кода завершена. Проиндексировано файлов: {}", indexedResults.size)

                call.respond(
                    HttpStatusCode.OK,
                    IndexCodeResponse(
                        status = "ok",
                        indexed = indexedResults
                    )
                )
            } catch (e: Exception) {
                logger.error("❌ Ошибка при индексации кода: {}", e.message, e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    IndexCodeResponse(
                        status = "error",
                        indexed = emptyList(),
                        message = e.message ?: "Неизвестная ошибка при индексации кода"
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

                // 1.1. Второй этап: фильтрация по порогу похожести
                val effectiveThreshold = request.similarityThreshold ?: 0.3
                val filteredHits = hits.filter { it.score >= effectiveThreshold }
                logger.info(
                    "🔎 Фильтрация результатов RAG по порогу {}: до={}, после={}",
                    String.format("%.3f", effectiveThreshold),
                    hits.size,
                    filteredHits.size
                )

                val usedChunksFiltered = filteredHits.map { hit ->
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

                // 2. Формируем запрос с RAG-контекстом (явно включаем найденные чанки, без фильтра)
                val ragUserContent = buildString {
                    if (hits.isNotEmpty()) {
                        appendLine("Ты работаешь в режиме RAG поверх внутренней документации проекта.")
                        appendLine("При ответе ОБЯЗАТЕЛЬНО:")
                        appendLine("  1) опирайся ТОЛЬКО на фрагменты ниже (не придумывай факты вне текста);")
                        appendLine("  2) внутри ответа ссылайся на источники в формате [sourceId:chunkIndex];")
                        appendLine("  3) в конце ответа добавь раздел 'Источники' со списком использованных [sourceId:chunkIndex] и кратким описанием.")
                        appendLine("Если во фрагментах нет ответа, честно скажи, что документация не содержит нужной информации, и не выдумывай.")
                        appendLine()
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
                logger.info("✅ Ответ с RAG (без фильтра) получен (tokens in={}, out={})",
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

                // 2.1. Запрос с фильтрованным RAG-контекстом (threshold)
                val withRagFilteredDto: RagAnswerVariantDto? = if (filteredHits.isNotEmpty()) {
                    val ragUserContentFiltered = buildString {
                        appendLine("Ты работаешь в режиме RAG поверх внутренней документации проекта.")
                        appendLine("Фрагменты ниже уже отфильтрованы по порогу релевантности.")
                        appendLine("При ответе ОБЯЗАТЕЛЬНО:")
                        appendLine("  1) опирайся ТОЛЬКО на эти фрагменты (не придумывай факты вне текста);")
                        appendLine("  2) внутри ответа ссылайся на источники в формате [sourceId:chunkIndex];")
                        appendLine("  3) в конце ответа добавь раздел 'Источники' со списком использованных [sourceId:chunkIndex] и кратким описанием.")
                        appendLine("Если во фрагментах нет ответа, честно скажи, что документация не содержит нужной информации, и не выдумывай.")
                        appendLine()
                        appendLine("Ниже приведены отфильтрованные по порогу похожести фрагменты документации.")
                        appendLine("Используй ИМЕННО ИХ при ответе.")
                        appendLine("Порог косинусного сходства: " + String.format("%.3f", effectiveThreshold))
                        appendLine()

                        filteredHits.forEachIndexed { index, hit ->
                            appendLine("### Фрагмент ${index + 1} (sourceId=${hit.sourceId}, score=${hit.score})")
                            appendLine(hit.text)
                            appendLine()
                        }

                        appendLine("--- ВОПРОС ПОЛЬЗОВАТЕЛЯ ---")
                        appendLine(request.question)
                    }

                    val withRagFilteredRequest = CompletionRequest(
                        modelId = request.modelId,
                        messages = listOf(
                            AIMessage(role = "user", content = ragUserContentFiltered)
                        ),
                        temperature = request.temperature,
                        maxTokens = request.maxTokens,
                        systemPrompt = baseSystemPrompt,
                        tools = null
                    )

                    val withRagFilteredResult = AppContainer.modelRegistry.complete(withRagFilteredRequest)
                    logger.info(
                        "✅ Ответ с RAG (с фильтром, threshold={}) получен (tokens in={}, out={})",
                        String.format("%.3f", effectiveThreshold),
                        withRagFilteredResult.tokenUsage.inputTokens,
                        withRagFilteredResult.tokenUsage.outputTokens
                    )

                    RagAnswerVariantDto(
                        answer = withRagFilteredResult.text,
                        modelId = withRagFilteredResult.modelId,
                        tokenUsage = TokenUsageDto(
                            inputTokens = withRagFilteredResult.tokenUsage.inputTokens,
                            outputTokens = withRagFilteredResult.tokenUsage.outputTokens,
                            totalTokens = withRagFilteredResult.tokenUsage.totalTokens
                        )
                    )
                } else {
                    logger.info("ℹ️ После фильтрации по порогу {} не осталось фрагментов — пропускаем вариант withRagFiltered",
                        String.format("%.3f", effectiveThreshold)
                    )
                    null
                }

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
                    withRagFiltered = withRagFilteredDto,
                    withoutRag = withoutRagDto,
                    usedChunks = usedChunks,
                    usedChunksFiltered = usedChunksFiltered.takeIf { it.isNotEmpty() },
                    similarityThreshold = effectiveThreshold
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
