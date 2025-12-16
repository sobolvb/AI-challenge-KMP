package com.aichallengekmp.routes

import com.aichallengekmp.speech.WhisperService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

private val logger = LoggerFactory.getLogger("SpeechRoutes")

fun Route.speechRoutes(whisperService: WhisperService) {
    route("/speech") {
        /**
         * POST /api/speech/recognize
         * Принимает аудио файл и возвращает распознанный текст
         */
        post("/recognize") {
            logger.info("🎤 Получен запрос на распознавание речи")

            val multipart = call.receiveMultipart()
            var audioFile: File? = null
            var language = "ru"

            try {
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            if (part.name == "audio") {
                                // Сохраняем аудио во временный файл
                                val fileName = "audio_${UUID.randomUUID()}.wav"
                                val tempDir = File("server/temp")
                                if (!tempDir.exists()) {
                                    tempDir.mkdirs()
                                }
                                audioFile = File(tempDir, fileName)

                                part.streamProvider().use { input ->
                                    audioFile!!.outputStream().buffered().use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                logger.info("📁 Аудио сохранено: ${audioFile!!.absolutePath} (${audioFile!!.length()} bytes)")
                            }
                        }
                        is PartData.FormItem -> {
                            if (part.name == "language") {
                                language = part.value
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (audioFile == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No audio file provided"))
                    return@post
                }

                // Распознаем речь
                val result = withContext(Dispatchers.IO) {
                    whisperService.transcribeAudio(audioFile!!, language)
                }

                // Удаляем временный файл
                audioFile!!.delete()

                if (result.error != null) {
                    logger.error("❌ Ошибка распознавания: ${result.error}")
                    call.respond(HttpStatusCode.InternalServerError, result)
                } else {
                    logger.info("✅ Распознано: \"${result.text}\"")
                    call.respond(result)
                }

            } catch (e: Exception) {
                logger.error("❌ Ошибка обработки запроса: ${e.message}", e)
                audioFile?.delete()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        /**
         * GET /api/speech/status
         * Проверка доступности Whisper
         */
        get("/status") {
            val scriptPath = File("server/whisper_service.py")
            val available = scriptPath.exists()

            call.respond(
                mapOf(
                    "available" to available,
                    "script_path" to scriptPath.absolutePath
                )
            )
        }
    }
}
