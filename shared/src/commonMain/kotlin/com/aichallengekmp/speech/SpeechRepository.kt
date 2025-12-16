package com.aichallengekmp.speech

/**
 * Репозиторий для работы с распознаванием речи
 */
interface SpeechRepository {
    /**
     * Отправить аудио файл для распознавания
     */
    suspend fun transcribeAudio(audioData: ByteArray, language: String = "ru"): Result<TranscriptionResult>
}
