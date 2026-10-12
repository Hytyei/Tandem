"""Tiny painter for dreamy, cinematic flat-vector-with-grain scenes.

Everything is drawn on a float RGB canvas (W x W) at 2x the export size, with coordinates in
0..100 units so scenes are resolution independent. Style ingredients: gradient skies, layered
hills with atmospheric haze, screen-blended glows, rim-lit silhouettes, stipple grain.
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageChops

W = 960
RNG = np.random.default_rng(7)


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


def mix(a, b, t):
    return rgb(a) * (1 - t) + rgb(b) * t if isinstance(a, str) else a * (1 - t) + b * t


def S(v):
    return v * W / 100.0


class Mask:
    """A drawable L-mode mask using 0..100 coordinates."""

    def __init__(self):
        self.im = Image.new("L", (W, W), 0)
        self.d = ImageDraw.Draw(self.im)

    def poly(self, pts, v=255):
        self.d.polygon([(S(x), S(y)) for x, y in pts], fill=v); return self

    def ell(self, cx, cy, rx, ry=None, v=255):
        ry = rx if ry is None else ry
        self.d.ellipse([S(cx - rx), S(cy - ry), S(cx + rx), S(cy + ry)], fill=v); return self

    def rect(self, x, y, w, h, r=0, v=255):
        if r > 0:
            self.d.rounded_rectangle([S(x), S(y), S(x + w), S(y + h)], radius=S(r), fill=v)
        else:
            self.d.rectangle([S(x), S(y), S(x + w), S(y + h)], fill=v)
        return self

    def line(self, pts, w, v=255):
        p = [(S(x), S(y)) for x, y in pts]
        self.d.line(p, fill=v, width=max(1, int(S(w))), joint="curve")
        r = S(w) / 2
        for (x, y) in (p[0], p[-1]):
            self.d.ellipse([x - r, y - r, x + r, y + r], fill=v)
        return self

    def curve(self, pts, w, steps=24, v=255):
        return self.line(smooth(pts, steps), w, v)

    def blur(self, r):
        self.im = self.im.filter(ImageFilter.GaussianBlur(S(r))); self.d = ImageDraw.Draw(self.im); return self

    def arr(self):
        return np.asarray(self.im, dtype=np.float32) / 255.0


def ys_at(xx, xs, ys):
    """Ridge height for every pixel column (xx is in 0..100 units)."""
    return np.interp(xx, xs, ys).astype(np.float32)


def smooth(pts, steps=24):
    """Catmull-Rom spline through points."""
    if len(pts) < 3:
        return pts
    P = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for k in range(steps):
            t = k / steps
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2 +
                                    (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in (0, 1)))
    out.append(pts[-1])
    return out


def heart_pts(cx, cy, s):
    pts = []
    for i in range(80):
        t = i / 80 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * s, cy - y * s))
    return pts


class Canvas:
    def __init__(self, stops):
        self.img = np.zeros((W, W, 3), np.float32)
        ys = np.linspace(0, 1, W)
        cols = np.array([rgb(c) for _, c in stops])
        pos = [p for p, _ in stops]
        for ch in range(3):
            col = np.interp(ys, pos, cols[:, ch])
            self.img[:, :, ch] = col[:, None]
        self.yy, self.xx = np.mgrid[0:W, 0:W].astype(np.float32)
        self.yy /= W / 100.0; self.xx /= W / 100.0  # 0..100 units

    # ----------------------------------------------------------- basics
    def paint(self, mask, fill, a=1.0):
        m = (mask.arr() if isinstance(mask, Mask) else mask)[:, :, None] * a
        f = fill if getattr(fill, "ndim", 1) == 3 else (rgb(fill) if isinstance(fill, str) else fill)[None, None, :]
        self.img = self.img * (1 - m) + f * m

    def vfill(self, c_top, c_bot, y0, y1):
        t = np.clip((self.yy - y0) / max(y1 - y0, 1e-3), 0, 1)[:, :, None]
        return rgb(c_top)[None, None, :] * (1 - t) + rgb(c_bot)[None, None, :] * t

    def hfill(self, c_l, c_r, x0, x1):
        t = np.clip((self.xx - x0) / max(x1 - x0, 1e-3), 0, 1)[:, :, None]
        return rgb(c_l)[None, None, :] * (1 - t) + rgb(c_r)[None, None, :] * t

    def screen(self, light, color, a=1.0):
        l = (light if light.ndim == 2 else light[:, :, 0])[:, :, None] * a
        c = rgb(color)[None, None, :] * l
        self.img = 1 - (1 - self.img) * (1 - np.clip(c, 0, 1))

    def glow(self, x, y, r, color, a=1.0, power=2.0, sx=1.0, sy=1.0):
        d = np.sqrt(((self.xx - x) / sx) ** 2 + ((self.yy - y) / sy) ** 2) / r
        self.screen(np.clip(1 - d, 0, 1) ** power, color, a)

    def fog(self, y0, y1, color, a=0.4, peak=None):
        """Horizontal haze band that fades in and out vertically."""
        peak = (y0 + y1) / 2 if peak is None else peak
        t = np.where(self.yy < peak, (self.yy - y0) / max(peak - y0, 1e-3), (y1 - self.yy) / max(y1 - peak, 1e-3))
        t = np.clip(t, 0, 1)[:, :, None] * a
        self.img = self.img * (1 - t) + rgb(color)[None, None, :] * t

    def rim(self, mask, color, dx=0.0, dy=0.8, a=0.8, blur=0.25):
        """Light the edge of `mask` facing away from (dx, dy) — i.e. the lit side."""
        m = mask.im
        shifted = ImageChops.offset(m, int(S(dx)), int(S(dy)))
        edge = ImageChops.subtract(m, shifted).filter(ImageFilter.GaussianBlur(S(blur)))
        e = np.asarray(edge, dtype=np.float32) / 255.0
        self.screen(np.clip(e * 1.6, 0, 1), color, a)
        return e

    def stipple(self, zone, color, density=0.5, a=0.5):
        """Speckled grain concentrated where `zone` (0..1 array) is bright."""
        z = zone if zone.ndim == 2 else zone[:, :, 0]
        sp = (RNG.random((W, W)).astype(np.float32) < z * density).astype(np.float32)
        sp = np.asarray(Image.fromarray((sp * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7)), dtype=np.float32) / 255.0
        self.screen(np.clip(sp * 2.2, 0, 1), color, a)

    def grain(self, amount=0.028):
        n = RNG.normal(0, amount, (W, W, 1)).astype(np.float32)
        self.img = np.clip(self.img + n, 0, 1)

    def vignette(self, strength=0.35):
        d = np.sqrt((self.xx - 50) ** 2 + (self.yy - 50) ** 2) / 70
        v = 1 - np.clip(d, 0, 1) ** 2.2 * strength
        self.img *= v[:, :, None]

    # ----------------------------------------------------------- scenery
    def stars(self, n=90, ymax=60, a=0.9, color="#FFFFFF"):
        m = np.zeros((W, W), np.float32)
        for _ in range(n):
            x = RNG.uniform(2, 98); y = RNG.uniform(2, ymax); r = RNG.choice([0.18, 0.22, 0.3, 0.4], p=[.4, .3, .2, .1])
            mk = Mask().ell(x, y, r)
            m = np.maximum(m, mk.arr() * RNG.uniform(.35, 1.0))
        self.screen(m, color, a)
        for _ in range(4):
            self.sparkle(RNG.uniform(8, 92), RNG.uniform(5, ymax), RNG.uniform(1.4, 2.4), color, 0.9)

    def sparkle(self, x, y, s, color="#FFFFFF", a=1.0):
        mk = Mask().ell(x, y, s, s * 0.09).blur(0.12)
        mk2 = Mask().ell(x, y, s * 0.09, s).blur(0.12)
        self.screen(np.maximum(mk.arr(), mk2.arr()), color, a)
        self.glow(x, y, s * 1.6, color, a * 0.5, 2.5)

    def sun(self, x, y, r, c_in="#FFE3C7", c_out="#FF8F8A", halo="#FF9E8A", halo_r=None, a=1.0):
        self.glow(x, y, halo_r or r * 5, halo, 0.85 * a, 2.2)
        self.glow(x, y, (halo_r or r * 5) * 0.45, "#FFD7B8", 0.55 * a, 2.0)
        mk = Mask().ell(x, y, r)
        fill = self.vfill(c_in, c_out, y - r, y + r)
        self.paint(mk, fill, a)

    def moon(self, x, y, r, crescent=0.0, color="#FFF1D6", halo="#B6A6FF", a=1.0):
        self.glow(x, y, r * 5, halo, 0.55 * a, 2.0)
        mk = Mask().ell(x, y, r)
        if crescent > 0:
            cut = Mask().ell(x + r * crescent, y - r * 0.12, r * 0.92)
            mk.im = ImageChops.subtract(mk.im, cut.im)
        self.paint(mk, self.vfill("#FFFFFF", color, y - r, y + r), a)
        self.glow(x, y, r * 1.6, color, 0.35 * a, 2.0)

    def cloud(self, x, y, w, h, color="#FFFFFF", under="#9C86F5", a=0.8, blur=0.8):
        mk = Mask()
        for i in range(5):
            mk.ell(x + (i - 2) * w * 0.17, y + math.sin(i * 1.7) * h * 0.18, w * 0.2, h * (0.45 + 0.1 * (i % 2)))
        mk.ell(x, y + h * 0.12, w * 0.5, h * 0.3)
        mk.blur(blur)
        self.paint(mk, self.vfill(color, under, y - h, y + h * 0.7), a)

    def birds(self, spots, color="#2A2060", a=0.9, s=1.0):
        mk = Mask()
        for x, y, k in spots:
            z = s * k
            mk.curve([(x - 1.6 * z, y - 0.2 * z), (x - 0.7 * z, y - 1.1 * z), (x, y), (x + 0.7 * z, y - 1.1 * z), (x + 1.6 * z, y - 0.2 * z)], 0.28 * z)
        self.paint(mk, color, a)

    def bokeh(self, n, box, colors, rmin=1.2, rmax=4.0, a=0.5, blur=0.3):
        m = np.zeros((W, W, 3), np.float32)
        x0, y0, x1, y1 = box
        for _ in range(n):
            c = rgb(colors[RNG.integers(len(colors))])
            mk = Mask().ell(RNG.uniform(x0, x1), RNG.uniform(y0, y1), RNG.uniform(rmin, rmax)).blur(blur).arr()
            m = np.maximum(m, mk[:, :, None] * c[None, None, :] * RNG.uniform(.4, 1))
        self.img = 1 - (1 - self.img) * (1 - np.clip(m * a, 0, 1))

    def steam(self, x, y, h, color="#FFFFFF", a=0.45, w=1.4, sway=2.2, n=3):
        mk = Mask()
        for k in range(n):
            ox = (k - (n - 1) / 2) * 3.0
            pts = [(x + ox, y), (x + ox + sway, y - h * 0.33), (x + ox - sway, y - h * 0.66), (x + ox + sway * 0.6, y - h)]
            mk.curve(pts, w)
        mk.blur(0.9)
        self.paint(mk, color, a)

    def hills(self, layers, sun=None, rim_color="#FFB59A", far="#B9A6FF", near="#241A63", horizon=55, spread=40,
              amp=(7, 4), rim_a=0.4, haze="#C9B6FF", haze_a=0.2, seed=1, sun_x=50):
        """Layered silhouettes from far (hazy, light) to near (dark, saturated), lit from the sun side."""
        rs = np.random.default_rng(seed)
        out = []
        for i in range(layers):
            t = i / max(layers - 1, 1)
            base = horizon + t * spread
            a1, a2 = amp[0] * (0.7 + 0.6 * rs.random()), amp[1] * (0.7 + 0.6 * rs.random())
            ph = rs.uniform(0, 6.28, 3)
            f = rs.uniform(0.7, 1.5)
            xs = np.linspace(0, 100, 220)
            ys = base + a1 * np.sin(xs / 100 * 2 * math.pi * 0.9 * f + ph[0]) + a2 * np.sin(xs / 100 * 2 * math.pi * 2.1 * f + ph[1]) \
                + 0.6 * a2 * np.sin(xs / 100 * 2 * math.pi * 3.7 + ph[2])
            mk = Mask().poly([(x, y) for x, y in zip(xs, ys)] + [(100, 100), (0, 100)])
            col_top = mix(far, near, t ** 0.9)
            col_bot = mix(far, near, min(1, t ** 0.9 + 0.3))
            self.paint(mk, self.vfill_arr(col_top, col_bot, base - amp[0], 100))
            mka = mk.arr()
            # sunlit slopes: warm wash that fades with distance below the ridge and away from the sun
            lit = np.exp(-((self.xx - sun_x) / 30.0) ** 2) * np.clip(1 - (self.yy - ys_at(self.xx, xs, ys)) / (20 + 10 * (1 - t)), 0, 1)
            self.screen(mka * lit, rim_color, 0.3 * (1 - 0.5 * t))
            e = self.rim(mk, rim_color, 0, 0.3, rim_a * (1 - 0.5 * t), blur=0.1)
            self.stipple(e * 1.2 + mka * lit * 0.5, rim_color, 0.5, 0.5 * (1 - 0.4 * t))
            if i < layers - 1:
                self.fog(base - 6, base + 14, haze, haze_a * (1 - t), peak=base + 4)
            out.append((mk, base))
        return out

    def vfill_arr(self, c_top, c_bot, y0, y1):
        t = np.clip((self.yy - y0) / max(y1 - y0, 1e-3), 0, 1)[:, :, None]
        return c_top[None, None, :] * (1 - t) + c_bot[None, None, :] * t

    def silhouette(self, mask, color="#1B1450", rim_color="#FFB59A", rim_dir=(0.0, 0.9), rim_a=0.8, fill=None, blur=0.18):
        self.paint(mask, color if fill is None else fill)
        return self.rim(mask, rim_color, rim_dir[0], rim_dir[1], rim_a, blur)

    # ----------------------------------------------------------- finish
    def finish(self, grain=0.026, vig=0.3):
        self.vignette(vig)
        self.grain(grain)
        out = (np.clip(self.img, 0, 1) * 255).astype(np.uint8)
        return Image.fromarray(out)
