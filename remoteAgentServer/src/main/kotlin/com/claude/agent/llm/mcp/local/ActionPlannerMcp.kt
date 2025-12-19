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