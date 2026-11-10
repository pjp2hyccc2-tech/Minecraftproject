"""商店街 Shops south-west of the plaza.

- 郵便局 Post office (x -50..-28, z 7..21, front east towards the plaza): counter with letter racks and parcels
  (Chico, ``npc.mailbox``), a red mailbox outside, and the pigeon loft on the roof ridge.
- 道具屋 Item shop (x -26..-8, z 25..39, front north on the plaza ring): shelves of potions, food and tools,
  Pim's counter (``npc.item_shop``).
- 鍛冶屋 Blacksmith (x -50..-29, z 25..39, front north on the shop lane): stone forge with a big hearth, anvils,
  quenching trough, grindstone, weapon racks, an open smithy yard; heavy smoke from the forge chimney
  (Goran, ``npc.blacksmith``).
"""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2, hash3

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "shops"
AIR = K.AIR
F = K.F
CB, SM, GOLD, ROOF = K.CB, K.SM, K.GOLD, K.ROOF

VIEWS = {
    "shops_front": dict(pos=(-15, 4.5, 3), look=(-36, 8, 27), fov=72, sun=(40, -160), fog=2600,
                        label="Shops south-west of the plaza: post office, item shop, smithy"),
    "smithy": dict(pos=(-34.5, 4.0, 27.5), look=(-44, 2, 36), fov=80, sun=(40, -160), fog=900,
                   label="Goran's forge"),
    "item_shop": dict(pos=(-19.5, 3.6, 26.5), look=(-19, 2.5, 37), fov=80, sun=(40, -160), fog=900,
                      label="Pim's item shop"),
    "post_office": dict(pos=(-29.5, 3.6, 17.5), look=(-48, 2.5, 12), fov=80, sun=(40, -160), fog=900,
                        label="Post office"),
}

POST_BANNER = B.banner_nbt([("bs", "white"), ("ts", "white"), ("mc", "white"), ("bo", "yellow")])
SMITH_BANNER = B.banner_nbt([("cr", "light_gray"), ("bo", "light_gray"), ("mc", "orange")])
SHOP_BANNER = B.banner_nbt([("mc", "red"), ("bo", "yellow"), ("cbo", "lime")])


# ============================================================================== post office
def post_office(w):
    rect = (-50, 7, -28, 21)
    f, W, D = TK.frame_for(w, rect, "east")
    sp = TK.HallSpec(storeys=2, sh=5, upper="timber", roof="royal_roof_tiles", roof_kind="gable_x", portal_w=2,
                     portal_h=4, banner_nbt=POST_BANNER, banner_color="red",
                     sign=(["", "郵便局", "Post Office", ""], "yellow"), window_every=3, dormers=False)
    a = TK.civic_hall(f, W, D, sp)
    ridge = a["ridge"]
    # counter across the hall with Chico behind it; pigeonholes (chiseled bookshelves) on the back wall
    w.fill(-49, 0, 8, -29, 0, 20, F("spruce_planks"))
    for z in range(9, 20):
        w.set(-36, 1, z, F("spruce_planks") if z in (9, 19) else B.S("barrel", facing="east"))
        w.set(-36, 2, z, B.slab("spruce", "top"))
    w.set(-36, 1, 14, B.trapdoor("spruce", "west", "bottom", True))
    w.set(-36, 2, 14, AIR)
    w.spot("npc.mailbox", (-37, 1, 15), -90.0)
    for z in range(9, 20):
        for y in range(1, 5):
            occ = {f"slot_{i}_occupied": hash3(z, y, i, 51) < 0.6 for i in range(6)}
            w.set(-49, y, z, B.S("chiseled_bookshelf", facing="east", **occ))
    # parcels: barrels, chests, stacked crates (wood blocks)
    for (x, z) in ((-46, 9), (-45, 9), (-46, 10), (-46, 19), (-45, 19), (-44, 19)):
        w.set(x, 1, z, B.barrel("up") if (x + z) % 2 else B.chest("east"))
    w.set(-46, 2, 9, B.barrel("up"))
    w.set(-40, 1, 9, B.S("cartography_table"))
    w.set(-36, 3, 11, B.S("lectern", facing="east", has_book=True))
    w.be(-36, 3, 11, B.lectern_nbt("配達記録", "Chico", ["Letters delivered today: 37.", "Pigeons fed: all."]))
    deco.hanging_lantern(w, -40, 5, 14, drop=1)
    deco.hanging_lantern(w, -33, 5, 11, drop=1)
    # stair to the sorting room upstairs
    for i in range(4):
        w.set(-48 + i, 1 + i, 20, B.stairs("spruce", "east"))
        w.fill(-48 + i, 1, 20, -48 + i, i, 20, F("spruce_planks")) if i else None
    w.fill(-48, 5, 20, -44, 5, 20, AIR)
    # mailbox outside the door (red), the post-horn sign
    w.set(-27, 1, 11, B.S("red_concrete") if False else B.S("red_terracotta"))
    w.set(-27, 2, 11, B.S("red_wool"))
    w.set(-27, 3, 11, B.slab("mangrove", "bottom"))
    # pigeon loft on the ridge: timber box with perches, pyramid roof, pennant
    lx, lz = -39, 14
    yb = ridge - 1
    w.fill(lx - 2, yb, lz - 2, lx + 2, yb + 4, lz + 2, F("white_plaster"))
    w.fill(lx - 1, yb + 1, lz - 1, lx + 1, yb + 4, lz + 1, AIR)
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        w.fill(lx + dx, yb, lz + dz, lx + dx, yb + 4, lz + dz, B.log("dark_oak"))
    for (dx, dz, fac) in ((0, -2, "north"), (0, 2, "south"), (-2, 0, "west"), (2, 0, "east")):
        for dy in (1, 3):
            w.set(lx + dx, yb + dy, lz + dz, AIR)
            vx, vz = (dx // 2, dz // 2)
            w.set(lx + dx + vx, yb + dy - 1, lz + dz + vz, B.trapdoor("spruce", K.OPP[fac], "top", False))
        w.set(lx + dx * 3 // 2, yb + 2, lz + dz * 3 // 2, B.S("white_wool")) if False else None
    w.fill(lx - 1, yb + 1, lz - 1, lx + 1, yb + 1, lz + 1, B.S("hay_block", axis="y"))
    S.hip_roof(w, lx - 2, lz - 2, lx + 2, lz + 2, yb + 5, ROOF, overhang=1)
    TK.flagpole(w, lx, yb + 8, lz, 3, "east", "pennant", 4.0, 1.0, pole="fence")
    # white "pigeons": a few birch buttons / white candles on the perches
    for (dx, dz) in ((0, -3), (3, 0), (0, 3)):
        w.set(lx + dx, yb + 1, lz + dz, B.candle(1, "white", lit=False)) if False else None


# ============================================================================== item shop
def item_shop(w):
    rect = (-28, 25, -11, 38)
    f, W, D = TK.frame_for(w, rect, "north")
    sp = TK.HallSpec(storeys=2, sh=5, upper="timber", roof="mangrove", roof_kind="gable_z", portal_w=2,
                     portal_h=4, banner_nbt=SHOP_BANNER, banner_color="green", trim=None,
                     sign=(["", "道具屋", "Item Shop", ""], "yellow"), window_every=3, dormers=False,
                     chimney=True, wall="castle_bricks")
    a = TK.civic_hall(f, W, D, sp)
    # striped awnings over the shop windows
    for (x0, x1) in ((-27, -22), (-17, -13)):
        for x in range(x0, x1 + 1):
            c = "green" if (x % 2) else "white"
            w.set(x, 4, 24, B.wool(c))
            w.set(x, 3, 23, B.S(f"{c}_wall_banner", facing="north"))
    # interior: counter, shelves of goods
    w.fill(-27, 0, 26, -12, 0, 37, F("oak_planks"))
    for x in range(-25, -13):
        w.set(x, 1, 33, F("oak_planks") if x in (-25, -14) else B.S("barrel", facing="north"))
        w.set(x, 2, 33, B.slab("oak", "top"))
    w.set(-19, 1, 33, B.trapdoor("oak", "south", "bottom", True))
    w.set(-19, 2, 33, AIR)
    w.spot("npc.item_shop", (-20, 1, 34), 180.0)
    K.shelf(w, -27, 37, -12, 37, 1, 4, "north", seed=61, kinds=["barrel", "chiseled", "bookshelf", "barrel",
                                                                "chest"])
    K.shelf(w, -27, 27, -27, 32, 1, 3, "east", seed=62)
    K.shelf(w, -12, 27, -12, 32, 1, 3, "west", seed=63)
    goods = [B.S("brewing_stand", has_bottle_0=True, has_bottle_1=True, has_bottle_2=True), B.S("cake"),
             B.S("decorated_pot"), B.candle(3, "red"), B.S("flower_pot"), B.lantern(), B.candle(2, "light_blue")]
    for i, x in enumerate(range(-24, -14)):
        if x != -19:
            w.set(x, 3, 33, goods[i % len(goods)]) if hash2(x, 3, 9) < 0.6 else None
    for (x, item) in ((-26, "minecraft:potion"), (-22, "minecraft:bread"), (-16, "minecraft:golden_apple"),
                      (-13, "minecraft:arrow")):
        deco.item_frame(w, x, 3, 26, "south", item)
    deco.hanging_lantern(w, -19, 5, 29, drop=1)
    deco.hanging_lantern(w, -23, 5, 35, drop=1)
    deco.hanging_lantern(w, -15, 5, 35, drop=1)


# ============================================================================== blacksmith
def smithy(w):
    rect = (-50, 25, -32, 38)
    f, W, D = TK.frame_for(w, rect, "north")
    sp = TK.HallSpec(storeys=1, sh=6, roof="deepslate_tiles", roof_kind="gable_x", portal_w=3, portal_h=4,
                     banner_nbt=SMITH_BANNER, banner_color="gray", trim=None, wall="stone_bricks",
                     pilaster="polished_andesite", plinth="cobblestone", window_every=3, dormers=False,
                     sign=(["", "鍛冶屋", "Blacksmith", ""], "white"))
    a = TK.civic_hall(f, W, D, sp)
    ridge = a["ridge"]
    # the great forge: brick hearth against the back wall with a hood and a tall chimney (heavy smoke)
    hx, hz = -41, 37
    w.fill(hx - 2, 0, hz - 2, hx + 2, 0, hz, F("polished_blackstone_bricks"))
    w.fill(hx - 2, 1, hz - 1, hx + 2, 1, hz, F("bricks"))
    w.fill(hx - 1, 1, hz - 1, hx + 1, 1, hz - 1, B.S("magma_block"))
    w.set(hx, 1, hz - 1, B.S("lava_cauldron"))
    w.fill(hx - 2, 2, hz, hx + 2, 4, hz, F("bricks"))
    w.fill(hx - 2, 4, hz - 2, hx + 2, 4, hz - 1, F("bricks"))
    for x in range(hx - 2, hx + 3):
        w.set(x, 3, hz - 2, B.stairs("bricks", "south", "top"))
    w.fill(hx - 1, 5, hz - 1, hx + 1, ridge + 3, hz, F("bricks"))
    for x in range(hx - 1, hx + 2):
        TK.smoke(w, x, ridge + 4, hz, heavy=True) if x == hx else None
    w.set(hx - 1, ridge + 4, hz - 1, B.slab("stone_bricks", "bottom"))
    w.set(hx + 1, ridge + 4, hz - 1, B.slab("stone_bricks", "bottom"))
    # anvils, quench trough, grindstone, furnaces, racks
    w.set(hx, 1, hz - 4, B.S("anvil", facing="east"))
    w.set(hx - 4, 1, hz - 4, B.S("damaged_anvil", facing="north"))
    w.set(hx + 3, 1, hz - 1, B.S("blast_furnace", facing="north", lit=True))
    w.set(hx - 3, 1, hz - 1, B.S("furnace", facing="north", lit=True))
    w.set(hx + 4, 1, hz - 1, B.S("blast_furnace", facing="north", lit=True))
    for x in (hx + 2, hx + 3, hx + 4):
        w.set(x, 1, hz - 6, B.S("water_cauldron", level=3))
    w.set(hx - 3, 1, hz - 6, B.S("grindstone", face="floor", facing="north"))
    w.set(hx - 6, 1, hz - 1, B.S("smithing_table"))
    w.set(hx - 7, 1, hz - 1, B.S("barrel", facing="up"))
    w.set(hx - 7, 1, hz - 3, B.S("coal_block")) if False else None
    for (x, z) in ((hx - 7, hz - 2), (hx - 7, hz - 3)):
        w.set(x, 1, z, B.S("coal_block"))
    w.set(hx - 7, 2, hz - 2, B.S("coal_block"))
    w.set(hx + 6, 1, hz, B.S("chest", facing="north"))
    # weapon racks on the side walls (item frames + armour stands)
    for (x, fac) in ((-49, "east"), (-33, "west")):
        for z in range(28, 37, 2):
            deco.item_frame(w, x, 3, z, fac, ("minecraft:iron_sword", "minecraft:iron_axe", "minecraft:iron_pickaxe",
                                              "minecraft:shield", "minecraft:iron_helmet")[(z // 2) % 5], rotation=1)
    deco.armor_stand(w, -48, 0, 28, yaw=-90.0, armor="iron", weapon="minecraft:iron_sword",
                     offhand=deco.blue_shield())
    deco.armor_stand(w, -34, 0, 28, yaw=90.0, armor="chainmail", weapon="minecraft:iron_axe")
    w.spot("npc.blacksmith", (hx, 1, hz - 5), 180.0)
    w.fill(-49, 0, 26, -33, 0, 38, F("cobblestone"), replace=[F("polished_andesite")])
    deco.hanging_lantern(w, hx, 6, hz - 6, drop=1)
    deco.hanging_lantern(w, -36, 6, 31, drop=1)
    # open smithy yard behind the lane: lean-to roof on posts, logs, ore piles
    for x in range(-31, -27):
        for z in range(26, 39):
            pass


def grounds(w):
    TK.pave(w, -51, 6, -6, 39, TK.SQUARE_MIX, seed=91)
    # lane between the post office and the shops (z 22..24)
    TK.pave(w, -51, 22, -27, 24, TK.LANE_MIX, seed=92, only_ground=False)
    # forge yard x -31..-28, z 25..39 between smithy and item shop: firewood, ore, a cart
    for z in range(27, 38, 2):
        w.set(-30, 1, z, B.log("oak", "z")) if z < 33 else w.set(-30, 1, z, B.S("coarse_dirt"))
    w.set(-29, 1, 35, B.S("raw_iron_block"))
    w.set(-29, 1, 36, B.S("coal_block"))
    w.set(-29, 2, 36, B.S("raw_iron_block"))
    for (x, z) in ((-27, 24), (-51, 23)):
        K.lamp(w, x, 0, z, height=4) if x > -51 else None
    # flags of the shops on the corner
    TK.flagpole(w, -27, 0, 22, 7, "east", "market", 3.0, 2.0, pole="fence")
    for (x, z) in ((-12, 22), (-20, 22)):
        pass


def build(w, district, terrain):
    grounds(w)
    post_office(w)
    item_shop(w)
    smithy(w)
