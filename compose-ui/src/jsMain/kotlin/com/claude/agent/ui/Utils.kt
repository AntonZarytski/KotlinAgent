package com.claude.agent.ui

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.Date
import kotlin.js.Promise
import kotlin.random.Random

object Utils {
    fun generateSessionId(): String {
        val timestamp = Date.now().toLong()
        val random = (Random.nextDouble() * 1000000).toInt()
        return "session_${timestamp}_${random}"
    }

    fun formatTimestamp(timestamp: String): String {
        return try {
            val date = Date(timestamp)
            val hours = date.getHours().toString().padStart(2, '0')
            val minutes = date.getMinutes().toString().padStart(2, '0')
            "$hours:$minutes"
        } catch (e: Exception) {
            ""
        }
    }

    fun formatDate(timestamp: String): String {
        return try {
            val date = Date(timestamp)
            val day = date.getDate().toString().padStart(2, '0')
            val month = (date.getMonth() + 1).toString().padStart(2, '0')
            val year = date.getFullYear()
            val hours = date.getHours().toString().padStart(2, '0')
            val minutes = date.getMinutes().toString().padStart(2, '0')
            "$day.$month.$year $hours:$minutes"
        } catch (e: Exception) {
            ""
        }
    }

    fun formatTimeLeft(milliseconds: Long): String {
        val seconds = (milliseconds / 1000.0).toLong()
        val minutes = (seconds / 60.0).toLong()
        val hours = (minutes / 60.0).toLong()
        val days = (hours / 24.0).toLong()

        return when {
            days > 0 -> "$days дн. ${hours % 24} ч."
            hours > 0 -> "$hours ч. ${minutes % 60} мин."
            minutes > 0 -> "$minutes мин. ${seconds % 60} сек."
            else -> "$seconds сек."
        }
    }

    suspend fun getGeolocation(): UserLocation? {
        return try {
            if (js("navigator.geolocation") == null) {
                console.warn("Geolocation API not supported")
                return null
            }

            if (!window.asDynamic().isSecureContext && window.location.protocol != "file:") {
                console.warn("Geolocation requires HTTPS or localhost")
                return null
            }

            val position = Promise<dynamic> { resolve, reject ->
                js("navigator.geolocation.getCurrentPosition")(resolve, reject, js("""({
                    enableHighAccuracy: false,
                    timeout: 10000,
                    maximumAge: 300000
                })"""))
            }.await()

            UserLocation(
                latitude = position.coords.latitude as Double,
                longitude = position.coords.longitude as Double,
                source = "browser_geolocation"
            )
        } catch (e: Exception) {
            console.warn("Failed to get geolocation: ${e.message}")
            null
        }
    }

    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;")
    }

    fun parseMarkdown(text: String): String {
        // Simple markdown parser - in production you'd use a library like markdown-it

        // Шаг 1: Извлекаем блоки кода и сохраняем в мапу с placeholders
        val codeBlocks = mutableMapOf<String, String>()
        var codeBlockCounter = 0
        var html = text

        html = html.replace(Regex("```(\\w*)\\n([\\s\\S]*?)\\n```")) { match ->
            val lang = match.groupValues[1].ifEmpty { "code" }
            val code = escapeHtml(match.groupValues[2])
            val placeholder = "___CODE_BLOCK_${codeBlockCounter++}___"

            codeBlocks[placeholder] = "<div class=\"code-block-wrapper\">" +
                "<div class=\"code-block-header\">" +
                    "<span class=\"code-language\">$lang</span>" +
                    "<button class=\"copy-btn\" onclick=\"copyCode(this)\">" +
                        "<span>Копировать</span>" +
                    "</button>" +
                "</div>" +
                "<pre><code class=\"language-$lang\">$code</code></pre>" +
            "</div>"

            placeholder
        }

        // Шаг 2: Экранируем весь остальной текст (плейсхолдеры останутся как есть)
        html = escapeHtml(html)

        // Inline code
        html = html.replace(Regex("`([^`]+)`")) { match ->
            "<code>${match.groupValues[1]}</code>"
        }

        // Bold
        html = html.replace(Regex("\\*\\*([^*]+)\\*\\*")) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }

        // Italic
        html = html.replace(Regex("\\*([^*]+)\\*")) { match ->
            "<em>${match.groupValues[1]}</em>"
        }

        // Headers
        html = html.replace(Regex("^### (.+)$", RegexOption.MULTILINE)) { match ->
            "<h3>${match.groupValues[1]}</h3>"
        }
        html = html.replace(Regex("^## (.+)$", RegexOption.MULTILINE)) { match ->
            "<h2>${match.groupValues[1]}</h2>"
        }
        html = html.replace(Regex("^# (.+)$", RegexOption.MULTILINE)) { match ->
            "<h1>${match.groupValues[1]}</h1>"
        }

        // Links
        html = html.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")) { match ->
            "<a href=\"${match.groupValues[2]}\" target=\"_blank\">${match.groupValues[1]}</a>"
        }

        // Lists
        html = html.replace(Regex("^- (.+)$", RegexOption.MULTILINE)) { match ->
            "<li>${match.groupValues[1]}</li>"
        }
        html = html.replace(Regex("(<li>.*</li>)", RegexOption.MULTILINE)) { match ->
            "<ul>${match.value}</ul>"
        }

        // Paragraphs (не трогаем placeholders для блоков кода)
        html = html.replace(Regex("^(?!<[hul]|<div|<pre|___CODE_BLOCK_)(.+)$", RegexOption.MULTILINE)) { match ->
            "<p>${match.groupValues[1]}</p>"
        }

        // Шаг 3: Возвращаем блоки кода обратно в конце
        codeBlocks.forEach { (placeholder, codeHtml) ->
            html = html.replace(placeholder, codeHtml)
        }

        return html
    }

    fun requestNotificationPermission() {
        if (js("'Notification' in window") == true) {
            val permission = js("Notification.permission")
            if (permission == "default") {
                js("Notification.requestPermission()")
            }
        }
    }

    fun showBrowserNotification(title: String, body: String, tag: String? = null) {
        if (js("'Notification' in window") == true && js("Notification.permission") == "granted") {
            try {
                js("""
                    new Notification(title, {
                        body: body,
                        icon: '/favicon.ico',
                        badge: '/favicon.ico',
                        tag: tag || 'reminder',
                        requireInteraction: true,
                        vibrate: [200, 100, 200]
                    })
                """)
                console.log("Browser notification sent: $title")
            } catch (e: Exception) {
                console.error("Error sending notification: ${e.message}")
            }
        }
    }

    fun copyToClipboard(text: String) {
        try {
            js("navigator.clipboard.writeText(text)")
            console.log("Copied to clipboard")
        } catch (e: Exception) {
            console.error("Failed to copy: ${e.message}")
        }
    }

    fun needsGeolocation(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("погод") ||
                lower.contains("weather") ||
                lower.contains("температур") ||
                lower.contains("temperature") ||
                lower.contains("солнечн") ||
                lower.contains("solar") ||
                lower.contains("аврор") ||
                lower.contains("aurora") ||
                lower.contains("сиян") ||
                lower.contains("северн")
    }

    fun scrollToBottom() {
        try {
            // Scroll the messages container to the bottom
            val messagesContainer = js("document.getElementById('messages')")
            if (messagesContainer != null) {
                messagesContainer.scrollTop = messagesContainer.scrollHeight
            }
        } catch (e: Exception) {
            console.warn("Failed to scroll to bottom: ${e.message}")
        }
    }
}
