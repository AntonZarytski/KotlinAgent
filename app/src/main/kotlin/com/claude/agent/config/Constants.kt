package com.claude.agent.config

/**
 * Константы приложения KotlinAgent.
 *
 * Аналог constants.py из Python-версии.
 * Все константы собраны в sealed interface для группировки.
 */

// === Настройки сервера ===
object ServerConfig {
    const val DEFAULT_PORT = 8000
    const val DEFAULT_HOST = "0.0.0.0"
    const val DEBUG_MODE = false
}

// === Настройки Claude API ===
object ClaudeConfig {
    const val MODEL = "claude-sonnet-4-20250514"
    const val MAX_TOKENS = 1024
}

// === Форматы вывода ===
object OutputFormat {
    const val DEFAULT = "default"
    const val JSON = "json"
    const val XML = "xml"

    val VALID_FORMATS = listOf(DEFAULT, JSON, XML)
}

// === HTTP коды ответов ===
object HttpStatus {
    const val OK = 200
    const val BAD_REQUEST = 400
    const val TOO_MANY_REQUESTS = 429
    const val INTERNAL_SERVER_ERROR = 500
    const val SERVICE_UNAVAILABLE = 503
}

// === Сообщения об ошибках ===
object ErrorMessages {
    const val EMPTY_MESSAGE = "Пустое сообщение"
    const val INVALID_API_KEY = "Неверный API ключ Claude"
    const val RATE_LIMIT = "Превышен лимит запросов к Claude API"
    const val CONNECTION = "Не удалось подключиться к Claude API"
    const val API_KEY_NOT_SET = "ANTHROPIC_API_KEY не установлен"
    const val API_KEY_NOT_FOUND = "ANTHROPIC_API_KEY не найден в переменных окружения!"
}

// === Лимиты ===
object Limits {
    const val MAX_MESSAGE_LOG_LENGTH = 200
    const val MAX_REPLY_LOG_LENGTH = 500
}

// === Настройки сжатия истории ===
object CompressionConfig {
    const val THRESHOLD = 10                  // Количество сообщений для сжатия
    const val KEEP_RECENT = 2                 // Сколько последних сохранять без сжатия
    const val SUMMARY_MAX_TOKENS = 150        // Максимум токенов для summary
}

// === Маркер конца результата для spec mode ===
const val SPEC_END_MARKER = "---END_RESULT---"
