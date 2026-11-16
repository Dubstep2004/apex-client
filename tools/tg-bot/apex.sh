#!/usr/bin/env bash
# ╭───────────────────────────────────────────────────────────────╮
# │  APEX ШТАБ — меню управления ботом APEX CLIENT на своём сервере │
# ╰───────────────────────────────────────────────────────────────╯
# Ubuntu 22.04 / 24.04, Debian 12. Первый запуск (от root):
#   sudo bash -c "$(curl -fsSL https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot/apex.sh)"
# Потом меню открывается командой:  sudo apex
# Без меню:  apex install [--yes] | update (u) | rollback | restart (r) | stop | start | status (s) | logs (l) | errors (e) | stats | check | backup (b) | import <файл> | self-update | uninstall | remove
# Другой бот на этом сервере:  apex -b <имя> <команда>.  Без вопросов:  APEX_TOKEN=… APEX_OWNER=… apex install --yes
set -uo pipefail
export LC_ALL=C.UTF-8 2>/dev/null  # русские буквы — по одной, а не по байтам

APEX_VER="2.5"
SRC="https://raw.githubusercontent.com/Dubstep2004/apex-client/main/tools/tg-bot"
API="https://api.github.com/repos/Dubstep2004/apex-client/commits?path=tools/tg-bot&per_page=1"
DIR=${APEX_DIR:-/opt/apex-bot}; SELF=${APEX_SELF:-/usr/local/bin/apex}
CONF=${APEX_CONF:-/etc/apex-menu.conf}   # настройки самого меню: какой бот выбран, язык, цвет, что включено
cget() { [ -f "$CONF" ] && sed -n "s/^$1=//p" "$CONF" | tail -1; }
cset() { local k=$1 v=$2; touch "$CONF"; chmod 600 "$CONF"; grep -v "^$k=" "$CONF" >"$CONF.tmp" 2>/dev/null; printf '%s=%s\n' "$k" "$v" >>"$CONF.tmp"; mv "$CONF.tmp" "$CONF"; }
# 2.0: несколько ботов на одном сервере. Основной — apex-bot; дополнительные — apex-bot-<имя> со своими настройками и данными
profile() {
  P=${1:-}; [[ "$P" =~ ^[a-z0-9]{0,16}$ ]] || P=
  if [ -z "$P" ]; then SVC=apex-bot; ENVF=${APEX_ENV:-/etc/apex-bot.env}; DATA=${APEX_DATA:-/var/lib/apex-bot}
  else SVC=apex-bot-$P; ENVF=/etc/apex-bot-$P.env; DATA=/var/lib/apex-bot-$P; fi
  UNIT=${APEX_UNIT:-/etc/systemd/system/$SVC.service}; WHO=
}
profile "$(cget BOT)"
TIMER=/etc/systemd/system/apex-bot-update.timer; TSVC=/etc/systemd/system/apex-bot-update.service
BTIMER=/etc/systemd/system/apex-bot-backup.timer; BSVC=/etc/systemd/system/apex-bot-backup.service
BACKUPS=${APEX_BACKUPS:-/root/apex-bot-backups}
GTIMER=/etc/systemd/system/apex-bot-guard.timer; GSVC=/etc/systemd/system/apex-bot-guard.service

# ── Оформление ─────────────────────────────────────────────────────
if [ -t 1 ]; then
  R=$'\e[0m'; B=$'\e[1m'; D=$'\e[2m'; I=$'\e[3m'
  case "$(cget THEME)" in   # 2.0: цвет меню на выбор
    purple) C1=$'\e[38;5;141m'; C2=$'\e[38;5;177m'; C3=$'\e[38;5;183m' ;;
    mono)   C1=$'\e[38;5;250m'; C2=$'\e[38;5;252m'; C3=$'\e[38;5;255m' ;;
    *)      C1=$'\e[38;5;43m'; C2=$'\e[38;5;79m'; C3=$'\e[38;5;115m' ;;   # бирюзовый → зелёный, как в приложении
  esac
  NEW=$'\e[38;5;78m'; IMP=$'\e[38;5;141m'; FIX=$'\e[38;5;75m'; BGN=$'\e[48;5;22m'; BGI=$'\e[48;5;54m'; BGF=$'\e[48;5;24m'
  OK=$'\e[38;5;78m'; WR=$'\e[38;5;221m'; BD=$'\e[38;5;203m'; DIM=$'\e[38;5;245m'; ACC=$'\e[38;5;117m'; [ "$(cget THEME)" = purple ] && ACC=$'\e[38;5;183m'; [ "$(cget THEME)" = mono ] && ACC=$'\e[38;5;255m'
else R= B= D= I= C1= C2= C3= OK= WR= BD= DIM= ACC= NEW= IMP= FIX= BGN= BGI= BGF=; fi
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
running()   { systemctl is-active --quiet "$SVC" 2>/dev/null; }
envget()    { [ -f "$ENVF" ] && sed -n "s/^$1=//p" "$ENVF" | tail -1; }
envset() {  # envset KEY VALUE — меняет строку в /etc/apex-bot.env (значение — одной строкой)
  local k=$1 v=$2; [ -f "$ENVF" ] || ( umask 077; : >"$ENVF" )   # файл с токеном — только root (и группа apexbot)
  v=${v//$'\n'/}
  if grep -q "^#\? *$k=" "$ENVF"; then
    ( umask 077; awk -v k="$k" -v v="$v" 'BEGIN{d=0} $0 ~ "^#? *"k"=" { if(!d){print k"="v; d=1}; next } {print}' "$ENVF" >"$ENVF.tmp" ) && cat "$ENVF.tmp" >"$ENVF" && rm -f "$ENVF.tmp"
  else printf '%s=%s\n' "$k" "$v" >>"$ENVF"; fi
}
# Токен и прокси передаём curl через stdin (-K -), а не в командной строке: её видят все пользователи сервера (ps)
curlk() { local url=$1 px=${2:-} tm=${3:-8}; { printf 'url = "%s"\n' "$url"; [ -n "$px" ] && printf 'proxy = "%s"\n' "$px"; } | curl -sS -m "$tm" -K - "${@:4}" 2>/dev/null; }
tgapi() { local t; t=$(envget BOT_TOKEN); [ -n "$t" ] || return 1; curlk "https://api.telegram.org/bot$t/$1" "$(envget TG_PROXY)" "${2:-8}" -f; }
onoff() { if [ "$1" = 1 ]; then printf "%s● вкл%s" "$OK" "$R"; else printf "%s○ выкл%s" "$DIM" "$R"; fi; }
jget()  { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" | head -1; }
remote_sha() { curl -fsS -m 6 -H 'Accept: application/vnd.github+json' "$API" 2>/dev/null | sed -n 's/^ *"sha": "\([0-9a-f]\{40\}\)".*/\1/p' | head -1; }
local_sha()  { cat "$DIR/.version" 2>/dev/null; }
# 2.3: качаем файлы по номеру последнего коммита, а не с «main»: адрес main GitHub кэширует до 5 минут,
# и сразу после выхода новой версии сервер получал старую («Уже последняя версия»)
srcv() { local sha; sha=${RSHA:-$(remote_sha)}; RSHA=$sha; printf 'https://raw.githubusercontent.com/Dubstep2004/apex-client/%s/tools/tg-bot' "${sha:-main}"; }

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
    up=$(systemctl show "$SVC" -p ActiveEnterTimestamp --value 2>/dev/null)
    local s=$(( $(date +%s) - $(date -d "$up" +%s 2>/dev/null || date +%s) ))
    st="${OK}●${R} ${B}${OK}Работает${R} ${DIM}$(dur $s)${R}"
  else st="${BD}●${R} ${B}${BD}Остановлен${R}"; fi
  mem=$(systemctl show "$SVC" -p MemoryCurrent --value 2>/dev/null); [[ "$mem" =~ ^[0-9]+$ ]] && mem="$((mem / 1048576)) МБ" || mem="—"
  [ -n "${WHO:-}" ] || WHO=$(tgapi getMe 4 | jget username)  # один раз за сеанс — меню не ждёт Telegram на каждом шаге
  [ -n "$WHO" ] && who="@$WHO" || who="${WR}Telegram не ответил${R}"
  host=$(. /etc/os-release 2>/dev/null; echo "${PRETTY_NAME:-Linux}")
  say "   $st   ${DIM}│${R} $who${P:+  ${DIM}· бот «$P»${R}}"
  say "   ${DIM}Память $mem · $(df -h / | awk 'NR==2{print "диск "$5}') · $host${R}"
  [ -n "${UPD:-}" ] && mf updbanner && say "   ${WR}★ $(T 'Есть обновление — пункт 10 «Обновить всё»')${R}"
  say ""
}
dur() { local s=$1 d h m; d=$((s/86400)); h=$((s%86400/3600)); m=$((s%3600/60)); if [ $d -gt 0 ]; then echo "${d} д ${h} ч"; elif [ $h -gt 0 ]; then echo "${h} ч ${m} мин"; else echo "${m} мин"; fi; }
section() { printf '   %s%s%s\n' "$DIM" "$1" "$R"; }
item()    { printf '   %s%3s%s  %s%s%s\n' "$ACC$B" "$1" "$R" "${3:-}" "$2" "$R"; }

# ── Что нового ─────────────────────────────────────────────────────
# Строки: «V|версия|дата» — заголовок; «n|текст» — новое, «u|…» — улучшено, «f|…» — исправлено.
CHANGES='V|2.5|16 ноября 2026
n|Второй и третий бот видны в пульте отдельно: свои тексты, функции, вопросы, статистика и рассылка; при добавлении бота меню само подключает его тексты из пульта и предлагает свободный порт для связи
V|2.4|16 ноября 2026
n|«Включить / выключить» → «Функции бота»: любую функцию бота — вопросы мне, рассылку, /status, антиспам, конкурсы… — одним нажатием (то же, что /settings в боте и пульт)
n|«Функции меню»: спрятать ненужные пункты — мониторинг, SSH, очистку, откат, быстрые команды, подсказки…
V|2.3|16 ноября 2026
f|«Обновить меню» и «Обновить бота» сразу видят новую версию — раньше GitHub до 5 минут отдавал старую
V|2.2|16 ноября 2026
n|«Обновить всё» — бот и меню одной кнопкой: в главном меню (10) и в «Обновлениях» (1); без меню — apex all
V|2.1|16 ноября 2026
f|После «Обновить меню» сразу открывается новое меню, а не консоль
V|2.0|16 ноября 2026
n|Меню по разделам: Бот · Настройки · Включить / выключить · Сервер и безопасность · Обновления · Копии · Боты · Удаление
n|Включить / выключить: копия в Telegram, сторож (бот завис — перезапуск), тревога по диску и памяти, файрвол, защита от перебора паролей, обновления безопасности
n|Сервер: мониторинг, скорость до Telegram и сайта, порты и файрвол, SSH — порт и вход только по ключу, очистка места, «всё ли настроено», проверка на подмену
n|Несколько ботов на одном сервере; откат бота на прошлую версию; статистика бота
n|Связь с пультом: вопросы и статистика бота видны в пульте, ответы людям — из пульта
n|Быстрые команды: apex s · r · l · e · u · b; установка без вопросов: APEX_TOKEN=… apex install --yes
n|Английский язык меню и три цвета: бирюзовый, фиолетовый, монохромный; «?» — помощь по каждому пункту
V|1.2|16 ноября 2026
f|Токен бота и пароль прокси больше не видны в списке процессов сервера
f|Восстановление проверяет копию: только два файла APEX, без ссылок — чужой архив не перезапишет файлы сервера
f|Файл настроек с токеном создаётся сразу закрытым — без мгновения, когда его могли прочитать другие
f|Меню не ждёт Telegram на каждом шаге — имя бота проверяется один раз
f|«Журнал: только ошибки» больше не показывает строку о включённом прокси как ошибку
V|1.1|16 ноября 2026
n|Раздел «Включить / выключить»: уведомления, автообновление, копия в 4:00, запуск вместе с сервером — видно, что включено
n|Связь с Telegram и прокси: проверка, открывается ли Telegram с сервера, и HTTP-прокси, если нет
n|Журнал только с ошибками за сутки — красным
n|Резервная копия каждый день в 4:00, хранятся 10 последних
n|Перенос бота на другой сервер: копия, команда для копирования и восстановление на новом месте
n|Бот: /status — состояние серверов, ответы на английском, вопросы без ответа — вам в личку, /stats и /ping
n|Что нового — этот экран
u|Восстановление принимает копию с другого сервера, даже если бот там ещё не стоит
V|1.0|16 ноября 2026
n|Меню «APEX ШТАБ»: установить, обновить, перезапустить, журнал, токен, номер владельца, уведомления, копии, удалить бота и меню'
chip() { case $1 in n) printf '%s%s %s %s' "$BGN" "$NEW$B" "● Новое" "$R";; u) printf '%s%s %s %s' "$BGI" "$IMP$B" "● Улучшено" "$R";; f) printf '%s%s %s %s' "$BGF" "$FIX$B" "● Исправлено" "$R";; esac; }
do_news() {
  local only=${1:-} kind ver date line last="" shown=0
  [ -z "$only" ] && step "Что нового в APEX ШТАБ"
  while IFS= read -r line; do
    kind=${line%%|*}; line=${line#*|}
    if [ "$kind" = V ]; then
      ver=${line%%|*}; date=${line#*|}
      [ -n "$only" ] && [ "$ver" != "$only" ] && { shown=$((shown + 1)); continue; }
      [ -n "$only" ] || [ $shown -lt 3 ] || break
      shown=$((shown + 1)); last=""
      local tag=""; [ "$ver" = "$APEX_VER" ] && tag="  ${OK}${B}✓ установлена${R}"
      say ""; say "  ${C1}┃${R} ${B}Версия $ver${R}$tag   ${DIM}$date${R}"
      continue
    fi
    [ -n "$only" ] && [ "$ver" != "$only" ] && continue
    if [ "$kind" != "$last" ]; then say "  ${C1}┃${R}"; say "  ${C1}┃${R} $(chip "$kind")"; last=$kind; fi
    local c=$NEW; [ "$kind" = u ] && c=$IMP; [ "$kind" = f ] && c=$FIX
    local first=1 cur="" w
    for w in $line; do  # перенос по словам: ${#} считает буквы (UTF-8), а не байты, как fold
      if [ -n "$cur" ] && [ $(( ${#cur} + ${#w} + 1 )) -gt 60 ]; then
        if [ $first = 1 ]; then say "  ${C1}┃${R}  ${c}•${R} $cur"; first=0; else say "  ${C1}┃${R}    $cur"; fi; cur=$w
      else cur=${cur:+$cur }$w; fi
    done
    if [ $first = 1 ]; then say "  ${C1}┃${R}  ${c}•${R} $cur"; else say "  ${C1}┃${R}    $cur"; fi
  done <<<"$CHANGES"
  say ""
}

# ── Действия ───────────────────────────────────────────────────────
do_install() {
  need_root
  step "Node.js"
  if ! command -v node >/dev/null || [ "$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)" -lt 18 ]; then
    apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nodejs ca-certificates curl >/dev/null || { err "Не удалось поставить Node.js"; return 1; }
  fi
  ok "Node.js $(node --version)"
  step "Код бота"
  id apexbot >/dev/null 2>&1 || useradd --system --home /var/lib/apex-bot --shell /usr/sbin/nologin apexbot
  mkdir -p "$DIR" "$DATA"; fetch_code || return 1
  chown -R apexbot:apexbot "$DATA"; chmod 750 "$DATA"
  if [ -z "$(envget BOT_TOKEN)" ]; then
    step "Настройки"
    umask 077; [ -f "$ENVF" ] || printf '# APEX CLIENT — настройки бота. Меню: sudo apex\n' >"$ENVF"
    if [ -n "${APEX_TOKEN:-}" ]; then   # 2.0: установка без вопросов — APEX_TOKEN=… APEX_OWNER=… apex install --yes
      local u; u=$(curlk "https://api.telegram.org/bot$APEX_TOKEN/getMe" "" 10 -f | jget username); [ -n "$u" ] || { err "Telegram не принял APEX_TOKEN"; return 1; }
      envset BOT_TOKEN "$APEX_TOKEN"; ok "Токен принят: @$u"
    else set_token || return 1; fi
    local o=${APEX_OWNER:-}; [ -n "${YES:-}" ] || [ -n "$o" ] || o=$(ask "Ваш номер в Telegram от @userinfobot ${DIM}(Enter — пропустить)${R}: ")
    [[ "$o" =~ ^[0-9]{4,15}$ ]] && envset OWNER_ID "$o"
    chown root:apexbot "$ENVF"; chmod 640 "$ENVF"
  fi
  write_unit; systemctl daemon-reload; systemctl enable "$SVC" >/dev/null 2>&1; systemctl restart "$SVC"
  sleep 4
  if running; then ok "Бот запущен — напишите ему /start"; journalctl -u "$SVC" -n 2 --no-pager -o cat | sed 's/^/    /'
  else err "Бот не запустился:"; journalctl -u "$SVC" -n 8 --no-pager -o cat | sed 's/^/    /'; fi
  [ -f "$TIMER" ] || { [ -n "${YES:-}" ] && auto_on || { confirm "Обновлять бота автоматически раз в день?" && auto_on; }; }
}
fetch_code() {
  local f tmp src; tmp=$(mktemp -d); RSHA=; src=$(srcv)
  for f in worker.js server.js package.json; do
    curl -fsSL -m 30 "$src/$f" -o "$tmp/$f" || { err "Не скачался $f — проверьте интернет"; rm -rf "$tmp"; return 1; }
  done
  node --check "$tmp/server.js" 2>/dev/null || { err "Скачанный код повреждён — обновление отменено"; rm -rf "$tmp"; return 1; }
  [ -f "$DIR/server.js" ] && { rm -rf "$DIR.prev"; cp -a "$DIR" "$DIR.prev"; }   # 2.0: прошлая версия — для отката
  cp "$tmp"/* "$DIR"/; rm -rf "$tmp"
  [ -n "$RSHA" ] && printf '%s\n' "$RSHA" >"$DIR/.version"
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
  local u; u=$(curlk "https://api.telegram.org/bot$t/getMe" "$(envget TG_PROXY)" 10 -f | jget username)
  [ -n "$u" ] || { err "Telegram не принял токен"; return 1; }
  envset BOT_TOKEN "$t"; WHO=$u; ok "Токен принят: @$u"
}
do_update() {
  need_root; installed || { err "Бот не установлен"; return 1; }
  step "Обновление бота"
  local r l; r=$(remote_sha); l=$(local_sha)
  if [ -n "$r" ] && [ "$r" = "$l" ] && [ -z "${FORCE:-}" ]; then ok "Уже последняя версия"; return 0; fi
  fetch_code && systemctl restart "$SVC" && sleep 3 && { running && ok "Бот перезапущен на новой версии" || err "Бот не запустился — журнал: пункт 5"; }
  UPD=
}
do_self_update() {
  need_root; step "Обновление меню"
  local tmp; tmp=$(mktemp)
  RSHA=; curl -fsSL -m 30 "$(srcv)/apex.sh" -o "$tmp" && bash -n "$tmp" || { err "Не скачалось"; rm -f "$tmp"; return 1; }
  local nv; nv=$(sed -n 's/^APEX_VER="\(.*\)"/\1/p' "$tmp" | head -1)
  install -m 755 "$tmp" "$SELF"; rm -f "$tmp"
  if [ "$nv" != "$APEX_VER" ]; then ok "Меню обновлено: $APEX_VER → $nv"; "$SELF" news "$nv"; else ok "Уже последняя версия меню"; fi
  # 2.1: из меню — сразу открываем новое меню, а не выходим в консоль
  if [ -n "${IN_MENU:-}" ]; then pause; exec "$SELF"; fi
  exit 0
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
  systemctl is-enabled --quiet "$SVC" 2>/dev/null && ok "Автозапуск при старте сервера" || warn "Автозапуск выключен — после перезагрузки сервера бот не запустится"
  local r l; r=$(remote_sha); l=$(local_sha)
  if [ -n "$r" ] && [ "$r" != "$l" ]; then warn "Есть обновление бота (пункт 14)"; elif [ -n "$r" ]; then ok "Последняя версия кода"; fi
  [ -f "$DATA/kv.json" ] && ok "Конкурсы: $(grep -o '"c:[^"]*"' "$DATA/kv.json" | wc -l) шт."
}
do_logs() { say "  ${DIM}Журнал бота. Выход — Ctrl+C${R}"; trap 'echo' INT; journalctl -u "$SVC" -n 40 -f --no-pager -o short-iso; trap - INT; }
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
  local f="$BACKUPS/apex-bot-${P:+$P-}$(date +%Y%m%d-%H%M).tar.gz"
  tar -czf "$f" -C / "${ENVF#/}" $( [ -f "$DATA/kv.json" ] && echo "${DATA#/}/kv.json" ) 2>/dev/null && chmod 600 "$f" && ok "Копия: $f" || err "Не получилось"
  say "  ${DIM}Внутри токен бота — не отправляйте этот файл никому.${R}"
  ls -1t "$BACKUPS"/apex-bot-*.tar.gz 2>/dev/null | tail -n +11 | xargs -r rm -f   # храним 10 последних
  # 2.0: копия — вам в Telegram файлом (если включено): не потеряется, даже если сервер умрёт
  if [ "$(cget TGBACKUP)" = 1 ] && [ -f "$f" ]; then tgfile "$f" "💾 Резервная копия бота ${P:-основного} · $(hostname) · $(date '+%d.%m %H:%M'). Внутри токен — никому не пересылайте." && ok "Копия отправлена вам в Telegram"; fi
}
do_errors() {
  step "Ошибки за последние сутки"
  local out; out=$(journalctl -u "$SVC" --since "-24h" --no-pager -o short-iso 2>/dev/null | grep -iE 'ошибк|error|нет связи|не принял|не запустил|failed|conflict|прокси:|прокси не|уже работает|exception|enoent|eacces')
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
    code=$(curlk https://api.telegram.org "$p" 10 -o /dev/null -w '%{http_code}')
    [ "$code" != 000 ] && ok "Через прокси — открывается" || err "Через прокси — не открывается"
  fi
  WHO=; [ -n "$(tgapi getMe)" ] && ok "Бот отвечает через $([ -n "$p" ] && echo прокси || echo прямую связь)" || warn "getMe не прошёл — проверьте токен или связь"
  say ""
  if [ -n "$p" ]; then
    item 1 "Сменить прокси"; item 2 "Выключить прокси" "$BD"; item 0 "Назад" "$DIM"
  else
    say "  ${DIM}Если Telegram с сервера не открывается — укажите HTTP-прокси.${R}"
    item 1 "Включить прокси"; item 0 "Назад" "$DIM"
  fi
  case "$(ask "Пункт: ")" in
    1) local np; np=$(ask "Прокси ${DIM}(http://адрес:порт или http://логин:пароль@адрес:порт)${R}: ")
       [[ "$np" =~ ^https?://[^[:space:]\"]+:[0-9]+/?$ ]] || { err "Нужен вид http://адрес:порт"; return; }
       code=$(curlk https://api.telegram.org "$np" 10 -o /dev/null -w '%{http_code}')
       [ "$code" != 000 ] || { err "Через этот прокси Telegram не открывается — не сохраняю"; return; }
       envset TG_PROXY "$np"; systemctl restart "$SVC"; ok "Прокси включён, бот перезапущен" ;;
    2) [ -n "$p" ] && { envset TG_PROXY ""; systemctl restart "$SVC"; ok "Прокси выключен"; } ;;
  esac
}
restore_file() {  # restore_file <архив> — только два файла: настройки и конкурсы
  local f=$1
  tar -tzf "$f" 2>/dev/null | grep -qx "${ENVF#/}" || { err "Это не копия APEX: внутри нет ${ENVF#/}"; return 1; }
  # Только обычные файлы: ссылка вместо файла могла бы перезаписать чужой файл на сервере
  if tar -tvzf "$f" 2>/dev/null | grep -vq '^-'; then err "В копии есть ссылки или папки — это не копия APEX, не восстанавливаю"; return 1; fi
  if tar -tzf "$f" 2>/dev/null | grep -vqxE "${ENVF#/}|${DATA#/}/kv.json"; then err "В копии лишние файлы — не восстанавливаю"; return 1; fi
  installed && systemctl stop "$SVC"
  mkdir -p "$DATA"
  tar -xzf "$f" -C / "${ENVF#/}" 2>/dev/null; tar -xzf "$f" -C / "${DATA#/}/kv.json" 2>/dev/null
  if installed; then chown root:apexbot "$ENVF"; chmod 640 "$ENVF"; chown -R apexbot:apexbot "$DATA"; systemctl start "$SVC"; ok "Восстановлено и перезапущено"
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
toggle_boot() { if systemctl is-enabled --quiet "$SVC" 2>/dev/null; then systemctl disable "$SVC" >/dev/null 2>&1; warn "Автозапуск выключен — после перезагрузки сервера запускайте бота вручную"; else systemctl enable "$SVC" >/dev/null 2>&1; ok "Бот будет запускаться вместе с сервером"; fi; }
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
  systemctl disable --now "$SVC" >/dev/null 2>&1; [ -f "$TIMER" ] && auto_off; [ -f "$BTIMER" ] && backup_off
  rm -f "$UNIT"; systemctl daemon-reload
  rm -rf "$DATA" "$ENVF"
  # Код и пользователь общие для всех ботов сервера — удаляем, только если ботов больше нет
  if ! ls /etc/apex-bot.env /etc/apex-bot-*.env >/dev/null 2>&1; then rm -rf "$DIR" "$DIR.prev"; id apexbot >/dev/null 2>&1 && userdel apexbot 2>/dev/null
  else profile ""; cset BOT ""; ok "Другие боты на сервере остались"; fi
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
  systemctl restart "$SVC"
}
set_owner()   { local o; o=$(ask "Номер в Telegram ${DIM}(узнать у @userinfobot; пусто — убрать)${R}: "); if [ -z "$o" ] || [[ "$o" =~ ^[0-9]{4,15}$ ]]; then envset OWNER_ID "$o"; systemctl restart "$SVC"; ok "Сохранено"; else err "Только цифры"; fi; }
set_support() { local u; u=$(ask "Ссылка поддержки ${DIM}(https://t.me/…; пусто — из пульта)${R}: "); if [ -z "$u" ] || [[ "$u" =~ ^https:// ]]; then envset SUPPORT "$u"; systemctl restart "$SVC"; ok "Сохранено"; else err "Ссылка должна начинаться с https://"; fi; }

# ── 2.0: язык меню, сообщения владельцу ────────────────────────────
# Английский — для главного меню, разделов и подсказок; сообщения действий остаются по-русски
declare -A EN=(
  ['Бот']='Bot' ['Настройки']='Settings' ['Включить / выключить']='On / off' ['Сервер и безопасность']='Server & security' ['Обновления']='Updates'
  ['Копии и перенос']='Backups & moving' ['Боты на сервере']='Bots on this server' ['Удаление']='Removal' ['Выход']='Exit' ['Назад']='Back'
  ['Выберите пункт:']='Choose:' ['Нет такого пункта']='No such item' ['Статус и проверка']='Status & check' ['Перезапустить']='Restart'
  ['Остановить бота']='Stop bot' ['Запустить бота']='Start bot' ['Журнал в реальном времени']='Live log' ['Журнал: только ошибки']='Log: errors only'
  ['Статистика бота']='Bot statistics' ['Список конкурсов']='Contests' ['Сменить токен бота']='Change bot token' ['Номер владельца']='Owner ID'
  ['Ссылка поддержки']='Support link' ['Связь с Telegram и прокси']='Telegram connection & proxy' ['Связь с пультом']='Link to the control panel'
  ['Язык меню']='Menu language' ['Цвет меню']='Menu colour' ['Уведомления владельцу']='Owner notifications' ['Автообновление по ночам']='Nightly auto-update'
  ['Копия каждый день в 4:00']='Daily backup at 4:00' ['Копия — вам в Telegram']='Backup to your Telegram' ['Сторож: бот завис — перезапуск']='Watchdog: restart if stuck'
  ['Тревога: диск и память']='Alerts: disk & memory' ['Запуск вместе с сервером']='Start with server' ['Файрвол (открыт только SSH)']='Firewall (SSH only)'
  ['Защита от перебора паролей']='Brute-force protection' ['Обновления безопасности']='Security updates' ['Функции бота']='Bot features' ['Функции меню']='Menu features' ['Мониторинг сервера']='Server monitor'
  ['Скорость до Telegram и сайта']='Speed to Telegram & site' ['Порты и файрвол']='Ports & firewall' ['SSH: порт и вход по ключу']='SSH: port & key login'
  ['Очистка места']='Free up space' ['Всё ли настроено']='Setup check' ['Проверка меню и кода на подмену']='Integrity check'
  ['Обновить бота']='Update bot' ['Обновить всё']='Update everything' ['Откатить бота на прошлую версию']='Roll back bot' ['Обновить меню']='Update menu' ['Что нового']='What’s new'
  ['Сделать резервную копию']='Make a backup' ['Восстановить из копии']='Restore from backup' ['Перенести на другой сервер']='Move to another server'
  ['Список ботов']='List bots' ['Добавить бота']='Add bot' ['Переключиться на бота']='Switch bot' ['Удалить бота']='Remove bot' ['Удалить меню']='Remove menu'
  ['Установить бота']='Install bot' ['Начало']='Start' ['Меню']='Menu' ['Помощь: ? и номер пункта']='Help: ? and item number' ['Работает']='Running' ['Остановлен']='Stopped'
  ['Бот не установлен']='Bot is not installed'
)
T() { if [ "$(cget MLANG)" = en ] && [ -n "${EN[$1]+x}" ]; then printf '%s' "${EN[$1]}"; else printf '%s' "$1"; fi; }
tgsend() { local t o; t=$(envget BOT_TOKEN); o=$(envget OWNER_ID); [ -n "$t" ] && [ -n "$o" ] || return 1
  curlk "https://api.telegram.org/bot$t/sendMessage" "$(envget TG_PROXY)" 15 -f --data-urlencode "chat_id=$o" --data-urlencode "text=$1" >/dev/null; }
tgfile() { local t o; t=$(envget BOT_TOKEN); o=$(envget OWNER_ID); [ -n "$t" ] && [ -n "$o" ] || { warn "Нет номера владельца — копию в Telegram не отправить"; return 1; }
  curlk "https://api.telegram.org/bot$t/sendDocument" "$(envget TG_PROXY)" 60 -f -F "chat_id=$o" -F "document=@$1" -F "caption=$2" >/dev/null; }

# ── 2.0: сервер ────────────────────────────────────────────────────
do_monitor() {
  step "Мониторинг сервера"
  local cpu mem disk load up
  load=$(cut -d' ' -f1-3 /proc/loadavg); up=$(uptime -p 2>/dev/null | sed 's/up //')
  mem=$(free -m | awk '/^Mem:/{printf "%d из %d МБ (%d%%)", $3, $2, $3*100/$2}')
  disk=$(df -h / | awk 'NR==2{print $3" из "$2" ("$5")"}')
  cpu=$(nproc)
  say "   Процессор   ${B}$cpu ядер${R} · нагрузка $load"
  say "   Память      ${B}$mem${R}"
  say "   Диск        ${B}$disk${R}"
  say "   Работает    ${B}${up:-—}${R}"
  say "   Бот         $(running && echo "${OK}работает${R}" || echo "${BD}остановлен${R}") · память $(systemctl show "$SVC" -p MemoryCurrent --value 2>/dev/null | awk '{ if ($1 ~ /^[0-9]+$/) printf "%d МБ", $1/1048576; else print "—" }')"
  say ""; say "  ${DIM}Тревогу в Telegram (диск или память больше 90%) включает «Включить / выключить» → «Тревога».${R}"
}
do_speed() {
  step "Скорость с сервера"
  local u n tm
  for u in "Telegram|https://api.telegram.org" "Сайт APEX|https://dubstep2004.github.io/" "GitHub|https://raw.githubusercontent.com/Dubstep2004/apex-client/main/README.md" "Cloudflare|https://1.1.1.1/"; do
    n=${u%%|*}; tm=$(curlk "${u#*|}" "$([ "$n" = Telegram ] && envget TG_PROXY)" 10 -o /dev/null -w '%{time_connect} %{time_total} %{http_code}')
    if [ -z "$tm" ] || [ "${tm##* }" = 000 ]; then printf '   %-12s %sне открывается%s\n' "$n" "$BD" "$R"
    else printf '   %-12s %sподключение %4d мс · ответ %4d мс%s\n' "$n" "$OK" "$(awk -v x="${tm%% *}" 'BEGIN{print int(x*1000)}')" "$(awk -v x="$(echo "$tm" | cut -d' ' -f2)" 'BEGIN{print int(x*1000)}')" "$R"; fi
  done
}
do_ports() {
  step "Что открыто наружу"
  ss -tulpnH 2>/dev/null | awk '$5 !~ /^(127\.|\[::1\]|::1)/ {split($5,a,":"); port=a[length(a)]; proc=$7; gsub(/users:\(\("|".*/,"",proc); printf "   %-5s %-6s %s\n", $1, port, proc}' | sort -u -k2,2n
  say ""
  if command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q 'Status: active'; then ok "Файрвол включён:"; ufw status 2>/dev/null | sed -n '5,$p' | sed 's/^/     /'
  else warn "Файрвол выключен — всё, что слушает порты, видно из интернета. Включить: «Включить / выключить» → «Файрвол»"; fi
}
sshd_port() { sshd -T 2>/dev/null | awk '/^port /{print $2; exit}'; }
do_ssh() {
  step "SSH: порт и вход по ключу"
  local port pass keys; port=$(sshd_port); pass=$(sshd -T 2>/dev/null | awk '/^passwordauthentication /{print $2}')
  keys=$(cat /root/.ssh/authorized_keys /home/*/.ssh/authorized_keys 2>/dev/null | grep -c '^ssh-\|^ecdsa-\|^sk-')
  say "   Порт SSH: ${B}${port:-22}${R} · вход по паролю: ${B}${pass:-yes}${R} · ключей: ${B}$keys${R}"
  say ""; item 1 "Сменить порт SSH"; item 2 "$([ "$pass" = no ] && echo 'Разрешить вход по паролю' || echo 'Вход только по ключу')"; item 0 "$(T Назад)" "$DIM"
  case "$(ask "Пункт: ")" in
    1) local np; np=$(ask "Новый порт ${DIM}(1025–65535)${R}: "); [[ "$np" =~ ^[0-9]+$ ]] && [ "$np" -gt 1024 ] && [ "$np" -lt 65536 ] || { err "Порт — число 1025–65535"; return; }
       command -v ufw >/dev/null && ufw status | grep -q active && ufw allow "$np/tcp" >/dev/null && ok "Файрвол: порт $np открыт"
       printf '# APEX ШТАБ\nPort %s\n' "$np" >/etc/ssh/sshd_config.d/99-apex-port.conf
       sshd -t || { rm -f /etc/ssh/sshd_config.d/99-apex-port.conf; err "Настройка не прошла проверку — отменено"; return; }
       systemctl daemon-reload; systemctl restart ssh.socket 2>/dev/null; systemctl restart ssh 2>/dev/null || systemctl restart sshd 2>/dev/null
       ok "SSH теперь на порту $np"; warn "НЕ закрывайте это окно: откройте второе и проверьте вход  ssh -p $np root@сервер . Не входит — верните порт здесь же" ;;
    2) if [ "$pass" = no ]; then rm -f /etc/ssh/sshd_config.d/99-apex-keys.conf; sshd -t && systemctl reload ssh 2>/dev/null || systemctl reload sshd; ok "Вход по паролю снова разрешён"
       else
         [ "$keys" -gt 0 ] || { err "На сервере нет ни одного SSH-ключа — без пароля вы не войдёте. Сначала добавьте ключ (ssh-copy-id)"; return; }
         confirm "Запретить вход по паролю? Входить можно будет только по ключу" || return
         printf '# APEX ШТАБ\nPasswordAuthentication no\nKbdInteractiveAuthentication no\n' >/etc/ssh/sshd_config.d/99-apex-keys.conf
         sshd -t || { rm -f /etc/ssh/sshd_config.d/99-apex-keys.conf; err "Настройка не прошла проверку — отменено"; return; }
         systemctl reload ssh 2>/dev/null || systemctl reload sshd; ok "Вход только по ключу"; warn "Проверьте вход по ключу во втором окне, пока это открыто"; fi ;;
  esac
}
do_clean() {
  step "Очистка места"
  local before after; before=$(df --output=avail -m / | tail -1)
  journalctl --vacuum-time=14d >/dev/null 2>&1 && ok "Журналы старше 14 дней удалены"
  apt-get clean >/dev/null 2>&1 && ok "Кэш пакетов очищен"
  ls -1t "$BACKUPS"/apex-bot-*.tar.gz 2>/dev/null | tail -n +11 | xargs -r rm -f && ok "Оставлено 10 последних копий"
  rm -rf /tmp/apex* 2>/dev/null
  after=$(df --output=avail -m / | tail -1); ok "Освободилось: $(( after - before )) МБ · свободно $(( after / 1024 )) ГБ"
}
do_integrity() {
  step "Проверка меню и кода бота на подмену"
  local f rs ls bad=0
  RSHA=; local src; src=$(srcv)
  rs=$(curl -fsSL -m 20 "$src/apex.sh" 2>/dev/null | sha256sum | cut -c1-64); ls=$(sha256sum "$SELF" 2>/dev/null | cut -c1-64)
  if [ -z "$rs" ]; then warn "GitHub не ответил — проверить нельзя"; return; fi
  if [ "$rs" = "$ls" ]; then ok "Меню совпадает с GitHub"; else warn "Меню отличается от GitHub — обновите меню («Обновления» → «Обновить меню»). Если вы не меняли его сами — это повод насторожиться"; bad=1; fi
  for f in worker.js server.js; do
    rs=$(curl -fsSL -m 20 "$src/$f" 2>/dev/null | sha256sum | cut -c1-64); ls=$(sha256sum "$DIR/$f" 2>/dev/null | cut -c1-64)
    [ "$rs" = "$ls" ] && ok "Код бота $f совпадает" || { warn "$f отличается — «Обновить бота» поставит версию с GitHub"; bad=1; }
  done
  [ $bad = 0 ] && ok "Подмены нет"
}
do_check() {
  step "Всё ли настроено"
  local n=0; adv() { warn "$1"; n=$((n+1)); }
  installed && running || adv "Бот не работает — «Бот» → «Запустить»"
  [ -n "$(tgapi getMe 6)" ] || adv "Telegram не принимает токен или недоступен — «Настройки» → «Связь с Telegram и прокси»"
  [ -n "$(envget OWNER_ID)" ] || adv "Нет номера владельца — без него нет уведомлений, /stats, /draw"
  systemctl is-enabled --quiet "$SVC" 2>/dev/null || adv "Бот не запустится после перезагрузки сервера — включите «Запуск вместе с сервером»"
  [ -f "$BTIMER" ] || adv "Нет ежедневной копии — включите «Копия каждый день в 4:00»"
  [ -f "$TIMER" ] || adv "Нет автообновления бота"
  [ -f "$GTIMER" ] && [ "$(cget WATCH)" = 1 ] || adv "Сторож выключен — если бот зависнет, никто не перезапустит"
  command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q 'Status: active' || adv "Файрвол выключен"
  systemctl is-active --quiet fail2ban 2>/dev/null || adv "Нет защиты от перебора паролей (fail2ban)"
  [ -n "$(envget BOT_PORT)" ] || adv "Нет связи с пультом — вопросы и статистика бота не видны в пульте («Настройки» → «Связь с пультом»)"
  local d; d=$(df --output=pcent / | tail -1 | tr -dc 0-9); [ "${d:-0}" -lt 85 ] || adv "Диск занят на $d% — «Сервер» → «Очистка места»"
  [ $n = 0 ] && ok "Всё настроено 👍" || say "  ${DIM}Советов: $n${R}"
}
do_botstats() {
  step "Статистика бота"
  [ -s "$DATA/kv.json" ] || { say "  ${DIM}Пока нет данных${R}"; return; }
  node -e '
    const kv = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")), j = (k, d) => { try { return JSON.parse(kv[k]) } catch { return d } }
    const users = j("users", []), q = j("q", []), day = new Date().toISOString().slice(0, 10), wk = {}
    for (let i = 0; i < 7; i++) { const d = new Date(Date.now() - i * 864e5).toISOString().slice(0, 10); for (const [k, v] of Object.entries(j("st:" + d, {}))) wk[k] = (wk[k] || 0) + v }
    const today = Object.values(j("st:" + day, {})).reduce((a, b) => a + b, 0), week = Object.values(wk).reduce((a, b) => a + b, 0)
    console.log("   Писали боту       " + users.length + " чел. · отписались " + j("stop", []).length)
    console.log("   Вопросов          сегодня " + today + " · за неделю " + week + " · без ответа " + (wk._miss || 0))
    console.log("   Переслано вам     " + q.length + " · отвечено " + q.filter(x => x.ans).length)
    const fb = j("fbk", []); if (fb.length) console.log("   «Помогло?»        👍 " + fb.filter(x => x.ok).length + " · 👎 " + fb.filter(x => !x.ok).length)
    const top = Object.entries(wk).filter(([k]) => k !== "_miss").sort((a, b) => b[1] - a[1]).slice(0, 5)
    if (top.length) { console.log("\n   Частые темы за неделю:"); top.forEach(([k, v], i) => console.log("     " + (i + 1) + ". " + k + " — " + v)) }
    const refs = Object.entries(j("refc", {})).sort((a, b) => b[1] - a[1]).slice(0, 3); if (refs.length) console.log("\n   Больше всех пригласили: " + refs.map(r => r[1]).join(", ") + " чел.")
    console.log("   Удалено спама в группе: " + j("spam", 0))' "$DATA/kv.json"
}
do_rollback() {
  step "Откат бота на прошлую версию"
  [ -f "$DIR.prev/server.js" ] || { warn "Прошлой версии нет — она появится после следующего обновления"; return; }
  confirm "Вернуть прошлую версию кода бота?" || return
  rm -rf "$DIR.new"; mv "$DIR" "$DIR.new" && mv "$DIR.prev" "$DIR" && mv "$DIR.new" "$DIR.prev"
  systemctl restart "$SVC"; sleep 3; running && ok "Откатили и перезапустили. Вернуть новую — ещё раз этот пункт" || err "Бот не запустился — журнал ошибок в разделе «Бот»"
  auto_off >/dev/null 2>&1 && warn "Автообновление выключено, чтобы ночью не вернуло новую версию"
}
do_link() {
  step "Связь с пультом"
  say "  ${DIM}Пульт показывает вопросы, статистику, рассылки и опросы бота, а вы отвечаете людям прямо из пульта.${R}"
  say "  ${DIM}Данные забирает робот GitHub: запрос подписан секретным словом, ответ зашифрован им же — слово по сети не идёт.${R}"
  local port; port=$(envget BOT_PORT)
  if [ -n "$port" ]; then
    item 1 "Показать данные для пульта"; item 2 "Выключить связь" "$BD"; item 0 "$(T Назад)" "$DIM"
    case "$(ask "Пункт: ")" in
      1) link_show ;;
      2) envset BOT_PORT ""; command -v ufw >/dev/null && ufw delete allow "$port/tcp" >/dev/null 2>&1; systemctl restart "$SVC"; ok "Связь выключена, порт $port закрыт" ;;
    esac
  else
    confirm "Включить? Откроется один порт для робота GitHub" || return
    local dp=8787 idx; [ -n "$P" ] && { idx=$(ls /etc/apex-bot-*.env 2>/dev/null | grep -n "/etc/apex-bot-$P.env" | cut -d: -f1); dp=$((8787 + ${idx:-1})); }
    port=$(ask "Порт ${DIM}(Enter — $dp)${R}: "); port=${port:-$dp}; [[ "$port" =~ ^[0-9]+$ ]] && [ "$port" -gt 1024 ] && [ "$port" -lt 65536 ] || { err "Порт — число 1025–65535"; return; }
    [ -n "$(envget WEBHOOK_SECRET)" ] || envset WEBHOOK_SECRET "$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' | cut -c1-40)"
    envset BOT_PORT "$port"; envset BOT_HOST 0.0.0.0
    command -v ufw >/dev/null && ufw status | grep -q active && ufw allow "$port/tcp" >/dev/null && ok "Файрвол: порт $port открыт"
    systemctl restart "$SVC"; sleep 2; ok "Связь включена"; link_show
  fi
}
link_show() {
  local ip; ip=$(curl -fsS -m 6 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
  say ""
  if [ -n "$P" ]; then say "  Впишите в пульт: ${B}Telegram → «Настройки бота» → поля «Бот 2» или «Бот 3»${R} (номер бота «$P» — в переключателе «Бот в личке»):"
  else say "  Впишите в пульт: ${B}Telegram → Настройка → «Настройки бота»${R}:"; fi
  say "   Адрес бота  ${C2}http://$ip:$(envget BOT_PORT)${R}"
  say "   Слово       ${C2}$(envget WEBHOOK_SECRET)${R}"
  say "  ${DIM}Слово — как пароль: только в пульт, никому не отправляйте.${R}"
}
do_bots() {
  step "Боты на сервере"
  local b cur=${P:-основной}
  for b in /etc/apex-bot.env /etc/apex-bot-*.env; do [ -f "$b" ] || continue
    local n=${b#/etc/apex-bot}; n=${n#-}; n=${n%.env}; local s=apex-bot${n:+-$n}
    printf '   %s %-12s %s\n' "$( [ "${n:-основной}" = "$cur" ] && echo "${OK}▸${R}" || echo ' ')" "${n:-основной}" "$(systemctl is-active --quiet "$s" && echo "${OK}работает${R}" || echo "${DIM}остановлен${R}")"
  done
  say ""; item 1 "$(T 'Добавить бота')"; item 2 "$(T 'Переключиться на бота')"; item 3 "$(T 'Удалить бота') ${DIM}(дополнительного)${R}" "$BD"; item 0 "$(T Назад)" "$DIM"
  case "$(ask "Пункт: ")" in
    1) local n; n=$(ask "Имя бота ${DIM}(латиница и цифры, например en или test)${R}: "); [[ "$n" =~ ^[a-z0-9]{1,16}$ ]] || { err "Только латиница и цифры, до 16 знаков"; return; }
       [ -f "/etc/apex-bot-$n.env" ] && { err "Такой бот уже есть"; return; }
       profile "$n"; cset BOT "$n"; ok "Ставлю бота «$n» — нужен отдельный токен от @BotFather"
       ( umask 077; [ -f "$ENVF" ] || printf '# APEX CLIENT — бот «%s». Меню: sudo apex\n' "$n" >"$ENVF" )
       envset BOT_CONFIG "https://dubstep2004.github.io/apex-client/tg/bot-$n.json"   # тексты и функции этого бота — из пульта
       do_install
       say "  ${DIM}В пульте: Telegram → «Бот в личке» → «＋ Бот» → имя ${B}$n${R}${DIM}. Свои тексты, вопросы и рассылка будут у него отдельно.${R}" ;;
    2) local n; n=$(ask "Имя бота ${DIM}(пусто — основной)${R}: "); [ -z "$n" ] || [ -f "/etc/apex-bot-$n.env" ] || { err "Нет такого бота"; return; }
       profile "$n"; cset BOT "$n"; ok "Выбран бот «${n:-основной}»" ;;
    3) local n; n=$(ask "Имя дополнительного бота: "); [[ "$n" =~ ^[a-z0-9]{1,16}$ ]] && [ -f "/etc/apex-bot-$n.env" ] || { err "Нет такого бота (основной удаляется в «Удаление»)"; return; }
       confirm "Удалить бота «$n» с его настройками и данными?" || return
       systemctl disable --now "apex-bot-$n" >/dev/null 2>&1; rm -f "/etc/systemd/system/apex-bot-$n.service" "/etc/apex-bot-$n.env"; rm -rf "/var/lib/apex-bot-$n"; systemctl daemon-reload
       [ "$P" = "$n" ] && { profile ""; cset BOT ""; }; ok "Бот «$n» удалён" ;;
  esac
}
# Защита сервера: файрвол, fail2ban, обновления безопасности
ufw_on() {
  command -v ufw >/dev/null || { apt-get install -y -qq ufw >/dev/null || { err "Не удалось поставить ufw"; return; }; }
  local sp; sp=$(sshd_port); sp=${sp:-22}
  confirm "Включить файрвол? Открытыми останутся SSH (порт $sp)$( [ -n "$(envget BOT_PORT)" ] && echo " и связь с пультом ($(envget BOT_PORT))")" || return
  ufw allow "$sp/tcp" >/dev/null; [ -n "$(envget BOT_PORT)" ] && ufw allow "$(envget BOT_PORT)/tcp" >/dev/null
  ufw --force enable >/dev/null && ok "Файрвол включён: открыт SSH ($sp)"
}
ufw_off() { command -v ufw >/dev/null && ufw --force disable >/dev/null; ok "Файрвол выключен"; }
f2b_on() { command -v fail2ban-client >/dev/null || apt-get install -y -qq fail2ban >/dev/null || { err "Не удалось поставить fail2ban"; return; }
  printf '[sshd]\nenabled = true\nmaxretry = 5\nbantime = 1h\n' >/etc/fail2ban/jail.d/apex.local; systemctl enable --now fail2ban >/dev/null 2>&1; systemctl restart fail2ban; ok "Защита от перебора паролей включена: 5 ошибок — бан на час"; }
f2b_off() { systemctl disable --now fail2ban >/dev/null 2>&1; ok "Защита от перебора паролей выключена"; }
uu_state() { grep -q 'Unattended-Upgrade "1"' /etc/apt/apt.conf.d/20auto-upgrades 2>/dev/null && echo 1; }
uu_on() { dpkg -s unattended-upgrades >/dev/null 2>&1 || apt-get install -y -qq unattended-upgrades >/dev/null
  printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' >/etc/apt/apt.conf.d/20auto-upgrades; ok "Обновления безопасности ставятся сами каждый день"; }
uu_off() { printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "0";\n' >/etc/apt/apt.conf.d/20auto-upgrades; ok "Автоматические обновления безопасности выключены"; }
# Сторож и тревоги: один таймер раз в 5 минут — «apex guard»
guard_sync() {
  if [ "$(cget WATCH)" = 1 ] || [ "$(cget ALERT)" = 1 ]; then
    printf '[Unit]\nDescription=APEX bot guard\n[Service]\nType=oneshot\nEnvironment=QUIET=1\nExecStart=%s guard\n' "$SELF" >"$GSVC"
    printf '[Unit]\nDescription=APEX bot guard (5 min)\n[Timer]\nOnBootSec=3min\nOnUnitActiveSec=5min\n[Install]\nWantedBy=timers.target\n' >"$GTIMER"
    systemctl daemon-reload; systemctl enable --now apex-bot-guard.timer >/dev/null 2>&1
  else systemctl disable --now apex-bot-guard.timer >/dev/null 2>&1; rm -f "$GTIMER" "$GSVC"; systemctl daemon-reload; fi
}
do_guard() {
  local f
  if [ "$(cget WATCH)" = 1 ]; then
    for f in /etc/apex-bot.env /etc/apex-bot-*.env; do [ -f "$f" ] || continue
      local n=${f#/etc/apex-bot}; n=${n#-}; n=${n%.env}; profile "$n"
      systemctl is-enabled --quiet "$SVC" 2>/dev/null || continue
      # Завис: служба не работает, или последние 5 минут в журнале только «Нет связи»
      local bad=; running || bad="служба не работала"
      [ -z "$bad" ] && [ "$(journalctl -u "$SVC" --since -5min -o cat 2>/dev/null | grep -c 'Нет связи с Telegram')" -ge 5 ] && bad="5 минут нет связи с Telegram"
      if [ -n "$bad" ]; then systemctl restart "$SVC"; sleep 5; tgsend "🛠 Сторож APEX перезапустил бота${P:+ «$P»} на $(hostname): $bad. $(running && echo 'Сейчас работает ✅' || echo 'Не запустился ❌ — откройте sudo apex')"; fi
    done; profile "$(cget BOT)"
  fi
  if [ "$(cget ALERT)" = 1 ]; then
    local d m last; d=$(df --output=pcent / | tail -1 | tr -dc 0-9); m=$(free | awk '/^Mem:/{print int($3*100/$2)}'); last=$(cget ALERTAT)
    if { [ "${d:-0}" -ge 90 ] || [ "${m:-0}" -ge 90 ]; } && [ $(( $(date +%s) - ${last:-0} )) -gt 21600 ]; then
      tgsend "⚠️ Сервер $(hostname): диск занят на ${d}%, память на ${m}%. Освободить место: sudo apex → «Сервер» → «Очистка места»" && cset ALERTAT "$(date +%s)"
    fi
  fi
}
toggle_cfg() { if [ "$(cget "$1")" = 1 ]; then cset "$1" 0; ok "$2 — выключено"; else cset "$1" 1; ok "$2 — включено"; fi; }

# ── 2.4: функции бота из меню (те же, что /settings в боте и «Функции бота» в пульте) ──
botfeat_list() {  # печатает: номер|ключ|название|1/0
  [ -f "$DIR/worker.js" ] || return 1
  ( cd "$DIR" && node --input-type=module -e '
    const { FEATURES, FEAT_NAMES, CONFIG_URL } = await import(process.argv[1] + "/worker.js")
    let c = {}; try { const r = await fetch(CONFIG_URL, { signal: AbortSignal.timeout(8000) }); if (r.ok) c = await r.json() } catch {}
    let ov = {}; try { ov = JSON.parse(JSON.parse((await import("node:fs")).readFileSync(process.argv[2], "utf8")).fover || "{}") } catch {}
    const pAt = +c.featuresAt || 0
    Object.keys(FEAT_NAMES).forEach((k, i) => { let v = typeof c.features?.[k] === "boolean" ? c.features[k] : FEATURES[k]; const o = ov[k]; if (o && typeof o.v === "boolean" && (o.at || 0) > pAt) v = o.v; console.log((i + 1) + "|" + k + "|" + FEAT_NAMES[k] + "|" + (v ? 1 : 0)) })
  ' "$DIR" "$DATA/kv.json" 2>/dev/null )
}
do_botfeat() {
  while true; do
    banner; section "Функции бота"
    local rows; rows=$(botfeat_list) || { err "Бот не установлен"; return; }
    [ -n "$rows" ] || { err "Не удалось прочитать функции — обновите бота («Обновления» → «Обновить бота»)"; return; }
    while IFS='|' read -r n k t v; do printf '   %s%3s%s  %-34s %s\n' "$ACC$B" "$n" "$R" "$t" "$(onoff "$([ "$v" = 1 ] && echo 1)")"; done <<<"$rows"
    item 0 "$(T Назад)" "$DIM"; say "   ${DIM}То же — командой /settings в боте и в пульте: Telegram → «Тексты и функции бота»${R}"; say ""
    local c line k v; c=$(ask "Номер функции: "); [ -z "$c" ] || [ "$c" = 0 ] && return
    line=$(grep "^$c|" <<<"$rows") || { warn "Нет такого пункта"; pause; continue; }
    k=$(cut -d'|' -f2 <<<"$line"); v=$(cut -d'|' -f4 <<<"$line")
    # Бот держит данные в памяти — останавливаем на секунду, меняем, запускаем
    systemctl stop "$SVC" 2>/dev/null
    node -e '
      const fs = require("fs"), f = process.argv[1]; let kv = {}; try { kv = JSON.parse(fs.readFileSync(f, "utf8")) } catch {}
      const ov = JSON.parse(kv.fover || "{}"); ov[process.argv[2]] = { v: process.argv[3] === "1", at: Date.now() }; kv.fover = JSON.stringify(ov)
      fs.writeFileSync(f + ".tmp", JSON.stringify(kv)); fs.renameSync(f + ".tmp", f)' "$DATA/kv.json" "$k" "$([ "$v" = 1 ] && echo 0 || echo 1)" && chown apexbot:apexbot "$DATA/kv.json" 2>/dev/null
    systemctl start "$SVC" 2>/dev/null; ok "$(cut -d'|' -f3 <<<"$line") — $([ "$v" = 1 ] && echo выключено || echo включено)"; sleep 1
  done
}
# ── 2.4: функции самого меню — выключенные пропадают из разделов (и быстрые команды тоже)
MENU_FEATS=('monitor|Мониторинг сервера' 'speed|Скорость до Telegram и сайта' 'ports|Порты и файрвол' 'ssh|SSH: порт и вход по ключу' 'clean|Очистка места' 'check|Всё ли настроено' 'integrity|Проверка на подмену' 'stats|Статистика бота' 'rollback|Откат бота' 'bots|Несколько ботов' 'short|Быстрые команды (apex s, r, l…)' 'hints|Подсказки внизу меню' 'updbanner|Подсказка «Есть обновление»')
mf() { [ "$(cget "OFF_$1")" != 1 ]; }   # mf ssh && …  — функция меню включена?
do_menufeat() {
  while true; do
    banner; section "Функции меню"
    local i=1 x; for x in "${MENU_FEATS[@]}"; do printf '   %s%3s%s  %-34s %s\n' "$ACC$B" "$i" "$R" "${x#*|}" "$(onoff "$(mf "${x%%|*}" && echo 1)")"; i=$((i+1)); done
    item 0 "$(T Назад)" "$DIM"; say ""
    local c; c=$(ask "Номер: "); [ -z "$c" ] || [ "$c" = 0 ] && return
    [[ "$c" =~ ^[0-9]+$ ]] && [ "$c" -ge 1 ] && [ "$c" -le ${#MENU_FEATS[@]} ] || { warn "Нет такого пункта"; pause; continue; }
    x=${MENU_FEATS[$((c-1))]}; if mf "${x%%|*}"; then cset "OFF_${x%%|*}" 1; else cset "OFF_${x%%|*}" ""; fi
  done
}

# ── Помощь (?) ─────────────────────────────────────────────────────
declare -A HELP
help_show() { local k; step "Помощь: что делает каждый пункт"; for k in $(printf '%s\n' "${!HELP[@]}" | sort -n); do printf '   %s%3s%s  %s\n' "$ACC$B" "$k" "$R" "${HELP[$k]}"; done; }

# ── Меню ───────────────────────────────────────────────────────────
menu_missing() {
  section "$(T Начало)"
  item 1 "$(T 'Установить бота')" "$B"
  item 2 "$(T 'Восстановить из копии') ${DIM}(перенос с другого сервера)${R}"
  section "$(T Меню)"
  item 3 "$(T 'Обновить меню')"
  item 4 "$(T 'Что нового')"
  item 5 "$(T 'Язык меню') ${DIM}· $( [ "$(cget MLANG)" = en ] && echo English || echo Русский)${R}"
  item 6 "$(T 'Удалить меню')"
  item 0 "$(T Выход)" "$DIM"
  say ""
}
sub_menu() {  # sub_menu <раздел> — показывает пункты раздела и выполняет выбранный
  local s=$1 c on
  while true; do
    banner; HELP=()
    section "$(T "$s")"
    case "$s" in
      'Бот') running && on="$(T 'Остановить бота')" || on="$(T 'Запустить бота')"
        item 1 "$(T 'Статус и проверка')"; item 2 "$(T Перезапустить)"; item 3 "$on"; item 4 "$(T 'Журнал в реальном времени')"; item 5 "$(T 'Журнал: только ошибки')"; mf stats && item 6 "$(T 'Статистика бота')"; item 7 "$(T 'Список конкурсов')"
        HELP=([1]='Работает ли служба, принят ли токен, кто забирает сообщения, что включено' [2]='Перезапуск бота — настройки и данные сохраняются' [3]='Остановить или запустить бота' [4]='Журнал в реальном времени, выход — Ctrl+C' [5]='Только строки с ошибками за сутки' [6]='Сколько людей писали, частые темы, «Помогло?», приглашения' [7]='Конкурсы и число участников') ;;
      'Настройки')
        item 1 "$(T 'Сменить токен бота')"; item 2 "$(T 'Номер владельца') ${DIM}$(envget OWNER_ID)${R}"; item 3 "$(T 'Ссылка поддержки')"; item 4 "$(T 'Связь с Telegram и прокси') ${DIM}$([ -n "$(envget TG_PROXY)" ] && echo '· через прокси')${R}"
        item 5 "$(T 'Связь с пультом')    $(onoff "$([ -n "$(envget BOT_PORT)" ] && echo 1)")"; item 6 "$(T 'Язык меню') ${DIM}· $( [ "$(cget MLANG)" = en ] && echo English || echo Русский)${R}"; item 7 "$(T 'Цвет меню') ${DIM}· $(cget THEME | sed 's/^$/teal/')${R}"
        HELP=([1]='Новый токен от @BotFather — бот перезапустится' [2]='Ваш номер в Telegram: уведомления, /stats, /draw, вопросы без ответа' [3]='Куда бот отправляет за помощью (иначе — из пульта)' [4]='Открывается ли Telegram с сервера; HTTP-прокси, если нет' [5]='Пульт видит вопросы и статистику бота, вы отвечаете людям из пульта' [6]='Русский или английский' [7]='Бирюзовый, фиолетовый или монохромный — под ваш терминал') ;;
      'Включить / выключить')
        item 1 "$(T 'Уведомления владельцу')            $(onoff "$([ "$(envget NOTIFY)" = 1 ] && echo 1)")"
        item 2 "$(T 'Автообновление по ночам')          $(onoff "$([ -f "$TIMER" ] && echo 1)")"
        item 3 "$(T 'Копия каждый день в 4:00')         $(onoff "$([ -f "$BTIMER" ] && echo 1)")"
        item 4 "$(T 'Копия — вам в Telegram')           $(onoff "$(cget TGBACKUP)")"
        item 5 "$(T 'Сторож: бот завис — перезапуск')   $(onoff "$(cget WATCH)")"
        item 6 "$(T 'Тревога: диск и память')           $(onoff "$(cget ALERT)")"
        item 7 "$(T 'Запуск вместе с сервером')         $(onoff "$(systemctl is-enabled --quiet "$SVC" 2>/dev/null && echo 1)")"
        item 8 "$(T 'Файрвол (открыт только SSH)')      $(onoff "$(command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q 'Status: active' && echo 1)")"
        item 9 "$(T 'Защита от перебора паролей')       $(onoff "$(systemctl is-active --quiet fail2ban 2>/dev/null && echo 1)")"
        item 10 "$(T 'Обновления безопасности')          $(onoff "$(uu_state)")"
        item 11 "🤖 $(T 'Функции бота')…"; item 12 "🧩 $(T 'Функции меню')…"
        HELP=([1]='Сообщение вам, когда бот запустился или остановился' [2]='Каждую ночь бот обновляется сам с GitHub' [3]='Резервная копия настроек и данных в 4:00, хранятся 10' [4]='Каждая копия приходит вам в Telegram файлом' [5]='Раз в 5 минут: бот не работает или завис — перезапуск и сообщение вам' [6]='Диск или память заняты больше 90% — сообщение вам (не чаще раза в 6 часов)' [7]='Бот запускается сам после перезагрузки сервера' [8]='ufw: закрыто всё, кроме SSH (и связи с пультом)' [9]='fail2ban: 5 неверных паролей SSH — бан на час' [10]='Ubuntu сама ставит обновления безопасности' [11]='Все функции бота: вопросы мне, рассылка, /status, антиспам… — как /settings в боте' [12]='Спрятать ненужные пункты этого меню') ;;
      'Сервер и безопасность')
        mf monitor && item 1 "$(T 'Мониторинг сервера')"; mf speed && item 2 "$(T 'Скорость до Telegram и сайта')"; mf ports && item 3 "$(T 'Порты и файрвол')"; mf ssh && item 4 "$(T 'SSH: порт и вход по ключу')"; mf clean && item 5 "$(T 'Очистка места')"; mf check && item 6 "$(T 'Всё ли настроено')"; mf integrity && item 7 "$(T 'Проверка меню и кода на подмену')"
        HELP=([1]='Процессор, память, диск, сколько работает сервер и бот' [2]='Время до Telegram, сайта, GitHub и Cloudflare' [3]='Что слушает порты снаружи и включён ли файрвол' [4]='Сменить порт SSH или запретить вход по паролю (с проверками)' [5]='Старые журналы, кэш пакетов, лишние копии' [6]='Советы: что ещё стоит включить' [7]='Совпадают ли меню и код бота с GitHub') ;;
      'Обновления')
        item 1 "$(T 'Обновить всё') ${DIM}· бота и меню${R}" "$B"; item 2 "$(T 'Обновить бота')"; item 3 "$(T 'Обновить меню')"; mf rollback && item 4 "$(T 'Откатить бота на прошлую версию')"; item 5 "$(T 'Что нового') ${DIM}· версия $APEX_VER${R}"
        HELP=([1]='Сначала бот, потом меню — одной кнопкой' [2]='Свежий код бота с GitHub и перезапуск' [3]='Свежая версия этого меню' [4]='Если обновление что-то сломало — вернуть прошлую версию' [5]='Что изменилось в меню') ;;
      'Копии и перенос')
        item 1 "$(T 'Сделать резервную копию')"; item 2 "$(T 'Восстановить из копии')"; item 3 "$(T 'Перенести на другой сервер')"
        HELP=([1]='Настройки и данные бота в один файл (в нём токен!)' [2]='Вернуть настройки и данные из копии' [3]='Копия + команда, как забрать её на новый сервер') ;;
      'Боты на сервере') do_bots; pause; return ;;
      'Удаление')
        item 1 "$(T 'Удалить бота')" "$BD"; item 2 "$(T 'Удалить меню')" "$BD"
        HELP=([1]='Служба, код, настройки и данные — с предложением сделать копию' [2]='Команда apex; бот продолжит работать') ;;
    esac
    item 0 "$(T Назад)" "$DIM"; mf hints && say "   ${DIM}$(T 'Помощь: ? и номер пункта')${R}"; say ""
    c=$(ask "${B}$(T 'Выберите пункт:')${R} "); say ""
    case "$c" in 0|q|"") return ;; '?') help_show; pause; continue ;; '?'*) say "   ${HELP[${c#?}]:-$(T 'Нет такого пункта')}"; pause; continue ;; esac
    case "$s|$c" in
      'Бот|1') do_status ;; 'Бот|2') systemctl restart "$SVC"; sleep 2; running && ok "Перезапущен" || err "Не запустился — «Журнал: только ошибки»" ;;
      'Бот|3') if running; then systemctl stop "$SVC"; ok "Остановлен"; else systemctl start "$SVC"; sleep 2; running && ok "Запущен" || err "Не запустился"; fi ;;
      'Бот|4') do_logs; continue ;; 'Бот|5') do_errors ;; 'Бот|6') mf stats && do_botstats ;; 'Бот|7') do_contests ;;
      'Настройки|1') set_token && systemctl restart "$SVC" && ok "Бот перезапущен с новым токеном" ;; 'Настройки|2') set_owner ;; 'Настройки|3') set_support ;; 'Настройки|4') tg_check ;; 'Настройки|5') do_link ;;
      'Настройки|6') [ "$(cget MLANG)" = en ] && cset MLANG ru || cset MLANG en; continue ;;
      'Настройки|7') case "$(cget THEME)" in purple) cset THEME mono ;; mono) cset THEME "" ;; *) cset THEME purple ;; esac; exec "$SELF" ;;
      'Включить / выключить|1') toggle_notify ;; 'Включить / выключить|2') if [ -f "$TIMER" ]; then auto_off; else auto_on; fi ;;
      'Включить / выключить|3') if [ -f "$BTIMER" ]; then backup_off; else backup_on; fi ;;
      'Включить / выключить|4') [ -n "$(envget OWNER_ID)" ] || { warn "Сначала номер владельца («Настройки»)"; pause; continue; }; toggle_cfg TGBACKUP "Копия в Telegram"; [ -f "$BTIMER" ] || { backup_on; } ;;
      'Включить / выключить|5') toggle_cfg WATCH "Сторож"; guard_sync ;; 'Включить / выключить|6') [ -n "$(envget OWNER_ID)" ] || { warn "Сначала номер владельца («Настройки»)"; pause; continue; }; toggle_cfg ALERT "Тревога: диск и память"; guard_sync ;;
      'Включить / выключить|7') toggle_boot ;;
      'Включить / выключить|8') if command -v ufw >/dev/null && ufw status | grep -q 'Status: active'; then confirm "Выключить файрвол?" && ufw_off; else ufw_on; fi ;;
      'Включить / выключить|9') if systemctl is-active --quiet fail2ban; then f2b_off; else f2b_on; fi ;;
      'Включить / выключить|10') if [ "$(uu_state)" = 1 ]; then uu_off; else uu_on; fi ;;
      'Включить / выключить|11') do_botfeat; continue ;; 'Включить / выключить|12') do_menufeat; continue ;;
      'Сервер и безопасность|1') mf monitor && do_monitor ;; 'Сервер и безопасность|2') mf speed && do_speed ;; 'Сервер и безопасность|3') mf ports && do_ports ;; 'Сервер и безопасность|4') mf ssh && do_ssh ;;
      'Сервер и безопасность|5') mf clean && do_clean ;; 'Сервер и безопасность|6') mf check && do_check ;; 'Сервер и безопасность|7') mf integrity && do_integrity ;;
      'Обновления|1') do_update; do_self_update ;; 'Обновления|2') do_update ;; 'Обновления|3') do_self_update ;; 'Обновления|4') mf rollback && do_rollback ;; 'Обновления|5') do_news ;;
      'Копии и перенос|1') do_backup ;; 'Копии и перенос|2') do_restore ;; 'Копии и перенос|3') do_move ;;
      'Удаление|1') do_uninstall; installed || return ;; 'Удаление|2') do_remove_menu ;;
      *) warn "$(T 'Нет такого пункта')" ;;
    esac
    pause
  done
}
main_menu() {
  need_root; IN_MENU=1
  local r l; r=$(remote_sha); l=$(local_sha); [ -n "$r" ] && [ -n "$l" ] && [ "$r" != "$l" ] && UPD=1
  local S=('' 'Бот' 'Настройки' 'Включить / выключить' 'Сервер и безопасность' 'Обновления' 'Копии и перенос' 'Боты на сервере' 'Удаление') IC=('' '🤖' '⚙️ ' '🔀' '🛡 ' '⬆️ ' '💾' '🧩' '🗑 ')
  while true; do
    banner
    if ! installed; then
      menu_missing
      case "$(ask "${B}$(T 'Выберите пункт:')${R} ")" in 1) do_install ;; 2) do_restore ;; 3) do_self_update ;; 4) do_news ;; 5) [ "$(cget MLANG)" = en ] && cset MLANG ru || cset MLANG en; continue ;; 6) do_remove_menu ;; 0|q|"") exit 0 ;; *) warn "$(T 'Нет такого пункта')" ;; esac
      pause; continue
    fi
    local i; for i in 1 2 3 4 5 6 7 8; do [ "$i" = 7 ] && ! mf bots && continue; item "$i" "${IC[$i]} $(T "${S[$i]}")" "$([ "$i" = 8 ] && echo "$BD")"; done
    item 9 "📰 $(T 'Что нового') ${DIM}· $APEX_VER${R}"; item 10 "⬆️  $(T 'Обновить всё') ${DIM}· бота и меню${R}"; item 0 "$(T Выход)" "$DIM"; say ""
    mf hints && { say "   ${DIM}Быстро без меню: apex s — статус · r — перезапуск · l — журнал · e — ошибки · u — бот · all — всё · b — копия${R}"; say ""; }
    local c; c=$(ask "${B}$(T 'Выберите пункт:')${R} ")
    case "$c" in [1-8]) sub_menu "${S[$c]}" ;; 9) say ""; do_news; pause ;; 10) say ""; do_update; do_self_update ;; 0|q|"") clear 2>/dev/null; exit 0 ;; *) warn "$(T 'Нет такого пункта')"; pause ;; esac
  done
}

# ── Первый запуск: ставим себя как команду apex ─────────────────────
self_install() {
  [ "$(id -u)" = 0 ] || return 0
  [ "$(readlink -f "${BASH_SOURCE[0]:-$0}" 2>/dev/null)" = "$SELF" ] && return 0
  local tmp; tmp=$(mktemp)
  if [ -f "${BASH_SOURCE[0]:-}" ] && head -c 800 "${BASH_SOURCE[0]}" | grep -q 'APEX'; then cp "${BASH_SOURCE[0]}" "$tmp"
  else curl -fsSL -m 30 "$(srcv)/apex.sh" -o "$tmp" || { rm -f "$tmp"; return 0; }; fi
  bash -n "$tmp" && install -m 755 "$tmp" "$SELF" && [ -z "${QUIET:-}" ] && ok "Команда ${B}apex${R} установлена — дальше меню открывается так: ${B}sudo apex${R}"
  rm -f "$tmp"
}

self_install
[ "${1:-}" = -b ] && { profile "${2:-}"; shift 2; }
case "${1:-}" in s|r|l|e|u|b) mf short || { err "Быстрые команды выключены: sudo apex → «Включить / выключить» → «Функции меню»"; exit 1; } ;; esac   # apex -b en status — команда для бота «en»
[ "${2:-}" = --yes ] && YES=1
case "${1:-}" in
  install) do_install ;;
  update|u) do_update ;;
  upgrade|all) do_update; do_self_update ;;
  rollback) do_rollback ;;
  self-update) do_self_update ;;
  restart|r) need_root; systemctl restart "$SVC" && ok "Перезапущен" ;;
  stop) need_root; systemctl stop "$SVC" && ok "Остановлен" ;;
  start) need_root; systemctl start "$SVC" && ok "Запущен" ;;
  status|s) do_status ;;
  logs|l) do_logs ;;
  errors|e) do_errors ;;
  stats) do_botstats ;;
  check) do_check ;;
  guard) need_root; do_guard ;;
  news) do_news "${2:-}" ;;
  import) need_root; [ -f "${2:-}" ] || { err "Укажите файл: apex import /root/apex-bot-….tar.gz"; exit 1; }; restore_file "$2" ;;
  backup|b) do_backup ;;
  uninstall) do_uninstall ;;
  remove) do_remove_menu ;;
  ""|menu) main_menu ;;
  -h|--help|help) sed -n 2,10p "$0" 2>/dev/null || true ;;
  *) err "Неизвестная команда: $1"; exit 1 ;;
esac
