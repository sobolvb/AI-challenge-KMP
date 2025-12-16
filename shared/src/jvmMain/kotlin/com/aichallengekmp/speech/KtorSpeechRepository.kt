package com.aichallengekmp.speech

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

class KtorSpeechRepository(
    private val client: HttpClient,
    private val baseUrl: String
) : SpeechRepository {

    override suspend fun transcribeAudio(audioData: ByteArray, language: String): Result<TranscriptionResult> = runCatching {
        client.submitFormWithBinaryData(
            url = "$baseUrl/api/speech/recognize",
            formData = formData {
                append("audio", audioData, Headers.build {
                    append(HttpHeaders.ContentType, "audio/wav")
                    append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                })
                append("language", language)
            }
        ).body()
    }
}
