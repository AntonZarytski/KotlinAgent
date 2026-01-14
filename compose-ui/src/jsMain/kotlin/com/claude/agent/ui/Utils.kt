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

        // Улучшенное регулярное выражение для блоков кода:
        // - Поддерживает блоки с языком и без
        // - Не требует обязательный \n перед закрывающими ```
        // - Обрабатывает случаи когда ``` идут сразу после текста
        html = html.replace(Regex("```(\\w*)\n([\\s\\S]*?)```", RegexOption.MULTILINE)) { match ->
            val lang = match.groupValues[1].ifEmpty { "code" }
            val code = escapeHtml(match.groupValues[2].trimEnd())
            val placeholder = "\n___CODE_BLOCK_${codeBlockCounter++}___\n"

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

        // Шаг 2: Обрабатываем таблицы
        val tableBlocks = mutableMapOf<String, String>()
        var tableBlockCounter = 0

        // Находим таблицы (строки, начинающиеся с |)
        val tableRegex = Regex("^\\|.+\\|\\s*$", RegexOption.MULTILINE)
        val allLines = html.split("\n")
        val processedTableLines = mutableListOf<String>()
        var i = 0

        while (i < allLines.size) {
            val line = allLines[i]

            // Проверяем, начинается ли таблица
            if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                val tableLines = mutableListOf<String>()
                var j = i

                // Собираем все строки таблицы
                while (j < allLines.size && allLines[j].trim().startsWith("|") && allLines[j].trim().endsWith("|")) {
                    tableLines.add(allLines[j])
                    j++
                }

                // Если нашли хотя бы 2 строки (заголовок + разделитель или заголовок + данные)
                if (tableLines.size >= 2) {
                    val placeholder = "\n___TABLE_BLOCK_${tableBlockCounter++}___\n"
                    tableBlocks[placeholder] = buildTable(tableLines)
                    processedTableLines.add(placeholder)
                    i = j
                    continue
                }
            }

            processedTableLines.add(line)
            i++
        }

        html = processedTableLines.joinToString("\n")

        // Шаг 3: Разбиваем на строки для обработки
        val lines = html.split("\n")
        val processedLines = mutableListOf<String>()

        for (line in lines) {
            // Пропускаем плейсхолдеры блоков кода и таблиц
            if (line.trim().startsWith("___CODE_BLOCK_") || line.trim().startsWith("___TABLE_BLOCK_")) {
                processedLines.add(line)
                continue
            }

            // Экранируем HTML в строке
            var processedLine = escapeHtml(line)

            // Inline code (должно быть до bold/italic чтобы не конфликтовать)
            processedLine = processedLine.replace(Regex("`([^`]+)`")) { match ->
                "<code>${match.groupValues[1]}</code>"
            }

            // Bold
            processedLine = processedLine.replace(Regex("\\*\\*([^*]+)\\*\\*")) { match ->
                "<strong>${match.groupValues[1]}</strong>"
            }

            // Italic
            processedLine = processedLine.replace(Regex("\\*([^*]+)\\*")) { match ->
                "<em>${match.groupValues[1]}</em>"
            }

            // Headers
            processedLine = when {
                processedLine.startsWith("### ") -> "<h3>${processedLine.substring(4)}</h3>"
                processedLine.startsWith("## ") -> "<h2>${processedLine.substring(3)}</h2>"
                processedLine.startsWith("# ") -> "<h1>${processedLine.substring(2)}</h1>"
                else -> processedLine
            }

            // Links
            processedLine = processedLine.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")) { match ->
                "<a href=\"${match.groupValues[2]}\" target=\"_blank\">${match.groupValues[1]}</a>"
            }

            // Lists
            if (processedLine.trimStart().startsWith("- ")) {
                val indent = processedLine.takeWhile { it == ' ' }.length
                val content = processedLine.trimStart().substring(2)
                processedLine = "${"  ".repeat(indent / 2)}<li>$content</li>"
            }

            processedLines.add(processedLine)
        }

        // Объединяем строки обратно
        html = processedLines.joinToString("\n")

        // Оборачиваем списки в <ul>
        html = html.replace(Regex("(<li>.*?</li>\n?)+", RegexOption.MULTILINE)) { match ->
            "<ul>${match.value}</ul>"
        }

        // Paragraphs - оборачиваем непустые строки, которые не являются тегами или плейсхолдерами
        html = html.split("\n").joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> ""
                trimmed.startsWith("<") -> line  // Уже HTML тег
                trimmed.startsWith("___CODE_BLOCK_") -> line  // Плейсхолдер кода
                trimmed.startsWith("___TABLE_BLOCK_") -> line  // Плейсхолдер таблицы
                trimmed == "---" -> "<hr/>"  // Горизонтальная линия
                else -> "<p>$line</p>"
            }
        }

        // Шаг 4: Возвращаем блоки кода и таблиц обратно в конце
        codeBlocks.forEach { (placeholder, codeHtml) ->
            html = html.replace(placeholder.trim(), codeHtml)
        }

        tableBlocks.forEach { (placeholder, tableHtml) ->
            html = html.replace(placeholder.trim(), tableHtml)
        }

        return html
    }

    /**
     * Построение HTML таблицы из markdown строк
     */
    private fun buildTable(lines: List<String>): String {
        if (lines.isEmpty()) return ""

        val rows = lines.map { line ->
            line.trim()
                .removePrefix("|")
                .removeSuffix("|")
                .split("|")
                .map { it.trim() }
        }

        if (rows.isEmpty()) return ""

        // Проверяем, есть ли строка-разделитель (содержит только дефисы и пробелы)
        val separatorIndex = rows.indexOfFirst { row ->
            row.all { cell -> cell.matches(Regex("^[-:\\s]+$")) }
        }

        val headerRows = if (separatorIndex > 0) rows.take(separatorIndex) else listOf(rows.first())
        val dataRows = if (separatorIndex >= 0) rows.drop(separatorIndex + 1) else rows.drop(1)

        return buildString {
            append("<table class=\"markdown-table\">")

            // Заголовок
            if (headerRows.isNotEmpty()) {
                append("<thead>")
                headerRows.forEach { row ->
                    append("<tr>")
                    row.forEach { cell ->
                        append("<th>")
                        append(parseInlineMarkdown(cell))
                        append("</th>")
                    }
                    append("</tr>")
                }
                append("</thead>")
            }

            // Данные
            if (dataRows.isNotEmpty()) {
                append("<tbody>")
                dataRows.forEach { row ->
                    append("<tr>")
                    row.forEach { cell ->
                        append("<td>")
                        append(parseInlineMarkdown(cell))
                        append("</td>")
                    }
                    append("</tr>")
                }
                append("</tbody>")
            }

            append("</table>")
        }
    }

    /**
     * Парсинг inline markdown (bold, italic, code) для ячеек таблицы
     */
    private fun parseInlineMarkdown(text: String): String {
        var result = escapeHtml(text)

        // Inline code
        result = result.replace(Regex("`([^`]+)`")) { match ->
            "<code>${match.groupValues[1]}</code>"
        }

        // Bold
        result = result.replace(Regex("\\*\\*([^*]+)\\*\\*")) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }

        // Italic
        result = result.replace(Regex("\\*([^*]+)\\*")) { match ->
            "<em>${match.groupValues[1]}</em>"
        }

        return result
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
                val notificationTag = tag ?: "reminder"
                // Используем eval для передачи параметров
                js("""
                    (function(title, body, tag) {
                        new Notification(title, {
                            body: body,
                            icon: '/favicon.ico',
                            badge: '/favicon.ico',
                            tag: tag,
                            requireInteraction: true,
                            vibrate: [200, 100, 200]
                        });
                    })
                """)(title, body, notificationTag)
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

    /**
     * Сохранить настройки в localStorage
     */
    fun saveSettings(settings: Settings) {
        try {
            // Сохраняем только enabledTools, остальные настройки можно добавить позже
            val enabledToolsJson = settings.enabledTools.joinToString(",")
            window.localStorage.setItem("chat_settings_enabled_tools", enabledToolsJson)
            console.log("Settings saved: enabledTools = $enabledToolsJson")
        } catch (e: Exception) {
            console.error("Failed to save settings: ${e.message}")
        }
    }

    /**
     * Загрузить настройки из localStorage
     * Возвращает Settings с восстановленными enabledTools или с инструментами по умолчанию
     */
    fun loadSettings(): Settings {
        return try {
            val enabledToolsJson = window.localStorage.getItem("chat_settings_enabled_tools")

            if (enabledToolsJson != null && enabledToolsJson.isNotBlank()) {
                // Восстанавливаем сохраненные инструменты
                val enabledTools = enabledToolsJson.split(",").filter { it.isNotBlank() }.toSet()
                console.log("Settings loaded: enabledTools = $enabledTools")
                Settings(enabledTools = enabledTools)
            } else {
                // Первый запуск - используем инструменты по умолчанию
                val defaultTools = setOf("plan_actions", "project_help", "support_crm")
                console.log("First run, using default tools: $defaultTools")
                Settings(enabledTools = defaultTools)
            }
        } catch (e: Exception) {
            console.error("Failed to load settings: ${e.message}")
            // В случае ошибки возвращаем настройки по умолчанию
            Settings(enabledTools = setOf("plan_actions", "project_help", "support_crm"))
        }
    }

    /**
     * Очистить сохраненные настройки
     */
    fun clearSettings() {
        try {
            window.localStorage.removeItem("chat_settings_enabled_tools")
            console.log("Settings cleared")
        } catch (e: Exception) {
            console.error("Failed to clear settings: ${e.message}")
        }
    }
}
