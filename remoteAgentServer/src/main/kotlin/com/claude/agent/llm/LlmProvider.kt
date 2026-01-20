package com.claude.agent.llm

import com.claude.agent.models.Message
import com.claude.agent.models.TokenUsage
import com.claude.agent.models.UserLocation

/**
 * Общий интерфейс для всех LLM провайдеров (Claude, Qwen, и т.д.)
 * 
 * Позволяет абстрагироваться от конкретной реализации LLM и легко переключаться между провайдерами.
 */
interface LlmProvider {
    
    /**
     * Генерирует ответ на основе системного промпта и истории сообщений
     * 
     * @param systemPrompt Системный промпт с инструкциями для модели
     * @param messages История сообщений в формате role -> content
     * @param model Название модели (опционально, используется дефолтная модель провайдера)
     * @param maxTokens Максимальное количество токенов в ответе
     * @param temperature Температура генерации (0.0 - детерминированный, 2.0 - креативный)
     * @param enabledTools Список включенных MCP инструментов
     * @param clientIp IP-адрес клиента для геолокации
     * @param userLocation Координаты пользователя
     * @param sessionId ID сессии для WebSocket уведомлений
     * @param showIntermediateMessages Показывать ли промежуточные сообщения при tool calling
     * @return Результат генерации с текстом ответа, использованием токенов и промежуточными сообщениями
     */
    suspend fun generate(
        systemPrompt: String,
        messages: List<Message>,
        model: String? = null,
        maxTokens: Int = 1024,
        temperature: Double = 1.0,
        enabledTools: List<String> = emptyList(),
        clientIp: String? = null,
        userLocation: UserLocation? = null,
        sessionId: String? = null,
        showIntermediateMessages: Boolean = true
    ): LlmResponse
    
    /**
     * Проверяет, настроен ли провайдер (есть ли API ключ, доступен ли сервер и т.д.)
     */
    fun isConfigured(): Boolean
    
    /**
     * Возвращает название провайдера для логирования
     */
    fun getProviderName(): String
}

/**
 * Результат генерации LLM
 */
data class LlmResponse(
    val reply: String?,
    val usage: TokenUsage?,
    val error: String?,
    val intermediateMessages: List<Message> = emptyList()
)

