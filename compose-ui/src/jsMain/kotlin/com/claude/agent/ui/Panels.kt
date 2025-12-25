package com.claude.agent.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import kotlin.js.Date

@Composable
fun SettingsPanel(
    visible: Boolean,
    settings: Settings,
    tools: List<Tool>,
    onClose: () -> Unit,
    onSettingsChange: (Settings) -> Unit,
    onClearHistory: () -> Unit
) {
    Div({
        classes("panel")
        if (visible) classes("active")
    }) {
        Div({
            style {
                property("background", "#6366f1")
                property("color", "white")
                property("padding", "20px")
                property("display", "flex")
                property("justify-content", "space-between")
                property("align-items", "center")
            }
        }) {
            H2({
                style { property("margin", "0"); property("font-size", "20px") }
            }) {
                Text("⚙️ Настройки")
            }
            Button({
                style {
                    property("background", "transparent")
                    property("border", "0")
                    property("color", "white")
                    property("font-size", "28px")
                    property("cursor", "pointer")
                }
                onClick { onClose() }
            }) {
                Text("×")
            }
        }

        Div({
            style {
                property("flex", "1")
                property("overflow-y", "auto")
                property("padding", "24px")
                property("padding-bottom", "150px")
                property("scroll-behavior", "smooth")
            }
        }) {
            // Output Format
            Div({ style { property("margin-bottom", "24px") } }) {
                Label(null, {
                    style {
                        property("display", "block")
                        property("font-size", "14px")
                        property("font-weight", "600")
                        property("color", "#374151")
                        property("margin-bottom", "8px")
                    }
                }) {
                    Text("📋 Формат ответа")
                }
                Select({
                    style {
                        property("width", "100%")
                        property("padding", "12px 16px")
                        property("border", "2px solid #e5e7eb")
                        property("border-radius", "12px")
                        property("font-size", "14px")
                    }
                    onChange { event ->
                        val value = (event.target as HTMLSelectElement).value
                        onSettingsChange(settings.copy(outputFormat = value))
                    }
                }) {
                    Option("default", { if (settings.outputFormat == "default") selected() }) { Text("По умолчанию (текст)") }
                    Option("json", { if (settings.outputFormat == "json") selected() }) { Text("JSON") }
                    Option("xml", { if (settings.outputFormat == "xml") selected() }) { Text("XML") }
                }
            }

            // Max Tokens
            Div({ style { property("margin-bottom", "24px") } }) {
                Label(null, {
                    style {
                        property("display", "block")
                        property("font-size", "14px")
                        property("font-weight", "600")
                        property("color", "#374151")
                        property("margin-bottom", "8px")
                    }
                }) {
                    Text("📊 Максимум токенов: ${settings.maxTokens}")
                }
                Input(InputType.Range) {
                    attr("min", "128")
                    attr("max", "4096")
                    attr("step", "64")
                    value("${settings.maxTokens}")
                    style {
                        property("width", "100%")
                    }
                    onInput { event ->
                        val value = (event.target as HTMLInputElement).value.toIntOrNull() ?: 1024
                        onSettingsChange(settings.copy(maxTokens = value))
                    }
                }
            }

            // Temperature
            Div({ style { property("margin-bottom", "24px") } }) {
                Label(null, {
                    style {
                        property("display", "block")
                        property("font-size", "14px")
                        property("font-weight", "600")
                        property("color", "#374151")
                        property("margin-bottom", "8px")
                    }
                }) {
                    Text("🌡️ Температура: ${settings.temperature}")
                }
                Input(InputType.Range) {
                    attr("min", "0")
                    attr("max", "1")
                    attr("step", "0.1")
                    value("${settings.temperature}")
                    style {
                        property("width", "100%")
                    }
                    onInput { event ->
                        val value = (event.target as HTMLInputElement).value.toFloatOrNull() ?: 1.0f
                        onSettingsChange(settings.copy(temperature = value))
                    }
                }
            }

            // Spec Mode
            Div({ style { property("margin-bottom", "24px") } }) {
                Label(null, {
                    style {
                        property("display", "flex")
                        property("align-items", "center")
                        property("gap", "12px")
                        property("cursor", "pointer")
                        property("padding", "12px 16px")
                        property("background", "#f9fafb")
                        property("border-radius", "12px")
                    }
                }) {
                    CheckboxInput {
                        checked(settings.specMode)
                        onInput { event ->
                            val checked = (event.target as HTMLInputElement).checked
                            onSettingsChange(settings.copy(specMode = checked))
                        }
                    }
                    Text("🎯 Собирать уточняющие данные")
                }
            }

            // Send History
            Div({ style { property("margin-bottom", "24px") } }) {
                Label(null, {
                    style {
                        property("display", "flex")
                        property("align-items", "center")
                        property("gap", "12px")
                        property("cursor", "pointer")
                        property("padding", "12px 16px")
                        property("background", "#f9fafb")
                        property("border-radius", "12px")
                    }
                }) {
                    CheckboxInput {
                        checked(settings.sendHistory)
                        onInput { event ->
                            val checked = (event.target as HTMLInputElement).checked
                            onSettingsChange(settings.copy(sendHistory = checked))
                        }
                    }
                    Text("💬 Отправлять историю диалога")
                }
            }

            // Show Token Count
            Div({ style { property("margin-bottom", "24px") } }) {
                Label(null, {
                    style {
                        property("display", "flex")
                        property("align-items", "center")
                        property("gap", "12px")
                        property("cursor", "pointer")
                        property("padding", "12px 16px")
                        property("background", "#f9fafb")
                        property("border-radius", "12px")
                    }
                }) {
                    CheckboxInput {
                        checked(settings.showTokenCount)
                        onInput { event ->
                            val checked = (event.target as HTMLInputElement).checked
                            onSettingsChange(settings.copy(showTokenCount = checked))
                        }
                    }
                    Text("📊 Показывать количество токенов")
                }
            }

            // Show Intermediate Messages
            Div({ style { property("margin-bottom", "24px") } }) {
                Label(null, {
                    style {
                        property("display", "flex")
                        property("align-items", "center")
                        property("gap", "12px")
                        property("cursor", "pointer")
                        property("padding", "12px 16px")
                        property("background", "#f9fafb")
                        property("border-radius", "12px")
                    }
                }) {
                    CheckboxInput {
                        checked(settings.showAllIntermediateMessages)
                        onInput { event ->
                            val checked = (event.target as HTMLInputElement).checked
                            onSettingsChange(settings.copy(showAllIntermediateMessages = checked))
                        }
                    }
                    Text("💬 Показывать все промежуточные сообщения")
                }
            }

            // === RAG Settings ===
            Div({
                style {
                    property("margin-bottom", "24px")
                    property("padding", "20px")
                    property("background", "#f0fdf4")
                    property("border", "2px solid #86efac")
                    property("border-radius", "16px")
                }
            }) {
                // RAG Header
                Div({ style { property("margin-bottom", "16px") } }) {
                    H3({
                        style {
                            property("margin", "0")
                            property("font-size", "16px")
                            property("font-weight", "700")
                            property("color", "#166534")
                        }
                    }) {
                        Text("🔍 RAG (Retrieval-Augmented Generation)")
                    }
                }

                // Use RAG Checkbox
                Div({ style { property("margin-bottom", "16px") } }) {
                    Label(null, {
                        style {
                            property("display", "flex")
                            property("align-items", "center")
                            property("gap", "12px")
                            property("cursor", "pointer")
                            property("padding", "12px 16px")
                            property("background", "white")
                            property("border-radius", "12px")
                        }
                    }) {
                        CheckboxInput {
                            checked(settings.useRag)
                            onInput { event ->
                                val checked = (event.target as HTMLInputElement).checked
                                onSettingsChange(settings.copy(useRag = checked))
                            }
                        }
                        Text("📚 Использовать RAG для контекста")
                    }
                }

                // RAG Filter Enabled Checkbox
                if (settings.useRag) {
                    Div({ style { property("margin-bottom", "16px") } }) {
                        Label(null, {
                            style {
                                property("display", "flex")
                                property("align-items", "center")
                                property("gap", "12px")
                                property("cursor", "pointer")
                                property("padding", "12px 16px")
                                property("background", "white")
                                property("border-radius", "12px")
                            }
                        }) {
                            CheckboxInput {
                                checked(settings.ragFilterEnabled)
                                onInput { event ->
                                    val checked = (event.target as HTMLInputElement).checked
                                    onSettingsChange(settings.copy(ragFilterEnabled = checked))
                                }
                            }
                            Text("✅ Включить фильтрацию по порогу схожести")
                        }
                    }

                    // Top-K Slider
                    Div({ style { property("margin-bottom", "16px") } }) {
                        Label(null, {
                            style {
                                property("display", "block")
                                property("font-size", "14px")
                                property("font-weight", "600")
                                property("color", "#166534")
                                property("margin-bottom", "8px")
                            }
                        }) {
                            Text("📊 Top-K результатов: ${settings.ragTopK}")
                        }
                        Input(InputType.Range) {
                            attr("min", "1")
                            attr("max", "10")
                            attr("step", "1")
                            value("${settings.ragTopK}")
                            style {
                                property("width", "100%")
                            }
                            onInput { event ->
                                val value = (event.target as HTMLInputElement).value.toIntOrNull() ?: 3
                                onSettingsChange(settings.copy(ragTopK = value))
                            }
                        }
                        Div({
                            style {
                                property("font-size", "12px")
                                property("color", "#059669")
                                property("margin-top", "4px")
                            }
                        }) {
                            Text("Количество релевантных документов для контекста")
                        }
                    }

                    // Min Similarity Slider
                    Div({
                        style {
                            property("margin-bottom", "8px")
                            property("opacity", if (settings.ragFilterEnabled) "1" else "0.5")
                        }
                    }) {
                        Label(null, {
                            style {
                                property("display", "block")
                                property("font-size", "14px")
                                property("font-weight", "600")
                                property("color", "#166534")
                                property("margin-bottom", "8px")
                            }
                        }) {
                            val formatted = (settings.ragMinSimilarity * 100).toInt() / 100.0
                            Text("🎯 Минимальная схожесть: $formatted")
                        }
                        Input(InputType.Range) {
                            attr("min", "0")
                            attr("max", "1")
                            attr("step", "0.05")
                            value("${settings.ragMinSimilarity}")
                            if (!settings.ragFilterEnabled) {
                                attr("disabled", "disabled")
                            }
                            style {
                                property("width", "100%")
                            }
                            onInput { event ->
                                val value = (event.target as HTMLInputElement).value.toFloatOrNull() ?: 0.3f
                                onSettingsChange(settings.copy(ragMinSimilarity = value))
                            }
                        }
                        Div({
                            style {
                                property("font-size", "12px")
                                property("color", "#059669")
                                property("margin-top", "4px")
                            }
                        }) {
                            Text("Порог cosine similarity (выше = строже фильтрация)")
                        }
                    }
                }
            }

            // Enabled Tools
            if (tools.isNotEmpty()) {
                Div({ style { property("margin-bottom", "24px") } }) {
                    Label(null, {
                        style {
                            property("display", "block")
                            property("font-size", "14px")
                            property("font-weight", "600")
                            property("color", "#374151")
                            property("margin-bottom", "8px")
                        }
                    }) {
                        Text("🔧 Включенные инструменты")
                    }
                    tools.forEach { tool ->
                        Div({ style { property("margin-bottom", "8px") } }) {
                            Label(null, {
                                style {
                                    property("display", "flex")
                                    property("align-items", "center")
                                    property("gap", "12px")
                                    property("cursor", "pointer")
                                    property("padding", "12px 16px")
                                    property("background", "#f9fafb")
                                    property("border-radius", "12px")
                                }
                            }) {
                                CheckboxInput {
                                    checked(settings.enabledTools.contains(tool.name))
                                    onInput { event ->
                                        val checked = (event.target as HTMLInputElement).checked
                                        val newTools = if (checked) {
                                            settings.enabledTools + tool.name
                                        } else {
                                            settings.enabledTools - tool.name
                                        }
                                        onSettingsChange(settings.copy(enabledTools = newTools))
                                    }
                                }
                                Text("${tool.name} - ${tool.description}")
                            }
                        }
                    }
                }
            }

            // Clear History Button
            Button({
                style {
                    property("width", "100%")
                    property("padding", "12px 16px")
                    property("background", "#fee2e2")
                    property("color", "#dc2626")
                    property("border", "2px solid #fecaca")
                    property("border-radius", "12px")
                    property("font-size", "14px")
                    property("font-weight", "500")
                    property("cursor", "pointer")
                }
                onClick { onClearHistory() }
            }) {
                Text("🗑️ Очистить историю")
            }
        }
    }
}

@Composable
fun HistoryPanel(
    visible: Boolean,
    sessions: List<ChatSession>,
    currentSessionId: String,
    unreadCounts: Map<String, Int>,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onLoadSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    Div({
        classes("history-panel")
        if (visible) classes("active")
    }) {
        Div({
            style {
                property("background", "#6366f1")
                property("color", "white")
                property("padding", "20px")
                property("display", "flex")
                property("justify-content", "space-between")
                property("align-items", "center")
            }
        }) {
            H2({
                style { property("margin", "0"); property("font-size", "20px") }
            }) {
                Text("💬 История чатов")
            }
            Button({
                style {
                    property("background", "transparent")
                    property("border", "0")
                    property("color", "white")
                    property("font-size", "28px")
                    property("cursor", "pointer")
                }
                onClick { onClose() }
            }) {
                Text("×")
            }
        }

        Button({
            style {
                property("margin", "16px")
                property("padding", "12px 16px")
                property("background", "linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)")
                property("color", "white")
                property("border", "0")
                property("border-radius", "12px")
                property("font-size", "14px")
                property("font-weight", "600")
                property("cursor", "pointer")
            }
            onClick { onNewChat() }
        }) {
            Text("➕ Новый чат")
        }

        Div({
            style {
                property("flex", "1")
                property("overflow-y", "auto")
                property("padding", "16px")
                property("padding-bottom", "150px")
                property("scroll-behavior", "smooth")
            }
        }) {
            if (sessions.isEmpty()) {
                Div({
                    style {
                        property("padding", "40px 20px")
                        property("text-align", "center")
                        property("color", "#9ca3af")
                    }
                }) {
                    Div({
                        style { property("font-size", "48px"); property("margin-bottom", "16px") }
                    }) {
                        Text("💬")
                    }
                    Div { Text("Нет сохраненных чатов") }
                }
            } else {
                sessions.forEach { session ->
                    val unreadCount = unreadCounts[session.id] ?: 0
                    Div({
                        style {
                            property("padding", "12px 16px")
                            property("background", if (session.id == currentSessionId) "#eef2ff" else "#f9fafb")
                            property("border-radius", "12px")
                            property("border", if (session.id == currentSessionId) "2px solid #6366f1" else "2px solid #e5e7eb")
                            property("margin-bottom", "8px")
                            property("display", "flex")
                            property("justify-content", "space-between")
                            property("align-items", "center")
                            property("gap", "8px")
                        }
                    }) {
                        Div({
                            style {
                                property("flex", "1")
                                property("cursor", "pointer")
                                property("overflow", "hidden")
                                property("text-overflow", "ellipsis")
                                property("white-space", "nowrap")
                            }
                            onClick { onLoadSession(session.id) }
                        }) {
                            Text(session.title)
                        }

                        // Unread badge
                        if (unreadCount > 0) {
                            Div({
                                style {
                                    property("background", "#ef4444")
                                    property("color", "white")
                                    property("font-size", "12px")
                                    property("font-weight", "bold")
                                    property("padding", "4px 8px")
                                    property("border-radius", "12px")
                                    property("min-width", "20px")
                                    property("text-align", "center")
                                }
                            }) {
                                Text(unreadCount.toString())
                            }
                        }

                        Button({
                            style {
                                property("background", "transparent")
                                property("border", "0")
                                property("color", "#ef4444")
                                property("font-size", "18px")
                                property("cursor", "pointer")
                                property("padding", "4px 8px")
                                property("border-radius", "6px")
                                property("transition", "all 0.2s ease")
                            }
                            onClick { event ->
                                event.stopPropagation()
                                onDeleteSession(session.id)
                            }
                        }) {
                            Text("🗑️")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderPanel(
    visible: Boolean,
    reminders: List<Reminder>,
    onClose: () -> Unit,
    onDismiss: (String) -> Unit
) {
    var currentTime by remember { mutableStateOf(Date.now()) }

    // Update current time every second for countdown
    LaunchedEffect(visible) {
        if (visible) {
            while (true) {
                delay(1000)
                currentTime = Date.now()
            }
        }
    }

    Div({
        classes("reminder-panel")
        if (visible) classes("active")
    }) {
        Div({
            style {
                property("background", "#6366f1")
                property("color", "white")
                property("padding", "20px")
                property("display", "flex")
                property("justify-content", "space-between")
                property("align-items", "center")
            }
        }) {
            H2({
                style { property("margin", "0"); property("font-size", "20px") }
            }) {
                Text("🔔 Напоминания")
            }
            Button({
                style {
                    property("background", "transparent")
                    property("border", "0")
                    property("color", "white")
                    property("font-size", "28px")
                    property("cursor", "pointer")
                }
                onClick { onClose() }
            }) {
                Text("×")
            }
        }

        Div({
            style {
                property("flex", "1")
                property("overflow-y", "auto")
                property("padding", "16px")
                property("padding-bottom", "150px")
                property("scroll-behavior", "smooth")
            }
        }) {
            if (reminders.isEmpty()) {
                Div({
                    style {
                        property("padding", "40px 20px")
                        property("text-align", "center")
                        property("color", "#9ca3af")
                    }
                }) {
                    Div({
                        style { property("font-size", "48px"); property("margin-bottom", "16px") }
                    }) {
                        Text("🔔")
                    }
                    Div { Text("Нет активных напоминаний") }
                }
            } else {
                reminders.forEach { reminder ->
                    val dueDate = Date(reminder.due_at)
                    val timeLeft = (dueDate.getTime() - currentTime).toLong()
                    val isOverdue = timeLeft <= 0

                    Div({
                        style {
                            property("padding", "16px")
                            property("background", if (isOverdue) "#fee2e2" else "#f9fafb")
                            property("border", if (isOverdue) "2px solid #dc2626" else "2px solid #e5e7eb")
                            property("border-radius", "12px")
                            property("margin-bottom", "12px")
                        }
                    }) {
                        // com.claude.agent.ui.Reminder text
                        Div({
                            style {
                                property("font-size", "14px")
                                property("font-weight", "600")
                                property("color", "#374151")
                                property("margin-bottom", "8px")
                            }
                        }) {
                            Text(reminder.text)
                        }

                        // Due date and countdown
                        Div({
                            style {
                                property("font-size", "12px")
                                property("color", if (isOverdue) "#dc2626" else "#6b7280")
                                property("margin-bottom", "8px")
                            }
                        }) {
                            Text("📅 ${Utils.formatDate(reminder.due_at)}")
                        }

                        Div({
                            style {
                                property("font-size", "13px")
                                property("font-weight", "600")
                                property("color", if (isOverdue) "#dc2626" else "#6366f1")
                                property("margin-bottom", "8px")
                            }
                        }) {
                            Text(if (isOverdue) "⏰ Время истекло!" else "⏳ ${Utils.formatTimeLeft(timeLeft)}")
                        }

                        // Status badges
                        Div({
                            style {
                                property("display", "flex")
                                property("gap", "8px")
                                property("margin-bottom", "12px")
                                property("flex-wrap", "wrap")
                            }
                        }) {
                            // Recurring badge
                            if (reminder.recurrenceType != null) {
                                Div({
                                    style {
                                        property("padding", "4px 12px")
                                        property("background", "#dbeafe")
                                        property("color", "#1e40af")
                                        property("border-radius", "12px")
                                        property("font-size", "11px")
                                        property("font-weight", "600")
                                    }
                                }) {
                                    val recurrenceText = when (reminder.recurrenceType) {
                                        "none" -> "Разовое"
                                        "minutely" -> "Каждую минуту"
                                        "hourly" -> "Каждый час"
                                        "daily" -> "Каждый день"
                                        "weekly" -> "Каждую неделю"
                                        "monthly" -> "Каждый месяц"
                                        else -> "Разовое"
                                    }
                                    val icon = if (reminder.recurrenceType == "none") "📌" else "🔄"
                                    Text("$icon $recurrenceText")
                                }
                            }

                            // Status badge
                            Div({
                                style {
                                    property("padding", "4px 12px")
                                    property("background", if (isOverdue) "#fef3c7" else "#d1fae5")
                                    property("color", if (isOverdue) "#92400e" else "#065f46")
                                    property("border-radius", "12px")
                                    property("font-size", "11px")
                                    property("font-weight", "600")
                                }
                            }) {
                                Text(if (isOverdue) "⚠️ Выполнено" else "✓ Запланировано")
                            }
                        }

                        // Dismiss button
                        Button({
                            style {
                                property("width", "100%")
                                property("padding", "8px 16px")
                                property("background", if (isOverdue) "#dc2626" else "#6366f1")
                                property("color", "white")
                                property("border", "0")
                                property("border-radius", "8px")
                                property("font-size", "13px")
                                property("font-weight", "600")
                                property("cursor", "pointer")
                            }
                            onClick { onDismiss(reminder.id) }
                        }) {
                            Text(if (isOverdue) "✓ Отметить выполненным" else "❌ Отменить напоминание")
                        }
                    }
                }
            }
        }
    }
}
