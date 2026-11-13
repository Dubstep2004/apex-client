"""Сколько дней без сбоев и техработ — для визитки («Без сбоев и техработ: N дней»).

Проходит по истории docs/config/app.json в git: в каждой версии файла ищет пометки серверов
«обслуживание» и аварийные баннеры (кроме предпросмотра) и находит, когда закончился последний
такой период. Пишет docs/stats/uptime.json: {"since": мс, "checked": мс}. Запускается из stats.yml.
"""
import json, subprocess, time

APP = 'docs/config/app.json'
now = int(time.time() * 1000)
log = subprocess.run(['git', 'log', '--format=%H %ct', '--', APP], capture_output=True, text=True, check=True).stdout.split('\n')
commits = [(h, int(t) * 1000) for h, t in (l.split() for l in log if l.strip())][::-1]  # от старых к новым
if not commits:
    raise SystemExit('нет истории app.json')
last_end, ongoing = None, False
for i, (h, t0) in enumerate(commits):
    t1 = commits[i + 1][1] if i + 1 < len(commits) else now
    try:
        c = json.loads(subprocess.run(['git', 'show', f'{h}:{APP}'], capture_output=True, text=True, check=True).stdout)
    except Exception:
        continue
    spans = [(v.get('from') or 0, v.get('until') or 0) for v in (c.get('servers') or {}).get('status', {}).values()
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
open('docs/stats/uptime.json', 'w').write(json.dumps({'since': since, 'checked': now}) + '\n')
print('Без сбоев с', time.strftime('%Y-%m-%d %H:%M', time.gmtime(since / 1000)), '— дней:', (now - since) // 86_400_000)
