"""Робот Telegram-канала. Запускается из .github/workflows/telegram.yml: каждые 10 минут, после сборки,
после «Сохранить» в пульте и сразу, когда пульт кладёт задание в docs/tg/outbox.json.

Секреты репозитория:
  TG_BOT_TOKEN — токен бота от @BotFather (обязательно);
  TG_CHAT      — @канал, @группа или -100…; несколько — через запятую (обязательно);
  TG_CHAT_EN   — англоязычный канал для английских постов из пульта (по желанию);
  TG_OWNER     — ваш личный номер в Telegram: робот пишет вам в личку о сбоях сборки и сохранениях пульта (по желанию).
Нет TG_BOT_TOKEN / TG_CHAT — тихо ничего не делает.

Что делает:
  0. Задания из пульта: сообщение (картинка, своё фото, кнопки, закрепить, без звука, позже, английский текст),
     опрос (картинка, итоги через N часов), «Новая версия», удалить/исправить последний пост, проверка.
  1. Новая версия — пост с картинкой «Вышла версия X».
  2. Техработы на серверах — запланированы / изменилось время / закончились.
  3. Итоги опросов, подписчики раз в день, дайджест по воскресеньям, праздничные открытки.
  4. Владельцу в личку: упала сборка, в пульте сохранили изменения, у робота ошибка.
Что помнит — docs/stats/tg-state.json (публичный файл: только номера постов, итоги и журнал, без токенов).
Первый запуск только запоминает текущее состояние и ничего не публикует — чтобы не завалить канал старым.
"""
import datetime, json, os, re, sys, urllib.error, urllib.parse, urllib.request, uuid

E = os.environ.get
TOKEN, CHAT, REPO = E('TG_BOT_TOKEN', ''), E('TG_CHAT', ''), E('GITHUB_REPOSITORY', 'Dubstep2004/apex-client')
STATE = E('TG_STATE', 'docs/stats/tg-state.json')  # пути можно подменить для проверки
SETTINGS = E('TG_SETTINGS', 'docs/tg/settings.json')
MEDIA = 'docs/tg/media/'
SITE = 'https://dubstep2004.github.io'
MSK = datetime.timezone(datetime.timedelta(hours=3))
DRY = '--dry' in sys.argv  # проверка без отправки: печатает, что бы отправил
MAX_POSTS = 8  # защита от спама: не больше автоматических постов за один запуск (остальное — в следующий раз)
sent = 0
split = lambda s: [c for c in re.split(r'[\s,;]+', s or '') if c]
CHATS, CHATS_EN, OWNER = split(CHAT), split(E('TG_CHAT_EN')), split(E('TG_OWNER'))



def read(path, default):
    try: return json.load(open(path))
    except (OSError, ValueError): return default


def next_due():
    """Режим --next для будильника в telegram.yml: через сколько секунд следующее дело и какой робот нужен.
    GitHub запускает «каждые 10 минут» очень редко (бывает раз в несколько часов), поэтому после каждого
    запуска будильник ждёт ровно до следующего дела. Печатает «секунды робот[,робот]» или «none»."""
    t = datetime.datetime.now(datetime.timezone.utc).timestamp() * 1000
    st, s, app = read(STATE, {}) or {}, read(SETTINGS, {}), read(E('TG_APP', 'docs/config/app.json'), {})
    due = []  # (время, робот)
    for it in (read(E('TG_OUTBOX', 'docs/tg/outbox.json'), {}).get('items') or []):
        w = int((it.get('opts') or {}).get('when') or 0)
        if w > t and str(it.get('id')) not in (st.get('results') or {}): due.append((w, 'telegram'))
    for v in ((app.get('servers') or {}).get('status') or {}).values():
        if isinstance(v, dict) and v.get('state') == 'maintenance' and (v.get('until') or 0) > t: due.append((v['until'] + 30000, 'telegram'))
        if isinstance(v, dict) and v.get('state') == 'maintenance' and (v.get('from') or 0) > t:  # напоминания за сутки и за час
            due += [(v['from'] - 86400_000 + 20000, 'telegram'), (v['from'] - 3600_000 + 20000, 'telegram')]
    for p in st.get('polls') or []: due.append((p.get('until') or 0, 'telegram'))
    for c in st.get('contests') or []:
        if not c.get('drawn'): due.append((c.get('until') or 0, 'telegram'))
    pend = read('docs/config/pending.json', {})
    if pend.get('at') and not pend.get('error'): due.append((max(pend['at'], t), 'apply-pending'))
    m = datetime.datetime.now(MSK)
    for k in range(8):  # дайджест (вс 18:00) и праздники (10:00) на неделю вперёд
        d = (m + datetime.timedelta(days=k)).replace(minute=1, second=0, microsecond=0)
        if s.get('digest', True) and d.weekday() == 6: due.append((d.replace(hour=18).timestamp() * 1000, 'telegram'))
        if s.get('holidays', True) and d.strftime('%m-%d') in ('12-31', '02-14', '03-08', '10-31'): due.append((d.replace(hour=10).timestamp() * 1000, 'telegram'))
    due = sorted(x for x in due if x[0] > t - 60000)
    if not due: return 'none'
    first_at = due[0][0]; bots = sorted({b for w, b in due if w <= first_at + 120000})
    return f'{max(60, int((first_at - t) / 1000) + 20)} {",".join(bots)}'


if '--next' in sys.argv:
    print(next_due()); raise SystemExit

if not (TOKEN and CHATS) and not DRY:
    print('Нет секретов TG_BOT_TOKEN / TG_CHAT — пропускаю'); raise SystemExit

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
try:
    import tg_card
except ImportError as e:  # нет Pillow — посты без картинок
    print('без картинок:', e); tg_card = None


class TgError(Exception):
    pass


def tg(method, **params):
    """Вызов Telegram Bot API. Ошибка Telegram — TgError с понятным текстом."""
    params = {k: v for k, v in params.items() if v is not None}
    if DRY:
        print('→', method, json.dumps(params, ensure_ascii=False)[:400]); return {'message_id': 1}
    data = urllib.parse.urlencode({k: (json.dumps(v) if isinstance(v, (dict, list, bool)) else v) for k, v in params.items()}).encode()
    try:
        with urllib.request.urlopen(urllib.request.Request(f'https://api.telegram.org/bot{TOKEN}/{method}', data), timeout=30) as r:
            res = json.load(r)
    except urllib.error.HTTPError as e:
        try: res = json.load(e)
        except Exception: raise TgError(f'HTTP {e.code}')
    if not res.get('ok'):
        raise TgError(nice(res.get('description')))
    return res['result']


def nice(d):
    d = str(d or 'ошибка')
    for a, b in [('chat not found', 'чат не найден — проверьте TG_CHAT и что бот добавлен'), ('not enough rights', 'у бота не хватает прав в канале'),
                 ('bot is not a member', 'бот не добавлен в чат'), ('Unauthorized', 'Telegram не узнал токен бота'),
                 ("message can't be deleted", 'пост нельзя удалить (старше 48 часов?)'), ('message to edit not found', 'пост не найден'),
                 ('message is not modified', 'текст тот же — нечего менять')]:
        if a.lower() in d.lower(): return b
    return d.replace('Bad Request: ', '')


def send_photo(chat, data, caption, html=False, fname='card.png', ctype='image/png', **extra):
    """Картинка с подписью (multipart). extra — reply_markup, disable_notification, reply_to_message_id."""
    if DRY:
        print('→ sendPhoto', chat, len(data), 'байт,', caption[:70].replace('\n', ' '), extra or ''); return {'message_id': 1}
    b = uuid.uuid4().hex; parts = []
    fields = {'chat_id': chat, 'caption': caption[:1024], **({'parse_mode': 'HTML'} if html else {}),
              **{k: (json.dumps(v) if isinstance(v, (dict, list, bool)) else v) for k, v in extra.items() if v is not None}}
    for k, v in fields.items():
        parts.append(f'--{b}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    parts.append(f'--{b}\r\nContent-Disposition: form-data; name="photo"; filename="{fname}"\r\nContent-Type: {ctype}\r\n\r\n'.encode() + data + b'\r\n')
    parts.append(f'--{b}--\r\n'.encode())
    req = urllib.request.Request(f'https://api.telegram.org/bot{TOKEN}/sendPhoto', b''.join(parts), {'Content-Type': 'multipart/form-data; boundary=' + b})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            res = json.load(r)
    except urllib.error.HTTPError as e:
        try: res = json.load(e)
        except Exception: raise TgError(f'HTTP {e.code}')
    if not res.get('ok'):
        raise TgError(nice(res.get('description')))
    return res['result']


def gh(path):
    if DRY:  # локальная проверка — через gh (в песочнице прямой доступ к API закрыт)
        import subprocess
        return json.loads(subprocess.run(['gh', 'api', 'repos/' + REPO + path], capture_output=True, text=True, check=True).stdout)
    req = urllib.request.Request('https://api.github.com/repos/' + REPO + path, headers={'Accept': 'application/vnd.github+json', **({'Authorization': 'Bearer ' + E('GITHUB_TOKEN')} if E('GITHUB_TOKEN') else {})})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


esc = lambda s: str(s).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
when = lambda ms: datetime.datetime.fromtimestamp(ms / 1000, MSK).strftime('%d.%m %H:%M') + ' МСК'


def dur_text(ms):
    m = max(1, round(ms / 60000))
    if m < 60:
        return f'{m} ' + ('минуту' if m % 10 == 1 and m % 100 != 11 else 'минуты' if m % 10 in (2, 3, 4) and m % 100 not in (12, 13, 14) else 'минут')
    h, m = divmod(m, 60)
    return f'{h} ч' + (f' {m} мин' if m else '')

state = read(STATE, None)
first = state is None
state = state or {'version': '', 'marks': {}}
for k, v in {'results': {}, 'log': [], 'last': [], 'polls': [], 'members': {}, 'holidays': {}, 'fails': []}.items():
    state.setdefault(k, v)
settings = read(SETTINGS, {})
changed = first
now = int(datetime.datetime.now(datetime.timezone.utc).timestamp() * 1000)
msk = datetime.datetime.now(MSK)
errors = []  # ошибки этого запуска — владельцу в личку


def log(kind, text, ok=True, err=''):
    global changed
    state['log'] = (state['log'] + [{'at': now, 'kind': kind, 'text': str(text)[:140], 'ok': ok, 'err': str(err)[:200]}])[-60:]
    changed = True
    if not ok: errors.append(f'{kind}: {err}')


def key(chat):
    """Чат в публичном tg-state.json — номером ('1', 'en1'), а не адресом: номера групп -100… не светим."""
    return ('en' + str(CHATS_EN.index(chat) + 1)) if chat in CHATS_EN and chat not in CHATS else str(CHATS.index(chat) + 1) if chat in CHATS else ''


def chat_of(k):
    k = str(k)
    try: return CHATS_EN[int(k[2:]) - 1] if k.startswith('en') else CHATS[int(k) - 1]
    except (ValueError, IndexError): return k if k.startswith('@') or k.lstrip('-').isdigit() else ''  # старая запись — адресом


def remember(kind, text, ids):
    """Запомнить последний пост (по чатам) — для «Удалить» и «Исправить» в пульте."""
    global changed
    if ids:
        state['last'] = (state['last'] + [{'at': now, 'kind': kind, 'text': str(text)[:300], 'ids': ids}])[-10:]; changed = True


def keyboard(buttons, cols=1):
    ok = [{'text': str(t)[:40], 'url': u} for t, u in (buttons or [])[:6] if isinstance(u, str) and u.startswith('https://')]
    rows = [ok[i:i + cols] for i in range(0, len(ok), cols)]
    return {'inline_keyboard': rows} if rows else None


def media(name):
    """Своё фото из пульта: docs/tg/media/<имя>. Только простое имя — без путей."""
    if not re.fullmatch(r'[\w-]{4,60}\.(jpg|jpeg|png)', str(name or '')):
        raise TgError('неправильное имя фото')
    return open(MEDIA + name, 'rb').read()


def send_one(chat, text, o, kind='Сообщение'):
    """Один пост в один чат по настройкам o: card, photo, buttons, silent, pin. Возвращает [номера сообщений]."""
    extra = {'reply_markup': keyboard(o.get('buttons')), 'disable_notification': True if o.get('silent') else None}
    if o.get('photo'):
        n = o['photo']; m = send_photo(chat, media(n), text, fname=n, ctype='image/png' if n.endswith('.png') else 'image/jpeg', **extra)
    elif o.get('card') and tg_card:
        m = send_photo(chat, tg_card.text_card(text, theme=o.get('theme')), text, **extra)
    else:
        m = tg('sendMessage', chat_id=chat, text=text, link_preview_options={'is_disabled': True}, **extra)
    if o.get('pin'):
        try: tg('pinChatMessage', chat_id=chat, message_id=m.get('message_id'), disable_notification=True)
        except TgError as e: errors.append('закрепить: ' + str(e))
    return [m.get('message_id')]


def broadcast(kind, text, chats, fn):
    """Отправить во все чаты; один чат не принял — в остальные всё равно. Итог — (готово, ошибки)."""
    done, errs, ids = [], [], {}
    for i, chat in enumerate(chats, 1):
        try:
            ids[key(chat)] = fn(chat); done.append(str(i))
        except Exception as e:
            errs.append(f'чат {i}: {e}')
    remember(kind, text, ids)
    log(kind, text, not errs, '; '.join(errs))
    return done, errs


def channel_url():
    """Ссылка на канал для подписи: из пульта («Ссылки» → «Канал»), иначе из TG_CHAT (@имя)."""
    u = ((app.get('links') or {}).get('channel') or '').strip()
    if u.startswith('https://t.me/'): return u
    at = next((c for c in CHATS if c.startswith('@')), '')
    return 'https://t.me/' + at[1:] if at else ''


def version_post(rel):
    """Пост «Вышла версия X»: картинка и подпись по разделам «Новое / Улучшено / Исправлено» из описания релиза.
    Тот же пост и у робота, и в пульте, и при «Обновить посты о версиях». Нет Pillow — картинка None."""
    body = (rel.get('body') or '').split('\n---')[0]
    sections, cur = [], None  # [(«🟢», «Новое», [пункты])]
    for l in body.splitlines():
        m = re.match(r'^(\S+)\s+\*\*(.+?)\*\*\s*$', l.strip())
        if m: cur = (m.group(1), m.group(2), []); sections.append(cur); continue
        if l.startswith('- '):
            if cur is None: cur = ('🟢', 'Новое', []); sections.append(cur)
            cur[2].append(re.sub(r'\*\*|`', '', l[2:]).strip())
    v = rel.get('tag_name', '').lstrip('v')
    ch = channel_url()
    tail = (f'\n\nОбновление придёт само — плашка «Вышла версия» или окно «Доступна версия» → «Обновить».'
            f'\n<a href="{SITE}/update/">Как обновить</a> · <a href="{SITE}/versions/">Все изменения</a> · <a href="{esc(SUPPORT)}">🐞 Сообщить об ошибке</a>'
            + (f'\n📣 <a href="{esc(ch)}">Наш канал</a> — подпишитесь, чтобы не пропускать обновления' if ch else ''))
    keep = [list(x[2]) for x in sections]
    def build():
        parts = [f'{e} <b>{esc(t)}</b>\n' + '\n'.join('• ' + esc(i) for i in keep[k]) for k, (e, t, _) in enumerate(sections) if keep[k]]
        return f'🆕 <b>Вышла версия {esc(v)}</b>\n\n' + '\n\n'.join(parts) + tail
    text = build()
    # подпись к картинке — до 1024 символов: убираем пункты с конца самого длинного раздела, а не режем посередине разметки
    while len(text) > 1000 and sum(map(len, keep)) > 1:
        k = max(range(len(keep)), key=lambda i: len(keep[i])); keep[k].pop(); text = build()
    if len(text) > 1000: text = f'🆕 <b>Вышла версия {esc(v)}</b>' + tail
    items = [i for x in keep for i in x][:8]
    return v, (tg_card.card(v, items) if tg_card else None), text


def send_version(rel, chats, extra_note=''):
    v, png, text = version_post(rel)
    # 2.27: кнопки под постом — как обновить и сообщить об ошибке (реакции 👍 — встроенные в канале)
    kb = keyboard([['⬇️ Как обновить', SITE + '/update/'], ['🐞 Нашёл ошибку', SUPPORT]]) if settings.get('buttons', True) else None
    def one(chat):
        try:
            return [(send_photo(chat, png, text, html=True, reply_markup=kb) if png else tg('sendMessage', chat_id=chat, text=text, parse_mode='HTML', link_preview_options={'is_disabled': True}, reply_markup=kb)).get('message_id')]
        except TgError as e:
            if not png: raise
            print('картинка не ушла:', e, '— отправляю текстом')
            return [tg('sendMessage', chat_id=chat, text=text, parse_mode='HTML', link_preview_options={'is_disabled': True}).get('message_id')]
    return broadcast('Новая версия', f'Вышла версия {v}{extra_note}', chats, one)


rel = gh('/releases/latest')
tag = rel.get('tag_name', '')
app = read(E('TG_APP', 'docs/config/app.json'), {})
SUPPORT = ((app.get('links') or {}).get('support') or '').strip()
SUPPORT = SUPPORT if SUPPORT.startswith('https://') else 'https://t.me/Apexx_support'

# ── 0. Задания из пульта (docs/tg/outbox.json). Пульт ждёт итог в state['results'][id].
ALLOWED = {'sendMessage': {'text', 'link_preview_options'}, 'sendPoll': {'question', 'options', 'is_anonymous', 'allows_multiple_answers'},
           'getChat': set(), 'sendVersion': {'version'}, 'deleteLast': set(), 'editLast': {'text'}, 'ping': set(), 'translate': {'text'}, 'contest': {'text', 'until', 'n', 'button'}, 'refreshVersions': set(), 'setButtons': {'mid', 'buttons'}}
box = read(E('TG_OUTBOX', 'docs/tg/outbox.json'), {})
results = state['results']
for it in (box.get('items') or [])[-40:]:
    rid = str(it.get('id', ''))[:40]
    if not rid or rid in results or not isinstance(it.get('body', {}), dict):
        continue
    o = it.get('opts') if isinstance(it.get('opts'), dict) else {}
    if int(o.get('when') or 0) > now:
        continue  # отложенный пост — ещё не время (пульт показывает его в «Запланировано»)
    late = now - max(int(it.get('at') or 0), int(o.get('when') or 0))
    method = it.get('method')
    if late > 6 * 3600 * 1000:
        results[rid] = {'ok': False, 'error': 'задание устарело — отправьте ещё раз', 'at': now}; changed = True; continue
    if method not in ALLOWED:
        results[rid] = {'ok': False, 'error': 'неизвестное действие', 'at': now}; changed = True; continue
    body = {k: v for k, v in it['body'].items() if k in ALLOWED[method]}
    done, errs = [], []
    try:
        if method == 'getChat':
            for i, chat in enumerate(CHATS, 1):
                try: r = tg('getChat', chat_id=chat); done.append(('канал ' if r.get('type') == 'channel' else 'группа ') + str(r.get('title') or r.get('username') or i))
                except Exception as e: errs.append(f'чат {i}: {e}')
        elif method == 'translate':  # 2.27: перевод на английский — бесплатный переводчик Cloudflare Workers AI
            acc, key = E('CF_ACCOUNT_ID', ''), E('CF_AI_TOKEN', '')
            if not (acc and key): raise TgError('нет секретов CF_ACCOUNT_ID и CF_AI_TOKEN — см. пульт → Telegram → Настройка')
            body_t = json.dumps({'text': str(body.get('text', ''))[:4000], 'source_lang': 'russian', 'target_lang': 'english'}).encode()
            req = urllib.request.Request(f'https://api.cloudflare.com/client/v4/accounts/{acc}/ai/run/@cf/meta/m2m100-1.2b', body_t, {'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
            try:
                with urllib.request.urlopen(req, timeout=60) as r: tr = json.load(r)
            except urllib.error.HTTPError as e: raise TgError(f'переводчик ответил {e.code}' + (' — проверьте CF_AI_TOKEN' if e.code in (401, 403) else ''))
            done = [str((tr.get('result') or {}).get('translated_text') or '')[:4096]]
            if not done[0]: raise TgError('переводчик вернул пустой текст')
        elif method == 'contest':  # 2.27: конкурс — пост с кнопкой «Участвую» (нажатия принимает бот на Cloudflare)
            until = int(body.get('until') or 0)
            if until < now + 10 * 60_000: raise TgError('время итогов должно быть хотя бы через 10 минут')
            cid = re.sub(r'[^\w-]', '', rid)[:24]
            text = str(body.get('text', ''))[:3500] + f'\n\n🎁 Итоги — {when(until)}. Нажмите «Участвую»: победителей выберем случайно и честно.'
            btn = str(body.get('button') or '🎉 Участвую')[:30]
            kb = {'inline_keyboard': [[{'text': btn, 'callback_data': 'join:' + cid}]]}
            done, errs = broadcast('Конкурс', text, CHATS, lambda chat: [tg('sendMessage', chat_id=chat, text=text, reply_markup=kb, link_preview_options={'is_disabled': True}).get('message_id')])
            if not errs:
                state.setdefault('contests', []).append({'cid': cid, 'until': until, 'n': max(1, min(int(body.get('n') or 1), 10)), 'title': text[:80], 'ids': (state['last'][-1]['ids'] if state['last'] else {}), 'drawn': False})
        elif method == 'setButtons':  # 2.29: кнопки-ссылки под любым постом канала (номер поста — из «Просмотров» в пульте)
            mid = int(body.get('mid') or 0)
            if mid <= 0: raise TgError('нет номера поста')
            kb = keyboard(body.get('buttons') or [], cols=2) or {'inline_keyboard': []}  # по две в ряд; пустой список — убрать кнопки
            for k, chat in [(key(c), c) for c in CHATS if c.startswith('@')][:1] or [(key(CHATS[0]), CHATS[0])]:
                tg('editMessageReplyMarkup', chat_id=chat, message_id=mid, reply_markup=kb); done.append(str(mid))
            log('Кнопки к посту', f'пост №{mid}: ' + (', '.join(str(t) for t, _ in (body.get('buttons') or [])) or 'кнопки убраны'))
        elif method == 'refreshVersions':  # 2.28: переписать уже опубликованные посты о версиях в новом виде
            found = {}  # версия → {ключ чата: номер сообщения}
            for g in state['last']:
                m = re.search(r'Вышла версия ([\d.]+)', g.get('text', ''))
                if g.get('kind') == 'Новая версия' and m:
                    for k, ids in g['ids'].items(): found.setdefault(m.group(1), {})[k] = ids[-1]
            vk = next((key(c) for c in CHATS if c.startswith('@')), '')  # открытая страница канала — номера старых постов
            for pv in state.get('views') or []:
                m = re.search(r'Вышла версия ([\d.]+)', pv.get('text', ''))
                if m and vk and pv.get('id', '').isdigit(): found.setdefault(m.group(1), {}).setdefault(vk, int(pv['id']))
            kb = keyboard([['⬇️ Как обновить', SITE + '/update/'], ['🐞 Нашёл ошибку', SUPPORT]]) if settings.get('buttons', True) else None
            for ver, where in sorted(found.items()):
                try: r_ = gh('/releases/tags/v' + re.sub(r'[^\d.]', '', ver))
                except Exception: errs.append(f'{ver}: нет такого релиза'); continue
                _, _, cap = version_post(r_)
                for k, mid in where.items():
                    chat = chat_of(k)
                    try:
                        try: tg('editMessageCaption', chat_id=chat, message_id=mid, caption=cap, parse_mode='HTML', reply_markup=kb)
                        except TgError as e:
                            if 'caption' not in str(e).lower() and 'no text' not in str(e).lower(): raise
                            tg('editMessageText', chat_id=chat, message_id=mid, text=cap, parse_mode='HTML', reply_markup=kb, link_preview_options={'is_disabled': True})
                        done.append(ver)
                    except TgError as e:
                        if 'not modified' in str(e).lower() or 'нечего менять' in str(e): done.append(ver)
                        else: errs.append(f'{ver}: {e}')
            if not found: errs.append('не нашёл постов о версиях — откройте пульт позже (робот собирает их номера раз в 3 часа)')
            log('Обновлены посты о версиях', ', '.join(sorted(set(done))), not errs, '; '.join(errs))
        elif method == 'ping':
            done = [f'робот работает · чатов: {len(CHATS)} · английский канал: {"да" if CHATS_EN else "нет"} · личка владельца: {"да" if OWNER else "нет"} · картинки: {"да" if tg_card else "нет"}']
        elif method == 'sendVersion':
            tagv = re.sub(r'[^\w.-]', '', 'v' + str(body.get('version', '')).lstrip('v'))[:24]
            done, errs = send_version(gh('/releases/tags/' + tagv), CHATS, ' (из пульта)')
            if tagv == tag and not errs: state['version'] = tag  # 19: объявили из пульта — робот не повторит
        elif method == 'sendMessage':
            text = str(body.get('text', ''))[:4096]
            if (o.get('card') or o.get('photo')) and len(text) > 1024:
                raise TgError(f'с картинкой подпись не длиннее 1024 знаков (сейчас {len(text)})')
            done, errs = broadcast('Сообщение', text, CHATS, lambda chat: send_one(chat, text, o))
            en = str(o.get('en') or '').strip()[:4096]
            if en:  # 20: английский текст — в англоязычный канал
                if not CHATS_EN: errs.append('английский: нет секрета TG_CHAT_EN')
                else:
                    ru = state['last'][-1] if state['last'] else None
                    d2, e2 = broadcast('Сообщение (EN)', en, CHATS_EN, lambda chat: send_one(chat, en, o)); done += ['EN ' + x for x in d2]; errs += e2
                    if ru is not None and state['last'] and state['last'][-1] is not ru and ru in state['last'][:-1]:  # английский — в ту же запись, что русский
                        g_en = state['last'].pop(); ru['en'] = g_en['ids']
        elif method == 'sendPoll':
            q = str(body.get('question', ''))[:300]
            opts = [o2 for o2 in (body.get('options') or []) if isinstance(o2, dict)][:10]
            def poll(chat):
                ids = []
                if o.get('card') and tg_card:  # картинка перед опросом
                    try: ids.append(send_photo(chat, tg_card.poll_card(q, [str(x.get('text', '')) for x in opts]), '📊 ' + q, disable_notification=True if o.get('silent') else None).get('message_id'))
                    except TgError as e: print('картинка опроса не ушла:', e)
                m = tg('sendPoll', chat_id=chat, question=q, options=opts, is_anonymous=body.get('is_anonymous', True), allows_multiple_answers=bool(body.get('allows_multiple_answers')), disable_notification=True if o.get('silent') else None)
                ids.append(m.get('message_id'))
                if o.get('pin'):
                    try: tg('pinChatMessage', chat_id=chat, message_id=m.get('message_id'), disable_notification=True)
                    except TgError as e: errors.append('закрепить: ' + str(e))
                h = int(o.get('results') or 0)
                if h > 0:  # 3: итоги опроса через h часов
                    state['polls'].append({'chat': key(chat), 'mid': m.get('message_id'), 'q': q, 'until': now + min(h, 168) * 3600 * 1000})
                return ids
            done, errs = broadcast('Опрос', q, CHATS, poll)
        elif method == 'deleteLast':  # 10: удалить последний пост (все его сообщения во всех чатах)
            if not state['last']: raise TgError('нет постов, которые можно удалить')
            g = state['last'][-1]
            for k, ids in {**g['ids'], **g.get('en', {})}.items():
                chat = chat_of(k)
                for mid in ids:
                    try: tg('deleteMessage', chat_id=chat, message_id=mid); done.append(str(mid))
                    except TgError as e: errs.append(str(e))
            if not errs: state['last'].pop()
            log('Удалён пост', g.get('text', ''), not errs, '; '.join(errs))
        elif method == 'editLast':  # 10: исправить текст последнего поста
            if not state['last']: raise TgError('нет постов, которые можно исправить')
            g, text = state['last'][-1], str(body.get('text', ''))[:4096]
            if g['kind'] in ('Опрос', 'Новая версия'): raise TgError('опрос и пост «Новая версия» исправить нельзя — удалите и отправьте заново')
            for k, ids in g['ids'].items():
                chat, mid = chat_of(k), ids[-1]
                try:
                    try: tg('editMessageText', chat_id=chat, message_id=mid, text=text, link_preview_options={'is_disabled': True})
                    except TgError as e:
                        if 'no text' not in str(e).lower() and 'there is no text' not in str(e).lower(): raise
                        tg('editMessageCaption', chat_id=chat, message_id=mid, caption=text[:1024])
                    done.append(str(mid))
                except TgError as e: errs.append(str(e))
            if not errs: g['text'] = text[:300]
            log('Исправлен пост', text, not errs, '; '.join(errs))
    except Exception as e:
        errs.append(str(e))
    print(f'из пульта: {method} — готово {len(done)}, ошибок {len(errs)}')
    results[rid] = {'ok': not errs, 'done': done, 'error': '; '.join(errs)[:300], 'at': now}; changed = True
if len(results) > 40:  # храним только последние итоги
    state['results'] = dict(sorted(results.items(), key=lambda kv: kv[1].get('at') or 0)[-40:])

# ── 1. Новая версия (не бета)
if tag and tag != state['version']:
    if not first and not rel.get('prerelease') and settings.get('version', True):  # пульт: «Автопосты → Новая версия»
        sent += 1
        send_version(rel, CHATS)
        v = tag.lstrip('v')
        if v.endswith('.0') and settings.get('video', True):  # пульт: «Видео „Что нового“»  # новая X.Y — ещё и видео «Что нового», если уже готово
            for chat in CHATS:
                try: tg('sendVideo', chat_id=chat, video=f'{SITE}/media/whatsnew-ru.mp4', caption=f'Главное в {esc(".".join(v.split(".")[:2]))} — за 12 секунд')
                except TgError as e: print('видео не отправилось:', e)
    state['version'] = tag; changed = True


def post(kind, text):
    """Автоматический пост (HTML) во все чаты, с ограничением MAX_POSTS за запуск."""
    global sent
    if sent >= MAX_POSTS:
        print('лимит постов за запуск — остальное в следующий раз'); return False
    sent += 1
    broadcast(kind, re.sub('<[^>]+>', '', text), CHATS, lambda chat: [tg('sendMessage', chat_id=chat, text=text, parse_mode='HTML', link_preview_options={'is_disabled': True}).get('message_id')])
    return True


# ── 2. Техработы на серверах (пометки «обслуживание» из пульта, кроме предпросмотра)
marks = {n: v for n, v in ((app.get('servers') or {}).get('status') or {}).items()
         if isinstance(v, dict) and v.get('state') == 'maintenance' and not v.get('preview') and (not v.get('until') or v['until'] > now)}
state.setdefault('mstart', {}); state.setdefault('remind', {})
for name, v in marks.items():
    key = f"{v.get('from') or 0}-{v.get('until') or 0}"
    start = v.get('from') or 0
    # 2.27: когда работы фактически начались — для сводки «шли 40 минут»
    if start <= now and name not in state['mstart']:
        state['mstart'][name] = start or now; changed = True
    # 2.27: напоминания за сутки и за час до начала (будильник робота просыпается к этому времени)
    rk = f'{name}|{key}'
    if not first and start > now and settings.get('maint', True) and settings.get('remind', True) and state['marks'].get(name) == key:
        done = state['remind'].get(rk, [])
        left = start - now
        for tag_r, lim, txt in (('hour', 3600_000, 'через час'), ('day', 86400_000, 'завтра' if datetime.datetime.fromtimestamp(start / 1000, MSK).date() != msk.date() else 'сегодня')):
            if left <= lim and tag_r not in done and sent < MAX_POSTS:
                post('Техработы: напоминание', f'⏰ <b>Напоминание: {txt} техработы — {esc(name)}</b>\nС {when(start)}' + (f' до {when(v["until"])}' if v.get('until') else '') + '.\nАвтопилот сам переключит вас на рабочий сервер.')
                state['remind'][rk] = done + ['day', 'hour'] if tag_r == 'hour' else done + ['day']; changed = True
                break
    if state['marks'].get(name) == key:
        continue
    if not first and sent >= MAX_POSTS:
        continue  # не запоминаем — напишем в следующий запуск
    if not first and settings.get('maint', True):  # пульт: «Автопосты → Техработы» (выключено — только запоминаем)
        start = v.get('from') or 0
        head = f'🔧 <b>Изменилось время техработ: {esc(name)}</b>' if name in state['marks'] else f'🔧 <b>Запланированы техработы: {esc(name)}</b>' if start > now else f'🔧 <b>Техработы: {esc(name)}</b>'
        span = (f'С {when(start)}' if start > now else 'Сейчас') + (f' до {when(v["until"])}' if v.get('until') else ' до окончания работ')
        note = f'\n{esc(v["note"])}' if v.get('note') else ''
        post('Техработы', f'{head}\n{span}.{note}\n\nАвтопилот сам переключит вас на рабочий сервер — ничего делать не нужно.\n<a href="{SITE}/status/">Статус серверов</a>')
        # напоминания, чьё время уже наступило, не нужны — о работах только что написали
        state['remind'][f'{name}|{key}'] = (['day'] if start - now <= 86400_000 else []) + (['hour'] if start - now <= 3600_000 else [])
    state['marks'][name] = key; changed = True
for name in list(state['marks']):
    if name not in marks:
        if not first and sent >= MAX_POSTS:
            continue
        if not first and settings.get('maint', True):
            # 2.27: сводка с картинкой — сколько шли работы
            began = state['mstart'].get(name)
            dur = dur_text(now - began) if began else ''
            text = f'✅ Работы закончены: {name}\n' + (f'Шли {dur}. ' if dur else '') + 'Сервер снова работает — спасибо за терпение!'
            sent += 1
            broadcast('Техработы закончены', text, CHATS, lambda chat: send_one(chat, text, {'card': True}))
        del state['marks'][name]; state['mstart'].pop(name, None); changed = True
        for k in [k for k in state['remind'] if k.startswith(name + '|')]: del state['remind'][k]

# ── 3a. Итоги опросов (3): закрываем опрос и публикуем картинку с процентами ответом на него
for p in list(state['polls']):
    if p['until'] > now:
        continue
    state['polls'].remove(p); changed = True
    try:
        chat = chat_of(p['chat']); r = tg('stopPoll', chat_id=chat, message_id=p['mid'])
        rows = [(x.get('text', ''), int(x.get('voter_count') or 0)) for x in r.get('options', [])] if not DRY else [('Да', 3), ('Нет', 1)]
        total = sum(c for _, c in rows)
        cap = f'📊 Итоги опроса: {p["q"]}\n\n' + '\n'.join(f'{t} — {round(100 * c / (total or 1))}%' for t, c in sorted(rows, key=lambda x: -x[1])) + f'\n\nПроголосовали: {total}. Спасибо!'
        m = send_photo(chat, tg_card.results_card(p['q'], rows), cap, reply_to_message_id=p['mid']) if tg_card else tg('sendMessage', chat_id=chat, text=cap, reply_to_message_id=p['mid'])
        remember('Итоги опроса', cap, {key(chat): [m.get('message_id')]}); log('Итоги опроса', p['q'])
    except Exception as e:
        log('Итоги опроса', p['q'], False, e)

# ── 3b. Подписчики раз в день (16) — для «Обзора» в пульте
today = msk.strftime('%Y-%m-%d')
if state.get('mday') != today:
    for i, chat in enumerate(CHATS, 1):
        try:
            n = tg('getChatMemberCount', chat_id=chat) if not DRY else 0
            if isinstance(n, int): state['members'][str(i)] = (state['members'].get(str(i), []) + [[today, n]])[-60:]
        except Exception as e:
            print('подписчики:', e)
    state['mday'] = today; changed = True
    # 2.27: значок «📣 Канал · N подписчиков» для README (docs/stats/tg-members.svg); сайт берёт число из tg-state.json
    n1 = (state['members'].get('1') or [[0, 0]])[-1][1]
    if n1 and not DRY:
        word = 'подписчик' if n1 % 10 == 1 and n1 % 100 != 11 else 'подписчика' if n1 % 10 in (2, 3, 4) and n1 % 100 not in (12, 13, 14) else 'подписчиков'
        label, val = 'Канал', f'{n1} {word}'
        lw, vw = 80, 18 + int(8.4 * len(val))
        open('docs/stats/tg-members.svg', 'w').write(
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{lw + vw}" height="24" role="img" aria-label="{label}: {val}">'
            f'<rect width="{lw + vw}" height="24" rx="12" fill="#1d1f33"/><rect x="{lw}" width="{vw}" height="24" rx="12" fill="#229ED9"/><rect x="{lw}" width="12" height="24" fill="#229ED9"/>'
            f'<g font-family="Verdana,DejaVu Sans,sans-serif" font-size="12" fill="#fff"><text x="12" y="16">📣 {label}</text><text x="{lw + 8}" y="16" font-weight="bold">{val}</text></g></svg>\n')


def members_week():
    out = []
    for i, rows in sorted(state['members'].items()):
        if rows:
            old = next((n for d, n in rows if d >= (msk - datetime.timedelta(days=7)).strftime('%Y-%m-%d')), rows[0][1])
            out.append((rows[-1][1], rows[-1][1] - old))
    return out


# ── 3c. Дайджест недели (7): воскресенье после 18:00 МСК
week = msk.strftime('%G-%V')
if settings.get('digest', True) and not first and msk.weekday() == 6 and msk.hour >= 18 and state.get('digest') != week:
    state['digest'] = week; changed = True
    try:
        since = (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=7)).strftime('%Y-%m-%dT%H:%M:%SZ')
        vers = [r['tag_name'].lstrip('v') for r in gh('/releases?per_page=15') if not r.get('prerelease') and not r.get('draft') and (r.get('published_at') or '') >= since]
        maint = sum(1 for l in state['log'] if l['kind'] == 'Техработы' and l['at'] > now - 7 * 86400000)
        mw = members_week()
        lines = [('🆕 Новые версии: ' + ', '.join(reversed(vers))) if vers else '🆕 Без новых версий — всё работает стабильно',
                 f'🔧 Техработ: {maint}' if maint else '🔧 Без техработ']
        if mw: lines.append(f'👥 Подписчиков: {mw[0][0]}' + (f' (+{mw[0][1]} за неделю)' if mw[0][1] > 0 else ''))
        text = '📅 Итоги недели APEX CLIENT\n\n' + '\n'.join(lines) + '\n\nСпасибо, что вы с нами!'
        broadcast('Дайджест', text, CHATS, lambda chat: send_one(chat, text, {'card': True, 'silent': True}))
    except Exception as e:
        log('Дайджест', '', False, e)

# ── 3d. Праздничные открытки (12) — после 10:00 МСК, раз в год
HOLIDAYS = {'12-31': ('newyear', 'С наступающим Новым годом!\nСпасибо, что были с нами весь год. Пусть в новом году всё открывается быстро и без ограничений 🎄'),
            '02-14': ('valentine', 'С Днём святого Валентина!\nПусть всё, что вы любите, всегда будет под рукой ❤️'),
            '03-08': ('march8', 'С 8 Марта!\nПоздравляем всех девушек и женщин — весны, тепла и быстрого интернета 🌷'),
            '10-31': ('halloween', 'Счастливого Хэллоуина!\nСтрашно только без VPN 🎃')}
h = HOLIDAYS.get(msk.strftime('%m-%d'))
if h and settings.get('holidays', True) and not first and msk.hour >= 10 and state['holidays'].get(h[0]) != msk.year:
    state['holidays'][h[0]] = msk.year; changed = True
    broadcast('Праздник', h[1], CHATS, lambda chat: send_one(chat, h[1], {'card': True, 'theme': h[0]}))

# ── 3f. Итоги конкурсов (38): в назначенное время просим бота на Cloudflare выбрать победителей и пишем ответом на пост
for c in state.get('contests', []):
    if c.get('drawn') or c['until'] > now: continue
    c['drawn'] = True; changed = True
    wurl, wkey = E('BOT_WORKER_URL', '').rstrip('/'), E('BOT_WORKER_KEY', '')
    try:
        if not (wurl.startswith('https://') and wkey): raise TgError('нет секретов BOT_WORKER_URL и BOT_WORKER_KEY — итоги можно подвести командой /draw ' + c['cid'] + ' в личке бота')
        req = urllib.request.Request(wurl + '/api/draw', json.dumps({'cid': c['cid'], 'n': c.get('n', 1)}).encode(), {'x-key': wkey, 'content-type': 'application/json'})
        r = json.load(urllib.request.urlopen(req, timeout=30)) if not DRY else {'count': 3, 'winners': [{'username': 'test'}]}
        if r.get('error'): raise TgError(r['error'])
        wins = ', '.join('@' + w['username'] if w.get('username') else (w.get('name') or 'участник') for w in r.get('winners') or [])
        text = f'🏆 Итоги конкурса\nУчастников: {r.get("count", 0)}\n' + (f'Победители: {wins}\nПоздравляем! Напишем вам в личку.' if wins else 'Участников не было — конкурс не состоялся.')
        for k, mids in (c.get('ids') or {}).items():
            chat = chat_of(k)
            try:
                tg('sendMessage', chat_id=chat, text=text, reply_to_message_id=mids[-1])
                tg('editMessageReplyMarkup', chat_id=chat, message_id=mids[-1], reply_markup={'inline_keyboard': []})
            except TgError as e: errors.append('итоги конкурса: ' + str(e))
        c['result'] = {'count': r.get('count', 0), 'winners': wins}
        log('Итоги конкурса', text)
    except Exception as e:
        log('Итоги конкурса', c.get('title', ''), False, e)
state['contests'] = [c for c in state.get('contests', []) if not c.get('drawn') or now - c['until'] < 30 * 86400_000][-20:]

# ── 3e. Просмотры постов (37): у бота нет доступа к просмотрам — берём с открытой страницы t.me/s/канал (раз в 3 часа)
if not first and now - int(state.get('vat') or 0) > 3 * 3600_000:
    for chat in CHATS:
        if not chat.startswith('@'): continue  # группы и закрытые каналы — нет открытой страницы
        try:
            req = urllib.request.Request(f'https://t.me/s/{chat[1:]}', headers={'User-Agent': 'Mozilla/5.0 (Linux; Android 14) Chrome/130.0 Mobile Safari/537.36'})
            html = urllib.request.urlopen(req, timeout=20).read().decode('utf-8', 'replace') if not DRY else ''
            posts = []
            for part in html.split('data-post="')[1:]:
                pid = part.split('"', 1)[0]
                mt = re.search(r'tgme_widget_message_text[^>]*>(.*?)</div>', part, re.S)
                mv = re.search(r'tgme_widget_message_views">([\d.,]+)([KM]?)<', part)
                md = re.search(r'datetime="([^"]+)"', part)
                if not mv: continue
                n = float(mv.group(1).replace(',', '.')) * {'': 1, 'K': 1000, 'M': 1_000_000}[mv.group(2)]
                text = re.sub(r'<[^>]+>', ' ', mt.group(1)) if mt else '(картинка или видео)'
                import html as _html
                text = re.sub(r'\s+', ' ', _html.unescape(text)).strip()[:90]
                posts.append({'id': pid.split('/')[-1], 'text': text, 'views': int(n), 'at': (md.group(1) if md else '')[:16]})
            if posts:
                state['views'] = posts[-30:]; state['vat'] = now; changed = True
        except Exception as e:
            print('просмотры:', type(e).__name__)
        break  # хватит первого публичного канала

# ── 4. Владельцу в личку (17): упала сборка, сохранили в пульте, ошибки робота
def notify(text):
    for chat in OWNER:
        try: tg('sendMessage', chat_id=chat, text=text, link_preview_options={'is_disabled': True})
        except Exception as e: print('личка владельца:', e)


if OWNER or DRY:
    try:
        for r in gh('/actions/runs?per_page=10&status=completed').get('workflow_runs', []):
            fresh = (r.get('updated_at') or '') >= (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(hours=3)).strftime('%Y-%m-%dT%H:%M:%SZ')
            if r.get('name') == 'Build APEX CLIENT' and r.get('conclusion') == 'failure' and fresh and r['id'] not in state['fails']:
                state['fails'] = (state['fails'] + [r['id']])[-20:]; changed = True
                if not first: notify(f'❌ Сборка не прошла: {r.get("display_title", "")[:120]}\nРелиз не выйдет, у пользователей остаётся прежняя версия.\n{r.get("html_url", "")}')
        c = (gh('/commits?path=docs/config/app.json&per_page=1') or [{}])[0]
        if c.get('sha') and c['sha'] != state.get('cfg_sha'):
            if state.get('cfg_sha') and not first: notify('💾 В пульте сохранили: ' + c.get('commit', {}).get('message', '').split('\n')[0][:200])
            state['cfg_sha'] = c['sha']; changed = True
    except Exception as e:
        print('проверка для владельца:', e)
    if errors and not first: notify('⚠️ Робот канала: ' + '; '.join(errors)[:900])

if changed:
    os.makedirs(os.path.dirname(STATE) or '.', exist_ok=True)
    if not DRY:
        open(STATE, 'w').write(json.dumps(state, ensure_ascii=False, indent=1) + '\n')
    print('состояние обновлено' + (' (первый запуск — ничего не отправлял)' if first else ''))
else:
    print('нового нет')
