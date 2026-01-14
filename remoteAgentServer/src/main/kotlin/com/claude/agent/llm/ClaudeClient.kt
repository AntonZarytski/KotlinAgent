package com.claude.agent.llm

import com.claude.agent.common.database.normalizeToRange
import com.claude.agent.config.AppConfig
import com.claude.agent.config.ClaudeConfig
import com.claude.agent.config.PromptCachingConfig
import com.claude.agent.config.ToolIterationConfig
import com.claude.agent.config.SPEC_END_MARKER
import com.claude.agent.llm.mcp.REMINDER
import com.claude.agent.models.Message
import com.claude.agent.models.TokenUsage
import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.service.WebSocketService
import com.claude.agent.service.WebSocketMessage
import com.claude.agent.service.TokenMetricsService
import com.claude.agent.service.ToolsFilterService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/**
 * Результат отправки сообщения Claude
 */
data class ClaudeResponse(
    val reply: String?,
    val usage: TokenUsage?,
    val error: String?,
    val intermediateMessages: List<Message> = emptyList(),
    val ticketCreatedViaMcp: Boolean = false  // Флаг, что тикет был создан через MCP
)

/**
 * Внутренний результат обработки ответа от Claude API
 */
private data class HandleResponseResult(
    val reply: String,
    val usage: TokenUsage,
    val intermediateMessages: List<Message>,
    val ticketCreatedViaMcp: Boolean = false
)

/**
 * Клиент для работы с Claude API (Anthropic).
 *
 * Улучшенная версия с поддержкой:
 * - Prompt Caching для экономии токенов
 * - Динамической фильтрации tools
 * - Оптимизации tool iterations
 * - Детального мониторинга метрик
 */
class ClaudeClient(
    private val httpClient: HttpClient,
    private val mcpTools: MCPTools,
    private val webSocketService: WebSocketService,
    private val tokenMetricsService: TokenMetricsService? = null,
    private val toolsFilterService: ToolsFilterService? = null,
    private val ragService: com.claude.agent.service.RagService? = null,
    private val ollamaEmbeddingClient: com.claude.agent.service.OllamaEmbeddingClient? = null
) {
    private val logger = LoggerFactory.getLogger(ClaudeClient::class.java)
    private val apiKey = AppConfig.anthropicApiKey
    private val apiUrl = "https://api.anthropic.com/v1/messages"

    companion object {
        private const val MAX_TOOL_ITERATIONS = 20  // Увеличено для сложных задач
        private const val LOOP_DETECTION_THRESHOLD = 7  // Порог для детекции зацикливания

        // Thread-local storage for accumulated tool results during a conversation turn
        private val accumulatedToolResults = ThreadLocal<MutableMap<String, String>>()
    }

    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Get accumulated tool results for the current conversation turn.
     * This is used by tools (like reminder) that need access to results from previous tool calls.
     */
    fun getAccumulatedToolResults(): Map<String, String> {
        return accumulatedToolResults.get() ?: emptyMap()
    }

    /**
     * Отправляет сообщение в Claude API и возвращает ответ.
     *
     * @param clientIp IP-адрес клиента для автоматического определения местоположения в MCP инструментах
     * @param userLocation Координаты пользователя из браузера (если доступны)
     * @return Triple(reply, usage, error)
     */
    suspend fun sendMessage(
        userMessage: String,
        outputFormat: String = "default",
        model: String = ClaudeConfig.MODEL,
        maxTokens: Int = ClaudeConfig.MAX_TOKENS,
        specMode: Boolean = false,
        conversationHistory: List<Message> = emptyList(),
        temperature: Double = 1.0,
        enabledTools: List<String> = emptyList(),
        clientIp: String? = null,
        userLocation: com.claude.agent.models.UserLocation? = null,
        sessionId: String? = null,
        showIntermediateMessages: Boolean = true,
        useRag: Boolean = false,
        ragTopK: Int = 3,
        ragMinSimilarity: Double = 0.3,
        ragFilterEnabled: Boolean = true
    ): ClaudeResponse {
        try {
            // Получаем RAG контекст если включен
            logger.info("Rag enabled: $useRag")
            logger.info("RagService enabled: ${ragService != null}")
            logger.info("OllamaEmbeddingClient enabled: ${ollamaEmbeddingClient != null}")

            val isRagEnabled = useRag && ragService != null && ollamaEmbeddingClient != null
            logger.info("Rag result isEnabled: $isRagEnabled")

            val ragContext = if (isRagEnabled) {
                retrieveRagContext(
                    query = userMessage,
                    topK = ragTopK,
                    minSimilarity = ragMinSimilarity,
                    filterEnabled = ragFilterEnabled
                )
            } else {
                null
            }

            // Формируем системный промпт
            val systemPrompt = SystemPrompts.getSystemPrompt(outputFormat = outputFormat, specMode = specMode, enabledTools = enabledTools, isRagEnabled = isRagEnabled)

            val cleanUserMessage = SystemPrompts.getUserMessage(userMessage)

            // Формируем массив сообщений с историей
            val messages = buildMessages(
                history = conversationHistory,
                userMessage = cleanUserMessage,
                ragContext = ragContext
            )

            mcpTools.enableServers(enabledTools)
            // Получаем remote MCP серверы
            val remoteMcpParams = mcpTools.getRemoteMCP()

            // Получаем инструменты с динамической фильтрацией
            val localMcpParams = getFilteredTools(enabledTools, remoteMcpParams, cleanUserMessage)

            // Формируем запрос с поддержкой prompt caching
            val requestBody = buildAnthropicRequest(
                model = model,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt,
                messages = messages,
                temperature = temperature,
                tools = localMcpParams,
                remoteMcp = remoteMcpParams,
                specMode = specMode
            )

            logger.info("=== Отправка запроса к Claude API ===")
            logger.info("Модель: $model")
            logger.info("Max tokens: $maxTokens")
            logger.info("Temperature: $temperature")
            if (localMcpParams != null) {
                logger.info("MCP инструменты: ${localMcpParams.size} шт.")
            }
            logger.info("Сообщений: ${messages.size}")
            logger.info("=====================================")

            // Отправляем запрос
            val startTime = System.currentTimeMillis()
            val response: HttpResponse = httpClient.post(apiUrl) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                // Добавляем beta headers
                val betaHeaders = mutableListOf<String>()
                if (remoteMcpParams.isNotEmpty()) {
                    betaHeaders.add("mcp-client-2025-11-20")
                }
                if (PromptCachingConfig.ENABLED) {
                    betaHeaders.add("prompt-caching-2024-07-31")
                }
                if (betaHeaders.isNotEmpty()) {
                    header("anthropic-beta", betaHeaders.joinToString(","))
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("Ошибка API: ${response.status}, body: $errorBody")
                return ClaudeResponse(
                    reply = null,
                    usage = null,
                    error = "Ошибка Claude API: ${response.status}",
                    intermediateMessages = emptyList()
                )
            }

            val responseBody = response.body<JsonObject>()
            val elapsed = System.currentTimeMillis() - startTime

            // Извлекаем информацию о кэшировании
            val usage = responseBody["usage"]?.jsonObject
            val cacheCreationTokens = usage?.get("cache_creation_input_tokens")?.jsonPrimitive?.intOrNull ?: 0
            val cacheReadTokens = usage?.get("cache_read_input_tokens")?.jsonPrimitive?.intOrNull ?: 0

            if (cacheReadTokens > 0) {
                logger.info("💾 Cache hit! Read $cacheReadTokens tokens from cache")
                tokenMetricsService?.recordCachingSavings(cacheReadTokens.toLong())
            }
            if (cacheCreationTokens > 0) {
                logger.info("💾 Cache created: $cacheCreationTokens tokens")
            }

            // Обрабатываем ответ (с поддержкой tool_use)
            val handleResult = handleResponse(
                responseBody = responseBody,
                initialMessages = messages,
                model = model,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt,
                temperature = temperature,
                tools = localMcpParams,
                remoteMcp = remoteMcpParams,
                clientIp = clientIp,
                userLocation = userLocation,
                sessionId = sessionId,
                collectIntermediateMessages = showIntermediateMessages
            )

            logger.info("Ответ получен за ${elapsed}ms")
            logger.info("Использовано токенов: input=${handleResult.usage.input_tokens}, output=${handleResult.usage.output_tokens}")

            // Записываем метрики
            tokenMetricsService?.recordTokenUsage(
                sessionId = sessionId,
                usage = handleResult.usage,
                cachedTokens = cacheReadTokens
            )

            return ClaudeResponse(
                reply = handleResult.reply,
                usage = handleResult.usage,
                error = null,
                intermediateMessages = handleResult.intermediateMessages,
                ticketCreatedViaMcp = handleResult.ticketCreatedViaMcp
            )

        } catch (e: Exception) {
            logger.error("Ошибка sendMessage: ${e.message}", e)
            return ClaudeResponse(
                reply = null,
                usage = null,
                error = "Ошибка сервера: ${e.message}",
                intermediateMessages = emptyList()
            )
        }
    }

    /**
     * Обрабатывает ответ от Claude API (включая tool_use цепочки).
     */
    private suspend fun handleResponse(
        responseBody: JsonObject,
        initialMessages: List<JsonObject>,
        model: String,
        maxTokens: Int,
        systemPrompt: String,
        temperature: Double,
        tools: JsonArray?,
        remoteMcp: JsonArray,
        clientIp: String?,
        userLocation: com.claude.agent.models.UserLocation?,
        sessionId: String?,
        collectIntermediateMessages: Boolean = true
    ): HandleResponseResult {
        var currentResponse = responseBody
        val messages = initialMessages.toMutableList()
        var totalInputTokens = currentResponse["usage"]?.jsonObject?.get("input_tokens")?.jsonPrimitive?.int ?: 0
        var totalOutputTokens = currentResponse["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.int ?: 0

        val intermediateMessages = mutableListOf<Message>()
        var ticketCreatedViaMcp = false  // Флаг создания тикета через MCP
        var iteration = 0

        // Детекция зацикливания: отслеживаем последние вызовы инструментов
        val recentToolCalls = mutableListOf<String>()

        // Initialize thread-local storage for accumulated tool results
        val toolResultsMap = mutableMapOf<String, String>()
        accumulatedToolResults.set(toolResultsMap)

        while (iteration < MAX_TOOL_ITERATIONS) {
            val content = currentResponse["content"]?.jsonArray ?: JsonArray(emptyList())

            // Проверяем наличие tool_use
            val hasToolUse = content.any { block ->
                block.jsonObject["type"]?.jsonPrimitive?.content == "tool_use"
            }

            // Извлекаем текстовые блоки (могут быть вместе с tool_use)
            val textBlocks = content.mapNotNull { block ->
                if (block.jsonObject["type"]?.jsonPrimitive?.content == "text") {
                    block.jsonObject["text"]?.jsonPrimitive?.content
                } else null
            }

            if (!hasToolUse) {
                // Финальный текстовый ответ (нет больше tool_use)
                val finalText = textBlocks.joinToString("")
                val usage = TokenUsage(totalInputTokens, totalOutputTokens)

                // Clean up thread-local storage
                accumulatedToolResults.remove()

                // Если финальный текст пустой, это означает, что Claude не вернул текстовый ответ
                // после выполнения всех инструментов. Возвращаем сообщение по умолчанию.
                if (finalText.isBlank()) {
                    logger.warn("Claude returned empty text response after tool execution. Iteration: $iteration")
                    return HandleResponseResult(
                        reply = "✅ Задача выполнена",
                        usage = usage,
                        intermediateMessages = intermediateMessages,
                        ticketCreatedViaMcp = ticketCreatedViaMcp
                    )
                }

                return HandleResponseResult(
                    reply = finalText,
                    usage = usage,
                    intermediateMessages = intermediateMessages,
                    ticketCreatedViaMcp = ticketCreatedViaMcp
                )
            }

            // Есть tool_use - обрабатываем
            iteration++
            val currentText = textBlocks.joinToString("")

            // Собираем промежуточное сообщение если включено
            if (collectIntermediateMessages && currentText.isNotBlank()) {
                intermediateMessages.add(
                    Message(
                        role = "assistant",
                        content = currentText,
                        timestamp = java.time.Instant.now().toString()
                    )
                )
            }

            // Извлекаем имена вызываемых инструментов для детекции зацикливания
            val toolNames = content
                .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "tool_use" }
                .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }

            logger.info("=== Tool call iteration $iteration/$MAX_TOOL_ITERATIONS - Calling: ${toolNames.joinToString(", ")} ===")

            // Детекция зацикливания
            toolNames.forEach { toolName ->
                recentToolCalls.add(toolName)
            }

            // Проверяем последние N вызовов на повторяемость
            if (recentToolCalls.size >= LOOP_DETECTION_THRESHOLD) {
                val lastCalls = recentToolCalls.takeLast(LOOP_DETECTION_THRESHOLD)
                if (lastCalls.all { it == lastCalls.first() }) {
                    logger.warn("⚠️ Обнаружено зацикливание: инструмент '${lastCalls.first()}' вызван $LOOP_DETECTION_THRESHOLD раз подряд")
                    logger.warn("💡 Подсказка: возможно, модель застряла в рекурсивном просмотре директорий")
                }
            }

            if (currentText.isNotBlank()) {
                logger.info("Text in this iteration: $currentText")

                // 🔥 НОВОЕ: Отправляем промежуточный текст через WebSocket
                if (sessionId != null) {
                    try {
                        val messageData = buildJsonObject {
                            put("role", "assistant")
                            put("content", currentText)
                            put("is_intermediate", true)
                            put("iteration", iteration)
                            put("timestamp", System.currentTimeMillis())
                        }

                        webSocketService.broadcastToSession(
                            sessionId = sessionId,
                            message = WebSocketMessage(
                                type = "streaming_text",
                                sessionId = sessionId,
                                data = Json.encodeToString(messageData)
                            )
                        )
                        logger.info("📡 Промежуточный текст отправлен через WebSocket (iteration $iteration)")
                    } catch (e: Exception) {
                        logger.warn("Не удалось отправить промежуточный текст через WebSocket: ${e.message}")
                    }
                }
            }

            // Добавляем ответ ассистента в историю
            messages.add(JsonObject(mapOf(
                "role" to JsonPrimitive("assistant"),
                "content" to content
            )))

            // Выполняем все tool calls
            val toolResults = mutableListOf<JsonObject>()
            var toolCallIndex = 0
            for (block in content) {
                val blockObj = block.jsonObject
                if (blockObj["type"]?.jsonPrimitive?.content == "tool_use") {
                    toolCallIndex++
                    val toolName = blockObj["name"]?.jsonPrimitive?.content ?: continue
                    val toolInput = blockObj["input"]?.jsonObject ?: JsonObject(emptyMap())
                    val toolUseId = blockObj["id"]?.jsonPrimitive?.content ?: continue

                    logger.info("🔧 [$iteration/$MAX_TOOL_ITERATIONS] Tool call #$toolCallIndex: $toolName")

                    logger.info("Tool call iteration $iteration - Calling: $toolName")

                    val result = try {
                        mcpTools.callLocalTool(toolName, toolInput, clientIp, userLocation, sessionId)
                    } catch (e: Exception) {
                        logger.error("Ошибка выполнения $toolName: ${e.message}")
                        """{"error": "${e.message}"}"""
                    }

                    logger.info("Tool result for $toolName: $result")

                    // 🔥 НОВОЕ: Отправляем RAW результат инструмента через WebSocket
                    if (sessionId != null) {
                        try {
                            val toolResultData = buildJsonObject {
                                put("tool_name", toolName)
                                put("tool_input", toolInput)
                                put("tool_result", result)
                                put("iteration", iteration)
                                put("tool_index", toolCallIndex)
                                put("timestamp", System.currentTimeMillis())
                            }

                            webSocketService.broadcastToSession(
                                sessionId = sessionId,
                                message = WebSocketMessage(
                                    type = "tool_result",
                                    sessionId = sessionId,
                                    data = Json.encodeToString(toolResultData)
                                )
                            )
                            logger.info("📡 Raw tool result отправлен через WebSocket: $toolName")

                            // Специальная обработка для support_crm tool
                            if (toolName == "support_crm" && sessionId != null) {
                                try {
                                    val resultJson = Json.parseToJsonElement(result).jsonObject
                                    val action = toolInput["action"]?.jsonPrimitive?.content

                                    // Обработка создания тикета
                                    if (action == "create_ticket" && resultJson["success"]?.jsonPrimitive?.boolean == true) {
                                        val ticket = resultJson["ticket"]?.jsonObject
                                        if (ticket != null) {
                                            val ticketId = ticket["id"]?.jsonPrimitive?.content ?: "unknown"
                                            val title = ticket["title"]?.jsonPrimitive?.content ?: "Без названия"
                                            val priority = ticket["priority"]?.jsonPrimitive?.content ?: "MEDIUM"

                                            logger.info("🎫 Тикет создан через MCP: $ticketId - $title")
                                            ticketCreatedViaMcp = true  // Устанавливаем флаг

                                            // Отправляем уведомление о создании тикета
                                            val ticketData = buildJsonObject {
                                                put("ticket_id", ticketId)
                                                put("title", title)
                                                put("priority", priority)
                                                put("created_via_mcp", true)
                                            }

                                            webSocketService.broadcastToSession(
                                                sessionId = sessionId,
                                                message = WebSocketMessage(
                                                    type = "ticket_created",
                                                    sessionId = sessionId,
                                                    data = Json.encodeToString(ticketData)
                                                )
                                            )

                                            webSocketService.broadcastGlobal(
                                                message = WebSocketMessage(
                                                    type = "ticket_created",
                                                    sessionId = sessionId,
                                                    data = Json.encodeToString(ticketData)
                                                )
                                            )
                                        }
                                    }

                                    // Обработка обновления тикета
                                    if ((action == "update_ticket" || action == "update_ticket_status") &&
                                        resultJson["success"]?.jsonPrimitive?.boolean == true) {
                                        val ticketId = toolInput["ticket_id"]?.jsonPrimitive?.content ?: "unknown"
                                        val oldStatus = resultJson["old_status"]?.jsonPrimitive?.content
                                        val newStatus = resultJson["new_status"]?.jsonPrimitive?.content
                                        val wasDeleted = resultJson["deleted"]?.jsonPrimitive?.boolean ?: false

                                        logger.info("🎫 Тикет обновлен через MCP: $ticketId ($oldStatus -> $newStatus)")

                                        val ticketData = buildJsonObject {
                                            put("ticket_id", ticketId)
                                            if (oldStatus != null) put("old_status", oldStatus)
                                            if (newStatus != null) put("new_status", newStatus)
                                            put("was_deleted", wasDeleted)
                                            put("updated_via_mcp", true)
                                        }

                                        webSocketService.broadcastToSession(
                                            sessionId = sessionId,
                                            message = WebSocketMessage(
                                                type = "ticket_status_changed",
                                                sessionId = sessionId,
                                                data = Json.encodeToString(ticketData)
                                            )
                                        )
                                    }

                                    // Обработка удаления тикета
                                    if (action == "delete_ticket" && resultJson["success"]?.jsonPrimitive?.boolean == true) {
                                        val ticketId = resultJson["ticket_id"]?.jsonPrimitive?.content ?: "unknown"

                                        logger.info("🎫 Тикет удален через MCP: $ticketId")

                                        val ticketData = buildJsonObject {
                                            put("ticket_id", ticketId)
                                            put("deleted_via_mcp", true)
                                        }

                                        webSocketService.broadcastToSession(
                                            sessionId = sessionId,
                                            message = WebSocketMessage(
                                                type = "ticket_deleted",
                                                sessionId = sessionId,
                                                data = Json.encodeToString(ticketData)
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    logger.warn("Ошибка обработки support_crm результата: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("Не удалось отправить tool result через WebSocket: ${e.message}")
                        }
                    }

                    // Store result in accumulated map (except for reminder tool itself)
                    if (toolName != REMINDER) {
                        toolResultsMap[toolName] = result
                    }

                    toolResults.add(JsonObject(mapOf(
                        "type" to JsonPrimitive("tool_result"),
                        "tool_use_id" to JsonPrimitive(toolUseId),
                        "content" to JsonPrimitive(result)
                    )))
                }
            }

            // Добавляем результаты инструментов
            messages.add(JsonObject(mapOf(
                "role" to JsonPrimitive("user"),
                "content" to JsonArray(toolResults)
            )))

            // Оптимизация: сжимаем историю в итерациях если включено
            val messagesToSend = if (ToolIterationConfig.COMPRESS_HISTORY_IN_ITERATIONS &&
                                     messages.size > ToolIterationConfig.MAX_CONTEXT_MESSAGES * 2) {
                // Берем только последние N пар сообщений для контекста
                val contextSize = ToolIterationConfig.MAX_CONTEXT_MESSAGES * 2
                messages.takeLast(contextSize)
            } else {
                messages
            }

            // Повторный запрос к Claude
            val requestBody = buildAnthropicRequest(
                model = model,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt,
                messages = messagesToSend,
                temperature = temperature,
                tools = tools,
                remoteMcp = remoteMcp,
                specMode = false
            )

            val response: HttpResponse = httpClient.post(apiUrl) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                // Добавляем beta header для поддержки MCP connector
                if (remoteMcp.isNotEmpty()) {
                    header("anthropic-beta", "mcp-client-2025-11-20")
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            currentResponse = response.body<JsonObject>()
            totalInputTokens += currentResponse["usage"]?.jsonObject?.get("input_tokens")?.jsonPrimitive?.int ?: 0
            totalOutputTokens += currentResponse["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.int ?: 0
        }

        // Лимит итераций достигнут
        logger.warn("⚠️ Достигнут лимит tool call итераций ($MAX_TOOL_ITERATIONS)")
        logger.warn("💡 История вызовов: ${recentToolCalls.takeLast(10).joinToString(" → ")}")

        // Анализируем, была ли задача выполнена
        val uniqueTools = recentToolCalls.toSet()
        if (uniqueTools.size == 1 && recentToolCalls.size >= 5) {
            logger.error("❌ ЗАЦИКЛИВАНИЕ: инструмент '${uniqueTools.first()}' вызывался ${recentToolCalls.count { it == uniqueTools.first() }} раз")
            logger.error("💡 Рекомендация: улучшить системный промпт или увеличить MAX_TOOL_ITERATIONS")
        }

        // Clean up thread-local storage
        accumulatedToolResults.remove()

        val content = currentResponse["content"]?.jsonArray ?: JsonArray(emptyList())
        val textBlocks = content.mapNotNull { block ->
            if (block.jsonObject["type"]?.jsonPrimitive?.content == "text") {
                block.jsonObject["text"]?.jsonPrimitive?.content
            } else null
        }
        val finalText = textBlocks.joinToString("")
        val usage = TokenUsage(totalInputTokens, totalOutputTokens)

        // Если после лимита итераций текст пустой, возвращаем сообщение по умолчанию
        if (finalText.isBlank()) {
            logger.warn("Claude не вернул текстовый ответ после достижения лимита итераций")
            return HandleResponseResult(
                reply = "⚠️ Достигнут лимит итераций ($MAX_TOOL_ITERATIONS). " +
                "Выполнено инструментов: ${recentToolCalls.size}. " +
                "Возможно, задача не завершена полностью. " +
                "Попробуйте переформулировать запрос или разбить на более мелкие задачи.",
                usage = usage,
                intermediateMessages = intermediateMessages,
                ticketCreatedViaMcp = ticketCreatedViaMcp
            )
        }

        return HandleResponseResult(
            reply = finalText,
            usage = usage,
            intermediateMessages = intermediateMessages,
            ticketCreatedViaMcp = ticketCreatedViaMcp
        )
    }

    private fun buildMessages(history: List<Message>, userMessage: String, ragContext: String?): List<JsonObject> {
        val messages = mutableListOf<JsonObject>()

        // Добавляем историю
        for (msg in history) {
            if (msg.role in listOf("user", "assistant") && msg.content.isNotBlank()) {
                messages.add(JsonObject(mapOf(
                    "role" to JsonPrimitive(msg.role),
                    "content" to JsonPrimitive(msg.content)
                )))
            }
        }

        // Добавляем текущее сообщение с RAG контекстом (если есть)
        if (ragContext != null && ragContext.isNotBlank()) {
            logger.info("✅ Adding RAG context as separate content block with caching (${ragContext.length} chars)")

            // Используем content blocks: RAG контекст + вопрос пользователя
            messages.add(JsonObject(mapOf(
                "role" to JsonPrimitive("user"),
                "content" to JsonArray(listOf(
                    // Блок 1: RAG контекст с кешированием
                    JsonObject(mapOf(
                        "type" to JsonPrimitive("text"),
                        "text" to JsonPrimitive(ragContext),
                        "cache_control" to JsonObject(mapOf(
                            "type" to JsonPrimitive("ephemeral")
                        ))
                    )),
                    // Блок 2: Вопрос пользователя
                    JsonObject(mapOf(
                        "type" to JsonPrimitive("text"),
                        "text" to JsonPrimitive(userMessage)
                    ))
                ))
            )))
        } else {
            // Без RAG - обычное сообщение
            messages.add(JsonObject(mapOf(
                "role" to JsonPrimitive("user"),
                "content" to JsonPrimitive(userMessage)
            )))
        }

        return messages
    }

    private fun getFilteredTools(
        enabledTools: List<String>,
        remoteMcp: JsonArray,
        userMessage: String = ""
    ): JsonArray? {
        val allTools = mcpTools.getLocalToolsDefinitions(enabledTools)

        // Применяем динамическую фильтрацию если доступна
        val filtered = if (toolsFilterService != null && userMessage.isNotBlank()) {
            val originalCount = allTools.filter { it.name in enabledTools }.size
            val filteredTools = toolsFilterService.filterRelevantTools(userMessage, enabledTools, allTools)

            // Записываем экономию
            val savedTokens = toolsFilterService.estimateTokensSaved(originalCount, filteredTools.size)
            if (savedTokens > 0) {
                tokenMetricsService?.recordToolFilteringSavings(savedTokens)
            }

            filteredTools
        } else {
            allTools.filter { it.name in enabledTools }
        }

        val elements = buildList {
            // Если есть remote MCP серверы, добавляем mcp_toolset для каждого
            if (remoteMcp.isNotEmpty()) {
                remoteMcp.forEach { serverElement ->
                    val serverObj = serverElement.jsonObject
                    val serverName = serverObj["name"]?.jsonPrimitive?.content
                    if (serverName != null) {
                        add(JsonObject(mapOf(
                            "type" to JsonPrimitive("mcp_toolset"),
                            "mcp_server_name" to JsonPrimitive(serverName)
                        )))
                    }
                }
            }

            // Добавляем локальные инструменты с поддержкой кэширования
            filtered.forEachIndexed { index, tool ->
                val toolDef = mutableMapOf(
                    "name" to JsonPrimitive(tool.name),
                    "description" to JsonPrimitive(tool.description),
                    "input_schema" to tool.input_schema
                )

                // Добавляем cache_control для последнего инструмента (если включено кэширование)
                if (PromptCachingConfig.ENABLED && PromptCachingConfig.CACHE_TOOLS && index == filtered.size - 1) {
                    toolDef["cache_control"] = JsonObject(mapOf("type" to JsonPrimitive("ephemeral")))
                }

                add(JsonObject(toolDef))
            }
        }

        return if (elements.isNotEmpty()) JsonArray(elements) else null
    }

    private fun buildAnthropicRequest(
        model: String,
        maxTokens: Int,
        systemPrompt: String,
        messages: List<JsonObject>,
        temperature: Double,
        remoteMcp: JsonArray,
        tools: JsonArray?,
        specMode: Boolean
    ): JsonObject {
        val params = mutableMapOf(
            "model" to JsonPrimitive(model),
            "max_tokens" to JsonPrimitive(maxTokens),
            "messages" to JsonArray(messages),
            "temperature" to JsonPrimitive(temperature),
        )

        // Системный промпт с поддержкой кэширования
        if (PromptCachingConfig.ENABLED && PromptCachingConfig.CACHE_SYSTEM_PROMPT) {
            // Используем массив блоков для системного промпта с cache_control
            params["system"] = JsonArray(listOf(
                JsonObject(mapOf(
                    "type" to JsonPrimitive("text"),
                    "text" to JsonPrimitive(systemPrompt),
                    "cache_control" to JsonObject(mapOf("type" to JsonPrimitive("ephemeral")))
                ))
            ))
        } else {
            params["system"] = JsonPrimitive(systemPrompt)
        }

        if (remoteMcp.isNotEmpty()) {
            params["mcp_servers"] = remoteMcp
        }

        if (tools != null) {
            params["tools"] = tools
        }

        if (specMode) {
            params["stop_sequences"] = JsonArray(listOf(JsonPrimitive(SPEC_END_MARKER)))
        }

        return JsonObject(params)
    }

    fun isApiKeyConfigured(): Boolean = apiKey.isNotBlank()

    /**
     * Получение релевантного контекста из RAG базы данных
     *
     * @param query Запрос пользователя
     * @param topK Количество наиболее релевантных чанков
     * @param minSimilarity Минимальный порог схожести (0.0-1.0)
     * @param filterEnabled Включить фильтрацию по порогу (false для A/B тестирования)
     * @return Отформатированный контекст для добавления в промпт
     */
    private suspend fun retrieveRagContext(
        query: String,
        topK: Int,
        minSimilarity: Double = 0.3,
        filterEnabled: Boolean = true
    ): String? {
        return try {
            if (ragService == null || ollamaEmbeddingClient == null) {
                logger.warn("RAG services not configured")
                return null
            }

            logger.info("🔍 Retrieving RAG context for query: ${query.take(100)}...")

            // Генерируем embedding для запроса
            val queryEmbedding = ollamaEmbeddingClient.embed(query)
            logger.debug("Generated query embedding: ${queryEmbedding.size} dimensions")

            // ВАЖНО: Нормализуем вектор запроса так же, как при индексации
            val normalizedQueryEmbedding = normalizeToRange(queryEmbedding)
            logger.debug("Normalized query embedding to [0,1] range")

            // Ищем релевантные чанки
            // Если фильтрация отключена, устанавливаем порог в 0.0 для получения всех результатов
            val effectiveMinSimilarity = if (filterEnabled) minSimilarity else 0.0
            logger.info("RAG filtering: enabled=$filterEnabled, threshold=$effectiveMinSimilarity, topK=$topK")

            val results = ragService.search(
                queryEmbedding = normalizedQueryEmbedding,
                topK = topK,
                minSimilarity = effectiveMinSimilarity
            )

            if (results.isEmpty()) {
                logger.info("No relevant RAG context found")
                return null
            }

            logger.info("Found ${results.size} relevant chunks (similarities: ${results.map { "%.3f".format(it.similarity) }})")

            // Форматируем контекст
            ragService.formatContext(results)

        } catch (e: Exception) {
            logger.error("Failed to retrieve RAG context: ${e.message}", e)
            null
        }
    }
}
