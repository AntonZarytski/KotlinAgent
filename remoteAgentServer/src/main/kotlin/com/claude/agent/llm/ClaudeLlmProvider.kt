package com.claude.agent.llm

import com.claude.agent.config.ClaudeConfig
import com.claude.agent.models.Message
import org.slf4j.LoggerFactory

/**
 * Адаптер для ClaudeClient, реализующий интерфейс LlmProvider
 * 
 * Делегирует все вызовы существующему ClaudeClient, сохраняя всю его функциональность
 * (prompt caching, tool filtering, RAG и т.д.)
 */
class ClaudeLlmProvider(
    private val claudeClient: ClaudeClient
) : LlmProvider {
    
    private val logger = LoggerFactory.getLogger(ClaudeLlmProvider::class.java)
    
    override suspend fun generate(
        systemPrompt: String,
        messages: List<Message>,
        model: String?,
        maxTokens: Int,
        temperature: Double,
        topP: Double,
        topK: Int,
        contextWindow: Int,
        enabledTools: List<String>,
        clientIp: String?,
        userLocation: com.claude.agent.models.UserLocation?,
        sessionId: String?,
        showIntermediateMessages: Boolean,
        useRag: Boolean,
        ragTopK: Int,
        ragMinSimilarity: Double,
        ragFilterEnabled: Boolean,
        selectedFiles: List<String>
    ): LlmResponse {
        // Извлекаем последнее пользовательское сообщение
        val userMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        
        // Формируем историю без последнего сообщения
        val conversationHistory = if (messages.isNotEmpty()) {
            messages.dropLast(1)
        } else {
            emptyList()
        }
        
        logger.info("=== Claude LLM Request ===")
        logger.info("Model: ${model ?: ClaudeConfig.MODEL}")
        logger.info("User message: ${userMessage.take(100)}...")
        logger.info("History size: ${conversationHistory.size}")
        logger.info("Enabled tools: ${enabledTools.size}")
        
        // Вызываем существующий ClaudeClient
        // Системный промпт будет сформирован внутри ClaudeClient через SystemPrompts.getSystemPrompt
        val response = claudeClient.sendMessage(
            userMessage = userMessage,
            model = model ?: ClaudeConfig.MODEL,
            maxTokens = maxTokens,
            specMode = false,
            conversationHistory = conversationHistory,
            temperature = temperature,
            enabledTools = enabledTools,
            clientIp = clientIp,
            userLocation = userLocation,
            sessionId = sessionId,
            showIntermediateMessages = showIntermediateMessages,
            useRag = useRag,
            ragTopK = ragTopK,
            ragMinSimilarity = ragMinSimilarity,
            ragFilterEnabled = ragFilterEnabled,
            selectedFiles = selectedFiles
        )
        
        // Конвертируем ClaudeResponse в LlmResponse
        return LlmResponse(
            reply = response.reply,
            usage = response.usage,
            error = response.error,
            intermediateMessages = response.intermediateMessages
        )
    }
    
    override fun isConfigured(): Boolean {
        return claudeClient.isApiKeyConfigured()
    }
    
    override fun getProviderName(): String = "Claude (Anthropic)"
}

