package com.claude.agent.service

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Сервис для синхронизации пути к проекту между различными MCP tools.
 * 
 * Этот сервис решает проблему синхронизации между:
 * - AndroidStudioLocalMcp (работает через локальный агент)
 * - GitRepositoryMcp (работает локально на сервере)
 * 
 * Когда пользователь устанавливает путь к проекту через AndroidStudioLocalMcp,
 * GitRepositoryMcp автоматически начинает работать с тем же проектом.
 */
object ProjectPathService {
    private val logger = LoggerFactory.getLogger(ProjectPathService::class.java)
    
    // Хранилище путей проектов по sessionId
    // Ключ: sessionId, Значение: путь к проекту
    private val projectPaths = ConcurrentHashMap<String, String>()
    
    // Путь по умолчанию (текущая директория сервера)
    private val defaultPath = System.getenv("GIT_REPO_PATH") ?: System.getProperty("user.dir")
    
    init {
        logger.info("ProjectPathService initialized with default path: $defaultPath")
    }
    
    /**
     * Установить путь к проекту для сессии.
     * Вызывается когда AndroidStudioLocalMcp получает set_project_path.
     */
    fun setProjectPath(sessionId: String?, projectPath: String) {
        if (sessionId != null) {
            projectPaths[sessionId] = projectPath
            logger.info("✅ Project path set for session $sessionId: $projectPath")
        } else {
            logger.warn("⚠️ Attempted to set project path without sessionId")
        }
    }
    
    /**
     * Получить путь к проекту для сессии.
     * Используется GitRepositoryMcp для определения, с каким репозиторием работать.
     */
    fun getProjectPath(sessionId: String?): String {
        return if (sessionId != null) {
            projectPaths[sessionId] ?: defaultPath
        } else {
            defaultPath
        }.also {
            logger.debug("📂 Project path for session $sessionId: $it")
        }
    }
    
    /**
     * Очистить путь проекта для сессии.
     */
    fun clearProjectPath(sessionId: String?) {
        if (sessionId != null) {
            projectPaths.remove(sessionId)
            logger.info("🗑️ Project path cleared for session $sessionId")
        }
    }
    
    /**
     * Получить все активные пути проектов.
     */
    fun getAllProjectPaths(): Map<String, String> {
        return projectPaths.toMap()
    }
}

