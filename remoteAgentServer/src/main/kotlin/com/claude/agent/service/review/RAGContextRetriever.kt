package com.claude.agent.service.review

import com.claude.agent.service.OllamaEmbeddingClient
import com.claude.agent.service.RagService
import org.slf4j.LoggerFactory

/**
 * Получатель релевантного контекста из RAG системы
 * 
 * Отвечает за поиск и форматирование релевантной документации для ревью
 */
class RAGContextRetriever(
    private val ragService: RagService?,
    private val ollamaEmbeddingClient: OllamaEmbeddingClient?
) {
    private val logger = LoggerFactory.getLogger(RAGContextRetriever::class.java)
    
    companion object {
        private const val MAX_FILES_FOR_QUERY = 10
        private const val MAX_COMMITS_FOR_QUERY = 5
        private const val TOP_K_RESULTS = 5
        private const val MIN_SIMILARITY = 0.7
    }

    /**
     * Получает релевантный контекст из RAG системы
     * 
     * @return Отформатированный контекст или null если RAG недоступен или не нашел результатов
     */
    suspend fun retrieve(prData: PRData, sessionId: String?): String? {
        if (ragService == null || ollamaEmbeddingClient == null) {
            logger.debug("RAG service not available")
            return null
        }

        logger.info("🔍 Retrieving relevant context from RAG...")

        return try {
            val query = buildQuery(prData)
            val queryEmbedding = generateEmbedding(query)
            val results = searchDocuments(queryEmbedding)
            
            if (results.isEmpty()) {
                logger.info("No relevant documentation found")
                return null
            }

            logger.info("Found ${results.size} relevant documentation chunks")
            ragService.formatContext(results)

        } catch (e: Exception) {
            logger.error("Failed to retrieve RAG context: ${e.message}", e)
            null
        }
    }

    /**
     * Формирует запрос на основе измененных файлов и коммитов
     */
    private fun buildQuery(prData: PRData): String {
        return buildString {
            appendLine("Code review context:")
            appendLine("Changed files: ${prData.files.take(MAX_FILES_FOR_QUERY).joinToString(", ") { it.path }}")
            appendLine("Commit messages: ${prData.commits.take(MAX_COMMITS_FOR_QUERY).joinToString(" | ") { it.message }}")
        }
    }

    /**
     * Генерирует embedding для запроса
     */
    private suspend fun generateEmbedding(query: String): FloatArray {
        val queryEmbedding = ollamaEmbeddingClient!!.embed(query)
        return com.claude.agent.common.database.normalizeToRange(queryEmbedding)
    }

    /**
     * Ищет релевантные документы
     */
    private suspend fun searchDocuments(queryEmbedding: FloatArray): List<com.claude.agent.service.RagService.SearchResult> {
        return ragService!!.search(
            queryEmbedding = queryEmbedding,
            topK = TOP_K_RESULTS,
            minSimilarity = MIN_SIMILARITY
        )
    }
}

