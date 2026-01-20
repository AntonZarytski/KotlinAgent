package com.claude.agent.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class ChatViewModel(private val apiClient: ClaudeApiClient) {
    var messages by mutableStateOf<List<Message>>(emptyList())
        private set

    var sessions by mutableStateOf<List<ChatSession>>(emptyList())
        private set

    var currentSessionId by mutableStateOf(generateSessionId())
        private set

    var settings by mutableStateOf(Settings())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showSettingsPanel by mutableStateOf(false)

    var showHistoryPanel by mutableStateOf(false)

    var showTokenModal by mutableStateOf(false)

    var tokenCount by mutableStateOf(0)
        private set

    var messageCountSinceCompression by mutableStateOf(0)
        private set

    var availableTools by mutableStateOf<List<Tool>>(emptyList())
        private set

    init {
        messages = listOf(
            Message(
                role = "assistant",
                content = "Привет! Я твой локальный агент. Нажми 💬 для истории чатов. Нажми ⚙️ для настроек. Спроси что-нибудь 🙂"
            )
        )
    }

    suspend fun loadTools() {
        try {
            val response = apiClient.getTools()
            availableTools = response.tools
            if (settings.enabledTools.isEmpty()) {
                settings = settings.copy(enabledTools = availableTools.map { it.name }.toSet())
            }
        } catch (e: Exception) {
            println("Ошибка загрузки инструментов: ${e.message}")
        }
    }

    suspend fun sendMessage(text: String) {
        if (text.isBlank()) return

        messages = messages + Message(role = "user", content = text)

        isLoading = true

        try {
            // Создаем сессию при первом сообщении
            if (messages.count { it.role == "user" } == 1) {
                apiClient.createSession(
                    currentSessionId,
                    text.take(50) + if (text.length > 50) "..." else ""
                )
            }

            val historyToSend = if (settings.sendHistory) {
                messages.dropLast(1).map { Message(it.role, it.content) }
            } else {
                emptyList()
            }

            val request = ChatRequest(
                message = text,
                session_id = currentSessionId,
                output_format = settings.outputFormat,
                max_tokens = settings.maxTokens,
                spec_mode = settings.specMode,
                temperature = settings.temperature,
                conversation_history = historyToSend,
                enabled_tools = settings.enabledTools.toList()
            )

            val response = apiClient.sendMessage(request)

            if (response.error != null) {
                messages = messages + Message(role = "assistant", content = "❌ ${response.error}")
            } else if (response.reply != null) {
                if (response.compression_applied && response.compressed_history != null) {
                    messages = response.compressed_history
                    messageCountSinceCompression = 1
                    messages = messages + Message(
                        role = "assistant",
                        content = "💾 История диалога была автоматически сжата для экономии токенов. Контекст сохранён."
                    )
                }

                messages = messages + Message(
                    role = "assistant",
                    content = response.reply,
                    usage = response.usage
                )
                messageCountSinceCompression++
            }
        } catch (e: Exception) {
            messages = messages + Message(
                role = "assistant",
                content = "❌ Ошибка связи с сервером: ${e.message}"
            )
        } finally {
            isLoading = false
        }
    }

    suspend fun checkTokens(text: String) {
        if (text.isBlank()) {
            tokenCount = 0
            showTokenModal = true
            return
        }

        try {
            val historyToSend = if (settings.sendHistory) {
                messages.map { Message(it.role, it.content) }
            } else {
                emptyList()
            }

            val request = TokenCountRequest(
                message = text,
                output_format = settings.outputFormat,
                spec_mode = settings.specMode,
                conversation_history = historyToSend
            )

            val response = apiClient.countTokens(request)
            tokenCount = response.input_tokens
            showTokenModal = true
        } catch (e: Exception) {
            println("Ошибка подсчета токенов: ${e.message}")
        }
    }

    suspend fun loadSessions() {
        try {
            val response = apiClient.getSessions()
            sessions = response.sessions.sortedByDescending { it.last_updated }
        } catch (e: Exception) {
            println("Ошибка загрузки сессий: ${e.message}")
        }
    }

    suspend fun loadSession(sessionId: String) {
        try {
            val response = apiClient.getSession(sessionId)
            messages = response.history
            currentSessionId = sessionId
            messageCountSinceCompression = 0
            showHistoryPanel = false
        } catch (e: Exception) {
            println("Ошибка загрузки сессии: ${e.message}")
        }
    }

    suspend fun deleteSession(sessionId: String) {
        try {
            apiClient.deleteSession(sessionId)
            if (sessionId == currentSessionId) {
                startNewChat()
            }
            loadSessions()
        } catch (e: Exception) {
            println("Ошибка удаления сессии: ${e.message}")
        }
    }

    fun updateSettings(newSettings: Settings) {
        settings = newSettings
    }

    fun toggleSettingsPanel() {
        showSettingsPanel = !showSettingsPanel
        if (showSettingsPanel) showHistoryPanel = false
    }

    fun toggleHistoryPanel() {
        showHistoryPanel = !showHistoryPanel
        if (showHistoryPanel) showSettingsPanel = false
    }

    fun clearHistory() {
        messages = listOf(
            Message(
                role = "assistant",
                content = "История очищена. Начнём новый диалог! 🙂"
            )
        )
        currentSessionId = generateSessionId()
        messageCountSinceCompression = 0
    }

    fun startNewChat() {
        messages = listOf(
            Message(
                role = "assistant",
                content = "Новый чат начат! Чем могу помочь? 🙂"
            )
        )
        currentSessionId = generateSessionId()
        messageCountSinceCompression = 0
        showHistoryPanel = false
    }

    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${UUID.randomUUID()}"
    }
}
