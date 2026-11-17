"""Картинки для бота поддержки (1280×720 JPG): приветствие, режим владельца и обложки тем.
Кладёт в docs/img/bot/ — бот берёт их по адресу https://dubstep2004.github.io/apex-client/img/bot/<имя>.jpg
Запуск из корня репозитория: python3 tools/tg-bot/pics.py   (шрифты и фон — те же, что у картинок канала, scripts/tg_card.py)"""
import io, os, sys
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../scripts'))
from tg_card import W, H, base, font, gradient, wrap  # noqa: E402

OUT = 'docs/img/bot/'
# имя: (заголовок, подзаголовок, цвет щита сверху, снизу, значок на щите)
PICS = {
    'hello': ('Поддержка', 'Выберите тему ниже или просто напишите вопрос — ответим', (63, 184, 255), (125, 92, 255), 'check'),
    'hello-en': ('Support', 'Pick a topic below or just type your question', (63, 184, 255), (125, 92, 255), 'check'),
    'owner': ('Режим владельца', 'Вопросы, люди и настройки бота — в одной панели', (255, 196, 70), (240, 110, 60), 'crown'),
    'install': ('Как установить', 'Скачать, поставить и включить — за минуту', (60, 200, 140), (45, 150, 230), 'down'),
    'connect': ('Не подключается', 'Пошаговая проверка — по порядку', (255, 120, 90), (200, 60, 140), 'plug'),
    'update': ('Как обновить', 'Новая версия приходит сама', (90, 210, 255), (90, 110, 255), 'up'),
    'slow': ('Медленно', 'Как выбрать быстрый сервер', (255, 200, 80), (230, 120, 40), 'bolt'),
    'whitelist': ('Белые списки', 'Когда мобильный интернет урезан', (150, 120, 255), (60, 190, 220), 'wave'),
    'support': ('Поддержка и продление', 'Написать человеку и продлить подписку', (255, 110, 160), (140, 80, 255), 'chat'),
}


def shield(im, top, bottom, icon, x0=880, y0=170):
    halo = Image.new('L', (W, H), 0); ImageDraw.Draw(halo).ellipse((x0 - 10, y0 + 10, x0 + 310, y0 + 350), fill=150)
    im = Image.composite(Image.new('RGB', (W, H), tuple(int(c * .6) for c in top)), im, halo.filter(ImageFilter.GaussianBlur(80)))
    k, sw, shh = 4, 300, 360

    def bez(p0, p1, p2, p3, n=24):
        return [tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t ** 2 * c + t ** 3 * e for a, b, c, e in zip(p0, p1, p2, p3)) for t in [i / n for i in range(n + 1)]]
    pts = [(150, 0), (300, 50), (300, 170)] + bez((300, 170), (300, 270), (230, 330), (150, 360)) + bez((150, 360), (70, 330), (0, 270), (0, 170)) + [(0, 50)]
    big = Image.new('L', (sw * k, shh * k), 0); ImageDraw.Draw(big).polygon([(x * k, y * k) for x, y in pts], fill=255)
    im.paste(gradient(sw, shh, top, bottom), (x0, y0), big.resize((sw, shh), Image.LANCZOS))
    # значок рисуем в 4 раза крупнее и уменьшаем — ровные края
    ic = Image.new('L', (sw * k, shh * k), 0); g = ImageDraw.Draw(ic); s = lambda *p: [(x * k, y * k) for x, y in p]
    w = 24 * k
    if icon == 'check': g.line(s((95, 175), (140, 220), (215, 130)), fill=255, width=w, joint='curve')
    elif icon == 'crown': g.polygon(s((80, 230), (70, 120), (115, 165), (150, 100), (185, 165), (230, 120), (220, 230)), fill=255)
    elif icon == 'down': g.line(s((150, 95), (150, 225)), fill=255, width=w); g.line(s((95, 175), (150, 230), (205, 175)), fill=255, width=w, joint='curve'); g.line(s((90, 265), (210, 265)), fill=255, width=w)
    elif icon == 'up': g.line(s((150, 245), (150, 115)), fill=255, width=w); g.line(s((95, 165), (150, 110), (205, 165)), fill=255, width=w, joint='curve')
    elif icon == 'bolt': g.polygon(s((170, 85), (95, 190), (145, 190), (125, 275), (205, 160), (155, 160), (180, 85)), fill=255)
    elif icon == 'plug':
        g.rounded_rectangle(s((95, 150), (205, 220))[0] + s((95, 150), (205, 220))[1], 18 * k, fill=255)
        g.line(s((125, 95), (125, 150)), fill=255, width=18 * k); g.line(s((175, 95), (175, 150)), fill=255, width=18 * k); g.line(s((150, 220), (150, 275)), fill=255, width=18 * k)
    elif icon == 'wave':
        for r in (40, 80, 120): g.arc((k * (150 - r), k * (230 - r), k * (150 + r), k * (230 + r)), 220, 320, fill=255, width=18 * k)
        g.ellipse(s((136, 216), (164, 244))[0] + s((136, 216), (164, 244))[1], fill=255)
    elif icon == 'chat':
        g.rounded_rectangle(s((80, 110), (220, 220))[0] + s((80, 110), (220, 220))[1], 30 * k, fill=255); g.polygon(s((110, 210), (100, 260), (150, 215)), fill=255)
    for x, y in ((95, 175), (215, 130)) if icon == 'check' else ():
        g.ellipse((k * (x - 12), k * (y - 12), k * (x + 12), k * (y + 12)), fill=255)
    im.paste(Image.new('RGB', (sw, shh), 'white'), (x0, y0), ic.resize((sw, shh), Image.LANCZOS))
    return im


def pic(title, sub, top, bottom, icon, tag):
    im, d = base(None, tag)
    for size in (72, 62, 52):
        ft = font('Unbounded.ttf', size, 800); tl = wrap(d, title, ft, 740, 2)
        if len(tl) == 1 or size == 52: break
    y = 250 if len(tl) == 1 else 210
    # заголовок — градиентом, как версия на картинке канала
    for l in tl:
        bb = d.textbbox((0, 0), l, font=ft); mk = Image.new('L', (bb[2] + 10, bb[3] + 20), 0); ImageDraw.Draw(mk).text((0, 0), l, font=ft, fill=255)
        im.paste(gradient(mk.width, mk.height, (243, 244, 255), tuple(min(255, c + 60) for c in top)), (78, y), mk); y += int(size * 1.25)
    d = ImageDraw.Draw(im); fs = font('Manrope.ttf', 32, 600); y += 20
    for l in wrap(d, sub, fs, 740, 2): d.text((80, y), l, font=fs, fill=(205, 209, 235)); y += 46
    d.rounded_rectangle((80, 600, 80 + 64, 606), 3, fill=top)
    d.text((80, 660), 'dubstep2004.github.io', font=font('Manrope.ttf', 22, 600), fill=(150, 155, 190), anchor='lm')
    return shield(im, top, bottom, icon)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, (t, s, a, b, ic) in PICS.items():
        tag = 'APEX CLIENT · support' if name.endswith('-en') else 'APEX CLIENT · поддержка'
        buf = io.BytesIO(); pic(t, s, a, b, ic, tag).save(buf, 'JPEG', quality=86, optimize=True, progressive=True)
        open(OUT + name + '.jpg', 'wb').write(buf.getvalue()); print(name, len(buf.getvalue()) // 1024, 'КБ')
