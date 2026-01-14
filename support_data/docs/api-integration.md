# API Integration Guide - KotlinAgent

## Обзор

KotlinAgent предоставляет REST API для интеграции с внешними системами.

## Базовый URL

```
http://localhost:8000
```

Production:
```
https://your-domain.com
```

## Аутентификация

API использует ANTHROPIC_API_KEY, настроенный на сервере. 
Клиентская аутентификация не требуется для публичных endpoints.

## Основные Endpoints

### 1. Chat API

Отправка сообщений Claude AI.

```http
POST /api/chat
Content-Type: application/json

{
  "message": "Ваш вопрос",
  "session_id": "optional-session-id",
  "output_format": "default",
  "enabled_tools": ["get_weather_forecast", "support_crm"],
  "use_rag": true,
  "rag_top_k": 5,
  "rag_min_similarity": 0.7
}
```

**Response:**
```json
{
  "reply": "Ответ от Claude",
  "usage": {
    "input_tokens": 150,
    "output_tokens": 200
  }
}
```

### 2. Support API

Специализированный endpoint для вопросов поддержки.

```http
POST /support/ask
Content-Type: application/json

{
  "question": "Почему не работает авторизация?",
  "userId": "123",
  "ticketId": "T-001"
}
```

**Response:**
```json
{
  "answer": "Персонализированный ответ с учетом контекста",
  "sources": [
    {
      "docId": "authentication.md",
      "chunkIndex": 0,
      "text": "Фрагмент документации...",
      "similarity": 0.95
    }
  ],
  "userContext": {
    "id": "123",
    "name": "Иван Петров",
    "status": "ACTIVE"
  },
  "ticketContext": {
    "id": "T-001",
    "title": "Не работает авторизация",
    "status": "OPEN",
    "priority": "HIGH"
  }
}
```

## Webhooks

### Настройка Webhook

KotlinAgent может отправлять уведомления о событиях через webhooks.

**Конфигурация (application.conf):**
```hocon
webhooks {
  enabled = true
  endpoints = [
    {
      url = "https://your-app.com/webhook"
      events = ["ticket.created", "ticket.updated"]
      secret = "your-webhook-secret"
    }
  ]
}
```

### Формат Webhook Payload

```json
{
  "event": "ticket.created",
  "timestamp": "2024-12-21T10:00:00Z",
  "data": {
    "ticket": {
      "id": "T-006",
      "userId": "123",
      "title": "Новый тикет",
      "status": "OPEN"
    }
  },
  "signature": "sha256=..."
}
```

### Проверка подписи

```kotlin
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

fun verifyWebhookSignature(payload: String, signature: String, secret: String): Boolean {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    val expectedSignature = "sha256=" + Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    return signature == expectedSignature
}
```

## Примеры интеграции

### Kotlin

```kotlin
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

suspend fun askSupport(question: String, userId: String? = null): String {
    val client = HttpClient()
    
    val response = client.post("http://localhost:8000/support/ask") {
        contentType(ContentType.Application.Json)
        setBody(buildJsonObject {
            put("question", question)
            userId?.let { put("userId", it) }
        })
    }
    
    val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    return json["answer"]?.jsonPrimitive?.content ?: "No answer"
}
```

### JavaScript/TypeScript

```typescript
async function askSupport(question: string, userId?: string): Promise<string> {
  const response = await fetch('http://localhost:8000/support/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      question,
      userId,
    }),
  });
  
  const data = await response.json();
  return data.answer;
}
```

### Python

```python
import requests

def ask_support(question: str, user_id: str = None) -> str:
    response = requests.post(
        'http://localhost:8000/support/ask',
        json={
            'question': question,
            'userId': user_id,
        }
    )
    return response.json()['answer']
```

## Rate Limiting

API использует rate limiting для защиты от злоупотреблений:

- **Chat API**: 60 запросов/минуту на IP
- **Support API**: 30 запросов/минуту на IP

**Response при превышении лимита:**
```json
{
  "error": "Rate limit exceeded",
  "retry_after": 60
}
```

## Обработка ошибок

### Коды ошибок

- `400 Bad Request` - Неверные параметры запроса
- `429 Too Many Requests` - Превышен rate limit
- `500 Internal Server Error` - Ошибка сервера
- `503 Service Unavailable` - Сервис временно недоступен

### Пример обработки

```kotlin
try {
    val response = client.post("http://localhost:8000/support/ask") {
        // ...
    }
    
    when (response.status) {
        HttpStatusCode.OK -> {
            // Success
        }
        HttpStatusCode.TooManyRequests -> {
            val retryAfter = response.headers["Retry-After"]?.toInt() ?: 60
            delay(retryAfter * 1000L)
            // Retry
        }
        else -> {
            // Handle error
        }
    }
} catch (e: Exception) {
    // Network error
}
```

## Дополнительные ресурсы

- [Полная документация API](https://docs.example.com/api)
- [Примеры кода](https://github.com/your-repo/examples)
- [Swagger UI](http://localhost:8000/swagger)

