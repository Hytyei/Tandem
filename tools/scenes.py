"""The 23 Tandem scenes. Each function returns a PIL image (W x W)."""
import math
import numpy as np
from engine import *

# ------------------------------------------------------------------ palettes
DUSK = [(0, "#2B2790"), (0.42, "#8467F0"), (0.74, "#F096B4"), (1, "#FFC9A2")]
NIGHT = [(0, "#0B0930"), (0.5, "#2A2A82"), (1, "#7456D0")]
DAWN = [(0, "#6A5FE0"), (0.45, "#E88CD0"), (0.8, "#FFC09A"), (1, "#FFE3A8")]
WARM = [(0, "#241657"), (0.5, "#9C458F"), (1, "#FF9A78")]
RAIN = [(0, "#17234F"), (0.55, "#3C58A5"), (1, "#8DA3E6")]
STORM = [(0, "#120C2E"), (0.5, "#3A2868"), (1, "#8A4C94")]
EMBER = [(0, "#17081F"), (0.5, "#6A1C4A"), (1, "#FF6A3F")]
LAKE = [(0, "#4A5BC9"), (0.5, "#9C8BEA"), (1, "#F7C1C8")]

PEOPLE = "#1A1250"


def person(m, x, base, h, dress=False, reach=None, side=1):
    m.ell(x, base - h * 0.88, h * 0.105)
    if dress:
        m.poly([(x - h * .16, base - h * .79), (x + h * .16, base - h * .79), (x + h * .11, base - h * .5), (x - h * .11, base - h * .5)])
        m.poly([(x - h * .11, base - h * .52), (x + h * .11, base - h * .52), (x + h * .21, base - h * .1), (x - h * .21, base - h * .1)])
        m.line([(x - h * .05, base - h * .12), (x - h * .05, base)], h * .05)
        m.line([(x + h * .05, base - h * .12), (x + h * .05, base)], h * .05)
        m.poly([(x - h * .1, base - h * .93), (x + h * .1, base - h * .93), (x + h * .15 * (-side), base - h * .55), (x - h * .12 * (-side), base - h * .6)])
    else:
        m.poly([(x - h * .16, base - h * .79), (x + h * .16, base - h * .79), (x + h * .12, base - h * .46), (x - h * .12, base - h * .46)])
        m.line([(x - h * .045, base - h * .46), (x - h * .055, base)], h * .12)
        m.line([(x + h * .045, base - h * .46), (x + h * .055, base)], h * .12)
    # arms
    m.line([(x - h * .12 * side, base - h * .76), (x - h * .13 * side, base - h * .5)], h * .08)
    if reach:
        m.line([(x + h * .12 * side, base - h * .76), reach], h * .08)
    else:
        m.line([(x + h * .12 * side, base - h * .76), (x + h * .13 * side, base - h * .5)], h * .08)


# =========================================================================== moods
def mood_happy():
    c = Canvas(DAWN)
    c.sun(50, 53, 9.5, "#FFF2D0", "#FFC27A", "#FFC98A", 46)
    for k in range(14):  # soft god-rays
        a = k / 14 * math.pi * 2
        m = Mask().poly([(50, 53), (50 + 90 * math.cos(a - .05), 53 + 90 * math.sin(a - .05)), (50 + 90 * math.cos(a + .05), 53 + 90 * math.sin(a + .05))]).blur(1.5)
        c.screen(m.arr() * 0.5, "#FFE4B0", 0.25)
    c.cloud(24, 24, 24, 3.2, "#FFF4EA", "#F5A7C8", .75)
    c.cloud(78, 34, 20, 2.6, "#FFF4EA", "#F5A7C8", .65)
    c.hills(4, rim_color="#FFE2A8", far="#F2B1C8", near="#4A2F86", horizon=54, spread=34, amp=(5, 3), haze="#FFD6B0", seed=4)
    c.birds([(30, 30, 1.0), (36, 26, .8), (26, 24, .7), (70, 22, .9), (76, 19, .7)], "#5B3A8A", .85, 1.2)
    c.sparkle(18, 45, 2.0, "#FFF0C8"); c.sparkle(84, 50, 1.6, "#FFF0C8")
    return c.finish()


def mood_loved():
    c = Canvas([(0, "#3A2597"), (0.45, "#B65AC4"), (0.78, "#FF8FA3"), (1, "#FFC4A4")])
    c.sun(50, 62, 8, "#FFE6D6", "#FF8FA8", "#FF8FA3", 40, .9)
    c.hills(3, rim_color="#FFB0C4", far="#E58AB8", near="#34185E", horizon=62, spread=26, amp=(4, 2.5), haze="#FFB0C8", seed=2)
    c.stars(40, 30, .7)
    for (x, y, s, a) in [(30, 30, .95, 1), (55, 20, .75, .9), (68, 38, 1.15, 1), (44, 46, .6, .8), (82, 22, .55, .7), (20, 52, .5, .7)]:
        c.glow(x, y, 15 * s, "#FF7A9C", .55 * a, 2.0)
        hm = Mask().poly(heart_pts(x, y, s * .62))
        c.paint(hm, c.vfill("#FFD0D8", "#FF4F82", y - 8 * s, y + 8 * s), a)
        c.glow(x, y, 7 * s, "#FFE0E6", .5 * a, 2.0)
    return c.finish()


def mood_calm():
    c = Canvas(LAKE)
    c.moon(70, 22, 5.5, 0.0, "#FFF6E0", "#C8C0FF", .95)
    c.stars(45, 30, .6)
    hs = c.hills(3, rim_color="#FFD9D0", far="#B7AAF5", near="#3B3A9A", horizon=52, spread=12, amp=(5, 3), haze="#F0C8E0", seed=11)
    # water: mirror the upper half, darken & ripple
    h = W // 2 + int(S(2))
    top = c.img[: int(S(58)), :, :].copy()
    y0 = int(S(58))
    mir = top[::-1][: W - y0]
    c.img[y0: y0 + mir.shape[0]] = mir * 0.78 + rgb("#4B4FB5")[None, None, :] * 0.22
    for i in range(9):
        y = 62 + i * 4.2
        rp = Mask().line([(8 + (i * 13) % 30, y), (30 + (i * 17) % 40, y)], .22 + i * .02).blur(.15)
        c.screen(rp.arr(), "#FFE8E0", .35 - i * .02)
    c.glow(70, 60, 22, "#FFF0D8", .35, 2.2, 0.35, 1.2)
    c.fog(52, 66, "#F3C9E4", .35, 58)
    return c.finish(vig=.35)


def mood_excited():
    c = Canvas([(0, "#07061F"), (0.55, "#241B6E"), (1, "#7D3FA8")])
    c.stars(60, 50, .7)
    cols = [("#FF4FA6", "#FFD27A"), ("#6AA9FF", "#C7B6FF"), ("#FFB35A", "#FF6A8A")]
    for i, (x, y, r) in enumerate([(30, 30, 20), (68, 24, 24), (52, 46, 15)]):
        c1, c2 = cols[i]
        c.glow(x, y, r * 1.8, c1, .5, 2.0)
        sp = Mask()
        for k in range(26):
            a = k / 26 * math.tau
            sp.line([(x + r * .35 * math.cos(a), y + r * .35 * math.sin(a)), (x + r * math.cos(a), y + r * math.sin(a))], .35)
            for j in (0.62, 0.8):
                sp.ell(x + r * (j + .28) * math.cos(a + .1) * .9, y + r * (j + .28) * math.sin(a + .1) * .9, .45)
        sp.blur(.12)
        c.screen(sp.arr(), c2, .95); c.screen(sp.arr(), c1, .6)
        c.sparkle(x, y, 3, "#FFFFFF", .9)
    c.hills(3, rim_color="#FF9AC8", far="#4E3A9E", near="#0E0A2E", horizon=72, spread=18, amp=(4, 2), haze="#7D4FC0", haze_a=.3, seed=5)
    crowd = Mask()
    for i in range(14):
        x = 5 + i * 7 + (i % 3)
        crowd.ell(x, 93 + (i % 2) * 1.2, 2.2); crowd.rect(x - 2.6, 94 + (i % 2) * 1.2, 5.2, 8)
    c.silhouette(crowd, "#0A0722", "#FF9AC8", (0, .8), .45)
    return c.finish()


def mood_tired():
    c = Canvas([(0, "#2A2F7E"), (0.5, "#6D74C4"), (1, "#C9BDE8")])
    c.moon(62, 30, 12, 0.0, "#F8F1FF", "#C9C6FF", .9)
    c.cloud(30, 22, 30, 3, "#E8E4FF", "#8E92D8", .5, 1.0)
    c.hills(5, rim_color="#E6DFFF", far="#C7C5F2", near="#3A3F96", horizon=56, spread=32, amp=(4, 2.5), haze="#DCD6FA", haze_a=.55, seed=8)
    c.fog(62, 90, "#D8D2F8", .35, 74)
    c.stars(25, 30, .4)
    return c.finish(grain=.03)


def mood_sad():
    c = Canvas(RAIN)
    c.glow(50, 75, 70, "#9CB4F0", .35, 2.0)
    c.hills(4, rim_color="#A9C0F5", far="#7F96DA", near="#16255E", horizon=58, spread=34, amp=(4, 2.5), haze="#9DB4EE", haze_a=.5, seed=6)
    tree = Mask().rect(33.4, 68, 1.2, 14).ell(34, 64, 6, 7).ell(30, 67, 4, 4).ell(38, 67, 4.5, 4)
    c.silhouette(tree, "#101A4C", "#7F98E0", (0, .6), .3)
    rain = Mask()
    r = np.random.default_rng(3)
    for _ in range(150):
        x = r.uniform(-5, 105); y = r.uniform(-5, 100); l = r.uniform(4, 9)
        rain.line([(x, y), (x - l * .22, y + l)], .18, int(r.uniform(90, 255)))
    rain.blur(.1)
    c.screen(rain.arr(), "#C6D4FF", .5)
    drops = Mask()
    for _ in range(14):
        x = r.uniform(5, 95); y = r.uniform(5, 60); rr = r.uniform(.5, 1.3)
        drops.ell(x, y, rr, rr * 1.3)
    drops.blur(.2)
    c.screen(drops.arr(), "#E6EEFF", .35)
    return c.finish(grain=.032, vig=.4)


def mood_anxious():
    c = Canvas(STORM)
    c.glow(50, 44, 46, "#C06AC0", .38, 2.0)
    allm = Mask()
    for i in range(26):
        t = i / 25
        ang = t * math.tau * 1.7 + .5
        rad = 5 + t * 40
        x = 50 + rad * math.cos(ang); y = 44 + rad * math.sin(ang) * .82
        tx, ty = -math.sin(ang), math.cos(ang)
        L = 7 + t * 12
        blob = Mask().line([(x - tx * L, y - ty * L * .82), (x + tx * L, y + ty * L * .82)], 7 + t * 8).blur(.8)
        col = mix("#5A3C94", "#1B1042", t ** .8)
        c.paint(blob, col, .88)
        allm.im = ImageChops.lighter(allm.im, blob.im)
    c.rim(allm, "#E08AD0", -.4, -.5, .55, .5)
    c.rim(allm, "#7A8AF0", .5, .4, .35, .5)
    c.glow(50, 44, 9, "#FFE0F4", .6, 2.0)
    c.sparkle(50, 44, 3.2, "#FFFFFF", .9)
    c.hills(2, rim_color="#C07AD0", far="#2E2060", near="#090620", horizon=84, spread=9, amp=(3, 2), haze="#4A2C80", haze_a=.2, seed=3)
    pm = Mask(); person(pm, 22, 90.5, 9, False)
    c.silhouette(pm, "#07041A", "#C07AD0", (-.6, .4), .7)
    return c.finish(grain=.035)


def mood_angry():
    c = Canvas(EMBER)
    c.sun(50, 52, 10, "#FFD9A0", "#FF5A3C", "#FF5A3C", 48, .95)
    c.hills(3, rim_color="#FF8A4A", far="#6A1E4E", near="#12060F", horizon=58, spread=30, amp=(5, 3), haze="#FF5A3C", haze_a=.25, seed=14)
    v = Mask().poly([(14, 100), (34, 66), (44, 56), (50, 54), (56, 56), (66, 66), (88, 100)])
    c.silhouette(v, "#14070F", "#FF7A3A", (0, .9), .6)
    lava = Mask()
    for pts in ([(50, 56), (48, 66), (52, 76), (47, 90)], [(46, 58), (40, 70), (36, 84)], [(54, 58), (60, 70), (66, 86)]):
        lava.curve(pts, .9)
    lava.blur(.2)
    c.screen(lava.arr(), "#FF9A4A", .95); c.glow(50, 56, 14, "#FF6A3A", .6, 2.0)
    r = np.random.default_rng(2)
    sp = Mask()
    for _ in range(60):
        x = 50 + r.normal(0, 14); y = r.uniform(5, 54)
        sp.ell(x, y, r.uniform(.15, .45))
    sp.blur(.12)
    c.screen(sp.arr(), "#FFB36A", .9)
    return c.finish()


# ====================================================================== activities
def act_gaming():
    c = Canvas([(0, "#0E0A33"), (0.6, "#2A1A6E"), (1, "#14103F")])
    c.glow(50, 36, 62, "#6C7BFF", .55, 2.0, 1.1, 0.9)
    scr = Mask().rect(20, 14, 60, 36, 2.2)
    c.paint(scr, c.vfill("#3E5BFF", "#C14FE0", 14, 50))
    inner = c.img.copy()
    # tiny game scene in the screen
    sm = Mask().rect(22, 16, 56, 32, 1.2)
    c.paint(sm, c.vfill("#271E86", "#FF8FB0", 16, 48))
    c.sun(60, 38, 4, "#FFF0D8", "#FFB08A", "#FF9AB0", 12, .9)
    mt = Mask().poly([(22, 48), (36, 32), (48, 44), (60, 34), (78, 48)])
    c.paint(mt.rect(22, 44, 56, 4), "#1A1060")
    c.stars(10, 24, .0)
    c.screen(Mask().rect(22, 16, 56, 32, 1.2).arr(), "#FFFFFF", .05)
    c.glow(50, 32, 30, "#FFFFFF", .12, 2.0)
    stand = Mask().rect(46, 50, 8, 8).rect(36, 57, 28, 2.2, 1)
    c.silhouette(stand, "#0C0830", "#7B8CFF", (0, .8), .5)
    floor = Mask().poly([(0, 78), (100, 78), (100, 100), (0, 100)])
    c.paint(floor, c.vfill("#1B1456", "#0A0724", 78, 100), .9)
    c.glow(50, 82, 40, "#6C7BFF", .35, 2.0, 1.4, .35)
    pad = Mask().rect(24, 71, 52, 17, 8.5).ell(30, 82, 9.5, 11).ell(70, 82, 9.5, 11)
    c.silhouette(pad, fill=c.vfill("#251B73", "#0E0A33", 70, 94), rim_color="#9CB0FF", rim_dir=(0, 1.1), rim_a=.9)
    c.glow(50, 70, 36, "#9CB0FF", .12, 2.0, 1, .25)
    dp = Mask().rect(30, 77, 8, 2.4, 1).rect(32.8, 74.2, 2.4, 8, 1)
    c.paint(dp, "#070520", .9)
    for (x, y, col) in [(68, 74.5, "#FF5F8F"), (73, 79, "#5AE6A8"), (68, 83.5, "#6AA9FF"), (63, 79, "#FFD27A")]:
        c.glow(x, y, 4.5, col, .7, 2.0)
        c.paint(Mask().ell(x, y, 1.5), col, .95)
    c.paint(Mask().ell(44, 83, 2.6).ell(56, 83, 2.6), "#0A0724", .9)
    c.bokeh(10, (4, 4, 96, 20), ["#8AA0FF", "#C58AFF"], 1.0, 2.6, .35)
    return c.finish()


def act_working():
    c = Canvas([(0, "#231B7A"), (0.45, "#7E5FD8"), (0.8, "#F092B0"), (1, "#FFC4A0")])
    c.sun(70, 50, 5, "#FFE8D0", "#FF9AA8", "#FF9AA8", 28, .8)
    sky = Mask()
    r = np.random.default_rng(5)
    x = 0
    far = Mask(); near = Mask()
    while x < 100:
        w = r.uniform(5, 11); h = r.uniform(14, 34)
        far.rect(x, 62 - h, w, h + 10); x += w + r.uniform(0, 1.5)
    x = -3
    while x < 100:
        w = r.uniform(6, 12); h = r.uniform(10, 24)
        near.rect(x, 66 - h, w, h + 14); x += w + r.uniform(0, 1.2)
    c.silhouette(far, c.vfill("#6A4DB8", "#3A2A86", 28, 68), "#FFB59A", (0, .9), .5)
    c.fog(40, 70, "#C9A0E8", .25, 60)
    c.silhouette(near, c.vfill("#3A2A86", "#1E1658", 40, 70), "#FF9AAA", (-.5, .7), .5)
    win = Mask()
    for _ in range(80):
        win.rect(r.uniform(0, 98), r.uniform(42, 64), .9, 1.1)
    c.screen(win.arr() * (c.yy > 40) * 0 + win.arr(), "#FFD58A", .9)
    desk = Mask().rect(0, 74, 100, 26)
    c.paint(desk, c.vfill("#24195E", "#0F0A33", 74, 100))
    c.rim(desk, "#FFB59A", 0, .8, .35)
    c.glow(24, 66, 34, "#FFB070", .5, 2.0)  # lamp
    lamp = Mask().line([(14, 74), (18, 56), (27, 50)], 1.1).poly([(23, 48), (33, 48), (35, 55), (21, 55)]).rect(10, 73, 10, 1.8, .8)
    c.silhouette(lamp, "#120C3A", "#FFC890", (.6, .6), .6)
    c.screen(Mask().poly([(23, 55), (35, 55), (46, 78), (14, 78)]).blur(1.4).arr() * .5, "#FFC080", .35)
    scr = Mask().rect(36, 42, 36, 25, 1.6)
    c.paint(scr, c.vfill("#3B4FD8", "#8A6CF0", 42, 67))
    c.glow(54, 56, 38, "#8AA0FF", .35, 2.0, 1.2, .9)
    for i, (w, off, col) in enumerate([(14, 0, "#FFD58A"), (22, 3, "#FFFFFF"), (10, 3, "#7FF0C8"), (18, 6, "#FFFFFF"), (12, 6, "#FF9AC0"), (20, 3, "#FFFFFF")]):
        c.paint(Mask().rect(39 + off, 46 + i * 3.3, w, 1.1, .5), col, .8)
    base = Mask().poly([(32, 70), (76, 70), (80, 75), (28, 75)])
    c.silhouette(base, "#1A1250", "#9CB0FF", (0, 1), .8)
    mug = Mask().rect(80, 63, 9, 11, 2).ell(90.4, 68, 2.4, 3)
    c.silhouette(mug, "#1A1250", "#FFC090", (-.6, .6), .6)
    c.paint(Mask().ell(90.4, 68, 1.2, 2), "#24195E")
    c.steam(84.5, 62, 12, "#FFE8D8", .45, 1.0, 1.6, 2)
    return c.finish()


def act_sleeping():
    c = Canvas(NIGHT)
    c.glow(35, 30, 60, "#8A7AF0", .35, 2.0)
    c.stars(110, 62, .95)
    c.moon(36, 30, 15, .55, "#FFF2D6", "#9C8CFF", 1)
    c.cloud(78, 24, 26, 2.8, "#B9B0F5", "#4F4AB0", .55, .9)
    c.cloud(24, 58, 32, 3, "#B9B0F5", "#4F4AB0", .45, .9)
    c.hills(3, rim_color="#B9A8FF", far="#4B4AAE", near="#0F0B38", horizon=64, spread=20, amp=(3.5, 2), haze="#7F6AD8", haze_a=.3, seed=21, sun_x=36)
    house = Mask().poly([(60, 84), (60, 74), (68, 67), (76, 74), (76, 84)]).rect(77, 71, 7, 13).poly([(76, 71), (80.5, 66), (85, 71)])
    c.silhouette(house, "#0B0830", "#A99AFF", (-.7, .5), .6)
    for (x, y, w, h) in [(65, 77, 3, 3.2), (79.5, 75, 2.2, 2.6)]:
        c.glow(x + w / 2, y + h / 2, 6, "#FFC070", .6, 2.0)
        c.paint(Mask().rect(x, y, w, h, .4), "#FFD58A")
    c.paint(Mask().rect(0, 84, 100, 16), "#07051F")
    for k, (x, y, s) in enumerate([(56, 40, 3.2), (63, 34, 2.4), (68, 29, 1.8)]):
        z = Mask().line([(x - s, y - s), (x + s, y - s), (x - s, y + s), (x + s, y + s)], .55 * s / 2)
        c.glow(x, y, s * 3, "#CFC4FF", .25, 2.0)
        c.paint(z, "#E8E2FF", .9)
    return c.finish()


def act_cooking():
    c = Canvas([(0, "#1D1250"), (0.55, "#7A3485"), (1, "#FF8E6E")])
    c.bokeh(26, (4, 6, 96, 58), ["#FFD27A", "#FFB070", "#FF8FA8", "#C9A0FF"], 2.4, 6.4, .55, .6)
    c.glow(50, 82, 54, "#FF8A4A", .6, 2.0, 1.3, .8)
    c.paint(Mask().rect(0, 80, 100, 20), c.vfill("#2C1654", "#120A30", 80, 100))
    stove = Mask().rect(0, 80, 100, 3)
    c.rim(stove, "#FFB79A", 0, .8, .6)
    pot = Mask().rect(26, 56, 48, 24, 4).rect(21, 59, 6, 3, 1.4).rect(73, 59, 6, 3, 1.4)
    lid = Mask().poly([(25, 56), (75, 56), (68, 47), (32, 47)]).ell(50, 45, 2.3)
    c.silhouette(pot, fill=c.hfill("#3A1F6E", "#1A0F46", 26, 74), rim_color="#FFB070", rim_dir=(0, 0), rim_a=0)
    c.rim(pot, "#FFB07A", .8, .3, .75)
    c.silhouette(lid, fill=c.vfill("#4A2B80", "#241458", 45, 56), rim_color="#FFD0A0", rim_dir=(-.6, .8), rim_a=.7)
    c.glow(50, 78, 28, "#FFB04A", .55, 2.0, 1.2, .35)
    fl = Mask()
    for x in (34, 42, 50, 58, 66):
        fl.poly([(x - 2, 82), (x, 77.5 + (x % 3)), (x + 2, 82)])
    fl.blur(.3)
    c.screen(fl.arr(), "#FFB84A", .95)
    c.steam(44, 44, 26, "#FFE6DA", .5, 1.5, 3, 3)
    c.steam(58, 44, 22, "#FFE6DA", .4, 1.3, -2.6, 2)
    return c.finish()


def act_eating():
    c = Canvas([(0, "#150C3F"), (0.55, "#5A2C8F"), (1, "#C25A7E")])
    c.bokeh(34, (2, 4, 98, 62), ["#FFD27A", "#FF9A6A", "#FF7AA8", "#8AB4FF"], 2, 6, .55, .6)
    c.glow(50, 70, 50, "#FFAA5A", .5, 2.0, 1.1, .6)
    table = Mask().rect(0, 74, 100, 26)
    c.paint(table, c.vfill("#3A1E5E", "#150C30", 74, 100)); c.rim(table, "#FFB59A", 0, .8, .4)
    bowl = Mask().poly([(18, 62), (82, 62), (76, 78), (68, 86), (32, 86), (24, 78)]).ell(50, 86, 18, 2.6)
    c.silhouette(bowl, fill=c.vfill("#6A2F86", "#1E1050", 62, 88), rim_color="#FFC8A0", rim_dir=(-.5, .6), rim_a=.7)
    c.paint(Mask().rect(20, 71, 60, 1.2).rect(26, 78, 48, 1), "#FF9AAA", .55)
    top = Mask().ell(50, 62, 32, 6)
    c.paint(top, c.vfill("#FFD9A0", "#FF9A5A", 56, 68))
    for i in range(7):
        c.paint(Mask().curve([(24 + i * 8, 62.5), (28 + i * 8, 60.6), (32 + i * 8, 62.5)], .6), "#FFF1C8", .8)
    c.paint(Mask().ell(40, 61, 4.2, 2.2).ell(60, 62, 3.6, 1.8), "#FFF7E6", .95)
    c.paint(Mask().ell(40, 61, 1.6, 1).ell(60, 62, 1.4, .9), "#FFB45A")
    c.paint(Mask().ell(50, 64, 4, 1.6), "#6ADC9A", .8)
    ch = Mask().line([(64, 36), (86, 66)], 1.3).line([(70, 33), (92, 63)], 1.3)
    c.silhouette(ch, "#2A1456", "#FFD0A0", (-.5, .6), .7)
    c.steam(40, 56, 24, "#FFF0E4", .5, 1.6, 3, 3)
    c.steam(58, 56, 20, "#FFF0E4", .4, 1.4, -2.6, 2)
    return c.finish()


def act_sport():
    c = Canvas([(0, "#2B2790"), (0.4, "#9A6AE8"), (0.75, "#FF9A9A"), (1, "#FFD0A0")])
    c.sun(38, 58, 10, "#FFF0D0", "#FF9A7A", "#FF9A8A", 48, .95)
    c.hills(2, rim_color="#FFC0A0", far="#C07AD8", near="#4A2B90", horizon=66, spread=10, amp=(3, 2), haze="#FFB0B8", seed=7, sun_x=38)
    ground = Mask().rect(0, 82, 100, 18)
    c.silhouette(ground, c.vfill("#2A1B6A", "#120C38", 82, 100), "#FFB59A", (0, .8), .5)
    c.paint(Mask().poly([(40, 82), (56, 82), (84, 100), (12, 100)]), "#FFB59A", .08)
    pole = Mask().rect(78, 22, 2.2, 61).rect(70, 22, 8.4, 1.6, .5).poly([(58, 28), (74, 26), (74, 40), (58, 44)])
    c.silhouette(pole, "#150E46", "#FFB59A", (-.6, .5), .8)
    c.paint(Mask().poly([(60, 31), (72, 30), (72, 38), (60, 41)]), "#FFE4D0", .0)
    rim = Mask().ell(56, 45, 7.5, 2.2)
    c.paint(rim, "#FF7A5A")
    c.glow(56, 45, 9, "#FF9A7A", .4, 2.0)
    net = Mask()
    for k in range(6):
        net.line([(49 + k * 2.8, 46), (51.4 + k * 1.8, 58)], .22)
    for yy in (49, 52, 55):
        net.line([(50 + (yy - 46) * .2, yy), (63 - (yy - 46) * .2, yy)], .2)
    c.paint(net, "#FFE0D0", .65)
    for t, a in zip(np.linspace(0, 1, 9), np.linspace(.15, .6, 9)):
        x = 24 + t * 30; y = 40 + (t - .5) ** 2 * 60 - 12
        c.paint(Mask().ell(x, y, .6), "#FFE4D0", a)
    ball = Mask().ell(54, 40, 4.6)
    c.paint(ball, c.hfill("#FFB070", "#C0502C", 49, 59))
    for pts in ([(54, 35.4), (54, 44.6)], [(49.4, 40), (58.6, 40)]):
        c.paint(Mask().line(pts, .35), "#5A2010", .8)
    c.paint(Mask().curve([(51, 36.5), (49.8, 40), (51, 43.5)], .3), "#5A2010", .7)
    c.paint(Mask().curve([(57, 36.5), (58.2, 40), (57, 43.5)], .3), "#5A2010", .7)
    c.glow(54, 40, 7, "#FFD0A0", .35, 2.0)
    c.birds([(20, 24, .9), (27, 21, .7)], "#4A2B8A", .8, 1.1)
    return c.finish()


def act_reading():
    c = Canvas([(0, "#0C0A30"), (0.6, "#2B1E78"), (1, "#4A2C8F")])
    c.glow(50, 56, 56, "#FFB87A", .5, 2.0, 1.1, .8)
    r = np.random.default_rng(12)
    dots = Mask()
    for _ in range(46):
        dots.ell(r.uniform(18, 82), r.uniform(8, 66), r.uniform(.2, .55))
    dots.blur(.1)
    c.screen(dots.arr(), "#FFE0A8", .85)
    for _ in range(4):
        c.sparkle(r.uniform(22, 78), r.uniform(12, 50), r.uniform(1.4, 2.4), "#FFE9C0", .9)
    c.screen(Mask().poly([(34, 58), (66, 58), (84, 4), (16, 4)]).blur(2.2).arr() * .5, "#FFD79A", .22)
    tbl = Mask().rect(0, 80, 100, 20)
    c.paint(tbl, c.vfill("#2B1A60", "#0C0828", 80, 100)); c.rim(tbl, "#FFC090", 0, .8, .5)
    cover = Mask().poly([(10, 70), (50, 80), (90, 70), (90, 76), (50, 86), (10, 76)])
    c.silhouette(cover, "#241454", "#FFC090", (0, 1), .5)
    lp = Mask().poly([(12, 56), (50, 66), (50, 80), (12, 70)])
    rp = Mask().poly([(88, 56), (50, 66), (50, 80), (88, 70)])
    c.paint(lp, c.hfill("#FFE3B8", "#FFF8EA", 12, 50)); c.paint(rp, c.hfill("#FFF8EA", "#FFD9A0", 50, 88))
    c.glow(50, 62, 26, "#FFF0D0", .5, 2.0, 1.4, .5)
    for i in range(4):
        y = 59.5 + i * 2.4
        c.paint(Mask().line([(18, y + i * .1), (46, y + 5.2)], .35), "#C0905A", .45)
        c.paint(Mask().line([(54, y + 5.2), (82, y + i * .1)], .35), "#C0905A", .45)
    c.paint(Mask().line([(50, 66), (50, 80)], .5), "#C0905A", .6)
    c.paint(Mask().poly([(70, 58), (74, 59.3), (74, 70), (72, 68.5), (70, 69.6)]), "#FF5F8F", .95)
    return c.finish()


def act_music():
    c = Canvas([(0, "#1A0F55"), (0.55, "#7E3FB8"), (1, "#FF7AA8")])
    c.bokeh(30, (2, 4, 98, 62), ["#FF9AC8", "#8AB4FF", "#FFD27A", "#C58AFF"], 2.2, 6.4, .55, .6)
    c.glow(50, 52, 46, "#FF9AD0", .5, 2.0)
    for i, r in enumerate([26, 34, 42]):
        w = Mask()
        w.curve([(50 + r * math.cos(a), 52 + r * math.sin(a)) for a in np.linspace(-.8, .8, 12)], .6)
        w.curve([(50 - r * math.cos(a), 52 + r * math.sin(a)) for a in np.linspace(-.8, .8, 12)], .6)
        w.blur(.15)
        c.screen(w.arr(), "#FFD0F0", .7 - i * .17)
    band = Mask().curve([(24, 62), (22, 44), (34, 26), (50, 22), (66, 26), (78, 44), (76, 62)], 3.6, 18)
    c.silhouette(band, "#1A0F4A", "#FFB0D8", (0, .9), .8)
    for cx in (24, 76):
        cup = Mask().rect(cx - 8, 52, 16, 28, 8)
        c.silhouette(cup, fill=c.vfill("#2D1A78", "#14093A", 52, 80), rim_color="#FFB0D8", rim_dir=(0, .9), rim_a=.8)
        c.paint(Mask().rect(cx - 4.5, 57, 9, 18, 4.5), "#0C0628", .8)
        c.glow(cx, 66, 9, "#FF6AA8", .35, 2.0)
    for (x, y, s) in [(40, 14, 1), (62, 10, .8), (50, 80, .8)]:
        n = Mask().ell(x, y, 1.9 * s, 1.4 * s).rect(x + 1.2 * s, y - 8 * s, .55 * s, 8 * s)
        n.curve([(x + 1.7 * s, y - 8 * s), (x + 4.5 * s, y - 6 * s), (x + 4 * s, y - 3.5 * s)], .6 * s)
        c.glow(x, y - 3, 7 * s, "#FFB0E0", .5, 2.0)
        c.paint(n, "#FFE6F6", .95)
    return c.finish()


def act_movie():
    c = Canvas([(0, "#0A0724"), (0.5, "#2A1A6E"), (1, "#0E0A33")])
    c.glow(50, 36, 60, "#8A8CFF", .5, 2.0, 1.2, .9)
    c.screen(Mask().poly([(50, 94), (50, 94), (6, 4), (94, 4)]).blur(.1).arr() * .0, "#FFFFFF", 0)
    scr = Mask().rect(10, 14, 80, 40, 1)
    c.paint(scr, c.vfill("#3E6BFF", "#E58AD0", 14, 54))
    sm = Mask().rect(10, 14, 80, 40, 1)
    c.sun(66, 38, 6, "#FFF2D8", "#FFB08A", "#FFB0B8", 18, .9)
    c.paint(Mask().poly([(10, 54), (28, 36), (44, 46), (60, 34), (90, 54)]), "#1B1470")
    c.screen(sm.arr(), "#FFFFFF", .04)
    c.glow(50, 34, 48, "#FFFFFF", .12, 2.0, 1.4, .8)
    beam = Mask().poly([(50, 96), (45, 96), (8, 54), (92, 54), (55, 96)]).blur(1.0)
    c.screen(beam.arr() * .5, "#A5B4FF", .22)
    r = np.random.default_rng(1)
    dust = Mask()
    for _ in range(40):
        dust.ell(r.uniform(20, 80), r.uniform(56, 90), r.uniform(.15, .4))
    dust.blur(.1)
    c.screen(dust.arr(), "#E0E4FF", .6)
    rows = Mask()
    for row, (y, sc) in enumerate([(80, 1.0), (92, 1.25)]):
        for i in range(8):
            x = 6 + i * 12.5 + (row * 6)
            rows.ell(x, y, 3.1 * sc, 3.5 * sc); rows.ell(x, y + 9 * sc, 6.2 * sc, 7 * sc)
    c.silhouette(rows, "#07051A", "#8CA0FF", (0, .9), .7)
    return c.finish()


def act_traveling():
    c = Canvas([(0, "#262A94"), (0.4, "#8E6CF0"), (0.72, "#FF9AB0"), (1, "#FFD0A0")])
    c.sun(70, 60, 8, "#FFF0D8", "#FFA88A", "#FFA08A", 44, .95)
    for k, (y, col, un, a) in enumerate([(66, "#FFD7DC", "#E58AC8", .8), (74, "#E8C8F8", "#9A74E0", .85), (84, "#B49AF0", "#4F3AAE", .9), (94, "#7A62D8", "#2A1E78", .95)]):
        for i in range(6):
            c.cloud(8 + i * 18 + (k % 2) * 6, y + math.sin(i + k) * 2, 26, 6 + k, col, un, a, 1.0)
    pl = Mask()
    pl.poly([(18, 44), (60, 38), (66, 40), (60, 43.5), (24, 47)])
    pl.poly([(34, 42), (46, 24), (50, 24.5), (46, 41)])
    pl.poly([(34, 45), (47, 56), (51, 55), (46, 42.5)])
    pl.poly([(20, 44), (13, 33), (17, 33), (25, 42.5)])
    pl.ell(63, 41, 4.4, 1.5)
    c.silhouette(pl, "#1A1052", "#FFC9A0", (-.2, .8), .9)
    for pts in ([(18, 46), (-2, 52)], [(24, 47), (-2, 57)]):
        t = Mask().line(pts, .5).blur(.5)
        c.screen(t.arr(), "#FFE4E0", .5)
    c.sparkle(88, 20, 2, "#FFE9D0"); c.sparkle(20, 18, 1.5, "#FFE9D0", .8)
    return c.finish()


def act_relaxing():
    c = Canvas([(0, "#1E1660"), (0.5, "#6A4AB8"), (1, "#D77AA8")])
    c.bokeh(14, (0, 0, 100, 100), ["#8A6AD8"], 6, 12, .12, 1)
    win = Mask().rect(20, 14, 60, 62, 0).ell(50, 30, 30, 22)
    win.rect(20, 30, 60, 46)
    sky = c.vfill("#5A58D8", "#FFB0A0", 14, 76)
    c.paint(win, sky)
    c.sun(56, 56, 6, "#FFF0D8", "#FFA88A", "#FF9AAA", 30, .8)
    c.cloud(40, 30, 18, 2.2, "#FFE4EE", "#B890F0", .7)
    h = Mask().poly([(20, 66), (34, 56), (50, 64), (66, 54), (80, 62), (80, 76), (20, 76)])
    c.paint(h, "#3B2A96", .9)
    c.glow(50, 55, 40, "#FFB09A", .3, 2.0)
    frame = Mask().rect(17, 11, 66, 68, 0).ell(50, 30, 33, 25).rect(17, 30, 66, 49)
    frame.im = ImageChops.subtract(frame.im, win.im)
    c.silhouette(frame, "#140C3E", "#B890F0", (0, .8), .4)
    mull = Mask().rect(49.4, 8, 1.2, 68).rect(20, 44, 60, 1.2)
    c.paint(mull, "#140C3E", .9)
    c.paint(Mask().rect(0, 76, 100, 24), c.vfill("#2A1A66", "#0F0A30", 76, 100))
    sofa = Mask().rect(26, 66, 52, 14, 6).rect(18, 70, 12, 14, 5).rect(74, 70, 12, 14, 5).rect(30, 56, 20, 14, 6).rect(54, 56, 20, 14, 6)
    c.silhouette(sofa, fill=c.vfill("#4A2E9A", "#1C1160", 56, 86), rim_color="#FFB59A", rim_dir=(-.3, .8), rim_a=.7)
    plant = Mask()
    for ang in (-60, -30, 0, 28, 56):
        a = math.radians(ang - 90)
        plant.poly([(8, 76), (8 + 9 * math.cos(a) - 2, 76 + 14 * math.sin(a)), (8 + 14 * math.cos(a), 76 + 20 * math.sin(a) - 4), (8 + 9 * math.cos(a) + 2, 76 + 14 * math.sin(a))])
    plant.rect(4.5, 74, 7, 10, 1.6)
    c.silhouette(plant, "#0D0838", "#FFB59A", (-.6, .6), .6)
    c.glow(90, 62, 22, "#FFB070", .5, 2.0)
    c.paint(Mask().ell(90, 64, 3.5, 4.5), "#FFE0A8"); c.glow(90, 64, 7, "#FFD08A", .6, 2.0)
    return c.finish()


def act_driving():
    c = Canvas([(0, "#2A2690"), (0.4, "#8A64E8"), (0.7, "#FF98AA"), (1, "#FFD2A0")])
    c.sun(50, 52, 9, "#FFF0D0", "#FF9A7A", "#FF9A8A", 46, .95)
    c.hills(3, rim_color="#FFC0A0", far="#C07AD8", near="#2E1B78", horizon=50, spread=14, amp=(5, 3), haze="#FFB0B8", seed=17, sun_x=50)
    road = Mask().poly([(48, 55), (52, 55), (100, 100), (0, 100)])
    c.paint(road, c.vfill("#4A3490", "#120C38", 55, 100))
    c.glow(50, 56, 28, "#FFB09A", .35, 2.0, 1, .35)
    for t in np.linspace(0.05, 0.95, 7):
        y = 56 + t ** 1.6 * 44
        w = .25 + t ** 1.6 * 1.6
        h = .8 + t ** 1.6 * 8
        c.paint(Mask().poly([(50 - w / 2, y), (50 + w / 2, y), (50 + w / 2 + .1, y + h), (50 - w / 2 - .1, y + h)]), "#FFD9B8", .7 * (0.4 + t))
    car = Mask().poly([(34, 90), (34, 80), (38, 72), (46, 66), (54, 66), (62, 72), (66, 80), (66, 90)])
    car.rect(30, 80, 40, 11, 3)
    c.silhouette(car, "#0E0830", "#FFB59A", (0, 1), .8)
    c.paint(Mask().poly([(41, 72), (47, 68), (53, 68), (59, 72)]), "#241454", .95)
    for x in (36, 64):
        c.glow(x, 85, 6, "#FF4F6A", .8, 2.0); c.paint(Mask().rect(x - 2.6, 84, 5.2, 2, 1), "#FF6A82")
    c.paint(Mask().rect(30, 90, 8, 3, 1).rect(62, 90, 8, 3, 1), "#07051A")
    c.screen(Mask().poly([(34, 92), (66, 92), (80, 100), (20, 100)]).blur(.8).arr() * .5, "#FF4F6A", .3)
    c.birds([(24, 22, 1), (30, 18, .7)], "#4A2B8A", .8, 1.1)
    return c.finish()


def act_shopping():
    c = Canvas([(0, "#1D1459"), (0.5, "#7A3A98"), (1, "#FF8E86")])
    c.bokeh(36, (2, 2, 98, 66), ["#FFD27A", "#FF9AC8", "#8AB4FF", "#FFB070"], 2, 6.5, .6, .6)
    shop = Mask().rect(6, 40, 50, 46)
    c.silhouette(shop, c.vfill("#3A1E78", "#1A0F4A", 40, 86), "#FFB0C8", (0, .9), .5)
    c.glow(32, 62, 26, "#FFD090", .5, 2.0)
    c.paint(Mask().rect(12, 48, 38, 28, 1.2), c.vfill("#FFD9A0", "#FF9A8A", 48, 76), .95)
    for yy in (56, 64, 72):
        c.paint(Mask().rect(13, yy, 36, .9), "#7A3A6A", .7)
    for i, (xx, hh, col) in enumerate([(16, 5, "#FF6A92"), (21, 4, "#8A6CF0"), (26, 6, "#FFFFFF"), (31, 4.5, "#FF9A6A"), (37, 5.5, "#7FD0F0"), (43, 4, "#FF6A92")]):
        c.paint(Mask().rect(xx, 56 - hh, 3.2, hh, .4), col, .85)
        c.paint(Mask().rect(xx + (i % 2) * 2, 64 - hh * .9, 3, hh * .9, .4), col, .75)
    aw = Mask()
    for i in range(0, 7, 2):
        aw.poly([(6 + i * 7.15, 36), (13.15 + i * 7.15, 36), (13.15 + i * 7.15, 44), (6 + i * 7.15, 44)])
    c.paint(aw, "#FF7A96"); c.paint(Mask().rect(6, 36, 50, 1.2), "#FFD0D8", .6)
    c.paint(Mask().rect(0, 84, 100, 16), c.vfill("#3A2070", "#150C38", 84, 100))
    for (x, w, h, col, col2) in [(54, 22, 28, "#FF6A92", "#B83A78"), (76, 18, 24, "#FFC27A", "#E0705A")]:
        b = Mask().poly([(x, 100 - h - 3), (x + w, 100 - h - 3), (x + w + 1.5, 100), (x - 1.5, 100)])
        c.silhouette(b, fill=c.vfill(col, col2, 100 - h, 100), rim_color="#FFE0D0", rim_dir=(-.4, .8), rim_a=.6)
        hd = Mask().curve([(x + w * .28, 100 - h - 2), (x + w * .32, 100 - h - 12), (x + w * .68, 100 - h - 12), (x + w * .72, 100 - h - 2)], 1.2)
        c.silhouette(hd, "#2A1460", "#FFE0D0", (0, .8), .5)
        c.paint(Mask().poly(heart_pts(x + w * .5, 100 - h * .5, .38)), "#FFF0F0", .85)
    return c.finish()


def act_outdoors():
    c = Canvas([(0, "#080626"), (0.55, "#262A86"), (1, "#7A58D0")])
    band = Mask().curve([(-5, 52), (30, 30), (60, 18), (105, 4)], 22, 30).blur(5)
    c.screen(band.arr(), "#B8A8FF", .35)
    c.stars(140, 60, .95)
    c.hills(3, rim_color="#B7A8FF", far="#6A56C8", near="#0E0A34", horizon=58, spread=24, amp=(6, 3.5), haze="#8A76E0", haze_a=.35, seed=31)
    c.paint(Mask().rect(0, 84, 100, 16), "#07051F")
    c.glow(62, 84, 36, "#FF8A4A", .6, 2.0, 1.2, .6)
    tent = Mask().poly([(14, 86), (36, 56), (58, 86)])
    c.silhouette(tent, c.hfill("#3A2A9A", "#1E1470", 14, 58), "#FFB080", (-.7, .3), .6)
    c.paint(Mask().poly([(36, 56), (28.5, 86), (43.5, 86)]), c.vfill("#FFC27A", "#FF7A4A", 56, 86), .95)
    c.glow(36, 76, 14, "#FFB04A", .55, 2.0)
    c.paint(Mask().rect(0, 84, 100, 16), "#07051F", .0)
    fire = Mask().poly([(60, 86), (62, 79), (64, 83), (66, 76), (68, 83), (70, 79), (72, 86)]).blur(.25)
    c.paint(fire, c.vfill("#FFE08A", "#FF6A3A", 76, 86))
    logs = Mask().line([(58, 87), (74, 84)], 1.6).line([(58, 84), (74, 87)], 1.6)
    c.paint(logs, "#1A0F3A")
    r = np.random.default_rng(4)
    sp = Mask()
    for _ in range(14):
        sp.ell(66 + r.normal(0, 3), r.uniform(56, 80), r.uniform(.15, .35))
    c.screen(sp.blur(.1).arr(), "#FFB36A", .9)
    pines = Mask()
    for x, h in [(82, 28), (91, 34), (97, 24), (75, 20)]:
        for j in range(4):
            apex = 92 - h + j * h * .2
            hw = h * .13 * (1 + j * .5)
            pines.poly([(x - hw, apex + h * .27), (x, apex), (x + hw, apex + h * .27)])
        pines.rect(x - .7, 90 - h * .1, 1.4, 4)
    c.silhouette(pines, "#07051F", "#FFB080", (-.7, .4), .5)
    return c.finish()


def act_together():
    c = Canvas(DUSK)
    c.stars(30, 30, .5)
    c.sun(63, 50, 9.5, "#FFF0D8", "#FF9A92", "#FF9AA0", 50, .95)
    c.cloud(28, 28, 26, 2.8, "#FFE0EE", "#B88AF0", .7)
    c.cloud(80, 22, 20, 2.4, "#FFE0EE", "#B88AF0", .6)
    c.hills(3, rim_color="#FFC0A8", far="#C58AE0", near="#3A2490", horizon=56, spread=16, amp=(5, 3), haze="#FFB0C0", seed=9, sun_x=63)
    ridge = smooth([(-5, 76), (18, 70), (38, 66), (60, 71), (82, 67), (105, 73)], 20)
    near = Mask().poly(ridge + [(105, 100), (-5, 100)])
    c.silhouette(near, c.vfill("#2C2278", "#120C3E", 64, 100), "#FFB59A", (0, .9), .55)
    pm = Mask()
    person(pm, 40.5, 67.3, 19, False, reach=(46.4, 58.6), side=1)
    person(pm, 52.6, 68.2, 17.4, True, reach=(46.4, 58.6), side=-1)
    c.silhouette(pm, "#140C42", "#FFC5A0", (-.8, .3), .5)
    c.birds([(26, 18, .9), (33, 14, .7), (74, 14, .8)], "#4A2B8A", .85, 1.2)
    c.glow(46.6, 59.6, 5, "#FFD8B8", .25, 2.0)
    for (x, y, s) in [(78, 40, .55), (84, 33, .35)]:
        c.glow(x, y, 10 * s * 2, "#FF7A9C", .5, 2.0)
        c.paint(Mask().poly(heart_pts(x, y, s * .5)), c.vfill("#FFD0D8", "#FF5F8F", y - 6 * s, y + 6 * s), .95)
    return c.finish()


SCENES = {
    "mood_happy": mood_happy, "mood_loved": mood_loved, "mood_calm": mood_calm, "mood_excited": mood_excited,
    "mood_tired": mood_tired, "mood_sad": mood_sad, "mood_anxious": mood_anxious, "mood_angry": mood_angry,
    "hero_together": act_together,
}
# Activity artworks are supplied (tools/provided/act_<key>.jpg); they are not painted by this script.
ACTIVITY_KEYS = ["gaming", "sleeping", "eating", "tv", "music", "football", "cycling", "hiking",
                 "camping", "roadtrip", "stargazing", "photography", "hair", "nails"]
