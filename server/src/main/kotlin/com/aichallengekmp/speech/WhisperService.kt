package com.aichallengekmp.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Сервис для распознавания речи через Whisper
 */
class WhisperService {
    private val logger = LoggerFactory.getLogger(WhisperService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Распознать речь из аудио файла
     */
    suspend fun transcribeAudio(audioFile: File, language: String = "ru"): TranscriptionResult {
        logger.info("🎤 Начинаем распознавание речи: ${audioFile.name}")

        try {
            // Путь к Python скрипту
            val scriptPath = File("server/whisper_service.py").absolutePath

            if (!File(scriptPath).exists()) {
                logger.error("❌ Python скрипт не найден: $scriptPath")
                return TranscriptionResult(
                    text = "",
                    error = "Whisper service script not found"
                )
            }

            // Запускаем Python скрипт
            val processBuilder = ProcessBuilder(
                "python3",
                scriptPath,
                audioFile.absolutePath,
                language
            )
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()

            // Читаем вывод
            val output = process.inputStream.bufferedReader().use { it.readText() }

            // Ждем завершения (максимум 30 секунд)
            val finished = process.waitFor(30, TimeUnit.SECONDS)

            if (!finished) {
                process.destroy()
                logger.error("❌ Whisper процесс превысил таймаут")
                return TranscriptionResult(
                    text = "",
                    error = "Transcription timeout"
                )
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                logger.error("❌ Whisper завершился с ошибкой: $exitCode\n$output")
                return TranscriptionResult(
                    text = "",
                    error = "Whisper process failed: $output"
                )
            }

            // Парсим JSON ответ (берем последнюю строку, т.к. могут быть warnings)
            val jsonLine = output.lines().lastOrNull { it.trim().startsWith("{") }
            if (jsonLine == null) {
                logger.error("❌ Не найден JSON в выводе Whisper:\n$output")
                return TranscriptionResult(
                    text = "",
                    error = "No JSON found in Whisper output"
                )
            }

            val result = try {
                json.decodeFromString<WhisperResponse>(jsonLine)
            } catch (e: Exception) {
                logger.error("❌ Ошибка парсинга ответа Whisper: ${e.message}\nОтвет: $output")
                return TranscriptionResult(
                    text = "",
                    error = "Failed to parse Whisper response: ${e.message}"
                )
            }

            if (result.error != null) {
                logger.error("❌ Ошибка от Whisper: ${result.error}")
                return TranscriptionResult(
                    text = "",
                    error = result.error
                )
            }

            logger.info("✅ Распознано: \"${result.text}\" (язык: ${result.language}, вероятность: ${result.language_probability})")

            return TranscriptionResult(
                text = result.text ?: "",
                language = result.language,
                confidence = result.language_probability
            )

        } catch (e: Exception) {
            logger.error("❌ Ошибка распознавания речи: ${e.message}", e)
            return TranscriptionResult(
                text = "",
                error = e.message ?: "Unknown error"
            )
        }
    }
}

/**
 * Ответ от Python скрипта
 */
@Serializable
private data class WhisperResponse(
    val text: String? = null,
    val language: String? = null,
    val language_probability: Double? = null,
    val error: String? = null
)

/**
 * Результат распознавания
 */
@Serializable
data class TranscriptionResult(
    val text: String,
    val language: String? = null,
    val confidence: Double? = null,
    val error: String? = null
)
