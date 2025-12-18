package com.claude.agent.llm.mcp.local

import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.REMINDER
import com.claude.agent.models.UserLocation
import com.claude.agent.service.ReminderService
import com.claude.agent.llm.mcp.local.model.LocalToolDefinition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class ReminderMcp(
    val reminderService: ReminderService,
) : Mcp.Local {
    private val logger = LoggerFactory.getLogger(ReminderMcp::class.java)

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = REMINDER,
        second = LocalToolDefinition(
            name = REMINDER,
            description = "Возможность создать/удалить напоминание с указанным описанием и временем. Поддерживает повторяющиеся напоминания (каждую минуту, час, день, неделю, месяц). Также поддерживает отложенные AI задачи - когда нужно сгенерировать ответ в будущем (например, 'отправь мне рецепт пиццы через 30 секунд') или вызвать MCP инструмент в будущем (например, 'покажи погоду через 1 минуту').",
            enabled = true,
            input_schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "action" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "enum" to JsonArray(
                                        listOf(
                                            JsonPrimitive("add"),
                                            JsonPrimitive("list"),
                                            JsonPrimitive("delete")
                                        )
                                    ),
                                    "description" to JsonPrimitive("Действие: add - добавить новое напоминание, list - список напоминаний, delete - удалить напоминание")
                                )
                            ),
                            "due_at" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Дата и время напоминания в формате ISO")
                                )
                            ),
                            "text" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Текст напоминания")
                                )
                            ),
                            "task_type" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "enum" to JsonArray(
                                        listOf(
                                            JsonPrimitive("reminder"),
                                            JsonPrimitive("ai_response"),
                                            JsonPrimitive("mcp_tool")
                                        )
                                    ),
                                    "description" to JsonPrimitive("Тип задачи: reminder (простое напоминание), ai_response (сгенерировать AI ответ в будущем), mcp_tool (вызвать MCP инструмент в будущем). По умолчанию: reminder")
                                )
                            ),
                            "task_context" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("JSON с контекстом задачи. Для ai_response: {\"user_request\": \"текст запроса пользователя\"}. Для mcp_tool: {\"tool_name\": \"название инструмента\", \"tool_arguments\": {...}, \"user_request\": \"оригинальный запрос пользователя\"}. Для reminder: не требуется")
                                )
                            ),
                            "recurrence_type" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "enum" to JsonArray(
                                        listOf(
                                            JsonPrimitive("none"),
                                            JsonPrimitive("minutely"),
                                            JsonPrimitive("hourly"),
                                            JsonPrimitive("daily"),
                                            JsonPrimitive("weekly"),
                                            JsonPrimitive("monthly")
                                        )
                                    ),
                                    "description" to JsonPrimitive("Тип повторения: none (одноразовое), minutely (каждую минуту), hourly (каждый час), daily (каждый день), weekly (каждую неделю), monthly (каждый месяц). По умолчанию: none")
                                )
                            ),
                            "recurrence_interval" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("integer"),
                                    "description" to JsonPrimitive("Интервал повторения (например, 2 для 'каждые 2 часа'). По умолчанию: 1")
                                )
                            ),
                            "recurrence_end_date" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Дата окончания повторений в формате ISO. Опционально - если не указано, напоминание будет повторяться бесконечно")
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(listOf(JsonPrimitive("action")))
                )
            )
        ),
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        return executeReminderTool(arguments, sessionId)
    }

    fun executeReminderTool(arguments: JsonObject, sessionId: String? = null): String {
        val action = arguments["action"]?.jsonPrimitive?.content ?: return errorJson("action required")
        logger.info("reminder tool called: $action, args: $arguments, sessionId: $sessionId")
        return when (action) {
            "add" -> {
                val text = arguments["text"]?.jsonPrimitive?.content
                    ?: return errorJson("text required")
                val dueAt = arguments["due_at"]?.jsonPrimitive?.content
                    ?: return errorJson("due_at required")

                // Parse recurrence parameters
                val recurrenceType = arguments["recurrence_type"]?.jsonPrimitive?.content ?: "none"
                val recurrenceInterval = arguments["recurrence_interval"]?.jsonPrimitive?.intOrNull ?: 1
                val recurrenceEndDate = arguments["recurrence_end_date"]?.jsonPrimitive?.content

                // Parse task parameters
                val taskType = arguments["task_type"]?.jsonPrimitive?.content ?: "reminder"
                val taskContext = arguments["task_context"]?.jsonPrimitive?.content

                val reminder = reminderService.addReminder(
                    text = text,
                    dueAt = dueAt,
                    sessionId = sessionId,
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    recurrenceEndDate = recurrenceEndDate,
                    taskType = taskType,
                    taskContext = taskContext
                )
                Json.Default.encodeToString(reminder)
            }

            "list" -> {
                Json.Default.encodeToString(reminderService.listReminders())
            }

            "delete" -> {
                val id = arguments["id"]?.jsonPrimitive?.content
                    ?: return errorJson("id required")
                reminderService.deleteReminder(id)
                """{"status":"deleted","id":"$id"}"""
            }

            else -> errorJson("unknown action: $action")
        }
    }
}