package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.common.database.normalizeToRange
import com.claude.agent.llm.mcp.LOG_ANALYZER
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import com.claude.agent.service.OllamaEmbeddingClient
import com.claude.agent.service.RagService
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * MCP tool for analyzing application logs (app.log).
 * Provides on-the-fly analysis of log entries: error frequency, patterns, metrics.
 *
 * С интеграцией RAG: автоматически ищет релевантную документацию для найденных ошибок.
 */
class LogAnalyzerMcp(
    private val ragService: RagService? = null,
    private val ollamaEmbeddingClient: OllamaEmbeddingClient? = null
) : Mcp.Local {
    private val logger = LoggerFactory.getLogger(LogAnalyzerMcp::class.java)

    // Log line format: HH:mm:ss.SSS Logger - Message
    private val logLineRegex = Regex("""^(\d{2}:\d{2}:\d{2}\.\d{3})\s+([^\s]+)\s+-\s+(.*)$""")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = LOG_ANALYZER,
        second = LocalToolDefinition(
            name = LOG_ANALYZER,
            ui_description = "Анализирует логи приложения для поиска ошибок, паттернов и метрик",
            description = """
                Анализирует логи приложения (app.log) для выявления ошибок, предупреждений и паттернов.

                Используй этот инструмент когда пользователь спрашивает:
                - "проанализируй логи"
                - "какая ошибка чаще всего?"
                - "где больше всего пользователей теряется?"
                - "покажи ошибки за последний час"

                ДВА СПОСОБА ИСПОЛЬЗОВАНИЯ:

                1. С локальным агентом (если подключен):
                   Шаг 1: android_studio_mcp {"action": "read_app_log", "offset": 0, "limit": 1000}
                   Шаг 2: log_analyzer {"action": "analyze_logs", "raw_logs": "<результат из шага 1>", "query": "топ ошибок"}

                2. Без локального агента (прямое чтение с сервера):
                   log_analyzer {"action": "analyze_logs", "query": "топ ошибок"}
                   (raw_logs опционален - если не указан, читается app.log с сервера)

                Инструмент автоматически использует RAG для поиска релевантной документации по найденным ошибкам.
            """.trimIndent(),
            enabled = true,
            input_schema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        put("description", "Action to perform")
                        putJsonArray("enum") {
                            add("analyze_logs")
                        }
                    }
                    putJsonObject("raw_logs") {
                        put("type", "string")
                        put("description", "Raw log content to analyze (optional - если не указан, читается app.log с сервера)")
                    }
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Что искать: 'топ ошибок', 'общий анализ', 'ошибки в ChatRoutes' (optional)")
                    }
                    putJsonObject("time_range") {
                        put("type", "string")
                        put("description", "Time range filter: 'last_hour', 'last_day', 'all' (optional)")
                    }
                }
                putJsonArray("required") {
                    add("action")
                    // raw_logs теперь опционален
                }
            }
        )
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        val action = arguments["action"]?.jsonPrimitive?.content
            ?: return errorJson("Missing required parameter: action")

        val rawLogsParam = arguments["raw_logs"]?.jsonPrimitive?.content
        val query = arguments["query"]?.jsonPrimitive?.content ?: "general analysis"
        val timeRange = arguments["time_range"]?.jsonPrimitive?.content

        logger.info("Log analyzer called: action=$action, query=$query, timeRange=$timeRange")

        return when (action) {
            "analyze_logs" -> {
                // Если raw_logs не передан ИЛИ это плейсхолдер, читаем напрямую с сервера
                val rawLogs = if (rawLogsParam == null || isPlaceholder(rawLogsParam)) {
                    if (rawLogsParam != null) {
                        logger.warn("Detected placeholder string in raw_logs: '${rawLogsParam.take(100)}...', falling back to direct file reading")
                    }
                    readServerLogs()
                } else {
                    rawLogsParam
                }

                // Проверяем, не является ли rawLogs JSON-ответом от android_studio_mcp
                val logsText = extractLogsFromJson(rawLogs)

                analyzeLogs(logsText, query, timeRange)
            }
            else -> errorJson("Unknown action: $action")
        }
    }

    /**
     * Читает логи напрямую с сервера (app.log в корне проекта)
     */
    private fun readServerLogs(): String {
        return try {
            val logFile = java.io.File("app.log")
            if (logFile.exists()) {
                logger.info("Reading server logs from: ${logFile.absolutePath}")
                logFile.readText()
            } else {
                logger.warn("Server log file not found: ${logFile.absolutePath}")
                ""
            }
        } catch (e: Exception) {
            logger.error("Failed to read server logs: ${e.message}", e)
            ""
        }
    }

    /**
     * Проверяет, является ли строка плейсхолдером из system prompt
     */
    private fun isPlaceholder(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("<результат") ||
               (trimmed.startsWith("<") && trimmed.contains("android_studio_mcp"))
    }


    /**
     * Извлекает текст логов из JSON-ответа android_studio_mcp или возвращает как есть
     */
    private fun extractLogsFromJson(rawLogs: String): String {
        return try {
            // Пытаемся распарсить как JSON
            val json = kotlinx.serialization.json.Json.parseToJsonElement(rawLogs).jsonObject

            // Если есть поле "logs" (массив) - это ответ от android_studio_mcp
            val logsArray = json["logs"]?.jsonArray
            if (logsArray != null) {
                logger.info("Detected JSON format from android_studio_mcp, extracting logs array")
                return logsArray.joinToString("\n") { it.jsonPrimitive.content }
            }

            // Если есть поле "content" - это тоже может быть ответ
            val content = json["content"]?.jsonPrimitive?.content
            if (content != null) {
                logger.info("Detected JSON format with 'content' field")
                return content
            }

            // Иначе возвращаем как есть
            rawLogs
        } catch (e: Exception) {
            // Не JSON - возвращаем как есть
            logger.debug("raw_logs is not JSON, using as plain text")
            rawLogs
        }
    }

    private suspend fun analyzeLogs(rawLogs: String, query: String, timeRange: String?): String {
        try {
            val lines = rawLogs.trim().split("\n").filter { it.isNotBlank() }

            if (lines.isEmpty()) {
                return buildJsonObject {
                    put("analysis", "Логи пустые или не содержат записей для анализа")
                    putJsonObject("metrics") {
                        put("total_logs", 0)
                        put("error_count", 0)
                        put("warn_count", 0)
                        putJsonArray("top_errors") {}
                        putJsonObject("context") {}
                    }
                }.toString()
            }

            // Parse all log entries
            val logEntries = lines.mapNotNull { parseLine(it) }

            // Filter by time range if specified
            val filteredEntries = timeRange?.let { filterByTimeRange(logEntries, it) } ?: logEntries

            // Classify by severity
            val errors = filteredEntries.filter { isError(it.message) }
            val warnings = filteredEntries.filter { isWarning(it.message) }

            // Group errors by type
            val errorTypes = errors.groupBy { extractErrorType(it.message) }
                .mapValues { (_, entries) -> entries.size }
                .toList()
                .sortedByDescending { it.second }

            // Group by logger (context)
            val loggerStats = filteredEntries.groupBy { it.logger }
                .mapValues { (_, entries) -> entries.size }

            // Build error details
            val topErrors = errorTypes.take(5).map { (errorType, count) ->
                val firstOccurrence = errors.first { extractErrorType(it.message) == errorType }
                buildJsonObject {
                    put("type", errorType)
                    put("count", count)
                    put("first_seen", firstOccurrence.time)
                    put("logger", firstOccurrence.logger)
                }
            }

            // Получаем RAG контекст для найденных ошибок
            val ragContext = retrieveRagContext(errorTypes.take(5), loggerStats)

            // Generate analysis text based on query
            val analysisText = generateAnalysis(
                query = query,
                totalLogs = filteredEntries.size,
                errorCount = errors.size,
                warnCount = warnings.size,
                topErrors = errorTypes.take(5),
                loggerStats = loggerStats,
                allEntries = filteredEntries,
                ragContext = ragContext
            )

            return buildJsonObject {
                put("analysis", analysisText)
                putJsonObject("metrics") {
                    put("total_logs", filteredEntries.size)
                    put("error_count", errors.size)
                    put("warn_count", warnings.size)
                    putJsonArray("top_errors") {
                        topErrors.forEach { add(it) }
                    }
                    putJsonObject("context") {
                        loggerStats.entries.sortedByDescending { it.value }.take(10).forEach {
                            put(it.key, it.value)
                        }
                    }
                }
            }.toString()

        } catch (e: Exception) {
            logger.error("Error analyzing logs: ${e.message}", e)
            return errorJson("Failed to analyze logs: ${e.message}")
        }
    }

    private fun parseLine(line: String): LogEntry? {
        val match = logLineRegex.matchEntire(line) ?: return null
        val (time, logger, message) = match.destructured
        return LogEntry(time, logger, message)
    }

    private fun isError(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("error") ||
                lowerMessage.contains("exception") ||
                lowerMessage.contains("failed") ||
                lowerMessage.contains("❌")
    }

    private fun isWarning(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("warn") ||
                lowerMessage.contains("warning") ||
                lowerMessage.contains("⚠️")
    }

    private fun extractErrorType(message: String): String {
        // Try to extract exception class name
        val exceptionMatch = Regex("""(\w+Exception)""").find(message)
        if (exceptionMatch != null) {
            return exceptionMatch.groupValues[1]
        }

        // Try to extract "Error: <type>" pattern
        val errorMatch = Regex("""Error:\s*([^-\n]+)""").find(message)
        if (errorMatch != null) {
            return errorMatch.groupValues[1].trim().take(50)
        }

        // Try to extract "Failed to <action>" pattern
        val failedMatch = Regex("""Failed to\s+([^-\n]+)""").find(message)
        if (failedMatch != null) {
            return "Failed to ${failedMatch.groupValues[1].trim().take(30)}"
        }

        // Generic error
        return "General Error"
    }

    private fun filterByTimeRange(entries: List<LogEntry>, timeRange: String): List<LogEntry> {
        if (timeRange == "all" || entries.isEmpty()) return entries

        try {
            val latestEntry = entries.maxByOrNull { parseTime(it.time) } ?: return entries
            val latestTime = parseTime(latestEntry.time)

            val cutoffTime = when (timeRange) {
                "last_hour" -> latestTime.minusHours(1)
                "last_day" -> latestTime.minusHours(24)
                else -> return entries
            }

            return entries.filter { parseTime(it.time).isAfter(cutoffTime) || parseTime(it.time) == cutoffTime }
        } catch (e: Exception) {
            logger.warn("Failed to filter by time range: ${e.message}")
            return entries
        }
    }

    private fun parseTime(timeStr: String): LocalTime {
        return LocalTime.parse(timeStr, timeFormatter)
    }

    /**
     * Получает релевантный контекст из RAG базы знаний на основе найденных ошибок
     */
    private suspend fun retrieveRagContext(
        topErrors: List<Pair<String, Int>>,
        loggerStats: Map<String, Int>
    ): String? {
        if (ragService == null || ollamaEmbeddingClient == null) {
            logger.debug("RAG services not available for log analysis")
            return null
        }

        if (topErrors.isEmpty() && loggerStats.isEmpty()) {
            return null
        }

        return try {
            // Формируем запрос на основе найденных ошибок и модулей
            val query = buildRagQuery(topErrors, loggerStats)
            logger.info("🔍 Searching RAG for log analysis context: ${query.take(100)}...")

            // Генерируем embedding
            val queryEmbedding = ollamaEmbeddingClient.embed(query)
            val normalizedEmbedding = normalizeToRange(queryEmbedding)

            // Ищем релевантные документы
            val results = ragService.search(
                queryEmbedding = normalizedEmbedding,
                topK = 3,
                minSimilarity = 0.5  // Более низкий порог для логов
            )

            if (results.isEmpty()) {
                logger.info("No relevant documentation found for log errors")
                return null
            }

            logger.info("Found ${results.size} relevant documentation chunks for log analysis")

            // Форматируем контекст специально для анализа логов
            formatRagContextForLogs(results)

        } catch (e: Exception) {
            logger.error("Failed to retrieve RAG context for logs: ${e.message}", e)
            null
        }
    }

    /**
     * Формирует запрос к RAG на основе найденных ошибок
     */
    private fun buildRagQuery(
        topErrors: List<Pair<String, Int>>,
        loggerStats: Map<String, Int>
    ): String {
        return buildString {
            appendLine("Log analysis context:")

            if (topErrors.isNotEmpty()) {
                appendLine("Top errors found:")
                topErrors.take(3).forEach { (errorType, count) ->
                    appendLine("- $errorType (occurred $count times)")
                }
            }

            if (loggerStats.isNotEmpty()) {
                appendLine("Most active modules:")
                loggerStats.entries.sortedByDescending { it.value }.take(3).forEach { (logger, count) ->
                    appendLine("- $logger ($count log entries)")
                }
            }
        }
    }

    /**
     * Форматирует RAG результаты специально для анализа логов
     */
    private fun formatRagContextForLogs(results: List<RagService.SearchResult>): String {
        return buildString {
            appendLine("\n📚 Релевантная документация:\n")

            results.forEachIndexed { index, result ->
                val fileName = result.docId.substringAfterLast('/')
                val similarityPercent = (result.similarity * 100).toInt()

                appendLine("${index + 1}. $fileName (релевантность: $similarityPercent%)")

                // Показываем краткую выдержку (первые 200 символов)
                val excerpt = result.text.trim().take(200)
                appendLine("   ${excerpt}...")
                appendLine()
            }

            appendLine("💡 Рекомендации:")
            appendLine("- Проверьте документацию выше для понимания архитектуры")
            appendLine("- Убедитесь, что конфигурация соответствует best practices")
            appendLine("- Рассмотрите возможность добавления обработки ошибок")
        }
    }

    private fun generateAnalysis(
        query: String,
        totalLogs: Int,
        errorCount: Int,
        warnCount: Int,
        topErrors: List<Pair<String, Int>>,
        loggerStats: Map<String, Int>,
        allEntries: List<LogEntry>,
        ragContext: String?
    ): String {
        val builder = StringBuilder()

        builder.append("📊 Анализ логов:\n\n")
        builder.append("Всего записей: $totalLogs\n")
        builder.append("Ошибок (ERROR): $errorCount\n")
        builder.append("Предупреждений (WARN): $warnCount\n")
        builder.append("Информационных: ${totalLogs - errorCount - warnCount}\n\n")

        if (topErrors.isNotEmpty()) {
            builder.append("🔴 Топ ошибок:\n")
            topErrors.forEachIndexed { index, (errorType, count) ->
                val percentage = (count.toDouble() / errorCount * 100).toInt()
                builder.append("${index + 1}. $errorType — $count раз ($percentage%)\n")
            }
            builder.append("\n")
        } else {
            builder.append("✅ Ошибок не обнаружено\n\n")
        }

        if (loggerStats.isNotEmpty()) {
            builder.append("📦 Контекст (по модулям):\n")
            loggerStats.entries.sortedByDescending { it.value }.take(5).forEach { (logger, count) ->
                val percentage = (count.toDouble() / totalLogs * 100).toInt()
                builder.append("- $logger: $count записей ($percentage%)\n")
            }
            builder.append("\n")
        }

        // Add query-specific insights
        if (query.contains("чаще", ignoreCase = true) && topErrors.isNotEmpty()) {
            val (mostFrequent, count) = topErrors.first()
            builder.append("🎯 Самая частая ошибка: $mostFrequent\n")
            builder.append("   Встречается $count раз (${(count.toDouble() / errorCount * 100).toInt()}% всех ошибок)\n")
        }

        // Добавляем RAG контекст если доступен
        if (ragContext != null) {
            builder.append("\n")
            builder.append(ragContext)
        }

        return builder.toString().trim()
    }

    private data class LogEntry(
        val time: String,
        val logger: String,
        val message: String
    )
}
