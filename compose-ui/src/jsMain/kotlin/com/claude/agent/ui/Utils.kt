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

    /**
     * Проверяет доступность MediaRecorder API
     */
    fun isMediaRecorderSupported(): Boolean {
        return try {
            js("'mediaDevices' in navigator && 'getUserMedia' in navigator.mediaDevices") == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Запрашивает доступ к микрофону и возвращает MediaStream
     */
    suspend fun requestMicrophoneAccess(): dynamic? {
        return try {
            if (!isMediaRecorderSupported()) {
                console.warn("MediaRecorder API not supported")
                return null
            }

            if (!window.asDynamic().isSecureContext && window.location.protocol != "file:") {
                console.warn("MediaRecorder requires HTTPS or localhost")
                return null
            }

            val stream = Promise<dynamic> { resolve, reject ->
                js("""
                    navigator.mediaDevices.getUserMedia({
                        audio: {
                            channelCount: 1,
                            sampleRate: 16000,
                            echoCancellation: true,
                            noiseSuppression: true
                        }
                    }).then(resolve).catch(reject)
                """)
            }.await()

            console.log("Microphone access granted")
            stream
        } catch (e: Exception) {
            console.error("Failed to get microphone access: ${e.message}")
            null
        }
    }

    /**
     * Создает MediaRecorder и начинает запись
     * Возвращает объект с методами stop() и getBlob()
     */
    fun createMediaRecorder(stream: dynamic, onDataAvailable: (dynamic) -> Unit): dynamic? {
        return try {
            val chunks = js("[]")
            val recorder = js("new MediaRecorder(stream, { mimeType: 'audio/webm' })")

            recorder.ondataavailable = { event: dynamic ->
                if (event.data.size > 0) {
                    chunks.push(event.data)
                    onDataAvailable(event.data)
                }
            }

            js("""({
                recorder: recorder,
                chunks: chunks,
                start: function() {
                    // Очищаем chunks перед началом новой записи
                    this.chunks.length = 0;
                    this.recorder.start(1000);
                },
                stop: function() {
                    var self = this;
                    return new Promise(function(resolve) {
                        self.recorder.onstop = function() {
                            var blob = new Blob(self.chunks, { type: 'audio/webm' });
                            console.log('🎤 Blob created from ' + self.chunks.length + ' chunks, total size: ' + blob.size);
                            resolve(blob);
                        };
                        self.recorder.stop();
                    });
                },
                getState: function() {
                    return this.recorder.state;
                }
            })""")
        } catch (e: Exception) {
            console.error("Failed to create MediaRecorder: ${e.message}")
            null
        }
    }

    /**
     * Останавливает все треки в MediaStream
     */
    fun stopMediaStream(stream: dynamic) {
        try {
            js("stream.getTracks().forEach(function(track) { track.stop(); })")
            console.log("Media stream stopped")
        } catch (e: Exception) {
            console.error("Failed to stop media stream: ${e.message}")
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
            val code = match.groupValues[2].trimEnd()
            val placeholder = "\n___CODE_BLOCK_${codeBlockCounter++}___\n"

            // Проверяем, является ли это diff-блоком
            val isDiff = lang == "diff" || code.lines().any { it.startsWith("+ ") || it.startsWith("- ") }

            if (isDiff) {
                // Обрабатываем diff с подсветкой
                val diffHtml = code.lines().joinToString("\n") { line ->
                    when {
                        line.startsWith("+ ") -> "<span style=\"color: #22c55e; background: rgba(34, 197, 94, 0.1);\">$line</span>"
                        line.startsWith("- ") -> "<span style=\"color: #ef4444; background: rgba(239, 68, 68, 0.1);\">$line</span>"
                        line.startsWith("Changes in ") || line.startsWith("Total changes:") ->
                            "<span style=\"color: #3b82f6; font-weight: 600;\">$line</span>"
                        else -> escapeHtml(line)
                    }
                }

                codeBlocks[placeholder] = "<div class=\"code-block-wrapper diff-block\">" +
                    "<div class=\"code-block-header\" style=\"background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);\">" +
                        "<span class=\"code-language\">📝 Изменения файла</span>" +
                        "<button class=\"copy-btn\" onclick=\"copyCode(this)\">" +
                            "<span>Копировать</span>" +
                        "</button>" +
                    "</div>" +
                    "<pre><code class=\"language-diff\">$diffHtml</code></pre>" +
                "</div>"
            } else {
                // Обычный блок кода
                val escapedCode = escapeHtml(code)
                codeBlocks[placeholder] = "<div class=\"code-block-wrapper\">" +
                    "<div class=\"code-block-header\">" +
                        "<span class=\"code-language\">$lang</span>" +
                        "<button class=\"copy-btn\" onclick=\"copyCode(this)\">" +
                            "<span>Копировать</span>" +
                        "</button>" +
                    "</div>" +
                    "<pre><code class=\"language-$lang\">$escapedCode</code></pre>" +
                "</div>"
            }

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
