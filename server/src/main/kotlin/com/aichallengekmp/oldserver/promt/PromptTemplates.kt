package local.promt

import local.data.TokenUsage
import local.data.DialogMessage

object PromptTemplates {
    // Простой промт для выполнения задачи
    fun direct(task: String): String =
        "Ответь на задачу: $task"
    
    // Промт для анализа использования токенов
    fun tokenAnalyzer(
        shortTokens: TokenUsage,
        longTokens: TokenUsage,
        exceedingTokens: TokenUsage
    ): String = buildString {
        appendLine("Ты — эксперт по анализу языковых моделей.")
        appendLine("Проанализируй следующие данные об использовании токенов:")
        appendLine()
        appendLine("📊 КОРОТКИЙ ЗАПРОС:")
        appendLine("  - Входных токенов: ${shortTokens.inputTokens}")
        appendLine("  - Выходных токенов: ${shortTokens.outputTokens}")
        appendLine("  - Всего токенов: ${shortTokens.totalTokens}")
        appendLine()
        appendLine("📊 ДЛИННЫЙ ЗАПРОС:")
        appendLine("  - Входных токенов: ${longTokens.inputTokens}")
        appendLine("  - Выходных токенов: ${longTokens.outputTokens}")
        appendLine("  - Всего токенов: ${longTokens.totalTokens}")
        appendLine()
        appendLine("📊 ЗАПРОС, ПРЕВЫШАЮЩИЙ ЛИМИТ:")
        appendLine("  - Входных токенов: ${exceedingTokens.inputTokens}")
        appendLine("  - Выходных токенов: ${exceedingTokens.outputTokens}")
        appendLine("  - Всего токенов: ${exceedingTokens.totalTokens}")
        appendLine()
        appendLine("ИНСТРУКЦИИ ДЛЯ АНАЛИЗА:")
        appendLine("1. Сравни количество токенов для разных типов запросов")
        appendLine("2. Оцени эффективность использования (соотношение вход/выход)")
        appendLine("3. Опиши как влияет длина запроса на потребление токенов")
        appendLine("4. Дай рекомендации по оптимизации запросов")
        appendLine("5. Объясни поведение модели при превышении лимита")
        appendLine()
        appendLine("ДАЙ ПОДРОБНЫЙ СТРУКТУРИРОВАННЫЙ АНАЛИЗ С ВЫВОДАМИ.")
    }

    // Промт для создания summary диалога
    fun dialogSummary(messages: List<DialogMessage>): String = buildString {
        appendLine("Ты — эксперт по анализу и суммаризации диалогов.")
        appendLine("Твоя задача — создать краткое, но информативное резюме следующего диалога.")
        appendLine()
        appendLine("ТРЕБОВАНИЯ К РЕЗЮМЕ:")
        appendLine("1. Сохрани все ключевые факты и информацию")
        appendLine("2. Укажи основные темы обсуждения")
        appendLine("3. Зафиксируй важные решения или выводы")
        appendLine("4. Сохрани контекст для продолжения диалога")
        appendLine("5. Будь максимально кратким, но информативным")
        appendLine()
        appendLine("ДИАЛОГ ДЛЯ СУММАРИЗАЦИИ:")
        appendLine()

        messages.forEach { msg ->
            val roleLabel = when (msg.role) {
                "user" -> "👤 Пользователь"
                "assistant" -> "🤖 Ассистент"
                else -> msg.role
            }
            appendLine("$roleLabel: ${msg.content}")
            appendLine()
        }

        appendLine("Создай КРАТКОЕ РЕЗЮМЕ этого диалога (2-5 предложений):")
    }

    // Промт для анализа эффективности сжатия
    fun compressionAnalysis(
        messagesBeforeCount: Int,
        messagesAfterCount: Int,
        tokensBeforeCount: Int,
        tokensAfterCount: Int,
        tokensSaved: Int,
        summary: String
    ): String = buildString {
        appendLine("Проанализируй эффективность сжатия истории диалога:")
        appendLine()
        appendLine("📊 СТАТИСТИКА СЖАТИЯ:")
        appendLine("Сообщений до сжатия: $messagesBeforeCount")
        appendLine("Сообщений после сжатия: $messagesAfterCount")
        appendLine("Токенов до сжатия: $tokensBeforeCount")
        appendLine("Токенов после сжатия: $tokensAfterCount")
        appendLine("Токенов сэкономлено: $tokensSaved")
        appendLine("Коэффициент сжатия: ${String.format("%.2f", tokensSaved.toDouble() / tokensBeforeCount * 100)}%")
        appendLine()
        appendLine("📝 SUMMARY:")
        appendLine(summary)
        appendLine()
        appendLine("ЗАДАЧИ АНАЛИЗА:")
        appendLine("1. Оцени эффективность сжатия")
        appendLine("2. Проверь, сохранена ли важная информация в summary")
        appendLine("3. Оцени качество summary")
        appendLine("4. Дай рекомендации по улучшению процесса сжатия")
        appendLine("5. Оцени влияние на качество будущих ответов")
        appendLine()
        appendLine("ДАЙ ПОДРОБНЫЙ АНАЛИЗ:")
    }
}
