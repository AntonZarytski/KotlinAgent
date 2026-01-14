package com.claude.agent.service

import com.claude.agent.database.TicketRepository
import com.claude.agent.llm.ClaudeClient
import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.models.*
import com.claude.agent.common.database.normalizeToRange
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис поддержки пользователей с AI-ассистентом
 *
 * Интегрирует:
 * - RAG для поиска в документации
 * - MCP инструмент support_crm для доступа к данным пользователей и тикетов
 * - Claude API для генерации персонализированных ответов
 * - Автоматическое создание тикетов
 */
class SupportService(
    private val claudeClient: ClaudeClient,
    private val mcpTools: MCPTools,
    private val ragService: RagService?,
    private val ollamaEmbeddingClient: OllamaEmbeddingClient?,
    private val ticketRepository: TicketRepository
) {
    private val logger = LoggerFactory.getLogger(SupportService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Обрабатывает вопрос пользователя к службе поддержки
     * 
     * @param request Запрос с вопросом и контекстом
     * @return Персонализированный ответ с источниками
     */
    suspend fun askSupport(request: SupportRequest): SupportResponse {
        logger.info("Processing support request: question='${request.question.take(50)}...', sessionId=${request.sessionId}, ticketId=${request.ticketId}")

        try {
            // 1. Получаем контекст тикета через MCP
            val ticketContext = request.ticketId?.let { getTicketContext(it) }
            
            // 2. Получаем релевантную документацию через RAG
            val ragResults = if (request.useRag && ragService != null && ollamaEmbeddingClient != null) {
                searchDocumentation(request.question, request.ragTopK, request.ragMinSimilarity)
            } else {
                emptyList()
            }
            
            // 3. Формируем контекстный промпт
            val contextPrompt = buildContextPrompt(
                question = request.question,
                ticketContext = ticketContext,
                ragResults = ragResults
            )
            
            // 4. Получаем ответ от Claude
            val claudeResponse = claudeClient.sendMessage(
                userMessage = contextPrompt,
                sessionId = request.sessionId ?: "support-${System.currentTimeMillis()}",
                enabledTools = listOf("support_crm"),
                useRag = false, // RAG уже применен вручную
                maxTokens = 2048,
                temperature = 0.7
            )
            
            val answer = claudeResponse.reply ?: "Извините, не удалось сгенерировать ответ"
            
            // 5. Формируем ответ
            return SupportResponse(
                answer = answer,
                sources = ragResults.map {
                    DocumentSource(
                        docId = it.docId,
                        chunkIndex = it.chunkIndex,
                        text = it.text,
                        similarity = it.similarity
                    )
                },
                ticketContext = ticketContext,
                confidence = calculateConfidence(ragResults),
                suggestedActions = extractSuggestedActions(answer, ticketContext)
            )
            
        } catch (e: Exception) {
            logger.error("Error processing support request: ${e.message}", e)
            return SupportResponse(
                answer = "Извините, произошла ошибка при обработке вашего запроса. Пожалуйста, попробуйте позже или обратитесь к администратору.",
                sources = emptyList(),
                confidence = 0.0
            )
        }
    }
    
    /**
     * Получает информацию о тикете через MCP
     */
    private suspend fun getTicketContext(ticketId: String): SupportTicket? {
        return try {
            val result = mcpTools.callLocalTool(
                toolName = "support_crm",
                arguments = buildJsonObject {
                    put("action", "get_ticket")
                    put("ticket_id", ticketId)
                }
            )
            
            if (result.contains("error")) {
                logger.warn("Failed to get ticket context: $result")
                null
            } else {
                json.decodeFromString<SupportTicket>(result)
            }
        } catch (e: Exception) {
            logger.error("Error getting ticket context: ${e.message}", e)
            null
        }
    }
    
    /**
     * Ищет релевантную документацию через RAG
     */
    private suspend fun searchDocumentation(
        query: String,
        topK: Int,
        minSimilarity: Double
    ): List<RagService.SearchResult> {
        return try {
            if (ragService == null || ollamaEmbeddingClient == null) {
                logger.warn("RAG services not available")
                return emptyList()
            }
            
            // Генерируем embedding для запроса
            val queryEmbedding = ollamaEmbeddingClient.embed(query)
            val normalizedEmbedding = normalizeToRange(queryEmbedding)
            
            // Ищем релевантные документы
            ragService.search(
                queryEmbedding = normalizedEmbedding,
                topK = topK,
                minSimilarity = minSimilarity
            )
        } catch (e: Exception) {
            logger.error("Error searching documentation: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Формирует контекстный промпт для Claude
     */
    private fun buildContextPrompt(
        question: String,
        ticketContext: SupportTicket?,
        ragResults: List<RagService.SearchResult>
    ): String {
        return buildString {
            appendLine("Ты - AI-ассистент службы поддержки KotlinAgent.")
            appendLine("Твоя задача - помочь пользователю решить его проблему, используя доступную информацию.")
            appendLine()

            // Контекст тикета
            if (ticketContext != null) {
                appendLine("## Информация о тикете:")
                appendLine("- ID: ${ticketContext.id}")
                appendLine("- Заголовок: ${ticketContext.title}")
                appendLine("- Описание: ${ticketContext.description}")
                appendLine("- Статус: ${ticketContext.status}")
                appendLine("- Приоритет: ${ticketContext.priority}")
                appendLine("- Категория: ${ticketContext.category ?: "не указана"}")
                appendLine("- Создан: ${ticketContext.createdAt}")
                appendLine("- Обновлен: ${ticketContext.updatedAt}")
                if (ticketContext.tags.isNotEmpty()) {
                    appendLine("- Теги: ${ticketContext.tags.joinToString(", ")}")
                }
                appendLine()
            }

            // Релевантная документация
            if (ragResults.isNotEmpty()) {
                appendLine("## Релевантная документация:")
                ragResults.forEachIndexed { index, result ->
                    appendLine()
                    appendLine("### Документ ${index + 1}: ${result.docId}")
                    appendLine("Релевантность: ${(result.similarity * 100).toInt()}%")
                    appendLine()
                    appendLine(result.text.trim())
                    appendLine()
                }
                appendLine()
            }

            // Вопрос пользователя
            appendLine("## Вопрос пользователя:")
            appendLine(question)
            appendLine()

            // Инструкции для ассистента
            appendLine("## Инструкции:")
            appendLine("1. Проанализируй вопрос пользователя и предоставленный контекст")
            appendLine("2. Дай четкий, понятный и полезный ответ")
            appendLine("3. Если есть релевантная документация, используй её для ответа")
            appendLine("4. Если проблема связана с тикетом, учитывай его статус и историю")
            appendLine("5. Предложи конкретные шаги для решения проблемы")
            appendLine("6. Если нужна дополнительная информация, попроси её у пользователя")
            appendLine("7. Будь вежливым и профессиональным")
            appendLine("8. НЕ предлагай автоматически изменить статус тикета - пользователь сделает это сам через UI")
        }
    }

    /**
     * Вычисляет уверенность в ответе на основе качества RAG результатов
     */
    private fun calculateConfidence(ragResults: List<RagService.SearchResult>): Double {
        if (ragResults.isEmpty()) {
            return 0.5 // Средняя уверенность без документации
        }

        // Средняя similarity топ-3 результатов
        val topResults = ragResults.take(3)
        val avgSimilarity = topResults.map { it.similarity }.average()

        return avgSimilarity
    }

    /**
     * Извлекает предложенные действия из ответа
     */
    private fun extractSuggestedActions(
        answer: String,
        ticketContext: SupportTicket?
    ): List<String> {
        val actions = mutableListOf<String>()

        // Простая эвристика для извлечения действий
        val lowerAnswer = answer.lowercase()

        if (lowerAnswer.contains("перезапуст") || lowerAnswer.contains("restart")) {
            actions.add("Перезапустить сервер")
        }

        if (lowerAnswer.contains("проверь") || lowerAnswer.contains("check")) {
            actions.add("Проверить конфигурацию")
        }

        if (lowerAnswer.contains("обнов") || lowerAnswer.contains("update")) {
            actions.add("Обновить зависимости")
        }

        if (lowerAnswer.contains("лог") || lowerAnswer.contains("log")) {
            actions.add("Проверить логи")
        }

        // УДАЛЕНО: Автоматическое предложение изменить статус на RESOLVED
        // Статус тикета изменяется только по явному подтверждению пользователя
        // или через кнопки в UI

        return actions
    }

    /**
     * Анализирует вопрос пользователя и определяет, нужно ли создать тикет
     */
    suspend fun analyzeProblem(question: String, sessionId: String): ProblemAnalysis {
        logger.info("Analyzing problem for potential ticket creation")

        val analysisPrompt = """
            Проанализируй следующий вопрос пользователя и определи, требуется ли создание тикета поддержки.

            Вопрос: "$question"

            ⚠️ ВАЖНО: Тикет создается ТОЛЬКО для конкретных проблем и неисправностей!

            ✅ Тикет НУЖЕН если пользователь:
            - Явно сообщает что что-то "не работает", "не получается", "выдает ошибку", "сломалось"
            - Описывает техническую проблему с конкретным проектом или функциональностью
            - Сообщает о баге, сбое, неожиданном поведении системы
            - Просит помощь в исправлении неисправности
            - Указывает на проблему, требующую отслеживания и решения

            Примеры: "У меня не запускается сервер", "Выдает ошибку при компиляции",
                     "Не работает авторизация", "Приложение крашится при запуске"

            ❌ Тикет НЕ НУЖЕН если это:
            - Общий вопрос типа "Как сделать X?", "Что такое Y?", "Где найти Z?"
            - Запрос документации, примеров кода, инструкций
            - Консультационный вопрос о том, как что-то реализовать
            - Благодарность, обратная связь, комментарий
            - Просьба объяснить концепцию или технологию
            - Вопрос о лучших практиках или рекомендациях

            Примеры: "Как создать REST API?", "Объясни что такое Kotlin coroutines",
                     "Покажи пример использования", "Спасибо за помощь"

            Ответь в формате JSON:
            {
                "needsTicket": true/false,
                "title": "Краткое описание проблемы (если нужен тикет)",
                "description": "Детальное описание проблемы",
                "priority": "LOW/MEDIUM/HIGH/CRITICAL",
                "category": "authentication/api/configuration/bug/feature_request/other",
                "tags": ["тег1", "тег2"],
                "reasoning": "Почему нужен или не нужен тикет"
            }
        """.trimIndent()

        try {
            val response = claudeClient.sendMessage(
                userMessage = analysisPrompt,
                sessionId = sessionId,
                enabledTools = emptyList(),
                useRag = false,
                maxTokens = 1024,
                temperature = 0.3
            )

            val responseText = response.reply ?: return ProblemAnalysis(needsTicket = false)

            // Извлекаем JSON из ответа
            val jsonMatch = Regex("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}").find(responseText)
            val jsonText = jsonMatch?.value ?: return ProblemAnalysis(needsTicket = false)

            val analysisJson = json.parseToJsonElement(jsonText).jsonObject

            val needsTicket = analysisJson["needsTicket"]?.jsonPrimitive?.booleanOrNull ?: false

            if (!needsTicket) {
                return ProblemAnalysis(
                    needsTicket = false,
                    reasoning = analysisJson["reasoning"]?.jsonPrimitive?.contentOrNull
                )
            }

            return ProblemAnalysis(
                needsTicket = true,
                title = analysisJson["title"]?.jsonPrimitive?.contentOrNull,
                description = analysisJson["description"]?.jsonPrimitive?.contentOrNull,
                priority = analysisJson["priority"]?.jsonPrimitive?.contentOrNull?.let {
                    try { TicketPriority.valueOf(it) } catch (e: Exception) { TicketPriority.MEDIUM }
                },
                category = analysisJson["category"]?.jsonPrimitive?.contentOrNull,
                tags = analysisJson["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                reasoning = analysisJson["reasoning"]?.jsonPrimitive?.contentOrNull
            )

        } catch (e: Exception) {
            logger.error("Error analyzing problem: ${e.message}", e)
            return ProblemAnalysis(needsTicket = false, reasoning = "Error during analysis")
        }
    }

    /**
     * Автоматически создает тикет на основе анализа проблемы
     */
    fun createTicketFromAnalysis(
        analysis: ProblemAnalysis,
        sessionId: String,
        originalQuestion: String
    ): SupportTicket? {
        if (!analysis.needsTicket) {
            logger.info("Ticket creation not needed based on analysis")
            return null
        }

        val title = analysis.title ?: "Вопрос пользователя"
        val description = analysis.description ?: originalQuestion
        val priority = analysis.priority ?: TicketPriority.MEDIUM

        val request = CreateTicketRequest(
            sessionId = sessionId,
            title = title,
            description = description,
            priority = priority,
            category = analysis.category,
            tags = analysis.tags
        )

        val ticket = ticketRepository.createTicket(request, autoCreated = true)
        logger.info("Auto-created ticket: ${ticket.id} for session $sessionId")

        return ticket
    }
}
