# Тестирование публикации Android приложения в Google Play

## 📋 Подготовка

### Собранные данные:
- ✅ **Package name**: `com.tonproduction.round.timer`
- ✅ **AAB файл**: `/Users/anton/StudioProjects/RoundTimer/app/release/app-release.aab` (26MB)
- ✅ **Текущая версия**: versionCode=10, versionName=1.04
- ✅ **Google Play Service Account**: `gpd-console-claude-e8a3dbb39785.json`
- ✅ **Keystore**: `RoundTimer.jks` (настроен в keystore.properties)

### Для тестовой публикации:
- **Version code**: `11` (увеличен на 1)
- **Version name**: `1.05`
- **Track**: `internal` (для тестирования)
- **Release notes**: "Test release via AI agent. Bug fixes and performance improvements."

## 🚀 Workflow тестирования

### Шаг 1: Проверка конфигурации

Убедитесь, что в `.env` добавлен путь к Google Play Service Account:
```bash
GOOGLE_PLAY_SERVICE_ACCOUNT_PATH=gpd-console-claude-e8a3dbb39785.json
```

✅ **Выполнено**

### Шаг 2: Перезапуск сервера

Перезапустите сервер для применения новой конфигурации:
```bash
./restart-server.sh
```

### Шаг 3: Публикация через GooglePlayPublisherMcp

Используйте следующие параметры для публикации:

```json
{
  "action": "publish_aab",
  "package_name": "com.tonproduction.round.timer",
  "aab_file_path": "/Users/anton/StudioProjects/RoundTimer/app/release/app-release.aab",
  "version_code": 11,
  "version_name": "1.05",
  "release_notes": "Test release via AI agent. Bug fixes and performance improvements.",
  "track": "internal",
  "session_id": "test-session-123"
}
```

### Шаг 4: Проверка результата

После публикации проверьте:
1. ✅ Успешная загрузка AAB файла
2. ✅ Автоматический перевод release notes на языки: en-US, ru-RU, de-DE, fr-FR, es-ES
3. ✅ Публикация на internal track
4. ✅ Получение edit_id и version_code в ответе

При ошибке:
- ❌ Создание support ticket с деталями ошибки
- ❌ Логирование ошибки в server.log

## ⚠️ Важные замечания

### Проблема с signing config

В `app/build.gradle.kts` **отсутствует** блок `signingConfigs`. Это означает, что существующий AAB файл может быть:
- Подписан debug-ключом (не подходит для production)
- Подписан вручную через Android Studio

Для production публикации нужно добавить в `app/build.gradle.kts`:

```kotlin
// Загружаем keystore properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
}

android {
    // ... существующая конфигурация ...
    
    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { rootProject.file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... остальная конфигурация ...
        }
    }
}
```

## 🔄 Полный workflow с автоматической сборкой

Для полного тестирования workflow (сборка + публикация):

### 1. Запустить Local Agent
```bash
./start-android-system.sh
```

### 2. Собрать AAB через AndroidStudioLocalMcp
```json
{
  "action": "gradle_build",
  "build_variant": "release"
}
```

### 3. Найти путь к AAB файлу
Обычно: `app/build/outputs/bundle/release/app-release.aab`

### 4. Опубликовать через GooglePlayPublisherMcp
(см. Шаг 3 выше)

## 📊 Ожидаемый результат

### Успешная публикация:
```json
{
  "success": true,
  "message": "Successfully published version 1.05 (11) to internal track",
  "edit_id": "1234567890",
  "version_code": 11,
  "track": "internal",
  "package_name": "com.tonproduction.round.timer"
}
```

### Ошибка публикации:
```json
{
  "success": false,
  "error": "Error message from Google Play API",
  "message": "Publication failed: ...",
  "track": "internal"
}
```

+ Support ticket создан с деталями ошибки

