package local.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import local.repository.ReasoningRepository
import local.repository.ReasoningRepositoryImpl
import local.viewmodel.ReasoningViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

object ServiceLocator {
    
    // HTTP Client
    private val httpClient: HttpClient by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.BODY
            }
            engine {
                connectTimeout = 30_000_000
                socketTimeout = 30_000_000
            }
        }
    }
    
    // JSON
    private val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    
    // Repository
    private val repository: ReasoningRepository by lazy {
        ReasoningRepositoryImpl(httpClient, json)
    }
    
    // ViewModel Factory
    class ViewModelFactory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReasoningViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ReasoningViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
    
    // ViewModel
    fun getViewModel(): ViewModelFactory {
        return ViewModelFactory()
    }
    
    // Cleanup function
    fun cleanup() {
        httpClient.close()
    }
}
