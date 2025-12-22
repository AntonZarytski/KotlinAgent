package com.claude.agent.common.database

import org.jetbrains.exposed.sql.Table
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared database table definitions for RAG (Retrieval-Augmented Generation) module.
 *
 * This table can be accessed by both:
 * - :rag module for indexing documents
 * - :remoteAgentServer module for querying embeddings
 */
object DocumentChunks : Table("document_chunks") {
    val id = varchar("id", 500)                     // PRIMARY KEY: "docId:chunkIndex"
    val docId = varchar("doc_id", 500)              // Source document path
    val chunkIndex = integer("chunk_index")         // Chunk position in document
    val text = text("text")                         // Chunk text content
    val vector = blob("vector")                     // Normalized embedding vector (BLOB)
    val createdAt = varchar("created_at", 50)       // ISO 8601 timestamp

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, docId)  // Index for faster queries by document
    }
}

/**
 * Utility functions for converting between FloatArray and ByteArray for BLOB storage
 */

/**
 * Convert FloatArray to ByteArray for BLOB storage in SQLite
 */
fun FloatArray.toByteArray(): ByteArray =
    ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
        forEach { putFloat(it) }
    }.array()

/**
 * Convert ByteArray from BLOB storage back to FloatArray
 */
fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(size / 4) { buffer.getFloat() }
}

/**
 * Normalize vector to [0, 1] range using min-max normalization
 */
fun normalizeToRange(vector: FloatArray): FloatArray {
    if (vector.isEmpty()) return vector
    
    val min = vector.minOrNull() ?: return vector
    val max = vector.maxOrNull() ?: return vector
    
    // If all values are the same, return array of 0.5
    if (min == max) return FloatArray(vector.size) { 0.5f }
    
    // Min-max normalization to [0, 1]
    return vector.map { (it - min) / (max - min) }.toFloatArray()
}

