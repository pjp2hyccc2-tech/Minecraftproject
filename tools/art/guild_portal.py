#!/usr/bin/env python3
"""Art of the guild houses (docs/modules/guildhouse.md): the guild gates' portal surface, the guild board and the
catalogue's house pictures.

    python3 tools/art/guild_portal.py            # textures, models, blockstates, tools/art/guild_portal_preview.png

* ``guild_portal_base`` (32 frames): a slow swirl of light rising through the gate, drawn in grey so the block's
  first tint layer gives it the theme's colour; ``guild_portal_glint`` (32 frames): sparkles drifting up and
  twinkling, tinted with the theme's accent colour (second layer).
* ``guild_board``: a framed cork board three blocks wide and two high (96 x 64 px) with brass corner plates, a carved
  header and pins; the client writes the guild's notes on it.
* ``gui/guildhouse/<design>.png`` (128 x 72): the catalogue's pictures of the houses. ``tools/estates/guild/build.py``
  renders the real ones from the builds; this script only writes a stand-in where none exists yet.

Everything is procedural (numpy noise, supersampled shapes); no external images or fonts.
"""
from __future__ import annotations

import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eternalrealm")
NS = "eternalrealm"
FRAMES = 32


def _save(img: Image.Image, rel: str):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    return path


def _json(rel: str, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _hash(x, y, seed):
    h = (np.asarray(x, np.int64) * 374761393 + np.asarray(y, np.int64) * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def _tile_noise(size, cells, seed, t=0.0):
    """Smooth periodic value noise on a size x size tile (period = size), sampled at time t (periodic in t)."""
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    u = xs / size * cells
    v = ys / size * cells
    x0 = np.floor(u).astype(np.int64)
    y0 = np.floor(v).astype(np.int64)
    fx = u - x0
    fy = v - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)

    def lattice(ix, iy):
        a = _hash(ix % cells, iy % cells, seed) * 2 * math.pi
        return 0.5 + 0.5 * np.sin(a + t * 2 * math.pi)

    n00, n10 = lattice(x0, y0), lattice(x0 + 1, y0)
    n01, n11 = lattice(x0, y0 + 1), lattice(x0 + 1, y0 + 1)
    return (n00 * (1 - fx) + n10 * fx) * (1 - fy) + (n01 * (1 - fx) + n11 * fx) * fy


# =========================================================================== the portal
def portal_base():
    """Grey swirl rising through the gate: bright veins of light over a softly breathing glow (tileable)."""
    size = 16
    frames = []
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    for f in range(FRAMES):
        t = f / FRAMES
        # rising flow: the noise field scrolls upward, two octaves turning against each other
        n1 = _tile_noise(size, 4, 11, t)
        n2 = _tile_noise(size, 8, 23, -t * 2)
        rise = (ys / size + t) % 1.0
        waves = 0.5 + 0.5 * np.sin(2 * math.pi * (xs / size * 2 + rise * 2 + n1 * 0.9))
        veins = np.clip(1.0 - np.abs(np.sin(2 * math.pi * (xs / size + n2 * 0.7 - t))) * 3.2, 0, 1)
        v = 0.58 + 0.22 * waves + 0.12 * n2 + 0.22 * veins
        v = np.clip(v, 0, 1)
        g = (150 + 105 * v).astype(np.uint8)
        a = (150 + 70 * v).astype(np.uint8)
        rgba = np.stack([g, g, g, a], axis=-1)
        frames.append(rgba)
    strip = np.concatenate(frames, axis=0)
    return Image.fromarray(strip, "RGBA")


def portal_glint():
    """Sparkles drifting upward, each twinkling once per loop (transparent elsewhere)."""
    size = 16
    sparks = []
    for i in range(14):
        sparks.append((_hash(i, 1, 71), _hash(i, 2, 71), _hash(i, 3, 71), 0.5 + _hash(i, 4, 71)))
    frames = []
    for f in range(FRAMES):
        t = f / FRAMES
        img = np.zeros((size, size, 4), np.float64)
        for (sx, sy, phase, speed) in sparks:
            x = sx * size
            y = (sy - t * (1 if speed < 1 else 2)) % 1.0 * size
            life = 0.5 + 0.5 * math.cos(2 * math.pi * (t * 2 + phase))
            if life < 0.15:
                continue
            for dx in range(-2, 3):
                for dy in range(-2, 3):
                    px, py = int(x + dx) % size, int(y + dy) % size
                    d = math.hypot(dx, dy)
                    w = max(0.0, 1.0 - d / 2.2) * life
                    if dx == 0 or dy == 0:
                        w *= 1.3
                    img[py, px, :3] = np.maximum(img[py, px, :3], 255 * min(1.0, w + 0.2))
                    img[py, px, 3] = max(img[py, px, 3], 255 * min(1.0, w))
        frames.append(np.clip(img, 0, 255).astype(np.uint8))
    return Image.fromarray(np.concatenate(frames, axis=0), "RGBA")


def portal_assets():
    _save(portal_base(), "textures/block/guild_portal_base.png")
    _json("textures/block/guild_portal_base.png.mcmeta", {"animation": {"frametime": 3, "interpolate": True}})
    _save(portal_glint(), "textures/block/guild_portal_glint.png")
    _json("textures/block/guild_portal_glint.png.mcmeta", {"animation": {"frametime": 2, "interpolate": True}})
    light = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    _json("models/block/guild_portal.json", {
        "parent": "block/block",
        "render_type": "minecraft:translucent",
        "ambientocclusion": False,
        "textures": {"particle": f"{NS}:block/guild_portal_base", "base": f"{NS}:block/guild_portal_base",
                     "glint": f"{NS}:block/guild_portal_glint"},
        "elements": [
            {"from": [0, 0, 6.5], "to": [16, 16, 9.5], "shade": False, "forge_data": light,
             "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#base", "tintindex": 0},
                       "south": {"uv": [16, 0, 0, 16], "texture": "#base", "tintindex": 0}}},
            {"from": [0, 0, 6.2], "to": [16, 16, 9.8], "shade": False, "forge_data": light,
             "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#glint", "tintindex": 1},
                       "south": {"uv": [16, 0, 0, 16], "texture": "#glint", "tintindex": 1}}},
        ],
    })
    _json("blockstates/guild_portal.json", {"variants": {
        "axis=x": {"model": f"{NS}:block/guild_portal"},
        "axis=z": {"model": f"{NS}:block/guild_portal", "y": 90},
    }})


# =========================================================================== the board
def board_face():
    """96 x 64: dark carved frame, cork field with grain and speckles, brass corner plates, a header plaque, pins."""
    W, H = 96, 64
    ss = 4
    img = Image.new("RGBA", (W * ss, H * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # cork
    ys, xs = np.mgrid[0:H, 0:W]
    n = (_hash(xs, ys, 5) * 0.55 + _hash(xs // 2, ys // 2, 9) * 0.45)
    cork = np.stack([150 + 50 * n, 104 + 40 * n, 62 + 28 * n, np.full(n.shape, 255)], -1).astype(np.uint8)
    speck = _hash(xs, ys, 13) > 0.93
    cork[speck] = (98, 66, 38, 255)
    light = _hash(xs, ys, 17) > 0.96
    cork[light] = (206, 160, 104, 255)
    base = Image.fromarray(cork, "RGBA").resize((W * ss, H * ss), Image.NEAREST)
    img.alpha_composite(base)
    # frame
    fw = 5 * ss
    frame = (58, 36, 20, 255)
    hi = (104, 68, 38, 255)
    lo = (36, 22, 12, 255)
    d.rectangle([0, 0, W * ss - 1, fw - 1], fill=frame)
    d.rectangle([0, H * ss - fw, W * ss - 1, H * ss - 1], fill=frame)
    d.rectangle([0, 0, fw - 1, H * ss - 1], fill=frame)
    d.rectangle([W * ss - fw, 0, W * ss - 1, H * ss - 1], fill=frame)
    d.line([ss, ss, W * ss - ss, ss], fill=hi, width=ss)
    d.line([ss, ss, ss, H * ss - ss], fill=hi, width=ss)
    d.line([fw, fw - ss, W * ss - fw, fw - ss], fill=lo, width=ss)
    d.line([fw - ss, fw, fw - ss, H * ss - fw], fill=lo, width=ss)
    # wood grain on the frame
    for i in range(40):
        y = int(_hash(i, 3, 41) * (fw - 2 * ss)) + ss
        x0 = int(_hash(i, 5, 41) * W * ss)
        d.line([x0, y, x0 + 18 * ss, y], fill=(48, 30, 16, 255), width=1)
    # brass corner plates
    brass = (214, 168, 74, 255)
    brass_dark = (150, 108, 40, 255)
    for (cx, cy) in ((0, 0), (W * ss, 0), (0, H * ss), (W * ss, H * ss)):
        r = 8 * ss
        d.polygon([(cx, cy), (cx + (r if cx == 0 else -r), cy), (cx, cy + (r if cy == 0 else -r))], fill=brass,
                  outline=brass_dark)
        d.ellipse([cx + (3 if cx == 0 else -5) * ss, cy + (3 if cy == 0 else -5) * ss,
                   cx + (5 if cx == 0 else -3) * ss, cy + (5 if cy == 0 else -3) * ss], fill=brass_dark)
    # carved header plaque for the guild's name
    hx0, hx1, hy0, hy1 = 30 * ss, 66 * ss, 2 * ss, 11 * ss
    d.rounded_rectangle([hx0, hy0, hx1, hy1], radius=2 * ss, fill=(196, 154, 86, 255), outline=brass_dark,
                        width=ss)
    d.rounded_rectangle([hx0 + ss, hy0 + ss, hx1 - ss, hy1 - ss], radius=ss, outline=(232, 196, 120, 255),
                        width=1)
    # pins
    for (px, py, c) in ((14, 18, (200, 40, 40)), (80, 20, (40, 90, 200)), (24, 44, (40, 160, 70)),
                        (70, 48, (220, 180, 40)), (48, 33, (200, 40, 40))):
        d.ellipse([px * ss - 2 * ss, py * ss - 2 * ss, px * ss + 2 * ss, py * ss + 2 * ss], fill=c + (255,),
                  outline=(30, 20, 20, 255))
        d.ellipse([px * ss - ss, py * ss - ss, px * ss, py * ss], fill=(255, 255, 255, 200))
    small = img.resize((W, H), Image.LANCZOS)
    square = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    square.alpha_composite(small, (0, 0))
    # bottom 32 rows: edge wood for the board's sides and back
    edge = np.zeros((32, 96, 4), np.uint8)
    ys2, xs2 = np.mgrid[0:32, 0:96]
    g = _hash(xs2 // 3, ys2, 61)
    edge[..., 0] = 70 + 30 * g
    edge[..., 1] = 44 + 20 * g
    edge[..., 2] = 24 + 12 * g
    edge[..., 3] = 255
    square.alpha_composite(Image.fromarray(edge, "RGBA"), (0, 64))
    return square


def board_assets():
    _save(board_face(), "textures/block/guild_board.png")
    face_uv = [0, 0, 16, 10.6667]
    edge_uv = [0, 10.6667, 16, 16]
    _json("models/block/guild_board.json", {
        "parent": "block/block",
        "textures": {"particle": f"{NS}:block/guild_board", "board": f"{NS}:block/guild_board"},
        "elements": [
            {"from": [-16, 0, 0], "to": [32, 32, 2],
             "faces": {"south": {"uv": face_uv, "texture": "#board"},
                       "north": {"uv": edge_uv, "texture": "#board"},
                       "up": {"uv": [0, 10.6667, 16, 11.2], "texture": "#board"},
                       "down": {"uv": [0, 10.6667, 16, 11.2], "texture": "#board"},
                       "east": {"uv": [0, 10.6667, 1, 16], "texture": "#board"},
                       "west": {"uv": [0, 10.6667, 1, 16], "texture": "#board"}}},
        ],
        "display": {"gui": {"rotation": [0, 180, 0], "translation": [0, -3, 0], "scale": [0.28, 0.28, 0.28]},
                    "fixed": {"rotation": [0, 180, 0], "scale": [0.3, 0.3, 0.3]}},
    })
    _json("blockstates/guild_board.json", {"variants": {
        "facing=south": {"model": f"{NS}:block/guild_board"},
        "facing=west": {"model": f"{NS}:block/guild_board", "y": 90},
        "facing=north": {"model": f"{NS}:block/guild_board", "y": 180},
        "facing=east": {"model": f"{NS}:block/guild_board", "y": 270},
    }})
    _json("models/item/guild_board.json", {"parent": f"{NS}:block/guild_board"})


# =========================================================================== catalogue pictures
def placeholder_thumbnails():
    """A stand-in picture (the theme's colours, a gate silhouette) for designs not rendered yet."""
    path = os.path.join(ROOT, "src", "main", "resources", "data", "eternalrealm", "guildhouse", "designs.json")
    with open(path, encoding="utf-8") as f:
        designs = json.load(f)["designs"]
    for d in designs:
        rel = f"textures/gui/guildhouse/{d['id']}.png"
        if os.path.exists(os.path.join(ASSETS, rel)):
            continue
        base = tuple(int(d["base"][i:i + 2], 16) for i in (1, 3, 5))
        accent = tuple(int(d["accent"][i:i + 2], 16) for i in (1, 3, 5))
        img = Image.new("RGB", (128, 72), (20, 18, 28))
        dr = ImageDraw.Draw(img)
        for y in range(72):
            t = y / 71
            c = tuple(int(base[i] * (1 - t) * 0.6 + 20 * t) for i in range(3))
            dr.line([0, y, 127, y], fill=c)
        dr.rectangle([52, 18, 76, 66], fill=(40, 34, 48))
        dr.ellipse([52, 8, 76, 32], fill=(40, 34, 48))
        dr.rectangle([56, 24, 72, 66], fill=accent)
        dr.ellipse([56, 14, 72, 30], fill=accent)
        img = img.filter(ImageFilter.SMOOTH)
        _save(img, rel)


def preview():
    tiles = []
    base = portal_base()
    glint = portal_glint()
    for color, accent in (((216, 30, 44), (255, 176, 32)), ((140, 224, 76), (244, 244, 154)),
                          ((154, 92, 255), (92, 224, 255))):
        for f in (0, 8, 16, 24):
            b = np.asarray(base.crop((0, f * 16, 16, f * 16 + 16)), np.float64) / 255.0
            g = np.asarray(glint.crop((0, f * 16, 16, f * 16 + 16)), np.float64) / 255.0
            out = np.zeros((16, 16, 3))
            out[:] = (30, 30, 40)
            a = b[..., 3:4]
            out = out * (1 - a) + b[..., :3] * np.array(color) * a
            ga = g[..., 3:4]
            out = out * (1 - ga) + g[..., :3] * np.array(accent) * ga
            tiles.append(Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).resize((64, 64), Image.NEAREST))
    sheet = Image.new("RGB", (64 * 4 + 300, 64 * 3), (16, 16, 20))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % 4) * 64, (i // 4) * 64))
    board = board_face().crop((0, 0, 96, 64)).resize((288, 192), Image.NEAREST)
    sheet.paste(board, (64 * 4 + 6, 0), board)
    sheet.save(os.path.join(HERE, "guild_portal_preview.png"))


def main():
    portal_assets()
    board_assets()
    placeholder_thumbnails()
    preview()
    print("guild portal art written")


if __name__ == "__main__":
    sys.exit(main())
