package com.claude.agent.routes

import com.claude.agent.service.TokenMetricsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * API endpoints для получения метрик использования токенов
 */
fun Route.metricsRoutes(tokenMetricsService: TokenMetricsService) {
    route("/api/metrics") {
        
        /**
         * GET /api/metrics - Получить общую статистику использования токенов
         */
        get {
            val snapshot = tokenMetricsService.getMetricsSnapshot()
            
            val response = buildJsonObject {
                put("totalRequests", snapshot.totalRequests)
                put("totalInputTokens", snapshot.totalInputTokens)
                put("totalOutputTokens", snapshot.totalOutputTokens)
                put("totalCachedTokens", snapshot.totalCachedTokens)
                put("totalTokens", snapshot.totalInputTokens + snapshot.totalOutputTokens)
                
                putJsonObject("savings") {
                    put("compression", snapshot.compressionSavings)
                    put("caching", snapshot.cachingSavings)
                    put("toolFiltering", snapshot.toolFilteringSavings)
                    put("total", snapshot.totalSavings)
                }
                
                putJsonObject("efficiency") {
                    val totalTokens = snapshot.totalInputTokens + snapshot.totalOutputTokens
                    val effectiveTokens = totalTokens - snapshot.totalSavings
                    val savingsPercent = if (totalTokens > 0) {
                        (snapshot.totalSavings.toDouble() / totalTokens * 100).toInt()
                    } else 0
                    
                    put("effectiveTokens", effectiveTokens)
                    put("savingsPercent", savingsPercent)
                    put("cacheHitRate", if (snapshot.totalInputTokens > 0) {
                        (snapshot.totalCachedTokens.toDouble() / snapshot.totalInputTokens * 100).toInt()
                    } else 0)
                }
                
                putJsonObject("averages") {
                    put("inputPerRequest", if (snapshot.totalRequests > 0) {
                        snapshot.totalInputTokens / snapshot.totalRequests
                    } else 0)
                    put("outputPerRequest", if (snapshot.totalRequests > 0) {
                        snapshot.totalOutputTokens / snapshot.totalRequests
                    } else 0)
                }
            }
            
            call.respond(HttpStatusCode.OK, response)
        }
        
        /**
         * GET /api/metrics/session/{sessionId} - Получить метрики для конкретной сессии
         */
        get("/session/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
            
            if (sessionId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Session ID is required"))
                return@get
            }
            
            val sessionMetrics = tokenMetricsService.getSessionMetrics(sessionId)
            
            if (sessionMetrics == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session not found"))
                return@get
            }
            
            val response = buildJsonObject {
                put("sessionId", sessionId)
                put("requestCount", sessionMetrics.requestCount)
                put("totalInputTokens", sessionMetrics.inputTokens)
                put("totalOutputTokens", sessionMetrics.outputTokens)
                put("totalCachedTokens", sessionMetrics.cachedTokens)
                put("totalTokens", sessionMetrics.inputTokens + sessionMetrics.outputTokens)

                putJsonObject("averages") {
                    put("inputPerRequest", if (sessionMetrics.requestCount > 0) {
                        sessionMetrics.inputTokens / sessionMetrics.requestCount
                    } else 0)
                    put("outputPerRequest", if (sessionMetrics.requestCount > 0) {
                        sessionMetrics.outputTokens / sessionMetrics.requestCount
                    } else 0)
                }
            }
            
            call.respond(HttpStatusCode.OK, response)
        }
        
        /**
         * POST /api/metrics/reset - Сбросить все метрики (для тестирования)
         */
        post("/reset") {
            tokenMetricsService.reset()
            call.respond(HttpStatusCode.OK, mapOf("message" to "Metrics reset successfully"))
        }
        
        /**
         * GET /api/metrics/summary - Получить краткую сводку для отображения в UI
         */
        get("/summary") {
            val snapshot = tokenMetricsService.getMetricsSnapshot()
            
            val totalTokens = snapshot.totalInputTokens + snapshot.totalOutputTokens
            val savingsPercent = if (totalTokens > 0) {
                (snapshot.totalSavings.toDouble() / totalTokens * 100).toInt()
            } else 0
            
            val response = buildJsonObject {
                put("totalRequests", snapshot.totalRequests)
                put("totalTokens", totalTokens)
                put("savedTokens", snapshot.totalSavings)
                put("savingsPercent", savingsPercent)
                put("cacheHitRate", if (snapshot.totalInputTokens > 0) {
                    (snapshot.totalCachedTokens.toDouble() / snapshot.totalInputTokens * 100).toInt()
                } else 0)
            }
            
            call.respond(HttpStatusCode.OK, response)
        }
    }
}

