package com.claude.agent.services

import com.claude.agent.models.UserLocation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

class LocalMCPClient(
    private val httpClient: HttpClient,
    private val geolocationService: GeolocationService = GeolocationService(httpClient),
) {

    private val logger = LoggerFactory.getLogger(LocalMCPClient::class.java)

    private val tools by lazy {
        mapOf(
            "get_weather_forecast" to ToolDefinition(
                name = "get_weather_forecast",
                description = "Получает прогноз погоды по географическому местоположению. Возвращает текущую температуру, описание погоды, скорость ветра и вероятность осадков. Если координаты не указаны, местоположение определяется автоматически по IP-адресу.",
                input_schema = JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(
                            mapOf(
                                "latitude" to JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("number"),
                                        "description" to JsonPrimitive("Широта местоположения. Опционально - если не указано, определяется автоматически.")
                                    )
                                ),
                                "longitude" to JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("number"),
                                        "description" to JsonPrimitive("Долгота местоположения. Опционально - если не указано, определяется автоматически.")
                                    )
                                ),
                                "units" to JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("string"),
                                        "enum" to JsonArray(
                                            listOf(
                                                JsonPrimitive("metric"),
                                                JsonPrimitive("imperial")
                                            )
                                        ),
                                        "description" to JsonPrimitive("Единицы измерения: metric (Цельсий, км/ч) или imperial (Фаренгейт, миль/ч)"),
                                        "default" to JsonPrimitive("metric")
                                    )
                                )
                            )
                        ),
                        "required" to JsonArray(emptyList())
                    )
                ),
            ),
            "get_solar_activity" to ToolDefinition(
                name = "get_solar_activity",
                description = "Получает данные о солнечной активности и вероятности наблюдения полярных сияний для указанного местоположения. Возвращает Kp-индекс, уровень активности и вероятность видимости авроры. Если координаты не указаны, местоположение определяется автоматически по IP-адресу.",
                input_schema = JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(
                            mapOf(
                                "latitude" to JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("number"),
                                        "description" to JsonPrimitive("Широта местоположения. Опционально - если не указано, определяется автоматически.")
                                    )
                                ),
                                "longitude" to JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("number"),
                                        "description" to JsonPrimitive("Долгота местоположения. Опционально - если не указано, определяется автоматически.")
                                    )
                                )
                            )
                        ),
                        "required" to JsonArray(emptyList())
                    )
                )
            ),
        )
    }

    fun getToolsDefinitions(enabledTools: List<String>): List<ToolDefinition> {
        return tools.map { it.value }.filter { it.name in enabledTools }
    }

    fun getAllTools(): List<ToolDefinition> {
        val local = tools.map { it.value }
        return local
    }

    /**
     * Определяет местоположение из аргументов, браузера или автоматически по IP.
     * Приоритет: явные координаты > координаты браузера > определение по IP
     */
    private suspend fun getLocationFromArguments(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?
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

    /**
     * Информация о местоположении с источником данных.
     */
    private data class LocationInfo(
        val latitude: Double,
        val longitude: Double,
        val source: String  // "explicit" или "auto_ip"
    )

    suspend fun getWeatherForecast(
        arguments: JsonObject,
        clientIp: String? = null,
        userLocation: UserLocation? = null
    ): String {
        val location = getLocationFromArguments(arguments, clientIp, userLocation)
        val latitude: Double = location.latitude
        val longitude: Double = location.longitude
        val locationSource = location.source
        val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"

        logger.info("get_weather_forecast вызван: lat=$latitude, lon=$longitude, units=$units, source=$locationSource")

        return try {
            val tempUnit = if (units == "metric") "celsius" else "fahrenheit"
            val windUnit = if (units == "metric") "kmh" else "mph"

            val response: String = httpClient.get("https://api.open-meteo.com/v1/forecast") {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("current", "temperature_2m,weather_code,wind_speed_10m,precipitation_probability")
                parameter("temperature_unit", tempUnit)
                parameter("wind_speed_unit", windUnit)
            }.bodyAsText()

            val json = Json.parseToJsonElement(response).jsonObject
            val current = json["current"]?.jsonObject ?: JsonObject(emptyMap())
            val weatherCode = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0
            val weatherDescription = getWeatherDescription(weatherCode)

            val result = JsonObject(
                mapOf(
                    "temperature" to (current["temperature_2m"] ?: JsonPrimitive(null)),
                    "weather" to JsonPrimitive(weatherDescription),
                    "wind_speed" to (current["wind_speed_10m"] ?: JsonPrimitive(null)),
                    "precipitation_probability" to (current["precipitation_probability"] ?: JsonPrimitive(0)),
                    "location_source" to JsonPrimitive(locationSource)
                )
            )

            result.toString()
        } catch (e: Exception) {
            logger.error("Ошибка get_weather_forecast: ${e.message}")
            """{"error": "HTTP ошибка: ${e.message}"}"""
        }
    }

    suspend fun getSolarActivity(
        arguments: JsonObject,
        clientIp: String? = null,
        userLocation: UserLocation? = null
    ): String {
        val location = getLocationFromArguments(arguments, clientIp, userLocation)
        val latitude: Double = location.latitude
        val longitude: Double = location.longitude
        val locationSource = location.source

        logger.info("get_solar_activity вызван: lat=$latitude, lon=$longitude, source=$locationSource")

        return try {
            val response: String = httpClient.get("https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json")
                .bodyAsText()

            val json = Json.parseToJsonElement(response).jsonArray
            val kpIndex = if (json.size > 1) {
                json.last().jsonArray[1].jsonPrimitive.double
            } else 0.0

            val activityLevel = when {
                kpIndex < 4 -> "низкий"
                kpIndex < 6 -> "средний"
                else -> "высокий"
            }

            val absLatitude = kotlin.math.abs(latitude)
            val auroraProb = calculateAuroraProbability(absLatitude, kpIndex)

            val result = JsonObject(
                mapOf(
                    "kp_index" to JsonPrimitive(kpIndex),
                    "activity_level" to JsonPrimitive(activityLevel),
                    "aurora_visibility_probability" to JsonPrimitive(auroraProb),
                    "location_source" to JsonPrimitive(locationSource)
                )
            )

            result.toString()
        } catch (e: Exception) {
            logger.error("Ошибка get_solar_activity: ${e.message}")
            """{"error": "HTTP ошибка: ${e.message}"}"""
        }
    }

    private fun getWeatherDescription(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Облачно"
        45, 48 -> "Туман"
        51, 53, 55 -> "Морось"
        61 -> "Небольшой дождь"
        63 -> "Дождь"
        65 -> "Сильный дождь"
        71, 73, 75 -> "Снег"
        80, 81, 82 -> "Ливень"
        95, 96, 99 -> "Гроза"
        else -> "Код погоды: $code"
    }

    private fun calculateAuroraProbability(absLatitude: Double, kpIndex: Double): Int {
        return when {
            absLatitude >= 60 -> when {
                kpIndex >= 5 -> 80
                kpIndex >= 3 -> 50
                else -> 20
            }

            absLatitude >= 50 -> when {
                kpIndex >= 6 -> 60
                kpIndex >= 5 -> 30
                else -> 10
            }

            else -> when {
                kpIndex >= 7 -> 30
                kpIndex >= 6 -> 15
                else -> 5
            }
        }
    }
}