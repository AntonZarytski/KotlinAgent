package com.claude.agent.llm

import org.slf4j.LoggerFactory

/**
 * Фабрика для выбора LLM провайдера
 *
 * Централизует логику выбора провайдера и упрощает добавление новых провайдеров.
 * Вместо передачи всех провайдеров в каждый сервис, передается только фабрика.
 *
 * Пример использования:
 * ```kotlin
 * val factory = LlmProviderFactory(
 *     claudeLlmProvider = claudeProvider,
 *     qwen15bProvider = qwen15bProvider,
 *     qwen7bProvider = qwen7bProvider,
 *     defaultProvider = qwen15bProvider
 * )
 *
 * // Выбор провайдера из запроса
 * val provider = factory.getProvider(request.llm_provider)
 *
 * // Использование провайдера по умолчанию
 * val defaultProvider = factory.getDefaultProvider()
 * ```
 */
class LlmProviderFactory(
    private val claudeLlmProvider: LlmProvider?,
    private val qwen15bProvider: LlmProvider,
    private val qwen7bProvider: LlmProvider,
    private val defaultProvider: LlmProvider
) {
    private val logger = LoggerFactory.getLogger(LlmProviderFactory::class.java)
    
    /**
     * Возвращает провайдер по имени
     *
     * @param providerName Имя провайдера: "claude", "qwen-1.5b", "qwen-7b", "local", "qwen" или null для провайдера по умолчанию
     * @return Выбранный LLM провайдер
     */
    fun getProvider(providerName: String?): LlmProvider {
        val provider = when (providerName?.lowercase()) {
            "claude" -> {
                if (claudeLlmProvider == null) {
                    logger.error("Claude provider requested but not available (missing API key)")
                    throw IllegalStateException("Claude provider is not available. Please set ANTHROPIC_API_KEY environment variable.")
                }
                logger.debug("Selected Claude provider")
                claudeLlmProvider
            }
            "qwen-1.5b", "qwen1.5b", "qwen-small" -> {
                logger.debug("Selected Qwen 1.5B provider")
                qwen15bProvider
            }
            "qwen-7b", "qwen7b", "qwen-large" -> {
                logger.debug("Selected Qwen 7B provider")
                qwen7bProvider
            }
            "local", "qwen" -> {
                logger.debug("Selected default Qwen provider (1.5B)")
                qwen15bProvider
            }
            null -> {
                logger.debug("Using default provider: ${defaultProvider.getProviderName()}")
                defaultProvider
            }
            else -> {
                logger.warn("Unknown provider '$providerName', using default: ${defaultProvider.getProviderName()}")
                defaultProvider
            }
        }

        return provider
    }
    
    /**
     * Возвращает провайдер по умолчанию
     */
    fun getDefaultProvider(): LlmProvider = defaultProvider

    /**
     * Возвращает Claude провайдер или null если недоступен
     */
    fun getClaudeProviderOrNull(): LlmProvider? = claudeLlmProvider

    /**
     * Возвращает Qwen 1.5B провайдер
     */
    fun getQwen15bProvider(): LlmProvider = qwen15bProvider

    /**
     * Возвращает Qwen 7B провайдер
     */
    fun getQwen7bProvider(): LlmProvider = qwen7bProvider

    /**
     * Возвращает Qwen провайдер (для обратной совместимости - возвращает 1.5B)
     */
    @Deprecated("Use getQwen15bProvider() or getQwen7bProvider() instead")
    fun getQwenProvider(): LlmProvider = qwen15bProvider

    /**
     * Проверяет, доступен ли указанный провайдер
     */
    fun isProviderAvailable(providerName: String): Boolean {
        return when (providerName.lowercase()) {
            "claude" -> claudeLlmProvider?.isConfigured() ?: false
            "qwen-1.5b", "qwen1.5b", "qwen-small" -> qwen15bProvider.isConfigured()
            "qwen-7b", "qwen7b", "qwen-large" -> qwen7bProvider.isConfigured()
            "local", "qwen" -> qwen15bProvider.isConfigured()
            else -> false
        }
    }

    /**
     * Возвращает список доступных провайдеров
     */
    fun getAvailableProviders(): List<String> {
        val providers = mutableListOf<String>()

        if (claudeLlmProvider?.isConfigured() == true) {
            providers.add("claude")
        }

        if (qwen15bProvider.isConfigured()) {
            providers.add("qwen-1.5b")
        }

        if (qwen7bProvider.isConfigured()) {
            providers.add("qwen-7b")
        }

        return providers
    }

    /**
     * Проверяет, доступен ли Claude провайдер
     */
    fun isClaudeAvailable(): Boolean = claudeLlmProvider != null
}

