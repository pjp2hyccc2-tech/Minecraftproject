#!/usr/bin/env python3
"""The paper trail map in the bag (assets/sounan/textures/gui/trail_map.png, 512x512), drawn from the baked terrain:
contour lines, the old trail, the stream, place names — on wet, stained, folded paper with one torn corner."""
import json
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage
from skimage import measure

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src/main/resources/assets/sounan/textures/gui/trail_map.png")
FONT = "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc"
FONT_R = "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc"
S = 1024  # drawn at 2x, downsampled
OFF = 256


def main():
    T = np.load(os.path.join(HERE, "out", "terrain.npz"))
    H = T["H"].astype(float)
    area = T["area"]
    water = T["water"]
    surf = T["surf"]
    pois = json.load(open(os.path.join(ROOT, "src/main/resources/data/sounan/terrain/pois.json"), encoding="utf-8"))
    rng = np.random.default_rng(3)

    # paper
    base = np.zeros((S, S, 3))
    base[...] = (222, 210, 182)
    n = ndimage.gaussian_filter(rng.normal(0, 1, (S, S)), 2) * 6 + ndimage.gaussian_filter(rng.normal(0, 1, (S, S)), 30) * 40
    base += n[..., None] * np.array([1.0, 0.95, 0.8])
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img, "RGBA")
    k = S / 512.0

    def P(x, z):
        return ((x + OFF) * k, (z + OFF) * k)

    # land tint for the corridor, contours
    Hs = ndimage.gaussian_filter(H, 1.2)
    for level in range(80, 236, 4):
        major = level % 20 == 0
        for c in measure.find_contours(Hs, level):
            pts = [(p[1] * k, p[0] * k) for p in c[::2]]
            if len(pts) > 2:
                d.line(pts, fill=(140, 96, 60, 150 if major else 80), width=2 if major else 1)
    # cliffs: hatching where very steep
    gy, gx = np.gradient(H)
    steep = np.hypot(gx, gy) > 3.0
    ys, xs = np.nonzero(steep)
    for y, x in zip(ys[::7], xs[::7]):
        d.line([(x * k, y * k), (x * k + 4, y * k + 4)], fill=(90, 60, 40, 140), width=1)
    # water
    wy, wx = np.nonzero(water > 0)
    for y, x in zip(wy, wx):
        d.rectangle([x * k, y * k, x * k + k, y * k + k], fill=(70, 110, 160, 140))
    # roads
    ry, rx = np.nonzero((surf == 6) | (surf == 5))
    for y, x in zip(ry, rx):
        d.rectangle([x * k, y * k, x * k + k, y * k + k], fill=(90, 80, 70, 110))
    # the trail (red dashes) along the route polyline; faded beyond the shrine (wet ink)
    route = pois["route"]
    for i in range(len(route) - 1):
        (ax, az), (bx, bz) = route[i], route[i + 1]
        L = max(1, int(np.hypot(bx - ax, bz - az)))
        fade = 200 if i < 18 else 90
        for s in range(0, L, 4):
            x0, z0 = ax + (bx - ax) * s / L, az + (bz - az) * s / L
            x1, z1 = ax + (bx - ax) * min(L, s + 2.5) / L, az + (bz - az) * min(L, s + 2.5) / L
            d.line([P(x0, z0), P(x1, z1)], fill=(180, 30, 30, fade), width=4)
    # place names
    fnt = ImageFont.truetype(FONT, 26)
    small = ImageFont.truetype(FONT_R, 20)
    names = {"start": None, "bag": "沢", "rope_anchor": "ロープ場", "dead_end": None, "village": "帰野（廃村）", "shrine": "祠",
             "bridge_n": "吊橋", "hut": "避難小屋", "pylon": "鉄塔", "tunnel_n": "旧隧道", "parking": "登山口"}
    for p in pois["points"]:
        label = names.get(p["id"])
        if not label:
            continue
        x, y = P(p["x"], p["z"])
        d.ellipse([x - 5, y - 5, x + 5, y + 5], outline=(40, 30, 20, 220), width=2)
        d.text((x + 9, y - 16), label, font=fnt if p["id"] in ("village", "parking") else small, fill=(40, 26, 18, 230))
    d.text(P(-150, -60), "杉の森", font=small, fill=(60, 70, 40, 170))
    d.text(P(60, 180), "御山", font=ImageFont.truetype(FONT, 40), fill=(60, 30, 20, 120))
    # title, north arrow, scale
    d.text((40, 30), "帰らず山　登山詳細図", font=ImageFont.truetype(FONT, 40), fill=(30, 20, 14, 235))
    d.text((44, 80), "帰野地区観光協会　昭和五十九年発行", font=small, fill=(50, 36, 26, 200))
    nx, ny = 80, 190
    d.polygon([(nx, ny - 40), (nx - 14, ny + 10), (nx, ny), (nx + 14, ny + 10)], fill=(30, 20, 14, 220))
    d.text((nx - 9, ny + 14), "北", font=small, fill=(30, 20, 14, 220))
    d.line([(60, S - 60), (60 + 100 * k, S - 60)], fill=(30, 20, 14, 220), width=3)
    d.text((60, S - 90), "100m", font=small, fill=(30, 20, 14, 220))
    d.text((S - 330, S - 70), "※夜間の入山は固く禁ず", font=small, fill=(150, 20, 20, 220))
    # water damage stains, a fold line, a torn corner
    img = img.convert("RGBA")
    arr = np.array(img).astype(float)
    for _ in range(9):
        cx, cy, r = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(40, 160)
        yy, xx = np.mgrid[0:S, 0:S]
        dist = np.hypot(xx - cx, yy - cy) / r
        ring = np.exp(-((dist - 1.0) ** 2) / 0.004) * 0.35 + (dist < 1) * 0.08
        arr[..., :3] *= (1 - ring[..., None] * np.array([0.25, 0.32, 0.45]))
    for fx in (S // 2,):
        arr[:, fx - 1:fx + 2, :3] *= 0.82
    arr[S // 2 - 1:S // 2 + 2, :, :3] *= 0.85
    tear = np.zeros((S, S), bool)
    yy, xx = np.mgrid[0:S, 0:S]
    edge = 260 + ndimage.gaussian_filter1d(rng.normal(0, 30, S), 6)
    tear |= (xx + (S - yy)) < edge[np.clip(yy, 0, S - 1)] * 0.0 + 0  # placeholder no-op
    tear |= ((S - xx) + yy) < (edge[yy] * 0.9)
    arr[tear, 3] = 0
    vign = np.hypot(xx - S / 2, yy - S / 2) / (S * 0.72)
    arr[..., :3] *= (1 - np.clip(vign - 0.4, 0, 1)[..., None] * 0.5)
    out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").resize((512, 512), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
