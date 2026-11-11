"""Tier 5 guild houses (至高, about 63 x 63): the dragon's lair, the arcane tower, the sky temple and the marble
palace - the grandest, with six chests, two vaults, four armour stands, eight trophy frames and five dummies."""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.districts import _furnish as FN
from capital.districts import _gate_kit as GK
from capital.noise import fbm2, hash2, hash3

from . import hkit as K
from .hkit import AIR, F, SL, ST, WL

OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def _walls(w, x0, z0, x1, z1, y0, y1, state):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                w.fill(x, y0, z, x, y1, z, state)


def _disc(w, cx, cz, r, y, state):
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            if math.hypot(dx, dz) <= r + 0.25:
                w.set(cx + dx, y, cz + dz, state)


def _ring(w, cx, cz, r, y, state, thick=0.6):
    for dx in range(-int(r) - 2, int(r) + 3):
        for dz in range(-int(r) - 2, int(r) + 3):
            if abs(math.hypot(dx, dz) - r) <= thick:
                w.set(cx + dx, y, cz + dz, state)


# ====================================================================================== 竜の巣
def dragon_lair(w):
    """竜の巣: a crag of black rock with the bones of a great dragon coiled over its summit, its skull looking
    west over the arena. Through a vast cave mouth lies the lair: a cavern of basalt pillars and amethyst, lava
    pools behind rims of blackstone, and on a raised shelf the guild's hall - the board, the council, the armour
    and the trophies - above the hoard, where gold lies heaped round the dragon egg and the two vaults. The forge
    burns by the lava; dummies stand in the sand arena outside under a bone arch with the bell."""
    h = K.House(w, "dragon_lair")
    cells = K.island(w, 0, 0, 30.5, 30.0, depth=20, surface=("blackstone", "basalt", "tuff", "coarse_dirt"),
                     soil="blackstone", rock=("blackstone", "basalt", "tuff", "deepslate"), seed=201)
    K.drip_stalactites(w, cells, 0, seed=201, kinds=("blackstone", "basalt", "tuff"))
    h.gate()
    rock = ("blackstone", "basalt", "tuff", "blackstone", "deepslate", "smooth_basalt")
    # ------------------------------------------------------------------ the crag (centre 12, 0)
    ccx, ccz = 12, 0
    peak = {}
    for (x, z) in sorted(cells):
        d = math.hypot((x - ccx) / 17.0, (z - ccz) / 18.5)
        n = fbm2(x, z, 6.0, 3, 202)
        spur = max(0.0, fbm2(x, z, 11.0, 2, 213)) * 9
        top = int(23 + 9 * (1 - d * d) + n * 4 + spur) if d <= 1.0 else int(23 - (d - 1.0) * 32 + n * 4)
        if top <= 0 or x < -6:
            continue
        peak[(x, z)] = top
        for y in range(1, top + 1):
            st = rock[int(hash3(x, y, z, 203) * len(rock)) % len(rock)]
            w.set(x, y, z, B.S(st, axis="y") if st in ("basalt",) else F(st))
    # the cavern x -2..25, z -14..14, y 1..20 with a domed roof
    for x in range(-2, 26):
        for z in range(-14, 15):
            ex, ez = (x - 11.5) / 14.0, z / 14.5
            d = math.hypot(ex, ez)
            if d > 1.0:
                continue
            hgt = int(20 * math.sqrt(max(0.0, 1 - d * d)) + fbm2(x, z, 4.0, 2, 204) * 2)
            for y in range(1, max(4, hgt) + 1):
                w.set(x, y, z, AIR)
            w.set(x, 0, z, F("smooth_basalt") if hash2(x, z, 205) < 0.6 else F("blackstone"))
    K.clear(w, -6, 1, -4, -1, 10, 4)                                  # the cave mouth (west)
    for x in range(-6, -1):
        for z in range(-5, 6):
            w.set(x, 0, z, F("smooth_basalt"))
    for (x, z) in ((3, -8), (3, 8), (11, -10), (11, 10), (19, -8), (19, 8)):     # basalt pillars
        for y in range(1, 22):
            if w.is_empty(x, y, z) or y < 20:
                w.set(x, y, z, B.S("basalt", axis="y"))
                if hash3(x, y, z, 206) < 0.3:
                    w.set(x + 1, y, z, B.S("basalt", axis="y"))
    for (x, z) in sorted(peak):                                        # amethyst in the cavern roof
        if hash2(x, z, 207) < 0.05:
            for y in range(20, 0, -1):
                if not w.is_empty(x, y, z) and w.is_empty(x, y - 1, z):
                    w.set(x, y, z, F("amethyst_block"))
                    w.set(x, y - 1, z, B.S("amethyst_cluster", facing="down"))
                    break
    # lava pools behind blackstone rims (north and south of the cavern)
    for (pz0, pz1) in ((-12, -10), (10, 12)):
        for x in range(4, 20):
            for z in range(pz0, pz1 + 1):
                w.set(x, 0, z, B.S("lava"))
                w.set(x, -1, z, F("magma_block"))
            for z in (pz0 - 1 if pz0 < 0 else pz1 + 1, pz1 + 1 if pz0 < 0 else pz0 - 1):
                if pz0 < 0:
                    w.set(x, 1, pz1 + 1, WL("blackstone"))
                else:
                    w.set(x, 1, pz0 - 1, WL("blackstone"))
    # ------------------------------------------------------------------ the shelf: the guild's hall (east)
    sx0, sx1 = 15, 24
    w.fill(sx0, 0, -7, sx1, 2, 7, F("polished_blackstone_bricks"))
    K.clear(w, sx0, 3, -7, sx1, 12, 7)
    for k in range(2):                                                  # steps up to the shelf (rising east)
        for z in (-1, 0, 1):
            w.set(sx0 - 2 + k, 1 + k, z, ST("polished_blackstone_brick", "east"))
            w.fill(sx0 - 2 + k, 0, z, sx0 - 2 + k, k, z, F("polished_blackstone_bricks"))
    for x in range(sx0, sx1 + 1):
        for z in (-7, 7):
            w.set(x, 3, z, WL("polished_blackstone"))
    w.fill(sx1 - 1, 3, -2, sx1, 3, 2, F("gold_block"))
    w.set(sx1 - 1, 4, 0, ST("polished_blackstone_brick", "west"))     # the master's seat on gold
    w.fill(sx1 + 1, 3, -4, sx1 + 1, 8, 4, F("polished_blackstone_bricks"))
    h.board(w, sx1, 4, -3, "west")
    K.council(w, sx0 + 2, 2, 0, 5, axis="x", top="crimson", cloth="red", chair="crimson")
    K.armory(h, w, [(sx0 + 1, -5), (sx0 + 3, -5), (sx0 + 1, 5), (sx0 + 3, 5)], 2, 0.0)
    frames = [(x, -7) for x in (sx0 + 5, sx0 + 6, sx0 + 7, sx0 + 8)] + [(x, 7) for x in (sx0 + 5, sx0 + 6, sx0 + 7,
                                                                                         sx0 + 8)]
    for (x, z) in frames:
        w.set(x, 4, z + (-1 if z < 0 else 1), F("polished_blackstone_bricks"))
        w.set(x, 3, z, F("polished_blackstone_bricks"))
        h.frame(w, x, 4, z, "south" if z < 0 else "north")
    for x in (sx0 + 2, sx0 + 6):
        K.lamp(w, x, 11, 0, chain=4)
    # the enchanting nook in a high alcove above the shelf (north)
    K.clear(w, 6, 7, -9, 12, 12, -3)
    w.fill(6, 6, -9, 12, 6, -3, F("polished_blackstone_bricks"))
    h.enchanting_nook(w, 9, 6, -6, open_side="south")
    for k in range(6):                                                  # a stair up to it along the cavern wall
        x = 4 + k
        w.set(x, 1 + k, -3, ST("polished_blackstone_brick", "east"))
        w.fill(x, 0, -3, x, k, -3, F("polished_blackstone_bricks"))
        for yy in range(2 + k, 5 + k):
            w.set(x, yy, -3, AIR)
    # ------------------------------------------------------------------ the hoard (centre): gold, the egg, vaults
    hcx, hcz = 8, 2
    for dx in range(-4, 5):
        for dz in range(-3, 4):
            d = math.hypot(dx, dz * 1.3)
            if d <= 4.2:
                hgt = int(2.4 - d * 0.5 + hash2(dx, dz, 208) * 0.8)
                for y in range(1, hgt + 1):
                    w.set(hcx + dx, y, hcz + dz, F("gold_block") if hash3(dx, y, dz, 209) < 0.6 else
                          F("raw_gold_block"))
    w.set(hcx, 3, hcz, F("gold_block"))
    w.set(hcx, 4, hcz, B.S("dragon_egg"))
    h.vault(w, hcx - 5, 1, hcz - 1, "west")
    h.vault(w, hcx - 5, 1, hcz + 1, "west")
    # chests in rock niches round the cavern wall (west half)
    for (x, z, f) in ((0, -9, "south"), (2, -10, "south"), (0, 9, "north"), (2, 10, "north"), (-1, -6, "east"),
                      (-1, 6, "east")):
        K.clear(w, x, 1, z, x, 2, z)
        h.chest(w, x, 1, z, f)
        w.set(x, 3, z, F("polished_blackstone_bricks"))
    # the forge by the southern lava pool
    h.stations_row(w, [(x, 8) for x in range(5, 17)], 0, "north",
                   kinds=("blast_furnace", "anvil", "smithing_table", "grindstone", "blast_furnace", "furnace",
                          "stonecutter", "crafting_table", "smoker", "loom", "cartography_table", "brewing_stand"))
    h.station(w, 17, 1, 8, "fletching_table", "north")
    for x in (6, 12):
        w.set(x, 1, 6, B.S("campfire", lit=True, facing="north"))
    # ------------------------------------------------------------------ the dragon's bones over the crag
    spine = []
    for i in range(70):
        a = i / 69.0 * math.pi * 1.6 - 0.5
        x = ccx + int(round(11 * math.cos(a)))
        z = ccz + int(round(12 * math.sin(a)))
        top = max(peak.get((x, z), 0), 22)
        spine.append((x, top + 2 + int(3 * math.sin(i / 9.0)), z))
    for i, (x, y, z) in enumerate(spine):
        w.set(x, y, z, B.S("bone_block", axis="y"))
        if i % 4 == 0 and 6 < i < 62:                                 # ribs arching down to the rock
            nx, _, nz = spine[min(i + 1, len(spine) - 1)]
            px, pz = -(nz - z), nx - x
            for s in (1, -1):
                for k in range(1, 5):
                    rx, rz = x + px * s * k // 1, z + pz * s * k // 1
                    ry = y + (1 if k < 2 else 0) - max(0, k - 2)
                    if abs(px) + abs(pz) > 0:
                        w.set(rx, ry, rz, B.S("bone_block", axis="y"))
    hx, hy, hz = spine[0]                                              # the skull looking west
    for (dx, dy, dz) in ((0, 0, 0), (-1, 0, 0), (-2, 0, 0), (-1, 1, 0), (0, 1, 0), (-3, 0, 0), (0, 0, -1),
                         (0, 0, 1), (-1, 0, -1), (-1, 0, 1), (0, 1, -1), (0, 1, 1)):
        w.set(hx - 2 + dx, hy + dy, hz + dz, B.S("bone_block", axis="y"))
    w.set(hx - 3, hy + 1, hz - 1, F("crying_obsidian"))
    w.set(hx - 3, hy + 1, hz + 1, F("crying_obsidian"))
    for k in range(1, 4):                                              # horns
        w.set(hx - 1 + k, hy + 1 + k, hz - 1, B.S("bone_block", axis="y"))
        w.set(hx - 1 + k, hy + 1 + k, hz + 1, B.S("bone_block", axis="y"))
    K.TK.smoke(w, ccx, max(peak.values()) + 2, ccz, heavy=True)
    # ------------------------------------------------------------------ the arena (west): dummies, targets, bell
    K.mix(w, -20, 0, -10, -7, 0, 10, [F("sand"), F("red_sand"), F("sand"), F("smooth_sandstone")], 210)
    for (x, z) in sorted(cells):
        if -20 <= x <= -7 and abs(z) in (10, 11) and hash2(x, z, 211) < 0.8:
            w.fill(x, 1, z, x, 1 + int(hash2(z, x, 212) * 2), z, F("blackstone"))
    K.training_ground(h, w, [(-16, -7), (-13, -7), (-10, -7), (-16, 7), (-13, 7)], 0, 90.0, looks=(0, 1, 2, 1, 0))
    h.target(w, -9, 0, 7)
    h.target(w, -9, 0, 5)
    for z in (-3, 3):                                                   # the bone arch with the bell
        w.fill(-10, 1, z, -10, 6, z, B.S("bone_block", axis="y"))
    for z in range(-3, 4):
        w.set(-10, 7 + (1 if abs(z) < 2 else 0), z, B.S("bone_block", axis="z"))
    w.set(-10, 7, 0, B.S("bone_block", axis="z"))
    h.bell(w, -10, 6, 0, facing="east", attachment="ceiling")
    for (x, z) in ((-14, -2), (-14, 2)):
        w.set(x, 1, z, F("polished_basalt"))
        w.set(x, 2, z, B.S("campfire", lit=True, facing="north"))
    return h.finish()


# ====================================================================================== 魔導の塔
def arcane_tower(w):
    """魔導の塔: a sorcerers' tower of purpur and amethyst rising sixty blocks from an enchanted island, girdled by
    two floating rings and crowned by a crystal spire. A spiral stair climbs six floors: the entrance hall with
    the board and the armour, the library with the enchanting nook, the alchemy laboratory, the council chamber,
    the treasury and, at the top, the sanctum whose balcony carries the bell. Crystal shards hover round it;
    the artificers' workshop sits at its foot and dummies wait in a rune circle."""
    h = K.House(w, "arcane_tower")
    cells = K.island(w, 0, 0, 30.5, 30.0, depth=20, surface=("grass_block", "moss_block", "grass_block", "calcite"),
                     rock=("stone", "calcite", "amethyst_block", "tuff", "stone"), seed=221)
    K.drip_stalactites(w, cells, 0, seed=221, kinds=("calcite", "amethyst_block", "tuff"))
    h.gate()
    pur, pil, ame, gold = F("purpur_block"), B.S("purpur_pillar", axis="y"), F("amethyst_block"), F("gilded_trim")
    cx, cz, R = 10, 0, 7
    floors = (0, 8, 16, 24, 32, 40)
    top = 48
    # ------------------------------------------------------------------ the tower
    S.cylinder(w, cx, cz, R + 0.4, 0, 0, F("polished_deepslate"))
    S.cylinder(w, cx, cz, R + 0.4, 1, top, pur, hollow=True, thickness=1.2)
    S.cylinder(w, cx, cz, R - 0.8, 1, top, AIR)
    for (dx, dz) in ((R, 0), (-R, 0), (0, R), (0, -R), (5, 5), (-5, 5), (5, -5), (-5, -5)):   # buttress ribs
        w.fill(cx + dx, 1, cz + dz, cx + dx, top, cz + dz, pil)
    for fy in floors:
        S.cylinder(w, cx, cz, R + 0.4, fy, fy, F("polished_deepslate") if fy == 0 else pur)
        S.cylinder(w, cx, cz, R + 0.9, fy + 7, fy + 7, gold, hollow=True, thickness=0.5) if fy < 40 else None
    for fy in floors[1:]:
        S.cylinder(w, cx, cz, R - 0.8, fy, fy, F("dark_oak_planks"))
    for fy in floors:                                                   # windows
        for (dx, dz) in ((R, 2), (-R + 1, -3), (2, R), (-3, -R + 1), (R - 1, -3), (-2, R - 1)):
            if fy == 0 and dx < 0:
                continue
            w.fill(cx + dx, fy + 3, cz + dz, cx + dx, fy + 5, cz + dz, B.pane("purple"))
    K.clear(w, cx - R - 1, 1, -1, cx - R + 1, 4, 1)                     # the door (west)
    w.fill(cx - R - 1, 0, -1, cx - R + 1, 0, 1, F("polished_deepslate"))
    w.set(cx - R, 5, 0, ame)
    S.cone(w, cx, cz, R + 1.5, top + 1, 6, "slate_roof_tiles", stair_edges=False, finial=False)
    for y in range(top + 6, top + 14):                                  # the crystal spire
        rr = max(0.4, 2.2 - (y - top - 6) * 0.3)
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if math.hypot(dx, dz) <= rr:
                    w.set(cx + dx, y, cz + dz, ame if (dx + dz + y) % 3 else F("purple_stained_glass"))
    w.set(cx, top + 14, cz, B.S("end_rod", facing="up"))
    # the floating rings (two blocks clear of the wall)
    for (ry, r) in ((20, R + 4), (36, R + 3)):
        _ring(w, cx, cz, r, ry, pur, thick=0.55)
        for a in range(0, 360, 45):
            x = cx + int(round(r * math.cos(math.radians(a))))
            z = cz + int(round(r * math.sin(math.radians(a))))
            w.set(x, ry + 1, z, B.S("amethyst_cluster", facing="up"))
            w.set(x, ry - 1, z, ame)
    # the spiral stair through all floors
    K.spiral_stair(w, cx, cz, R - 1.8, floors[0], floors[-1], wood="dark_oak", start=0)
    # ------------------------------------------------------------------ floor 0: hall, board, armour
    fy = floors[0]
    w.fill(cx + R - 1, 1, -2, cx + R - 1, 4, 2, pur)
    h.board(w, cx + R - 2, 2, 0, "west")
    K.armory(h, w, [(cx + 2, -3), (cx + 2, 3), (cx - 1, -4), (cx - 1, 4)], fy, 0.0)
    for (dx, dz) in ((0, 0),):
        w.set(cx + dx, fy, cz + dz, ame)
    K.lamp(w, cx, fy + 7, cz, chain=0)
    # floor 1: the library and the enchanting nook
    fy = floors[1]
    h.enchanting_nook(w, cx, fy, cz, open_side="east")
    # floor 2: the alchemy laboratory
    fy = floors[2]
    for (dx, dz) in ((-3, -2), (-3, 2), (3, -2), (3, 2), (0, -4)):
        w.set(cx + dx, fy + 1, cz + dz, SL("dark_oak", "top"))
    for (dx, dz) in ((-3, -2), (3, 2)):
        h.station(w, cx + dx, fy + 2, cz + dz, "brewing_stand")
    w.set(cx, fy + 1, cz, B.S("water_cauldron", level=3))
    for (dx, dz) in ((-3, 2), (3, -2), (0, -4)):
        w.set(cx + dx, fy + 2, cz + dz, B.potted(("fern", "red_mushroom", "brown_mushroom")[(dx + dz) % 3]))
    K.lamp(w, cx, fy + 7, cz)
    # floor 3: the council chamber with the trophy frames
    fy = floors[3]
    K.council(w, cx - 2, fy, cz, 5, axis="x", top="dark_oak", cloth="purple", chair="dark_oak")
    for (dx, dz, f) in ((-2, -5, "south"), (0, -5, "south"), (2, -5, "south"), (4, -4, "south"),
                        (-2, 5, "north"), (0, 5, "north"), (2, 5, "north"), (4, 4, "north")):
        x, z = cx + dx, cz + dz
        w.set(x, fy + 3, z + (-1 if f == "south" else 1), pur)
        h.frame(w, x, fy + 3, z, f, glow=True)
    # floor 4: the treasury
    fy = floors[4]
    for i, (dx, dz, f) in enumerate(((-4, -2, "east"), (-4, 0, "east"), (-4, 2, "east"), (4, -2, "west"),
                                     (4, 0, "west"), (4, 2, "west"))):
        h.chest(w, cx + dx, fy + 1, cz + dz, f)
    h.vault(w, cx, fy + 1, cz - 4, "south")
    h.vault(w, cx, fy + 1, cz + 4, "north")
    w.set(cx, fy + 1, cz, F("gold_block"))
    w.set(cx, fy + 2, cz, ame)
    # floor 5: the sanctum with a balcony and the bell
    fy = floors[5]
    S.cylinder(w, cx, cz, R + 3.4, fy, fy, pur)
    S.cylinder(w, cx, cz, R + 3.4, fy + 1, fy + 1, B.S("iron_bars"), hollow=True,
               thickness=0.6)
    for (dx, dz) in ((R, 0), (0, R), (0, -R), (-R, 0)):
        x, z = cx + dx, cz + dz
        K.clear(w, x, fy + 1, z, x, fy + 3, z)
    K.clear(w, cx + R - 1, fy + 1, -1, cx + R, fy + 3, 1)
    w.fill(cx + R + 2, fy + 1, -1, cx + R + 2, fy + 4, -1, pil)
    w.fill(cx + R + 2, fy + 1, 1, cx + R + 2, fy + 4, 1, pil)
    w.fill(cx + R + 2, fy + 5, -1, cx + R + 2, fy + 5, 1, pur)
    h.bell(w, cx + R + 2, fy + 4, 0, facing="east", attachment="ceiling")
    w.set(cx, fy + 1, cz, F("lodestone"))
    w.set(cx, fy + 2, cz, B.S("amethyst_cluster", facing="up"))
    K.lamp(w, cx, fy + 7, cz)
    # ------------------------------------------------------------------ floating crystals round the tower
    for (a, r, y) in ((20, 15, 12), (100, 16, 26), (170, 14, 18), (250, 15, 30), (320, 17, 8), (60, 18, 40)):
        x = cx + int(round(r * math.cos(math.radians(a))))
        z = cz + int(round(r * math.sin(math.radians(a))))
        GK.ball(w, x, y, z, 1.6, ame, ry=1.2, seed=a)
        w.set(x, y + 2, z, B.S("amethyst_cluster", facing="up"))
        w.set(x, y - 2, z, B.S("amethyst_cluster", facing="down"))
    # ------------------------------------------------------------------ the artificers' workshop (north)
    ax0, ax1, az0, az1 = -2, 14, -22, -14
    w.fill(ax0, 0, az0, ax1, 0, az1, F("polished_deepslate"))
    _walls(w, ax0, az0, ax1, az1, 1, 5, pur)
    K.clear(w, ax0 + 1, 1, az0 + 1, ax1 - 1, 8, az1 - 1)
    S.gable_roof(w, ax0, az0, ax1, az1, 6, "slate_roof_tiles", axis="x", overhang=1, pitch=1, gable_state=pur)
    K.clear(w, 6, 1, az1, 7, 3, az1)
    for x in range(ax0 + 2, ax1 - 1, 3):
        w.fill(x, 2, az0, x, 3, az0, B.pane("purple"))
    h.stations_row(w, [(x, az0 + 1) for x in range(ax0 + 1, ax1)], 0, "south",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "cartography_table", "furnace", "blast_furnace", "smoker", "fletching_table",
                          "brewing_stand", "crafting_table", "anvil", "furnace"))
    for x in (2, 10):
        K.lamp(w, x, 5, -18)
    # ------------------------------------------------------------------ the rune circle: dummies, targets
    rcx, rcz = -8, 14
    _ring(w, rcx, rcz, 6.0, 0, F("purpur_block"), thick=0.5)
    _ring(w, rcx, rcz, 3.0, 0, F("amethyst_block"), thick=0.5)
    for a in range(0, 360, 72):
        x = rcx + int(round(5 * math.cos(math.radians(a))))
        z = rcz + int(round(5 * math.sin(math.radians(a))))
        h.dummy(w, x, 0, z, (a + 90) % 360, look=(a // 72) % 3)
    h.target(w, rcx, 0, rcz)
    h.target(w, rcx + 8, 0, rcz + 4)
    K.path(w, [(-21, 0), (cx - R - 2, 0)], 0, ["purpur_block", "calcite", "polished_deepslate"], width=3, seed=222)
    for (x, z) in ((-20, -14), (-22, 12), (24, -16), (24, 14), (-6, -25), (8, 26), (-10, 24)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "cherry" if (x + z) % 3 else "azalea", seed=x * 7 + z, height=5)
    for (x, z) in ((-16, -4), (-16, 4), (-12, -4), (-12, 4)):
        w.set(x, 1, z, pil)
        w.set(x, 2, z, ame)
        w.set(x, 3, z, B.S("amethyst_cluster", facing="up"))
    K.scatter_plants(w, cells, 0, seed=223, kinds=("grass", "allium", "fern", "allium",
                                                  "azure_bluet"), density=0.22, on=("grass_block", "moss_block"))
    return h.finish()


# ====================================================================================== 天空の神殿
def _cloud(w, cx, cy, cz, rx, ry, rz, seed):
    for dx in range(-int(rx) - 1, int(rx) + 2):
        for dy in range(-int(ry) - 1, int(ry) + 2):
            for dz in range(-int(rz) - 1, int(rz) + 2):
                d = (dx / rx) ** 2 + (dy / ry) ** 2 + (dz / rz) ** 2 + (hash3(dx, dy, dz, seed) - 0.5) * 0.4
                if d <= 1.0:
                    w.set(cx + dx, cy + dy, cz + dz, F("white_concrete") if hash3(dz, dx, dy, seed) < 0.7 else
                          F("white_wool"), only_empty=True)


def sky_temple(w):
    """天空の神殿: a temple of white marble and gold on islands floating among clouds. On the main island a
    peripteral temple of fluted columns under a golden roof holds the sanctum - a winged guardian statue, the
    board, the council, the armour and the trophies, the enchanting nook and the treasury. Arched bridges lead to
    the garden island with a fountain, the training island with five dummies and the workshop island; the bell
    hangs in a little gilded rotunda."""
    h = K.House(w, "sky_temple")
    q, qp, cal, gold = F("smooth_quartz"), B.S("quartz_pillar", axis="y"), F("calcite"), F("gilded_trim")
    main = K.island(w, 0, 0, 22.0, 18.0, depth=14, surface=("grass_block", "grass_block", "moss_block"),
                    rock=("calcite", "stone", "diorite", "calcite"), seed=231)
    garden = K.island(w, 8, 25, 9.0, 6.0, top=0, depth=8, surface=("grass_block",), rock=("calcite", "diorite"),
                      seed=232)
    train = K.island(w, 8, -25, 9.0, 6.0, top=0, depth=8, surface=("grass_block", "moss_block"),
                     rock=("calcite", "diorite"), seed=233)
    shop = K.island(w, 27, 0, 5.5, 9.0, top=0, depth=8, surface=("grass_block",), rock=("calcite", "diorite"),
                    seed=234)
    cells = main | garden | train | shop
    for (x, z) in sorted(cells):                                        # clouds under the islands
        if hash2(x, z, 235) < 0.025:
            _cloud(w, x, -10 - int(hash2(z, x, 236) * 6), z, 3.5, 1.6, 2.6, seed=x * 31 + z)
    h.gate()
    # ------------------------------------------------------------------ the temple x -6..18, z -9..9
    tx0, tx1, tz0, tz1 = -6, 18, -9, 9
    w.fill(tx0 - 2, 0, tz0 - 2, tx1 + 2, 1, tz1 + 2, cal)               # the stylobate, two steps
    w.fill(tx0 - 1, 2, tz0 - 1, tx1 + 1, 2, tz1 + 1, q)
    for z in range(-2, 3):
        w.set(tx0 - 3, 1, z, ST("smooth_quartz", "east"))
        w.set(tx0 - 2, 2, z, ST("smooth_quartz", "east"))
    for x in range(tx0, tx1 + 1, 3):                                     # the colonnade
        for z in (tz0, tz1):
            w.fill(x, 3, z, x, 11, z, qp)
            w.set(x, 12, z, gold)
    for z in range(tz0, tz1 + 1, 3):
        for x in (tx0, tx1):
            w.fill(x, 3, z, x, 11, z, qp)
            w.set(x, 12, z, gold)
    w.fill(tx0, 13, tz0, tx1, 13, tz1, q)                                # entablature
    for x in range(tx0, tx1 + 1):
        for z in (tz0, tz1):
            w.set(x, 13, z, gold if x % 3 == 0 else q)
    S.gable_roof(w, tx0, tz0, tx1, tz1, 14, "gilded_roof_tiles", axis="x", overhang=1, pitch=1, gable_state=q)
    # the cella (sanctum) x -2..14, z -5..5, walls to y 12
    cx0, cx1, cz0, cz1 = -2, 14, -5, 5
    _walls(w, cx0, cz0, cx1, cz1, 3, 12, q)
    K.clear(w, cx0 + 1, 3, cz0 + 1, cx1 - 1, 12, cz1 - 1)
    w.fill(cx0, 13, cz0, cx1, 13, cz1, q)
    for x in range(cx0 + 1, cx1):
        for z in range(cz0 + 1, cz1):
            w.set(x, 2, z, gold if (x + z) % 6 == 0 else (F("polished_diorite") if (x + z) % 2 else q))
    K.clear(w, cx0, 3, -1, cx0, 8, 1)
    for z in (-1, 1):
        w.set(cx0, 8, z, gold)
    for x in range(cx0 + 2, cx1 - 1, 3):
        for z in (cz0, cz1):
            w.fill(x, 6, z, x, 9, z, B.pane("yellow"))
    S.statue_figure(w, cx1 - 2, 3, 0, facing="west", material="quartz_block", wings=True, plinth="smooth_quartz")
    w.fill(cx1 - 1, 3, -4, cx1 - 1, 7, 4, q)
    h.board(w, cx1 - 2, 4, 3, "west")
    K.council(w, 3, 2, 0, 5, axis="x", top="birch", cloth="yellow", chair="birch")
    K.armory(h, w, [(cx0 + 2, -4), (cx0 + 2, 4), (cx1 - 5, -4), (cx1 - 5, 4)], 2, 90.0)
    for (x, f) in ((1, "south"), (3, "south"), (9, "south"), (11, "south")):
        h.frame(w, x, 5, cz0 + 1, f, glow=True)
    for (x, f) in ((1, "north"), (3, "north"), (9, "north"), (11, "north")):
        h.frame(w, x, 5, cz1 - 1, f, glow=True)
    for x in (2, 7, 12):
        deco.chandelier(w, x, 13, 0, drop=4)
    # the treasury under the stylobate (stair down at the east end)
    w.fill(cx0 + 2, -5, cz0 + 1, cx1 - 2, 1, cz1 - 1, cal)
    K.clear(w, cx0 + 3, -4, cz0 + 2, cx1 - 3, -2, cz1 - 2)
    for k in range(5):
        x = cx1 - 3 - k
        w.set(x, 1 - k, cz0 + 2, ST("smooth_quartz", "east"))
        for yy in range(2 - k, 5 - k):
            w.set(x, yy, cz0 + 2, AIR)
    for (x, z, f) in ((cx0 + 3, -1, "east"), (cx0 + 3, 1, "east"), (cx0 + 5, cz1 - 2, "north"),
                      (cx0 + 7, cz1 - 2, "north"), (cx0 + 9, cz1 - 2, "north"), (cx0 + 11, cz1 - 2, "north")):
        h.chest(w, x, -4, z, f)
    h.vault(w, cx0 + 5, -4, cz0 + 2, "south")
    h.vault(w, cx0 + 7, -4, cz0 + 2, "south")
    K.lamp(w, 6, -2, 0)
    # the enchanting nook in the pronaos (east porch)
    h.enchanting_nook(w, cx1 + 2, 2, 0, open_side="west")
    # ------------------------------------------------------------------ bridges to the other islands
    def bridge(x0, z0, x1, z1):
        n = max(abs(x1 - x0), abs(z1 - z0))
        for i in range(n + 1):
            x = x0 + (x1 - x0) * i // n
            z = z0 + (z1 - z0) * i // n
            arch = int(round(2.0 * math.sin(math.pi * i / n)))
            for dw in (-1, 0, 1):
                px, pz = (x + dw, z) if z1 != z0 else (x, z + dw)
                w.set(px, arch, pz, q)
                w.set(px, arch - 1, pz, cal)
                for k in (1, 2, 3):
                    w.set(px, arch + k, pz, AIR)
            for dw in (-2, 2):
                px, pz = (x + dw, z) if z1 != z0 else (x, z + dw)
                w.set(px, arch + 1, pz, SL("quartz", "bottom"))
                w.set(px, arch, pz, q)
    bridge(8, 12, 8, 20)
    bridge(8, -12, 8, -20)
    bridge(19, 0, 23, 0)
    # ------------------------------------------------------------------ the garden island: fountain, the bell rotunda
    _disc(w, 8, 25, 2.6, 0, q)
    _disc(w, 8, 25, 2.6, 1, q)
    _disc(w, 8, 25, 1.6, 1, B.S("water"))
    w.set(8, 1, 25, gold)
    w.set(8, 2, 25, B.S("fountain_spray", power=3, facing="up"))
    for (x, z) in ((3, 23), (13, 27), (4, 28), (12, 22)):
        deco.flower_bed(w, x - 1, z - 1, x + 1, z + 1, 0, seed=x * z, border="smooth_quartz")
    bcx, bcz = 14, 24
    for a in range(0, 360, 60):
        x = bcx + int(round(2 * math.cos(math.radians(a))))
        z = bcz + int(round(2 * math.sin(math.radians(a))))
        w.fill(x, 1, z, x, 4, z, qp)
    _disc(w, bcx, bcz, 2.4, 5, q)
    S.dome(w, bcx, 5, bcz, 2.4, F("gilded_roof_tiles"), ry=2.2, hollow=False)
    h.bell(w, bcx, 4, bcz, facing="east", attachment="ceiling")
    w.set(bcx, 0, bcz, gold)
    # ------------------------------------------------------------------ the training island: five dummies, targets
    K.mix(w, 2, 0, -29, 14, 0, -21, [F("grass_block"), F("moss_block"), cal], 237)
    K.training_ground(h, w, [(3, -27), (6, -28), (9, -28), (12, -27), (5, -23)], 0, 0.0, looks=(0, 1, 2, 1, 0))
    h.target(w, 13, 0, -23)
    h.target(w, 10, 0, -22)
    # ------------------------------------------------------------------ the workshop island: an open stoa
    w.fill(24, 0, -6, 30, 0, 6, cal)
    for z in range(-6, 7, 3):
        w.fill(24, 1, z, 24, 5, z, qp)
        w.fill(30, 1, z, 30, 5, z, qp)
    w.fill(23, 6, -7, 31, 6, 7, q)
    for x in range(23, 32):
        for z in (-7, 7):
            w.set(x, 7, z, gold if x % 2 else q)
    h.stations_row(w, [(29, z) for z in range(-5, 7)], 0, "west",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "cartography_table", "furnace", "blast_furnace", "smoker", "brewing_stand",
                          "fletching_table"))
    for z in (-3, 3):
        K.lamp(w, 27, 5, z)
    # ------------------------------------------------------------------ the main island's grounds
    K.path(w, [(-21, 0), (tx0 - 4, 0)], 0, ["smooth_quartz", "calcite", "polished_diorite"], width=3, seed=238)
    for (x, z) in ((-14, -10), (-14, 10), (20, -13), (20, 13), (-4, -14), (-4, 14)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "cherry" if (x + z) % 2 else "birch", seed=x * 3 + z, height=5)
    for (x, z) in ((-12, -4), (-12, 4)):
        S.statue_figure(w, x, 1, z, facing="south" if z < 0 else "north", material="quartz_block", wings=True,
                        plinth="smooth_quartz")
    K.scatter_plants(w, cells, 0, seed=239, kinds=("grass", "oxeye_daisy", "azure_bluet", "lily_of_the_valley",
                                                  "white_tulip"), density=0.25, on=("grass_block", "moss_block"))
    for (x, z) in sorted(main):
        if hash2(x, z, 240) < 0.012:
            _cloud(w, x, 22 + int(hash2(z, x, 241) * 8), z, 4.0, 1.4, 3.0, seed=x + z)
    return h.finish()


# ====================================================================================== 白亜の宮殿
def marble_palace(w):
    """白亜の宮殿: a white marble palace with gilded domes behind formal gardens. From the triumphal gate a
    reflecting pool leads to the grand stair and the colonnaded front; under the great dome the ballroom glitters
    with chandeliers, the throne room beyond holds the board, the armour and the trophy gallery, the library wing
    the enchanting nook, the treasury the chests and the two vaults. Parterres of clipped hedges with fountains,
    statues and a belvedere with the bell flank the axis; the service wing holds the workshop and the parade
    court the dummies."""
    h = K.House(w, "marble_palace")
    cells = K.island(w, 0, 0, 30.5, 30.0, depth=20, surface=("grass_block", "grass_block", "moss_block"),
                     rock=("calcite", "stone", "diorite", "andesite"), seed=251)
    K.drip_stalactites(w, cells, 0, seed=251, kinds=("calcite", "diorite"))
    h.gate()
    q, qp, qb, gold = F("smooth_quartz"), B.S("quartz_pillar", axis="y"), F("quartz_bricks"), F("gilded_trim")
    cal = F("calcite")
    # ------------------------------------------------------------------ the axis: reflecting pool and parterres
    for x in range(-20, -2):
        for z in range(-3, 4):
            w.set(x, 0, z, F("polished_diorite"))
    w.fill(-17, 0, -1, -6, 0, 1, B.S("water"))
    w.fill(-17, -1, -1, -6, -1, 1, F("prismarine_bricks"))
    for x in (-15, -9):
        w.set(x, 0, 0, gold)
        w.set(x, 1, 0, B.S("fountain_spray", power=2, facing="up"))
    for (pz0, pz1) in ((-14, -5), (5, 14)):                              # the parterres
        for x in range(-20, -3):
            for z in range(pz0, pz1 + 1):
                if (x, z) not in cells:
                    continue
                w.set(x, 0, z, F("grass_block"))
                edge = x in (-20, -4) or z in (pz0, pz1)
                diag = abs((x + 12) - (z - (pz0 + pz1) // 2)) <= 0 or abs((x + 12) + (z - (pz0 + pz1) // 2)) <= 0
                if edge or diag:
                    w.set(x, 1, z, B.leaves("azalea"))
        mz = (pz0 + pz1) // 2
        _disc(w, -12, mz, 2.0, 0, q)
        _disc(w, -12, mz, 2.0, 1, q)
        _disc(w, -12, mz, 1.2, 1, B.S("water"))
        w.set(-12, 1, mz, gold)
        w.set(-12, 2, mz, B.S("fountain_spray", power=2, facing="up"))
        for x in (-18, -6):
            S.statue_figure(w, x, 1, mz, facing="south" if mz < 0 else "north", material="quartz_block",
                            wings=False, plinth="smooth_quartz")
    # ------------------------------------------------------------------ the palace: x -2..26, z -18..18
    px0, px1, pz0, pz1 = -2, 26, -18, 18
    w.fill(px0 - 1, 0, pz0 - 1, px1 + 1, 0, pz1 + 1, cal)
    # central block x -2..14, z -8..8 (ballroom under the dome), wings to the north and south
    for (x0, z0, x1, z1, hgt) in ((px0, -8, 14, 8, 14), (px0 + 2, pz0, 12, -9, 10), (px0 + 2, 9, 12, pz1, 10),
                                  (15, -8, px1, 8, 12)):
        _walls(w, x0, z0, x1, z1, 1, hgt, qb)
        K.clear(w, x0 + 1, 1, z0 + 1, x1 - 1, hgt, z1 - 1)
        w.fill(x0, hgt + 1, z0, x1, hgt + 1, z1, q)
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                w.set(x, hgt + 2, z, SL("quartz", "bottom") if (x + z) % 2 else F("chiseled_quartz_block"))
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                w.set(x, hgt + 2, z, SL("quartz", "bottom") if (x + z) % 2 else F("chiseled_quartz_block"))
        for x in range(x0 + 2, x1 - 1, 3):                                # tall windows with gold heads
            for z in (z0, z1):
                w.fill(x, 3, z, x, hgt - 3, z, B.pane())
                w.set(x, hgt - 2, z, gold)
        for z in range(z0 + 2, z1 - 1, 3):
            for x in (x0, x1):
                if x == px0 and abs(z) <= 2:
                    continue
                w.fill(x, 3, z, x, hgt - 3, z, B.pane())
                w.set(x, hgt - 2, z, gold)
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                w.set(x, 0, z, F("polished_diorite") if (x + z) % 2 else q)
    # openings between the blocks
    for (x0, z0, x1, z1) in ((4, -9, 6, -8), (4, 8, 6, 9), (14, -1, 15, 1)):
        K.clear(w, x0, 1, z0, x1, 4, z1)
    # the portico (west front): eight columns and a pediment, a grand stair
    for z in range(-7, 8, 2):
        w.fill(px0 - 4, 1, z, px0 - 4, 11, z, qp)
        w.set(px0 - 4, 12, z, gold)
    w.fill(px0 - 5, 13, -8, px0 - 1, 13, 8, q)
    for k in range(0, 9):                                                  # the pediment
        z0, z1 = -8 + k, 8 - k
        if z0 > z1:
            break
        for z in range(z0, z1 + 1):
            w.set(px0 - 5, 14 + k // 2, z, q if (z in (z0, z1)) else qb)
    w.fill(px0 - 4, 0, -8, px0 - 1, 0, 8, q)
    K.clear(w, px0, 1, -2, px0, 6, 2)
    for z in (-2, 2):
        w.set(px0, 6, z, gold)
    for z in range(-3, 4):
        w.set(px0 - 5, 1, z, ST("smooth_quartz", "east"))
    # the great dome over the ballroom (centre 6, 0)
    dcx, dcz = 6, 0
    S.cylinder(w, dcx, dcz, 6.4, 15, 18, qb, hollow=True, thickness=1.1)
    for a in range(0, 360, 30):
        x = dcx + int(round(6 * math.cos(math.radians(a))))
        z = dcz + int(round(6 * math.sin(math.radians(a))))
        w.fill(x, 16, z, x, 17, z, B.pane("yellow"))
    S.dome(w, dcx, 19, dcz, 6.6, F("gilded_roof_tiles"), ry=7.5, hollow=True, thickness=1.1)
    w.set(dcx, 27, dcz, F("gold_block"))
    w.set(dcx, 28, dcz, B.S("gilded_finial"))
    S.cylinder(w, dcx, dcz, 5.4, 15, 15, AIR)
    for (x, z) in ((8, -13), (8, 13)):
        S.dome(w, x, 11, z, 3.4, F("gilded_roof_tiles"), ry=3.6, hollow=False)
        w.set(x, 15, z, B.S("gilded_finial"))
    # ------------------------------------------------------------------ the ballroom: chandeliers, banquet
    for (x, z) in ((2, -4), (2, 4), (10, -4), (10, 4)):
        deco.chandelier(w, x, 14, z, drop=4)
    deco.chandelier(w, dcx, 25, dcz, drop=9)
    for k in range(1, 9):
        w.set(dcx, 25 - k, dcz, B.chain())
    K.council(w, 1, 0, -5, 10, axis="x", top="birch", cloth="white", chair="birch")
    K.council(w, 1, 0, 5, 10, axis="x", top="birch", cloth="white", chair="birch")
    for x in range(px0 + 1, 14):
        w.set(x, 1, 0, B.carpet("red"))
        w.set(x, 1, -1, B.carpet("yellow"))
        w.set(x, 1, 1, B.carpet("yellow"))
    # ------------------------------------------------------------------ the throne room (east block x 15..26)
    w.fill(23, 1, -3, 25, 1, 3, F("red_wool"))
    w.fill(24, 2, -1, 25, 2, 1, gold)
    w.set(24, 3, 0, ST("quartz", "west"))
    w.fill(25, 3, -1, 25, 6, 1, gold)
    w.fill(px1 - 1, 1, -6, px1 - 1, 5, -4, qb)
    h.board(w, px1 - 2, 2, -5, "west")
    K.armory(h, w, [(21, -6), (21, 6), (18, -6), (18, 6)], 0, 0.0)
    for x in range(16, 26):
        w.set(x, 1, 0, B.carpet("red"))
    for (x, z, f) in ((17, -7, "south"), (19, -7, "south"), (22, -7, "south"), (24, -7, "south"),
                      (17, 7, "north"), (19, 7, "north"), (22, 7, "north"), (24, 7, "north")):
        h.frame(w, x, 4, z, f, glow=True)
    for x in (18, 23):
        deco.chandelier(w, x, 12, 0, drop=4)
    # ------------------------------------------------------------------ the library wing (north): enchanting nook
    h.enchanting_nook(w, 7, 0, -13, open_side="south")
    for x in range(px0 + 3, 12):
        if not (5 <= x <= 9):
            w.fill(x, 1, pz0 + 1, x, 4, pz0 + 1, B.S("bookshelf"))
    for (x, z) in ((2, -12), (11, -12)):
        w.set(x, 1, z, B.S("lectern", facing="south", has_book=True))
    deco.chandelier(w, 7, 10, -15, drop=3)
    # ------------------------------------------------------------------ the treasury wing (south): chests, vaults
    for i, x in enumerate((2, 4, 6, 8, 10, 11)):
        h.chest(w, x, 1, pz1 - 1, "north")
    w.set(3, 1, pz1 - 1, gold)
    w.set(5, 1, pz1 - 1, gold)
    w.set(7, 1, pz1 - 1, gold)
    w.set(9, 1, pz1 - 1, gold)
    h.vault(w, 2, 1, 11, "east")
    h.vault(w, 11, 1, 11, "west")
    w.fill(3, 1, 11, 10, 1, 11, B.carpet("purple"))
    deco.chandelier(w, 7, 10, 14, drop=3)
    # ------------------------------------------------------------------ the service wing and parade court (east)
    sx0, sx1, sz0, sz1 = 16, 26, 11, 20
    w.fill(sx0, 0, sz0, sx1, 0, sz1, cal)
    _walls(w, sx0, sz0, sx1, sz1, 1, 6, qb)
    K.clear(w, sx0 + 1, 1, sz0 + 1, sx1 - 1, 6, sz1 - 1)
    w.fill(sx0, 7, sz0, sx1, 7, sz1, q)
    K.clear(w, 20, 1, sz0, 21, 3, sz0)
    h.stations_row(w, [(x, sz1 - 1) for x in range(sx0 + 1, sx1)], 0, "north",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "cartography_table", "furnace", "blast_furnace"))
    for (z, kind) in ((sz0 + 2, "smoker"), (sz0 + 3, "brewing_stand"), (sz0 + 4, "fletching_table")):
        h.station(w, sx1 - 1, 1, z, kind, "west")
    K.lamp(w, 21, 6, 15)
    K.mix(w, 16, 0, -22, 27, 0, -12, [cal, F("polished_diorite"), q], 252)
    K.training_ground(h, w, [(18, -20), (21, -20), (24, -20), (18, -16), (24, -16)], 0, 0.0, looks=(1, 1, 0, 2, 2))
    h.target(w, 21, 0, -14)
    h.target(w, 26, 0, -14)
    # ------------------------------------------------------------------ the belvedere with the bell (south-west)
    bcx, bcz = -10, 21
    for a in range(0, 360, 60):
        x = bcx + int(round(2 * math.cos(math.radians(a))))
        z = bcz + int(round(2 * math.sin(math.radians(a))))
        w.fill(x, 1, z, x, 4, z, qp)
    _disc(w, bcx, bcz, 2.4, 0, q)
    _disc(w, bcx, bcz, 2.4, 5, q)
    S.dome(w, bcx, 5, bcz, 2.4, F("gilded_roof_tiles"), ry=2.2, hollow=False)
    h.bell(w, bcx, 4, bcz, facing="east", attachment="ceiling")
    # ------------------------------------------------------------------ grounds
    for (x, z) in ((-24, -14), (-24, 14), (-8, -24), (-8, 24), (28, -6), (28, 6), (6, -26), (6, 26)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "cypress", seed=x + 5 * z, height=7)
    for (x, z) in ((-21, -4), (-21, 4)):
        deco.lamp_post(w, x, 0, z, height=4, double=True, facing="east")
    K.scatter_plants(w, cells, 0, seed=253, kinds=("grass", "poppy", "oxeye_daisy", "azure_bluet", "cornflower"),
                     density=0.15, on=("grass_block",))
    return h.finish()


DESIGNS = {
    "dragon_lair": dragon_lair,
    "arcane_tower": arcane_tower,
    "sky_temple": sky_temple,
    "marble_palace": marble_palace,
}
INTERIORS = {
    "dragon_lair": {"cavern": ((-3.0, 4.62, 0.5), (22.0, 8.0, 0.0)), "shelf": ((15.0, 4.62, 0.5), (25.0, 5.0, 0.0))},
    "arcane_tower": {"hall": ((5.0, 2.62, 0.5), (16.0, 3.0, 0.0)), "council": ((6.0, 26.62, 0.5), (14.0, 26.0, 0.0))},
    "sky_temple": {"sanctum": ((0.0, 4.62, 0.5), (13.0, 6.0, 0.0)), "islands": ((-6.0, 6.0, 12.0), (8.0, 1.0, 25.0))},
    "marble_palace": {"ballroom": ((0.0, 2.62, 0.5), (12.0, 8.0, 0.0)), "throne": ((16.0, 2.62, 0.5),
                                                                            (26.0, 4.0, 0.0))},
}
