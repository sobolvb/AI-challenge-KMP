package com.aichallengekmp.ai

import org.slf4j.LoggerFactory

/**
 * Реестр всех доступных AI моделей
 * Агрегирует модели от всех зарегистрированных провайдеров
 */
class ModelRegistry {
    private val logger = LoggerFactory.getLogger(ModelRegistry::class.java)
    private val providers = mutableMapOf<String, AIProvider>()
    
    /**
     * Регистрация провайдера
     */
    fun registerProvider(provider: AIProvider) {
        logger.info("📝 Регистрация AI провайдера: ${provider.providerId}")
        providers[provider.providerId] = provider
    }
    
    /**
     * Получить все доступные модели от всех провайдеров
     */
    suspend fun getAllModels(): List<AIModel> {
        logger.debug("📋 Запрос всех доступных моделей")
        return providers.values.flatMap { it.getSupportedModels() }
    }
    
    /**
     * Получить модель по ID
     */
    suspend fun getModel(modelId: String): AIModel? {
        return getAllModels().firstOrNull { it.id == modelId }
    }
    
    /**
     * Получить провайдер для модели
     */
    suspend fun getProviderForModel(modelId: String): AIProvider? {
        return providers.values.firstOrNull { provider ->
            provider.getSupportedModels().any { it.id == modelId }
        }
    }
    
    /**
     * Отправить запрос на генерацию
     */
    suspend fun complete(request: CompletionRequest): CompletionResult {
        val provider = getProviderForModel(request.modelId)
            ?: throw IllegalArgumentException("Модель ${request.modelId} не найдена")
        
        logger.info("🚀 Отправка запроса через провайдер: ${provider.providerId}")
        return provider.complete(request)
    }
}
