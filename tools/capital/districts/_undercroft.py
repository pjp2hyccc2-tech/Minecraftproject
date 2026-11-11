"""地下水路 The undercroft: an underground waterway under the market square with a smugglers' dock, a warehouse and
a black market (2026-11-11 rebuild after the owner's pictures: 「地下水路(裏取引・倉庫)」, hidden passages).

Everything lies under the market square, inside the market district (which leaves out the plaza circle), floors at
y -11 (people stand at y -10), ceilings at y -5, the street 5 blocks above. The ways in:

- the canal culvert: the Grand Canal runs on under the north quay at x 40..42 (canal.py) and pours down a shaft
  into the waterway, a 3-wide curtain of falling water from y -2 to the channel (y -11). Swimmers and boats can
  come through and drop down the fall;
- the cellar hatch in the aisle between the stalls (x 30, z 17): a trapdoor in the paving over a ladder down a
  column into the old cistern;
- the old well on the square (x 8, z 27): its dry shaft has a ladder all the way down into the black market;
- from the warehouse, the black market is behind a secret door (eternalrealm:secret_door, looking like the
  bookshelves of the archive wall around it).

The waterway (x 37..45, z 14..38): a 3-wide channel (x 40..42, water y -13..-11) between walkways under a red brick
vault with stone ribs coming down as pilasters, soul lanterns and torches, glow lichen; at its north end the
smugglers' dock (x 36..47, z 7..14: a pool round a jetty, moored boats, a crane, cargo). The old cistern (x 26..35,
z 8..22): marble columns standing in still water lit from below by sea lanterns, a boardwalk from the waterway to
the hatch ladder and on to the warehouse. The warehouse (x 25..35, z 25..37): racks of barrels, crates and sacks
under beams, the archive wall of bookshelves with reading desks. The black market (x 8..23, z 27..37): stalls of a
fence (skulls, gold), a potion seller and an information broker with the smugglers' ledger on a lectern, a weapons
rack, a card table and a bar.
"""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital.noise import hash2, hash3

from . import _civic_kit as K
from . import _town_kit as TK

AIR = "minecraft:air"
F = K.F
FLOOR = -11            # floor blocks; people stand at y -10
CEIL = -5              # ceiling blocks
WTOP = -11             # top of the underground water
BED = -14
CH = (40, 42)          # the channel x range
CULVERT_X = 40         # canal.py carves the culvert at x 40..42 under the north quay (z 39..40)
HATCH = (30, 17)       # the cellar hatch in the market paving
WELL = (8, 27)         # the old well on the square


def _mason(x, y, z, seed=0):
    u = hash3(x, y, z, 7100 + seed)
    if u < 0.22:
        return B.S("mossy_stone_bricks")
    if u < 0.32:
        return B.S("cracked_stone_bricks")
    if u < 0.38:
        return B.S("andesite")
    return B.S("stone_bricks")


def _room(w, x0, z0, x1, z1, floor=None, top=CEIL - 1):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            w.fill(x, FLOOR + 1, z, x, top, z, AIR)
            if floor:
                w.set(x, FLOOR, z, floor(x, z))


def _lichen(w, x, y, z, side):
    if w.get(x, y, z) == AIR:
        w.set(x, y, z, B.S("glow_lichen", **{side: True}))


# ============================================================================================ the waterway
def _waterway(w):
    # the tunnel: walkways either side of the channel under a segmental brick vault with stone ribs
    for z in range(14, 38):
        rib = z % 6 == 2
        vault = B.S("stone_bricks") if rib else B.S("bricks")
        for x in range(38, 45):
            w.fill(x, FLOOR + 1, z, x, -7, z, AIR)
            w.set(x, FLOOR, z, B.S("polished_andesite") if hash2(x, z, 7110) < 0.7 else B.S("stone_bricks"))
        for x in range(39, 44):
            w.set(x, -6, z, AIR)
        for x in range(40, 43):
            w.set(x, -5, z, AIR)
        for x in range(37, 46):
            w.set(x, -4, z, vault)
        w.set(37, -6, z, vault)
        w.set(45, -6, z, vault)
        w.set(38, -5, z, vault)
        w.set(44, -5, z, vault)
        st = "stone_brick" if rib else "brick"
        w.set(38, -6, z, B.S(f"{st}_stairs", facing="west", half="top"))
        w.set(44, -6, z, B.S(f"{st}_stairs", facing="east", half="top"))
        w.set(39, -5, z, B.S(f"{st}_stairs", facing="west", half="top"))
        w.set(43, -5, z, B.S(f"{st}_stairs", facing="east", half="top"))
        for x in (37, 45):
            for y in range(FLOOR, -6):
                w.set(x, y, z, _mason(x, y, z))
        # the channel
        for x in range(CH[0], CH[1] + 1):
            w.set(x, BED, z, B.S("gravel") if hash2(x, z, 7111) < 0.6 else B.S("stone"))
            w.fill(x, BED + 1, z, x, WTOP, z, B.S("water"))
        for x in (CH[0] - 1, CH[1] + 1):
            w.set(x, FLOOR, z, B.S("smooth_castle_stone") if hash2(x, z, 7112) < 0.8 else B.S("stone_bricks"))
            for y in range(BED, FLOOR):
                w.set(x, y, z, _mason(x, y, z, 1))
        if rib:
            for x in (38, 44):                                 # the rib comes down the wall as a pilaster
                w.fill(x, FLOOR + 1, z, x, -7, z, B.S("stone_bricks"))
    # lights: soul lanterns in the crown, torches on the walls, glow lichen
    for z in (17, 23, 29, 35):
        w.set(41, -5, z, B.lantern(hanging=True, soul=True))
    for z in (20, 26, 32):                                     # torches on the rib pilasters
        w.set(39, -8, z, B.S("wall_torch", facing="east"))
        w.set(43, -8, z, B.S("wall_torch", facing="west"))
    for z in range(14, 38):
        for y in (-9, -8, -7):
            if hash3(38, y, z, 7120) < 0.07:
                _lichen(w, 38, y, z, "west")
            if hash3(44, y, z, 7121) < 0.07:
                _lichen(w, 44, y, z, "east")
    # the fall: the canal pours in through the culvert above the south end (z 38)
    for x in range(CH[0], CH[1] + 1):
        w.set(x, BED, 38, B.S("stone"))
        w.fill(x, BED + 1, 38, x, WTOP, 38, B.S("water"))
        w.fill(x, WTOP + 1, 38, x, -2, 38, AIR)
        w.set(x, -1, 38, B.S("stone_bricks"))
    for x in (37, 38, 39, 43, 44, 45):
        for y in range(FLOOR, -1):
            w.set(x, y, 38, _mason(x, y, 38, 2))
    for x in range(CH[0], CH[1] + 1):
        for y in (-4, -3):
            w.set(x, y, 37, _mason(x, y, 37, 3))
    TK.spray(w, 41, FLOOR + 1, 37, power=1, facing="up")                # mist where the fall lands
    deco.sign_text(w, 38, -8, 36, B.wall_sign("spruce", "east"), ["", "地下水路", "Underground", "Waterway"],
                   color="white", glowing=True)


def _dock(w):
    """The smugglers' dock at the north end: a pool round a jetty, walkways, crates, a crane, moored boats."""
    _room(w, 37, 8, 46, 13, floor=lambda x, z: B.S("spruce_planks") if z == 8 or x in (37, 46) else
          B.S("polished_andesite"))
    for x in range(37, 47):
        for z in range(8, 14):
            w.set(x, CEIL, z, B.S("dark_oak_planks"))
    for x in range(36, 48):
        for z in (7, 14):
            if not (z == 14 and 38 <= x <= 44):
                w.fill(x, FLOOR, z, x, CEIL, z, _mason(x, 0, z))
    for z in range(7, 15):
        for x in (36, 47):
            w.fill(x, FLOOR, z, x, CEIL, z, _mason(x, 0, z, 4))
    # the pool and the channel mouth
    for x in range(39, 45):
        for z in range(9, 14):
            w.set(x, BED, z, B.S("gravel"))
            w.fill(x, BED + 1, z, x, WTOP, z, B.S("water"))
    for z in range(9, 14):
        for x in (38, 45):
            for y in range(BED, FLOOR + 1):
                w.set(x, y, z, _mason(x, y, z, 5) if y < FLOOR else B.S("smooth_castle_stone"))
    for x in range(39, 45):
        for y in range(BED, FLOOR + 1):
            w.set(x, y, 8, _mason(x, y, 8, 6) if y < FLOOR else B.S("smooth_castle_stone"))
    # the east walkway of the tunnel joins the dock's east walk
    for x in (45, 46):
        w.fill(x, FLOOR + 1, 14, x, -7, 14, AIR)
        w.set(x, FLOOR, 14, B.S("polished_andesite"))
    # the jetty: planks on fence piles down the middle of the pool
    for z in range(9, 12):
        for x in (41, 42):
            w.set(x, WTOP, z, B.S("spruce_planks"))
            w.fill(x, BED + 1, z, x, WTOP - 1, z, B.fence("spruce"))
    for x in (41, 42):
        w.set(x, FLOOR + 1, 11, B.fence("spruce"))
    w.set(41, FLOOR + 2, 11, B.lantern())
    for (bx, bz, wood) in ((39.9, 10.5, "dark_oak"), (44.1, 11.5, "spruce")):
        w.entity("minecraft:boat", (bx, WTOP + 0.7, bz), 0.0, '{Type:"%s",Invulnerable:1b}' % wood)
    # beams, lanterns, the crane with a hanging cargo barrel
    for z in (9, 12):
        w.fill(37, CEIL - 1, z, 46, CEIL - 1, z, B.log("dark_oak", "x"))
    for (x, z) in ((39, 10), (44, 10), (39, 13), (44, 13)):
        w.set(x, CEIL - 1, z, B.lantern(hanging=True))
    w.set(46, CEIL - 1, 11, B.chain())
    w.set(46, CEIL - 2, 11, B.chain())
    w.set(46, CEIL - 3, 11, B.barrel("up"))
    # cargo on the walks
    pile = [(37, 8), (38, 8), (37, 9), (46, 8), (45, 8), (46, 9), (37, 12), (46, 13)]
    for i, (x, z) in enumerate(pile):
        k = hash2(x, z, 7130)
        w.set(x, FLOOR + 1, z, B.barrel("up") if k < 0.4 else B.S("hay_block", axis="y") if k < 0.6 else
              B.wool("brown") if k < 0.8 else B.chest("south"))
        if k < 0.3:
            w.set(x, FLOOR + 2, z, B.barrel("up"))
    deco.sign_text(w, 40, -8, 8, B.wall_sign("spruce", "south"), ["", "裏の船着場", "Back Dock", ""],
                   color="white", glowing=True)


# ============================================================================================ the cistern
def _cistern(w):
    """Marble columns in still water, lit from below; a boardwalk from the waterway (arch x 36..37, z 16..18) past the
    hatch ladder to the warehouse door (x 29..30, z 23..24)."""
    x0, z0, x1, z1 = 26, 8, 35, 22
    _room(w, x0, z0, x1, z1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            w.set(x, -13, z, B.S("stone_bricks") if hash2(x, z, 7170) < 0.8 else B.S("mossy_stone_bricks"))
            w.fill(x, -12, z, x, FLOOR, z, B.S("water"))
            w.set(x, CEIL, z, B.S("bricks"))
    for (x, z) in ((28, 11), (32, 11), (28, 19), (32, 19), (30, 14), (34, 15)):
        w.set(x, -13, z, B.S("sea_lantern"))
    cols = [(x, z) for x in (27, 30, 33) for z in (10, 13, 16, 21)]
    for (x, z) in cols:
        w.set(x, -12, z, B.S("smooth_quartz"))
        w.fill(x, -11, z, x, CEIL - 2, z, B.S("quartz_pillar", axis="y"))
        w.set(x, CEIL - 1, z, B.S("chiseled_quartz_block"))
    for z in (10, 13, 16, 21):                       # arcades of stone brick over the column rows
        for x in range(x0, x1 + 1):
            if (x, z) not in cols:
                w.set(x, CEIL - 1, z, B.S("stone_bricks"))
    # the boardwalk (top slabs over the water) and its branch to the warehouse door
    walk = [(x, z) for x in range(x0, x1 + 1) for z in (17, 18)] + [(x, z) for x in (29, 30) for z in range(19, 23)]
    for (x, z) in walk:
        w.set(x, FLOOR, z, B.slab("spruce", "top"))
    # the way through to the waterway's west walk and the door to the warehouse
    for x in (36, 37):
        for z in (16, 17, 18):
            w.fill(x, FLOOR + 1, z, x, FLOOR + 3, z, AIR)
            w.set(x, FLOOR, z, B.S("polished_andesite"))
        w.set(x, FLOOR + 4, 16, B.S("stone_brick_stairs", facing="north", half="top"))
        w.set(x, FLOOR + 4, 17, AIR)
        w.set(x, FLOOR + 4, 18, B.S("stone_brick_stairs", facing="south", half="top"))
    for x in (29, 30):
        for z in (23, 24):
            w.fill(x, FLOOR + 1, z, x, FLOOR + 3, z, AIR)
            w.set(x, FLOOR, z, B.S("spruce_planks"))
    # the hatch: a ladder up the column at (30, 16) and a shaft to the trapdoor in the market paving
    hx, hz = HATCH
    for y in range(FLOOR + 1, 0):
        w.set(hx, y, hz, B.S("ladder", facing="south"))
        if y > CEIL:
            for (dx, dz) in ((-1, 0), (1, 0), (0, 1), (0, -1)):
                w.set(hx + dx, y, hz + dz, B.S("stone_bricks"))
    w.set(hx, 0, hz, B.trapdoor("spruce", "north", "top", False))
    # lights: soul lanterns under the arcades, glow lichen on the columns
    for (x, z) in ((28, 12), (32, 12), (28, 20), (32, 20), (31, 15)):
        w.set(x, CEIL - 1, z, B.lantern(hanging=True, soul=True))
    for (x, z) in cols:
        for (dx, side) in ((1, "west"), (-1, "east")):
            if hash2(x + dx, z, 7172) < 0.5:
                _lichen(w, x + dx, -9, z, side)
    deco.sign_text(w, 35, -8, 19, B.wall_sign("spruce", "west"), ["", "古い貯水槽", "Old Cistern", ""],
                   color="white", glowing=True)


# ============================================================================================ the warehouse
def _stack(w, x, z, h, seed):
    for y in range(FLOOR + 1, FLOOR + 1 + h):
        k = hash3(x, y, z, seed)
        w.set(x, y, z, B.barrel("up") if k < 0.35 else B.S("hay_block", axis="y") if k < 0.5 else
              B.wool(("brown", "white", "light_gray")[int(k * 30) % 3]) if k < 0.75 else
              B.chest("north") if y == FLOOR + 1 else B.S("spruce_planks"))


def _warehouse(w):
    x0, z0, x1, z1 = 25, 25, 35, 37
    _room(w, x0, z0, x1, z1, floor=lambda x, z: B.S("spruce_planks") if 29 <= x <= 31 or 29 <= z <= 31 else
          B.S("polished_andesite"))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            w.set(x, CEIL, z, B.S("spruce_planks"))
    for z in (27, 35):
        w.fill(x0, CEIL - 1, z, x1, CEIL - 1, z, B.log("dark_oak", "x"))
    # racks of storage either side of the aisles (x 29..31 north-south, z 29..31 east-west)
    for z in range(z0 + 1, z1 + 1):
        if 29 <= z <= 31:
            continue
        for x in (27, 28, 32, 33):
            if hash2(x, z, 7140) < 0.85:
                _stack(w, x, z, 1 + int(hash2(z, x, 7141) * 3), 7142)
    # the archive wall: bookshelves along the black market side with the secret door in it, reading desks
    for z in range(z0 - 1, z1 + 2):
        for y in range(FLOOR + 1, CEIL):
            w.set(24, y, z, B.S("bookshelf") if y < CEIL - 1 else B.log("dark_oak", "z"))
    for z in (27, 36):
        w.set(25, FLOOR + 1, z, B.S("lectern", facing="east"))
    w.set(24, FLOOR + 1, 33, B.S("eternalrealm:secret_door", facing="east", half="lower", open=False, look="bookshelf"))
    w.set(24, FLOOR + 2, 33, B.S("eternalrealm:secret_door", facing="east", half="upper", open=False, look="bookshelf"))
    # the way through to the waterway's west walk
    for x in (36, 37):
        for z in (29, 30, 31):
            w.fill(x, FLOOR + 1, z, x, FLOOR + 3, z, AIR)
            w.set(x, FLOOR, z, B.S("polished_andesite"))
        w.set(x, FLOOR + 4, 29, B.S("stone_brick_stairs", facing="north", half="top"))
        w.set(x, FLOOR + 4, 30, AIR)
        w.set(x, FLOOR + 4, 31, B.S("stone_brick_stairs", facing="south", half="top"))
    # lanterns, the sign, cobwebs in the corners
    for (x, z) in ((26, 26), (34, 26), (26, 36), (34, 36), (30, 30), (30, 33)):
        w.set(x, CEIL - 1, z, B.lantern(hanging=True))
    deco.sign_text(w, 35, -8, 28, B.wall_sign("spruce", "west"), ["", "第三倉庫", "Warehouse 3", ""],
                   color="black", glowing=False)
    for (x, z) in ((25, 25), (35, 25), (25, 37), (35, 37)):
        w.set(x, CEIL - 1, z, B.S("cobweb"))


# ============================================================================================ the black market
LEDGER = [
    "密輸台帳\n\n第三倉庫の\n書架の奥は\n立入禁止。\n\n合言葉は\n『星は地下にも灯る』",
    "入荷\n・絹布 四反\n・南方の香辛料 二樽\n・古地図 一枚\n  (王都の地下に\n   まだ道がある)",
    "出荷\n・青い薬瓶 十二\n・竜の鱗? 一\n  → 偽物だった\n\n水門番には\n口止め料を忘れずに。",
]


def _stall(w, x0, z, x1, face, awning, goods):
    """A little stall: a dark oak counter x0..x1 at row z facing ``face`` (north/south) with goods on it, posts and
    a cloth awning over it and the keeper's row behind."""
    back = 1 if face == "north" else -1
    for x in range(x0, x1 + 1):
        w.set(x, FLOOR + 1, z, B.S("dark_oak_planks") if x not in (x0, x1) else B.log("dark_oak", "y"))
        w.set(x, FLOOR + 4, z, B.wool(awning[(x - x0) % len(awning)]))
        w.set(x, FLOOR + 4, z + back, B.wool(awning[(x - x0) % len(awning)]))
    for x in (x0, x1):
        w.fill(x, FLOOR + 2, z, x, FLOOR + 3, z, B.fence("dark_oak"))
    for i, x in enumerate(range(x0 + 1, x1)):
        g = goods[i % len(goods)]
        if g:
            w.set(x, FLOOR + 2, z, g)


def _black_market(w):
    x0, z0, x1, z1 = 8, 27, 23, 37
    _room(w, x0, z0, x1, z1, floor=lambda x, z: B.S("deepslate_tiles") if (x + z) % 5 else
          B.S("polished_blackstone"))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            w.set(x, CEIL, z, B.S("dark_oak_planks"))
    for z in (29, 35):
        w.fill(x0, CEIL - 1, z, x1, CEIL - 1, z, B.log("dark_oak", "x"))
    for (x, z) in ((11, 31), (20, 31), (11, 34), (20, 34)):
        w.fill(x, FLOOR + 1, z, x, CEIL - 1, z, B.S("polished_blackstone_bricks"))
        w.set(x, FLOOR + 1, z, B.S("chiseled_polished_blackstone"))
    # a runner from the secret door
    for x in range(12, x1 + 1):
        w.set(x, FLOOR + 1, 33, B.carpet("red"))
    # south stalls: the fence (skulls and gold) and the potion seller; their wares hang on the wall behind
    _stall(w, 9, 36, 13, "north", ("black", "purple"), [B.S("skeleton_skull", rotation=8),
                                                       B.S("wither_skeleton_skull", rotation=8),
                                                       B.candle(3, "black")])
    for (x, item) in ((10, "minecraft:golden_apple"), (11, "minecraft:name_tag"), (12, "minecraft:golden_helmet")):
        deco.item_frame(w, x, FLOOR + 2, 37, "north", item)
    _stall(w, 15, 36, 19, "north", ("purple", "magenta"),
           [B.S("brewing_stand", has_bottle_0=True, has_bottle_1=True, has_bottle_2=True), B.candle(2, "purple"),
            B.S("water_cauldron", level=3)])
    for (x, item) in ((16, "minecraft:splash_potion"), (17, "minecraft:lingering_potion"),
                      (18, "minecraft:dragon_breath")):
        deco.item_frame(w, x, FLOOR + 2, 37, "north", item)
    # north stall: the information broker with the smugglers' ledger
    _stall(w, 13, 28, 17, "south", ("gray", "black"), [B.S("cartography_table"), None, B.candle(1, "white")])
    w.set(15, FLOOR + 2, 28, B.lectern("south", has_book=True))
    w.be(15, FLOOR + 2, 28, B.lectern_nbt("密輸台帳", "名無し", LEDGER))
    for (x, item) in ((14, "minecraft:heart_of_the_sea"), (16, "minecraft:nautilus_shell")):
        deco.item_frame(w, x, FLOOR + 3, 27, "south", item)
    # the weapons rack by the door
    deco.armor_stand(w, 23, FLOOR, 29, yaw=90.0, armor="chainmail", weapon="minecraft:crossbow")
    deco.armor_stand(w, 23, FLOOR, 36, yaw=90.0, armor="iron", weapon="minecraft:trident")
    # the card table
    w.fill(15, FLOOR + 1, 30, 16, FLOOR + 1, 31, B.wool("green"))
    deco.item_frame(w, 15, FLOOR + 2, 30, "up", "minecraft:paper", invisible=True)
    deco.item_frame(w, 16, FLOOR + 2, 31, "up", "minecraft:emerald", invisible=True)
    w.set(15, FLOOR + 2, 31, B.candle(2, "white"))
    for z in (30, 31):
        w.set(14, FLOOR + 1, z, B.S("dark_oak_stairs", facing="west"))
        w.set(17, FLOOR + 1, z, B.S("dark_oak_stairs", facing="east"))
    # the bar along the west wall
    for z in range(30, 36):
        w.set(x0, FLOOR + 1, z, B.barrel("east"))
        w.set(x0, FLOOR + 2, z, B.barrel("east") if z % 2 else B.S("spruce_planks"))
    for z in range(30, 35):
        w.set(x0 + 2, FLOOR + 1, z, B.S("spruce_planks"))
    w.set(x0 + 2, FLOOR + 2, 31, B.candle(3, "orange"))
    w.set(x0 + 2, FLOOR + 2, 33, B.S("decorated_pot", facing="east"))
    # lights: soul lanterns and lanterns; cobwebs; black banners and the sign by the door
    for (x, z, soul) in ((13, 30, True), (19, 30, True), (13, 36, True), (19, 36, True), (16, 33, False),
                         (9, 33, False)):
        w.set(x, CEIL - 1, z, B.lantern(hanging=True, soul=soul))
    for (x, z) in ((x0, z1), (x1, z0), (x1, z1)):
        w.set(x, CEIL - 1, z, B.S("cobweb"))
    for z in (31, 35):
        deco.kingdom_wall_banner(w, x1, -8, z, facing="west", color="black", nbt="{}")
    deco.sign_text(w, x1, -8, 32, B.wall_sign("spruce", "west"), ["", "闇市", "合言葉を", "忘れるな"],
                   color="red", glowing=True)


def _well(w):
    """The old well on the square: a dry shaft with a ladder down into the black market."""
    x, z = WELL
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx or dz:
                w.set(x + dx, 1, z + dz, B.wall("cobblestone") if (dx and dz) else B.slab("stone_bricks", "bottom"))
    for (dx, dz) in ((-1, -1), (1, 1)):
        w.fill(x + dx, 2, z + dz, x + dx, 3, z + dz, B.fence("spruce"))
    w.set(x - 1, 4, z - 1, B.stairs("spruce", "south"))
    w.set(x + 1, 4, z + 1, B.stairs("spruce", "north"))
    w.set(x, 4, z, B.slab("spruce", "bottom"))
    w.set(x - 1, 4, z + 1, B.slab("spruce", "bottom"))
    w.set(x + 1, 4, z - 1, B.slab("spruce", "bottom"))
    w.set(x + 1, 2, z - 1, B.lantern())
    w.set(x - 1, 2, z + 1, B.lantern())
    for y in range(FLOOR + 1, 1):
        w.set(x, y, z, B.S("ladder", facing="east"))
        if y > CEIL:
            w.set(x - 1, y, z, B.S("cobblestone"))
            for (dx, dz) in ((1, 0), (0, -1), (0, 1)):
                w.set(x + dx, y, z + dz, B.S("mossy_cobblestone") if hash3(x + dx, y, z + dz, 7160) < 0.4
                      else B.S("cobblestone"))


def build(w):
    """Carve and furnish the undercroft (called from market.py; the canal culvert is canal.py's)."""
    for x in range(7, 48):
        for z in range(7, 39):
            for y in range(BED - 1, CEIL + 2):
                w.set(x, y, z, _mason(x, y, z, 9))
    _waterway(w)
    _dock(w)
    _cistern(w)
    _warehouse(w)
    _black_market(w)
    _well(w)
    TK.crowd(w, "undercroft", [(16, FLOOR + 1, 33), (11, FLOOR + 1, 29), (30, FLOOR + 1, 32),
                               (42, FLOOR + 1, 8)], need=12)
