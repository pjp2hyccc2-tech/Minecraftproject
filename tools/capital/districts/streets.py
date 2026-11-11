"""通り Town streets: re-paved with rounded cobbles (avenues edged with flagstones), signposts at the
junctions, benches and planters along the avenues, pennant poles, street crowd spots, and the castle-gate
guards at the top of the viaduct."""
from __future__ import annotations

import numpy as np

from capital import blocks as B
from capital import deco
from capital import layout as L
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "streets"

ROAD_STONES = {"minecraft:stone_bricks", "minecraft:polished_andesite", "minecraft:andesite", "minecraft:cobblestone",
               "minecraft:cracked_stone_bricks", "minecraft:mossy_cobblestone", "minecraft:gravel"}


def repave(w, district):
    m = district.mask() & w.footprint
    from capital.world import X0, Z0, Y0
    xi, zi = np.nonzero(m)
    ids = w.a[xi, zi, 0 - Y0]
    road_ids = {w.idx(B.S(n.split(":")[1])) for n in ROAD_STONES}
    sel = np.isin(ids, np.array(sorted(road_ids), np.uint16))
    xs, zs = xi[sel] + X0, zi[sel] + Z0
    u = hash2(xs, zs, 2201)
    cob = w.idx(B.S("cobbled_paving"))
    flag = w.idx(B.S("plaza_flagstones"))
    mossy = w.idx(B.S("mossy_cobblestone"))
    sb = w.idx(B.S("stone_bricks"))
    out = np.where(u < 0.86, cob, np.where(u < 0.92, sb, np.where(u < 0.96, mossy, flag))).astype(np.uint16)
    # avenues: a flagstone band along the middle
    mid = ((np.abs(xs) <= 1) & (np.abs(zs) > 26)) | ((np.abs(zs) <= 1) & (np.abs(xs) > 26))
    out = np.where(mid, flag, out).astype(np.uint16)
    w.a[xs - X0, zs - Z0, 0 - Y0] = out


def build(w, district, terrain):
    repave(w, district)
    # signposts at the main junctions
    for (x, z, lines) in ((-6, -40, {"south": ["↑ 王城", "Castle", "↓ 広場", "Plaza"]}),
                          (5, 37, {"north": ["↓ 城門・港", "Gate, Harbour", "↑ 広場", "Plaza"]}),
                          (-52, -6, {"east": ["← 西門", "West gate", "→ 冒険者ギルド", "Guild"]}),
                          (52, 6, {"west": ["→ 東門 迷宮", "East gate", "← 市場", "Market"]})):
        w.set(x, 1, z, B.fence("dark_oak"))
        w.set(x, 2, z, B.fence("dark_oak"))
        f = list(lines.keys())[0]
        deco.sign_text(w, x, 3, z, B.S("dark_oak_sign", rotation={"north": 8, "south": 0, "east": 12,
                                                                  "west": 4}[f]), lines[f], color="black")
    # pennant poles where the avenues leave the plaza ring
    for (x, z) in ((-7, -30), (7, 30), (-30, 7), (30, -7)):
        TK.flagpole(w, x, 0, z, 9, "east", "pennant", 5.0, 1.0, pole="wall")
        w.set(x, 1, z, K.F(K.SM))
    # street crowd
    TK.crowd(w, "street", [(0, 1, -36), (0, 1, 50), (-75, 1, 0), (72, 1, 0), (-30, 1, -43), (40, 1, 43)],
             start=30, need=16)
    # castle gate guards (top of the viaduct) and a few people on the castle terrace
    for (n, x) in ((1, -4), (2, 4)):
        TK.people_spot(w, f"guard.castle.{n}", x, L.PLATEAU_Y + 1, -86, 0.0, need=0, search=4)
    TK.crowd(w, "castle", [(-20, L.TERRACE_Y + 1, -106), (22, L.TERRACE_Y + 1, -106), (0, L.TERRACE_Y + 1, -112)],
             need=12)
