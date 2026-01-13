package com.claude.agent.common.models

import kotlinx.serialization.Serializable

/**
 * Информация о Pull Request для ревью
 */
@Serializable
data class PRInfo(
    val branch: String,
    val targetBranch: String = "main",
    val title: String? = null,
    val description: String? = null
)

/**
 * Результат автоматического ревью Pull Request
 */
@Serializable
data class PRReview(
    val summary: String,
    val overallAssessment: String,
    val comments: List<ReviewComment>,
    val suggestions: List<String>,
    val complianceChecks: List<ComplianceCheck>
)

/**
 * Комментарий к коду в рамках ревью
 */
@Serializable
data class ReviewComment(
    val file: String,
    val line: Int? = null,
    val severity: Severity,
    val message: String,
    val suggestion: String? = null
)

/**
 * Уровень важности комментария
 */
@Serializable
enum class Severity {
    CRITICAL,   // Критическая проблема, требует исправления
    WARNING,    // Предупреждение, рекомендуется исправить
    INFO,       // Информационное сообщение
    SUGGESTION  // Предложение по улучшению
}

/**
 * Проверка соответствия стандартам и best practices
 */
@Serializable
data class ComplianceCheck(
    val category: String,
    val status: String,  // "PASS" или "FAIL"
    val details: String
)

