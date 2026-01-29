# 🎨 Персонализация агента

Система персонализации позволяет адаптировать поведение агента под ваши предпочтения, привычки и рабочий контекст.

## 📋 Быстрый старт

1. **Скопируйте пример профиля:**
   ```bash
   cp agent-profile.example.json agent-profile.json
   ```

2. **Отредактируйте профиль** под себя в файле `agent-profile.json`

3. **Перезапустите агент** - профиль загрузится автоматически

## 🔧 Структура профиля

### Базовая информация (`user`)

```json
{
  "user": {
    "name": "Антон",                    // Ваше имя
    "role": "Senior Android Developer", // Ваша роль/должность
    "timezone": "Europe/Moscow",        // Часовой пояс
    "language": "ru"                    // Предпочитаемый язык
  }
}
```

### Предпочтения (`preferences`)

```json
{
  "preferences": {
    "communicationStyle": "direct",     // Стиль общения: "direct", "friendly", "formal"
    "codeStyle": "kotlin_official",     // Стиль кода: "kotlin_official", "google", "custom"
    "verbosity": "medium",              // Детализация: "brief", "medium", "detailed"
    "emojiUsage": true,                 // Использовать эмодзи
    "codeExampleFormat": "separate",    // Формат примеров: "inline", "separate"
    "explainCode": true                 // Объяснять код
  }
}
```

### Рабочий контекст (`workContext`)

```json
{
  "workContext": {
    "primaryProjects": ["KotlinAgent", "SecretChat"],
    "techStack": ["Kotlin", "Compose", "Ktor", "Android"],
    "favoriteTools": ["Android Studio", "Git", "Gradle"],
    "currentFocus": "Разработка AI агента",
    "expertiseLevel": "advanced"        // "beginner", "intermediate", "advanced", "expert"
  }
}
```

### Привычки (`habits`)

```json
{
  "habits": {
    "workHours": "10:00-19:00",         // Рабочие часы
    "breakReminders": true,             // Напоминания о перерывах
    "taskManagementStyle": "agile",     // Стиль управления: "agile", "waterfall", "kanban"
    "preferredSessionLength": 90,       // Длина сессии (минуты)
    "peakProductivityTime": "morning"   // Пик продуктивности: "morning", "afternoon", "evening"
  }
}
```

### Кастомные инструкции (`customInstructions`)

```json
{
  "customInstructions": [
    "Всегда отвечай на русском языке",
    "Предпочитаю краткие ответы с примерами кода",
    "Использую Kotlin Coroutines вместо RxJava"
  ]
}
```

## 🌍 Использование

### Локальный агент

Профиль загружается автоматически при запуске из корня проекта:
```bash
./gradlew :localAgentClient:run
```

### Облачный агент (VPS)

1. Загрузите профиль на сервер:
   ```bash
   scp agent-profile.json user@your-vps:/home/agent/KotlinAgent/
   ```

2. Или используйте переменную окружения:
   ```bash
   export AGENT_PROFILE_PATH=/path/to/agent-profile.json
   ```

## 🔒 Безопасность

- ✅ Файл `agent-profile.json` **НЕ коммитится** в Git (добавлен в `.gitignore`)
- ✅ Храните персональные данные только локально или на защищенном сервере
- ✅ Используйте `agent-profile.example.json` как шаблон для документации

## 🎯 Как это работает

1. **При запуске** агент ищет `agent-profile.json` в:
   - Текущей директории
   - Родительской директории
   - Рабочей директории JVM
   - Пути из `AGENT_PROFILE_PATH`

2. **Если профиль найден:**
   - Загружается и валидируется
   - Применяется к системному промпту
   - Адаптирует стиль общения и контекст

3. **Если профиль не найден:**
   - Используется профиль по умолчанию
   - Агент работает в стандартном режиме

## 📝 Примеры использования

### Минималистичный профиль

```json
{
  "user": {
    "name": "Иван",
    "language": "ru"
  },
  "customInstructions": [
    "Отвечай кратко и по делу"
  ]
}
```

### Полный профиль для команды

```json
{
  "user": {
    "name": "Team Lead",
    "role": "Engineering Manager",
    "timezone": "UTC",
    "language": "en"
  },
  "preferences": {
    "communicationStyle": "formal",
    "verbosity": "detailed",
    "emojiUsage": false
  },
  "workContext": {
    "primaryProjects": ["ProductionApp"],
    "techStack": ["Kotlin", "Spring Boot", "PostgreSQL"],
    "expertiseLevel": "expert"
  }
}
```

## 🛠️ Программный доступ

### Использование в коде

```kotlin
import com.claude.agent.config.UserProfileConfig
import com.claude.agent.services.PersonalizationService

// Получить профиль
val profile = UserProfileConfig.profile

// Использовать сервис персонализации
val service = PersonalizationService()
val context = service.generatePersonalizedContext()
val greeting = service.getGreeting()
```

## 🔄 Обновление профиля

Профиль загружается **один раз при старте**. Для применения изменений:

1. Отредактируйте `agent-profile.json`
2. Перезапустите агент

## 📚 Дополнительно

- Пример профиля: `agent-profile.example.json`
- Исходный код: `common/src/main/kotlin/com/claude/agent/common/models/UserProfile.kt`
- Конфигурация: `remoteAgentServer/src/main/kotlin/com/claude/agent/config/UserProfileConfig.kt`

