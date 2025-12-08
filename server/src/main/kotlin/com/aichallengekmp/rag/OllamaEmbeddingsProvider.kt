package com.aichallengekmp.rag

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Провайдер эмбеддингов через локальный Ollama-сервер
 */
class OllamaEmbeddingsProvider(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "nomic-embed-text"
) : EmbeddingsProvider {

    private val logger = LoggerFactory.getLogger(OllamaEmbeddingsProvider::class.java)

    @Serializable
    private data class EmbeddingsRequest(
        val model: String,
        val prompt: String
    )

    @Serializable
    private data class EmbeddingsResponse(
        val embedding: List<Float>? = null,
        val embeddings: List<List<Float>>? = null
    )

    override suspend fun embed(text: String): FloatArray {
        val payload = EmbeddingsRequest(model = model, prompt = text)

        logger.debug("📡 Запрос эмбеддинга в Ollama: model={}, baseUrl={}", model, baseUrl)

        val url = "$baseUrl/api/embeddings"

        return try {
            val httpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            val raw = httpResponse.bodyAsText()
            logger.debug("📡 RAW ответ от Ollama: {}", raw)

            val json = Json { ignoreUnknownKeys = true }

            // Пытаемся сначала декодировать в EmbeddingsResponse (embedding или embeddings)
            val decoded = try {
                json.decodeFromString(EmbeddingsResponse.serializer(), raw)
            } catch (_: Exception) {
                null
            }

            val embeddingList: List<Float>? = when {
                decoded?.embedding != null -> decoded.embedding
                !decoded?.embeddings.isNullOrEmpty() -> decoded!!.embeddings!!.firstOrNull()
                else -> {
                    // Фоллбек: парсим JSON вручную и ищем первое подходящее числовое массивное поле
                    extractEmbeddingManually(json.parseToJsonElement(raw))
                }
            }

            if (embeddingList == null) {
                throw IllegalStateException("Не удалось найти поле embedding в ответе Ollama: $raw")
            }

            val vector = embeddingList.toFloatArray()
            logger.debug("✅ Получен вектор размерности {} от Ollama", vector.size)
            vector
        } catch (e: Exception) {
            logger.error("❌ Ошибка при получении эмбеддингов из Ollama: {}", e.message, e)
            throw RuntimeException("Ошибка при получении эмбеддингов из Ollama: ${e.message}", e)
        }
    }

    /**
     * Фоллбек-парсер: пытается найти массив чисел (эмбеддинг) в произвольном JSON-ответе
     */
    private fun extractEmbeddingManually(root: JsonElement): List<Float>? {
        if (root is JsonObject) {
            // Приоритетно ищем ключи embedding / embeddings
            root["embedding"]?.let { elem ->
                (elem as? JsonArray)?.let { arr ->
                    return arr.mapNotNull { it.jsonPrimitive.content.toFloatOrNull() }
                }
            }

            root["embeddings"]?.let { elem ->
                val arr = elem as? JsonArray
                val first = arr?.firstOrNull() as? JsonArray
                if (first != null) {
                    return first.mapNotNull { it.jsonPrimitive.content.toFloatOrNull() }
                }
            }

            // Если структура иная — ищем первый массив чисел в объекте
            root.values.forEach { value ->
                val candidate = extractEmbeddingManually(value)
                if (candidate != null) return candidate
            }
        } else if (root is JsonArray) {
            // Если это массив чисел — считаем его эмбеддингом
            val floats = root.mapNotNull { it.jsonPrimitive.content.toFloatOrNull() }
            if (floats.isNotEmpty()) return floats

            // Иначе ищем внутри
            root.forEach { child ->
                val candidate = extractEmbeddingManually(child)
                if (candidate != null) return candidate
            }
        }

        return null
    }
}