package com.claude.agent.llm.mcp.local.model

import com.claude.agent.models.UserLocation
import com.claude.agent.service.GeolocationService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Информация о местоположении с источником данных.
 */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val source: String
)

private val logger = LoggerFactory.getLogger(LocationInfo::class.java)

/**
 * Определяет местоположение из аргументов, браузера или автоматически по IP.
 * Приоритет: явные координаты > координаты браузера > определение по IP
 */
suspend fun getLocationFromArguments(
    arguments: JsonObject,
    clientIp: String?,
    userLocation: UserLocation?,
    geolocationService: GeolocationService,
    ): LocationInfo {
    val latitude = arguments["latitude"]?.jsonPrimitive?.doubleOrNull
    val longitude = arguments["longitude"]?.jsonPrimitive?.doubleOrNull

    // Приоритет 1: Если координаты указаны явно в аргументах инструмента, используем их
    if (latitude != null && longitude != null) {
        logger.info("Используются координаты из аргументов инструмента: lat=$latitude, lon=$longitude")
        return LocationInfo(latitude, longitude, "explicit")
    }

    // Приоритет 2: Если есть координаты от браузера, используем их
    if (userLocation != null) {
        logger.info("Используются координаты от браузера: lat=${userLocation.latitude}, lon=${userLocation.longitude}")
        return LocationInfo(userLocation.latitude, userLocation.longitude, "browser_geolocation")
    }

    // Приоритет 3: Определяем местоположение по IP
    logger.info("Координаты не указаны, определяем местоположение по IP: $clientIp")
    val locationData = geolocationService.getLocationFromIP(clientIp)
    logger.info("Определено местоположение по IP: ${locationData.city}, ${locationData.country} (${locationData.latitude}, ${locationData.longitude})")

    return LocationInfo(locationData.latitude, locationData.longitude, "auto_ip")
}
