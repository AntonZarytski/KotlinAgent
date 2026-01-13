package com.claude.agent.service.review

import com.claude.agent.common.models.*
import org.slf4j.LoggerFactory

/**
 * Парсер ответов Claude для структурированного ревью
 * 
 * Отвечает за преобразование текстового ответа Claude в структурированный объект PRReview
 */
class ReviewResponseParser {
    private val logger = LoggerFactory.getLogger(ReviewResponseParser::class.java)

    /**
     * Парсит ответ Claude и формирует структурированное ревью
     */
    fun parse(reviewText: String): PRReview {
        val lines = reviewText.lines()
        
        var summary = ""
        var overallAssessment = ""
        val comments = mutableListOf<ReviewComment>()
        val suggestions = mutableListOf<String>()
        val complianceChecks = mutableListOf<ComplianceCheck>()

        var currentSection = ""
        val summaryLines = mutableListOf<String>()
        val assessmentLines = mutableListOf<String>()
        
        for (line in lines) {
            when {
                // Поддерживаем оба формата: ## и ###
                line.startsWith("## 1. SUMMARY") || line.startsWith("### 1. SUMMARY") -> currentSection = "SUMMARY"
                line.startsWith("## 2. OVERALL ASSESSMENT") || line.startsWith("### 2. OVERALL ASSESSMENT") -> currentSection = "ASSESSMENT"
                line.startsWith("## 3. DETAILED COMMENTS") || line.startsWith("### 3. DETAILED COMMENTS") -> currentSection = "COMMENTS"
                line.startsWith("## 4. SUGGESTIONS") || line.startsWith("### 4. SUGGESTIONS") -> currentSection = "SUGGESTIONS"
                line.startsWith("## 5. COMPLIANCE") || line.startsWith("### 5. COMPLIANCE") -> currentSection = "COMPLIANCE"

                line.startsWith("###") || line.startsWith("##") -> {
                    // Новая секция - сбрасываем
                }

                else -> when (currentSection) {
                    "SUMMARY" -> if (line.isNotBlank()) summaryLines.add(line)
                    "ASSESSMENT" -> if (line.isNotBlank()) assessmentLines.add(line)
                    "COMMENTS" -> parseComment(line)?.let { comments.add(it) }
                    "SUGGESTIONS" -> if (line.startsWith("-") || line.startsWith("*")) {
                        suggestions.add(line.removePrefix("-").removePrefix("*").trim())
                    }
                    "COMPLIANCE" -> parseComplianceCheck(line)?.let { complianceChecks.add(it) }
                }
            }
        }

        summary = summaryLines.joinToString(" ").trim()
        overallAssessment = assessmentLines.joinToString(" ").trim()

        return PRReview(
            summary = summary.ifBlank { "No summary provided" },
            overallAssessment = overallAssessment.ifBlank { "No assessment provided" },
            comments = comments,
            suggestions = suggestions,
            complianceChecks = complianceChecks
        )
    }

    /**
     * Парсит строку комментария
     * Формат: - **[SEVERITY]** `file.kt:line` - Description
     */
    private fun parseComment(line: String): ReviewComment? {
        if (!line.trim().startsWith("-") && !line.trim().startsWith("*")) {
            return null
        }

        val trimmed = line.trim().removePrefix("-").removePrefix("*").trim()
        
        // Парсим severity
        val severityRegex = """^\*\*\[(CRITICAL|WARNING|INFO|SUGGESTION)]\*\*""".toRegex()
        val severityMatch = severityRegex.find(trimmed) ?: return null
        val severityStr = severityMatch.groupValues[1]
        val severity = try {
            Severity.valueOf(severityStr)
        } catch (e: Exception) {
            logger.warn("Unknown severity: $severityStr, defaulting to INFO")
            Severity.INFO
        }

        val afterSeverity = trimmed.substring(severityMatch.range.last + 1).trim()

        // Парсим file:line
        val fileRegex = """`([^`]+?):?(\d+)?`""".toRegex()
        val fileMatch = fileRegex.find(afterSeverity)
        
        val file = fileMatch?.groupValues?.get(1) ?: "unknown"
        val line = fileMatch?.groupValues?.get(2)?.toIntOrNull()
        
        // Остальное - это message
        val message = if (fileMatch != null) {
            afterSeverity.substring(fileMatch.range.last + 1).trim().removePrefix("-").trim()
        } else {
            afterSeverity
        }

        return ReviewComment(
            file = file,
            line = line,
            severity = severity,
            message = message,
            suggestion = null
        )
    }

    /**
     * Парсит проверку соответствия стандартам
     * Формат: [✓/✗] Category: Details
     */
    private fun parseComplianceCheck(line: String): ComplianceCheck? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("-")) {
            return null
        }

        val checkRegex = """[\\-\\*]?\\s*\\[([✓✗×X])]\\s*([^:]+):\\s*(.+)""".toRegex()
        val match = checkRegex.find(trimmed) ?: return null

        val status = if (match.groupValues[1] in listOf("✓", "X")) "PASS" else "FAIL"
        val category = match.groupValues[2].trim()
        val details = match.groupValues[3].trim()

        return ComplianceCheck(
            category = category,
            status = status,
            details = details
        )
    }
}

