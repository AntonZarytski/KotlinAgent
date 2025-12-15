"""
Клиент для работы с Claude API.

Этот модуль инкапсулирует логику взаимодействия с Anthropic Claude API,
включая отправку запросов и обработку ответов.
"""

import os
import traceback
import time
from typing import Dict, Tuple, Optional, List, Any

from anthropic import Anthropic, APIError, APIConnectionError, RateLimitError, AuthenticationError

from constants import (
    CLAUDE_MODEL,
    MAX_TOKENS,
    OUTPUT_FORMAT_DEFAULT,
    VALID_OUTPUT_FORMATS,
    MAX_REPLY_LOG_LENGTH,
    ERROR_INVALID_API_KEY,
    ERROR_RATE_LIMIT,
    ERROR_CONNECTION,
    ERROR_API_KEY_NOT_SET,
    ERROR_API_KEY_NOT_FOUND,
    HTTP_TOO_MANY_REQUESTS,
    HTTP_INTERNAL_SERVER_ERROR,
    HTTP_SERVICE_UNAVAILABLE
)
from prompts import get_system_prompt, get_user_message, SPEC_END_MARKER
from logger import get_logger
from mcp_tools import MCPTools

logger = get_logger(__name__)


class ClaudeClient:
    """
    Клиент для взаимодействия с Claude API.
    
    Attributes:
        client: Экземпляр Anthropic клиента
        api_key: API ключ для аутентификации
    """
    
    def __init__(self, api_key: Optional[str] = None, enable_mcp: bool = True):
        """
        Инициализирует Claude клиент.

        Args:
            api_key: API ключ Anthropic. Если None, берется из переменной окружения
            enable_mcp: Включить MCP инструменты (по умолчанию True)

        Raises:
            ValueError: Если API ключ не найден
        """
        self.api_key = api_key or os.environ.get("ANTHROPIC_API_KEY")

        if not self.api_key:
            logger.error(ERROR_API_KEY_NOT_FOUND)
            raise ValueError(ERROR_API_KEY_NOT_SET)

        logger.info(f"API ключ загружен: {self.api_key[:10]}...{self.api_key[-4:] if len(self.api_key) > 14 else ''}")

        try:
            self.client = Anthropic(api_key=self.api_key)
            logger.info("Anthropic клиент успешно инициализирован")
        except Exception as e:
            logger.error(f"Ошибка инициализации Anthropic клиента: {str(e)}")
            raise

        # Инициализация MCP инструментов
        self.enable_mcp = enable_mcp
        if self.enable_mcp:
            logger.info("MCP инструменты включены")
    
    def validate_output_format(self, output_format: str) -> bool:
        """
        Проверяет валидность формата вывода.
        
        Args:
            output_format: Формат вывода для проверки
            
        Returns:
            True если формат валиден, False иначе
        """
        return output_format in VALID_OUTPUT_FORMATS
    
    def _get_mcp_tools(self, enabled_tools: Optional[List[str]] = None) -> Optional[List[Dict[str, Any]]]:
        """
        Получает список MCP инструментов в формате Anthropic.

        Args:
            enabled_tools: Список имен включенных инструментов. Если None, возвращаются все.

        Returns:
            Список инструментов или None при ошибке
        """
        if not self.enable_mcp:
            return None

        try:
            all_tools = MCPTools.get_tools_definitions()

            # Если enabled_tools не указан или пустой, возвращаем все
            if not enabled_tools:
                logger.info(f"Получено {len(all_tools)} MCP инструментов (все)")
                return all_tools

            # Фильтруем только включенные инструменты
            filtered_tools = [
                tool for tool in all_tools
                if tool['name'] in enabled_tools
            ]

            logger.info(f"Получено {len(filtered_tools)} MCP инструментов (из {len(enabled_tools)} запрошенных)")
            return filtered_tools if filtered_tools else None
        except Exception as e:
            logger.error(f"Ошибка получения MCP инструментов: {e}")
            return None

    def _handle_tool_calls(
        self,
        message_response: Any,
        messages: List[Dict[str, Any]],
        api_params: Dict[str, Any],
        max_iterations: int = 5
    ) -> Tuple[Optional[str], Optional[Dict]]:
        """
        Обрабатывает tool_use блоки от Claude и выполняет цепочки вызовов.

        Args:
            message_response: Ответ от Claude API
            messages: Список сообщений
            api_params: Параметры API запроса
            max_iterations: Максимальное количество итераций tool calls

        Returns:
            Кортеж (финальный_ответ, usage_info)
        """
        iteration = 0
        current_response = message_response
        total_input_tokens = current_response.usage.input_tokens
        total_output_tokens = current_response.usage.output_tokens

        while iteration < max_iterations:
            # Проверяем наличие tool_use в content
            has_tool_use = any(
                block.type == "tool_use"
                for block in current_response.content
            )

            if not has_tool_use:
                # Нет tool_use, возвращаем текстовый ответ
                text_blocks = [
                    block.text for block in current_response.content
                    if hasattr(block, 'text')
                ]
                final_text = "".join(text_blocks)
                usage = {
                    'input_tokens': total_input_tokens,
                    'output_tokens': total_output_tokens
                }
                return final_text, usage

            # Есть tool_use, обрабатываем
            iteration += 1
            logger.info(f"=== Tool call итерация {iteration} ===")

            # Добавляем ответ ассистента с tool_use в историю
            messages.append({
                "role": "assistant",
                "content": current_response.content
            })

            # Собираем результаты всех tool calls
            tool_results = []
            for block in current_response.content:
                if block.type == "tool_use":
                    tool_name = block.name
                    tool_input = block.input
                    tool_use_id = block.id

                    logger.info(f"Вызов инструмента: {tool_name}")
                    logger.info(f"Аргументы: {tool_input}")

                    try:
                        # Вызываем MCP инструмент
                        start_time = time.time()
                        result = MCPTools.call_tool(tool_name, tool_input)
                        elapsed = time.time() - start_time

                        logger.info(f"Результат {tool_name} (за {elapsed:.2f}s): {result[:200]}..." if result and len(result) > 200 else f"Результат {tool_name}: {result}")

                        tool_results.append({
                            "type": "tool_result",
                            "tool_use_id": tool_use_id,
                            "content": result or "Инструмент не вернул результат"
                        })

                    except Exception as e:
                        logger.error(f"Ошибка выполнения инструмента {tool_name}: {e}")
                        tool_results.append({
                            "type": "tool_result",
                            "tool_use_id": tool_use_id,
                            "content": f"Ошибка: {str(e)}",
                            "is_error": True
                        })

            # Добавляем результаты инструментов
            messages.append({
                "role": "user",
                "content": tool_results
            })

            # Повторный вызов Claude с результатами
            api_params["messages"] = messages
            current_response = self.client.messages.create(**api_params)

            # Обновляем счетчики токенов
            total_input_tokens += current_response.usage.input_tokens
            total_output_tokens += current_response.usage.output_tokens

        # Достигнут лимит итераций
        logger.warning(f"Достигнут лимит итераций tool calls ({max_iterations})")
        text_blocks = [
            block.text for block in current_response.content
            if hasattr(block, 'text')
        ]
        final_text = "".join(text_blocks)
        usage = {
            'input_tokens': total_input_tokens,
            'output_tokens': total_output_tokens
        }
        return final_text, usage

    def send_message(
        self,
        user_message: str,
        output_format: str = OUTPUT_FORMAT_DEFAULT,
        model: str = CLAUDE_MODEL,
        max_tokens: int = MAX_TOKENS,
        spec_mode: bool = False,
        conversation_history: Optional[List[Dict[str, str]]] = None,
        temperature: float = 1.0,
        enabled_tools: Optional[List[str]] = None
    ) -> Tuple[Optional[str], Optional[Dict], int, Optional[Dict]]:
        """
        Отправляет сообщение в Claude API и возвращает ответ.

        Args:
            user_message: Сообщение пользователя
            output_format: Формат вывода ('default', 'json', 'xml')
            model: Модель Claude для использования
            max_tokens: Максимальное количество токенов в ответе
            spec_mode: Режим сбора уточняющих данных (True/False)
            conversation_history: История диалога (список сообщений с role и content)
            temperature: Температура генерации (0.0 - 1.0)
            enabled_tools: Список имен включенных инструментов. Если None, используются все.

        Returns:
            Кортеж из (ответ, ошибка, HTTP код, usage):
            - ответ: Текст ответа от Claude или None при ошибке
            - ошибка: Словарь с описанием ошибки или None при успехе
            - HTTP код: Код статуса HTTP
            - usage: Словарь с информацией о токенах (input_tokens, output_tokens) или None
        """
        try:
            # Валидация формата
            if not self.validate_output_format(output_format):
                logger.warning(f"Неподдерживаемый формат: {output_format}, используется default")
                output_format = OUTPUT_FORMAT_DEFAULT

            # Получаем системный промпт
            try:
                system_prompt = get_system_prompt(output_format, spec_mode)
            except ValueError as e:
                logger.error(f"Ошибка получения системного промпта: {str(e)}")
                return None, {'error': str(e)}, HTTP_INTERNAL_SERVER_ERROR, None

            # Получаем чистое сообщение пользователя
            clean_user_message = get_user_message(user_message)

            # Формируем массив сообщений с историей
            messages = []

            # Добавляем историю диалога (если есть)
            if conversation_history:
                for msg in conversation_history:
                    if msg.get('role') in ('user', 'assistant') and msg.get('content'):
                        messages.append({
                            "role": msg['role'],
                            "content": msg['content']
                        })

            # Добавляем текущее сообщение пользователя
            messages.append({
                "role": "user",
                "content": clean_user_message
            })

            # Получаем MCP инструменты с учетом фильтра
            tools = self._get_mcp_tools(enabled_tools)

            # Формируем параметры запроса
            api_params = {
                "model": model,
                "max_tokens": max_tokens,
                "system": system_prompt,
                "messages": messages,
                "temperature": temperature
            }

            # Добавляем инструменты если есть
            if tools:
                api_params["tools"] = tools

            # Добавляем stop_sequences для spec mode
            if spec_mode: api_params["stop_sequences"] = [SPEC_END_MARKER]

            # === Детальное логирование параметров API запроса ===
            logger.info("=== Отправка запроса к Claude API ===")
            logger.info(f"Модель: {model}")
            logger.info(f"Max tokens: {max_tokens}")
            logger.info(f"Temperature: {temperature}")
            if tools:
                logger.info(f"MCP инструменты: {[t['name'] for t in tools]}")
            # Логируем system prompt (первые 200 символов)
            system_preview = system_prompt[:200] + "..." if len(system_prompt) > 200 else system_prompt
            logger.info(f"System prompt ({len(system_prompt)} символов): \"{system_preview}\"")

            # Логируем сообщения
            logger.info(f"Сообщения ({len(messages)} шт.):")
            for i, msg in enumerate(messages, 1):
                content = msg['content']
                if isinstance(content, str):
                    content_preview = content[:100] + "..." if len(content) > 100 else content
                    # Убираем переносы строк для компактности
                    content_preview = content_preview.replace('\n', ' ').replace('\r', '')
                    logger.info(f"  [{i}] {msg['role']} ({len(content)} символов): \"{content_preview}\"")
                else:
                    logger.info(f"  [{i}] {msg['role']} (structured content)")

            if spec_mode:
                logger.info(f"Stop sequences: {api_params.get('stop_sequences', [])}")
            logger.info("=====================================")

            # Отправляем запрос к API
            request_start = time.time()
            message = self.client.messages.create(**api_params)
            request_time = time.time() - request_start

            # Проверяем наличие tool_use
            has_tool_use = any(
                block.type == "tool_use"
                for block in message.content
                if hasattr(block, 'type')
            )

            if has_tool_use and tools:
                # Обрабатываем tool calls
                logger.info("Ответ содержит tool_use, начинаем обработку...")
                raw_reply, usage = self._handle_tool_calls(message, messages, api_params)
                logger.info(f"Tool calls завершены за {time.time() - request_start:.2f}s")
            else:
                # Обычный текстовый ответ
                raw_reply = message.content[0].text
                usage = {
                    'input_tokens': message.usage.input_tokens,
                    'output_tokens': message.usage.output_tokens
                }

            logger.info(f"Сырой ответ от Claude: {raw_reply[:MAX_REPLY_LOG_LENGTH]}...")
            logger.info(f"Использовано токенов: input={usage['input_tokens']}, output={usage['output_tokens']}")
            logger.info(f"Общее время запроса: {time.time() - request_start:.2f}s")

            return raw_reply, None, 200, usage

        except AuthenticationError as e:
            logger.error(f"Ошибка аутентификации API: {e}\n{traceback.format_exc()}")
            return None, {'error': ERROR_INVALID_API_KEY}, HTTP_INTERNAL_SERVER_ERROR, None

        except RateLimitError as e:
            logger.error(f"Превышен лимит запросов: {e}\n{traceback.format_exc()}")
            return None, {'error': ERROR_RATE_LIMIT}, HTTP_TOO_MANY_REQUESTS, None

        except APIConnectionError as e:
            logger.error(f"Ошибка соединения с API: {e}\n{traceback.format_exc()}")
            return None, {'error': ERROR_CONNECTION}, HTTP_SERVICE_UNAVAILABLE, None

        except APIError as e:
            logger.error(f"Ошибка Claude API: {e}\n{traceback.format_exc()}")
            return None, {'error': f'Ошибка Claude API: {str(e)}'}, HTTP_INTERNAL_SERVER_ERROR, None

        except Exception as e:
            logger.error(f"Неожиданная ошибка: {e}\n{traceback.format_exc()}")
            return None, {'error': f'Ошибка сервера: {str(e)}'}, HTTP_INTERNAL_SERVER_ERROR, None
    
    def is_api_key_configured(self) -> bool:
        return bool(self.api_key)

