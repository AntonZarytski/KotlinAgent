package com.claude.agent.llm.mcp.remote

import com.claude.agent.llm.mcp.AIR_TICKETS
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.remote.model.RemoteToolDefinition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AirTicketsMcp: Mcp.Remote {

    override val tool = Pair(
        first = AIR_TICKETS ,
        second = RemoteToolDefinition(
            name = AIR_TICKETS,
            url = "https://mcp.kiwi.com",
            description = "Поиск авиабилетов через Kiwi.com",
            enabled = false // По умолчанию выключен
        )
    )

    override val params: JsonObject = tool.second.let { config ->
        JsonObject(mapOf(
            "type" to JsonPrimitive("url"),
            "url" to JsonPrimitive(config.url),
            "name" to JsonPrimitive(config.name)
        ))
    }
}