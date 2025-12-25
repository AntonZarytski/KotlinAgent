package com.claude.agent.routes

import com.claude.agent.config.ErrorMessages
import com.claude.agent.database.ConversationRepository
import com.claude.agent.models.*
import com.claude.agent.llm.ClaudeClient
import com.claude.agent.llm.SystemPrompts
import com.claude.agent.service.HistoryCompressor
import io.ktor.http.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

/**
 * Роуты для работы с чатом.
 *
 * Аналог эндпоинтов /api/chat и /api/count_tokens из App.py.
 */
fun Route.chatRoutes(
    claudeClient: ClaudeClient,
    historyCompressor: HistoryCompressor,
    repository: ConversationRepository
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
            val maxTokens = request.max_tokens.coerceIn(128, 4096)
            val temperature = request.temperature.coerceIn(0.0, 1.0)

            logger.info("Параметры: format=${request.output_format}, max_tokens=$maxTokens, " +
                    "spec_mode=${request.spec_mode}, history_len=${request.conversation_history.size}, " +
                    "temperature=$temperature, session_id=${request.session_id}, " +
                    "enabled_tools=${request.enabled_tools}, show_intermediate=${request.show_intermediate_messages}")

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

            // Отправляем запрос к Claude API
            val claudeResponse = claudeClient.sendMessage(
                userMessage = request.message,
                outputFormat = request.output_format,
                maxTokens = maxTokens,
                specMode = request.spec_mode,
                conversationHistory = conversationHistory,
                temperature = temperature,
                enabledTools = request.enabled_tools,
                clientIp = clientIp,
                userLocation = request.user_location,
                sessionId = request.session_id,
                showIntermediateMessages = request.show_intermediate_messages,
                useRag = request.use_rag,
                ragTopK = request.rag_top_k,
                ragMinSimilarity = request.rag_min_similarity,
                ragFilterEnabled = request.rag_filter_enabled
            )

            // Обработка ошибок
            if (claudeResponse.error != null) {
                logger.error("Ошибка от Claude API: ${claudeResponse.error}")
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(claudeResponse.error))
                return@post
            }

            // Сохраняем ответ ассистента в БД
            if (request.session_id != null && claudeResponse.reply != null) {
                repository.saveMessage(
                    sessionId = request.session_id,
                    role = "assistant",
                    content = claudeResponse.reply,
                    inputTokens = claudeResponse.usage?.input_tokens,
                    outputTokens = claudeResponse.usage?.output_tokens
                )
            }

            // Формируем ответ
            val response = ChatResponse(
                reply = claudeResponse.reply ?: "",
                usage = claudeResponse.usage,
                compressed_history = if (compressionApplied) conversationHistory else null,
                compression_applied = compressionApplied,
                intermediate_messages = claudeResponse.intermediateMessages
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

            logger.info("Подсчёт токенов: format=${request.output_format}, spec=${request.spec_mode}, " +
                    "history_len=${request.conversation_history.size}")
            // Формируем системный промпт и сообщения
            val systemPrompt = SystemPrompts.getSystemPrompt(outputFormat = request.output_format, specMode = request.spec_mode, enabledTools = emptyList(), isRagEnabled = false)
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
}
