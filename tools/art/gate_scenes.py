"""The nine pictures of the gatekeeper screen (one per dungeon and the final raid), painted with gate_paint.

Each scene is 472x200 (the screen shows it at 236x100 GUI units, so two texels per unit). Original art: the scenes
follow the themes of docs/modules/dungeons.md (section 4) and borrow nothing from Minecraft's own textures.
"""
from __future__ import annotations

import math

import numpy as np

from gate_paint import Canvas, rgb, mix, smoothstep, jitter_line

W, H = 472, 200


# ---------------------------------------------------------------------------------------------- shared helpers

def reflect(c, y_water, strength=0.45, wave=1.5, blur=1.2, tint=None):
    """Mirrors what is above the waterline into the water below it, rippled and darkened."""
    src = c.blurred(blur)
    out = c.img.copy()
    rows = H - y_water
    for i in range(rows):
        y = y_water + i
        sy = y_water - 1 - int(i * 1.05)
        if sy < 0:
            break
        shift = int(round(math.sin(i * 0.9) * wave * (0.4 + i / rows)))
        row = np.roll(src[sy], shift, axis=0)
        k = strength * (1.0 - i / rows * 0.6)
        if tint is not None:
            row = row * tint
        out[y] = out[y] * (1.0 - k) + row * k
    c.img = out


def stars(c, count, y_max, color=rgb("#ffffff"), k=0.8, seed_shift=0):
    rng = c.rng
    pts = []
    for _ in range(count):
        x = rng.uniform(0, W)
        y = rng.uniform(0, y_max) ** 1.0
        s = rng.uniform(0.15, 1.0) ** 2.2
        pts.append((x, y, s))
    c.dots(pts, color, k)
    for x, y, s in pts:
        if s > 0.6:
            c.glow(x, y, 3.5, color, 0.25 * s)


def motes(c, count, box, color, k=0.7, glow_r=3.0, glow_k=0.25, size=(0.3, 1.0)):
    rng = c.rng
    x0, y0, x1, y1 = box
    pts = [(rng.uniform(x0, x1), rng.uniform(y0, y1), rng.uniform(*size)) for _ in range(count)]
    c.dots(pts, color, k, glow_r, glow_k)


def flame(c, x, y, size, core=rgb("#fff3b0"), mid=rgb("#ffa030"), outer=rgb("#ff4a10"), glow=0.6):
    """A small fire whose base is at (x, y)."""
    h = size * 2.4
    body = c.poly([(x - size * 0.55, y), (x - size * 0.65, y - h * 0.35), (x - size * 0.2, y - h * 0.7),
                   (x + size * 0.05, y - h), (x + size * 0.3, y - h * 0.62), (x + size * 0.62, y - h * 0.32),
                   (x + size * 0.55, y)])
    c.put(body, outer, 0.95)
    inner = c.poly([(x - size * 0.32, y), (x - size * 0.35, y - h * 0.35), (x, y - h * 0.72),
                    (x + size * 0.32, y - h * 0.35), (x + size * 0.32, y)])
    c.put(inner, mid, 0.95)
    c.put(c.ellipse(x, y - h * 0.22, size * 0.22, size * 0.42), core, 0.95)
    c.glow(x, y - h * 0.4, size * 7, outer, glow * 0.6, power=2.2)
    c.glow(x, y - h * 0.4, size * 2.6, mid, glow * 0.7, power=1.8)


def catenary(x0, y0, x1, y1, sag, n=40):
    pts = []
    for i in range(n + 1):
        t = i / n
        pts.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t + sag * 4 * t * (1 - t)))
    return pts


def lit_window(c, x, y, w, h, color, k=0.9, glow_r=7.0, glow_k=0.25, frame=None):
    c.put(c.rect(x, y, x + w, y + h), color, k)
    if frame is not None and w >= 3 and h >= 4:
        c.put(c.rect(x + w / 2 - 0.4, y, x + w / 2 + 0.4, y + h), frame, 0.8)
        c.put(c.rect(x, y + h / 2 - 0.4, x + w, y + h / 2 + 0.4), frame, 0.8)
    if glow_k > 0:
        c.glow(x + w / 2, y + h / 2, glow_r, color, glow_k)


def pine(c, x, base, height, width, color, snow=None):
    tiers = 4
    for t in range(tiers):
        top = base - height + t * height * 0.2
        bottom = base - height * 0.25 + t * height * 0.08 - (tiers - 1 - t) * height * 0.12
        half = width * (0.38 + 0.2 * t)
        pts = [(x, top), (x + half, bottom), (x - half, bottom)]
        c.put(c.poly(pts), color)
        if snow is not None:
            c.put(c.poly([(x, top + 1), (x + half * 0.7, bottom - (bottom - top) * 0.22),
                          (x + half * 0.2, bottom - (bottom - top) * 0.3), (x, top + (bottom - top) * 0.35),
                          (x - half * 0.3, bottom - (bottom - top) * 0.3), (x - half * 0.75, bottom - (bottom - top)
                                                                            * 0.2)]), snow, 0.55)
    c.put(c.rect(x - width * 0.06, base - height * 0.28, x + width * 0.06, base), color)


def crystal(c, x, base, height, width, lean, light, dark, glow_color=None, glow_k=0.3):
    tip = (x + lean, base - height)
    left = [(x - width / 2, base), (x - width / 2 + lean * 0.75, base - height * 0.78), tip,
            (x + lean * 0.9 + width * 0.05, base - height * 0.8), (x + width * 0.05, base)]
    right = [(x + width * 0.05, base), (x + lean * 0.9 + width * 0.05, base - height * 0.8), tip,
             (x + width / 2 + lean * 0.75, base - height * 0.78), (x + width / 2, base)]
    c.put(c.poly(left), dark)
    c.put(c.poly(right), light)
    c.put(c.line([(x + width * 0.05, base), (x + lean * 0.9 + width * 0.05, base - height * 0.8), tip], 0.7),
          mix(light, np.ones(3, np.float32), 0.6), 0.8)
    if glow_color is not None:
        c.glow(x + lean * 0.5, base - height * 0.5, height * 0.9, glow_color, glow_k)


def column(c, x, top, bottom, w, light, dark, broken=False):
    """A round column lit from the right (cylinder shading)."""
    xs = np.clip((c.X - (x - w / 2)) / w, 0, 1)
    shade = np.clip(0.25 + 0.85 * np.sin(xs * math.pi * 0.95 + 0.15), 0, 1)[..., None]
    col = dark[None, None, :] + (light - dark)[None, None, :] * shade
    if broken:
        rng = c.rng
        pts = [(x - w / 2, bottom), (x - w / 2, top + rng.uniform(2, 6))]
        steps = 5
        for i in range(1, steps):
            pts.append((x - w / 2 + w * i / steps, top + rng.uniform(0, 7)))
        pts += [(x + w / 2, top + rng.uniform(2, 6)), (x + w / 2, bottom)]
        mask = c.poly(pts)
    else:
        mask = c.rect(x - w / 2, top, x + w / 2, bottom)
    c.put(mask, col)
    # fluting
    for i in range(1, 4):
        fx = x - w / 2 + w * i / 4
        c.put(c.rect(fx - 0.35, top + 3, fx + 0.35, bottom - 2) * mask, dark, 0.35)
    return mask


def tapered(c, pts, w0, w1):
    """A stroke along the polyline pts whose width goes from w0 to w1 (a horn, a root, a tail)."""
    n = len(pts)
    left = []
    right = []
    for i, (x, y) in enumerate(pts):
        if i == 0:
            dx, dy = pts[1][0] - x, pts[1][1] - y
        elif i == n - 1:
            dx, dy = x - pts[i - 1][0], y - pts[i - 1][1]
        else:
            dx, dy = pts[i + 1][0] - pts[i - 1][0], pts[i + 1][1] - pts[i - 1][1]
        ln = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / ln, dx / ln
        w = (w0 + (w1 - w0) * i / (n - 1)) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    return c.poly(left + right[::-1])


def bezier(p0, p1, p2, p3, n=24):
    out = []
    for i in range(n + 1):
        t = i / n
        a = (1 - t) ** 3
        b = 3 * (1 - t) ** 2 * t
        d = 3 * (1 - t) * t * t
        e = t ** 3
        out.append((a * p0[0] + b * p1[0] + d * p2[0] + e * p3[0], a * p0[1] + b * p1[1] + d * p2[1] + e * p3[1]))
    return out


# ---------------------------------------------------------------------------------------------- the scenes

def scene_d1(c):
    """Lost Underground City: a buried old town in a cavern, paper lanterns and a canal."""
    rng = c.rng
    c.img[:] = c.vgrad([(0.0, rgb("#0a0705")), (0.5, rgb("#1e130c")), (1.0, rgb("#0f0a08"))])
    rock = c.fbm(36, 5)
    c.img *= (0.7 + 0.6 * rock)[..., None]
    c.glow(236, 118, 250, rgb("#8a4a20"), 0.55, power=1.5, ry=105)
    c.glow(236, 122, 110, rgb("#ffb060"), 0.18, power=1.8, ry=50)
    # far town
    far = []
    x = 30.0
    while x < 445:
        w = rng.uniform(12, 24)
        h = rng.uniform(14, 30)
        base = 132 + rng.uniform(-2, 2)
        far.append([(x, base), (x, base - h), (x + w / 2, base - h - rng.uniform(5, 9)), (x + w, base - h),
                    (x + w, base)])
        x += w + rng.uniform(-3, 2)
    c.put(c.polys(far), rgb("#3c2416"), 0.95)
    for pts in far:
        x0, x1 = pts[0][0], pts[-1][0]
        top = pts[1][1]
        for _ in range(int(rng.integers(0, 3))):
            wx = rng.uniform(x0 + 2, x1 - 4)
            wy = rng.uniform(top + 3, pts[0][1] - 6)
            lit_window(c, wx, wy, 1.6, 2.2, rgb("#ffb860"), 0.85, 5, 0.18)
    # stalactites and the cave roof
    spikes = []
    for _ in range(34):
        x = rng.uniform(-10, W + 10)
        w = rng.uniform(6, 20)
        ln = rng.uniform(10, 38) * (1.2 if abs(x - 236) > 140 else 0.8)
        spikes.append([(x - w / 2, -1), (x + w / 2, -1), (x + rng.uniform(-3, 3), ln)])
    c.put(c.polys(spikes), rgb("#080504"))
    # cool light from a crack in the roof
    shaft = c.blur_mask(c.poly([(78, 0), (98, 0), (210, 200), (150, 200)]), 4.0)
    c.add(shaft * smoothstep(200, 0, c.Y) * smoothstep(0, 30, c.Y), rgb("#6a8ca8"), 0.1)
    motes(c, 40, (90, 10, 200, 190), rgb("#cfe6ff"), 0.5, 2.0, 0.08, (0.2, 0.8))
    # the near town: timbered houses on both banks
    for side in (-1, 1):
        x = 0 if side < 0 else 330
        end = 150 if side < 0 else 480
        while x < end:
            w = rng.uniform(26, 40)
            h = rng.uniform(40, 66)
            base = 160
            roof = rng.uniform(10, 16)
            body = c.poly([(x, base), (x, base - h), (x + w / 2, base - h - roof), (x + w, base - h), (x + w, base)])
            wall = c.vgrad([(0, rgb("#3a2618")), (1, rgb("#24170f"))], base - h, base)
            c.put(body, wall)
            c.put(c.poly([(x - 2, base - h + 1), (x + w / 2, base - h - roof - 1), (x + w + 2, base - h + 1),
                          (x + w + 2, base - h + 3), (x + w / 2, base - h - roof + 2), (x - 2, base - h + 3)]),
                  rgb("#140c08"))
            beams = [[(x + w * f, base - h + 2), (x + w * f, base)] for f in (0.08, 0.5, 0.92)]
            beams.append([(x, base - h * 0.55), (x + w, base - h * 0.55)])
            beams.append([(x + w * 0.08, base - h * 0.55), (x + w * 0.5, base - h + 2)])
            c.put(c.lines(beams, 1.2), rgb("#120a06"), 0.9)
            for row in (0.3, 0.72):
                for f in (0.22, 0.66):
                    if rng.random() < 0.75:
                        lit_window(c, x + w * f, base - h * row - 3, 4.5, 6, rgb("#ffaa48"), 0.95, 12, 0.32,
                                   frame=rgb("#5a2a10"))
            x += w + rng.uniform(1, 5)
    # the bridge: a stone arch over the canal
    stone = c.vgrad([(0, rgb("#4a3426")), (1, rgb("#22170f"))], 128, 170)
    body = c.rect(132, 128, 340, 170)
    hole = c.ellipse(236, 176, 74, 30)
    c.put(np.clip(body - hole, 0, 1), stone)
    voussoirs = []
    for a in np.linspace(math.pi * 1.04, math.pi * 1.96, 15):
        voussoirs.append([(236 + math.cos(a) * 74, 176 + math.sin(a) * 30),
                          (236 + math.cos(a) * 82, 176 + math.sin(a) * 37)])
    c.put(c.lines(voussoirs, 0.7), rgb("#1a120c"), 0.8)
    c.put(np.clip(c.ellipse(236, 176, 82, 37) - c.ellipse(236, 176, 74, 30), 0, 1) * (c.Y < 170), rgb("#6a4a34"),
          0.45)
    for (x0, x1) in ((132, 146), (326, 340)):
        c.put(c.rect(x0, 126, x1, 170), rgb("#3a2a1e"))
        c.put(c.rect(x0, 126, x1, 127.2), rgb("#b07a44"), 0.8)
    c.put(c.rect(132, 128, 340, 130), rgb("#b07a44"), 0.85)
    posts = [[(px, 128), (px, 122)] for px in np.linspace(150, 322, 12)]
    c.put(c.lines(posts, 1.4), rgb("#2a1c14"))
    c.put(c.rect(146, 121, 326, 122.6), rgb("#3a2a1c"))
    for lx in (139, 333):
        c.put(c.rect(lx - 0.8, 104, lx + 0.8, 126), rgb("#1a120c"))
        c.put(c.ellipse(lx, 102, 3.2, 4.0), rgb("#ffd890"))
        c.glow(lx, 102, 40, rgb("#ff9a40"), 0.4)
    # water
    water = c.rect(0, 160, W, H)
    c.put(water, c.vgrad([(0, rgb("#140e0c")), (1, rgb("#0a0708"))], 160, 200))
    reflect(c, 160, 0.55, 1.6, 1.0)
    c.put(c.rect(0, 159, W, 161), rgb("#2a1a10"), 0.6)
    # strings of paper lanterns
    for (x0, y0, x1, y1, sag, n) in ((-10, 24, 482, 36, 26, 9), (-10, 58, 482, 52, 16, 8)):
        rope = catenary(x0, y0, x1, y1, sag)
        c.put(c.line(rope, 0.7), rgb("#120a06"))
        for i in range(n):
            t = (i + 0.5) / n
            px = x0 + (x1 - x0) * t
            py = y0 + (y1 - y0) * t + sag * 4 * t * (1 - t)
            c.put(c.rect(px - 0.4, py, px + 0.4, py + 3), rgb("#120a06"))
            body = c.ellipse(px, py + 7.5, 3.6, 4.8)
            c.put(body, c.vgrad([(0, rgb("#ffd27a")), (1, rgb("#ff7a26"))], py + 3, py + 12))
            c.put(c.rect(px - 2.4, py + 2.6, px + 2.4, py + 3.6), rgb("#2a1206"))
            c.put(c.rect(px - 2.0, py + 11.6, px + 2.0, py + 12.6), rgb("#2a1206"))
            c.glow(px, py + 7.5, 22, rgb("#ff8a30"), 0.32)
    # foreground crates and barrels
    fg = []
    fg.append([(0, 200), (0, 172), (22, 170), (24, 200)])
    fg.append([(20, 200), (22, 182), (44, 182), (46, 200)])
    fg.append([(440, 200), (442, 176), (464, 176), (466, 200)])
    fg.append([(462, 200), (462, 168), (476, 168), (476, 200)])
    c.put(c.polys(fg), rgb("#070504"))
    c.put(c.rect(0, 169.5, 22, 171), rgb("#7a4a24"), 0.5)
    c.put(c.rect(442, 175.5, 464, 177), rgb("#7a4a24"), 0.5)
    motes(c, 70, (0, 20, W, 190), rgb("#ffcf8a"), 0.45, 2.5, 0.06, (0.2, 0.7))
    c.bloom(0.65, 5.0, 0.5)


def scene_d2(c):
    """Labyrinth of the Ancient Tree: root arches leading into a glowing heart."""
    rng = c.rng
    c.img[:] = c.vgrad([(0.0, rgb("#040b08")), (0.55, rgb("#0b1f17")), (1.0, rgb("#06100b"))])
    c.glow(236, 104, 200, rgb("#1f8a72"), 0.85, power=1.7, ry=120)
    c.glow(236, 102, 48, rgb("#bfffe6"), 0.75, power=1.6)
    # god rays from the heart
    for a in np.linspace(-2.7, -0.45, 9):
        a += rng.uniform(-0.1, 0.1)
        spread = rng.uniform(0.015, 0.05)
        ray = c.poly([(236, 102), (236 + math.cos(a - spread) * 300, 102 + math.sin(a - spread) * 300),
                      (236 + math.cos(a + spread) * 300, 102 + math.sin(a + spread) * 300)])
        c.add(c.blur_mask(ray, 2.0) * c.radial(236, 102, 230, power=1.6), rgb("#8affd8"), rng.uniform(0.03, 0.07))
    # mossy path into the heart
    path = c.poly([(176, 200), (296, 200), (246, 118), (226, 118)])
    c.put(path, c.vgrad([(0, rgb("#3a6a3c")), (1, rgb("#1a2e1c"))], 200, 118), 0.55)
    # root rings, far to near
    rings = [0.30, 0.44, 0.62, 0.86, 1.18]
    haze = rgb("#1d5a4a")
    for idx, s in enumerate(rings):
        cx = 236 + rng.uniform(-4, 4)
        base = 112 + 96 * s
        half = 150 * s
        height = 132 * s
        th = 30 * s
        n = 40
        inner = []
        outer = []
        for i in range(n + 1):
            t = math.pi * i / n
            wob = 1.0 + 0.06 * math.sin(i * 1.7 + idx) + rng.uniform(-0.03, 0.03)
            ix = cx - math.cos(t) * half * wob
            iy = base - math.sin(t) ** 0.85 * height * wob
            inner.append((ix, iy))
            ox = cx - math.cos(t) * (half + th) * (1.02 + rng.uniform(-0.02, 0.03))
            oy = base - math.sin(t) ** 0.85 * (height + th) * (1.02 + rng.uniform(-0.02, 0.03))
            outer.append((ox, oy))
        poly = [(outer[0][0] - 10 * s, H + 2)] + outer + [(outer[-1][0] + 10 * s, H + 2), (inner[-1][0], H + 2)] \
            + inner[::-1] + [(inner[0][0], H + 2)]
        mask = c.poly(poly)
        fade = 1.0 - (s - rings[0]) / (rings[-1] - rings[0])
        bark = mix(rgb("#2a1a0e"), haze, fade * 0.75)
        dark = mix(rgb("#100a05"), haze * 0.6, fade * 0.7)
        stripes = c.fbm(9 * s + 2, 3)
        col = dark[None, None, :] + (bark - dark)[None, None, :] * stripes[..., None]
        c.put(mask, col)
        # twisted strands along the arch
        for k in range(4):
            r = 0.15 + 0.7 * k / 3
            pts = []
            for i in range(n + 1):
                t = math.pi * i / n
                rr = half + th * r + rng.normal(0, 1.2 * s)
                hh = height + th * r + rng.normal(0, 1.2 * s)
                pts.append((cx - math.cos(t) * rr, base - math.sin(t) ** 0.85 * hh))
            pts = [(pts[0][0], H + 2)] + pts + [(pts[-1][0], H + 2)]
            tone = mix(dark, bark * 1.25, rng.uniform(0.3, 1.0))
            c.put(c.line(pts, max(0.8, 3.6 * s * rng.uniform(0.6, 1.2))) * mask, tone, 0.85)
        # moss on the upper face
        moss = mask * smoothstep(0.58, 0.8, c.fbm(6 * s + 2, 3)) * smoothstep(base, base - height - th, c.Y)
        c.put(moss, mix(rgb("#3e7a34"), haze, fade * 0.6), 0.6)
        # hanging rootlets
        hang = []
        for _ in range(int(10 * s + 3)):
            t = rng.uniform(0.15, 0.85) * math.pi
            hx = cx - math.cos(t) * half
            hy = base - math.sin(t) ** 0.85 * height
            hang.append(jitter_line(rng, hx, hy - 2, hx + rng.uniform(-3, 3), hy + rng.uniform(8, 34) * s, 5, 0.8))
        c.put(c.lines(hang, max(0.6, 1.2 * s)), dark, 0.9)
        if idx >= 2:
            # glow berries on vines
            for _ in range(int(3 * s + 1)):
                vx = cx + rng.uniform(-half * 0.8, half * 0.8)
                vy = base - height * rng.uniform(0.7, 0.95)
                ln = rng.uniform(16, 46) * s
                c.put(c.line(jitter_line(rng, vx, vy, vx + rng.uniform(-2, 2), vy + ln, 5, 0.6), 0.7),
                      rgb("#1e3a14"), 0.9)
                for b in range(int(ln / 7)):
                    by = vy + 4 + b * 7 + rng.uniform(-1, 1)
                    c.put(c.ellipse(vx + rng.uniform(-1.2, 1.2), by, 1.4 * s + 0.6), rgb("#ffd25a"))
                    c.glow(vx, by, 7 * s + 3, rgb("#ffb43a"), 0.2)
    # glowing mushrooms at the foot of the roots
    for (x0, x1) in ((20, 130), (342, 452)):
        for _ in range(9):
            mx = rng.uniform(x0, x1)
            my = rng.uniform(168, 196)
            r = rng.uniform(2.5, 7.5)
            c.put(c.rect(mx - r * 0.18, my - r * 0.2, mx + r * 0.18, my + r * 1.2), rgb("#a8d8cc"), 0.85)
            cap = c.ellipse(mx, my, r, r * 0.62) * (c.Y < my + 0.5)
            c.put(cap, c.vgrad([(0, rgb("#b8ffff")), (1, rgb("#2ab8d8"))], my - r * 0.6, my))
            c.glow(mx, my, r * 4.5, rgb("#40e0ff"), 0.32)
    # ferns in the near corners
    fronds = []
    for (bx, dirx) in ((-6, 1), (W + 6, -1)):
        for _ in range(6):
            ang = rng.uniform(0.25, 1.2)
            ln = rng.uniform(40, 70)
            pts = []
            for i in range(12):
                t = i / 11
                a = ang - t * 0.9
                pts.append((bx + dirx * math.cos(a) * ln * t, 202 - math.sin(a) * ln * t))
            fronds.append(pts)
            for i in range(2, 11):
                x, y = pts[i]
                ll = (1 - i / 12) * 9 + 2
                fronds.append([(x, y), (x + dirx * ll * 0.5, y - ll)])
                fronds.append([(x, y), (x + dirx * ll, y + ll * 0.25)])
    c.put(c.lines(fronds, 1.3), rgb("#020a06"))
    motes(c, 46, (40, 40, 432, 190), rgb("#d6ff6e"), 0.9, 4.0, 0.22, (0.4, 1.0))
    motes(c, 80, (0, 0, W, H), rgb("#9affe0"), 0.35, 0, 0, (0.2, 0.6))
    c.bloom(0.6, 6.0, 0.5)


def scene_d3(c):
    """Temple of Sand: a pylon gate against a desert sunset."""
    rng = c.rng
    c.img[:] = c.vgrad([(0.0, rgb("#1b1238")), (0.3, rgb("#4a2350")), (0.55, rgb("#b0402c")),
                        (0.68, rgb("#ec8a42")), (0.74, rgb("#ffc27a"))], 0, 140)
    stars(c, 60, 50, rgb("#ffe8d0"), 0.6)
    c.glow(426, 118, 240, rgb("#ff7a3a"), 0.75, power=1.6, ry=110)
    c.put(c.ellipse(426, 120, 22), rgb("#fff1c4"))
    c.glow(426, 120, 64, rgb("#ffd59a"), 0.55)
    # haze streaks in the sky
    streak = smoothstep(0.55, 0.8, c.fbm(30, 4)) * smoothstep(60, 100, c.Y) * smoothstep(140, 110, c.Y)
    c.add(streak, rgb("#ffb070"), 0.18)
    # distant dunes
    for (y, amp, col, k) in ((126, 7, "#c46a40", 0.9), (134, 9, "#9a4a2c", 1.0)):
        pts = [(-2, H)]
        for x in np.linspace(-2, W + 2, 30):
            pts.append((x, y - amp * (0.5 + 0.5 * math.sin(x * 0.021 + y)) - rng.uniform(0, 2)))
        pts.append((W + 2, H))
        c.put(c.poly(pts), rgb(col), k)
    # the temple, backlit
    facade = c.vgrad([(0, rgb("#3e2216")), (1, rgb("#6e3c24"))], 50, 170)
    pylons = [[(118, 168), (128, 64), (192, 64), (202, 168)], [(270, 168), (280, 64), (344, 64), (354, 168)]]
    c.put(c.polys(pylons), facade)
    c.put(c.polys([[(122, 66), (124, 56), (196, 56), (198, 66)], [(274, 66), (276, 56), (348, 56), (350, 66)]]),
          rgb("#4a2a1a"))
    c.put(c.rect(194, 96, 278, 112), rgb("#4a2818"))
    c.put(c.rect(192, 92, 280, 97), rgb("#5a3220"))
    c.put(c.rect(200, 112, 272, 168), facade)
    # courses and carved glyph columns
    for y in range(70, 168, 6):
        c.put(c.lines([[(120, y), (200, y)], [(272, y), (352, y)]], 0.5), rgb("#2a160e"), 0.45)
    glyphs = []
    for gx in (138, 152, 166, 180, 290, 304, 318, 332):
        for gy in range(74, 158, 7):
            if rng.random() < 0.8:
                shape = rng.integers(0, 4)
                if shape == 0:
                    glyphs.append([(gx - 1.5, gy), (gx + 1.5, gy)])
                elif shape == 1:
                    glyphs.append([(gx, gy - 2), (gx, gy + 2)])
                elif shape == 2:
                    glyphs.append([(gx - 1.5, gy + 1.5), (gx, gy - 1.5), (gx + 1.5, gy + 1.5)])
                else:
                    glyphs.append([(gx - 1.5, gy - 1), (gx + 1.5, gy - 1), (gx + 1.5, gy + 1.5)])
    c.put(c.lines(glyphs, 0.6), rgb("#d08a52"), 0.4)
    # rim light from the sun on the right edges
    for pts in pylons:
        (bx0, by0), (tx0, ty0), (tx1, ty1), (bx1, by1) = pts
        c.put(c.line([(tx1, ty1 - 8), (bx1, by1)], 1.2), rgb("#ffb070"), 0.75)
    c.put(c.rect(272, 112, 274, 168), rgb("#ff9a5a"), 0.5)
    # the doorway and the fire inside
    door = c.rect(214, 116, 258, 168)
    c.put(door, rgb("#140806"))
    c.glow(236, 160, 30, rgb("#ff7a20"), 0.5, ry=22)
    c.put(c.rect(212, 112, 260, 116), rgb("#2e180e"))
    # obelisks
    for ox in (96, 376):
        ob = c.poly([(ox - 7, 172), (ox - 4.5, 82), (ox, 72), (ox + 4.5, 82), (ox + 7, 172)])
        c.put(ob, c.vgrad([(0, rgb("#3a2016")), (1, rgb("#5e3220"))], 72, 172))
        c.put(c.line([(ox + 4.5, 82), (ox + 7, 172)], 1.0), rgb("#ffb070"), 0.7)
        c.put(c.poly([(ox, 72), (ox + 4.5, 82), (ox, 82)]), rgb("#ffd890"), 0.9)
    # steps
    for i, (x0, x1) in enumerate(((206, 266), (198, 274), (190, 282))):
        y = 168 + i * 4
        c.put(c.rect(x0, y, x1, y + 4), rgb("#7a4a2c"))
        c.put(c.rect(x0, y, x1, y + 0.8), rgb("#e09a5a"), 0.6)
    # braziers
    for bx in (202, 270):
        c.put(c.poly([(bx - 6, 158), (bx + 6, 158), (bx + 3, 164), (bx - 3, 164)]), rgb("#1a0e08"))
        c.put(c.rect(bx - 1, 164, bx + 1, 172), rgb("#1a0e08"))
        flame(c, bx, 158, 4.2)
    # sand
    dunes = [(-2, H)]
    for x in np.linspace(-2, W + 2, 40):
        dunes.append((x, 176 + 10 * math.sin(x * 0.018 + 1.3) + 4 * math.sin(x * 0.05)))
    dunes.append((W + 2, H))
    sand = c.poly(dunes)
    c.put(sand, c.vgrad([(0, rgb("#d89458")), (1, rgb("#6a3a22"))], 166, 200))
    ripples = []
    for _ in range(26):
        x0 = rng.uniform(-20, W)
        y0 = rng.uniform(182, 199)
        ripples.append([(x0 + i * 4, y0 + math.sin(i * 0.7) * 0.8) for i in range(10)])
    c.put(c.lines(ripples, 0.6) * sand, rgb("#7a4426"), 0.5)
    blow = smoothstep(0.55, 0.85, c.fbm(24, 4)) * smoothstep(120, 190, c.Y)
    c.add(blow, rgb("#ffb878"), 0.12)
    motes(c, 70, (0, 90, W, 200), rgb("#ffd8a0"), 0.5, 0, 0, (0.2, 0.8))
    c.bloom(0.68, 6.0, 0.45)


def scene_d4(c):
    """Frozen Prison: a corridor of ice and iron cells, soul lanterns burning cold."""
    rng = c.rng
    vx, vy = 236, 96
    near = (-20, -24, W + 20, H + 20)
    far = (204, 76, 268, 116)

    def lerp_pt(p_near, p_far, s):
        return (p_near[0] + (p_far[0] - p_near[0]) * s, p_near[1] + (p_far[1] - p_near[1]) * s)

    c.img[:] = c.vgrad([(0, rgb("#040710")), (1, rgb("#0b1424"))])
    nl, nt, nr, nb = near
    fl, ft, fr, fb = far
    ceiling = c.poly([(nl, nt), (nr, nt), (fr, ft), (fl, ft)])
    floor = c.poly([(nl, nb), (nr, nb), (fr, fb), (fl, fb)])
    left = c.poly([(nl, nt), (fl, ft), (fl, fb), (nl, nb)])
    right = c.poly([(nr, nt), (fr, ft), (fr, fb), (nr, nb)])
    depth_l = np.clip((c.X - nl) / (fl - nl), 0, 1)
    depth_r = np.clip((nr - c.X) / (nr - fr), 0, 1)
    ice = c.fbm(14, 4)
    wall_near = rgb("#1c3352")
    wall_far = rgb("#2a5a80")
    c.put(left, mix(wall_near, wall_far, depth_l[..., None] ** 1.6) * (0.75 + 0.5 * ice[..., None]))
    c.put(right, mix(wall_near, wall_far, depth_r[..., None] ** 1.6) * (0.75 + 0.5 * ice[..., None]))
    c.put(ceiling, c.vgrad([(0, rgb("#060a14")), (1, rgb("#1a3050"))], nt, ft))
    c.put(floor, c.vgrad([(0, rgb("#3a5a7a")), (1, rgb("#22405e"))], fb, nb))
    # block courses
    course = []
    for f in np.linspace(0.08, 0.92, 9):
        yl = nt + (nb - nt) * f
        yf = ft + (fb - ft) * f
        course.append([(nl, yl), (fl, yf)])
        course.append([(nr, yl), (fr, yf)])
    for s in (0.25, 0.45, 0.6, 0.72, 0.8, 0.86, 0.91):
        p0 = lerp_pt((nl, nt), (fl, ft), s)
        p1 = lerp_pt((nl, nb), (fl, fb), s)
        course.append([p0, p1])
        q0 = lerp_pt((nr, nt), (fr, ft), s)
        q1 = lerp_pt((nr, nb), (fr, fb), s)
        course.append([q0, q1])
    c.put(c.lines(course, 0.6), rgb("#0a1626"), 0.55)
    # the far door: a portcullis with light behind it
    c.put(c.rect(fl, ft, fr, fb), rgb("#0c1a2c"))
    c.glow(vx, vy + 6, 46, rgb("#5ad8ff"), 0.9, ry=30)
    c.put(c.ellipse(vx, vy + 4, 14, 18) * (c.Y > vy - 14), rgb("#9ff0ff"), 0.6)
    bars = [[(x, vy - 14), (x, fb)] for x in np.linspace(vx - 12, vx + 12, 7)]
    bars += [[(vx - 14, y), (vx + 14, y)] for y in (vy - 4, vy + 8)]
    c.put(c.lines(bars, 1.0), rgb("#06101a"))
    # cells on both walls
    for (s0, s1) in ((0.04, 0.36), (0.48, 0.66), (0.74, 0.84)):
        for side in (0, 1):
            if side == 0:
                top_near, bot_near, top_far, bot_far = (nl, nt), (nl, nb), (fl, ft), (fl, fb)
            else:
                top_near, bot_near, top_far, bot_far = (nr, nt), (nr, nb), (fr, ft), (fr, fb)
            a0 = lerp_pt(top_near, top_far, s0)
            a1 = lerp_pt(top_near, top_far, s1)
            b0 = lerp_pt(bot_near, bot_far, s0)
            b1 = lerp_pt(bot_near, bot_far, s1)

            def at(p_top, p_bot, f):
                return (p_top[0] + (p_bot[0] - p_top[0]) * f, p_top[1] + (p_bot[1] - p_top[1]) * f)

            q = [at(a0, b0, 0.3), at(a1, b1, 0.3), at(a1, b1, 0.97), at(a0, b0, 0.97)]
            cell = c.poly(q)
            c.put(cell, rgb("#03060c"))
            cx = (q[0][0] + q[1][0]) / 2
            cy = (q[0][1] + q[2][1]) / 2
            c.glow(cx, cy, abs(q[1][0] - q[0][0]) * 0.8, rgb("#2a7ab0"), 0.35)
            nbars = max(3, int(abs(q[1][0] - q[0][0]) / 4))
            bl = []
            for i in range(nbars + 1):
                f = i / nbars
                p_top = (q[0][0] + (q[1][0] - q[0][0]) * f, q[0][1] + (q[1][1] - q[0][1]) * f)
                p_bot = (q[3][0] + (q[2][0] - q[3][0]) * f, q[3][1] + (q[2][1] - q[3][1]) * f)
                bl.append([p_top, p_bot])
            width = max(0.6, 1.6 * (1 - s0))
            c.put(c.lines(bl, width), rgb("#0a0f16"))
            c.put(c.lines([[(p[0] + width * 0.4, p[1]) for p in b] for b in bl], width * 0.4),
                  rgb("#6a8aa8"), 0.5)
            c.put(c.lines([[q[0], q[1]], [q[3], q[2]]], width * 1.4), rgb("#0a0f16"))
            # a soul lantern between this cell and the next
            ls = s1 + 0.03
            lp = at(lerp_pt(top_near, top_far, ls), lerp_pt(bot_near, bot_far, ls), 0.42)
            size = 4.5 * (1 - ls) + 1.2
            c.put(c.rect(lp[0] - size * 0.5, lp[1] - size * 0.7, lp[0] + size * 0.5, lp[1] + size * 0.7),
                  rgb("#101820"))
            c.put(c.ellipse(lp[0], lp[1], size * 0.3, size * 0.45), rgb("#c8ffff"))
            c.glow(lp[0], lp[1], size * 9, rgb("#3ae0ff"), 0.55)
    # icicles along the ceiling edges
    ic = []
    for side in (0, 1):
        for i in range(40):
            s = (i / 40) ** 0.7 * 0.94
            p = lerp_pt((nl, nt) if side == 0 else (nr, nt), (fl, ft) if side == 0 else (fr, ft), s)
            p = (p[0], p[1] + 1)
            ln = rng.uniform(4, 14) * (1 - s) + 1
            wd = rng.uniform(1.5, 3.5) * (1 - s) + 0.6
            if p[1] < -4:
                p = (p[0], -4 + rng.uniform(0, 4))
            ic.append([(p[0] - wd, p[1] - 2), (p[0] + wd, p[1] - 2), (p[0] + rng.uniform(-0.5, 0.5), p[1] + ln)])
    for x in np.linspace(nl + 20, nr - 20, 34):
        ic.append([(x - 3, -2), (x + 3, -2), (x + rng.uniform(-1, 1), rng.uniform(6, 16))])
    c.put(c.polys(ic), rgb("#a8e4ff"), 0.75)
    # hanging chains
    for (hx, ln) in ((150, 54), (186, 30), (318, 44), (362, 62)):
        for k in range(int(ln / 3.2)):
            yy = 2 + k * 3.2
            if k % 2 == 0:
                ring = c.ellipse(hx, yy, 1.4, 2.0) - c.ellipse(hx, yy, 0.6, 1.1)
            else:
                ring = c.ellipse(hx, yy, 0.8, 2.0) - c.ellipse(hx, yy, 0.25, 1.1)
            c.put(np.clip(ring, 0, 1), rgb("#3a4c60"))
    # snow drifts against the walls
    drift = c.poly([(nl, nb), (fl, fb), (fl + 18, fb + 2), (150, 170), (60, H), (nl, H)])
    drift_r = c.poly([(nr, nb), (fr, fb), (fr - 18, fb + 2), (322, 170), (412, H), (nr, H)])
    snow = c.vgrad([(0, rgb("#7aa0c4")), (1, rgb("#c8dcf0"))], fb, H)
    c.put(np.clip(drift + drift_r, 0, 1) * (0.55 + 0.45 * c.fbm(10, 3)), snow, 0.85)
    # a cold sheen down the middle of the floor
    c.glow(vx, 170, 70, rgb("#7adaff"), 0.22, ry=34)
    fog = smoothstep(130, 200, c.Y) * (0.5 + 0.5 * c.fbm(30, 3))
    c.add(fog, rgb("#4a7aa0"), 0.12)
    motes(c, 120, (0, 0, W, H), rgb("#e8f8ff"), 0.6, 2.0, 0.08, (0.2, 1.0))
    c.bloom(0.62, 5.0, 0.55)


def scene_d5(c):
    """Frozen Castle: an ice castle under the aurora, snow falling."""
    rng = c.rng
    c.img[:] = c.vgrad([(0, rgb("#030716")), (0.55, rgb("#0d1e44")), (0.78, rgb("#26467a"))], 0, 160)
    stars(c, 220, 130, rgb("#f0f6ff"), 0.9)
    # aurora curtains
    for (base_y, amp, freq, col_lo, col_hi, k) in ((70, 20, 0.012, "#3affb0", "#9a6aff", 0.55),
                                                     (52, 14, 0.019, "#5affd0", "#c070ff", 0.35)):
        phase = rng.uniform(0, 6.28)
        curve = base_y + amp * np.sin(c.X * freq + phase) + 8 * np.sin(c.X * freq * 2.7 + phase * 2)
        below = c.Y - curve
        streaks = 0.55 + 0.45 * np.sin(c.X * 0.6 + np.sin(c.X * 0.05) * 6)
        body = np.where(below < 0, np.exp(below / 34.0), np.exp(-below / 3.0)) * streaks
        body *= smoothstep(0, 60, c.X) * smoothstep(W, W - 60, c.X)
        col = mix(rgb(col_lo)[None, None, :], rgb(col_hi)[None, None, :],
                  np.clip(-below / 60.0, 0, 1)[..., None])
        c.img += body[..., None] * col * k
    c.put(c.ellipse(404, 34, 11), rgb("#f4f8ff"))
    c.put(c.ellipse(399, 31, 9.5), rgb("#1a2a50"), 0.85)
    c.glow(404, 34, 60, rgb("#a8c8ff"), 0.25)
    # mountains
    for (y, jag, col, snowk) in ((118, 26, "#1e3256", 0.7), (132, 18, "#152644", 0.5)):
        pts = [(-2, H)]
        x = -2.0
        while x < W + 10:
            pts.append((x, y - rng.uniform(0, jag)))
            x += rng.uniform(14, 30)
        pts.append((W + 2, H))
        m = c.poly(pts)
        c.put(m, rgb(col))
        capm = m * smoothstep(y - jag * 0.4, y - jag * 0.9, c.Y) * (0.5 + 0.5 * c.fbm(6, 3))
        c.put(capm, rgb("#b8cce8"), snowk)
    # the hill
    hill = c.poly([(-2, H), (-2, 186), (80, 170), (170, 154), (236, 150), (300, 154), (392, 170), (W + 2, 184),
                   (W + 2, H)])
    c.put(hill, c.vgrad([(0, rgb("#d4e4f8")), (1, rgb("#6a8cb8"))], 150, 200))
    # the castle
    wall_l = rgb("#7aa0cc")
    wall_d = rgb("#3a5a8c")

    def tower(x0, x1, top, base, roof_h, roof_col="#284a8a"):
        body = c.rect(x0, top, x1, base)
        xs = np.clip((c.X - x0) / (x1 - x0), 0, 1)[..., None]
        c.put(body, mix(wall_d, wall_l, xs ** 0.8))
        roof = c.poly([(x0 - 3, top + 1), ((x0 + x1) / 2, top - roof_h), (x1 + 3, top + 1)])
        c.put(roof, rgb(roof_col))
        c.put(c.poly([((x0 + x1) / 2, top - roof_h), (x1 + 3, top + 1), (x1, top + 1)]), rgb("#4a74b8"), 0.8)
        c.put(c.line([(x0 - 3, top + 1), (x1 + 3, top + 1)], 1.2), rgb("#e8f2ff"), 0.9)
        for wy in np.arange(top + 8, base - 8, 14):
            lit_window(c, (x0 + x1) / 2 - 1.2, wy, 2.4, 5, rgb("#8ff0ff"), 0.95, 8, 0.3)

    c.put(c.rect(150, 118, 322, 152), mix(wall_d, wall_l, 0.55))
    c.put(c.rect(150, 116, 322, 119), rgb("#e0eeff"), 0.9)
    merlons = [(x, 112, x + 4, 118) for x in np.arange(152, 320, 7)]
    for m in merlons:
        c.put(c.rect(*m), mix(wall_d, wall_l, 0.6))
        c.put(c.rect(m[0], m[1], m[2], m[1] + 1), rgb("#eef6ff"), 0.9)
    tower(120, 138, 106, 152, 26)
    tower(334, 352, 106, 152, 26)
    tower(150, 172, 86, 152, 40)
    tower(300, 322, 86, 152, 40)
    tower(206, 266, 70, 152, 62, "#1e3c7a")
    # rose window and the gate
    c.put(c.ellipse(236, 96, 8), rgb("#0a1a3a"))
    for a in range(8):
        ang = a * math.pi / 4
        c.put(c.line([(236, 96), (236 + math.cos(ang) * 7, 96 + math.sin(ang) * 7)], 1.0),
              rgb("#7ae8ff") if a % 2 else rgb("#a080ff"))
    c.put(c.ellipse(236, 96, 2.2), rgb("#e8ffff"))
    c.glow(236, 96, 30, rgb("#6ad8ff"), 0.5)
    gate = c.poly([(226, 152), (226, 136), (236, 128), (246, 136), (246, 152)])
    c.put(gate, rgb("#0a1630"))
    c.glow(236, 146, 18, rgb("#5ac8ff"), 0.45)
    # ice shards at the foot
    for _ in range(14):
        sx = rng.uniform(140, 332)
        crystal(c, sx, 156 + rng.uniform(-2, 4), rng.uniform(6, 16), rng.uniform(3, 6), rng.uniform(-4, 4),
                rgb("#d8f4ff"), rgb("#5a9ad8"), rgb("#7ad8ff"), 0.08)
    # pines in the foreground
    for (x, h) in ((14, 90), (44, 70), (70, 52), (402, 56), (430, 76), (458, 94)):
        pine(c, x, 204, h, h * 0.5, rgb("#050c1a"), rgb("#c8dcf4"))
    snow = []
    for _ in range(240):
        snow.append((rng.uniform(0, W), rng.uniform(0, H), rng.uniform(0.2, 1.0) ** 1.5))
    c.dots(snow, rgb("#ffffff"), 0.9)
    c.bloom(0.62, 6.0, 0.5)


def scene_d6(c):
    """Scorching Volcanic Cave: a dragon-head gate over a river of lava."""
    rng = c.rng
    c.img[:] = c.vgrad([(0, rgb("#080303")), (0.6, rgb("#1e0806")), (1, rgb("#2a0a04"))])
    rock = c.fbm(30, 5)
    c.img *= (0.6 + 0.8 * rock)[..., None]
    c.glow(236, 200, 300, rgb("#ff4a10"), 0.55, power=1.6, ry=150)
    # lava falls, pouring from cracks in the roof
    for (fx, fw) in ((104, 13), (370, 15)):
        wob = np.sin(c.Y * 0.09 + fx) * 1.6 + np.sin(c.Y * 0.23 + fx * 2) * 0.8
        half = fw / 2 + smoothstep(120, 172, c.Y) * 4
        fall = smoothstep(half + 0.8, half - 0.8, np.abs(c.X - fx - wob)) * (c.Y < 172)
        flow = c.fbm(6, 3)
        striae = 0.55 + 0.45 * np.sin((c.X - fx - wob) * 1.4 + flow * 5 - c.Y * 0.05)
        col = mix(rgb("#ff3a0a")[None, None, :], rgb("#ffe07a")[None, None, :], striae[..., None])
        c.put(fall, col)
        edge = fall * smoothstep(half - 3, half, np.abs(c.X - fx - wob))
        c.put(edge, rgb("#b0200a"), 0.6)
        c.glow(fx, 90, 70, rgb("#ff6a1a"), 0.45, ry=110)
        c.glow(fx, 170, 30, rgb("#ffd060"), 0.5, ry=10)
    # basalt columns on both sides
    for side in (-1, 1):
        xs = np.arange(0, 140, 15) if side < 0 else np.arange(W - 140, W + 10, 15)
        for i, x in enumerate(xs):
            w = rng.uniform(12, 16)
            top = rng.uniform(34, 120) if abs(x - 236) > 150 else rng.uniform(90, 140)
            body = c.rect(x - w / 2, top, x + w / 2, 176)
            shade = np.clip((c.X - (x - w / 2)) / w, 0, 1)
            if side > 0:
                shade = 1 - shade
            base_col = mix(rgb("#0e0808")[None, None, :], rgb("#2a1a16")[None, None, :], shade[..., None] ** 1.5)
            heat = smoothstep(top + 40, 176, c.Y)[..., None]
            col = base_col + heat * rgb("#7a2a0c")[None, None, :] * (0.6 + 0.4 * (1 - shade[..., None]))
            c.put(body, col)
            c.put(c.ellipse(x, top, w / 2, 2.4), rgb("#3a2420"))
            c.put(c.line([(x + side * -w / 2, top + 2), (x + side * -w / 2, 176)], 0.9), rgb("#ff7a2a"), 0.5)
    # the dragon head gate
    head = [(186, 56), (236, 44), (286, 56), (302, 78), (298, 104), (284, 122), (272, 166), (200, 166), (188, 122),
            (174, 104), (170, 78)]
    hm = c.poly(head)
    stone = c.vgrad([(0, rgb("#1a0e0c")), (0.6, rgb("#4a2014")), (1, rgb("#9a4a1e"))], 44, 166)
    c.put(hm, stone)
    tex = c.fbm(7, 3)
    c.multiply(hm, np.array([0.7, 0.7, 0.7], np.float32), (1 - tex) * 0.8)
    for sx in (-1, 1):
        spine = bezier((236 + sx * 34, 62), (236 + sx * 62, 50), (236 + sx * 72, 26), (236 + sx * 64, 2), 20)
        horn = tapered(c, spine, 14, 1.5)
        c.put(horn, c.vgrad([(0, rgb("#6a4a36")), (1, rgb("#1e100c"))], 0, 66))
        ridges = []
        for k in range(1, 8):
            x, y = spine[k * 2]
            ridges.append([(x - sx * 4, y + 2), (x + sx * 4, y - 1)])
        c.put(c.lines(ridges, 0.6) * horn, rgb("#0a0504"), 0.7)
        c.put(tapered(c, [(x + sx * 1.6, y) for x, y in spine], 4, 0.6) * horn, rgb("#ff8a3a"), 0.25)
        brow = [(236 + sx * 8, 76), (236 + sx * 46, 70), (236 + sx * 62, 80), (236 + sx * 40, 80)]
        c.put(c.poly(brow), rgb("#0e0706"))
        eye = [(236 + sx * 14, 90), (236 + sx * 44, 84), (236 + sx * 50, 90), (236 + sx * 22, 97)]
        c.put(c.poly(eye), rgb("#ffcf3a"))
        c.glow(236 + sx * 32, 90, 26, rgb("#ff7a1a"), 0.65)
        c.put(c.ellipse(236 + sx * 11, 112, 3.2, 2.2), rgb("#0a0403"))
    mouth = c.poly([(208, 122), (264, 122), (270, 168), (202, 168)])
    c.put(mouth, rgb("#120403"))
    c.glow(236, 160, 34, rgb("#ff6a14"), 0.85, ry=30)
    c.glow(236, 162, 12, rgb("#ffd060"), 0.6)
    teeth = []
    for x in np.linspace(210, 262, 8):
        teeth.append([(x - 3, 121), (x + 3, 121), (x, 121 + rng.uniform(6, 10))])
        teeth.append([(x - 3, 169), (x + 3, 169), (x, 169 - rng.uniform(4, 8))])
    c.put(c.polys(teeth), rgb("#d8b890"))
    c.put(c.polys(teeth) * smoothstep(121, 130, c.Y), rgb("#ff9a4a"), 0.35)
    # the river of lava
    river = c.poly([(-2, 168), (W + 2, 168), (W + 2, H), (-2, H)])
    flow = c.fbm(16, 4)
    lava = mix(rgb("#ff4a0a")[None, None, :], rgb("#ffd86a")[None, None, :], smoothstep(0.45, 0.75, flow)[..., None])
    crust = smoothstep(0.38, 0.3, c.fbm(10, 3))
    c.put(river, lava)
    c.put(river * crust, rgb("#2a0c06"), 0.9)
    c.put(c.rect(0, 166, W, 169), rgb("#ffb040"), 0.8)
    for x in np.linspace(20, W - 20, 9):
        c.glow(x, 176, 60, rgb("#ff5a10"), 0.18, ry=36)
    # smoke under the roof
    smoke = c.fbm(40, 4) * smoothstep(60, 0, c.Y)
    c.multiply(smoke, np.array([0.3, 0.25, 0.25], np.float32), 0.9)
    # embers
    emb = []
    for _ in range(170):
        x = rng.uniform(0, W)
        y = rng.uniform(0, 175)
        s = rng.uniform(0.2, 1.0) ** 1.6
        emb.append((x, y, s))
        if s > 0.5:
            emb.append((x, y + 1.2, s * 0.5))
    c.dots(emb, rgb("#ffb040"), 1.0, 3.0, 0.08)
    c.bloom(0.6, 6.0, 0.55)


def floating_island(c, cx, top, half, depth, light, mid, dark, grass=("#a8e07a", "#4a8a3a"), jag=1.0):
    rng = c.rng
    under = [(cx - half, top)]
    for i in range(1, 10):
        t = i / 10
        under.append((cx - half + half * t + rng.uniform(-3, 3) * jag,
                       top + 6 + depth * math.sin(t * math.pi / 2) ** 1.5 + rng.uniform(-4, 4) * jag * depth / 60))
    under.append((cx + rng.uniform(-3, 3), top + depth))
    for i in range(9, 0, -1):
        t = i / 10
        under.append((cx + half - half * t + rng.uniform(-3, 3) * jag,
                      top + 6 + depth * math.sin(t * math.pi / 2) ** 1.5 + rng.uniform(-4, 4) * jag * depth / 60))
    under.append((cx + half, top))
    mask = c.poly(under)
    c.put(mask, c.vgrad([(0, rgb(light)), (0.45, rgb(mid)), (1, rgb(dark))], top, top + depth))
    strata = c.lines([[(cx - half + i * 3, top + 7 + k * depth / 7 + math.sin(i * 0.5 + k) * 1.5)
                       for i in range(int(half * 2 / 3) + 1)] for k in range(6)], 0.6)
    c.put(strata * mask, rgb(dark), 0.35)
    shade = mask * smoothstep(cx - half * 0.2, cx + half, c.X)
    c.multiply(shade, np.array([0.75, 0.75, 0.85], np.float32), 0.6)
    if grass is not None:
        cap = c.poly([(cx - half - 3, top + 2), (cx - half + 8, top - 3), (cx, top - 5), (cx + half - 8, top - 3),
                      (cx + half + 3, top + 2), (cx + half, top + 6), (cx - half, top + 6)])
        c.put(cap, c.vgrad([(0, rgb(grass[0])), (1, rgb(grass[1]))], top - 5, top + 6))
        tufts = [[(x, top + 5), (x + rng.uniform(-1, 1), top + 5 + rng.uniform(2, 7))]
                 for x in np.linspace(cx - half + 4, cx + half - 4, int(half / 3))]
        c.put(c.lines(tufts, 0.9), rgb(grass[1]), 0.9)
    return mask


def waterfall(c, x, y0, y1, width):
    wob = np.sin(c.Y * 0.12 + x) * 0.8
    half = width / 2 + smoothstep(y0, y1, c.Y) * width * 0.6
    m = smoothstep(half + 0.7, half - 0.7, np.abs(c.X - x - wob)) * (c.Y > y0) * (c.Y < y1)
    m *= smoothstep(y1, y1 - 18, c.Y) * 0.6 + 0.4
    streak = 0.65 + 0.35 * np.sin((c.X - x) * 2.2 + c.fbm(5, 2) * 4)
    c.put(m, mix(rgb("#bfe6ff")[None, None, :], rgb("#ffffff")[None, None, :], streak[..., None]), 0.95)
    c.glow(x, y1, width * 4, rgb("#ffffff"), 0.5, ry=width * 2)


def scene_d7(c):
    """Ancient Sky Ruins: white columns and a copper ring on floating islands above the clouds."""
    rng = c.rng
    c.img[:] = c.vgrad([(0, rgb("#1a4cb0")), (0.6, rgb("#58a0e4")), (1, rgb("#bfe2ff"))])
    c.glow(438, 4, 260, rgb("#fff6d8"), 0.6, power=1.7)
    c.put(c.ellipse(438, 4, 13), rgb("#fffdf0"))
    for a in np.linspace(1.95, 2.85, 7):
        spread = rng.uniform(0.012, 0.035)
        ray = c.poly([(438, 4), (438 + math.cos(a - spread) * 520, 4 + math.sin(a - spread) * 520),
                      (438 + math.cos(a + spread) * 520, 4 + math.sin(a + spread) * 520)])
        c.add(c.blur_mask(ray, 1.5) * c.radial(438, 4, 470, power=1.1), rgb("#fff8e0"), rng.uniform(0.05, 0.1))
    wisp = smoothstep(0.6, 0.78, c.fbm(44, 5)) * smoothstep(90, 20, c.Y)
    c.put(wisp, rgb("#f4fbff"), 0.4)
    # far islands in the haze
    for (fx, fy, fh, fd) in ((402, 96, 22, 22), (346, 56, 12, 14), (452, 140, 14, 16)):
        m = floating_island(c, fx, fy, fh, fd, "#9ab8d8", "#8aa8c8", "#7a98bc", grass=("#a8cdb8", "#90b4a8"),
                            jag=0.4)
    # a bank of cloud behind the main island
    bank_n = c.fbm(30, 5) + smoothstep(110, 175, c.Y) * 0.55 - 0.18
    bank = smoothstep(0.46, 0.58, bank_n)
    bank_lit = smoothstep(0.46, 0.58, np.roll(bank_n, 3, axis=0))
    c.put(bank, mix(rgb("#ffffff")[None, None, :], rgb("#b8cce6")[None, None, :],
                    np.clip(bank - bank_lit * 0.7, 0, 1)[..., None]), 0.95)
    # the left island: a ruined round tower and a waterfall
    lm = floating_island(c, 66, 66, 44, 44, "#9a8470", "#6a5650", "#3a3444")
    column(c, 58, 22, 64, 20, rgb("#f6f2ea"), rgb("#a4a2b8"), broken=True)
    for wy in (34, 48):
        c.put(c.poly([(56, wy + 6), (56, wy), (58, wy - 2), (60, wy), (60, wy + 6)]), rgb("#3a3a50"))
    c.put(c.lines([jitter_line(rng, 50, 30, 49, 58, 6, 0.6), jitter_line(rng, 66, 40, 67, 62, 5, 0.6)], 0.9),
          rgb("#4a8a3a"), 0.9)
    waterfall(c, 104, 70, 148, 3.0)
    # a chain from the left island to the main one
    link_pts = catenary(100, 80, 134, 118, 10, 14)
    for i, (lx, ly) in enumerate(link_pts):
        ring = c.ellipse(lx, ly, 1.8, 1.3) - c.ellipse(lx, ly, 0.8, 0.5) if i % 2 == 0 else c.ellipse(lx, ly, 0.9,
                                                                                                         1.3)
        c.put(np.clip(ring, 0, 1), rgb("#3a3a4a"))
    # the main island
    top_y = 116
    floating_island(c, 240, top_y, 116, 70, "#9a8470", "#6a5650", "#3a3444")
    vines = []
    for _ in range(20):
        vx = rng.uniform(132, 348)
        vy = top_y + 4 + rng.uniform(0, 26) * (1 - abs(vx - 240) / 116)
        vines.append(jitter_line(rng, vx, vy, vx + rng.uniform(-3, 3), vy + rng.uniform(8, 30), 5, 0.6))
    c.put(c.lines(vines, 0.9), rgb("#3a7a34"), 0.9)
    waterfall(c, 352, top_y + 2, 176, 3.4)
    # the arch with a copper ring and a light within
    marble_l = rgb("#fbf8f0")
    marble_d = rgb("#a8a8bc")
    column(c, 200, 62, top_y - 2, 12, marble_l, marble_d)
    column(c, 280, 62, top_y - 2, 12, marble_l, marble_d)
    arch = c.arc_band(240, 64, 34, 46, 180, 360)
    c.put(arch, c.hgrad([(0, marble_d), (0.6, marble_l), (1, mix(marble_l, marble_d, 0.3))], 194, 286))
    c.put(c.rect(190, 58, 210, 64), marble_l)
    c.put(c.rect(270, 58, 290, 64), marble_l)
    for a in np.linspace(195, 345, 9):
        r = math.radians(a)
        c.put(c.line([(240 + math.cos(r) * 34, 64 + math.sin(r) * 34),
                      (240 + math.cos(r) * 46, 64 + math.sin(r) * 46)], 0.5), marble_d, 0.6)
    ring = c.arc_band(240, 84, 18, 23)
    c.put(ring, rgb("#2e9a8c"))
    c.put(c.arc_band(240, 84, 21.5, 23), rgb("#7ae0c8"), 0.8)
    teeth = []
    for k in range(16):
        r = k * math.pi / 8
        tx, ty = 240 + math.cos(r) * 24.5, 84 + math.sin(r) * 24.5
        ca, sa = math.cos(r), math.sin(r)
        teeth.append([(tx - sa * 1.6 - ca * 1.6, ty + ca * 1.6 - sa * 1.6), (tx + sa * 1.6 - ca * 1.6,
                                                                              ty - ca * 1.6 - sa * 1.6),
                      (tx + sa * 1.6 + ca * 1.6, ty - ca * 1.6 + sa * 1.6), (tx - sa * 1.6 + ca * 1.6,
                                                                              ty + ca * 1.6 + sa * 1.6)])
    c.put(c.polys(teeth), rgb("#2a8a7e"))
    c.glow(240, 84, 46, rgb("#7af0ff"), 0.7)
    c.put(c.ellipse(240, 84, 6.5), rgb("#e8ffff"))
    # broken columns, fallen drums, steps and a tree
    column(c, 158, 90, top_y - 2, 11, marble_l, marble_d, broken=True)
    column(c, 316, 80, top_y - 2, 11, marble_l, marble_d, broken=True)
    c.put(c.ellipse(176, top_y - 4, 9, 4), marble_d)
    c.put(c.ellipse(176, top_y - 5, 8, 3), marble_l)
    for (x0, x1, y) in ((206, 274, top_y - 3), (214, 266, top_y - 6)):
        c.put(c.rect(x0, y, x1, y + 3), marble_l)
        c.put(c.rect(x0, y + 2, x1, y + 3), marble_d, 0.6)
    c.put(c.rect(337, top_y - 22, 340, top_y), rgb("#5a3a26"))
    for (ex, ey, er) in ((332, top_y - 26, 8), (344, top_y - 28, 9), (338, top_y - 34, 8)):
        c.put(c.ellipse(ex, ey, er, er * 0.85), rgb("#3e8a3a"))
    for (ex, ey, er) in ((336, top_y - 30, 5), (346, top_y - 31, 5)):
        c.put(c.ellipse(ex, ey, er, er * 0.8), rgb("#7ac85a"), 0.85)
    # the sea of clouds in front
    sea = c.fbm(34, 5) + smoothstep(150, 200, c.Y) * 0.6 - 0.3
    cloud = smoothstep(0.42, 0.56, sea)
    lit = smoothstep(0.42, 0.56, np.roll(sea, 3, axis=0))
    shade = np.clip(cloud - lit * 0.6, 0, 1)
    c.put(cloud, mix(rgb("#ffffff")[None, None, :], rgb("#b4c8e4")[None, None, :], shade[..., None]))
    birds = []
    for (bx, by, bs) in ((120, 36, 3), (134, 30, 2.5), (148, 40, 2.2), (300, 22, 2), (312, 28, 1.6)):
        birds.append([(bx - bs, by - bs * 0.6), (bx, by), (bx + bs, by - bs * 0.6)])
    c.put(c.lines(birds, 0.7), rgb("#2a4a7a"), 0.8)
    motes(c, 40, (160, 40, 320, 120), rgb("#e0fdff"), 0.8, 3.0, 0.15, (0.3, 1.0))
    c.bloom(0.75, 6.0, 0.4)


def scene_d8(c):
    """Tower of the End: a black spire against a vast moon, crystals and violet mist."""
    rng = c.rng
    c.img[:] = c.vgrad([(0, rgb("#060209")), (0.5, rgb("#1c0a2c")), (0.85, rgb("#3a1250"))], 0, 180)
    stars(c, 120, 120, rgb("#f0e0ff"), 0.6)
    c.glow(312, 70, 210, rgb("#9a50ff"), 0.4, power=1.6)
    moon = c.ellipse(312, 70, 46)
    maria = smoothstep(0.42, 0.62, c.fbm(16, 4))
    craters = c.fbm(5, 3)
    surface = mix(rgb("#b9a6d4")[None, None, :], rgb("#7a6aa0")[None, None, :], maria[..., None] * 0.8)
    surface = surface * (0.86 + 0.22 * craters[..., None])
    limb = np.clip(1.0 - (np.sqrt((c.X - 312) ** 2 + (c.Y - 70) ** 2) / 46.0) ** 4, 0, 1)
    c.put(moon, surface * (0.7 + 0.3 * limb[..., None]))
    for (mx, my, mr) in ((296, 58, 5), (326, 84, 4), (300, 92, 3), (330, 52, 2.5), (318, 66, 2)):
        c.put(np.clip(c.ellipse(mx, my, mr) - c.ellipse(mx + 0.6, my + 0.6, mr * 0.8), 0, 1) * moon,
              rgb("#e4d8f4"), 0.5)
        c.put(c.ellipse(mx + 0.6, my + 0.6, mr * 0.8) * moon, rgb("#6a5a8a"), 0.35)
    # storm clouds
    cl = smoothstep(0.5, 0.72, c.fbm(40, 5)) * smoothstep(150, 20, c.Y)
    c.put(cl, rgb("#140820"), 0.75)
    edge = np.clip(cl - smoothstep(0.5, 0.72, np.roll(c.fbm(40, 5, seed=99), 2, axis=1)), 0, 1)
    c.add(edge, rgb("#6a4a9a"), 0.15)
    # lightning
    bolt = [(64, -2)]
    x, y = 64.0, -2.0
    while y < 84:
        x += rng.uniform(-6, 7)
        y += rng.uniform(6, 11)
        bolt.append((x, y))
    c.put(c.line(bolt, 0.9), rgb("#f0e0ff"), 0.9)
    branch = [bolt[3]]
    bx, by = bolt[3]
    for _ in range(4):
        bx += rng.uniform(2, 8)
        by += rng.uniform(4, 8)
        branch.append((bx, by))
    c.put(c.line(branch, 0.6), rgb("#d8c0ff"), 0.7)
    c.glow(70, 40, 60, rgb("#a080ff"), 0.25)
    # floating rocks
    for (fx, fy, fs) in ((150, 70, 9), (330, 136, 7), (128, 118, 5), (360, 40, 5), (172, 30, 4)):
        pts = [(fx + math.cos(a) * fs * rng.uniform(0.7, 1.2), fy + math.sin(a) * fs * 0.6 * rng.uniform(0.7, 1.2)
                + (fs * 0.8 if math.sin(a) > 0 else 0)) for a in np.linspace(0, 6.28, 9)]
        c.put(c.poly(pts), rgb("#0c0612"))
        c.glow(fx, fy + fs, fs * 3, rgb("#b050ff"), 0.3)
    # the tower
    sil = [(184, 202), (192, 152), (204, 146), (206, 100), (214, 92), (216, 62), (226, 52), (236, 10), (246, 52),
           (256, 62), (258, 92), (266, 100), (268, 146), (280, 152), (288, 202)]
    side = [[(160, 202), (162, 132), (158, 132), (168, 104), (178, 132), (174, 132), (176, 202)],
            [(296, 202), (298, 132), (294, 132), (304, 100), (314, 132), (310, 132), (312, 202)]]
    bridges = [[(174, 150), (194, 140), (194, 146), (174, 156)], [(298, 150), (278, 140), (278, 146), (298, 156)]]
    tm = np.clip(c.poly(sil) + c.polys(side) + c.polys(bridges), 0, 1)
    c.put(tm, rgb("#0b0711"))
    rim = np.clip(tm - np.roll(tm, -2, axis=1), 0, 1)
    c.put(rim, rgb("#9a7ad0"), 0.8)
    c.put(c.rect(204, 130, 268, 133), rgb("#1a1024"))
    c.put(c.rect(204, 130, 268, 131), rgb("#6a4a9a"), 0.6)
    for (sx, sy) in ((168, 124), (304, 120)):
        c.put(c.poly([(sx - 1.5, sy + 4), (sx - 1.5, sy), (sx, sy - 2), (sx + 1.5, sy), (sx + 1.5, sy + 4)]),
              rgb("#ff6ae8"))
        c.glow(sx, sy + 1, 8, rgb("#ff40d0"), 0.35)
    c.glow(236, 10, 16, rgb("#ff60e0"), 0.8)
    c.put(c.ellipse(236, 10, 2.2), rgb("#ffd0ff"))
    for (wx, wy, ww, wh) in ((236, 118, 5, 14), (236, 76, 4, 10), (222, 162, 3, 9), (250, 162, 3, 9),
                             (236, 36, 2, 6)):
        win = c.poly([(wx - ww / 2, wy + wh / 2), (wx - ww / 2, wy - wh / 4), (wx, wy - wh / 2),
                      (wx + ww / 2, wy - wh / 4), (wx + ww / 2, wy + wh / 2)])
        c.put(win, rgb("#ff6ae8"))
        c.glow(wx, wy, ww * 5, rgb("#ff40d0"), 0.4)
    # ground, crystals and mist
    ground = c.poly([(-2, H), (-2, 178), (60, 172), (140, 182), (236, 188), (330, 180), (420, 170), (W + 2, 176),
                     (W + 2, H)])
    c.put(ground, rgb("#07030b"))
    for (cx0, n, side) in ((20, 7, -1), (400, 7, 1), (150, 3, 0), (316, 3, 0)):
        for _ in range(n):
            x = cx0 + rng.uniform(0, 50)
            h = rng.uniform(12, 40) if side != 0 else rng.uniform(8, 18)
            crystal(c, x, 190 + rng.uniform(-6, 6), h, h * 0.32, rng.uniform(-8, 8), rgb("#d088ff"),
                    rgb("#4a1288"), rgb("#b050ff"), 0.12)
    mist = smoothstep(150, 200, c.Y) * (0.4 + 0.6 * c.fbm(30, 4))
    c.add(mist, rgb("#7a2aa8"), 0.3)
    sparks = [(rng.uniform(0, W), rng.uniform(40, 200), rng.uniform(0.2, 1.0) ** 1.4) for _ in range(90)]
    c.dots(sparks, rgb("#e070ff"), 0.9, 3.0, 0.1)
    c.bloom(0.62, 6.0, 0.5)


def scene_final(c):
    """Throne of the End: a black throne before a ring of light, two dragon statues keeping watch."""
    rng = c.rng
    c.img[:] = c.vgrad([(0, rgb("#030106")), (0.5, rgb("#0e0416")), (1, rgb("#07020b"))])
    stars(c, 260, 200, rgb("#f4e8ff"), 0.85)
    cx, cy = 236, 78
    # the vortex within the ring
    dx = c.X - cx
    dy = c.Y - cy
    r = np.sqrt(dx * dx + dy * dy)
    ang = np.arctan2(dy, dx)
    swirl = np.sin(ang * 3 + r * 0.16) * 0.5 + 0.5
    inside = smoothstep(64, 54, r)
    c.put(inside, mix(rgb("#06020c")[None, None, :], rgb("#3a1260")[None, None, :],
                      (swirl * smoothstep(0, 60, r))[..., None]))
    c.add(inside * c.radial(cx, cy, 30, power=1.5), rgb("#c080ff"), 0.4)
    # the ring
    band = np.exp(-((r - 62) / 2.6) ** 2)
    c.add(band, rgb("#d8a0ff"), 1.2)
    c.add(np.exp(-((r - 62) / 0.9) ** 2), rgb("#ffffff"), 0.9)
    c.add(np.exp(-((r - 62) / 14.0) ** 2), rgb("#8a40ff"), 0.35)
    c.add(np.exp(-((r - 76) / 0.8) ** 2) * (0.5 + 0.5 * np.sin(ang * 24)), rgb("#e0b8ff"), 0.35)
    runes = []
    for k in range(36):
        a = k * math.pi * 2 / 36
        px, py = cx + math.cos(a) * 69, cy + math.sin(a) * 69
        kind = k % 3
        if kind == 0:
            runes.append([(px - 1.2, py), (px + 1.2, py)])
        elif kind == 1:
            runes.append([(px, py - 1.2), (px, py + 1.2)])
        else:
            runes.append([(px - 1, py - 1), (px + 1, py + 1)])
    c.add(c.lines(runes, 0.5), rgb("#f0d8ff"), 0.7)
    # columns at the sides with banners
    for (x0, x1) in ((0, 26), (446, 472)):
        c.put(c.rect(x0, 0, x1, 176), c.hgrad([(0, rgb("#0a0610")), (1, rgb("#1a1024"))], x0, x1))
        bx = (x0 + x1) / 2
        ban = c.poly([(bx - 8, 18), (bx + 8, 18), (bx + 8, 92), (bx, 84), (bx - 8, 92)])
        c.put(ban, c.vgrad([(0, rgb("#3a0c5a")), (1, rgb("#22063a"))], 18, 92))
        c.put(c.lines([[(bx - 8, 18), (bx - 8, 92)], [(bx + 8, 18), (bx + 8, 92)]], 0.8), rgb("#c89a3a"), 0.8)
        c.put(c.ellipse(bx, 44, 3), rgb("#e0b04a"), 0.9)
    # dais steps
    gold = rgb("#d8a84a")
    for i, (x0, x1, y0) in enumerate(((160, 312, 160), (176, 296, 150), (190, 282, 140))):
        c.put(c.rect(x0, y0, x1, y0 + 10 if i else y0 + 14), c.vgrad([(0, rgb("#1c1226")), (1, rgb("#0c0812"))],
                                                                       y0, y0 + 12))
        c.put(c.rect(x0, y0, x1, y0 + 0.9), gold, 0.85)
    # the carpet
    carpet = c.poly([(206, 200), (266, 200), (250, 140), (222, 140)])
    c.put(carpet, c.vgrad([(0, rgb("#5a0a1e")), (1, rgb("#8a1430"))], 140, 200))
    c.put(c.lines([[(206, 200), (222, 140)], [(266, 200), (250, 140)]], 1.0), gold, 0.8)
    # the throne
    back = [(212, 140), (212, 74), (220, 60), (225, 70), (236, 40), (247, 70), (252, 60), (260, 74), (260, 140)]
    c.put(c.poly(back), c.vgrad([(0, rgb("#1a1024")), (1, rgb("#0a0610"))], 40, 140))
    c.put(c.line(back + [back[0]], 0.9), gold, 0.9)
    c.put(c.poly([(222, 132), (222, 82), (236, 66), (250, 82), (250, 132)]), rgb("#3a0c4a"), 0.9)
    c.put(c.rect(206, 122, 266, 140), rgb("#140c1c"))
    c.put(c.rect(206, 122, 266, 123), gold, 0.8)
    for (ax0, ax1) in ((198, 210), (262, 274)):
        c.put(c.rect(ax0, 114, ax1, 142), rgb("#100a18"))
        c.put(c.rect(ax0 - 1, 112, ax1 + 1, 115), gold, 0.9)
    c.put(c.ellipse(236, 56, 3.4, 4.4), rgb("#e8a0ff"))
    c.glow(236, 56, 22, rgb("#c060ff"), 0.6)
    # dragon statues on pedestals, facing the throne
    for sx in (-1, 1):
        px = 236 + sx * 146
        f = -sx
        c.put(c.rect(px - 22, 128, px + 22, 176), c.vgrad([(0, rgb("#1a1224")), (1, rgb("#0a0610"))], 128, 176))
        c.put(c.rect(px - 24, 126, px + 24, 130), gold, 0.7)
        parts = [c.ellipse(px - f * 2, 113, 13, 14), c.ellipse(px + f * 6, 104, 8, 11),
                 tapered(c, bezier((px + f * 6, 100), (px + f * 12, 90), (px + f * 14, 80), (px + f * 10, 70), 12),
                         9, 6),
                 c.poly([(px + f * 6, 70), (px + f * 14, 64), (px + f * 27, 68), (px + f * 28, 72),
                         (px + f * 14, 78)]),
                 tapered(c, bezier((px + f * 8, 67), (px + f * 4, 60), (px - f * 2, 56), (px - f * 6, 52), 8), 3, 0.5),
                 c.poly([(px - f * 2, 100), (px - f * 8, 62), (px - f * 4, 42), (px - f * 16, 56), (px - f * 26, 50),
                         (px - f * 26, 68), (px - f * 36, 70), (px - f * 24, 90), (px - f * 12, 112)]),
                 c.rect(px + f * 4 - 3, 108, px + f * 4 + 3, 128),
                 c.rect(px + f * 12 - 3, 110, px + f * 12 + 3, 128),
                 tapered(c, bezier((px - f * 12, 120), (px - f * 22, 124), (px - f * 22, 128), (px - f * 10, 128),
                                   10), 5, 1)]
        sil = np.clip(sum(parts), 0, 1)
        c.put(sil, c.vgrad([(0, rgb("#2a2036")), (1, rgb("#140e1c"))], 40, 128))
        bones = [[(px - f * 4, 98), (px - f * 4, 44)], [(px - f * 4, 98), (px - f * 16, 56)],
                 [(px - f * 4, 98), (px - f * 26, 52)], [(px - f * 4, 98), (px - f * 36, 70)]]
        c.put(c.lines(bones, 0.7) * sil, rgb("#0a0610"), 0.7)
        inner = np.clip(sil - np.roll(sil, -f * 2, axis=1), 0, 1)
        c.put(inner, rgb("#b48ae8"), 0.75)
        c.put(c.ellipse(px + f * 16, 69, 1.2), rgb("#ff7ab8"))
        c.glow(px + f * 16, 69, 6, rgb("#ff5aa0"), 0.5)
    # the floor of black glass
    c.put(c.rect(0, 176, W, H), rgb("#06030a"))
    reflect(c, 176, 0.38, 0.8, 1.6, tint=np.array([0.8, 0.7, 1.0], np.float32))
    c.put(c.rect(0, 175.5, W, 176.5), rgb("#5a3a8a"), 0.6)
    pull = []
    for _ in range(120):
        a = rng.uniform(0, 6.28)
        rr = rng.uniform(70, 200)
        pull.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr * 0.75, rng.uniform(0.2, 1.0) ** 1.6))
    c.dots(pull, rgb("#d080ff"), 0.9, 2.5, 0.08)
    c.bloom(0.6, 6.0, 0.55)


SCENES = {
    "d1": (scene_d1, 101), "d2": (scene_d2, 202), "d3": (scene_d3, 303), "d4": (scene_d4, 404),
    "d5": (scene_d5, 505), "d6": (scene_d6, 606), "d7": (scene_d7, 707), "d8": (scene_d8, 808),
    "final": (scene_final, 909),
}

FINISH = {
    "d1": dict(exposure=1.25, saturation=1.1), "d2": dict(exposure=1.2, saturation=1.1),
    "d3": dict(exposure=1.1, saturation=1.05), "d4": dict(exposure=1.25, saturation=1.05),
    "d5": dict(exposure=1.1, saturation=1.05), "d6": dict(exposure=1.15, saturation=1.1),
    "d7": dict(exposure=1.0, saturation=1.0, vignette=0.2), "d8": dict(exposure=1.2, saturation=1.1),
    "final": dict(exposure=1.25, saturation=1.1),
}


def paint(name):
    fn, seed = SCENES[name]
    c = Canvas(W, H, seed)
    fn(c)
    return c.finish(**FINISH.get(name, {}))
