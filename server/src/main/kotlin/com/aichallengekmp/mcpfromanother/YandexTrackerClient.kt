package com.aichallengekmp.mcpfromanother

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Клиент для работы с API Яндекс.Трекер
 * Документация API: https://cloud.yandex.ru/docs/tracker/concepts/
 */
class YandexTrackerClient(
    // Использовать:
    private val token: String = System.getenv("YANDEX_TRACKER_TOKEN"),
    // Использовать:
    private val orgId: String = System.getenv("YANDEX_TRACKER_ORG_ID")
) {
    private val baseUrl = "https://api.tracker.yandex.net/v2"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })

        }
        install(SSE)
//        install(Logging) {
//            logger = Logger.DEFAULT
//            level = LogLevel.INFO
//        }
    }

    /**
     * Модель данных для задачи в Яндекс.Трекер
     */
    @Serializable
    data class Issue(
        val id: String? = null,
        val key: String,
        val summary: String,
        val description: String? = null,
        val status: IssueStatus? = null,
        val assignee: User? = null,
        val createdBy: User? = null,
        val updatedAt: String? = null,
        val createdAt: String? = null
    )

    @Serializable
    data class IssueStatus(
        val key: String,
        val display: String
    )

    @Serializable
    data class User(
        val id: String? = null,
        val display: String? = null
    )

    /**
     * Получить количество всех задач
     */
    suspend fun getIssuesCount(): Int {
        return try {
            val issues = getAllIssues()
            issues.size
        } catch (e: Exception) {
            throw Exception("Ошибка при получении количества задач: ${e.message}", e)
        }
    }

    /**
     * Получить все задачи
     */
    suspend fun getAllIssues(): List<Issue> {
        return try {
            val response = client.get("$baseUrl/issues") {
                headers {
                    append(HttpHeaders.Authorization, "OAuth $token")
                    append("X-Org-ID", orgId)
                    append("X-Cloud-Org-ID", orgId)
                }
            }
            response.body()
        } catch (e: Exception) {
            throw Exception("Ошибка при получении списка задач: ${e.message}", e)
        }
    }

    /**
     * Получить имена (summary) всех задач
     */
    suspend fun getAllIssueNames(): List<String> {
        return try {
            val issues = getAllIssues()
            issues.map { "${it.key}: ${it.summary}" }
        } catch (e: Exception) {
            throw Exception("Ошибка при получении имен задач: ${e.message}", e)
        }
    }

    /**
     * Получить информацию о конкретной задаче по ключу
     */
    suspend fun getIssueByKey(issueKey: String): Issue {
        return try {
            val response = client.get("$baseUrl/issues/$issueKey") {
                headers {
                    append(HttpHeaders.Authorization, "OAuth $token")
                    append("X-Org-ID", orgId)
                    append("X-Cloud-Org-ID", orgId)
                }
            }
            response.body()
        } catch (e: Exception) {
            throw Exception("Ошибка при получении задачи $issueKey: ${e.message}", e)
        }
    }

    /**
     * Закрыть HTTP клиент
     */
    fun close() {
        client.close()
    }
}
