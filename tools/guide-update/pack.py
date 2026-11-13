"""Кадры PNG → живой WebP (README) и неподвижный последний кадр 900×534 (справка в приложении)."""
import glob, sys
from PIL import Image

src, anim, still = sys.argv[1:4]
frames = [Image.open(f).convert('RGB') for f in sorted(glob.glob(src + '/*.png'))]
durs = [90] * (len(frames) - 1) + [2600]  # последний кадр держим подольше
frames[0].save(anim, save_all=True, append_images=frames[1:], duration=durs, loop=0, quality=80, method=6)
frames[-1].resize((900, 534), Image.LANCZOS).save(still, quality=82, method=6)
print(anim, still)
