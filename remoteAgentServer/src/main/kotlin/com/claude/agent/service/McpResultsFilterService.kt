package com.claude.agent.service

import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для фильтрации MCP результатов.
 *
 * Агенту не всегда нужен полный ответ от MCP инструментов - например,
 * если через android_mcp получаем дерево файлов с тысячами файлов,
 * то агенту достаточно знать, что операция прошла успешно и сколько файлов получено.
 *
 * Полный результат отправляется в UI через WebSocket для отображения пользователю.
 */
object McpResultsFilterService {
    private val logger = LoggerFactory.getLogger(McpResultsFilterService::class.java)

    /**
     * Данные о результате MCP инструмента
     */
    data class McpResult(
        val fullResult: String,      // Полный результат для UI
        val summaryResult: String,   // Краткий результат для агента
        val useSummary: Boolean       // Использовать ли краткую версию для агента
    )

    /**
     * Фильтрует результат MCP инструмента.
     * Возвращает полный результат для UI и краткий (summary) для агента.
     */
    fun filterMcpResult(toolName: String, action: String?, result: String): McpResult {
        logger.debug("🔍 Filtering MCP result: tool=$toolName, action=$action")

        return when {
            // android_studio_mcp
            toolName == "android_studio_mcp" -> filterAndroidStudioResult(action, result)

            // Другие MCP инструменты можно добавить здесь
            else -> {
                // По умолчанию не фильтруем
                McpResult(
                    fullResult = result,
                    summaryResult = result,
                    useSummary = false
                )
            }
        }
    }

    private fun filterAndroidStudioResult(action: String?, result: String): McpResult {
        try {
            val json = Json.parseToJsonElement(result).jsonObject
            val status = json["status"]?.jsonPrimitive?.contentOrNull

            return when (action) {
                "get_file_tree" -> {
                    // Для file tree всегда возвращаем summary
                    if (status == "success") {
                        val projectPath = json["project_path"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                        val fileCount = countFilesInTree(json["tree"]?.jsonObject)

                        val summary = buildJsonObject {
                            put("status", "success")
                            put("action", "get_file_tree")
                            put("project_path", projectPath)
                            put("file_count", fileCount)
                            put("message", "File tree loaded successfully with $fileCount files")
                        }.toString()

                        logger.info("📂 File tree summary: $fileCount files at $projectPath")

                        McpResult(
                            fullResult = result,
                            summaryResult = summary,
                            useSummary = true
                        )
                    } else {
                        // Ошибка - передаем как есть
                        McpResult(result, result, false)
                    }
                }

                "browse_files" -> {
                    // Для browse_files - summary если >20 файлов
                    if (status == "success") {
                        val filesArray = json["files"]?.jsonArray
                        val fileCount = filesArray?.size ?: 0

                        if (fileCount > 20) {
                            val path = json["path"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                            val dirCount = filesArray?.count {
                                it.jsonObject["type"]?.jsonPrimitive?.content == "directory"
                            } ?: 0
                            val actualFileCount = fileCount - dirCount

                            val summary = buildJsonObject {
                                put("status", "success")
                                put("action", "browse_files")
                                put("path", path)
                                put("total_items", fileCount)
                                put("directories", dirCount)
                                put("files", actualFileCount)
                                put("message", "Found $actualFileCount files and $dirCount directories")
                            }.toString()

                            logger.info("📁 Browse files summary: $fileCount items (>20 threshold)")

                            McpResult(
                                fullResult = result,
                                summaryResult = summary,
                                useSummary = true
                            )
                        } else {
                            // Мало файлов - передаем полностью
                            McpResult(result, result, false)
                        }
                    } else {
                        McpResult(result, result, false)
                    }
                }

                "read_file" -> {
                    // Для read_file - всегда полный текст агенту
                    McpResult(result, result, false)
                }

                "logcat" -> {
                    // Для logcat - summary если >100 строк
                    if (status == "success") {
                        val logsArray = json["logs"]?.jsonArray
                        val logCount = logsArray?.size ?: 0

                        if (logCount > 100) {
                            // Берем первые 20 и последние 20 строк
                            val firstLogs = logsArray?.take(20) ?: emptyList()
                            val lastLogs = logsArray?.takeLast(20) ?: emptyList()

                            val summary = buildJsonObject {
                                put("status", "success")
                                put("action", "logcat")
                                put("total_lines", logCount)
                                put("showing", "first 20 and last 20 lines")
                                putJsonArray("first_logs") {
                                    firstLogs.forEach { add(it) }
                                }
                                putJsonArray("last_logs") {
                                    lastLogs.forEach { add(it) }
                                }
                                put("message", "Showing 40 out of $logCount log lines (first 20 and last 20)")
                            }.toString()

                            logger.info("📋 Logcat summary: $logCount lines (>100 threshold)")

                            McpResult(
                                fullResult = result,
                                summaryResult = summary,
                                useSummary = true
                            )
                        } else {
                            // Мало строк - передаем полностью
                            McpResult(result, result, false)
                        }
                    } else {
                        McpResult(result, result, false)
                    }
                }

                else -> {
                    // Для остальных действий - без фильтрации
                    McpResult(result, result, false)
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Error filtering MCP result: ${e.message}", e)
            // При ошибке парсинга - возвращаем оригинал
            return McpResult(result, result, false)
        }
    }

    /**
     * Рекурсивно подсчитывает количество файлов в дереве
     */
    private fun countFilesInTree(tree: JsonObject?): Int {
        if (tree == null) return 0

        var count = 0
        val type = tree["type"]?.jsonPrimitive?.content

        if (type == "file") {
            count = 1
        } else if (type == "directory") {
            val children = tree["children"]?.jsonArray
            children?.forEach { child ->
                count += countFilesInTree(child.jsonObject)
            }
        }

        return count
    }
}
