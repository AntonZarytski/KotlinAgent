package com.claude.agent.llm

import com.claude.agent.config.OutputFormat
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
    const val DEFAULT_MODE = "Ты — универсальный помощник. Отвечай на вопрос пользователя в зависимости от заданных параметров."

    /**
     * Промпт для режима сбора уточняющих данных (spec mode).
     */
    const val SPEC_MODE = """Ты — универсальный агент по сбору требований и контекста для задач пользователя.

Твоя цель:
1) Понять, какой результат нужен пользователю.
2) Задать минимум нужных вопросов.
3) Когда информации достаточно — перестать спрашивать и выдать итоговый результат.

Работай по шагам:

1. Уточни цель
- Кратко выясни: что именно пользователь хочет получить в итоге (подбор товара/услуги, план, ТЗ, список шагов, чек-лист, рекомендации и т.п.).
- Для кого это делается и в какой ситуации будет использоваться.

2. Собери ключевой контекст
Задавай по 1–3 коротких вопроса за сообщение, чтобы уточнить:
- Контекст: кто пользователь, для кого это, где/как это будет использоваться.
- Ограничения: бюджет, сроки, уровень опыта, технические или организационные ограничения.
- Предпочтения: что важнее (цена, качество, скорость, простота, бренд и т.д.).
- Исходные данные: есть ли уже варианты, ссылки, тексты, материалы.

3. Формат результата
- Спроси, в каком виде удобнее получить результат: список, план по шагам, ТЗ, чек-лист, краткое резюме и т.п.
- Запомни формат и используй его в финальном ответе.

4. Финальный результат
- После каждого ответа пользователя оцени, достаточно ли информации.
- Если достаточно — НЕ задавай новых вопросов.
- Начни с фразы вроде: "Готово, вот результат:".
- Выдай структурированный, конкретный и полезный итог в выбранном формате.

Всегда отвечай по-русски, если пользователь пишет по-русски."""

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
     * Генерирует системный промпт для LLM API.
     *
     * @param outputFormat Формат вывода ('default', 'json', 'xml')
     * @param specMode Режим сбора уточняющих данных (true/false)
     * @param llmType Тип LLM модели ('claude' или 'qwen') для оптимизации промпта
     * @return Склеенный системный промпт
     */
    fun getSystemPrompt(
        outputFormat: String,
        enabledTools: List<String>,
        specMode: Boolean = false,
        isRagEnabled: Boolean,
        llmType: String = "claude"
    ): String {
        // Получаем провайдеры для данного типа LLM
        val systemPromptProvider = PromptProviderFactory.createSystemPromptProvider(llmType)
        val toolDescriptionProvider = PromptProviderFactory.createToolDescriptionProvider(llmType)
        
        // Получаем текущее время с часовым поясом
        val currentTime = ZonedDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        val formattedTime = currentTime.format(formatter)

        // Выбираем базовый промпт через провайдер
        val baseModePrompt = if (specMode) {
            systemPromptProvider.getSpecModePrompt()
        } else {
            systemPromptProvider.getDefaultModePrompt()
        }

        // Добавляем текущее время
        var basePrompt = """Текущее время: $formattedTime

$baseModePrompt"""

        // Добавляем описания инструментов
        if (enabledTools.isNotEmpty()) {
            basePrompt += toolDescriptionProvider.getToolsSectionHeader()

            for (toolName in enabledTools) {
                val description = toolDescriptionProvider.getToolDescription(toolName)
                basePrompt += "\n$description"
            }

            // Добавляем правила использования инструментов
            basePrompt += systemPromptProvider.getToolUsageRules()
        }

        // Добавляем инструкции по RAG, если включен
        if (isRagEnabled) {
            basePrompt += systemPromptProvider.getRagInstructions()
        }

        // Добавляем инструкции по формату вывода
        val formatPrompt = when (outputFormat.lowercase()) {
            "json" -> FORMAT_JSON
            "xml" -> FORMAT_XML
            else -> ""
        }

        return if (formatPrompt.isNotEmpty()) {
            "$basePrompt\n\n$formatPrompt"
        } else {
            basePrompt
        }
    }
}

