package com.clauder.agent

import com.clauder.agent.models.TextChunk

/**
 * Text chunking utilities for RAG module.
 *
 * Splits documents into overlapping chunks for embedding generation.
 */

/**
 * Split text into overlapping chunks
 *
 * @param docId Document identifier
 * @param text Text to chunk
 * @param chunkTokens Approximate number of tokens per chunk (default: 800)
 * @param overlapTokens Approximate number of overlapping tokens (default: 100)
 * @return List of TextChunk objects
 */
fun chunkText(
    docId: String,
    text: String,
    chunkTokens: Int = 800,
    overlapTokens: Int = 100
): List<TextChunk> {
    // Приблизительная оценка: 1 токен ≈ 4 символа
    val approxTokenSize = 4
    val chunkSizeChars = chunkTokens * approxTokenSize
    val overlapChars = overlapTokens * approxTokenSize

    val chunks = mutableListOf<TextChunk>()
    var start = 0
    var index = 0

    val textLength = text.length

    while (start < textLength) {
        // Вычисляем конец чанка
        val end = minOf(start + chunkSizeChars, textLength)

        // Создаем чанк (substring создает копию, но это неизбежно)
        val chunkText = text.substring(start, end).trim()

        // Пропускаем пустые чанки
        if (chunkText.isNotEmpty()) {
            chunks.add(TextChunk(
                docId = docId,
                index = index++,
                text = chunkText
            ))
        }

        // Сдвигаем начало с учетом overlap
        start = end - overlapChars

        // Если overlap больше чем осталось текста, выходим
        if (start >= textLength - overlapChars) {
            break
        }
    }

    return chunks
}