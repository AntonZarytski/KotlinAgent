package com.claude.agent.service

import com.claude.agent.models.TokenUsage
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Сервис для отслеживания метрик использования токенов.
 * 
 * Собирает статистику по:
 * - Общему расходу токенов
 * - Расходу по сессиям
 * - Эффективности кэширования
 * - Экономии от оптимизаций
 */
class TokenMetricsService {
    private val logger = LoggerFactory.getLogger(TokenMetricsService::class.java)
    
    // Глобальные счетчики
    private val totalInputTokens = AtomicLong(0)
    private val totalOutputTokens = AtomicLong(0)
    private val totalCachedTokens = AtomicLong(0)
    private val totalRequests = AtomicInteger(0)
    
    // Метрики по сессиям
    private val sessionMetrics = ConcurrentHashMap<String, SessionTokenMetrics>()
    
    // Метрики оптимизаций
    private val compressionSavings = AtomicLong(0)
    private val cachingSavings = AtomicLong(0)
    private val toolFilteringSavings = AtomicLong(0)
    
    data class SessionTokenMetrics(
        val sessionId: String,
        var inputTokens: Long = 0,
        var outputTokens: Long = 0,
        var cachedTokens: Long = 0,
        var requestCount: Int = 0,
        var lastUpdated: String = Instant.now().toString()
    ) {
        val totalInputTokens: Long get() = inputTokens
        val totalOutputTokens: Long get() = outputTokens
        val totalCachedTokens: Long get() = cachedTokens
    }
    
    data class TokenMetricsSnapshot(
        val totalInputTokens: Long,
        val totalOutputTokens: Long,
        val totalCachedTokens: Long,
        val totalRequests: Int,
        val compressionSavings: Long,
        val cachingSavings: Long,
        val toolFilteringSavings: Long,
        val averageInputPerRequest: Double,
        val averageOutputPerRequest: Double,
        val cacheHitRate: Double,
        val topSessions: List<SessionTokenMetrics>
    ) {
        val totalSavings: Long
            get() = compressionSavings + cachingSavings + toolFilteringSavings
    }
    
    /**
     * Записывает использование токенов для запроса
     */
    fun recordTokenUsage(
        sessionId: String?,
        usage: TokenUsage,
        cachedTokens: Int = 0
    ) {
        val inputTokens = usage.input_tokens?.toLong() ?: 0
        val outputTokens = usage.output_tokens?.toLong() ?: 0
        
        // Обновляем глобальные счетчики
        totalInputTokens.addAndGet(inputTokens)
        totalOutputTokens.addAndGet(outputTokens)
        totalCachedTokens.addAndGet(cachedTokens.toLong())
        totalRequests.incrementAndGet()
        
        // Обновляем метрики сессии
        if (sessionId != null) {
            sessionMetrics.compute(sessionId) { _, existing ->
                val metrics = existing ?: SessionTokenMetrics(sessionId)
                metrics.apply {
                    this.inputTokens += inputTokens
                    this.outputTokens += outputTokens
                    this.cachedTokens += cachedTokens.toLong()
                    this.requestCount++
                    this.lastUpdated = Instant.now().toString()
                }
            }
        }
        
        logger.debug("Token usage recorded: session=$sessionId, input=$inputTokens, output=$outputTokens, cached=$cachedTokens")
    }
    
    /**
     * Записывает экономию от сжатия истории
     */
    fun recordCompressionSavings(savedTokens: Long) {
        compressionSavings.addAndGet(savedTokens)
        logger.info("💰 Compression saved ~$savedTokens tokens")
    }
    
    /**
     * Записывает экономию от кэширования
     */
    fun recordCachingSavings(savedTokens: Long) {
        cachingSavings.addAndGet(savedTokens)
        logger.info("💰 Caching saved ~$savedTokens tokens")
    }
    
    /**
     * Записывает экономию от фильтрации tools
     */
    fun recordToolFilteringSavings(savedTokens: Long) {
        toolFilteringSavings.addAndGet(savedTokens)
        logger.info("💰 Tool filtering saved ~$savedTokens tokens")
    }
    
    /**
     * Получает текущий снимок метрик
     */
    fun getMetricsSnapshot(): TokenMetricsSnapshot {
        val requests = totalRequests.get()
        val inputTokens = totalInputTokens.get()
        val outputTokens = totalOutputTokens.get()
        val cachedTokens = totalCachedTokens.get()
        
        val avgInput = if (requests > 0) inputTokens.toDouble() / requests else 0.0
        val avgOutput = if (requests > 0) outputTokens.toDouble() / requests else 0.0
        val cacheHitRate = if (inputTokens > 0) cachedTokens.toDouble() / inputTokens else 0.0
        
        val topSessions = sessionMetrics.values
            .sortedByDescending { it.inputTokens + it.outputTokens }
            .take(10)
        
        return TokenMetricsSnapshot(
            totalInputTokens = inputTokens,
            totalOutputTokens = outputTokens,
            totalCachedTokens = cachedTokens,
            totalRequests = requests,
            compressionSavings = compressionSavings.get(),
            cachingSavings = cachingSavings.get(),
            toolFilteringSavings = toolFilteringSavings.get(),
            averageInputPerRequest = avgInput,
            averageOutputPerRequest = avgOutput,
            cacheHitRate = cacheHitRate,
            topSessions = topSessions
        )
    }
    
    /**
     * Получает метрики для конкретной сессии
     */
    fun getSessionMetrics(sessionId: String): SessionTokenMetrics? {
        return sessionMetrics[sessionId]
    }
    
    /**
     * Сбрасывает все метрики
     */
    fun reset() {
        totalInputTokens.set(0)
        totalOutputTokens.set(0)
        totalCachedTokens.set(0)
        totalRequests.set(0)
        compressionSavings.set(0)
        cachingSavings.set(0)
        toolFilteringSavings.set(0)
        sessionMetrics.clear()
        logger.info("Token metrics reset")
    }
}

