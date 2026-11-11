"""The inn rooms (宿屋の館の客室): one design per tier, each a room of the inn's upper floors with a view of the town.

Every room sits in a wing of the inn whose ground is local y = 0 (a cobbled street, houses across it); the room's
floor block is at ``FY`` (players stand at FY + 1). The windows face west, towards the late-afternoon sun of the
estates dimension (fixed time 10000), so light comes in through them. The way out is the room door (exit) on the
east wall, players arrive just inside it facing the window.

Storage names must grow from tier to tier (a bigger room keeps every name of the smaller ones), so an upgrade puts
everything back where it was: see ``STORAGE`` and ``check_storage``.
"""
from __future__ import annotations

from capital import blocks as B
from capital import deco
from capital.districts import _furnish as FU
from capital.districts import _town_kit as TK
from capital.noise import hash2

from . import kit

FY = 12          # room floor block (players stand at FY + 1)
AIR = "minecraft:air"


def F(m):
    return TK.st_of(m)


# ============================================================================ the town outside the windows
def street_and_houses(w, x_street0, x_street1, z0, z1, seed=0):
    """A cobbled street (x_street0..x_street1) running north-south, a row of houses across it facing east, a second
    row behind them facing west, gardens and paving under everything, and a few distant towers."""
    TK.pave(w, -70, z0 - 12, x_street1, z1 + 12, TK.LANE_MIX, seed=seed, y=0, only_ground=False)
    TK.pave(w, x_street0, z0 - 12, x_street1, z1 + 12, TK.COBBLE_MIX, seed=seed + 1, y=0, only_ground=False)
    front_x1 = x_street0 - 1
    TK.row(w, front_x1 - 11, z0, front_x1, z1, "east", seed=seed + 11, min_w=5, max_w=8, interior=False, floors=3,
           inset=2)
    TK.row(w, front_x1 - 26, z0 + 3, front_x1 - 14, z1 - 3, "west", seed=seed + 23, min_w=6, max_w=9,
           interior=False, floors=2, inset=1)
    # distant landmarks above the roofs: a bell tower and a slender spire with a pennant
    _tower(w, front_x1 - 46, -14, 4, 30, "royal_roof_tiles", seed + 3)
    _tower(w, front_x1 - 58, 12, 3, 38, "pale_roof_tiles", seed + 5, pennant=True)


def _tower(w, cx, cz, r, height, roof, seed, pennant=False):
    wall = B.full("castle_bricks")
    for y in range(0, height):
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if max(abs(dx), abs(dz)) == r:
                    w.set(cx + dx, y, cz + dz, wall)
    for y in (height - 6, height - 5):
        for d in range(-r + 1, r):
            for (ax, az) in ((d, -r), (d, r), (-r, d), (r, d)):
                if abs(d) <= 1:
                    w.set(cx + ax, y, cz + az, AIR)
    w.fill(cx - r, height, cz - r, cx + r, height, cz + r, B.full("smooth_castle_stone"))
    for k in range(0, r + 3):
        rr = r - k * r // (r + 2)
        y = height + 1 + k * 2
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if max(abs(dx), abs(dz)) == rr:
                    w.set(cx + dx, y, cz + dz, B.full(roof))
                    w.set(cx + dx, y + 1, cz + dz, B.full(roof))
    top = height + 2 * (r + 3) + 1
    w.set(cx, top, cz, B.S("gilded_finial"))
    if pennant:
        TK.flag(w, cx, top + 1, cz, "east", "pennant", 3.0, 1.0)


# ============================================================================ the inn wing around a room
def inn_wing(w, x0, x1, z0, z1, eave_y, roof="terracotta_roof_tiles", seed=0):
    """The inn's wing: stone ground floor, half-timbered plaster upper floors up to ``eave_y`` with windows, and a
    gable roof whose ridge runs along z. Its west face is what the rooms look out of."""
    stone = B.full("stone_bricks")
    plaster = F("white_plaster")
    timber_y = B.log("dark_oak", "y")
    for y in range(0, eave_y + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                w.set(x, y, z, stone if y <= 4 else plaster)
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                w.set(x, y, z, stone if y <= 4 else plaster)
    # timber frame on the long faces: posts every 3 blocks, a beam at every floor
    for x in (x0, x1):
        for z in range(z0, z1 + 1):
            for y in range(5, eave_y + 1):
                if (z - z0) % 3 == 0:
                    w.set(x, y, z, timber_y)
            for y in (5, 8, eave_y):
                w.set(x, y, z, B.log("dark_oak", "z"))
        # windows with shutters between the posts on floors 2 and 3
        for z in range(z0 + 1, z1, 3):
            for y0 in (6, 9):
                if hash2(z, y0, seed) < 0.85:
                    w.set(x, y0, z + 1, B.pane())
                    w.set(x, y0 + 1, z + 1, B.pane())
    # ground floor arches (an arcade of shop fronts and the inn's side door)
    for z in range(z0 + 2, z1 - 1, 4):
        for x in (x0, x1):
            w.fill(x, 1, z, x, 3, z + 1, AIR)
            w.set(x, 4, z, B.stairs("stone_brick", "north", "top"))
            w.set(x, 4, z + 1, B.stairs("stone_brick", "south", "top"))
    w.fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, B.full("spruce_planks"))
    w.fill(x0 + 1, 4, z0 + 1, x1 - 1, 4, z1 - 1, B.full("spruce_planks"))
    w.fill(x0 + 1, 8, z0 + 1, x1 - 1, 8, z1 - 1, B.full("spruce_planks"))
    # gable roof, ridge along z
    half = (x1 - x0) // 2 + 1
    cx = (x0 + x1) // 2
    for d in range(0, half + 1):
        y = eave_y + 1 + (half - d)
        for z in range(z0 - 1, z1 + 2):
            for x in (cx - d, cx + d):
                if d == 0:
                    w.set(x, y, z, B.slab(roof, "bottom"))
                else:
                    w.set(x, y, z, B.stairs(roof, "east" if x < cx else "west"))
    # gable ends (plaster under the roof)
    for z in (z0, z1):
        for d in range(0, half):
            for x in (cx - d, cx + d):
                for y in range(eave_y + 1, eave_y + 1 + (half - d)):
                    w.set(x, y, z, plaster)
    # chimneys with smoke
    for (zc, heavy) in ((z0 + 2, False), (z1 - 2, False)):
        top = eave_y + half + 2
        for y in range(eave_y, top + 1):
            w.set(cx + 2, y, zc, B.full("bricks"))
        TK.smoke(w, cx + 2, top + 1, zc, heavy)


# ============================================================================ room shell (an attic or a floor)
def attic_room(w, x0, x1, z0, z1, wall="white_plaster", roof="terracotta_roof_tiles", ceiling_wood="spruce"):
    """Carves an attic room under a gable roof whose ridge runs along x: interior x0..x1, z0..z1, knee walls two
    blocks high just outside (z0-1, z1+1), gable walls at x0-1 / x1+1, floor block at FY. The ceiling follows the
    roof slope with upside-down stairs and a ridge beam. Returns ceiling(z) -> the ceiling block's y at z."""
    cz = (z0 + z1) // 2
    half = (z1 - z0) // 2 + 1
    plaster = F(wall)

    def ceiling(z):
        return FY + 3 + (half - abs(z - cz))

    w.fill(x0 - 1, FY, z0 - 1, x1 + 1, FY, z1 + 1, B.full("spruce_planks"))
    for x in range(x0, x1 + 1, 3):
        w.fill(x, FY, z0, x, FY, z1, B.full("dark_oak_planks"))
    for x in range(x0 - 1, x1 + 2):
        for z in (z0 - 1, z1 + 1):
            for y in range(FY + 1, FY + 3):
                w.set(x, y, z, plaster)
    for z in range(z0 - 1, z1 + 2):
        for x in (x0 - 1, x1 + 1):
            for y in range(FY + 1, ceiling(z) + 1):
                w.set(x, y, z, plaster)
    for z in range(z0 - 1, z1 + 2):
        north = z < cz
        for x in range(x0, x1 + 1):
            y = ceiling(z)
            for yy in range(FY + 1 if z0 <= z <= z1 else FY + 3, y):
                w.set(x, yy, z, AIR)
            if z == cz:
                w.set(x, y, z, B.log("dark_oak", "x"))
            else:
                w.set(x, y, z, B.stairs(ceiling_wood, "north" if north else "south", "top"))
            for yy in range(y + 1, ceiling(cz) + 2):
                if w.get(x, yy, z) != AIR and yy > y + 1:
                    w.set(x, yy, z, AIR)
    for z in range(z0 - 2, z1 + 3):
        d = abs(z - cz)
        roof_y = ceiling(z) + 1 if d <= half else FY + 3
        for x in range(x0 - 2, x1 + 3):
            if d == 0:
                w.set(x, roof_y, z, B.slab(roof, "bottom"))
            else:
                w.set(x, roof_y, z, B.stairs(roof, "south" if z < cz else "north"))
    return ceiling


# ============================================================================ tier 1: the garret
def garret(w):
    """屋根裏の小部屋 — a snug attic under a cross gable of the inn's roof: bed under the eaves, the sorting desk at the
    gable window with the town and two distant towers in the evening sun, three labelled chests, three barrels, two
    book shelves, an armour stand by the door and a pair of weapon frames."""
    s = kit.Storage()
    seed = 101
    street_and_houses(w, -15, -9, -34, 34, seed=seed)
    inn_wing(w, -7, 7, -34, 34, eave_y=FY, seed=seed)
    # the room: interior x -6..2, z -3..3; its west gable is in the wing's west face, the door in the east gable
    x0, x1, z0, z1 = -6, 2, -3, 3
    ceiling = attic_room(w, x0, x1, z0, z1)
    gx = x0 - 1
    for z in range(z0 - 1, z1 + 2):
        top = ceiling(z) if z0 <= z <= z1 else FY + 2
        for y in range(FY + 1, top + 1):
            if abs(z) == 2:
                w.set(gx, y, z, B.log("dark_oak", "y"))
        w.set(gx, FY + 1, z, B.log("dark_oak", "z"))
    # window: three panes wide and three high, a round light above, a sill with flowers outside
    for z in (-1, 0, 1):
        for y in (FY + 2, FY + 3, FY + 4):
            w.set(gx, y, z, B.S("leaded_glass_pane"))
        w.set(gx - 1, FY + 1, z, B.stairs("spruce", "east", "top"))
    w.set(gx, FY + 6, 0, B.S("leaded_glass_pane"))
    w.set(gx - 1, FY + 2, -1, B.potted("red_tulip"))
    w.set(gx - 1, FY + 2, 1, B.potted("cornflower"))
    deco.light_shaft(w, x0, FY + 4, 0, "east", length=8.0, width=2.6, height=2.6, pitch=28.0)

    # the way in and out: the room door in the east gable
    kit.exit_door(w, x1 + 1, FY + 1, 0, "west")
    kit.entry(w, x1 - 1, FY + 1, 0, 90.0)

    # bed under the north eaves, head west, a barrel nightstand with a candle
    foot, head = B.bed_states("red", "west")
    w.set(-4, FY + 1, -3, foot)
    w.set(-5, FY + 1, -3, head)
    kit.barrel(w, s, -6, FY + 1, -3, "up")
    w.set(-6, FY + 2, -3, B.candle(2, "white"))
    FU.painting(w, -3, FY + 2, -3, "south", variant="kebab")

    # the sorting desk under the window, a chair before it, a candle and a pot of flowers beside it
    kit.sorting_desk(w, -6, FY + 1, 0, "east")
    deco.chair(w, -5, FY, 0, faces="west")
    w.set(-6, FY + 1, 1, B.slab("spruce", "top"))
    w.set(-6, FY + 2, 1, B.candle(3, "yellow"))

    # storage along the south eaves: three labelled chests and two barrels
    kit.chest(w, s, -5, FY + 1, 3, "north")
    kit.chest(w, s, -4, FY + 1, 3, "north")
    kit.chest(w, s, -3, FY + 1, 3, "north")
    kit.barrel(w, s, -2, FY + 1, 3, "north")
    kit.barrel(w, s, -1, FY + 1, 3, "up")
    w.set(-1, FY + 2, 3, B.S("decorated_pot"))
    # by the door: armour stand, two shelves of books, two weapon frames on the gable wall
    kit.stand(w, s, 1, FY, -2, yaw=180.0)
    kit.shelf(w, s, 1, FY + 1, 3, "north")
    kit.shelf(w, s, -1, FY + 1, -3, "south")
    kit.frame(w, s, x1, FY + 3, -2, "west")
    kit.frame(w, s, x1, FY + 3, 2, "west")

    # rug, lanterns under the ridge, a plant in the corner
    FU.rug(w, -4, -1, 0, 1, FY, field="red", border="yellow")
    deco.hanging_lantern(w, -2, ceiling(0), 0, drop=1)
    deco.hanging_lantern(w, 1, ceiling(0), 0, drop=1)
    w.set(-6, FY + 1, -1, B.potted("fern"))
    return s


from . import suites  # noqa: E402  (the dearer rooms; they use the helpers above)

DESIGNS = {
    ("room", "garret"): garret,
    ("room", "canal"): suites.canal,
    ("room", "library"): suites.library,
    ("room", "tower"): suites.tower,
    ("room", "grand"): suites.grand,
}

# Storage names of each room tier, smallest first: each must contain the one before (checked by check_storage);
# the same order and ids as com.eternalrealm.estate.RoomDesign.
ROOM_TIERS = ["garret", "canal", "library", "tower", "grand"]


def check_storage(results: dict) -> list:
    problems = []
    previous = None
    for tier in ROOM_TIERS:
        names = set(results[("room", tier)].names)
        if previous is not None and not previous[1] <= names:
            problems.append(f"room {tier} lacks storage of {previous[0]}: {sorted(previous[1] - names)}")
        previous = (tier, names)
    return problems
