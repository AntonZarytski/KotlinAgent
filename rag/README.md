# RAG (Retrieval-Augmented Generation) Module

This module implements a complete RAG system for document indexing and retrieval using Ollama embeddings and SQLite storage.

## Features

✅ **Document Processing Pipeline**
- Loads documents from a directory (supports .md, .txt, .kt, .java files)
- Chunks text with configurable size and overlap
- Generates embeddings using Ollama
- Stores vectors in SQLite database

✅ **Smart Text Chunking**
- Chunk size: 800 tokens (~3200 characters)
- Overlap: 100 tokens (~400 characters)
- Ensures proper context preservation between chunks

✅ **Ollama Integration**
- Uses `nomic-embed-text` model for embeddings
- Async/coroutine-based API calls
- Proper error handling and resource management

✅ **Vector Storage**
- SQLite database with BLOB storage for vectors
- Efficient indexing by document ID
- Metadata storage (chunk position, source file, timestamp)

✅ **Vector Normalization**
- Min-max normalization to [0, 1] range
- Ensures consistent similarity calculations

## Prerequisites

1. **Ollama** must be installed and running:
   ```bash
   # Install Ollama (macOS)
   brew install ollama
   
   # Start Ollama service
   ollama serve
   ```

2. **Pull the embedding model**:
   ```bash
   ollama pull nomic-embed-text
   ```

3. **Verify Ollama is running**:
   ```bash
   curl http://localhost:11434/api/version
   ```

## Usage

### Build the module

```bash
./gradlew :rag:build
```

### Run the index builder

Basic usage (uses default database path and Ollama URL):
```bash
./gradlew :rag:run --args="./test_docs"
```

With custom database path:
```bash
./gradlew :rag:run --args="./test_docs my_index.db"
```

With custom Ollama URL:
```bash
./gradlew :rag:run --args="./test_docs my_index.db http://localhost:11434"
```

### Example Output

```
🚀 RAG Index Builder
==================================================
📂 Documents directory: /path/to/test_docs
💾 Database path: rag_index.db
🤖 Ollama URL: http://localhost:11434

✅ Database schema initialized
📚 Found 3 documents

📄 [1/3] Processing: /path/to/test_docs/README.md
   📝 Text length: 1234 characters
   ✂️  Created 2 chunks
   🔹 Chunk 0/1 - Generating embedding... ✓
   🔹 Chunk 1/1 - Generating embedding... ✓
   ✅ Document processed

...

==================================================
✅ Index built successfully!
📊 Total chunks indexed: 8
💾 Database: rag_index.db
```

## Architecture

### Components

1. **DocumentReader** (`DocumentReader.kt`)
   - Recursively scans directory for supported files
   - Reads file content into memory

2. **Text Chunker** (`TextChunk.kt`)
   - Splits text into overlapping chunks
   - Approximates token count (1 token ≈ 4 characters)
   - Creates `TextChunk` objects with metadata

3. **OllamaClient** (`OllamaClient.kt`)
   - HTTP client for Ollama API
   - Sends text to `/api/embeddings` endpoint
   - Returns float array of embedding vectors

4. **DataBase** (`DataBase.kt`)
   - SQLite connection management
   - Schema initialization
   - Vector storage and retrieval
   - Float array ↔ byte array conversion

5. **Main** (`Main.kt`)
   - Orchestrates the entire pipeline
   - Command-line argument parsing
   - Progress reporting

### Database Schema

```sql
CREATE TABLE document_chunks (
    id TEXT PRIMARY KEY,              -- Format: "docId:chunkIndex"
    doc_id TEXT NOT NULL,             -- Source document path
    chunk_index INTEGER NOT NULL,     -- Chunk position in document
    text TEXT NOT NULL,               -- Chunk text content
    vector BLOB NOT NULL,             -- Normalized embedding vector
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_doc_id ON document_chunks(doc_id);
```

## Testing

Test documents are provided in the `test_docs/` directory:
- `README.md` - RAG system documentation
- `kotlin_basics.md` - Kotlin programming guide
- `example.kt` - Sample Kotlin code

## Troubleshooting

### "Connection refused" error
- Make sure Ollama is running: `ollama serve`
- Check if Ollama is listening on port 11434: `lsof -i :11434`

### "Model not found" error
- Pull the model: `ollama pull nomic-embed-text`
- List available models: `ollama list`

### Build errors
- Clean and rebuild: `./gradlew :rag:clean :rag:build`
- Check Kotlin version: Should be 2.2.20
- Check Java version: Should be 21

## Future Enhancements

- [ ] PDF document support
- [ ] Vector similarity search
- [ ] Query interface for retrieval
- [ ] Batch processing for large document sets
- [ ] Progress bar for long-running operations
- [ ] Support for other embedding models
- [ ] Vector database alternatives (ChromaDB, Pinecone, etc.)

