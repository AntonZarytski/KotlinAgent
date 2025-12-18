#!/bin/bash

# Скрипт для автоматической настройки SSL с Nginx и Let's Encrypt
# Использование: sudo ./setup-ssl-nginx.sh yourdomain.com your@email.com

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Проверка прав root
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}Ошибка: Запустите скрипт с sudo${NC}"
    exit 1
fi

# Проверка аргументов
if [ -z "$1" ] || [ -z "$2" ]; then
    echo -e "${RED}Ошибка: Укажите домен и email${NC}"
    echo "Использование: sudo $0 yourdomain.com your@email.com"
    exit 1
fi

DOMAIN=$1
EMAIL=$2
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Настройка SSL для KotlinAgent с Nginx + Let's Encrypt ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "Домен: $DOMAIN"
echo "Email: $EMAIL"
echo "Проект: $PROJECT_DIR"
echo ""

# Шаг 1: Установка Nginx
echo -e "${YELLOW}[1/6] Установка Nginx...${NC}"
if ! command -v nginx &> /dev/null; then
    apt update
    apt install -y nginx
    echo -e "${GREEN}✅ Nginx установлен${NC}"
else
    echo -e "${GREEN}✅ Nginx уже установлен${NC}"
fi

# Шаг 2: Установка Certbot
echo -e "${YELLOW}[2/6] Установка Certbot...${NC}"
if ! command -v certbot &> /dev/null; then
    apt install -y certbot python3-certbot-nginx
    echo -e "${GREEN}✅ Certbot установлен${NC}"
else
    echo -e "${GREEN}✅ Certbot уже установлен${NC}"
fi

# Шаг 3: Копирование конфигурации Nginx
echo -e "${YELLOW}[3/6] Настройка Nginx...${NC}"
cp "$PROJECT_DIR/deploy/nginx-ssl.conf" /etc/nginx/sites-available/kotlinagent

# Замена доменного имени в конфигурации
sed -i "s/yourdomain.com/$DOMAIN/g" /etc/nginx/sites-available/kotlinagent

# Создание символической ссылки
ln -sf /etc/nginx/sites-available/kotlinagent /etc/nginx/sites-enabled/

# Удаление дефолтной конфигурации
rm -f /etc/nginx/sites-enabled/default

# Проверка конфигурации
nginx -t
echo -e "${GREEN}✅ Конфигурация Nginx настроена${NC}"

# Шаг 4: Перезапуск Nginx
echo -e "${YELLOW}[4/6] Перезапуск Nginx...${NC}"
systemctl restart nginx
systemctl enable nginx
echo -e "${GREEN}✅ Nginx перезапущен${NC}"

# Шаг 5: Получение SSL сертификата
echo -e "${YELLOW}[5/6] Получение SSL сертификата от Let's Encrypt...${NC}"
echo ""
echo -e "${BLUE}Certbot запросит подтверждение. Следуйте инструкциям.${NC}"
echo ""

certbot --nginx -d "$DOMAIN" -d "www.$DOMAIN" \
    --non-interactive \
    --agree-tos \
    --email "$EMAIL" \
    --redirect

echo -e "${GREEN}✅ SSL сертификат получен и настроен${NC}"

# Шаг 6: Настройка автообновления
echo -e "${YELLOW}[6/6] Настройка автообновления сертификата...${NC}"

# Тест автообновления
certbot renew --dry-run

echo -e "${GREEN}✅ Автообновление настроено${NC}"

# Итоговая информация
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                  Настройка завершена!                  ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}✅ SSL сертификат установлен и настроен${NC}"
echo ""
echo -e "${YELLOW}Ваш сайт доступен по адресу:${NC}"
echo -e "  ${GREEN}https://$DOMAIN${NC}"
echo -e "  ${GREEN}https://www.$DOMAIN${NC}"
echo ""
echo -e "${YELLOW}Следующие шаги:${NC}"
echo "1. Убедитесь, что KotlinAgent запущен на порту 8001:"
echo "   cd $PROJECT_DIR && ./gradlew :app:run"
echo ""
echo "2. Проверьте работу сайта:"
echo "   curl https://$DOMAIN/health"
echo ""
echo "3. Проверьте SSL рейтинг:"
echo "   https://www.ssllabs.com/ssltest/analyze.html?d=$DOMAIN"
echo ""
echo -e "${YELLOW}Автообновление сертификата:${NC}"
echo "Certbot автоматически обновит сертификат за 30 дней до истечения."
echo "Проверить: sudo certbot renew --dry-run"
echo ""
echo -e "${YELLOW}Полезные команды:${NC}"
echo "  sudo systemctl status nginx       # Статус Nginx"
echo "  sudo systemctl restart nginx      # Перезапуск Nginx"
echo "  sudo nginx -t                     # Проверка конфигурации"
echo "  sudo certbot certificates         # Список сертификатов"
echo "  sudo certbot renew                # Обновить сертификаты"
echo ""

