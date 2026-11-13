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


def read(path, default):
    try: return json.load(open(path))
    except (OSError, ValueError): return default


esc = lambda s: str(s).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
when = lambda ms: datetime.datetime.fromtimestamp(ms / 1000, MSK).strftime('%d.%m %H:%M') + ' МСК'

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


def keyboard(buttons):
    rows = [[{'text': str(t)[:40], 'url': u}] for t, u in (buttons or [])[:4] if isinstance(u, str) and u.startswith('https://')]
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


def version_post(rel):
    """Пост «Вышла версия X»: картинка и подпись с пунктами из описания релиза. Тот же и у робота, и в пульте."""
    body = (rel.get('body') or '').split('\n---')[0]
    items = [re.sub(r'\*\*|`', '', l[2:]).strip() for l in body.splitlines() if l.startswith('- ')][:8]
    v = rel.get('tag_name', '').lstrip('v')
    tail = f'\n\nОбновление придёт само — плашка «Вышла версия» или окно «Доступна версия» → «Обновить».\n<a href="{SITE}/update/">Как обновить</a> · <a href="{SITE}/versions/">Все изменения</a>'
    while True:  # подпись к картинке — до 1024 символов: убираем пункты целиком
        text = f'🆕 <b>Вышла версия {esc(v)}</b>\n\n' + '\n'.join('• ' + esc(i) for i in items) + tail
        if len(text) <= 1000 or len(items) <= 1: break
        items = items[:-1]
    if len(text) > 1000: text = f'🆕 <b>Вышла версия {esc(v)}</b>' + tail
    return v, (tg_card.card(v, items) if tg_card else None), text


def send_version(rel, chats, extra_note=''):
    v, png, text = version_post(rel)
    def one(chat):
        try:
            return [(send_photo(chat, png, text, html=True) if png else tg('sendMessage', chat_id=chat, text=text, parse_mode='HTML', link_preview_options={'is_disabled': True})).get('message_id')]
        except TgError as e:
            if not png: raise
            print('картинка не ушла:', e, '— отправляю текстом')
            return [tg('sendMessage', chat_id=chat, text=text, parse_mode='HTML', link_preview_options={'is_disabled': True}).get('message_id')]
    return broadcast('Новая версия', f'Вышла версия {v}{extra_note}', chats, one)


rel = gh('/releases/latest')
tag = rel.get('tag_name', '')

# ── 0. Задания из пульта (docs/tg/outbox.json). Пульт ждёт итог в state['results'][id].
ALLOWED = {'sendMessage': {'text', 'link_preview_options'}, 'sendPoll': {'question', 'options', 'is_anonymous', 'allows_multiple_answers'},
           'getChat': set(), 'sendVersion': {'version'}, 'deleteLast': set(), 'editLast': {'text'}, 'ping': set()}
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
    if not first and not rel.get('prerelease'):
        sent += 1
        send_version(rel, CHATS)
        v = tag.lstrip('v')
        if v.endswith('.0'):  # новая X.Y — ещё и видео «Что нового», если уже готово
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
app = read(E('TG_APP', 'docs/config/app.json'), {})
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
        post('Техработы', f'{head}\n{span}.{note}\n\nАвтопилот сам переключит вас на рабочий сервер — ничего делать не нужно.\n<a href="{SITE}/status/">Статус серверов</a>')
    state['marks'][name] = key; changed = True
for name in list(state['marks']):
    if name not in marks:
        if not first and sent >= MAX_POSTS:
            continue
        if not first:
            post('Техработы закончены', f'✅ <b>Работы закончены: {esc(name)}</b>\nСервер снова работает. Спасибо за терпение!')
        del state['marks'][name]; changed = True

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
