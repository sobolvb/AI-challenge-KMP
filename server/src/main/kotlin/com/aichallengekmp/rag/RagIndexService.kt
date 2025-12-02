package com.aichallengekmp.rag

import com.aichallengekmp.database.dao.RagChunkDao
import com.aichallengekmp.database.dao.RagChunkDao.DocumentChunkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Сервис индексации документов для RAG
 */
class RagIndexService(
    private val ragChunkDao: RagChunkDao,
    private val embeddingsProvider: EmbeddingsProvider
) {

    private val logger = LoggerFactory.getLogger(RagIndexService::class.java)

    /**
     * Проиндексировать документ: разбить на чанки, получить эмбеддинги, сохранить в БД
     */
    suspend fun indexDocument(sourceId: String, text: String) = withContext(Dispatchers.Default) {
        logger.info("📚 Индексация документа: {}", sourceId)

        // Удаляем старые чанки этого документа
        ragChunkDao.deleteBySourceId(sourceId)

        val chunks = splitIntoChunks(text)
        logger.info("✂️ Документ {} разбит на {} чанков", sourceId, chunks.size)

        if (chunks.isEmpty()) {
            logger.warn("⚠️ Документ {} не содержит текста для индексации", sourceId)
            return@withContext
        }

        val entities = chunks.mapIndexed { index, chunkText ->
            val embedding = embeddingsProvider.embed(chunkText)
            DocumentChunkEntity(
                id = null,
                sourceId = sourceId,
                chunkIndex = index,
                text = chunkText,
                embedding = embedding
            )
        }

        ragChunkDao.insertChunks(entities)

        logger.info("✅ Индексация документа {} завершена. Сохранено чанков: {}", sourceId, entities.size)
    }

    /**
     * Разбиение текста на перекрывающиеся чанки по ~200-300 слов с overlap ~50 слов
     */
    fun splitIntoChunks(
        text: String,
        maxWordsPerChunk: Int = 50,   // было 250
        overlapWords: Int = 10        // было 50
    ): List<String> {
        val words = text.split("\n", "\t", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (words.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var start = 0
        val step = (maxWordsPerChunk - overlapWords).coerceAtLeast(1)

        while (start < words.size) {
            val end = (start + maxWordsPerChunk).coerceAtMost(words.size)
            if (start >= end) break

            val chunkWords = words.subList(start, end)
            val chunkText = chunkWords.joinToString(" ")
            if (chunkText.isNotBlank()) {
                chunks += chunkText
            }

            if (end == words.size) break
            start += step
        }

        return chunks
    }

    /**
     * Проиндексировать файл с кодом: разбить на чанки по строкам, получить эмбеддинги, сохранить в БД
     * Для кода используется другая логика чанкования - по строкам кода, а не по словам
     */
    suspend fun indexCodeFile(sourceId: String, code: String) = withContext(Dispatchers.Default) {
        logger.info("💻 Индексация кода: {}", sourceId)

        // Удаляем старые чанки этого файла
        ragChunkDao.deleteBySourceId(sourceId)

        val chunks = splitCodeIntoChunks(code)
        logger.info("✂️ Код {} разбит на {} чанков", sourceId, chunks.size)

        if (chunks.isEmpty()) {
            logger.warn("⚠️ Файл {} не содержит кода для индексации", sourceId)
            return@withContext
        }

        val entities = chunks.mapIndexed { index, chunkText ->
            val embedding = embeddingsProvider.embed(chunkText)
            DocumentChunkEntity(
                id = null,
                sourceId = sourceId,
                chunkIndex = index,
                text = chunkText,
                embedding = embedding
            )
        }

        ragChunkDao.insertChunks(entities)

        logger.info("✅ Индексация кода {} завершена. Сохранено чанков: {}", sourceId, entities.size)
    }

    /**
     * Разбиение кода на чанки по строкам (более подходящее для исходного кода)
     * Чанк ~100-150 строк с overlap ~20 строк
     */
    fun splitCodeIntoChunks(
        code: String,
        maxLinesPerChunk: Int = 100,
        overlapLines: Int = 20
    ): List<String> {
        val lines = code.lines()
        if (lines.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        var start = 0
        val step = (maxLinesPerChunk - overlapLines).coerceAtLeast(1)

        while (start < lines.size) {
            val end = (start + maxLinesPerChunk).coerceAtMost(lines.size)
            if (start >= end) break

            val chunkLines = lines.subList(start, end)
            val chunkText = chunkLines.joinToString("\n")
            if (chunkText.isNotBlank()) {
                chunks += chunkText
            }

            if (end == lines.size) break
            start += step
        }

        return chunks
    }
}