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
 *     qwenLlmProvider = qwenProvider,
 *     defaultProvider = qwenProvider
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
    private val qwenLlmProvider: LlmProvider,
    private val defaultProvider: LlmProvider
) {
    private val logger = LoggerFactory.getLogger(LlmProviderFactory::class.java)
    
    /**
     * Возвращает провайдер по имени
     * 
     * @param providerName Имя провайдера: "claude", "local", "qwen" или null для провайдера по умолчанию
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
            "local", "qwen" -> {
                logger.debug("Selected Qwen (local) provider")
                qwenLlmProvider
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
     * Возвращает Qwen провайдер (для обратной совместимости)
     */
    fun getQwenProvider(): LlmProvider = qwenLlmProvider
    
    /**
     * Проверяет, доступен ли указанный провайдер
     */
    fun isProviderAvailable(providerName: String): Boolean {
        return when (providerName.lowercase()) {
            "claude" -> claudeLlmProvider?.isConfigured() ?: false
            "local", "qwen" -> qwenLlmProvider.isConfigured()
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

        if (qwenLlmProvider.isConfigured()) {
            providers.add("local")
        }

        return providers
    }

    /**
     * Проверяет, доступен ли Claude провайдер
     */
    fun isClaudeAvailable(): Boolean = claudeLlmProvider != null
}

