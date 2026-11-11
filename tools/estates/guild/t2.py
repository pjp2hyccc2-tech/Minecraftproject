"""Tier 2 guild houses (ゆったり, about 39 x 39): the flower glasshouse, the dwarven forge, the desert palace and the
observatory. Each stands on its own floating island; the return gate is on the west side facing east."""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.districts import _furnish as FN
from capital.noise import fbm2, hash2, hash3

from . import hkit as K
from .hkit import AIR, F, SL, ST, WL

OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def _tall(w, x, y, z, plant):
    lo, hi = B.tall_plant_states(plant)
    w.set(x, y, z, lo)
    w.set(x, y + 1, z, hi)


def _disc(w, cx, cz, r, y, state, only_empty=False):
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            if math.hypot(dx, dz) <= r + 0.25:
                w.set(cx + dx, y, cz + dz, state, only_empty=only_empty)


# ====================================================================================== 花園の温室
def flower_glasshouse(w):
    """花園の温室: a Victorian palm house in a flower garden. A white-ribbed glass dome over a fountain whose jet
    rises among palms and flower beds, between two barrel-vaulted wings - the orangery lounge with the guild board
    and the plant room with the seed vault. A brick potting shed with a slate roof holds the work benches and the
    chests; dummies stand on a lawn inside clipped hedges beside a rose arch and a white garden bell."""
    h = K.House(w, "flower_glasshouse")
    cells = K.island(w, 0, 0, 18.5, 17.5, depth=15, surface=("grass_block", "moss_block", "grass_block"),
                     rock=("stone", "andesite", "calcite", "stone"), seed=71)
    K.drip_stalactites(w, cells, 0, seed=71)
    h.gate()
    q, qp, glass = F("smooth_quartz"), B.S("quartz_pillar", axis="y"), F("glass")
    # ------------------------------------------------------------------ the dome (centre 6, 0) and the wings
    cx, cz, R = 6, 0, 6
    _disc(w, cx, cz, R + 0.5, 0, F("polished_diorite"))
    w.fill(-5, 0, -3, 17, 0, 3, F("polished_diorite"))
    for x in range(-4, 17):
        for z in range(-2, 3):
            if (x + z) % 2 == 0:
                w.set(x, 0, z, F("calcite"))
    for dx in range(-R - 1, R + 2):                               # drum: low wall, glass, cornice
        for dz in range(-R - 1, R + 2):
            d = math.hypot(dx, dz)
            if R - 0.5 < d <= R + 0.5:
                x, z = cx + dx, cz + dz
                w.set(x, 1, z, F("calcite"))
                ang = math.degrees(math.atan2(dz, dx)) % 360
                rib = min(ang % 45, 45 - ang % 45) < 6
                for y in range(2, 7):
                    w.set(x, y, z, qp if rib else B.pane())
                w.set(x, 7, z, q)
    for y in range(8, 15):                                        # the glass dome with eight white ribs
        rr = R * math.sqrt(max(0.0, 1 - ((y - 7) / 7.2) ** 2))
        for dx in range(-R - 1, R + 2):
            for dz in range(-R - 1, R + 2):
                d = math.hypot(dx, dz)
                if rr - 1.0 < d <= rr + 0.2 or (d <= rr + 0.2 and y == 14):
                    ang = math.degrees(math.atan2(dz, dx)) % 360
                    rib = min(ang % 45, 45 - ang % 45) < 7 or y == 11
                    w.set(cx + dx, y, cz + dz, q if rib else glass)
    w.set(cx, 15, cz, q)
    w.set(cx, 16, cz, B.S("gilded_finial"))
    S.cylinder(w, cx, cz, R - 0.5, 1, 13, AIR)
    for (x0, x1) in ((-5, 0), (12, 16)):                          # the wings: x -5..0 and 12..16, z -3..3
        for x in range(x0, x1 + 1):
            end = x in (x0, x1) and (x == -5 or x == 16)
            rib = (x - x0) % 3 == 0 or end
            for z in (-3, 3):
                w.set(x, 1, z, F("calcite"))
                for y in range(2, 6):
                    w.set(x, y, z, qp if rib else B.pane())
                w.set(x, 6, z, q)
            for (z, y) in ((-3, 7), (-2, 8), (-1, 9), (0, 9), (1, 9), (2, 8), (3, 7)):
                w.set(x, y, z, q if rib else glass)
            for (z, y) in ((-2, 7), (2, 7)):
                w.set(x, y, z, q if rib else glass)
            K.clear(w, x, 1, -2, x, 6, 2) if not end else None
            K.clear(w, x, 7, -1, x, 8, 1)
        xe = x0 if x0 == -5 else x1
        for z in range(-2, 3):
            w.set(xe, 1, z, F("calcite"))
            for y in range(2, 9):
                if w.get(xe, y, z) == "eternalrealm:void" or y < 7:
                    w.set(xe, y, z, B.pane() if abs(z) < 2 else qp)
        for (z, y) in ((-1, 7), (0, 7), (1, 7), (-1, 8), (0, 8), (1, 8)):
            w.set(xe, y, z, F("pink_stained_glass") if (z + y) % 2 else glass)
    for x in (0, 12):                                             # openings between wings and dome
        K.clear(w, x, 1, -2, x, 5, 2)
        K.clear(w, x + (1 if x == 0 else -1), 1, -2, x + (1 if x == 0 else -1), 5, 2)
    K.clear(w, -5, 1, -1, -5, 4, 1)                               # the front door (west)
    for z in (-1, 1):
        w.set(-5, 4, z, ST("quartz", "south" if z < 0 else "north", "top"))
    w.set(-5, 4, 0, q)
    # ------------------------------------------------------------------ inside the dome: fountain, palms, beds
    _disc(w, cx, cz, 2.4, 0, F("prismarine_bricks"))
    _disc(w, cx, cz, 2.4, 1, F("smooth_quartz"))
    _disc(w, cx, cz, 1.5, 1, B.S("water"))
    w.set(cx, 1, cz, F("prismarine_bricks"))
    w.set(cx, 2, cz, B.S("fountain_spray", power=3, facing="up"))
    for (dx, dz) in ((-4, -3), (3, 4), (-3, 4), (4, -3)):
        K.palm(w, cx + dx, 0, cz + dz, height=6, seed=dx * 3 + dz, lean=(0, 0))
    for (x0_, z0_, x1_, z1_) in ((cx - 5, -1, cx - 4, 1), (cx + 4, -1, cx + 5, 1)):
        deco.flower_bed(w, x0_, z0_, x1_, z1_, 0, seed=x0_, border=None, soil="grass_block")
    for (x, z) in ((cx - 1, cz - 4), (cx + 1, cz + 4), (cx + 1, cz - 4), (cx - 1, cz + 4)):
        _tall(w, x, 1, z, ("rose_bush", "peony", "lilac", "sunflower")[(x + z) % 4])
    for (x, z, f) in ((cx - 2, cz - 3, "south"), (cx + 2, cz + 3, "north")):
        deco.bench(w, x, 1 - 1, z, facing=f, length=2, wood="birch")
    K.lamp(w, cx, 12, cz - 3, chain=1)
    K.lamp(w, cx, 12, cz + 3, chain=1)
    # ------------------------------------------------------------------ the orangery (west wing): lounge + board
    w.fill(-2, 1, -2, -2, 3, -2, F("smooth_quartz"))
    w.fill(-4, 1, -2, -4, 3, -2, F("smooth_quartz"))
    w.fill(-3, 1, -2, -3, 3, -2, F("smooth_quartz"))
    h.board(w, -3, 2, -1, "south")
    w.set(-3, 1, -1, AIR)
    for (x, z) in ((-3, 2), (-1, 2)):
        w.set(x, 1, z, B.fence("birch"))
        w.set(x, 2, z, B.carpet("white"))
        w.set(x - 1 if x == -3 else x + 1, 1, z, ST("birch", "east" if x == -3 else "west"))
    for (x, z) in ((-4, 0), (-1, -2)):
        w.set(x, 1, z, B.potted("orange_tulip"))
    K.lamp(w, -2, 8, 0)
    # ------------------------------------------------------------------ the plant room (east wing): the vault
    h.vault(w, 15, 1, 0, "west")
    for z in (-2, 2):
        w.fill(13, 1, z, 15, 1, z, F("spruce_planks"))
        for x in (13, 14, 15):
            w.set(x, 2, z, B.potted(("fern", "cactus", "blue_orchid", "azure_bluet", "allium")[(x + z) % 5]))
    w.set(15, 1, -1, B.S("composter", level=6))
    w.set(15, 1, 1, B.S("water_cauldron", level=3))
    K.lamp(w, 14, 8, 0)
    # ------------------------------------------------------------------ the potting shed (north): stations + chests
    px0, px1, pz0, pz1 = -4, 9, -13, -8
    w.fill(px0, 0, pz0, px1, 0, pz1, F("bricks"))
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            if x in (px0, px1) or z in (pz0, pz1):
                for y in range(1, 5):
                    w.set(x, y, z, F("bricks"))
    K.clear(w, px0 + 1, 1, pz0 + 1, px1 - 1, 9, pz1 - 1)
    S.gable_roof(w, px0, pz0, px1, pz1, 5, "slate_roof_tiles", axis="x", overhang=1, pitch=1,
                 gable_state=F("bricks"))
    for x in range(px0 + 2, px1 - 1, 3):
        w.set(x, 2, pz0, B.pane())
        w.set(x, 3, pz0, B.pane())
    K.door(w, 2, 0, pz1, "north", wood="birch")
    for x in (px0 + 1, px1 - 1):
        w.set(x, 2, pz1, B.pane())
        w.set(x, 3, pz1, B.pane())
    h.stations_row(w, [(x, pz0 + 1) for x in range(px0 + 1, px1)], 0, "south",
                   kinds=("crafting_table", "furnace", "anvil", "smithing_table", "grindstone", "stonecutter",
                          "loom", "cartography_table", "smoker", "brewing_stand", "fletching_table", "blast_furnace"))
    K.storage_wall(h, w, [(x, pz1 - 1) for x in (-3, -2, -1, 5, 6)], 0, "north",
                   between=B.potted("red_tulip"))
    for x in (0, 5):
        K.lamp(w, x, 4, -10)
    w.fill(px1 - 1, 5, pz0 + 2, px1 - 1, 8, pz0 + 2, F("bricks"))
    K.TK.smoke(w, px1 - 1, 9, pz0 + 2)
    # ------------------------------------------------------------------ the hedge garden (south): dummies
    gx0, gx1, gz0, gz1 = -2, 13, 7, 15
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            if (x, z) in cells:
                w.set(x, 0, z, F("grass_block"))
                edge = x in (gx0, gx1) or z in (gz0, gz1)
                if edge and not (gz0 < z < gz0 + 3 and x == gx0) and not (x in (5, 6) and z == gz0):
                    w.set(x, 1, z, B.leaves("azalea"))
                    w.set(x, 2, z, B.leaves("flowering_azalea" if (x + z) % 3 == 0 else "azalea"))
    K.training_ground(h, w, [(2, 12), (5, 13), (8, 12)], 0, 180.0, looks=(0, 2, 0))
    h.target(w, 11, 0, 11)
    for k, x in enumerate((5, 6)):                                # rose arch over the hedge gap
        w.fill(x, 1, gz0, x, 3, gz0, AIR)
    for (x, y) in ((4, 1), (4, 2), (4, 3), (7, 1), (7, 2), (7, 3), (5, 4), (6, 4), (4, 4), (7, 4)):
        w.set(x, y, gz0, B.leaves("flowering_azalea") if y == 4 else B.S("birch_fence"))
    for x in (-5, -3):                                            # the white garden bell
        w.fill(x, 1, 9, x, 3, 9, qp)
    w.fill(-5, 4, 9, -3, 4, 9, SL("quartz", "bottom"))
    w.set(-4, 4, 9, q)
    h.bell(w, -4, 3, 9, facing="east", attachment="ceiling")
    # ------------------------------------------------------------------ grounds: paths, flower beds, topiary
    K.path(w, [(-9, 0), (-6, 0)], 0, ["polished_diorite", "calcite", "smooth_quartz"], width=3, seed=72)
    K.path(w, [(-7, 1), (-7, 9), (gx0 + 1, 9)], 0, ["calcite", "polished_diorite"], width=1, seed=73)
    K.path(w, [(2, -4), (2, -7)], 0, ["calcite", "polished_diorite"], width=1, seed=74)
    for (x0_, z0_, x1_, z1_) in ((-10, -6, -7, -4), (-10, 4, -7, 6), (12, -8, 16, -6), (14, 5, 17, 7)):
        deco.flower_bed(w, x0_, z0_, x1_, z1_, 0, seed=x0_ * 3 + z0_, border="smooth_quartz")
    for (x, z) in ((-12, -9), (-12, 9), (17, -2), (17, 2), (-1, -15), (10, -15)):
        if (x, z) in cells:
            deco.topiary(w, x, 0, z, height=3, kind="azalea")
    for (x, z) in ((-8, -2), (-8, 2), (4, -6), (8, -6)):
        deco.lamp_post(w, x, 0, z, height=3)
    for (x, z) in ((-15, -8), (-14, 11), (15, -12), (16, 11), (-6, 15), (14, 15)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "cherry" if (x + z) % 2 else "birch", seed=x + z * 3, height=5)
    K.scatter_plants(w, cells, 0, seed=75, kinds=("grass", "poppy", "dandelion", "azure_bluet", "oxeye_daisy",
                                                 "cornflower", "allium", "pink_tulip", "white_tulip"),
                     density=0.32, on=("grass_block",))
    return h.finish()


# ====================================================================================== ドワーフの鍛冶坑
DEEP = ("deepslate", "cobbled_deepslate", "tuff", "deepslate", "andesite", "stone")
ORES = ("deepslate_iron_ore", "deepslate_gold_ore", "deepslate_copper_ore", "deepslate_diamond_ore",
        "deepslate_lapis_ore", "deepslate_emerald_ore")


def dwarven_forge(w):
    """ドワーフの鍛冶坑: a mountain of seamed rock whose heart is a pillared hall twenty blocks high. The gate opens
    in its west wall; down the hall run massive gold-banded pillars, a forge of blast furnaces and anvils under a
    great hood, curtains of lava behind iron grilles, a sunken practice pit with armoured dummies, the bronze gong
    and a vault behind an iron door opened by a button. A balcony looks out from the mountain's east face; the
    chimney smokes on the summit."""
    h = K.House(w, "dwarven_forge")
    cells = K.island(w, 0, 0, 18.5, 18.0, depth=16, surface=("stone", "andesite", "coarse_dirt"),
                     soil="stone", rock=DEEP, seed=81)
    K.drip_stalactites(w, cells, 0, seed=81, kinds=("deepslate", "tuff", "cobbled_deepslate"))
    # the mountain: a rugged heightfield over the island
    peak = {}
    for (x, z) in sorted(cells):
        # a massif over the hall (x -13..17, z -11..11) with steep, stepped flanks and a few peaks
        ex = max(0.0, abs(x - 2) - 15.0)
        ez = max(0.0, abs(z) - 11.0)
        out = math.hypot(ex, ez)
        n = fbm2(x, z, 6.0, 3, 82)
        top = int(24 + n * 4 + max(0.0, fbm2(x, z, 9.0, 2, 89)) * 8) if out == 0 else \
            int(23 - out * 3.2 + n * 3)
        top = max(0, top)
        peak[(x, z)] = top
        for y in range(1, top + 1):
            k = hash3(x, y, z, 83)
            st = ORES[int(k * 97) % len(ORES)] if k < 0.035 else DEEP[int(k * 31) % len(DEEP)]
            w.set(x, y, z, F(st))
        if top >= 26 and hash2(x, z, 84) < 0.8:
            w.set(x, top, z, F("snow_block"))
        elif 1 <= top < 14 and hash2(x, z, 85) < 0.45:
            w.set(x, top, z, F("grass_block") if hash2(x, z, 86) < 0.6 else F("moss_block"))
            if hash2(x, z, 87) < 0.05 and top >= 1:
                K.tree(w, x, top, z, "spruce", seed=x * 3 + z, height=5)
    # ------------------------------------------------------------------ the great hall x -10..14, z -8..8, y 1..19
    hx0, hx1, hz0, hz1, top = -10, 14, -8, 8, 19
    for x in range(hx0 - 1, hx1 + 2):                              # keep a shell of rock around the hall
        for z in range(hz0 - 1, hz1 + 2):
            for y in range(1, top + 3):
                if w.is_empty(x, y, z):
                    w.set(x, y, z, F(DEEP[int(hash3(x, y, z, 87) * 6) % 6]))
    K.clear(w, hx0, 1, hz0, hx1, top, hz1)
    for x in range(hx0, hx1 + 1):                                 # vaulted ceiling: the corners rounded off
        for z in range(hz0, hz1 + 1):
            e = min(z - hz0, hz1 - z)
            for y in range(top - 2 + min(e, 2), top + 1):
                if e < 2:
                    w.set(x, y, z, F("deepslate_bricks"))
    K.mix(w, hx0, 0, hz0, hx1, 0, hz1, [F("polished_deepslate"), F("deepslate_tiles"), F("deepslate_tiles")], 88)
    for x in range(hx0, hx1 + 1):
        w.set(x, 0, 0, F("polished_blackstone_bricks"))
        w.set(x, 0, -1, F("gilded_trim") if x % 4 == 0 else F("polished_blackstone_bricks"))
        w.set(x, 0, 1, F("gilded_trim") if x % 4 == 0 else F("polished_blackstone_bricks"))
    h.gate()
    # pillars with gold bands
    for px in (-5, 2, 9):
        for pz in (-5, 5):
            for y in range(1, top + 1):
                band = y in (1, 6, 12, top - 2)
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        corner = abs(dx) == 1 and abs(dz) == 1
                        st = F("gilded_trim") if band and not corner else (
                            F("chiseled_deepslate") if corner and y % 4 == 0 else F("polished_deepslate"))
                        w.set(px + dx, y, pz + dz, st)
            for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
                w.set(px + dx, 10, pz + dz, B.lantern(hanging=False))
                w.set(px + dx, 9, pz + dz, AIR)
    for x in range(hx0, hx1 + 1):                                 # beams between the pillar rows
        for z in (-5, 5):
            if w.is_empty(x, 16, z):
                w.set(x, 16, z, F("polished_blackstone_bricks"))
    for (x, z) in ((-2, 0), (5, 0), (11, 0)):
        deco.chandelier(w, x, top + 1, z, drop=5)
        for k in range(1, 5):
            w.set(x, top + 1 - k, z, B.chain())
    # the forge on the north side: hood, chimney, stations
    fx0, fx1 = -2, 7
    for x in range(fx0, fx1 + 1):
        w.set(x, 6, hz0, F("polished_blackstone_bricks"))
        w.set(x, 6, hz0 + 1, ST("polished_blackstone_brick", "south", "top"))
        w.set(x, 5, hz0 + 1, AIR)
    w.fill(fx0, 7, hz0, fx1, top + 1, hz0, F("polished_blackstone_bricks"))
    h.stations_row(w, [(x, hz0) for x in range(fx0, fx1 + 1)], 0, "south",
                   kinds=("blast_furnace", "anvil", "smithing_table", "blast_furnace", "grindstone", "anvil",
                          "furnace", "stonecutter", "blast_furnace", "smoker"))
    for (x, kind) in ((fx0 - 3, "crafting_table"), (fx0 - 2, "loom"), (fx1 + 3, "cartography_table"),
                      (fx1 + 4, "fletching_table"), (fx1 + 5, "brewing_stand")):
        h.station(w, x, 1, hz0, kind, "south")
    for x in (fx0 + 1, fx0 + 5):
        w.set(x, 1, hz0 + 2, B.S("water_cauldron", level=3))
    for (sx, sz) in ((3, hz0 - 2),):                              # chimney up through the mountain
        for y in range(1, 40):
            if y > 6 and (y > peak.get((sx, sz), 0) + 1):
                break
            w.set(sx, y, sz, F("polished_blackstone_bricks") if y >= 6 else w.get(sx, y, sz))
        yy = max(peak.get((sx, sz), 0), top + 1) + 1
        w.fill(sx - 1, yy, sz - 1, sx + 1, yy + 2, sz + 1, F("polished_blackstone_bricks"))
        w.set(sx, yy + 2, sz, AIR)
        K.TK.smoke(w, sx, yy + 3, sz, heavy=True)
    # lava curtains behind iron grilles (north wall, either side of the forge)
    for (lx0, lx1) in ((hx0 + 1, hx0 + 3), (11, 13)):
        for x in range(lx0, lx1 + 1):
            for y in range(1, 9):
                w.set(x, y, hz0 - 1, B.S("lava"))
                w.set(x, y, hz0, B.S("iron_bars"))
            w.set(x, 9, hz0, F("gilded_trim"))
    # the practice pit in the middle: armoured dummies and a target
    w.fill(0, -1, -2, 7, -1, 2, F("packed_mud"))
    K.clear(w, 0, 0, -2, 7, 0, 2)
    for x in range(0, 8):
        for z in (-2, 2):
            w.set(x, 0, z, SL("polished_deepslate", "bottom"))
    K.training_ground(h, w, [(2, -1, ), (5, 1)], -1, 270.0, looks=(1, 1))
    h.target(w, 7, -1, 0)
    # the gong (rally bell) between the western pillars
    w.fill(-5, 9, -3, -5, 9, 3, B.log("dark_oak", "z"))
    h.bell(w, -5, 8, 0, facing="east", attachment="ceiling")
    # the board on the west face of the south-west pillar, the throne at the east end
    h.board(w, -7, 2, 5, "west")
    w.fill(12, 1, -2, 14, 1, 2, F("polished_blackstone_bricks"))
    w.set(13, 2, 0, ST("polished_blackstone_brick", "west"))
    w.set(13, 2, -1, F("gold_block"))
    w.set(13, 2, 1, F("gold_block"))
    for z in (-2, 2):
        K.crest_banner(w, 14, 5, z, "west", "orange", [("ts", "yellow"), ("bo", "black"), ("mc", "yellow")])
    # store room alcoves (south wall) with the chests, the vault behind an iron door with a button
    for (x, kind) in ((-6, "c"), (-2, "c"), (2, "c")):
        K.clear(w, x - 1, 1, hz1 + 1, x + 1, 3, hz1 + 2)
        w.set(x, 4, hz1 + 1, F("gilded_trim"))
        h.chest(w, x, 1, hz1 + 2, "north")
        w.set(x - 1, 1, hz1 + 2, B.barrel("up"))
        w.set(x + 1, 1, hz1 + 2, B.barrel("north"))
        w.set(x, 3, hz1 + 2, B.lantern(hanging=True))
    vx = 9
    K.clear(w, vx - 1, 1, hz1 + 1, vx + 2, 3, hz1 + 4)
    w.fill(vx - 1, 0, hz1 + 1, vx + 2, 0, hz1 + 4, F("gold_block"))
    lo, hi = B.door_states("iron", "south", "left", False)
    w.set(vx, 1, hz1 + 1, lo)
    w.set(vx, 2, hz1 + 1, hi)
    w.set(vx - 1, 1, hz1 + 1, F("polished_deepslate"))
    w.set(vx - 1, 2, hz1 + 1, F("polished_deepslate"))
    w.set(vx + 1, 1, hz1 + 1, F("polished_deepslate"))
    w.set(vx + 1, 2, hz1 + 1, F("polished_deepslate"))
    w.set(vx - 1, 3, hz1 + 1, F("polished_deepslate"))
    w.set(vx, 3, hz1 + 1, F("gilded_trim"))
    w.set(vx + 1, 3, hz1 + 1, F("polished_deepslate"))
    w.set(vx + 2, 1, hz1 + 1, F("polished_deepslate"))
    w.set(vx + 2, 2, hz1 + 1, F("polished_deepslate"))
    w.set(vx + 1, 2, hz1, B.S("stone_button", face="wall", facing="north", powered=False))
    h.vault(w, vx, 1, hz1 + 4, "north")
    w.set(vx + 1, 1, hz1 + 4, F("gold_block"))
    w.set(vx - 1, 1, hz1 + 4, F("raw_gold_block"))
    K.lamp(w, vx, 3, hz1 + 3)
    # the balcony on the east face
    K.clear(w, hx1 + 1, 1, -2, hx1 + 3, 5, 2)
    w.fill(hx1 + 1, 0, -3, hx1 + 4, 0, 3, F("polished_deepslate"))
    for z in range(-3, 4):
        w.set(hx1 + 4, 1, z, WL("polished_blackstone"))
    for x in range(hx1 + 1, hx1 + 4):
        for z in (-3, 3):
            w.set(x, 1, z, WL("polished_blackstone"))
    w.set(hx1 + 1, 5, 0, F("gilded_trim"))
    # glowing runes along the walls
    for x in range(hx0 + 2, hx1, 4):
        for z in (hz0 - 1, hz1 + 1):
            if not w.get(x, 6, z).startswith("minecraft:lava") and "door" not in w.get(x, 1, z):
                w.set(x, 7, z, F("shroomlight"))
    return h.finish()


# ====================================================================================== 砂漠の離宮
SAND = ("cut_sandstone", "smooth_sandstone", "sandstone", "cut_sandstone")


def desert_palace(w):
    """砂漠の離宮: an oasis palace behind crenellated sandstone walls. Through the horseshoe gate the courtyard
    opens round a pool whose fountain plays between orange trees and patterned tiles; arcades of horseshoe arches
    hold the artisans' workshop and the store chests; the domed audience hall (iwan) glows with lanterns over
    carpets and cushions, the treasury vault behind its grille; a minaret rises at the corner, and outside the
    walls palms ring a spring."""
    h = K.House(w, "desert_palace")
    cells = K.island(w, 0, 0, 18.5, 18.0, depth=14, surface=("sand", "sand", "grass_block"), soil="sand",
                     rock=("sandstone", "smooth_sandstone", "stone", "sandstone"), seed=91)
    K.drip_stalactites(w, cells, 0, seed=91, kinds=("sandstone", "smooth_sandstone"))
    ox0, ox1, oz0, oz1 = -12, 14, -11, 11
    # ------------------------------------------------------------------ the enclosure and the gatehouse
    K.mix(w, ox0, 0, oz0, ox1, 0, oz1, [F("smooth_sandstone")], 92)
    for x in range(ox0, ox1 + 1):
        for z in range(oz0, oz1 + 1):
            if x in (ox0, ox0 + 1, ox1 - 1, ox1) or z in (oz0, oz0 + 1, oz1 - 1, oz1):
                K.mix(w, x, 1, z, x, 6, z, [F(s) for s in SAND], 93)
                outer = x in (ox0, ox1) or z in (oz0, oz1)
                if outer:
                    w.set(x, 7, z, F("chiseled_sandstone") if (x + z) % 2 == 0 else SL("sandstone", "bottom"))
    K.mix(w, -17, 1, -5, -11, 6, 5, [F(s) for s in SAND], 94)
    h.gate()
    # ------------------------------------------------------------------ the courtyard: tiles, pool, fountain, trees
    for x in range(ox0 + 2, 5):
        for z in range(oz0 + 2, oz1 - 1):
            k = (abs(x + 3) + abs(z)) % 4
            w.set(x, 0, z, F(("cut_sandstone", "white_terracotta", "cut_sandstone", "cyan_terracotta")[k]))
    w.fill(-6, 0, -2, 0, 0, 2, F("prismarine_bricks"))
    w.fill(-6, 1, -2, 0, 1, 2, F("cut_sandstone"))
    w.fill(-5, 1, -1, -1, 1, 1, B.S("water"))
    w.set(-3, 1, 0, F("chiseled_sandstone"))
    w.set(-3, 2, 0, B.S("fountain_spray", power=2, facing="up"))
    for (x, z) in ((-8, -6), (2, -6), (-8, 6), (2, 6)):
        w.set(x, 0, z, F("grass_block"))
        w.fill(x, 1, z, x, 2, z, B.log("jungle", "y"))
        K.GK.ball(w, x, 4, z, 1.6, B.leaves("flowering_azalea"), ry=1.3)
        w.set(x, 3, z, B.log("jungle", "y"))
    # arcades north (workshop) and south (store chests, bell): horseshoe arches on columns
    for (zc, zb, face) in ((oz0 + 4, oz0 + 2, "south"), (oz1 - 4, oz1 - 2, "north")):
        for x in range(ox0 + 2, 5):
            for z in range(min(zc, zb), max(zc, zb) + 1):
                w.set(x, 5, z, F("cut_sandstone"))
            if (x - ox0) % 4 == 2:
                w.fill(x, 1, zc, x, 4, zc, F("cut_sandstone"))
                w.set(x, 1, zc, F("chiseled_sandstone"))
            else:
                w.set(x, 4, zc, ST("sandstone", "east" if (x - ox0) % 4 == 3 else "west", "top")
                      if (x - ox0) % 4 != 0 else F("cut_sandstone"))
            w.set(x, 6, zc, SL("cut_sandstone", "bottom"))
        for x in range(ox0 + 4, 4, 4):
            w.set(x, 4, zb, B.lantern(hanging=True))
    h.stations_row(w, [(x, oz0 + 2) for x in range(ox0 + 3, 4) if (x - ox0) % 4 != 2], 0, "south",
                   kinds=("crafting_table", "loom", "cartography_table", "anvil", "smithing_table", "stonecutter",
                          "grindstone", "furnace", "smoker", "brewing_stand", "fletching_table", "blast_furnace"))
    K.storage_wall(h, w, [(x, oz1 - 2) for x in (-8, -7, -6, -4, -3, -2)], 0, "north",
                   between=B.S("decorated_pot"))
    h.bell(w, 0, 4, oz1 - 3, facing="east", attachment="ceiling")
    # training in the courtyard's east part
    K.training_ground(h, w, [(-8, 3), (-8, -3)], 0, 90.0, looks=(0, 2))
    h.target(w, 3, 0, 0)
    # ------------------------------------------------------------------ the audience hall (iwan) x 5..12, z -8..8
    ix0, ix1, iz0, iz1 = 5, 12, -8, 8
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            if x in (ix0, ix1) or z in (iz0, iz1):
                K.mix(w, x, 1, z, x, 9, z, [F(s) for s in SAND], 95)
    K.clear(w, ix0 + 1, 1, iz0 + 1, ix1 - 1, 9, iz1 - 1)
    w.fill(ix0, 10, iz0, ix1, 10, iz1, F("cut_sandstone"))
    for x in range(ix0, ix1 + 1):
        for z in (iz0, iz1):
            w.set(x, 11, z, F("chiseled_sandstone") if (x + z) % 2 == 0 else SL("sandstone", "bottom"))
    K.clear(w, ix0, 1, -2, ix0, 6, 2)                              # the iwan arch
    for (z, y) in ((-2, 6), (2, 6), (-2, 5), (2, 5)):
        w.set(ix0, y, z, F("cut_sandstone"))
    w.set(ix0, 7, -1, ST("sandstone", "south", "top"))
    w.set(ix0, 7, 1, ST("sandstone", "north", "top"))
    w.set(ix0, 7, 0, F("gold_block"))
    for y in range(1, 9):
        for z in (-3, 3):
            w.set(ix0, y, z, B.S("cyan_glazed_terracotta", facing="east") if y % 2 else F("light_blue_terracotta"))
    # the gilded onion dome over the hall
    dcx, dcz = (ix0 + ix1) // 2, 0
    for y in range(11, 20):
        t = (y - 11) / 9.0
        r = 4.6 * math.sin(math.pi * (0.18 + 0.82 * t)) ** 0.7 + 0.3 if t < 0.95 else 0.6
        for dx in range(-6, 7):
            for dz in range(-6, 7):
                d = math.hypot(dx, dz)
                if r - 1.1 < d <= r:
                    w.set(dcx + dx, y, dcz + dz, F("gilded_roof_tiles"))
    w.set(dcx, 20, dcz, F("gold_block"))
    w.set(dcx, 21, dcz, B.S("gilded_finial"))
    for (dx, dz) in ((-3, 0), (3, 0), (0, -3), (0, 3)):
        w.set(dcx + dx, 10, dcz + dz, F("light_blue_stained_glass"))
    # inside the hall: carpets, cushions, low tables, lanterns, the board, the treasury vault
    for x in range(ix0 + 1, ix1):
        for z in range(iz0 + 1, iz1):
            c = "red" if (x + z) % 2 == 0 else "orange"
            if abs(z) <= 1:
                c = "yellow" if x % 2 else "red"
            w.set(x, 1, z, B.carpet(c))
    for z in (-6, 6):
        for x in range(ix0 + 2, ix1 - 1):
            w.set(x, 1, z, B.wool("red") if x % 2 else B.wool("purple"))
            w.set(x, 1, z + (1 if z > 0 else -1), B.wool("orange"))
    for (x, z) in ((7, -3), (9, 3)):
        w.set(x, 1, z, SL("dark_oak", "bottom"))
    for (x, z) in ((7, -4), (9, 4), (10, -4), (7, 4)):
        K.lamp(w, x, 7, z, chain=2)
    w.fill(ix1 - 1, 1, -2, ix1 - 1, 5, 2, F("cut_sandstone"))
    h.board(w, ix1 - 2, 3, 0, "west")
    w.set(ix1 - 2, 1, 0, AIR)
    w.set(ix1 - 2, 2, 0, AIR)
    for z in (-2, 2):
        w.set(ix1 - 2, 1, z, ST("dark_oak", "east"))
    K.clear(w, ix1, 1, 5, ix1 + 1, 3, 7)                             # treasury alcove
    w.fill(ix1 + 2, 1, 4, ix1 + 2, 4, 8, F("cut_sandstone"))
    w.fill(ix1, 4, 4, ix1 + 1, 4, 8, F("cut_sandstone"))
    h.vault(w, ix1 + 1, 1, 6, "west")
    w.set(ix1 + 1, 1, 5, F("gold_block"))
    w.set(ix1 + 1, 1, 7, F("gold_block"))
    w.fill(ix1, 1, 5, ix1, 3, 5, B.S("iron_bars"))
    w.fill(ix1, 1, 7, ix1, 3, 7, B.S("iron_bars"))
    w.set(ix1, 3, 6, B.S("iron_bars"))
    # ------------------------------------------------------------------ the minaret (north-east corner)
    mx, mz = ox1 - 1, oz0 + 1
    S.cylinder(w, mx, mz, 2.2, 1, 18, F("cut_sandstone"))
    for y in (8, 14):
        S.cylinder(w, mx, mz, 3.0, y, y, F("chiseled_sandstone"))
        for (dx, dz) in ((-3, 0), (3, 0), (0, -3), (0, 3)):
            w.set(mx + dx, y + 1, mz + dz, B.fence("jungle"))
    for y in range(19, 23):
        S.cylinder(w, mx, mz, max(0.5, 2.4 - (y - 19) * 0.6), y, y, F("gilded_roof_tiles"))
    w.set(mx, 23, mz, B.S("gilded_finial"))
    for y in (16, 17):
        for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
            w.set(mx + dx, y, mz + dz, B.lantern(hanging=False) if y == 16 else AIR)
    # ------------------------------------------------------------------ outside: the spring and the palms
    K.pond(w, -4, 15, 3.2, 1.8, 0, border=("sand", "smooth_sandstone"), depth=2, seed=96, lily=0.0)
    for (x, z, lean) in ((-15, -9, (1, 0)), (-15, 9, (1, 0)), (-9, 15, (0, -1)), (1, 16, (0, -1)), (8, 15, (-1, 0)),
                         (17, 6, (-1, 0)), (16, -8, (-1, 0)), (4, -15, (0, 1)), (-6, -15, (0, 1))):
        if (x, z) in cells:
            K.palm(w, x, 0, z, height=6 + int(hash2(x, z, 97) * 3), seed=x + z, lean=lean)
    K.scatter_plants(w, cells, 0, seed=98, kinds=("dead_bush", "grass", "fern"), density=0.12,
                     on=("grass_block", "sand"))
    for (x, z) in ((-9, -9), (2, -9), (-9, 9), (2, 9)):
        w.set(x, 1, z, B.potted(("cactus", "dead_bush")[(x + z) % 2]))
    return h.finish()


# ====================================================================================== 星見の天文台
def observatory(w):
    """星見の天文台: a stargazers' tower of midnight-blue tile banded in gold on an alpine crag. Its ground floor holds
    a brass orrery under a starry ceiling; a stair climbs to the chart room (shelves, star tables, the vault) and
    on to the telescope chamber, where a great copper telescope points through the open slit of a verdigris dome.
    Beside it the instrument makers' workshop; in the garden a sundial, an armillary sphere, the dummies and a bell
    on a little bell-cote."""
    h = K.House(w, "observatory")
    cells = K.island(w, 0, 0, 18.5, 18.0, depth=16, surface=("grass_block", "stone", "andesite", "moss_block"),
                     rock=("stone", "andesite", "calcite", "diorite"), seed=101)
    K.drip_stalactites(w, cells, 0, seed=101, kinds=("stone", "calcite", "andesite"))
    h.gate(mass=("stone", "andesite", "calcite", "stone", "deepslate"), mass_half=10, mass_depth=6)
    blue, gold, deep = F("blue_terracotta"), F("gilded_trim"), F("polished_blackstone_bricks")
    cx, cz, R = 6, 0, 6
    floors = (0, 7, 14)
    # ------------------------------------------------------------------ the tower
    S.cylinder(w, cx, cz, R + 0.4, 0, 0, deep)
    S.cylinder(w, cx, cz, R + 0.4, 1, 20, blue, hollow=True, thickness=1.1)
    S.cylinder(w, cx, cz, R - 0.6, 1, 20, AIR)
    for y in (1, 7, 14, 20):
        S.cylinder(w, cx, cz, R + 0.4, y, y, gold, hollow=True, thickness=1.1)
    for y in (7, 14):
        S.cylinder(w, cx, cz, R - 0.6, y, y, F("dark_oak_planks"))
    S.cylinder(w, cx, cz, R + 1.4, 21, 21, deep)
    for (dx, dz) in ((0, -R), (0, R), (-R + 1, -4), (4, -4), (4, 4)):
        for fy in (2, 9, 16):
            if (dx, dz) == (-R + 1, -4) and fy == 2:
                continue
            x, z = cx + dx, cz + dz
            w.set(x, fy + 1, z, B.pane("light_blue"))
            w.set(x, fy + 2, z, B.pane("light_blue"))
    K.clear(w, cx - R, 1, -1, cx - R + 1, 4, 1)                    # the door (west) under a gold arch
    w.set(cx - R, 4, -1, ST("gilded_trim", "south", "top"))
    w.set(cx - R, 4, 1, ST("gilded_trim", "north", "top"))
    w.set(cx - R, 5, 0, F("gold_block"))
    for (dx, dz) in ((-1, -2), (-1, 2)):
        w.set(cx - R + dx, 1, cz + dz, gold)
        w.set(cx - R + dx, 2, cz + dz, B.lantern())
    # gold stars on the outside
    for dx in range(-R - 1, R + 2):
        for dz in range(-R - 1, R + 2):
            d = math.hypot(dx, dz)
            if R - 0.6 < d <= R + 0.4:
                for y in range(2, 20):
                    if w.get(cx + dx, y, cz + dz) == blue and hash3(dx, y, dz, 102) < 0.05:
                        w.set(cx + dx, y, cz + dz, F("glowstone") if y % 3 == 0 else F("gold_block"))
    # ------------------------------------------------------------------ the dome with its slit and the telescope
    for y in range(21, 29):
        rr = (R + 1) * math.sqrt(max(0.0, 1 - ((y - 21) / 7.5) ** 2))
        for dx in range(-R - 2, R + 3):
            for dz in range(-R - 2, R + 3):
                d = math.hypot(dx, dz)
                if rr - 1.1 < d <= rr + 0.2 or (y == 28 and d <= rr + 0.2):
                    slit = abs(dz) <= 1 and dx >= -1
                    w.set(cx + dx, y, cz + dz, AIR if slit else F("verdigris_roof_tiles"))
    S.cylinder(w, cx, cz, R - 0.6, 15, 27, AIR)
    for k in range(0, 9):                                          # the telescope, rising towards the slit (east)
        x = cx - 2 + k
        y = 16 + int(k * 0.9)
        for (dy, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)) if k < 6 else ((0, 0), (1, 0)):
            w.set(x, y + dy, cz + dz - (0 if k < 6 else 0), F("waxed_cut_copper") if k % 3 else F("gilded_trim"))
    w.fill(cx - 1, 15, cz, cx - 1, 16, cz, deep)
    w.set(cx - 3, 16, cz, B.S("end_rod", facing="west"))
    # ------------------------------------------------------------------ ground floor: the orrery, the board
    S.cylinder(w, cx, cz, R - 0.6, 0, 0, F("polished_deepslate"))
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            if math.hypot(dx, dz) <= 2.5:
                w.set(cx + dx, 0, cz + dz, F("lapis_block"))
    w.set(cx, 0, cz, F("gold_block"))
    w.fill(cx, 1, cz, cx, 4, cz, B.S("chain"))
    w.set(cx, 5, cz, B.lantern(hanging=False))
    for (ang, r, y, st) in ((0, 1, 3, F("orange_concrete")), (80, 2, 3, F("light_blue_concrete")),
                            (170, 2, 4, F("red_concrete")), (260, 3, 4, F("white_concrete")),
                            (320, 3, 2, F("yellow_concrete"))):
        x = cx + round(r * math.cos(math.radians(ang)))
        z = cz + round(r * math.sin(math.radians(ang)))
        w.set(x, y, z, st)
    for dx in range(-R + 1, R):                                    # stars in the ceiling
        for dz in range(-R + 1, R):
            if math.hypot(dx, dz) <= R - 0.8 and hash2(dx, dz, 103) < 0.1:
                w.set(cx + dx, 7, cz + dz, F("sea_lantern"))
    h.board(w, cx + R - 1, 2, cz, "west")
    w.set(cx + 3, 1, cz - 3, ST("dark_oak", "south"))
    w.set(cx + 3, 1, cz + 3, ST("dark_oak", "north")) if w.is_empty(cx + 3, 1, cz + 3) else None
    # ------------------------------------------------------------------ chart room: shelves, tables, chests, vault
    fy = floors[1]
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            d = math.hypot(dx, dz)
            if R - 1.6 < d <= R - 0.6 and dx > 1:
                for y in (fy + 1, fy + 2):
                    if w.is_empty(cx + dx, y, cz + dz) and not (abs(dz) <= 1 and dx > 3):
                        w.set(cx + dx, y, cz + dz, B.S("bookshelf"))
    h.station(w, cx, fy + 1, cz, "cartography_table")
    w.set(cx, fy + 2, cz, B.S("candle", candles=3, lit=True))
    h.chest(w, cx - 4, fy + 1, cz - 2, "east")
    h.chest(w, cx - 4, fy + 1, cz, "east")
    h.chest(w, cx - 4, fy + 1, cz + 2, "east") if w.is_empty(cx - 4, fy + 1, cz + 2) else None
    if h.storage.summary().get("chest", 0) < 3:
        h.chest(w, cx - 2, fy + 1, cz - 4, "south")
    for (x, z) in ((cx - 4, cz - 1), (cx - 4, cz + 1)):
        w.set(x, fy + 1, z, B.S("lectern", facing="east", has_book=True))
    h.vault(w, cx + 4, fy + 1, cz, "west")
    K.lamp(w, cx, fy + 6, cz, chain=0)
    # stairs between the floors, along the walls (they cut their way through whatever is in the way)
    FN.stair_up(w, cx - 3, cz + 4, floors[0], 7, "east", wood="dark_oak")
    FN.stair_up(w, cx + 3, cz - 4, floors[1], 7, "west", wood="dark_oak")
    # ------------------------------------------------------------------ the instrument makers' workshop (north)
    wx0, wx1, wz0, wz1 = -3, 9, -15, -9
    w.fill(wx0, 0, wz0, wx1, 0, wz1, F("polished_andesite"))
    for x in range(wx0, wx1 + 1):
        for z in range(wz0, wz1 + 1):
            if x in (wx0, wx1) or z in (wz0, wz1):
                for y in range(1, 5):
                    w.set(x, y, z, blue if y > 1 else deep)
    K.clear(w, wx0 + 1, 1, wz0 + 1, wx1 - 1, 9, wz1 - 1)
    S.hip_roof(w, wx0, wz0, wx1, wz1, 5, "slate_roof_tiles", overhang=1, pitch=1)
    for x in range(wx0 + 2, wx1 - 1, 3):
        w.set(x, 2, wz0, B.pane("light_blue"))
        w.set(x, 3, wz0, B.pane("light_blue"))
    K.door(w, 3, 0, wz1, "north", wood="dark_oak")
    h.stations_row(w, [(x, wz0 + 1) for x in range(wx0 + 1, wx1)], 0, "south",
                   kinds=("crafting_table", "cartography_table", "anvil", "smithing_table", "grindstone",
                          "stonecutter", "loom", "furnace", "blast_furnace", "smoker", "brewing_stand"))
    h.station(w, wx0 + 1, 1, wz1 - 1, "fletching_table")
    for x in (0, 6):
        K.lamp(w, x, 4, -12)
    # ------------------------------------------------------------------ the garden: sundial, sphere, dummies, bell
    _disc(w, -4, 9, 2.2, 0, F("calcite"))
    w.set(-4, 1, 9, F("polished_deepslate"))
    w.set(-4, 2, 9, B.S("lightning_rod", facing="up"))
    ax, az = 10, 10                                                # the armillary sphere
    w.set(ax, 1, az, F("polished_deepslate"))
    w.set(ax, 2, az, B.fence("dark_oak"))
    for a in range(0, 360, 30):
        x = ax + round(2 * math.cos(math.radians(a)))
        y = 4 + round(2 * math.sin(math.radians(a)))
        w.set(x, y, az, gold)
        z = az + round(2 * math.cos(math.radians(a)))
        w.set(ax, y, z, F("waxed_cut_copper"))
    w.set(ax, 4, az, B.S("sea_lantern"))
    K.training_ground(h, w, [(1, 12), (4, 13)], 0, 180.0, looks=(0, 2))
    h.target(w, 6, 0, 14)
    for z in (13, 15):                                               # the bell-cote
        w.fill(-8, 1, z, -8, 3, z, deep)
    w.fill(-8, 4, 13, -8, 4, 15, deep)
    w.set(-8, 5, 14, SL("slate_roof_tiles", "bottom"))
    h.bell(w, -8, 3, 14, facing="east", attachment="ceiling")
    K.path(w, [(-9, 0), (cx - R - 1, 0)], 0, ["polished_andesite", "andesite", "stone_bricks"], width=3, seed=104)
    K.path(w, [(-6, 2), (-6, 12), (6, 12)], 0, ["polished_andesite", "andesite"], width=1, seed=105)
    for (x, z) in ((-14, -8), (-12, 11), (16, -6), (15, 9), (12, -15)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "spruce", seed=x + 3 * z)
    for (x, z) in ((-10, -10), (14, 0), (-2, -5)):
        if (x, z) in cells:
            K.rock(w, x, 0, z, r=1.4, states=("stone", "calcite", "andesite"), seed=x * z)
    K.scatter_plants(w, cells, 0, seed=106, kinds=("grass", "fern", "azure_bluet", "cornflower", "lily_of_the_valley"),
                     density=0.22, on=("grass_block",))
    for (x, z) in ((-8, -3), (-8, 3)):
        deco.lamp_post(w, x, 0, z, height=3)
    return h.finish()


DESIGNS = {
    "flower_glasshouse": flower_glasshouse,
    "dwarven_forge": dwarven_forge,
    "desert_palace": desert_palace,
    "observatory": observatory,
}
INTERIORS = {
    "flower_glasshouse": {"dome": ((-3.0, 2.62, 0.5), (12.0, 5.0, 0.0)),
                          "shed": ((-2.0, 2.62, -10.0), (8.0, 2.0, -12.0))},
    "dwarven_forge": {"hall": ((-8.0, 3.62, 0.5), (14.0, 6.0, 0.0)), "forge": ((4.0, 2.62, -2.0), (3.0, 3.0, -8.0))},
    "desert_palace": {"court": ((-9.0, 2.62, 0.5), (8.0, 4.0, 0.0)), "iwan": ((6.0, 2.62, 0.5), (12.0, 4.0, 0.0))},
    "observatory": {"orrery": ((1.5, 2.62, 0.5), (10.0, 3.0, 0.0)), "telescope": ((3.0, 16.62, -3.0),
                                                                               (12.0, 22.0, 0.0))},
}
