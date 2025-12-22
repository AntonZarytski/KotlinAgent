package com.clauder.agent.models

import kotlinx.serialization.Serializable

@Serializable
data class OllamaEmbedingsRequest(
    val prompt: String,
    val model: String = "nomic-embed-text"
)

@Serializable
data class OllamaEmbededResponse(
    val embedding: List<Double>
)


