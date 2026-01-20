package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.providers.LocalMcpProvider
import com.claude.agent.llm.mcp.remote.model.RemoteToolDefinition
import com.claude.agent.models.UserLocation
import com.claude.agent.service.OllamaEmbeddingClient
import com.claude.agent.service.RagService
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class HelpMcp(
    private val ragService: RagService?,
    private val ollamaEmbeddingClient: OllamaEmbeddingClient?
) : Mcp.Local {
    private val logger = LoggerFactory.getLogger(HelpMcp::class.java)

    // Будет установлен после инициализации для избежания циклической зависимости
    var localMcpProvider: LocalMcpProvider? = null
    var remoteTools: List<RemoteToolDefinition>? = null
    
    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = "project_help",
        second = LocalToolDefinition(
            name = "project_help",
            ui_description = "Помощь по проекту: документация, примеры кода, правила стиля, объяснения работы",
            description = """
                Инструмент для получения помощи по проекту KotlinAgent.

                РЕЖИМЫ РАБОТЫ:
                1. Общая справка (без query): возвращает полный обзор проекта, архитектуру, список MCP tools, API endpoints
                2. Поиск по теме (с query): использует RAG для поиска релевантной информации по конкретному вопросу

                Категории помощи (опционально):
                - architecture: архитектура проекта
                - api: API endpoints и использование
                - mcp: MCP tools и интеграция
                - rag: RAG система
                - deployment: деплой и конфигурация
                - code_style: правила стиля кода
                - examples: примеры использования
                - general: общие вопросы
            """.trimIndent(),
            enabled = true,
            input_schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "query" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("Вопрос о проекте (опционально). Если не указан - возвращается общая справка")
                                )
                            ),
                            "category" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "enum" to JsonArray(listOf(
                                        JsonPrimitive("architecture"),
                                        JsonPrimitive("api"),
                                        JsonPrimitive("mcp"),
                                        JsonPrimitive("rag"),
                                        JsonPrimitive("deployment"),
                                        JsonPrimitive("code_style"),
                                        JsonPrimitive("examples"),
                                        JsonPrimitive("general")
                                    )),
                                    "description" to JsonPrimitive("Категория вопроса (опционально)")
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(emptyList())  // query теперь опциональный
                )
            )
        )
    )
    
    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        val query = arguments["query"]?.jsonPrimitive?.content?.trim()
        val category = arguments["category"]?.jsonPrimitive?.content

        logger.info("project_help called: query='$query', category=$category")

        // Проверяем доступность RAG
        if (ragService == null || ollamaEmbeddingClient == null) {
            logger.warn("RAG service not available, returning static overview")
            return generateOverview()
        }

        return try {
            // Режим 1: Общая справка (query пустой или отсутствует)
            // Используем широкий запрос для получения общей информации о проекте
            val searchQuery = if (query.isNullOrBlank()) {
                "KotlinAgent проект архитектура компоненты API MCP tools функциональность"
            } else {
                // Режим 2: Поиск по конкретной теме
                if (category != null) {
                    "$category: $query"
                } else {
                    query
                }
            }

            val queryEmbedding = ollamaEmbeddingClient.embed(searchQuery)
            val normalizedEmbedding = normalizeToRange(queryEmbedding)

            // Для общей справки берем больше результатов
            val topK = if (query.isNullOrBlank()) 10 else 5
            val minSimilarity = if (query.isNullOrBlank()) 0.2 else 0.3

            val results = ragService.search(
                queryEmbedding = normalizedEmbedding,
                topK = topK,
                minSimilarity = minSimilarity
            )

            if (results.isEmpty()) {
                logger.warn("No RAG results found, returning static overview")
                return if (query.isNullOrBlank()) {
                    generateOverview()
                } else {
                    """{"status": "no_results", "message": "Не найдено релевантной информации по запросу: $query"}"""
                }
            }

            val formattedContext = ragService.formatContext(results)

            // Добавляем информацию о доступных MCP tools
            val toolsInfo = buildToolsInfo()

            val response = buildString {
                append("""{"status": "success", """)
                append(""""mode": "${if (query.isNullOrBlank()) "overview" else "search"}", """)
                if (!query.isNullOrBlank()) {
                    append(""""query": "$query", """)
                }
                if (category != null) {
                    append(""""category": "$category", """)
                }
                append(""""results_count": ${results.size}, """)
                append(""""context": ${JsonPrimitive(formattedContext)}, """)
                append(""""tools_info": ${JsonPrimitive(toolsInfo)}, """)
                append(""""sources": [""")
                results.forEachIndexed { index, result ->
                    if (index > 0) append(", ")
                    append("""{"doc_id": "${result.docId}", "similarity": ${result.similarity}}""")
                }
                append("]}")
            }

            response
        } catch (e: Exception) {
            logger.error("Help search failed: ${e.message}", e)
            // В случае ошибки возвращаем статический обзор для общей справки
            if (query.isNullOrBlank()) {
                generateOverview()
            } else {
                errorJson("Help search failed: ${e.message}")
            }
        }
    }
    
    /**
     * Генерирует информацию о доступных MCP tools
     */
    private fun buildToolsInfo(): String {
        return buildString {
            appendLine("## 🛠️ Доступные MCP Tools")
            appendLine()

            // Локальные tools
            val localTools = localMcpProvider?.getAllTools() ?: emptyList()
            if (localTools.isNotEmpty()) {
                appendLine("### Local Tools (выполняются на сервере):")
                appendLine()
                localTools.forEach { tool ->
                    appendLine("#### `${tool.name}`")
                    appendLine(tool.ui_description)
                    appendLine()
                }
            }

            // Remote tools
            val remoteToolsList = remoteTools ?: emptyList()
            if (remoteToolsList.isNotEmpty()) {
                appendLine("### Remote Tools (выполняются на клиенте через WebSocket):")
                appendLine()
                remoteToolsList.forEach { tool ->
                    appendLine("#### `${tool.name}`")
                    appendLine(tool.description)
                    appendLine()
                }
            }
        }
    }

    /**
     * Генерирует статическую справку (fallback на случай недоступности RAG)
     */
    private fun generateOverview(): String {
        val overview = buildString {
            appendLine("# 📚 KotlinAgent - Справка по проекту")
            appendLine()
            appendLine("⚠️ RAG система недоступна. Показана базовая информация.")
            appendLine()
            appendLine("## 🏗️ Архитектура проекта")
            appendLine()
            appendLine("KotlinAgent - это AI-агент с поддержкой MCP (Model Context Protocol).")
            appendLine()
            appendLine("### Основные компоненты:")
            appendLine()
            appendLine("- **Backend**: Ktor server (Kotlin)")
            appendLine("- **Frontend**: Compose for Web (Kotlin/JS)")
            appendLine("- **RAG**: Ollama embeddings + SQLite vector search")
            appendLine("- **Database**: SQLite (история чатов, напоминания)")
            appendLine("- **WebSocket**: Двусторонняя связь с локальными агентами")
            appendLine()

            append(buildToolsInfo())

            appendLine("## 🌐 API Endpoints")
            appendLine()
            appendLine("### Chat API")
            appendLine("- `POST /api/chat` - Отправить сообщение")
            appendLine("- `GET /api/sessions` - Получить список сессий")
            appendLine("- `GET /api/sessions/{id}` - Получить историю сессии")
            appendLine("- `DELETE /api/sessions/{id}` - Удалить сессию")
            appendLine()
            appendLine("### Health & Tools")
            appendLine("- `GET /health` - Health check")
            appendLine("- `GET /api/tools` - Список доступных MCP tools")
            appendLine()
            appendLine("### RAG API")
            appendLine("- `POST /api/rag/index` - Индексировать документы")
            appendLine("- `POST /api/rag/search` - Поиск по документам")
            appendLine("- `GET /api/rag/stats` - Статистика RAG индекса")
            appendLine()
            appendLine("### Reminders")
            appendLine("- `GET /api/reminders` - Список напоминаний")
            appendLine("- `DELETE /api/reminders/{id}` - Удалить напоминание")
            appendLine()
            appendLine("### Metrics")
            appendLine("- `GET /api/metrics/tokens` - Статистика использования токенов")
            appendLine()
            appendLine("### WebSocket")
            appendLine("- `WS /ws` - Real-time обновления")
            appendLine("- `WS /mcp/local-agent` - Подключение локальных агентов")
            appendLine()

            appendLine("## 📖 Примеры использования")
            appendLine()
            appendLine("### Команды в чате:")
            appendLine()
            appendLine("- `/help` - Показать справку")
            appendLine("- `/help [вопрос]` - Поиск по документации")
            appendLine()
            appendLine("### Примеры запросов:")
            appendLine()
            appendLine("```")
            appendLine("Какая погода в Москве?")
            appendLine("Создай напоминание на завтра в 10:00")
            appendLine("Покажи последние коммиты в git")
            appendLine("Запусти Android эмулятор")
            appendLine("```")
            appendLine()

            appendLine("## 🔗 Полезные ссылки")
            appendLine()
            appendLine("- **Документация**: `/rag/rag_data/` - полная документация проекта")
            appendLine("- **Архитектура**: `ARCHITECTURE.md` - детальное описание архитектуры")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("💡 **Совет**: Используйте `/help [конкретный вопрос]` для поиска детальной информации")
        }

        return buildString {
            append("""{"status": "success", "mode": "overview_static", """)
            append(""""context": ${JsonPrimitive(overview)}}""")
        }
    }

    private fun normalizeToRange(vector: FloatArray): FloatArray {
        val min = vector.minOrNull() ?: 0f
        val max = vector.maxOrNull() ?: 1f
        val range = max - min

        return if (range > 0) {
            vector.map { (it - min) / range }.toFloatArray()
        } else {
            vector
        }
    }

    override fun errorJson(msg: String): String {
        return """{"status": "error", "message": "${msg.replace("\"", "\\\"")}"}"""
    }
}