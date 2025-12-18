#!/bin/bash
# Быстрая настройка production SSL после деплоя

set -e

SERVER="root@95.217.187.167"
REMOTE_DIR="/home/agent/KotlinAgent"

echo "=== Настройка Production SSL ==="
echo ""
echo "Выберите вариант:"
echo ""
echo "1) DuckDNS + Let's Encrypt (бесплатный домен)"
echo "2) Свой домен + Let's Encrypt"
echo "3) Улучшенный самоподписанный для IP"
echo ""
read -p "Ваш выбор (1-3): " choice

case $choice in
    1)
        echo ""
        echo "=== DuckDNS + Let's Encrypt ==="
        echo ""
        read -p "Введите поддомен (например: myapp): " subdomain
        read -p "Введите DuckDNS токен: " token
        read -p "Введите email: " email
        
        echo ""
        echo "Запуск настройки на сервере..."
        ssh -t "$SERVER" << ENDSSH
cd $REMOTE_DIR
sudo -u agent ./scripts/setup-duckdns-ssl.sh $subdomain $token $email
ENDSSH
        
        echo ""
        echo "✅ Готово! Ваш сайт: https://$subdomain.duckdns.org"
        ;;
        
    2)
        echo ""
        echo "=== Свой домен + Let's Encrypt ==="
        echo ""
        read -p "Введите домен (например: mysite.com): " domain
        read -p "Введите email: " email
        
        echo ""
        echo "⚠️  Убедитесь, что DNS домена указывает на 95.217.187.167"
        read -p "Продолжить? (y/n): " confirm
        
        if [[ $confirm != "y" ]]; then
            echo "Отменено"
            exit 0
        fi
        
        echo ""
        echo "Запуск настройки на сервере..."
        ssh -t "$SERVER" << ENDSSH
cd $REMOTE_DIR
sudo ./scripts/setup-ssl-nginx.sh $domain $email
ENDSSH
        
        echo ""
        echo "✅ Готово! Ваш сайт: https://$domain"
        ;;
        
    3)
        echo ""
        echo "=== Улучшенный самоподписанный для IP ==="
        echo ""
        
        IP="95.217.187.167"
        echo "IP адрес: $IP"
        
        echo ""
        echo "Запуск настройки на сервере..."
        ssh -t "$SERVER" << ENDSSH
cd $REMOTE_DIR
sudo -u agent ./scripts/generate-ssl-for-ip.sh $IP
ENDSSH
        
        echo ""
        echo "✅ Готово! Ваш сайт: https://$IP:8443"
        echo ""
        echo "⚠️  Браузер покажет предупреждение о безопасности"
        echo ""
        echo "Для добавления в доверенные (на вашем компьютере):"
        echo "  1. Скачайте сертификат:"
        echo "     scp $SERVER:$REMOTE_DIR/server.crt ."
        echo "  2. Добавьте в доверенные:"
        echo "     sudo security add-trusted-cert -d -r trustRoot \\"
        echo "       -k /Library/Keychains/System.keychain server.crt"
        ;;
        
    *)
        echo "Неверный выбор"
        exit 1
        ;;
esac

echo ""
echo "=== Перезапуск приложения ==="
ssh "$SERVER" << 'ENDSSH'
if systemctl list-unit-files | grep -q kotlinagent.service; then
    sudo systemctl restart kotlinagent
    echo "✅ Приложение перезапущено"
else
    cd /home/agent/KotlinAgent
    ./deploy/stop.sh || true
    nohup ./deploy/start.sh > app.log 2>&1 &
    echo "✅ Приложение перезапущено"
fi
ENDSSH

echo ""
echo "=== Health Check ==="
sleep 3

# HTTP
if curl -s "http://95.217.187.167:8001/health" | grep -q "ok"; then
    echo "✅ HTTP работает"
else
    echo "❌ HTTP не работает"
fi

# HTTPS
if curl -k -s "https://95.217.187.167:8443/health" | grep -q "ok"; then
    echo "✅ HTTPS работает"
else
    echo "❌ HTTPS не работает"
fi

echo ""
echo "=== ✅ Настройка завершена ==="

