"""商店街 Shops south-west of the plaza.

- 郵便局 Post office (x -50..-28, z 7..21, front east towards the plaza): counter with letter racks and parcels
  (Chico, ``npc.mailbox``), a red mailbox outside, and the pigeon loft on the roof ridge.
- 道具屋 Item shop (x -26..-8, z 25..39, front north on the plaza ring): shelves of potions, food and tools,
  Pim's counter (``npc.item_shop``).
- 鍛冶屋 Blacksmith (x -50..-32, z 25..38, front south on the street at z = 43, a back door to the shop lane): the
  great forge with its hood and chimney against the back wall, bellows, anvils, a quenching trough, grindstone and
  smithing table, a tool wall, weapon racks and armour on stands, ore and coal bins, a sales counter by the door,
  exposed beams with chains and lanterns; heavy smoke from the forge chimney (Goran, ``npc.blacksmith``). The owner
  found the entrance on the wrong side (2026-11-11): it faced the back lane, so the street saw a blank wall.
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
    "smithy": dict(pos=(-36.5, 3.6, 36.5), look=(-44, 2, 27), fov=84, sun=(40, -160), fog=900, exposure=1.3,
                   label="Goran's forge"),
    "smithy_street": dict(pos=(-30, 4.0, 47), look=(-41, 4, 36), fov=70, sun=(40, -160), fog=2600,
                          label="The smithy from the street (entrance on the street)"),
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
    # stair to the sorting room upstairs (along the south wall, rising east; the top step is level with the
    # sorting-room floor, the landing beyond it at x = -43)
    from . import _furnish as FU
    FU.stair_up(w, -48, 20, 0, 5, "east", wood="spruce")
    post_office_rooms(w)
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


def post_office_rooms(w):
    """The sorting room upstairs (floor y = 5): sorting tables with letters and parcels, mail sacks, pigeonholes,
    Chico's desk; the counter room below dressed with a rug, plants and paintings."""
    from . import _furnish as FU
    y = 5
    # sorting tables down the middle
    for x in range(-46, -32, 4):
        for z in (12, 13):
            w.set(x, y + 1, z, B.slab("spruce", "top"))
            w.set(x + 1, y + 1, z, B.slab("spruce", "top"))
            w.set(x, y + 2, z, B.carpet("white"))
        w.set(x + 1, y + 2, 12, B.candle(2, "white"))
        for z in (11, 14):
            w.set(x, y + 1, z, B.stairs("spruce", "north" if z == 11 else "south"))
    # pigeonholes along the back wall, sacks and parcels
    for z in range(9, 20):
        for yy in (y + 1, y + 2, y + 3):
            if w.is_empty(-49, yy, z):
                w.set(-49, yy, z, B.S("chiseled_bookshelf", facing="east",
                                      **{f"slot_{i}_occupied": hash3(z, yy, i, 77) < 0.65 for i in range(6)}))
    for (x, z) in ((-31, 9), (-30, 9), (-31, 10), (-30, 19), (-31, 19)):
        if w.is_empty(x, y + 1, z):
            w.set(x, y + 1, z, B.wool("brown") if (x + z) % 2 else B.wool("white"))
    FU.crates(w, -33, y, 17, n=4, seed=31)
    FU.desk(w, -40, y, 9, "north", wood="spruce", seed=32)
    FU.dress(w, -48, 9, -29, 19, y, 10, seed=33, rug_colors=("blue", "white"), paintings=2,
             keep={(-44, 20), (-43, 20), (-44, 19)})
    deco.hanging_lantern(w, -38, 10, 14, drop=1)
    # the counter room
    FU.dress(w, -35, 8, -29, 20, 0, 5, seed=34, rug_colors=("red", "yellow"), paintings=1, beams_on=False)


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
            w.set(x, 4, 23, B.S(f"{c}_wall_banner", facing="north"))
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
    item_shop_rooms(w)


def item_shop_rooms(w):
    """The shop floor dressed (display tables of goods, barrels of produce, a rug, plants, paintings) and the
    stair behind the counter up to Pim's storeroom and room under the roof."""
    from . import _furnish as FU
    # display tables in the middle of the shop floor
    for (tx, tz) in ((-23, 29), (-16, 29)):
        for x in (tx, tx + 1):
            w.set(x, 1, tz, B.slab("spruce", "top"))
            FU.goods(w, x, 2, tz, seed=x * 3 + tz, kinds=("brewing_stand", "potted_red_tulip", "cake", "candle",
                                                          "decorated_pot", "lantern"))
    for (x, z, st) in ((-26, 27, B.S("melon")), (-26, 28, B.S("pumpkin")), (-13, 27, B.S("hay_block", axis="y")),
                       (-13, 28, B.barrel("up"))):
        if w.is_empty(x, 1, z):
            w.set(x, 1, z, st)
    FU.rug(w, -21, 26, -18, 28, 0, field="green", border="yellow")
    # stair behind the counter, rising east along the back shelves, to the floor at y = 5
    FU.stair_up(w, -27, 36, 0, 5, "east", wood="spruce")
    # Pim's storeroom and room (free cells x -27..-12, z 26..37, floor y = 5)
    y = 5
    for x in range(-27, -12):
        if w.is_empty(x, y + 1, 37) and not (-27 <= x <= -21):
            w.set(x, y + 1, 37, B.barrel("north") if (x % 3) else B.S("chest", facing="north"))
            if x % 2 == 0 and w.is_empty(x, y + 2, 37):
                w.set(x, y + 2, 37, B.barrel("up"))
    FU.crates(w, -15, y, 33, n=5, seed=5)
    FU.crates(w, -26, y, 27, n=4, seed=6)
    FU.bed_pair(w, -13, y, 27, "north", "lime")
    if w.is_empty(-13, y + 1, 29):
        FU.nightstand(w, -13, y, 29)
    FU.desk(w, -19, y, 27, "north", wood="spruce", seed=7)
    FU.dress(w, -27, 26, -12, 37, y, 10, seed=8, rug_colors=("orange", "white"), paintings=2,
             keep={(-21, 36), (-22, 36), (-21, 35)})
    deco.hanging_lantern(w, -19, 10, 31, drop=1)


# ============================================================================== blacksmith
def smithy(w):
    """Goran's forge, its portal on the street to the south; the forge against the back (north) wall."""
    from . import _furnish as FU
    rect = (-50, 25, -32, 38)
    f, W, D = TK.frame_for(w, rect, "south")
    sp = TK.HallSpec(storeys=1, sh=6, roof="deepslate_tiles", roof_kind="gable_x", portal_w=3, portal_h=4,
                     banner_nbt=SMITH_BANNER, banner_color="gray", trim=None, wall="stone_bricks",
                     pilaster="polished_andesite", plinth="cobblestone", window_every=3, dormers=False,
                     sign=(["", "鍛冶屋", "Blacksmith", ""], "white"))
    a = TK.civic_hall(f, W, D, sp)
    ridge = a["ridge"]
    x0, x1, z0, z1 = -49, -33, 26, 37            # the room (free cells)
    # floor: cobbles with an andesite path from the door to the counter
    w.fill(x0, 0, z0, x1, 0, z1, F("cobblestone"))
    for z in range(z0, z1 + 1):
        for x in range(-43, -39):
            w.set(x, 0, z, F("polished_andesite") if (x + z) % 3 else F("andesite"))
    # ---- the great forge against the back wall: hearth, hood, chimney, bellows
    hx, hz = -41, 26
    w.fill(hx - 3, 0, hz, hx + 3, 0, hz + 2, F("polished_blackstone_bricks"))
    w.fill(hx - 3, 1, hz, hx + 3, 1, hz + 1, F("bricks"))
    w.fill(hx - 2, 1, hz + 1, hx + 2, 1, hz + 1, B.S("magma_block"))
    w.set(hx, 1, hz + 1, B.S("lava_cauldron"))
    w.set(hx - 1, 2, hz + 1, B.S("campfire", lit=True, facing="south"))
    w.set(hx + 1, 2, hz + 1, B.S("campfire", lit=True, facing="south"))
    w.fill(hx - 3, 2, hz, hx + 3, 4, hz, F("bricks"))
    w.set(hx - 3, 2, hz + 1, F("bricks"))
    w.set(hx + 3, 2, hz + 1, F("bricks"))
    w.fill(hx - 3, 4, hz + 1, hx + 3, 4, hz + 1, F("bricks"))
    for x in range(hx - 3, hx + 4):
        w.set(x, 3, hz + 2, B.stairs("bricks", "north", "top"))
        w.set(x, 5, hz + 1, B.stairs("bricks", "north"))
    w.fill(hx - 1, 5, hz, hx + 1, ridge + 3, hz, F("bricks"))
    TK.smoke(w, hx, ridge + 4, hz, heavy=True)
    w.set(hx - 1, ridge + 4, hz, B.slab("stone_bricks", "bottom"))
    w.set(hx + 1, ridge + 4, hz, B.slab("stone_bricks", "bottom"))
    # an iron grate in front of the fire (nobody steps onto the magma)
    for x in range(hx - 2, hx + 3):
        w.set(x, 1, hz + 2, B.S("iron_bars"))
    # bellows on both sides of the hearth (a leather bag between two boards)
    for bxs in (hx - 4, hx + 4):
        w.set(bxs, 1, hz, B.S("piston", facing="up"))
        w.set(bxs, 2, hz, B.wool("brown"))
        w.set(bxs, 3, hz, B.trapdoor("dark_oak", "south", "top", False))
    # ---- the working floor: anvils, quench trough, grindstone, smithing table, workbench
    w.set(hx, 1, hz + 4, B.S("anvil", facing="east"))
    w.set(hx - 3, 1, hz + 4, B.S("chipped_anvil", facing="north"))
    for x in (hx + 2, hx + 3, hx + 4):
        w.set(x, 1, hz + 4, B.S("water_cauldron", level=3))
    w.set(hx + 5, 1, hz + 4, B.S("barrel", facing="up"))
    w.set(hx - 5, 1, hz + 3, B.S("grindstone", face="floor", facing="east"))
    w.set(hx - 6, 1, hz, B.S("smithing_table"))
    w.set(hx - 7, 1, hz, B.S("blast_furnace", facing="south", lit=True))
    w.set(hx + 6, 1, hz, B.S("blast_furnace", facing="south", lit=True))
    w.set(hx + 7, 1, hz, B.S("furnace", facing="south", lit=True))
    w.set(hx + 5, 1, hz, B.S("crafting_table"))
    w.spot("npc.blacksmith", (hx, 1, hz + 3), 0.0)       # between the hearth and the anvil, facing the door
    # ---- ore, coal and timber in the west corner
    for (x, y, z, st) in ((x0, 1, 31, B.S("coal_block")), (x0, 1, 32, B.S("coal_block")), (x0, 2, 31, B.S("coal_block")),
                          (x0, 1, 33, B.S("raw_iron_block")), (x0 + 1, 1, 33, B.S("raw_copper_block")),
                          (x0, 2, 33, B.S("raw_iron_block")), (x0, 1, 34, B.barrel("up")),
                          (x0, 1, 35, B.barrel("up")),
                          (x0 + 1, 1, 31, B.log("oak", "z")), (x0 + 1, 1, 32, B.log("oak", "z"))):
        w.set(x, y, z, st)
    w.set(x0, 2, 34, B.S("raw_gold_block"))
    # ---- the tool wall (back wall either side of the forge) and weapon racks on the side walls
    tools = ("minecraft:iron_pickaxe", "minecraft:iron_axe", "minecraft:iron_shovel", "minecraft:iron_hoe",
             "minecraft:shears", "minecraft:flint_and_steel", "minecraft:iron_sword", "minecraft:bow")
    i = 0
    for x in list(range(x0 + 1, hx - 4)) + list(range(hx + 5, x1)):
        for y in (3, 4):
            FU.item_on_wall(w, x, y, z0, "south", tools[i % len(tools)])
            i += 1
    for (x, fac) in ((x0, "east"), (x1, "west")):
        for z in range(28, 37, 2):
            FU.item_on_wall(w, x, 3, z, fac, ("minecraft:iron_sword", "minecraft:iron_axe", "minecraft:shield",
                                              "minecraft:crossbow", "minecraft:iron_helmet")[(z // 2) % 5])
    deco.armor_stand(w, x1, 0, 30, yaw=90.0, armor="iron", weapon="minecraft:iron_sword", offhand=deco.blue_shield())
    deco.armor_stand(w, x1, 0, 33, yaw=90.0, armor="chainmail", weapon="minecraft:iron_axe")
    deco.armor_stand(w, x1, 0, 36, yaw=90.0, armor="golden", weapon="minecraft:golden_sword")
    # ---- the sales counter by the door, weapons laid out on it, a bell
    for x in range(x0 + 2, x0 + 7):
        w.set(x, 1, 36, B.S("dark_oak_planks") if x in (x0 + 2, x0 + 6) else B.S("barrel", facing="south"))
        w.set(x, 2, 36, B.slab("dark_oak", "top"))
    w.set(x0 + 4, 3, 36, B.S("bell", attachment="floor", facing="south"))
    for x, item in ((x0 + 3, "minecraft:iron_sword"), (x0 + 5, "minecraft:iron_axe")):
        deco.item_frame(w, x, 3, 36, "up", item)
    w.set(x0 + 2, 3, 36, B.candle(3, "white"))
    w.set(x0 + 6, 3, 36, B.lantern())
    # ---- a back door onto the shop lane (north wall, east of the forge)
    lo, hi = B.door_states("spruce", "north", "left")
    w.set(-36, 1, 25, lo)
    w.set(-36, 2, 25, hi)
    # ---- beams, chains and lanterns, a soot-dark ceiling
    FU.beams(w, x0, z0 + 3, x1, z1, 5, axis="x", every=3, wood="dark_oak")
    for (x, z) in ((hx - 4, 31), (hx + 4, 31), (hx, 34), (x0 + 4, 34)):
        w.set(x, 4, z, B.chain("y"))
        w.set(x, 3, z, B.lantern(hanging=True))
    w.fill(x0, 6, z0, x1, 6, z1, F("dark_oak_planks"), replace=[F("dark_oak_planks")])


def grounds(w):
    TK.pave(w, -51, 6, -6, 39, TK.SQUARE_MIX, seed=91)
    # lane between the post office and the shops (z 22..24)
    TK.pave(w, -51, 22, -27, 24, TK.LANE_MIX, seed=92, only_ground=False)
    # forge yard x -31..-28, z 25..39 between smithy and item shop: firewood, ore, a cart (open to the street)
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
