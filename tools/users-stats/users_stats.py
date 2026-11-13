"""Сколько человек пользуются APEX CLIENT — по данным вашей панели Remnawave (пульт → «Обзор» → «Пользователи»).

Запускается из .github/workflows/stats.yml: раз в день и по кнопке «Обновить» в пульте.
Секреты репозитория (добавляет владелец):
  REMNA_URL    — адрес панели, например https://panel.example.com (в код и файлы не попадает);
  REMNA_TOKEN  — API-токен панели: Remnawave → Настройки → API-токены;
  STATS_KEY    — ключ шифрования: пульт создаёт его кнопкой «Создать ключ»;
  REMNA_COOKIE — по желанию: если панель закрыта cookie (имя=значение).
Нет секретов — тихо ничего не делает.

Что считает — только числа, без имён, почты, адресов и ссылок подписки:
  всего пользователей и активных, онлайн сейчас / за сутки / за неделю (из панели);
  каким приложением обновляют подписку за сутки и неделю (уникальные пользователи);
  нагрузка серверов: сколько человек онлайн на каждом узле (название и страна, без адреса);
  версии APEX CLIENT за неделю; итог дня для графика (до 120 дней).
Файл docs/stats/users.enc.json зашифрован (AES-GCM, ключ из STATS_KEY): репозиторий открытый,
а сколько у вас пользователей — не для всех. Пульт расшифровывает его ключом, который хранит у себя.
"""
import base64, datetime, hashlib, json, os, re, sys, urllib.error, urllib.request

URL, TOKEN, KEY, COOKIE = (os.environ.get(k, '').strip() for k in ('REMNA_URL', 'REMNA_TOKEN', 'STATS_KEY', 'REMNA_COOKIE'))
OUT = os.environ.get('USERS_OUT', 'docs/stats/users.enc.json')
if not (URL and TOKEN and KEY):
    print('Нет секретов REMNA_URL / REMNA_TOKEN / STATS_KEY — пропускаю'); raise SystemExit
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

URL = URL.rstrip('/')
if not (URL.startswith('https://') or URL.startswith('http://127.0.0.1:')):  # 127.0.0.1 — только для проверки на своём компьютере
    raise SystemExit('REMNA_URL должен начинаться с https://')
aes = AESGCM(hashlib.sha256(('apex-users:' + KEY).encode()).digest())


def api(path):
    """GET к API панели. Ошибка — без адреса и токена в тексте (логи Actions публичные)."""
    h = {'Authorization': 'Bearer ' + TOKEN, 'Accept': 'application/json', 'User-Agent': 'APEX-stats'}
    if COOKIE: h['Cookie'] = COOKIE
    try:
        with urllib.request.urlopen(urllib.request.Request(URL + path, headers=h), timeout=40) as r:
            return json.load(r)['response']
    except urllib.error.HTTPError as e:
        raise SystemExit(f'Панель ответила {e.code} на {path.split("?")[0]}' + (' — проверьте REMNA_TOKEN' if e.code in (401, 403) else ''))
    except Exception as e:  # любая ошибка сети — коротко, без адреса
        raise SystemExit(f'Панель не ответила на {path.split("?")[0]} ({type(e).__name__}) — проверьте REMNA_URL и REMNA_COOKIE')


def app_of(ua):
    """«APEX VPN/3.21.0 (…)» → ('APEX CLIENT', '3.21.0'); «Happ/1.2» → ('Happ', '1.2')."""
    ua = (ua or '').strip()
    if not ua: return 'Неизвестно', ''
    m = re.match(r'APEX VPN/([\w.\-]+)', ua)
    if m: return 'APEX CLIENT', m.group(1)
    name = ua.split('/', 1)[0] if '/' in ua else ua.split(' ', 1)[0]
    ver = re.match(r'[^/]+/([\w.\-]+)', ua)
    return name[:32] or 'Неизвестно', ver.group(1) if ver else ''


def read_prev():
    try:
        v = json.load(open(OUT))
        return json.loads(aes.decrypt(base64.b64decode(v['i']), base64.b64decode(v['c']), None))
    except Exception:
        return {}  # нет файла или ключ сменили — начинаем историю заново


now = datetime.datetime.now(datetime.timezone.utc)
st = api('/api/system/stats')
users = st.get('users') or {}
online = st.get('onlineStats') or {}

# История запросов подписки: последние записи каждого пользователя (панель хранит несколько на человека)
day, week = (now - datetime.timedelta(days=1)), (now - datetime.timedelta(days=7))
last = {}  # userId → (время, приложение, версия) — самое свежее обновление подписки за неделю
apps_day, apps_week = {}, {}
start, size, total = 0, 1000, None
while start < 60000:
    page = api(f'/api/subscription-request-history?start={start}&size={size}')
    recs = page.get('records') or []
    total = page.get('total', total)
    for r in recs:
        try: at = datetime.datetime.fromisoformat(str(r['requestAt']).replace('Z', '+00:00'))
        except (KeyError, ValueError): continue
        if at < week: continue
        uid = r.get('userId')
        app, ver = app_of(r.get('userAgent'))
        apps_week.setdefault(app, set()).add(uid)
        if at >= day: apps_day.setdefault(app, set()).add(uid)
        if uid not in last or last[uid][0] < at: last[uid] = (at, app, ver)
    start += size
    if not recs or (total is not None and start >= total): break

# 2.27: нагрузка серверов — сколько человек онлайн на каждом (только название и страна узла, без адреса)
nodes = []
try:
    for nd in api('/api/nodes') or []:
        nodes.append({'name': str(nd.get('name', ''))[:40], 'cc': str(nd.get('countryCode', ''))[:4], 'online': int(nd.get('usersOnline') or 0),
                      'up': bool(nd.get('isConnected')) and not nd.get('isDisabled'), 'traffic': int(nd.get('trafficUsedBytes') or 0)})
    nodes.sort(key=lambda x: -x['online'])
except SystemExit as e:
    print('узлы:', e)  # старая панель или нет прав — без нагрузки, остальное считаем

versions = {}
for at, app, ver in last.values():
    if app == 'APEX CLIENT': versions[ver or '?'] = versions.get(ver or '?', 0) + 1
apex_week = len(apps_week.get('APEX CLIENT', ()))
apex_day = len(apps_day.get('APEX CLIENT', ()))

prev = read_prev()
today = now.astimezone(datetime.timezone(datetime.timedelta(hours=3))).strftime('%Y-%m-%d')
days = [d for d in (prev.get('days') or []) if d.get('d') != today] + [{
    'd': today, 'total': users.get('totalUsers', 0), 'active': (users.get('statusCounts') or {}).get('ACTIVE', 0),
    'onlineDay': online.get('lastDay', 0), 'apexWeek': apex_week, 'apexDay': apex_day,
}]
data = {
    'at': int(now.timestamp() * 1000),
    'total': users.get('totalUsers', 0), 'status': users.get('statusCounts') or {},
    'online': {'now': online.get('onlineNow', 0), 'day': online.get('lastDay', 0), 'week': online.get('lastWeek', 0), 'never': online.get('neverOnline', 0)},
    'appsDay': sorted(([a, len(u)] for a, u in apps_day.items()), key=lambda x: -x[1])[:12],
    'appsWeek': sorted(([a, len(u)] for a, u in apps_week.items()), key=lambda x: -x[1])[:12],
    'versions': sorted(([v, n] for v, n in versions.items()), key=lambda x: [int(p) if p.isdigit() else 0 for p in re.split(r'[.\-]', x[0])], reverse=True)[:12],
    'apexDay': apex_day, 'apexWeek': apex_week,
    'nodes': nodes[:40],
    'days': days[-120:],
}
iv = os.urandom(12)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w').write(json.dumps({'v': 1, 'i': base64.b64encode(iv).decode(), 'c': base64.b64encode(aes.encrypt(iv, json.dumps(data, ensure_ascii=False).encode(), None)).decode()}) + '\n')
print(f'Готово: всего {data["total"]}, APEX CLIENT за неделю {apex_week}, приложений {len(apps_week)}')
