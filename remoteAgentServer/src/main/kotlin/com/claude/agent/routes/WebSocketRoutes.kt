package com.claude.agent.routes

import com.claude.agent.service.WebSocketService
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import org.slf4j.LoggerFactory

/**
 * WebSocket routes for real-time communication.
 * Clients connect to /ws/{sessionId} to receive real-time updates for that session.
 */
fun Route.webSocketRoutes(webSocketService: WebSocketService) {
    val logger = LoggerFactory.getLogger("WebSocketRoutes")

    // Global WebSocket for session list updates, unread counts, etc.
    webSocket("/ws/global") {
        logger.info("Global WebSocket connection established")

        try {
            // Register this global connection
            webSocketService.registerGlobalConnection(this)

            // Send initial connection confirmation
            send(Frame.Text("""{"type":"connected","scope":"global"}"""))

            // Keep connection alive and handle incoming messages
            incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        logger.debug("Received global WebSocket message: $text")
                        if (text == "ping") {
                            send(Frame.Text("pong"))
                        }
                    }
                    is Frame.Close -> {
                        logger.info("Global WebSocket close frame received")
                    }
                    else -> {
                        logger.debug("Received other frame type: ${frame.frameType}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Global WebSocket error: ${e.message}", e)
        } finally {
            // Unregister connection when it closes
            webSocketService.unregisterGlobalConnection(this)
            logger.info("Global WebSocket connection closed")
        }
    }

    webSocket("/ws/{sessionId}") {
        val sessionId = call.parameters["sessionId"]
        
        if (sessionId == null) {
            logger.warn("WebSocket connection attempt without sessionId")
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "sessionId required"))
            return@webSocket
        }
        
        logger.info("WebSocket connection established for session: $sessionId")
        
        try {
            // Register this connection
            webSocketService.registerConnection(sessionId, this)
            
            // Send initial connection confirmation
            send(Frame.Text("""{"type":"connected","sessionId":"$sessionId"}"""))
            
            // Keep connection alive and handle incoming messages
            incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        logger.debug("Received WebSocket message from $sessionId: $text")
                        // Handle ping/pong or other client messages if needed
                        if (text == "ping") {
                            send(Frame.Text("pong"))
                        }
                    }
                    is Frame.Close -> {
                        logger.info("WebSocket close frame received for session: $sessionId")
                    }
                    else -> {
                        logger.debug("Received other frame type: ${frame.frameType}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("WebSocket error for session $sessionId: ${e.message}", e)
        } finally {
            // Unregister connection when it closes
            webSocketService.unregisterConnection(sessionId, this)
            logger.info("WebSocket connection closed for session: $sessionId")
        }
    }
}

