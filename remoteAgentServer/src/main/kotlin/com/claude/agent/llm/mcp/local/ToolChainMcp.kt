package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.TOOL_CHAIN
import com.claude.agent.models.UserLocation
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * MCP инструмент для планирования цепочек вызовов других инструментов.
 *
 * Помогает Claude понять какие инструменты вызывать последовательно
 * и как использовать результаты предыдущих вызовов.
 *
 * Примеры использования:
 * 1. "Найди файл X и покажи его содержимое"
 *    -> plan_tool_chain -> git_repository (search) -> git_repository (read)
 *
 * 2. "Проверь погоду и создай напоминание если будет дождь"
 *    -> plan_tool_chain -> weather -> reminder (если дождь)
 *
 * 3. "Найди все TODO в проекте и создай тикет"
 *    -> plan_tool_chain -> git_repository (search) -> support (create_ticket)
 */
class ToolChainMcp : Mcp.Local {

    private val logger = LoggerFactory.getLogger(ToolChainMcp::class.java)

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = TOOL_CHAIN,
        second = LocalToolDefinition(
            name = TOOL_CHAIN,
            ui_description = "Планирует последовательность вызовов инструментов для сложных задач.",
            description = """Планирует последовательность вызовов инструментов для выполнения сложной задачи.

Используй ТОЛЬКО когда нужно:
- Вызвать 2+ инструмента последовательно
- Использовать результат одного инструмента в другом
- Выполнить условную логику (если X, то Y)

НЕ используй для:
- Одиночных вызовов инструментов
- Простых задач без зависимостей

Примеры:
✅ "Найди файл config.kt и покажи его" -> search + read
✅ "Проверь погоду и напомни если дождь" -> weather + conditional reminder
✅ "Найди все TODO и создай тикет" -> search + create_ticket
❌ "Покажи погоду" -> просто вызови weather
❌ "Создай напоминание" -> просто вызови reminder""".trimIndent(),
            enabled = true,  // Всегда включен - легковесный
            input_schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "task" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Описание задачи которую нужно выполнить")
                                )
                            ),
                            "available_tools" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("array"),
                                    "items" to JsonObject(
                                        mapOf("type" to JsonPrimitive("string"))
                                    ),
                                    "description" to JsonPrimitive("Список доступных инструментов")
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(listOf(JsonPrimitive("task"), JsonPrimitive("available_tools")))
                )
            )
        )
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        
        val task = arguments["task"]?.jsonPrimitive?.content ?: ""
        val availableTools = arguments["available_tools"]?.jsonArray?.map { 
            it.jsonPrimitive.content 
        } ?: emptyList()
        
        // Простой анализ задачи и предложение плана
        val plan = analyzeTasks(task, availableTools)
        
        return buildJsonObject {
            put("task", task)
            putJsonArray("suggested_sequence") {
                plan.forEach { step ->
                    addJsonObject {
                        put("step", step.stepNumber)
                        put("tool", step.tool)
                        put("action", step.action)
                        put("use_result_from", step.useResultFrom)
                    }
                }
            }
            put("instruction", """План готов. Теперь выполни шаги последовательно:
${plan.mapIndexed { i, step -> "${i + 1}. ${step.tool}: ${step.action}" }.joinToString("\n")}

Используй результаты предыдущих шагов для следующих.""")
        }.toString()
    }
    
    private data class PlanStep(
        val stepNumber: Int,
        val tool: String,
        val action: String,
        val useResultFrom: Int? = null
    )
    
    private fun analyzeTasks(task: String, availableTools: List<String>): List<PlanStep> {
        val steps = mutableListOf<PlanStep>()
        val taskLower = task.lowercase()
        
        // Простая эвристика для определения последовательности
        var stepNum = 1
        
        // Поиск файлов
        if (taskLower.contains("найди") || taskLower.contains("найти") || taskLower.contains("поиск")) {
            if ("git_repository" in availableTools) {
                steps.add(PlanStep(stepNum++, "git_repository", "Найти файл/код", null))
            }
        }
        
        // Чтение файлов
        if (taskLower.contains("покажи") || taskLower.contains("прочитай") || taskLower.contains("содержимое")) {
            if ("git_repository" in availableTools && steps.isNotEmpty()) {
                steps.add(PlanStep(stepNum++, "git_repository", "Прочитать файл", steps.size))
            }
        }
        
        // Погода
        if (taskLower.contains("погод") || taskLower.contains("weather")) {
            if ("weather" in availableTools) {
                steps.add(PlanStep(stepNum++, "weather", "Получить прогноз погоды", null))
            }
        }
        
        // Напоминания
        if (taskLower.contains("напомни") || taskLower.contains("reminder")) {
            if ("reminder" in availableTools) {
                val prevStep = if (steps.isNotEmpty()) steps.size else null
                steps.add(PlanStep(stepNum++, "reminder", "Создать напоминание", prevStep))
            }
        }
        
        // Тикеты
        if (taskLower.contains("тикет") || taskLower.contains("ticket") || taskLower.contains("issue")) {
            if ("support" in availableTools) {
                val prevStep = if (steps.isNotEmpty()) steps.size else null
                steps.add(PlanStep(stepNum++, "support", "Создать тикет", prevStep))
            }
        }
        
        return steps
    }
}

