package com.claude.agent.llm

import com.claude.agent.config.AppConfig
import com.claude.agent.config.ClaudeConfig
import com.claude.agent.config.SPEC_END_MARKER
import com.claude.agent.llm.mcp.REMINDER
import com.claude.agent.models.Message
import com.claude.agent.models.TokenUsage
import com.claude.agent.llm.mcp.MCPTools
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Клиент для работы с Claude API (Anthropic).
 *
 * Аналог claude_client.py из Python-версии.
 * Использует ktor-client для HTTP запросов к Anthropic API.
 */
class ClaudeClient(
    private val httpClient: HttpClient,
    private val mcpTools: MCPTools
) {
    private val logger = LoggerFactory.getLogger(ClaudeClient::class.java)
    private val apiKey = AppConfig.anthropicApiKey
    private val apiUrl = "https://api.anthropic.com/v1/messages"

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5

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
        sessionId: String? = null
    ): Triple<String?, TokenUsage?, String?> {
        try {
            // Формируем системный промпт
            val systemPrompt = SystemPrompts.getSystemPrompt(outputFormat = outputFormat, specMode = specMode, enabledTools = enabledTools)
            val cleanUserMessage = SystemPrompts.getUserMessage(userMessage)

            // Формируем массив сообщений с историей
            val messages = buildMessages(conversationHistory, cleanUserMessage)

            mcpTools.enableServers(enabledTools)
            // Получаем remote MCP серверы
            val remoteMcpParams = mcpTools.getRemoteMCP()

            // Получаем инструменты
            val localMcpParams = getFilteredTools(enabledTools, remoteMcpParams)

            // Формируем запрос
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
                // Добавляем beta header для поддержки MCP connector
                if (remoteMcpParams.isNotEmpty()) {
                    header("anthropic-beta", "mcp-client-2025-11-20")
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("Ошибка API: ${response.status}, body: $errorBody")
                return Triple(null, null, "Ошибка Claude API: ${response.status}")
            }

            val responseBody = response.body<JsonObject>()
            val elapsed = System.currentTimeMillis() - startTime

            // Обрабатываем ответ (с поддержкой tool_use)
            val (finalReply, totalUsage) = handleResponse(
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
                sessionId = sessionId
            )

            logger.info("Ответ получен за ${elapsed}ms")
            logger.info("Использовано токенов: input=${totalUsage?.input_tokens}, output=${totalUsage?.output_tokens}")

            return Triple(finalReply, totalUsage, null)

        } catch (e: Exception) {
            logger.error("Ошибка sendMessage: ${e.message}", e)
            return Triple(null, null, "Ошибка сервера: ${e.message}")
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
        sessionId: String?
    ): Pair<String, TokenUsage> {
        var currentResponse = responseBody
        val messages = initialMessages.toMutableList()
        var totalInputTokens = currentResponse["usage"]?.jsonObject?.get("input_tokens")?.jsonPrimitive?.int ?: 0
        var totalOutputTokens = currentResponse["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.int ?: 0

        var iteration = 0

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
                    return Pair("✅ Задача выполнена", usage)
                }

                return Pair(finalText, usage)
            }

            // Есть tool_use - обрабатываем
            iteration++
            val currentText = textBlocks.joinToString("")
            logger.info("=== Tool call iteration $iteration - Calling: ${content.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "tool_use" }.joinToString(", ") { it.jsonObject["name"]?.jsonPrimitive?.content ?: "unknown" }} ===")
            if (currentText.isNotBlank()) {
                logger.info("Text in this iteration: ${currentText.take(100)}...")
            }

            // Добавляем ответ ассистента в историю
            messages.add(JsonObject(mapOf(
                "role" to JsonPrimitive("assistant"),
                "content" to content
            )))

            // Выполняем все tool calls
            val toolResults = mutableListOf<JsonObject>()
            for (block in content) {
                val blockObj = block.jsonObject
                if (blockObj["type"]?.jsonPrimitive?.content == "tool_use") {
                    val toolName = blockObj["name"]?.jsonPrimitive?.content ?: continue
                    val toolInput = blockObj["input"]?.jsonObject ?: JsonObject(emptyMap())
                    val toolUseId = blockObj["id"]?.jsonPrimitive?.content ?: continue

                    logger.info("Tool call iteration $iteration - Calling: $toolName")

                    val result = try {
                        mcpTools.callLocalTool(toolName, toolInput, clientIp, userLocation, sessionId)
                    } catch (e: Exception) {
                        logger.error("Ошибка выполнения $toolName: ${e.message}")
                        """{"error": "${e.message}"}"""
                    }

                    logger.info("Tool result for $toolName: ${result.take(200)}...")

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

            // Повторный запрос к Claude
            val requestBody = buildAnthropicRequest(
                model = model,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt,
                messages = messages,
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
        logger.warn("Достигнут лимит tool call итераций ($MAX_TOOL_ITERATIONS)")

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
        return Pair(finalText, usage)
    }

    private fun buildMessages(history: List<Message>, userMessage: String): List<JsonObject> {
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

        // Добавляем текущее сообщение
        messages.add(JsonObject(mapOf(
            "role" to JsonPrimitive("user"),
            "content" to JsonPrimitive(userMessage)
        )))

        return messages
    }

    private fun getFilteredTools(enabledTools: List<String>, remoteMcp: JsonArray): JsonArray? {
        val allTools = mcpTools.getLocalToolsDefinitions(enabledTools)
        val filtered = allTools.filter { it.name in enabledTools }

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

            // Добавляем локальные инструменты (если есть)
            filtered.forEach { tool ->
                add(JsonObject(mapOf(
                    "name" to JsonPrimitive(tool.name),
                    "description" to JsonPrimitive(tool.description),
                    "input_schema" to tool.input_schema
                )))
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
            "system" to JsonPrimitive(systemPrompt),
            "messages" to JsonArray(messages),
            "temperature" to JsonPrimitive(temperature),
        )

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
}
