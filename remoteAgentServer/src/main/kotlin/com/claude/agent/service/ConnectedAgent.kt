package com.claude.agent.service

import com.claude.agent.common.AgentMessage
import com.claude.agent.common.LocalToolDefinition
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.*

data class ConnectedAgent(
    val agentId: String,
    val session: WebSocketSession,
    val tool: LocalToolDefinition,
    val capabilities: List<String>,
    val pendingRequests: ConcurrentHashMap<String, CompletableDeferred<String>> = ConcurrentHashMap()
)

object LocalAgentManager {
    private val logger = LoggerFactory.getLogger(LocalAgentManager::class.java)
    private val agents = ConcurrentHashMap<String, ConnectedAgent>()
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun handleConnection(session: WebSocketSession) {
        var currentAgent: ConnectedAgent? = null
        
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        val message = json.decodeFromString<AgentMessage>(text)
                        
                        when (message) {
                            is AgentMessage.Register -> {
                                currentAgent = ConnectedAgent(
                                    agentId = message.agentId,
                                    session = session,
                                    tool = message.tool,
                                    capabilities = message.capabilities
                                )
                                agents[message.agentId] = currentAgent
                                logger.info("✅ Agent registered: ${message.agentId}")
                                logger.info("   Tool name: ${message.tool.name}")
                                logger.info("   Capabilities: ${message.capabilities}")
                            }
                            
                            is AgentMessage.ExecuteResponse -> {
                                currentAgent?.pendingRequests?.get(message.requestId)?.let { deferred ->
                                    deferred.complete(message.result)
                                    currentAgent.pendingRequests.remove(message.requestId)
                                }
                            }
                            
                            is AgentMessage.Pong -> {
                                // Heartbeat received
                            }
                            
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Agent connection error: ${e.message}", e)
        } finally {
            currentAgent?.let {
                agents.remove(it.agentId)
                logger.info("🔌 Agent disconnected: ${it.agentId}")

                // Отменяем все pending requests
                it.pendingRequests.values.forEach { deferred ->
                    deferred.completeExceptionally(Exception("Agent disconnected"))
                }
            }
        }
    }
    
    suspend fun executeOnLocalAgent(
        toolName: String,
        arguments: JsonObject,
        timeoutMs: Long = 60_000
    ): String {
        // Находим агента с нужным инструментом
        val agent = agents.values.firstOrNull { it.tool.name == toolName }
            ?: return """{"error":"No agent available for tool: $toolName"}"""
        
        val requestId = java.util.UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        agent.pendingRequests[requestId] = deferred
        
        try {
            // Отправляем запрос
            val request = AgentMessage.ExecuteRequest(
                requestId = requestId,
                toolName = toolName,
                arguments = arguments
            )
            
            agent.session.send(
                Frame.Text(Json.encodeToString(AgentMessage.serializer(), request))
            )
            
            // Ждем ответ с таймаутом
            return withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            agent.pendingRequests.remove(requestId)
            return """{"error":"Request timeout after ${timeoutMs}ms"}"""
        } catch (e: Exception) {
            agent.pendingRequests.remove(requestId)
            return """{"error":"Execution failed: ${e.message}"}"""
        }
    }
    
    fun getConnectedAgents(): List<String> = agents.keys.toList()
    
    fun isAgentConnected(toolName: String): Boolean {
        return agents.values.any { it.tool.name == toolName }
    }
}