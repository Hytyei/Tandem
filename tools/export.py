"""Exports artwork into the Android project: full WebP + 4:3 widget WebP (w_*) + grain tile."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from PIL import Image
import scenes

res = sys.argv[1]
d = os.path.join(res, "drawable-nodpi"); os.makedirs(d, exist_ok=True)
PROVIDED = os.path.join(os.path.dirname(__file__), "provided")


def center_square(im):
    s = min(im.size)
    return im.crop(((im.width - s) // 2, (im.height - s) // 2, (im.width + s) // 2, (im.height + s) // 2))


jobs = list(scenes.SCENES.items()) + [("act_" + k, None) for k in scenes.ACTIVITY_KEYS]
for n, fn in jobs:
    prov = os.path.join(PROVIDED, n + ".jpg")
    if os.path.exists(prov):
        # Artwork supplied by the app owner: ship it as-is (4:3), tiles centre-crop it.
        src = Image.open(prov).convert("RGB")
        src.resize((800, round(800 * src.height / src.width)), Image.LANCZOS).save(os.path.join(d, n + ".webp"), quality=86, method=6)
        im = src
    else:
        if fn is None: raise SystemExit("missing supplied artwork: " + prov)
        im = fn()
        im.resize((480, 480), Image.LANCZOS).save(os.path.join(d, n + ".webp"), quality=86, method=6)
    # widget variant: 4:3, 480x360, unrounded (Glance clips corners with cornerRadius)
    wim = im if abs(im.width * 3 - im.height * 4) <= 8 else im.crop((0, im.height // 8, im.width, im.height - im.height // 8))
    wim.resize((480, 360), Image.LANCZOS).save(os.path.join(d, "w_" + n + ".webp"), quality=84, method=6)
    old = os.path.join(res, "drawable", n + ".xml")
    if os.path.exists(old): os.remove(old)
    print("exported", n)
# film grain tile (white speckle with alpha) used as an overlay in the app
rng = np.random.default_rng(3)
g = rng.random((256, 256))
a = (np.clip((g - 0.55) * 2.2, 0, 1) * 255).astype(np.uint8)
t = np.dstack([np.full((256, 256), 255, np.uint8)] * 3 + [a])
Image.fromarray(t, "RGBA").save(os.path.join(d, "grain.png"), optimize=True)
