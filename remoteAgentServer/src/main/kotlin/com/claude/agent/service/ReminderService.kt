package com.claude.agent.service

import com.claude.agent.database.ConversationRepository
import com.claude.agent.models.Reminder
import com.claude.agent.models.Message
import com.claude.agent.llm.LlmProvider
import com.claude.agent.llm.mcp.MCPTools
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class ReminderService(
    val conversationRepository: ConversationRepository,
    private val webSocketService: WebSocketService? = null
) {

    private val logger = LoggerFactory.getLogger(ReminderService::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // LlmProvider and MCPTools will be set after initialization to avoid circular dependency
    var llmProvider: LlmProvider? = null
    var mcpTools: MCPTools? = null

    // Map of reminder ID to its timer job
    private val reminderTimers = mutableMapOf<String, Job>()
    private val timersLock = Any()

    /**
     * Запускает индивидуальные таймеры для всех существующих напоминаний.
     * Вызывается при старте приложения.
     */
    fun startScheduler() {
        val activeReminders = conversationRepository.getReminders()
        if (activeReminders.isEmpty()) {
            logger.info("No active reminders to schedule")
            return
        }

        logger.info("Scheduling ${activeReminders.size} active reminders with individual timers")

        activeReminders.forEach { reminder ->
            scheduleReminder(reminder)
        }
    }

    /**
     * Создает индивидуальный таймер для конкретного напоминания.
     * Таймер запустится точно в момент due_at.
     */
    private fun scheduleReminder(reminder: Reminder) {
        synchronized(timersLock) {
            // Отменяем существующий таймер если есть
            reminderTimers[reminder.id]?.cancel()

            val dueAt = try {
                Instant.parse(reminder.due_at)
            } catch (e: Exception) {
                logger.error("Invalid due_at format for reminder ${reminder.id}: ${reminder.due_at}")
                return
            }

            val now = Instant.now()
            val delayMillis = dueAt.toEpochMilli() - now.toEpochMilli()

            if (delayMillis < 0) {
                // Напоминание просрочено - выполнить немедленно
                logger.info("Reminder ${reminder.id} is overdue, executing immediately")
                scope.launch {
                    executeReminder(reminder)
                }
                return
            }

            // Создаем таймер который запустится в нужное время
            val timerJob = scope.launch {
                try {
                    logger.info("Timer scheduled for reminder '${reminder.text}' (ID: ${reminder.id}) to fire in ${delayMillis / 1000} seconds")
                    delay(delayMillis)

                    // Время пришло - выполняем напоминание
                    executeReminder(reminder)
                } catch (e: CancellationException) {
                    logger.debug("Timer cancelled for reminder ${reminder.id}")
                } catch (e: Exception) {
                    logger.error("Error in reminder timer: ${e.message}", e)
                }
            }

            reminderTimers[reminder.id] = timerJob
        }
    }

    /**
     * Выполняет напоминание: отправляет уведомление и обрабатывает повторение.
     */
    private suspend fun executeReminder(reminder: Reminder) {
        logger.info("Executing reminder: ${reminder.text} (ID: ${reminder.id})")

        sendNotification(reminder)

        // Handle recurring reminders
        if (reminder.recurrenceType != "none") {
            handleRecurringReminder(reminder)
        } else {
            // One-time reminder - mark as notified and remove timer
            markNotified(reminder.id)
            synchronized(timersLock) {
                reminderTimers.remove(reminder.id)
            }
        }
    }

    /**
     * Отменяет таймер для конкретного напоминания.
     */
    private fun cancelReminderTimer(reminderId: String) {
        synchronized(timersLock) {
            reminderTimers[reminderId]?.cancel()
            reminderTimers.remove(reminderId)
            logger.debug("Timer cancelled for reminder: $reminderId")
        }
    }

    private fun sendNotification(reminder: Reminder) {
        // Log the reminder notification
        logger.info(
            """
            🔔 REMINDER DUE
            Text: ${reminder.text}
            Due at: ${reminder.due_at}
            Session: ${reminder.sessionId}
            Task Type: ${reminder.taskType}
            """.trimIndent()
        )

        // Handle different task types
        when (reminder.taskType) {
            "reminder" -> handleSimpleReminder(reminder)
            "ai_response" -> handleAIResponse(reminder)
            "mcp_tool" -> handleMCPTool(reminder)
            else -> {
                logger.error("Unknown task type: ${reminder.taskType}")
                handleSimpleReminder(reminder)
            }
        }
    }

    private fun handleSimpleReminder(reminder: Reminder) {
        // Если есть sessionId, отправляем сообщение в чат
        if (reminder.sessionId != null) {
            val reminderMessage = "🔔 **Напоминание:**\n\n${reminder.text}"
            val message = conversationRepository.saveMessage(
                sessionId = reminder.sessionId,
                role = "assistant",
                content = reminderMessage
            )
            logger.info("Reminder notification sent to session: ${reminder.sessionId}")

            // Broadcast via WebSocket for real-time delivery
            broadcastMessage(reminder.sessionId, message, reminder.text)
        }
    }

    private fun handleAIResponse(reminder: Reminder) {
        if (reminder.sessionId == null) {
            logger.error("Cannot execute AI response task without sessionId")
            return
        }

        if (llmProvider == null) {
            logger.error("LlmProvider not available for AI response task")
            handleSimpleReminder(reminder)
            return
        }

        // Parse task context to get user request and accumulated results
        val (userRequest, accumulatedResults) = try {
            val context = Json.parseToJsonElement(reminder.taskContext ?: "{}")
            val request = context.jsonObject["user_request"]?.jsonPrimitive?.content ?: reminder.text
            val results = context.jsonObject["accumulated_results"]?.jsonPrimitive?.content
            Pair(request, results)
        } catch (e: Exception) {
            logger.error("Error parsing task context: ${e.message}")
            Pair(reminder.text, null)
        }

        logger.info("Executing AI response task for request: $userRequest")
        if (accumulatedResults != null) {
            logger.info("Accumulated results available: ${accumulatedResults.take(200)}...")
        }

        // Execute AI request asynchronously
        scope.launch {
            try {
                // Get conversation history
                val history = conversationRepository.getSessionHistory(reminder.sessionId)

                // Build the prompt with accumulated results if available
                val promptMessage = if (accumulatedResults != null) {
                    """
                    Пользователь запросил: "$userRequest"

                    Уже были получены следующие результаты:
                    $accumulatedResults

                    Пожалуйста, представь эти результаты пользователю в удобном для чтения формате.
                    """.trimIndent()
                } else {
                    userRequest
                }

                val systemPrompt = "Ты - полезный AI ассистент. Отвечай на вопросы пользователя четко и по делу."

                val userMessage = Message(
                    role = "user",
                    content = promptMessage
                )

                val messages = history + userMessage

                // Call LLM provider
                val response = llmProvider!!.generate(
                    systemPrompt = systemPrompt,
                    messages = messages,
                    sessionId = reminder.sessionId,
                    enabledTools = emptyList()
                )

                if (response.error != null) {
                    logger.error("Error from LLM provider: ${response.error}")
                    handleSimpleReminder(reminder)
                } else if (response.reply != null) {
                    logger.info("AI response generated successfully for session: ${reminder.sessionId}")
                    // Save response to database
                    val message = conversationRepository.saveMessage(
                        sessionId = reminder.sessionId,
                        role = "assistant",
                        content = response.reply,
                        inputTokens = response.usage?.input_tokens,
                        outputTokens = response.usage?.output_tokens
                    )
                    broadcastMessage(reminder.sessionId, message, reminder.text)
                }
            } catch (e: Exception) {
                logger.error("Error executing AI response task: ${e.message}", e)
                // Fallback to simple reminder
                handleSimpleReminder(reminder)
            }
        }
    }

    private fun handleMCPTool(reminder: Reminder) {
        if (reminder.sessionId == null) {
            logger.error("Cannot execute MCP tool task without sessionId")
            return
        }

        if (mcpTools == null) {
            logger.error("MCPTools not available for MCP tool task")
            handleSimpleReminder(reminder)
            return
        }

        if (llmProvider == null) {
            logger.error("LlmProvider not available for MCP tool task")
            handleSimpleReminder(reminder)
            return
        }

        // Parse task context to get tool name, parameters, and original user request
        logger.info("⚙️ MCP Tool Task - Raw taskContext: ${reminder.taskContext}")

        val (toolName, toolArguments, userRequest) = try {
            val context = Json.parseToJsonElement(reminder.taskContext ?: "{}")
            val contextObj = context.jsonObject
            val name = contextObj["tool_name"]?.jsonPrimitive?.content ?: ""
            val args = contextObj["tool_arguments"]?.jsonObject ?: JsonObject(emptyMap())
            val request = contextObj["user_request"]?.jsonPrimitive?.content ?: ""
            Triple(name, args, request)
        } catch (e: Exception) {
            logger.error("❌ Error parsing MCP tool context: ${e.message}, taskContext: ${reminder.taskContext}")
            handleSimpleReminder(reminder)
            return
        }

        if (toolName.isEmpty()) {
            logger.error("❌ MCP tool name is empty, taskContext was: ${reminder.taskContext}")
            handleSimpleReminder(reminder)
            return
        }

        logger.info("⚙️ Executing MCP tool task: toolName='$toolName', arguments=$toolArguments, userRequest='$userRequest'")

        // Execute MCP tool asynchronously
        scope.launch {
            try {
                // Call the MCP tool to get raw result
                val toolResult = mcpTools?.callLocalTool(
                    toolName = toolName,
                    arguments = toolArguments,
                    clientIp = null,
                    userLocation = null,
                    sessionId = reminder.sessionId
                )

                logger.info("✅ MCP tool executed: toolName='$toolName', result length=${toolResult?.length ?: 0}")
                logger.debug("MCP tool result (first 500 chars): ${toolResult?.take(500)}")

                // Now ask LLM to format the tool result into a human-readable response
                // Get conversation history
                val history = conversationRepository.getSessionHistory(reminder.sessionId)

                // Create a prompt that asks LLM to interpret the tool result
                val interpretationPrompt = if (userRequest.isNotBlank()) {
                    "Пользователь запросил: \"$userRequest\"\n\nИнструмент '$toolName' вернул следующий результат:\n$toolResult\n\nПожалуйста, представь этот результат в удобном для чтения формате."
                } else {
                    "Инструмент '$toolName' вернул следующий результат:\n$toolResult\n\nПожалуйста, представь этот результат в удобном для чтения формате."
                }

                val systemPrompt = "Ты - полезный AI ассистент. Форматируй результаты инструментов в удобный для чтения вид."

                val userMessage = Message(
                    role = "user",
                    content = interpretationPrompt
                )

                val messages = history + userMessage

                // Call LLM provider to format the response
                val response = llmProvider!!.generate(
                    systemPrompt = systemPrompt,
                    messages = messages,
                    sessionId = reminder.sessionId,
                    enabledTools = emptyList()
                )

                if (response.error != null) {
                    logger.error("Error from LLM provider: ${response.error}")
                    // Fallback to raw result
                    val resultMessage = "🔧 **Результат автоматического вызова инструмента '$toolName':**\n\n$toolResult"
                    val message = conversationRepository.saveMessage(
                        sessionId = reminder.sessionId,
                        role = "assistant",
                        content = resultMessage
                    )
                    broadcastMessage(reminder.sessionId, message, reminder.text)
                } else if (response.reply != null) {
                    logger.info("MCP tool result formatted successfully by LLM")
                    // Save the formatted response
                    val message = conversationRepository.saveMessage(
                        sessionId = reminder.sessionId,
                        role = "assistant",
                        content = response.reply,
                        inputTokens = response.usage?.input_tokens,
                        outputTokens = response.usage?.output_tokens
                    )
                    broadcastMessage(reminder.sessionId, message, reminder.text)
                }

            } catch (e: Exception) {
                logger.error("Error executing MCP tool task: ${e.message}", e)
                // Send error message to chat
                val errorMessage = "❌ **Ошибка при выполнении инструмента '$toolName':**\n\n${e.message}"
                val message = conversationRepository.saveMessage(
                    sessionId = reminder.sessionId,
                    role = "assistant",
                    content = errorMessage
                )
                broadcastMessage(reminder.sessionId, message, reminder.text)
            }
        }
    }

    private fun broadcastMessage(sessionId: String, message: com.claude.agent.models.Message?, reminderText: String? = null) {
        if (webSocketService != null && message != null) {
            scope.launch {
                try {
                    val wsMessage = WebSocketMessage(
                        type = "new_message",
                        sessionId = sessionId,
                        data = Json.encodeToString(message)
                    )
                    // Отправить подключенным к этой сессии
                    webSocketService.broadcastToSession(sessionId, wsMessage)

                    // ВАЖНО: Также отправить new_message глобально для Compose Web UI
                    // который не подключается к WebSocket сессии
                    webSocketService.broadcastGlobal(wsMessage)

                    // ВАЖНО: Отправить глобальное уведомление для обновления списка чатов
                    val globalMessage = WebSocketMessage(
                        type = "session_updated",
                        sessionId = sessionId,
                        data = """{"unread":true,"hasNewMessage":true}"""
                    )
                    webSocketService.broadcastGlobal(globalMessage)

                    // Отправить браузерное уведомление
                    if (reminderText != null) {
                        val notificationMessage = WebSocketMessage(
                            type = "show_notification",
                            sessionId = sessionId,
                            data = Json.encodeToString(mapOf(
                                "title" to "🔔 Напоминание",
                                "body" to reminderText
                            ))
                        )
                        webSocketService.broadcastGlobal(notificationMessage)
                    }

                    logger.info("WebSocket broadcast sent for reminder in session: $sessionId (session + global)")
                } catch (e: Exception) {
                    logger.error("Error broadcasting WebSocket message: ${e.message}", e)
                }
            }
        }
    }

    private fun markNotified(id: String) {
        conversationRepository.markNotified(id)
        logger.info("Reminder marked as notified: $id")
    }

    private fun handleRecurringReminder(reminder: Reminder) {
        try {
            val currentDueAt = Instant.parse(reminder.due_at)
            val now = Instant.now()

            // Calculate next occurrence
            val nextDueAt = when (reminder.recurrenceType) {
                "minutely" -> currentDueAt.plus(reminder.recurrenceInterval.toLong(), ChronoUnit.MINUTES)
                "hourly" -> currentDueAt.plus(reminder.recurrenceInterval.toLong(), ChronoUnit.HOURS)
                "daily" -> currentDueAt.plus(reminder.recurrenceInterval.toLong(), ChronoUnit.DAYS)
                "weekly" -> currentDueAt.plus((reminder.recurrenceInterval * 7).toLong(), ChronoUnit.DAYS)
                "monthly" -> currentDueAt.plus(reminder.recurrenceInterval.toLong(), ChronoUnit.MONTHS)
                else -> {
                    logger.error("Unknown recurrence type: ${reminder.recurrenceType}")
                    markNotified(reminder.id)
                    return
                }
            }

            // Check if we should stop recurring
            val shouldContinue = if (reminder.recurrenceEndDate != null) {
                try {
                    val endDate = Instant.parse(reminder.recurrenceEndDate)
                    nextDueAt.isBefore(endDate)
                } catch (e: Exception) {
                    logger.error("Error parsing recurrence end date: ${e.message}")
                    false
                }
            } else {
                true
            }

            if (shouldContinue) {
                // Delete current reminder and create next occurrence
                conversationRepository.deleteReminder(reminder.id)
                cancelReminderTimer(reminder.id)

                val nextReminder = reminder.copy(
                    id = UUID.randomUUID().toString(),
                    due_at = nextDueAt.toString(),
                    notified = false,
                    done = false
                )

                conversationRepository.createReminder(nextReminder)
                logger.info("Created next occurrence of recurring reminder: ${reminder.text} at $nextDueAt")

                // Schedule timer for next occurrence
                scheduleReminder(nextReminder)
            } else {
                // End of recurrence - mark as notified and cancel timer
                markNotified(reminder.id)
                cancelReminderTimer(reminder.id)
                logger.info("Recurring reminder ended: ${reminder.text}")
            }
        } catch (e: Exception) {
            logger.error("Error handling recurring reminder: ${e.message}", e)
            markNotified(reminder.id)
        }
    }

    fun addReminder(
        text: String,
        dueAt: String,
        sessionId: String? = null,
        recurrenceType: String = "none",
        recurrenceInterval: Int = 1,
        recurrenceEndDate: String? = null,
        taskType: String = "reminder",
        taskContext: String? = null
    ): Reminder {
        val now = Instant.now().toString()
        val reminder = Reminder(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            text = text,
            due_at = dueAt,
            created_at = now,
            updated_at = now,
            done = false,
            recurrenceType = recurrenceType,
            recurrenceInterval = recurrenceInterval,
            recurrenceEndDate = recurrenceEndDate,
            taskType = taskType,
            taskContext = taskContext
        )

        conversationRepository.createReminder(reminder)

        logger.info("Reminder created: ${reminder.text} for session: $sessionId (type: $taskType, recurrence: $recurrenceType every $recurrenceInterval)")

        // Schedule individual timer for this reminder
        scheduleReminder(reminder)

        return reminder
    }

    fun listReminders(): List<Reminder> {
        val reminders = conversationRepository.getReminders()
        logger.info("Listed ${reminders.size} reminders")
        return reminders
    }


    fun deleteReminder(id: String) {
        conversationRepository.deleteReminder(id)
        cancelReminderTimer(id)
        logger.info("Reminder deleted: $id")
    }
}