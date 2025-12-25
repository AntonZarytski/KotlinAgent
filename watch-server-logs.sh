#!/bin/bash

# Скрипт для просмотра логов сервера в реальном времени

SERVER_IP="95.217.187.167" 
SERVER_USER="agent"

# Цвета
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}🔍 Подключаемся к серверу для просмотра логов в реальном времени...${NC}"
echo -e "${GREEN}Нажмите Ctrl+C для выхода${NC}"
echo -e "${YELLOW}Сервер: $SERVER_USER@$SERVER_IP${NC}"
echo

# Функция для обработки сигнала выхода
cleanup() {
    echo -e "\n${RED}🛑 Отключение от сервера...${NC}"
    exit 0
}

# Устанавливаем обработчик сигнала
trap cleanup SIGINT SIGTERM

# Добавляем заголовок с временной меткой
echo -e "${BLUE}=== Стрим логов начат: $(date) ===${NC}"
echo -e "${BLUE}=== Сервер: $SERVER_USER@$SERVER_IP ===${NC}"
echo -e "${BLUE}=========================================${NC}"

# Подключаемся и следим за логами с цветным выводом
ssh -i "$HOME/.ssh/id_rsa" -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" 'journalctl -u kotlinagent -f --no-pager --output=short-iso' | while read line; do
    # Добавляем цвета для разных типов сообщений
    if echo "$line" | grep -q "ERROR"; then
        echo -e "${RED}$line${NC}"
    elif echo "$line" | grep -q "WARN"; then
        echo -e "${YELLOW}$line${NC}"
    elif echo "$line" | grep -q "INFO"; then
        echo -e "${GREEN}$line${NC}"
    elif echo "$line" | grep -q "DEBUG"; then
        echo -e "${BLUE}$line${NC}"
    else
        echo "$line"
    fi
done