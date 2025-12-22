package com.clauder.agent.models

data class TextChunk(
    val docId: String,
    val index: Int,
    val text: String
)