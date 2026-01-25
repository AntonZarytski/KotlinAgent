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

                Инструмент автоматически использует RAG для поиска релевантной документации по найденным логам.
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
     *
     * Поддерживаемые форматы:
     * 1. {"content": "..."} - ответ от read_file
     * 2. {"logs": ["...", "..."]} - массив логов
     * 3. Обычный текст - возвращается как есть
     */
    private fun extractLogsFromJson(rawLogs: String): String {
        return try {
            // Пытаемся распарсить как JSON
            val json = kotlinx.serialization.json.Json.parseToJsonElement(rawLogs).jsonObject

            // Проверяем статус ответа
            val status = json["status"]?.jsonPrimitive?.content
            if (status == "error") {
                val errorMsg = json["error"]?.jsonPrimitive?.content ?: "Unknown error"
                logger.error("Error from android_studio_mcp: $errorMsg")
                return ""
            }

            // Если есть поле "content" - это ответ от read_file
            val content = json["content"]?.jsonPrimitive?.content
            if (content != null) {
                logger.info("Detected JSON format with 'content' field (read_file response)")
                return content
            }

            // Если есть поле "logs" (массив) - это массив логов
            val logsArray = json["logs"]?.jsonArray
            if (logsArray != null) {
                logger.info("Detected JSON format from android_studio_mcp, extracting logs array")
                return logsArray.joinToString("\n") { it.jsonPrimitive.content }
            }

            // Иначе возвращаем как есть
            logger.debug("JSON format not recognized, using raw content")
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
                        put("info_count", 0)
                        put("debug_count", 0)
                        putJsonArray("top_errors") {}
                        putJsonArray("top_patterns") {}
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
            val infos = filteredEntries.filter { isInfo(it.message) }
            val debugs = filteredEntries.filter { isDebug(it.message) }

            // Group errors by type
            val errorTypes = errors.groupBy { extractErrorType(it.message) }
                .mapValues { (_, entries) -> entries.size }
                .toList()
                .sortedByDescending { it.second }

            // Group ALL log patterns (not just errors)
            val allPatterns = filteredEntries.groupBy { extractLogPattern(it.message) }
                .mapValues { (_, entries) ->
                    PatternInfo(
                        count = entries.size,
                        firstOccurrence = entries.first(),
                        level = detectLogLevel(entries.first().message)
                    )
                }
                .toList()
                .sortedByDescending { it.second.count }

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

            // Build top patterns (all levels)
            val topPatterns = allPatterns.take(10).map { (pattern, info) ->
                buildJsonObject {
                    put("pattern", pattern)
                    put("count", info.count)
                    put("level", info.level)
                    put("first_seen", info.firstOccurrence.time)
                    put("logger", info.firstOccurrence.logger)
                }
            }

            // Получаем RAG контекст для ВСЕХ типов логов (не только ошибок)
            val ragContext = retrieveRagContext(
                topErrors = errorTypes.take(3),
                topPatterns = allPatterns.take(10),
                loggerStats = loggerStats,
                allEntries = filteredEntries
            )

            // Generate analysis text based on query
            val analysisText = generateAnalysis(
                query = query,
                totalLogs = filteredEntries.size,
                errorCount = errors.size,
                warnCount = warnings.size,
                infoCount = infos.size,
                debugCount = debugs.size,
                topErrors = errorTypes.take(5),
                topPatterns = allPatterns.take(10),
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
                    put("info_count", infos.size)
                    put("debug_count", debugs.size)
                    putJsonArray("top_errors") {
                        topErrors.forEach { add(it) }
                    }
                    putJsonArray("top_patterns") {
                        topPatterns.forEach { add(it) }
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

    private fun isInfo(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("info") ||
                lowerMessage.contains("✅") ||
                lowerMessage.contains("📊") ||
                lowerMessage.contains("🔍") ||
                (!isError(message) && !isWarning(message) && !isDebug(message))
    }

    private fun isDebug(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("debug") ||
                lowerMessage.contains("🐛") ||
                lowerMessage.contains("[debug]")
    }

    private fun detectLogLevel(message: String): String {
        return when {
            isError(message) -> "ERROR"
            isWarning(message) -> "WARN"
            isDebug(message) -> "DEBUG"
            isInfo(message) -> "INFO"
            else -> "UNKNOWN"
        }
    }

    /**
     * Извлекает паттерн лога для группировки
     * Пытается найти ключевую фразу или действие в сообщении
     */
    private fun extractLogPattern(message: String): String {
        // Убираем эмодзи и специальные символы для чистоты паттерна
        val cleanMessage = message.replace(Regex("[🔍📊✅❌⚠️🐛📁🔌💾🚀]"), "").trim()

        // Пытаемся извлечь ключевую фразу (первые 60 символов)
        val pattern = cleanMessage.take(60)

        // Если есть двоеточие, берем часть до него
        val colonIndex = pattern.indexOf(':')
        if (colonIndex > 0 && colonIndex < 50) {
            return pattern.substring(0, colonIndex).trim()
        }

        return pattern
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
     * Получает релевантный контекст из RAG базы знаний на основе найденных паттернов
     * Теперь работает со ВСЕМИ типами логов, не только с ошибками
     */
    private suspend fun retrieveRagContext(
        topErrors: List<Pair<String, Int>>,
        topPatterns: List<Pair<String, PatternInfo>>,
        loggerStats: Map<String, Int>,
        allEntries: List<LogEntry>
    ): String? {
        if (ragService == null || ollamaEmbeddingClient == null) {
            logger.debug("RAG services not available for log analysis")
            return null
        }

        if (topErrors.isEmpty() && topPatterns.isEmpty() && loggerStats.isEmpty()) {
            return null
        }

        return try {
            // Формируем запрос на основе найденных паттернов (включая конкретные строки логов)
            val query = buildRagQuery(topErrors, topPatterns, loggerStats, allEntries)
            logger.info("🔍 Searching RAG for log analysis context: ${query.take(150)}...")

            // Генерируем embedding
            val queryEmbedding = ollamaEmbeddingClient.embed(query)
            val normalizedEmbedding = normalizeToRange(queryEmbedding)

            // Ищем релевантные документы с более широким порогом
            val results = ragService.search(
                queryEmbedding = normalizedEmbedding,
                topK = 5,  // Увеличиваем количество результатов
                minSimilarity = 0.5  // Более низкий порог для логов
            )

            if (results.isEmpty()) {
                logger.info("No relevant documentation found for log patterns")
                return null
            }

            logger.info("Found ${results.size} relevant documentation chunks for log analysis")

            // Форматируем контекст специально для анализа логов
            formatRagContextForLogs(results, topPatterns)

        } catch (e: Exception) {
            logger.error("Failed to retrieve RAG context for logs: ${e.message}", e)
            null
        }
    }

    /**
     * Формирует запрос к RAG на основе найденных паттернов
     * Включает конкретные строки логов для более точного поиска в logs-catalog.md
     */
    private fun buildRagQuery(
        topErrors: List<Pair<String, Int>>,
        topPatterns: List<Pair<String, PatternInfo>>,
        loggerStats: Map<String, Int>,
        allEntries: List<LogEntry>
    ): String {
        return buildString {
            appendLine("Log analysis context:")

            if (topErrors.isNotEmpty()) {
                appendLine("\nTop errors found:")
                topErrors.take(3).forEach { (errorType, count) ->
                    appendLine("- $errorType (occurred $count times)")
                }
            }

            if (topPatterns.isNotEmpty()) {
                appendLine("\nTop log patterns (all levels):")
                topPatterns.take(5).forEach { (pattern, info) ->
                    appendLine("- [${info.level}] $pattern (occurred ${info.count} times)")
                    // Добавляем конкретную строку лога для точного поиска
                    appendLine("  Example: ${info.firstOccurrence.message.take(100)}")
                }
            }

            if (loggerStats.isNotEmpty()) {
                appendLine("\nMost active modules:")
                loggerStats.entries.sortedByDescending { it.value }.take(3).forEach { (logger, count) ->
                    appendLine("- $logger ($count log entries)")
                }
            }

            // Добавляем примеры конкретных строк логов для поиска в logs-catalog.md
            appendLine("\nSample log messages for context:")
            allEntries.take(5).forEach { entry ->
                appendLine("- ${entry.message.take(80)}")
            }
        }
    }

    /**
     * Форматирует RAG результаты специально для анализа логов
     * Показывает детальное объяснение каждого паттерна из logs-catalog.md
     */
    private fun formatRagContextForLogs(
        results: List<RagService.SearchResult>,
        topPatterns: List<Pair<String, PatternInfo>>
    ): String {
        return buildString {
            appendLine("\n📚 Контекст из документации (logs-catalog.md):\n")

            results.forEachIndexed { index, result ->
                val fileName = result.docId.substringAfterLast('/')
                val similarityPercent = (result.similarity * 100).toInt()

                appendLine("${index + 1}. $fileName (релевантность: $similarityPercent%)")
                appendLine("   Схожесть: ${String.format("%.3f", result.similarity)}")
                appendLine()

                // Парсим и показываем детальную информацию из logs-catalog.md
                val logInfo = parseLogCatalogEntry(result.text)
                if (logInfo != null) {
                    appendLine("   📋 Описание лога:")
                    appendLine("   • Уровень: ${logInfo.level}")
                    appendLine("   • Сообщение: ${logInfo.message}")
                    if (logInfo.file.isNotEmpty()) {
                        appendLine("   • Файл: ${logInfo.file}")
                    }
                    if (logInfo.whenAppears.isNotEmpty()) {
                        appendLine("   • Когда появляется: ${logInfo.whenAppears}")
                    }
                    if (logInfo.meaning.isNotEmpty()) {
                        appendLine("   • Значение: ${logInfo.meaning}")
                    }
                    if (logInfo.causes.isNotEmpty()) {
                        appendLine("   • Возможные причины:")
                        logInfo.causes.forEach { cause ->
                            appendLine("     - $cause")
                        }
                    }
                    if (logInfo.solution.isNotEmpty()) {
                        appendLine("   • Решение: ${logInfo.solution}")
                    }
                } else {
                    // Если не удалось распарсить, показываем как есть
                    val excerpt = result.text.trim().take(300)
                    appendLine("   $excerpt...")
                }
                appendLine()
                appendLine("   " + "─".repeat(60))
                appendLine()
            }

            // Добавляем сводку по найденным паттернам
            if (topPatterns.isNotEmpty()) {
                appendLine("\n🔍 Анализ найденных паттернов:")
                topPatterns.take(5).forEach { (pattern, info) ->
                    appendLine("• [${info.level}] $pattern")
                    appendLine("  Встречается: ${info.count} раз")
                    appendLine("  Модуль: ${info.firstOccurrence.logger}")
                    appendLine()
                }
            }

            appendLine("\n💡 Рекомендации на основе анализа:")
            appendLine("• Изучите документацию выше для понимания каждого типа лога")
            appendLine("• Обратите внимание на частоту появления паттернов")
            appendLine("• Проверьте модули с наибольшей активностью")
            appendLine("• Для ошибок следуйте рекомендациям по решению из документации")
        }
    }

    /**
     * Парсит запись из logs-catalog.md для извлечения структурированной информации
     */
    private fun parseLogCatalogEntry(text: String): LogCatalogInfo? {
        return try {
            val lines = text.split("\n").map { it.trim() }

            // Ищем заголовок с уровнем и сообщением
            val headerLine = lines.firstOrNull { it.startsWith("###") } ?: return null
            val level = when {
                headerLine.contains("✅ INFO") -> "INFO"
                headerLine.contains("⚠️ WARN") -> "WARN"
                headerLine.contains("❌ ERROR") -> "ERROR"
                headerLine.contains("🐛 DEBUG") -> "DEBUG"
                else -> "UNKNOWN"
            }

            val message = headerLine.substringAfter(":").trim().removeSurrounding("`")

            // Извлекаем остальные поля
            var file = ""
            var whenAppears = ""
            var meaning = ""
            val causes = mutableListOf<String>()
            var solution = ""

            var inCauses = false

            for (line in lines) {
                when {
                    line.startsWith("- **Файл:**") -> file = line.substringAfter("**Файл:**").trim().removeSurrounding("`")
                    line.startsWith("- **Когда появляется:**") -> whenAppears = line.substringAfter("**Когда появляется:**").trim()
                    line.startsWith("- **Значение:**") -> meaning = line.substringAfter("**Значение:**").trim()
                    line.startsWith("- **Возможные причины:**") -> inCauses = true
                    line.startsWith("- **Решение:**") -> {
                        inCauses = false
                        solution = line.substringAfter("**Решение:**").trim()
                    }
                    inCauses && line.startsWith("  -") -> causes.add(line.substring(3).trim())
                }
            }

            LogCatalogInfo(level, message, file, whenAppears, meaning, causes, solution)
        } catch (e: Exception) {
            logger.debug("Failed to parse log catalog entry: ${e.message}")
            null
        }
    }

    private data class LogCatalogInfo(
        val level: String,
        val message: String,
        val file: String,
        val whenAppears: String,
        val meaning: String,
        val causes: List<String>,
        val solution: String
    )

    private fun generateAnalysis(
        query: String,
        totalLogs: Int,
        errorCount: Int,
        warnCount: Int,
        infoCount: Int,
        debugCount: Int,
        topErrors: List<Pair<String, Int>>,
        topPatterns: List<Pair<String, PatternInfo>>,
        loggerStats: Map<String, Int>,
        allEntries: List<LogEntry>,
        ragContext: String?
    ): String {
        val builder = StringBuilder()

        builder.append("📊 Комплексный анализ логов:\n\n")

        // 1. Статистика
        builder.append("═══ 1. СТАТИСТИКА ═══\n")
        builder.append("Всего записей: $totalLogs\n")
        builder.append("• Ошибок (ERROR): $errorCount (${percentage(errorCount, totalLogs)}%)\n")
        builder.append("• Предупреждений (WARN): $warnCount (${percentage(warnCount, totalLogs)}%)\n")
        builder.append("• Информационных (INFO): $infoCount (${percentage(infoCount, totalLogs)}%)\n")
        builder.append("• Отладочных (DEBUG): $debugCount (${percentage(debugCount, totalLogs)}%)\n\n")

        // 2. Топ паттернов (все уровни)
        if (topPatterns.isNotEmpty()) {
            builder.append("═══ 2. ТОП ПАТТЕРНОВ (ВСЕ УРОВНИ) ═══\n")
            topPatterns.take(10).forEachIndexed { index, (pattern, info) ->
                val percentage = percentage(info.count, totalLogs)
                builder.append("${index + 1}. [${info.level}] $pattern\n")
                builder.append("   Частота: ${info.count} раз ($percentage%)\n")
                builder.append("   Модуль: ${info.firstOccurrence.logger}\n")
                builder.append("   Первое появление: ${info.firstOccurrence.time}\n")
            }
            builder.append("\n")
        }

        // 3. Топ ошибок (детально)
        if (topErrors.isNotEmpty()) {
            builder.append("═══ 3. ТОП ОШИБОК (ДЕТАЛЬНО) ═══\n")
            topErrors.forEachIndexed { index, (errorType, count) ->
                val percentage = if (errorCount > 0) percentage(count, errorCount) else 0
                builder.append("${index + 1}. $errorType\n")
                builder.append("   Встречается: $count раз ($percentage% всех ошибок)\n")
            }
            builder.append("\n")
        } else {
            builder.append("═══ 3. ТОП ОШИБОК ═══\n")
            builder.append("✅ Ошибок не обнаружено\n\n")
        }

        // 4. Временные паттерны
        builder.append("═══ 4. ВРЕМЕННЫЕ ПАТТЕРНЫ ═══\n")
        val timeAnalysis = analyzeTimePatterns(allEntries)
        builder.append(timeAnalysis)
        builder.append("\n")

        // 5. Анализ модулей
        if (loggerStats.isNotEmpty()) {
            builder.append("═══ 5. АНАЛИЗ МОДУЛЕЙ ═══\n")
            loggerStats.entries.sortedByDescending { it.value }.take(10).forEach { (logger, count) ->
                val percentage = percentage(count, totalLogs)
                builder.append("• $logger: $count записей ($percentage%)\n")
            }
            builder.append("\n")
        }

        // 6. Связи между событиями
        builder.append("═══ 6. СВЯЗИ МЕЖДУ СОБЫТИЯМИ ═══\n")
        val correlations = analyzeCorrelations(allEntries)
        builder.append(correlations)
        builder.append("\n")

        // 7. RAG контекст (если доступен)
        if (ragContext != null) {
            builder.append("═══ 7. КОНТЕКСТ ИЗ ДОКУМЕНТАЦИИ ═══\n")
            builder.append(ragContext)
            builder.append("\n")
        }

        // 8. Общие выводы
        builder.append("═══ 8. ОБЩИЕ ВЫВОДЫ ═══\n")
        builder.append(generateConclusions(errorCount, warnCount, infoCount, topErrors, topPatterns, allEntries))

        // Query-specific insights
        if (query.contains("чаще", ignoreCase = true) && topErrors.isNotEmpty()) {
            builder.append("\n\n🎯 Ответ на запрос:\n")
            val (mostFrequent, count) = topErrors.first()
            builder.append("Самая частая ошибка: $mostFrequent\n")
            builder.append("Встречается $count раз (${if (errorCount > 0) percentage(count, errorCount) else 0}% всех ошибок)\n")
        }

        return builder.toString().trim()
    }

    private fun percentage(part: Int, total: Int): Int {
        return if (total > 0) (part.toDouble() / total * 100).toInt() else 0
    }

    /**
     * Анализирует временные паттерны в логах
     */
    private fun analyzeTimePatterns(entries: List<LogEntry>): String {
        if (entries.isEmpty()) return "Нет данных для анализа\n"

        val builder = StringBuilder()

        // Группируем по часам
        val byHour = entries.groupBy { entry ->
            try {
                val time = parseTime(entry.time)
                time.hour
            } catch (e: Exception) {
                -1
            }
        }.filterKeys { it >= 0 }

        if (byHour.isNotEmpty()) {
            val maxHour = byHour.maxByOrNull { it.value.size }
            if (maxHour != null) {
                builder.append("• Пик активности: ${maxHour.key}:00 (${maxHour.value.size} записей)\n")
            }

            // Находим часы с наибольшим количеством ошибок
            val errorsByHour = byHour.mapValues { (_, entries) ->
                entries.count { isError(it.message) }
            }.filterValues { it > 0 }

            if (errorsByHour.isNotEmpty()) {
                val maxErrorHour = errorsByHour.maxByOrNull { it.value }
                if (maxErrorHour != null) {
                    builder.append("• Больше всего ошибок: ${maxErrorHour.key}:00 (${maxErrorHour.value} ошибок)\n")
                }
            }
        }

        // Анализ временных промежутков
        if (entries.size > 1) {
            val firstTime = entries.first().time
            val lastTime = entries.last().time
            builder.append("• Временной диапазон: $firstTime - $lastTime\n")
        }

        return builder.toString()
    }

    /**
     * Анализирует корреляции между событиями
     */
    private fun analyzeCorrelations(entries: List<LogEntry>): String {
        if (entries.size < 2) return "Недостаточно данных для анализа корреляций\n"

        val builder = StringBuilder()

        // Ищем последовательности: после какого события часто идет ошибка
        val errors = entries.filter { isError(it.message) }
        if (errors.isNotEmpty()) {
            val precedingPatterns = mutableMapOf<String, Int>()

            errors.forEach { error ->
                val errorIndex = entries.indexOf(error)
                if (errorIndex > 0) {
                    val preceding = entries[errorIndex - 1]
                    val pattern = extractLogPattern(preceding.message)
                    precedingPatterns[pattern] = precedingPatterns.getOrDefault(pattern, 0) + 1
                }
            }

            if (precedingPatterns.isNotEmpty()) {
                val topPreceding = precedingPatterns.entries.sortedByDescending { it.value }.take(3)
                builder.append("• События, после которых часто появляются ошибки:\n")
                topPreceding.forEach { (pattern, count) ->
                    builder.append("  - $pattern (${count} раз)\n")
                }
            }
        }

        // Ищем повторяющиеся последовательности
        if (entries.size >= 3) {
            builder.append("• Обнаружено ${entries.size} событий в хронологическом порядке\n")
        }

        return builder.toString()
    }

    /**
     * Генерирует общие выводы о состоянии системы
     */
    private fun generateConclusions(
        errorCount: Int,
        warnCount: Int,
        infoCount: Int,
        topErrors: List<Pair<String, Int>>,
        topPatterns: List<Pair<String, PatternInfo>>,
        allEntries: List<LogEntry>
    ): String {
        val builder = StringBuilder()

        // Оценка состояния системы
        val healthScore = when {
            errorCount == 0 && warnCount == 0 -> "Отличное"
            errorCount == 0 && warnCount < 5 -> "Хорошее"
            errorCount < 5 && warnCount < 10 -> "Удовлетворительное"
            errorCount < 10 -> "Требует внимания"
            else -> "Критическое"
        }

        builder.append("• Состояние системы: $healthScore\n")

        if (errorCount > 0) {
            builder.append("• Требуется исправление ${errorCount} ошибок\n")
            if (topErrors.isNotEmpty()) {
                val (topError, count) = topErrors.first()
                builder.append("• Приоритет: $topError (встречается чаще всего)\n")
            }
        } else {
            builder.append("• Критических проблем не обнаружено\n")
        }

        if (warnCount > 0) {
            builder.append("• Рекомендуется проверить ${warnCount} предупреждений\n")
        }

        // Рекомендации
        builder.append("\n💡 Рекомендации:\n")
        if (errorCount > 0) {
            builder.append("  1. Начните с исправления самых частых ошибок\n")
            builder.append("  2. Изучите контекст из документации выше\n")
        }
        if (warnCount > 5) {
            builder.append("  3. Обратите внимание на предупреждения - они могут стать ошибками\n")
        }
        builder.append("  4. Мониторьте модули с наибольшей активностью\n")

        return builder.toString()
    }

    private data class LogEntry(
        val time: String,
        val logger: String,
        val message: String
    )

    private data class PatternInfo(
        val count: Int,
        val firstOccurrence: LogEntry,
        val level: String
    )
}
