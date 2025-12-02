package com.aichallengekmp.tools

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Сервис для работы с git через инструменты
 */
class GitToolsService(
    private val httpClient: HttpClient? = null,
    private val workingDirectory: String = System.getProperty("user.dir")
) {
    private val logger = LoggerFactory.getLogger(GitToolsService::class.java)
    private val githubToken = System.getenv("GITHUB_TOKEN")

    /**
     * Получить список всех доступных git-инструментов
     */
    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "get_git_branch",
                description = "Получить название текущей git ветки проекта",
                parameters = emptyMap()
            ),
            ToolDefinition(
                name = "git_get_pr_diff",
                description = "Получить diff изменений Pull Request по номеру PR",
                parameters = mapOf(
                    "pr_number" to "string",
                    "repository" to "string (опционально, формат: owner/repo, по умолчанию из текущего репозитория)"
                )
            ),
            ToolDefinition(
                name = "git_get_changed_files",
                description = "Получить список файлов, измененных в Pull Request",
                parameters = mapOf(
                    "pr_number" to "string",
                    "repository" to "string (опционально, формат: owner/repo)"
                )
            ),
            ToolDefinition(
                name = "git_get_file_content",
                description = "Получить содержимое конкретного файла из репозитория",
                parameters = mapOf(
                    "file_path" to "string",
                    "ref" to "string (опционально, ветка или коммит, по умолчанию HEAD)"
                )
            ),
            ToolDefinition(
                name = "github_get_pr_info",
                description = "Получить метаданные Pull Request (заголовок, описание, автор, статус)",
                parameters = mapOf(
                    "pr_number" to "string",
                    "repository" to "string (опционально, формат: owner/repo)"
                )
            )
        )
    }

    /**
     * Выполнить git-инструмент
     */
    suspend fun executeTool(toolName: String, arguments: Map<String, Any>): String {
        logger.info("🔧 Выполнение git-инструмента: $toolName")

        return try {
            when (toolName) {
                "get_git_branch" -> getCurrentBranch()
                "git_get_pr_diff" -> {
                    val prNumber = arguments["pr_number"]?.toString()
                        ?: return "Ошибка: не указан параметр pr_number"
                    val repository = arguments["repository"]?.toString()
                    getPRDiff(prNumber, repository)
                }
                "git_get_changed_files" -> {
                    val prNumber = arguments["pr_number"]?.toString()
                        ?: return "Ошибка: не указан параметр pr_number"
                    val repository = arguments["repository"]?.toString()
                    getChangedFiles(prNumber, repository)
                }
                "git_get_file_content" -> {
                    val filePath = arguments["file_path"]?.toString()
                        ?: return "Ошибка: не указан параметр file_path"
                    val ref = arguments["ref"]?.toString()
                    getFileContent(filePath, ref)
                }
                "github_get_pr_info" -> {
                    val prNumber = arguments["pr_number"]?.toString()
                        ?: return "Ошибка: не указан параметр pr_number"
                    val repository = arguments["repository"]?.toString()
                    getPRInfo(prNumber, repository)
                }
                else -> {
                    logger.warn("⚠️ Неизвестный инструмент: $toolName")
                    "Ошибка: инструмент '$toolName' не найден"
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка выполнения инструмента $toolName: ${e.message}", e)
            "Ошибка выполнения инструмента: ${e.message}"
        }
    }

    /**
     * Получить текущую git ветку
     */
    private fun getCurrentBranch(): String {
        return try {
            val processBuilder = ProcessBuilder("git", "branch", "--show-current")
            processBuilder.directory(File(workingDirectory))
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            val output = reader.readText().trim()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotBlank()) {
                logger.info("✅ Текущая ветка: $output")
                "Текущая git ветка: $output"
            } else {
                logger.warn("⚠️ Не удалось определить ветку. Exit code: $exitCode, output: $output")
                "Не удалось определить текущую ветку. Возможно, проект не является git репозиторием."
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при выполнении git команды: ${e.message}", e)
            "Ошибка при выполнении git команды: ${e.message}"
        }
    }

    /**
     * Получить информацию о репозитории из текущей директории
     */
    private fun getRepositoryInfo(): Pair<String, String>? {
        return try {
            val processBuilder = ProcessBuilder("git", "remote", "get-url", "origin")
            processBuilder.directory(File(workingDirectory))
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()

            // Парсим URL вида: git@github.com:owner/repo.git или https://github.com/owner/repo.git
            val regex = """(?:github\.com[:/])([^/]+)/(.+?)(?:\.git)?$""".toRegex()
            val match = regex.find(output)

            if (match != null) {
                val owner = match.groupValues[1]
                val repo = match.groupValues[2]
                logger.info("✅ Определен репозиторий: $owner/$repo")
                owner to repo
            } else {
                logger.warn("⚠️ Не удалось распарсить URL репозитория: $output")
                null
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении информации о репозитории: ${e.message}", e)
            null
        }
    }

    /**
     * Получить diff PR через GitHub API
     */
    private suspend fun getPRDiff(prNumber: String, repository: String?): String {
        val repo = repository ?: getRepositoryInfo()?.let { "${it.first}/${it.second}" }
            ?: return "Ошибка: не удалось определить репозиторий. Укажите параметр repository"

        if (httpClient == null) {
            return "Ошибка: HTTP клиент не инициализирован"
        }

        if (githubToken.isNullOrBlank()) {
            return "Ошибка: GITHUB_TOKEN не установлен в переменных окружения"
        }

        return try {
            logger.info("📥 Получение diff для PR #$prNumber в репозитории $repo")

            val response = httpClient.get("https://api.github.com/repos/$repo/pulls/$prNumber") {
                header("Authorization", "token $githubToken")
                header("Accept", "application/vnd.github.v3.diff")
            }

            if (response.status == HttpStatusCode.OK) {
                val diff = response.bodyAsText()
                logger.info("✅ Получен diff для PR #$prNumber (${diff.length} символов)")
                diff
            } else {
                val error = "Ошибка получения diff: ${response.status}, ${response.bodyAsText()}"
                logger.error("❌ $error")
                error
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении diff PR: ${e.message}", e)
            "Ошибка при получении diff PR: ${e.message}"
        }
    }

    /**
     * Получить список измененных файлов в PR
     */
    private suspend fun getChangedFiles(prNumber: String, repository: String?): String {
        val repo = repository ?: getRepositoryInfo()?.let { "${it.first}/${it.second}" }
            ?: return "Ошибка: не удалось определить репозиторий. Укажите параметр repository"

        if (httpClient == null) {
            return "Ошибка: HTTP клиент не инициализирован"
        }

        if (githubToken.isNullOrBlank()) {
            return "Ошибка: GITHUB_TOKEN не установлен в переменных окружения"
        }

        return try {
            logger.info("📂 Получение списка файлов для PR #$prNumber в репозитории $repo")

            val response = httpClient.get("https://api.github.com/repos/$repo/pulls/$prNumber/files") {
                header("Authorization", "token $githubToken")
                header("Accept", "application/vnd.github.v3+json")
            }

            if (response.status == HttpStatusCode.OK) {
                val jsonResponse = Json.parseToJsonElement(response.bodyAsText())
                val files = jsonResponse.jsonArray

                val fileList = files.joinToString("\n") { fileElement ->
                    val file = fileElement.jsonObject
                    val filename = file["filename"]?.jsonPrimitive?.content ?: "unknown"
                    val status = file["status"]?.jsonPrimitive?.content ?: "unknown"
                    val additions = file["additions"]?.jsonPrimitive?.int ?: 0
                    val deletions = file["deletions"]?.jsonPrimitive?.int ?: 0
                    val changes = file["changes"]?.jsonPrimitive?.int ?: 0

                    "- $filename [$status] (+$additions -$deletions, всего изменений: $changes)"
                }

                logger.info("✅ Получен список из ${files.size} файлов для PR #$prNumber")
                "Измененные файлы в PR #$prNumber:\n$fileList"
            } else {
                val error = "Ошибка получения списка файлов: ${response.status}, ${response.bodyAsText()}"
                logger.error("❌ $error")
                error
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении списка файлов PR: ${e.message}", e)
            "Ошибка при получении списка файлов PR: ${e.message}"
        }
    }

    /**
     * Получить содержимое файла из репозитория
     */
    private fun getFileContent(filePath: String, ref: String?): String {
        return try {
            val reference = ref ?: "HEAD"
            logger.info("📄 Получение содержимого файла: $filePath (ref: $reference)")

            val processBuilder = ProcessBuilder("git", "show", "$reference:$filePath")
            processBuilder.directory(File(workingDirectory))
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val content = reader.readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                logger.info("✅ Получено содержимое файла $filePath (${content.length} символов)")
                content
            } else {
                val error = "Не удалось получить содержимое файла $filePath. Exit code: $exitCode"
                logger.error("❌ $error")
                error
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении содержимого файла: ${e.message}", e)
            "Ошибка при получении содержимого файла: ${e.message}"
        }
    }

    /**
     * Получить метаданные PR через GitHub API
     */
    private suspend fun getPRInfo(prNumber: String, repository: String?): String {
        val repo = repository ?: getRepositoryInfo()?.let { "${it.first}/${it.second}" }
            ?: return "Ошибка: не удалось определить репозиторий. Укажите параметр repository"

        if (httpClient == null) {
            return "Ошибка: HTTP клиент не инициализирован"
        }

        if (githubToken.isNullOrBlank()) {
            return "Ошибка: GITHUB_TOKEN не установлен в переменных окружения"
        }

        return try {
            logger.info("ℹ️ Получение информации о PR #$prNumber в репозитории $repo")

            val response = httpClient.get("https://api.github.com/repos/$repo/pulls/$prNumber") {
                header("Authorization", "token $githubToken")
                header("Accept", "application/vnd.github.v3+json")
            }

            if (response.status == HttpStatusCode.OK) {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject

                val title = json["title"]?.jsonPrimitive?.content ?: "N/A"
                val body = json["body"]?.jsonPrimitive?.content ?: "Нет описания"
                val state = json["state"]?.jsonPrimitive?.content ?: "N/A"
                val author = json["user"]?.jsonObject?.get("login")?.jsonPrimitive?.content ?: "N/A"
                val baseBranch = json["base"]?.jsonObject?.get("ref")?.jsonPrimitive?.content ?: "N/A"
                val headBranch = json["head"]?.jsonObject?.get("ref")?.jsonPrimitive?.content ?: "N/A"
                val createdAt = json["created_at"]?.jsonPrimitive?.content ?: "N/A"
                val updatedAt = json["updated_at"]?.jsonPrimitive?.content ?: "N/A"

                val info = buildString {
                    appendLine("Pull Request #$prNumber")
                    appendLine("Заголовок: $title")
                    appendLine("Автор: $author")
                    appendLine("Статус: $state")
                    appendLine("Ветки: $headBranch -> $baseBranch")
                    appendLine("Создан: $createdAt")
                    appendLine("Обновлен: $updatedAt")
                    appendLine("Описание:")
                    appendLine(body)
                }

                logger.info("✅ Получена информация о PR #$prNumber")
                info
            } else {
                val error = "Ошибка получения информации о PR: ${response.status}, ${response.bodyAsText()}"
                logger.error("❌ $error")
                error
            }
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении информации о PR: ${e.message}", e)
            "Ошибка при получении информации о PR: ${e.message}"
        }
    }

    /**
     * Выполнить git команду и получить результат
     */
    private fun executeGitCommand(vararg command: String): String {
        return try {
            val processBuilder = ProcessBuilder(*command)
            processBuilder.directory(File(workingDirectory))
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()

            output
        } catch (e: Exception) {
            logger.error("❌ Ошибка выполнения git команды: ${e.message}", e)
            ""
        }
    }
}
