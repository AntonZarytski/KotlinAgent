package com.claude.agent.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Клиент для генерации embedding'ов через Ollama API
 * 
 * Используется для преобразования текстовых запросов в векторы
 * для семантического поиска в RAG системе.
 */
class OllamaEmbeddingClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:11434"
) {
    private val logger = LoggerFactory.getLogger(OllamaEmbeddingClient::class.java)
    
    @Serializable
    data class EmbeddingRequest(
        val model: String = "nomic-embed-text",
        val prompt: String  // Use 'prompt' for /api/embeddings endpoint
    )

    @Serializable
    data class EmbeddingResponse(
        val embedding: List<Double>  // Single embedding array
    )
    
    /**
     * Генерация embedding вектора для текста
     *
     * @param text Текст для преобразования в вектор
     * @return Вектор embedding'а (normalized)
     */
    suspend fun embed(text: String): FloatArray {
        return try {
            val response = httpClient.post("$baseUrl/api/embeddings") {  // Use /api/embeddings endpoint
                contentType(ContentType.Application.Json)
                setBody(EmbeddingRequest(prompt = text))  // Use 'prompt' field
            }.body<EmbeddingResponse>()

            // Convert embedding to FloatArray
            response.embedding.map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            logger.error("Failed to generate embedding: ${e.message}", e)
            throw RuntimeException("Ollama embedding generation failed: ${e.message}", e)
        }
    }
    
    /**
     * Проверка доступности Ollama сервера
     */
    suspend fun isAvailable(): Boolean {
        return try {
            httpClient.get("$baseUrl/api/tags")
            true
        } catch (e: Exception) {
            logger.warn("Ollama server not available at $baseUrl: ${e.message}")
            false
        }
    }
}

