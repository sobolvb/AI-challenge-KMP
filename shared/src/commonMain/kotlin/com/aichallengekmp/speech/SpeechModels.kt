package com.aichallengekmp.speech

import kotlinx.serialization.Serializable

@Serializable
data class TranscriptionResult(
    val text: String,
    val language: String? = null,
    val confidence: Double? = null,
    val error: String? = null
)
