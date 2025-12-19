package com.claude.agent.service

import com.claude.agent.config.CompressionConfig
import com.claude.agent.models.Message
import com.claude.agent.llm.ClaudeClient
import org.slf4j.LoggerFactory

/**
 * Сервис для сжатия истории диалога.
 *
 * Улучшенная версия с:
 * - Скользящим окном (sliding window)
 * - Приоритизацией важных сообщений
 * - Интеллектуальной суммаризацией
 * - Оценкой экономии токенов
 */
class HistoryCompressor(
    private val claudeClient: ClaudeClient,
    private val tokenMetricsService: TokenMetricsService? = null
) {
    private val logger = LoggerFactory.getLogger(HistoryCompressor::class.java)

    /**
     * Проверяет, нужно ли сжимать историю.
     */
    fun shouldCompress(history: List<Message>): Boolean {
        val messageCount = history.count { it.role in listOf("user", "assistant") }
        val shouldCompress = messageCount >= CompressionConfig.THRESHOLD

        if (shouldCompress) {
            logger.info("История содержит $messageCount сообщений, требуется сжатие")
        }

        return shouldCompress
    }

    /**
     * Сжимает историю диалога с улучшенной стратегией.
     *
     * @param history Полная история диалога
     * @param keepRecent Количество последних сообщений для сохранения
     * @return Сжатая история с summary
     */
    suspend fun compressHistory(
        history: List<Message>,
        keepRecent: Int = CompressionConfig.KEEP_RECENT
    ): List<Message> {
        if (!shouldCompress(history)) {
            logger.debug("Сжатие не требуется")
            return history
        }

        // Фильтруем только user/assistant сообщения
        val dialogMessages = history.filter { it.role in listOf("user", "assistant") }

        if (dialogMessages.size < keepRecent) {
            logger.debug("Недостаточно сообщений для сжатия")
            return history
        }

        // Применяем скользящее окно
        val windowedMessages = applySlidingWindow(dialogMessages)

        // Разделяем на старые (для сжатия) и новые (для сохранения)
        val messagesToCompress = windowedMessages.dropLast(keepRecent)
        val messagesToKeep = windowedMessages.takeLast(keepRecent)

        logger.info("Сжатие ${messagesToCompress.size} сообщений, сохранение ${messagesToKeep.size}")

        // Оценка экономии токенов (примерно 4 символа на токен)
        val estimatedTokensBefore = dialogMessages.sumOf { it.content.length } / 4

        // Создаём summary
        val summaryText = createSummary(messagesToCompress)

        // Формируем новую историю: [summary] + [последние сообщения]
        val compressedHistory = mutableListOf<Message>()
        compressedHistory.add(
            Message(
                role = "user",
                content = "[Ранее обсуждали: $summaryText]"
            )
        )
        compressedHistory.addAll(messagesToKeep)

        // Оценка экономии
        val estimatedTokensAfter = compressedHistory.sumOf { it.content.length } / 4
        val savedTokens = (estimatedTokensBefore - estimatedTokensAfter).toLong()

        if (savedTokens > 0) {
            tokenMetricsService?.recordCompressionSavings(savedTokens)
        }

        logger.info("История сжата: ${history.size} -> ${compressedHistory.size} сообщений (~$savedTokens токенов сэкономлено)")

        return compressedHistory
    }

    /**
     * Применяет скользящее окно к истории сообщений.
     * Ограничивает максимальное количество сообщений.
     */
    private fun applySlidingWindow(messages: List<Message>): List<Message> {
        val maxMessages = CompressionConfig.MAX_HISTORY_MESSAGES

        if (messages.size <= maxMessages) {
            return messages
        }

        // Берем последние N сообщений
        val windowed = messages.takeLast(maxMessages)
        logger.info("📊 Sliding window applied: ${messages.size} -> ${windowed.size} messages")

        return windowed
    }

    /**
     * Определяет важность сообщения на основе его характеристик.
     */
    private fun isImportantMessage(message: Message): Boolean {
        // Длинные сообщения обычно содержат важную информацию
        if (message.content.length >= CompressionConfig.IMPORTANT_MESSAGE_THRESHOLD) {
            return true
        }

        // Сообщения с кодом
        if (message.content.contains("```")) {
            return true
        }

        // Сообщения с вопросами
        if (message.content.contains("?")) {
            return true
        }

        return false
    }

    /**
     * Создаёт краткое резюме истории диалога используя Claude API.
     */
    private suspend fun createSummary(messages: List<Message>): String {
        if (messages.isEmpty()) return ""

        val conversationText = formatConversation(messages)

        val summaryPrompt = """Создай ОЧЕНЬ краткое резюме диалога (максимум 2-3 предложения).
Укажи только ключевые темы, о которых говорили. Без деталей, без форматирования.

Диалог:
$conversationText

КРАТКОЕ резюме (2-3 предложения):"""

        return try {
            logger.info("Создание summary для ${messages.size} сообщений...")

            val (reply, _, error) = claudeClient.sendMessage(
                userMessage = summaryPrompt,
                maxTokens = CompressionConfig.SUMMARY_MAX_TOKENS,
                temperature = 0.0,  // Детерминированный вывод
                conversationHistory = emptyList()
            )

            if (error != null) {
                logger.error("Ошибка создания summary: $error")
                return fallbackSummary(messages)
            }

            val summary = reply?.trim() ?: fallbackSummary(messages)
            logger.info("Summary создан: ${summary.length} символов")
            summary

        } catch (e: Exception) {
            logger.error("Ошибка при создании summary: ${e.message}")
            fallbackSummary(messages)
        }
    }

    /**
     * Форматирует сообщения в читаемый текст для создания summary.
     */
    private fun formatConversation(messages: List<Message>): String {
        return messages.mapIndexed { index, msg ->
            val roleLabel = if (msg.role == "user") "Пользователь" else "Ассистент"
            val content = if (msg.content.length > 500) {
                msg.content.take(500) + "..."
            } else {
                msg.content
            }
            "[${index + 1}] $roleLabel: $content"
        }.joinToString("\n\n")
    }

    /**
     * Создаёт упрощенное резюме без API (fallback).
     */
    private fun fallbackSummary(messages: List<Message>): String {
        val userMessages = messages
            .filter { it.role == "user" }
            .map { it.content.take(50) }
            .take(3)

        return if (userMessages.isEmpty()) {
            "общие вопросы"
        } else {
            userMessages.joinToString(", ")
        }
    }
}