"""Tier 4 guild houses (壮麗, about 55 x 55): the night cathedral, the coral sea hall, the volcanic fortress and the
ice castle. From this tier a house has two vaults, an armoury of armour stands and six trophy frames."""
from __future__ import annotations

import math

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.districts import _furnish as FN
from capital.districts import _gate_kit as GK
from capital.noise import fbm2, hash2, hash3

from . import hkit as K
from .hkit import AIR, F, SL, ST, WL

OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def _walls(w, x0, z0, x1, z1, y0, y1, state):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                w.fill(x, y0, z, x, y1, z, state)


def _disc(w, cx, cz, r, y, state):
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            if math.hypot(dx, dz) <= r + 0.25:
                w.set(cx + dx, y, cz + dz, state)


# ====================================================================================== 夜のゴシック聖堂
def gothic_cathedral(w):
    """夜のゴシック聖堂: a cathedral of dark stone at dusk. Twin spired towers flank a portal under a rose window;
    flying buttresses with pinnacles carry the nave, whose purple and blue glass glows over candle-lit pews. The
    crossing is the guild master's dais with the board on the choir screen and two armoured stands; trophy
    frames line the south aisle, chapels keep the chests, the north transept holds the enchanting nook and the
    crypt under the apse the two vaults. The cloister beside it keeps the workshop in its walks and the dummies
    in its garth; the rally bell hangs in the tower porch."""
    h = K.House(w, "gothic_cathedral")
    cells = K.island(w, 0, 0, 26.5, 25.5, depth=18, surface=("grass_block", "moss_block", "grass_block", "podzol"),
                     rock=("deepslate", "tuff", "stone", "cobbled_deepslate"), seed=161)
    K.drip_stalactites(w, cells, 0, seed=161, kinds=("deepslate", "tuff"))
    h.gate()
    wall, dark, trim = F("deepslate_bricks"), F("polished_blackstone_bricks"), F("polished_deepslate")
    glass_cols = (B.pane("purple"), B.pane("blue"), B.pane("magenta"), B.pane("light_blue"))
    # ------------------------------------------------------------------ the nave x -8..20, z -5..5, walls to y 17
    nx0, nx1, nz0, nz1, nh = -8, 20, -5, 5, 17
    w.fill(nx0 - 5, 0, nz0 - 6, nx1 + 7, 0, nz1 + 6, trim)
    _walls(w, nx0, nz0, nx1, nz1, 1, nh, wall)
    K.clear(w, nx0 + 1, 1, nz0 + 1, nx1 - 1, nh + 10, nz1 - 1)
    for x in range(nx0 + 1, nx1):                                      # checkered floor
        for z in range(nz0 + 1, nz1):
            w.set(x, 0, z, F("polished_blackstone") if (x + z) % 2 else F("calcite"))
    # aisles (z -10..-6 and 6..10), lower, lean-to roofs, arcades into the nave
    for (a0, a1) in ((nz0 - 5, nz0), (nz1, nz1 + 5)):
        _walls(w, nx0, a0, nx1, a1, 1, 8, wall)
        K.clear(w, nx0 + 1, 1, a0 + 1, nx1 - 1, 8, a1 - 1)
        for x in range(nx0 + 1, nx1):
            for z in range(a0 + 1, a1):
                w.set(x, 0, z, trim)
    for x in range(nx0 + 2, nx1 - 1, 4):                               # arcade openings
        for z in (nz0, nz1):
            m = S.pointed_arch_mask(3, 7)
            for u in range(3):
                for v in range(7):
                    if m[u, v]:
                        w.set(x - 1 + u, 1 + v, z, AIR)
    for x in range(nx0 - 1, nx1 + 2):                                  # aisle roofs (lean-to, slabs)
        for (z, zz) in ((nz0 - 5, nz0 - 1), (nz1 + 5, nz1 + 1)):
            for k, zc in enumerate(range(min(z, zz), max(z, zz) + 1)):
                dist = abs(zc - (nz0 if zc < 0 else nz1))
                w.set(x, 9 + (5 - dist) // 2, zc, SL("slate_roof_tiles", "bottom" if (5 - dist) % 2 == 0 else "top"))
    ridge = S.gable_roof(w, nx0, nz0, nx1, nz1, nh + 1, "slate_roof_tiles", axis="x", overhang=1, pitch=1,
                         gable_state=wall)
    # clerestory windows (pointed, stained) and aisle windows
    for x in range(nx0 + 2, nx1 - 1, 4):
        for z in (nz0, nz1):
            S.gothic_window(w, "x", x - 1, 10, z, 3, 6, glass=glass_cols[(x // 4) % 4], mullion=dark, frame=trim)
        for z in (nz0 - 5, nz1 + 5):
            S.gothic_window(w, "x", x - 1, 2, z, 3, 5, glass=glass_cols[(x // 4 + 1) % 4], mullion=dark)
    # flying buttresses with pinnacles over the aisles
    for x in range(nx0 + 4, nx1 - 1, 4):
        for (z, out) in ((nz0 - 6, "north"), (nz1 + 6, "south")):
            w.fill(x, 1, z, x, 12, z, wall)
            w.fill(x, 1, z + (-1 if out == "north" else 1), x, 9, z + (-1 if out == "north" else 1), wall)
            S.pinnacle(w, x, 13, z, height=4, base=wall, shaft=WL("deepslate_brick"), tip=B.S("gilded_finial"))
            for k in range(1, 5):
                zz = z + (k if out == "north" else -k)
                w.set(x, 12 + k, zz, ST("deepslate_brick", OPP[out]))
                w.set(x, 11 + k, zz, ST("deepslate_brick", out, "top"))
    # ------------------------------------------------------------------ the west front: towers, portal, rose window
    for tz in (nz0 - 5, nz1 + 1):
        tx0, tx1, tz0, tz1 = nx0 - 4, nx0, tz, tz + 4
        _walls(w, tx0, tz0, tx1, tz1, 1, 30, wall)
        K.clear(w, tx0 + 1, 1, tz0 + 1, tx1 - 1, 30, tz1 - 1)
        for y in (10, 20, 30):
            _walls(w, tx0, tz0, tx1, tz1, y, y, trim)
        S.gothic_window(w, "z", tz0 + 1, 22, tx0, 3, 6, glass=B.pane("purple"), mullion=dark)
        S.square_spire(w, tx0 + 2, tz0 + 2, 2, 31, 14, "slate_roof_tiles", finial=True)
        for (px, pz) in ((tx0, tz0), (tx1, tz0), (tx0, tz1), (tx1, tz1)):
            S.pinnacle(w, px, 31, pz, height=4, base=wall, shaft=WL("deepslate_brick"), tip=B.S("gilded_finial"))
    w.fill(nx0, nh + 1, nz0, nx0, ridge, nz1, wall)
    K.clear(w, nx0 - 4, 1, -2, nx0, 7, 2)                              # the portal, recessed
    for d in range(0, 4):
        m = S.pointed_arch_mask(5 - 0, 8 - 0)
        for u in range(5):
            for v in range(8):
                if not m[u, v]:
                    w.set(nx0 - 4 + d, 1 + v, -2 + u, trim if d % 2 else wall)
    lo, hi = B.door_states("dark_oak", "east", "left", True)
    w.fill(nx0 - 4, 0, -2, nx0, 0, 2, trim)
    S.rose_window(w, "z", 0, 12, nx0, 3.6, dark, colors=("purple", "blue", "magenta", "light_blue", "red"))
    # the rally bell in the north tower's porch
    btx, btz = nx0 - 2, nz0 - 3
    K.clear(w, btx - 1, 1, btz - 1, btx + 1, 6, btz + 1)
    K.clear(w, nx0 - 4, 1, btz - 1, nx0 - 4, 4, btz + 1)
    h.bell(w, btx, 5, btz, facing="east", attachment="ceiling")
    w.set(btx, 6, btz, wall)
    # ------------------------------------------------------------------ crossing, transepts and apse
    cx0, cx1 = 12, 16
    for (z0, z1) in ((nz0 - 9, nz0 - 1), (nz1 + 1, nz1 + 9)):         # transept arms
        _walls(w, cx0, z0, cx1, z1, 1, nh, wall)
        K.clear(w, cx0 + 1, 1, z0 + 1, cx1 - 1, nh, z1 - 1)
        for x in range(cx0 + 1, cx1):
            for z in range(z0 + 1, z1):
                w.set(x, 0, z, trim)
        S.gable_roof(w, cx0, z0, cx1, z1, nh + 1, "slate_roof_tiles", axis="z", overhang=1, pitch=1,
                     gable_state=wall)
        zz = z0 if z0 < 0 else z1
        S.rose_window(w, "x", 14, 11, zz, 2.6, dark, colors=("purple", "blue", "light_blue", "magenta"))
    K.clear(w, cx0 + 1, 1, nz0 - 8, cx1 - 1, nh - 1, nz1 + 8)
    for x in range(cx0 + 1, cx1):
        K.clear(w, x, 1, nz0, x, 12, nz0)
        K.clear(w, x, 1, nz1, x, 12, nz1)
    # crossing tower with a slender spire
    _walls(w, cx0, nz0, cx1, nz1, nh, nh + 6, wall)
    S.square_spire(w, 14, 0, 3, nh + 7, 16, "slate_roof_tiles", finial=True)
    # apse: a half-round at the east end
    acx = nx1
    for dx in range(0, 8):
        for dz in range(-6, 7):
            d = math.hypot(dx, dz)
            if d <= 6.4:
                w.set(acx + dx, 0, dz, trim)
                if d > 5.4:
                    w.fill(acx + dx, 1, dz, acx + dx, nh, dz, wall)
    for a in range(-60, 61, 30):
        x = acx + int(round(6 * math.cos(math.radians(a))))
        z = int(round(6 * math.sin(math.radians(a))))
        w.fill(x, 4, z, x, 12, z, B.pane("purple") if a % 60 == 0 else B.pane("blue"))
    K.clear(w, acx, 1, -4, acx, 14, 4)
    for dx in range(0, 7):
        for dz in range(-6, 7):
            d = math.hypot(dx, dz)
            if d <= 6.4:
                hgt = nh + 1 + int((6.4 - d) * 0.9)
                w.set(acx + dx, hgt, dz, F("slate_roof_tiles"))
    # ------------------------------------------------------------------ inside: pews, dais, board, stands, frames
    for x in range(nx0 + 2, 10, 2):                                    # pews facing the altar (east)
        for z in (-4, -3, -2, 2, 3, 4):
            w.set(x, 1, z, ST("dark_oak", "west"))
    for x in range(nx0 + 2, cx0):
        w.set(x, 1, -1, B.carpet("purple"))
        w.set(x, 1, 0, B.carpet("purple"))
        w.set(x, 1, 1, B.carpet("purple"))
    w.fill(cx0, 1, -3, cx1 + 3, 1, 3, F("polished_blackstone"))         # the dais at the crossing
    w.fill(cx0 - 1, 1, -2, cx0 - 1, 1, 2, ST("polished_blackstone", "west"))
    w.set(cx1 + 1, 2, 0, ST("dark_oak", "west"))                       # the master's chair
    for z in (-1, 1):
        w.set(cx1 + 1, 2, z, B.candle(4, "purple"))
    w.fill(cx1 + 4, 1, -4, cx1 + 4, 6, 4, wall)                        # the choir screen with the board
    h.board(w, cx1 + 3, 3, 0, "west")
    K.armory(h, w, [(cx1 + 2, -3), (cx1 + 2, 3)], 1, 90.0)
    K.trophy_wall(h, w, [(x, nz1 + 4) for x in (-4, -1, 2, 5, 8, 11)], 3, "north")
    for x in range(nx0 + 2, nx1, 4):                                   # chandeliers and candles
        deco.chandelier(w, x, nh, 0, drop=5)
        for z in (nz0 + 1, nz1 - 1):
            w.set(x, 1, z, B.candle(3, "white"))
    # chapels in the north aisle: chests between candle shelves
    for i, x in enumerate((-5, -1, 3, 7, 11)):
        h.chest(w, x, 1, nz0 - 4, "south")
        w.set(x + 1, 1, nz0 - 4, B.S("lectern", facing="south", has_book=True) if i % 2 else B.candle(4, "white"))
        K.lamp(w, x, 7, nz0 - 3, soul=True)
    # north transept: the enchanting nook
    h.enchanting_nook(w, 14, 0, nz0 - 6, open_side="south")
    # crypt under the apse: stair down, two vaults among tombs
    w.fill(acx - 3, -6, -4, acx + 4, -1, 4, wall)
    K.clear(w, acx - 2, -5, -3, acx + 3, -2, 3)
    w.fill(acx - 2, -6, -3, acx + 3, -6, 3, trim)
    for k in range(7):                                                 # straight stair down (rising west)
        x = acx - 3 + k
        w.set(x, 1 - k, 3, ST("deepslate_brick", "west"))
        for yy in range(2 - k, 5 - k):
            w.set(x, yy, 3, AIR)
    h.vault(w, acx + 3, -5, -2, "west")
    h.vault(w, acx + 3, -5, 0, "west")
    w.fill(acx - 1, -5, -2, acx + 1, -5, -2, dark)
    w.set(acx, -4, -2, B.candle(3, "white"))
    K.lamp(w, acx, -2, 0, soul=True)
    # ------------------------------------------------------------------ the cloister (south): workshop and dummies
    qx0, qx1, qz0, qz1 = -4, 12, 12, 24
    w.fill(qx0, 0, qz0, qx1, 0, qz1, trim)
    _walls(w, qx0, qz0, qx1, qz1, 1, 5, wall)
    K.clear(w, qx0 + 1, 1, qz0 + 1, qx1 - 1, 5, qz1 - 1)
    for x in range(qx0, qx1 + 1):
        for z in range(qz0, qz1 + 1):
            if x in (qx0, qx1) or z in (qz0, qz1):
                w.set(x, 6, z, SL("slate_roof_tiles", "bottom"))
    gx0, gx1, gz0, gz1 = qx0 + 3, qx1 - 3, qz0 + 3, qz1 - 3            # the garth
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            w.set(x, 0, z, F("grass_block"))
    for x in range(qx0 + 1, qx1):
        for z in range(qz0 + 1, qz1):
            if not (gx0 <= x <= gx1 and gz0 <= z <= gz1):
                w.set(x, 5, z, wall)
    for x in range(gx0, gx1 + 1, 2):
        for z in (gz0 - 1, gz1 + 1):
            w.fill(x, 1, z, x, 4, z, WL("deepslate_brick"))
    K.clear(w, 3, 1, nz1 + 5, 5, 3, qz0)
    w.fill(3, 0, nz1 + 6, 5, 0, qz0, trim)
    h.stations_row(w, [(x, qz1 - 1) for x in range(qx0 + 1, qx1)], 0, "north",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "cartography_table", "furnace", "blast_furnace", "smoker", "brewing_stand",
                          "fletching_table", "crafting_table"))
    K.training_ground(h, w, [(gx0 + 1, gz0 + 1), (gx0 + 4, gz0 + 2), (gx0 + 7, gz0 + 1), (gx0 + 2, gz1 - 1)], 0,
                      0.0, looks=(0, 1, 2))
    h.target(w, gx1, 0, gz1)
    for x in (qx0 + 2, qx1 - 2):
        K.lamp(w, x, 4, qz0 + 1, soul=True)
    # ------------------------------------------------------------------ grounds: yews, lamps, a path
    K.path(w, [(-17, 0), (nx0 - 5, 0)], 0, ["polished_deepslate", "deepslate_tiles", "polished_blackstone"],
           width=3, seed=162)
    for (x, z) in ((-14, -12), (-15, 12), (26, -10), (26, 12), (-5, -21), (18, -20), (20, 20), (-9, 21)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "cypress", seed=x + z * 3, height=8)
    for (x, z) in ((-14, -3), (-14, 3), (-11, -3), (-11, 3)):
        deco.lamp_post(w, x, 0, z, height=4, soul=True)
    K.scatter_plants(w, cells, 0, seed=163, kinds=("grass", "fern", "lily_of_the_valley", "azure_bluet"),
                     density=0.15, on=("grass_block", "moss_block", "podzol"))
    return h.finish()


# ====================================================================================== 珊瑚の海殿
def coral_hall(w):
    """珊瑚の海殿: a hall under the sea. The island holds a sea inside its rim, coral reefs and kelp on its floor;
    from the gate a stair runs down inside a glass tube to a great glass dome on the sea floor. Under the dome,
    in air, stands a prismarine palace: the throne of shells with the board, pearl pillars, the council table, the
    trophy frames and the armour stands, the enchanting nook, the chests in coral niches and two vaults in the
    pearl treasury; a second bubble holds the workshop. On the rim, a terrace of dummies looks over the water."""
    h = K.House(w, "coral_hall")
    cells = K.island(w, 0, 0, 26.5, 25.5, depth=20, surface=("sand", "sand", "smooth_sandstone"), soil="sand",
                     rock=("prismarine", "stone", "sandstone", "dark_prismarine"), seed=171)
    K.drip_stalactites(w, cells, 0, seed=171, kinds=("prismarine", "stone"))
    h.gate()
    prism, bricks, dark, lamp = F("prismarine"), F("prismarine_bricks"), F("dark_prismarine"), F("sea_lantern")
    # ------------------------------------------------------------------ the sea: x -12..24, z -20..20, floor y -11
    sea = set()
    scx, scz, srx, srz = 6, 0, 18.5, 20.5
    for (x, z) in sorted(cells):
        d = math.hypot((x - scx) / srx, (z - scz) / srz) + fbm2(x, z, 6.0, 2, 172) * 0.05
        if d <= 1.0:
            sea.add((x, z))
    floor_y = -11
    for (x, z) in sea:
        edge = any((x + dx, z + dz) not in sea for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        for y in range(floor_y - 1, 1):
            w.set(x, y, z, B.S("water") if not edge or y == floor_y - 1 else bricks)
        w.set(x, floor_y - 1, z, F("sand") if hash2(x, z, 173) < 0.7 else F("gravel"))
        if edge:
            w.set(x, 1, z, B.wall("prismarine"))
            w.fill(x, floor_y - 1, z, x, 0, z, bricks)
    # reefs on the sea floor
    corals = ("tube", "brain", "bubble", "fire", "horn")
    for (x, z) in sorted(sea):
        k = hash2(x, z, 174)
        y = floor_y
        if k < 0.05:
            c = corals[int(hash2(z, x, 175) * 5)]
            hgt = 1 + int(hash2(x, z, 176) * 3)
            for yy in range(y, y + hgt):
                w.set(x, yy, z, F(f"{c}_coral_block"))
            w.set(x, y + hgt, z, B.S(f"{c}_coral", waterlogged=True))
        elif k < 0.16:
            w.set(x, y, z, B.S(f"{corals[int(hash2(z, x, 177) * 5)]}_coral_fan", waterlogged=True))
        elif k < 0.26:
            n = 2 + int(hash2(x, z, 178) * 7)
            for yy in range(y, y + n):
                w.set(x, yy, z, B.S("kelp_plant") if yy < y + n - 1 else B.S("kelp", age=25))
        elif k < 0.34:
            w.set(x, y, z, B.S("seagrass"))
        elif k < 0.37:
            w.set(x, y, z, B.S("sea_pickle", pickles=3, waterlogged=True))
    # ------------------------------------------------------------------ the great dome (centre 8, 0) in air
    dcx, dcz, R = 8, 0, 11
    dome_floor = floor_y
    for dx in range(-R - 1, R + 2):
        for dz in range(-R - 1, R + 2):
            d = math.hypot(dx, dz)
            for dy in range(0, R + 2):
                dd = math.sqrt(d * d + dy * dy)
                x, y, z = dcx + dx, dome_floor + dy, dcz + dz
                if dd <= R - 0.6:
                    w.set(x, y, z, AIR)
                elif dd <= R + 0.5:
                    rib = (round(math.degrees(math.atan2(dz, dx))) % 45 < 5) or dy == 0
                    w.set(x, y, z, bricks if rib else F("light_blue_stained_glass"))
    _disc(w, dcx, dcz, R - 0.5, dome_floor - 1, dark)
    for dx in range(-R, R + 1):                                        # mosaic floor
        for dz in range(-R, R + 1):
            d = math.hypot(dx, dz)
            if d <= R - 0.6:
                w.set(dcx + dx, dome_floor, dcz + dz, bricks if int(d) % 3 else prism)
    w.set(dcx, dome_floor + R + 1, dcz, lamp)
    # the glass tube with the stair down from the rim (west side)
    tz = 0
    sx0 = -9
    for k in range(-floor_y + 1):
        x = sx0 + k
        y = -k
        for z in (tz - 2, tz + 2):
            w.fill(x, y - 1, z, x, y + 4, z, F("light_blue_stained_glass") if k % 3 else bricks)
        w.fill(x, y + 4, tz - 2, x, y + 4, tz + 2, F("light_blue_stained_glass") if k % 3 else bricks)
        for z in range(tz - 1, tz + 2):
            w.set(x, y - 1, z, bricks)
            w.fill(x, y, z, x, y + 3, z, AIR)
            if k > 0:
                w.set(x, y, z, ST("prismarine_brick", "west"))
    w.fill(sx0 - 3, 0, tz - 2, sx0, 0, tz + 2, bricks)                 # the landing at the rim
    for x in range(sx0 - 3, sx0 + 1):
        for z in (tz - 2, tz + 2):
            w.set(x, 1, z, B.wall("prismarine"))
    for (x, z) in ((sx0 - 3, tz - 1), (sx0 - 3, tz + 1)):
        w.set(x, 1, z, AIR)
    # ------------------------------------------------------------------ the palace under the dome
    fy = dome_floor
    for (x, z) in ((dcx - 5, -5), (dcx - 5, 5), (dcx + 5, -5), (dcx + 5, 5)):   # pearl pillars
        w.fill(x, fy + 1, z, x, fy + 7, z, B.S("quartz_pillar", axis="y"))
        w.set(x, fy + 8, z, lamp)
    w.fill(dcx + 6, fy + 1, -2, dcx + 8, fy + 1, 2, bricks)            # the shell throne with the board behind
    w.set(dcx + 8, fy + 2, 0, ST("prismarine_brick", "west"))
    w.fill(dcx + 9, fy + 1, -3, dcx + 9, fy + 5, 3, dark)
    h.board(w, dcx + 8, fy + 3, 0, "west")
    w.set(dcx + 8, fy + 2, 0, AIR)
    for z in (-2, 2):
        w.set(dcx + 8, fy + 2, z, F("dead_brain_coral_block"))
    K.council(w, dcx - 3, fy, 0, 5, axis="x", top="dark_oak", cloth="cyan", chair="warped")
    K.armory(h, w, [(dcx + 5, -3), (dcx + 5, 3)], fy, 270.0)
    h.enchanting_nook(w, dcx - 6, fy, -6, open_side="east")
    for (x, z, f) in ((dcx - 1, -9, "south"), (dcx + 1, -9, "south"), (dcx + 3, -9, "south"),
                      (dcx - 1, 9, "north"), (dcx + 1, 9, "north"), (dcx + 3, 9, "north")):
        w.set(x, fy + 3, z + (-1 if f == "south" else 1), dark)
        h.frame(w, x, fy + 3, z, f)
    for (x, z, f) in ((dcx - 6, 6, "north"), (dcx - 4, 8, "north"), (dcx - 2, 9, "north"), (dcx + 6, 6, "north"),
                      (dcx - 6, -2, "east")):
        h.chest(w, x, fy + 1, z, f)
    for (x, z) in ((dcx + 7, -5), (dcx + 7, 5)):                        # the pearl treasury: two vaults
        h.vault(w, x, fy + 1, z, "west")
        w.set(x, fy + 2, z, B.lantern())
    for (dx, dz) in ((-3, 3), (3, -3), (0, 7), (0, -7)):
        K.lamp(w, dcx + dx, fy + 8, dcz + dz, chain=2)
    # ------------------------------------------------------------------ the workshop bubble (north-east)
    wcx, wcz, wr = 16, -12, 5
    for dx in range(-wr - 1, wr + 2):
        for dz in range(-wr - 1, wr + 2):
            d = math.hypot(dx, dz)
            for dy in range(0, wr + 2):
                dd = math.sqrt(d * d + dy * dy)
                x, y, z = wcx + dx, floor_y + dy, wcz + dz
                if dd <= wr - 0.6:
                    w.set(x, y, z, AIR)
                elif dd <= wr + 0.5:
                    w.set(x, y, z, F("light_blue_stained_glass") if dy > 0 else bricks)
    _disc(w, wcx, wcz, wr - 0.5, floor_y, bricks)
    # a glass corridor from the dome
    for (x0, z0, x1, z1) in ((dcx + 6, -8, wcx - 2, -6),):
        for x in range(x0, x1 + 1):
            for z in range(z0 - 1, z1 + 2):
                w.set(x, floor_y, z, bricks)
                for y in range(floor_y + 1, floor_y + 4):
                    edge = z in (z0 - 1, z1 + 1)
                    w.set(x, y, z, F("light_blue_stained_glass") if edge else AIR)
                w.set(x, floor_y + 4, z, F("light_blue_stained_glass"))
    ring = [(wcx + int(round(3 * math.cos(math.radians(a)))), wcz + int(round(3 * math.sin(math.radians(a)))))
            for a in range(0, 360, 30)]
    seen = []
    for c in ring:
        if c not in seen:
            seen.append(c)
    h.stations_row(w, seen, floor_y, "north",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "cartography_table", "furnace", "blast_furnace", "smoker", "brewing_stand",
                          "fletching_table"))
    w.set(wcx, floor_y + wr, wcz, lamp)
    # ------------------------------------------------------------------ the rim: the dummies' terrace, the bell
    K.mix(w, -15, 0, -10, -10, 0, -4, [bricks, prism], 179)
    K.training_ground(h, w, [(-14, -9), (-12, -9), (-14, -6), (-12, -6)], 0, 90.0, looks=(0, 1, 2))
    h.target(w, -11, 0, -11) if (-11, -11) in cells else h.target(w, -10, 0, -8)
    for z in (6, 10):
        w.fill(-13, 1, z, -13, 4, z, B.S("quartz_pillar", axis="y"))
    w.fill(-13, 5, 6, -13, 5, 10, bricks)
    h.bell(w, -13, 4, 8, facing="east", attachment="ceiling")
    for (x, z) in ((-12, -3), (-12, 3), (24, -6), (23, 8)):
        if (x, z) in cells:
            w.set(x, 1, z, bricks)
            w.set(x, 2, z, lamp)
    K.path(w, [(-17, 0), (sx0 - 3, 0)], 0, ["prismarine_bricks", "prismarine", "dark_prismarine"], width=3, seed=180)
    for (x, z, lean) in ((-20, -8, (1, 0)), (-19, 9, (1, 0)), (-6, -21, (0, 1)), (-4, 22, (0, -1)), (23, -15, (-1, 0)),
                         (22, 17, (-1, 0))):
        if (x, z) in cells and (x, z) not in sea:
            K.palm(w, x, 0, z, height=7, seed=x + z, lean=lean)
    return h.finish()


# ====================================================================================== 火山の砦
BASALT = ("basalt", "smooth_basalt", "blackstone", "polished_blackstone", "basalt")


def volcanic_fortress(w):
    """火山の砦: a basalt fortress on a smouldering island under its volcano. Behind the horned gatehouse the
    courtyard holds the dummies and the archery butts; the keep's great hall burns with braziers, its walls hung
    with trophy frames and armour; the forge beside it runs off a lava channel behind iron grilles, the vaults lie
    in the keep's undercroft, the enchanting nook at the top of the tower. Lava falls from the crater down the
    cone into a moat ringed with magma; smoke rises from the summit; the bell hangs on the wall walk."""
    h = K.House(w, "volcanic_fortress")
    cells = K.island(w, 0, 0, 26.5, 26.0, depth=18, surface=("blackstone", "basalt", "smooth_basalt", "tuff"),
                     soil="blackstone", rock=("blackstone", "basalt", "tuff", "deepslate"), seed=181)
    K.drip_stalactites(w, cells, 0, seed=181, kinds=("blackstone", "basalt"))
    h.gate()
    # ------------------------------------------------------------------ the volcano (east), its crater and lava fall
    vcx, vcz = 19, -10
    for (x, z) in sorted(cells):
        d = math.hypot(x - vcx, z - vcz)
        top = int(30 - d * 2.4 + fbm2(x, z, 5.0, 2, 182) * 2)
        if top <= 0:
            continue
        for y in range(1, top + 1):
            st = BASALT[int(hash3(x, y, z, 183) * 5) % 5]
            w.set(x, y, z, F(st) if st != "basalt" else B.S("basalt", axis="y"))
        if hash2(x, z, 184) < 0.08:
            w.set(x, top, z, F("magma_block"))
    for dx in range(-3, 4):                                            # the crater with a lava lake
        for dz in range(-3, 4):
            if math.hypot(dx, dz) <= 3.2:
                for y in range(25, 31):
                    w.set(vcx + dx, y, vcz + dz, AIR)
                w.set(vcx + dx, 24, vcz + dz, B.S("lava"))
                w.set(vcx + dx, 23, vcz + dz, F("magma_block"))
    for k in range(0, 12):                                             # the lava fall down the west flank
        x = vcx - 3 - k
        y = 24 - int(k * 2.0)
        if y < 1:
            break
        w.set(x, y, vcz, B.S("lava"))
        w.set(x, y - 1, vcz, F("magma_block"))
        w.set(x, y, vcz - 1, F("blackstone"))
        w.set(x, y, vcz + 1, F("blackstone"))
    K.TK.smoke(w, vcx, 31, vcz, heavy=True)
    # ------------------------------------------------------------------ curtain wall and courtyard x -15..12, z -2..22
    fx0, fx1, fz0, fz1 = -15, 13, -4, 21
    w.fill(fx0, 0, fz0, fx1, 0, fz1, F("polished_blackstone"))
    K.mix(w, fx0 + 2, 0, fz0 + 2, fx1 - 2, 0, fz1 - 2, [F("polished_blackstone_bricks"), F("blackstone"),
                                                       F("polished_basalt"), F("smooth_basalt")], 185)
    for x in range(fx0, fx1 + 1):
        for z in range(fz0, fz1 + 1):
            if x in (fx0, fx0 + 1, fx1 - 1, fx1) or z in (fz0, fz0 + 1, fz1 - 1, fz1):
                w.fill(x, 1, z, x, 7, z, F("polished_blackstone_bricks"))
                if x in (fx0, fx1) or z in (fz0, fz1):
                    if (x + z) % 2 == 0:
                        w.set(x, 8, z, F("polished_blackstone_bricks"))
                        w.set(x, 9, z, F("red_nether_bricks"))
    K.clear(w, fx0, 1, -2, fx0 + 1, 5, 2)                              # the inner gate (west) facing the arrival
    for z in (-2, 2):
        w.set(fx0, 5, z, ST("polished_blackstone_brick", "south" if z < 0 else "north", "top"))
    for (tx, tz) in ((fx0, fz0), (fx1, fz0), (fx0, fz1), (fx1, fz1)):    # corner towers with horns
        S.cylinder(w, tx, tz, 3.2, 1, 12, F("polished_blackstone_bricks"))
        S.cylinder(w, tx, tz, 3.6, 13, 13, F("polished_blackstone_bricks"))
        for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
            w.set(tx + dx, 14, tz + dz, F("red_nether_bricks"))
            w.set(tx + dx + (1 if dx > 0 else -1), 15, tz + dz + (1 if dz > 0 else -1), WL("red_nether_bricks"))
        w.set(tx, 14, tz, F("magma_block"))
    # wall walk (top of the curtain) reached by a stair in the courtyard; the bell up there
    FN.stair_up(w, 8, fz1 - 2, 0, 7, "west", wood="dark_oak")         # up to the wall walk (south)
    w.fill(-3, 8, fz1, -1, 8, fz1, F("polished_blackstone_bricks"))
    w.fill(-3, 10, fz1, -1, 10, fz1, F("polished_blackstone_bricks"))
    w.set(-3, 9, fz1, F("polished_blackstone_bricks"))
    w.set(-1, 9, fz1, F("polished_blackstone_bricks"))
    h.bell(w, -2, 9, fz1, facing="north", attachment="ceiling")
    # ------------------------------------------------------------------ the keep x -2..10, z 0..12 (great hall)
    kx0, kx1, kz0, kz1 = -1, 11, 2, 13
    _walls(w, kx0, kz0, kx1, kz1, 1, 14, F("polished_blackstone_bricks"))
    K.clear(w, kx0 + 1, 1, kz0 + 1, kx1 - 1, 13, kz1 - 1)
    w.fill(kx0, 14, kz0, kx1, 14, kz1, F("polished_blackstone_bricks"))
    for x in range(kx0, kx1 + 1):
        for z in (kz0, kz1):
            if (x + z) % 2 == 0:
                w.set(x, 15, z, F("red_nether_bricks"))
    for z in range(kz0, kz1 + 1):
        for x in (kx0, kx1):
            if (x + z) % 2 == 0:
                w.set(x, 15, z, F("red_nether_bricks"))
    K.clear(w, kx0, 1, 6, kx0, 4, 8)                                    # the keep door (west)
    w.set(kx0, 4, 6, ST("polished_blackstone_brick", "south", "top"))
    w.set(kx0, 4, 8, ST("polished_blackstone_brick", "north", "top"))
    for x in range(kx0 + 2, kx1 - 1, 3):                                # arrow slits with a red glow
        for z in (kz0, kz1):
            w.fill(x, 5, z, x, 8, z, B.pane("red"))
    for x in range(kx0 + 1, kx1):
        for z in range(kz0 + 1, kz1):
            w.set(x, 0, z, F("polished_blackstone") if (x + z) % 2 else F("polished_basalt"))
    for (x, z) in ((2, 5), (8, 5), (2, 10), (8, 10)):                   # braziers on basalt columns
        w.set(x, 1, z, F("polished_basalt"))
        w.set(x, 2, z, B.S("campfire", lit=True, facing="north"))
    h.board(w, kx1 - 1, 3, 7, "west")
    w.fill(kx1 - 3, 1, 5, kx1 - 1, 1, 9, F("red_nether_bricks"))
    w.set(kx1 - 3, 2, 7, ST("polished_blackstone_brick", "west"))
    w.set(kx1 - 1, 2, 7, AIR)
    K.armory(h, w, [(kx1 - 2, 4), (kx1 - 2, 10)], 1, 270.0)
    K.trophy_wall(h, w, [(x, kz0 + 1) for x in (1, 3, 5, 7, 9)] + [(5, kz1 - 1)], 4, "south")
    w.entities = [e for e in w.entities if not (e["type"].endswith("item_frame") and e["pos"][2] > kz1 - 1.5
                                                 and "er_slot" in e["nbt"])]
    h.storage.names = [n for n in h.storage.names if n != "frame.6"]
    h.storage.count["frame"] = 5
    h.frame(w, 5, 4, kz1 - 1, "north")
    K.council(w, 3, 0, 7, 5, axis="x", top="crimson", cloth="red", chair="crimson")
    for x in (3, 7):
        deco.chandelier(w, x, 13, 7, drop=5)
    # the undercroft: two vaults below the hall
    w.fill(1, -5, 4, 9, -1, 11, F("polished_blackstone_bricks"))
    K.clear(w, 2, -4, 5, 8, -2, 10)
    for k in range(4):                                                 # stair down along the north wall
        x = 2 + k
        w.set(x, -1 - k, kz0 + 1, ST("polished_blackstone_brick", "west"))
        for yy in range(-k, 3 - k):
            w.set(x, yy, kz0 + 1, AIR)
    w.set(1, 0, kz0 + 1, AIR)
    K.clear(w, 2, -4, kz0 + 1, 6, -2, kz0 + 2)
    h.vault(w, 8, -4, 9, "west")
    h.vault(w, 8, -4, 6, "west")
    K.lamp(w, 5, -2, 8)
    w.fill(2, -4, 9, 3, -4, 10, F("gold_block"))
    # chests along the hall's south wall
    for x in (1, 3, 5, 7, 9):
        h.chest(w, x, 1, kz1 - 1, "north")
    # the tower on the keep with the enchanting nook at the top (north-east corner)
    ttx, ttz = kx1 - 3, kz0 + 3
    S.cylinder(w, ttx, ttz, 3.6, 14, 24, F("polished_blackstone_bricks"), hollow=True, thickness=1.0)
    S.cylinder(w, ttx, ttz, 2.6, 15, 24, AIR)
    S.cylinder(w, ttx, ttz, 3.6, 19, 19, F("polished_blackstone_bricks"))
    S.cylinder(w, ttx, ttz, 3.6, 25, 25, F("red_nether_bricks"))
    h.enchanting_nook(w, ttx, 19, ttz, open_side="west")
    for y in range(14, 19):                                            # ladder up the tower
        w.set(ttx - 2, y, ttz + 2, AIR)
    K.clear(w, ttx - 2, 14, ttz + 1, ttx - 1, 18, ttz + 2)
    for y in range(1, 20):
        w.set(ttx - 2, y, ttz + 2, B.S("ladder", facing="east"))
        w.set(ttx - 3, y, ttz + 2, F("polished_blackstone_bricks"))
    w.set(ttx - 2, 14, ttz + 2, B.S("ladder", facing="east"))
    K.clear(w, ttx - 2, 15, ttz + 2, ttx - 2, 18, ttz + 2)
    for y in range(15, 20):
        w.set(ttx - 2, y, ttz + 2, B.S("ladder", facing="east"))
    for (dx, dz) in ((3, 0), (0, -3), (-3, 0)):
        w.fill(ttx + dx, 21, ttz + dz, ttx + dx, 22, ttz + dz, B.pane("red"))
    # ------------------------------------------------------------------ the forge on a lava channel (north side)
    gx0, gx1 = -12, 10
    for x in range(gx0, gx1 + 1):
        w.set(x, 0, fz0 + 2, F("polished_blackstone_bricks"))
        w.set(x, 0, fz0 + 3, B.S("lava") if x % 6 != 0 else F("magma_block"))
        w.set(x, -1, fz0 + 3, F("blackstone"))
        w.set(x, 1, fz0 + 3, B.S("iron_bars"))
    h.stations_row(w, [(x, fz0 + 2) for x in range(gx0, gx0 + 12)], 0, "south",
                   kinds=("blast_furnace", "anvil", "smithing_table", "grindstone", "blast_furnace", "furnace",
                          "stonecutter", "crafting_table", "smoker", "loom", "cartography_table", "brewing_stand"))
    h.station(w, gx0 + 12, 1, fz0 + 2, "fletching_table", "south")
    for x in range(gx0, gx0 + 13, 4):
        w.fill(x, 4, fz0 + 2, x, 4, fz0 + 2, F("polished_blackstone_bricks"))
    # ------------------------------------------------------------------ the courtyard: dummies and butts
    K.training_ground(h, w, [(-11, 8), (-11, 11), (-11, 14), (-8, 17)], 0, 90.0, looks=(1, 1, 0, 2))
    h.target(w, -4, 0, 17)
    h.target(w, -2, 0, 17)
    # the moat ringed with magma on the outer west side
    for z in range(-12, 26):
        x = -19
        if (x, z) in cells and abs(z) > 5:
            w.set(x, 0, z, B.S("lava") if abs(z) % 5 else F("magma_block"))
            w.set(x, -1, z, F("blackstone"))
    K.path(w, [(-17, 0), (fx0 - 1, 0)], 0, ["polished_blackstone", "blackstone", "polished_basalt"], width=3,
           seed=186)
    for (x, z) in ((-12, -10), (-10, -14), (2, -18), (-20, 15), (16, 20)):
        if (x, z) in cells:
            w.fill(x, 1, z, x, 2, z, F("polished_basalt"))
            w.set(x, 3, z, F("magma_block"))
            w.set(x, 4, z, B.S("campfire", lit=True, facing="north"))
    return h.finish()


# ====================================================================================== 氷の城
ICE = ("packed_ice", "packed_ice", "blue_ice", "snow_block")


def ice_castle(w):
    """氷の城: a castle of packed and blue ice on a snowy island, its towers capped with icy spires. In the throne
    hall a blue soul fire burns under ice chandeliers over white furs; the guild's armour and trophies stand
    along its walls. A timber annex keeps the warm workshop, the ice vault the chests, the north tower the
    enchanting nook. A frozen waterfall hangs from the island's edge, snowy spruces ring the frozen lake where the
    dummies stand, and the bell rings from the gate tower's arch."""
    h = K.House(w, "ice_castle")
    cells = K.island(w, 0, 0, 26.5, 26.0, depth=18, surface=("snow_block", "snow_block", "packed_ice", "snow_block"),
                     soil="snow_block", rock=("stone", "packed_ice", "andesite", "calcite"), seed=191)
    for (x, z) in sorted(cells):                                       # icicles under the island
        if hash2(x, z, 192) < 0.06:
            y = -3
            while y > -32 and not w.is_empty(x, y, z):
                y -= 1
            for k in range(1 + int(hash2(z, x, 193) * 4)):
                w.set(x, y - k, z, F("packed_ice") if k < 2 else F("blue_ice"))
    h.gate()
    pk, bl, sn = F("packed_ice"), F("blue_ice"), F("snow_block")
    # ------------------------------------------------------------------ the castle x -12..16, z -12..12
    cx0, cx1, cz0, cz1 = -12, 16, -12, 12
    w.fill(cx0, 0, cz0, cx1, 0, cz1, pk)
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            if x in (cx0, cx1) or z in (cz0, cz1):
                for y in range(1, 9):
                    w.set(x, y, z, bl if (y + x + z) % 5 == 0 else pk)
                if (x + z) % 2 == 0:
                    w.set(x, 9, z, sn)
    K.clear(w, cx0, 1, -2, cx0, 6, 2)                                    # the castle door (west)
    for z in (-2, 2):
        w.set(cx0, 6, z, bl)
    for (tx, tz) in ((cx0, cz0), (cx1, cz0), (cx0, cz1), (cx1, cz1)):     # corner towers with icy spires
        S.cylinder(w, tx, tz, 3.4, 1, 16, pk)
        S.cylinder(w, tx, tz, 3.8, 17, 17, sn)
        S.cone(w, tx, tz, 3.6, 18, 12, "packed_ice", stair_edges=False, finial=False)
        w.set(tx, 30, tz, bl)
    # the keep (throne hall) x -2..14, z -8..8 inside the courtyard's east half
    kx0, kx1, kz0, kz1 = -1, 15, -8, 8
    _walls(w, kx0, kz0, kx1, kz1, 1, 16, pk)
    K.clear(w, kx0 + 1, 1, kz0 + 1, kx1 - 1, 20, kz1 - 1)
    for x in range(kx0 + 1, kx1):
        for z in range(kz0 + 1, kz1):
            w.set(x, 0, z, bl if (x + z) % 4 == 0 else F("smooth_quartz"))
    for x in range(kx0 + 2, kx1 - 1, 3):
        for z in (kz0, kz1):
            S.gothic_window(w, "x", x - 1, 4, z, 3, 7, glass=B.pane("light_blue"), mullion=bl)
    for y in range(17, 25):                                              # vaulted ice roof
        k = y - 17
        for x in range(kx0 - 1 + k, kx1 + 2 - k):
            for z in range(kz0 - 1 + k, kz1 + 2 - k):
                if x in (kx0 - 1 + k, kx1 + 1 - k) or z in (kz0 - 1 + k, kz1 + 1 - k):
                    w.set(x, y, z, pk if (x + y) % 3 else bl)
    w.fill(kx0 + 8, 17, kz0 + 8, kx0 + 8, 17, kz0 + 8, AIR)
    K.clear(w, kx0, 1, -2, kx0, 7, 2)
    for z in (-2, 2):
        w.set(kx0, 7, z, bl)
    # the throne, the soul fire, the board, chandeliers, furs, stands, trophies
    w.fill(kx1 - 3, 1, -3, kx1 - 1, 1, 3, bl)
    w.set(kx1 - 2, 2, 0, ST("quartz", "west"))
    w.fill(kx1 - 1, 2, -1, kx1 - 1, 5, 1, pk)
    h.board(w, kx1 - 1, 2, 5, "west")
    for x in range(kx0 + 3, kx1 - 4):
        for z in (-1, 0, 1):
            w.set(x, 1, z, B.carpet("white") if z else B.carpet("light_gray"))
    w.fill(5, 1, -4, 7, 1, -4, F("polished_andesite"))
    w.set(6, 1, -4, B.S("soul_campfire", lit=True, facing="south"))
    for (x, z) in ((3, 0), (9, 0)):
        deco.chandelier(w, x, 17, z, drop=6)
    K.armory(h, w, [(kx1 - 3, -6), (kx1 - 3, 6)], 0, 270.0)
    for (x, z, f) in ((2, kz0 + 1, "south"), (5, kz0 + 1, "south"), (8, kz0 + 1, "south"),
                      (2, kz1 - 1, "north"), (5, kz1 - 1, "north"), (8, kz1 - 1, "north")):
        h.frame(w, x, 3, z, f, glow=True)
    K.council(w, 2, 0, 5, 7, axis="x", top="spruce", cloth="light_blue", chair="spruce")
    # the ice vault (north-west of the courtyard): chests and the two vaults
    vx0, vx1, vz0, vz1 = cx0 + 2, cx0 + 9, cz0 + 2, cz0 + 7
    _walls(w, vx0, vz0, vx1, vz1, 1, 6, bl)
    K.clear(w, vx0 + 1, 1, vz0 + 1, vx1 - 1, 5, vz1 - 1)
    w.fill(vx0, 6, vz0, vx1, 6, vz1, pk)
    K.clear(w, vx0 + 3, 1, vz1, vx0 + 4, 3, vz1)
    K.storage_wall(h, w, [(x, vz0 + 1) for x in range(vx0 + 1, vx1)], 0, "south", between=B.lantern(), vaults=2)
    for z in (vz1 - 1, vz1 - 3):
        h.chest(w, vx0 + 1, 1, z, "east")
        w.set(vx0 + 1, 1, z + 1, B.lantern()) if z + 1 < vz1 else None
    for z in (vz1 - 1, vz1 - 3):
        h.chest(w, vx1 - 1, 1, z, "west") if h.storage.summary().get("chest", 0) < 5 else None
    # the timber annex (south-west): the warm workshop
    ax0, ax1, az0, az1 = cx0 + 2, cx0 + 11, cz1 - 7, cz1 - 2
    w.fill(ax0, 0, az0, ax1, 0, az1, F("spruce_planks"))
    for x in range(ax0, ax1 + 1):
        for z in range(az0, az1 + 1):
            if x in (ax0, ax1) or z in (az0, az1):
                post = (x - ax0) % 3 == 0 and z in (az0, az1)
                w.fill(x, 1, z, x, 5, z, B.log("spruce", "y") if post else F("spruce_planks"))
    K.clear(w, ax0 + 1, 1, az0 + 1, ax1 - 1, 6, az1 - 1)
    S.gable_roof(w, ax0, az0, ax1, az1, 6, "shingle_roof_tiles", axis="x", overhang=1, pitch=1,
                 gable_state=F("spruce_planks"))
    K.clear(w, ax0 + 4, 1, az0, ax0 + 5, 3, az0)
    h.stations_row(w, [(x, az1 - 1) for x in range(ax0 + 1, ax1)], 0, "north",
                   kinds=("crafting_table", "anvil", "smithing_table", "grindstone", "stonecutter", "loom",
                          "cartography_table", "furnace"))
    for (x, kind) in ((ax0 + 1, "blast_furnace"), (ax0 + 2, "smoker"), (ax1 - 1, "brewing_stand"),
                      (ax1 - 2, "fletching_table")):
        h.station(w, x, 1, az0 + 1, kind, "south")
    K.hearth(w, ax0, 0, (az0 + az1) // 2, "east", stone="stone_bricks", chimney_top=8)
    K.lamp(w, ax0 + 4, 5, az0 + 2)
    # the north tower: the enchanting nook on its upper floor
    tx, tz = cx0, cz0
    S.cylinder(w, tx, tz, 2.4, 1, 15, AIR)
    S.cylinder(w, tx, tz, 3.4, 9, 9, pk)
    h.enchanting_nook(w, tx, 9, tz, open_side="south")
    for y in range(1, 10):
        w.set(tx + 2, y, tz + 2, AIR)
    for y in range(1, 10):
        w.set(tx + 1, y, tz + 3, B.S("ladder", facing="south"))
    K.clear(w, tx + 1, 1, tz + 3, tx + 1, 2, tz + 3)
    for y in range(1, 10):
        w.set(tx + 1, y, tz + 3, B.S("ladder", facing="south"))
        w.set(tx + 1, y, tz + 2, pk) if y < 9 else None
    K.clear(w, tx + 1, 1, tz + 4, tx + 1, 2, tz + 4)
    # ------------------------------------------------------------------ the frozen lake: dummies, bell, spruces
    for (x, z) in sorted(cells):
        if 18 <= x <= 25 and abs(z) <= 9:
            w.set(x, 0, z, bl if hash2(x, z, 194) < 0.5 else pk)
    K.training_ground(h, w, [(20, -6), (20, -2), (20, 2), (20, 6)], 0, 270.0, looks=(0, 1, 0, 2))
    h.target(w, 24, 0, 0)
    K.clear(w, cx1, 1, -1, cx1, 4, 1)
    for z in (-1, 1):
        w.set(cx1, 4, z, bl)
    for z in (-14, -16):
        w.fill(-6, 1, z, -6, 4, z, pk)
    w.fill(-6, 5, -16, -6, 5, -14, bl)
    h.bell(w, -6, 4, -15, facing="east", attachment="ceiling")
    for k in range(0, 18):                                               # the frozen waterfall over the edge
        for z in (-20, -19, -18):
            x = 21 + (k // 6)
            w.set(x, -k, z, bl if (k + z) % 3 else pk)
    for (x, z) in ((-18, -12), (-17, 13), (-7, -21), (-5, 21), (8, -21), (9, 20), (21, -14), (22, 15), (25, 9)):
        if (x, z) in cells:
            K.tree(w, x, 0, z, "spruce", seed=x + 5 * z, height=7)
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    yy = w.top_y(x + dx, z + dz)
                    if "leaves" in w.get(x + dx, yy, z + dz) and w.is_empty(x + dx, yy + 1, z + dz):
                        w.set(x + dx, yy + 1, z + dz, B.S("snow", layers=2))
    K.path(w, [(-17, 0), (cx0 - 1, 0)], 0, ["packed_ice", "snow_block", "blue_ice"], width=3, seed=195)
    for (x, z) in ((-15, -3), (-15, 3)):
        w.set(x, 1, z, pk)
        w.set(x, 2, z, pk)
        w.set(x, 3, z, B.S("sea_lantern"))
    return h.finish()


DESIGNS = {
    "gothic_cathedral": gothic_cathedral,
    "coral_hall": coral_hall,
    "volcanic_fortress": volcanic_fortress,
    "ice_castle": ice_castle,
}
INTERIORS = {
    "gothic_cathedral": {"nave": ((-6.0, 3.62, 0.5), (20.0, 8.0, 0.0)), "cloister": ((-2.0, 2.62, 14.0),
                                                                                (10.0, 2.0, 22.0))},
    "coral_hall": {"dome": ((1.0, -8.38, 0.5), (16.0, -6.0, 0.0)), "tube": ((-10.0, 1.62, 0.5), (0.0, -8.0, 0.0))},
    "volcanic_fortress": {"court": ((-13.0, 2.62, 1.0), (10.0, 5.0, 14.0)), "hall": ((0.5, 2.62, 7.5),
                                                                                (10.0, 4.0, 7.0))},
    "ice_castle": {"hall": ((0.5, 2.62, 0.5), (14.0, 5.0, 0.0)), "lake": ((17.0, 3.0, -9.0), (24.0, 1.0, 6.0))},
}
