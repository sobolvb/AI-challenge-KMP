package com.aichallengekmp.models

/**
 * Preset для настройки LLM под конкретные задачи
 */
data class LLMPreset(
    val id: String,
    val name: String,
    val description: String,
    val temperature: Double,
    val topP: Double?,
    val topK: Int?,
    val numCtx: Int?,
    val repeatPenalty: Double?,
    val systemPrompt: String?
)

/**
 * Готовые preset-ы для разных задач
 */
object LLMPresets {

    /**
     * Максимальная креативность
     * Для генерации идей, историй, творческого контента
     */
    val CREATIVE = LLMPreset(
        id = "creative",
        name = "Creative",
        description = "Максимальная креативность для генерации идей и творческого контента",
        temperature = 1.2,
        topP = 0.95,
        topK = 80,
        numCtx = 8192,
        repeatPenalty = 1.1,
        systemPrompt = "Ты креативный ассистент. Генерируй интересные и необычные идеи, используй воображение."
    )

    /**
     * Сбалансированный режим
     * Универсальный preset для большинства задач
     */
    val BALANCED = LLMPreset(
        id = "balanced",
        name = "Balanced",
        description = "Сбалансированный режим для большинства задач",
        temperature = 0.7,
        topP = 0.9,
        topK = 40,
        numCtx = 8192,
        repeatPenalty = 1.1,
        systemPrompt = "Ты полезный ассистент. Отвечай точно и понятно."
    )

    /**
     * Максимальная точность
     * Для фактических ответов, анализа данных
     */
    val PRECISE = LLMPreset(
        id = "precise",
        name = "Precise",
        description = "Максимальная точность для фактических ответов",
        temperature = 0.3,
        topP = 0.7,
        topK = 20,
        numCtx = 8192,
        repeatPenalty = 1.15,
        systemPrompt = "Ты точный и фактичный ассистент. Отвечай кратко, по делу, основываясь на фактах."
    )

    /**
     * Coding Assistant
     * Для генерации и анализа кода
     */
    val CODING = LLMPreset(
        id = "coding",
        name = "Coding",
        description = "Оптимизирован для генерации и анализа кода",
        temperature = 0.4,
        topP = 0.85,
        topK = 30,
        numCtx = 16384,  // Больший контекст для кода
        repeatPenalty = 1.2,
        systemPrompt = """
            Ты опытный программист на Kotlin и Kotlin Multiplatform.
            Пиши чистый, читаемый код с комментариями.
            Следуй best practices и современным паттернам.
            Объясняй свои решения.
        """.trimIndent()
    )

    /**
     * Code Review
     * Для проверки кода и поиска багов
     */
    val CODE_REVIEW = LLMPreset(
        id = "code_review",
        name = "Code Review",
        description = "Для анализа кода и поиска проблем",
        temperature = 0.5,
        topP = 0.8,
        topK = 25,
        numCtx = 16384,
        repeatPenalty = 1.15,
        systemPrompt = """
            Ты опытный code reviewer.
            Анализируй код на:
            - Баги и потенциальные ошибки
            - Security issues (SQL injection, XSS, etc.)
            - Performance проблемы
            - Code style и читаемость
            - Best practices
            Будь конструктивным и предлагай улучшения.
        """.trimIndent()
    )

    /**
     * Long Context
     * Для работы с большими текстами и документами
     */
    val LONG_CONTEXT = LLMPreset(
        id = "long_context",
        name = "Long Context",
        description = "Для работы с большими документами и длинными диалогами",
        temperature = 0.6,
        topP = 0.9,
        topK = 40,
        numCtx = 32768,  // Максимальный контекст
        repeatPenalty = 1.1,
        systemPrompt = "Ты ассистент для работы с большими текстами. Анализируй полный контекст перед ответом."
    )

    /**
     * Technical Writing
     * Для создания документации и технических текстов
     */
    val TECHNICAL_WRITING = LLMPreset(
        id = "technical_writing",
        name = "Technical Writing",
        description = "Для создания документации и технических описаний",
        temperature = 0.5,
        topP = 0.85,
        topK = 30,
        numCtx = 8192,
        repeatPenalty = 1.1,
        systemPrompt = """
            Ты технический писатель.
            Создавай четкую, структурированную документацию.
            Используй примеры кода, списки, таблицы.
            Пиши для разработчиков, но делай текст понятным.
        """.trimIndent()
    )

    /**
     * Все доступные preset-ы
     */
    val ALL = listOf(
        CREATIVE,
        BALANCED,
        PRECISE,
        CODING,
        CODE_REVIEW,
        LONG_CONTEXT,
        TECHNICAL_WRITING
    )

    /**
     * Получить preset по ID
     */
    fun getById(id: String): LLMPreset? = ALL.find { it.id == id }

    /**
     * Применить preset к настройкам сессии
     */
    fun applyToSettings(preset: LLMPreset, currentSettings: SessionSettingsDto): SessionSettingsDto {
        return currentSettings.copy(
            temperature = preset.temperature,
            topP = preset.topP,
            topK = preset.topK,
            numCtx = preset.numCtx,
            repeatPenalty = preset.repeatPenalty,
            systemPrompt = preset.systemPrompt
        )
    }
}
