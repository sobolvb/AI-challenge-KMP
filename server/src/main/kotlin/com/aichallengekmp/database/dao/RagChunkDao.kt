package com.aichallengekmp.database.dao

import com.aichallengekmp.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * DAO для работы с RAG-чанками документов
 */
class RagChunkDao(private val database: AppDatabase) {
    private val logger = LoggerFactory.getLogger(RagChunkDao::class.java)
    private val queries = database.documentChunkQueries

    data class DocumentChunkEntity(
        val id: Long?,
        val sourceId: String,
        val chunkIndex: Int,
        val text: String,
        val embedding: FloatArray
    )

    /**
     * Удалить все чанки документа по его sourceId
     */
    suspend fun deleteBySourceId(sourceId: String) = withContext(Dispatchers.IO) {
        logger.info("🧹 Удаление старых чанков для документа: {}", sourceId)
        queries.deleteBySourceId(sourceId)
    }

    /**
     * Массовая вставка чанков (в одной транзакции)
     */
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>) = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) {
            logger.warn("⚠️ Попытка вставить пустой список RAG-чанков — операция пропущена")
            return@withContext
        }

        logger.info("💾 Сохранение {} RAG-чанков в БД", chunks.size)

        database.transaction {
            chunks.forEach { chunk ->
                queries.insertChunk(
                    sourceId = chunk.sourceId,
                    chunkIndex = chunk.chunkIndex.toLong(),
                    text = chunk.text,
                    embedding = serializeEmbedding(chunk.embedding)
                )
            }
        }
    }

    /**
     * Получить все чанки из индекса
     */
    suspend fun getAllChunks(): List<DocumentChunkEntity> = withContext(Dispatchers.IO) {
        logger.debug("📥 Загрузка всех RAG-чанков из БД")
        queries.selectAll()
            .executeAsList()
            .map { row ->
                DocumentChunkEntity(
                    id = row.id,
                    sourceId = row.sourceId,
                    chunkIndex = row.chunkIndex.toInt(),
                    text = row.text,
                    embedding = deserializeEmbedding(row.embedding)
                )
            }
    }

    // ============= Вспомогательные методы сериализации =============

    private fun serializeEmbedding(embedding: FloatArray): String {
        // Простейшее JSON-представление вида [0.1, -0.2, ...]
        return embedding.joinToString(prefix = "[", postfix = "]", separator = ",") { value ->
            // Используем toString(), чтобы сохранить достаточную точность
            value.toString()
        }
    }

    private fun deserializeEmbedding(json: String): FloatArray {
        val trimmed = json.trim()
        if (trimmed.length < 2 || trimmed == "[]") return FloatArray(0)

        val content = trimmed.removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return FloatArray(0)

        val parts = content.split(',')
        val result = FloatArray(parts.size)
        var i = 0
        for (part in parts) {
            val value = part.trim()
            if (value.isNotEmpty()) {
                result[i] = value.toFloat()
                i++
            }
        }
        // Если по какой-то причине часть элементов была пустой, обрежем массив
        return if (i == result.size) result else result.copyOf(i)
    }
}