package com.claude.agent.services

import com.claude.agent.common.models.UserProfile
import com.claude.agent.common.models.UserProfileUtils.toSystemPromptContext
import com.claude.agent.config.UserProfileConfig
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Сервис персонализации для адаптации поведения агента под пользователя.
 * 
 * Формирует персонализированный контекст для системных промптов,
 * учитывая предпочтения, привычки и рабочий контекст пользователя.
 */
class PersonalizationService(
    private val userProfile: UserProfile = UserProfileConfig.profile
) {
    private val logger = LoggerFactory.getLogger(PersonalizationService::class.java)
    
    /**
     * Генерирует персонализированный контекст для системного промпта
     * 
     * @param includeTimeContext Включить контекст времени (рабочие часы, время суток)
     * @return Строка с персонализированным контекстом
     */
    fun generatePersonalizedContext(includeTimeContext: Boolean = true): String {
        val sections = mutableListOf<String>()
        
        // Базовый контекст из профиля
        val baseContext = userProfile.toSystemPromptContext()
        if (baseContext.isNotBlank()) {
            sections.add("# User Profile")
            sections.add(baseContext)
        }
        
        // Временной контекст
        if (includeTimeContext) {
            val timeContext = generateTimeContext()
            if (timeContext.isNotBlank()) {
                sections.add("\n# Time Context")
                sections.add(timeContext)
            }
        }
        
        // Контекст стиля общения
        val styleContext = generateStyleContext()
        if (styleContext.isNotBlank()) {
            sections.add("\n# Communication Guidelines")
            sections.add(styleContext)
        }
        
        return sections.joinToString("\n")
    }
    
    /**
     * Генерирует контекст времени (рабочие часы, время суток)
     */
    private fun generateTimeContext(): String {
        val parts = mutableListOf<String>()
        
        try {
            val timezone = ZoneId.of(userProfile.user.timezone)
            val currentTime = ZonedDateTime.now(timezone)
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            
            parts.add("Current time in user's timezone (${userProfile.user.timezone}): ${currentTime.format(timeFormatter)}")
            
            // Проверка рабочих часов
            userProfile.habits.workHours?.let { workHours ->
                val isWorkingHours = isWithinWorkingHours(currentTime.toLocalTime(), workHours)
                if (!isWorkingHours) {
                    parts.add("⚠️ Note: User is currently outside their typical work hours ($workHours)")
                }
            }
            
            // Контекст пикового времени продуктивности
            userProfile.habits.peakProductivityTime?.let { peakTime ->
                parts.add("User's peak productivity time: $peakTime")
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to generate time context: ${e.message}")
        }
        
        return parts.joinToString("\n")
    }
    
    /**
     * Генерирует контекст стиля общения
     */
    private fun generateStyleContext(): String {
        val parts = mutableListOf<String>()
        
        // Стиль общения
        when (userProfile.preferences.communicationStyle) {
            "direct" -> parts.add("- Be direct and concise, avoid unnecessary pleasantries")
            "friendly" -> parts.add("- Use a friendly and warm tone, build rapport")
            "formal" -> parts.add("- Maintain a formal and professional tone")
        }
        
        // Уровень детализации
        when (userProfile.preferences.verbosity) {
            "brief" -> parts.add("- Keep responses brief and to the point")
            "medium" -> parts.add("- Provide balanced responses with key details")
            "detailed" -> parts.add("- Provide comprehensive and detailed explanations")
        }
        
        // Эмодзи
        if (!userProfile.preferences.emojiUsage) {
            parts.add("- Avoid using emojis in responses")
        }
        
        // Формат примеров кода
        when (userProfile.preferences.codeExampleFormat) {
            "inline" -> parts.add("- Include code examples inline with explanations")
            "separate" -> parts.add("- Provide code examples in separate, well-formatted blocks")
        }
        
        // Объяснения кода
        if (userProfile.preferences.explainCode) {
            parts.add("- Always explain code examples and their purpose")
        }
        
        // Стиль кода
        if (userProfile.preferences.codeStyle.isNotBlank()) {
            parts.add("- Follow ${userProfile.preferences.codeStyle} code style conventions")
        }
        
        return parts.joinToString("\n")
    }
    
    /**
     * Проверяет, находится ли текущее время в рабочих часах
     * 
     * @param currentTime Текущее время
     * @param workHours Рабочие часы в формате "HH:MM-HH:MM"
     * @return true если в рабочих часах
     */
    private fun isWithinWorkingHours(currentTime: LocalTime, workHours: String): Boolean {
        return try {
            val (start, end) = workHours.split("-").map { LocalTime.parse(it.trim()) }
            currentTime.isAfter(start) && currentTime.isBefore(end)
        } catch (e: Exception) {
            logger.warn("Failed to parse work hours: $workHours")
            true // По умолчанию считаем, что в рабочих часах
        }
    }
    
    /**
     * Получает краткое приветствие с учетом имени пользователя
     */
    fun getGreeting(): String {
        val name = if (userProfile.user.name != "User") userProfile.user.name else null
        return when {
            name != null -> "Привет, $name!"
            else -> "Привет!"
        }
    }
    
    /**
     * Проверяет, нужно ли напомнить о перерыве
     * (может использоваться в будущем для уведомлений)
     */
    fun shouldRemindBreak(sessionDurationMinutes: Int): Boolean {
        if (!userProfile.habits.breakReminders) return false
        
        val preferredLength = userProfile.habits.preferredSessionLength ?: 90
        return sessionDurationMinutes >= preferredLength
    }
}

