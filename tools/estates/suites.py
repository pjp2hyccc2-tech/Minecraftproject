"""The dearer rooms of the Starlight Manor (星灯りの館): the canal-view room, the library room, the star tower room and
the grand suite (the garret is in rooms.py). Each is a separate design with its own view out of the windows.

Storage names grow from room to room (see rooms.check_storage): a bigger room has every piece of the smaller ones,
so moving up keeps everything exactly where it was, labels included.
  canal    chest 1-5   barrel 1-4   shelf 1-3    stand 1-2  frame 1-4
  library  chest 1-6   barrel 1-5   shelf 1-12   stand 1-2  frame 1-4
  tower    chest 1-8   barrel 1-6   shelf 1-12   stand 1-3  frame 1-6
  grand    chest 1-12  barrel 1-10  shelf 1-16   stand 1-6  frame 1-10
Every room has a sorting desk (the ledger), a bed, a fireplace, lamps and rugs; the way out is its room door.
"""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital.districts import _furnish as FU
from capital.districts import _gimmick_kit as GK
from capital.districts import _town_kit as TK
from capital.noise import hash2

from . import kit
from . import rooms as R

FY = R.FY
AIR = R.AIR
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}


# ============================================================================ shared pieces
def parquet(w, x0, x1, z0, z1, y, a="dark_oak_planks", b="spruce_planks"):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            w.set(x, y, z, B.full(a if (x + 2 * z) % 4 < 2 else b))


def room_box(w, x0, x1, z0, z1, fy, height, wall="white_plaster", base="spruce", ceiling="dark_oak_planks",
             floor=("dark_oak_planks", "spruce_planks"), beams="x", outer=True):
    """A room: interior x0..x1, z0..z1, floor block at fy, air up to fy + height, ceiling above; walls one block
    thick around with a plank wainscot and a moulding (stripped log) under plaster."""
    plaster = R.F(wall)
    if outer:
        for y in range(fy + 1, fy + height + 1):
            for x in range(x0 - 1, x1 + 2):
                for z in (z0 - 1, z1 + 1):
                    w.set(x, y, z, B.full(f"{base}_planks") if y == fy + 1 else
                          B.log("dark_oak", "x", stripped=True) if y == fy + 2 else plaster)
            for z in range(z0, z1 + 1):
                for x in (x0 - 1, x1 + 1):
                    w.set(x, y, z, B.full(f"{base}_planks") if y == fy + 1 else
                          B.log("dark_oak", "z", stripped=True) if y == fy + 2 else plaster)
    w.fill(x0, fy + 1, z0, x1, fy + height, z1, AIR)
    parquet(w, x0 - 1, x1 + 1, z0 - 1, z1 + 1, fy, *floor)
    w.fill(x0 - 1, fy + height + 1, z0 - 1, x1 + 1, fy + height + 1, z1 + 1, B.full(ceiling))
    if beams == "x":
        for z in range(z0 + 1, z1, 3):
            for x in range(x0, x1 + 1):
                w.set(x, fy + height, z, B.log("dark_oak", "x"))
    elif beams == "z":
        for x in range(x0 + 1, x1, 3):
            for z in range(z0, z1 + 1):
                w.set(x, fy + height, z, B.log("dark_oak", "z"))


def window(w, x0, z0, x1, z1, y0, y1, outside, sill=True, glass="leaded_glass_pane"):
    """Glass in a wall from (x0, z0) to (x1, z1) (a line), rows y0..y1; ``outside`` is the side the sill sticks out."""
    dx, dz = STEP[outside]
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(y0, y1 + 1):
                w.set(x, y, z, B.S(glass))
            if sill:
                w.set(x + dx, y0 - 1, z + dz, B.stairs("spruce", OPP[outside], "top"))


def fireplace(w, x, z, into, fy, top, stone="bricks", hearth="polished_blackstone_bricks"):
    """A fireplace on a wall: (x, z) is the wall cell behind the fire, ``into`` the room side. The chimney breast
    (three wide) stands one block into the room up to ``top``; the fire burns in a recess in the wall, a mantel shelf
    with candles runs over the mouth."""
    dx, dz = STEP[into]
    sx, sz = STEP[CW[into]]
    for k in (-1, 0, 1):
        bx, bz = x + dx + sx * k, z + dz + sz * k
        for y in range(fy + 1, top + 1):
            w.set(bx, y, bz, B.full(stone))
        w.set(bx, fy, bz, B.full(hearth))
        w.set(bx + dx, fy, bz + dz, B.full(hearth))
    # the mouth: open in front, the fire in the wall recess, a lintel of upside-down stairs
    w.set(x + dx, fy + 1, z + dz, AIR)
    w.set(x, fy + 1, z, B.S("campfire", facing=into, lit=True, signal_fire=False))
    w.set(x, fy + 2, z, B.full(stone))
    w.set(x + dx, fy + 2, z + dz, B.stairs("stone_brick", OPP[into], "top"))
    for k in (-1, 1):
        w.set(x + dx + sx * k, fy + 1, z + dz + sz * k, B.full("chiseled_stone_bricks"))
    # mantel shelf with candles and a clock-like pot
    for k in (-1, 0, 1):
        mx, mz = x + 2 * dx + sx * k, z + 2 * dz + sz * k
        w.set(mx, fy + 3, mz, B.slab("dark_oak", "top"))
    w.set(x + 2 * dx - sx, fy + 4, z + 2 * dz - sz, B.candle(3, "white"))
    w.set(x + 2 * dx + sx, fy + 4, z + 2 * dz + sz, B.candle(2, "red"))
    w.set(x + 2 * dx, fy + 4, z + 2 * dz, B.S("decorated_pot"))
    # a fender of iron bars in front of the hearth
    w.set(x + 2 * dx, fy + 1, z + 2 * dz, AIR)


def four_poster(w, x, z, head, fy, color="red", canopy="red", wood="dark_oak", double=True):
    """A bed whose head lies towards ``head``, foot at (x, z) (double: a second bed clockwise of it), posts at the
    head corners and the foot corners, and a cloth canopy over it at fy + 4."""
    hx, hz = STEP[head]
    sx, sz = STEP[CW[head]]
    width = 2 if double else 1
    for i in range(width):
        foot, top = B.bed_states(color, head)
        w.set(x + sx * i, fy + 1, z + sz * i, foot)
        w.set(x + sx * i + hx, fy + 1, z + sz * i + hz, top)
    posts = [(x - sx, z - sz), (x + sx * width, z + sz * width), (x - sx + hx, z - sz + hz),
             (x + sx * width + hx, z + sz * width + hz)]
    for (px, pz) in posts:
        for y in range(fy + 1, fy + 4):
            w.set(px, y, pz, B.fence(wood))
    for i in range(-1, width + 1):
        for j in (0, 1):
            w.set(x + sx * i + hx * j, fy + 4, z + sz * i + hz * j, B.wool(canopy))
    # the valance: carpet-trimmed edge over the foot
    for i in range(-1, width + 1):
        w.set(x + sx * i - hx, fy + 4, z + sz * i - hz, B.slab(wood, "top"))


def stands_and_frames(w, s, stands, frames):
    for (x, z, yaw) in stands:
        kit.stand(w, s, x, FY, z, yaw=yaw)
    for (x, y, z, facing) in frames:
        kit.frame(w, s, x, y, z, facing)


def run(w, s, cells, y, facing, pattern):
    """Storage along a wall: ``pattern`` c = labelled chest, b = barrel, s = named shelf, k = plain bookshelf,
    w = wardrobe (two blocks of planks), - = nothing; one letter per cell."""
    for (x, z), p in zip(cells, pattern):
        if p == "c":
            kit.chest(w, s, x, y, z, facing)
        elif p == "b":
            kit.barrel(w, s, x, y, z, facing)
        elif p == "s":
            kit.shelf(w, s, x, y, z, facing)
        elif p == "k":
            w.set(x, y, z, B.S("bookshelf"))


def line(x0, z0, x1, z1):
    if x0 == x1:
        return [(x0, z) for z in (range(z0, z1 + 1) if z1 >= z0 else range(z0, z1 - 1, -1))]
    return [(x, z0) for x in (range(x0, x1 + 1) if x1 >= x0 else range(x0, x1 - 1, -1))]


def inward(x, z):
    """The direction from a cell on a ring towards its centre (for things facing into a round room)."""
    if abs(x) >= abs(z):
        return "west" if x > 0 else "east"
    return "north" if z > 0 else "south"


def sconce_pair(w, cells, y, out_dir):
    for (x, z) in cells:
        deco.sconce(w, x, y, z, out_dir)


# ============================================================================ the views
def canal_view(w, x_face, z0, z1, seed=0):
    """West of a canal-side facade at x = x_face: our quay, the Grand Canal, the far quay with lamps, a row of tall
    houses facing us, an arched footbridge, candle lanterns floating on the water, towers beyond."""
    water_y = 2
    TK.pave(w, x_face - 2, z0 - 30, x_face + 20, z1 + 30, TK.COBBLE_MIX, seed=seed, y=water_y + 1, only_ground=False)
    w.fill(x_face - 2, -2, z0 - 30, x_face + 20, water_y, z1 + 30, B.full("stone_bricks"))
    w.fill(x_face - 16, -2, z0 - 30, x_face - 3, -2, z1 + 30, B.full("gravel"))
    w.fill(x_face - 16, -1, z0 - 30, x_face - 3, water_y, z1 + 30, B.S("water"))
    for x in (x_face - 2, x_face - 17):
        w.fill(x, -2, z0 - 30, x, water_y + 1, z1 + 30, B.full("stone_bricks"))
        for z in range(z0 - 30, z1 + 31, 4):
            w.set(x, water_y, z, B.full("mossy_stone_bricks"))
    TK.pave(w, x_face - 24, z0 - 30, x_face - 17, z1 + 30, TK.COBBLE_MIX, seed=seed + 1, y=water_y + 1,
            only_ground=False)
    w.fill(x_face - 24, -2, z0 - 30, x_face - 17, water_y, z1 + 30, B.full("stone_bricks"))
    for z in range(z0 - 24, z1 + 25, 8):
        deco.lamp_post(w, x_face - 19, water_y + 1, z, height=4)
    rows = TK.row(w, x_face - 40, z0 - 30, x_face - 21, z1 + 30, "east", seed=seed + 7, min_w=5, max_w=8,
                  interior=False, floors=3, inset=3)
    for (rect, spec) in rows:
        pass
    # the footbridge: an arch over the water at z = z1 + 9
    bz = z1 + 9
    for x in range(x_face - 18, x_face - 1):
        d = abs(x - (x_face - 9.5))
        deck = water_y + 4 - int(d * d / 18.0)
        for z in (bz - 1, bz, bz + 1):
            w.fill(x, max(water_y + 1, deck - 1), z, x, deck, z, B.full("stone_bricks"))
            w.set(x, deck + 1, z, B.S("cobbled_paving") if z == bz else B.wall("stone_brick"))
    for (x, z) in ((x_face - 6, z0 + 1), (x_face - 12, z0 + 6), (x_face - 9, z1 - 2), (x_face - 5, z1 + 4),
                   (x_face - 13, z0 - 5)):
        GK.floating_lantern(w, x, water_y + 1, z)
    R._tower(w, x_face - 62, z0 - 10, 4, 34, "royal_roof_tiles", seed + 3, pennant=True)
    R._tower(w, x_face - 70, z1 + 14, 3, 40, "pale_roof_tiles", seed + 5)


def facade(w, x_face, z0, z1, y_top, seed=0, windows_at=()):
    """The manor's west face below and around a room: stone base, half-timbered plaster storeys with shuttered
    windows, a cornice; the room's own wall is built by the room."""
    for y in range(water_y_quay(), y_top + 1):
        for z in range(z0, z1 + 1):
            if w.get(x_face, y, z) == AIR:
                w.set(x_face, y, z, B.full("stone_bricks") if y <= 7 else R.F("white_plaster"))
    for z in range(z0, z1 + 1, 3):
        for y in range(8, y_top + 1):
            if w.get(x_face, y, z) == R.F("white_plaster"):
                w.set(x_face, y, z, B.log("dark_oak", "y"))
    for (z, y) in windows_at:
        w.set(x_face, y, z, B.S("leaded_glass_pane"))
        w.set(x_face, y + 1, z, B.S("leaded_glass_pane"))
        w.set(x_face - 1, y - 1, z, B.stairs("spruce", "east", "top"))


def water_y_quay():
    return 3


def garden_view(w, x_face, z0, z1, seed=0):
    """West of the facade: a walled garden of the manor (lawn, gravel paths, a fountain, flower beds, trees), a
    cloister wall with an arcade beyond and the town's towers above it."""
    w.fill(x_face - 26, -3, z0 - 14, x_face - 1, 0, z1 + 14, B.full("dirt"))
    w.fill(x_face - 26, 0, z0 - 14, x_face - 1, 0, z1 + 14, B.S("grass_block", snowy=False))
    cz = (z0 + z1) // 2
    for x in range(x_face - 24, x_face):
        w.set(x, 0, cz, B.S("gravel"))
    for z in range(z0 - 12, z1 + 13):
        w.set(x_face - 12, 0, z, B.S("gravel"))
    deco.flower_bed(w, x_face - 22, z0 - 10, x_face - 15, z0 - 4, 0, seed=seed)
    deco.flower_bed(w, x_face - 9, z1 + 4, x_face - 3, z1 + 10, 0, seed=seed + 1)
    deco.flower_bed(w, x_face - 22, z1 + 4, x_face - 15, z1 + 10, 0, seed=seed + 2)
    for (x, z, k) in ((x_face - 6, z0 - 8, "oak"), (x_face - 20, cz + 2, "birch"), (x_face - 18, z1 + 12, "oak")):
        deco.tree_oak(w, x, 1, z, seed=seed + x, kind=k)
    deco.tree_cypress(w, x_face - 3, 1, z0 - 12, seed=seed + 9)
    deco.tree_cypress(w, x_face - 3, 1, z1 + 12, seed=seed + 10)
    # the fountain where the paths cross
    fx, fz = x_face - 12, cz
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                w.set(fx + dx, 1, fz + dz, B.slab("smooth_castle_stone", "bottom"))
            else:
                w.set(fx + dx, 0, fz + dz, B.S("water"))
    w.set(fx, 1, fz, B.full("smooth_castle_stone"))
    w.set(fx, 2, fz, B.S("fountain_spray", power="1", facing="up"))
    # the cloister wall beyond with an arcade
    wx = x_face - 26
    for z in range(z0 - 14, z1 + 15):
        for y in range(1, 9):
            w.set(wx, y, z, B.full("castle_bricks"))
        if (z - z0) % 4 == 0:
            w.fill(wx, 1, z, wx, 4, z + 1, AIR)
            w.set(wx, 5, z, B.stairs("castle_brick", "north", "top"))
            w.set(wx, 5, z + 1, B.stairs("castle_brick", "south", "top"))
        w.set(wx, 9, z, B.slab("terracotta_roof_tile", "bottom"))
    R._tower(w, x_face - 44, z0 - 8, 4, 32, "royal_roof_tiles", seed + 3, pennant=True)
    R._tower(w, x_face - 52, z1 + 10, 3, 38, "pale_roof_tiles", seed + 5)
    TK.row(w, x_face - 42, z0 - 20, x_face - 30, z1 + 20, "east", seed=seed + 4, min_w=6, max_w=9,
           interior=False, floors=2, inset=1)


def rooftops_view(w, cx, cz, radius, seed=0):
    """All round a tower: the town's roofs below (rows of houses in four directions), the canal glinting to the west,
    the castle's towers to the north-east, hills far off."""
    TK.pave(w, cx - radius - 30, cz - radius - 30, cx + radius + 30, cz + radius + 30, TK.LANE_MIX, seed=seed, y=0,
            only_ground=False)
    TK.row(w, cx - radius - 26, cz - radius - 4, cx - radius - 8, cz + radius + 4, "east", seed=seed + 1,
           min_w=5, max_w=8, interior=False, floors=3, inset=2)
    TK.row(w, cx + radius + 8, cz - radius - 4, cx + radius + 26, cz + radius + 4, "west", seed=seed + 2,
           min_w=5, max_w=8, interior=False, floors=3, inset=2)
    TK.row(w, cx - radius - 4, cz - radius - 26, cx + radius + 4, cz - radius - 8, "south", seed=seed + 3,
           min_w=5, max_w=8, interior=False, floors=2, inset=2)
    TK.row(w, cx - radius - 4, cz + radius + 8, cx + radius + 4, cz + radius + 26, "north", seed=seed + 4,
           min_w=5, max_w=8, interior=False, floors=2, inset=2)
    R._tower(w, cx + 40, cz - 36, 5, 44, "royal_roof_tiles", seed + 5, pennant=True)
    R._tower(w, cx + 50, cz - 22, 4, 36, "royal_roof_tiles", seed + 6)
    R._tower(w, cx - 46, cz + 30, 3, 30, "pale_roof_tiles", seed + 7, pennant=True)
    for x in range(cx - radius - 44, cx - radius - 30):
        for z in range(cz - 40, cz + 41):
            w.set(x, 0, z, B.S("water"))


# ============================================================================ tier 2: the canal-view room
def canal(w):
    """運河の見える部屋 — a panelled room on the manor's canal front: a bay window with a window seat and the sorting
    desk over the Grand Canal (the far quay, its lamps and houses, the footbridge, lanterns floating on the water),
    a four-poster bed, a fireplace, a wardrobe, five labelled chests, four barrels, three book shelves, two armour
    stands and four weapon frames."""
    s = kit.Storage()
    seed = 211
    x0, x1, z0, z1 = -5, 5, -4, 4
    height = 5
    canal_view(w, x0 - 1, z0 - 6, z1 + 6, seed=seed)
    # the house around the room: storeys below, the floor above, a roof
    for y in range(water_y_quay() + 1, FY):
        for x in range(x0 - 1, x1 + 2):
            for z in (z0 - 1, z1 + 1):
                w.set(x, y, z, B.full("stone_bricks") if y <= 7 else R.F("white_plaster"))
        for z in range(z0 - 1, z1 + 2):
            for x in (x0 - 1, x1 + 1):
                w.set(x, y, z, B.full("stone_bricks") if y <= 7 else R.F("white_plaster"))
    facade(w, x0 - 1, z0 - 1, z1 + 1, FY - 1, seed, windows_at=[(z, y) for z in (z0 + 1, z1 - 1) for y in (5, 9)])
    room_box(w, x0, x1, z0, z1, FY, height, base="dark_oak", beams="x")
    for y in range(FY + height + 2, FY + height + 6):
        d = y - (FY + height + 2)
        for x in range(x0 - 2 + d, x1 + 3 - d):
            for z in range(z0 - 2, z1 + 3):
                w.set(x, y, z, B.full("terracotta_roof_tiles") if x in (x0 - 2 + d, x1 + 2 - d) else AIR)
    for z in range(z0 - 2, z1 + 3):
        for d in range(4):
            y = FY + height + 2 + d
            w.set(x0 - 2 + d, y, z, B.stairs("terracotta_roof_tile", "east"))
            w.set(x1 + 2 - d, y, z, B.stairs("terracotta_roof_tile", "west"))
    # the bay window: two deep, five wide, over the quay
    bx = x0 - 1
    for z in range(-2, 3):
        for y in range(FY + 1, FY + height + 1):
            w.set(bx, y, z, AIR)
        w.set(bx, FY, z, B.full("dark_oak_planks"))
        w.set(bx - 1, FY, z, B.full("dark_oak_planks"))
        w.set(bx - 1, FY - 1, z, B.stairs("stone_brick", "east", "top"))
        w.set(bx, FY + height + 1, z, B.full("dark_oak_planks"))
        w.set(bx - 1, FY + height + 1, z, B.slab("terracotta_roof_tile", "bottom"))
    window(w, bx - 2, -2, bx - 2, 2, FY + 2, FY + 4, "west", sill=False)
    for z in range(-2, 3):
        w.set(bx - 2, FY + 1, z, B.full("dark_oak_planks"))
        w.set(bx - 2, FY + 5, z, B.full("dark_oak_planks"))
        w.set(bx - 2, FY, z, B.full("dark_oak_planks"))
        w.set(bx - 3, FY, z, B.stairs("dark_oak", "east", "top"))
    for z in (-3, 3):
        for y in range(FY, FY + 6):
            w.set(bx - 1, y, z, B.full("dark_oak_planks") if y in (FY, FY + 5) else
                  B.S("leaded_glass_pane") if FY + 2 <= y <= FY + 4 else B.full("dark_oak_planks"))
            w.set(bx - 2, y, z, B.log("dark_oak", "y"))
    # window seats at both ends of the bay, cushions on them
    for z in (-2, 2):
        w.set(bx - 1, FY + 1, z, B.stairs("spruce", "east", "bottom"))
        w.set(bx, FY + 1, z, B.stairs("spruce", "north" if z < 0 else "south"))
    # the sorting desk in the bay, facing the room, with the canal behind it
    kit.sorting_desk(w, bx - 1, FY + 1, 0, "east")
    deco.chair(w, bx, FY, 0, faces="west")
    w.set(bx - 1, FY + 1, -1, B.potted("blue_orchid"))
    w.set(bx - 1, FY + 1, 1, B.potted("white_tulip"))
    deco.light_shaft(w, bx, FY + 4, 0, "east", length=7.0, width=3.0, height=2.6, pitch=26.0)
    # the way out: the room door in the east wall
    kit.exit_door(w, x1 + 1, FY + 1, 0, "west")
    kit.entry(w, x1 - 1, FY + 1, 0, 90.0)
    deco.sconce(w, x1, FY + 3, -2, "west")
    deco.sconce(w, x1, FY + 3, 2, "west")
    # the bed: a four-poster in the north-east corner, head north
    four_poster(w, 2, z0 + 1, "north", FY, color="light_blue", canopy="blue")
    w.set(1, FY + 1, z0, AIR)
    kit.barrel(w, s, 0, FY + 1, z0, "up")                       # barrel.1: the nightstand
    w.set(0, FY + 2, z0, B.lantern())
    # the fireplace on the south wall, armchairs before it
    fireplace(w, 0, z1 + 1, "north", FY, FY + height)
    deco.chair(w, -2, FY, z1 - 3, faces="south")
    deco.chair(w, 2, FY, z1 - 3, faces="south")
    FU.rug(w, -1, z1 - 3, 1, z1 - 2, FY, field="blue", border="light_blue")
    # storage: chests and barrels along the north wall's west half, shelves above them
    run(w, s, line(x0, z0, -2, z0), FY + 1, "south", "cccb")        # chest 1-3, barrel 2
    run(w, s, line(x0, z0, -2, z0), FY + 2, "south", "skks")        # shelf 1, 2
    run(w, s, line(x0, z1, -3, z1), FY + 1, "north", "cbc")         # chest 4, barrel 3, chest 5
    kit.barrel(w, s, 3, FY + 1, z1, "north")                       # barrel 4
    kit.shelf(w, s, 4, FY + 1, z1, "north")                        # shelf 3
    w.set(-4, FY + 2, z1, B.candle(2, "white"))
    w.set(3, FY + 2, z1, B.potted("fern"))
    FU.wardrobe(w, -1, FY, z0, "south", wood="dark_oak")
    # the armour stands by the door, weapon frames on the east wall
    stands_and_frames(w, s, [(4, z1 - 1, -90.0), (4, z0 + 3, -90.0)],
                      [(x1, FY + 3, -3, "west"), (x1, FY + 3, 3, "west"), (x0, FY + 4, z0, "south"),
                       (-3, FY + 4, z0, "south")])
    # rug, chandelier, paintings, plants
    FU.rug(w, -3, -2, 2, 2, FY, field="red", border="yellow")
    deco.chandelier(w, 0, FY + height, 0, drop=1)
    FU.painting(w, -2, FY + 3, z1, "north", variant="wanderer")
    deco.hanging_lantern(w, bx - 1, FY + height, 0, drop=1)
    w.set(x1, FY + 1, z0 + 2, B.potted("azalea_bush"))
    return s


# ============================================================================ tier 3: the library room
def library(w):
    """書斎の間 — a two-storey library: walls of books with twelve named shelves, a gallery with a railing round three
    sides reached by a wooden stair, two tall windows on the manor's walled garden (its fountain, flower beds and
    trees, the cloister beyond), a fireplace, reading chairs, the sorting desk under the windows, a bed in an alcove,
    six chests, five barrels, two armour stands and four frames."""
    s = kit.Storage()
    seed = 307
    x0, x1, z0, z1 = -6, 6, -5, 5
    height = 9
    gy = FY + 5                                                     # the gallery floor
    garden_view(w, x0 - 1, z0, z1, seed=seed)
    for y in range(1, FY):
        for x in range(x0 - 1, x1 + 2):
            for z in (z0 - 1, z1 + 1):
                w.set(x, y, z, B.full("castle_bricks") if y <= 4 else R.F("white_plaster"))
        for z in range(z0 - 1, z1 + 2):
            for x in (x0 - 1, x1 + 1):
                w.set(x, y, z, B.full("castle_bricks") if y <= 4 else R.F("white_plaster"))
    room_box(w, x0, x1, z0, z1, FY, height, wall="white_plaster", base="dark_oak", beams="z")
    facade(w, x0 - 1, z0 - 1, z1 + 1, FY + height, seed, windows_at=[(z, y) for z in (z0 + 1, 0, z1 - 1)
                                                                     for y in (6, 9)])
    # roof
    for d in range(0, 8):
        y = FY + height + 2 + d
        for x in range(x0 - 2, x1 + 3):
            for z in (z0 - 2 + d, z1 + 2 - d):
                w.set(x, y, z, B.stairs("royal_roof_tile", "south" if z < 0 else "north"))
            for z in range(z0 - 1 + d, z1 + 2 - d):
                w.set(x, y, z, AIR)
        if z0 - 2 + d >= z1 + 2 - d:
            break
    for x in range(x0 - 2, x1 + 3):
        w.set(x, FY + height + 1, z0 - 2, B.slab("royal_roof_tile", "top"))
        w.set(x, FY + height + 1, z1 + 2, B.slab("royal_roof_tile", "top"))
    # two tall windows on the west wall with a round light between
    for z in (-3, 3):
        window(w, x0 - 1, z - 1, x0 - 1, z + 1, FY + 2, FY + 7, "west")
        w.set(x0 - 1, FY + 8, z, B.S("leaded_glass_pane"))
        deco.light_shaft(w, x0, FY + 6, z, "east", length=9.0, width=2.6, height=3.0, pitch=30.0)
    # the gallery: three rows along the north, east and south walls at gy, a balustrade on the inner row with a gap
    # where the stair arrives (x -5..-4 on the south side)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if z <= z0 + 2 or z >= z1 - 2 or x >= x1 - 2:
                w.set(x, gy, z, B.slab("spruce", "top"))
    for x in range(x0, x1 - 2):
        w.set(x, gy + 1, z0 + 2, B.fence("dark_oak"))
        if x not in (-5, -4):
            w.set(x, gy + 1, z1 - 2, B.fence("dark_oak"))
    for z in range(z0 + 2, z1 - 1):
        w.set(x1 - 2, gy + 1, z, B.fence("dark_oak"))
    for (x, z) in ((x1 - 2, z0 + 2), (x1 - 2, z1 - 2), (x0 + 3, z0 + 2), (x0 + 3, z1 - 2)):
        for y in range(FY + 1, gy):
            w.set(x, y, z, B.log("dark_oak", "y"))
    # the stair up to the gallery inside the well, rising west along z = 2, arriving at the balustrade's gap
    for k in range(5):
        w.set(-k, FY + 1 + k, z1 - 3, B.stairs("spruce", "west"))
    # books: the gallery walls are bookcases (plain books with the named shelves among them)
    run(w, s, line(x0, z0 - 1, x1, z0 - 1), gy + 1, "south", "kskkskkskkskk")      # shelf 1-4
    run(w, s, line(x0, z0 - 1, x1, z0 - 1), gy + 2, "south", "kkkkkkkkkkkkk")
    run(w, s, line(x1 + 1, z0, x1 + 1, z1), gy + 1, "west", "kskskskskkk")         # shelf 5-8
    run(w, s, line(x1 + 1, z0, x1 + 1, z1), gy + 2, "west", "kkkkkkkkkkk")
    run(w, s, line(x0, z1 + 1, x1, z1 + 1), gy + 1, "north", "kskskskkkkkkk")      # shelf 9-11
    run(w, s, line(x0, z1 + 1, x1, z1 + 1), gy + 2, "north", "kkkkkkkkkkkkk")
    deco.hanging_lantern(w, -3, FY + height, z0 + 1, drop=1)
    deco.hanging_lantern(w, 3, FY + height, z1 - 1, drop=1)
    # downstairs: the sorting desk under the windows, chests and barrels along the north wall, shelf 12
    kit.sorting_desk(w, x0, FY + 1, 0, "east")
    deco.chair(w, x0 + 1, FY, 0, faces="west")
    w.set(x0, FY + 1, -1, B.potted("fern"))
    w.set(x0, FY + 1, 1, B.lantern())
    run(w, s, line(x0 + 1, z0, x0 + 6, z0), FY + 1, "south", "ccccbb")     # chest 1-4, barrel 1-2
    run(w, s, line(x0 + 1, z0, x0 + 6, z0), FY + 2, "south", "kskkkk")     # shelf 12
    run(w, s, line(x0 + 7, z0, x1, z0), FY + 1, "south", "cbcbb")          # chest 5, barrel 3, chest 6, barrel 4-5
    run(w, s, line(x0 + 7, z0, x1, z0), FY + 2, "south", "kkkkk")
    # the fireplace in the east wall, reading chairs and a low table before it
    kit.exit_door(w, x1 + 1, FY + 1, 3, "west")
    kit.entry(w, x1 - 1, FY + 1, 3, 90.0)
    fireplace(w, x1 + 1, -1, "west", FY, gy - 1, stone="stone_bricks")
    deco.chair(w, x1 - 4, FY, -2, faces="east")
    deco.chair(w, x1 - 4, FY, 0, faces="east")
    w.set(x1 - 4, FY + 1, -1, B.slab("dark_oak", "top"))
    w.set(x1 - 4, FY + 2, -1, B.candle(3, "white"))
    FU.rug(w, x1 - 5, -3, x1 - 2, 1, FY, field="green", border="lime")
    # the bed in an alcove on the south side under the gallery, curtains of wool either side
    four_poster(w, -4, z1 - 1, "south", FY, color="green", canopy="green", double=False)
    stands_and_frames(w, s, [(x0 + 1, z1 - 2, 0.0), (1, z1 - 1, 180.0)],
                      [(x0 + 1, FY + 3, z0, "south"), (x0 + 4, FY + 3, z0, "south"), (x1, FY + 3, z0 + 1, "west"),
                       (x1, FY + 3, z1 - 1, "west")])
    # the reading table in the middle, lamps, a globe
    deco.table(w, -2, -1, 0, 0, FY, top="dark_oak", cloth="green")
    w.set(-2, FY + 2, 0, B.candle(2, "yellow"))
    for (x, z) in ((-3, -1), (1, -1), (-3, 0), (1, 0)):
        deco.chair(w, x, FY, z, faces="east" if x < 0 else "west")
    w.set(2, FY + 1, -3, B.fence("dark_oak"))
    w.set(2, FY + 2, -3, B.full("light_blue_terracotta"))
    deco.chandelier(w, 0, FY + height, 0, drop=2)
    FU.rug(w, -4, -2, 2, 1, FY, field="red", border="orange")
    return s


# ============================================================================ tier 4: the star tower room
def tower(w):
    """星見の塔の部屋 — a round tower room on two floors, windows all round over the roofs of the capital: below the bed,
    the fireplace, the sorting desk and the chests in niches round the wall; a spiral stair up to the observatory
    with its brass telescope under a glass lantern roof, the shelves of star books and a balcony ring."""
    s = kit.Storage()
    seed = 401
    r = 7                                                           # wall radius
    up = FY + 6                                                     # the upper floor
    rooftops_view(w, 0, 0, r + 2, seed=seed)

    def ring(d):
        return d <= r + 0.5

    # the tower shaft from the ground, stone below and plaster above
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            d = math.hypot(x, z)
            if d > r + 0.5:
                continue
            wall = d > r - 0.5
            for y in range(1, up + 7):
                if wall:
                    w.set(x, y, z, B.full("castle_bricks") if y < FY or y in (FY + 1, up) else R.F("white_plaster"))
                elif y < FY:
                    w.set(x, y, z, B.full("castle_bricks") if y in (1, FY - 1) else AIR)
                else:
                    w.set(x, y, z, AIR)
            if not wall:
                w.set(x, FY, z, B.full("dark_oak_planks" if (x + z) % 2 else "spruce_planks"))
                w.set(x, up, z, B.full("spruce_planks"))
    # windows: eight tall ones round each floor
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x, z = int(round(math.cos(a) * r)), int(round(math.sin(a) * r))
        for y in range(FY + 2, FY + 5):
            w.set(x, y, z, B.S("leaded_glass_pane"))
        for y in range(up + 1, up + 5):
            w.set(x, y, z, B.S("leaded_glass_pane"))
    # the observatory's ceiling: a night sky painted in blue with little lights for stars; a cone roof over it
    for x in range(-r, r + 1):
        for z in range(-r, r + 1):
            d = math.hypot(x, z)
            if d <= r + 0.5:
                star = d <= r - 1.5 and hash2(x * 7 + 3, z * 5 + 1, seed) < 0.09
                w.set(x, up + 6, z, B.full("castle_bricks") if d > r - 0.5 else
                      B.S("glowstone") if star else B.wool("blue"))
    for k in range(1, r + 2):
        rr = r + 1 - k
        for x in range(-r - 1, r + 2):
            for z in range(-r - 1, r + 2):
                d = math.hypot(x, z)
                if rr - 0.5 < d <= rr + 0.5:
                    w.set(x, up + 6 + k, z, B.full("royal_roof_tiles") if rr > 1 else B.full("gilded_trim"))
    w.set(0, up + 6 + r + 2, 0, B.S("gilded_finial"))
    # the balcony ring round the upper floor
    for x in range(-r - 2, r + 3):
        for z in range(-r - 2, r + 3):
            d = math.hypot(x, z)
            if r + 0.5 < d <= r + 2.0:
                w.set(x, up, z, B.slab("smooth_castle_stone", "top"))
                if d > r + 1.2:
                    w.set(x, up + 1, z, B.wall("castle_brick"))
    for (x, z) in ((r, 0), (-r, 0)):
        w.set(x, up + 1, z, AIR)
        w.set(x, up + 2, z, AIR)
    # the spiral stair round a centre post up through the upper floor (its top step is part of the upper floor)
    stair = [(2, 0, "north"), (2, -1, "north"), (1, -2, "west"), (0, -2, "west"), (-1, -2, "west"),
             (-2, -1, "south")]
    for k, (x, z, f) in enumerate(stair):
        w.set(x, FY + 1 + k, z, B.stairs("spruce", f))
        for yy in range(FY + 2 + k, up + 3):
            w.set(x, yy, z, AIR)
    for y in range(FY + 1, up):
        w.set(0, y, 0, B.log("dark_oak", "y"))
    for (x, z) in ((1, 0), (1, -1), (0, -1), (-1, -1)):
        w.set(x, up + 1, z, B.fence("dark_oak"))
    # downstairs: the door, the fireplace opposite, the bed, the desk and storage in the ring
    kit.exit_door(w, 0, FY + 1, r, "north")
    kit.entry(w, 0, FY + 1, r - 2, 180.0)
    fireplace(w, 0, -r, "south", FY, up - 1)
    four_poster(w, -3, -2, "west", FY, color="purple", canopy="purple", double=True)
    kit.sorting_desk(w, 5, FY + 1, -3, "west")
    deco.chair(w, 4, FY, -3, faces="east")
    ground_ring = [(6, -1), (6, 1), (5, 3), (4, 4), (3, 5), (-3, 5), (-4, 4), (-5, 3), (-6, 1), (-6, -1)]
    for (x, z), p in zip(ground_ring, "ccbcbcbccb"):            # chest 1-6, barrel 1-4
        (kit.chest if p == "c" else kit.barrel)(w, s, x, FY + 1, z, inward(x, z))
    kit.chest(w, s, 5, FY + 1, -4, "west")                            # chest 7
    kit.chest(w, s, 2, FY + 1, 6, "north")                            # chest 8
    kit.barrel(w, s, -2, FY + 1, 6, "north")                          # barrel 5
    kit.barrel(w, s, 4, FY + 1, -5, "south")                          # barrel 6
    stands_and_frames(w, s, [(3, 3, 225.0), (-3, 3, 135.0), (5, 1, 270.0)],
                      [(6, FY + 3, -1, "west"), (6, FY + 3, 1, "west"), (-6, FY + 3, -1, "east"),
                       (-6, FY + 3, 1, "east"), (3, FY + 3, 6, "north"), (-3, FY + 3, 6, "north")])
    FU.rug(w, -2, 1, 2, 4, FY, field="purple", border="magenta")
    deco.hanging_lantern(w, 3, up - 1, 3, drop=1)
    deco.hanging_lantern(w, -3, up - 1, 3, drop=1)
    # upstairs: the observatory — the telescope on a stand at the centre-west, star books on shelves round the wall
    tx, tz = -3, 1
    w.set(tx, up + 1, tz, B.fence("dark_oak"))
    w.set(tx, up + 2, tz, B.full("gilded_trim"))
    w.set(tx + 1, up + 3, tz, B.S("lightning_rod", facing="east"))
    w.set(tx - 1, up + 2, tz, B.S("lightning_rod", facing="west"))
    w.set(tx, up + 3, tz, B.S("lightning_rod", facing="up"))
    deco.chair(w, tx - 1, up, tz + 1, faces="east")
    upper_ring = [(6, -2), (6, 2), (5, 4), (4, 5), (2, 6), (-2, 6), (-4, 5), (-5, 4), (-6, 2), (-6, -2), (-5, -4),
                  (-4, -5), (4, -5), (5, -4)]
    for i, (x, z) in enumerate(upper_ring):                       # shelf 1-12 under plain books
        if i < 12:
            kit.shelf(w, s, x, up + 1, z, inward(x, z))
        else:
            w.set(x, up + 1, z, B.S("bookshelf"))
        w.set(x, up + 2, z, B.S("bookshelf"))
    FU.rug(w, -2, -1, 2, 3, up, field="blue", border="light_blue")
    w.set(2, up + 1, 3, B.S("lectern", facing="south", has_book=False))
    w.set(3, up + 1, -2, B.S("cartography_table"))
    w.set(-2, up + 1, 4, B.potted("flowering_azalea_bush"))
    deco.hanging_lantern(w, 0, up + 5, 3, drop=1)
    return s


# ============================================================================ tier 5: the grand suite
def grand(w):
    """貴賓のスイート — the manor's finest rooms in a row along the canal front: a salon with a fireplace, sofas and a
    chandelier, the bedchamber with a great four-poster, the armoury (six armour stands, ten weapon frames), the vault
    of chests and barrels behind an iron gate, a library corner, and a balcony over the Grand Canal."""
    s = kit.Storage()
    seed = 503
    height = 6
    # rooms along z (west face on the canal at x = -8): salon z -6..2, bedchamber z 4..12, armoury z -14..-8,
    # vault behind the armoury (x 2..7, z -14..-8), library corner in the salon's east end
    x0, x1 = -7, 7
    canal_view(w, x0 - 1, -16, 14, seed=seed)
    for y in range(water_y_quay() + 1, FY):
        for x in range(x0 - 1, x1 + 2):
            for z in (-15, 14):
                w.set(x, y, z, B.full("stone_bricks") if y <= 7 else R.F("white_plaster"))
        for z in range(-15, 15):
            for x in (x0 - 1, x1 + 1):
                w.set(x, y, z, B.full("stone_bricks") if y <= 7 else R.F("white_plaster"))
    facade(w, x0 - 1, -15, 14, FY - 1, seed, windows_at=[(z, y) for z in range(-13, 13, 4) for y in (5, 9)])
    room_box(w, x0, x1, -14, 13, FY, height, wall="white_plaster", base="dark_oak", beams="x",
             floor=("dark_oak_planks", "cherry_planks"))
    # roof over the whole wing
    for d in range(0, 9):
        y = FY + height + 2 + d
        for z in range(-16, 16):
            w.set(x0 - 2 + d, y, z, B.stairs("royal_roof_tile", "east"))
            w.set(x1 + 2 - d, y, z, B.stairs("royal_roof_tile", "west"))
            for x in range(x0 - 1 + d, x1 + 2 - d):
                w.set(x, y, z, AIR)
        if x0 - 2 + d >= x1 + 2 - d:
            break
    # partition walls with arched openings: salon | bedchamber at z = 3, salon | armoury at z = -7
    for zp, gap in ((3, (-1, 1)), (-7, (-1, 1))):
        for x in range(x0, x1 + 1):
            for y in range(FY + 1, FY + height + 1):
                w.set(x, y, zp, B.full("dark_oak_planks") if y == FY + 1 else R.F("white_plaster"))
        for x in range(gap[0], gap[1] + 1):
            for y in range(FY + 1, FY + 4):
                w.set(x, y, zp, AIR)
        w.set(gap[0], FY + 4, zp, B.stairs("dark_oak", "east", "top"))
        w.set(gap[1], FY + 4, zp, B.stairs("dark_oak", "west", "top"))
        w.set(0, FY + 4, zp, B.full("chiseled_stone_bricks"))
    # the vault: a walled room in the armoury's east half behind an iron gate (bars)
    for z in range(-14, -7):
        for y in range(FY + 1, FY + height + 1):
            w.set(2, y, z, B.full("stone_bricks"))
    for y in range(FY + 1, FY + 4):
        w.set(2, y, -11, B.S("iron_bars", north=True, south=True, east=False, west=False, waterlogged=False))
    # tall windows on the canal front, the balcony before the salon
    for z in (-12, -9, -4, 0, 6, 10):
        window(w, x0 - 1, z, x0 - 1, z + 1, FY + 2, FY + 4, "west")
    for z in range(-3, 2):
        for y in range(FY + 1, FY + 4):
            w.set(x0 - 1, y, z, AIR)
    for z in range(-4, 3):
        for x in range(x0 - 4, x0 - 1):
            w.set(x, FY, z, B.slab("smooth_castle_stone", "top"))
            w.set(x, FY - 1, z, B.stairs("stone_brick", "east", "top") if x == x0 - 4 else B.full("stone_bricks"))
    for z in range(-4, 3):
        w.set(x0 - 4, FY + 1, z, B.wall("castle_brick"))
    for x in range(x0 - 3, x0 - 1):
        w.set(x, FY + 1, -4, B.wall("castle_brick"))
        w.set(x, FY + 1, 2, B.wall("castle_brick"))
    w.set(x0 - 4, FY + 2, -4, B.lantern())
    w.set(x0 - 4, FY + 2, 2, B.lantern())
    w.set(x0 - 3, FY + 1, -1, B.potted("red_tulip"))
    deco.light_shaft(w, x0, FY + 3, -1, "east", length=10.0, width=4.0, height=3.0, pitch=24.0)
    # ---- the salon (z -6..2): door, fireplace, sofas, chandelier, the sorting desk, library corner
    kit.exit_door(w, x1 + 1, FY + 1, -2, "west")
    kit.entry(w, x1 - 1, FY + 1, -2, 90.0)
    fireplace(w, -3, 3, "north", FY, FY + height, stone="smooth_castle_stone", hearth="polished_deepslate")
    for x in (-5, -4):
        w.set(x, FY + 1, -1, B.stairs("cherry", "west"))
    for z in (-3, 1):
        w.set(-3, FY + 1, z, B.stairs("cherry", "north" if z < 0 else "south"))
    FU.rug(w, -5, -3, -1, 1, FY, field="red", border="yellow")
    deco.table(w, -3, -1, -3, -1, FY, top="dark_oak")
    w.set(-3, FY + 2, -1, B.candle(3, "white"))
    kit.sorting_desk(w, 2, FY + 1, -6, "south")
    deco.chair(w, 2, FY, -5, faces="north")
    run(w, s, line(3, -6, 6, -6), FY + 1, "south", "cccc")                   # chest 1-4
    run(w, s, line(3, -6, 6, -6), FY + 2, "south", "ksks")                   # shelf 1-2
    run(w, s, line(x1, -5, x1, -4), FY + 1, "west", "bb")                    # barrel 1-2
    run(w, s, line(x1, 0, x1, 2), FY + 1, "west", "csc")                     # chest 5, shelf 3, chest 6
    run(w, s, line(x1, 0, x1, 2), FY + 2, "west", "sks")                     # shelf 4-5
    run(w, s, line(3, 2, 6, 2), FY + 1, "north", "bbss")                     # barrel 3-4, shelf 6-7
    run(w, s, line(3, 2, 6, 2), FY + 2, "north", "skkk")                     # shelf 8
    deco.chandelier(w, -2, FY + height, -1, drop=2)
    deco.chandelier(w, 3, FY + height, -2, drop=1)
    # ---- the bedchamber (z 4..12)
    four_poster(w, 2, 11, "south", FY, color="white", canopy="red")
    FU.wardrobe(w, x1, FY, 6, "west", wood="dark_oak")
    FU.wardrobe(w, x1, FY, 8, "west", wood="dark_oak")
    run(w, s, line(x0, 4, x0, 5), FY + 1, "east", "cc")                      # chest 7-8
    run(w, s, line(-6, 13, -3, 13), FY + 1, "north", "bsbs")                 # barrel 5, shelf 9, barrel 6, shelf 10
    run(w, s, line(-6, 13, -3, 13), FY + 2, "north", "ksks")                 # shelf 11-12
    FU.rug(w, -1, 5, 5, 9, FY, field="purple", border="magenta")
    deco.chandelier(w, 2, FY + height, 7, drop=1)
    w.set(-6, FY + 1, 9, B.potted("flowering_azalea_bush"))
    FU.painting(w, 0, FY + 3, 13, "north", variant="sunset")
    deco.chair(w, -5, FY, 8, faces="west")
    # ---- the armoury (x -7..1, z -14..-8): six stands in a row, ten frames on the walls
    stands_and_frames(w, s,
                      [(-6, -12, 0.0), (-4, -12, 0.0), (-2, -12, 0.0), (-6, -9, 180.0), (-4, -9, 180.0),
                       (-2, -9, 180.0)],
                      [(x0, FY + 3, -13, "east"), (x0, FY + 3, -10, "east"), (-5, FY + 3, -14, "south"),
                       (-3, FY + 3, -14, "south"), (-1, FY + 3, -14, "south"), (1, FY + 3, -13, "west"),
                       (1, FY + 3, -9, "west"), (-5, FY + 4, -14, "south"), (-3, FY + 4, -14, "south"),
                       (-1, FY + 4, -14, "south")])
    FU.rug(w, -6, -11, -1, -10, FY, field="gray", border="black")
    deco.hanging_lantern(w, -4, FY + height, -11, drop=1)
    # ---- the vault (x 3..7, z -14..-8): chests and barrels round the walls, shelves above
    run(w, s, line(3, -14, x1, -14), FY + 1, "south", "cc-cc")               # chest 9-12
    run(w, s, line(x1, -13, x1, -9), FY + 1, "west", "bbbb")                 # barrel 7-10
    run(w, s, line(3, -14, x1, -14), FY + 2, "south", "sksks")               # shelf 13-15
    run(w, s, line(3, -8, 5, -8), FY + 1, "north", "s")                      # shelf 16
    deco.hanging_lantern(w, 5, FY + height, -11, drop=1)
    return s
