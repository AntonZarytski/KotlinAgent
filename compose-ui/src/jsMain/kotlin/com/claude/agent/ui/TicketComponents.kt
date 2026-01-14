package com.claude.agent.ui

import androidx.compose.runtime.*
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement

/**
 * Панель со списком тикетов поддержки
 */
@Composable
fun TicketPanel(
    visible: Boolean,
    tickets: List<SupportTicket>,
    onClose: () -> Unit,
    onTicketClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Div({
        classes("ticket-panel")
        if (visible) classes("active")
    }) {
        // Header
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
                Text("🎫 Тикеты поддержки")
            }
            Div({
                style {
                    property("display", "flex")
                    property("gap", "8px")
                }
            }) {
                Button({
                    style {
                        property("background", "transparent")
                        property("border", "1px solid white")
                        property("color", "white")
                        property("padding", "8px 16px")
                        property("border-radius", "8px")
                        property("cursor", "pointer")
                        property("font-size", "14px")
                    }
                    onClick { onRefresh() }
                }) {
                    Text("🔄 Обновить")
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
        }

        // Tickets list
        Div({
            style {
                property("flex", "1")
                property("overflow-y", "auto")
                property("padding", "16px")
                property("padding-bottom", "200px") // Увеличенный отступ для панели ввода
            }
        }) {
            if (tickets.isEmpty()) {
                Div({
                    style {
                        property("text-align", "center")
                        property("padding", "40px 20px")
                        property("color", "#9ca3af")
                    }
                }) {
                    Text("📭 Нет тикетов")
                }
            } else {
                tickets.forEach { ticket ->
                    TicketItem(ticket, onTicketClick)
                }
            }
        }
    }
}

@Composable
fun TicketItem(ticket: SupportTicket, onClick: (String) -> Unit) {
    val statusColor = when (ticket.status) {
        "OPEN" -> "#3b82f6"
        "IN_PROGRESS" -> "#f59e0b"
        "WAITING_FOR_USER" -> "#8b5cf6"
        "RESOLVED" -> "#10b981"
        "CLOSED" -> "#6b7280"
        else -> "#9ca3af"
    }

    val priorityEmoji = when (ticket.priority) {
        "CRITICAL" -> "🔴"
        "HIGH" -> "🟠"
        "MEDIUM" -> "🟡"
        "LOW" -> "🟢"
        else -> "⚪"
    }

    Div({
        style {
            property("background", "#f9fafb")
            property("border", "2px solid #e5e7eb")
            property("border-radius", "12px")
            property("padding", "16px")
            property("margin-bottom", "12px")
            property("cursor", "pointer")
            property("transition", "all 0.2s")
        }
        onClick { onClick(ticket.id) }
    }) {
        // Header with ID and status
        Div({
            style {
                property("display", "flex")
                property("justify-content", "space-between")
                property("align-items", "center")
                property("margin-bottom", "8px")
            }
        }) {
            Span({
                style {
                    property("font-weight", "600")
                    property("color", "#374151")
                }
            }) {
                Text("$priorityEmoji ${ticket.id}")
            }
            Span({
                style {
                    property("background", statusColor)
                    property("color", "white")
                    property("padding", "4px 12px")
                    property("border-radius", "12px")
                    property("font-size", "12px")
                    property("font-weight", "600")
                }
            }) {
                Text(ticket.status)
            }
        }

        // Title
        Div({
            style {
                property("font-weight", "600")
                property("color", "#111827")
                property("margin-bottom", "8px")
                property("font-size", "16px")
            }
        }) {
            Text(ticket.title)
        }

        // Description (truncated)
        Div({
            style {
                property("color", "#6b7280")
                property("font-size", "14px")
                property("margin-bottom", "12px")
                property("overflow", "hidden")
                property("text-overflow", "ellipsis")
                property("display", "-webkit-box")
                property("-webkit-line-clamp", "2")
                property("-webkit-box-orient", "vertical")
            }
        }) {
            Text(ticket.description)
        }

        // Footer with metadata
        Div({
            style {
                property("display", "flex")
                property("justify-content", "space-between")
                property("align-items", "center")
                property("font-size", "12px")
                property("color", "#9ca3af")
            }
        }) {
            Span {
                Text("Создан: ${formatDate(ticket.createdAt)}")
            }
            if (ticket.category != null) {
                Span({
                    style {
                        property("background", "#e5e7eb")
                        property("padding", "2px 8px")
                        property("border-radius", "8px")
                        property("color", "#374151")
                    }
                }) {
                    Text(ticket.category)
                }
            }
        }

        // Tags
        if (ticket.tags.isNotEmpty()) {
            Div({
                style {
                    property("display", "flex")
                    property("gap", "4px")
                    property("margin-top", "8px")
                    property("flex-wrap", "wrap")
                }
            }) {
                ticket.tags.forEach { tag ->
                    Span({
                        style {
                            property("background", "#dbeafe")
                            property("color", "#1e40af")
                            property("padding", "2px 8px")
                            property("border-radius", "8px")
                            property("font-size", "11px")
                        }
                    }) {
                        Text(tag)
                    }
                }
            }
        }
    }
}

/**
 * Детальный вид тикета
 */
@Composable
fun TicketDetailView(
    ticket: SupportTicket?,
    onClose: () -> Unit,
    onUpdateStatus: (String) -> Unit,
    onGoToChat: ((String) -> Unit)? = null
) {
    if (ticket == null) return

    Div({
        style {
            property("position", "fixed")
            property("top", "0")
            property("left", "0")
            property("right", "0")
            property("bottom", "0")
            property("background", "rgba(0, 0, 0, 0.5)")
            property("display", "flex")
            property("align-items", "center")
            property("justify-content", "center")
            property("z-index", "1000")
        }
        onClick { onClose() }
    }) {
        Div({
            style {
                property("background", "white")
                property("border-radius", "16px")
                property("max-width", "600px")
                property("width", "90%")
                property("max-height", "80vh")
                property("overflow-y", "auto")
                property("box-shadow", "0 20px 25px -5px rgba(0, 0, 0, 0.1)")
            }
            onClick { event -> event.stopPropagation() }
        }) {
            // Header
            Div({
                style {
                    property("background", "#6366f1")
                    property("color", "white")
                    property("padding", "24px")
                    property("border-radius", "16px 16px 0 0")
                }
            }) {
                Div({
                    style {
                        property("display", "flex")
                        property("justify-content", "space-between")
                        property("align-items", "start")
                    }
                }) {
                    Div {
                        H2({
                            style {
                                property("margin", "0 0 8px 0")
                                property("font-size", "24px")
                            }
                        }) {
                            Text(ticket.title)
                        }
                        Span({
                            style {
                                property("font-size", "14px")
                                property("opacity", "0.9")
                            }
                        }) {
                            Text(ticket.id)
                        }
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
            }

            // Content
            Div({
                style {
                    property("padding", "24px")
                    property("padding-bottom", "80px") // Отступ снизу для кнопок
                }
            }) {
                // Status and Priority
                Div({
                    style {
                        property("display", "flex")
                        property("gap", "12px")
                        property("margin-bottom", "24px")
                    }
                }) {
                    StatusBadge(ticket.status)
                    PriorityBadge(ticket.priority)
                }

                // Description
                Div({
                    style {
                        property("margin-bottom", "24px")
                    }
                }) {
                    H3({
                        style {
                            property("margin", "0 0 8px 0")
                            property("font-size", "16px")
                            property("color", "#374151")
                        }
                    }) {
                        Text("Описание")
                    }
                    P({
                        style {
                            property("margin", "0")
                            property("color", "#6b7280")
                            property("line-height", "1.6")
                        }
                    }) {
                        Text(ticket.description)
                    }
                }

                // Metadata
                Div({
                    style {
                        property("background", "#f9fafb")
                        property("padding", "16px")
                        property("border-radius", "12px")
                        property("margin-bottom", "24px")
                    }
                }) {
                    MetadataRow("Создан", formatDate(ticket.createdAt))
                    MetadataRow("Обновлен", formatDate(ticket.updatedAt))
                    if (ticket.category != null) {
                        MetadataRow("Категория", ticket.category)
                    }
                    if (ticket.assignedTo != null) {
                        MetadataRow("Назначен", ticket.assignedTo)
                    }
                }

                // Tags
                if (ticket.tags.isNotEmpty()) {
                    Div({
                        style {
                            property("margin-bottom", "24px")
                        }
                    }) {
                        H3({
                            style {
                                property("margin", "0 0 8px 0")
                                property("font-size", "16px")
                                property("color", "#374151")
                            }
                        }) {
                            Text("Теги")
                        }
                        Div({
                            style {
                                property("display", "flex")
                                property("gap", "8px")
                                property("flex-wrap", "wrap")
                            }
                        }) {
                            ticket.tags.forEach { tag ->
                                Span({
                                    style {
                                        property("background", "#dbeafe")
                                        property("color", "#1e40af")
                                        property("padding", "4px 12px")
                                        property("border-radius", "12px")
                                        property("font-size", "14px")
                                    }
                                }) {
                                    Text(tag)
                                }
                            }
                        }
                    }
                }

                // Update History
                if (ticket.updateHistory.isNotEmpty()) {
                    Div({
                        style {
                            property("margin-bottom", "24px")
                        }
                    }) {
                        H3({
                            style {
                                property("margin", "0 0 12px 0")
                                property("font-size", "16px")
                                property("color", "#374151")
                            }
                        }) {
                            Text("📝 История обновлений")
                        }
                        Div({
                            style {
                                property("display", "flex")
                                property("flex-direction", "column")
                                property("gap", "12px")
                            }
                        }) {
                            ticket.updateHistory.forEach { update ->
                                Div({
                                    style {
                                        property("background", "#f9fafb")
                                        property("border-left", "3px solid #6366f1")
                                        property("padding", "12px")
                                        property("border-radius", "4px")
                                    }
                                }) {
                                    Div({
                                        style {
                                            property("font-size", "12px")
                                            property("color", "#6b7280")
                                            property("margin-bottom", "4px")
                                        }
                                    }) {
                                        Text(Utils.formatDate(update.timestamp))
                                    }
                                    Div({
                                        style {
                                            property("font-size", "14px")
                                            property("color", "#374151")
                                            property("line-height", "1.5")
                                        }
                                    }) {
                                        Text(update.description)
                                    }
                                }
                            }
                        }
                    }
                }

                // Action buttons
                Div({
                    style {
                        property("display", "flex")
                        property("gap", "8px")
                        property("flex-wrap", "wrap")
                        property("margin-bottom", "16px")
                    }
                }) {
                    // Go to chat button
                    if (onGoToChat != null) {
                        Button({
                            classes("button-primary")
                            style {
                                property("background", "#10b981")
                                property("border", "none")
                            }
                            onClick {
                                onGoToChat(ticket.sessionId)
                                onClose()
                            }
                        }) {
                            Text("💬 Перейти к чату")
                        }
                    }
                }

                // Status update buttons
                Div({
                    style {
                        property("display", "flex")
                        property("gap", "8px")
                        property("flex-wrap", "wrap")
                    }
                }) {
                    if (ticket.status != "RESOLVED") {
                        Button({
                            classes("button-primary")
                            onClick { onUpdateStatus("RESOLVED") }
                        }) {
                            Text("✅ Решено")
                        }
                    }
                    if (ticket.status != "CLOSED") {
                        Button({
                            classes("button-secondary")
                            onClick { onUpdateStatus("CLOSED") }
                        }) {
                            Text("🔒 Закрыть")
                        }
                    }
                    if (ticket.status == "OPEN") {
                        Button({
                            classes("button-secondary")
                            onClick { onUpdateStatus("IN_PROGRESS") }
                        }) {
                            Text("🔄 В работу")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when (status) {
        "OPEN" -> "#3b82f6"
        "IN_PROGRESS" -> "#f59e0b"
        "WAITING_FOR_USER" -> "#8b5cf6"
        "RESOLVED" -> "#10b981"
        "CLOSED" -> "#6b7280"
        else -> "#9ca3af"
    }

    Span({
        style {
            property("background", color)
            property("color", "white")
            property("padding", "6px 16px")
            property("border-radius", "12px")
            property("font-size", "14px")
            property("font-weight", "600")
        }
    }) {
        Text(status)
    }
}

@Composable
fun PriorityBadge(priority: String) {
    val (emoji, color) = when (priority) {
        "CRITICAL" -> "🔴" to "#ef4444"
        "HIGH" -> "🟠" to "#f59e0b"
        "MEDIUM" -> "🟡" to "#eab308"
        "LOW" -> "🟢" to "#10b981"
        else -> "⚪" to "#9ca3af"
    }

    Span({
        style {
            property("background", color)
            property("color", "white")
            property("padding", "6px 16px")
            property("border-radius", "12px")
            property("font-size", "14px")
            property("font-weight", "600")
        }
    }) {
        Text("$emoji $priority")
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Div({
        style {
            property("display", "flex")
            property("justify-content", "space-between")
            property("padding", "8px 0")
            property("border-bottom", "1px solid #e5e7eb")
        }
    }) {
        Span({
            style {
                property("color", "#6b7280")
                property("font-size", "14px")
            }
        }) {
            Text(label)
        }
        Span({
            style {
                property("color", "#111827")
                property("font-size", "14px")
                property("font-weight", "600")
            }
        }) {
            Text(value)
        }
    }
}

private fun formatDate(dateString: String): String {
    // Simple date formatting - можно улучшить
    return try {
        val date = js("new Date(dateString)")
        date.toLocaleString("ru-RU", js("{dateStyle: 'short', timeStyle: 'short'}"))
    } catch (e: Exception) {
        dateString
    }
}

