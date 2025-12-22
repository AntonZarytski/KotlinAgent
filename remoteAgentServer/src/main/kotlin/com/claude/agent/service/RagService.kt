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
        minSimilarity: Double = 0.0
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
        
        val context = buildString {
            appendLine("# Relevant Documentation Context")
            appendLine()
            results.forEachIndexed { index, result ->
                appendLine("## Document ${index + 1}: ${result.docId} (similarity: ${"%.3f".format(result.similarity)})")
                appendLine()
                appendLine(result.text.trim())
                appendLine()
                appendLine("---")
                appendLine()
            }
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

