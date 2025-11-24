package com.aichallengekmp.rag

import com.aichallengekmp.database.dao.RagChunkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Результат поиска по RAG индексу
 */
data class RagHit(
    val sourceId: String,
    val chunkIndex: Int,
    val text: String,
    val score: Double
)

/**
 * Сервис поиска по документам с использованием векторного индекса
 */
class RagSearchService(
    private val ragChunkDao: RagChunkDao,
    private val embeddingsProvider: EmbeddingsProvider
) {

    private val logger = LoggerFactory.getLogger(RagSearchService::class.java)

    suspend fun search(query: String, topK: Int = 5): List<RagHit> = withContext(Dispatchers.Default) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            logger.warn("⚠️ Пустой запрос к RAG-поиску")
            return@withContext emptyList()
        }

        logger.info("🔎 RAG-поиск. Запрос: '{}' (topK={})", normalizedQuery, topK)

        val queryEmbedding = embeddingsProvider.embed(normalizedQuery)
        val queryNorm = l2Norm(queryEmbedding)
        if (queryNorm == 0f) {
            logger.warn("⚠️ Нулевой вектор запроса, результаты будут пустыми")
            return@withContext emptyList()
        }

        val chunks = ragChunkDao.getAllChunks()
        if (chunks.isEmpty()) {
            logger.info("ℹ️ RAG-индекс пуст. Нет проиндексированных документов")
            return@withContext emptyList()
        }

        val hits = chunks.mapNotNull { chunk ->
            val chunkEmbedding = chunk.embedding
            if (chunkEmbedding.isEmpty() || chunkEmbedding.size != queryEmbedding.size) {
                // Защита от несовпадения размерности
                return@mapNotNull null
            }

            val score = cosineSimilarity(queryEmbedding, queryNorm, chunkEmbedding)
            RagHit(
                sourceId = chunk.sourceId,
                chunkIndex = chunk.chunkIndex,
                text = chunk.text,
                score = score.toDouble()
            )
        }
            .sortedByDescending { it.score }
            .take(topK.coerceAtLeast(1))

        logger.info("✅ RAG-поиск завершён. Найдено кандидатов: {}", hits.size)

        hits
    }

    private fun l2Norm(vector: FloatArray): Float {
        var sum = 0.0f
        for (v in vector) {
            sum += v * v
        }
        return if (sum == 0.0f) 0.0f else sqrt(sum)
    }

    private fun cosineSimilarity(q: FloatArray, qNorm: Float, v: FloatArray): Float {
        var dot = 0.0f
        var i = 0
        val size = q.size
        while (i < size) {
            dot += q[i] * v[i]
            i++
        }
        val vNorm = l2Norm(v)
        if (qNorm == 0.0f || vNorm == 0.0f) return 0.0f
        return dot / (qNorm * vNorm)
    }
}