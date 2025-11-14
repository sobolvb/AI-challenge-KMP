package local.config

object AppConfig {
    const val API_KEY_ENV = "YANDEX_API_KEY"
    const val MODEL_URI_ENV = "YANDEX_MODEL_URI"

    // Yandex Cloud API base
    const val YANDEX_URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
}