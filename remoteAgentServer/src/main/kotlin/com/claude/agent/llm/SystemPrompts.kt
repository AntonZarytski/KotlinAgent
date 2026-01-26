package com.claude.agent.llm

import com.claude.agent.services.PersonalizationService
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Промпты для различных форматов вывода Claude API.
 *
 * Аналог prompts.py из Python-версии.
 * Использует Kotlin object и константы для организации промптов.
 */
object SystemPrompts {
    private val logger = LoggerFactory.getLogger(SystemPrompts::class.java)

    /**
     * Генерирует системный промпт для LLM API.
     *
     * @param specMode Режим сбора уточняющих данных (true/false)
     * @param llmType Тип LLM модели ('claude' или 'qwen') для оптимизации промпта
     * @param includePersonalization Включить персонализацию из профиля пользователя
     * @return Склеенный системный промпт
     */
    fun getSystemPrompt(
        enabledTools: List<String>,
        specMode: Boolean = false,
        isRagEnabled: Boolean,
        llmType: String = "claude",
        includePersonalization: Boolean = true
    ): String {
        // Получаем провайдеры для данного типа LLM
        val systemPromptProvider = PromptProviderFactory.createSystemPromptProvider(llmType)
        val toolDescriptionProvider = PromptProviderFactory.createToolDescriptionProvider(llmType)

        // Получаем текущее время с часовым поясом
        val currentTime = ZonedDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        val formattedTime = currentTime.format(formatter)

        // Выбираем базовый промпт через провайдер
        val baseModePrompt = if (specMode) {
            systemPromptProvider.getSpecModePrompt()
        } else {
            systemPromptProvider.getDefaultModePrompt()
        }

        // Начинаем с текущего времени
        var basePrompt = """Текущее время: $formattedTime"""

        // СНАЧАЛА добавляем персонализацию (чтобы модель видела её в начале промпта)
        if (includePersonalization) {
            logger.info("🔧 Начинаем добавление персонализации в системный промпт...")
            try {
                logger.debug("Создаем PersonalizationService...")
                val personalizationService = PersonalizationService()
                logger.debug("PersonalizationService создан успешно")

                logger.debug("Генерируем персонализированный контекст...")
                val personalizedContext = personalizationService.generatePersonalizedContext(
                    includeTimeContext = true
                )
                logger.debug("Персонализированный контекст сгенерирован, длина: ${personalizedContext.length}")

                if (personalizedContext.isNotBlank()) {
                    basePrompt += "\n\n$personalizedContext"
                    logger.info("✅ Персонализация успешно добавлена в системный промпт")
                    logger.debug("Персонализированный контекст:\n$personalizedContext")
                } else {
                    logger.warn("⚠️ Персонализированный контекст пустой")
                }
            } catch (e: Exception) {
                logger.error("❌ Ошибка персонализации: ${e.message}", e)
                // Игнорируем ошибки персонализации, чтобы не ломать основной функционал
            }
        } else {
            logger.info("ℹ️ Персонализация отключена (includePersonalization = false)")
        }

        // ПОТОМ добавляем базовый промпт с инструкциями
        basePrompt += "\n\n$baseModePrompt"

        // Добавляем описания инструментов
        if (enabledTools.isNotEmpty()) {
            basePrompt += toolDescriptionProvider.getToolsSectionHeader()

            for (toolName in enabledTools) {
                val description = toolDescriptionProvider.getToolDescription(toolName)
                basePrompt += "\n$description"
            }

            // Добавляем правила использования инструментов
            basePrompt += systemPromptProvider.getToolUsageRules()
        }

        // Добавляем инструкции по RAG, если включен
        if (isRagEnabled) {
            basePrompt += systemPromptProvider.getRagInstructions()
        }

        return basePrompt
    }
}

