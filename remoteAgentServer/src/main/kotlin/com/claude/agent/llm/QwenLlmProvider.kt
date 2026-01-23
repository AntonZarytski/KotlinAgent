package com.claude.agent.llm

import com.claude.agent.common.database.normalizeToRange
import com.claude.agent.config.localModel
import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.models.Message
import com.claude.agent.models.TokenUsage
import com.claude.agent.models.UserLocation
import com.claude.agent.service.OllamaEmbeddingClient
import com.claude.agent.service.RagService
import com.claude.agent.service.WebSocketMessage
import com.claude.agent.service.WebSocketService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Провайдер для локальной Qwen модели через Ollama API
 *
 * Поддерживает:
 * - Tool calling через Ollama API
 * - Итеративное выполнение инструментов
 * - WebSocket уведомления
 * - RAG интеграцию
 * - Чтение выбранных файлов
 */
class QwenLlmProvider(
    private val httpClient: HttpClient,
    private val mcpTools: MCPTools,
    private val webSocketService: WebSocketService,
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = localModel,
    private val ragService: RagService? = null,
    private val ollamaEmbeddingClient: OllamaEmbeddingClient? = null
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
        val num_predict: Int? = null,
        val top_p: Double? = null,
        val top_k: Int? = null,
        val num_ctx: Int? = null  // Контекстное окно
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
        topP: Double,
        topK: Int,
        contextWindow: Int,
        enabledTools: List<String>,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?,
        showIntermediateMessages: Boolean,
        useRag: Boolean,
        ragTopK: Int,
        ragMinSimilarity: Double,
        ragFilterEnabled: Boolean,
        selectedFiles: List<String>
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

            // Получаем контекст выбранных файлов
            val fileContext = if (selectedFiles.isNotEmpty()) {
                retrieveFileContext(selectedFiles, sessionId)
            } else null

            // Получаем RAG контекст
            val ragContext = if (useRag) {
                val query = messages.lastOrNull()?.content ?: ""
                retrieveRagContext(query, ragTopK, ragMinSimilarity, ragFilterEnabled)
            } else null

            // Логируем полученные контексты
            if (fileContext != null) {
                logger.info("📂 File context retrieved: ${fileContext.length} chars")
            }
            if (ragContext != null) {
                logger.info("🔍 RAG context retrieved: ${ragContext.length} chars")
            }

            // Дополняем последнее сообщение пользователя контекстом
            val augmentedMessages = if (fileContext != null || ragContext != null) {
                val lastMessage = messages.lastOrNull()
                if (lastMessage != null) {
                    val contextParts = mutableListOf<String>()
                    if (fileContext != null) contextParts.add(fileContext)
                    if (ragContext != null) contextParts.add(ragContext)

                    val augmentedContent = contextParts.joinToString("\n\n") + "\n\n" + lastMessage.content

                    messages.dropLast(1) + lastMessage.copy(content = augmentedContent)
                } else {
                    messages
                }
            } else {
                messages
            }

            // Формируем сообщения для Ollama с дополненным контекстом
            val ollamaMessages = buildOllamaMessages(systemPrompt, augmentedMessages)
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
                topP = topP,
                topK = topK,
                contextWindow = contextWindow,
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

    /**
     * Получает контекст выбранных файлов
     */
    private suspend fun retrieveFileContext(
        selectedFiles: List<String>,
        sessionId: String?
    ): String? {
        if (selectedFiles.isEmpty()) {
            return null
        }

        return try {
            logger.info("📂 Processing ${selectedFiles.size} selected files...")

            val fileContents = mutableListOf<String>()

            // Расширения бинарных файлов, для которых не нужно читать содержимое
            val binaryExtensions = setOf(
                "aab", "apk", "jar", "aar", "so", "a", "o",
                "zip", "tar", "gz", "7z", "rar",
                "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico",
                "mp3", "mp4", "avi", "mov", "wav", "flac",
                "pdf", "doc", "docx", "xls", "xlsx",
                "class", "dex", "bin", "exe", "dll"
            )

            for (filePath in selectedFiles) {
                try {
                    val fileName = filePath.substringAfterLast('/')
                    val extension = fileName.substringAfterLast('.', "").lowercase()
                    val hasExtension = fileName.contains('.')
                    val isBinary = extension in binaryExtensions

                    // Если нет расширения - скорее всего это папка
                    if (!hasExtension) {
                        fileContents.add("""
                            |Directory: $filePath
                            |Type: Directory
                            |Note: This is a directory path. The full path is available for use with tools.
                        """.trimMargin())
                        logger.info("📁 Directory: $filePath")
                    } else if (isBinary) {
                        // Для бинарных файлов просто указываем путь
                        fileContents.add("""
                            |File: $filePath
                            |Type: Binary file (.$extension)
                            |Note: This is a binary file. The full path is available for use with tools.
                        """.trimMargin())
                        logger.info("📦 Binary file: $filePath (.$extension)")
                    } else {
                        // Для текстовых файлов читаем содержимое
                        val result = mcpTools.callLocalTool(
                            toolName = "android_studio",
                            arguments = buildJsonObject {
                                put("action", "read_file")
                                put("file_path", filePath)
                            },
                            clientIp = null,
                            userLocation = null,
                            sessionId = sessionId
                        )

                        val resultJson = Json.parseToJsonElement(result).jsonObject
                        val content = resultJson["content"]?.jsonPrimitive?.content

                        if (content != null) {
                            fileContents.add("""
                                |File: $filePath
                                |```
                                |$content
                                |```
                            """.trimMargin())
                            logger.info("✅ Read text file: $filePath (${content.length} chars)")
                        } else {
                            logger.warn("⚠️ Failed to read file: $filePath")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("❌ Error processing file $filePath: ${e.message}")
                }
            }

            if (fileContents.isEmpty()) {
                return null
            }

            """
            |<selected_files>
            |The user has selected the following files for context:
            |
            |${fileContents.joinToString("\n\n")}
            |</selected_files>
            """.trimMargin()

        } catch (e: Exception) {
            logger.error("Failed to retrieve file context: ${e.message}", e)
            null
        }
    }

    /**
     * Получает RAG контекст для запроса
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


    override fun isConfigured(): Boolean {
        return try {
            // Проверяем доступность Ollama сервера
            runBlocking {
                val response = httpClient.get("$baseUrl/api/tags")
                val isAvailable = response.status.isSuccess()
                if (!isAvailable) {
                    logger.warn("Ollama server not available at $baseUrl: ${response.status}")
                }
                isAvailable
            }
        } catch (e: Exception) {
            logger.error("Failed to check Ollama availability: ${e.message}")
            false
        }
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
        topP: Double,
        topK: Int,
        contextWindow: Int,
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

        // Счетчик повторяющихся ошибок для предотвращения бесконечных циклов
        val errorHistory = mutableListOf<String>()
        val maxConsecutiveErrors = 3

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
                    num_predict = maxTokens,
                    top_p = topP,
                    top_k = topK,
                    num_ctx = contextWindow
                )
            )

            // Логируем полный запрос для отладки
            logger.info("=== OLLAMA REQUEST ===")
            logger.info("URL: $baseUrl/api/chat")
            logger.info("Request body: ${Json.encodeToString(OllamaChatRequest.serializer(), request)}")
            logger.info("=== END REQUEST ===")

            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("Ollama API error: ${response.status}, body: $errorBody")
                logger.error("Request was: ${Json.encodeToString(OllamaChatRequest.serializer(), request)}")
                throw RuntimeException("Ollama API error: ${response.status} - $errorBody")
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
                logger.info("⚠️ No tool_calls from API, attempting to parse from content")
                logger.debug("Content to parse: ${assistantMessage.content}")

                // Проверяем теги <tool_call>
                if (assistantMessage.content.contains("<tool_call>")) {
                    logger.info("Parsing tool calls from <tool_call> tags in response")
                    toolCalls = parseToolCallsFromText(assistantMessage.content)
                    logger.info("Parsed ${toolCalls?.size ?: 0} tool calls from <tool_call> tags")
                }
                // Проверяем, является ли весь ответ JSON с вызовом инструмента
                else if (assistantMessage.content.trim().startsWith("{") &&
                         assistantMessage.content.trim().endsWith("}")) {
                    logger.info("Attempting to parse response as pure JSON tool call")
                    toolCalls = parseJsonToolCall(assistantMessage.content)
                    logger.info("Parsed ${toolCalls?.size ?: 0} tool calls from pure JSON")
                }
                // Проверяем JSON в markdown блоке ```json
                else if (assistantMessage.content.contains("```json")) {
                    logger.info("Attempting to parse tool call from markdown JSON block")
                    toolCalls = parseJsonFromMarkdown(assistantMessage.content)
                    logger.info("Parsed ${toolCalls?.size ?: 0} tool calls from markdown JSON")
                }
                else {
                    logger.warn("⚠️ Content does not match any known tool call format")
                    logger.warn("   Content starts with: ${assistantMessage.content.take(100)}")
                    logger.warn("   Content ends with: ${assistantMessage.content.takeLast(100)}")
                }
            } else {
                logger.info("✅ Got ${toolCalls.size} tool calls from API")
            }

            if (toolCalls.isNullOrEmpty()) {
                // Проверяем, не является ли это обычным ответом в JSON формате
                val cleanedResponse = extractTextFromJsonResponse(assistantMessage.content)
                if (cleanedResponse != assistantMessage.content) {
                    logger.info("Extracted text from JSON response")
                    return Triple(cleanedResponse, totalTokens, intermediateMessages)
                }

                // Отправляем промежуточное сообщение через WebSocket
                if (sessionId != null && showIntermediateMessages) {
                    try {
                        val messageData = buildJsonObject {
                            put("role", "assistant")
                            put("content", assistantMessage.content)
                            put("is_intermediate", false)
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
                        logger.info("📡 Финальный ответ отправлен через WebSocket (iteration $iteration)")
                    } catch (e: Exception) {
                        logger.warn("Не удалось отправить финальный ответ через WebSocket: ${e.message}")
                    }
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
                var toolName = toolCall.function.name
                var toolArgs = toolCall.function.arguments

                logger.info("🔧 [$iteration/$MAX_TOOL_ITERATIONS] Tool call #${index + 1}: $toolName")

                // Пытаемся автоматически исправить неправильный вызов
                val fixed = fixToolCall(toolName, toolArgs)
                if (fixed != null) {
                    toolName = fixed.first
                    toolArgs = fixed.second
                    logger.info("✅ Tool call auto-fixed to: $toolName")
                }

                // Валидация обязательных параметров для android_studio_mcp
                val action = toolArgs["action"]?.jsonPrimitive?.contentOrNull
                val validationError = if (toolName == "android_studio_mcp" && action != null) {
                    validateRequiredParameters(toolName, action, toolArgs)
                } else null

                val result = if (validationError != null) {
                    // Параметры отсутствуют - прерываем цикл и просим пользователя предоставить их
                    logger.warn("⚠️ Validation failed: $validationError")
                    errorHistory.add("validation_error:$action")

                    // Проверяем, не повторяется ли эта ошибка
                    if (errorHistory.takeLast(maxConsecutiveErrors).all { it.startsWith("validation_error:$action") }) {
                        logger.error("❌ Same validation error repeated $maxConsecutiveErrors times. Breaking loop.")
                        // Возвращаем финальный ответ с просьбой к пользователю
                        return Triple(
                            "Для выполнения действия '$action' требуется дополнительная информация: $validationError\n\nПожалуйста, предоставьте необходимые данные.",
                            totalTokens,
                            intermediateMessages
                        )
                    }

                    """{"error": "$validationError", "status": "missing_parameters"}"""
                } else {
                    try {
                        mcpTools.callLocalTool(toolName, toolArgs, clientIp, userLocation, sessionId)
                    } catch (e: Exception) {
                        logger.error("Error executing $toolName: ${e.message}")
                        val errorKey = "tool_error:$toolName:${e.message}"
                        errorHistory.add(errorKey)

                        // Проверяем повторяющиеся ошибки
                        if (errorHistory.takeLast(maxConsecutiveErrors).all { it == errorKey }) {
                            logger.error("❌ Same error repeated $maxConsecutiveErrors times. Breaking loop.")
                            return Triple(
                                "Не удалось выполнить операцию '$toolName' после $maxConsecutiveErrors попыток. Ошибка: ${e.message}\n\nПожалуйста, проверьте параметры или попробуйте другой подход.",
                                totalTokens,
                                intermediateMessages
                            )
                        }

                        // Проверяем, не пытается ли модель вызвать похожий инструмент
                        val suggestion = suggestCorrectToolName(toolName, enabledTools)
                        if (suggestion != null) {
                            logger.warn("⚠️ Tool '$toolName' not found. Did you mean '$suggestion'?")
                            """{"error": "Tool '$toolName' not found. Did you mean '$suggestion'? Use EXACT tool name: $suggestion"}"""
                        } else {
                            """{"error": "${e.message}"}"""
                        }
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
     * Проверяет обязательные параметры для инструмента
     * Возвращает сообщение об ошибке если параметры отсутствуют или пусты, иначе null
     */
    private fun validateRequiredParameters(toolName: String, action: String?, arguments: JsonObject): String? {
        // Определяем обязательные параметры для каждого действия android_studio_mcp
        val requiredParams = when (action) {
            "start_emulator" -> listOf("avd_name")
            "read_file" -> listOf("file_path")
            "browse_files" -> listOf("directory_path")
            "gradle_build" -> emptyList() // build_variant опционален
            "install_apk" -> listOf("apk_path")
            "run_app" -> listOf("package_name")
            "adb_shell" -> listOf("command")
            "read_file_lines" -> listOf("file_path")
            "find_files" -> listOf("pattern")
            "save_log" -> listOf("log_content")
            "set_project_path" -> listOf("project_path")
            else -> emptyList()
        }

        // Проверяем каждый обязательный параметр
        for (param in requiredParams) {
            val value = arguments[param]?.jsonPrimitive?.contentOrNull
            if (value.isNullOrBlank()) {
                logger.warn("⚠️ Missing or empty required parameter '$param' for action '$action'")
                return "Missing required parameter '$param' for action '$action'. Please provide a valid value."
            }
        }

        return null
    }

    /**
     * Автоматически исправляет неправильные вызовы инструментов
     * Возвращает пару (исправленное имя, исправленные аргументы) или null если исправление невозможно
     * Также исправляет неправильные имена параметров (например, directory_path -> project_path)
     */
    private fun fixToolCall(toolName: String, arguments: JsonObject): Pair<String, JsonObject>? {
        // Карта действий android_studio_mcp, которые модель может вызывать напрямую
        val androidStudioActions = setOf(
            "read_file", "browse_files", "start_emulator", "stop_emulator",
            "list_emulators", "gradle_build", "gradle_install_run",
            "install_apk", "run_app", "adb_shell", "screenshot",
            "logcat", "logcat_clear", "read_file_lines", "find_files", "save_log",
            "set_project_path", "get_project_path", "get_file_tree"
        )

        // Если модель вызвала действие напрямую, преобразуем в android_studio_mcp
        if (toolName in androidStudioActions) {
            logger.info("🔧 Auto-fixing tool call: $toolName -> android_studio_mcp with action=$toolName")

            // Создаем новые аргументы с action и исправленными именами параметров
            val fixedArguments = buildJsonObject {
                put("action", toolName)

                // Копируем аргументы с маппингом неправильных имен параметров
                arguments.forEach { (key, value) ->
                    // Исправляем неправильные имена параметров в зависимости от действия
                    val fixedKey = when {
                        // set_project_path требует project_path, а не directory_path
                        toolName == "set_project_path" && key == "directory_path" -> {
                            logger.info("🔧 Auto-fixing parameter name: directory_path -> project_path")
                            "project_path"
                        }
                        // browse_files требует directory_path (это правильно)
                        // read_file требует file_path (это правильно)
                        else -> key
                    }
                    put(fixedKey, value)
                }
            }

            return Pair("android_studio_mcp", fixedArguments)
        }

        return null
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

