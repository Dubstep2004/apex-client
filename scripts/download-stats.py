#!/usr/bin/env python3
"""211. Статистика скачиваний APK из GitHub Releases → docs/stats.svg (картинка для README).

Запускается в GitHub Actions раз в день (stats.yml). Вход: JSON списка релизов
(gh api repos/OWNER/REPO/releases --paginate) из stdin.
"""
import json, sys, datetime, html

raw = sys.stdin.read().strip()
# gh api --paginate --slurp → массив страниц; без --slurp — одна страница
data = json.loads(raw) if raw else []
releases = [r for page in data for r in page] if data and isinstance(data[0], list) else data

def apk_count(rel, wear=False):
    return sum(a.get("download_count", 0) for a in rel.get("assets", [])
               if a["name"].endswith(".apk") and (("wear" in a["name"]) == wear))

releases = [r for r in releases if not r.get("draft")]
total = sum(apk_count(r) for r in releases)
wear = sum(apk_count(r, True) for r in releases)
latest = next((r for r in releases if not r.get("prerelease")), None)
latest_name = latest["tag_name"] if latest else "—"
latest_n = apk_count(latest) if latest else 0
top = sorted(releases, key=lambda r: r.get("published_at") or "", reverse=True)[:6]
updated = datetime.datetime.now(datetime.timezone.utc).strftime("%d.%m.%Y")

W, H = 880, 300
mx = max([apk_count(r) for r in top] + [1])
bars = ""
for i, r in enumerate(reversed(top)):
    n = apk_count(r)
    h = int(150 * n / mx)
    x = 470 + i * 64
    bars += (f'<rect x="{x}" y="{230 - h}" width="40" height="{max(h, 2)}" rx="8" fill="url(#g)"/>'
             f'<text x="{x + 20}" y="{222 - h}" text-anchor="middle" class="s">{n}</text>'
             f'<text x="{x + 20}" y="252" text-anchor="middle" class="d">{html.escape(r["tag_name"])}</text>')

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<style>text{{font-family:Inter,"Segoe UI",Roboto,Arial,sans-serif;fill:#F4F4F7}}.d{{font-size:13px;fill:#9A9AAE}}.s{{font-size:14px;font-weight:700}}</style>
<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#15132A"/><stop offset="1" stop-color="#0A0A12"/></linearGradient>
<linearGradient id="g" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="#7B6CFF"/><stop offset="1" stop-color="#22D3EE"/></linearGradient></defs>
<rect width="{W}" height="{H}" rx="24" fill="url(#bg)" stroke="#2A2A40"/>
<text x="36" y="58" style="font-size:22px;font-weight:800">📊 Скачивания APEX CLIENT</text>
<text x="36" y="84" class="d">из GitHub Releases · обновлено {updated}</text>
<text x="36" y="160" style="font-size:64px;font-weight:900">{total}</text>
<text x="36" y="190" class="d">всего скачиваний APK для телефона</text>
<text x="36" y="236" class="s">{html.escape(latest_name)}: {latest_n}</text><text x="36" y="258" class="d">последняя версия · часы Wear OS: {wear}</text>
<text x="470" y="58" class="d">последние версии</text>
{bars}
</svg>
'''
sys.stdout.write(svg)
