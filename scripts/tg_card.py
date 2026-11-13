"""Картинки для Telegram (1280×720 PNG): «Вышла версия X» и опрос. Рисует Pillow, шрифты — docs/img/src/*.ttf (OFL).
Проверка: python3 scripts/tg_card.py 3.21.0 "Пункт один" "Пункт два" > /tmp/card.png
Опрос:    python3 scripts/tg_card.py --poll "Какой сервер добавить?" Япония США Турция > /tmp/poll.png"""
import io, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1280, 720
FONTS = 'docs/img/src/'


def font(name, size, weight):
    f = ImageFont.truetype(FONTS + name, size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def gradient(w, h, c1, c2):
    g = Image.new('RGB', (w, 1))
    for x in range(w):
        k = x / max(1, w - 1); g.putpixel((x, 0), tuple(int(a + (b - a) * k) for a, b in zip(c1, c2)))
    return g.resize((w, h))


def wrap(d, text, f, width, lines=2):
    words, out, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if d.textlength(t, font=f) <= width: cur = t
        else:
            out.append(cur); cur = w
            if len(out) == lines: break
    if len(out) < lines and cur: out.append(cur)
    if len(out) == lines and ' '.join(out) != text:
        while d.textlength(out[-1] + '…', font=f) > width and ' ' in out[-1]: out[-1] = out[-1].rsplit(' ', 1)[0]
        if ' ' in out[-1] and out[-1].rsplit(' ', 1)[1].startswith(('(', '«')): out[-1] = out[-1].rsplit(' ', 1)[0]  # не обрывать на скобке
        out[-1] = out[-1].rstrip(',.;:—- ') + '…'
    return out


def card(version, items):
    im = Image.new('RGB', (W, H), (10, 13, 28))
    glow = Image.new('RGB', (W, H), (10, 13, 28)); g = ImageDraw.Draw(glow)
    g.ellipse((760, -120, 1400, 520), fill=(60, 70, 200)); g.ellipse((-260, 420, 380, 980), fill=(20, 120, 120)); g.ellipse((300, -260, 760, 120), fill=(70, 40, 140))
    im = Image.blend(im, glow.filter(ImageFilter.GaussianBlur(150)), .75)
    d = ImageDraw.Draw(im)
    # бренд
    sq = gradient(52, 52, (109, 92, 255), (45, 226, 230)); m = Image.new('L', (52, 52), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, 51, 51), 15, fill=255)
    im.paste(sq, (80, 70), m); d.text((106, 96), 'A', font=font('Unbounded.ttf', 30, 800), fill='white', anchor='mm')
    d.text((148, 96), 'APEX CLIENT', font=font('Manrope.ttf', 26, 800), fill=(201, 204, 234), anchor='lm')
    # заголовок и версия градиентом
    d.text((80, 160), 'Вышла версия', font=font('Unbounded.ttf', 62, 700), fill=(243, 244, 255))
    fv = font('Unbounded.ttf', 96, 800); bb = d.textbbox((0, 0), version, font=fv)
    mask = Image.new('L', (bb[2] + 10, bb[3] + 20), 0); ImageDraw.Draw(mask).text((0, 0), version, font=fv, fill=255)
    im.paste(gradient(mask.width, mask.height, (139, 125, 255), (45, 226, 230)), (78, 240), mask)
    # пункты — сколько влезает до подписи внизу
    fi, y = font('Manrope.ttf', 28, 600), 395
    for it in items[:3]:
        lines = wrap(d, it, fi, 640)
        if y + 38 * len(lines) > 630: break
        for i, line in enumerate(lines):
            if i == 0: d.ellipse((84, y + 13, 96, y + 25), fill=(125, 140, 255))
            d.text((112, y), line, font=fi, fill=(214, 217, 238)); y += 38
        y += 14
    d.text((80, 676), 'Обновление придёт само · dubstep2004.github.io', font=font('Manrope.ttf', 22, 600), fill=(150, 155, 190), anchor='lm')
    # свечение за щитом
    halo = Image.new('L', (W, H), 0); ImageDraw.Draw(halo).ellipse((880, 170, 1200, 510), fill=150)
    im = Image.composite(Image.new('RGB', (W, H), (70, 110, 255)), im, halo.filter(ImageFilter.GaussianBlur(70)))
    # щит: рисуем в 4 раза крупнее и уменьшаем — ровные края
    k, sw, shh = 4, 300, 360
    def bez(p0, p1, p2, p3, n=24):
        return [tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t ** 2 * c + t ** 3 * e for a, b, c, e in zip(p0, p1, p2, p3)) for t in [i / n for i in range(n + 1)]]
    pts = [(150, 0), (300, 50), (300, 170)] + bez((300, 170), (300, 270), (230, 330), (150, 360)) + bez((150, 360), (70, 330), (0, 270), (0, 170)) + [(0, 50)]
    big = Image.new('L', (sw * k, shh * k), 0); ImageDraw.Draw(big).polygon([(x * k, y * k) for x, y in pts], fill=255)
    sh = big.resize((sw, shh), Image.LANCZOS)
    im.paste(gradient(sw, shh, (63, 184, 255), (125, 92, 255)), (890, 160), sh)
    d = ImageDraw.Draw(im); d.line([(985, 335), (1030, 380), (1105, 290)], fill='white', width=24, joint='curve')
    for x, y in [(985, 335), (1105, 290)]: d.ellipse((x - 12, y - 12, x + 12, y + 12), fill='white')
    # «Обновить»
    d.rounded_rectangle((930, 560, 1160, 624), 32, fill='white')
    d.text((1060, 592), 'Обновить', font=font('Manrope.ttf', 26, 800), fill=(10, 13, 28), anchor='mm')
    d.line([(962, 578), (962, 604)], fill=(10, 13, 28), width=5); d.line([(952, 594), (962, 605), (972, 594)], fill=(10, 13, 28), width=5)
    buf = io.BytesIO(); im.save(buf, 'PNG', optimize=True); return buf.getvalue()


def poll_card(question, options):
    """Картинка перед опросом: вопрос крупно и варианты кружками — голосовать ниже, в самом опросе."""
    im = Image.new('RGB', (W, H), (10, 13, 28))
    glow = Image.new('RGB', (W, H), (10, 13, 28)); g = ImageDraw.Draw(glow)
    g.ellipse((820, -160, 1460, 480), fill=(110, 50, 190)); g.ellipse((-260, 420, 380, 980), fill=(20, 120, 120)); g.ellipse((420, 520, 980, 1000), fill=(50, 60, 190))
    im = Image.blend(im, glow.filter(ImageFilter.GaussianBlur(150)), .75)
    d = ImageDraw.Draw(im)
    sq = gradient(52, 52, (109, 92, 255), (45, 226, 230)); m = Image.new('L', (52, 52), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, 51, 51), 15, fill=255)
    im.paste(sq, (80, 70), m); d.text((106, 96), 'A', font=font('Unbounded.ttf', 30, 800), fill='white', anchor='mm')
    d.text((148, 96), 'APEX CLIENT · опрос', font=font('Manrope.ttf', 26, 800), fill=(201, 204, 234), anchor='lm')
    # вопрос: крупно, до 3 строк; длинный — шрифт меньше
    for size in (60, 52, 44):
        fq = font('Unbounded.ttf', size, 700); ql = wrap(d, question, fq, 1110, 3)
        if len(ql) <= 2 or size == 44: break
    y = 160
    for line in ql:
        d.text((80, y), line, font=fq, fill=(243, 244, 255)); y += int(size * 1.3)
    # варианты: до 6, в две колонки, если больше трёх
    opts = [o for o in options if o.strip()][:6]; more = len([o for o in options if o.strip()]) - len(opts)
    cols = 2 if len(opts) > 3 else 1; cw = 540 if cols == 2 else 1110; fo = font('Manrope.ttf', 30, 700)
    rows = (len(opts) + cols - 1) // cols; rh = min(84, (640 - y - 20) // max(1, rows)); y += 24
    for i, o in enumerate(opts):
        x0, y0 = 80 + (i // rows) * (cw + 30), y + (i % rows) * rh
        d.rounded_rectangle((x0, y0, x0 + cw, y0 + rh - 14), 22, fill=(28, 31, 58), outline=(70, 78, 140), width=2)
        cy = y0 + (rh - 14) // 2; d.ellipse((x0 + 24, cy - 14, x0 + 52, cy + 14), outline=(139, 125, 255), width=4)
        t = wrap(d, o, fo, cw - 100, 1)[0] if o else ''
        d.text((x0 + 72, cy), t, font=fo, fill=(226, 228, 245), anchor='lm')
    word = 'вариант' if more % 10 == 1 and more % 100 != 11 else 'варианта' if more % 10 in (2, 3, 4) and more % 100 not in (12, 13, 14) else 'вариантов'
    foot = 'Голосуйте в опросе ниже ↓' + (f' · и ещё {more} {word}' if more > 0 else '')
    d.text((80, 676), foot, font=font('Manrope.ttf', 22, 600), fill=(150, 155, 190), anchor='lm')
    buf = io.BytesIO(); im.save(buf, 'PNG', optimize=True); return buf.getvalue()


if __name__ == '__main__':
    if sys.argv[1] == '--poll':
        sys.stdout.buffer.write(poll_card(sys.argv[2], sys.argv[3:]))
    else:
        sys.stdout.buffer.write(card(sys.argv[1], sys.argv[2:]))
