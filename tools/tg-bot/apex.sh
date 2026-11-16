#!/usr/bin/env bash
# ╭───────────────────────────────────────────────────────────────╮
# │  APEX ШТАБ — меню управления ботом APEX CLIENT на своём сервере │
# ╰───────────────────────────────────────────────────────────────╯
# Ubuntu 22.04 / 24.04, Debian 12. Первый запуск (от root):
#   sudo bash -c "$(curl -fsSL https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot/apex.sh)"
# Потом меню открывается командой:  sudo apex
# Без меню:  apex install | update | restart | stop | start | status | logs | errors | backup | import <файл> | self-update | uninstall | remove
set -uo pipefail

APEX_VER="1.1"
SRC="https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot"
API="https://api.github.com/repos/Dubstep2004/apex-client/commits?path=tools/tg-bot&per_page=1"
DIR=${APEX_DIR:-/opt/apex-bot}; ENVF=${APEX_ENV:-/etc/apex-bot.env}; DATA=${APEX_DATA:-/var/lib/apex-bot}; SELF=${APEX_SELF:-/usr/local/bin/apex}
UNIT=${APEX_UNIT:-/etc/systemd/system/apex-bot.service}
TIMER=/etc/systemd/system/apex-bot-update.timer; TSVC=/etc/systemd/system/apex-bot-update.service
BTIMER=/etc/systemd/system/apex-bot-backup.timer; BSVC=/etc/systemd/system/apex-bot-backup.service
BACKUPS=${APEX_BACKUPS:-/root/apex-bot-backups}

# ── Оформление ─────────────────────────────────────────────────────
if [ -t 1 ]; then
  R=$'\e[0m'; B=$'\e[1m'; D=$'\e[2m'; I=$'\e[3m'
  C1=$'\e[38;5;43m'; C2=$'\e[38;5;79m'; C3=$'\e[38;5;115m'   # бирюзовый → зелёный, как в приложении
  OK=$'\e[38;5;78m'; WR=$'\e[38;5;221m'; BD=$'\e[38;5;203m'; DIM=$'\e[38;5;245m'; ACC=$'\e[38;5;117m'
else R= B= D= I= C1= C2= C3= OK= WR= BD= DIM= ACC=; fi
TTY=/dev/tty; { : <"$TTY"; } 2>/dev/null || TTY=/dev/stdin   # нет терминала (скрипт, таймер) — читаем stdin

say()  { printf '%s\n' "$*"; }
ok()   { printf '  %s✔%s %s\n' "$OK" "$R" "$*"; }
warn() { printf '  %s!%s %s\n' "$WR" "$R" "$*"; }
err()  { printf '  %s✖%s %s\n' "$BD" "$R" "$*"; }
step() { printf '\n  %s▸%s %s%s%s\n' "$C1" "$R" "$B" "$*" "$R"; }
ask()  { local a; read -r -p "  $1" a <"$TTY"; printf '%s' "$a"; }
confirm() { local a; read -r -p "  $1 ${DIM}[y/N]${R} " a <"$TTY"; [[ "$a" =~ ^[yYдД] ]]; }
pause(){ [ "${QUIET:-}" ] && return; read -r -s -n1 -p "  ${DIM}Нажмите любую клавишу…${R}" _ <"$TTY"; echo; }
need_root() { [ "$(id -u)" = 0 ] || { err "Нужен root: запустите  sudo apex"; exit 1; }; }

installed() { [ -f "$DIR/server.js" ] && [ -f "$UNIT" ]; }
running()   { systemctl is-active --quiet apex-bot 2>/dev/null; }
envget()    { [ -f "$ENVF" ] && sed -n "s/^$1=//p" "$ENVF" | tail -1; }
envset() {  # envset KEY VALUE — меняет строку в /etc/apex-bot.env (значение — одной строкой)
  local k=$1 v=$2; touch "$ENVF"
  if grep -q "^#\? *$k=" "$ENVF"; then
    awk -v k="$k" -v v="$v" 'BEGIN{d=0} $0 ~ "^#? *"k"=" { if(!d){print k"="v; d=1}; next } {print}' "$ENVF" >"$ENVF.tmp" && cat "$ENVF.tmp" >"$ENVF" && rm -f "$ENVF.tmp"
  else printf '%s=%s\n' "$k" "$v" >>"$ENVF"; fi
}
pxarg() { local p; p=$(envget TG_PROXY); [ -n "$p" ] && printf -- "-x\n%s\n" "$p"; }
tgapi() { local t; t=$(envget BOT_TOKEN); [ -n "$t" ] || return 1; local -a px; mapfile -t px < <(pxarg); curl -fsS -m 8 "${px[@]}" "https://api.telegram.org/bot$t/$1" 2>/dev/null; }
onoff() { if [ "$1" = 1 ]; then printf "%s● вкл%s" "$OK" "$R"; else printf "%s○ выкл%s" "$DIM" "$R"; fi; }
jget()  { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" | head -1; }
remote_sha() { curl -fsS -m 6 -H 'Accept: application/vnd.github+json' "$API" 2>/dev/null | sed -n 's/^ *"sha": "\([0-9a-f]\{40\}\)".*/\1/p' | head -1; }
local_sha()  { cat "$DIR/.version" 2>/dev/null; }

# ── Шапка ──────────────────────────────────────────────────────────
banner() {
  clear 2>/dev/null || true
  say ""
  say "  ${C1}╭──────────────────────────────────────────────╮${R}"
  say "  ${C1}│${R}   ${B}${C1}▲ A${C2}P${C2}E${C3}X${R}  ${B}ШТАБ${R}   ${DIM}управление ботом · v$APEX_VER${R}     ${C1}│${R}"
  say "  ${C1}╰──────────────────────────────────────────────╯${R}"
  if ! installed; then
    say "   ${DIM}●${R} ${B}Бот не установлен${R}  ${DIM}— начните с пункта 1${R}"; say ""; return
  fi
  local st up mem who host
  if running; then
    up=$(systemctl show apex-bot -p ActiveEnterTimestamp --value 2>/dev/null)
    local s=$(( $(date +%s) - $(date -d "$up" +%s 2>/dev/null || date +%s) ))
    st="${OK}●${R} ${B}${OK}Работает${R} ${DIM}$(dur $s)${R}"
  else st="${BD}●${R} ${B}${BD}Остановлен${R}"; fi
  mem=$(systemctl show apex-bot -p MemoryCurrent --value 2>/dev/null); [[ "$mem" =~ ^[0-9]+$ ]] && mem="$((mem / 1048576)) МБ" || mem="—"
  who=$(tgapi getMe | jget username); [ -n "$who" ] && who="@$who" || who="${WR}токен не проверен${R}"
  host=$(. /etc/os-release 2>/dev/null; echo "${PRETTY_NAME:-Linux}")
  say "   $st   ${DIM}│${R} $who"
  say "   ${DIM}Память $mem · $(df -h / | awk 'NR==2{print "диск "$5}') · $host${R}"
  [ -n "${UPD:-}" ] && say "   ${WR}★ Есть обновление бота — пункт 14${R}"
  say ""
}
dur() { local s=$1 d h m; d=$((s/86400)); h=$((s%86400/3600)); m=$((s%3600/60)); if [ $d -gt 0 ]; then echo "${d} д ${h} ч"; elif [ $h -gt 0 ]; then echo "${h} ч ${m} мин"; else echo "${m} мин"; fi; }
section() { printf '   %s%s%s\n' "$DIM" "$1" "$R"; }
item()    { printf '   %s%3s%s  %s%s%s\n' "$ACC$B" "$1" "$R" "${3:-}" "$2" "$R"; }

# ── Действия ───────────────────────────────────────────────────────
do_install() {
  need_root
  step "Node.js"
  if ! command -v node >/dev/null || [ "$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)" -lt 18 ]; then
    apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nodejs ca-certificates curl >/dev/null || { err "Не удалось поставить Node.js"; return 1; }
  fi
  ok "Node.js $(node --version)"
  step "Код бота"
  id apexbot >/dev/null 2>&1 || useradd --system --home "$DATA" --shell /usr/sbin/nologin apexbot
  mkdir -p "$DIR" "$DATA"; fetch_code || return 1
  chown -R apexbot:apexbot "$DATA"; chmod 750 "$DATA"
  if [ -z "$(envget BOT_TOKEN)" ]; then
    step "Настройки"
    umask 077; [ -f "$ENVF" ] || printf '# APEX CLIENT — настройки бота. Меню: sudo apex\n' >"$ENVF"
    set_token || return 1
    local o; o=$(ask "Ваш номер в Telegram от @userinfobot ${DIM}(Enter — пропустить)${R}: ")
    [[ "$o" =~ ^[0-9]{4,15}$ ]] && envset OWNER_ID "$o"
    chown root:apexbot "$ENVF"; chmod 640 "$ENVF"
  fi
  write_unit; systemctl daemon-reload; systemctl enable apex-bot >/dev/null 2>&1; systemctl restart apex-bot
  sleep 4
  if running; then ok "Бот запущен — напишите ему /start"; journalctl -u apex-bot -n 2 --no-pager -o cat | sed 's/^/    /'
  else err "Бот не запустился:"; journalctl -u apex-bot -n 8 --no-pager -o cat | sed 's/^/    /'; fi
  [ -f "$TIMER" ] || { confirm "Обновлять бота автоматически раз в день?" && auto_on; }
}
fetch_code() {
  local f tmp; tmp=$(mktemp -d)
  for f in worker.js server.js package.json; do
    curl -fsSL -m 30 "$SRC/$f" -o "$tmp/$f" || { err "Не скачался $f — проверьте интернет"; rm -rf "$tmp"; return 1; }
  done
  node --check "$tmp/server.js" 2>/dev/null || { err "Скачанный код повреждён — обновление отменено"; rm -rf "$tmp"; return 1; }
  cp "$tmp"/* "$DIR"/; rm -rf "$tmp"
  remote_sha >"$DIR/.version" 2>/dev/null || true
  ok "Код бота обновлён ${DIM}($(cut -c1-7 "$DIR/.version" 2>/dev/null))${R}"
}
write_unit() {
  cat >"$UNIT" <<UNIT
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
}
set_token() {
  local t
  say "  ${DIM}Токен от @BotFather. При вводе его не видно — так и надо.${R}"
  read -r -s -p "  Токен бота: " t <"$TTY"; echo
  t=$(printf '%s' "$t" | tr -d '[:space:]')
  [[ "$t" =~ ^[0-9]+:[A-Za-z0-9_-]{30,}$ ]] || { err "Не похоже на токен (123456789:AA…)"; return 1; }
  local u; u=$(curl -fsS -m 10 "https://api.telegram.org/bot$t/getMe" 2>/dev/null | jget username)
  [ -n "$u" ] || { err "Telegram не принял токен"; return 1; }
  envset BOT_TOKEN "$t"; ok "Токен принят: @$u"
}
do_update() {
  need_root; installed || { err "Бот не установлен"; return 1; }
  step "Обновление бота"
  local r l; r=$(remote_sha); l=$(local_sha)
  if [ -n "$r" ] && [ "$r" = "$l" ] && [ -z "${FORCE:-}" ]; then ok "Уже последняя версия"; return 0; fi
  fetch_code && systemctl restart apex-bot && sleep 3 && { running && ok "Бот перезапущен на новой версии" || err "Бот не запустился — журнал: пункт 5"; }
  UPD=
}
do_self_update() {
  need_root; step "Обновление меню"
  local tmp; tmp=$(mktemp)
  curl -fsSL -m 30 "$SRC/apex.sh" -o "$tmp" && bash -n "$tmp" || { err "Не скачалось"; rm -f "$tmp"; return 1; }
  install -m 755 "$tmp" "$SELF"; rm -f "$tmp"; ok "Меню обновлено — откройте снова: sudo apex"; exit 0
}
do_status() {
  step "Проверка"
  installed && ok "Установлен в $DIR" || { err "Бот не установлен"; return; }
  running && ok "Служба работает" || err "Служба остановлена"
  local me wh; me=$(tgapi getMe)
  if [ -n "$me" ]; then ok "Токен принят: @$(printf '%s' "$me" | jget username)"; else err "Telegram не отвечает или токен неверный"; fi
  wh=$(tgapi getWebhookInfo)
  if printf '%s' "$wh" | grep -q '"url":"http'; then warn "Бот подключён к Cloudflare — сервер и Cloudflare спорят. Перезапуск (пункт 2) заберёт бота сюда"
  else ok "Сообщения забирает этот сервер"; fi
  local p; p=$(printf '%s' "$wh" | jget pending_update_count); [ -n "$p" ] && [ "$p" != 0 ] && warn "Ждут ответа: $p сообщ."
  [ -n "$(envget OWNER_ID)" ] && ok "Владелец: $(envget OWNER_ID)" || warn "Номер владельца не задан — /draw и /contests не работают (пункт 7)"
  [ -n "$(envget TG_PROXY)" ] && ok "Telegram — через прокси"
  [ -f "$TIMER" ] && ok "Автообновление: раз в день" || say "  ${DIM}• Автообновление выключено${R}"
  [ -f "$BTIMER" ] && ok "Ежедневная копия: в 4:00" || say "  ${DIM}• Ежедневная копия выключена${R}"
  systemctl is-enabled --quiet apex-bot 2>/dev/null && ok "Автозапуск при старте сервера" || warn "Автозапуск выключен — после перезагрузки сервера бот не запустится"
  local r l; r=$(remote_sha); l=$(local_sha)
  if [ -n "$r" ] && [ "$r" != "$l" ]; then warn "Есть обновление бота (пункт 14)"; elif [ -n "$r" ]; then ok "Последняя версия кода"; fi
  [ -f "$DATA/kv.json" ] && ok "Конкурсы: $(grep -o '"c:[^"]*"' "$DATA/kv.json" | wc -l) шт."
}
do_logs() { say "  ${DIM}Журнал бота. Выход — Ctrl+C${R}"; trap 'echo' INT; journalctl -u apex-bot -n 40 -f --no-pager -o short-iso; trap - INT; }
do_contests() {
  step "Конкурсы"
  [ -s "$DATA/kv.json" ] || { say "  ${DIM}Пока нет — их создаёт пульт: Telegram → Отправить → Конкурс${R}"; return; }
  node -e '
    const kv = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"))
    const rows = Object.keys(kv).filter(k => k.startsWith("c:")).sort()
    if (!rows.length) console.log("  Пока нет")
    for (const k of rows) { const c = JSON.parse(kv[k] || "{}"); console.log("  • " + k.slice(2) + " — " + (c.ids || []).length + " участн." + (c.closed ? ", завершён" : "")) }
    console.log("\n  Подвести итоги: в личке бота  /draw <номер>")' "$DATA/kv.json"
}
do_backup() {
  need_root; mkdir -p "$BACKUPS"; chmod 700 "$BACKUPS"
  local f="$BACKUPS/apex-bot-$(date +%Y%m%d-%H%M).tar.gz"
  tar -czf "$f" -C / "${ENVF#/}" $( [ -f "$DATA/kv.json" ] && echo "${DATA#/}/kv.json" ) 2>/dev/null && chmod 600 "$f" && ok "Копия: $f" || err "Не получилось"
  say "  ${DIM}Внутри токен бота — не отправляйте этот файл никому.${R}"
  ls -1t "$BACKUPS"/apex-bot-*.tar.gz 2>/dev/null | tail -n +11 | xargs -r rm -f   # храним 10 последних
}
do_errors() {
  step "Ошибки за последние сутки"
  local out; out=$(journalctl -u apex-bot --since "-24h" --no-pager -o short-iso 2>/dev/null | grep -iE 'ошибк|error|нет связи|не принял|не запустил|failed|conflict|прокси|уже работает|exception|enoent|eacces')
  if [ -z "$out" ]; then ok "Ошибок нет"; return; fi
  printf '%s\n' "$out" | tail -40 | while IFS= read -r l; do printf '  %s%s%s %s%s%s\n' "$DIM" "${l:0:19}" "$R" "$BD" "${l:20}" "$R"; done
  say "  ${DIM}Всего строк с ошибками: $(printf '%s\n' "$out" | wc -l)${R}"
}
tg_check() {
  step "Связь с Telegram"
  local p code; p=$(envget TG_PROXY)
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 8 https://api.telegram.org 2>/dev/null)
  if [ "$code" != 000 ]; then ok "Напрямую — открывается"; else err "Напрямую — не открывается (провайдер или страна блокирует)"; fi
  if [ -n "$p" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' -m 10 -x "$p" https://api.telegram.org 2>/dev/null)
    [ "$code" != 000 ] && ok "Через прокси — открывается" || err "Через прокси — не открывается"
  fi
  [ -n "$(tgapi getMe)" ] && ok "Бот отвечает через $([ -n "$p" ] && echo прокси || echo прямую связь)" || warn "getMe не прошёл — проверьте токен или связь"
  say ""
  if [ -n "$p" ]; then
    item 1 "Сменить прокси"; item 2 "Выключить прокси" "$BD"; item 0 "Назад" "$DIM"
  else
    say "  ${DIM}Если Telegram с сервера не открывается — укажите HTTP-прокси.${R}"
    item 1 "Включить прокси"; item 0 "Назад" "$DIM"
  fi
  case "$(ask "Пункт: ")" in
    1) local np; np=$(ask "Прокси ${DIM}(http://адрес:порт или http://логин:пароль@адрес:порт)${R}: ")
       [[ "$np" =~ ^https?://[^[:space:]]+:[0-9]+/?$ ]] || { err "Нужен вид http://адрес:порт"; return; }
       code=$(curl -s -o /dev/null -w '%{http_code}' -m 10 -x "$np" https://api.telegram.org 2>/dev/null)
       [ "$code" != 000 ] || { err "Через этот прокси Telegram не открывается — не сохраняю"; return; }
       envset TG_PROXY "$np"; systemctl restart apex-bot; ok "Прокси включён, бот перезапущен" ;;
    2) [ -n "$p" ] && { envset TG_PROXY ""; systemctl restart apex-bot; ok "Прокси выключен"; } ;;
  esac
}
restore_file() {  # restore_file <архив> — только два файла: настройки и конкурсы
  local f=$1
  tar -tzf "$f" 2>/dev/null | grep -qx "${ENVF#/}" || { err "Это не копия APEX: внутри нет ${ENVF#/}"; return 1; }
  installed && systemctl stop apex-bot
  mkdir -p "$DATA"
  tar -xzf "$f" -C / "${ENVF#/}" 2>/dev/null; tar -xzf "$f" -C / "${DATA#/}/kv.json" 2>/dev/null
  if installed; then chown root:apexbot "$ENVF"; chmod 640 "$ENVF"; chown -R apexbot:apexbot "$DATA"; systemctl start apex-bot; ok "Восстановлено и перезапущено"
  else ok "Настройки и конкурсы на месте — ставлю бота"; do_install; fi
}
do_restore() {
  need_root; local list n f
  mapfile -t list < <(ls -1t "$BACKUPS"/apex-bot-*.tar.gz /root/apex-bot-*.tar.gz 2>/dev/null)
  step "Восстановить из копии"
  for i in "${!list[@]}"; do printf '   %s%3d%s  %s\n' "$ACC$B" $((i+1)) "$R" "${list[$i]}"; done
  [ ${#list[@]} -gt 0 ] || say "  ${DIM}Копий на этом сервере нет. Перенос: положите файл apex-bot-….tar.gz в /root/ или укажите путь.${R}"
  n=$(ask "Номер копии или путь к файлу (Enter — отмена): ")
  if [[ "$n" =~ ^[0-9]+$ ]] && [ "$n" -ge 1 ] && [ "$n" -le ${#list[@]} ]; then f=${list[$((n-1))]}
  elif [ -n "$n" ] && [ -f "$n" ]; then f=$n
  else [ -n "$n" ] && err "Нет такого файла"; return; fi
  confirm "Восстановить $(basename "$f")? Текущие настройки и конкурсы заменятся" || return
  restore_file "$f"
}
do_move() {
  need_root; step "Перенос бота на другой сервер"
  do_backup >/dev/null; local f ip; f=$(ls -1t "$BACKUPS"/apex-bot-*.tar.gz | head -1); ip=$(hostname -I 2>/dev/null | awk '{print $1}')
  ok "Копия готова: $f"
  say ""
  say "  ${B}На новом сервере:${R}"
  say "  ${ACC}1${R}  Скопируйте копию к себе:"
  say "     ${C2}scp root@${ip:-ЭТОТ-СЕРВЕР}:$f /root/${R}"
  say "  ${ACC}2${R}  Поставьте меню (команда из пульта) и откройте ${B}sudo apex${R}"
  say "  ${ACC}3${R}  Пункт ${B}«Восстановить из копии»${R} — бот встанет с теми же настройками и конкурсами"
  say "  ${ACC}4${R}  Вернитесь сюда и остановите или удалите бота — два сервера сразу спорят за сообщения"
  say ""; say "  ${DIM}В копии токен бота — не отправляйте файл никому.${R}"
}
backup_on() {
  printf '[Unit]\nDescription=APEX bot daily backup\n[Service]\nType=oneshot\nEnvironment=QUIET=1\nExecStart=%s backup\n' "$SELF" >"$BSVC"
  printf '[Unit]\nDescription=APEX bot daily backup (04:00)\n[Timer]\nOnCalendar=*-*-* 04:00\nPersistent=true\n[Install]\nWantedBy=timers.target\n' >"$BTIMER"
  systemctl daemon-reload; systemctl enable --now apex-bot-backup.timer >/dev/null 2>&1 && ok "Копия каждый день в 4:00 → $BACKUPS (хранятся 10 последних)"
}
backup_off() { systemctl disable --now apex-bot-backup.timer >/dev/null 2>&1; rm -f "$BTIMER" "$BSVC"; systemctl daemon-reload; ok "Ежедневная копия выключена"; }
toggle_boot() { if systemctl is-enabled --quiet apex-bot 2>/dev/null; then systemctl disable apex-bot >/dev/null 2>&1; warn "Автозапуск выключен — после перезагрузки сервера запускайте бота вручную"; else systemctl enable apex-bot >/dev/null 2>&1; ok "Бот будет запускаться вместе с сервером"; fi; }
auto_on() {
  cat >"$TSVC" <<EOF
[Unit]
Description=APEX bot auto-update
[Service]
Type=oneshot
Environment=QUIET=1
ExecStart=$SELF update
EOF
  cat >"$TIMER" <<EOF
[Unit]
Description=APEX bot auto-update (daily)
[Timer]
OnCalendar=*-*-* 04:20
RandomizedDelaySec=3600
Persistent=true
[Install]
WantedBy=timers.target
EOF
  systemctl daemon-reload; systemctl enable --now apex-bot-update.timer >/dev/null 2>&1 && ok "Автообновление включено: каждую ночь около 4–5 часов"
}
auto_off() { systemctl disable --now apex-bot-update.timer >/dev/null 2>&1; rm -f "$TIMER" "$TSVC"; systemctl daemon-reload; ok "Автообновление выключено"; }
do_uninstall() {
  need_root
  installed || [ -d "$DIR" ] || { warn "Бот и так не установлен"; return; }
  say ""; warn "Будут удалены: служба, код бота, настройки (токен) и конкурсы."
  confirm "Сначала сделать резервную копию?" && do_backup
  confirm "${BD}Удалить бота?${R}" || return
  systemctl disable --now apex-bot >/dev/null 2>&1; [ -f "$TIMER" ] && auto_off; [ -f "$BTIMER" ] && backup_off
  rm -f "$UNIT"; systemctl daemon-reload
  rm -rf "$DIR" "$DATA" "$ENVF"; id apexbot >/dev/null 2>&1 && userdel apexbot 2>/dev/null
  ok "Бот удалён. Меню осталось: sudo apex (пункт «Удалить меню» — убрать и его)"
}
do_remove_menu() {
  need_root
  installed && warn "Бот останется работать — управлять им можно будет командами systemctl."
  confirm "Удалить меню APEX ШТАБ (команду apex)?" || return
  [ -f "$TIMER" ] && { warn "Автообновление использует меню — выключаю"; auto_off; }
  [ -f "$BTIMER" ] && { warn "Ежедневная копия использует меню — выключаю"; backup_off; }
  rm -f "$SELF"; ok "Меню удалено. Вернуть: команда установки из пульта"; exit 0
}
toggle_notify() {
  if [ "$(envget NOTIFY)" = 1 ]; then envset NOTIFY 0; ok "Уведомления выключены"
  else [ -n "$(envget OWNER_ID)" ] || { warn "Сначала укажите номер владельца (пункт 7)"; return; }; envset NOTIFY 1; ok "Владелец получит сообщение при запуске и остановке бота"; fi
  systemctl restart apex-bot
}
set_owner()   { local o; o=$(ask "Номер в Telegram ${DIM}(узнать у @userinfobot; пусто — убрать)${R}: "); if [ -z "$o" ] || [[ "$o" =~ ^[0-9]{4,15}$ ]]; then envset OWNER_ID "$o"; systemctl restart apex-bot; ok "Сохранено"; else err "Только цифры"; fi; }
set_support() { local u; u=$(ask "Ссылка поддержки ${DIM}(https://t.me/…; пусто — из пульта)${R}: "); if [ -z "$u" ] || [[ "$u" =~ ^https:// ]]; then envset SUPPORT "$u"; systemctl restart apex-bot; ok "Сохранено"; else err "Ссылка должна начинаться с https://"; fi; }

# ── Меню ───────────────────────────────────────────────────────────
menu_missing() {
  section "Начало"
  item 1 "Установить бота" "$B"
  item 18 "Восстановить из копии ${DIM}(перенос с другого сервера)${R}"
  section "Меню"
  item 15 "Обновить меню"
  item 21 "Удалить меню"
  item 0 "Выход" "$DIM"
  say ""
}
menu_full() {
  local on; running && on="Остановить бота" || on="Запустить бота"
  section "Бот"
  item 1 "Статус и проверка"
  item 2 "Перезапустить"
  item 3 "$on"
  item 4 "Журнал в реальном времени"
  item 5 "Журнал: только ошибки"
  section "Настройки"
  item 6 "Сменить токен бота"
  item 7 "Номер владельца ${DIM}$(envget OWNER_ID)${R}"
  item 8 "Ссылка поддержки"
  item 9 "Связь с Telegram и прокси ${DIM}$([ -n "$(envget TG_PROXY)" ] && echo '· через прокси')${R}"
  section "Включить / выключить"
  item 10 "Уведомления владельцу      $(onoff "$([ "$(envget NOTIFY)" = 1 ] && echo 1)")"
  item 11 "Автообновление по ночам    $(onoff "$([ -f "$TIMER" ] && echo 1)")"
  item 12 "Копия каждый день в 4:00   $(onoff "$([ -f "$BTIMER" ] && echo 1)")"
  item 13 "Запуск вместе с сервером   $(onoff "$(systemctl is-enabled --quiet apex-bot 2>/dev/null && echo 1)")"
  section "Обновления"
  item 14 "Обновить бота"
  item 15 "Обновить меню"
  section "Копии и перенос"
  item 16 "Список конкурсов"
  item 17 "Сделать резервную копию"
  item 18 "Восстановить из копии"
  item 19 "Перенести на другой сервер"
  section "Удаление"
  item 20 "Удалить бота" "$BD"
  item 21 "Удалить меню" "$BD"
  item 0 "Выход" "$DIM"
  say ""
}
main_menu() {
  need_root
  local r l; r=$(remote_sha); l=$(local_sha); [ -n "$r" ] && [ -n "$l" ] && [ "$r" != "$l" ] && UPD=1
  while true; do
    banner
    if installed; then menu_full; else menu_missing; fi
    local c; c=$(ask "${B}Выберите пункт:${R} ")
    say ""
    if ! installed; then
      case "$c" in 1) do_install ;; 18) do_restore ;; 15) do_self_update ;; 21) do_remove_menu ;; 0|q|"") exit 0 ;; *) warn "Нет такого пункта" ;; esac
      pause; continue
    fi
    case "$c" in
      1) do_status ;;
      2) systemctl restart apex-bot; sleep 2; running && ok "Перезапущен" || err "Не запустился — журнал: пункт 5" ;;
      3) if running; then systemctl stop apex-bot; ok "Остановлен"; else systemctl start apex-bot; sleep 2; running && ok "Запущен" || err "Не запустился"; fi ;;
      4) do_logs; continue ;;
      5) do_errors ;;
      6) set_token && systemctl restart apex-bot && ok "Бот перезапущен с новым токеном" ;;
      7) set_owner ;;
      8) set_support ;;
      9) tg_check ;;
      10) toggle_notify ;;
      11) if [ -f "$TIMER" ]; then auto_off; else auto_on; fi ;;
      12) if [ -f "$BTIMER" ]; then backup_off; else backup_on; fi ;;
      13) toggle_boot ;;
      14) do_update ;;
      15) do_self_update ;;
      16) do_contests ;;
      17) do_backup ;;
      18) do_restore ;;
      19) do_move ;;
      20) do_uninstall ;;
      21) do_remove_menu ;;
      0|q|"") clear 2>/dev/null; exit 0 ;;
      *) warn "Нет такого пункта" ;;
    esac
    pause
  done
}

# ── Первый запуск: ставим себя как команду apex ─────────────────────
self_install() {
  [ "$(id -u)" = 0 ] || return 0
  [ "$(readlink -f "${BASH_SOURCE[0]:-$0}" 2>/dev/null)" = "$SELF" ] && return 0
  local tmp; tmp=$(mktemp)
  if [ -f "${BASH_SOURCE[0]:-}" ] && head -c 800 "${BASH_SOURCE[0]}" | grep -q 'APEX'; then cp "${BASH_SOURCE[0]}" "$tmp"
  else curl -fsSL -m 30 "$SRC/apex.sh" -o "$tmp" || { rm -f "$tmp"; return 0; }; fi
  bash -n "$tmp" && install -m 755 "$tmp" "$SELF" && [ -z "${QUIET:-}" ] && ok "Команда ${B}apex${R} установлена — дальше меню открывается так: ${B}sudo apex${R}"
  rm -f "$tmp"
}

self_install
case "${1:-}" in
  install) do_install ;;
  update) do_update ;;
  self-update) do_self_update ;;
  restart) need_root; systemctl restart apex-bot && ok "Перезапущен" ;;
  stop) need_root; systemctl stop apex-bot && ok "Остановлен" ;;
  start) need_root; systemctl start apex-bot && ok "Запущен" ;;
  status) do_status ;;
  logs) do_logs ;;
  errors) do_errors ;;
  import) need_root; [ -f "${2:-}" ] || { err "Укажите файл: apex import /root/apex-bot-….tar.gz"; exit 1; }; restore_file "$2" ;;
  backup) do_backup ;;
  uninstall) do_uninstall ;;
  remove) do_remove_menu ;;
  ""|menu) main_menu ;;
  -h|--help|help) sed -n 2,9p "$0" 2>/dev/null || true ;;
  *) err "Неизвестная команда: $1"; exit 1 ;;
esac
