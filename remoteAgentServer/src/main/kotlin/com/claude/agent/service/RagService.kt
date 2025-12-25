package com.claude.agent.service

import com.claude.agent.common.database.DocumentChunks
import com.claude.agent.common.database.toFloatArray
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * RAG (Retrieval-Augmented Generation) Service
 *
 * Выполняет семантический поиск по векторным embedding'ам документов
 * для предоставления релевантного контекста Claude API.
 */
class RagService(
    private val ragDatabasePath: String = "rag_index.db"
) {
    private val logger = LoggerFactory.getLogger(RagService::class.java)

    // Отдельное подключение к RAG базе данных
    private val ragDatabase: Database by lazy {
        val jdbcUrl = "jdbc:sqlite:$ragDatabasePath"
        logger.info("Подключение к RAG базе данных: $jdbcUrl")
        val db = Database.connect(
            url = jdbcUrl,
            driver = "org.sqlite.JDBC"
        )

        // Инициализация схемы базы данных (создание таблицы если не существует)
        try {
            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(DocumentChunks)
                logger.info("✅ RAG database schema initialized successfully")
            }
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize RAG database schema: ${e.message}", e)
            throw e
        }

        db
    }
    
    data class SearchResult(
        val docId: String,
        val chunkIndex: Int,
        val text: String,
        val similarity: Double
    )
    
    /**
     * Поиск наиболее релевантных чанков по векторному сходству
     *
     * @param queryEmbedding Вектор запроса (normalized)
     * @param topK Количество результатов
     * @param minSimilarity Минимальный порог сходства (0.0 - 1.0)
     * @return Список найденных чанков, отсортированных по релевантности
     */
    fun search(
        queryEmbedding: FloatArray,
        topK: Int = 5,
        minSimilarity: Double = 0.9
    ): List<SearchResult> {
        // ВАЖНО: Используем явное подключение к RAG базе данных
        return transaction(ragDatabase) {
            val results = mutableListOf<SearchResult>()

            // Получаем все чанки из базы
            DocumentChunks.selectAll().forEach { row ->
                val docId = row[DocumentChunks.docId]
                val chunkIndex = row[DocumentChunks.chunkIndex]
                val text = row[DocumentChunks.text]
                val vectorBytes = row[DocumentChunks.vector].bytes
                val chunkVector = vectorBytes.toFloatArray()

                // Вычисляем косинусное сходство
                val similarity = cosineSimilarity(queryEmbedding, chunkVector)

                if (similarity >= minSimilarity) {
                    results.add(SearchResult(
                        docId = docId,
                        chunkIndex = chunkIndex,
                        text = text,
                        similarity = similarity
                    ))
                }
            }

            // Сортируем по убыванию сходства и берем top-K
            results.sortedByDescending { it.similarity }.take(topK)
        }
    }
    
    /**
     * Вычисление косинусного сходства между двумя векторами
     * 
     * cosine_similarity = (A · B) / (||A|| * ||B||)
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectors must have the same dimension" }
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        normA = sqrt(normA)
        normB = sqrt(normB)
        
        return if (normA > 0 && normB > 0) {
            dotProduct / (normA * normB)
        } else {
            0.0
        }
    }
    
    /**
     * Форматирование результатов поиска в текстовый контекст для Claude
     */
    fun formatContext(results: List<SearchResult>): String {
        if (results.isEmpty()) {
            return ""
        }

        // Извлекаем уникальные источники для итоговой справки
        val sources = results.map { result ->
            // Извлекаем имя файла из пути для более читаемого отображения
            val fileName = result.docId.substringAfterLast('/')
            "$fileName (${result.docId})"
        }.distinct()

        val context = buildString {
            appendLine("# 📚 Контекст из документации")
            appendLine()
            appendLine("Найдено ${results.size} релевантных фрагментов из ${sources.size} документов.")
            appendLine()

            results.forEachIndexed { index, result ->
                val fileName = result.docId.substringAfterLast('/')
                val similarityPercent = (result.similarity * 100).toInt()

                appendLine("## 📄 Фрагмент ${index + 1}: $fileName")
                appendLine("**Источник:** `${result.docId}`")
                appendLine("**Релевантность:** $similarityPercent% (${String.format("%.3f", result.similarity)})")
                appendLine()
                appendLine(result.text.trim())
                appendLine()
                appendLine("---")
                appendLine()
            }

            // Добавляем итоговый список источников для удобства Claude
            appendLine("## 📑 Список всех источников:")
            sources.forEachIndexed { index, source ->
                appendLine("${index + 1}. $source")
            }
            appendLine()
            appendLine("**ВАЖНО:** Используйте эти источники в своем ответе!")
        }

        return context
    }
    
    /**
     * Получение статистики по базе RAG
     */
    fun getStats(): Map<String, Any> {
        // ВАЖНО: Используем явное подключение к RAG базе данных
        return transaction(ragDatabase) {
            val totalChunks: Long = DocumentChunks.selectAll().count()
            val uniqueDocs: Long = DocumentChunks.selectAll()
                .map { it[DocumentChunks.docId] }
                .distinct()
                .count()
                .toLong()

            mapOf(
                "total_chunks" to totalChunks,
                "unique_documents" to uniqueDocs,
                "database_path" to ragDatabasePath
            )
        }
    }
}

