package com.claude.agent.cli

import com.claude.agent.config.AppConfig
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Валидатор опций CLI
 * 
 * Отвечает за проверку корректности опций перед выполнением ревью
 */
class CLIOptionsValidator {
    private val logger = LoggerFactory.getLogger(CLIOptionsValidator::class.java)

    /**
     * Валидирует опции ревью
     * 
     * @param options Опции для проверки
     * @return true если опции валидны, false иначе
     */
    fun validate(options: ReviewOptions): ValidationResult {
        val errors = mutableListOf<String>()

        // Проверяем обязательные параметры
        if (options.branch.isBlank()) {
            errors.add("--branch is required")
        }

        // Проверяем API ключ
        if (AppConfig.anthropicApiKey.isBlank()) {
            errors.add("ANTHROPIC_API_KEY environment variable is not set")
        }

        // Проверяем существование репозитория
        val repoDir = File(options.repoPath)
        if (!repoDir.exists()) {
            errors.add("Repository directory not found: ${options.repoPath}")
        } else if (!File(repoDir, ".git").exists()) {
            errors.add("Git repository not found at ${options.repoPath}")
        }

        // Проверяем RAG database если RAG включен
        if (options.enableRag) {
            val ragDbFile = File(options.ragDbPath)
            if (!ragDbFile.exists()) {
                logger.warn("⚠️ RAG enabled but database not found: ${options.ragDbPath}")
                logger.warn("   RAG will be disabled")
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(errors)
        }
    }

    /**
     * Логирует ошибки валидации
     */
    fun logErrors(errors: List<String>) {
        logger.error("❌ Validation failed:")
        errors.forEach { error ->
            logger.error("   - $error")
        }
    }
}

/**
 * Результат валидации
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val errors: List<String>) : ValidationResult()
}

