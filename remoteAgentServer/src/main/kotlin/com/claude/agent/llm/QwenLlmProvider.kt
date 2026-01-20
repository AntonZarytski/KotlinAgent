package com.claude.agent.llm

import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.models.Message
import com.claude.agent.models.TokenUsage
import com.claude.agent.models.UserLocation
import com.claude.agent.service.WebSocketMessage
import com.claude.agent.service.WebSocketService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Провайдер для локальной Qwen модели через Ollama API
 * 
 * Поддерживает:
 * - Tool calling через Ollama API
 * - Итеративное выполнение инструментов
 * - WebSocket уведомления
 */
class QwenLlmProvider(
    private val httpClient: HttpClient,
    private val mcpTools: MCPTools,
    private val webSocketService: WebSocketService,
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "qwen2.5-coder:7b-instruct"
) : LlmProvider {
    
    private val logger = LoggerFactory.getLogger(QwenLlmProvider::class.java)
    
    companion object {
        private const val MAX_TOOL_ITERATIONS = 20
    }
    
    @Serializable
    data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val tools: List<OllamaTool>? = null,
        val stream: Boolean = false,
        val options: OllamaOptions? = null
    )
    
    @Serializable
    data class OllamaMessage(
        val role: String,
        val content: String,
        val tool_calls: List<OllamaToolCall>? = null
    )
    
    @Serializable
    data class OllamaToolCall(
        val function: OllamaFunction
    )
    
    @Serializable
    data class OllamaFunction(
        val name: String,
        val arguments: JsonObject
    )
    
    @Serializable
    data class OllamaTool(
        val type: String = "function",
        val function: OllamaToolFunction
    )
    
    @Serializable
    data class OllamaToolFunction(
        val name: String,
        val description: String,
        val parameters: JsonObject
    )
    
    @Serializable
    data class OllamaOptions(
        val temperature: Double? = null,
        val num_predict: Int? = null
    )
    
    @Serializable
    data class OllamaChatResponse(
        val model: String,
        val message: OllamaMessage,
        val done: Boolean
    )
    
    override suspend fun generate(
        systemPrompt: String,
        messages: List<Message>,
        model: String?,
        maxTokens: Int,
        temperature: Double,
        enabledTools: List<String>,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?,
        showIntermediateMessages: Boolean
    ): LlmResponse {
        return try {
            val actualModel = model ?: modelName
            
            logger.info("=== Qwen LLM Request ===")
            logger.info("Model: $actualModel")
            logger.info("Max tokens: $maxTokens")
            logger.info("Temperature: $temperature")
            logger.info("Enabled tools: ${enabledTools.size}")
            logger.info("System prompt: $systemPrompt")
            logger.info("Messages count: ${messages.size}")
            
            // Включаем MCP серверы
            mcpTools.enableServers(enabledTools)

            // Формируем сообщения для Ollama
            val ollamaMessages = buildOllamaMessages(systemPrompt, messages)
            logger.info("OllamaMessages: $ollamaMessages")

            // Получаем инструменты
            val tools = if (enabledTools.isNotEmpty()) {
                buildOllamaTools(enabledTools)
            } else null
            logger.info("OllamaTools count: ${tools?.size ?: 0}")
            tools?.forEach { tool ->
                logger.info("  - Tool: ${tool.function.name} - ${tool.function.description.take(100)}")
            }

            // Выполняем запрос с поддержкой tool calling
            val (finalReply, totalTokens, intermediateMessages) = handleToolCalling(
                model = actualModel,
                initialMessages = ollamaMessages,
                tools = tools,
                maxTokens = maxTokens,
                temperature = temperature,
                enabledTools = enabledTools,
                clientIp = clientIp,
                userLocation = userLocation,
                sessionId = sessionId,
                showIntermediateMessages = showIntermediateMessages
            )
            
            logger.info("Qwen response completed. Tokens: $totalTokens, finalReply: \n$finalReply \nintermediateMessages: $intermediateMessages")
            
            LlmResponse(
                reply = finalReply,
                usage = TokenUsage(input_tokens = totalTokens, output_tokens = totalTokens),
                error = null,
                intermediateMessages = intermediateMessages
            )
            
        } catch (e: Exception) {
            logger.error("Qwen LLM error: ${e.message}", e)
            LlmResponse(
                reply = null,
                usage = null,
                error = "Qwen error: ${e.message}",
                intermediateMessages = emptyList()
            )
        }
    }
    
    override fun isConfigured(): Boolean {
        // Проверяем доступность Ollama сервера
        return true // TODO: добавить проверку доступности
    }

    override fun getProviderName(): String = "Qwen (Ollama)"

    /**
     * Формирует сообщения для Ollama API
     */
    private fun buildOllamaMessages(systemPrompt: String, messages: List<Message>): MutableList<OllamaMessage> {
        val ollamaMessages = mutableListOf<OllamaMessage>()

        // Добавляем системный промпт
        ollamaMessages.add(OllamaMessage(
            role = "system",
            content = systemPrompt
        ))

        // Добавляем историю сообщений
        messages.forEach { msg ->
            ollamaMessages.add(OllamaMessage(
                role = msg.role,
                content = msg.content
            ))
        }

        return ollamaMessages
    }

    /**
     * Формирует инструменты для Ollama API
     */
    private fun buildOllamaTools(enabledTools: List<String>): List<OllamaTool> {
        val toolDefinitions = mcpTools.getLocalToolsDefinitions(enabledTools)

        return toolDefinitions.map { tool ->
            OllamaTool(
                type = "function",
                function = OllamaToolFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.input_schema
                )
            )
        }
    }

    /**
     * Парсит tool calls из текста с тегами <tool_call>
     * Формат: <tool_call>{"name": "tool_name", "arguments": {...}}</tool_call>
     */
    private fun parseToolCallsFromText(content: String): List<OllamaToolCall>? {
        val toolCalls = mutableListOf<OllamaToolCall>()
        val regex = Regex("<tool_call>\\s*(.+?)\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL)

        regex.findAll(content).forEach { match ->
            try {
                val jsonText = match.groupValues[1].trim()
                logger.debug("Parsing tool call JSON: $jsonText")

                // Парсим JSON
                val jsonElement = Json.parseToJsonElement(jsonText)
                if (jsonElement is JsonObject) {
                    val name = jsonElement["name"]?.jsonPrimitive?.content ?: return@forEach
                    val arguments = jsonElement["arguments"]?.jsonObject ?: JsonObject(emptyMap())

                    toolCalls.add(
                        OllamaToolCall(
                            function = OllamaFunction(
                                name = name,
                                arguments = arguments
                            )
                        )
                    )
                    logger.info("Parsed tool call: $name")
                }
            } catch (e: Exception) {
                logger.error("Failed to parse tool call from text: ${match.value}", e)
            }
        }

        return if (toolCalls.isNotEmpty()) toolCalls else null
    }

    /**
     * Парсит чистый JSON с вызовом инструмента
     * Формат: {"name": "tool_name", "arguments": {...}}
     * Поддерживает множественные JSON объекты, разделенные переносами строк
     */
    private fun parseJsonToolCall(content: String): List<OllamaToolCall>? {
        return try {
            val trimmed = content.trim()
            logger.debug("Parsing pure JSON tool call: $trimmed")

            // Проверяем, есть ли несколько JSON объектов
            val toolCalls = mutableListOf<OllamaToolCall>()

            // Разбиваем по пустым строкам и пытаемся парсить каждый блок
            val jsonBlocks = trimmed.split("\n\n").filter { it.isNotBlank() }

            if (jsonBlocks.size > 1) {
                logger.info("Found ${jsonBlocks.size} JSON blocks, parsing each separately")
                jsonBlocks.forEach { block ->
                    val parsedCall = parseSingleJsonToolCall(block.trim())
                    if (parsedCall != null) {
                        toolCalls.add(parsedCall)
                    }
                }
                return if (toolCalls.isNotEmpty()) toolCalls else null
            }

            // Одиночный JSON объект
            val parsedCall = parseSingleJsonToolCall(trimmed)
            return if (parsedCall != null) listOf(parsedCall) else null

        } catch (e: Exception) {
            logger.error("Failed to parse pure JSON tool call: $content", e)
            null
        }
    }

    /**
     * Парсит один JSON объект с вызовом инструмента
     */
    private fun parseSingleJsonToolCall(jsonText: String): OllamaToolCall? {
        return try {
            val jsonElement = Json.parseToJsonElement(jsonText)
            if (jsonElement is JsonObject) {
                val name = jsonElement["name"]?.jsonPrimitive?.content
                val arguments = jsonElement["arguments"]?.jsonObject

                if (name != null) {
                    logger.info("Parsed pure JSON tool call: $name")
                    OllamaToolCall(
                        function = OllamaFunction(
                            name = name,
                            arguments = arguments ?: JsonObject(emptyMap())
                        )
                    )
                } else {
                    logger.warn("JSON object missing 'name' field")
                    null
                }
            } else {
                logger.warn("JSON is not an object")
                null
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse single JSON: ${e.message}")
            null
        }
    }

    /**
     * Парсит JSON из markdown блока ```json
     */
    private fun parseJsonFromMarkdown(content: String): List<OllamaToolCall>? {
        return try {
            val regex = Regex("```json\\s*(.+?)\\s*```", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(content)

            if (match != null) {
                val jsonText = match.groupValues[1].trim()
                logger.debug("Extracted JSON from markdown: $jsonText")
                parseJsonToolCall(jsonText)
            } else {
                logger.warn("No JSON block found in markdown")
                null
            }
        } catch (e: Exception) {
            logger.error("Failed to parse JSON from markdown: $content", e)
            null
        }
    }

    /**
     * Извлекает текст из JSON ответа, если это обычный ответ (не tool call)
     * Поддерживает форматы:
     * - {"response": "текст"}
     * - {"answer": "текст"}
     * - {"result": {"output": "текст"}}
     * - {"task_id": 1, "status": "completed", "result": {"output": "текст"}}
     * - ```json {"response": "текст"} ```
     */
    private fun extractTextFromJsonResponse(content: String): String {
        return try {
            // Убираем markdown блоки, если есть
            val cleanContent = if (content.contains("```json")) {
                val regex = Regex("```json\\s*(.+?)\\s*```", RegexOption.DOT_MATCHES_ALL)
                val match = regex.find(content)
                match?.groupValues?.get(1)?.trim() ?: content
            } else {
                content.trim()
            }

            // Проверяем, является ли это JSON
            if (cleanContent.startsWith("{") && cleanContent.endsWith("}")) {
                val jsonElement = Json.parseToJsonElement(cleanContent)
                if (jsonElement is JsonObject) {
                    // Проверяем наличие поля "name" - если есть, это tool call, не трогаем
                    if (jsonElement.containsKey("name")) {
                        return content
                    }

                    // Проверяем различные форматы ответа
                    val responseText =
                        // Простые форматы
                        jsonElement["response"]?.jsonPrimitive?.content
                        ?: jsonElement["answer"]?.jsonPrimitive?.content
                        ?: jsonElement["text"]?.jsonPrimitive?.content
                        ?: jsonElement["content"]?.jsonPrimitive?.content
                        // Вложенные форматы
                        ?: jsonElement["result"]?.jsonObject?.get("output")?.jsonPrimitive?.content
                        ?: jsonElement["result"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                        ?: jsonElement["data"]?.jsonObject?.get("output")?.jsonPrimitive?.content

                    if (responseText != null) {
                        logger.info("Extracted text from JSON response field")
                        return responseText
                    }
                }
            }

            // Если не удалось извлечь, возвращаем оригинал
            content
        } catch (e: Exception) {
            logger.debug("Not a JSON response or failed to parse: ${e.message}")
            content
        }
    }

    /**
     * Обрабатывает tool calling с итерациями
     */
    private suspend fun handleToolCalling(
        model: String,
        initialMessages: MutableList<OllamaMessage>,
        tools: List<OllamaTool>?,
        maxTokens: Int,
        temperature: Double,
        enabledTools: List<String>,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?,
        showIntermediateMessages: Boolean
    ): Triple<String, Int, List<Message>> {
        val messages = initialMessages
        val intermediateMessages = mutableListOf<Message>()
        var iteration = 0
        var totalTokens = 0

        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++

            // Отправляем запрос к Ollama
            val request = OllamaChatRequest(
                model = model,
                messages = messages,
                tools = tools,
                stream = false,
                options = OllamaOptions(
                    temperature = temperature,
                    num_predict = maxTokens
                )
            )

            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("Ollama API error: ${response.status}, body: $errorBody")
                throw RuntimeException("Ollama API error: ${response.status}")
            }

            val chatResponse = response.body<OllamaChatResponse>()
            val assistantMessage = chatResponse.message

            // Логируем сырой ответ от Qwen
            logger.info("=== RAW QWEN RESPONSE (iteration $iteration) ===")
            logger.info("Content: ${assistantMessage.content}")
            logger.info("Tool calls from API: ${assistantMessage.tool_calls}")
            logger.info("=== END RAW RESPONSE ===")

            // Проверяем наличие tool calls
            var toolCalls = assistantMessage.tool_calls

            // Если tool_calls пустой, пытаемся распарсить из текста (для Qwen)
            if (toolCalls.isNullOrEmpty()) {
                // Проверяем теги <tool_call>
                if (assistantMessage.content.contains("<tool_call>")) {
                    logger.info("Parsing tool calls from <tool_call> tags in response")
                    toolCalls = parseToolCallsFromText(assistantMessage.content)
                }
                // Проверяем, является ли весь ответ JSON с вызовом инструмента
                else if (assistantMessage.content.trim().startsWith("{") &&
                         assistantMessage.content.trim().endsWith("}")) {
                    logger.info("Attempting to parse response as pure JSON tool call")
                    toolCalls = parseJsonToolCall(assistantMessage.content)
                }
                // Проверяем JSON в markdown блоке ```json
                else if (assistantMessage.content.contains("```json")) {
                    logger.info("Attempting to parse tool call from markdown JSON block")
                    toolCalls = parseJsonFromMarkdown(assistantMessage.content)
                }
            }

            if (toolCalls.isNullOrEmpty()) {
                // Проверяем, не является ли это обычным ответом в JSON формате
                val cleanedResponse = extractTextFromJsonResponse(assistantMessage.content)
                if (cleanedResponse != assistantMessage.content) {
                    logger.info("Extracted text from JSON response")
                    return Triple(cleanedResponse, totalTokens, intermediateMessages)
                }

                // Финальный ответ без tool calls
                logger.info("Qwen final response (iteration $iteration)")
                return Triple(assistantMessage.content, totalTokens, intermediateMessages)
            }

            // Есть tool calls - выполняем их
            logger.info("=== Tool call iteration $iteration/$MAX_TOOL_ITERATIONS - ${toolCalls.size} tools ===")

            // Добавляем ответ ассистента с tool calls
            messages.add(assistantMessage)

            // Выполняем все tool calls
            for ((index, toolCall) in toolCalls.withIndex()) {
                val toolName = toolCall.function.name
                val toolArgs = toolCall.function.arguments

                logger.info("🔧 [$iteration/$MAX_TOOL_ITERATIONS] Tool call #${index + 1}: $toolName")

                val result = try {
                    mcpTools.callLocalTool(toolName, toolArgs, clientIp, userLocation, sessionId)
                } catch (e: Exception) {
                    logger.error("Error executing $toolName: ${e.message}")

                    // Проверяем, не пытается ли модель вызвать похожий инструмент
                    val suggestion = suggestCorrectToolName(toolName, enabledTools)
                    if (suggestion != null) {
                        logger.warn("⚠️ Tool '$toolName' not found. Did you mean '$suggestion'?")
                        """{"error": "Tool '$toolName' not found. Did you mean '$suggestion'? Use EXACT tool name: $suggestion"}"""
                    } else {
                        """{"error": "${e.message}"}"""
                    }
                }

                logger.info("Tool result for $toolName: ${result.take(200)}")

                // Отправляем результат через WebSocket
                if (sessionId != null && showIntermediateMessages) {
                    sendToolResultViaWebSocket(sessionId, toolName, toolArgs, result, iteration, index + 1)
                }

                // Добавляем результат инструмента
                messages.add(OllamaMessage(
                    role = "tool",
                    content = result
                ))
            }
        }

        // Достигнут лимит итераций
        logger.warn("⚠️ Reached max tool iterations ($MAX_TOOL_ITERATIONS)")
        return Triple(
            "⚠️ Достигнут лимит итераций ($MAX_TOOL_ITERATIONS). Задача может быть не завершена.",
            totalTokens,
            intermediateMessages
        )
    }

    /**
     * Подсказывает правильное имя инструмента на основе похожести
     */
    private fun suggestCorrectToolName(wrongName: String, enabledTools: List<String>): String? {
        val normalizedWrong = wrongName.lowercase().replace("_", "")

        // Карта распространенных ошибок
        val commonMistakes = mapOf(
            "get_weather" to "get_weather_forecast",
            "get_current_weather" to "get_weather_forecast",
            "weather" to "get_weather_forecast",
            "current_weather" to "get_weather_forecast",
            "check_solar_activity" to "get_solar_activity",
            "solar_activity" to "get_solar_activity",
            "solar" to "get_solar_activity",
            "get_solar" to "get_solar_activity"
        )

        // Проверяем точное совпадение в карте ошибок
        val directMatch = commonMistakes[wrongName.lowercase()]
        if (directMatch != null && enabledTools.contains(directMatch)) {
            return directMatch
        }

        // Ищем наиболее похожий инструмент
        return enabledTools.maxByOrNull { tool ->
            val normalizedTool = tool.lowercase().replace("_", "")
            // Простая метрика похожести: количество общих символов
            normalizedWrong.toSet().intersect(normalizedTool.toSet()).size
        }
    }

    /**
     * Отправляет результат инструмента через WebSocket
     */
    private suspend fun sendToolResultViaWebSocket(
        sessionId: String,
        toolName: String,
        toolInput: JsonObject,
        toolResult: String,
        iteration: Int,
        toolIndex: Int
    ) {
        try {
            val toolResultData = buildJsonObject {
                put("tool_name", toolName)
                put("tool_input", toolInput)
                put("tool_result", toolResult)
                put("iteration", iteration)
                put("tool_index", toolIndex)
                put("timestamp", System.currentTimeMillis())
            }

            webSocketService.broadcastToSession(
                sessionId = sessionId,
                message = WebSocketMessage(
                    type = "tool_result",
                    sessionId = sessionId,
                    data = toolResultData.toString()
                )
            )
            logger.info("📡 Tool result sent via WebSocket: $toolName")
        } catch (e: Exception) {
            logger.warn("Failed to send tool result via WebSocket: ${e.message}")
        }
    }
}

