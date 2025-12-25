package com.claude.agent.ui

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import org.w3c.dom.WebSocket
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event

@Serializable
data class WebSocketMessage(
    val type: String,
    val sessionId: String? = null,
    val data: String? = null
)

@Serializable
data class StreamingTextData(
    val role: String,
    val content: String,
    val is_intermediate: Boolean = false,
    val iteration: Int = 0,
    val timestamp: Long = 0
)

@Serializable
data class ToolResultData(
    val tool_name: String,
    val tool_result: String,
    val iteration: Int = 0,
    val tool_index: Int = 0,
    val timestamp: Long = 0
)

object ApiClient {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val baseUrl = "${window.location.protocol}//${window.location.host}"

    // WebSocket connection
    private var webSocket: WebSocket? = null
    private var onStreamingText: ((StreamingTextData) -> Unit)? = null
    private var onToolResult: ((ToolResultData) -> Unit)? = null
    private var onNewMessage: ((Message) -> Unit)? = null
    private var currentSessionId: String? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private suspend inline fun <reified B, T> post(endpoint: String, body: B, decoder: (String) -> T): T {
        val headers = Headers()
        headers.append("Content-Type", "application/json")

        val bodyString = when (body) {
            is String -> body
            else -> jsonParser.encodeToString(body)
        }

        val response = window.fetch(
            "$baseUrl$endpoint",
            RequestInit(
                method = "POST",
                headers = headers,
                body = bodyString
            )
        ).await()

        val text = response.text().await()
        return decoder(text)
    }

    private suspend fun <T> get(endpoint: String, decoder: (String) -> T): T {
        val response = window.fetch("$baseUrl$endpoint").await()
        val text = response.text().await()
        return decoder(text)
    }

    private suspend fun delete(endpoint: String) {
        window.fetch(
            "$baseUrl$endpoint",
            RequestInit(method = "DELETE")
        ).await()
    }

    suspend fun sendMessage(request: ChatRequest): ChatResponse {
        return post("/api/chat", request) { text ->
            jsonParser.decodeFromString<ChatResponse>(text)
        }
    }

    suspend fun countTokens(request: TokenCountRequest): TokenCountResponse {
        return post("/api/count_tokens", request) { text ->
            jsonParser.decodeFromString<TokenCountResponse>(text)
        }
    }

    suspend fun getSessions(): SessionsResponse {
        return get("/api/sessions") { text ->
            jsonParser.decodeFromString<SessionsResponse>(text)
        }
    }

    suspend fun getSession(sessionId: String): SessionResponse {
        return get("/api/sessions/$sessionId") { text ->
            jsonParser.decodeFromString<SessionResponse>(text)
        }
    }

    suspend fun createSession(sessionId: String, title: String) {
        val body = """{"session_id":"$sessionId","title":"$title"}"""
        post("/api/sessions", body) { it }
    }

    suspend fun deleteSession(sessionId: String) {
        delete("/api/sessions/$sessionId")
    }

    suspend fun getTools(): ToolsResponse {
        return get("/api/tools") { text ->
            jsonParser.decodeFromString<ToolsResponse>(text)
        }
    }

    suspend fun getReminders(): RemindersResponse {
        return get("/api/reminders") { text ->
            jsonParser.decodeFromString<RemindersResponse>(text)
        }
    }

    suspend fun dismissReminder(reminderId: String) {
        delete("/api/reminders/$reminderId")
    }

    suspend fun getUnreadCounts(): UnreadCountsResponse {
        return get("/api/sessions/unread_counts") { text ->
            jsonParser.decodeFromString<UnreadCountsResponse>(text)
        }
    }

    suspend fun markSessionRead(sessionId: String) {
        post("/api/sessions/$sessionId/mark_read", "{}") { it }
    }

    // WebSocket functions
    fun connectWebSocket(
        sessionId: String,
        onStreamingTextCallback: (StreamingTextData) -> Unit,
        onToolResultCallback: (ToolResultData) -> Unit,
        onNewMessageCallback: (Message) -> Unit
    ) {
        // Close existing connection if switching sessions
        if (webSocket != null && currentSessionId != sessionId) {
            disconnectWebSocket()
        }

        if (webSocket?.readyState == WebSocket.OPEN) {
            console.log("WebSocket already connected")
            return
        }

        currentSessionId = sessionId
        onStreamingText = onStreamingTextCallback
        onToolResult = onToolResultCallback
        onNewMessage = onNewMessageCallback

        val protocol = if (window.location.protocol == "https:") "wss:" else "ws:"
        val wsUrl = "$protocol//${window.location.host}/ws/$sessionId"

        console.log("Connecting to WebSocket: $wsUrl")
        webSocket = WebSocket(wsUrl)

        webSocket?.onopen = { _: Event ->
            console.log("WebSocket connected for session: $sessionId")
            reconnectAttempts = 0
        }

        webSocket?.onmessage = { event: MessageEvent ->
            val data = event.data as? String
            if (data != null) {
                console.log("WebSocket message received: $data")
                try {
                    handleWebSocketMessage(data)
                } catch (e: Exception) {
                    console.error("Error parsing WebSocket message: ${e.message}")
                }
            }
        }

        webSocket?.onerror = { event: Event ->
            console.error("WebSocket error occurred")
            console.error("WebSocket readyState: ${webSocket?.readyState}")
            console.error("WebSocket URL was: $wsUrl")
        }

        webSocket?.onclose = { event: dynamic ->
            console.log("WebSocket disconnected")
            console.log("Close code: ${event.code}, reason: ${event.reason}")
            // Reconnect after 3 seconds if we have a session
            if (currentSessionId != null && reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++
                window.setTimeout({
                    if (currentSessionId != null) {
                        console.log("Attempting WebSocket reconnect (attempt $reconnectAttempts)")
                        connectWebSocket(
                            currentSessionId!!,
                            onStreamingText ?: {},
                            onToolResult ?: {},
                            onNewMessage ?: {}
                        )
                    }
                }, 3000)
            }
        }
    }

    private fun handleWebSocketMessage(data: String) {
        console.log("🔍 handleWebSocketMessage called with: ${data.take(200)}")
        try {
            val message = jsonParser.decodeFromString<WebSocketMessage>(data)
            console.log("🔍 Parsed message type: ${message.type}")

            when (message.type) {
                "connected" -> {
                    console.log("✅ WebSocket connection confirmed")
                }
                "streaming_text" -> {
                    console.log("📡 Processing streaming_text...")
                    if (message.data != null) {
                        val streamingData = jsonParser.decodeFromString<StreamingTextData>(message.data)
                        console.log("📡 Streaming text received (iteration ${streamingData.iteration}): ${streamingData.content.take(100)}")
                        onStreamingText?.invoke(streamingData)
                        console.log("📡 onStreamingText callback invoked")
                    } else {
                        console.log("⚠️ streaming_text data is null")
                    }
                }
                "tool_result" -> {
                    console.log("🔧 Processing tool_result...")
                    if (message.data != null) {
                        val toolData = jsonParser.decodeFromString<ToolResultData>(message.data)
                        console.log("🔧 com.claude.agent.ui.Tool result received: ${toolData.tool_name}")
                        onToolResult?.invoke(toolData)
                        console.log("🔧 onToolResult callback invoked")
                    } else {
                        console.log("⚠️ tool_result data is null")
                    }
                }
                "new_message" -> {
                    console.log("📨 Processing new_message...")
                    if (message.data != null) {
                        val newMessage = jsonParser.decodeFromString<Message>(message.data)
                        console.log("📨 New message received: ${newMessage.content.take(100)}")
                        onNewMessage?.invoke(newMessage)
                        console.log("📨 onNewMessage callback invoked")
                    } else {
                        console.log("⚠️ new_message data is null")
                    }
                }
                else -> {
                    console.log("⚠️ Unknown message type: ${message.type}")
                }
            }
        } catch (e: Exception) {
            console.error("❌ Error in handleWebSocketMessage: ${e.message}")
            console.error("Raw data was: $data")
        }
    }

    fun disconnectWebSocket() {
        webSocket?.close()
        webSocket = null
        currentSessionId = null
        reconnectAttempts = 0
    }

    fun isWebSocketConnected(): Boolean {
        return webSocket?.readyState == WebSocket.OPEN
    }
}
