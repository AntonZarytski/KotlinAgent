package com.claude.agent.common.models

import kotlinx.serialization.Serializable

/**
 * Модель персонального профиля пользователя для персонализации агента.
 * 
 * Используется как локальным, так и облачным агентом для адаптации
 * поведения, стиля общения и контекста под конкретного пользователя.
 * 
 * Загружается из agent-profile.json при старте приложения.
 */
@Serializable
data class UserProfile(
    val user: UserInfo = UserInfo(),
    val preferences: UserPreferences = UserPreferences(),
    val workContext: WorkContext = WorkContext(),
    val habits: UserHabits = UserHabits(),
    val customInstructions: List<String> = emptyList()
)

/**
 * Базовая информация о пользователе
 */
@Serializable
data class UserInfo(
    val name: String = "User",
    val role: String? = null,
    val timezone: String = "UTC",
    val language: String = "en"
)

/**
 * Предпочтения пользователя по стилю общения и работы
 */
@Serializable
data class UserPreferences(
    /**
     * Стиль общения: "direct" (прямой), "friendly" (дружелюбный), "formal" (формальный)
     */
    val communicationStyle: String = "direct",
    
    /**
     * Стиль кода: "kotlin_official", "google", "custom"
     */
    val codeStyle: String = "kotlin_official",
    
    /**
     * Уровень детализации ответов: "brief" (краткий), "medium" (средний), "detailed" (подробный)
     */
    val verbosity: String = "medium",
    
    /**
     * Использовать эмодзи в ответах
     */
    val emojiUsage: Boolean = true,
    
    /**
     * Предпочитаемый формат примеров кода: "inline" (в тексте), "separate" (отдельными блоками)
     */
    val codeExampleFormat: String = "separate",
    
    /**
     * Показывать объяснения к коду
     */
    val explainCode: Boolean = true
)

/**
 * Рабочий контекст пользователя
 */
@Serializable
data class WorkContext(
    /**
     * Основные проекты, над которыми работает пользователь
     */
    val primaryProjects: List<String> = emptyList(),
    
    /**
     * Технологический стек
     */
    val techStack: List<String> = emptyList(),
    
    /**
     * Любимые инструменты и IDE
     */
    val favoriteTools: List<String> = emptyList(),
    
    /**
     * Текущие задачи или фокус работы
     */
    val currentFocus: String? = null,
    
    /**
     * Уровень экспертизы: "beginner", "intermediate", "advanced", "expert"
     */
    val expertiseLevel: String = "intermediate"
)

/**
 * Привычки и режим работы пользователя
 */
@Serializable
data class UserHabits(
    /**
     * Рабочие часы в формате "HH:MM-HH:MM" (например, "10:00-19:00")
     */
    val workHours: String? = null,
    
    /**
     * Напоминать о перерывах
     */
    val breakReminders: Boolean = false,
    
    /**
     * Стиль управления задачами: "agile", "waterfall", "kanban", "custom"
     */
    val taskManagementStyle: String = "agile",
    
    /**
     * Предпочитаемая длина сессий работы (в минутах)
     */
    val preferredSessionLength: Int? = null,
    
    /**
     * Предпочитаемое время для сложных задач: "morning", "afternoon", "evening", "night"
     */
    val peakProductivityTime: String? = null
)

/**
 * Вспомогательные функции для работы с профилем
 */
object UserProfileUtils {
    /**
     * Создает краткое описание пользователя для системного промпта
     */
    fun UserProfile.toSystemPromptContext(): String {
        val parts = mutableListOf<String>()
        
        // Базовая информация
        if (user.name != "User") {
            parts.add("User's name: ${user.name}")
        }
        if (user.role != null) {
            parts.add("Role: ${user.role}")
        }
        if (user.language != "en") {
            parts.add("Preferred language: ${user.language}")
        }
        
        // Предпочтения
        parts.add("Communication style: ${preferences.communicationStyle}")
        parts.add("Response verbosity: ${preferences.verbosity}")
        
        // Рабочий контекст
        if (workContext.primaryProjects.isNotEmpty()) {
            parts.add("Primary projects: ${workContext.primaryProjects.joinToString(", ")}")
        }
        if (workContext.techStack.isNotEmpty()) {
            parts.add("Tech stack: ${workContext.techStack.joinToString(", ")}")
        }
        if (workContext.currentFocus != null) {
            parts.add("Current focus: ${workContext.currentFocus}")
        }
        
        // Кастомные инструкции
        if (customInstructions.isNotEmpty()) {
            parts.add("\nCustom instructions:")
            customInstructions.forEach { parts.add("- $it") }
        }
        
        return parts.joinToString("\n")
    }
}

