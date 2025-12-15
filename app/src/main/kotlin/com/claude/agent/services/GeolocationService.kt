package com.claude.agent.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Сервис для определения геолокации пользователя по IP-адресу.
 * 
 * Использует бесплатный API ipapi.co для определения местоположения.
 * Предоставляет автоматическое определение координат для MCP инструментов.
 */
class GeolocationService(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(GeolocationService::class.java)
    
    // Кэш для хранения результатов геолокации (IP -> Location)
    private val locationCache = mutableMapOf<String, LocationData>()
    
    // Местоположение по умолчанию (Москва) на случай ошибки
    private val defaultLocation = LocationData(
        latitude = 55.7558,
        longitude = 37.6173,
        city = "Moscow",
        country = "Russia"
    )
    
    /**
     * Определяет местоположение пользователя по IP-адресу.
     * 
     * @param ipAddress IP-адрес клиента
     * @return LocationData с координатами и информацией о местоположении
     */
    suspend fun getLocationFromIP(ipAddress: String?): LocationData {
        // Если IP не предоставлен или это localhost, возвращаем местоположение по умолчанию
        if (ipAddress.isNullOrBlank() || isLocalhost(ipAddress)) {
            logger.info("IP адрес localhost или не предоставлен, используем местоположение по умолчанию: ${defaultLocation.city}")
            return defaultLocation
        }
        
        // Проверяем кэш
        locationCache[ipAddress]?.let {
            logger.info("Местоположение для IP $ipAddress найдено в кэше: ${it.city}, ${it.country}")
            return it
        }
        
        return try {
            logger.info("Определение местоположения для IP: $ipAddress")
            
            // Используем ipapi.co API (бесплатный, без ключа, лимит 1000 запросов/день)
            val response: String = httpClient.get("https://ipapi.co/$ipAddress/json/").bodyAsText()
            
            val json = Json { ignoreUnknownKeys = true }
            val apiResponse = json.decodeFromString<IpApiResponse>(response)
            
            // Проверяем, что получили валидные данные
            if (apiResponse.error != null) {
                logger.warn("Ошибка от ipapi.co: ${apiResponse.error}, используем местоположение по умолчанию")
                return defaultLocation
            }
            
            val location = LocationData(
                latitude = apiResponse.latitude ?: defaultLocation.latitude,
                longitude = apiResponse.longitude ?: defaultLocation.longitude,
                city = apiResponse.city ?: defaultLocation.city,
                country = apiResponse.country_name ?: defaultLocation.country
            )
            
            // Сохраняем в кэш
            locationCache[ipAddress] = location
            
            logger.info("Местоположение определено: ${location.city}, ${location.country} (${location.latitude}, ${location.longitude})")
            
            location
            
        } catch (e: Exception) {
            logger.error("Ошибка при определении местоположения для IP $ipAddress: ${e.message}")
            logger.info("Используем местоположение по умолчанию: ${defaultLocation.city}")
            defaultLocation
        }
    }
    
    /**
     * Проверяет, является ли IP-адрес локальным.
     */
    private fun isLocalhost(ip: String): Boolean {
        return ip == "127.0.0.1" || 
               ip == "localhost" || 
               ip == "0:0:0:0:0:0:0:1" || 
               ip == "::1" ||
               ip.startsWith("192.168.") ||
               ip.startsWith("10.") ||
               ip.startsWith("172.16.") ||
               ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") ||
               ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") ||
               ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") ||
               ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") ||
               ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") ||
               ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") ||
               ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") ||
               ip.startsWith("172.31.")
    }
}

/**
 * Данные о местоположении пользователя.
 */
@Serializable
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val city: String,
    val country: String
)

/**
 * Ответ от ipapi.co API.
 */
@Serializable
private data class IpApiResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val country_name: String? = null,
    val error: Boolean? = null,
    val reason: String? = null
)

