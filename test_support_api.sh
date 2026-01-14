#!/bin/bash

# Тестовый скрипт для проверки Support API

BASE_URL="http://localhost:8000"

echo "=== Testing Support API ==="
echo ""

# 1. Проверка health endpoint
echo "1. Health check..."
curl -s "$BASE_URL/health" | jq '.'
echo ""

# 2. Тест support/ask без контекста
echo "2. Testing /support/ask without context..."
curl -s -X POST "$BASE_URL/support/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Как настроить API ключ?",
    "useRag": true,
    "ragTopK": 3,
    "ragMinSimilarity": 0.5
  }' | jq '.'
echo ""

# 3. Тест support/ask с userId
echo "3. Testing /support/ask with userId..."
curl -s -X POST "$BASE_URL/support/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Почему не работает авторизация?",
    "userId": "user_001",
    "useRag": true,
    "ragTopK": 5
  }' | jq '.'
echo ""

# 4. Тест support/ask с ticketId
echo "4. Testing /support/ask with ticketId..."
curl -s -X POST "$BASE_URL/support/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Какой статус моего тикета?",
    "userId": "user_001",
    "ticketId": "T-001",
    "useRag": false
  }' | jq '.'
echo ""

# 5. Тест получения информации о пользователе
echo "5. Testing /support/user/{userId}..."
curl -s "$BASE_URL/support/user/user_001" | jq '.'
echo ""

# 6. Тест получения информации о тикете
echo "6. Testing /support/ticket/{ticketId}..."
curl -s "$BASE_URL/support/ticket/T-001" | jq '.'
echo ""

# 7. Тест обновления статуса тикета
echo "7. Testing /support/ticket/update..."
curl -s -X POST "$BASE_URL/support/ticket/update" \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": "T-001",
    "status": "IN_PROGRESS"
  }' | jq '.'
echo ""

echo "=== Tests completed ==="

