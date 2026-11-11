"""商店回廊 The Galleria: an indoor shopping street under a glass vault, east of the market square (2026-11-11
rebuild after the owner's pictures, 「内部商店街(区画制)」). It replaces the old covered market hall.

Local frame (rotation 0, origin = the north-west corner): x 0..24 across, z 0..25 along (towards the canal).

- A 9-wide flagstone passage (x 8..16) between two rows of five partitioned shops (x 1..6 west, x 18..23 east,
  each 4 long between the partitions on z = 0, 5, 10, 15, 20, 25). Every shop has its trade: wares in item frames on
  the back shelves and lying on the counter, small goods on the shelf top, the big pieces of the trade by the
  partition (an armour stand, a brewing stand and cauldron, an oven, a loom...), a hanging shop sign with the
  picture of the trade under the beam, a striped awning with a banner valance, a rug and a lantern. Above each
  shop a lit storeroom with windows onto the passage (window-box flowers on the awning) and onto the outside.
- Castle pillars on the partition lines carry the passage walls up to a moulded cornice (y 12) hung with
  kingdom banners; dark oak ribs carry a leaded-glass barrel vault (crown y 17, ribs standing proud outside,
  a gilded ridge) with a chandelier in every bay.
- A sunken pool with a fountain in the middle bay, back-to-back benches in the bays either side, flush flower
  beds in the end bays, potted ferns at the pillar feet, a clock over each arch inside.
- Cream sandstone walls with white castle-stone trims on a grey plinth. End façades: a great round arch
  (x 9..15) with white voussoirs and a keystone between pilasters standing proud, a glazed lunette in the vault
  end with a mullion and the kingdom crest at the apex, the name board, banners, sconces, display windows of the
  end shops, a projecting cornice and a balustrade hiding the terracotta lean-to roofs of the shop rows.
"""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital.noise import hash2
from capital.world import Frame

from . import _civic_kit as K
from . import _furnish as FU
from . import _gimmick_kit as GK
from . import _maze as MZ
from . import _town_kit as TK

AIR = "minecraft:air"
F = K.F
SM, CB = K.SM, K.CB
WALL = "smooth_sandstone"     # warm cream walls (white castle-stone trims, a grey plinth)
PLINTH = "polished_andesite"
GLASS = B.S("leaded_glass")
W, D = 25, 26
SHOP_TOP = 5            # shop ceiling = storeroom floor
STORE_TOP = 9           # storeroom ceiling = top of the outer walls
WALL_TOP = 12           # top of the passage walls (the cornice)
VAULT = {7: 13, 8: 14, 9: 15, 10: 16, 11: 17, 12: 17, 13: 17, 14: 16, 15: 15, 16: 14, 17: 13}
UNITS = (1, 6, 11, 16, 21)
PARTITIONS = (0, 5, 10, 15, 20, 25)
TRADES = (("sword", "potion"), ("bread", "gem"), ("shirt", "book"), ("flower", "map"), ("hammer", "mug"))
# lean-to roofs over the shop rows: (depth as in _x, y, full block?) from the eave (-2) over the outer wall (-1)
# rising towards the passage wall
ROOF_STEPS = ((-2, 9, False), (-1, 9, True), (0, 10, False), (1, 10, True), (2, 11, False), (3, 11, True),
              (4, 12, False), (5, 12, True))


def _potion(name):
    return B.item_snbt("minecraft:potion", 1, f'{{Potion:"minecraft:{name}"}}')


# wares of every trade: four for the back shelf, two lying on the counter
WARES = {
    "sword": ("iron_sword", "golden_sword", "diamond_sword", "bow", "iron_axe", "crossbow"),
    "potion": (_potion("healing"), _potion("swiftness"), _potion("night_vision"), _potion("fire_resistance"),
               "glass_bottle", "glowstone_dust"),
    "bread": ("bread", "cookie", "pumpkin_pie", "cake", "wheat", "honey_bottle"),
    "gem": ("emerald", "diamond", "amethyst_shard", "gold_ingot", "lapis_lazuli", "quartz"),
    "shirt": ("leather_chestplate", "leather_helmet", "leather_boots", "leather_leggings", "red_wool", "string"),
    "book": ("book", "writable_book", "enchanted_book", "paper", "feather", "ink_sac"),
    "flower": ("poppy", "blue_orchid", "allium", "sunflower", "cornflower", "peony"),
    "map": ("map", "compass", "clock", "spyglass", "recovery_compass", "paper"),
    "hammer": ("iron_pickaxe", "iron_shovel", "iron_hoe", "shears", "fishing_rod", "flint_and_steel"),
    "mug": ("honey_bottle", "milk_bucket", "sweet_berries", "apple", "glow_berries", "mushroom_stew"),
}


def _item(name):
    return name if name.startswith("{") else f"minecraft:{name}"


def _shelf_top(trade, face):
    """Two small goods for the top of the back shelf."""
    return {
        "sword": (B.S("decorated_pot", facing=face), B.lantern()),
        "potion": (B.S("brewing_stand", has_bottle_0=True, has_bottle_1=True, has_bottle_2=True),
                   B.candle(3, "purple")),
        "bread": (B.S("cake"), B.S("decorated_pot", facing=face)),
        "gem": (B.S("amethyst_cluster", facing="up"), B.candle(2, "yellow")),
        "shirt": (B.wool("light_blue"), B.wool("yellow")),
        "book": (B.candle(1, "white"), B.S("decorated_pot", facing=face)),
        "flower": (B.potted("red_tulip"), B.potted("blue_orchid")),
        "map": (B.lantern(), B.potted("fern")),
        "hammer": (B.lantern(), B.S("decorated_pot", facing=face)),
        "mug": (B.barrel("up"), B.candle(2, "orange")),
    }[trade]


def _x(side, d):
    """World-frame x of the depth d in a shop row: d 0 = the back shelf, 5 = the counter, 6 = the pillar line,
    7 = the awning, 8 = the valance. ``side`` +1 = west row, -1 = east row."""
    return 1 + d if side > 0 else W - 2 - d


# ============================================================================================ the shops
def _pieces(f, side, z0, trade, face):
    """The big pieces of the trade on the row by the partition (z0), depth 2 and 3."""
    x2, x3 = _x(side, 2), _x(side, 3)
    yaw = -90.0 if side > 0 else 90.0
    if trade == "sword":
        deco.armor_stand(f, x2, 0, z0, yaw=yaw, armor="iron", weapon="minecraft:iron_sword",
                         offhand=deco.blue_shield())
        f.set(x3, 1, z0, B.S("grindstone", face="floor", facing="north"))
    elif trade == "potion":
        f.set(x2, 1, z0, B.S("water_cauldron", level=3))
        f.set(x3, 1, z0, B.S("brewing_stand", has_bottle_0=True, has_bottle_1=False, has_bottle_2=True))
    elif trade == "bread":
        f.set(x2, 1, z0, B.S("smoker", facing=face, lit=True))
        f.set(x3, 1, z0, B.S("hay_block", axis="y"))
        f.set(x3, 2, z0, B.S("cake"))
    elif trade == "gem":
        f.set(x2, 1, z0, B.full("polished_blackstone"))
        f.set(x2, 2, z0, B.S("amethyst_cluster", facing="up"))
        f.set(x3, 1, z0, B.S("gold_block"))
    elif trade == "shirt":
        deco.armor_stand(f, x2, 0, z0, yaw=yaw, armor="leather", weapon=None, color=0x3B5BA5)
        f.set(x3, 1, z0, B.S("loom", facing=face))
    elif trade == "book":
        f.set(x2, 1, z0, B.lectern(face, has_book=True))
        f.set(x3, 1, z0, B.S("bookshelf"))
        f.set(x3, 2, z0, B.S("chiseled_bookshelf", facing=face, slot_0_occupied=True, slot_2_occupied=True,
                             slot_3_occupied=True, slot_4_occupied=True))
    elif trade == "flower":
        f.set(x2, 1, z0, B.S("flowering_azalea"))
        f.set(x3, 1, z0, B.potted("cornflower"))
    elif trade == "map":
        f.set(x2, 1, z0, B.S("cartography_table"))
        f.set(x2, 2, z0, B.lantern())
        f.set(x3, 1, z0, B.barrel("up"))
        deco.item_frame(f, x3, 2, z0, "up", "minecraft:map", invisible=True)
    elif trade == "hammer":
        f.set(x2, 1, z0, B.S("blast_furnace", facing=face, lit=True))
        f.set(x3, 1, z0, B.S("anvil", facing="north"))
    elif trade == "mug":
        f.set(x2, 1, z0, B.barrel(face))
        f.set(x2, 2, z0, B.barrel(face))
        f.set(x3, 1, z0, B.barrel("up"))
        f.set(x3, 2, z0, B.candle(1, "white"))


def _unit(f, side, z0, trade, i):
    """One shop of a row (z0..z0+3) with its storeroom above."""
    X = lambda d: _x(side, d)  # noqa: E731
    face = "east" if side > 0 else "west"
    wares = WARES[trade]
    seed = 9100 + 13 * i + (side > 0)
    # the rooms: shop y 1..4 under a dark oak ceiling, storeroom y 6..8 under a spruce ceiling
    for d in range(0, 6):
        x = X(d)
        for z in range(z0, z0 + 4):
            f.set(x, 0, z, F("spruce_planks"))
            f.fill(x, 1, z, x, SHOP_TOP - 1, z, AIR)
            f.set(x, SHOP_TOP, z, F("dark_oak_planks"))
            f.fill(x, SHOP_TOP + 1, z, x, STORE_TOP - 1, z, AIR)
            f.set(x, STORE_TOP, z, F("spruce_planks"))
    # back shelf: chests and barrels, a plank board with the wares in frames, a slab shelf with goods on top
    tops = _shelf_top(trade, face)
    for k, z in enumerate(range(z0, z0 + 4)):
        f.set(X(0), 1, z, B.barrel(face) if k % 2 else B.chest(face))
        f.set(X(0), 2, z, F("spruce_planks"))
        f.set(X(0), 3, z, B.slab("spruce", "top"))
        if k in (0, 2):
            f.set(X(0), 4, z, tops[k // 2])
        deco.item_frame(f, X(1), 2, z, face, _item(wares[k]))
    _pieces(f, side, z0, trade, face)
    # the counter along the front with a gap for the shopkeeper; two wares lying on it and a candle
    for z in range(z0, z0 + 3):
        f.set(X(5), 1, z, B.log("spruce", "z", stripped=True))
    deco.item_frame(f, X(5), 2, z0, "up", _item(wares[4]), invisible=True)
    f.set(X(5), 2, z0 + 1, B.candle(2, ("white", "yellow", "orange", "red")[int(hash2(z0, side, seed) * 4)]))
    deco.item_frame(f, X(5), 2, z0 + 2, "up", _item(wares[5]), invisible=True)
    colors = TK.AWNINGS[int(hash2(z0, side, seed + 1) * len(TK.AWNINGS))]
    FU.rug(f, X(1), z0, X(4), z0 + 3, 0, field=colors[0], border="yellow" if colors[0] != "yellow" else "white")
    f.set(X(3), SHOP_TOP - 1, z0 + 2, B.lantern(hanging=True))
    # the front: open under a dark oak beam with the shop sign and a lantern hanging from it
    for z in range(z0, z0 + 4):
        f.fill(X(6), 1, z, X(6), SHOP_TOP - 1, z, AIR)
        f.set(X(6), 0, z, F("plaza_flagstones"))
        f.set(X(6), SHOP_TOP, z, B.log("dark_oak", "z"))
        f.fill(X(6), SHOP_TOP + 1, z, X(6), WALL_TOP - 1, z, F(WALL))
        f.set(X(6), WALL_TOP, z, F(SM))
    f.fill(X(6), STORE_TOP, z0, X(6), STORE_TOP, z0 + 3, F(SM))
    MZ.shop_sign(f, X(6), SHOP_TOP - 1, z0 + 1, face, trade, attach="ceiling")
    f.set(X(6), SHOP_TOP - 1, z0 + 3, B.lantern(hanging=True))
    # striped awning with its valance; window-box flowers on it under the storeroom window
    K.awning(K.Face(f, "z", X(6), side), z0, z0 + 3, SHOP_TOP, depth=1, colors=colors)
    f.fill(X(6), SHOP_TOP + 1, z0 + 1, X(6), SHOP_TOP + 2, z0 + 2, TK.LEADED)
    for z in (z0 + 1, z0 + 2):
        f.set(X(7), SHOP_TOP + 1, z, B.potted(("red_tulip", "allium", "azure_bluet", "oxeye_daisy", "blue_orchid",
                                               "pink_tulip")[int(hash2(z, side, seed + 2) * 6)]))
    # banner on the upper wall over the shop
    deco.kingdom_wall_banner(f, X(7), WALL_TOP - 1, z0 + 2, facing=face)
    # storeroom: sacks and crates, a lantern, a window to the outside
    f.set(X(0), SHOP_TOP + 1, z0, B.barrel("up"))
    f.set(X(0), SHOP_TOP + 2, z0, B.barrel("up"))
    f.set(X(0), SHOP_TOP + 1, z0 + 3, B.S("hay_block", axis="y"))
    f.set(X(1), SHOP_TOP + 1, z0 + 3, B.wool("white"))
    f.set(X(0), SHOP_TOP + 1, z0 + 1, B.chest(face))
    f.set(X(3), STORE_TOP - 1, z0 + 1, B.lantern(hanging=True))
    f.fill(X(-1), SHOP_TOP + 1, z0 + 1, X(-1), SHOP_TOP + 2, z0 + 2, TK.LEADED)
    for z in (z0 + 1, z0 + 2):
        f.set(X(-2), SHOP_TOP, z, B.stairs(SM, face, "top"))                # the sill outside


def _partitions(f):
    for z in PARTITIONS:
        for side in (1, -1):
            for d in range(0, 6):
                x = _x(side, d)
                f.fill(x, 1, z, x, SHOP_TOP - 1, z, F("white_plaster"))
                f.fill(x, SHOP_TOP, z, x, STORE_TOP, z, F(CB))
            x = _x(side, 6)
            f.fill(x, 0, z, x, WALL_TOP - 1, z, B.pillar("y"))
            f.set(x, WALL_TOP, z, F("carved_castle_stone"))


# ============================================================================================ the passage
def _passage(f):
    for x in range(8, 17):
        for z in range(0, D):
            u = hash2(x, z, 8801)
            f.set(x, 0, z, F("plaza_flagstones") if u < 0.72 else F(SM) if u < 0.9 else B.S("polished_diorite"))
    for z in range(0, D):
        f.set(12, 0, z, B.S("polished_diorite") if z % 2 else F(SM))
    # the sunken pool and fountain in the middle bay
    for x in range(11, 14):
        for z in range(11, 14):
            f.set(x, -1, z, F(SM))
            f.set(x, 0, z, B.S("water"))
    f.set(12, 0, 12, F(SM))
    f.set(12, 1, 12, F("carved_castle_stone"))
    f.set(12, 2, 12, B.S("water_cauldron", level=3))
    TK.spray(f, 12, 3, 12, power=2, facing="up")
    GK.canal_lanterns(f, 11, 11, 13, 13, 0, every=1.5, seed=6)          # a candle lantern or two on the pool
    for x in range(10, 15):
        for z in range(10, 15):
            if x in (10, 14) or z in (10, 14):
                corner = x in (10, 14) and z in (10, 14)
                f.set(x, 1, z, B.potted("flowering_azalea_bush") if corner else B.slab(SM, "bottom"))
    # back-to-back benches in the bays either side, lamps in the end bays, ferns at the pillar feet
    for zn in (7, 17):
        deco.bench(f, 11, 0, zn, facing="north", length=3)
        deco.bench(f, 13, 0, zn + 1, facing="south", length=3)
    for zb in (2, 22):                                   # flush flower beds in the end bays
        for x in range(11, 14):
            for z in (zb, zb + 1):
                f.set(x, 0, z, B.S("grass_block"))
                f.set(x, 1, z, B.S(("azure_bluet", "allium", "red_tulip", "oxeye_daisy", "cornflower",
                                    "pink_tulip")[int(hash2(x, z, 8805) * 6)]))
    for z in PARTITIONS[1:-1]:
        for x in (8, 16):
            f.set(x, 1, z, B.potted(("fern", "azalea_bush")[int(hash2(x, z, 8803) * 2)]))


def _cornice(f):
    for z in range(0, D):
        f.set(8, WALL_TOP, z, B.stairs(SM, "west", "top"))
        f.set(16, WALL_TOP, z, B.stairs(SM, "east", "top"))


def _vault(f):
    """Leaded glass on dark oak ribs (on the partition lines), the ribs smoothed with upside-down stairs and
    standing proud outside; a gilded ridge; a chandelier in every bay."""
    for z in range(1, D - 1):
        rib = z % 5 == 0
        for x, y in VAULT.items():
            if x in (7, 17):
                f.set(x, y, z, F(SM))
                continue
            f.set(x, y, z, F("dark_oak_planks") if rib else GLASS)
            if rib:
                f.set(x, y + 1, z, B.slab("dark_oak", "bottom"))
        if rib:
            for x in (8, 9, 10, 11):
                f.set(x, VAULT[x] - 1, z, B.stairs("dark_oak", "west", "top"))
            for x in (13, 14, 15, 16):
                f.set(x, VAULT[x] - 1, z, B.stairs("dark_oak", "east", "top"))
        f.set(12, VAULT[12] + 1, z, B.slab("gilded_trim", "bottom"))
    for z in (2, 7, 12, 17, 22):
        deco.chandelier(f, 12, VAULT[12], z, drop=4)
    for i, z in enumerate((2, 7, 17, 22)):                # pennant strings across the passage under the cornice
        MZ.bunting(f, 8, STORE_TOP, z, (8, 0, 0), style="pennant", colors=MZ.PENNANTS[i % len(MZ.PENNANTS)],
                   sag=0.8, wind=0.0, facing="east")


def _roofs(f):
    for side in (1, -1):
        for (d, y, full) in ROOF_STEPS:
            x = _x(side, d)
            zs = range(0, D) if d < -1 else range(1, D - 1)
            for z in zs:
                f.set(x, y, z, F("terracotta_roof_tiles") if full else
                      B.stairs("terracotta_roof_tiles", "east" if side > 0 else "west"))
                if d >= 0:
                    f.fill(x, STORE_TOP + 1, z, x, y - 1, z, F("dark_oak_planks"))     # closed attic


def _outer_walls(f):
    """The long outer walls: grey plinth, cream wall, a white band at the storeroom floor, white pilasters on the
    partition lines and sconces under the storeroom window sills."""
    for side in (1, -1):
        x = _x(side, -1)
        xo = _x(side, -2)
        for z in range(0, D):
            f.set(x, 0, z, F("stone_bricks"))
            for y in range(1, STORE_TOP):
                f.set(x, y, z, F(PLINTH) if y == 1 else F(SM) if y == SHOP_TOP else F(WALL))
            if z % 5 == 0:
                f.fill(xo, 1, z, xo, STORE_TOP - 1, z, B.pillar("y"))
            elif z % 5 == 2:
                deco.sconce(f, xo, 4, z, "west" if side > 0 else "east")


# ============================================================================================ the ends
def _end(f, z, out):
    """End façade on the line z; ``out`` = -1 (north end, outside towards -z) or +1 (south end)."""
    face = "north" if out < 0 else "south"
    inner = "south" if out < 0 else "north"
    zo = z + out
    # the wall: grey plinth, cream wall, white bands and quoins, a projecting cornice, a balustrade over the
    # side bays with gilded finials on the corners
    for x in range(0, W):
        f.set(x, 0, z, F("stone_bricks"))
        for y in range(1, WALL_TOP + 1):
            quoin = x in (0, W - 1) and y % 2 == 0
            f.set(x, y, z, F(PLINTH) if y == 1 else F(SM) if (y in (SHOP_TOP, WALL_TOP) or quoin) else F(WALL))
        f.set(x, WALL_TOP, zo, B.stairs(SM, inner, "top"))
        if x < 7 or x > 17:
            f.set(x, WALL_TOP + 1, z, B.wall(CB) if x % 2 == 0 else B.slab(SM, "bottom"))
    for x in (0, W - 1):
        f.set(x, WALL_TOP + 2, z, B.S("gilded_finial"))
    # the vault end: a white outline round a glazed lunette with a dark oak mullion, the crest at the apex
    for x, top in VAULT.items():
        for y in range(WALL_TOP + 1, top + 1):
            if y == top or x in (7, 8, 16, 17):
                f.set(x, y, z, F(SM))
            elif x == 12:
                f.set(x, y, z, B.log("dark_oak"))
            else:
                f.set(x, y, z, GLASS)
    f.set(12, VAULT[12] + 1, z, F("castle_crest_stone"))
    f.set(11, VAULT[11] + 1, z, B.slab("gilded_trim", "bottom"))
    f.set(13, VAULT[13] + 1, z, B.slab("gilded_trim", "bottom"))
    # the great arch (x 9..15, springing at y 6, crown at y 9): white voussoirs, a carved keystone
    f.fill(9, 1, z, 15, 6, z, AIR)
    f.fill(10, 7, z, 14, 7, z, AIR)
    f.fill(11, 8, z, 13, 8, z, AIR)
    f.set(12, 9, z, AIR)
    for (xa, xb, y) in ((9, 15, 7), (10, 14, 8), (11, 13, 9)):
        f.set(xa, y, z, B.stairs(SM, "west", "top"))
        f.set(xb, y, z, B.stairs(SM, "east", "top"))
    for (x, y) in ((8, 7), (16, 7), (9, 8), (15, 8), (10, 9), (14, 9), (11, 10), (13, 10)):
        f.set(x, y, z, F(SM))
    f.set(12, 10, z, F("carved_castle_stone"))
    for y in range(1, 7):
        f.set(8, y, z, F(SM))
        f.set(16, y, z, F(SM))
    for x in range(9, 16):
        f.set(x, 0, z, F("plaza_flagstones"))
    # white pilasters standing proud either side of the arch, on pedestals, with carved capitals
    for x in (7, 17):
        f.set(x, 1, zo, F(PLINTH))
        f.fill(x, 2, zo, x, WALL_TOP - 2, zo, B.pillar("y"))
        f.set(x, WALL_TOP - 1, zo, F("carved_castle_stone"))
    # display windows of the end shops and arched windows of their storerooms
    for x0 in (2, W - 6):
        f.fill(x0, 2, z, x0 + 3, 4, z, TK.LEADED)
        f.fill(x0, 1, zo, x0 + 3, 1, zo, B.stairs(SM, inner, "top"))
        f.fill(x0 + 1, SHOP_TOP + 2, z, x0 + 2, SHOP_TOP + 3, z, TK.LEADED)
        f.fill(x0 + 1, SHOP_TOP + 1, zo, x0 + 2, SHOP_TOP + 1, zo, B.stairs(SM, inner, "top"))
        f.set(x0 + 1, SHOP_TOP + 4, zo, B.stairs(SM, "west", "top"))
        f.set(x0 + 2, SHOP_TOP + 4, zo, B.stairs(SM, "east", "top"))
    # outside: the name board, banners, sconces, potted azaleas at the foot of the arch
    deco.sign_text(f, 12, 11, zo, B.wall_sign("dark_oak", face), ["", "商店回廊", "The Galleria", ""],
                   color="yellow", glowing=True)
    for x in (4, W - 5):
        deco.kingdom_wall_banner(f, x, WALL_TOP - 1, zo, facing=face)
    for x in (6, 18):
        deco.sconce(f, x, 6, zo, face)
    for x in (8, 16):
        f.set(x, 1, zo, B.potted("flowering_azalea_bush"))
    # inside: a clock over the arch
    TK.clock(f, 12, WALL_TOP, z, inner, size=2.0)


# ============================================================================================ build
def galleria(w, x0, z0):
    """The Galleria with its west wall on x = x0 and north façade on z = z0 (needs x0 - 1 .. x0 + 25 and
    z0 - 1 .. z0 + 26 inside the district)."""
    f = Frame(w, (x0, 0, z0), 0)
    f.fill(-1, 1, -1, W, VAULT[12] + 3, D, AIR)
    f.fill(0, 0, 0, W - 1, 0, D - 1, B.S("stone_bricks"))
    _outer_walls(f)
    _partitions(f)
    _passage(f)
    for i, z0u in enumerate(UNITS):
        west, east = TRADES[i]
        _unit(f, 1, z0u, west, i)
        _unit(f, -1, z0u, east, i)
    _cornice(f)
    _vault(f)
    _roofs(f)
    _end(f, 0, -1)
    _end(f, D - 1, 1)
