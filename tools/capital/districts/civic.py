"""市庁区 Civic quarter north-east of the plaza.

- 栄誉の殿堂 Hall of fame (x 8..30, z -39..-25, front south towards the plaza): white stone hall with a shallow
  portico, a gilded blue dome over the centre, heroes' statues in niches, the ranking board on the north wall
  (spot ``board.ranking``) and Chloe's desk (``npc.hall_of_fame``).
- 競売所 Auction house (x 28..50, z -21..-7, front west towards the plaza): tall saleroom with benches facing
  the auctioneer's rostrum (Luce, ``npc.auction``), lots on display.
- 職業ギルド Job hall (x 33..51, z -39..-25, front north on street_n1): tool displays per profession and the
  master's counter (Hagen, ``npc.job_master``).
"""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

DISTRICT = "civic"
AIR = K.AIR
F = K.F
CB, SM, GOLD, ROOF, PALE = K.CB, K.SM, K.GOLD, K.ROOF, K.PALE

VIEWS = {
    "civic_front": dict(pos=(4, 5, -4), look=(30, 12, -28), fov=72, sun=(40, -160), fog=2600,
                        label="Hall of fame and auction house from the plaza"),
    "hall_of_fame": dict(pos=(21.5, 4.5, -26.5), look=(21, 4, -37), fov=78, sun=(40, -160), fog=900,
                         label="Hall of fame: heroes' statues and the ranking board"),
    "auction": dict(pos=(29.5, 4.5, -14.5), look=(49, 3, -14), fov=78, sun=(40, -160), fog=900,
                    label="Auction house saleroom"),
    "job_hall": dict(pos=(42, 4.2, -38.2), look=(42, 2, -27), fov=78, sun=(40, -160), fog=900,
                     label="Job hall"),
}

HOF_BANNER = B.banner_nbt([("er_crest", "yellow"), ("bo", "yellow"), ("tts", "yellow")])
AUCTION_BANNER = B.banner_nbt([("mc", "yellow"), ("bo", "yellow"), ("ts", "yellow"), ("bs", "yellow")])
JOB_BANNER = B.banner_nbt([("cr", "white"), ("mc", "brown"), ("bo", "white")])


# ============================================================================== hall of fame
def hall_of_fame(w):
    rect = (11, -38, 31, -25)
    f, W, D = TK.frame_for(w, rect, "south")
    sp = TK.HallSpec(storeys=1, sh=9, roof="royal_roof_tiles", roof_kind="gable_x", portal_w=3, portal_h=6,
                     banner_nbt=HOF_BANNER, banner_color="blue", sign=(["", "栄誉の殿堂", "Hall of Fame", ""],
                                                                    "yellow"),
                     window_every=4, dormers=False, back_windows=False)
    a = TK.civic_hall(f, W, D, sp)
    eave, ridge = a["eave"], a["ridge"]
    # dome over the centre: drum through the roof, gilded ribs, lantern, flag
    cx, cz = 21, -32
    S.cylinder(w, cx, cz, 4.5, eave + 1, ridge + 1, F(CB))
    S.cylinder(w, cx, cz, 3.5, eave + 1, ridge + 1, AIR)
    for k in range(8):
        a_ = math.radians(k * 45 + 22.5)
        x, z = cx + int(round(4.6 * math.cos(a_))), cz + int(round(4.6 * math.sin(a_)))
        w.fill(x, ridge - 3, z, x, ridge + 1, z, B.pillar("y"))
    S.cylinder(w, cx, cz, 5.2, ridge + 2, ridge + 2, F(SM))
    S.cylinder(w, cx, cz, 5.2, ridge + 2, ridge + 2, B.slab(GOLD, "top"), hollow=True)
    S.dome(w, cx, ridge + 2, cz, 5, F(PALE), ry=6)
    S.dome(w, cx, ridge + 2, cz, 4, AIR, ry=5, hollow=False)
    for k in range(8):                                                   # gilded ribs
        a_ = math.radians(k * 45)
        for i in range(0, 7):
            t = i / 6.0
            r = 5.3 * math.cos(t * math.pi / 2)
            y = ridge + 2 + int(round(6.3 * math.sin(t * math.pi / 2)))
            x, z = cx + int(round(r * math.cos(a_))), cz + int(round(r * math.sin(a_)))
            w.set(x, y, z, F(GOLD))
    top = ridge + 9
    w.fill(cx, ridge + 8, cz, cx, top, cz, F(GOLD))
    S.cylinder(w, cx, cz, 1.2, top + 1, top + 3, B.S("glass"))
    w.set(cx, top + 2, cz, B.S("chandelier"))
    S.cylinder(w, cx, cz, 1.4, top + 4, top + 4, B.slab(GOLD, "bottom"))
    TK.flagpole(w, cx, top + 4, cz, 4, "east", "kingdom", 3.0, 2.0, pole="wall")
    # oculus: light from the dome into the hall
    w.fill(cx - 2, eave, cz - 2, cx + 2, eave, cz + 2, AIR)
    for (dx, dz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        w.set(cx + dx, eave, cz + dz, F(SM))
    deco.chandelier(w, cx, ridge + 6, cz, drop=8)
    # portico: columns and a pediment in front of the portal (z -24..-23)
    px0, px1 = 16, 26
    w.fill(px0, 0, -24, px1, 0, -23, F("polished_diorite"))
    for x in (16, 19, 23, 26):
        w.set(x, 1, -23, F(SM))
        w.fill(x, 2, -23, x, 7, -23, B.pillar("y"))
        w.set(x, 8, -23, F(CARVED_OR_SM()))
    w.fill(px0, 9, -24, px1, 9, -23, F(SM))
    w.fill(px0 - 1, 9, -22, px1 + 1, 9, -22, B.stairs(SM, "north", "top")) if False else None
    w.fill(px0, 10, -24, px1, 10, -23, F(GOLD)) if False else None
    S.gable_roof(w, px0, -25, px1, -23, 10, ROOF, axis="z", overhang=0, gable_state=F(SM),
                 ridge_state=B.slab(GOLD), eave_trim=GOLD, end_overhang=1)
    for x in range(px0, px1 + 1):
        if w.get(x, 11, -23) == B.canon(F(SM)) and abs(x - 21) <= 1:
            w.set(x, 11, -23, B.S("castle_crest_stone"))
    w.set(21, 11, -24, B.S("castle_crest_stone"))
    # interior: polished floor, a carpet, statues of heroes in niches, ranking board on the north wall
    w.fill(12, 0, -37, 30, 0, -26, B.S("polished_diorite"))
    for x in range(12, 31):
        for z in range(-37, -25):
            if (x + z) % 2 == 0 and 13 <= x <= 29 and -36 <= z <= -27:
                w.set(x, 0, z, F("plaza_flagstones"))
    deco.carpet_runner(w, 20, -36, 22, -26, 0, color="blue", border="yellow")
    for (x, yaw) in ((13, -90.0), (29, 90.0)):
        for z in (-36, -32, -28):
            w.set(x, 1, z, F(SM))
            deco.armor_stand(w, x, 1, z, yaw=yaw, armor=("diamond", "netherite", "golden")[int(hash2(x, z, 3) * 3)],
                             weapon=("minecraft:diamond_sword", "minecraft:trident", "minecraft:bow",
                                     "minecraft:netherite_sword")[int(hash2(z, x, 4) * 4)],
                             offhand=deco.blue_shield() if hash2(x, z, 5) < 0.5 else None)
            w.set(x, 4, z, B.candle(3, "white")) if False else None
            xs = 12 if x == 13 else 30
            w.set(xs, 5, z, B.S("glowstone")) if False else None
            fac = "east" if x == 13 else "west"
            deco.kingdom_wall_banner(w, xs + (1 if x == 13 else -1), 7, z, facing=fac, color="blue", nbt=HOF_BANNER)
    # ranking board: dark oak frame, birch panel, gilded header, framed trophies
    bx0, bx1 = 15, 27
    for x in range(bx0, bx1 + 1):
        for y in range(2, 8):
            edge = x in (bx0, bx1) or y in (2, 7)
            w.set(x, y, -37, F("dark_oak_planks") if edge else F("birch_planks"))
    w.fill(bx0, 8, -37, bx1, 8, -37, F(GOLD))
    deco.sign_text(w, 21, 8, -36, B.S("dark_oak_wall_sign", facing="south"), ["", "ランキング", "Ranking", ""],
                   color="yellow", glowing=True)
    for i, x in enumerate(range(bx0 + 1, bx1)):
        for y in (3, 4, 5, 6):
            if (x - bx0) % 2 == 1:
                lines = [f"{(y - 3) * 6 + (x - bx0) // 2 + 1}.", "---", "", ""]
                deco.sign_text(w, x, y, -36, B.S("birch_wall_sign", facing="south"), lines, color="black")
            elif y == 6:
                deco.item_frame(w, x, y, -36, "south", ("minecraft:golden_apple", "minecraft:nether_star",
                                                         "minecraft:diamond", "minecraft:emerald",
                                                         "minecraft:totem_of_undying")[i % 5])
    w.spot("board.ranking", (21, 1, -35), 0.0)
    # Chloe's desk near the entrance, facing the door
    for x in (17, 18, 24, 25):
        w.set(x, 1, -28, F("dark_oak_planks") if x in (17, 25) else B.S("barrel", facing="south"))
        w.set(x, 2, -28, B.slab("dark_oak", "top"))
    w.set(24, 3, -28, B.lectern("south", True))
    w.be(24, 3, -28, B.lectern_nbt("栄誉録", "Chloe", ["The deeds of Arseria's heroes are written here."]))
    w.set(18, 3, -28, B.candle(3, "white"))
    w.spot("npc.hall_of_fame", (24, 1, -29), 0.0)
    for (x, z) in ((15, -31), (27, -31)):
        deco.chandelier(w, x, 9, z, drop=3)


def CARVED_OR_SM():
    return "carved_castle_stone"


# ============================================================================== auction house
def auction_house(w):
    rect = (28, -21, 50, -7)
    f, W, D = TK.frame_for(w, rect, "west")
    sp = TK.HallSpec(storeys=1, sh=9, roof="royal_roof_tiles", roof_kind="gable_x", portal_w=3, portal_h=6,
                     banner_nbt=AUCTION_BANNER, banner_color="red", sign=(["", "競売所", "Auction House", ""],
                                                                       "yellow"),
                     window_every=4, dormers=True, chimney=False)
    a = TK.civic_hall(f, W, D, sp)
    # saleroom: benches in rows facing the rostrum at the east end
    w.fill(29, 0, -20, 49, 0, -8, F("dark_oak_planks"))
    K.rug(w, 31, -18, 45, -10, 0, field="red", border="yellow")
    for x in range(33, 44, 3):
        for (z0, z1) in ((-19, -16), (-12, -9)):
            for z in range(z0, z1 + 1):
                w.set(x, 1, z, B.stairs("dark_oak", "west"))
            w.set(x, 1, z0 - 1, B.trapdoor("dark_oak", "south", "bottom", True)) if False else None
    # rostrum: raised dais with the auctioneer's desk and a gilded lectern
    w.fill(46, 1, -18, 49, 1, -10, F("dark_oak_planks"))
    w.fill(45, 1, -18, 45, 1, -10, B.stairs("dark_oak", "east"))
    for z in range(-16, -11):
        w.set(46, 2, z, F("dark_oak_planks") if z in (-16, -12) else B.S("chiseled_bookshelf", facing="west"))
        w.set(46, 3, z, B.slab(GOLD, "bottom"))
    w.set(46, 4, -14, B.lectern("west", True))
    w.be(46, 4, -14, B.lectern_nbt("競売目録", "Luce", ["Lot 1: a sword of the old kingdom.", "Lot 2: ..."]))
    w.set(46, 4, -13, B.S("bell", attachment="floor", facing="west"))
    w.spot("npc.auction", (47, 2, -14), 90.0)
    # lots on display along the side walls (armour stands + framed items)
    for x in (32, 38, 44):
        for (z, yaw) in ((-20, 0.0), (-8, 180.0)):
            w.set(x, 1, z, F(SM))
            deco.armor_stand(w, x, 1, z, yaw=yaw, armor=("golden", "chainmail", "iron")[int(hash2(x, z, 1) * 3)],
                             weapon=("minecraft:golden_sword", "minecraft:crossbow", "minecraft:iron_axe")[
                                 int(hash2(z, x, 2) * 3)])
    for x in range(30, 46, 3):
        for (z, fac) in ((-20, "south"), (-8, "north")):
            deco.item_frame(w, x, 5, z, fac, ("minecraft:enchanted_book", "minecraft:emerald", "minecraft:clock",
                                               "minecraft:compass", "minecraft:spyglass")[int(hash2(x, z, 6) * 5)])
    for (x, z) in ((35, -14), (42, -14)):
        deco.chandelier(w, x, 9, z, drop=3)
    deco.kingdom_wall_banner(w, 49, 7, -14, facing="west", color="red", nbt=AUCTION_BANNER)


# ============================================================================== job hall
def job_hall(w):
    rect = (33, -38, 50, -25)
    f, W, D = TK.frame_for(w, rect, "north")
    sp = TK.HallSpec(storeys=2, sh=5, upper="timber", roof="mangrove", roof_kind="gable_x", portal_w=2,
                     portal_h=4, banner_nbt=JOB_BANNER, banner_color="brown", trim=None,
                     sign=(["", "職業ギルド", "Job Hall", ""], "white"), window_every=3, dormers=True,
                     chimney=True, wall="stone_bricks", pilaster="polished_andesite", plinth="cobblestone")
    a = TK.civic_hall(f, W, D, sp)
    # interior: counter across the hall, profession displays on the walls
    w.fill(34, 0, -38, 50, 0, -26, F("spruce_planks"))
    for x in range(36, 49):
        w.set(x, 1, -31, F("spruce_planks") if x in (36, 48) else B.S("barrel", facing="north"))
        w.set(x, 2, -31, B.slab("spruce", "top"))
    w.set(42, 1, -31, B.trapdoor("spruce", "south", "bottom", True))
    w.set(42, 2, -31, AIR)
    w.spot("npc.job_master", (41, 1, -30), 180.0)
    w.set(40, 3, -31, B.lectern("north", True))
    w.be(40, 3, -31, B.lectern_nbt("職業登録簿", "Hagen", ["Miners, smiths, fishers, cooks, alchemists."]))
    # profession corners: smithing, alchemy, fishing, cooking, carpentry, mining
    stations = [(34, -37, B.S("smithing_table")), (35, -37, B.S("anvil", facing="east")),
                (50, -37, B.S("brewing_stand", has_bottle_0=True)), (49, -37, B.S("cauldron")),
                (34, -27, B.S("barrel", facing="up")), (50, -27, B.S("smoker", facing="west")),
                (49, -27, B.S("crafting_table")), (35, -27, B.S("fletching_table")),
                (44, -28, B.S("loom", facing="north")), (39, -28, B.S("stonecutter", facing="north")),
                (46, -37, B.S("cartography_table")), (38, -37, B.S("grindstone", face="floor", facing="north"))]
    for (x, z, st) in stations:
        w.set(x, 1, z, st)
    tools = ["minecraft:iron_pickaxe", "minecraft:fishing_rod", "minecraft:iron_axe", "minecraft:iron_shovel",
             "minecraft:iron_hoe", "minecraft:shears", "minecraft:brush", "minecraft:spyglass"]
    for i, x in enumerate(range(35, 50, 2)):
        deco.item_frame(w, x, 3, -38, "south", tools[i % len(tools)], rotation=1)
    for (x, z) in ((38, -34), (46, -34)):
        deco.hanging_lantern(w, x, 5, z, drop=1)
    w.fill(34, 5, -38, 50, 5, -26, F("spruce_planks"))
    for z in range(-38, -25, 3):
        w.fill(34, 4, z, 50, 4, z, B.log("spruce", "x")) if False else None
    deco.kingdom_wall_banner(w, 42, 4, -38, facing="south", color="brown", nbt=JOB_BANNER)


def grounds(w):
    TK.pave(w, 6, -39, 51, -6, TK.SQUARE_MIX, seed=81)
    # planters and benches on the forecourt between the hall of fame and the auction house
    deco.bench(w, 12, 0, -18, facing="east", length=3) if False else None
    for (x, z) in ((27, -23), (25, -11)):
        K.lamp(w, x, 0, z, height=4)
    # masts with kingdom flags flanking the hall of fame portico
    for (x, z) in ((14, -24), (29, -23)):
        K.mast(w, x, 0, z, height=9, sides=("east", "west"), nbt=HOF_BANNER)
        TK.flag(w, x, 10, z, "east", "kingdom", 3.0, 2.0)
    # the lane between job hall and auction house
    TK.pave(w, 31, -24, 51, -22, TK.LANE_MIX, seed=82, only_ground=False)


def build(w, district, terrain):
    grounds(w)
    hall_of_fame(w)
    auction_house(w)
    job_hall(w)
