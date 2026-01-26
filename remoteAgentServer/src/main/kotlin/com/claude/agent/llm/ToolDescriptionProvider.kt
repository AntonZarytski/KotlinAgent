package com.claude.agent.llm

import com.claude.agent.llm.mcp.*

/**
 * Провайдер описаний инструментов для разных LLM моделей.
 * 
 * Каждая модель получает оптимизированные описания:
 * - Claude: подробные описания с примерами и правилами
 * - Qwen: краткие описания с акцентом на точные имена
 */
interface ToolDescriptionProvider {
    /**
     * Возвращает описание инструмента
     */
    fun getToolDescription(toolName: String): String
    
    /**
     * Возвращает заголовок секции инструментов
     */
    fun getToolsSectionHeader(): String
    
    /**
     * Возвращает дополнительные правила для конкретного инструмента (если есть)
     */
    fun getToolSpecificRules(toolName: String): String?
}

/**
 * Провайдер описаний инструментов для Claude.
 * Подробные описания с примерами использования.
 */
class ClaudeToolDescriptionProvider : ToolDescriptionProvider {
    
    override fun getToolsSectionHeader(): String = """

ДОСТУПНЫЕ ИНСТРУМЕНТЫ:
"""
    
    override fun getToolDescription(toolName: String): String {
        return when (toolName) {
            ACTION_PLANNER -> """
 - $ACTION_PLANNER - планировщик действий для СЛОЖНЫХ МНОГОШАГОВЫХ задач

   КОГДА ИСПОЛЬЗОВАТЬ:
   • Задача состоит из 3+ различных действий
   • Требуется вызвать несколько РАЗНЫХ MCP инструментов
   • Результат одного шага нужен для следующего

   КОГДА НЕ ИСПОЛЬЗОВАТЬ:
   • Простые задачи (1-2 действия)
   • Задачи с одним инструментом
   • Уже начал выполнение - продолжай без планирования

   ПАРАМЕТРЫ:
   • goal (string, обязательный) - цель пользователя
   • steps (array, обязательный) - массив шагов, каждый содержит:
     - step (string, обязательный) - описание шага
     - tool (string, обязательный) - имя MCP инструмента
     - arguments (object, опционально) - аргументы для инструмента

   ПРИМЕР:
   {
     "goal": "Запустить приложение на эмуляторе",
     "steps": [
       {"step": "Установить путь проекта", "tool": "android_studio", "arguments": {"action": "set_project_path", "project_path": "/path/to/project"}},
       {"step": "Собрать проект", "tool": "android_studio", "arguments": {"action": "gradle_build", "build_variant": "debug"}},
       {"step": "Запустить эмулятор", "tool": "android_studio", "arguments": {"action": "start_emulator", "avd_name": "Pixel_5"}}
     ]
   }"""

            WEATHER -> """
 - $WEATHER - получить прогноз погоды по географическому местоположению

   ПАРАМЕТРЫ (все опциональные):
   • latitude (number) - широта местоположения
   • longitude (number) - долгота местоположения
   • units (string) - единицы измерения: "metric" (Цельсий, км/ч) или "imperial" (Фаренгейт, миль/ч), по умолчанию "metric"

   АВТООПРЕДЕЛЕНИЕ: Если координаты не указаны, местоположение определяется автоматически по IP-адресу

   ВОЗВРАЩАЕТ:
   • temperature - текущая температура
   • weather - описание погоды (Ясно, Дождь, Снег, Гроза и т.д.)
   • wind_speed - скорость ветра
   • precipitation_probability - вероятность осадков
   • location_source - источник координат (arguments/ip/default)

   ПРИМЕРЫ:
   {"latitude": 55.75, "longitude": 37.62, "units": "metric"}  // Москва
   {"units": "imperial"}  // Автоопределение по IP"""

            REMINDER -> """
 - $REMINDER - создать/удалить напоминание с поддержкой трех типов задач

   ДЕЙСТВИЯ (параметр action):
   • add - добавить новое напоминание
   • list - список всех напоминаний
   • delete - удалить напоминание по ID

   ПАРАМЕТРЫ ДЛЯ ACTION="add":
   ОБЯЗАТЕЛЬНЫЕ:
   • text (string) - текст напоминания
   • due_at (string) - дата и время в формате ISO 8601 (например: "2024-01-22T15:30:00Z")

   ОПЦИОНАЛЬНЫЕ:
   • task_type (string) - тип задачи:
     - "reminder" (по умолчанию) - простое текстовое напоминание БЕЗ вызова инструментов
     - "ai_response" - сгенерировать AI ответ в будущем БЕЗ вызова инструментов
     - "mcp_tool" - вызвать конкретный MCP инструмент в будущем (ОБЯЗАТЕЛЬНО для погоды, авиабилетов и т.д.)

   • task_context (string) - JSON с контекстом задачи:
     - Для "ai_response": {"user_request": "текст запроса", "accumulated_results": "результаты если были"}
     - Для "mcp_tool" (ОБЯЗАТЕЛЬНО!): {"tool_name": "get_weather_forecast", "tool_arguments": {...}, "user_request": "..."}
     - Для "reminder": не требуется

   • recurrence_type (string) - тип повторения:
     - "none" (по умолчанию) - одноразовое
     - "minutely" - каждую минуту
     - "hourly" - каждый час
     - "daily" - каждый день
     - "weekly" - каждую неделю
     - "monthly" - каждый месяц

   • recurrence_interval (integer) - интервал повторения (например, 2 для "каждые 2 часа"), по умолчанию 1
   • recurrence_end_date (string) - дата окончания повторений в формате ISO 8601 (опционально)

   ВАЖНО: Если в запросе упоминается погода, авиабилеты или другие данные, требующие вызова инструмента - ОБЯЗАТЕЛЬНО используй task_type="mcp_tool"!

   ПРИМЕРЫ:
   // Простое напоминание
   {"action": "add", "text": "Позвонить маме", "due_at": "2024-01-22T18:00:00Z"}

   // AI ответ в будущем
   {"action": "add", "text": "Рецепт пиццы", "due_at": "2024-01-22T15:30:00Z", "task_type": "ai_response", "task_context": "{\"user_request\": \"рецепт пиццы\"}"}

   // Вызов MCP инструмента (погода)
   {"action": "add", "text": "Погода завтра", "due_at": "2024-01-23T09:00:00Z", "task_type": "mcp_tool", "task_context": "{\"tool_name\": \"get_weather_forecast\", \"tool_arguments\": {\"latitude\": 55.75, \"longitude\": 37.62}, \"user_request\": \"покажи погоду\"}"}

   // Повторяющееся напоминание
   {"action": "add", "text": "Проверить почту", "due_at": "2024-01-22T09:00:00Z", "recurrence_type": "daily", "recurrence_interval": 1}"""

            ANDROID_STUDIO_MCP -> """
 - $ANDROID_STUDIO_MCP - управление Android Studio, Android Emulator, ADB, Gradle и ЛОКАЛЬНОЙ ФАЙЛОВОЙ СИСТЕМОЙ

   ВАЖНО: Этот инструмент выполняется на ЛОКАЛЬНОМ АГЕНТЕ (компьютере разработчика), НЕ на сервере!
   ВЕСЬ доступ к файлам ДОЛЖЕН осуществляться через этот инструмент.

   ДОСТУПНЫЕ ДЕЙСТВИЯ (параметр action):

   КОНФИГУРАЦИЯ ПРОЕКТА:
   • set_project_path - установить путь к Android-проекту
     Параметры: project_path (string, обязательный) - абсолютный путь к проекту
   • get_project_path - получить текущий путь проекта
   • get_file_tree - получить дерево файлов проекта

   ЭМУЛЯТОР И ADB:
   • list_emulators - список доступных AVD
   • start_emulator - запустить Android-эмулятор
     Параметры: avd_name (string, обязательный) - имя AVD
   • stop_emulator - остановить запущенный эмулятор
   • install_apk - установить APK в эмулятор
     Параметры: apk_path (string, обязательный) - путь к APK файлу
   • run_app - запустить установленное приложение
     Параметры: package_name (string, обязательный) - имя пакета (например, com.example.app)
   • adb_shell - выполнить команду adb shell
     Параметры: command (string, обязательный) - команда для выполнения
   • screenshot - сделать скриншот с эмулятора

   СБОРКА И ДЕПЛОЙ:
   • gradle_build - собрать Android-проект
     Параметры: build_variant (string, опционально) - вариант сборки (debug/release), по умолчанию "debug"
   • gradle_install_run - собрать, установить и запустить приложение

   ЛОГИРОВАНИЕ:
   • logcat - получить логи Android
     Параметры: filter_tag (string, опционально), filter_package (string, опционально),
                log_level (string, опционально, по умолчанию "V"), max_lines (integer, опционально, по умолчанию 500)
   • logcat_clear - очистить буфер logcat

   ФАЙЛОВАЯ СИСТЕМА (ТОЛЬКО ЛОКАЛЬНАЯ МАШИНА):
   • browse_files - список файлов и папок относительно корня проекта
     Параметры: directory_path (string, опционально) - относительный путь к директории
   • read_file - чтение файла относительно корня проекта
     Параметры: file_path (string, обязательный) - относительный путь к файлу
   • read_file_lines - чтение определенных строк файла
     Параметры: file_path (string, обязательный), start_line (integer, опционально),
                end_line (integer, опционально), search_pattern (string, опционально)
   • find_files - поиск файлов по паттерну
     Параметры: pattern (string, обязательный) - паттерн имени файла (например, '*.kt', 'MainActivity.*'),
                max_depth (integer, опционально) - максимальная глубина поиска
   • save_log - сохранить содержимое в файл на локальной машине
     Параметры: log_content (string, обязательный), log_name (string, опционально, по умолчанию "log")

   КРИТИЧЕСКИЕ ПРАВИЛА:
   • НИКОГДА не обращаться к файлам напрямую
   • НИКОГДА не предполагать содержимое файлов
   • ВСЕГДА изучать структуру перед чтением, если она неизвестна
   • Пути в browse_files/read_file ДОЛЖНЫ быть ОТНОСИТЕЛЬНЫМИ к корню проекта
   • Сначала установи путь проекта через set_project_path, затем используй относительные пути

   ЭФФЕКТИВНЫЙ РАБОЧИЙ ПРОЦЕСС:
   1. Один раз просмотреть корневую директорию (directory_path = "")
   2. При необходимости углубиться на ОДИН уровень
   3. НЕ просматривать все подкаталоги рекурсивно
   4. НЕ вызывать browse_files более 3 раз для одной задачи

   ПРИМЕРЫ:
   // Установить путь проекта
   {"action": "set_project_path", "project_path": "/Users/anton/StudioProjects/MyApp"}

   // Просмотреть корень проекта
   {"action": "browse_files", "directory_path": ""}

   // Прочитать манифест
   {"action": "read_file", "file_path": "app/src/main/AndroidManifest.xml"}

   // Собрать AAB для публикации
   {"action": "gradle_build", "build_variant": "release"}

   // Запустить эмулятор
   {"action": "start_emulator", "avd_name": "Pixel_5_API_30"}

   // Установить и запустить приложение
   {"action": "gradle_install_run"}"""

            "google_play_publisher" -> """
 - google_play_publisher - публикация Android приложений (AAB файлов) в Google Play Console

   ДЕЙСТВИЯ (параметр action):
   • publish_aab - опубликовать AAB файл в Google Play

   ПАРАМЕТРЫ:
   ОБЯЗАТЕЛЬНЫЕ:
   • release_notes (string) - описание изменений на английском языке (будет автоматически переведено на 5 языков: en-US, ru-RU, de-DE, fr-FR, es-ES)

   ОПЦИОНАЛЬНЫЕ (автоматически извлекаются из проекта):
   • package_name (string) - имя пакета приложения (извлекается из build.gradle.kts)
   • aab_file_path (string) - путь к AAB файлу (автоматически ищется или собирается)
   • version_code (integer) - код версии (извлекается из build.gradle.kts)
   • version_name (string) - имя версии (извлекается из build.gradle.kts)
   • track (string) - канал распространения: "internal", "alpha", "beta", "production" (по умолчанию "internal")
   • session_id (string) - ID сессии для создания тикета при ошибке

   АВТОМАТИЧЕСКИЕ ФУНКЦИИ:
   • Извлечение метаданных из AndroidManifest.xml и build.gradle.kts
   • Автоматический поиск AAB файла в стандартном месте (app/release/app-release.aab)
   • Автоматическая сборка AAB через gradle bundleRelease, если файл не найден
   • Перевод release notes на 5 языков с помощью Claude API
   • Создание support ticket при ошибках (если указан session_id)

   ВОЗВРАЩАЕТ:
   • success (boolean) - успешность публикации
   • message (string) - сообщение о результате
   • edit_id (string) - ID редактирования в Google Play (при успехе)
   • version_code (integer) - опубликованный код версии
   • track (string) - канал публикации
   • package_name (string) - имя пакета
   • error (string) - описание ошибки (при неудаче)

   ВАЖНО:
   • Перед публикацией убедись, что путь к проекту установлен через android_studio set_project_path
   • AAB файл должен быть собран через android_studio gradle_build с task="bundleRelease"
   • Release notes должны быть на английском языке - перевод происходит автоматически

   ПРИМЕРЫ:
   // Минимальный вызов (все параметры извлекаются автоматически)
   {"action": "publish_aab", "release_notes": "Bug fixes and performance improvements"}

   // Полный вызов с явными параметрами
   {"action": "publish_aab", "package_name": "com.example.myapp", "aab_file_path": "/path/to/app-release.aab",
    "version_code": 42, "version_name": "1.2.0", "release_notes": "New features: dark mode, offline support",
    "track": "beta", "session_id": "abc123"}"""
            
            GIT_REPOSITORY -> """
 - $GIT_REPOSITORY - работа с git-репозиторием:
   * get_current_branch - текущая ветка
   * get_status - измененные файлы в текущей ветке
   * get_recent_commits - последние коммиты
   * get_diff - изменения в коде
   * get_branches - список всех веток
   * get_file_history - история изменений файла
   * list_files_in_branch - список файлов в любой ветке (с фильтром по расширению)
   * show_file_from_branch - показать содержимое файла из любой ветки
   * compare_branches - сравнить две ветки (список измененных файлов)
   * get_branch_commits - коммиты в ветке (которых нет в main)

   ВАЖНО: Можно работать с любой веткой БЕЗ переключения (checkout)!
   Примеры:
   - Список .kt файлов в ветке day_12: list_files_in_branch(branch="origin/day_12", file_extension=".kt")
   - Содержимое файла из ветки: show_file_from_branch(branch="origin/day_12", file_path="path/to/file.kt")
   - Сравнить ветки: compare_branches(branch="origin/day_12", target_branch="main")"""

            else -> " - $toolName - инструмент доступен"
        }
    }
    
    override fun getToolSpecificRules(toolName: String): String? {
        return when (toolName) {
            ACTION_PLANNER -> """

ПЛАНИРОВАНИЕ ЗАДАЧ (КРИТИЧЕСКИ ВАЖНО):

КОГДА ИСПОЛЬЗОВАТЬ ACTION_PLANNER:
- Задача состоит из 3+ различных действий
- Требуется вызвать несколько РАЗНЫХ MCP инструментов
- Результат одного шага нужен для следующего

КОГДА НЕ ИСПОЛЬЗОВАТЬ:
- Простые задачи (1-2 действия)
- Задачи с одним инструментом
- УЖЕ НАЧАЛ ВЫПОЛНЕНИЕ - продолжай БЕЗ планирования!

ВАЖНО:
- НЕ планируй повторно если уже начал выполнять задачу
- НЕ выполняй действия сам - только планируй
- План должен быть КОНКРЕТНЫМ с именами инструментов и аргументами
- Каждый шаг должен приближать к финальной цели"""

            REMINDER -> """

СОЗДАНИЕ НАПОМИНАНИЙ (КРИТИЧЕСКИ ВАЖНО):

ВЫБОР ТИПА ЗАДАЧИ (task_type):

1. task_type="reminder" - простые текстовые напоминания
   Пример: "напомни позвонить маме"

2. task_type="ai_response" - AI ответ БЕЗ вызова инструментов
   Пример: "отправь рецепт пиццы через час"

3. task_type="mcp_tool" - ОБЯЗАТЕЛЬНО когда нужен вызов инструмента
   Примеры: "покажи погоду через час", "найди авиабилеты завтра"

ВАЖНО:
- Если упоминается ПОГОДА, АВИАБИЛЕТЫ, СОЛНЕЧНАЯ АКТИВНОСТЬ - используй task_type="mcp_tool"
- Для mcp_tool ОБЯЗАТЕЛЬНО укажи в task_context:
  * "tool_name": имя инструмента (например "get_weather_forecast")
  * "tool_arguments": JSON с аргументами
  * "user_request": оригинальный запрос"""

            ANDROID_STUDIO_MCP -> """

ПУБЛИКАЦИЯ ANDROID ПРИЛОЖЕНИЙ (КРИТИЧЕСКИ ВАЖНО):
Когда пользователь просит опубликовать/собрать/выпустить Android приложение:

ЗАПРЕЩЕНО:
- НЕ анализируй код проекта (MainActivity, build.gradle и т.д.)
- НЕ просматривай структуру проекта
- НЕ читай файлы проекта
- НЕ задавай вопросы о коде

ОБЯЗАТЕЛЬНО (ВЫПОЛНЯЙ ПОСЛЕДОВАТЕЛЬНО С ПРОВЕРКОЙ РЕЗУЛЬТАТА):

Шаг 1: Установить путь к проекту (если не установлен)
   - Используй android_studio с action="set_project_path"
   - Путь должен быть ПОЛНЫМ (например: /Users/anton/StudioProjects/SecretChat)
   - ПРОВЕРЬ результат: если status != "success" - ОСТАНОВИ выполнение и сообщи об ошибке

Шаг 2: Собрать AAB файл
   - Используй android_studio с action="gradle_build" и task="bundleRelease"
   - Дождись результата (это может занять несколько минут)
   - ПРОВЕРЬ результат: если status != "success" - ОСТАНОВИ выполнение и сообщи об ошибке

Шаг 3: Опубликовать в Google Play
   - Используй google_play_publisher с action="publish_aab"
   - Укажи ВСЕ параметры: package_name, aab_file_path, version_code, version_name, release_notes, track
   - AAB файл обычно находится в: <project_path>/app/release/app-release.aab
   - ПРОВЕРЬ результат: если success != true - сообщи об ошибке с деталями

НЕ ДЕЛАЙ НИЧЕГО ЛИШНЕГО! Просто выполни эти 3 шага последовательно с проверкой результата."""
            else -> null
        }
    }
}

/**
 * Провайдер описаний инструментов для Qwen.
 * Краткие описания с акцентом на точные имена инструментов.
 */
class QwenToolDescriptionProvider : ToolDescriptionProvider {

    override fun getToolsSectionHeader(): String = """

ДОСТУПНЫЕ ИНСТРУМЕНТЫ (используй ТОЧНЫЕ имена):
"""

    override fun getToolDescription(toolName: String): String {
        return when (toolName) {
            ACTION_PLANNER -> """✅ $ACTION_PLANNER - планировщик для СЛОЖНЫХ МНОГОШАГОВЫХ задач (3+ действия)
Параметры:
  • goal (string, обязательно) - цель задачи
  • steps (array, обязательно) - массив шагов, каждый содержит:
    - step (string, обязательно) - описание шага
    - tool (string, обязательно) - имя инструмента
    - arguments (object, опционально) - аргументы для инструмента
НЕ используй для простых задач (1-2 действия)!"""

            WEATHER -> """✅ $WEATHER - прогноз погоды
Параметры:
  • latitude (number, опционально) - широта
  • longitude (number, опционально) - долгота
  • units (string, опционально, default: "metric") - единицы измерения (metric/imperial)
Если координаты не указаны - автоопределение по IP"""

            REMINDER -> """✅ $REMINDER - напоминания (3 типа: reminder, ai_response, mcp_tool)
Параметры:
  • action (string, обязательно) - add/list/delete
  • text (string, для add) - текст напоминания
  • due_at (string, для add) - время в ISO 8601 формате
  • task_type (string, опционально) - reminder/ai_response/mcp_tool
  • task_context (string, опционально) - контекст для AI или JSON для MCP
  • recurrence_type (string, опционально) - minutely/hourly/daily/weekly/monthly
  • recurrence_interval (integer, опционально) - интервал повторения
  • recurrence_end_date (string, опционально) - дата окончания повторений"""

            ANDROID_STUDIO_MCP -> """✅ $ANDROID_STUDIO_MCP - Android Studio/ADB/Gradle/Файловая система
Параметр action (обязательно):
  Конфигурация:
    • set_project_path (project_path) - установить путь к проекту
    • get_project_path - получить текущий путь
    • get_file_tree (directory_path) - дерево файлов
  Эмулятор/ADB:
    • list_emulators - список эмуляторов
    • start_emulator (avd_name) - запустить
    • stop_emulator - остановить
    • install_apk (apk_path) - установить APK
    • run_app (package_name) - запустить приложение
    • adb_shell (command) - выполнить команду
    • screenshot (output_path) - скриншот
  Сборка/Развертывание:
    • gradle_build (task, build_variant) - собрать проект
    • gradle_install_run (build_variant, package_name) - собрать+установить+запустить
  Логирование:
    • logcat (filter_spec, max_lines) - логи
    • logcat_clear - очистить логи
  Файловая система:
    • browse_files (directory_path) - список файлов
    • read_file (file_path) - читать файл
    • read_file_lines (file_path, start_line, end_line) - читать строки
    • find_files (directory_path, pattern) - найти файлы
    • save_log (log_content, output_path) - сохранить лог
ВАЖНО: Все пути относительно project_path!"""

            "google_play_publisher" -> """✅ google_play_publisher - публикация в Google Play Console
Параметры:
  • action (string, обязательно) - publish_aab
  • release_notes (string, обязательно) - описание релиза (авто-перевод на 5 языков)
  • package_name (string, опционально) - авто-извлекается из AAB
  • aab_file_path (string, опционально) - авто-поиск в build/outputs
  • version_code (integer, опционально) - авто-извлекается из AAB
  • version_name (string, опционально) - авто-извлекается из AAB
  • track (string, опционально, default: "internal") - internal/alpha/beta/production
  • session_id (string, опционально) - для WebSocket уведомлений"""

            GIT_REPOSITORY -> "✅ $GIT_REPOSITORY - git (get_status, get_diff, list_files_in_branch, show_file_from_branch)"
            else -> "✅ $toolName"
        }
    }

    override fun getToolSpecificRules(toolName: String): String? {
        return when (toolName) {
            ACTION_PLANNER ->
"""ПЛАНИРОВАНИЕ:
- Используй для 3+ действий с разными инструментами
- НЕ используй если уже начал выполнение
- План должен быть конкретным с именами инструментов"""

            REMINDER ->
"""ТИПЫ НАПОМИНАНИЙ:
- task_type="reminder" - простой текст
- task_type="ai_response" - AI ответ без инструментов
- task_type="mcp_tool" - ОБЯЗАТЕЛЬНО для погоды/авиабилетов
Для mcp_tool укажи: tool_name, tool_arguments, user_request"""

            ANDROID_STUDIO_MCP ->
"""ПУБЛИКАЦИЯ ANDROID:
1. set_project_path (полный путь)
2. gradle_build (task="bundleRelease")
3. google_play_publisher (publish_aab)
Проверяй результат каждого шага!"""
            else -> null
        }
    }
}

