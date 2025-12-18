package com.claude.agent.llm.mcp.providers

import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.local.model.LocalToolDefinition

class LocalMcpProvider(private val mcps: List<Mcp.Local>) {

    private val tools: Map<String, LocalToolDefinition> = mcps.associate { mcp ->
        mcp.tool.first to mcp.tool.second
    }
    fun getTool(toolName: String): Mcp.Local? {
        return mcps.find { it.tool.first == toolName }
    }

    fun getToolsDefinitions(enabledTools: List<String>): List<LocalToolDefinition> {
        return tools.map { it.value }.filter { it.name in enabledTools }
    }

    fun getAllTools(): List<LocalToolDefinition> {
        val local = tools.map { it.value }
        return local
    }

    fun setToolEnabled(toolName: String, enabled: Boolean) {
        tools.forEach {
            if (it.key == toolName) {
                it.value.enabled = enabled
            }
        }
    }
}