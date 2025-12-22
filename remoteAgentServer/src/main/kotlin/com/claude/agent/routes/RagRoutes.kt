package com.claude.agent.routes

import com.claude.agent.service.OllamaEmbeddingClient
import com.claude.agent.service.RagService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * API endpoints для работы с RAG (Retrieval-Augmented Generation)
 */
fun Route.ragRoutes(
    ragService: RagService?,
    ollamaEmbeddingClient: OllamaEmbeddingClient?
) {
    val logger = LoggerFactory.getLogger("RagRoutes")
    
    @Serializable
    data class RagSearchRequest(
        val query: String,
        val top_k: Int = 5,
        val min_similarity: Double = 0.0
    )
    
    @Serializable
    data class RagSearchResult(
        val doc_id: String,
        val chunk_index: Int,
        val text: String,
        val similarity: Double
    )
    
    @Serializable
    data class RagSearchResponse(
        val results: List<RagSearchResult>,
        val formatted_context: String
    )
    
    @Serializable
    data class RagStatsResponse(
        val total_chunks: Long,
        val unique_documents: Long,
        val database_path: String,
        val ollama_available: Boolean
    )
    
    @Serializable
    data class ErrorResponse(
        val error: String
    )
    
    /**
     * POST /api/rag/search - поиск релевантных документов
     */
    post("/api/rag/search") {
        if (ragService == null || ollamaEmbeddingClient == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("RAG service not available"))
            return@post
        }
        
        try {
            val request = call.receive<RagSearchRequest>()
            
            if (request.query.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query cannot be empty"))
                return@post
            }
            
            logger.info("RAG search request: query='${request.query.take(50)}...', top_k=${request.top_k}")
            
            // Генерируем embedding для запроса
            val queryEmbedding = ollamaEmbeddingClient.embed(request.query)
            
            // Ищем релевантные чанки
            val results = ragService.search(
                queryEmbedding = queryEmbedding,
                topK = request.top_k,
                minSimilarity = request.min_similarity
            )
            
            // Форматируем контекст
            val formattedContext = ragService.formatContext(results)
            
            val response = RagSearchResponse(
                results = results.map { 
                    RagSearchResult(
                        doc_id = it.docId,
                        chunk_index = it.chunkIndex,
                        text = it.text,
                        similarity = it.similarity
                    )
                },
                formatted_context = formattedContext
            )
            
            logger.info("Found ${results.size} results")
            call.respond(HttpStatusCode.OK, response)
            
        } catch (e: Exception) {
            logger.error("RAG search failed: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Search failed: ${e.message}"))
        }
    }
    
    /**
     * GET /api/rag/stats - статистика RAG базы данных
     */
    get("/api/rag/stats") {
        if (ragService == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("RAG service not available"))
            return@get
        }
        
        try {
            val stats = ragService.getStats()
            val ollamaAvailable = ollamaEmbeddingClient?.isAvailable() ?: false
            
            val response = RagStatsResponse(
                total_chunks = stats["total_chunks"] as Long,
                unique_documents = stats["unique_documents"] as Long,
                database_path = stats["database_path"] as String,
                ollama_available = ollamaAvailable
            )
            
            call.respond(HttpStatusCode.OK, response)
            
        } catch (e: Exception) {
            logger.error("Failed to get RAG stats: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to get stats: ${e.message}"))
        }
    }
}

