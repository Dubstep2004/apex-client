"""Обновляет раздел «Что нового» в README.md из Changelog.kt (тот же разбор, что scripts/changelog.py).
Запуск из корня репозитория: python3 scripts/readme.py
Потом вручную: строка «📱 Android — <b>X</b>» в README (sed)."""
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
open('README.md', 'w', encoding='utf-8').write(t)
print(len(rels), rels[0]['version'])
