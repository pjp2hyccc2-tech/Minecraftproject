"""鉄鉱の鉱山 (iron mine, Lv.15-25): the miners' camp on its terrace (timber-framed houses, the smithy with its
forge smoke, the headframe over the shaft with its turning sheave, ore piles, rails with minecarts, timber-shored
tunnel mouths in the hillsides) and the quarry (the timber stairway that takes the road down the terraces,
scaffolding and ladders, the crane at the rim, rails and carts on the terraces, ore heaps, the foreman's hut).
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import shapes as SH
from capital.noise import hash2

from .. import blocks_ext as BX
from .. import build_kit as BK
from .. import geo
from .. import kit as K
from .. import probe
from ..world import Piece

AIR = K.AIR
F = K.F
S = K.S

L = probe.layout()
CAMP = (int(L["camp"][0]), int(L["camp"][1]), int(L["camp"][2]))
QX, QZ, QR, QFLOOR = L["quarry"]
ROAD = next(r for r in L["roads"] if r["id"] == "west")["points"]
D3 = next(g for g in L["landmarks"] if g["id"] == "d3")


def minecart(P, x, y, z, yaw=0.0, kind="minecraft:minecart"):
    """A cart standing on the rail at (x, y, z)."""
    P.entity(kind, (x + 0.5, y + 0.0625, z + 0.5), yaw, "{Motion:[0.0d,0.0d,0.0d]}")


def rail_line(P, pts, y_of=None, carts=(), seed=0):
    """Rails along a chain of axis-aligned segments at the ground (or ``y_of``); minecarts at the given indices."""
    cells = []
    for i in range(len(pts) - 1):
        (ax, az), (bx, bz) = pts[i], pts[i + 1]
        n = max(abs(bx - ax), abs(bz - az))
        for k in range(n + (1 if i == len(pts) - 2 else 0)):
            cells.append((ax + (bx - ax) * k // n if n else ax, az + (bz - az) * k // n if n else az))
    out = []
    for k, (x, z) in enumerate(cells):
        y = y_of(x, z) if y_of else K.ground_y(P, x, z) + 1
        prev = cells[k - 1] if k > 0 else None
        nxt = cells[k + 1] if k + 1 < len(cells) else None
        dirs = set()
        for o in (prev, nxt):
            if o is None:
                continue
            dx, dz = o[0] - x, o[1] - z
            dirs.add("east" if dx > 0 else "west" if dx < 0 else "south" if dz > 0 else "north")
        if dirs <= {"north", "south"}:
            shape = "north_south"
        elif dirs <= {"east", "west"}:
            shape = "east_west"
        else:
            shape = "_".join(sorted(dirs, key=lambda d: ("south", "north", "east", "west").index(d)))
            shape = {"south_east": "south_east", "south_west": "south_west", "north_east": "north_east",
                     "north_west": "north_west"}.get(shape, "north_south")
        P.set(x, y - 1, z, F("gravel") if hash2(x, z, seed) < 0.5 else F("cobblestone"))
        P.set(x, y, z, B.S("rail", shape=shape))
        P.fill(x, y + 1, z, x, y + 2, z, AIR)
        out.append((x, y, z))
    for idx in carts:
        if 0 <= idx < len(out):
            x, y, z = out[idx]
            minecart(P, x, y, z, yaw=0.0, kind="minecraft:chest_minecart" if idx % 2 else "minecraft:minecart")
    return out


def ore_pile(P, x, z, r=1.6, seed=0, kinds=("raw_iron_block", "cobblestone", "gravel", "coal_block", "andesite")):
    y = K.ground_y(P, x, z)
    R = int(math.ceil(r))
    for dx in range(-R, R + 1):
        for dz in range(-R, R + 1):
            h = r + 0.4 - math.hypot(dx, dz)
            for k in range(int(round(h))):
                kk = kinds[int(hash2(x + dx, z + dz + k * 7, seed) * len(kinds))]
                P.set(x + dx, y + 1 + k, z + dz, F(kk))


def tunnel(P, x, z, direction, floor, length=12, width=3, height=4, rails=True, cart=True, seed=0):
    """A timber-shored tunnel mouth driven into the hillside from (x, z) towards ``direction``."""
    dx, dz = K.DIRS[direction]
    sx, sz = K.DIRS[K.CW[direction]]
    hw = width // 2
    # the cutting in front of the mouth
    for i in range(-5, 1):
        for s in range(-hw - 2, hw + 3):
            px, pz = x + dx * i + sx * s, z + dz * i + sz * s
            t = P.top(px, pz)
            if t > floor:
                P.fill(px, floor + 1, pz, px, t + 1, pz, AIR)
                P.set(px, floor, pz, F("gravel") if abs(s) <= hw else F("coarse_dirt"))
    for i in range(0, length):
        for s in range(-hw - 1, hw + 2):
            px, pz = x + dx * i + sx * s, z + dz * i + sz * s
            if abs(s) <= hw:
                P.fill(px, floor + 1, pz, px, floor + height, pz, AIR)
                P.set(px, floor, pz, F("gravel") if hash2(px, pz, seed) < 0.6 else F("cobblestone"))
            # ore glinting in the walls and the roof
            for yy in range(floor + 1, floor + height + 2):
                if abs(s) == hw + 1 or yy == floor + height + 1:
                    if hash2(px, yy, pz + seed) < 0.08:
                        P.set(px, yy, pz, F("iron_ore") if hash2(pz, yy, px) < 0.6 else F("coal_ore"))
        if i % 3 == 0:
            for s in (-hw, hw):
                px, pz = x + dx * i + sx * s, z + dz * i + sz * s
                P.fill(px, floor + 1, pz, px, floor + height - 1, pz, B.log("spruce"))
            for s in range(-hw, hw + 1):
                px, pz = x + dx * i + sx * s, z + dz * i + sz * s
                P.set(px, floor + height, pz, B.log("spruce", "x" if sx else "z"))
            if i % 6 == 3:
                P.set(x + dx * i, floor + height - 1, z + dz * i, B.lantern(hanging=True))
    # a heavier portal frame at the mouth
    for s in (-hw - 1, hw + 1):
        px, pz = x + sx * s, z + sz * s
        P.fill(px, floor + 1, pz, px, floor + height, pz, B.log("dark_oak"))
    for s in range(-hw - 2, hw + 3):
        px, pz = x + sx * s - dx, z + sz * s - dz
        P.set(px, floor + height + 1, pz, B.stairs("dark_oak", direction, "bottom") if abs(s) > hw + 1 else
              B.slab("dark_oak", "bottom"))
        px, pz = x + sx * s, z + sz * s
        P.set(px, floor + height + 1, pz, B.log("dark_oak", "x" if sx else "z"))
    P.set(x - dx + sx * (hw + 2), floor + height - 1, z - dz + sz * (hw + 2), B.lantern(hanging=True))
    P.set(x - dx + sx * (hw + 2), floor + height, z - dz + sz * (hw + 2), B.fence("dark_oak"))
    end = (x + dx * (length - 1), z + dz * (length - 1))
    P.set(end[0], floor + 1, end[1], S("cobweb"))
    if rails:
        pts = [(x - dx * 5, z - dz * 5), (end[0] - dx, end[1] - dz)]
        rail_line(P, pts, y_of=lambda a, b: floor + 1, carts=(3,) if cart else (), seed=seed)


# ====================================================================================== the camp
def camp():
    cx, cz, cy = CAMP
    P = Piece("mine_camp", "鉱夫の野営地 (miners' camp: smithy, houses, headframe, tunnels)", 3, cx - 60, cz - 60, cx + 60,
              cz + 56, below=10, above=40, kind="scenery")
    y = cy
    road = K.polyline_mask(P, ROAD, 6.0)
    shrine = K.disc(P, cx, cz, 14)
    keep = road | shrine
    # smithy east of the shrine: stone ground floor, forge and chimney with heavy smoke
    sx0, sz0 = cx + 16, cz - 6
    BK.fit_ground(P, sx0, sz0, sx0 + 10, sz0 + 8, y, found="cobblestone", blend=3)
    f, W, D = BK.frame_for(P, sx0, sz0, sx0 + 10, sz0 + 8, "west", y)
    BK.house(f, W, D, BK.House(seed=31, floors=1, wall="cobblestone", ground_wall="cobblestone", timber="spruce",
                               roof="deepslate_tiles", roof_axis="x", shutters=None, chimney=None, interior=False,
                               porch=True, flowers=False))
    # open forge front: the forge, anvil, quench barrel, grindstone, chimney
    f.fill(2, 1, 0, W - 3, 2, 0, AIR)
    f.set(W - 3, 1, 2, F("bricks"))
    f.set(W - 3, 1, 3, B.S("blast_furnace", facing="north", lit=True))
    f.set(W - 2, 1, 3, B.S("lava_cauldron"))
    f.set(W - 4, 1, 3, B.S("smoker", facing="north", lit=True))
    f.set(4, 1, 2, B.S("anvil", facing="east"))
    f.set(3, 1, 3, B.S("water_cauldron", level=3))
    f.set(2, 1, 3, B.S("grindstone", face="floor", facing="north"))
    f.set(5, 1, 4, B.S("smithing_table"))
    f.fill(W - 3, 2, 3, W - 3, 9, 3, F("bricks"))
    f.set(W - 3, 10, 3, B.S("smoke_emitter", heavy=True))
    f.set((W - 1) // 2, 3, (D - 1) // 2, B.lantern(hanging=True))
    for k in range(3):
        f.set(1 + k, 1, D - 2, B.barrel("up"))
    # three timber-framed houses round the camp
    lots = [(cx - 30, cz + 10, cx - 22, cz + 17, "east", 32), (cx - 14, cz + 20, cx - 6, cz + 27, "north", 33),
            (cx + 6, cz + 20, cx + 14, cz + 27, "north", 34), (cx + 18, cz + 14, cx + 25, cz + 21, "west", 35)]
    for (a, b, c, d, face, sd) in lots:
        if (K.rect(P, a - 1, b - 1, c + 1, d + 1) & keep).any():
            continue
        BK.fit_ground(P, a, b, c, d, y, found="cobblestone", blend=3)
        ff, WW, DD = BK.frame_for(P, a, b, c, d, face, y)
        BK.house(ff, WW, DD, BK.House.pick(sd, roofs=("spruce", "dark_oak", "deepslate_tiles"), floors=1,
                                          wall="white_plaster", ground_wall="cobblestone", jetty=False))
    # the headframe over the shaft west of the shrine, its sheave wheel turning, an ore bin beside it
    hx, hz = cx - 22, cz - 4
    for (a, b) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        P.fill(hx + a, y + 1, hz + b, hx + a, y + 13, hz + b, B.log("spruce"))
    for yy in (y + 5, y + 9, y + 13):
        SH.rect_ring(P, hx - 2, hz - 2, hx + 2, hz + 2, yy, B.log("spruce", "x"))
    P.fill(hx - 2, y + 14, hz - 2, hx + 2, y + 14, hz + 2, B.slab("spruce", "bottom"))
    P.fill(hx - 1, y - 12, hz - 1, hx + 1, y, hz + 1, AIR)           # the shaft
    SH.rect_ring(P, hx - 1, hz - 1, hx + 1, hz + 1, y, B.log("spruce", "x"))
    for yy in range(y - 12, y + 1):
        P.set(hx, yy, hz - 1, B.S("ladder", facing="south"))
    P.set(hx, y - 12, hz, F("iron_ore"))
    P.set(hx, y + 12, hz, B.chain())
    for yy in range(y + 2, y + 12):
        P.set(hx, yy, hz, B.chain())
    P.set(hx, y + 13, hz, F("spruce_planks"))
    P.set(hx + 3, y + 13, hz, B.S("water_wheel", facing="east"))
    P.be(hx + 3, y + 13, hz, BX.wheel_nbt(1.8, 0.6))
    for (a, b) in ((-4, 3), (-4, 4), (-5, 4), (-3, 4)):
        ore_pile(P, hx + a, hz + b, r=1.4, seed=a * 7 + b)
    # rails from the headframe across the terrace to the tunnel in the north-west hill, carts on them
    rail_line(P, [(hx - 3, hz + 3), (hx - 3, hz - 12), (hx - 12, hz - 12)], y_of=lambda a, b: y + 1, carts=(2, 9),
              seed=2)
    # tunnel mouths in the hillsides
    tunnel(P, cx + 27, cz + 42, "south", y, length=12, seed=1)
    tunnel(P, cx - 40, cz - 22, "west", y, length=12, seed=2)
    # ore piles, tool racks, the camp sign, lanterns, a crate store
    for (a, b, r) in ((10, -14, 1.8), (14, -12, 1.5), (-6, 16, 1.5)):
        ore_pile(P, cx + a, cz + b, r=r, seed=a + b)
    for (a, b) in ((-2, -18), (2, -18), (-16, -10), (16, 10), (-10, 14)):
        x, z = cx + a, cz + b
        if not keep[x - P.X0, z - P.Z0]:
            K.lantern_post(P, x, K.ground_y(P, x, z), z, height=3, wood="spruce")
    for (a, b, st) in ((24, -12, B.barrel("up")), (25, -12, B.barrel("up")), (24, -11, F("spruce_planks")),
                       (25, -11, B.barrel("east")), (24, -10, F("spruce_planks"))):
        P.set(cx + a, y + 1, cz + b, st)
    K.place_sign(P, cx + 7, y + 1, cz - 20, ['{"translate":"sign.eternalrealm.mining_camp","bold":true}',
                                              K.field_level(3)], wood="spruce", rotation=8)
    P.probe(hx - 2, y + 1, hz - 2)
    P.view("mine_camp", (cx + 56, y + 40, cz + 62), (cx, y + 4, cz), fov=55)
    return P


# ====================================================================================== the quarry
def ring_height(d):
    if d < 18:
        return int(QFLOOR)
    return int(QFLOOR + 7 * math.floor((d - 18) / 11))


def quarry():
    P = Piece("mine_quarry", "採石場 (quarry: stairway, scaffolding, crane, rails)", 3, int(QX - 104), int(QZ - 104),
              int(QX + 90), int(QZ + 70), below=8, above=34, kind="scenery")
    gate_keep = K.disc(P, D3["x"], D3["z"], 22)
    approach = K.rect(P, D3["x"], D3["z"] - 6, D3["x"] + 70, D3["z"] + 6)
    # the stairway: where the road crosses each terrace edge, a timber flight down to the next terrace
    flights = []
    for Rb in (62, 51, 40, 29):
        # the point of the road at that distance from the centre
        best = None
        for s in np.arange(0, geo.polyline_length(ROAD), 0.5):
            x, z, ux, uz = geo.point_at(ROAD, s)
            d = math.hypot(x - QX, z - QZ)
            if best is None or abs(d - Rb) < abs(best[0] - Rb):
                best = (d, x, z)
        _, x, z = best
        flights.append((Rb, x, z))
    for (Rb, x, z) in flights:
        # radial unit vector inwards
        vx, vz = (QX - x), (QZ - z)
        n = math.hypot(vx, vz)
        vx, vz = vx / n, vz / n
        direction = K.facing_of(vx, vz)                  # the stairs descend towards the centre
        dx, dz = K.DIRS[direction]
        sx, sz = K.DIRS[K.CW[direction]]
        hu = ring_height(Rb + 0.5)                       # upper terrace
        x0, z0 = int(round(QX - vx * (Rb + 0.5))), int(round(QZ - vz * (Rb + 0.5)))
        for i in range(0, 9):
            px, pz = x0 + dx * i, z0 + dz * i
            step_top = hu - i
            for s in (-1, 0, 1):
                qx, qz = px + sx * s, pz + sz * s
                if i == 0:
                    P.fill(qx, step_top + 1, qz, qx, step_top + 4, qz, AIR)
                    P.set(qx, step_top, qz, F("spruce_planks"))
                    continue
                if step_top < hu - 7:
                    break
                tg = P.top(qx, qz)
                P.fill(qx, min(tg, step_top - 1), qz, qx, step_top - 1, qz, F("spruce_planks") if i % 3 == 0 else AIR)
                P.set(qx, step_top, qz, B.stairs("spruce", K.OPP[direction]))
                P.fill(qx, step_top + 1, qz, qx, step_top + 4, qz, AIR)
            # posts and railings
            for s in (-2, 2):
                qx, qz = px + sx * s, pz + sz * s
                if step_top >= hu - 7:
                    tg = P.top(qx, qz)
                    if i % 3 == 0:
                        P.fill(qx, min(tg, step_top) - 1, qz, qx, step_top + 1, qz, B.log("spruce"))
                        if i % 6 == 0:
                            P.set(qx, step_top + 2, qz, B.lantern())
                    else:
                        P.set(qx, step_top, qz, F("spruce_planks"))
                        P.set(qx, step_top + 1, qz, B.fence("spruce"))
        # the landing at the foot of the flight
        lx, lz = x0 + dx * 8, z0 + dz * 8
        for a in (-1, 0, 1):
            for b in (0, 1):
                P.set(lx + sx * a + dx * b, hu - 7, lz + sz * a + dz * b, F("spruce_planks"))
    # scaffolding with ladders on two other faces of the terraces
    for ang, Rb in ((200, 51), (300, 40)):
        a = math.radians(ang)
        x0, z0 = int(round(QX + math.cos(a) * (Rb - 1.5))), int(round(QZ + math.sin(a) * (Rb - 1.5)))
        if gate_keep[x0 - P.X0, z0 - P.Z0]:
            continue
        hl = ring_height(Rb - 2)
        for (sxo, szo) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            for yy in range(hl + 1, hl + 9):
                P.set(x0 + sxo, yy, z0 + szo, B.S("scaffolding", bottom=False, distance=0))
    # the crane at the rim, rails and carts along the lower terraces, ore heaps, the foreman's hut
    ca = math.radians(75)
    crx, crz = int(round(QX + math.cos(ca) * 66)), int(round(QZ + math.sin(ca) * 66))
    cy = ring_height(66)
    from .e_harbour import crane
    crane(P, crx, cy, crz, K.facing_of(QX - crx, QZ - crz))
    pts = []
    for k in range(0, 12):
        a = math.radians(150 + k * 9)
        pts.append((int(round(QX + math.cos(a) * 34)), int(round(QZ + math.sin(a) * 34))))
    seg = [pts[0]]
    for p in pts[1:]:
        last = seg[-1]
        seg.append((p[0], last[1]))
        seg.append(p)
    clean = [seg[0]]
    for p in seg[1:]:
        if p != clean[-1]:
            clean.append(p)
    rail_line(P, clean, y_of=lambda a, b: ring_height(math.hypot(a - QX, b - QZ)) + 1, carts=(5, 14, 23), seed=4)
    for (ang, Rr) in ((120, 24), (170, 35), (250, 46), (20, 46), (330, 24)):
        a = math.radians(ang)
        x, z = int(round(QX + math.cos(a) * Rr)), int(round(QZ + math.sin(a) * Rr))
        if not gate_keep[x - P.X0, z - P.Z0] and not approach[x - P.X0, z - P.Z0]:
            ore_pile(P, x, z, r=1.7, seed=ang)
    hx, hz = int(QX - 8), int(QZ - 10)
    yh = QFLOOR
    if not (K.rect(P, hx - 2, hz - 2, hx + 8, hz + 7) & (gate_keep | approach)).any():
        BK.fit_ground(P, hx, hz, hx + 6, hz + 5, yh, found="cobblestone", blend=0, margin=0)
        f, W, D = BK.frame_for(P, hx, hz, hx + 6, hz + 5, "east", yh)
        BK.house(f, W, D, BK.House(seed=37, floors=1, wall="spruce_planks", timber="spruce", roof="spruce",
                                   roof_axis="x", shutters=None, chimney="cobblestone", interior=True,
                                   ground_wall="cobblestone"))
    K.place_sign(P, int(round(flights[0][1])), ring_height(64) + 1,
                 int(round(flights[0][2])), ['{"translate":"sign.eternalrealm.quarry","bold":true}',
                                             '{"translate":"landmark.eternalrealm.d3"}'], wood="spruce",
                 rotation=K.rotation_of(-(QX - flights[0][1]), -(QZ - flights[0][2])))
    P.probe(int(round(flights[1][1])), ring_height(51.5), int(round(flights[1][2])))
    P.view("mine_quarry", (QX + 100, 150, QZ + 95), (QX, 80, QZ), fov=55)
    P.view("mine_quarry_stairs", (flights[1][1] + 28, ring_height(51) + 20, flights[1][2] - 10),
           (flights[1][1], ring_height(51), flights[1][2]), fov=60)
    return P


def build(only=None):
    out = []
    for pid, fn in (("mine_camp", camp), ("mine_quarry", quarry)):
        if not only or pid in only:
            out.append(fn())
    return out
