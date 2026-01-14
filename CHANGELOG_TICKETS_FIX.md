# Исправления системы тикетов и чата

## Дата: 14.01.2026

### Проблема 1: Кнопки управления тикетами не работали

**Симптомы:**
- При нажатии на кнопки "Решено", "В работу", "Закрыть" ничего не происходило
- В логах сервера ошибка: `Field 'ticketId' is required for type 'UpdateTicketRequest', but it was missing`

**Причина:**
Несоответствие моделей данных между сервером и UI:
- **Сервер** ожидал `ticketId` в теле запроса `UpdateTicketRequest`
- **UI** передавал `ticketId` только в URL (`/api/tickets/{ticketId}`)
- REST API стандарт: ID ресурса передается в URL, а не в теле

**Решение:**
1. Убрали поле `ticketId` из `UpdateTicketRequest` (серверная модель)
2. Создали отдельную модель `McpUpdateTicketRequest` для MCP endpoint, где `ticketId` нужен в теле
3. Обновили endpoint `/support/ticket/update` для использования новой модели
4. Реализовали реальное обновление тикетов через `TicketRepository`

**Измененные файлы:**
- `remoteAgentServer/src/main/kotlin/com/claude/agent/models/SupportModels.kt`
- `remoteAgentServer/src/main/kotlin/com/claude/agent/routes/SupportRoutes.kt`

### Проблема 2: Чат не прокручивался автоматически

**Симптомы:**
- Новые сообщения не были видны без ручной прокрутки
- Приходилось вручную скроллить вниз после каждого ответа

**Причина:**
- Прокрутка выполнялась слишком рано, до завершения рендеринга DOM
- Отсутствовала задержка для применения изменений

**Решение:**
Добавили `setTimeout` с задержкой 50ms в `LaunchedEffect` для автоматической прокрутки:
```kotlin
LaunchedEffect(messages.size, isLoading, streamingText) {
    kotlinx.browser.window.setTimeout({
        document.getElementById("chat")?.let { chat ->
            chat.scrollTop = chat.scrollHeight.toDouble()
        }
    }, 50) // Небольшая задержка для рендеринга
}
```

**Измененные файлы:**
- `compose-ui/src/jsMain/kotlin/com/claude/agent/ui/Components.kt`

## Тестирование

### Проверка кнопок тикетов:
1. Открыть панель тикетов (🎫)
2. Создать или выбрать существующий тикет
3. Нажать кнопки:
   - ✅ Решено → статус должен измениться на RESOLVED
   - 🔄 В работу → статус должен измениться на IN_PROGRESS
   - 🔒 Закрыть → статус должен измениться на CLOSED
4. Проверить, что статус обновляется в UI и в базе данных

### Проверка прокрутки чата:
1. Отправить несколько сообщений
2. Убедиться, что чат автоматически прокручивается к последнему сообщению
3. Проверить прокрутку при:
   - Получении нового сообщения от AI
   - Отображении промежуточных сообщений (streaming)
   - Загрузке истории чата

## API Changes

### REST API (без изменений в контракте)
```
PATCH /api/tickets/{ticketId}
Body: {
  "status": "RESOLVED",  // OPEN, IN_PROGRESS, WAITING_FOR_USER, RESOLVED, CLOSED
  "priority": "HIGH",    // LOW, MEDIUM, HIGH, CRITICAL (опционально)
  ...
}
```

### MCP API (новая модель)
```
POST /support/ticket/update
Body: {
  "ticketId": "T-001",   // Теперь обязательно в теле
  "status": "RESOLVED",
  ...
}
```

## Совместимость

- ✅ Обратная совместимость с UI сохранена
- ✅ REST API контракт не изменился
- ✅ MCP endpoint теперь работает корректно
- ✅ Существующие тикеты не затронуты

