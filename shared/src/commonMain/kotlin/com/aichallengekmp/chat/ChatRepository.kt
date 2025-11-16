package com.aichallengekmp.chat

import com.aichallengekmp.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Репозиторий для работы с Chat API
 */
interface ChatRepository {
    suspend fun getSessions(): Result<List<SessionListItem>>
    suspend fun getSessionDetail(sessionId: String): Result<SessionDetailResponse>
    suspend fun createSession(request: CreateSessionRequest): Result<SessionDetailResponse>
    suspend fun sendMessage(sessionId: String, message: String): Result<SessionDetailResponse>
    suspend fun updateSettings(sessionId: String, settings: SessionSettingsDto): Result<SessionSettingsDto>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun getAvailableModels(): Result<List<ModelInfoDto>>
}

/**
 * Реализация репозитория через Ktor HTTP Client
 */
class KtorChatRepository(
    private val client: HttpClient,
    private val baseUrl: String
) : ChatRepository {

    override suspend fun getSessions(): Result<List<SessionListItem>> = runCatching {
        client.get("$baseUrl/api/sessions") {
            accept(ContentType.Application.Json)
        }.body()
    }

    override suspend fun getSessionDetail(sessionId: String): Result<SessionDetailResponse> = runCatching {
        client.get("$baseUrl/api/sessions/$sessionId") {
            accept(ContentType.Application.Json)
        }.body()
    }

    override suspend fun createSession(request: CreateSessionRequest): Result<SessionDetailResponse> = runCatching {
        client.post("$baseUrl/api/sessions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun sendMessage(sessionId: String, message: String): Result<SessionDetailResponse> = runCatching {
        client.post("$baseUrl/api/sessions/$sessionId/messages") {
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequest(sessionId, message))
        }.body()
    }

    override suspend fun updateSettings(sessionId: String, settings: SessionSettingsDto): Result<SessionSettingsDto> = runCatching {
        client.put("$baseUrl/api/sessions/$sessionId/settings") {
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingsRequest(settings))
        }.body()
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        client.delete("$baseUrl/api/sessions/$sessionId") {
            accept(ContentType.Application.Json)
        }
    }

    override suspend fun getAvailableModels(): Result<List<ModelInfoDto>> = runCatching {
        client.get("$baseUrl/api/models") {
            accept(ContentType.Application.Json)
        }.body()
    }
}
