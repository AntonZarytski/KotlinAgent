"""
Упрощенная реализация MCP инструментов без использования MCP SDK.
Совместима с Python 3.9+.

Предоставляет два инструмента:
1. get_weather_forecast - прогноз погоды через Open-Meteo API
2. get_solar_activity - данные о солнечной активности
"""

import json
import httpx
from typing import Dict, Any, List

from logger import get_logger

logger = get_logger(__name__)


class MCPTools:
    """
    Простая реализация MCP инструментов без SDK.
    Работает напрямую с API и предоставляет описания инструментов в формате Anthropic.
    """

    @staticmethod
    def get_weather_forecast(latitude: float, longitude: float, units: str = "metric") -> str:
        """
        Получает прогноз погоды по географическому местоположению.

        Args:
            latitude: Широта местоположения
            longitude: Долгота местоположения
            units: Единицы измерения ('metric' или 'imperial')

        Returns:
            JSON строка с данными о погоде
        """
        logger.info(f"get_weather_forecast вызван: lat={latitude}, lon={longitude}, units={units}")

        try:
            # Open-Meteo API - бесплатный публичный API без ключа
            url = "https://api.open-meteo.com/v1/forecast"
            params = {
                "latitude": latitude,
                "longitude": longitude,
                "current": "temperature_2m,weather_code,wind_speed_10m,precipitation_probability",
                "temperature_unit": "celsius" if units == "metric" else "fahrenheit",
                "wind_speed_unit": "kmh" if units == "metric" else "mph"
            }

            with httpx.Client(timeout=10.0) as client:
                response = client.get(url, params=params)
                response.raise_for_status()
                data = response.json()

            # Извлекаем текущую погоду
            current = data.get("current", {})

            # Маппинг WMO Weather codes в описание
            weather_code = current.get("weather_code", 0)
            weather_descriptions = {
                0: "Ясно",
                1: "Преимущественно ясно",
                2: "Переменная облачность",
                3: "Облачно",
                45: "Туман",
                48: "Изморось",
                51: "Небольшая морось",
                53: "Морось",
                55: "Сильная морось",
                61: "Небольшой дождь",
                63: "Дождь",
                65: "Сильный дождь",
                71: "Небольшой снег",
                73: "Снег",
                75: "Сильный снег",
                77: "Снежные зерна",
                80: "Ливневый дождь",
                81: "Ливневый дождь умеренный",
                82: "Сильный ливень",
                85: "Снежный ливень",
                86: "Сильный снежный ливень",
                95: "Гроза",
                96: "Гроза с градом",
                99: "Гроза с сильным градом"
            }

            result = {
                "temperature": current.get("temperature_2m"),
                "weather": weather_descriptions.get(weather_code, f"Код погоды: {weather_code}"),
                "wind_speed": current.get("wind_speed_10m"),
                "precipitation_probability": current.get("precipitation_probability", 0)
            }

            logger.info(f"get_weather_forecast результат: {result}")
            return json.dumps(result, ensure_ascii=False)

        except httpx.HTTPError as e:
            logger.error(f"HTTP ошибка при получении погоды: {e}")
            return json.dumps({"error": f"HTTP ошибка: {str(e)}"}, ensure_ascii=False)
        except Exception as e:
            logger.error(f"Ошибка get_weather_forecast: {e}")
            return json.dumps({"error": str(e)}, ensure_ascii=False)

    @staticmethod
    def get_solar_activity(latitude: float, longitude: float) -> str:
        """
        Получает данные о солнечной активности и вероятности полярных сияний.

        Args:
            latitude: Широта местоположения
            longitude: Долгота местоположения

        Returns:
            JSON строка с данными о солнечной активности
        """
        logger.info(f"get_solar_activity вызван: lat={latitude}, lon={longitude}")

        try:
            # Используем NOAA Space Weather Prediction Center API
            # Получаем текущий Kp-index
            url = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"

            with httpx.Client(timeout=10.0) as client:
                response = client.get(url)
                response.raise_for_status()
                data = response.json()

            # Берем последнее значение Kp-index (исключая заголовок)
            if len(data) > 1:
                latest = data[-1]  # Последняя запись
                kp_index = float(latest[1])  # Второй элемент - значение Kp
            else:
                kp_index = 0.0

            # Определяем уровень активности
            if kp_index < 4:
                activity_level = "низкий"
            elif kp_index < 6:
                activity_level = "средний"
            else:
                activity_level = "высокий"

            # Вычисляем вероятность видимости полярного сияния
            abs_latitude = abs(latitude)

            if abs_latitude >= 60:
                if kp_index >= 5:
                    aurora_probability = 80
                elif kp_index >= 3:
                    aurora_probability = 50
                else:
                    aurora_probability = 20
            elif abs_latitude >= 50:
                if kp_index >= 6:
                    aurora_probability = 60
                elif kp_index >= 5:
                    aurora_probability = 30
                else:
                    aurora_probability = 10
            else:
                if kp_index >= 7:
                    aurora_probability = 30
                elif kp_index >= 6:
                    aurora_probability = 15
                else:
                    aurora_probability = 5

            result = {
                "kp_index": kp_index,
                "activity_level": activity_level,
                "aurora_visibility_probability": aurora_probability
            }

            logger.info(f"get_solar_activity результат: {result}")
            return json.dumps(result, ensure_ascii=False)

        except httpx.HTTPError as e:
            logger.error(f"HTTP ошибка при получении солнечной активности: {e}")
            return json.dumps({"error": f"HTTP ошибка: {str(e)}"}, ensure_ascii=False)
        except Exception as e:
            logger.error(f"Ошибка get_solar_activity: {e}")
            return json.dumps({"error": str(e)}, ensure_ascii=False)

    @staticmethod
    def get_tools_definitions() -> List[Dict[str, Any]]:
        """
        Возвращает описания инструментов в формате Anthropic API.

        Returns:
            Список определений инструментов
        """
        return [
            {
                "name": "get_weather_forecast",
                "description": "Получает прогноз погоды по географическому местоположению. Возвращает текущую температуру, описание погоды, скорость ветра и вероятность осадков.",
                "input_schema": {
                    "type": "object",
                    "properties": {
                        "latitude": {
                            "type": "number",
                            "description": "Широта местоположения (например, 55.7558 для Москвы)"
                        },
                        "longitude": {
                            "type": "number",
                            "description": "Долгота местоположения (например, 37.6173 для Москвы)"
                        },
                        "units": {
                            "type": "string",
                            "enum": ["metric", "imperial"],
                            "description": "Единицы измерения: metric (Цельсий, км/ч) или imperial (Фаренгейт, миль/ч)",
                            "default": "metric"
                        }
                    },
                    "required": ["latitude", "longitude"]
                }
            },
            {
                "name": "get_solar_activity",
                "description": "Получает данные о солнечной активности и вероятности наблюдения полярных сияний для указанного местоположения. Возвращает Kp-индекс, уровень активности и вероятность видимости авроры.",
                "input_schema": {
                    "type": "object",
                    "properties": {
                        "latitude": {
                            "type": "number",
                            "description": "Широта местоположения (например, 69.6492 для Мурманска)"
                        },
                        "longitude": {
                            "type": "number",
                            "description": "Долгота местоположения (например, 30.0450 для Мурманска)"
                        }
                    },
                    "required": ["latitude", "longitude"]
                }
            }
        ]

    @staticmethod
    def call_tool(tool_name: str, arguments: Dict[str, Any]) -> str:
        """
        Вызывает указанный инструмент с заданными аргументами.

        Args:
            tool_name: Имя инструмента
            arguments: Аргументы для инструмента

        Returns:
            Результат выполнения инструмента (JSON строка)

        Raises:
            ValueError: Если инструмент не найден
        """
        if tool_name == "get_weather_forecast":
            latitude = arguments.get("latitude")
            longitude = arguments.get("longitude")
            units = arguments.get("units", "metric")
            return MCPTools.get_weather_forecast(latitude, longitude, units)
        elif tool_name == "get_solar_activity":
            latitude = arguments.get("latitude")
            longitude = arguments.get("longitude")
            return MCPTools.get_solar_activity(latitude, longitude)
        else:
            raise ValueError(f"Неизвестный инструмент: {tool_name}")
