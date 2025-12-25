package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.ClaudeClient
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.REMINDER
import com.claude.agent.models.UserLocation
import com.claude.agent.service.ReminderService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class ReminderMcp(
    val reminderService: ReminderService,
) : Mcp.Local {
    private val logger = LoggerFactory.getLogger(ReminderMcp::class.java)

    // ClaudeClient will be set after initialization to avoid circular dependency
    var claudeClient: ClaudeClient? = null

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = REMINDER,
        second = LocalToolDefinition(
            name = REMINDER,
            ui_description = "Возможность создать/удалить напоминание с указанным описанием и временем. Поддерживает повторяющиеся напоминания (каждую минуту, час, день, неделю, месяц). Также поддерживает отложенные AI задачи - когда нужно сгенерировать ответ в будущем или вызвать MCP инструмент в будущем.",
            description = """
Создание напоминаний трех типов:

1. **reminder** (простое напоминание) - для текстовых напоминаний без вызова инструментов
   Пример: "напомни мне позвонить маме через час"

2. **ai_response** (AI ответ в будущем) - когда нужно сгенерировать текстовый ответ БЕЗ вызова инструментов
   Пример: "отправь мне рецепт пиццы через 30 секунд"
   task_context: {"user_request": "рецепт пиццы", "accumulated_results": "результаты если были"}

3. **mcp_tool** (вызов MCP инструмента в будущем) - ОБЯЗАТЕЛЬНО использовать когда нужно вызвать КОНКРЕТНЫЙ инструмент (погода, авиабилеты и т.д.)
   Примеры: "покажи погоду через 1 минуту", "напомни погоду завтра", "найди авиабилеты через час"
   task_context ОБЯЗАТЕЛЬНО должен содержать:
   - "tool_name": название MCP инструмента (например "get_weather_forecast" для погоды)
   - "tool_arguments": JSON объект с аргументами для инструмента
   - "user_request": оригинальный запрос пользователя

ВАЖНО: Если в запросе упоминается погода, авиабилеты или другие данные которые требуют вызова инструмента - ОБЯЗАТЕЛЬНО используй task_type="mcp_tool" и укажи правильный tool_name!
""",
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
                                    "description" to JsonPrimitive("""JSON с контекстом задачи:
- Для ai_response: {"user_request": "текст запроса", "accumulated_results": "результаты если были"}
- Для mcp_tool (ОБЯЗАТЕЛЬНО!): {"tool_name": "get_weather_forecast", "tool_arguments": {"latitude": 41.41, "longitude": 2.19}, "user_request": "покажи погоду"}
  tool_name должен быть название СУЩЕСТВУЮЩЕГО MCP инструмента (get_weather_forecast, find_flights и т.д.)
  tool_arguments должен содержать ВСЕ необходимые параметры для инструмента
- Для reminder: не требуется""")
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
                var taskContext = arguments["task_context"]?.jsonPrimitive?.content

                // For ai_response tasks, enrich task_context with accumulated tool results
                if (taskType == "ai_response" && claudeClient != null) {
                    val accumulatedResults = claudeClient!!.getAccumulatedToolResults()
                    if (accumulatedResults.isNotEmpty()) {
                        // Build a summary of accumulated results
                        val resultsSummary = accumulatedResults.entries.joinToString("\n\n") { (toolName, result) ->
                            "Результат инструмента $toolName:\n${result.take(500)}"
                        }

                        // Parse existing context or create new one
                        val contextObj = try {
                            taskContext?.let { Json.parseToJsonElement(it).jsonObject.toMutableMap() } ?: mutableMapOf()
                        } catch (e: Exception) {
                            logger.warn("Failed to parse task_context, creating new one: ${e.message}")
                            mutableMapOf()
                        }

                        // Add accumulated results to context
                        contextObj["accumulated_results"] = JsonPrimitive(resultsSummary)

                        // Serialize back to JSON string
                        taskContext = Json.encodeToString(JsonObject(contextObj))

                        logger.info("Enriched task_context with accumulated results from ${accumulatedResults.size} tools")
                    }
                }

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