package com.clauder.agent

import com.claude.agent.common.database.DocumentChunks
import com.claude.agent.common.database.toByteArray
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Database operations for RAG module using Exposed ORM.
 *
 * This class provides methods to:
 * - Initialize the database connection and schema
 * - Insert document chunk embeddings
 * - Query chunk statistics
 */
class DataBase {

    /**
     * Initialize database connection to SQLite
     */
    fun initDatabase(dbPath: String = "rag_index.db") {
        val jdbcUrl = "jdbc:sqlite:$dbPath"

        Database.connect(
            url = jdbcUrl,
            driver = "org.sqlite.JDBC"
        )

        println("✅ Database connected: $dbPath")
    }

    /**
     * Initialize database schema using Exposed
     */
    fun initSchema() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(DocumentChunks)
        }

        println("✅ Database schema initialized")
    }

    /**
     * Insert embedding into database using Exposed DSL
     */
    fun insertEmbedding(
        docId: String,
        chunkIndex: Int,
        text: String,
        vector: FloatArray
    ) {
        val id = "$docId:$chunkIndex"
        val now = Instant.now().toString()

        transaction {
            DocumentChunks.replace {
                it[DocumentChunks.id] = id
                it[DocumentChunks.docId] = docId
                it[DocumentChunks.chunkIndex] = chunkIndex
                it[DocumentChunks.text] = text
                it[DocumentChunks.vector] = ExposedBlob(vector.toByteArray())
                it[DocumentChunks.createdAt] = now
            }
        }
    }

    /**
     * Get total count of chunks in database
     */
    fun getChunkCount(): Int {
        return transaction {
            DocumentChunks.selectAll().count().toInt()
        }
    }

    /**
     * Get all chunks for a specific document
     */
    fun getDocumentChunks(docId: String): List<ResultRow> {
        return transaction {
            DocumentChunks.selectAll()
                .where { DocumentChunks.docId eq docId }
                .orderBy(DocumentChunks.chunkIndex to SortOrder.ASC)
                .toList()
        }
    }

    /**
     * Delete all chunks for a specific document
     */
    fun deleteDocument(docId: String): Int {
        return transaction {
            DocumentChunks.deleteWhere { DocumentChunks.docId eq docId }
        }
    }

    /**
     * Get all unique document IDs
     */
    fun getAllDocumentIds(): List<String> {
        return transaction {
            DocumentChunks
                .selectAll()
                .withDistinct()
                .map { it[DocumentChunks.docId] }
        }
    }
}


