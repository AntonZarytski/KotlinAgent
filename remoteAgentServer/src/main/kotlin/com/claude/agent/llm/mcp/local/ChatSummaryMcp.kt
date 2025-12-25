package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.CHAT_SUMMARY
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

class ChatSummaryMcp() : Mcp.Local {

    private val logger = LoggerFactory.getLogger(ChatSummaryMcp::class.java)

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = CHAT_SUMMARY,
        second = LocalToolDefinition(
            name = CHAT_SUMMARY,
            ui_description = "Возвращает краткое summary текущего чата.",
            description = """
                Возвращает краткое summary текущего чата.
                Используй этот инструмент, когда нужно:
                - получить сводку диалога
                - понять контекст разговора
                - продолжить работу после паузы
                - сохранить состояние сессии
                
                Инструмент НЕ принимает аргументов.
                Он должен быть вызван моделью, которая сама сформирует summary,
                основываясь на истории текущего чата.
                """.trimIndent(),
            enabled = true,
            input_schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(emptyMap())
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
        return executeChatSummaryTool(arguments.toString())
    }

    private fun executeChatSummaryTool(
        conversationHistory: String
    ): String {
        logger.info("get_chat_summary tool called")

        return JsonObject(
            mapOf(
                "summary" to JsonPrimitive(
                    """
                Сформируй краткое, структурированное summary текущего чата.
                Выдели:
                - основную цель пользователя
                - что уже сделано
                - текущее состояние
                - что планируется дальше

                История чата:
                $conversationHistory
                """.trimIndent()
                )
            )
        ).toString()
    }
}