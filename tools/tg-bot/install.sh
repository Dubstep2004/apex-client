#!/usr/bin/env bash
# APEX CLIENT — бот на своём сервере. Старая команда установки: теперь ставит меню «APEX ШТАБ» и бота.
#   curl -fsSL https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot/install.sh | sudo bash
# Новая команда (сразу с меню):
#   sudo bash -c "$(curl -fsSL https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot/apex.sh)"
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "Запустите от root: sudo bash"; exit 1; }
curl -fsSL https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot/apex.sh -o /usr/local/bin/apex
chmod 755 /usr/local/bin/apex
if [ -f /opt/apex-bot/server.js ]; then FORCE=1 /usr/local/bin/apex update; else /usr/local/bin/apex install; fi
echo "Меню управления ботом: sudo apex"
