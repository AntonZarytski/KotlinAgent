#!/bin/bash

# Скрипт для постоянного мониторинга сервера

SERVER_IP="95.217.187.167"
SERVER_USER="agent"

# Цвета
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${PURPLE}🔍 Постоянный мониторинг сервера${NC}"
echo -e "${YELLOW}Сервер: $SERVER_USER@$SERVER_IP${NC}"
echo -e "${GREEN}Нажмите Ctrl+C для выхода${NC}"
echo

# Функция для обработки выхода
cleanup() {
    echo -e "\n${RED}🛑 Остановка мониторинга...${NC}"
    exit 0
}

# Функция для проверки доступности
check_connectivity() {
    if ssh -o ConnectTimeout=5 -o BatchMode=yes "$SERVER_USER@$SERVER_IP" 'exit' 2>/dev/null; then
        echo -e "${GREEN}✅ SSH соединение активно${NC}"
        return 0
    else
        echo -e "${RED}❌ SSH соединение недоступно${NC}"
        return 1
    fi
}

# Функция для проверки портов
check_ports() {
    echo -e "${CYAN}🔗 Проверка портов:${NC}"
    
    # HTTP 8001
    if curl -s --connect-timeout 3 "http://$SERVER_IP:8001/health" >/dev/null; then
        echo -e "   ${GREEN}✅ HTTP 8001 - OK${NC}"
    else
        echo -e "   ${RED}❌ HTTP 8001 - недоступен${NC}"
    fi
    
    # HTTPS 8443
    if curl -s --connect-timeout 3 --insecure "https://$SERVER_IP:8443/health" >/dev/null; then
        echo -e "   ${GREEN}✅ HTTPS 8443 - OK${NC}"
    else
        echo -e "   ${RED}❌ HTTPS 8443 - недоступен${NC}"
    fi
}

# Функция для показа статуса сервиса
check_service() {
    if check_connectivity; then
        echo -e "${BLUE}⚙️  Статус сервиса:${NC}"
        ssh "$SERVER_USER@$SERVER_IP" "systemctl is-active kotlinagent 2>/dev/null" | while read status; do
            if [ "$status" = "active" ]; then
                echo -e "   ${GREEN}✅ kotlinagent - $status${NC}"
            else
                echo -e "   ${RED}❌ kotlinagent - $status${NC}"
            fi
        done
    fi
}

trap cleanup SIGINT SIGTERM

echo -e "${BLUE}=== Начало мониторинга: $(date) ===${NC}"
echo

# Основной цикл мониторинга
while true; do
    clear
    echo -e "${PURPLE}🔍 Мониторинг сервера - $(date)${NC}"
    echo -e "${YELLOW}Сервер: $SERVER_USER@$SERVER_IP${NC}"
    echo "----------------------------------------"
    
    # Проверяем соединение
    if check_connectivity; then
        check_service
        echo
        check_ports
        echo
        
        echo -e "${BLUE}📊 Последние 5 строк логов:${NC}"
        ssh "$SERVER_USER@$SERVER_IP" 'journalctl -u kotlinagent -n 5 --no-pager --output=short' 2>/dev/null | while read line; do
            if echo "$line" | grep -q "ERROR"; then
                echo -e "   ${RED}$line${NC}"
            elif echo "$line" | grep -q "WARN"; then
                echo -e "   ${YELLOW}$line${NC}"
            elif echo "$line" | grep -q "INFO.*WebSocket\|INFO.*CONNECT"; then
                echo -e "   ${GREEN}$line${NC}"
            else
                echo "   $line"
            fi
        done
    else
        echo -e "${RED}❌ Сервер недоступен${NC}"
    fi
    
    echo
    echo -e "${CYAN}🔄 Обновление через 10 секунд... (Ctrl+C для выхода)${NC}"
    sleep 10
done