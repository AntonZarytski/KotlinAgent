# RAG System Documentation

## Overview

This is a Retrieval-Augmented Generation (RAG) system built in Kotlin. The system processes documents, generates embeddings using Ollama, and stores them in a SQLite database for efficient retrieval.

## Features

- **Document Processing**: Supports multiple file formats including Markdown (.md), text files (.txt), Kotlin (.kt), and Java (.java) files
- **Smart Chunking**: Automatically splits documents into chunks of 500-1000 tokens with 50-100 token overlap
- **Embedding Generation**: Uses Ollama's nomic-embed-text model to generate high-quality embeddings
- **Vector Storage**: Stores embeddings in SQLite database with efficient indexing
- **Normalization**: Normalizes all vectors to [0, 1] range for consistent similarity calculations

## Architecture

The system consists of several key components:

1. **DocumentReader**: Loads and reads documents from the file system
2. **TextChunker**: Splits documents into overlapping chunks
3. **OllamaClient**: Communicates with Ollama API to generate embeddings
4. **DataBase**: Manages SQLite database operations and vector storage

## Usage

To build an index from your documents:

```bash
./gradlew :rag:run --args="./docs"
```

With custom database path and Ollama URL:

```bash
./gradlew :rag:run --args="./docs rag_index.db http://localhost:11434"
```

## Technical Details

- Chunk size: 800 tokens (approximately 3200 characters)
- Overlap: 100 tokens (approximately 400 characters)
- Embedding model: nomic-embed-text
- Database: SQLite with BLOB storage for vectors
- Vector normalization: Min-max normalization to [0, 1] range

