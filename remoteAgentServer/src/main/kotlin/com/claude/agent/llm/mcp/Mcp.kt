package com.claude.agent.llm.mcp

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.remote.model.RemoteToolDefinition
import com.claude.agent.models.UserLocation
import kotlinx.serialization.json.JsonObject

sealed interface Mcp {
    interface Local : Mcp {
        val tool: Pair<String, LocalToolDefinition>

        suspend fun executeTool(
            arguments: JsonObject,
            clientIp: String? = null,
            userLocation: UserLocation? = null,
            sessionId: String? = null
        ): String

        fun errorJson(msg: String) =
            """{"error":"$msg"}"""
    }
    interface Remote : Mcp {
        val tool: Pair<String, RemoteToolDefinition>
        val params: JsonObject
    }
}

const val AIR_TICKETS = "kiwi-com-flight-search"
const val ANDROID_STUDIO_MCP = "android_studio_mcp"
const val WEATHER = "get_weather_forecast"
const val SOLAR = "get_solar_activity"
const val CHAT_SUMMARY = "chat_summary"
const val REMINDER = "reminder"
const val ACTION_PLANNER = "plan_actions"