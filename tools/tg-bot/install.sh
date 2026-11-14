#!/usr/bin/env bash
# APEX CLIENT — бот на своём сервере (Ubuntu 22.04 / 24.04, Debian 12).
# Установка и обновление одной командой (от root):
#   curl -fsSL https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot/install.sh | sudo bash
# Повторный запуск — обновляет код бота, настройки не трогает.
set -euo pipefail
SRC="https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot"
DIR=/opt/apex-bot; ENVF=/etc/apex-bot.env; DATA=/var/lib/apex-bot
[ "$(id -u)" = 0 ] || { echo "Запустите от root: sudo bash"; exit 1; }

echo "▶ Node.js"
if ! command -v node >/dev/null || [ "$(node -p 'process.versions.node.split(".")[0]')" -lt 18 ]; then
  apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nodejs ca-certificates curl >/dev/null
fi
echo "  $(node --version)"

echo "▶ Код бота → $DIR"
id apexbot >/dev/null 2>&1 || useradd --system --home "$DATA" --shell /usr/sbin/nologin apexbot
mkdir -p "$DIR" "$DATA"
for f in worker.js server.js package.json; do curl -fsSL "$SRC/$f" -o "$DIR/$f.new" && mv "$DIR/$f.new" "$DIR/$f"; done
chown -R apexbot:apexbot "$DATA"; chmod 750 "$DATA"

if [ ! -s "$ENVF" ] || ! grep -q '^BOT_TOKEN=.\+' "$ENVF"; then
  echo "▶ Настройки"
  TOKEN=""; OWNER=""
  if [ -r /dev/tty ]; then
    read -r -s -p "  Токен бота от @BotFather (не показывается): " TOKEN </dev/tty; echo
    read -r -p "  Ваш номер в Telegram от @userinfobot (Enter — пропустить): " OWNER </dev/tty
  fi
  umask 077
  cat > "$ENVF" <<CONF
# APEX CLIENT — настройки бота. После правки: systemctl restart apex-bot
BOT_TOKEN=$TOKEN
OWNER_ID=$OWNER
# SUPPORT=https://t.me/Apexx_support
CONF
  chown root:apexbot "$ENVF"; chmod 640 "$ENVF"
fi

echo "▶ Служба apex-bot"
cat > /etc/systemd/system/apex-bot.service <<UNIT
[Unit]
Description=APEX CLIENT Telegram bot
After=network-online.target
Wants=network-online.target

[Service]
User=apexbot
EnvironmentFile=$ENVF
Environment=BOT_DATA=$DATA
WorkingDirectory=$DIR
ExecStart=/usr/bin/env node $DIR/server.js
Restart=always
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$DATA
PrivateTmp=true

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable apex-bot >/dev/null 2>&1
systemctl restart apex-bot
sleep 4
if systemctl is-active --quiet apex-bot; then
  journalctl -u apex-bot -n 3 --no-pager -o cat
  echo "✅ Готово. Напишите боту /start"
else
  journalctl -u apex-bot -n 10 --no-pager -o cat
  echo "⚠️ Бот не запустился. Проверьте токен: nano $ENVF, затем systemctl restart apex-bot"
fi
echo "Полезное: journalctl -u apex-bot -f — журнал · systemctl restart apex-bot — перезапуск"
