"""Previews of the field set pieces: a perspective view (ray traced with the capital's preview renderer) and a
top-down plan of every piece over its real terrain, a contact sheet of them all and a map of where they stand.

    python3 tools/fields/build.py --previews            # everything (several minutes)
    python3 tools/fields/build.py --previews --only shrine_capital,meadow_windmill
"""
from __future__ import annotations

import math
import os
import time

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from capital import preview as CP

from . import blocks_ext  # noqa: F401
from . import probe
from .world import ground_state

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "previews")


def _font(size):
    for path in ("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                 "/usr/share/fonts/truetype/fonts-japanese-gothic.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                pass
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


class SceneWorld:
    """Terrain (approximated from the probe) + the piece, as a capital-World-like voxel array for the renderer."""

    def __init__(self, P, margin=20):
        self.X0, self.Z0 = P.X0 - margin, P.Z0 - margin
        X1, Z1 = P.X1 + margin, P.Z1 + margin
        self.NX, self.NZ = X1 - self.X0 + 1, Z1 - self.Z0 + 1
        g = probe.grid(self.X0, self.Z0, X1, Z1, 1)
        L = probe.layout()
        lo = int(min(g.top.min(), P.Y0)) - 4
        hi = int(max(g.top.max(), np.where(g.water > -30000, g.water, 0).max(), P.Y1)) + 6
        self.Y0, self.NY = lo, hi - lo + 1
        self.palette = ["eternalrealm:void", "minecraft:air"]
        index = {s: i for i, s in enumerate(self.palette)}

        def idx(s):
            if s not in index:
                index[s] = len(self.palette)
                self.palette.append(s)
            return index[s]

        a = np.ones((self.NX, self.NZ, self.NY), np.uint16)
        ys = np.arange(self.NY)[None, None, :] + self.Y0
        top = g.top[:, :, None]
        surf = g.surface
        names = L["surfaces"]
        biomes = L["biomes"]
        stone = idx("minecraft:stone")
        dirt = idx("minecraft:dirt")
        a[ys <= top] = stone
        sub = (ys <= top) & (ys > top - 4)
        a[sub] = dirt
        tops = np.zeros(surf.shape, np.uint16)
        for si, sname in enumerate(names):
            m = surf == si
            if not m.any():
                continue
            tops[m] = idx(ground_state(sname))
        # biome tint for the end forest and snow
        for bi, bname in enumerate(biomes):
            m = (g.biome == bi) & ((surf == names.index("grass")) | (surf == names.index("forest_floor")))
            if not m.any():
                continue
            if bname.startswith("abyss"):
                tops[m] = idx("minecraft:coarse_dirt")
            elif bname in ("nordia_snowfield", "nordia_ice_spikes", "nordia_crystal_hollow"):
                tops[m] = idx("minecraft:snow_block")
            elif bname in ("elder_forest", "elder_old_growth", "elder_mushroom_glade"):
                tops[m & (np.indices(m.shape).sum(0) % 7 == 0)] = idx("minecraft:podzol[snowy=false]")
        topmask = ys == top
        a[topmask] = np.broadcast_to(tops[:, :, None], a.shape)[topmask]
        water = g.water[:, :, None]
        a[(ys > top) & (ys <= water)] = idx("minecraft:water[level=0]")
        # the piece
        ox, oz, oy = P.X0 - self.X0, P.Z0 - self.Z0, P.Y0 - self.Y0
        remap = np.array([idx(s) if i > 0 else 0 for i, s in enumerate(P.palette)], np.uint16)
        sub = a[ox:ox + P.NX, oz:oz + P.NZ, oy:oy + P.NY]
        m = P.a > 0
        sub[m] = remap[P.a[m]]
        self.a = a
        self.top = g.top


def _patch(sw):
    CP._font = _font
    CP.NX, CP.NY, CP.NZ = sw.NX, sw.NY, sw.NZ
    CP.X0, CP.Y0, CP.Z0 = sw.X0, sw.Y0, sw.Z0
    CP.OUT_DIR = OUT_DIR


def default_view(P):
    """A camera looking at the piece from the south-east, high enough to see it whole."""
    cx, cz = (P.X0 + P.X1) / 2.0, (P.Z0 + P.Z1) / 2.0
    xs, zs = np.nonzero((P.a > 1).any(axis=2))
    if xs.size:
        cx, cz = (xs.min() + xs.max()) / 2.0 + P.X0, (zs.min() + zs.max()) / 2.0 + P.Z0
        span = max(xs.max() - xs.min(), zs.max() - zs.min(), 12)
    else:
        span = max(P.NX, P.NZ)
    ys = np.nonzero((P.a > 1).any(axis=(0, 1)))[0]
    cy = (ys.min() + ys.max()) / 2.0 + P.Y0 if ys.size else P.Y0 + P.NY / 2
    dist = span * 1.05 + 14
    az = math.radians(40)
    el = math.radians(34)
    pos = (cx + dist * math.cos(el) * math.sin(az), cy + dist * math.sin(el), cz + dist * math.cos(el) * math.cos(az))
    return dict(name=P.id, pos=pos, look=(cx, cy - span * 0.06, cz), fov=50.0, sun=(42.0, -125.0), label=None)


def render_piece(P, size=(960, 600), views=True, margin=None):
    os.makedirs(OUT_DIR, exist_ok=True)
    t0 = time.time()
    span = max(P.NX, P.NZ)
    sw = SceneWorld(P, margin=margin if margin is not None else int(min(48, max(16, span * 0.35))))
    _patch(sw)
    scene = CP.Scene(sw)
    out = []
    cams = list(P.views) if (views and P.views) else []
    if not cams:
        cams = [default_view(P)]
    for k, cam in enumerate(cams):
        name = cam.get("name") or (P.id if k == 0 else f"{P.id}_{k}")
        label = cam.get("label") or f"{P.id}  —  {P.name}"
        sun = cam.get("sun") or (42.0, -125.0)
        sun = CP.sun_dir(*sun) if isinstance(sun, tuple) else sun
        CP.render(scene, name, cam["pos"], cam["look"], size=size, fov=cam.get("fov", 50.0), sun=sun, fog=1600,
                  label=label)
        out.append(os.path.join(OUT_DIR, f"{name}.png"))
    out.append(topdown(P, sw))
    print(f"render {P.id}: {time.time() - t0:.1f}s")
    return out


def topdown(P, sw=None, name=None, scale=None):
    """Plan view of the piece over its terrain (top visible block, hill shading, the piece outlined)."""
    from capital.colors import material
    sw = sw or SceneWorld(P, margin=12)
    mats = [material(s) for s in sw.palette]
    vis = np.array([m["kind"] != "air" for m in mats])
    col = np.array([m["top"] for m in mats], np.float32)
    v = vis[sw.a]
    top = np.where(v.any(axis=2), sw.NY - 1 - np.argmax(v[:, :, ::-1], axis=2), 0)
    ids = np.take_along_axis(sw.a, top[:, :, None], axis=2)[:, :, 0]
    c = col[ids]
    h = top.astype(np.float32)
    gx = np.gradient(h, axis=0)
    gz = np.gradient(h, axis=1)
    shade = np.clip(1.0 - (gx * 0.6 + gz * 0.9) * 0.1, 0.55, 1.3)
    c = c * shade[:, :, None]
    # dim what is not part of the piece
    piece = np.zeros((sw.NX, sw.NZ), bool)
    ox, oz = P.X0 - sw.X0, P.Z0 - sw.Z0
    piece[ox:ox + P.NX, oz:oz + P.NZ] = (P.a > 0).any(axis=2)
    c[~piece] = c[~piece] * 0.72 + 30
    scale = scale or max(2, min(6, int(900 / max(sw.NX, sw.NZ))))
    img = Image.fromarray(np.clip(c.transpose(1, 0, 2), 0, 255).astype(np.uint8)).resize(
        (sw.NX * scale, sw.NZ * scale), Image.NEAREST)
    d = ImageDraw.Draw(img)
    f = _font(11)
    for gxl in range((sw.X0 // 10 + 1) * 10, sw.X0 + sw.NX, 10):
        if gxl % 50 == 0:
            d.line([((gxl - sw.X0) * scale, 0), ((gxl - sw.X0) * scale, 6)], fill=(255, 255, 255))
            d.text(((gxl - sw.X0) * scale + 2, 2), str(gxl), fill=(255, 255, 255), font=f)
    for gzl in range((sw.Z0 // 10 + 1) * 10, sw.Z0 + sw.NZ, 10):
        if gzl % 50 == 0:
            d.line([(0, (gzl - sw.Z0) * scale), (6, (gzl - sw.Z0) * scale)], fill=(255, 255, 255))
            d.text((2, (gzl - sw.Z0) * scale + 2), str(gzl), fill=(255, 255, 255), font=f)
    if P.waystone:
        x, y, z, facing, _ = P.waystone
        px, pz = (x - sw.X0 + 0.5) * scale, (z - sw.Z0 + 0.5) * scale
        d.ellipse([px - 5, pz - 5, px + 5, pz + 5], outline=(0, 255, 255), width=2)
    name = name or f"{P.id}_plan"
    p = os.path.join(OUT_DIR, f"{name}.png")
    img.save(p, optimize=True)
    return p


def contact_sheet(paths, out="contact_sheet.png", thumb=(384, 240), cols=4, title=None):
    ims = []
    for p in paths:
        try:
            im = Image.open(p).convert("RGB")
        except OSError:
            continue
        im.thumbnail(thumb)
        ims.append((os.path.basename(p)[:-4], im))
    if not ims:
        return None
    rows = math.ceil(len(ims) / cols)
    W, H = cols * (thumb[0] + 8) + 8, rows * (thumb[1] + 26) + 8 + (30 if title else 0)
    sheet = Image.new("RGB", (W, H), (24, 26, 30))
    d = ImageDraw.Draw(sheet)
    f = _font(13)
    y0 = 8
    if title:
        d.text((10, 8), title, fill=(240, 240, 240), font=_font(18))
        y0 = 38
    for k, (name, im) in enumerate(ims):
        r, c = divmod(k, cols)
        x = 8 + c * (thumb[0] + 8)
        y = y0 + r * (thumb[1] + 26)
        sheet.paste(im, (x + (thumb[0] - im.size[0]) // 2, y))
        d.text((x + 2, y + thumb[1] + 4), name, fill=(220, 220, 220), font=f)
    p = os.path.join(OUT_DIR, out)
    sheet.save(p, optimize=True)
    return p


def overview(pieces, out="map.png", step=4):
    """Map of the whole field map with every piece's footprint, the roads, waystones and dungeon gates."""
    L = probe.layout()
    x0, z0, x1, z1 = L["bounds"]
    g = probe.grid(x0, z0, x1, z1, step)
    pal = {"grass": (98, 160, 62), "forest_floor": (70, 120, 50), "dirt_path": (190, 160, 90),
           "sand": (219, 207, 163), "gravel": (131, 127, 126), "stone": (125, 125, 125), "snow": (245, 250, 250),
           "mud": (80, 70, 60), "coarse_dirt": (119, 85, 59), "riverbed": (50, 90, 190), "lakebed": (50, 90, 190),
           "seabed": (40, 70, 160), "cliff": (110, 105, 100), "quarry": (150, 140, 130), "ash": (70, 60, 70)}
    col = np.zeros((g.nx, g.nz, 3), np.float32)
    for i, n in enumerate(L["surfaces"]):
        col[g.surface == i] = pal[n]
    col[g.water > -30000] = (50, 90, 190)
    h = g.raw.astype(np.float32)
    shade = np.clip(1.0 - (np.gradient(h, axis=0) * 0.5 + np.gradient(h, axis=1) * 0.8) * 0.25, 0.5, 1.4)
    col = col * shade[:, :, None]
    f = g.field
    border = np.zeros_like(f, bool)
    border[1:, :] |= f[1:, :] != f[:-1, :]
    border[:, 1:] |= f[:, 1:] != f[:, :-1]
    col[border] = (240, 230, 90)
    S = 1
    img = Image.fromarray(np.clip(col.transpose(1, 0, 2), 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)

    def p(x, z):
        return ((x - x0) / step * S, (z - z0) / step * S)

    for r in L["roads"]:
        d.line([p(*q) for q in r["points"]], fill=(150, 110, 60), width=1)
    for P in pieces:
        xs, zs = np.nonzero((P.a > 0).any(axis=2))
        if not xs.size:
            continue
        a, b = p(xs.min() + P.X0, zs.min() + P.Z0)
        c, e = p(xs.max() + P.X0, zs.max() + P.Z0)
        d.rectangle([a - 1, b - 1, c + 1, e + 1], outline=(255, 40, 40), width=1)
    for w in L["waystones"]:
        a, b = p(w["x"], w["z"])
        d.ellipse([a - 3, b - 3, a + 3, b + 3], outline=(0, 255, 255), width=1)
    for lm in L["landmarks"]:
        a, b = p(lm["x"], lm["z"])
        d.rectangle([a - 3, b - 3, a + 3, b + 3], outline=(255, 0, 255), width=1)
    fnt = _font(10)
    for P in pieces:
        xs, zs = np.nonzero((P.a > 0).any(axis=2))
        if xs.size and P.kind != "road":
            a, b = p(xs.max() + P.X0, zs.min() + P.Z0)
            d.text((a + 2, b - 2), P.id, fill=(255, 255, 255), font=fnt)
    img = img.resize((img.size[0] * 2, img.size[1] * 2), Image.NEAREST)
    path = os.path.join(OUT_DIR, out)
    img.save(path, optimize=True)
    return path
