package com.claude.agent.ui

import androidx.compose.runtime.*
import com.claude.agent.ui.Utils.createMediaRecorder
import com.claude.agent.ui.Utils.isMediaRecorderSupported
import com.claude.agent.ui.Utils.requestMicrophoneAccess
import com.claude.agent.ui.Utils.stopMediaStream
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
    var tickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var tools by remember { mutableStateOf<List<Tool>>(emptyList()) }
    var unreadCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var currentSessionId by remember { mutableStateOf(Utils.generateSessionId()) }
    var settings by remember { mutableStateOf(Settings()) }

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var showSettingsPanel by remember { mutableStateOf(false) }
    var showHistoryPanel by remember { mutableStateOf(false) }
    var showReminderPanel by remember { mutableStateOf(false) }
    var showFileTreePanel by remember { mutableStateOf(false) }
    var showTicketsPanel by remember { mutableStateOf(false) }
    var showTokenModal by remember { mutableStateOf(false) }
    var tokenCount by remember { mutableStateOf(0) }

    var messageCountSinceCompression by remember { mutableStateOf(0) }
    var userLocation by remember { mutableStateOf<UserLocation?>(null) }

    // Streaming state for real-time message updates
    var streamingText by remember { mutableStateOf<String?>(null) }
    var streamingIteration by remember { mutableStateOf(0) }
    var toolResults by remember { mutableStateOf<List<ToolResultData>>(emptyList()) }

    // File tree state
    var fileTree by remember { mutableStateOf<FileTreeNode?>(null) }
    var selectedFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedDirs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var projectPath by remember { mutableStateOf<String?>(null) }

    // Track which reminders have been notified to avoid duplicate notifications
    val notifiedReminders = remember { mutableSetOf<String>() }

    // Voice recording state
    var isRecording by remember { mutableStateOf(false) }
    var mediaStream by remember { mutableStateOf<dynamic>(null) }
    var mediaRecorder by remember { mutableStateOf<dynamic>(null) }
    var audioChunks by remember { mutableStateOf<List<dynamic>>(emptyList()) }

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
                // Добавляем tool result в список для отображения
                toolResults = toolResults + data
                console.log("🔧 Tool results count: ${toolResults.size}")
            },
            onNewMessageCallback = { message ->
                console.log("📨 New message from WebSocket: ${message.content.take(100)}")
                // Clear streaming state when final message arrives
                streamingText = null
                streamingIteration = 0
                toolResults = emptyList()  // Очищаем tool results
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

    // Voice recording functions
    suspend fun startRecording() {
        try {
            // Check if MediaRecorder is supported
            if (!isMediaRecorderSupported()) {
                console.error("MediaRecorder API is not supported in this browser")
                return
            }

            // 🔥 КРИТИЧНО: Очищаем старые chunks перед началом новой записи!
            audioChunks = emptyList()
            console.log("🧹 Audio chunks cleared before recording")

            // Request microphone access
            val stream = requestMicrophoneAccess()
            mediaStream = stream

            // Create MediaRecorder
            val recorder = createMediaRecorder(stream) { chunk ->
                // Collect audio chunks
                audioChunks = audioChunks + listOf(chunk)
            }
            mediaRecorder = recorder

            // Start recording
            recorder.start()
            isRecording = true
            console.log("Recording started")
        } catch (e: Exception) {
            console.error("Failed to start recording:", e.message)
            isRecording = false
        }
    }

    suspend fun sendVoiceMessage(audioBlob: dynamic) {
        try {
            isLoading = true
            // Очищаем промежуточные сообщения перед началом нового запроса
            streamingText = null
            streamingIteration = 0
            toolResults = emptyList()

            // 🔥 ШАГ 1: Сначала отправляем аудио на сервер для распознавания
            // (НЕ добавляем userMessage в UI, потому что ещё не знаем текст!)
            console.log("📤 Sending audio to server for recognition...")
            val response = ApiClient.sendVoiceMessage(
                audioBlob = audioBlob,
                sessionId = currentSessionId,
                llmProvider = settings.llmProvider
            )

            // 🔥 ШАГ 2: СРАЗУ добавляем сообщение пользователя в UI
            // (ДО того как сервер начнёт генерировать ответ через LLM)
            val userMessage = Message(
                role = "user",
                content = response.recognized_text,
                timestamp = Date().toISOString()
            )
            messages = messages + userMessage
            console.log("✅ User message added FIRST: ${response.recognized_text}")

            // Прокручиваем к новому сообщению
            scope.launch {
                delay(100)
                Utils.scrollToBottom()
            }

            // 🔥 ШАГ 3: WebSocket отправит финальный ответ ассистента через onNewMessageCallback
            // Поэтому здесь НЕ добавляем ответ ассистента вручную

            isLoading = false
            // Очищаем промежуточные сообщения после завершения
            streamingText = null
            streamingIteration = 0
            toolResults = emptyList()
        } catch (e: Exception) {
            console.error("Failed to send voice message:", e.message)
            isLoading = false
            streamingText = null
            streamingIteration = 0
            toolResults = emptyList()
        }
    }

    suspend fun stopRecording() {
        try {
            // Stop the MediaRecorder
            mediaRecorder?.stop()

            // Stop the MediaStream
            val stream = mediaStream
            if (stream != null) {
                stopMediaStream(stream)
            }

            // Create audio blob from chunks
            // Convert Kotlin list to JavaScript array
            val chunksArray = audioChunks.toTypedArray().asDynamic()
            val audioBlob = js("new Blob(chunksArray, { type: 'audio/webm' })")

            // Clear state
            audioChunks = emptyList()
            mediaStream = null
            mediaRecorder = null
            isRecording = false
            console.log("Recording stopped")

            // Send voice message in coroutine
            scope.launch {
                sendVoiceMessage(audioBlob)
            }
        } catch (e: Exception) {
            console.error("Failed to stop recording:", e.message)
            isRecording = false
        }
    }

    // Main UI
    Div({
        classes("container")
        style {
            property("display", "flex")
            property("flex-direction", "row")
            property("width", "100%")
            property("height", "100%")
        }
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

        // Ticket Panel
        if (showTicketsPanel) {
            TicketPanel(
                tickets = tickets,
                onClose = { showTicketsPanel = false }
            )
        }

        // File Tree Panel (left sidebar)
        if (showFileTreePanel) {
            Div({
                classes("file-tree-container")
                style {
                    property("width", "300px")
                    property("height", "100%")
                    property("border-right", "1px solid #e5e7eb")
                    property("background", "#f9fafb")
                    property("overflow-y", "auto")
                    property("display", "flex")
                    property("flex-direction", "column")
                }
            }) {
                // Header
                Div({
                    style {
                        property("padding", "12px 16px")
                        property("border-bottom", "1px solid #e5e7eb")
                        property("background", "white")
                        property("font-weight", "600")
                        property("font-size", "14px")
                        property("display", "flex")
                        property("align-items", "center")
                        property("justify-content", "space-between")
                    }
                }) {
                    Span { Text("📁 Project Files") }
                    Button({
                        classes("icon-button")
                        onClick {
                            scope.launch {
                                loadFileTree { tree, path ->
                                    fileTree = tree
                                    projectPath = path
                                }
                            }
                        }
                        attr("title", "Reload file tree")
                        style {
                            property("padding", "4px 8px")
                            property("font-size", "12px")
                        }
                    }) {
                        Text("🔄")
                    }
                }

                // Project path with "Up" button
                if (projectPath != null) {
                    Div({
                        style {
                            property("padding", "8px 16px")
                            property("font-size", "11px")
                            property("color", "#6b7280")
                            property("border-bottom", "1px solid #e5e7eb")
                            property("background", "#f3f4f6")
                            property("display", "flex")
                            property("align-items", "center")
                            property("gap", "8px")
                        }
                    }) {
                        // "Up" button to navigate to parent directory
                        Button({
                            style {
                                property("background", "#6366f1")
                                property("color", "white")
                                property("border", "none")
                                property("border-radius", "4px")
                                property("padding", "4px 8px")
                                property("cursor", "pointer")
                                property("font-size", "12px")
                                property("display", "flex")
                                property("align-items", "center")
                                property("gap", "4px")
                            }
                            onClick {
                                console.log("⬆️ Navigating up from: $projectPath")
                                scope.launch {
                                    try {
                                        // Get parent directory
                                        val parentPath = projectPath?.substringBeforeLast("/")
                                        if (parentPath != null && parentPath.isNotEmpty()) {
                                            console.log("⬆️ Parent path: $parentPath")
                                            val response = ApiClient.setProjectPath(parentPath, currentSessionId)
                                            if (response.success) {
                                                console.log("✅ Project path set to: ${response.projectPath}")
                                                projectPath = response.projectPath ?: parentPath

                                                // Перезагружаем дерево файлов с новым путем
                                                console.log("🔄 Reloading file tree for parent path...")
                                                loadFileTree { tree, newPath ->
                                                    fileTree = tree
                                                    projectPath = newPath
                                                    expandedDirs = emptySet()
                                                    console.log("✅ File tree reloaded for path: $newPath")
                                                }
                                            } else {
                                                console.error("❌ Failed to set project path: ${response.error}")
                                            }
                                        } else {
                                            console.warn("⚠️ Already at root directory")
                                        }
                                    } catch (e: Exception) {
                                        console.error("❌ Error navigating up: ${e.message}")
                                    }
                                }
                            }
                        }) {
                            Text("⬆️ Вверх")
                        }

                        // Current path
                        Div({
                            style {
                                property("flex", "1")
                                property("word-break", "break-all")
                            }
                        }) {
                            Text(projectPath!!)
                        }
                    }
                }

                // Selected files count
                if (selectedFiles.isNotEmpty()) {
                    Div({
                        style {
                            property("padding", "8px 16px")
                            property("font-size", "12px")
                            property("color", "#059669")
                            property("background", "#d1fae5")
                            property("border-bottom", "1px solid #a7f3d0")
                            property("font-weight", "500")
                        }
                    }) {
                        Text("✓ ${selectedFiles.size} file(s) selected")
                    }
                }

                // Tree content
                Div({
                    style {
                        property("flex", "1")
                        property("overflow-y", "auto")
                        property("padding", "8px 0")
                    }
                }) {
                    if (fileTree != null) {
                        FileTreeItem(
                            node = fileTree!!,
                            level = 0,
                            selectedFiles = selectedFiles,
                            onFileToggle = { filePath ->
                                selectedFiles = if (selectedFiles.contains(filePath)) {
                                    selectedFiles - filePath
                                } else {
                                    selectedFiles + filePath
                                }
                            },
                            expandedDirs = expandedDirs,
                            onDirToggle = { dirPath ->
                                expandedDirs = if (expandedDirs.contains(dirPath)) {
                                    expandedDirs - dirPath
                                } else {
                                    expandedDirs + dirPath
                                }
                            },
                            onProjectPathChange = { absolutePath ->
                                console.log("📁 Double-clicked on folder: $absolutePath")
                                scope.launch {
                                    try {
                                        // Путь уже абсолютный, используем его напрямую
                                        console.log("📁 Setting project path to: $absolutePath")

                                        val response = ApiClient.setProjectPath(absolutePath, currentSessionId)
                                        if (response.success) {
                                            console.log("✅ Project path set to: ${response.projectPath}")
                                            projectPath = response.projectPath ?: absolutePath

                                            // Перезагружаем дерево файлов с новым путем
                                            console.log("🔄 Reloading file tree for new path...")
                                            loadFileTree { tree, newPath ->
                                                fileTree = tree
                                                projectPath = newPath
                                                // Сбрасываем состояние раскрытых папок
                                                expandedDirs = emptySet()
                                                console.log("✅ File tree reloaded for path: $newPath")
                                            }
                                        } else {
                                            console.error("❌ Failed to set project path: ${response.error}")
                                        }
                                    } catch (e: Exception) {
                                        console.error("❌ Error setting project path: ${e.message}")
                                    }
                                }
                            }
                        )
                    } else {
                        Div({
                            style {
                                property("padding", "32px 16px")
                                property("text-align", "center")
                                property("color", "#9ca3af")
                                property("font-size", "14px")
                            }
                        }) {
                            Div { Text("📂") }
                            Br()
                            Text("Загрузка дерева файлов...")
                        }
                    }
                }
            }
        }

        // Main Chat Area
        Div({
            classes("chat-area")
            style {
                property("flex", "1")
                property("display", "flex")
                property("flex-direction", "column")
            }
        }) {
            ChatHeader(
                onHistoryClick = {
                    showHistoryPanel = !showHistoryPanel
                    if (showHistoryPanel) {
                        showSettingsPanel = false
                        showReminderPanel = false
                        showFileTreePanel = false
                        showTicketsPanel = false
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
                        showFileTreePanel = false
                        showTicketsPanel = false
                        scope.launch {
                            loadReminders(reminders, notifiedReminders) { reminders = it }
                        }
                    }
                },
                onFileTreeClick = {
                    showFileTreePanel = !showFileTreePanel
                    if (showFileTreePanel) {
                        showSettingsPanel = false
                        showHistoryPanel = false
                        showReminderPanel = false
                        showTicketsPanel = false
                        // Загружаем дерево файлов если еще не загружено
                        if (fileTree == null) {
                            scope.launch {
                                loadFileTree { tree, path ->
                                    fileTree = tree
                                    projectPath = path
                                }
                            }
                        }
                    }
                },
                onTicketsClick = {
                    showTicketsPanel = !showTicketsPanel
                    if (showTicketsPanel) {
                        showSettingsPanel = false
                        showHistoryPanel = false
                        showReminderPanel = false
                        showFileTreePanel = false
                        console.log("🎫 Opening tickets panel for session: $currentSessionId")
                        scope.launch {
                            try {
                                console.log("🎫 Loading tickets...")
                                val response = ApiClient.getTickets(currentSessionId)
                                tickets = response.tickets
                                console.log("🎫 Loaded ${tickets.size} tickets")
                            } catch (e: Exception) {
                                console.error("❌ Failed to load tickets: ${e.message}")
                                console.error(e)
                            }
                        }
                    } else {
                        console.log("🎫 Closing tickets panel")
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
                        showFileTreePanel = false
                        showTicketsPanel = false
                        scope.launch {
                            loadTools(tools) { tools = it }
                        }
                    }
                }
            )

            ChatMessages(
                messages = messages,
                isLoading = isLoading,
                streamingText = streamingText,
                toolResults = toolResults
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
                            selectedFiles = selectedFiles,
                            projectPath = projectPath,
                            onMessagesUpdate = { messages = it },
                            onLoadingChange = { isLoading = it },
                            onInputClear = { inputText = "" },
                            onMessageCountUpdate = { messageCountSinceCompression = it },
                            onStreamingTextClear = {  // 🆕 Очистка промежуточных сообщений
                                streamingText = null
                                streamingIteration = 0
                                toolResults = emptyList()  // Очищаем tool results
                            }
                        )
                    }
                },
                enabled = !isLoading,
                isRecording = isRecording,
                onVoiceRecordStart = {
                    console.log("🎤 Voice record start button clicked")
                    scope.launch {
                        startRecording()
                    }
                },
                onVoiceRecordStop = {
                    console.log("⏹️ Voice record stop button clicked")
                    scope.launch {
                        stopRecording()
                    }
                },
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
    selectedFiles: Set<String>,
    projectPath: String?,
    onMessagesUpdate: (List<Message>) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onInputClear: () -> Unit,
    onMessageCountUpdate: (Int) -> Unit,
    onStreamingTextClear: () -> Unit  // 🆕 Добавляем callback для очистки streamingText
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
    // 🆕 Очищаем промежуточные сообщения перед началом нового запроса
    onStreamingTextClear()

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

        // Prepare history (фильтруем промежуточные сообщения)
        val historyToSend = if (settings.sendHistory) {
            messages
                .filter { !it.is_intermediate }  // Исключаем промежуточные сообщения
                .map { Message(it.role, it.content) }
        } else {
            emptyList()
        }

        // Send message
        // Формируем полные пути для выбранных файлов
        // Проверяем, является ли путь уже абсолютным (начинается с /)
        val fullPathFiles = selectedFiles.map { filePath ->
            if (filePath.startsWith("/") || filePath.matches(Regex("^[A-Za-z]:\\\\.+"))) {
                // Уже абсолютный путь - используем как есть
                filePath
            } else if (projectPath != null) {
                // Относительный путь - добавляем projectPath
                "$projectPath/$filePath"
            } else {
                // Нет projectPath - используем как есть
                filePath
            }
        }

        if (fullPathFiles.isNotEmpty()) {
            console.log("📎 Sending ${fullPathFiles.size} selected files with full paths:")
            fullPathFiles.forEach { path ->
                console.log("  - $path")
            }
        }

        val response = ApiClient.sendMessage(
            ChatRequest(
                message = text,
                session_id = currentSessionId,
                max_tokens = settings.maxTokens,
                spec_mode = settings.specMode,
                temperature = settings.temperature,
                top_p = settings.topP.toDouble(),
                top_k = settings.topK,
                context_window = settings.contextWindow,
                conversation_history = historyToSend,
                enabled_tools = settings.enabledTools.toList(),
                user_location = location,
                use_rag = settings.useRag,
                rag_top_k = settings.ragTopK,
                rag_min_similarity = settings.ragMinSimilarity.toDouble(),
                rag_filter_enabled = settings.ragFilterEnabled,
                selected_files = fullPathFiles,
                llm_provider = settings.llmProvider  // 🆕 Передаем выбранный провайдер
            )
        )

        onLoadingChange(false)
        // 🆕 Очищаем промежуточные сообщения после получения ответа
        onStreamingTextClear()

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

            // Handle intermediate messages - always show all in chat history
            if (response.intermediate_messages.isNotEmpty()) {
                // Show all intermediate messages in chat history (marked as intermediate)
                response.intermediate_messages.forEach { intermediateMsg ->
                    updatedMessages = updatedMessages + Message(
                        role = intermediateMsg.role,
                        content = "🔄 " + intermediateMsg.content,
                        timestamp = Date().toISOString(),
                        is_intermediate = true  // Помечаем как промежуточное
                    )
                }

                // Add final response
                updatedMessages = updatedMessages + Message(
                    role = "assistant",
                    content = response.reply,
                    timestamp = Date().toISOString(),
                    usage = response.usage,
                    is_intermediate = false
                )
                onMessagesUpdate(updatedMessages)
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

private suspend fun loadFileTree(
    onUpdate: (FileTreeNode?, String?) -> Unit
) {
    try {
        val response = ApiClient.getFileTree()
        if (response.error != null) {
            console.error("Error loading file tree: ${response.error}")
        } else {
            console.log("📂 File tree loaded, projectPath: ${response.projectPath}")
            console.log("📂 Root node path: ${response.tree?.path}")
            if (response.tree?.children?.isNotEmpty() == true) {
                console.log("📂 First child path: ${response.tree.children?.firstOrNull()?.path}")
            }
            onUpdate(response.tree, response.projectPath)
        }
    } catch (e: Exception) {
        console.error("Error loading file tree: ${e.message}")
    }
}

