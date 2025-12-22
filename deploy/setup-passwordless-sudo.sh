#!/bin/bash
# Настройка sudo без пароля для systemctl команд kotlinagent

set -euo pipefail

echo "=== Настройка sudo без пароля для kotlinagent ==="
echo ""
echo "Этот скрипт нужно запустить на СЕРВЕРЕ от имени root"
echo ""

# Проверяем, что мы root
if [ "$EUID" -ne 0 ]; then 
    echo "❌ Запустите этот скрипт от root:"
    echo "   sudo $0"
    exit 1
fi

# Создаём файл sudoers для kotlinagent
SUDOERS_FILE="/etc/sudoers.d/kotlinagent"

echo "Создаём $SUDOERS_FILE..."

cat > "$SUDOERS_FILE" << 'EOF'
# Разрешаем root выполнять systemctl команды для kotlinagent без пароля
root ALL=(ALL) NOPASSWD: /bin/systemctl start kotlinagent
root ALL=(ALL) NOPASSWD: /bin/systemctl stop kotlinagent
root ALL=(ALL) NOPASSWD: /bin/systemctl restart kotlinagent
root ALL=(ALL) NOPASSWD: /bin/systemctl status kotlinagent
root ALL=(ALL) NOPASSWD: /bin/systemctl reload kotlinagent
root ALL=(ALL) NOPASSWD: /bin/systemctl enable kotlinagent
root ALL=(ALL) NOPASSWD: /bin/systemctl disable kotlinagent
EOF

# Устанавливаем правильные права
chmod 0440 "$SUDOERS_FILE"

# Проверяем синтаксис
if visudo -c -f "$SUDOERS_FILE"; then
    echo "✅ Файл $SUDOERS_FILE создан и проверен"
    echo ""
    echo "Теперь можно выполнять команды без пароля:"
    echo "  sudo systemctl restart kotlinagent"
    echo ""
else
    echo "❌ Ошибка в синтаксисе sudoers файла"
    rm -f "$SUDOERS_FILE"
    exit 1
fi

echo "=== Готово! ==="

