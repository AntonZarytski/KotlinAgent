package com.claude.agent.routes

import com.claude.agent.config.ErrorMessages
import com.claude.agent.database.ConversationRepository
import com.claude.agent.models.*
import com.claude.agent.llm.LlmProviderFactory
import com.claude.agent.llm.SystemPrompts
import com.claude.agent.llm.mcp.MCPTools
import com.claude.agent.service.HistoryCompressor
import com.claude.agent.service.SpeechRecognitionService
import com.claude.agent.service.WebSocketService
import com.claude.agent.service.WebSocketMessage
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Роуты для работы с чатом.
 *
 * Аналог эндпоинтов /api/chat и /api/count_tokens из App.py.
 */
fun Route.chatRoutes(
    llmProviderFactory: LlmProviderFactory,
    mcpTools: MCPTools,
    historyCompressor: HistoryCompressor,
    repository: ConversationRepository,
    speechRecognitionService: SpeechRecognitionService? = null,
    webSocketService: WebSocketService
) {
    val logger = LoggerFactory.getLogger("ChatRoutes")

    /**
     * POST /api/chat - основной эндпоинт для общения с Claude.
     */
    post("/api/chat") {
        try {
            val request = call.receive<ChatRequest>()
            logger.info("Получен запрос на /api/chat")

            // Валидация
            if (request.message.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorMessages.EMPTY_MESSAGE))
                return@post
            }

            // Валидация параметров
            val maxTokens = request.max_tokens.coerceIn(128, 8192)
            val temperature = request.temperature.coerceIn(0.0, 2.0)
            val topP = request.top_p.coerceIn(0.0, 1.0)
            val topK = request.top_k.coerceIn(1, 100)
            val contextWindow = request.context_window.coerceIn(512, 32768)

            logger.info("Параметры: max_tokens=$maxTokens, " +
                    "spec_mode=${request.spec_mode}, history_len=${request.conversation_history.size}, " +
                    "temperature=$temperature, session_id=${request.session_id}, " +
                    "enabled_tools=${request.enabled_tools}")

            // Сохраняем сообщение пользователя в БД
            if (request.session_id != null) {
                repository.saveMessage(request.session_id, "user", request.message)
            }

            // Сжимаем историю при необходимости
            var conversationHistory = request.conversation_history
            val originalHistoryLen = conversationHistory.size
            var compressionApplied = false

            if (historyCompressor.shouldCompress(conversationHistory)) {
                logger.info("Начинаем сжатие истории ($originalHistoryLen сообщений)...")
                conversationHistory = historyCompressor.compressHistory(conversationHistory)
                compressionApplied = true
                logger.info("История сжата: $originalHistoryLen -> ${conversationHistory.size} сообщений")
            }

            // Получаем IP-адрес клиента для автоматического определения местоположения
            val clientIp = call.request.origin.remoteHost
            logger.info("IP клиента: $clientIp")

            // Логируем информацию о геолокации
            if (request.user_location != null) {
                logger.info("Получена геолокация от браузера: lat=${request.user_location.latitude}, lon=${request.user_location.longitude}")
            }

            // Обработка команды /help - теперь через LLM для генерации красивого ответа
            val isHelpCommand = request.message.trim().startsWith("/help")
            val actualMessage = if (isHelpCommand) {
                val helpQuery = request.message.trim().removePrefix("/help").trim()

                // Вызываем project_help tool для получения контекста
                val helpResult = mcpTools.callLocalTool(
                    toolName = "project_help",
                    arguments = JsonObject(
                        mapOf(
                            "query" to JsonPrimitive(helpQuery)
                        )
                    ),
                    clientIp = clientIp,
                    userLocation = request.user_location,
                    sessionId = request.session_id
                )

                val helpJson = kotlinx.serialization.json.Json.parseToJsonElement(helpResult).jsonObject
                val mode = helpJson["mode"]?.jsonPrimitive?.content ?: "search"
                val context = helpJson["context"]?.jsonPrimitive?.content ?: ""
                val toolsInfo = helpJson["tools_info"]?.jsonPrimitive?.content ?: ""

                // Формируем запрос для LLM на основе режима
                if (mode == "overview" || mode == "overview_static") {
                    // Общая справка - просим LLM создать структурированный обзор
                    """На основе следующей информации создай подробную справку по проекту KotlinAgent.

Информация из документации:
$context

Доступные MCP Tools:
$toolsInfo

Создай красивый, структурированный обзор проекта с разделами:
1. Краткое описание проекта
2. Архитектура и основные компоненты
3. Доступные MCP Tools (используй информацию выше)
4. API Endpoints
5. Примеры использования
6. Полезные советы

Используй эмодзи для улучшения читаемости. Будь кратким, но информативным."""
                } else {
                    // Поиск по конкретной теме
                    """Вопрос пользователя: $helpQuery

Найденная информация в документации:
$context

На основе этой информации дай подробный и структурированный ответ на вопрос пользователя.
Используй markdown для форматирования. Будь конкретным и полезным."""
                }
            } else {
                request.message
            }

            // Выбираем LLM провайдер через фабрику
            val llmProvider = llmProviderFactory.getProvider(request.llm_provider)
            val llmType = request.llm_provider ?: "claude" // Определяем тип LLM для оптимизации промпта
            logger.info("Using LLM provider: ${llmProvider.getProviderName()}, type: $llmType")

            // Формируем системный промпт с учетом типа LLM
            val systemPrompt = SystemPrompts.getSystemPrompt(
                enabledTools = if (isHelpCommand) emptyList() else request.enabled_tools,
                specMode = request.spec_mode,
                isRagEnabled = false,
                llmType = llmType
            )

            // Формируем сообщения для провайдера
            val messages = conversationHistory + Message(
                role = "user",
                content = actualMessage,
                timestamp = java.time.Instant.now().toString()
            )

            // Вызываем LLM провайдер
            val llmResponse = llmProvider.generate(
                systemPrompt = systemPrompt,
                messages = messages,
                model = null, // Используем модель по умолчанию для провайдера
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP,
                topK = topK,
                contextWindow = contextWindow,
                enabledTools = if (isHelpCommand) emptyList() else request.enabled_tools,
                clientIp = clientIp,
                userLocation = request.user_location,
                sessionId = request.session_id,
                showIntermediateMessages = request.show_intermediate_messages,
                useRag = request.use_rag,
                ragTopK = request.rag_top_k,
                ragMinSimilarity = request.rag_min_similarity,
                ragFilterEnabled = request.rag_filter_enabled,
                selectedFiles = request.selected_files
            )

            // Обработка ошибок
            if (llmResponse.error != null) {
                logger.error("Ошибка от LLM: ${llmResponse.error}")
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(llmResponse.error))
                return@post
            }

            // Сохраняем ответ ассистента в БД
            if (request.session_id != null && llmResponse.reply != null) {
                repository.saveMessage(
                    sessionId = request.session_id,
                    role = "assistant",
                    content = llmResponse.reply,
                    inputTokens = llmResponse.usage?.input_tokens,
                    outputTokens = llmResponse.usage?.output_tokens
                )
            }

            // Формируем ответ
            val response = ChatResponse(
                reply = llmResponse.reply ?: "",
                usage = llmResponse.usage,
                compressed_history = if (compressionApplied) conversationHistory else null,
                compression_applied = compressionApplied,
                intermediate_messages = llmResponse.intermediateMessages
            )

            call.respond(HttpStatusCode.OK, response)

        } catch (e: Exception) {
            logger.error("Ошибка в /api/chat: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
        }
    }

    /**
     * POST /api/count_tokens - подсчёт токенов для сообщения.
     */
    post("/api/count_tokens") {
        try {
            val request = call.receive<CountTokensRequest>()

            if (request.message.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorMessages.EMPTY_MESSAGE))
                return@post
            }

            logger.info("Подсчёт токенов: spec=${request.spec_mode}, " +
                    "history_len=${request.conversation_history.size}")
            // Формируем системный промпт и сообщения
            val llmType = request.llm_provider ?: "claude"
            val systemPrompt = SystemPrompts.getSystemPrompt(
                enabledTools = emptyList(),
                specMode = request.spec_mode,
                isRagEnabled = false,
                llmType = llmType
            )
            val messages = mutableListOf<Message>()
            messages.addAll(request.conversation_history)
            messages.add(Message("user", request.message))

            // Приблизительный подсчёт токенов (1 токен ≈ 4 символа для английского, ~2 для русского)
            val totalText = systemPrompt + messages.joinToString("") { it.content }
            val estimatedTokens = (totalText.length / 2.5).toInt()

            logger.info("Подсчитано: ~$estimatedTokens токенов")

            call.respond(HttpStatusCode.OK, CountTokensResponse(estimatedTokens))

        } catch (e: Exception) {
            logger.error("Ошибка в /api/count_tokens: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
        }
    }

    /**
     * POST /api/sessions/{sessionId}/mark_read - пометить все сообщения сессии как прочитанные.
     */
    post("/api/sessions/{sessionId}/mark_read") {
        try {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Session ID is required"))
                return@post
            }

            val updatedCount = repository.markMessagesAsRead(sessionId)
            logger.info("Marked $updatedCount messages as read in session: $sessionId")

            call.respond(HttpStatusCode.OK, mapOf("updated" to updatedCount))

        } catch (e: Exception) {
            logger.error("Ошибка в /api/sessions/{sessionId}/mark_read: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
        }
    }

    /**
     * POST /api/voice/chat - голосовой чат с распознаванием речи
     *
     * Принимает аудио файл, распознает речь и отправляет текст в LLM
     */
    post("/api/voice/chat") {
        try {
            if (speechRecognitionService == null) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("Сервис распознавания речи не настроен. Проверьте конфигурацию SPEECH_MODEL_PATH в .env")
                )
                return@post
            }

            logger.info("Получен запрос на /api/voice/chat")

            // Получаем multipart данные
            val multipart = call.receiveMultipart()
            var audioFile: File? = null
            var requestParams: VoiceChatRequest? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        // Сохраняем аудио файл во временную директорию
                        val originalFileName = part.originalFileName
                        val contentType = part.contentType?.toString()

                        val suffix = when {
                            contentType?.contains("webm", ignoreCase = true) == true ||
                                originalFileName?.endsWith(".webm", ignoreCase = true) == true -> ".webm"
                            contentType?.contains("wav", ignoreCase = true) == true ||
                                originalFileName?.endsWith(".wav", ignoreCase = true) == true -> ".wav"
                            contentType?.contains("ogg", ignoreCase = true) == true ||
                                originalFileName?.endsWith(".ogg", ignoreCase = true) == true -> ".ogg"
                            contentType?.contains("mpeg", ignoreCase = true) == true ||
                                originalFileName?.endsWith(".mp3", ignoreCase = true) == true -> ".mp3"
                            else -> ".bin"
                        }

                        // Если вдруг прилетит несколько file-part'ов, не оставляем мусор
                        audioFile?.delete()
                        audioFile = File.createTempFile("voice_", suffix)

                        logger.info(
                            "Получен аудио part: originalFileName=$originalFileName, contentType=$contentType, savedAs=${audioFile!!.name}"
                        )
                        part.streamProvider().use { input ->
                            audioFile!!.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        val headerBytes = readFirstBytes(audioFile!!, 32)
                        val headerHex = toHex(headerBytes)
                        val detected = detectAudioContainer(headerBytes)
                        logger.info(
                            "Получен аудио файл: ${audioFile!!.length()} байт, header=${headerHex}, detected=${detected}"
                        )
                    }
                    is PartData.FormItem -> {
                        // Парсим параметры запроса из JSON
                        if (part.name == "params") {
                            requestParams = kotlinx.serialization.json.Json.decodeFromString<VoiceChatRequest>(part.value)
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            // Валидация
            if (audioFile == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Аудио файл не предоставлен"))
                return@post
            }

            if (requestParams == null) {
                requestParams = VoiceChatRequest() // Используем параметры по умолчанию
            }

            val request = requestParams!!

            // Валидация размера файла (максимум 10 МБ)
            val maxFileSize = 10 * 1024 * 1024 // 10 MB
            if (audioFile!!.length() > maxFileSize) {
                audioFile!!.delete()
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Аудио файл слишком большой (максимум 10 МБ)"))
                return@post
            }

            // Распознаем речь с таймаутом и валидацией
            val recognizedText = try {
                // Таймаут 60 секунд, максимальная длительность аудио 60 секунд
                speechRecognitionService.recognizeFromFile(
                    audioFile = audioFile!!,
                    timeoutMs = 60000,
                    maxDurationSeconds = 60.0
                )
            } catch (e: com.claude.agent.service.SpeechRecognitionException) {
                logger.error("Ошибка распознавания речи: ${e.message}", e)
                audioFile!!.delete()

                // Определяем тип ошибки и возвращаем соответствующий HTTP статус
                val (statusCode, errorMessage) = when {
                    e.message?.contains("Неверный формат WAV", ignoreCase = true) == true ||
                    e.message?.contains("ожидается WAV", ignoreCase = true) == true ||
                    e.message?.contains("RIFF", ignoreCase = true) == true ||
                    e.message?.contains("WAVE", ignoreCase = true) == true ||
                    e.message?.contains("Конвертация не дала WAV", ignoreCase = true) == true ||
                    e.message?.contains("Аудио формат должен быть PCM", ignoreCase = true) == true ||
                    e.message?.contains("Частота дискретизации должна быть 16000 Hz", ignoreCase = true) == true ||
                    e.message?.contains("Аудио должно быть моно", ignoreCase = true) == true ||
                    e.message?.contains("Разрядность должна быть 16 бит", ignoreCase = true) == true -> {
                        HttpStatusCode.BadRequest to "Неверный формат аудио. Требуется: WAV, 16kHz, моно, 16-бит PCM. ${e.message}"
                    }
                    e.message?.contains("Аудио слишком длинное") == true -> {
                        HttpStatusCode.BadRequest to "Аудио файл слишком длинный. ${e.message}"
                    }
                    e.message?.contains("Превышено время ожидания") == true -> {
                        HttpStatusCode.RequestTimeout to "Превышено время ожидания распознавания речи. Попробуйте с более коротким аудио."
                    }
                    e.message?.contains("Модель не загружена") == true -> {
                        HttpStatusCode.ServiceUnavailable to "Модель распознавания речи не загружена. ${e.message}"
                    }
                    else -> {
                        HttpStatusCode.InternalServerError to "Ошибка распознавания речи: ${e.message}"
                    }
                }

                call.respond(statusCode, ErrorResponse(errorMessage))
                return@post
            } catch (e: Exception) {
                logger.error("Неожиданная ошибка при распознавании речи: ${e.message}", e)
                audioFile!!.delete()
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Неожиданная ошибка: ${e.message}"))
                return@post
            } finally {
                // Удаляем временный файл
                audioFile?.delete()
            }

            if (recognizedText.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Не удалось распознать речь. Попробуйте говорить четче."))
                return@post
            }

            logger.info("Распознанный текст: '$recognizedText'")

            // Сохраняем распознанное сообщение пользователя в БД
            if (request.session_id != null) {
                repository.saveMessage(request.session_id, "user", recognizedText)
            }

            // 🔥 КРИТИЧНО: Сразу возвращаем recognized_text клиенту
            // (чтобы UI мог показать сообщение пользователя ДО ответа ассистента)
            val immediateResponse = VoiceChatResponse(
                recognized_text = recognizedText,
                reply = "",  // Пустой reply - финальный ответ придёт через WebSocket
                usage = null,
                compressed_history = null,
                compression_applied = false,
                intermediate_messages = emptyList()
            )
            call.respond(HttpStatusCode.OK, immediateResponse)
            logger.info("✅ Recognized text sent to client immediately")

            // 🔥 Запускаем LLM генерацию АСИНХРОННО (не блокируем HTTP response)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Получаем IP клиента
                    val clientIp = call.request.origin.remoteHost

                    // Валидация параметров
                    val maxTokens = request.max_tokens.coerceIn(128, 8192)
                    val temperature = request.temperature.coerceIn(0.0, 2.0)
                    val topP = request.top_p.coerceIn(0.0, 1.0)
                    val topK = request.top_k.coerceIn(1, 100)
                    val contextWindow = request.context_window.coerceIn(512, 32768)

                    // Сжимаем историю при необходимости
                    var conversationHistory = request.conversation_history
                    val originalHistoryLen = conversationHistory.size
                    var compressionApplied = false

                    if (historyCompressor.shouldCompress(conversationHistory)) {
                        logger.info("Начинаем сжатие истории ($originalHistoryLen сообщений)...")
                        conversationHistory = historyCompressor.compressHistory(conversationHistory)
                        compressionApplied = true
                        logger.info("История сжата: $originalHistoryLen -> ${conversationHistory.size} сообщений")
                    }

                    val messages = mutableListOf<Message>()
                    messages.addAll(conversationHistory)
                    messages.add(Message("user", recognizedText))

                    // Выбираем LLM провайдер
                    val llmProvider = llmProviderFactory.getProvider(request.llm_provider)
                    val llmType = request.llm_provider ?: "claude"
                    logger.info("Using LLM provider: ${llmProvider.getProviderName()}, type: $llmType")

                    // Формируем системный промпт
                    val systemPrompt = SystemPrompts.getSystemPrompt(
                        enabledTools = request.enabled_tools,
                        specMode = request.spec_mode,
                        isRagEnabled = false,
                        llmType = llmType
                    )

                    // Вызываем LLM провайдер (финальный ответ отправится через WebSocket)
                    val llmResponse = llmProvider.generate(
                        systemPrompt = systemPrompt,
                        messages = messages,
                        model = null,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        topP = topP,
                        topK = topK,
                        contextWindow = contextWindow,
                        enabledTools = request.enabled_tools,
                        clientIp = clientIp,
                        userLocation = request.user_location,
                        sessionId = request.session_id,
                        showIntermediateMessages = request.show_intermediate_messages,
                        useRag = request.use_rag,
                        ragTopK = request.rag_top_k,
                        ragMinSimilarity = request.rag_min_similarity,
                        ragFilterEnabled = request.rag_filter_enabled,
                        selectedFiles = request.selected_files
                    )

                    // Обработка ошибок
                    if (llmResponse.error != null) {
                        logger.error("Ошибка от LLM: ${llmResponse.error}")
                        // Отправляем ошибку через WebSocket
                        if (request.session_id != null) {
                            webSocketService.broadcastToSession(
                                sessionId = request.session_id,
                                message = WebSocketMessage(
                                    type = "error",
                                    sessionId = request.session_id,
                                    data = """{"error": "${llmResponse.error}"}"""
                                )
                            )
                        }
                        return@launch
                    }

                    // Сохраняем ответ ассистента в БД
                    if (request.session_id != null && llmResponse.reply != null) {
                        repository.saveMessage(
                            sessionId = request.session_id,
                            role = "assistant",
                            content = llmResponse.reply,
                            inputTokens = llmResponse.usage?.input_tokens,
                            outputTokens = llmResponse.usage?.output_tokens
                        )
                    }

                    logger.info("✅ LLM response completed and sent via WebSocket")
                } catch (e: Exception) {
                    logger.error("Ошибка в асинхронной LLM генерации: ${e.message}", e)
                }
            }

        } catch (e: Exception) {
            logger.error("Ошибка в /api/voice/chat: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Ошибка сервера: ${e.message}"))
        }
    }
}

private fun readFirstBytes(file: File, maxLen: Int): ByteArray {
    if (maxLen <= 0) return ByteArray(0)
    return file.inputStream().use { input ->
        val buf = ByteArray(maxLen)
        val read = input.read(buf)
        if (read <= 0) ByteArray(0) else buf.copyOf(read)
    }
}

private fun toHex(bytes: ByteArray): String =
    bytes.joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }

/**
 * Грубое определение контейнера/формата по magic-bytes (только для логов/диагностики).
 */
private fun detectAudioContainer(header: ByteArray): String {
    fun hasPrefix(vararg b: Int): Boolean {
        if (header.size < b.size) return false
        for (i in b.indices) {
            if ((header[i].toInt() and 0xFF) != b[i]) return false
        }
        return true
    }

    val isWav = header.size >= 12 &&
        header[0] == 'R'.code.toByte() &&
        header[1] == 'I'.code.toByte() &&
        header[2] == 'F'.code.toByte() &&
        header[3] == 'F'.code.toByte() &&
        header[8] == 'W'.code.toByte() &&
        header[9] == 'A'.code.toByte() &&
        header[10] == 'V'.code.toByte() &&
        header[11] == 'E'.code.toByte()

    if (hasPrefix(0x1A, 0x45, 0xDF, 0xA3)) return "webm/ebml"
    if (isWav) return "wav/riff"
    if (hasPrefix(0x4F, 0x67, 0x67, 0x53)) return "ogg/oggs"
    if (hasPrefix(0x49, 0x44, 0x33)) return "mp3/id3"

    // MP4/M4A: [size:4 bytes][ftyp]
    val isFtyp = header.size >= 8 &&
        header[4] == 'f'.code.toByte() &&
        header[5] == 't'.code.toByte() &&
        header[6] == 'y'.code.toByte() &&
        header[7] == 'p'.code.toByte()
    if (isFtyp) return "mp4/m4a(ftyp)"

    // MP3 frame sync
    if (header.size >= 2) {
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) return "mp3(frame)"
    }

    return "unknown"
}
