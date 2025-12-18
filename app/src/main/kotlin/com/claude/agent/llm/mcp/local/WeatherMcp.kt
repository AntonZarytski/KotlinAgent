package com.claude.agent.llm.mcp.local

import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.models.UserLocation
import com.claude.agent.service.GeolocationService
import com.claude.agent.llm.mcp.local.model.LocalToolDefinition
import com.claude.agent.llm.mcp.local.model.getLocationFromArguments
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

class WeatherMcp(
    val httpClient: HttpClient,
    val geolocationService: GeolocationService,
) : Mcp.Local {
    private val logger = LoggerFactory.getLogger(WeatherMcp::class.java)

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = "get_weather_forecast",
        second = LocalToolDefinition(
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
    )

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        return getWeatherForecast(arguments, clientIp, userLocation)
    }

    suspend fun getWeatherForecast(
        arguments: JsonObject,
        clientIp: String? = null,
        userLocation: UserLocation? = null
    ): String {
        val location = getLocationFromArguments(arguments, clientIp, userLocation, geolocationService)
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

            val json = Json.Default.parseToJsonElement(response).jsonObject
            val current = json["current"]?.jsonObject ?: JsonObject(emptyMap())
            val weatherCode = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0
            val weatherDescription = getWeatherDescription(weatherCode)

            val result = JsonObject(
                mapOf(
                    "temperature" to (current["temperature_2m"] ?: kotlinx.serialization.json.JsonPrimitive(null)),
                    "weather" to JsonPrimitive(weatherDescription),
                    "wind_speed" to (current["wind_speed_10m"] ?: kotlinx.serialization.json.JsonPrimitive(null)),
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
}