package com.claude.agent.llm

import com.claude.agent.config.OutputFormat
import com.claude.agent.llm.mcp.ACTION_PLANNER
import com.claude.agent.llm.mcp.AIR_TICKETS
import com.claude.agent.llm.mcp.ANDROID_STUDIO_MCP
import com.claude.agent.llm.mcp.CHAT_SUMMARY
import com.claude.agent.llm.mcp.GIT_REPOSITORY
import com.claude.agent.llm.mcp.PROJECT_HELP
import com.claude.agent.llm.mcp.REMINDER
import com.claude.agent.llm.mcp.SOLAR
import com.claude.agent.llm.mcp.TOOL_CHAIN
import com.claude.agent.llm.mcp.WEATHER
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Промпты для различных форматов вывода Claude API.
 *
 * Аналог prompts.py из Python-версии.
 * Использует Kotlin object и константы для организации промптов.
 */

object SystemPrompts {

    /**
     * Базовый промпт для обычного режима.
     */
    const val DEFAULT_MODE = """Ты — универсальный помощник.

ВАЖНО:
- ВСЕГДА предоставляй текстовый ответ после использования инструментов
- НЕ завершай работу молча - объясни что сделал
- Если используешь инструменты - опиши результаты понятным языком
- Отвечай кратко и по делу

РАБОТА С ИНСТРУМЕНТАМИ:
- Можешь вызывать несколько инструментов последовательно
- Используй результаты предыдущих инструментов для следующих вызовов
- Например: сначала найди файл (git_repository), потом прочитай его (git_repository)
- Или: получи погоду (weather), потом создай напоминание (reminder) на основе прогноза
- Результаты всех инструментов доступны тебе в контексте
- Планируй последовательность действий для достижения цели"""

    /**
     * Промпт для режима сбора уточняющих данных (spec mode).
     * Сокращенная версия для экономии токенов.
     */
    const val SPEC_MODE = """Ты — агент по сбору требований.

Цель: понять задачу, задать минимум вопросов, выдать результат.

Шаги:
1. Уточни цель и формат результата
2. Собери контекст: ограничения, предпочтения, исходные данные
3. Когда достаточно информации — выдай структурированный результат

Отвечай по-русски."""

    /**
     * Инструкции для формата JSON.
     */
    const val FORMAT_JSON = """
ФОРМАТ ОТВЕТА: Отвечай строго в формате JSON.

Структура ответа:
``` json
{
  "answer": "краткий ответ на вопрос пользователя одной-двумя фразами",
  "steps": [
    "шаг 1 объяснения",
    "шаг 2 объяснения",
    "шаг 3 объяснения"
  ]
}
```

Требования:
- Никакого текста до или после JSON.
- Никаких комментариев, пояснений, Markdown.
- Только один корректный JSON-объект."""

    /**
     * Инструкции для формата XML.
     */
    const val FORMAT_XML = """
ФОРМАТ ОТВЕТА: Отвечай строго в формате XML.

Структура ответа:
``` xml
<response>
  <answer>краткий ответ на вопрос пользователя одной-двумя фразами</answer>
  <steps>
    <step>шаг 1 объяснения</step>
    <step>шаг 2 объяснения</step>
    <step>шаг 3 объяснения</step>
  </steps>
</response>
```

Требования:
- Никакого текста до или после XML.
- Никаких комментариев, пояснений, Markdown.
- Только один корректный XML-документ.
- Используй правильную XML структуру с закрывающими тегами."""

    /**
     * Генерирует системный промпт для Claude API.
     *
     * @param outputFormat Формат вывода ('default', 'json', 'xml')
     * @param specMode Режим сбора уточняющих данных (true/false)
     * @return Склеенный системный промпт
     */
    fun getSystemPrompt(
        outputFormat: String,
        enabledTools: List<String>,
        specMode: Boolean = false,
        isRagEnabled: Boolean,
        fileContext: String? = null
    ): String {
        // Выбираем базовый промпт
        var basePrompt = if (specMode) SPEC_MODE else DEFAULT_MODE

        // ОПТИМИЗАЦИЯ: Добавляем время только если нужны time-sensitive tools
        val timeNeededTools = listOf(REMINDER, WEATHER, SOLAR)
        val needsTime = enabledTools.any { it in timeNeededTools }

        if (needsTime) {
            val currentTime = ZonedDateTime.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
            val formattedTime = currentTime.format(formatter)
            basePrompt = "Текущее время: $formattedTime\n\n$basePrompt"
        }

        // Минималистичные описания tools - детали в JSON Schema
        if (enabledTools.isNotEmpty()) {
            val toolsList = enabledTools.joinToString(", ")
            basePrompt += "\n\nДоступны инструменты: $toolsList (детали в описаниях инструментов)"
        }

        if (isRagEnabled) {
            val ragPrompt = """

RAG: Используй контекст из документации. ВСЕГДА указывай источники в формате "📚 Источники: [документ]"
            """.trimIndent()
            basePrompt += "\n$ragPrompt"
        }

        if (fileContext != null && fileContext.isNotBlank()) {
            val fileContextPrompt = """

ВЫБРАННЫЕ ФАЙЛЫ (используй как основной контекст):
$fileContext
            """.trimIndent()
            basePrompt += "\n$fileContextPrompt"
        }

        // Выбираем инструкции по формату
        val formatInstructions = when (outputFormat) {
            OutputFormat.JSON -> FORMAT_JSON
            OutputFormat.XML -> FORMAT_XML
            else -> ""
        }

        // Склеиваем промпты
        return if (formatInstructions.isNotBlank()) {
            "$basePrompt\n$formatInstructions"
        } else {
            basePrompt
        }
    }

    /**
     * Возвращает чистое сообщение пользователя (без дополнительных инструкций).
     */
    fun getUserMessage(userMessage: String): String {
        return userMessage.trim()
    }
}