#!/bin/bash

# Скрипт для мониторинга сервера через HTTP API в реальном времени

SERVER_IP="95.217.187.167"
HTTP_PORT="8001"
HTTPS_PORT="8443"

# Цвета
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Функция для очистки экрана и печати заголовка
print_header() {
    clear
    echo -e "${CYAN}=============================================${NC}"
    echo -e "${CYAN}   🖥️  Мониторинг сервера $SERVER_IP${NC}"
    echo -e "${CYAN}   📊 Обновление каждые 3 секунды${NC}"
    echo -e "${CYAN}   ⏹️  Для остановки нажмите Ctrl+C${NC}"
    echo -e "${CYAN}=============================================${NC}"
    echo -e "${YELLOW}Время: $(date '+%Y-%m-%d %H:%M:%S')${NC}"
    echo
}

# Функция для проверки доступности сервера
check_endpoint() {
    local port=$1
    local protocol=$2
    local endpoint=$3
    
    if [ "$protocol" = "HTTPS" ]; then
        response=$(curl -s --connect-timeout 3 --max-time 5 --insecure "https://$SERVER_IP:$port$endpoint" 2>/dev/null)
    else
        response=$(curl -s --connect-timeout 3 --max-time 5 "http://$SERVER_IP:$port$endpoint" 2>/dev/null)
    fi
    
    if [ $? -eq 0 ] && [ ! -z "$response" ]; then
        echo -e "${GREEN}✅ $protocol:$port$endpoint${NC}"
        if [ ${#response} -gt 100 ]; then
            echo "   ${response:0:100}..."
        else
            echo "   $response"
        fi
        return 0
    else
        echo -e "${RED}❌ $protocol:$port$endpoint - недоступен${NC}"
        return 1
    fi
}

# Функция для получения статуса агентов
check_agents() {
    echo -e "${BLUE}=== 📡 Статус агентов ===${NC}"
    
    # Пробуем HTTPS
    response=$(curl -s --connect-timeout 3 --max-time 5 --insecure "https://$SERVER_IP:$HTTPS_PORT/mcp/agents/status" 2>/dev/null)
    if [ $? -eq 0 ] && [ ! -z "$response" ]; then
        echo -e "${GREEN}HTTPS агенты:${NC} $response"
        return 0
    fi
    
    # Пробуем HTTP
    response=$(curl -s --connect-timeout 3 --max-time 5 "http://$SERVER_IP:$HTTP_PORT/mcp/agents/status" 2>/dev/null)
    if [ $? -eq 0 ] && [ ! -z "$response" ]; then
        echo -e "${GREEN}HTTP агенты:${NC} $response"
        return 0
    fi
    
    echo -e "${RED}❌ Статус агентов недоступен${NC}"
}

# Функция для проверки WebSocket (косвенно через health)
check_websocket_health() {
    echo -e "${BLUE}=== 🔌 WebSocket готовность ===${NC}"
    
    # Проверяем health endpoint, который косвенно говорит о готовности к WebSocket
    response=$(curl -s --connect-timeout 3 --max-time 5 --insecure "https://$SERVER_IP:$HTTPS_PORT/health" 2>/dev/null)
    if [ $? -eq 0 ] && [ ! -z "$response" ]; then
        echo -e "${GREEN}✅ Сервер готов к WebSocket подключениям${NC}"
    else
        echo -e "${RED}❌ Сервер может быть недоступен для WebSocket${NC}"
    fi
}

# Обработка Ctrl+C
cleanup() {
    echo -e "\n${YELLOW}🛑 Мониторинг остановлен${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

# Основной цикл мониторинга
while true; do
    print_header
    
    echo -e "${BLUE}=== 🌐 HTTP/HTTPS эндпоинты ===${NC}"
    check_endpoint $HTTP_PORT "HTTP" "/health"
    check_endpoint $HTTPS_PORT "HTTPS" "/health"
    check_endpoint $HTTP_PORT "HTTP" "/api"
    check_endpoint $HTTPS_PORT "HTTPS" "/ui"
    echo
    
    check_agents
    echo
    
    check_websocket_health
    echo
    
    echo -e "${BLUE}=== 🔄 Следующая проверка через 3 секунды ===${NC}"
    sleep 3
done