package com.claude.agent.llm

/**
 * Фабрика для создания провайдеров промптов и описаний инструментов.
 * 
 * Выбирает оптимальные провайдеры в зависимости от типа LLM модели:
 * - Claude: подробные описания
 * - Qwen: краткие описания
 */
object PromptProviderFactory {
    
    /**
     * Создает провайдер системных промптов для указанного типа LLM
     */
    fun createSystemPromptProvider(llmType: String): SystemPromptProvider {
        return when (llmType.lowercase()) {
            "qwen", "local" -> QwenSystemPromptProvider()
            "claude" -> ClaudeSystemPromptProvider()
            else -> ClaudeSystemPromptProvider() // по умолчанию Claude
        }
    }
    
    /**
     * Создает провайдер описаний инструментов для указанного типа LLM
     */
    fun createToolDescriptionProvider(llmType: String): ToolDescriptionProvider {
        return when (llmType.lowercase()) {
            "qwen", "local" -> QwenToolDescriptionProvider()
            "claude" -> ClaudeToolDescriptionProvider()
            else -> ClaudeToolDescriptionProvider() // по умолчанию Claude
        }
    }
}

