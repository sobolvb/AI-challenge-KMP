package com.aichallengekmp.service

import com.aichallengekmp.ai.AIMessage
import com.aichallengekmp.ai.CompletionRequest
import com.aichallengekmp.ai.ModelRegistry
import com.aichallengekmp.rag.RagSearchService
import com.aichallengekmp.tools.GitToolsService
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Сервис для автоматического ревью кода в Pull Request
 */
class CodeReviewService(
    private val gitTools: GitToolsService,
    private val ragSearchService: RagSearchService,
    private val modelRegistry: ModelRegistry
) {

    private val logger = LoggerFactory.getLogger(CodeReviewService::class.java)

    /**
     * Анализировать Pull Request
     */
    suspend fun analyzePR(prNumber: String, repository: String?): CodeReviewResult {
        logger.info("🔍 Начало анализа PR #$prNumber")

        // 1. Получить информацию о PR через MCP
        val prInfo = gitTools.executeTool(
            "github_get_pr_info",
            mapOf("pr_number" to prNumber, "repository" to (repository ?: ""))
        )

        // 2. Получить diff PR через MCP
        val prDiff = gitTools.executeTool(
            "git_get_pr_diff",
            mapOf("pr_number" to prNumber, "repository" to (repository ?: ""))
        )

        // 3. Получить список измененных файлов через MCP
        val changedFiles = gitTools.executeTool(
            "git_get_changed_files",
            mapOf("pr_number" to prNumber, "repository" to (repository ?: ""))
        )

        logger.info("📝 PR Info получена")
        logger.info("📄 Diff получен (${prDiff.length} символов)")
        logger.info("📂 Список файлов получен")

        // 4. Поиск релевантного кода через RAG
        // Извлекаем имена файлов из changedFiles для поиска в RAG
        val fileNames = extractFileNames(changedFiles)
        val relevantCodeChunks = fileNames.take(3).flatMap { fileName ->
            ragSearchService.search("kotlin код файл $fileName", topK = 2)
        }

        logger.info("🔎 Найдено ${relevantCodeChunks.size} релевантных фрагментов кода в RAG")

        // 5. Поиск code style guidelines через RAG
        val codeStyleChunks = ragSearchService.search("code style guidelines kotlin", topK = 3)
        logger.info("📐 Найдено ${codeStyleChunks.size} фрагментов code style")

        // 6. Формирование system prompt для code review
        val systemPrompt = buildCodeReviewSystemPrompt()

        // 7. Формирование user prompt с контекстом
        val userPrompt = buildCodeReviewUserPrompt(
            prInfo = prInfo,
            prDiff = prDiff,
            changedFiles = changedFiles,
            relevantCodeChunks = relevantCodeChunks.map { it.text },
            codeStyleChunks = codeStyleChunks.map { it.text }
        )

        logger.info("📋 System prompt и user prompt сформированы")

        // 8. Вызов YandexGPT с низкой temperature для консистентности
        val request = CompletionRequest(
            modelId = "yandex-gpt",
            messages = listOf(
                AIMessage(role = "user", content = userPrompt)
            ),
            temperature = 0.3,  // Низкая temperature для консистентности
            maxTokens = 4000,
            systemPrompt = systemPrompt,
            tools = null  // Без инструментов - только контекст
        )

        val response = modelRegistry.complete(request)
        logger.info("✅ Ответ от YandexGPT получен (tokens in=${response.tokenUsage.inputTokens}, out=${response.tokenUsage.outputTokens})")

        // 9. Парсинг ответа в структурированный формат
        val reviewText = response.text
        val parsedReview = parseReviewResponse(reviewText)

        return CodeReviewResult(
            summary = parsedReview.summary,
            criticalIssues = parsedReview.criticalIssues,
            warnings = parsedReview.warnings,
            suggestions = parsedReview.suggestions,
            usedSources = extractUsedSources(relevantCodeChunks, codeStyleChunks),
            rawReview = reviewText
        )
    }

    /**
     * Извлечь имена файлов из строки с измененными файлами
     */
    private fun extractFileNames(changedFilesText: String): List<String> {
        return changedFilesText.lines()
            .filter { it.startsWith("- ") }
            .map { line ->
                // Извлекаем имя файла из строки вида "- path/to/File.kt [status]"
                line.substringAfter("- ")
                    .substringBefore(" [")
                    .trim()
            }
            .filter { it.isNotBlank() }
    }

    /**
     * Построить system prompt для code review
     */
    private fun buildCodeReviewSystemPrompt(): String {
        return """
Ты — опытный Kotlin code reviewer с глубокими знаниями в области:
- Kotlin языка и его идиом
- Ktor framework для серверной разработки
- Архитектурных паттернов и best practices
- Безопасности (OWASP Top 10)
- Performance и оптимизации

Твоя задача — провести детальное ревью кода в Pull Request.

При ревью проверяй:
1. **Баги и потенциальные проблемы**: null safety, race conditions, утечки ресурсов
2. **Безопасность**: SQL injection, XSS, command injection, утечки данных
3. **Архитектура**: правильное разделение ответственности, SOLID принципы
4. **Производительность**: неэффективные алгоритмы, блокирующие операции
5. **Code style**: соответствие guidelines проекта, читаемость, naming conventions
6. **Тестируемость**: насколько код легко покрыть тестами

Будь конструктивным и конкретным:
- Указывай файл и строку для каждой проблемы (если возможно)
- Объясняй "почему" проблема важна
- Предлагай конкретные решения

Форматируй ответ структурированно:
```
## Общая оценка
[Краткая общая оценка PR]

## Критические проблемы
[Проблемы, которые блокируют merge]
- **Файл:строка**: описание проблемы и решение

## Предупреждения
[Проблемы, которые желательно исправить]
- **Файл:строка**: описание проблемы

## Рекомендации
[Необязательные улучшения]
- описание рекомендации
```

Используй только информацию из предоставленного контекста (diff, code style, примеры кода).
Не выдумывай проблемы, которых нет в коде.
        """.trimIndent()
    }

    /**
     * Построить user prompt с контекстом для ревью
     */
    private fun buildCodeReviewUserPrompt(
        prInfo: String,
        prDiff: String,
        changedFiles: String,
        relevantCodeChunks: List<String>,
        codeStyleChunks: List<String>
    ): String {
        return buildString {
            appendLine("# Информация о Pull Request")
            appendLine(prInfo)
            appendLine()

            if (codeStyleChunks.isNotEmpty()) {
                appendLine("# Code Style Guidelines")
                codeStyleChunks.forEachIndexed { index, chunk ->
                    appendLine("## Code Style Fragment ${index + 1}")
                    appendLine(chunk)
                    appendLine()
                }
            }

            if (relevantCodeChunks.isNotEmpty()) {
                appendLine("# Примеры существующего кода проекта")
                appendLine("Для справки - так написан код в этом проекте:")
                relevantCodeChunks.forEachIndexed { index, chunk ->
                    appendLine("## Код Fragment ${index + 1}")
                    appendLine("```kotlin")
                    appendLine(chunk)
                    appendLine("```")
                    appendLine()
                }
            }

            appendLine("# Измененные файлы")
            appendLine(changedFiles)
            appendLine()

            appendLine("# Diff изменений")
            appendLine("```diff")
            // Ограничиваем размер diff если он слишком большой
            val limitedDiff = if (prDiff.length > 10000) {
                prDiff.take(10000) + "\n... (diff обрезан, слишком большой)"
            } else {
                prDiff
            }
            appendLine(limitedDiff)
            appendLine("```")
            appendLine()

            appendLine("Проведи детальное ревью этого Pull Request.")
        }
    }

    /**
     * Парсинг ответа модели в структурированный формат
     */
    private fun parseReviewResponse(reviewText: String): ParsedReview {
        val summary = extractSection(reviewText, "Общая оценка", "Критические проблемы")
            ?: "Общая оценка не предоставлена"

        val criticalIssues = extractIssues(reviewText, "Критические проблемы", "Предупреждения")
        val warnings = extractIssues(reviewText, "Предупреждения", "Рекомендации")
        val suggestions = extractIssues(reviewText, "Рекомендации", null)

        return ParsedReview(
            summary = summary,
            criticalIssues = criticalIssues,
            warnings = warnings,
            suggestions = suggestions
        )
    }

    /**
     * Извлечь секцию из текста ревью
     */
    private fun extractSection(text: String, sectionName: String, nextSectionName: String?): String? {
        val sectionPattern = "##\\s*$sectionName".toRegex(RegexOption.IGNORE_CASE)
        val startIndex = sectionPattern.find(text)?.range?.last ?: return null

        val endIndex = if (nextSectionName != null) {
            val nextPattern = "##\\s*$nextSectionName".toRegex(RegexOption.IGNORE_CASE)
            nextPattern.find(text, startIndex)?.range?.first ?: text.length
        } else {
            text.length
        }

        return text.substring(startIndex, endIndex).trim()
    }

    /**
     * Извлечь список проблем из секции
     */
    private fun extractIssues(text: String, sectionName: String, nextSectionName: String?): List<ReviewIssue> {
        val sectionText = extractSection(text, sectionName, nextSectionName) ?: return emptyList()

        return sectionText.lines()
            .filter { it.trim().startsWith("-") || it.trim().startsWith("*") }
            .map { line ->
                val cleaned = line.trim().removePrefix("-").removePrefix("*").trim()

                // Пытаемся извлечь файл и строку если есть
                val fileLinePattern = """^\*\*([^:]+):(\d+)\*\*:?\s*(.+)""".toRegex()
                val match = fileLinePattern.find(cleaned)

                if (match != null) {
                    ReviewIssue(
                        file = match.groupValues[1].trim(),
                        line = match.groupValues[2].toIntOrNull(),
                        description = match.groupValues[3].trim()
                    )
                } else {
                    ReviewIssue(
                        file = null,
                        line = null,
                        description = cleaned
                    )
                }
            }
            .filter { it.description.isNotBlank() }
    }

    /**
     * Извлечь список использованных источников
     */
    private fun extractUsedSources(
        codeChunks: List<com.aichallengekmp.rag.RagHit>,
        styleChunks: List<com.aichallengekmp.rag.RagHit>
    ): List<String> {
        return (codeChunks + styleChunks)
            .map { it.sourceId }
            .distinct()
    }
}

/**
 * Результат анализа PR
 */
@Serializable
data class CodeReviewResult(
    val summary: String,
    val criticalIssues: List<ReviewIssue>,
    val warnings: List<ReviewIssue>,
    val suggestions: List<ReviewIssue>,
    val usedSources: List<String>,
    val rawReview: String
)

/**
 * Проблема или рекомендация из ревью
 */
@Serializable
data class ReviewIssue(
    val file: String?,
    val line: Int?,
    val description: String
)

/**
 * Распарсенное ревью
 */
private data class ParsedReview(
    val summary: String,
    val criticalIssues: List<ReviewIssue>,
    val warnings: List<ReviewIssue>,
    val suggestions: List<ReviewIssue>
)
