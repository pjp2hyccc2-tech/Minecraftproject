"""Tier 3 guild houses (立派, about 47 x 47): the Japanese mansion, the great tree, the sage's library and the golden
jungle temple. From this tier on a house has an enchanting nook, trophy frames and a council table."""
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


def _box_walls(w, x0, z0, x1, z1, y0, y1, state):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                w.fill(x, y0, z, x, y1, z, state)


# ====================================================================================== 和の屋敷
def _kawara_roof(w, x0, z0, x1, z1, y0, overhang=2):
    """An irimoya-style roof of kawara tiles: a hipped lower part with a gabled top, upturned corners."""
    top = S.hip_roof(w, x0, z0, x1, z1, y0, "kawara_roof_tiles", overhang=overhang, pitch=1)
    for (x, z) in ((x0 - overhang, z0 - overhang), (x1 + overhang, z0 - overhang), (x0 - overhang, z1 + overhang),
                   (x1 + overhang, z1 + overhang)):
        w.set(x, y0 + 1, z, SL("kawara_roof_tiles", "bottom"))
    return top


def japanese_mansion(w):
    """和の屋敷: a samurai mansion behind a white plastered wall. Through the vermilion-pillared yakuimon a gravel
    path leads past stone lanterns to the shoin hall on its raised floor, ringed by a veranda under an irimoya roof
    of kawara tiles: tatami rooms behind paper screens, the alcove with the board and the guild's trophies, a low
    council table, the scroll room with the enchanting nook. A white storehouse (kura) keeps the chests and the
    vault; the dojo holds the dummies; a koi pond with a red bridge, a raked-gravel garden, a torii, bamboo and
    cherry trees, and a bronze bell in its own belfry."""
    h = K.House(w, "japanese_mansion")
    cells = K.island(w, 0, 0, 22.5, 21.5, depth=16, surface=("grass_block", "moss_block", "grass_block"),
                     rock=("stone", "andesite", "tuff", "stone"), seed=111)
    K.drip_stalactites(w, cells, 0, seed=111)
    plaster, red = F("white_plaster"), F("red_concrete")
    # the outer wall along the west side (the gate builds its own segment in the middle)
    for z in range(-18, 19):
        if abs(z) <= 6:
            continue
        x = -15
        if (x, z) not in cells:
            continue
        w.set(x, 0, z, F("stone_bricks"))
        w.fill(x, 1, z, x, 5, z, plaster)
        w.set(x, 6, z, F("deepslate_tiles"))
        w.set(x + 1, 6, z, SL("deepslate_tiles", "bottom"))
        w.set(x - 1, 6, z, SL("deepslate_tiles", "bottom"))
    h.gate()
    # ------------------------------------------------------------------ the shoin hall x -6..12, z -9..5, floor y 1
    hx0, hx1, hz0, hz1 = -6, 12, -9, 5
    w.fill(hx0 - 2, 0, hz0 - 2, hx1 + 2, 0, hz1 + 2, F("gravel"))
    w.fill(hx0, 0, hz0, hx1, 0, hz1, F("stone_bricks"))
    w.fill(hx0 - 1, 1, hz0 - 1, hx1 + 1, 1, hz1 + 1, SL("spruce", "top"))         # the veranda (engawa)
    for x in range(hx0, hx1 + 1):                                  # tatami: straw mats with dark borders
        for z in range(hz0, hz1 + 1):
            w.set(x, 1, z, F("spruce_planks") if (x - hx0) % 3 == 0 or (z - hz0) % 6 == 0 else
                  F("bamboo_planks"))
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            edge = x in (hx0, hx1) or z in (hz0, hz1)
            post = edge and ((x - hx0) % 3 == 0 and z in (hz0, hz1) or (z - hz0) % 3 == 0 and x in (hx0, hx1))
            if post:
                w.fill(x, 2, z, x, 6, z, B.log("dark_oak", "y"))
            elif edge:
                w.fill(x, 2, z, x, 5, z, B.S("white_stained_glass_pane"))
                w.set(x, 6, z, F("white_plaster"))
    for x in range(hx0 - 1, hx1 + 2, 3):                             # veranda posts
        for z in (hz0 - 1, hz1 + 1):
            w.fill(x, 2, z, x, 6, z, B.log("dark_oak", "y"))
    for z in range(hz0 - 1, hz1 + 2, 3):
        for x in (hx0 - 1, hx1 + 1):
            w.fill(x, 2, z, x, 6, z, B.log("dark_oak", "y"))
    w.fill(hx0 - 1, 7, hz0 - 1, hx1 + 1, 7, hz1 + 1, F("dark_oak_planks"))
    K.clear(w, hx0 + 1, 2, hz0 + 1, hx1 - 1, 6, hz1 - 1)
    _kawara_roof(w, hx0 - 1, hz0 - 1, hx1 + 1, hz1 + 1, 8, overhang=2)
    for x in range(hx0, hx1 + 1):                                  # ceiling under the roof
        for z in range(hz0, hz1 + 1):
            w.set(x, 7, z, F("stripped_spruce_wood") if (x + z) % 4 == 0 else F("spruce_planks"))
    # the entrance (genkan) in the west front: open screens, steps
    K.clear(w, hx0, 2, -2, hx0, 5, 0)
    for z in (-2, -1, 0):
        w.set(hx0 - 2, 1, z, SL("stone_brick", "bottom"))
    # inner screens dividing the rooms
    for z in range(hz0 + 1, hz1):
        if z not in (-2, -1, 0):
            w.fill(3, 2, z, 3, 5, z, B.S("white_stained_glass_pane"))
    for x in range(3, hx1):
        if x not in (7, 8):
            w.fill(x, 2, -3, x, 5, -3, B.S("white_stained_glass_pane"))
    w.fill(3, 6, hz0 + 1, 3, 6, hz1 - 1, B.log("dark_oak", "z"))
    w.fill(4, 6, -3, hx1 - 1, 6, -3, B.log("dark_oak", "x"))
    # front room (x -5..2): the alcove (tokonoma) with the board and trophies, the council table
    w.fill(hx0 + 1, 2, hz0 + 1, 2, 2, hz0 + 1, F("dark_oak_planks"))
    h.board(w, -2, 3, hz0 + 2, "south")
    w.fill(-3, 2, hz0 + 1, -1, 4, hz0 + 1, plaster)
    w.set(-2, 2, hz0 + 2, AIR)
    for (x, z) in ((hx0 + 1, hz0 + 1), (1, hz0 + 1)):
        w.set(x, 3, z, B.potted("fern"))
    K.trophy_wall(h, w, [(-5, 2), (-5, 1), (-5, -4), (-5, -5)][:4], 4, "east")
    for (x, z) in ((-5, 2), (-5, 1), (-5, -4), (-5, -5)):
        w.set(x - 1, 4, z, B.log("dark_oak", "y"))
    for x in range(-3, 2):                                          # low council table with floor cushions
        w.set(x, 2, 1, SL("dark_oak", "bottom"))
    for x in range(-3, 2):
        w.set(x, 2, 2, B.carpet("red"))
        w.set(x, 2, -1, B.carpet("red")) if x != -2 else None
    w.set(-4, 2, 1, B.carpet("purple"))
    w.set(2, 2, 1, B.carpet("purple"))
    K.lamp(w, -1, 6, 1)
    K.lamp(w, -1, 6, -5)
    # the scroll room (north-east): the enchanting nook among shelves
    h.enchanting_nook(w, 8, 1, hz0 + 3, open_side="south")
    w.set(5, 2, -4, B.S("lectern", facing="south", has_book=True))
    K.lamp(w, 8, 6, -5)
    # the garden room (south-east): lounge looking onto the pond
    K.lounge(w, 5, 1, 0, "south", wood="dark_oak", carpet="green", border="lime", length=3)
    K.lamp(w, 7, 6, 2)
    for x in range(4, hx1):
        w.fill(x, 2, hz1, x, 5, hz1, AIR) if x % 3 != 0 else None
    # ------------------------------------------------------------------ the storehouse (kura): chests and vault
    kx0, kx1, kz0, kz1 = 1, 9, -19, -13
    w.fill(kx0, 0, kz0, kx1, 0, kz1, F("stone_bricks"))
    for x in range(kx0, kx1 + 1):
        for z in range(kz0, kz1 + 1):
            if x in (kx0, kx1) or z in (kz0, kz1):
                w.fill(x, 1, z, x, 2, z, F("polished_blackstone_bricks"))
                w.fill(x, 3, z, x, 6, z, plaster)
    K.clear(w, kx0 + 1, 1, kz0 + 1, kx1 - 1, 7, kz1 - 1)
    w.fill(kx0 + 1, 0, kz0 + 1, kx1 - 1, 0, kz1 - 1, F("spruce_planks"))
    S.gable_roof(w, kx0, kz0, kx1, kz1, 7, "kawara_roof_tiles", axis="x", overhang=1, pitch=1,
                 gable_state=plaster)
    K.door(w, 5, 0, kz1, "north", wood="dark_oak")
    for x in (3, 7):
        w.set(x, 4, kz0, B.S("iron_bars"))
    K.storage_wall(h, w, [(x, kz0 + 1) for x in range(kx0 + 1, kx1)], 0, "south", between=B.barrel("up"))
    h.vault(w, kx1 - 1, 1, kz1 - 2, "west")
    K.lamp(w, 5, 5, -16)
    # ------------------------------------------------------------------ the dojo (south): dummies and targets
    dx0, dx1, dz0, dz1 = -4, 10, 9, 18
    w.fill(dx0, 0, dz0, dx1, 0, dz1, F("stone_bricks"))
    w.fill(dx0 + 1, 1, dz0 + 1, dx1 - 1, 1, dz1 - 1, F("spruce_planks"))
    for x in range(dx0, dx1 + 1):
        for z in range(dz0, dz1 + 1):
            if x in (dx0, dx1) or z in (dz0, dz1):
                post = (x - dx0) % 3 == 0 or (z - dz0) % 3 == 0
                w.fill(x, 1, z, x, 5, z, B.log("dark_oak", "y") if post else plaster)
    K.clear(w, dx0 + 1, 2, dz0 + 1, dx1 - 1, 6, dz1 - 1)
    w.fill(dx0, 6, dz0, dx1, 6, dz1, F("dark_oak_planks"))
    _kawara_roof(w, dx0, dz0, dx1, dz1, 7, overhang=1)
    K.clear(w, 2, 1, dz0, 4, 4, dz0)
    w.set(2, 1, dz0, SL("spruce", "top"))
    w.set(3, 1, dz0, SL("spruce", "top"))
    w.set(4, 1, dz0, SL("spruce", "top"))
    for x in range(dx0 + 2, dx1 - 1, 3):
        w.fill(x, 3, dz1, x, 4, dz1, B.S("white_stained_glass_pane"))
    K.training_ground(h, w, [(0, 15), (3, 16), (6, 15)], 1, 180.0, looks=(0, 1, 0))
    h.target(w, 8, 1, 12)
    K.weapon_rack(w, dx0 + 1, 3, 12, "east", items=("minecraft:wooden_sword", "minecraft:bamboo",
                                                      "minecraft:bow"))
    K.lamp(w, 3, 5, 13)
    # ------------------------------------------------------------------ the craftsmen's shed (north-west)
    sx0, sx1, sz0, sz1 = -13, -3, -19, -14
    w.fill(sx0, 0, sz0, sx1, 0, sz1, F("packed_mud"))
    for x in (sx0, sx1, (sx0 + sx1) // 2):
        for z in (sz0, sz1):
            w.fill(x, 1, z, x, 3, z, B.log("dark_oak", "y"))
    w.fill(sx0, 1, sz0, sx1, 3, sz0, F("spruce_planks"))
    for x in (sx0, sx1, (sx0 + sx1) // 2):
        w.fill(x, 1, sz0, x, 3, sz0, B.log("dark_oak", "y"))
    for x in range(sx0 - 1, sx1 + 2):
        for z in range(sz0 - 1, sz1 + 2):
            w.set(x, 4, z, SL("kawara_roof_tiles", "top" if z < sz1 else "bottom"))
    h.stations_row(w, [(x, sz0 + 1) for x in range(sx0 + 1, sx1)], 0, "south",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "cartography_table", "furnace", "smoker", "fletching_table"))
    h.station(w, sx0 + 1, 1, sz1, "brewing_stand")
    h.station(w, sx1 - 1, 1, sz1, "blast_furnace", "north")
    K.lamp(w, -8, 3, -16)
    # ------------------------------------------------------------------ the garden: pond, bridge, lanterns, torii
    K.pond(w, 15, 9, 5.0, 3.6, 0, border=("stone", "mossy_cobblestone", "andesite"), depth=2, seed=112, lily=0.12)
    for (x, y) in ((15, 1), (15, 1)):
        pass
    for z in range(5, 14):                                           # the red arched bridge across the pond
        y = 1 + (1 if 7 <= z <= 11 else 0)
        w.set(15, y, z, SL("crimson", "bottom" if y == 1 else "top") if z in (5, 13) else F("crimson_planks"))
        w.set(14, y + 1, z, B.fence("crimson"))
        w.set(16, y + 1, z, B.fence("crimson"))
        for k in range(y + 1, y + 4):
            if k > y + 1:
                w.set(15, k, z, AIR)
    for (x, z) in ((-9, -3), (-9, 3), (-3, 8), (11, -12), (19, 2), (12, 15)):
        if (x, z) in cells:
            K.stone_lantern(w, x, 0, z)
    K.torii(w, -11, 0, 0, axis="x", width=5, height=6, wood="crimson")
    for x in range(-10, -7):                                         # raked gravel garden with rocks
        pass
    for x in range(14, 21):
        for z in range(-8, -1):
            if (x, z) in cells:
                w.set(x, 0, z, F("gravel"))
    for (x, z, r) in ((16, -6, 1.2), (19, -3, 0.9), (17, -3, 0.8)):
        K.rock(w, x, 0, z, r=r, states=("andesite", "stone", "mossy_cobblestone"), seed=x + z)
    for (x, z) in ((-12, -12), (-12, 12), (18, -12), (19, 14), (-6, 17), (12, -18)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "cherry", seed=x + 7 * z, height=5, crown=3.2)
            for (dx, dz) in ((1, 0), (-1, 1), (0, -1), (2, 1)):
                w.set(x + dx, 1, z + dz, B.S("pink_petals", facing="north", flower_amount=2), only_empty=True)
    for x in range(-14, -10):                                         # bamboo grove
        for z in range(10, 18):
            if (x, z) in cells and hash2(x, z, 113) < 0.45:
                for y in range(1, 6 + int(hash2(z, x, 114) * 4)):
                    w.set(x, y, z, B.S("bamboo", age=1, leaves="large" if y > 4 else "none", stage=0))
    # the bell tower (shōrō) by the pond
    bx, bz = 19, -12
    if (bx, bz) in cells:
        pass
    bx, bz = 17, -15
    w.fill(bx - 1, 0, bz - 1, bx + 1, 0, bz + 1, F("stone_bricks"))
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        w.fill(bx + dx, 1, bz + dz, bx + dx, 4, bz + dz, B.log("dark_oak", "y"))
    w.fill(bx - 1, 5, bz - 1, bx + 1, 5, bz + 1, F("dark_oak_planks"))
    S.hip_roof(w, bx - 1, bz - 1, bx + 1, bz + 1, 6, "kawara_roof_tiles", overhang=1, pitch=1)
    h.bell(w, bx, 4, bz, facing="east", attachment="ceiling")
    K.path(w, [(-13, 0), (hx0 - 2, 0)], 0, ["gravel", "smooth_stone", "gravel"], width=3, seed=115)
    K.path(w, [(5, -12), (5, hz0 - 2)], 0, ["gravel", "smooth_stone"], width=1, seed=116)
    K.path(w, [(3, 8), (3, hz1 + 2)], 0, ["gravel", "smooth_stone"], width=1, seed=117)
    K.path(w, [(-8, -13), (-8, -6), (hx0 - 2, -6)], 0, ["gravel", "smooth_stone"], width=1, seed=118)
    K.scatter_plants(w, cells, 0, seed=119, kinds=("grass", "fern", "azure_bluet", "lily_of_the_valley"),
                     density=0.15, on=("grass_block", "moss_block"))
    return h.finish()


# ====================================================================================== 森の大樹
def _blob(w, cx, cy, cz, r, state, seed, ry=None, rough=0.4):
    GK.ball(w, cx, cy, cz, r, state, ry=ry or r * 0.75, only_empty=True, seed=seed, rough=rough)


def great_tree(w):
    """森の大樹: a giant tree on a forest island, its hollow trunk the guild hall. Roots flare into arches round the
    entrance; inside, a spiral stair climbs the trunk wall past the hall (board, trophies, chests in root
    alcoves), the council room and the library with the enchanting nook. Decks on its great branches carry the
    workshop and a lounge; a rope bridge leads to a birch with a lookout pod. Glow berries hang from the
    canopy; dummies stand in a clearing ringed by ferns, and the bell hangs from a low bough."""
    h = K.House(w, "great_tree")
    cells = K.island(w, 0, 0, 22.5, 22.0, depth=16, surface=("grass_block", "moss_block", "podzol", "grass_block"),
                     rock=("stone", "andesite", "dirt", "tuff"), seed=121)
    for (x, z) in sorted(cells):                                     # roots hanging under the island
        if hash2(x, z, 122) < 0.05:
            y = -3
            while y > -30 and not w.is_empty(x, y, z):
                y -= 1
            for k in range(1 + int(hash2(z, x, 123) * 4)):
                w.set(x, y - k, z, B.S("hanging_roots"))
    h.gate(mass=("dirt", "moss_block", "stone", "rooted_dirt", "mossy_cobblestone"), mass_half=10, mass_depth=6)
    wood, bark = B.S("dark_oak_wood", axis="y"), B.S("spruce_wood", axis="y")
    cx, cz, R = 6, 0, 5
    floors = (0, 9, 17)
    top = 30
    # ------------------------------------------------------------------ trunk, roots, branches
    for y in range(-2, top + 1):
        r = R + (2.4 if y <= 1 else 1.2 if y <= 3 else 0) - max(0, y - 24) * 0.25
        for dx in range(-9, 10):
            for dz in range(-9, 10):
                d = math.hypot(dx, dz) + fbm2(dx + y, dz, 3.0, 2, 124) * 0.6
                if d <= r:
                    w.set(cx + dx, y, cz + dz, wood if hash3(dx, y, dz, 125) < 0.8 else bark)
    S.cylinder(w, cx, cz, R - 1.2, 1, 26, AIR)
    for (ang, length) in ((20, 9), (110, 8), (200, 9), (290, 8), (65, 7), (245, 7), (155, 6), (335, 7)):
        a = math.radians(ang)
        for k in range(0, length + 1):                                # roots flaring to the ground
            x = cx + int(round((R + k) * math.cos(a)))
            z = cz + int(round((R + k) * math.sin(a)))
            y = int(3 - k * 0.55)
            for yy in range(min(0, y), max(0, y) + 1):
                w.set(x, yy, z, wood)
            if y >= 1:
                w.set(x, y, z, wood)
    for (ang, y0, length) in ((30, 12, 11), (150, 12, 10), (260, 13, 11), (90, 20, 10), (210, 21, 9),
                              (330, 22, 10)):
        a = math.radians(ang)
        for k in range(0, length + 1):                                # branches
            x = cx + int(round((R + k) * math.cos(a)))
            z = cz + int(round((R + k) * math.sin(a)))
            y = y0 + int(k * 0.45)
            w.set(x, y, z, B.log("dark_oak", "x" if abs(math.cos(a)) > 0.7 else "z"))
            w.set(x, y - 1, z, B.log("dark_oak", "x" if abs(math.cos(a)) > 0.7 else "z"))
        ex = cx + int(round((R + length) * math.cos(a)))
        ez = cz + int(round((R + length) * math.sin(a)))
        _blob(w, ex, y0 + int(length * 0.45) + 3, ez, 4.6, B.leaves("oak"), seed=ang, ry=3.2)
        _blob(w, ex, y0 + int(length * 0.45) + 4, ez, 3.4, B.leaves("azalea"), seed=ang + 1, ry=2.4)
    for (dx, dy, dz, r) in ((0, 34, 0, 9.5), (5, 31, 6, 6.0), (-6, 32, -4, 6.4), (6, 30, -6, 5.5), (-5, 30, 6, 5.6)):
        _blob(w, cx + dx, dy, cz + dz, r, B.leaves("oak"), seed=dx * 7 + dz, ry=r * 0.6)
        _blob(w, cx + dx, dy + 2, cz + dz, r * 0.7, B.leaves("flowering_azalea"), seed=dx + dz, ry=r * 0.4)
    for y in range(26, top + 2):
        S.cylinder(w, cx, cz, R - 1.2, y, y, wood)
    for (x, z) in sorted(cells):                                       # glow berries hanging from the canopy
        if hash2(x, z, 126) < 0.12:
            y = 40
            while y > 14 and w.is_empty(x, y, z):
                y -= 1
            if y > 14 and "leaves" in w.get(x, y, z) and w.is_empty(x, y - 1, z):
                n = 1 + int(hash2(z, x, 127) * 4)
                for k in range(1, n + 1):
                    if w.is_empty(x, y - k, z):
                        w.set(x, y - k, z, B.S("cave_vines_plant", berries=True) if k < n else
                              B.S("cave_vines", age=25, berries=True))
    # ------------------------------------------------------------------ inside the trunk: floors, stair, rooms
    S.cylinder(w, cx, cz, R - 1.2, 0, 0, F("spruce_planks"))
    for fy in floors[1:]:
        S.cylinder(w, cx, cz, R - 1.2, fy, fy, F("spruce_planks"))
    K.clear(w, cx - R - 2, 1, -1, cx - R + 1, 4, 1)                    # the entrance (west) between roots
    w.fill(cx - R - 2, 0, -1, cx - R + 1, 0, 1, F("spruce_planks"))
    for z in (-1, 1):
        w.set(cx - R, 4, z, ST("dark_oak", "south" if z < 0 else "north", "top"))
    for (dx, dz) in ((0, -R), (0, R), (R, 0), (-3, -3), (3, 3), (3, -3)):   # round windows
        for fy in (2, 11, 19):
            if (dx, dz, fy) != (R, 0, 2):                                     # the board hangs there
                w.set(cx + dx, fy + 1, cz + dz, B.pane("lime"))
    h.board(w, cx + R - 1, 2, cz, "west")
    K.trophy_wall(h, w, [(cx + 2, cz - 3), (cx + 2, cz + 3), (cx - 2, cz - 3), (cx - 2, cz + 3)], 3, "south")
    for (x, z, f) in ((cx + 2, cz + 3, "north"), (cx - 2, cz + 3, "north")):
        pass
    for k, (x, z) in enumerate(((cx, cz - 3), (cx, cz + 3))):
        w.set(x, 1, z, B.carpet("green"))
    K.lamp(w, cx, 7, cz, chain=1)
    # root alcoves with the chests (dug into the flaring base)
    for (dx, dz, f) in ((-2, -4, "south"), (2, -4, "south"), (-2, 4, "north"), (2, 4, "north")):
        K.clear(w, cx + dx, 2, cz + dz, cx + dx, 3, cz + dz)
        h.chest(w, cx + dx, 1, cz + dz, f)
        w.set(cx + dx, 3, cz + dz, B.lantern(hanging=True))
        w.set(cx + dx, 4, cz + dz, wood)
    # council room (floor 1): table and seats; the vault
    fy = floors[1]
    K.council(w, cx - 1, fy, cz, 3, axis="x", top="dark_oak", cloth="green", chair="spruce")
    h.vault(w, cx + R - 2, fy + 1, cz, "west")
    K.lamp(w, cx, fy + 6, cz)
    # library (floor 2): the enchanting nook in the middle
    fy = floors[2]
    h.enchanting_nook(w, cx, fy, cz, open_side="west")
    K.lamp(w, cx, fy + 6, cz) if w.is_empty(cx, fy + 6, cz) else None
    # the spiral stair winding up round the outside of the trunk, from beside the entrance (west) by the north to
    # the lounge deck (east), with doorways into each floor
    sr = R + 1.6
    path = K.ring_path(cx, cz, sr)

    def ang(c):
        return math.degrees(math.atan2(c[1] - cz, c[0] - cx)) % 360

    start = min(range(len(path)), key=lambda i: abs(ang(path[i]) - 200))
    steps = K.spiral_stair(w, cx, cz, sr, floors[0], floors[2], wood="spruce", start=start)
    for (x, y, z) in steps:
        ox, oz = x - cx, z - cz
        n = math.hypot(ox, oz)
        px, pz = x + int(round(ox / n)), z + int(round(oz / n))
        if w.is_empty(px, y + 1, pz) or "leaves" in w.get(px, y + 1, pz):
            w.set(px, y + 1, pz, B.fence("spruce"))
        if y % 4 == 0:
            w.set(px, y, pz, B.log("dark_oak", "y"))
    for fy in floors[1:]:
        x, y, z = next(c for c in steps if c[1] == fy)
        ox, oz = cx - x, cz - z
        n = math.hypot(ox, oz)
        for k in range(1, 5):
            px, pz = x + int(round(ox * k / n)), z + int(round(oz * k / n))
            if math.hypot(px - cx, pz - cz) < R - 1.2:
                break
            w.set(px, fy, pz, F("spruce_planks"))
            K.clear(w, px, fy + 1, pz, px, fy + 2, pz)
    # ------------------------------------------------------------------ the decks: workshop and lounge
    def deck(x0, z0, x1, z1, y, rail=True):
        w.fill(x0, y, z0, x1, y, z1, F("spruce_planks"))
        if rail:
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        w.set(x, y + 1, z, B.fence("spruce"))
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            for yy in range(y - 1, -1, -1):
                if not w.is_empty(x, yy, z):
                    break
                w.set(x, yy, z, B.log("spruce", "y"))
    wy = 9
    deck(cx + 4, -10, cx + 14, -4, wy)
    for z in (-6, -5):                                               # door from the council floor onto the deck
        pass
    K.clear(w, cx + 3, wy + 1, -5, cx + 4, wy + 3, -4)
    w.set(cx + 4, wy + 1, -4, AIR)
    h.stations_row(w, [(x, -9) for x in range(cx + 5, cx + 14)], wy, "south",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "cartography_table", "furnace", "fletching_table"))
    for (x, kind) in ((cx + 13, "smoker"), (cx + 12, "brewing_stand"), (cx + 11, "blast_furnace")):
        h.station(w, x, wy + 1, -5, kind, "north")
    for x in (cx + 6, cx + 10):
        w.fill(x, wy + 1, -4, x, wy + 4, -4, B.log("spruce", "y"))
        K.lamp(w, x, wy + 4, -5)
    for x in range(cx + 5, cx + 14):
        w.set(x, wy + 5, -10, SL("shingle_roof_tiles", "bottom"))
        w.set(x, wy + 5, -9, SL("shingle_roof_tiles", "bottom"))
    for x in (cx + 5, cx + 13):
        w.fill(x, wy + 1, -10, x, wy + 4, -10, B.log("spruce", "y"))
    # the lounge deck on the east, where the stair arrives
    ly = 17
    deck(cx + 8, -4, cx + 14, 4, ly)
    for z in (-1, 0, 1):
        w.set(cx + 8, ly + 1, z, AIR)
    K.lounge(w, cx + 10, ly, -3, "south", wood="spruce", carpet="green", border="lime", length=3)
    # ------------------------------------------------------------------ the birch with the lookout pod and bridge
    px, pz = -10, -12
    for y in range(1, 17):
        w.set(px, y, pz, B.log("birch", "y"))
    _blob(w, px, 19, pz, 4.0, B.leaves("birch"), seed=128, ry=2.6)
    w.fill(px - 2, 14, pz - 2, px + 2, 14, pz + 2, F("birch_planks"))
    for x in range(px - 2, px + 3):
        for z in range(pz - 2, pz + 3):
            if x in (px - 2, px + 2) or z in (pz - 2, pz + 2):
                w.set(x, 15, z, B.fence("birch"))
    for y in range(1, 14):                                              # a ladder up the birch
        w.set(px + 1, y, pz, B.S("ladder", facing="east"))
    w.set(px + 1, 14, pz, B.S("birch_trapdoor", facing="east", half="top", open=True))
    for k in range(0, 15):                                              # the rope bridge to the trunk
        x = px + 2 + k
        z = pz + 2 + int(k * 0.6)
        y = 14 - (1 if 4 <= k <= 10 else 0)
        w.set(x, y, z, F("spruce_planks"))
    # ------------------------------------------------------------------ the clearing: dummies, target, bell
    K.mix(w, -6, 0, 10, 4, 0, 17, [F("grass_block"), F("coarse_dirt"), F("moss_block")], 129)
    K.training_ground(h, w, [(-4, 14), (-1, 15), (2, 14)], 0, 180.0, looks=(0, 1, 2))
    h.target(w, 3, 0, 17)
    bx, bz = -2, 8
    w.fill(bx, 1, bz - 2, bx, 5, bz - 2, B.log("dark_oak", "y"))
    w.fill(bx, 6, bz - 2, bx, 6, bz, B.log("dark_oak", "z"))
    h.bell(w, bx, 5, bz, facing="east", attachment="ceiling")
    w.set(bx, 6, bz, F("dark_oak_planks"))
    K.path(w, [(-13, 0), (cx - R - 3, 0)], 0, ["podzol", "coarse_dirt", "moss_block"], width=3, seed=130)
    for (x, z) in ((-16, -10), (-15, 12), (17, -12), (17, 12), (-4, -18), (12, 17), (-18, 3)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "oak" if (x + z) % 2 else "birch", seed=x + z * 5, height=6)
    K.scatter_plants(w, cells, 0, seed=131, kinds=("fern", "grass", "fern", "lily_of_the_valley", "azalea",
                                                  "red_mushroom", "brown_mushroom"), density=0.3,
                     on=("grass_block", "moss_block", "podzol"))
    for (x, z) in ((-8, -3), (-8, 3)):
        deco.lamp_post(w, x, 0, z, height=3)
    return h.finish()


# ====================================================================================== 賢者の大図書館
def sage_library(w):
    """賢者の大図書館: a great library in a walled garden. Under a coffered vault the reading hall rises three
    galleries high, its walls all books; long tables with lamps run down the nave to the enchanting nook on a dais,
    and beyond it the domed rotunda holds a great globe, the master's desk and the trophy wall. A clock tower stands
    over the entrance, the archive keeps the chests and the vault behind a grille, the bindery is the workshop, and
    dummies stand in the cloister garden by a bell."""
    h = K.House(w, "sage_library")
    cells = K.island(w, 0, 0, 22.5, 22.0, depth=16, surface=("grass_block", "moss_block", "grass_block"),
                     rock=("stone", "andesite", "stone_bricks", "stone"), seed=141)
    K.drip_stalactites(w, cells, 0, seed=141)
    h.gate(mass=("stone_bricks", "mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks"), mass_half=10,
           mass_depth=6)
    wall, trim, floor_ = F("stone_bricks"), F("smooth_stone"), F("polished_andesite")
    # ------------------------------------------------------------------ the reading hall x -6..14, z -7..7, y 1..16
    hx0, hx1, hz0, hz1, top = -6, 14, -7, 7, 15
    w.fill(hx0, 0, hz0, hx1, 0, hz1, floor_)
    for x in range(hx0 + 1, hx1):
        for z in range(hz0 + 1, hz1):
            if (x + z) % 2 == 0:
                w.set(x, 0, z, F("polished_diorite"))
    _box_walls(w, hx0, hz0, hx1, hz1, 1, top, wall)
    K.clear(w, hx0 + 1, 1, hz0 + 1, hx1 - 1, top + 6, hz1 - 1)
    for y in (6, 11):                                                 # string courses
        _box_walls(w, hx0, hz0, hx1, hz1, y, y, trim)
    for x in range(hx0 + 2, hx1 - 1, 3):                              # tall windows on both long sides
        for z in (hz0, hz1):
            for y0 in (2, 7, 12):
                w.fill(x, y0, z, x, y0 + 2, z, B.pane("light_blue"))
    S.gable_roof(w, hx0, hz0, hx1, hz1, top + 1, "slate_roof_tiles", axis="x", overhang=1, pitch=1,
                 gable_state=wall)
    for x in range(hx0 + 1, hx1):                                     # coffered ceiling
        for z in range(hz0 + 1, hz1):
            w.set(x, top, z, F("dark_oak_planks") if (x % 3 == 0 or z % 3 == 0) else F("spruce_planks"))
    # galleries (y 5 and y 10) along both long walls, bookshelves on every wall
    for gy in (5, 10):
        for x in range(hx0 + 1, hx1):
            for z in (hz0 + 1, hz0 + 2, hz1 - 2, hz1 - 1):
                w.set(x, gy, z, F("dark_oak_planks"))
            w.set(x, gy + 1, hz0 + 3, B.fence("dark_oak"))
            w.set(x, gy + 1, hz1 - 3, B.fence("dark_oak"))
    for x in range(hx0 + 1, hx1):
        for z in (hz0 + 1, hz1 - 1):
            for y in list(range(1, 5)) + list(range(6, 10)) + list(range(11, 14)):
                if w.get(x, y, z + (-1 if z == hz0 + 1 else 1)).startswith("minecraft:light_blue"):
                    continue
                w.set(x, y, z, B.S("bookshelf"))
    # stairs up to the galleries (straight runs along the west wall)
    FN.stair_up(w, hx0 + 1, hz0 + 3, 0, 5, "east", wood="dark_oak")
    FN.stair_up(w, hx0 + 1, hz1 - 3, 5, 5, "east", wood="dark_oak")
    # the entrance (west front) under the clock tower
    K.clear(w, hx0, 1, -1, hx0, 4, 1)
    for z in (-1, 1):
        w.set(hx0, 4, z, ST("stone_brick", "south" if z < 0 else "north", "top"))
    tx = hx0 - 4
    w.fill(tx, 0, -3, hx0 - 1, 0, 3, floor_)
    for (x, z) in ((tx, -3), (tx, 3)):
        w.fill(x, 1, z, x, 5, z, B.S("quartz_pillar", axis="y"))
    w.fill(tx, 6, -3, hx0 - 1, 6, 3, trim)
    for y in range(7, 24):                                            # the clock tower over the porch
        _box_walls(w, tx, -3, hx0 - 1, 3, y, y, wall)
    K.clear(w, tx + 1, 7, -2, hx0 - 2, 22, 2)
    K.TK.clock(w, tx, 19, 0, "west", size=2.2)
    for z in (-3, 3):
        w.set(tx, 19, z, trim)
    S.square_spire(w, tx + (hx0 - 1 - tx) // 2, 0, 3, 24, 10, "slate_roof_tiles", finial=True)
    # nave: long reading tables with lamps
    for z in (-2, 2):
        deco.long_table(w, 0, 0, z, 9, axis="x", top="dark_oak", cloth=None, chairs=True, chair_wood="spruce")
        for x in (1, 4, 7):
            w.set(x, 2, z, B.lantern() if x != 4 else B.S("lectern", facing="north", has_book=True))
    # dais with the enchanting nook at the east end of the nave
    w.fill(9, 1, -3, 13, 1, 3, F("polished_deepslate"))
    h.enchanting_nook(w, 11, 1, 0, open_side="west")
    for (x, z) in ((10, -4), (12, 4)):
        deco.chandelier(w, x, top, z, drop=4)
    for x in (2, 6):
        deco.chandelier(w, x, top, 0, drop=4)
    # ------------------------------------------------------------------ the rotunda (east): globe, desk, trophies
    rcx, rcz, rr = hx1 + 6, 0, 5
    S.cylinder(w, rcx, rcz, rr + 0.4, 0, 0, floor_)
    S.cylinder(w, rcx, rcz, rr + 0.4, 1, 11, wall, hollow=True, thickness=1.1)
    S.cylinder(w, rcx, rcz, rr - 0.6, 1, 11, AIR)
    S.dome(w, rcx, 11, rcz, rr + 0.4, F("verdigris_roof_tiles"), ry=6, hollow=True, thickness=1.1)
    w.set(rcx, 18, rcz, B.S("gilded_finial"))
    K.clear(w, hx1, 1, -2, hx1 + 1, 5, 2)
    for (dx, dz) in ((rr, 0), (0, -rr), (0, rr), (3, -4), (3, 4)):
        w.fill(rcx + dx, 3, rcz + dz, rcx + dx, 6, rcz + dz, B.pane("light_blue"))
    GK.ball(w, rcx, 4, rcz, 1.8, B.wool("blue"))
    for (dx, dy, dz) in ((1, 4, 1), (-1, 5, 0), (0, 3, -1), (1, 5, -1), (-1, 3, 1)):
        w.set(rcx + dx, dy, rcz + dz, B.wool("lime") if (dx + dz) % 2 else B.wool("green"))
    w.set(rcx, 1, rcz, F("gold_block"))
    w.set(rcx, 2, rcz, B.S("chain"))
    w.set(rcx + 3, 1, rcz, SL("dark_oak", "top"))
    w.set(rcx + 3, 1, rcz - 1, SL("dark_oak", "top"))
    w.set(rcx + 4, 1, rcz, ST("dark_oak", "east"))
    w.set(rcx + 3, 2, rcz, B.candle(3, "white"))
    for (x, z, f) in ((rcx - 2, rcz - 4, "south"), (rcx + 2, rcz - 4, "south"), (rcx - 2, rcz + 4, "north"),
                      (rcx + 2, rcz + 4, "north")):
        h.frame(w, x, 3, z, f)
    deco.chandelier(w, rcx, 15, rcz, drop=4)
    # ------------------------------------------------------------------ the board by the entrance
    w.fill(hx0 + 1, 1, 3, hx0 + 1, 4, 5, wall)
    h.board(w, hx0 + 2, 2, 4, "east")
    # ------------------------------------------------------------------ the archive (north): chests and the vault
    ax0, ax1, az0, az1 = -2, 10, -17, -9
    w.fill(ax0, 0, az0, ax1, 0, az1, floor_)
    _box_walls(w, ax0, az0, ax1, az1, 1, 6, wall)
    K.clear(w, ax0 + 1, 1, az0 + 1, ax1 - 1, 6, az1 - 1)
    S.gable_roof(w, ax0, az0, ax1, az1, 7, "slate_roof_tiles", axis="x", overhang=1, pitch=1, gable_state=wall)
    K.clear(w, 4, 1, az1, 4, 3, hz0)
    w.fill(4, 0, az1, 4, 0, hz0, floor_)
    w.fill(3, 1, az1 + 1, 3, 4, hz0 - 1, wall)
    w.fill(5, 1, az1 + 1, 5, 4, hz0 - 1, wall)
    w.fill(3, 5, az1 + 1, 5, 5, hz0 - 1, wall)
    K.clear(w, 4, 1, hz0, 4, 3, hz0)
    K.storage_wall(h, w, [(x, az0 + 1) for x in range(ax0 + 1, ax0 + 9)], 0, "south", between=B.S("bookshelf"))
    h.vault(w, ax1 - 1, 1, az0 + 1, "south")
    w.fill(ax1 - 2, 1, az0 + 2, ax1 - 1, 3, az0 + 2, B.S("iron_bars"))
    w.set(ax1 - 2, 1, az0 + 1, B.S("bookshelf"))
    for x in (1, 7):
        K.lamp(w, x, 5, -13)
    for x in range(ax0 + 1, ax1 - 2):
        w.set(x, 1, az1 - 1, B.S("chiseled_bookshelf", facing="north"))
    # ------------------------------------------------------------------ the bindery (south): the workshop
    bx0, bx1, bz0, bz1 = -2, 10, 9, 15
    w.fill(bx0, 0, bz0, bx1, 0, bz1, floor_)
    _box_walls(w, bx0, bz0, bx1, bz1, 1, 5, wall)
    K.clear(w, bx0 + 1, 1, bz0 + 1, bx1 - 1, 5, bz1 - 1)
    S.gable_roof(w, bx0, bz0, bx1, bz1, 6, "slate_roof_tiles", axis="x", overhang=1, pitch=1, gable_state=wall)
    K.clear(w, 4, 1, hz1, 4, 3, bz0)
    w.fill(4, 0, hz1, 4, 0, bz0, floor_)
    w.fill(3, 1, hz1 + 1, 3, 4, bz0 - 1, wall)
    w.fill(5, 1, hz1 + 1, 5, 4, bz0 - 1, wall)
    w.fill(3, 5, hz1 + 1, 5, 5, bz0 - 1, wall)
    h.stations_row(w, [(x, bz1 - 1) for x in range(bx0 + 1, bx1)], 0, "north",
                   kinds=("crafting_table", "cartography_table", "loom", "stonecutter", "anvil", "smithing_table",
                          "grindstone", "furnace", "brewing_stand", "fletching_table", "smoker"))
    h.station(w, bx0 + 1, 1, bz0 + 1, "blast_furnace", "south")
    for x in (1, 7):
        K.lamp(w, x, 4, 12)
    # ------------------------------------------------------------------ the cloister garden (south-west): dummies, bell
    K.mix(w, -14, 0, 7, -5, 0, 16, [F("grass_block"), F("grass_block"), F("moss_block")], 142)
    for x in range(-14, -4):
        for z in (7, 16):
            w.set(x, 1, z, B.leaves("azalea"))
    K.training_ground(h, w, [(-12, 12), (-9, 13), (-6, 12)], 0, 180.0, looks=(0, 1, 0))
    h.target(w, -9, 0, 9)
    for z in (10, 14):
        w.fill(-15, 1, z, -15, 4, z, B.S("quartz_pillar", axis="y"))
    w.fill(-15, 5, 10, -15, 5, 14, trim)
    h.bell(w, -15, 4, 12, facing="east", attachment="ceiling")
    w.set(-15, 5, 12, wall)
    K.path(w, [(-13, 0), (tx - 1, 0)], 0, ["polished_andesite", "stone_bricks", "smooth_stone"], width=3, seed=143)
    for (x0_, z0_, x1_, z1_) in ((-12, -6, -9, -3), (-12, 3, -9, 6)):
        deco.flower_bed(w, x0_, z0_, x1_, z1_, 0, seed=x0_ + z0_, border="stone_brick")
    for (x, z) in ((-16, -12), (-17, 4), (18, -14), (18, 13), (-6, -19), (12, 19), (6, -19)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "oak", seed=x * 11 + z, height=5)
    for (x, z) in ((-8, -2), (-8, 2)):
        deco.lamp_post(w, x, 0, z, height=3)
    K.scatter_plants(w, cells, 0, seed=144, kinds=("grass", "fern", "azure_bluet", "oxeye_daisy"), density=0.2,
                     on=("grass_block",))
    return h.finish()


# ====================================================================================== 密林の黄金神殿
JUNGLE = ("mossy_stone_bricks", "mossy_cobblestone", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks",
          "cobblestone")


def jungle_temple(w):
    """密林の黄金神殿: a stepped pyramid with a golden shrine on its summit, rising from the jungle. Serpent heads flank
    the grand stair; inside the pyramid a pillared hall lit by gold and torches holds the board, the trophy wall
    and the chests in glyph-carved niches, the treasure vault heaped with gold, and the council of stone seats.
    The summit shrine keeps the enchanting nook under a gilded roof. Thatched huts are the workshop, dummies stand
    on the plaza, a waterfall pours into a pool among jungle giants hung with vines, and the bell hangs in a stone
    arch."""
    h = K.House(w, "jungle_temple")
    cells = K.island(w, 0, 0, 22.5, 22.0, depth=16, surface=("grass_block", "moss_block", "podzol", "grass_block"),
                     rock=("stone", "mossy_cobblestone", "andesite", "dirt"), seed=151)
    K.drip_stalactites(w, cells, 0, seed=151, kinds=("mossy_cobblestone", "stone"))
    h.gate(mass=JUNGLE, mass_half=11, mass_depth=6)
    # ------------------------------------------------------------------ the pyramid: base x -3..21, z -12..12
    pcx, pcz, half = 9, 0, 12
    tiers = 5
    w.fill(pcx - half, 0, pcz - half, pcx + half, 0, pcz + half, F("stone_bricks"))
    for t in range(tiers):
        hh = half - t * 2
        y0, y1 = t * 3 + 1, t * 3 + 3
        for x in range(pcx - hh, pcx + hh + 1):
            for z in range(pcz - hh, pcz + hh + 1):
                for y in range(y0, y1 + 1):
                    w.set(x, y, z, F(JUNGLE[int(hash3(x, y, z, 152) * len(JUNGLE)) % len(JUNGLE)]))
        for x in range(pcx - hh, pcx + hh + 1):                       # gold bands and glyph stones
            for z in (pcz - hh, pcz + hh):
                w.set(x, y1, z, F("gilded_trim") if (x + t) % 4 == 0 else F("chiseled_stone_bricks"))
        for z in range(pcz - hh, pcz + hh + 1):
            for x in (pcx - hh, pcx + hh):
                w.set(x, y1, z, F("gilded_trim") if (z + t) % 4 == 0 else F("chiseled_stone_bricks"))
    ptop = tiers * 3
    # the grand stair up the west face: from the plaza (x -10, y 1) to the summit (x 4, y 15), 3 wide
    for k in range(ptop):
        x, y = -10 + k, 1 + k
        for z in (-1, 0, 1):
            w.fill(x, 0, z, x, y - 1, z, F("stone_bricks"))
            w.set(x, y, z, ST("stone_brick", "east"))
            for yy in range(y + 1, y + 4):
                w.set(x, yy, z, AIR)
        for z in (-2, 2):                                             # balustrades
            w.fill(x, 0, z, x, y, z, F("mossy_stone_bricks"))
            w.set(x, y + 1, z, WL("mossy_stone_bricks") if k % 3 else F("gilded_trim"))
    for z in (-2, 2):                                                 # serpent heads at the foot of the stair
        w.fill(-11, 1, z, -11, 2, z, F("mossy_stone_bricks"))
        w.set(-12, 2, z, ST("mossy_stone_bricks", "west", "top"))
        w.set(-11, 3, z, F("emerald_block"))
    sy = ptop
    # the summit shrine with a gilded roof: the enchanting nook
    for x in range(pcx - 3, pcx + 4):
        for z in range(pcz - 3, pcz + 4):
            if abs(x - pcx) == 3 or abs(z - pcz) == 3:
                corner = abs(x - pcx) == 3 and abs(z - pcz) == 3
                for y in range(sy + 1, sy + 5):
                    w.set(x, y, z, F("chiseled_stone_bricks") if corner else F("mossy_stone_bricks"))
    K.clear(w, pcx - 2, sy + 1, pcz - 2, pcx + 2, sy + 4, pcz + 2)
    K.clear(w, pcx - 3, sy + 1, -1, pcx - 3, sy + 3, 1)
    S.hip_roof(w, pcx - 3, pcz - 3, pcx + 3, pcz + 3, sy + 5, "gilded_roof_tiles", overhang=1, pitch=1)
    h.enchanting_nook(w, pcx, sy, pcz, open_side="west")
    w.set(pcx, sy + 9, pcz, B.S("gilded_finial"))
    for z in (-2, 2):
        w.set(pcx - 4, sy + 1, z, B.S("lantern"))
    # ------------------------------------------------------------------ the hall inside the pyramid (y 1..7)
    ix0, ix1, iz0, iz1 = pcx - 6, pcx + 6, -6, 6
    K.clear(w, ix0, 1, iz0, ix1, 7, iz1)
    K.mix(w, ix0, 0, iz0, ix1, 0, iz1, [F("polished_andesite"), F("chiseled_stone_bricks"), F("stone_bricks")], 153)
    for x in range(ix0, ix1 + 1):
        w.set(x, 0, 0, F("gilded_trim") if x % 3 == 0 else F("smooth_stone"))
    # the entrance: a corridor from the north face
    K.clear(w, pcx - 1, 1, pcz - half, pcx + 1, 4, iz0 - 1)
    w.fill(pcx - 1, 0, pcz - half - 2, pcx + 1, 0, iz0 - 1, F("stone_bricks"))
    for x in (pcx - 1, pcx + 1):
        w.set(x, 4, pcz - half, ST("stone_brick", "west" if x < pcx else "east", "top"))
    w.set(pcx, 5, pcz - half, F("gold_block"))
    for x in (pcx - 2, pcx + 2):
        w.set(x, 3, pcz - half - 1, B.S("wall_torch", facing="north"))
    for x in range(ix0 + 2, ix1 - 1, 4):                               # pillars with gold capitals
        for z in (-3, 3):
            w.fill(x, 1, z, x, 6, z, F("chiseled_stone_bricks"))
            w.set(x, 7, z, F("gilded_trim"))
    for x in range(ix0, ix1 + 1):
        for z in (iz0 - 1, iz1 + 1):
            if x % 3 == 0:
                w.set(x, 3, z, F("chiseled_stone_bricks"))
    h.board(w, ix0, 2, -2, "east")
    w.fill(ix0 - 1, 1, -3, ix0 - 1, 4, -1, F("chiseled_stone_bricks"))
    K.trophy_wall(h, w, [(ix0, 1), (ix0, 2), (ix0, 3), (ix0, 4)], 3, "east")
    for x in (ix0 + 2, ix0 + 5, ix0 + 8, ix0 + 11):                   # chests in glyph niches (south wall)
        h.chest(w, x, 1, iz1, "north")
        w.set(x, 3, iz1 + 1, F("chiseled_stone_bricks"))
    K.council(w, pcx - 2, 0, 0, 5, axis="x", top="spruce", cloth="lime", chair="stone_brick")
    # the treasure vault (east end) heaped with gold
    w.fill(ix1 - 1, 1, -3, ix1, 1, 3, F("gold_block"))
    w.fill(ix1, 2, -2, ix1, 2, 2, F("raw_gold_block"))
    w.set(ix1, 3, 0, F("gold_block"))
    h.vault(w, ix1 - 2, 1, 0, "west")
    for z in (-3, 3):
        w.set(ix1 - 2, 1, z, B.S("decorated_pot"))
    for (x, z) in ((ix0 + 4, 0), (ix0 + 9, 0)):
        w.set(x, 8, z, F("glowstone"))
        K.lamp(w, x, 7, z)
    for x in range(ix0 + 2, ix1 - 1, 4):
        for z in (-2, 2):
            w.set(x, 4, z, B.S("wall_torch", facing="south" if z > 0 else "north"))
    # ------------------------------------------------------------------ the thatched huts (north): workshop
    for (hx, hz) in ((-6, -17), (4, -18), (14, -17)):
        for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
            w.fill(hx + dx, 1, hz + dz, hx + dx, 3, hz + dz, B.log("jungle", "y"))
        S.hip_roof(w, hx - 2, hz - 2, hx + 2, hz + 2, 4, "thatch_roof", overhang=1, pitch=1)
        w.fill(hx - 2, 0, hz - 2, hx + 2, 0, hz + 2, F("packed_mud"))
    kinds = ["crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom", "cartography_table",
             "furnace", "brewing_stand", "fletching_table", "smoker", "blast_furnace"]
    slots = [(hx + dx, hz - 1) for (hx, hz) in ((-6, -17), (4, -18), (14, -17)) for dx in (-1, 0, 1)] + \
            [(hx + dx, hz + 1) for (hx, hz) in ((-6, -17), (4, -18), (14, -17)) for dx in (-1, 1)]
    for (x, z), kind in zip(slots, kinds):
        h.station(w, x, 1, z, kind, "south")
    for (hx, hz) in ((-6, -17), (4, -18), (14, -17)):
        K.lamp(w, hx, 3, hz)
    # ------------------------------------------------------------------ the plaza, waterfall, jungle
    K.mix(w, -13, 0, -7, -4, 0, 7, [F("stone_bricks"), F("mossy_stone_bricks"), F("cracked_stone_bricks")], 154)
    K.training_ground(h, w, [(-8, -5), (-5, -5), (-8, 5)], 0, 90.0, looks=(0, 2, 1))
    h.target(w, -5, 0, 5)
    for z in (13, 17):                                                 # the stone arch with the bell
        w.fill(-12, 1, z, -12, 5, z, F("chiseled_stone_bricks"))
    w.fill(-12, 6, 13, -12, 6, 17, F("mossy_stone_bricks"))
    w.set(-12, 7, 15, F("gilded_trim"))
    h.bell(w, -12, 5, 15, facing="east", attachment="ceiling")
    # waterfall from a rock outcrop (south-east) into a pool
    for x in range(14, 21):
        for z in range(13, 19):
            if (x, z) in cells:
                hgt = 9 - int(abs(x - 18) * 1.3 + abs(z - 17) * 1.3)
                for y in range(1, max(1, hgt) + 1):
                    w.set(x, y, z, F(JUNGLE[int(hash3(x, y, z, 155) * 6) % 6]))
    K.pond(w, 13, 11, 3.0, 2.4, 0, border=("mossy_cobblestone", "stone"), depth=2, seed=156, lily=0.2)
    for y in range(1, 8):
        w.set(15, y, 13, B.S("water"))
    for (x, z) in ((-17, -10), (-16, 11), (19, -10), (-2, 18), (6, 18), (19, 3), (-5, -12), (21, -2)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "jungle", seed=x * 3 + z, height=9, crown=4.0)
    for (x, z) in sorted(cells):                                       # vines over the pyramid's faces
        if hash2(x, z, 157) < 0.08:
            for y in range(15, 1, -1):
                st = w.get(x, y, z)
                if not w.is_empty(x, y, z) and "stone" in st:
                    for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
                        if w.is_empty(x + dx, y, z + dz) and w.is_empty(x + dx, y - 1, z + dz):
                            for k in range(0, 3):
                                if w.is_empty(x + dx, y - k, z + dz):
                                    w.set(x + dx, y - k, z + dz, B.S("vine", **{f: True}))
                    break
    K.scatter_plants(w, cells, 0, seed=158, kinds=("fern", "grass", "fern", "azalea", "fern", "grass"), density=0.35,
                     on=("grass_block", "moss_block", "podzol"))
    for x in range(-15, -12):
        for z in range(-12, -6):
            if (x, z) in cells and hash2(x, z, 159) < 0.5:
                for y in range(1, 5 + int(hash2(z, x, 160) * 4)):
                    w.set(x, y, z, B.S("bamboo", age=1, leaves="large" if y > 3 else "none", stage=0))
    return h.finish()


DESIGNS = {
    "japanese_mansion": japanese_mansion,
    "great_tree": great_tree,
    "sage_library": sage_library,
    "jungle_temple": jungle_temple,
}
INTERIORS = {
    "japanese_mansion": {"shoin": ((-4.5, 3.62, -1.0), (2.0, 3.0, 1.0)), "dojo": ((1.0, 3.62, 11.0),
                                                                               (6.0, 2.0, 16.0))},
    "great_tree": {"hall": ((3.0, 2.62, 0.5), (10.0, 3.0, 0.0)), "library": ((5.0, 18.62, 2.5), (6.0, 18.0, -1.0))},
    "sage_library": {"nave": ((-4.0, 3.62, 0.5), (14.0, 7.0, 0.0)), "rotunda": ((17.0, 3.62, 0.5),
                                                                            (22.0, 4.0, 0.0))},
    "jungle_temple": {"hall": ((0.0, 2.62, 0.5), (16.0, 3.0, 0.0)), "shrine": ((5.0, 17.62, 0.5), (8.0, 17.0, 0.0))},
}
