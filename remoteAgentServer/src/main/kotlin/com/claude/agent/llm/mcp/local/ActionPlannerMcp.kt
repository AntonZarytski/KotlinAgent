package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.ACTION_PLANNER
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
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
            ui_description = "Планировщик для ОЧЕНЬ СЛОЖНЫХ задач (5+ шагов с разными инструментами).",
            description = """
            ⚠️ ИСПОЛЬЗУЙ ТОЛЬКО ДЛЯ ОЧЕНЬ СЛОЖНЫХ ЗАДАЧ!

            КРИТЕРИИ для использования (ВСЕ должны выполняться):
            1. Задача требует 5+ различных действий
            2. Нужно вызвать 3+ РАЗНЫХ MCP инструмента
            3. Результаты шагов зависят друг от друга
            4. Задача явно формулируется как "сделай A, потом B, потом C, потом D, потом E"

            НЕ ИСПОЛЬЗУЙ для:
            ❌ Простых запросов ("покажи файл", "там ошибка", "что в проекте")
            ❌ Задач с 1-2 действиями
            ❌ Задач с одним инструментом
            ❌ Уточняющих вопросов
            ❌ Если уже начал выполнение - ПРОДОЛЖАЙ БЕЗ ПЛАНИРОВАНИЯ

            Для простых задач - СРАЗУ ВЫПОЛНЯЙ, не планируй!

            Если используешь этот инструмент:
            1. Построй краткий план (5-7 шагов максимум)
            2. Укажи для каждого шага: инструмент и аргументы
            3. После планирования - СРАЗУ НАЧНИ ВЫПОЛНЕНИЕ
        """.trimIndent(),
            enabled = false,  // По умолчанию ВЫКЛЮЧЕН
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
        logger.info("⚠️ plan_actions tool called - это должно быть редко!")

        // Возвращаем краткую инструкцию
        return """
План создан. Теперь СРАЗУ НАЧНИ ВЫПОЛНЕНИЕ шагов используя доступные MCP инструменты.

НЕ описывай план текстом - ВЫПОЛНЯЙ действия!
        """.trimIndent()
    }
}