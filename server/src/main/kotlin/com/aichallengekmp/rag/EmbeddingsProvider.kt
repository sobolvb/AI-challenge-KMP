package com.aichallengekmp.rag

/**
 * Абстракция над провайдером эмбеддингов
 */
interface EmbeddingsProvider {
    suspend fun embed(text: String): FloatArray
}