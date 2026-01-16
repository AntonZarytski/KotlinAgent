package com.claude.agent.service

import com.claude.agent.config.McpResultsConfig
import org.slf4j.LoggerFactory

/**
 * Сервис для фильтрации и сокращения результатов MCP инструментов.
 * 
 * Цель: снизить расход токенов, передавая агенту только релевантную информацию.
 */
class McpResultsFilterService {
    private val logger = LoggerFactory.getLogger(McpResultsFilterService::class.java)
    
    /**
     * Фильтрует результат MCP инструмента перед передачей агенту.
     * 
     * @param toolName Имя инструмента
     * @param result Исходный результат
     * @return Отфильтрованный результат
     */
    fun filterResult(toolName: String, result: String): String {
        if (!McpResultsConfig.TRUNCATE_LARGE_RESULTS) {
            return result
        }
        
        // Если результат короткий - возвращаем как есть
        if (result.length <= McpResultsConfig.MAX_RESULT_LENGTH) {
            return result
        }
        
        logger.info("🔍 Truncating large result from $toolName: ${result.length} -> ${McpResultsConfig.MAX_RESULT_LENGTH} chars")
        
        return if (McpResultsConfig.SMART_TRUNCATION) {
            smartTruncate(result, toolName)
        } else {
            simpleTruncate(result)
        }
    }
    
    /**
     * Простое обрезание - берем начало
     */
    private fun simpleTruncate(result: String): String {
        return result.take(McpResultsConfig.MAX_RESULT_LENGTH) + "\n\n... [результат обрезан]"
    }
    
    /**
     * Умное обрезание - сохраняем начало и конец
     */
    private fun smartTruncate(result: String, toolName: String): String {
        val maxLen = McpResultsConfig.MAX_RESULT_LENGTH
        val headSize = (maxLen * 0.7).toInt()  // 70% на начало
        val tailSize = (maxLen * 0.3).toInt()  // 30% на конец
        
        val head = result.take(headSize)
        val tail = result.takeLast(tailSize)
        
        val omittedChars = result.length - headSize - tailSize
        
        return buildString {
            append(head)
            append("\n\n... [пропущено $omittedChars символов] ...\n\n")
            append(tail)
        }
    }
    
    /**
     * Оценивает количество сэкономленных токенов
     */
    fun estimateTokensSaved(originalLength: Int, filteredLength: Int): Long {
        val savedChars = originalLength - filteredLength
        // Примерно 4 символа на токен
        return (savedChars / 4).toLong()
    }
    
    /**
     * Проверяет, нужно ли фильтровать результат для конкретного инструмента
     */
    fun shouldFilter(toolName: String): Boolean {
        // Некоторые инструменты всегда возвращают короткие результаты
        val alwaysShortTools = setOf(
            "get_weather_forecast",
            "get_solar_activity",
            "create_reminder",
            "list_reminders"
        )
        
        return toolName !in alwaysShortTools
    }
}

