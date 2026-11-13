"""Робот Telegram-канала: сам пишет в канал о новой версии и о техработах на серверах.

Запускается из .github/workflows/telegram.yml (каждые 10 минут, после сборки и после сохранения в пульте).
Нужны секреты репозитория: TG_BOT_TOKEN (токен бота от @BotFather) и TG_CHAT (@канал, @группа или -100…; несколько — через запятую).
Нет секретов — тихо ничего не делает.

Что помнит (docs/stats/tg-state.json): последнюю объявленную версию и техработы, о которых уже написал.
Первый запуск только запоминает текущее состояние и ничего не публикует — чтобы не завалить канал старым.
"""
import datetime, json, os, re, sys, urllib.error, urllib.parse, urllib.request

TOKEN, CHAT, REPO = os.environ.get('TG_BOT_TOKEN', ''), os.environ.get('TG_CHAT', ''), os.environ.get('GITHUB_REPOSITORY', 'Dubstep2004/apex-client')
STATE = os.environ.get('TG_STATE', 'docs/stats/tg-state.json')  # пути можно подменить для проверки
SITE = 'https://dubstep2004.github.io'
MSK = datetime.timezone(datetime.timedelta(hours=3))
DRY = '--dry' in sys.argv  # проверка без отправки: печатает, что бы отправил
MAX_POSTS = 8  # защита от спама: не больше постов за один запуск (остальное — в следующий раз)
sent = 0

if not (TOKEN and CHAT) and not DRY:
    print('Нет секретов TG_BOT_TOKEN / TG_CHAT — пропускаю'); raise SystemExit


def tg(method, **params):
    if DRY:
        print('→', method, json.dumps(params, ensure_ascii=False)[:600]); return {}
    data = urllib.parse.urlencode({k: (json.dumps(v) if isinstance(v, (dict, list, bool)) else v) for k, v in params.items()}).encode()
    with urllib.request.urlopen(urllib.request.Request(f'https://api.telegram.org/bot{TOKEN}/{method}', data), timeout=30) as r:
        res = json.load(r)
    if not res.get('ok'):
        raise SystemExit('Telegram: ' + str(res.get('description')))
    return res['result']


CHATS = [c for c in re.split(r'[\s,;]+', CHAT) if c]  # канал и/или группа через запятую


def send_photo(chat, png, caption, html=True):
    """Одна картинка с подписью в один чат (multipart). Ошибка — исключение."""
    import uuid
    if DRY:
        print('→ sendPhoto', chat, len(png), 'байт,', caption[:80]); return {}
    b = uuid.uuid4().hex; parts = []
    for k, v in {'chat_id': chat, 'caption': caption, **({'parse_mode': 'HTML'} if html else {})}.items():
        parts.append(f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    parts.append(f'--{b}\r\nContent-Disposition: form-data; name="photo"; filename="version.png"\r\nContent-Type: image/png\r\n\r\n'.encode() + png + b'\r\n')
    parts.append(f'--{b}--\r\n'.encode())
    req = urllib.request.Request(f'https://api.telegram.org/bot{TOKEN}/sendPhoto', b''.join(parts), {'Content-Type': 'multipart/form-data; boundary=' + b})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            res = json.load(r)
    except urllib.error.HTTPError as e:
        try: res = json.load(e)
        except Exception: raise SystemExit(f'Telegram: HTTP {e.code}')
    if not res.get('ok'):
        raise SystemExit('Telegram: ' + str(res.get('description')))
    return res['result']


def photo(png, caption):
    """Пост с картинкой во все чаты; не вышло — обычным сообщением."""
    global sent
    sent += 1
    for chat in CHATS:
        try:
            send_photo(chat, png, caption)
            print(f'картинка отправлена в чат {CHATS.index(chat) + 1} из {len(CHATS)}')
        except Exception as e:
            print(chat, 'картинка не ушла:', e, '— отправляю текстом')
            try: tg('sendMessage', chat_id=chat, text=caption, parse_mode='HTML', link_preview_options={'is_disabled': True})
            except SystemExit as e2: print(chat, '—', e2)


def post(text):
    global sent
    if sent >= MAX_POSTS:
        print('лимит постов за запуск — остальное в следующий раз'); return False
    sent += 1
    for chat in CHATS:
        try:
            tg('sendMessage', chat_id=chat, text=text, parse_mode='HTML', link_preview_options={'is_disabled': True})
            print(f'пост отправлен в чат {CHATS.index(chat) + 1} из {len(CHATS)}')
        except SystemExit as e:
            print(chat, '—', e)  # один чат не принял — в остальные всё равно отправим


def gh(path):
    if DRY:  # локальная проверка — через gh (в песочнице прямой доступ к API закрыт)
        import subprocess
        return json.loads(subprocess.run(['gh', 'api', 'repos/' + REPO + path], capture_output=True, text=True, check=True).stdout)
    req = urllib.request.Request('https://api.github.com/repos/' + REPO + path, headers={'Accept': 'application/vnd.github+json', **({'Authorization': 'Bearer ' + os.environ['GITHUB_TOKEN']} if os.environ.get('GITHUB_TOKEN') else {})})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


esc = lambda s: str(s).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
when = lambda ms: datetime.datetime.fromtimestamp(ms / 1000, MSK).strftime('%d.%m %H:%M') + ' МСК'

state = json.load(open(STATE)) if os.path.exists(STATE) else None
first = state is None
state = state or {'version': '', 'marks': {}}
changed = first
now = int(datetime.datetime.now(datetime.timezone.utc).timestamp() * 1000)

def version_post(rel):
    """Пост «Вышла версия X»: картинка (scripts/tg_card.py) и подпись с пунктами из описания релиза.
    Тот же пост и у робота, и по кнопке «🆕 Новая версия» в пульте. Нет Pillow — картинка None."""
    body = (rel.get('body') or '').split('\n---')[0]
    items = [re.sub(r'\*\*|`', '', l[2:]).strip() for l in body.splitlines() if l.startswith('- ')][:8]
    v = rel.get('tag_name', '').lstrip('v')
    tail = f'\n\nОбновление придёт само — плашка «Вышла версия» или окно «Доступна версия» → «Обновить».\n<a href="{SITE}/update/">Как обновить</a> · <a href="{SITE}/versions/">Все изменения</a>'
    # подпись к картинке — до 1024 символов: убираем пункты целиком, а не режем посередине разметки
    while True:
        text = f'🆕 <b>Вышла версия {esc(v)}</b>\n\n' + '\n'.join('• ' + esc(i) for i in items) + tail
        if len(text) <= 1000 or len(items) <= 1: break
        items = items[:-1]
    if len(text) > 1000: text = f'🆕 <b>Вышла версия {esc(v)}</b>' + tail
    try:
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import tg_card
        return v, tg_card.card(v, items), text
    except ImportError as e:
        print('без картинки:', e); return v, None, text


# 0. Отправки из пульта через GitHub (2.20): без VPN — в России api.telegram.org из браузера часто закрыт.
# Пульт дописывает задание в docs/tg/outbox.json, робот отправляет его в чаты из TG_CHAT и пишет итог
# в state['results'][id] — пульт читает его и показывает «Отправлено» или ошибку.
ALLOWED = {'sendMessage': {'text', 'link_preview_options'}, 'sendPoll': {'question', 'options', 'is_anonymous', 'allows_multiple_answers'}, 'getChat': set(), 'sendVersion': {'version'}}
try:
    box = json.load(open(os.environ.get('TG_OUTBOX', 'docs/tg/outbox.json')))
except (OSError, ValueError):
    box = {}
results = state.setdefault('results', {})
for it in (box.get('items') or [])[-20:]:
    rid = str(it.get('id', ''))[:40]
    if not rid or rid in results or not isinstance(it.get('body', {}), dict):
        continue
    if now - int(it.get('at') or 0) > 6 * 3600 * 1000:
        results[rid] = {'ok': False, 'error': 'задание устарело — отправьте ещё раз'}; changed = True; continue
    method = it.get('method')
    if method not in ALLOWED:
        results[rid] = {'ok': False, 'error': 'неизвестное действие'}; changed = True; continue
    body = {k: v for k, v in it['body'].items() if k in ALLOWED[method]}
    done, errs = [], []
    for i, chat in enumerate(CHATS, 1):
        try:
            if method == 'sendVersion':  # 2.21: «Новая версия» из пульта — тот же пост, что пишет робот при выходе версии
                if 'vp' not in it:
                    tagv = re.sub(r'[^\w.-]', '', 'v' + str(body.get('version', '')).lstrip('v'))[:24]
                    it['vp'] = version_post(gh('/releases/tags/' + tagv))
                _, png, cap = it['vp']
                r = send_photo(chat, png, cap) if png else tg('sendMessage', chat_id=chat, text=cap, parse_mode='HTML', link_preview_options={'is_disabled': True})
            else:
                r = tg(method, chat_id=chat, **body)
            done.append(('канал ' if r.get('type') == 'channel' else 'группа ') + str(r.get('title') or r.get('username') or i) if method == 'getChat' else str(i))
        except SystemExit as e:
            errs.append(f'чат {i}: ' + str(e).replace('Telegram: ', ''))
        except Exception as e:  # сеть — пробуем остальные
            errs.append(f'чат {i}: {e}')
    print(f'из пульта: {method} — отправлено {len(done)}, ошибок {len(errs)}')
    results[rid] = {'ok': not errs, 'done': done, 'error': '; '.join(errs)[:300], 'at': now}; changed = True
if len(results) > 30:  # храним только последние итоги
    state['results'] = dict(sorted(results.items(), key=lambda kv: kv[1].get('at') or 0)[-30:])

# 1. Новая версия (не бета): номер и пункты «- …» из описания релиза
rel = gh('/releases/latest')
tag = rel.get('tag_name', '')
if tag and tag != state['version']:
    if not first and not rel.get('prerelease'):
        v, png, text = version_post(rel)
        if png: photo(png, text)
        else: post(text)
        if v.endswith('.0'):  # новая X.Y — ещё и видео «Что нового», если уже готово
            try:
                for chat in CHATS: tg('sendVideo', chat_id=chat, video=f'{SITE}/media/whatsnew-ru.mp4', caption=f'Главное в {esc(".".join(v.split(".")[:2]))} — за 12 секунд')
            except SystemExit as e:
                print('видео не отправилось:', e)
    state['version'] = tag; changed = True

# 2. Техработы на серверах (пометки «обслуживание» из пульта, кроме предпросмотра)
app = json.load(open(os.environ.get('TG_APP', 'docs/config/app.json')))
marks = {n: v for n, v in ((app.get('servers') or {}).get('status') or {}).items()
         if isinstance(v, dict) and v.get('state') == 'maintenance' and not v.get('preview') and (not v.get('until') or v['until'] > now)}
for name, v in marks.items():
    key = f"{v.get('from') or 0}-{v.get('until') or 0}"
    if state['marks'].get(name) == key:
        continue
    if not first and sent >= MAX_POSTS:
        continue  # не запоминаем — напишем в следующий запуск
    if not first:
        start = v.get('from') or 0
        head = f'🔧 <b>Изменилось время техработ: {esc(name)}</b>' if name in state['marks'] else f'🔧 <b>Запланированы техработы: {esc(name)}</b>' if start > now else f'🔧 <b>Техработы: {esc(name)}</b>'
        span = (f'С {when(start)}' if start > now else 'Сейчас') + (f' до {when(v["until"])}' if v.get('until') else ' до окончания работ')
        note = f'\n{esc(v["note"])}' if v.get('note') else ''
        post(f'{head}\n{span}.{note}\n\nАвтопилот сам переключит вас на рабочий сервер — ничего делать не нужно.\n<a href="{SITE}/status/">Статус серверов</a>')
    state['marks'][name] = key; changed = True
for name in list(state['marks']):
    if name not in marks:
        if not first and sent >= MAX_POSTS:
            continue
        if not first:
            post(f'✅ <b>Работы закончены: {esc(name)}</b>\nСервер снова работает. Спасибо за терпение!')
        del state['marks'][name]; changed = True

if changed:
    os.makedirs('docs/stats', exist_ok=True)
    if not DRY:
        open(STATE, 'w').write(json.dumps(state, ensure_ascii=False, indent=1) + '\n')
    print('состояние обновлено' + (' (первый запуск — ничего не отправлял)' if first else ''))
else:
    print('нового нет')
