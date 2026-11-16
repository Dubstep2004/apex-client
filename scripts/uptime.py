"""Сколько дней без сбоев и техработ — для визитки («Без сбоев и техработ: N дней»).

Проходит по истории docs/config/app.json в git: в каждой версии файла ищет пометки серверов
«обслуживание» и аварийные баннеры (кроме предпросмотра) и находит, когда закончился последний
такой период. Пишет docs/stats/uptime.json: {"since": мс, "checked": мс, "days": {...}, "events": [...]}.
days — дни за 90 суток, когда что-то было: 'a' авария, 'm' обслуживание, 's' медленно (для полоски на странице статуса).
events — последние 20 событий: {"n": сервер или «Все», "k": a/m/s, "a": начало, "b": конец}. Только названия серверов
и время — без адресов. Запускается из stats.yml.
"""
import json, subprocess, time

APP = 'docs/config/app.json'
now = int(time.time() * 1000)
log = subprocess.run(['git', 'log', '--format=%H %ct', '--', APP], capture_output=True, text=True, check=True).stdout.split('\n')
commits = [(h, int(t) * 1000) for h, t in (l.split() for l in log if l.strip())][::-1]  # от старых к новым
if not commits:
    raise SystemExit('нет истории app.json')
last_end, ongoing = None, False
ev = {}  # (сервер, вид, начало) → конец
for i, (h, t0) in enumerate(commits):
    t1 = commits[i + 1][1] if i + 1 < len(commits) else now
    try:
        c = json.loads(subprocess.run(['git', 'show', f'{h}:{APP}'], capture_output=True, text=True, check=True).stdout)
    except Exception:
        continue
    st = (c.get('servers') or {}).get('status', {}) or {}
    for name, v in st.items():
        if isinstance(v, dict) and v.get('state') in ('maintenance', 'slow') and not v.get('preview'):
            a, b = max(v.get('from') or 0, t0), min(v.get('until') or t1, t1)
            if a < b:
                k = (str(name)[:40], 'm' if v.get('state') == 'maintenance' else 's', v.get('from') or a)
                ev[k] = (min(ev.get(k, (a, b))[0], a), max(ev.get(k, (a, b))[1], b))
    for bn in c.get('banners') or []:
        if isinstance(bn, dict) and bn.get('level') == 'alert' and not bn.get('preview'):
            a, b = max(bn.get('from') or 0, t0), min(bn.get('to') or t1, t1)
            if a < b:
                k = ('Все', 'a', bn.get('from') or a); ev[k] = (min(ev.get(k, (a, b))[0], a), max(ev.get(k, (a, b))[1], b))
    spans = [(v.get('from') or 0, v.get('until') or 0) for v in st.values()
             if isinstance(v, dict) and v.get('state') == 'maintenance' and not v.get('preview')]
    spans += [(b.get('from') or 0, b.get('to') or 0) for b in c.get('banners') or []
              if isinstance(b, dict) and b.get('level') == 'alert' and not b.get('preview')]
    for a, b in spans:
        start, end = max(a, t0), min(b or t1, t1)
        if start >= end:
            continue  # в эту версию файла период не попал
        if end >= now - 60_000:
            ongoing = True
        last_end = max(last_end or 0, end)
since = now if ongoing else (last_end or commits[0][1])
# История для страницы статуса: по дням (МСК) за 90 суток, худшее за день
ev = {k: v for k, v in ev.items() if v[1] - v[0] >= 120_000}  # пробные пометки на секунды не считаем
rank = {'s': 1, 'm': 2, 'a': 3}; days = {}; D = 86_400_000; MSK = 3 * 3_600_000
for (n, k, _), (a, b) in ev.items():
    d = max(a, now - 90 * D)
    while d < b:
        key = time.strftime('%Y-%m-%d', time.gmtime((d + MSK) / 1000))
        if rank[k] > rank.get(days.get(key), 0): days[key] = k
        d = ((d + MSK) // D + 1) * D - MSK
events = sorted(({'n': n, 'k': k, 'a': a, 'b': b if b < now - 60_000 else 0} for (n, k, _), (a, b) in ev.items()), key=lambda x: -x['a'])[:20]
open('docs/stats/uptime.json', 'w').write(json.dumps({'since': since, 'checked': now, 'days': dict(sorted(days.items())), 'events': events}, ensure_ascii=False) + '\n')
print('Без сбоев с', time.strftime('%Y-%m-%d %H:%M', time.gmtime(since / 1000)), '— дней:', (now - since) // 86_400_000)
