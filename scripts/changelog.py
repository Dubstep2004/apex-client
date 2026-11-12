#!/usr/bin/env python3
# APEX CLIENT — © 2026 Dubstep2004. Код — GPL-3.0 (LICENSE), название и логотип — TRADEMARKS.md.
"""История версий из app/.../data/Changelog.kt с цветными метками.

  python3 scripts/changelog.py notes [версия]   описание релиза для GitHub (по умолчанию — последняя версия)
  python3 scripts/changelog.py full             docs/CHANGELOG.md — все версии

Метки: n — Новое (зелёная), u — Улучшено и c — Изменено (фиолетовые),
f — Исправлено (синяя), d — Удалено (красная).
"""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/apexvpn/client/data/Changelog.kt"
KINDS = [  # порядок вывода
    ("n", "Новое", "🟢", "new"),
    ("u", "Улучшено", "🟣", "improved"),
    ("c", "Изменено", "🟣", "changed"),
    ("f", "Исправлено", "🔵", "fixed"),
    ("d", "Удалено", "🔴", "removed"),
]

def parse():
    text = SRC.read_text(encoding="utf-8")
    releases = []
    for m in re.finditer(r'Release\(\s*"([^"]+)",\s*(\d+),\s*"([^"]+)",\s*listOf\((.*?)\n\s*\),\s*\n\s*\)', text, re.S):
        items = re.findall(r'^\s*([nucfd])\("((?:[^"\\]|\\.)*)"\),?\s*$', m.group(4), re.M)
        releases.append({"version": m.group(1), "code": int(m.group(2)), "date": m.group(3), "items": items})
    return releases

def section(rel, images=False):
    out = []
    for k, title, emoji, slug in KINDS:
        items = [t for kk, t in rel["items"] if kk == k]
        if not items:
            continue
        head = f'<img src="img/tags/{slug}.svg" alt="{title}" height="22">' if images else f"{emoji} **{title}**"
        out.append(head)
        out.append("")
        out += [f"- {t}" for t in items]
        out.append("")
    return "\n".join(out).rstrip() + "\n"

def main():
    rels = parse()
    if not rels:
        sys.exit("история версий не найдена")
    cmd = sys.argv[1] if len(sys.argv) > 1 else "notes"
    if cmd == "notes":
        want = sys.argv[2] if len(sys.argv) > 2 else None
        rel = next((r for r in rels if r["version"] == want), rels[0]) if want else rels[0]
        sys.stdout.write(section(rel))
    elif cmd == "full":
        parts = ["# История версий APEX CLIENT", "",
                 '<img src="img/tags/new.svg" height="22"> <img src="img/tags/improved.svg" height="22"> '
                 '<img src="img/tags/changed.svg" height="22"> <img src="img/tags/fixed.svg" height="22"> '
                 '<img src="img/tags/removed.svg" height="22">', ""]
        for r in rels:
            parts += [f"## {r['version']} · {r['date']}", "", section(r, images=True)]
        (ROOT / "docs/CHANGELOG.md").write_text("\n".join(parts), encoding="utf-8")
        print(f"docs/CHANGELOG.md: {len(rels)} версий")
    else:
        sys.exit(__doc__)

if __name__ == "__main__":
    main()
