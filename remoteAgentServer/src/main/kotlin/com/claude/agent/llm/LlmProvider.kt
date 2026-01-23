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
     * @param useRag Использовать ли RAG для поиска релевантного контекста
     * @param ragTopK Количество релевантных чанков для RAG
     * @param ragMinSimilarity Минимальный порог схожести для RAG (0.0-1.0)
     * @param ragFilterEnabled Включить фильтрацию по порогу схожести
     * @param selectedFiles Список абсолютных путей к выбранным файлам из file tree
     * @return Результат генерации с текстом ответа, использованием токенов и промежуточными сообщениями
     */
    suspend fun generate(
        systemPrompt: String,
        messages: List<Message>,
        model: String? = null,
        maxTokens: Int = 1024,
        temperature: Double = 1.0,
        topP: Double = 0.9,
        topK: Int = 40,
        contextWindow: Int = 4096,
        enabledTools: List<String> = emptyList(),
        clientIp: String? = null,
        userLocation: UserLocation? = null,
        sessionId: String? = null,
        showIntermediateMessages: Boolean = true,
        useRag: Boolean = false,
        ragTopK: Int = 3,
        ragMinSimilarity: Double = 0.3,
        ragFilterEnabled: Boolean = true,
        selectedFiles: List<String> = emptyList()
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

