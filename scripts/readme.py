"""Обновляет README.md из Changelog.kt (тот же разбор, что scripts/changelog.py):
раздел «Что нового», строку «📱 Android — <b>X</b>» и значок версии на главной картинке
(docs/img/hero.webp, «v3.20» — только номер X.Y, чтобы не перерисовывать картинку на каждом исправлении).
Запуск из корня репозитория: python3 scripts/readme.py"""
import os
import re
import sys
sys.path.insert(0, 'scripts')
import changelog as cl

rels = cl.parse()


def sec(r):
    return cl.section(r, images=True).replace('img/tags/', 'docs/img/tags/')


t = open('README.md', encoding='utf-8').read()
s = t.index('\n### ', t.index('<a id="whatsnew"></a>')) + 1
e = t.index('Полный список с тегами')
out = [f"### {rels[0]['version']} · {rels[0]['date']}\n", sec(rels[0]), f"#### 📚 Все версии ({len(rels)})\n", "Каждая версия — отдельная вкладка.\n"]
for r in rels[1:]:
    out.append(f"<details>\n<summary><b>{r['version']}</b> · {r['date']}</summary>\n\n{sec(r)}\n</details>\n")
t = t[:s] + "\n".join(out) + "\n" + t[e:]
ver = rels[0]['version']
t = re.sub(r'📱 Android — <b>[^<]+</b>', f'📱 Android — <b>{ver}</b>', t, count=1)
open('README.md', 'w', encoding='utf-8').write(t)


def hero_badge(ver):
    """Значок «vX.Y» на главной картинке. Основа без текста — docs/img/src/hero-base.webp."""
    label = 'v' + '.'.join(ver.split('-')[0].split('.')[:2])
    mark = 'docs/img/src/hero-version.txt'
    if os.path.exists(mark) and open(mark).read().strip() == label:
        return label + ' (без изменений)'
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        return 'нет Pillow — картинку не обновил'
    base = Image.open('docs/img/src/hero-base.webp')
    font = ImageFont.truetype('docs/img/src/manrope-700.woff', 15)
    frames, durs = [], []
    for i in range(base.n_frames):
        base.seek(i)
        durs.append(base.info.get('duration', 100))
        f = base.convert('RGB')
        ImageDraw.Draw(f).text((110, 477), label, font=font, fill=(223, 139, 181), anchor='mm')
        frames.append(f)
    frames[0].save('docs/img/hero.webp', save_all=True, append_images=frames[1:], duration=durs, loop=0, quality=82, method=6)
    open(mark, 'w').write(label + '\n')
    return label


print(len(rels), ver, '· картинка:', hero_badge(ver))
