"""The tavern (酒場) and the inn (宿屋): shells from the civic hall kit with full interiors."""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2

from . import _civic_kit as K
from . import _town_kit as TK

AIR = K.AIR
F = K.F

TAVERN_BANNER = B.banner_nbt([("mc", "yellow"), ("bo", "brown"), ("cbo", "yellow")])
INN_BANNER = B.banner_nbt([("mc", "white"), ("bo", "yellow"), ("tt", "blue")])


def tavern(w, rect, front, name_lines=("", "酒場", "The Tipsy Wyvern", "")):
    """Two-storey tavern: stone ground floor with the bar, kegs, tables, stage and fireplace; timbered upper
    floor with a gallery of private rooms. ``rect``/``front`` as for ``frame_for``. Returns the frame."""
    f, W, D = TK.frame_for(w, rect, front)
    sp = TK.HallSpec(storeys=2, sh=6, upper="timber", roof="terracotta_roof_tiles", roof_kind="gable_x",
                     portal_w=2, portal_h=4, banner_nbt=TAVERN_BANNER, banner_color="brown", trim=None,
                     wall="stone_bricks", pilaster="spruce_planks" if False else "polished_andesite",
                     plinth="cobblestone", window_every=3, dormers=True, chimney=True,
                     sign=(list(name_lines), "yellow"), floor="spruce_planks")
    a = TK.civic_hall(f, W, D, sp)
    sh = sp.sh
    # floor: dark planks with a stone hearth area
    f.fill(1, 0, 1, W - 2, 0, D - 2, F("spruce_planks"))
    for x in range(1, W - 1, 3):
        f.fill(x, 0, 1, x, 0, D - 2, F("dark_oak_planks"))
    # bar counter along the back (z = D-4), kegs (barrels on their side) behind it
    bz = D - 4
    for x in range(2, W - 6):
        f.set(x, 1, bz, F("dark_oak_planks") if x in (2, W - 7) else B.S("barrel", facing="north"))
        f.set(x, 2, bz, B.slab("dark_oak", "top"))
        if hash2(x, bz, 3) < 0.4:
            f.set(x, 3, bz, (B.S("decorated_pot"), B.candle(2, "orange"), B.S("flower_pot"))[int(hash2(x, 1, 4) * 3)])
    f.set(W - 6, 1, bz, B.trapdoor("dark_oak", "south", "bottom", True))
    for x in range(2, W - 6):
        for y in (1, 2):
            f.set(x, y, D - 2, B.S("barrel", facing="north" if (x + y) % 2 else "up"))
        f.set(x, 3, D - 2, B.stairs("dark_oak", "north", "top"))
    for x in range(3, W - 7, 3):
        f.set(x, 4, D - 2, B.S("decorated_pot"))
    # stools at the bar
    for x in range(3, W - 7, 2):
        f.set(x, 1, bz - 1, B.fence("dark_oak"))
        f.set(x, 2, bz - 1, B.S("dark_oak_pressure_plate"))
    # tables with chairs in the common room
    for (tx, tz) in ((3, 3), (7, 3), (11, 3), (3, 7), (7, 7), (11, 7)):
        if tx < W - 6 and tz < bz - 2:
            K.table_set(f, tx, 0, tz, chairs="spruce", top="dark_oak", cloth=None)
            f.set(tx, 2, tz, B.candle(2, "white") if hash2(tx, tz, 5) < 0.5 else B.S("flower_pot"))
    # fireplace on the side wall and a small stage with a bard's lute (note block + jukebox)
    fs = K.Face(f, "z", W - 1, 1)
    K.fireplace(fs, D // 2, 1, w=3, h=4, d=-1, mat="bricks", mantel="smooth_castle_stone")
    f.fill(W - 5, 1, 2, W - 3, 1, 4, F("spruce_planks"))
    f.set(W - 4, 2, 3, B.S("note_block"))
    f.set(W - 3, 2, 2, B.S("jukebox"))
    # hanging lanterns, antlers/trophies, a wanted board by the door
    for x in range(3, W - 2, 4):
        for z in (3, D // 2 + 1):
            deco.hanging_lantern(f, x, sh, z, drop=1)
    for x in range(3, W - 3, 5):
        f.set(x, 4, 0 + 1, B.S("skeleton_wall_skull", facing="south")) if False else None
    f.set(1, 3, 2, B.S("oak_wall_sign", facing="east"))
    f.be(1, 3, 2, B.sign_nbt(["本日のおすすめ", "Wyvern stew", "Honey ale", ""], color="black"))
    # stair to the upper floor along the side wall
    for i in range(sh):
        f.set(1, 1 + i, D - 3 - i, B.stairs("spruce", "north"))
        f.fill(1, sh, D - 3 - i, 1, sh, D - 3 - i, AIR)
    # upper floor: rooms along the front, gallery corridor
    y0 = sh
    for x in range(3, W - 2, 5):
        f.fill(x, y0 + 1, 1, x, 2 * sh - 1, D // 2, F("white_plaster"))
        lo, hi = B.door_states("spruce", "north", "left")
        f.set(x + 2, y0 + 1, D // 2, lo) if x + 2 < W - 1 else None
        f.set(x + 2, y0 + 2, D // 2, hi) if x + 2 < W - 1 else None
        K.bed(f, x + 1, y0, 1, facing="north", color=("red", "blue", "green", "yellow")[(x // 5) % 4]) \
            if x + 1 < W - 1 else None
        f.set(x + 3, y0 + 1, 1, B.chest("south")) if x + 3 < W - 1 else None
    f.fill(1, y0 + 1, D // 2, W - 2, y0 + 1, D // 2, F("white_plaster")) if False else None
    deco.hanging_lantern(f, W // 2, 2 * sh, D - 3, drop=1)
    TK.bracket_flag(f, 1, sh + 2, -1, "north", "pennant", 3.0, 1.0)
    return f, W, D


def inn(w, rect, front, name_lines=("", "宿屋", "Inn of the Silver Star", "")):
    """Three-storey inn: reception, dining hall with long tables and fireplace, kitchen; two floors of guest
    rooms with beds, wardrobes and flower boxes; stable yard sign. Returns (frame, W, D)."""
    f, W, D = TK.frame_for(w, rect, front)
    sp = TK.HallSpec(storeys=3, sh=5, upper="timber", roof="royal_roof_tiles", roof_kind="gable_x",
                     portal_w=2, portal_h=4, banner_nbt=INN_BANNER, banner_color="blue", trim="gilded_trim",
                     wall="castle_bricks", pilaster="smooth_castle_stone", plinth="polished_andesite",
                     window_every=3, dormers=True, chimney=True, sign=(list(name_lines), "yellow"),
                     floor="oak_planks")
    a = TK.civic_hall(f, W, D, sp)
    sh = sp.sh
    px = (W - 1) // 2
    # ground floor: reception desk left of the door, dining hall right, kitchen at the back
    f.fill(1, 0, 1, W - 2, 0, D - 2, F("oak_planks"))
    K.rug(f, px - 2, 1, px + 2, 4, 0, field="blue", border="yellow")
    for z in range(2, 6):
        f.set(px - 4, 1, z, F("spruce_planks") if z in (2, 5) else B.S("barrel", facing="east"))
        f.set(px - 4, 2, z, B.slab("spruce", "top"))
    f.set(px - 4, 3, 3, B.S("bell", attachment="floor", facing="east"))
    f.set(px - 4, 3, 4, B.lectern("east", True))
    f.be(px - 4, 3, 4, B.lectern_nbt("宿帳", "Innkeeper", ["Guests of the Silver Star.", "Room 1: ...", ""]))
    for z in range(1, 6):
        for y in (1, 2, 3):
            if hash2(z, y, 61) < 0.6:
                f.set(1, y, z, B.S("chiseled_bookshelf", facing="east", slot_0_occupied=True, slot_4_occupied=True))
    # dining hall: long tables
    for x in range(px + 2, W - 3, 4):
        deco.long_table(f, x, 0, 2, max(3, D - 8), axis="z", top="spruce", cloth="white", chairs=True,
                        chair_wood="spruce", candles=True)
    fs = K.Face(f, "z", W - 1, 1)
    K.fireplace(fs, D - 4, 1, w=3, h=4, d=-1, mat="bricks", mantel="smooth_castle_stone")
    for x in range(px + 3, W - 2, 4):
        deco.chandelier(f, x, sh, D // 2, drop=2)
    # kitchen behind a wall at the back
    kz = D - 5
    f.fill(1, 1, kz, px, sh - 1, kz, F("white_plaster"))
    lo, hi = B.door_states("spruce", "south", "left")
    f.set(px - 1, 1, kz, lo)
    f.set(px - 1, 2, kz, hi)
    for x in range(1, px):
        f.set(x, 1, D - 2, (B.S("smoker", facing="north", lit=True), B.S("furnace", facing="north", lit=True),
                            B.S("water_cauldron", level=3), B.barrel("up"), F("spruce_planks"))[x % 5])
        f.set(x, 3, D - 2, B.stairs("spruce", "north", "top"))
    f.set(2, 1, D - 3, B.S("crafting_table"))
    f.set(3, 1, D - 3, B.S("hay_block", axis="y"))
    deco.hanging_lantern(f, px // 2, sh, D - 3, drop=1)
    # stairs up through the floors along the left wall (behind the reception)
    for k in range(2):
        y0 = k * sh
        for i in range(sh):
            f.set(2 + i, y0 + 1 + i, kz - 1, B.stairs("spruce", "east"))
            f.set(2 + i, y0 + sh, kz - 1, AIR) if i < sh - 1 else None
        f.fill(2, y0 + sh, kz - 1, 2 + sh - 2, y0 + sh, kz - 1, AIR)
    # guest rooms on floors 1 and 2: partitions every 5, a bed, a wardrobe, a rug, a lantern each
    for k in (1, 2):
        y0 = k * sh
        f.fill(1, y0 + 1, kz - 2, W - 2, y0 + sh - 1, kz - 2, F("white_plaster"))      # corridor wall
        for x in range(1, W - 1):
            if x % 5 == 0:
                f.fill(x, y0 + 1, 1, x, y0 + sh - 1, kz - 3, F("white_plaster"))
        for x in range(1, W - 5, 5):
            dx = x + 2
            lo, hi = B.door_states("oak", "north", "left")
            f.set(dx, y0 + 1, kz - 2, lo)
            f.set(dx, y0 + 2, kz - 2, hi)
            K.bed(f, x + 1, y0, 1, facing="north", color=("red", "blue", "light_blue", "green", "white")[
                (x // 5 + k) % 5])
            f.set(x + 3, y0 + 1, 1, B.chest("south"))
            f.set(x + 3, y0 + 2, 1, B.S("flower_pot"))
            K.rug(f, x + 1, 3, x + 3, kz - 4, y0, field=("blue", "red", "green")[(x // 5) % 3], border="white") \
                if kz - 4 >= 3 else None
            deco.hanging_lantern(f, x + 2, y0 + sh, 3, drop=1)
        deco.carpet_runner(f, 1, kz - 1, W - 2, kz - 1, y0, color="red", border=None)
    for x in (2, W - 3):
        TK.bracket_flag(f, x, 2 * sh + 1, -1, "north", "kingdom", 2.5, 1.5)
    return f, W, D
