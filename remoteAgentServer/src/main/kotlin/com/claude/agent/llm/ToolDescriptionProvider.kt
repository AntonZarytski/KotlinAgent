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
 - $ACTION_PLANNER - планировщик действий для выполнения задачи с возможностью последовательного вызова инструментов
   Используй для сложных задач, требующих нескольких шагов"""
            
            WEATHER -> """
 - $WEATHER - получить текущую погоду по координатам (latitude, longitude) или автоматически по IP
   Параметры: latitude (опционально), longitude (опционально)
   Если координаты не указаны, определяется по IP клиента"""
            
            SOLAR -> """
 - $SOLAR - получить данные о солнечной активности и полярных сияниях по координатам
   Параметры: latitude, longitude
   Возвращает: KP-индекс, вероятность полярных сияний"""
            
            REMINDER -> """
 - $REMINDER - создать напоминание
   Если пользователь просит напомнить/создать напоминание/уведомить в будущем,
   вызови инструмент $REMINDER с параметрами:
   - text: текст напоминания
   - due_at: дата и время (ISO 8601 формат)
   - recurrence_type: тип повторения (once, daily, weekly, monthly)"""
            
            CHAT_SUMMARY -> """
 - $CHAT_SUMMARY - получить краткое резюме текущего чата
   Анализирует историю сообщений и возвращает краткое содержание"""
            
            AIR_TICKETS -> """
 - $AIR_TICKETS - поиск авиабилетов
   Параметры: маршрут, даты, количество пассажиров, класс обслуживания
   Возвращает доступные варианты перелетов с ценами"""
            
            ANDROID_STUDIO_MCP -> """
 - $ANDROID_STUDIO_MCP - управление эмулятором Android Studio и выполнение команд ADB
   Доступные действия:
   - list_emulators: список доступных эмуляторов
   - start_emulator: запустить эмулятор
   - stop_emulator: остановить эмулятор
   - adb_command: выполнить ADB команду
   - browse_files: просмотр файлов проекта
   - read_file: чтение содержимого файла
   - set_project_path: установить путь к проекту
   - gradle_build: собрать проект (bundleRelease для AAB)"""
            
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
            
            PROJECT_HELP -> """
 - $PROJECT_HELP - помощь по проекту
   Пользователь может использовать команду /help для быстрого поиска информации в документации проекта.
   Формат: /help [вопрос](опционально)
   
   Примеры:
   - /help
   - /help Как работает RAG?
   - /help Как создать MCP tool?
   - /help Правила стиля кода
   
   Эта команда автоматически использует RAG для поиска релевантной информации.
   
   ИНСТРУМЕНТ project_help:
   При использовании инструмента project_help всегда давай конкретные ответы, только касающиеся вопроса, на основе найденного контекста.
   Если пользователь вводит только /help без вопроса, то выводи подробную информацию по проекту."""
            
            else -> " - $toolName - инструмент доступен"
        }
    }
    
    override fun getToolSpecificRules(toolName: String): String? {
        return when (toolName) {
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
            ACTION_PLANNER -> "✅ $ACTION_PLANNER - планировщик действий"
            WEATHER -> "✅ $WEATHER - погода (latitude, longitude или auto)"
            SOLAR -> "✅ $SOLAR - солнечная активность (latitude, longitude)"
            REMINDER -> "✅ $REMINDER - создать напоминание (text, due_at, recurrence_type)"
            CHAT_SUMMARY -> "✅ $CHAT_SUMMARY - резюме чата"
            AIR_TICKETS -> "✅ $AIR_TICKETS - поиск авиабилетов"
            ANDROID_STUDIO_MCP -> "✅ $ANDROID_STUDIO_MCP - Android Studio/ADB (set_project_path, gradle_build, browse_files, read_file)"
            GIT_REPOSITORY -> "✅ $GIT_REPOSITORY - git (get_status, get_diff, list_files_in_branch, show_file_from_branch)"
            PROJECT_HELP -> "✅ $PROJECT_HELP - помощь по проекту"
            else -> "✅ $toolName"
        }
    }

    override fun getToolSpecificRules(toolName: String): String? {
        return when (toolName) {
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

