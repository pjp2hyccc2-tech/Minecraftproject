#!/usr/bin/env python3
"""A perspective preview of the canal gimmicks as the game draws them: a street between two house fronts with
strings of bunting (pennants, flags, laundry, lanterns at night), hanging shop signs and floating lanterns.

    python3 tools/art/canal_gimmicks_scene.py      # writes tools/art/canal_gimmicks_scene.png (run canal_gimmicks.py first)

The geometry follows the Java renderers line by line (catenary cord, swing about the chord, cloths bent in two rows,
ribbed lantern bodies, the sign board under its bracket arm) at a fixed moment, with the real sprites, so a mistake in
the maths shows here. Lighting is a flat approximation; the game (and shaders) will differ.
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm", "textures")
sys.path.insert(0, os.path.join(ROOT, "tools"))
from capital.districts import _gimmick_kit as GK  # noqa: E402

DYE = {"white": 0xF4EFE2, "orange": 0xEC842B, "magenta": 0xC24BB4, "light_blue": 0x63B5E8, "yellow": 0xF6CD3B,
       "lime": 0x8DC63F, "pink": 0xF092B6, "gray": 0x6F6F72, "light_gray": 0xB7B4AD, "cyan": 0x2C9EA8,
       "purple": 0x7D4CB9, "blue": 0x3559C4, "brown": 0x8B5B35, "green": 0x4F8D3B, "red": 0xD43A30,
       "black": 0x2B2B31}


def sprite(*parts):
    return Image.open(os.path.join(TEX, *parts)).convert("RGBA")


# ====================================================================================== the cord (BuntingLine)
class Line:
    def __init__(self, dx, dy, dz, sag):
        self.d = (dx, dy, dz)
        span = math.hypot(dx, dz)
        self.h = (dx / span, dz / span)
        chord = math.sqrt(dx * dx + dy * dy + dz * dz)
        self.u = (dx / chord, dy / chord, dz / chord)
        pts, used = GK.bunting_curve(dx, dy, dz, sag, 0.02)
        self.sag = used
        self.pts = pts
        self.cum = [0.0]
        for a, b in zip(pts, pts[1:]):
            self.cum.append(self.cum[-1] + math.dist(a, b))
        self.length = self.cum[-1]

    def at(self, s):
        s = max(0.0, min(self.length, s))
        i = int(np.searchsorted(self.cum, s))
        i = max(1, min(len(self.pts) - 1, i))
        a, b = self.pts[i - 1], self.pts[i]
        f = (s - self.cum[i - 1]) / max(1e-9, self.cum[i] - self.cum[i - 1])
        p = tuple(a[k] + (b[k] - a[k]) * f for k in range(3))
        t = tuple(b[k] - a[k] for k in range(3))
        n = math.sqrt(sum(v * v for v in t)) or 1.0
        return p, tuple(v / n for v in t)

    def swing(self, v, cos, sin):
        ux, uy, uz = self.u
        x, y, z = v
        dot = ux * x + uy * y + uz * z
        cx, cy, cz = uy * z - uz * y, uz * x - ux * z, ux * y - uy * x
        k = dot * (1 - cos)
        return (x * cos + cx * sin + ux * k, y * cos + cy * sin + uy * k, z * cos + cz * sin + uz * k)

    def cross_wind(self):
        return self.u[2] * 1.0 - self.u[0] * 0.0


def pendulum_omega(sag):
    return math.sqrt(9.81 / (0.3 + 0.67 * sag))


def cloth_angle(flutter, s, wind, cross, give):
    w = max(0.0, min(1.6, wind))
    return 0.62 * w * cross * give + (0.1 + 0.22 * w) * give * (
        math.sin(flutter - 1.9 * s) + 0.35 * math.sin(2.3 * flutter - 1.7 * 1.9 * s + 0.9))


# ====================================================================================== a tiny quad rasteriser
class Scene:
    def __init__(self, w, h, eye, look, fov=70.0, night=False):
        self.w, self.h = w, h
        self.eye = np.array(eye, float)
        f = np.array(look, float) - self.eye
        self.f = f / np.linalg.norm(f)
        self.r = np.cross(self.f, [0, 1, 0])
        self.r /= np.linalg.norm(self.r)
        self.up = np.cross(self.r, self.f)
        self.k = (h / 2) / math.tan(math.radians(fov) / 2)
        self.quads = []
        self.night = night

    def project(self, p):
        v = np.array(p, float) - self.eye
        z = v @ self.f
        if z < 0.05:
            return None
        return (self.w / 2 + (v @ self.r) / z * self.k, self.h / 2 - (v @ self.up) / z * self.k, z)

    def quad(self, pts, img, tint=(1, 1, 1), shade=1.0, uv=(0, 0, 1, 1), additive=False):
        self.quads.append((pts, img, tint, shade, uv, additive))

    def render(self, background):
        out = background.copy()
        items = []
        for q in self.quads:
            pr = [self.project(p) for p in q[0]]
            if any(p is None for p in pr):
                continue
            items.append((max(p[2] for p in pr), pr, q))
        items.sort(key=lambda it: -it[0])
        for _, pr, (pts, img, tint, shade, uv, additive) in items:
            u0, v0, u1, v1 = uv
            sw, sh = img.size
            src = img.crop((int(u0 * sw), int(v0 * sh), max(int(u0 * sw) + 1, int(u1 * sw)),
                            max(int(v0 * sh) + 1, int(v1 * sh))))
            a = np.asarray(src).astype(float)
            a[..., :3] *= np.array(tint, float)[None, None, :] * shade
            src = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")
            xs = [p[0] for p in pr]
            ys = [p[1] for p in pr]
            x0, y0 = int(math.floor(min(xs))), int(math.floor(min(ys)))
            x1, y1 = int(math.ceil(max(xs))) + 1, int(math.ceil(max(ys))) + 1
            if x1 <= 0 or y1 <= 0 or x0 >= self.w or y0 >= self.h or x1 - x0 > 4000 or y1 - y0 > 4000:
                continue
            dst = [(p[0] - x0, p[1] - y0) for p in pr]
            # destination corners (TL, BL, BR, TR as emitted) -> source corners
            sw2, sh2 = src.size
            srcc = [(0, 0), (0, sh2), (sw2, sh2), (sw2, 0)]
            coeffs = perspective(dst, srcc)
            if coeffs is None:
                continue
            warped = src.transform((x1 - x0, y1 - y0), Image.PERSPECTIVE, coeffs, Image.NEAREST)
            mask = Image.new("L", warped.size, 0)
            ImageDraw.Draw(mask).polygon(dst, fill=255)
            alpha = np.minimum(np.asarray(warped)[..., 3], np.asarray(mask))
            if additive:
                base = np.asarray(out.crop((x0, y0, x1, y1))).astype(float)
                add = np.asarray(warped).astype(float)[..., :3] * (np.asarray(mask)[..., None] / 255.0)
                base[..., :3] = np.minimum(255, base[..., :3] + add * (np.asarray(warped)[..., 3:4] / 255.0))
                out.paste(Image.fromarray(base.astype(np.uint8), "RGBA"), (x0, y0))
            else:
                warped.putalpha(Image.fromarray((alpha > 25).astype(np.uint8) * 255))
                out.alpha_composite(warped, (x0, y0)) if x0 >= 0 and y0 >= 0 else out.paste(warped, (x0, y0), warped)
        return out


def perspective(dst, src):
    """Coefficients for Image.PERSPECTIVE mapping output pixels (dst quad) to input pixels (src quad)."""
    m, b = [], []
    for (x, y), (u, v) in zip(dst, src):
        m.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        b.append(u)
        m.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        b.append(v)
    try:
        return np.linalg.solve(np.array(m, float), np.array(b, float)).tolist()
    except np.linalg.LinAlgError:
        return None


def rgb(c):
    return ((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0


# ====================================================================================== the pieces (BuntingRenderer)
def string(scene, anchor, to, style, colors, sag, seconds=3.7, wind=0.8, seed=1):
    line = Line(*to, sag)
    cross = line.cross_wind()
    w = max(0.0, min(1.6, wind))
    amp = (0.04 + 0.09 * w) * (0.35 + 0.65 * abs(cross))
    om = pendulum_omega(line.sag)
    theta = 0.42 * w * cross + amp * (math.sin(om * seconds + seed) + 0.25 * math.sin(2.7 * om * seconds + 2 * seed))
    cs, sn = math.cos(theta), math.sin(theta)
    flutter = seconds * (3.2 + 4.5 * w) + seed
    ax, ay, az = (anchor[0] + 0.5, anchor[1] + 0.5, anchor[2] + 0.5)
    cord = sprite("block", "bunting", "cord.png")
    # the cord as short ribbons
    n = 40
    prev = None
    for k in range(n + 1):
        p, t = line.at(line.length * k / n)
        p = line.swing(p, cs, sn)
        q = (ax + p[0], ay + p[1], az + p[2])
        if prev is not None:
            side = (0, 0.025, 0)
            scene.quad([(prev[0] + side[0], prev[1] + side[1], prev[2]), (prev[0], prev[1] - 0.025, prev[2]),
                        (q[0], q[1] - 0.025, q[2]), (q[0], q[1] + 0.025, q[2])], cord, shade=0.95)
        prev = q
    spacing, width, height, kind = {"pennant": (0.56, 0.46, 0.58, "pennant"), "flags": (0.52, 0.4, 0.36, "flag"),
                                    "lanterns": (1.3, 0.32, 0.52, "lantern")}.get(style, (0.0, 0, 0, "laundry"))
    usable = line.length - 0.64
    pieces = []
    if style == "laundry":
        sizes = [("shirt", 0.62, 0.6), ("towel", 0.48, 0.62), ("socks", 0.34, 0.36), ("trousers", 0.42, 0.76),
                 ("dress", 0.5, 0.72)]
        s = 0.37
        i = 0
        while True:
            name, wd, ht = sizes[(i * 3 + seed) % len(sizes)]
            if s + wd > 0.32 + usable:
                break
            pieces.append((s + wd / 2, wd, ht, name))
            s += wd + 0.12 + 0.07 * ((i * 7) % 3)
            i += 1
    else:
        cnt = int((usable - width) // spacing) + 1
        start = 0.32 + (usable - (cnt - 1) * spacing) / 2
        pieces = [(start + i * spacing, width, height, kind) for i in range(cnt)]
    for i, (s, wd, ht, name) in enumerate(pieces):
        p, t = line.at(s)
        p = line.swing(p, cs, sn)
        t = line.swing(t, cs, sn)
        px, py, pz = ax + p[0], ay + p[1], az + p[2]
        tx, ty, tz = t
        dx, dy, dz = ty * tx, ty * ty - 1.0, ty * tz
        dl = math.sqrt(dx * dx + dy * dy + dz * dz)
        dx, dy, dz = dx / dl, dy / dl, dz / dl
        bx, by, bz = ty * dz - tz * dy, tz * dx - tx * dz, tx * dy - ty * dx
        crossl = bx
        col = rgb(DYE[colors[i % len(colors)]])
        if name == "lantern":
            lantern(scene, (px, py, pz), (tx, ty, tz), (dx, dy, dz), (bx, by, bz), s, crossl, flutter, wind, col)
            continue
        give = 1.0 if name in ("pennant", "flag") else 0.55
        psi = cloth_angle(flutter, s, wind, crossl, give)
        tip = psi * (1.35 if give == 1.0 else 1.2)
        d1 = tuple(dv * math.cos(psi) + bv * math.sin(psi) for dv, bv in zip((dx, dy, dz), (bx, by, bz)))
        d2 = tuple(dv * math.cos(tip) + bv * math.sin(tip) for dv, bv in zip((dx, dy, dz), (bx, by, bz)))
        hw = wd / 2
        L = (px - tx * hw, py - ty * hw, pz - tz * hw)
        R = (px + tx * hw, py + ty * hw, pz + tz * hw)
        ML = tuple(L[k] + d1[k] * ht / 2 for k in range(3))
        MR = tuple(R[k] + d1[k] * ht / 2 for k in range(3))
        BL = tuple(ML[k] + d2[k] * ht / 2 for k in range(3))
        BR = tuple(MR[k] + d2[k] * ht / 2 for k in range(3))
        img = sprite("block", "bunting", f"{name}.png")
        scene.quad([L, ML, MR, R], img, col, 1.0, (0, 0, 1, 0.5))
        scene.quad([ML, BL, BR, MR], img, col, 0.88, (0, 0.5, 1, 1))


def lantern(scene, p, t, d, b, s, cross, flutter, wind, col):
    psi = cloth_angle(flutter * 0.6, s, wind, cross, 0.45)
    a = tuple(dv * math.cos(psi) + bv * math.sin(psi) for dv, bv in zip(d, b))
    e = (a[1] * t[2] - a[2] * t[1], a[2] * t[0] - a[0] * t[2], a[0] * t[1] - a[1] * t[0])
    el = math.sqrt(sum(v * v for v in e))
    e = tuple(v / el for v in e)
    top = tuple(p[k] + a[k] * 0.08 for k in range(3))
    c = tuple(top[k] + a[k] * 0.2 for k in range(3))
    bot = tuple(top[k] + a[k] * 0.4 for k in range(3))
    img = sprite("block", "bunting", "lantern.png")
    glow = 1.0 if scene.night else 0.0
    shade = 1.25 if scene.night else 1.0
    for j in range(6):
        a0, a1 = 2 * math.pi * j / 6, 2 * math.pi * (j + 1) / 6
        ring = lambda cen, r, ang: tuple(cen[k] + (t[k] * math.cos(ang) + e[k] * math.sin(ang)) * r for k in range(3))  # noqa: E731
        scene.quad([ring(top, 0.1, a0), ring(c, 0.165, a0), ring(c, 0.165, a1), ring(top, 0.1, a1)], img, col, shade,
                   (j / 6, 0, (j + 1) / 6, 0.5))
        scene.quad([ring(c, 0.165, a0), ring(bot, 0.1, a0), ring(bot, 0.1, a1), ring(c, 0.165, a1)], img, col, shade,
                   (j / 6, 0.5, (j + 1) / 6, 1))
    if glow:
        halo = sprite("entity", "bunting", "glow.png")
        sz = 0.45
        scene.quad([(c[0] - sz, c[1] + sz, c[2]), (c[0] - sz, c[1] - sz, c[2]), (c[0] + sz, c[1] - sz, c[2]),
                    (c[0] + sz, c[1] + sz, c[2])], halo, (col[0] * 0.55 + 0.12, col[1] * 0.5 + 0.08, col[2] * 0.4),
                   1.0, additive=True)


def shop_sign_wall(scene, cell, facing, icon, seconds=3.7, wind=0.8):
    """The board under its arm (ShopSignRenderer, wall attach); the bracket drawn as dark bars."""
    fx, fz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    mx, mz = -fz, fx
    ex, ez = mz, -mx
    push = mx
    w = max(0.0, min(1.6, wind))
    ang = 0.1 * w * push + (0.025 + 0.055 * w) * (0.35 + 0.65 * abs(push)) * math.sin(2.6 * seconds + 0.4)
    dx, dy, dz = mx * math.sin(ang), -math.cos(ang), mz * math.sin(ang)
    X, Y, Z = cell
    piv = (X + 0.5 + ex / 16, Y + 12.5 / 16, Z + 0.5 + ez / 16)
    top = tuple(piv[k] + (dx, dy, dz)[k] * 2 / 16 for k in range(3))
    hw, hh = 0.5, 0.75
    TL = (top[0] - ex * hw, top[1], top[2] - ez * hw)
    TR = (top[0] + ex * hw, top[1], top[2] + ez * hw)
    BL = (TL[0] + dx * hh, TL[1] + dy * hh, TL[2] + dz * hh)
    BR = (TR[0] + dx * hh, TR[1] + dy * hh, TR[2] + dz * hh)
    board = sprite("block", "shop_sign", f"{icon}.png")
    scene.quad([TL, BL, BR, TR], board)
    iron = sprite("block", "shop_sign", "bracket.png")
    # the arm from the wall to the tip
    wall = (X + 0.5 - fx * 0.5, Y + 12.5 / 16, Z + 0.5 - fz * 0.5)
    tip = (X + 0.5 + fx * 0.6, Y + 12.5 / 16, Z + 0.5 + fz * 0.6)
    t = 0.035
    scene.quad([(wall[0], wall[1] + t, wall[2]), (wall[0], wall[1] - t, wall[2]), (tip[0], tip[1] - t, tip[2]),
                (tip[0], tip[1] + t, tip[2])], iron, shade=0.7)
    brace0 = (X + 0.5 - fx * 0.47, Y + 15.5 / 16, Z + 0.5 - fz * 0.47)
    brace1 = (X + 0.5 + fx * 0.0, Y + 12.4 / 16, Z + 0.5 + fz * 0.0)
    scene.quad([(brace0[0], brace0[1] + t, brace0[2]), (brace0[0], brace0[1] - t, brace0[2]),
                (brace1[0], brace1[1] - t, brace1[2]), (brace1[0], brace1[1] + t, brace1[2])], iron, shade=0.7)


def wall(scene, x0, x1, z, y1, img, step=1.0):
    e = 0.04                                     # overlap the tiles a little so no seams show
    for x in np.arange(x0, x1, step):
        for y in np.arange(0, y1, step):
            scene.quad([(x - e, y + step + e, z), (x - e, y - e, z), (x + step + e, y - e, z),
                        (x + step + e, y + step + e, z)], img, shade=0.82)


def floating_lantern(scene, cell, seconds, seed, tint=(1.0, 0.9, 0.62)):
    """FloatingLanternRenderer at a moment: float, four paper sides, halo and light on the water (night)."""
    p = (seed * 0.61803) % 1 * 6.283
    bob = 0.025 * math.sin(1.6 * seconds + p) + 0.01 * math.sin(2.9 * seconds + 2 * p)
    a = seconds * 0.21 + p
    dx, dz = 0.12 * math.cos(a), 0.1 * math.sin(a)
    yaw = p + seconds * 0.12
    rx, rz = math.cos(yaw), math.sin(yaw)
    fx, fz = -rz, rx
    X, Y, Z = cell
    base = (X + 0.5 + dx, Y - 0.11 + bob, Z + 0.5 + dz)
    raft = sprite("block", "floating_lantern", "raft.png")
    paper = sprite("block", "floating_lantern", "paper.png")
    h = 0.17
    top = base[1] + 0.06
    scene.quad([(base[0] - rx * h - fx * h, top, base[2] - rz * h - fz * h),
                (base[0] - rx * h + fx * h, top, base[2] - rz * h + fz * h),
                (base[0] + rx * h + fx * h, top, base[2] + rz * h + fz * h),
                (base[0] + rx * h - fx * h, top, base[2] + rz * h - fz * h)], raft, shade=0.7)
    c = (base[0], base[1] + 0.19, base[2])
    q = 0.115
    for (ax, az, nx, nz) in ((fx, fz, rx, rz), (fx, fz, -rx, -rz), (rx, rz, fx, fz), (rx, rz, -fx, -fz)):
        cx, cz = c[0] + nx * q, c[2] + nz * q
        scene.quad([(cx - ax * q, c[1] + 0.13, cz - az * q), (cx - ax * q, c[1] - 0.13, cz - az * q),
                    (cx + ax * q, c[1] - 0.13, cz + az * q), (cx + ax * q, c[1] + 0.13, cz + az * q)], paper,
                   tint, 1.3 if scene.night else 1.0)
    if scene.night:
        glow = sprite("entity", "bunting", "glow.png")
        s = 0.62
        scene.quad([(c[0], c[1] + s, c[2] - s), (c[0], c[1] - s, c[2] - s), (c[0], c[1] - s, c[2] + s),
                    (c[0], c[1] + s, c[2] + s)], glow, (tint[0] * 0.42, tint[1] * 0.3, tint[2] * 0.16), 1.0,
                   additive=True)
        w = 0.55
        y = Y - 0.1
        scene.quad([(c[0] - w, y, c[2] - w), (c[0] - w, y, c[2] + w), (c[0] + w, y, c[2] + w),
                    (c[0] + w, y, c[2] - w)], glow, (tint[0] * 0.3, tint[1] * 0.22, tint[2] * 0.12), 1.0,
                   additive=True)


def main():
    plaster = Image.open(os.path.join(TEX, "block", "white_plaster.png")).convert("RGBA")
    W, H = 1280, 720
    out = Image.new("RGBA", (W * 2, H), (0, 0, 0, 255))
    for k, night in enumerate((False, True)):
        sc = Scene(W, H, eye=(-3.5, 2.2, 5.0), look=(10.0, 4.6, 5.0), fov=72, night=night)
        wall(sc, -2, 22, 0.0, 9, plaster, 2.0)
        wall(sc, -2, 22, 10.0, 9, plaster, 2.0)
        if not night:
            string(sc, (3, 6, 0), (0, 0, 9), "pennant", ("red", "yellow", "blue", "white", "green"), 1.0, seed=1)
            string(sc, (8, 6, 9), (0, 0, -9), "flags", ("blue", "white", "red"), 0.9, seed=2)
            string(sc, (13, 5, 0), (0, 1, 9), "laundry", ("white", "light_blue", "white", "pink", "blue"), 0.8, seed=3)
        else:
            string(sc, (3, 6, 0), (0, 0, 9), "lanterns", ("orange", "red", "yellow", "pink"), 1.0, seed=1)
            string(sc, (8, 6, 9), (0, 0, -9), "lanterns", ("yellow", "orange", "white"), 1.2, seed=4)
        shop_sign_wall(sc, (5, 3, 1), "south", "sword")
        shop_sign_wall(sc, (10, 3, 9), "north", "potion")
        shop_sign_wall(sc, (16, 3, 1), "south", "mug")
        sky = (150, 186, 220, 255) if not night else (14, 18, 38, 255)
        bg = Image.new("RGBA", (W, H), sky)
        d = ImageDraw.Draw(bg)
        ground = (120, 118, 112, 255) if not night else (28, 28, 34, 255)
        d.rectangle([0, H * 0.62, W, H], fill=ground)
        # the canal down the middle of the street
        canal = [sc.project(p) for p in ((-1.0, -0.12, 3.0), (-1.0, -0.12, 7.0), (40.0, -0.12, 7.0), (40.0, -0.12, 3.0))]
        d.polygon([(p[0], p[1]) for p in canal], fill=(46, 92, 128, 255) if not night else (10, 22, 40, 255))
        for i, (x, z) in enumerate(((2, 4), (5, 5), (7, 4), (10, 6), (12, 4), (15, 5), (19, 6), (23, 4))):
            floating_lantern(sc, (x, 0, z), 3.7, i + 1, ((1.0, 0.9, 0.62), (1.0, 0.82, 0.72), (0.99, 0.78, 0.82))[i % 3])
        img = sc.render(bg)
        if night:
            a = np.asarray(img).astype(float)
            a[..., :3] *= 0.55
            img = Image.fromarray(a.astype(np.uint8), "RGBA")
            sc2 = Scene(W, H, eye=(-3.5, 2.2, 5.0), look=(10.0, 4.6, 5.0), fov=72, night=True)
            string(sc2, (3, 6, 0), (0, 0, 9), "lanterns", ("orange", "red", "yellow", "pink"), 1.0, seed=1)
            string(sc2, (8, 6, 9), (0, 0, -9), "lanterns", ("yellow", "orange", "white"), 1.2, seed=4)
            for i, (x, z) in enumerate(((2, 4), (5, 5), (7, 4), (10, 6), (12, 4), (15, 5), (19, 6), (23, 4))):
                floating_lantern(sc2, (x, 0, z), 3.7, i + 1,
                                 ((1.0, 0.9, 0.62), (1.0, 0.82, 0.72), (0.99, 0.78, 0.82))[i % 3])
            sc2.quads = [q for q in sc2.quads if q[1].size[0] in (16, 32)]
            img = sc2.render(img)
        out.paste(img, (k * W, 0))
    out.save(os.path.join(HERE, "canal_gimmicks_scene.png"))
    print("canal_gimmicks_scene.png")


if __name__ == "__main__":
    main()
