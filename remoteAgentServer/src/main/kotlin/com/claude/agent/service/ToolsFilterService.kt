package com.claude.agent.service

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.config.ToolsFilteringConfig
import org.slf4j.LoggerFactory

/**
 * Сервис для интеллектуальной фильтрации MCP tools на основе контекста запроса.
 * 
 * Использует ключевые слова и эвристики для определения релевантных инструментов,
 * что позволяет сократить количество токенов, передаваемых в API.
 */
class ToolsFilterService {
    private val logger = LoggerFactory.getLogger(ToolsFilterService::class.java)
    
    // Ключевые слова для каждого инструмента
    private val toolKeywords = mapOf(
        "weather" to listOf(
            "погода", "температура", "градус", "дождь", "снег", "солнце", "облачно",
            "weather", "temperature", "rain", "snow", "sunny", "cloudy", "forecast",
            "прогноз", "климат", "ветер", "wind"
        ),
        "solar" to listOf(
            "солнце", "солнечный", "aurora", "аврора", "полярное сияние", "геомагнитный",
            "solar", "sun", "space weather", "космическая погода", "вспышка", "flare"
        ),
        "air_tickets" to listOf(
            "билет", "авиабилет", "самолет", "рейс", "полет", "аэропорт", "перелет",
            "ticket", "flight", "airplane", "airport", "fly", "aviation", "авиа"
        ),
        "reminder" to listOf(
            "напомни", "напоминание", "reminder", "remind", "уведомление", "notification",
            "задача", "task", "todo", "запланировать", "schedule", "через", "in", "at"
        ),
        "chat_summary" to listOf(
            "резюме", "summary", "итог", "суммаризация", "summarize", "кратко", "brief",
            "обобщи", "summarization", "краткое содержание"
        ),
        "action_planner" to listOf(
            "план", "plan", "действие", "action", "шаг", "step", "последовательность",
            "sequence", "выполни", "execute", "сделай", "do"
        ),
        "android_studio" to listOf(
            "android", "студия", "studio", "эмулятор", "emulator", "gradle", "apk",
            "adb", "logcat", "build", "проект", "project", "файл", "file", "код", "code"
        )
    )
    
    /**
     * Фильтрует инструменты на основе контекста сообщения пользователя
     */
    fun filterRelevantTools(
        userMessage: String,
        enabledTools: List<String>,
        allTools: List<LocalToolDefinition>
    ): List<LocalToolDefinition> {
        if (!ToolsFilteringConfig.ENABLED) {
            // Фильтрация отключена - возвращаем все включенные инструменты
            return allTools.filter { it.name in enabledTools }
        }
        
        val messageLower = userMessage.lowercase()
        
        // Вычисляем релевантность для каждого инструмента
        val toolScores = allTools
            .filter { it.name in enabledTools }
            .map { tool ->
                val score = calculateRelevanceScore(tool.name, messageLower)
                tool to score
            }
            .sortedByDescending { it.second }
        
        // Логируем результаты
        if (toolScores.isNotEmpty()) {
            logger.debug("Tool relevance scores for message: '${userMessage.take(50)}...'")
            toolScores.forEach { (tool, score) ->
                logger.debug("  ${tool.name}: $score")
            }
        }
        
        // Берем топ-N инструментов или все с ненулевым score
        val relevantTools = if (ToolsFilteringConfig.MAX_TOOLS_PER_REQUEST > 0) {
            toolScores
                .filter { it.second > 0 }
                .take(ToolsFilteringConfig.MAX_TOOLS_PER_REQUEST)
                .map { it.first }
        } else {
            toolScores
                .filter { it.second > 0 }
                .map { it.first }
        }
        
        // Если ни один инструмент не релевантен, возвращаем все включенные
        // (чтобы не потерять функциональность)
        if (relevantTools.isEmpty() && enabledTools.isNotEmpty()) {
            logger.debug("No relevant tools found, returning all enabled tools")
            return allTools.filter { it.name in enabledTools }
        }
        
        val filtered = relevantTools.size
        val total = allTools.filter { it.name in enabledTools }.size
        if (filtered < total) {
            logger.info("🔍 Tool filtering: $filtered/$total tools selected (${total - filtered} filtered out)")
        }
        
        return relevantTools
    }
    
    /**
     * Вычисляет релевантность инструмента для сообщения
     */
    private fun calculateRelevanceScore(toolName: String, messageLower: String): Int {
        val keywords = toolKeywords[toolName] ?: return 0
        
        var score = 0
        
        // Проверяем наличие ключевых слов
        for (keyword in keywords) {
            if (messageLower.contains(keyword.lowercase())) {
                score += 10
                
                // Бонус за точное совпадение слова (не подстроки)
                val wordPattern = "\\b${Regex.escape(keyword.lowercase())}\\b".toRegex()
                if (wordPattern.containsMatchIn(messageLower)) {
                    score += 5
                }
            }
        }
        
        // Бонус за упоминание имени инструмента
        if (messageLower.contains(toolName.lowercase())) {
            score += 20
        }
        
        return score
    }
    
    /**
     * Оценивает количество токенов, сэкономленных фильтрацией
     */
    fun estimateTokensSaved(
        originalToolsCount: Int,
        filteredToolsCount: Int,
        avgTokensPerTool: Int = 150
    ): Long {
        val savedTools = originalToolsCount - filteredToolsCount
        return (savedTools * avgTokensPerTool).toLong()
    }
}

