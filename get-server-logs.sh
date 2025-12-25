#!/bin/bash

# Скрипт для получения логов с сервера

SERVER_IP="95.217.187.167"
SERVER_USER="agent"  
SSH_KEY="$HOME/.ssh/id_rsa"  # Путь к вашему приватному ключу

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔍 Получение логов с сервера $SERVER_IP${NC}"
echo

# Функция для выполнения команд на сервере
run_remote() {
    echo -e "${YELLOW}📡 Выполняем на сервере: $1${NC}"
    ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" "$1"
    echo
}

# Функция для получения файла с сервера
get_file() {
    echo -e "${YELLOW}📥 Скачиваем файл: $1${NC}"
    scp -i "$SSH_KEY" -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP:$1" "./$(basename "$1")" 2>/dev/null || echo "❌ Файл не найден: $1"
    echo
}

# 1. Проверяем статус сервиса
echo -e "${GREEN}=== 1. Статус сервиса ===${NC}"
run_remote "systemctl status kotlinagent --no-pager"

# 2. Логи systemd сервиса
echo -e "${GREEN}=== 2. Логи systemd (последние 50 строк) ===${NC}"
run_remote "journalctl -u kotlinagent -n 50 --no-pager"

# 3. Логи приложения (если есть)
echo -e "${GREEN}=== 3. Логи приложения ===${NC}"
run_remote "ls -la /opt/kotlinagent/*.log /opt/kotlinagent/logs/ /var/log/kotlinagent* 2>/dev/null || echo 'Файлы логов не найдены в стандартных местах'"

# 4. Проверяем процессы Java
echo -e "${GREEN}=== 4. Процессы Java ===${NC}"
run_remote "ps aux | grep -E '(java|kotlin|gradle)' | grep -v grep"

# 5. Проверяем какие порты слушает приложение
echo -e "${GREEN}=== 5. Открытые порты ===${NC}"
run_remote "netstat -tlnp | grep -E ':(8001|8443|8000)' || ss -tlnp | grep -E ':(8001|8443|8000)'"

# 6. Проверяем доступность портов извне
echo -e "${GREEN}=== 6. Проверка доступности портов ===${NC}"
echo "🔗 Проверяем HTTP (8001):"
curl -s --connect-timeout 5 "http://$SERVER_IP:8001/health" | head -20 || echo "❌ Порт 8001 недоступен"
echo
echo "🔒 Проверяем HTTPS (8443):"
curl -s --connect-timeout 5 --insecure "https://$SERVER_IP:8443/health" | head -20 || echo "❌ Порт 8443 недоступен"
echo

# 7. Содержимое директории приложения
echo -e "${GREEN}=== 7. Содержимое директории приложения ===${NC}"
run_remote "ls -la /opt/kotlinagent/ || ls -la /home/*/KotlinAgent/ || find / -name 'ktor.p12' -o -name 'kotlinagent*' 2>/dev/null | head -10"

# 8. Переменные окружения
echo -e "${GREEN}=== 8. Переменные окружения ===${NC}"
run_remote "systemctl show kotlinagent --property=Environment --no-pager || echo 'Сервис не найден'"

# 9. Скачиваем важные файлы логов
echo -e "${GREEN}=== 9. Скачивание файлов логов ===${NC}"
get_file "/opt/kotlinagent/application.log"
get_file "/opt/kotlinagent/kotlinagent.log" 
get_file "/var/log/kotlinagent.log"

# 10. Проверяем последние строки логов journalctl
echo -e "${GREEN}=== 10. Последние логи (100 строк) ===${NC}"
run_remote "journalctl -u kotlinagent -n 100 --no-pager | tail -50"

echo -e "${BLUE}✅ Готово! Логи получены.${NC}"
echo -e "${YELLOW}💡 Совет: Для real-time логов используйте: ssh $SERVER_USER@$SERVER_IP 'journalctl -u kotlinagent -f'${NC}"