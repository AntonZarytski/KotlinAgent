package com.claude.agent.ui

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.dom.*
import kotlin.js.Date

@Composable
fun ClaudeChatApp() {
    // State management
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var reminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var tools by remember { mutableStateOf<List<Tool>>(emptyList()) }
    var unreadCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var currentSessionId by remember { mutableStateOf(Utils.generateSessionId()) }
    var settings by remember { mutableStateOf(Settings()) }

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var showSettingsPanel by remember { mutableStateOf(false) }
    var showHistoryPanel by remember { mutableStateOf(false) }
    var showReminderPanel by remember { mutableStateOf(false) }
    var showTokenModal by remember { mutableStateOf(false) }
    var tokenCount by remember { mutableStateOf(0) }

    var messageCountSinceCompression by remember { mutableStateOf(0) }
    var userLocation by remember { mutableStateOf<UserLocation?>(null) }

    // Streaming state for real-time message updates
    var streamingText by remember { mutableStateOf<String?>(null) }
    var streamingIteration by remember { mutableStateOf(0) }

    // Track which reminders have been notified to avoid duplicate notifications
    val notifiedReminders = remember { mutableSetOf<String>() }

    val scope = rememberCoroutineScope()
    val json = remember {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    // Initialize app
    LaunchedEffect(Unit) {
        // Show welcome message
        messages = listOf(
            Message(
                role = "assistant",
                content = "Привет! Я твой локальный Claude-агент. Нажми 💬 для истории чатов. Нажми ⚙️ для настроек. Спроси что-нибудь 🙂",
                timestamp = Date().toISOString()
            )
        )

        // Request notification permission
        Utils.requestNotificationPermission()

        // Try to get geolocation
        userLocation = Utils.getGeolocation()
    }

    // Connect WebSocket when session changes
    LaunchedEffect(currentSessionId) {
        console.log("Session changed to: $currentSessionId, connecting WebSocket...")

        // Clear streaming state when switching sessions
        streamingText = null
        streamingIteration = 0

        ApiClient.connectWebSocket(
            sessionId = currentSessionId,
            onStreamingTextCallback = { data ->
                console.log("📡 Streaming text update: ${data.content.take(100)}")
                streamingText = data.content
                streamingIteration = data.iteration
            },
            onToolResultCallback = { data ->
                console.log("🔧 Tool result: ${data.tool_name}")
                // Tool results can be shown in a special UI element if needed
            },
            onNewMessageCallback = { message ->
                console.log("📨 New message from WebSocket: ${message.content.take(100)}")
                // Clear streaming state when final message arrives
                streamingText = null
                streamingIteration = 0
                // Add message to chat
                messages = messages + message
                scope.launch {
                    delay(100)
                    Utils.scrollToBottom()
                }
            }
        )
    }

    // Load reminders only when reminder panel is opened
    LaunchedEffect(showReminderPanel) {
        if (showReminderPanel) {
            // Load once when panel opens
            loadReminders(reminders, notifiedReminders) {
                reminders = it
            }
        }
    }

    // Periodic refresh only if there are active reminders (every 30 seconds instead of 10)
    LaunchedEffect(reminders.isNotEmpty()) {
        if (reminders.isNotEmpty()) {
            while (true) {
                delay(30000) // Проверка каждые 30 секунд (вместо 10)
                loadReminders(reminders, notifiedReminders) {
                    reminders = it
                }
            }
        }
    }

    // Global WebSocket for session updates and notifications
    LaunchedEffect(Unit) {
        try {
            val protocol = if (window.location.protocol == "https:") "wss:" else "ws:"
            val wsUrl = "$protocol//${window.location.host}/ws/global"

            console.log("Connecting to global WebSocket: $wsUrl")

            val ws = org.w3c.dom.WebSocket(wsUrl)

            ws.onopen = {
                console.log("Global WebSocket connected")
            }

            ws.onmessage = { event ->
                try {
                    val data = JSON.parse<dynamic>(event.asDynamic().data as String)
                    console.log("Global WebSocket message:", data.type)

                    when (data.type as String) {
                        "session_updated" -> {
                            console.log("Session updated, refreshing session list")
                            scope.launch {
                                loadSessions(sessions, unreadCounts) { s, u ->
                                    sessions = s
                                    unreadCounts = u
                                }
                            }
                        }
                        "show_notification" -> {
                            try {
                                val notificationData = JSON.parse<dynamic>(data.data as String)
                                val title = notificationData.title as? String ?: "Уведомление"
                                val body = notificationData.body as? String ?: ""
                                Utils.showBrowserNotification(title, body)
                                console.log("Browser notification shown: $title - $body")
                            } catch (e: Exception) {
                                console.error("Error showing notification:", e.message)
                            }
                        }
                        "new_message" -> {
                            try {
                                console.log("New message received via global WebSocket")
                                val messageData = JSON.parse<dynamic>(data.data as String)
                                val receivedSessionId = data.sessionId as? String

                                // Добавить сообщение только если это текущая сессия
                                if (receivedSessionId == currentSessionId) {
                                    val newMessage = json.decodeFromString<Message>(data.data as String)
                                    console.log("Adding message to current session: ${newMessage.content.take(100)}")
                                    messages = messages + newMessage

                                    // Прокрутить вниз
                                    scope.launch {
                                        delay(100)
                                        Utils.scrollToBottom()
                                    }
                                } else {
                                    console.log("Message for different session: $receivedSessionId (current: $currentSessionId)")
                                }
                            } catch (e: Exception) {
                                console.error("Error processing new_message:", e.message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    console.error("Error parsing global WebSocket message:", e.message)
                }
            }

            ws.onerror = { error ->
                console.error("Global WebSocket error:", error)
            }

            ws.onclose = {
                console.log("Global WebSocket disconnected")
            }
        } catch (e: Exception) {
            console.error("Failed to connect to global WebSocket:", e.message)
        }
    }

    // Main UI
    Div({
        classes("container")
    }) {
        // History Panel
        HistoryPanel(
            visible = showHistoryPanel,
            sessions = sessions,
            currentSessionId = currentSessionId,
            unreadCounts = unreadCounts,
            onClose = { showHistoryPanel = false },
            onNewChat = {
                startNewChat(
                    onSessionChange = { id -> currentSessionId = id },
                    onMessagesChange = { messages = it }
                )
                showHistoryPanel = false
            },
            onLoadSession = { sessionId ->
                scope.launch {
                    loadSession(
                        sessionId = sessionId,
                        onSuccess = { history ->
                            messages = history
                            currentSessionId = sessionId
                            messageCountSinceCompression = 0
                            showHistoryPanel = false
                        }
                    )
                }
            },
            onDeleteSession = { sessionId ->
                scope.launch {
                    ApiClient.deleteSession(sessionId)
                    if (sessionId == currentSessionId) {
                        startNewChat(
                            onSessionChange = { id -> currentSessionId = id },
                            onMessagesChange = { messages = it }
                        )
                    }
                    loadSessions(sessions, unreadCounts) { s, u ->
                        sessions = s
                        unreadCounts = u
                    }
                }
            }
        )

        // com.claude.agent.ui.Reminder Panel
        ReminderPanel(
            visible = showReminderPanel,
            reminders = reminders,
            onClose = { showReminderPanel = false },
            onDismiss = { reminderId ->
                scope.launch {
                    ApiClient.dismissReminder(reminderId)
                    notifiedReminders.remove(reminderId)
                    loadReminders(reminders, notifiedReminders) { reminders = it }
                }
            }
        )

        // Main Chat Area
        Div({
            classes("chat-area")
        }) {
            ChatHeader(
                onHistoryClick = {
                    showHistoryPanel = !showHistoryPanel
                    if (showHistoryPanel) {
                        showSettingsPanel = false
                        showReminderPanel = false
                        scope.launch {
                            loadSessions(sessions, unreadCounts) { s, u ->
                                sessions = s
                                unreadCounts = u
                            }
                        }
                    }
                },
                onReminderClick = {
                    showReminderPanel = !showReminderPanel
                    if (showReminderPanel) {
                        showSettingsPanel = false
                        showHistoryPanel = false
                        scope.launch {
                            loadReminders(reminders, notifiedReminders) { reminders = it }
                        }
                    }
                },
                onTokensClick = {
                    scope.launch {
                        if (inputText.isBlank()) {
                            tokenCount = 0
                            showTokenModal = true
                        } else {
                            val historyToSend = if (settings.sendHistory) messages else emptyList()
                            val response = ApiClient.countTokens(
                                TokenCountRequest(
                                    message = inputText,
                                    output_format = settings.outputFormat,
                                    spec_mode = settings.specMode,
                                    conversation_history = historyToSend
                                )
                            )
                            tokenCount = response.input_tokens
                            showTokenModal = true
                        }
                    }
                },
                onSettingsClick = {
                    showSettingsPanel = !showSettingsPanel
                    if (showSettingsPanel) {
                        showHistoryPanel = false
                        showReminderPanel = false
                        scope.launch {
                            loadTools(tools) { tools = it }
                        }
                    }
                }
            )

            ChatMessages(
                messages = messages,
                isLoading = isLoading,
                showTokenCount = settings.showTokenCount,
                streamingText = streamingText
            )

            InputPanel(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    scope.launch {
                        sendMessage(
                            text = inputText,
                            currentSessionId = currentSessionId,
                            settings = settings,
                            messages = messages,
                            userLocation = userLocation,
                            onMessagesUpdate = { messages = it },
                            onLoadingChange = { isLoading = it },
                            onInputClear = { inputText = "" },
                            onMessageCountUpdate = { messageCountSinceCompression = it }
                        )
                    }
                },
                enabled = !isLoading
            )
        }

        // com.claude.agent.ui.Settings Panel
        SettingsPanel(
            visible = showSettingsPanel,
            settings = settings,
            tools = tools,
            onClose = { showSettingsPanel = false },
            onSettingsChange = { settings = it },
            onClearHistory = {
                messages = listOf(
                    Message(
                        role = "assistant",
                        content = "История очищена. Начнём новый диалог! 🙂",
                        timestamp = Date().toISOString()
                    )
                )
                currentSessionId = Utils.generateSessionId()
                messageCountSinceCompression = 0
                showSettingsPanel = false
            }
        )
    }

    // Token Modal
    TokenModal(
        visible = showTokenModal,
        tokenCount = tokenCount,
        onClose = { showTokenModal = false }
    )
}

// Helper functions
private suspend fun sendMessage(
    text: String,
    currentSessionId: String,
    settings: Settings,
    messages: List<Message>,
    userLocation: UserLocation?,
    onMessagesUpdate: (List<Message>) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onInputClear: () -> Unit,
    onMessageCountUpdate: (Int) -> Unit
) {
    if (text.isBlank()) return

    // Add user message
    val userMessage = Message(
        role = "user",
        content = text,
        timestamp = Date().toISOString()
    )
    onMessagesUpdate(messages + userMessage)
    onInputClear()

    onLoadingChange(true)

    try {
        // Create session if first message
        val userMessages = messages.filter { it.role == "user" }
        if (userMessages.isEmpty()) {
            ApiClient.createSession(
                currentSessionId,
                text.take(50) + if (text.length > 50) "..." else ""
            )
        }

        // Check if geolocation is needed
        val location = if (Utils.needsGeolocation(text) && !text.contains(Regex("\\d+\\.\\d+"))) {
            userLocation
        } else {
            null
        }

        // Prepare history
        val historyToSend = if (settings.sendHistory) {
            messages.map { Message(it.role, it.content) }
        } else {
            emptyList()
        }

        // Send message
        val response = ApiClient.sendMessage(
            ChatRequest(
                message = text,
                session_id = currentSessionId,
                output_format = settings.outputFormat,
                max_tokens = settings.maxTokens,
                spec_mode = settings.specMode,
                temperature = settings.temperature,
                conversation_history = historyToSend,
                enabled_tools = settings.enabledTools.toList(),
                user_location = location,
                show_intermediate_messages = settings.showAllIntermediateMessages,
                use_rag = settings.useRag,
                rag_top_k = settings.ragTopK,
                rag_min_similarity = settings.ragMinSimilarity.toDouble(),
                rag_filter_enabled = settings.ragFilterEnabled
            )
        )

        onLoadingChange(false)

        if (response.error != null) {
            onMessagesUpdate(messages + userMessage + Message(
                role = "assistant",
                content = "❌ ${response.error}",
                timestamp = Date().toISOString()
            ))
        } else if (response.reply != null) {
            var updatedMessages = messages + userMessage

            // Handle compression
            if (response.compression_applied && response.compressed_history != null) {
                updatedMessages = response.compressed_history
                onMessageCountUpdate(1)
                updatedMessages = updatedMessages + Message(
                    role = "assistant",
                    content = "💾 История диалога была автоматически сжата для экономии токенов. Контекст сохранён.",
                    timestamp = Date().toISOString()
                )
            }

            // Handle intermediate messages based on settings
            if (response.intermediate_messages.isNotEmpty()) {
                if (settings.showAllIntermediateMessages) {
                    // Show all intermediate messages in chat history
                    response.intermediate_messages.forEach { intermediateMsg ->
                        updatedMessages = updatedMessages + Message(
                            role = intermediateMsg.role,
                            content = "🔄 " + intermediateMsg.content,
                            timestamp = Date().toISOString()
                        )
                    }

                    // Add final response
                    updatedMessages = updatedMessages + Message(
                        role = "assistant",
                        content = response.reply,
                        timestamp = Date().toISOString(),
                        usage = response.usage
                    )
                    onMessagesUpdate(updatedMessages)
                } else {
                    // Show intermediate messages one by one, replacing each other
                    coroutineScope {
                        launch {
                            response.intermediate_messages.forEachIndexed { index, intermediateMsg ->
                                val tempMessages = updatedMessages + Message(
                                    role = intermediateMsg.role,
                                    content = "🔄 " + intermediateMsg.content,
                                    timestamp = Date().toISOString()
                                )
                                onMessagesUpdate(tempMessages)

                                // Wait a bit before showing next message (except for the last one)
                                if (index < response.intermediate_messages.size - 1) {
                                    delay(800)
                                }
                            }

                            // Show final response after last intermediate message
                            delay(500)
                            updatedMessages = updatedMessages + Message(
                                role = "assistant",
                                content = response.reply,
                                timestamp = Date().toISOString(),
                                usage = response.usage
                            )
                            onMessagesUpdate(updatedMessages)
                        }
                    }
                }
            } else {
                // No intermediate messages, just add the response
                updatedMessages = updatedMessages + Message(
                    role = "assistant",
                    content = response.reply,
                    timestamp = Date().toISOString(),
                    usage = response.usage
                )
                onMessagesUpdate(updatedMessages)
            }
        }
    } catch (e: Exception) {
        console.error("Error sending message: ${e.message}")
        onLoadingChange(false)
        onMessagesUpdate(messages + userMessage + Message(
            role = "assistant",
            content = "❌ Ошибка связи с сервером: ${e.message}",
            timestamp = Date().toISOString()
        ))
    }
}

private fun startNewChat(
    onSessionChange: (String) -> Unit,
    onMessagesChange: (List<Message>) -> Unit
) {
    val newSessionId = Utils.generateSessionId()
    onSessionChange(newSessionId)
    onMessagesChange(
        listOf(
            Message(
                role = "assistant",
                content = "Новый чат начат! Чем могу помочь? 🙂",
                timestamp = Date().toISOString()
            )
        )
    )
}

private suspend fun loadSession(
    sessionId: String,
    onSuccess: (List<Message>) -> Unit
) {
    try {
        val response = ApiClient.getSession(sessionId)
        onSuccess(response.history)
        ApiClient.markSessionRead(sessionId)
    } catch (e: Exception) {
        console.error("Error loading session: ${e.message}")
    }
}

private suspend fun loadSessions(
    current: List<ChatSession>,
    currentUnreadCounts: Map<String, Int>,
    onUpdate: (List<ChatSession>, Map<String, Int>) -> Unit
) {
    try {
        val sessionsResponse = ApiClient.getSessions()
        val unreadResponse = ApiClient.getUnreadCounts()
        onUpdate(
            sessionsResponse.sessions.sortedByDescending { it.last_updated },
            unreadResponse.unread_counts
        )
    } catch (e: Exception) {
        console.error("Error loading sessions: ${e.message}")
    }
}

private suspend fun loadTools(
    current: List<Tool>,
    onUpdate: (List<Tool>) -> Unit
) {
    try {
        val response = ApiClient.getTools()
        onUpdate(response.tools)
    } catch (e: Exception) {
        console.error("Error loading tools: ${e.message}")
    }
}

private suspend fun loadReminders(
    current: List<Reminder>,
    notifiedReminders: MutableSet<String>,
    onUpdate: (List<Reminder>) -> Unit
) {
    try {
        val response = ApiClient.getReminders()
        onUpdate(response.reminders)

        // Check for due reminders and show notifications
        val now = Date.now()
        val currentReminderIds = response.reminders.map { it.id }.toSet()

        // Remove notified reminders that no longer exist (were dismissed/deleted)
        notifiedReminders.retainAll(currentReminderIds)

        response.reminders.forEach { reminder ->
            val dueDate = Date(reminder.due_at)
            val timeLeft = dueDate.getTime() - now

            // Show notification if reminder is due and hasn't been notified yet
            if (timeLeft <= 0 && !notifiedReminders.contains(reminder.id)) {
                Utils.showBrowserNotification(
                    "🔔 Напоминание",
                    reminder.text,
                    reminder.id
                )
                notifiedReminders.add(reminder.id)
                console.log("Notification shown for reminder: ${reminder.id}")
            }
        }
    } catch (e: Exception) {
        console.error("Error loading reminders: ${e.message}")
    }
}

