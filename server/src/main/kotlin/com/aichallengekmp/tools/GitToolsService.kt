package com.aichallengekmp.tools

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Сервис для работы с git через инструменты
 */
class GitToolsService {
    private val logger = LoggerFactory.getLogger(GitToolsService::class.java)

    /**
     * Получить список всех доступных git-инструментов
     */
    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                name = "get_git_branch",
                description = "Получить название текущей git ветки проекта",
                parameters = emptyMap()
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
}
