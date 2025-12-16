package com.aichallengekmp.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.*

/**
 * Рекордер аудио для Desktop (JavaSound API)
 */
class AudioRecorder {
    private val logger = LoggerFactory.getLogger(AudioRecorder::class.java)

    private var targetDataLine: TargetDataLine? = null
    private var isRecordingActive = false
    private var audioData: ByteArrayOutputStream? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    /**
     * Формат аудио: 16kHz, 16 bit, mono
     * (Оптимально для распознавания речи)
     */
    private val audioFormat = AudioFormat(
        16000f,  // Sample rate: 16kHz
        16,      // Sample size: 16 bit
        1,       // Channels: mono
        true,    // Signed
        false    // Little endian
    )

    /**
     * Начать запись
     */
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        try {
            logger.info("🎤 Начинаем запись...")

            val dataLineInfo = DataLine.Info(TargetDataLine::class.java, audioFormat)

            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                logger.error("❌ Аудио формат не поддерживается")
                return@withContext
            }

            targetDataLine = (AudioSystem.getLine(dataLineInfo) as TargetDataLine).apply {
                open(audioFormat)
                start()
            }

            audioData = ByteArrayOutputStream()
            isRecordingActive = true
            _isRecording.value = true

            logger.info("✅ Запись началась")

            // Читаем аудио в отдельном потоке
            val buffer = ByteArray(4096)
            while (isRecordingActive) {
                val bytesRead = targetDataLine?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    audioData?.write(buffer, 0, bytesRead)
                }
            }

        } catch (e: LineUnavailableException) {
            logger.error("❌ Не удалось получить доступ к микрофону: ${e.message}")
            _isRecording.value = false
        } catch (e: Exception) {
            logger.error("❌ Ошибка при записи: ${e.message}", e)
            _isRecording.value = false
        }
    }

    /**
     * Остановить запись и сохранить в файл
     */
    suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        try {
            logger.info("🛑 Останавливаем запись...")

            isRecordingActive = false
            _isRecording.value = false

            targetDataLine?.apply {
                stop()
                close()
            }

            val recordedData = audioData?.toByteArray()
            if (recordedData == null || recordedData.isEmpty()) {
                logger.warn("⚠️ Нет записанных данных")
                return@withContext null
            }

            logger.info("📊 Записано ${recordedData.size} bytes")

            // Создаем WAV файл
            val tempFile = File.createTempFile("voice_", ".wav")
            tempFile.deleteOnExit()

            AudioSystem.write(
                AudioInputStream(
                    recordedData.inputStream(),
                    audioFormat,
                    recordedData.size.toLong() / audioFormat.frameSize
                ),
                AudioFileFormat.Type.WAVE,
                tempFile
            )

            logger.info("✅ Аудио сохранено: ${tempFile.absolutePath} (${tempFile.length()} bytes)")

            tempFile

        } catch (e: Exception) {
            logger.error("❌ Ошибка при остановке записи: ${e.message}", e)
            null
        } finally {
            targetDataLine = null
            audioData = null
        }
    }

    /**
     * Проверка доступности микрофона
     */
    fun isMicrophoneAvailable(): Boolean {
        val dataLineInfo = DataLine.Info(TargetDataLine::class.java, audioFormat)
        return AudioSystem.isLineSupported(dataLineInfo)
    }
}
