package com.claude.agent.services

import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket service for managing real-time connections and broadcasting messages.
 * Allows clients to subscribe to specific sessions and receive real-time updates.
 */
class WebSocketService {
    private val logger = LoggerFactory.getLogger(WebSocketService::class.java)
    
    // Map of sessionId -> Set of WebSocket connections
    private val sessionConnections = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val mutex = Mutex()
    
    /**
     * Register a WebSocket connection for a specific session.
     */
    suspend fun registerConnection(sessionId: String, connection: WebSocketSession) {
        mutex.withLock {
            val connections = sessionConnections.getOrPut(sessionId) { mutableSetOf() }
            connections.add(connection)
            logger.info("WebSocket connected for session: $sessionId (total: ${connections.size})")
        }
    }
    
    /**
     * Unregister a WebSocket connection.
     */
    suspend fun unregisterConnection(sessionId: String, connection: WebSocketSession) {
        mutex.withLock {
            sessionConnections[sessionId]?.remove(connection)
            if (sessionConnections[sessionId]?.isEmpty() == true) {
                sessionConnections.remove(sessionId)
            }
            logger.info("WebSocket disconnected for session: $sessionId")
        }
    }
    
    /**
     * Broadcast a message to all connections subscribed to a session.
     */
    suspend fun broadcastToSession(sessionId: String, message: WebSocketMessage) {
        val connections = sessionConnections[sessionId] ?: return
        val messageJson = Json.encodeToString(message)
        
        logger.info("Broadcasting to session $sessionId: ${message.type} (${connections.size} connections)")
        
        val deadConnections = mutableSetOf<WebSocketSession>()
        
        for (connection in connections) {
            try {
                connection.send(Frame.Text(messageJson))
            } catch (e: ClosedReceiveChannelException) {
                logger.warn("Connection closed for session $sessionId")
                deadConnections.add(connection)
            } catch (e: Exception) {
                logger.error("Error sending message to WebSocket: ${e.message}", e)
                deadConnections.add(connection)
            }
        }
        
        // Clean up dead connections
        if (deadConnections.isNotEmpty()) {
            mutex.withLock {
                sessionConnections[sessionId]?.removeAll(deadConnections)
            }
        }
    }
    
    /**
     * Get the number of active connections for a session.
     */
    fun getConnectionCount(sessionId: String): Int {
        return sessionConnections[sessionId]?.size ?: 0
    }
}

/**
 * WebSocket message types for real-time communication.
 */
@Serializable
data class WebSocketMessage(
    val type: String, // "new_message", "reminder_triggered", "session_updated"
    val sessionId: String,
    val data: String // JSON-encoded payload
)

