package com.claude.agent.llm.mcp.local

import com.claude.agent.llm.mcp.ACTION_PLANNER
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import com.claude.agent.llm.mcp.local.model.LocalToolDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

class ActionPlannerMcp() : Mcp.Local {

    private val logger = LoggerFactory.getLogger(ActionPlannerMcp::class.java)

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = ACTION_PLANNER,
        second = LocalToolDefinition(
            name = ACTION_PLANNER,
            description = """
            Планировщик действий агента.

            Используй этот инструмент ВСЕГДА, когда:
            - задача состоит из нескольких шагов
            - требуется вызвать один или несколько MCP инструментов
            - результат одного шага нужен для следующего
            - нужно сохранить или восстановить контекст

            Твоя задача:
            1. Проанализировать запрос пользователя
            2. Построить пошаговый план
            3. Для каждого шага указать:
               - цель шага
               - имя MCP инструмента
               - аргументы для вызова
               - ожидаемый результат

            НЕ выполняй действия сам.
            НЕ выдумывай результаты.
            Только план.
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
        logger.info("plan_actions tool called")

        return JsonObject(
            mapOf(
                "instruction" to JsonPrimitive(
                    """
                Проанализируй текущий запрос пользователя и историю чата.

                Сформируй JSON следующего вида:

                {
                  "goal": "<основная цель пользователя>",
                  "steps": [
                    {
                      "step": "Описание шага",
                      "tool": "имя_mcp_инструмента",
                      "arguments": { }
                    }
                  ]
                }

                Правила:
                - Используй ТОЛЬКО доступные MCP инструменты
                - Если шаг не требует MCP — не добавляй его
                - Порядок шагов имеет значение
                - Аргументы должны соответствовать input_schema инструмента
                """.trimIndent()
                )
            )
        ).toString()
    }
}