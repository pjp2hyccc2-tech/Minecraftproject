"""Tier 1 guild houses (こぢんまり, about 33 x 33): the northern longhouse, the mushroom grove, the ancient ruins and
the pirate ship. Each stands on its own floating island; the return gate is on the west side facing east."""
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


def _shield(base, pattern="mc", color=0):
    return ('{id:"minecraft:shield",Count:1b,tag:{BlockEntityTag:{Base:%d,Patterns:[{Pattern:"%s",Color:%d},'
            '{Pattern:"bo",Color:15}]}}}' % (base, pattern, color))


# ====================================================================================== 北方の長屋
def northern_longhouse(w):
    """北方の長屋: a fjord-side longhouse of tarred spruce under a steep shingle roof with carved horns crossing
    over the gables. Inside, a long hearth runs down the hall between two rows of posts and fur-strewn benches to
    the high seat; outside a smithy lean-to, a fenced training yard with a bell frame, a rune stone, firewood and
    spruces on a mossy island with a rocky ridge on its west side, where the gate stands."""
    h = K.House(w, "northern_longhouse")
    cells = K.island(w, 0, 0, 15.5, 14.0, depth=15, surface=("grass_block", "podzol", "coarse_dirt", "moss_block"),
                     rock=("stone", "andesite", "cobblestone", "tuff", "stone"), seed=11)
    K.drip_stalactites(w, cells, 0, seed=11)
    h.gate(mass=("stone", "andesite", "mossy_cobblestone", "cobblestone", "stone"), mass_half=11, mass_depth=6)
    # ------------------------------------------------------------------ the hall: x -1..13, z -5..5
    x0, x1, z0, z1 = -1, 13, -5, 5
    w.fill(x0 - 1, 0, z0 - 1, x1 + 1, 0, z1 + 1, F("cobblestone"))
    w.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, F("spruce_planks"))
    for x in range(x0 + 1, x1):
        w.set(x, 0, z0 + 1, F("dark_oak_planks"))
        w.set(x, 0, z1 - 1, F("dark_oak_planks"))
    K.clear(w, x0, 1, z0, x1, 13, z1)
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            for y in range(1, 5):
                post = (x - x0) % 3 == 0
                w.set(x, y, z, B.log("dark_oak", "y") if post else (F("cobblestone") if y == 1 else
                                                                  F("spruce_planks")))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            for y in range(1, 5):
                post = z in (z0, z1, 0) or abs(z) == 3
                w.set(x, y, z, B.log("dark_oak", "y") if post else (F("cobblestone") if y == 1 else
                                                                  F("spruce_planks")))
    for x in range(x0, x1 + 1):                      # wall plates
        for z in (z0, z1):
            w.set(x, 4, z, B.log("spruce", "x"))
    ridge = S.gable_roof(w, x0, z0, x1, z1, 5, "shingle_roof_tiles", axis="x", overhang=1, pitch=1,
                         gable_state=F("spruce_planks"), eave_trim="spruce")
    # carved horns crossing over both gable ends
    for xe in (x0 - 1, x1 + 1):
        w.set(xe, ridge, -1, B.log("dark_oak", "y"))
        w.set(xe, ridge, 1, B.log("dark_oak", "y"))
        w.set(xe, ridge + 1, -1, ST("dark_oak", "north"))
        w.set(xe, ridge + 1, 1, ST("dark_oak", "south"))
        w.set(xe, ridge, 0, F("dark_oak_planks"))
    # gable vents, a smoke hole over the hearth
    for xe in (x0, x1):
        w.set(xe, 7, 0, B.S("spruce_trapdoor", facing="east" if xe == x1 else "west", half="bottom", open=True))
    K.TK.smoke(w, 6, ridge + 1, 0)
    # shuttered windows along the long walls
    for x in (2, 5, 8, 11):
        for z, out in ((z0, "north"), (z1, "south")):
            if (x, z) == (2, z0):
                continue                                # the board hangs here
            w.set(x, 2, z, AIR)
            w.set(x, 3, z, AIR)
            oz = -1 if z == z0 else 1
            w.set(x, 2, z + oz, B.S("spruce_trapdoor", facing=out, half="bottom", open=True))
            w.set(x, 3, z + oz, B.S("spruce_trapdoor", facing=out, half="bottom", open=True))
    # the doorway in the west gable with a little porch
    K.clear(w, x0, 1, -1, x0, 3, 1)
    for z in (-2, 2):
        w.fill(x0 - 3, 1, z, x0 - 3, 4, z, B.log("dark_oak", "y"))
        w.set(x0 - 3, 5, z, B.log("spruce", "x"))
        w.set(x0 - 2, 5, z, B.log("spruce", "x"))
    for x in (x0 - 3, x0 - 2, x0 - 1):
        for z in range(-3, 4):
            w.set(x, 6 if abs(z) < 2 else 5, z, SL("shingle_roof_tiles", "bottom"))
    w.set(x0 - 1, 4, 0, F("chiseled_stone_bricks"))
    K.mix(w, x0 - 3, 0, -2, x0 - 1, 0, 2, [F("cobblestone"), F("mossy_cobblestone"), F("stone_bricks")], 5)
    deco.item_frame(w, x0 - 1, 4, -2, "west", _shield(14, "mc", 0))
    deco.item_frame(w, x0 - 1, 4, 2, "west", _shield(11, "mc", 0))
    # ------------------------------------------------------------------ inside
    # two rows of posts carrying the roof, beams across
    for x in (2, 5, 8, 11):
        for z in (-3, 3):
            w.fill(x, 1, z, x, 5, z, B.log("spruce", "y"))
        w.fill(x, 6, -3, x, 6, 3, B.log("dark_oak", "z"))
    # the long hearth: a stone trench with three fires
    for x in range(3, 10):
        for z in (-1, 0, 1):
            w.set(x, 0, z, F("stone_bricks") if z else F("cobblestone"))
    for x in range(3, 10):
        for z in (-1, 1):
            w.set(x, 1, z, SL("stone_brick", "bottom"))
    for x in (4, 6, 8):
        w.set(x, 1, 0, B.S("campfire", lit=True, facing="north"))
    for x in (5, 7):
        w.set(x, 1, 0, SL("cobblestone", "bottom"))
    w.set(3, 1, 0, B.S("water_cauldron", level=3))
    w.set(9, 1, 0, B.S("cauldron"))
    # benches along both walls; tables between the posts
    for x in range(1, 12):
        if x in (2, 5, 8, 11):
            continue
        w.set(x, 1, z0 + 1, ST("spruce", "north"))
        w.set(x, 1, z1 - 1, ST("spruce", "south"))
    for x in (4, 7, 10):
        w.set(x, 1, z0 + 2, SL("spruce", "top"))
        FN.goods(w, x, 2, z0 + 2, seed=x, kinds=("candle", "flower_pot", "candle"))
    for x in (1, 4, 7, 10):
        w.set(x, 1, z1 - 2, SL("spruce", "top"))
        FN.goods(w, x, 2, z1 - 2, seed=x + 9, kinds=("candle", "decorated_pot", "candle"))
    for x in (3, 6, 9):
        w.set(x, 1, z0 + 2, B.carpet("brown"), only_empty=True)
        w.set(x, 1, z1 - 2, B.carpet("white"), only_empty=True)
    # the high seat on a dais at the east end, the house's banners behind it
    w.fill(11, 1, -2, 12, 1, 2, SL("dark_oak", "top"))
    w.set(12, 2, 0, ST("dark_oak", "west"))
    w.set(12, 2, -1, B.trapdoor("dark_oak", "south", "bottom", True))
    w.set(12, 2, 1, B.trapdoor("dark_oak", "north", "bottom", True))
    for z in (-2, 2):
        K.crest_banner(w, 12, 4, z, "west", "cyan", [("ts", "purple"), ("cbo", "white"), ("mr", "purple")])
    deco.item_frame(w, 12, 4, 0, "west", _shield(9, "sku", 0))
    h.vault(w, 12, 2, -3, "west")
    h.chest(w, 12, 2, 3, "west")
    # shields on the walls (fixed frames)
    for (x, z, fac, col) in ((3, z0 + 1, "south", 9), (9, z0 + 1, "south", 10), (3, z1 - 1, "north", 1),
                             (9, z1 - 1, "north", 14), (6, z0 + 1, "south", 4), (6, z1 - 1, "north", 3)):
        deco.item_frame(w, x, 3, z, fac, _shield(col))
    # the guild board on the north wall by the door, a chest by the door
    for x in (0, 1, 2):
        w.set(x, 1, z0 + 1, AIR)
    h.board(w, 1, 2, z0 + 1, "south")
    h.chest(w, 0, 1, z1 - 1, "north")
    w.set(1, 1, z1 - 1, B.barrel("up"))
    w.set(1, 2, z1 - 1, B.lantern())
    for x in (3, 6, 9):
        K.lamp(w, x, 5, -1)
        K.lamp(w, x, 5, 1)
    # ------------------------------------------------------------------ the smithy lean-to (north)
    sx0, sx1, sz0, sz1 = 1, 11, -11, -7
    K.mix(w, sx0 - 1, 0, sz0, sx1 + 1, 0, sz1, [F("gravel"), F("coarse_dirt"), F("cobblestone"), F("andesite")], 9)
    for x in (sx0, sx1, 6):
        w.fill(x, 1, sz1, x, 3, sz1, B.log("spruce", "y"))
    w.fill(sx0, 1, sz0 - 1, sx1, 3, sz0 - 1, F("spruce_planks"))
    for x in (sx0, 6, sx1):
        w.fill(x, 1, sz0 - 1, x, 3, sz0 - 1, B.log("dark_oak", "y"))
    for x in range(sx0 - 1, sx1 + 2):
        for z in range(sz0 - 1, sz1 + 2):
            w.set(x, 4, z, SL("shingle_roof_tiles", "top" if z < sz1 else "bottom"))
    h.stations_row(w, [(x, sz0) for x in range(sx0 + 1, sx1) if x != 6], 0, "south",
                   kinds=("crafting_table", "furnace", "anvil", "smithing_table", "grindstone", "stonecutter",
                          "loom", "cartography_table"))
    h.station(w, 6, 1, sz0, "blast_furnace", "south")
    w.fill(6, 1, sz0 - 2, 6, 7, sz0 - 2, F("cobblestone"))
    K.TK.smoke(w, 6, 8, sz0 - 2, heavy=True)
    for x in (3, 9):
        K.lamp(w, x, 3, sz0 + 2)
    for z in (sz1 - 1, sz1):
        w.fill(sx1 + 2, 1, z, sx1 + 3, 2, z, B.log("spruce", "z"))
    w.set(sx0 - 2, 1, sz1, B.log("oak", "y"))
    # ------------------------------------------------------------------ the training yard (south)
    yx0, yx1, yz0, yz1 = 0, 11, 8, 13
    K.mix(w, yx0, 0, yz0, yx1, 0, yz1, [F("coarse_dirt"), F("packed_mud"), F("coarse_dirt"), F("gravel")], 13)
    K.fence_line(w, [(yx0, yz0), (yx0, yz1), (yx1, yz1), (yx1, yz0), (8, yz0)], 0, B.fence("spruce"))
    w.set(yx0, 1, yz0 + 2, AIR)
    w.set(yx0, 1, yz0 + 3, AIR)
    K.training_ground(h, w, [(3, 11), (6, 11), (9, 11)], 0, 0.0, looks=(0, 1, 0))
    h.target(w, 10, 0, 9)
    # the bell frame at the yard's gate
    for z in (yz0 + 1, yz0 + 4):
        w.fill(-3, 1, z, -3, 4, z, B.log("dark_oak", "y"))
    w.fill(-3, 5, yz0 + 1, -3, 5, yz0 + 4, B.log("dark_oak", "z"))
    w.set(-3, 6, yz0 + 2, SL("shingle_roof_tiles", "bottom"))
    w.set(-3, 6, yz0 + 3, SL("shingle_roof_tiles", "bottom"))
    h.bell(w, -3, 4, yz0 + 2, facing="east", attachment="ceiling")
    w.set(-3, 4, yz0 + 3, B.lantern(hanging=True))
    # ------------------------------------------------------------------ grounds
    K.path(w, [(-6, 0), (x0 - 3, 0)], 0, ["cobblestone", "mossy_cobblestone", "gravel", "coarse_dirt"], width=3,
           seed=17)
    K.path(w, [(-4, 1), (-4, 9)], 0, ["gravel", "coarse_dirt", "cobblestone"], width=1, seed=18)
    w.fill(-5, 1, -6, -5, 3, -6, F("andesite"))                 # the rune stone
    w.set(-5, 2, -6, F("chiseled_stone_bricks"))
    w.set(-5, 4, -6, SL("andesite", "bottom"))
    w.set(-4, 1, -6, B.S("moss_carpet"))
    for (x, z) in ((-13, -10), (-12, 10), (14, -7), (13, 9), (4, 14), (-6, -12), (10, -13), (-14, 4), (15, 2)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "spruce", seed=x * 7 + z)
    for (x, z) in ((-8, 9), (12, -4), (-2, -9), (15, -2)):
        if (x, z) in cells:
            K.rock(w, x, 0, z, r=1.3, states=("stone", "andesite", "mossy_cobblestone"), seed=x + z)
    K.scatter_plants(w, cells, 0, seed=19, kinds=("grass", "fern", "grass", "sweet_berry_bush", "dandelion",
                                                 "lily_of_the_valley"), density=0.22, on=("grass_block", "podzol"))
    for z in (-3, 3):
        deco.lamp_post(w, -4, 0, z, height=3)
    for (x, z) in ((-12, -3), (-13, 2), (-11, 6), (-14, -6)):       # snow on the ridge
        for dx in (0, 1):
            for dz in (0, 1):
                yy = w.top_y(x + dx, z + dz)
                if yy >= 3 and w.is_empty(x + dx, yy + 1, z + dz):
                    w.set(x + dx, yy + 1, z + dz, B.S("snow", layers=1 + int(hash2(x + dx, z + dz, 4) * 2)))
    return h.finish()


# ====================================================================================== 茸の森
def _cap(w, cx, cz, y0, r, height, block, spots=None, seed=0, rim_drop=1, air=False):
    """A mushroom cap: a flattened dome over the disc r at y0, hollow (``air``: the hollow is set to air, for a
    room inside), its rim curling down ``rim_drop``."""
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            d = math.hypot(dx, dz)
            if d > r + 0.3:
                continue
            top = y0 + height * (1.0 - (d / (r + 0.3)) ** 2.2)
            for y in range(y0, int(round(top)) + 1):
                outer = y >= int(round(top)) - 1 or d >= r - 0.9
                if not outer:
                    if air and y > y0:
                        w.set(cx + dx, y, cz + dz, AIR)
                    continue
                st = block
                if spots and y >= int(round(top)) - 1 and hash3(cx + dx, y, cz + dz, seed) < 0.13:
                    st = spots
                w.set(cx + dx, y, cz + dz, st)
            if d >= r - 0.9:
                for k in range(1, rim_drop + 1):
                    w.set(cx + dx, y0 - k, cz + dz, block)


def mushroom_grove(w):
    """茸の森: a mossy island grown over with giant mushrooms. The hall is the hollow stem of a great fly agaric
    whose spotted cap holds a round loft (spore blossoms drift pink from its gills); the workshop sits under a broad
    brown cap, the training ground inside a fairy ring beside a glowing teal mushroom; shroomlight lamps on stalks,
    glow lichen and hanging roots under the island."""
    h = K.House(w, "mushroom_grove")
    cells = K.island(w, 1, 0, 15.5, 15.0, depth=14, surface=("mycelium", "moss_block", "podzol", "moss_block"),
                     soil="dirt", rock=("stone", "tuff", "dirt", "andesite", "rooted_dirt"), seed=21)
    for (x, z) in sorted(cells):                                 # roots and lichen under the island
        if hash2(x, z, 23) < 0.06:
            y = -3
            while y > -30 and not w.is_empty(x, y, z):
                y -= 1
            for k in range(1 + int(hash2(z, x, 24) * 3)):
                w.set(x, y - k, z, B.S("hanging_roots"))
    h.gate(mass=("moss_block", "dirt", "stone", "mossy_cobblestone", "rooted_dirt"), mass_half=10, mass_depth=6)
    # ------------------------------------------------------------------ the great fly agaric (hall + loft)
    cx, cz = 6, 0
    S.cylinder(w, cx, cz, 4.4, 0, 0, F("dark_oak_planks"))
    S.cylinder(w, cx, cz, 4.4, 1, 9, F("mushroom_stem"), hollow=True, thickness=1.2)
    S.cylinder(w, cx, cz, 3.4, 1, 9, AIR)
    K.mix(w, cx - 3, 0, cz - 3, cx + 3, 0, cz + 3, [F("spruce_planks"), F("spruce_planks"), F("dark_oak_planks")], 2)
    # the doorway (west) and round windows
    K.clear(w, cx - 4, 1, -1, cx - 3, 3, 1)
    w.set(cx - 4, 3, -1, ST("birch", "north", "top"))
    w.set(cx - 4, 3, 1, ST("birch", "south", "top"))
    for (x, z) in ((cx, cz - 4), (cx, cz + 4)):
        w.set(x, 3, z, B.pane("brown"))
        w.set(x, 4, z, B.pane("brown"))
    # inside: the board on the east wall, a stove, chests, a round table with stools, a lectern
    h.board(w, cx + 3, 2, cz, "west")
    w.set(cx + 3, 1, cz - 1, B.S("campfire", lit=True, facing="west"))
    w.set(cx + 3, 1, cz + 1, B.barrel("up"))
    w.set(cx, 1, cz, F("mushroom_stem"))
    w.set(cx, 2, cz, B.potted("red_mushroom"))
    for (x, z, fac) in ((cx - 1, cz, "east"), (cx + 1, cz, "west")):
        w.set(x, 1, z, ST("dark_oak", OPP[fac]))
    h.chest(w, cx - 1, 1, cz - 3, "south")
    w.set(cx, 1, cz - 3, B.barrel("up"))
    w.set(cx, 2, cz - 3, B.lantern())
    h.chest(w, cx + 1, 1, cz - 3, "south")
    w.set(cx + 1, 1, cz + 3, B.S("bookshelf"))
    w.set(cx, 1, cz + 3, B.S("lectern", facing="north", has_book=True))
    # a ladder up into the loft (south-west)
    for y in range(1, 10):
        w.set(cx - 2, y, cz + 3, F("mushroom_stem"))
        w.set(cx - 2, y, cz + 2, B.S("ladder", facing="north"))
    # the cap and the loft under it
    loft = 9
    S.cylinder(w, cx, cz, 9.0, loft, loft, F("mushroom_stem"))
    w.set(cx - 2, loft, cz + 2, B.S("ladder", facing="north"))
    _cap(w, cx, cz, loft, 9.6, 7.0, F("red_mushroom_block"), spots=F("mushroom_stem"), seed=26, rim_drop=1,
         air=True)
    for (dx, dz) in ((-9, 0), (0, -9), (0, 9), (9, 0)):                # windows through the cap
        for k in (-1, 0, 1):
            ax, az = (cx + dx + (0 if dx else k), cz + dz + (0 if dz else k))
            for y in (loft + 2, loft + 3):
                for step in range(0, 2):
                    px = ax - (1 if dx > 0 else -1 if dx < 0 else 0) * step
                    pz = az - (1 if dz > 0 else -1 if dz < 0 else 0) * step
                    if w.get(px, y, pz) != AIR:
                        w.set(px, y, pz, B.pane("white") if step == 0 else AIR)
    FN.rug(w, cx - 4, cz - 4, cx + 4, cz + 4, loft, field="lime", border="green")
    h.vault(w, cx + 6, loft + 1, cz, "west")
    w.set(cx + 6, loft + 1, cz - 1, B.S("bookshelf"))
    w.set(cx + 6, loft + 1, cz + 1, B.S("bookshelf"))
    K.lounge(w, cx - 4, loft, cz - 6, "south", wood="mangrove", carpet="brown", border="lime", length=3)
    for (x, z) in ((cx, cz), (cx - 4, cz + 4), (cx + 4, cz - 4)):
        w.set(x, loft + 5, z, F("shroomlight"))
    for (x, z) in ((cx + 3, cz + 5), (cx - 5, cz + 1)):
        w.set(x, loft + 1, z, B.potted("brown_mushroom"))
    for (dx, dz) in ((7, 3), (-7, -3), (3, -7), (-3, 7), (6, -5), (-5, 6)):    # spore blossoms under the gills
        w.set(cx + dx, loft - 1, cz + dz, B.S("spore_blossom"))
    # ------------------------------------------------------------------ the workshop under a brown cap (north)
    bx, bz = 0, -10
    w.fill(bx, 1, bz, bx, 5, bz, F("mushroom_stem"))
    _cap(w, bx, bz, 6, 5.6, 1.2, F("brown_mushroom_block"), seed=27, rim_drop=0)
    for (dx, dz) in ((-1, 0), (0, -1), (0, 1)):
        w.set(bx + dx, 5, bz + dz, F("mushroom_stem"))
    K.mix(w, bx - 4, 0, bz - 4, bx + 4, 0, bz + 4, [F("podzol"), F("coarse_dirt"), F("moss_block")], 28)
    ring = [(bx - 3, bz - 1), (bx - 3, bz + 1), (bx - 2, bz + 3), (bx, bz + 3), (bx + 2, bz + 3), (bx + 3, bz + 1),
            (bx + 3, bz - 1), (bx + 2, bz - 3), (bx, bz - 3)]
    kinds = ("crafting_table", "furnace", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
             "cartography_table", "brewing_stand")
    for (x, z), kind in zip(ring, kinds):
        h.station(w, x, 1, z, kind, "south")
    w.set(bx + 1, 5, bz, F("mushroom_stem"))                    # the rally bell hangs from the stem's arm
    h.bell(w, bx + 1, 4, bz, facing="east", attachment="ceiling")
    # ------------------------------------------------------------------ the training ground: a fairy ring
    tx, tz = 4, 11
    K.mix(w, tx - 3, 0, tz - 2, tx + 3, 0, tz + 2, [F("moss_block"), F("podzol"), F("moss_block")], 29)
    for a in range(0, 360, 24):
        x = round(tx + 4.6 * math.cos(math.radians(a)))
        z = round(tz + 3.4 * math.sin(math.radians(a)))
        if (x, z) in cells and w.is_empty(x, 1, z):
            w.set(x, 1, z, B.S("red_mushroom" if a % 48 else "brown_mushroom"))
    K.training_ground(h, w, [(tx - 2, tz), (tx + 2, tz)], 0, 180.0, looks=(0, 2))
    h.target(w, tx, 0, tz + 3)
    gx, gz = 11, 10                                             # a glowing teal mushroom over the ring
    w.fill(gx, 1, gz, gx, 6, gz, B.S("warped_stem", axis="y"))
    _cap(w, gx, gz, 7, 3.6, 1.6, F("warped_wart_block"), spots=F("shroomlight"), seed=30, rim_drop=1)
    # shroomlight lamps on stalks, small mushrooms, more caps
    for (x, z) in ((-5, -3), (-5, 3), (2, -4), (2, 4), (13, -4)):
        if (x, z) in cells:
            w.fill(x, 1, z, x, 2, z, F("mushroom_stem"))
            w.set(x, 3, z, F("shroomlight"))
            w.set(x, 4, z, SL("mangrove", "bottom"))
    for (x, z, r) in ((13, -8, 2.6), (-2, 12, 2.2), (-12, -9, 2.4), (14, 4, 2.0)):
        if (x, z) in cells:
            hh = 3 + int(r)
            w.fill(x, 1, z, x, hh, z, F("mushroom_stem"))
            _cap(w, x, z, hh + 1, r, 1.4, F("red_mushroom_block" if (x + z) % 2 else "brown_mushroom_block"),
                 spots=F("mushroom_stem") if (x + z) % 2 else None, seed=x * 3 + z, rim_drop=1)
    K.path(w, [(-6, 0), (cx - 5, 0)], 0, ["podzol", "coarse_dirt", "mossy_cobblestone", "moss_block"], width=3,
           seed=31)
    K.path(w, [(-3, -2), (-1, -6)], 0, ["podzol", "coarse_dirt"], width=1, seed=32)
    K.scatter_plants(w, cells, 0, seed=33, kinds=("red_mushroom", "brown_mushroom", "fern", "moss_carpet", "grass",
                                                 "azalea", "fern"), density=0.2,
                     on=("mycelium", "moss_block", "podzol"))
    return h.finish()


# ====================================================================================== 古代遺跡
RUIN = ("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "mossy_stone_bricks")


def _ruin_wall(w, x0, z0, x1, z1, y0, y1, seed):
    """A ruined masonry wall (axis-aligned line): mixed weathered bricks, its top broken off unevenly."""
    n = max(abs(x1 - x0), abs(z1 - z0))
    for i in range(n + 1):
        x = x0 + (x1 - x0) * i // max(n, 1)
        z = z0 + (z1 - z0) * i // max(n, 1)
        top = y1 - int(max(0.0, fbm2(x, z, 3.0, 2, seed)) * 6)
        for y in range(y0, top + 1):
            w.set(x, y, z, F(RUIN[int(hash3(x, y, z, seed) * len(RUIN)) % len(RUIN)]))


def _column(w, x, z, y0, height, broken=False, seed=0):
    w.set(x, y0, z, F("chiseled_stone_bricks"))
    top = y0 + height - (int(hash2(x, z, seed) * 3) + 1 if broken else 0)
    for y in range(y0 + 1, top):
        w.set(x, y, z, F("polished_andesite") if (y + x) % 3 else F("andesite"))
    w.set(x, top, z, SL("polished_andesite", "bottom") if broken else F("chiseled_stone_bricks"))


def ancient_ruins(w):
    """古代遺跡: the ruin of a temple on a grassy island. A podium with a broken peristyle of andesite columns; the
    cella restored as the guild hall under a mended roof, with a mosaic floor, an eternal flame on the altar and
    runes glowing in the walls; an oak grows through the fallen east end. A stoa of broken columns shelters the
    workshop, dummies stand among fallen drums in the courtyard, an old bell hangs in a surviving arch, and a
    reflecting pool holds lilies."""
    h = K.House(w, "ancient_ruins")
    cells = K.island(w, 0, 0, 15.5, 15.0, depth=14, surface=("grass_block", "moss_block", "coarse_dirt",
                                                               "grass_block"), seed=41)
    K.drip_stalactites(w, cells, 0, seed=41)
    h.gate(mass=RUIN + ("cobblestone", "andesite"), mass_half=10, mass_depth=6)
    # ------------------------------------------------------------------ podium and peristyle
    px0, px1, pz0, pz1 = -2, 14, -7, 7
    K.mix(w, px0, 0, pz0, px1, 1, pz1, [F("stone_bricks"), F("mossy_stone_bricks"), F("cracked_stone_bricks")], 42)
    for z in range(-2, 3):
        w.set(px0 - 1, 1, z, ST("stone_brick", "east"))
    cols = [(x, z) for x in (px0, 2, 6, 10, px1) for z in (pz0, pz1)] + [(px0, -3), (px0, 3), (px1, -3), (px1, 3)]
    for i, (x, z) in enumerate(cols):
        broken = hash2(x, z, 44) < 0.4 or x == px1
        _column(w, x, z, 2, 6, broken=broken, seed=45 + i)
    for x in range(px0, 9):                                       # surviving entablature on the west part
        for z in (pz0, pz1):
            if hash2(x, z, 46) < 0.8:
                w.set(x, 8, z, F("stone_bricks"))
    # ------------------------------------------------------------------ the cella x 1..12, z -4..4, floor y 1
    cx0, cx1, cz0, cz1 = 1, 12, -4, 4
    for x in range(cx0, cx1 + 1):
        for z in (cz0, cz1):
            _ruin_wall(w, x, z, x, z, 2, 7 if x < 9 else 5, 47)
    for z in range(cz0, cz1 + 1):
        _ruin_wall(w, cx1, z, cx1, z, 2, 5, 49)
    for x in range(cx0, 9):                                        # the mended part keeps its full height
        for z in (cz0, cz1):
            for y in range(2, 7):
                if w.is_empty(x, y, z):
                    w.set(x, y, z, F(RUIN[int(hash3(x, y, z, 50) * 5)]))
    for z in range(cz0, cz1 + 1):
        for y in range(2, 7):
            w.set(cx0, y, z, F(RUIN[int(hash3(cx0, y, z, 51) * 5)]))
    for x in range(cx0 + 1, cx1):                                  # mosaic floor
        for z in range(cz0 + 1, cz1):
            r = max(abs(x - 6.5), abs(z) * 1.5)
            w.set(x, 1, z, F(("polished_diorite", "terracotta", "polished_andesite", "cyan_terracotta")[int(r) % 4]))
    K.clear(w, cx0 + 1, 2, cz0 + 1, cx1 - 1, 9, cz1 - 1)
    K.clear(w, cx0, 2, -1, cx0, 5, 1)                               # doorway under a round arch
    w.set(cx0, 5, -1, ST("stone_brick", "north", "top"))
    w.set(cx0, 5, 1, ST("stone_brick", "south", "top"))
    w.set(cx0, 6, 0, F("chiseled_stone_bricks"))
    # the mended roof over the west part, the east end open with an oak growing through
    S.gable_roof(w, cx0, cz0, 9, cz1, 7, "terracotta_roof_tiles", axis="x", overhang=1, pitch=1,
                 gable_state=F("stone_bricks"))
    for x in range(cx0 + 1, 9):
        if x % 2 == 0:
            w.fill(x, 7, cz0 + 1, x, 7, cz1 - 1, B.log("spruce", "z"))
    K.tree(w, 11, 1, 2, "oak", seed=52, height=7, crown=3.6)
    for (x, z) in ((10, -2), (11, -1), (9, 3)):
        w.set(x, 2, z, F("cracked_stone_bricks") if (x + z) % 2 else SL("stone_brick", "bottom"))
    # inside: altar with the eternal flame, glowing runes, the board, chests, the vault in the back
    w.fill(8, 2, -1, 8, 2, 1, F("chiseled_stone_bricks"))
    w.set(8, 3, 0, B.S("soul_campfire", lit=True, facing="west"))
    w.set(8, 3, -1, B.candle(3, "white"))
    w.set(8, 3, 1, B.candle(2, "white"))
    for (x, z) in ((3, cz0), (6, cz0), (3, cz1), (6, cz1)):
        w.set(x, 5, z, F("verdant_froglight"))
    h.board(w, 3, 3, cz0 + 1, "south")
    h.chest(w, 5, 2, cz1 - 1, "north")
    w.set(4, 2, cz1 - 1, B.S("decorated_pot"))
    h.chest(w, 7, 2, cz1 - 1, "north")
    w.set(6, 2, cz1 - 1, B.S("decorated_pot"))
    h.vault(w, 11, 2, 0, "west")
    w.set(11, 2, -1, F("chiseled_stone_bricks"))
    w.set(11, 3, -1, B.candle(4, "yellow"))
    for x in (3, 6):
        K.lamp(w, x, 6, 0)
    w.set(5, 2, 0, F("polished_andesite"))
    w.set(5, 3, 0, B.S("lectern", facing="west", has_book=True))
    for z in (-1, 1):
        w.set(4, 2, z * 2, ST("stone_brick", "east"))
    for (x, z, fac) in ((cx0, -3, "west"), (cx0, 3, "west"), (12, -2, "east"), (5, cz1, "south"),
                        (9, cz0, "north")):                         # ivy over the ruin
        dx, dz = K.STEP[fac]
        for y in range(3, 7):
            if w.is_empty(x + dx, y, z + dz):
                w.set(x + dx, y, z + dz, B.S("vine", **{OPP[fac]: True}))
    # ------------------------------------------------------------------ the stoa (north): the workshop
    sz = -10
    K.mix(w, 0, 0, sz - 2, 13, 0, sz + 1, [F("stone_bricks"), F("mossy_stone_bricks"), F("andesite"),
                                           F("cracked_stone_bricks")], 53)
    _ruin_wall(w, 0, sz - 2, 13, sz - 2, 1, 5, 54)
    for x in (0, 4, 8, 12):
        _column(w, x, sz + 1, 1, 5, broken=(x == 12), seed=55 + x)
    for x in range(0, 10):
        for z in range(sz - 2, sz + 2):
            w.set(x, 6, z, SL("terracotta_roof_tiles", "bottom" if z == sz + 1 else "top"))
    h.stations_row(w, [(x, sz - 1) for x in range(1, 12) if x not in (4, 8)], 0, "south",
                   kinds=("crafting_table", "furnace", "anvil", "smithing_table", "grindstone", "stonecutter",
                          "loom", "cartography_table", "brewing_stand"))
    for x in (2, 6):
        K.lamp(w, x, 5, sz)
    # ------------------------------------------------------------------ the courtyard (south): dummies, bell, pool
    K.mix(w, 0, 0, 9, 9, 0, 13, [F("coarse_dirt"), F("gravel"), F("stone_bricks"), F("moss_block")], 56)
    K.training_ground(h, w, [(2, 11), (5, 12), (8, 11)], 0, 180.0, looks=(0, 1, 0))
    h.target(w, 9, 0, 9)
    for (x, z, ax) in ((3, 9, "x"), (6, 13, "z")):                 # fallen column drums
        for k in range(3):
            px, pz = (x + k, z) if ax == "x" else (x, z + k)
            w.set(px, 1, pz, B.S("quartz_pillar", axis=ax))
    w.fill(-4, 1, 9, -4, 5, 9, F("stone_bricks"))                  # the surviving arch with the bell
    w.fill(-4, 1, 13, -4, 5, 13, F("mossy_stone_bricks"))
    w.fill(-4, 6, 9, -4, 6, 13, F("stone_bricks"))
    w.set(-4, 5, 10, ST("stone_brick", "north", "top"))
    w.set(-4, 5, 12, ST("stone_brick", "south", "top"))
    w.set(-4, 7, 11, F("chiseled_stone_bricks"))
    h.bell(w, -4, 5, 11, facing="east", attachment="ceiling")
    K.pond(w, 12, 11, 2.6, 2.0, 0, border=("stone_bricks", "mossy_stone_bricks"), depth=2, seed=57, lily=0.2)
    # ------------------------------------------------------------------ grounds
    K.path(w, [(-6, 0), (px0 - 2, 0)], 0, ["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
                                          "gravel"], width=3, seed=58)
    for (x, z) in ((-13, -8), (-11, 10), (14, -9), (15, 6), (-2, -13), (8, 15)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "birch" if (x + z) % 2 else "oak", seed=x * 5 + z, height=5)
    for (x, z) in ((-7, -9), (16, -2), (-9, 6)):
        if (x, z) in cells:
            _column(w, x, z, 1, 3, broken=True, seed=x)
    K.scatter_plants(w, cells, 0, seed=59, kinds=("grass", "fern", "poppy", "azure_bluet", "oxeye_daisy", "grass",
                                                 "cornflower"), density=0.28, on=("grass_block", "moss_block"))
    return h.finish()


# ====================================================================================== 海賊船
def _hull(w, x0, x1, cz, beam, keel, deck, plank, wale):
    """A ship's hull along x (stern x0, bow x1): its half-width swells to ``beam`` amidships, narrows to a point at
    the bow and to a flat transom at the stern; the sides curve in towards the keel. Hollow; decks added apart.
    Returns {(x, y): half-width}."""
    L = x1 - x0
    sides = {}
    for x in range(x0, x1 + 1):
        t = (x - x0) / L
        hw = beam * (0.78 + 0.22 * math.sin(min(1.0, t / 0.35) * math.pi / 2)) if t < 0.55 else \
            beam * math.cos((t - 0.55) / 0.45 * math.pi / 2) ** 0.8
        for y in range(keel, deck + 2):
            f = ((y - keel + 1) / (deck - keel + 1)) ** 0.55
            sides[(x, y)] = hw * min(1.0, f)
    for (x, y), hw in sides.items():
        r = int(round(hw))
        for z in range(cz - r, cz + r + 1):
            if abs(z - cz) >= r or y == keel or x in (x0, x1):
                w.set(x, y, z, wale if y in (deck - 2, deck + 1) else plank)
            else:
                w.set(x, y, z, AIR)
    return sides


def pirate_ship(w):
    """海賊船: a pirate ship at anchor in the lagoon of a palm island. Black sails with a white skull, a crow's
    nest and the jolly roger; the captain's cabin under the quarterdeck holds the board, the charts and the
    treasure vault behind the gallery windows; the hold below keeps the chests. On the beach a thatched shipwright's
    shed is the workshop, dummies and a target stand on the sand, and the ship's bell rings on the forecastle."""
    h = K.House(w, "pirate_ship")
    cells = K.island(w, 0, 0, 15.5, 15.5, depth=13, surface=("sand", "grass_block", "sand", "coarse_dirt"),
                     soil="sand", rock=("sandstone", "stone", "sandstone", "andesite"), seed=61)
    K.drip_stalactites(w, cells, 0, seed=61, kinds=("sandstone", "stone"))
    h.gate(mass=("sandstone", "stone", "smooth_sandstone", "andesite", "sandstone"), mass_half=10, mass_depth=6)
    # ------------------------------------------------------------------ the lagoon
    lx, lz, rx, rz = 6, 0, 9.6, 6.6
    for x in range(lx - 11, lx + 12):
        for z in range(lz - 8, lz + 9):
            d = math.hypot((x - lx) / rx, (z - lz) / rz) + fbm2(x, z, 4.0, 2, 62) * 0.08
            if d <= 1.0:
                for y in range(-4, 1):
                    w.set(x, y, z, B.S("water"))
                w.set(x, -5, z, F("sand"))
                w.set(x, 1, z, AIR)
            elif d <= 1.22:
                w.set(x, 0, z, F("sand"))
    # ------------------------------------------------------------------ the ship: stern x 0, bow x 14, deck y 2
    sx0, sx1, keel, deck = 0, 14, -3, 2
    sides = _hull(w, sx0, sx1, 0, 3.6, keel, deck, F("dark_oak_planks"), B.log("stripped_dark_oak", "x"))
    for (x, y), hw in sides.items():                             # main deck, hold floor, rail
        r = int(round(hw))
        if y == deck:
            w.fill(x, deck, -r + 1, x, deck, r - 1, F("spruce_planks"))
        if y == -1:
            w.fill(x, -1, -r + 1, x, -1, r - 1, F("dark_oak_planks"))
        if y == deck + 1:
            for z in (-r, r):
                w.set(x, deck + 1, z, B.fence("dark_oak"))
    # the quarterdeck and the captain's cabin under it (x 0..4)
    qx = 4
    for x in range(sx0, qx + 1):
        r = int(round(sides[(x, deck)]))
        for z in range(-r, r + 1):
            w.set(x, deck + 3, z, F("spruce_planks"))
            if abs(z) == r or x in (sx0, qx):
                for y in (deck + 1, deck + 2):
                    w.set(x, y, z, F("dark_oak_planks"))
        for z in (-r, r):
            w.set(x, deck + 4, z, B.fence("dark_oak"))
    for x in range(sx0, qx + 1):
        w.set(x, deck + 4, -int(round(sides[(x, deck)])), B.fence("dark_oak"))
    lo, hi = B.door_states("spruce", "west", "left", False)        # the cabin door (players walk west in)
    w.set(qx, deck + 1, 0, lo)
    w.set(qx, deck + 2, 0, hi)
    for z in (-2, 2):                                            # stern gallery windows
        w.set(sx0, deck + 2, z, B.pane("yellow"))
    for z in (-3, 3):
        w.set(qx, deck + 4, z, B.lantern())
    for y in (deck + 1, deck + 2, deck + 3):                     # a ladder up to the quarterdeck
        w.set(qx + 1, y, -2, B.S("ladder", facing="east"))
    w.set(qx, deck + 3, -2, F("spruce_planks"))
    w.set(qx, deck + 4, -2, AIR)
    w.set(qx + 1, deck + 4, -2, AIR)
    # inside the cabin: the board, the chart table, the treasure vault, a lantern
    for x in range(sx0 + 1, qx):
        r = int(round(sides[(x, deck)]))
        K.clear(w, x, deck + 1, -r + 1, x, deck + 2, r - 1)
    h.board(w, sx0 + 1, deck + 2, 0, "east")
    h.vault(w, sx0 + 1, deck + 1, -2, "east")
    w.set(sx0 + 1, deck + 1, 2, F("gold_block"))
    h.station(w, sx0 + 2, deck + 1, 2, "cartography_table")
    w.set(3, deck + 1, -2, B.barrel("up"))
    K.lamp(w, 2, deck + 2, 1)
    # the forecastle (x 12..14) with the ship's bell
    for x in range(12, sx1 + 1):
        r = int(round(sides[(x, deck)]))
        for z in range(-r + 1, r):
            w.set(x, deck + 1, z, F("spruce_planks"))
    w.set(11, deck + 1, 0, ST("spruce", "east"))
    w.fill(12, deck + 2, -1, 12, deck + 3, -1, B.fence("dark_oak"))
    w.fill(12, deck + 2, 1, 12, deck + 3, 1, B.fence("dark_oak"))
    w.fill(12, deck + 4, -1, 12, deck + 4, 1, F("dark_oak_planks"))
    h.bell(w, 12, deck + 3, 0, facing="east", attachment="ceiling")
    for k in range(1, 5):                                         # the bowsprit
        w.set(sx1 + k, deck + 1 + k // 2, 0, B.log("spruce", "x"))
    # masts, yards and black sails with a white skull
    for (mx, top, sail) in ((7, 19, 3), (11, 16, 2), (2, 13, 2)):
        base = deck + 1 if mx != 2 else deck + 4
        w.fill(mx, base, 0, mx, top, 0, B.log("spruce", "y"))
        yards = ((top - 2, sail), (top - 7, sail + 1)) if mx != 2 else ((top - 2, sail),)
        for (yy, half) in yards:
            w.fill(mx, yy, -half - 1, mx, yy, half + 1, B.log("spruce", "z"))
            for y in range(yy - 4 if mx != 2 else yy - 3, yy):
                bulge = 1 if y < yy - 1 else 0
                for z in range(-half, half + 1):
                    if not (z == 0 and bulge == 0):
                        w.set(mx + bulge, y, z, B.wool("black"))
            if mx == 7 and yy == top - 2:
                for (z, y) in ((-1, yy - 2), (1, yy - 2), (0, yy - 3), (-1, yy - 4), (1, yy - 4), (0, yy - 2)):
                    w.set(mx + 1, y, z, B.wool("white"))
        if mx == 7:                                             # crow's nest and the jolly roger
            w.fill(mx - 1, top - 5, -1, mx + 1, top - 5, 1, F("spruce_planks"))
            w.set(mx, top - 5, 0, B.log("spruce", "y"))
            for (dx, dz) in ((-1, -1), (-1, 1), (1, -1), (1, 1), (0, -1), (0, 1), (-1, 0), (1, 0)):
                w.set(mx + dx, top - 4, dz, B.fence("dark_oak"))
            w.set(mx, top + 1, 0, B.fence("dark_oak"))
            w.set(mx + 1, top + 1, 0, B.wall_banner("black", "east"))
            w.be(mx + 1, top + 1, 0, B.banner_nbt([("sku", "white"), ("bo", "black")]))
    for x in (5, 9):                                              # cannons at the gun ports
        for side in (-1, 1):
            r = int(round(sides[(x, deck)]))
            w.set(x, deck + 1, side * (r - 1), WL("polished_blackstone"))
            w.set(x, deck + 1, side * r, F("dark_oak_planks"))
    # the hold: chests and cargo, a ladder down through a hatch
    h.chest(w, 8, 0, -1, "south")
    h.chest(w, 10, 0, -1, "south")
    for (x, z) in ((6, 1), (7, 1), (11, 1), (6, -1), (8, 1)):
        w.set(x, 0, z, B.barrel("up") if (x + z) % 2 else B.S("hay_block", axis="x"))
    K.lamp(w, 7, 1, -1)
    for y in (0, 1, 2):
        w.set(9, y, 0, AIR)
        w.set(9, y, -1, B.S("ladder", facing="south"))
        w.set(9, y, -2, F("dark_oak_planks"))
    # ship's wheel on the quarterdeck
    w.set(2, deck + 4, 0, B.fence("spruce"))
    w.set(1, deck + 4, 0, B.S("spruce_trapdoor", facing="east", half="bottom", open=True))
    # ------------------------------------------------------------------ the dock and the gangplank (north side)
    dz = -8
    for x in range(-5, 12):
        w.set(x, 0, dz, F("spruce_planks"))
        w.set(x, 0, dz - 1, F("spruce_planks"))
        if x % 4 == 0:
            w.fill(x, -4, dz, x, -1, dz, B.log("spruce", "y"))
            w.set(x, 1, dz - 1, B.fence("spruce"))
    for (z, y, half) in ((-7, 0, "top"), (-6, 1, "bottom"), (-5, 1, "top"), (-4, 2, "bottom")):
        w.set(6, y, z, SL("spruce", half))
        w.set(6, y + 1, z, AIR)
        w.set(6, y + 2, z, AIR)
    w.set(6, deck + 1, -int(round(sides[(6, deck)])), AIR)
    # ------------------------------------------------------------------ the shipwright's shed (north-west)
    hx0, hx1, hz0, hz1 = -6, 3, -14, -10
    K.mix(w, hx0, 0, hz0, hx1, 0, hz1, [F("spruce_planks"), F("sand"), F("spruce_planks")], 64)
    for (x, z) in ((hx0, hz0), (hx1, hz0), (hx0, hz1), (hx1, hz1)):
        w.fill(x, 1, z, x, 3, z, B.log("jungle", "y"))
    for x in range(hx0 - 1, hx1 + 2):
        for z in range(hz0 - 1, hz1 + 2):
            ring = min(z - (hz0 - 1), (hz1 + 1) - z)
            w.set(x, 4 + min(ring, 2) // 2, z, SL("thatch_roof", "bottom" if ring % 2 == 0 else "top"))
    h.stations_row(w, [(x, hz0) for x in range(hx0 + 1, hx1)], 0, "south",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "furnace", "fletching_table"))
    K.lamp(w, -2, 3, -12)
    # ------------------------------------------------------------------ the beach: dummies, target, palms
    K.training_ground(h, w, [(-1, 11), (2, 12)], 0, 200.0, looks=(0, 2))
    h.target(w, 6, 0, 12)
    w.set(-4, 1, 9, B.barrel("up"))
    for (x, z, lean) in ((-12, -9, (1, 0)), (-11, 9, (0, -1)), (14, -10, (-1, 0)), (12, 11, (0, -1)), (3, 14, (1, 0)),
                         (-4, 13, (0, -1)), (15, 7, (-1, 0))):
        if (x, z) in cells:
            K.palm(w, x, 0, z, height=6 + int(hash2(x, z, 66) * 3), seed=x + z, lean=lean)
    K.scatter_plants(w, cells, 0, seed=67, kinds=("grass", "fern", "dead_bush"), density=0.18, on=("grass_block",))
    for (x, z) in ((-8, 4), (-8, -4)):
        deco.lamp_post(w, x, 0, z, height=3)
    return h.finish()


DESIGNS = {
    "northern_longhouse": northern_longhouse,
    "mushroom_grove": mushroom_grove,
    "ancient_ruins": ancient_ruins,
    "pirate_ship": pirate_ship,
}
INTERIORS = {
    "northern_longhouse": {"hall": ((0.5, 2.62, 0.5), (12.0, 2.0, 0.0)), "yard": ((-1.0, 3.5, 8.0), (10.0, 1.0, 12.0)),
                           "smithy": ((4.0, 2.62, -6.0), (8.0, 1.0, -11.0))},
    "mushroom_grove": {"hall": ((3.5, 2.62, 0.5), (10.0, 2.0, 0.0)), "loft": ((1.0, 11.62, 2.0), (12.0, 11.0, -1.0))},
    "ancient_ruins": {"cella": ((2.5, 3.62, 0.5), (12.0, 3.0, 0.0)), "stoa": ((-1.0, 2.62, -8.0), (12.0, 2.0, -11.0))},
    "pirate_ship": {"deck": ((13.0, 4.62, 0.5), (0.0, 6.0, 0.0)), "cabin": ((3.5, 3.62, 0.5), (0.0, 3.0, 0.0)),
                    "hold": ((12.0, 0.62, 0.5), (5.0, 0.6, 0.0))},
}
