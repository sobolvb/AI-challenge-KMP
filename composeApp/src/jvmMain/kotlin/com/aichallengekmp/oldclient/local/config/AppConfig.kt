package local.config

object AppConfig {
    // Ваш сервер Ktor запущен на порту 8080
    const val SERVER_URL = "http://10.0.2.2:8080"
    
    val BASE_URL: String = SERVER_URL
    
    const val MODEL_URI_ENV = "MODEL_URI"
    const val API_KEY_ENV = "API_KEY"
    const val YANDEX_URL = "https://llm.api.cloud.yandex.net/foundation/v1/completion"
}