package com.claude.agent.llm.mcp.local

import com.claude.agent.common.LocalToolDefinition
import com.claude.agent.llm.mcp.Mcp
import com.claude.agent.llm.mcp.SOLAR
import com.claude.agent.models.UserLocation
import com.claude.agent.service.GeolocationService
import com.claude.agent.llm.mcp.local.model.getLocationFromArguments
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.math.abs

class SolarActivityMcp(
    val httpClient: HttpClient,
    val geolocationService: GeolocationService,
) : Mcp.Local {
    private val logger = LoggerFactory.getLogger(SolarActivityMcp::class.java)

    override val tool: Pair<String, LocalToolDefinition> = Pair(
        first = SOLAR,
        second = LocalToolDefinition(
            name = SOLAR,
            description = "Получает данные о солнечной активности и вероятности наблюдения полярных сияний для указанного местоположения. Возвращает Kp-индекс, уровень активности и вероятность видимости авроры. Если координаты не указаны, местоположение определяется автоматически по IP-адресу.",
            enabled = true,
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

    override suspend fun executeTool(
        arguments: JsonObject,
        clientIp: String?,
        userLocation: UserLocation?,
        sessionId: String?
    ): String {
        return getSolarActivity(arguments, clientIp, userLocation)
    }

    private suspend fun getSolarActivity(
        arguments: JsonObject,
        clientIp: String? = null,
        userLocation: UserLocation? = null
    ): String {
        val location = getLocationFromArguments(
            arguments = arguments,
            clientIp = clientIp,
            userLocation = userLocation,
            geolocationService = geolocationService
        )
        val latitude: Double = location.latitude
        val longitude: Double = location.longitude
        val locationSource = location.source

        logger.info("get_solar_activity вызван: lat=$latitude, lon=$longitude, source=$locationSource")

        return try {
            val response: String = httpClient.get("https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json")
                .bodyAsText()

            val json = Json.Default.parseToJsonElement(response).jsonArray
            val kpIndex = if (json.size > 1) {
                json.last().jsonArray[1].jsonPrimitive.double
            } else 0.0

            val activityLevel = when {
                kpIndex < 4 -> "низкий"
                kpIndex < 6 -> "средний"
                else -> "высокий"
            }

            val absLatitude = abs(latitude)
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