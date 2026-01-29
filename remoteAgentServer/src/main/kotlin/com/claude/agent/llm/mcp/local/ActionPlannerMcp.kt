package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.ACTION_PLANNER
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.providers.LocalMcpProvider
import com.claude.agent.models.UserLocation
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class ActionPlannerMcp() : Mcp.Local {

    private val logger = LoggerFactory.getLogger(ActionPlannerMcp::class.java)

    // LocalMcpProvider will be set after initialization to avoid circular dependency
    var localMcpProvider: LocalMcpProvider? = null

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = ACTION_PLANNER,
        second = LocalToolDefinition(
            name = ACTION_PLANNER,
            ui_description = "Планировщик действий агента для СЛОЖНЫХ МНОГОШАГОВЫХ задач.",
            description = """
            Планировщик действий агента для СЛОЖНЫХ МНОГОШАГОВЫХ задач.

            КОГДА ИСПОЛЬЗОВАТЬ:
            - Задача состоит из 3+ различных действий
            - Требуется вызвать несколько РАЗНЫХ MCP инструментов
            - Результат одного шага нужен для следующего
            - Задача формулируется как "сделай X и Y и Z"

            КОГДА НЕ ИСПОЛЬЗОВАТЬ:
            - Простые задачи (1-2 действия)
            - Задачи с одним инструментом
            - Уже начал выполнение - продолжай без планирования

            ТВОЯ ЗАДАЧА:
            1. Проанализировать запрос пользователя
            2. Построить КОНКРЕТНЫЙ пошаговый план (3-7 шагов)
            3. Для каждого шага указать:
               - Цель шага (что делаем)
               - Имя MCP инструмента
               - Конкретные аргументы для вызова
               - Ожидаемый результат

            ПРИМЕР ХОРОШЕГО ПЛАНА для "покажи проект и запусти на эмуляторе":
            1. Установить путь проекта (android_studio, set_project_path)
            2. Просмотреть корень проекта (android_studio, browse_files)
            3. Прочитать MainActivity (android_studio, read_file)
            4. Прочитать build.gradle (android_studio, read_file)
            5. Собрать проект (android_studio, gradle_build)
            6. Запустить на эмуляторе (android_studio, gradle_install_run)

            ВАЖНО:
            - НЕ выполняй действия сам - только планируй
            - НЕ выдумывай результаты
            - План должен быть ПОЛНЫМ и ЗАВЕРШЕННЫМ
            - Каждый шаг должен приближать к ФИНАЛЬНОЙ цели
        """.trimIndent(),
            enabled = true,
            input_schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "goal" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Цель пользователя")
                                )
                            ),
                            "steps" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("array"),
                                    "items" to JsonObject(
                                        mapOf(
                                            "type" to JsonPrimitive("object"),
                                            "properties" to JsonObject(
                                                mapOf(
                                                    "step" to JsonObject(
                                                        mapOf(
                                                            "type" to JsonPrimitive("string")
                                                        )
                                                    ),
                                                    "tool" to JsonObject(
                                                        mapOf(
                                                            "type" to JsonPrimitive("string")
                                                        )
                                                    ),
                                                    "arguments" to JsonObject(
                                                        mapOf(
                                                            "type" to JsonPrimitive("object")
                                                        )
                                                    )
                                                )
                                            ),
                                            "required" to JsonArray(
                                                listOf(
                                                    JsonPrimitive("step"),
                                                    JsonPrimitive("tool")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(
                        listOf(
                            JsonPrimitive("goal"),
                            JsonPrimitive("steps")
                        )
                    )
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
        logger.info("plan_actions tool called with arguments: $arguments")

        // Parse goal and steps from arguments
        val goal = arguments["goal"]?.jsonPrimitive?.content
        val stepsArray = arguments["steps"]?.jsonArray

        if (goal == null || stepsArray == null) {
            logger.error("Missing goal or steps in arguments")
            return "Error: Missing required fields 'goal' or 'steps' in plan"
        }

        if (localMcpProvider == null) {
            logger.error("LocalMcpProvider is not set")
            return "Error: ActionPlannerMcp is not properly initialized"
        }

        logger.info("Executing plan with goal: $goal, steps count: ${stepsArray.size}")

        val results = mutableListOf<String>()
        results.add("🎯 Goal: $goal\n")

        // Execute each step sequentially
        for ((index, stepElement) in stepsArray.withIndex()) {
            val stepObj = stepElement.jsonObject
            val stepDescription = stepObj["step"]?.jsonPrimitive?.content ?: "Step ${index + 1}"
            val toolName = stepObj["tool"]?.jsonPrimitive?.content
            val toolArguments = stepObj["arguments"]?.jsonObject ?: JsonObject(emptyMap())

            if (toolName == null) {
                logger.warn("Step ${index + 1} has no tool specified, skipping")
                results.add("⚠️ Step ${index + 1}: Skipped (no tool specified)")
                continue
            }

            logger.info("Executing step ${index + 1}/${ stepsArray.size}: $stepDescription using tool: $toolName")
            results.add("📍 Step ${index + 1}: $stepDescription")

            try {
                // Call the tool via localMcpProvider
                val tool = localMcpProvider?.getTool(toolName)
                if (tool == null) {
                    val errorMsg = "Tool '$toolName' not found"
                    logger.error(errorMsg)
                    results.add("   ❌ Error: $errorMsg\n")
                    continue
                }

                // Для android_studio_mcp проверяем наличие параметра action
                val finalArguments = if (toolName == "android_studio_mcp") {
                    val hasAction = toolArguments["action"] != null
                    if (!hasAction) {
                        // Пытаемся определить action по наличию параметров
                        val action = when {
                            toolArguments["file_path"] != null -> "read_file"
                            toolArguments["directory_path"] != null -> "browse_files"
                            toolArguments["project_path"] != null -> "set_project_path"
                            toolArguments["build_variant"] != null -> "gradle_build"
                            toolArguments["avd_name"] != null -> "start_emulator"
                            else -> null
                        }

                        if (action != null) {
                            logger.warn("⚠️ Missing 'action' parameter for android_studio_mcp, auto-detected: $action")
                            buildJsonObject {
                                put("action", action)
                                toolArguments.forEach { (key, value) ->
                                    put(key, value)
                                }
                            }
                        } else {
                            logger.error("❌ Cannot auto-detect action for android_studio_mcp with arguments: $toolArguments")
                            results.add("   ❌ Error: Missing required parameter: action\n")
                            continue
                        }
                    } else {
                        toolArguments
                    }
                } else {
                    toolArguments
                }

                val result = tool.executeTool(
                    arguments = finalArguments,
                    clientIp = clientIp,
                    userLocation = userLocation,
                    sessionId = sessionId
                )

                results.add("   ✅ Result: $result\n")
                logger.info("Step ${index + 1} completed successfully")

            } catch (e: Exception) {
                val errorMsg = "Exception executing tool '$toolName': ${e.message}"
                logger.error(errorMsg, e)
                results.add("   ❌ Error: ${e.message}\n")
            }
        }

        results.add("✨ Plan execution completed")

        // Return aggregated results
        return results.joinToString("\n")
    }
}