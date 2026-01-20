package com.claude.agent.ui

import androidx.compose.runtime.*
import kotlinx.browser.document
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLTextAreaElement

@Composable
fun ChatHeader(
    onHistoryClick: () -> Unit,
    onReminderClick: () -> Unit,
    onFileTreeClick: () -> Unit,
    onTicketsClick: () -> Unit,
    onTokensClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Div({ classes("header") }) {
        // Left buttons
        Div({ classes("header-buttons", "header-buttons-left") }) {
            Button({
                classes("icon-button")
                onClick { onHistoryClick() }
                attr("title", "История чатов")
            }) {
                Text("💬")
            }
            Button({
                classes("icon-button")
                onClick { onReminderClick() }
                attr("title", "Напоминания")
            }) {
                Text("🔔")
            }
            Button({
                classes("icon-button")
                onClick { onFileTreeClick() }
                attr("title", "Дерево файлов проекта")
            }) {
                Text("📁")
            }
            Button({
                classes("icon-button")
                onClick { onTicketsClick() }
                attr("title", "Тикеты задач")
            }) {
                Text("🎫")
            }
        }

        // Title
        Div({ classes("header-title") }) {
            Text("🤖")
            Span { Text(" Claude AI Chat ") }
            Span({ classes("status-indicator") })
            Span({
                style {
                    property("font-size", "10px")
                    property("color", "#9ca3af")
                    property("font-weight", "400")
                    property("margin-left", "12px")
                }
            }) {
                Text("v${BuildInfo.BUILD_TIME}")
            }
        }

        // Right buttons
        Div({ classes("header-buttons", "header-buttons-right") }) {
            Button({
                classes("icon-button")
                onClick { onTokensClick() }
                attr("title", "Подсчитать токены")
            }) {
                Text("🔢")
            }
            Button({
                classes("icon-button")
                onClick { onSettingsClick() }
                attr("title", "Настройки")
            }) {
                Text("⚙️")
            }
        }
    }
}

@Composable
fun ChatMessages(
    messages: List<Message>,
    isLoading: Boolean,
    showTokenCount: Boolean,
    streamingText: String? = null
) {
    // Auto-scroll to bottom when messages change or streaming text updates
    LaunchedEffect(messages.size, isLoading, streamingText) {
        document.getElementById("chat")?.let { chat ->
            chat.scrollTop = chat.scrollHeight.toDouble()
        }
    }

    Div({ classes("chat"); id("chat") }) {
        messages.forEach { message ->
            MessageItem(message, showTokenCount)
        }

        // Show streaming message (intermediate text while thinking)
        if (streamingText != null && streamingText.isNotEmpty()) {
            StreamingMessageItem(streamingText)
        }

        if (isLoading) {
            LoadingIndicator()
        }

        // Spacer to prevent content from being hidden behind fixed input panel
        Div({
            style {
                property("height", "1px")
                property("flex-shrink", "0")
            }
        })
    }
}

@Composable
fun MessageItem(message: Message, showTokenCount: Boolean) {
    val isUser = message.role == "user"

    Div({
        classes("message-wrapper")
        if (isUser) {
            classes("user")
        }
    }) {
        if (!isUser) {
            Div({ classes("avatar", "assistant") }) {
                Text("🤖")
            }
        }

        Div({ classes("message-container") }) {
            Div({ classes("message", if (isUser) "user" else "assistant") }) {
                if (isUser) {
                    Text(message.content)
                } else {
                    Div({
                        classes("markdown-content")
                        ref { element ->
                            element.innerHTML = Utils.parseMarkdown(message.content)
                            onDispose { }
                        }
                    })
                }

                // Токены внутри сообщения (только для ассистента)
                if (!isUser && showTokenCount && message.usage != null) {
                    val total = message.usage.input_tokens + message.usage.output_tokens
                    Div({
                        style {
                            property("font-size", "12px")
                            property("color", "#9ca3af")
                            property("margin-top", "8px")
                            property("padding-top", "8px")
                            property("border-top", "1px solid #e5e7eb")
                        }
                    }) {
                        Text("📊 Потрачено токенов: $total (вход: ${message.usage.input_tokens}, выход: ${message.usage.output_tokens})")
                    }
                }
            }

            // Временная метка справа от сообщения, выровнена по низу
            if (!message.timestamp.isNullOrEmpty()) {
                Div({ classes("message-timestamp") }) {
                    Text(Utils.formatTimestamp(message.timestamp))
                }
            }
        }

        if (isUser) {
            Div({ classes("avatar", "user") }) {
                Text("👤")
            }
        }
    }
}

@Composable
fun StreamingMessageItem(text: String) {
    // Используем DisposableEffect для обновления innerHTML при изменении text
    DisposableEffect(text) {
        val element = kotlinx.browser.document.getElementById("streaming-content")
        if (element != null) {
            element.innerHTML = Utils.parseMarkdown(text)
        }
        onDispose { }
    }

    Div({ classes("message-wrapper") }) {
        Div({ classes("avatar", "assistant") }) {
            Text("🤖")
        }

        Div({ classes("message-container") }) {
            Div({
                classes("message", "assistant")
                style {
                    // Особый стиль для промежуточных сообщений
                    property("background", "linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)")
                    property("border-left", "4px solid #f59e0b")
                    property("animation", "pulse 2s ease-in-out infinite")
                }
            }) {
                Div({
                    style {
                        property("font-size", "11px")
                        property("color", "#92400e")
                        property("font-weight", "600")
                        property("margin-bottom", "8px")
                        property("text-transform", "uppercase")
                        property("letter-spacing", "0.5px")
                    }
                }) {
                    Text("💭 Размышляю...")
                }

                Div({
                    classes("markdown-content")
                    id("streaming-content")
                })
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Div({ classes("message-wrapper") }) {
        Div({ classes("avatar", "assistant") }) {
            Text("🤖")
        }

        Div({ classes("message", "assistant") }) {
            Text("Модель думает...")
        }
    }
}

@Composable
fun InputPanel(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Div({ classes("input-group") }) {
        TextArea(inputText) {
            classes("textarea")
            id("input")
            attr("placeholder", "Введите ваше сообщение... (Shift+Enter для новой строки)")
            attr("rows", "1")
            onInput { event ->
                onInputChange((event.target as HTMLTextAreaElement).value)
            }
            onKeyDown { event ->
                if (event.key == "Enter" && !event.shiftKey) {
                    event.preventDefault()
                    if (enabled && inputText.isNotBlank()) {
                        onSend()
                    }
                }
            }
            if (!enabled) {
                disabled()
            }
        }

        Button({
            classes("send-button")
            onClick {
                if (enabled && inputText.isNotBlank()) {
                    onSend()
                }
            }
            if (!enabled || inputText.isBlank()) {
                disabled()
            }
        }) {
            Text("✨ Отправить")
        }
    }
}

@Composable
fun TokenModal(
    visible: Boolean,
    tokenCount: Int,
    onClose: () -> Unit
) {
    if (!visible) return

    Div({
        style {
            property("position", "fixed")
            property("top", "0")
            property("left", "0")
            property("width", "100%")
            property("height", "100%")
            property("background", "rgba(0, 0, 0, 0.5)")
            property("display", "flex")
            property("justify-content", "center")
            property("align-items", "center")
            property("z-index", "10000")
        }
        onClick { onClose() }
    }) {
        Div({
            style {
                property("background", "white")
                property("padding", "24px")
                property("border-radius", "20px")
                property("max-width", "400px")
                property("text-align", "center")
            }
            onClick { event -> event.stopPropagation() }
        }) {
            H3 { Text("📊 Подсчёт токенов") }
            Div({
                style {
                    property("font-size", "14px")
                    property("color", "#6b7280")
                    property("margin", "16px 0")
                }
            }) {
                Text("Количество входных токенов:")
            }
            Div({
                style {
                    property("font-size", "48px")
                    property("font-weight", "700")
                    property("color", "#6366f1")
                    property("margin", "16px 0")
                }
            }) {
                Text(tokenCount.toString())
            }
            Button({
                classes("send-button")
                style {
                    property("margin-top", "16px")
                }
                onClick { onClose() }
            }) {
                Text("Понятно")
            }
        }
    }
}
