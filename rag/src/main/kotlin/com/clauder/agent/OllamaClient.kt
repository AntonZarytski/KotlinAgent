package com.clauder.agent

import com.clauder.agent.models.OllamaEmbededResponse
import com.clauder.agent.models.OllamaEmbedingsRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class OllamaClient(private val baseUrl: String = "http://localhost:11434") : AutoCloseable {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true  // Важно! Сериализовать дефолтные значения
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }

        // Настройка таймаутов для Ollama (может работать медленно)
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000  // 120 секунд на весь запрос
            connectTimeoutMillis = 30_000   // 30 секунд на подключение
            socketTimeoutMillis = 120_000   // 120 секунд на чтение данных
        }
    }

    suspend fun embed(text: String): FloatArray {
        val requestBody = OllamaEmbedingsRequest(prompt = text)

        val httpResponse = client.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // Читаем тело ответа как текст и парсим JSON
        val rawBody = httpResponse.bodyAsText()
        val response = json.decodeFromString<OllamaEmbededResponse>(rawBody)

        return response.embedding.map { it.toFloat() }.toFloatArray()
    }

    override fun close() {
        client.close()
    }
}