"""Buildings of the countryside, drawn in a local ``Frame`` (capital.world.Frame over a Piece).

Convention for buildings: local x runs along the front wall (0 .. W-1), local z into the plot (0 .. D-1), the front
wall is at z = 0 and faces local north (-z), the ground-floor block is at local y = 0 (people stand at y = 1).
``frame_for(P, x0, z0, x1, z1, front, y)`` maps such a frame onto a world rectangle whose front faces a world
direction. Keep a couple of blocks free around a building for eaves, steps and the chimney.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import shapes as SH
from capital.noise import hash2, hash3
from capital.world import Frame

from . import blocks_ext as BX
from . import kit as K

AIR = K.AIR
F = K.F
S = K.S


def frame_for(P, x0, z0, x1, z1, front, y):
    """(frame, W, D) for the world rectangle with its front wall facing ``front``."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    if front == "north":
        return Frame(P, (x0, y, z0), 0), x1 - x0 + 1, z1 - z0 + 1
    if front == "south":
        return Frame(P, (x1, y, z1), 2), x1 - x0 + 1, z1 - z0 + 1
    if front == "east":
        return Frame(P, (x1, y, z0), 1), z1 - z0 + 1, x1 - x0 + 1
    return Frame(P, (x0, y, z1), 3), z1 - z0 + 1, x1 - x0 + 1


def fit_ground(P, x0, z0, x1, z1, y, found="cobblestone", blend=4, margin=1, top=None):
    """Level a building plot: masonry under the floor where the ground is lower, cut where it is higher, a margin
    of levelled ground around it and the terrain eased back beyond. Returns the plot mask."""
    m = K.rect(P, x0 - margin, z0 - margin, x1 + margin, z1 + margin)
    inner = K.rect(P, x0, z0, x1, z1)
    for (x, z) in K.cells(m, P):
        t = P.top(x, z)
        if inner[x - P.X0, z - P.Z0]:
            if t < y:
                P.fill(x, t - 1, z, x, y - 1, z, F(found))
            else:
                P.fill(x, y - 2, z, x, y - 1, z, F(found))
                P.fill(x, y + 1, z, x, t + 2, z, AIR)
            P.set(x, y, z, F(found))
        else:
            K.set_ground(P, x, z, y, top(x, z) if callable(top) else top)
    if blend:
        K.blend(P, m, y, width=blend)
    return m


# ====================================================================================== cottages
class House:
    """Parameters of a rural house; ``House.pick(seed, ...)`` chooses a coherent variant."""

    def __init__(self, **kw):
        self.floors = 1
        self.fh = 4                       # storey height (floor to floor)
        self.wall = "white_plaster"       # infill
        self.ground_wall = None           # ground-floor infill (stone) if different
        self.timber = "spruce"
        self.plinth = "cobblestone"
        self.roof = "spruce"              # roof material with stairs/slabs
        self.roof_axis = "x"              # ridge along the front (x) or into the plot (z)
        self.pitch = 1
        self.overhang = 1
        self.door = "spruce"
        self.window = "glass_pane"
        self.shutters = "spruce"
        self.flowers = True
        self.chimney = "cobblestone"
        self.smoke = True
        self.interior = True
        self.porch = False
        self.lantern = True
        self.seed = 0
        self.jetty = False
        for k, v in kw.items():
            setattr(self, k, v)

    @classmethod
    def pick(cls, seed, roofs=("spruce", "dark_oak", "terracotta_roof_tiles", "deepslate_tiles"), **kw):
        r = lambda k: hash2(seed, k, 4127)  # noqa: E731
        h = cls(seed=seed)
        h.floors = 2 if r(1) < 0.45 else 1
        h.roof = roofs[int(r(2) * len(roofs))]
        h.roof_axis = "x" if r(3) < 0.65 else "z"
        h.wall = ("white_plaster", "white_plaster", "birch_planks", "oak_planks", "white_plaster")[int(r(4) * 5)]
        h.timber = ("spruce", "dark_oak", "oak", "spruce")[int(r(5) * 4)]
        h.ground_wall = ("cobblestone", None, "stone_bricks", None, "mossy_cobblestone")[int(r(6) * 5)]
        h.shutters = (None, "spruce", "dark_oak", "oak", "spruce")[int(r(7) * 5)]
        h.door = ("spruce", "oak", "dark_oak", "spruce")[int(r(8) * 4)]
        h.chimney = ("cobblestone", "stone_bricks", "bricks", None)[int(r(9) * 4)]
        h.porch = r(10) < 0.35
        h.jetty = h.floors == 2 and r(11) < 0.5
        for k, v in kw.items():
            setattr(h, k, v)
        return h


def _infill(name):
    return F(name)


def house(f, W, D, h: House):
    """Draw a cottage in frame ``f`` (see module conventions). Returns anchors: door (x, y, z), ridge y."""
    fh, nf = h.fh, h.floors
    eave = fh * nf
    tim = B.log(h.timber)
    tim_x, tim_z = B.log(h.timber, "x"), B.log(h.timber, "z")
    infill = _infill(h.wall)
    ground_inf = _infill(h.ground_wall) if h.ground_wall else infill
    seed = h.seed
    r = lambda k: hash2(seed, k, 911)  # noqa: E731
    jet = 1 if (h.jetty and nf >= 2) else 0
    # shell
    f.fill(0, 1, -jet, W - 1, eave, D - 1 + jet, AIR)
    f.fill(0, 0, 0, W - 1, 0, D - 1, F("spruce_planks" if r(1) < 0.6 else "oak_planks"))
    f.fill(0, 0, 0, W - 1, 0, 0, F(h.plinth))
    f.fill(0, 0, D - 1, W - 1, 0, D - 1, F(h.plinth))
    f.fill(0, 0, 0, 0, 0, D - 1, F(h.plinth))
    f.fill(W - 1, 0, 0, W - 1, 0, D - 1, F(h.plinth))
    for k in range(nf):
        y0 = k * fh
        zj = -jet if k >= 1 else 0
        z1j = D - 1 + (jet if k >= 1 else 0)
        st = ground_inf if k == 0 else infill
        SH.walls(f, 0, zj, W - 1, z1j, y0 + 1, y0 + fh - 1, st)
        if k >= 1:
            f.fill(1, y0, zj + 1, W - 2, y0, z1j - 1, F("spruce_planks"))
        # timber posts at corners and every 3rd block (only on plaster/plank walls)
        if st == infill or k == 0 and h.ground_wall is None:
            for x in range(W):
                if x in (0, W - 1) or x % 3 == 0:
                    f.fill(x, y0 + 1, zj, x, y0 + fh - 1, zj, tim)
                    f.fill(x, y0 + 1, z1j, x, y0 + fh - 1, z1j, tim)
            for z in range(zj, z1j + 1):
                if z in (zj, z1j) or (z - zj) % 3 == 0:
                    f.fill(0, y0 + 1, z, 0, y0 + fh - 1, z, tim)
                    f.fill(W - 1, y0 + 1, z, W - 1, y0 + fh - 1, z, tim)
        else:
            for (x, z) in ((0, zj), (W - 1, zj), (0, z1j), (W - 1, z1j)):
                f.fill(x, y0 + 1, z, x, y0 + fh - 1, z, tim)
        # top plate
        f.fill(0, y0 + fh, zj, W - 1, y0 + fh, zj, tim_x)
        f.fill(0, y0 + fh, z1j, W - 1, y0 + fh, z1j, tim_x)
        f.fill(0, y0 + fh, zj, 0, y0 + fh, z1j, tim_z)
        f.fill(W - 1, y0 + fh, zj, W - 1, y0 + fh, z1j, tim_z)
        if jet and k == 1:
            for x in range(W):
                if x in (0, W - 1) or x % 3 == 0:
                    f.set(x, y0 - 1 + 0, -1, B.stairs(h.timber, "south", "top"))
                    f.set(x, y0 - 1 + 0, D, B.stairs(h.timber, "north", "top"))
    f.fill(1, eave, 1 - jet, W - 2, eave, D - 2 + jet, F("spruce_planks"))
    # door, step, porch
    door_x = W // 2 if W % 2 else W // 2 - (1 if r(2) < 0.5 else 0)
    lo, hi = B.door_states(h.door, "south", "left" if r(3) < 0.5 else "right")
    f.set(door_x, 1, 0, lo)
    f.set(door_x, 2, 0, hi)
    f.set(door_x, 0, -1, B.stairs("cobblestone" if h.plinth == "cobblestone" else "stone_bricks", "south"))
    if h.porch:
        for x in range(door_x - 1, door_x + 2):
            f.set(x, 3, -1, B.stairs(h.roof, "south", "bottom") if B.has_stairs(h.roof) else B.slab("spruce", "bottom"))
    if h.lantern:
        lx = door_x + 1 if door_x + 1 <= W - 2 else door_x - 1
        if h.porch:
            f.set(lx, 2, -1, B.lantern(hanging=True))
        else:
            f.set(lx, 3, -1, B.lantern(hanging=True))
    # windows
    pane = B.S(h.window)

    def window(x, y, z, out):
        f.set(x, y, z, pane)
        if h.shutters:
            sx, sz = K.DIRS[K.CW[out]]
            for s in (1, -1):
                px, pz = x + sx * s, z + sz * s
                if f.get(px, y, pz) in (B.canon(infill), B.canon(ground_inf)):
                    ox, oz = K.DIRS[out]
                    f.set(px + ox, y, pz + oz, B.trapdoor(h.shutters, out, "bottom", True))
        if h.flowers and y % fh == 2:
            ox, oz = K.DIRS[out]
            if f.get(x + ox, y - 1, z + oz) == "minecraft:air" or f.get(x + ox, y - 1, z + oz) == "eternalrealm:void":
                f.set(x + ox, y - 1, z + oz, B.trapdoor("spruce", K.OPP[out], "top", False))
                f.set(x + ox, y, z + oz, B.potted(("red_tulip", "cornflower", "allium", "white_tulip",
                                                    "azure_bluet", "poppy")[int(hash2(x, z, seed + y) * 6)]))

    for k in range(nf):
        y0 = k * fh
        zj = -jet if k >= 1 else 0
        z1j = D - 1 + (jet if k >= 1 else 0)
        for x in range(1, W - 1):
            if x % 3 == 0 or (k == 0 and abs(x - door_x) <= 1):
                continue
            if (x % 3 == 1 and W > 5) or W <= 5:
                window(x, y0 + 2, zj, "north")
            if x % 3 == 2 or W <= 5:
                window(x, y0 + 2, z1j, "south")
        for z in range(zj + 1, z1j):
            if (z - zj) % 3 == 0:
                continue
            if (z - zj) % 3 == 1:
                window(0, y0 + 2, z, "west")
                window(W - 1, y0 + 2, z, "east")
    # roof
    if h.roof_axis == "x":
        ridge = SH.gable_roof(f, 0, -jet, W - 1, D - 1 + jet, eave + 1, h.roof, axis="x", overhang=h.overhang,
                              pitch=h.pitch, gable_state=infill, end_overhang=1)
        gx = [0, W - 1]
        for x in gx:
            gy = eave + 2
            mid = (D - 1) // 2
            if f.get(x, gy, mid) == B.canon(infill):
                f.set(x, gy, mid, pane)
    else:
        ridge = SH.gable_roof(f, 0, -jet, W - 1, D - 1 + jet, eave + 1, h.roof, axis="z", overhang=h.overhang,
                              pitch=h.pitch, gable_state=infill, end_overhang=1)
        mid = (W - 1) // 2
        for z in (-jet, D - 1 + jet):
            if f.get(mid, eave + 2, z) == B.canon(infill):
                f.set(mid, eave + 2, z, pane)
            # timber on the gable
            for y in range(eave + 1, ridge + 1):
                if f.get(mid, y, z) == B.canon(infill) and y > eave + 2:
                    f.set(mid, y, z, tim)
    # chimney with smoke
    if h.chimney:
        cx = W - 2 if r(4) < 0.5 else 1
        cz = D - 2
        top = ridge + 2
        f.fill(cx, 1, cz, cx, top, cz, F(h.chimney))
        if h.smoke:
            f.set(cx, top + 1, cz, B.S("smoke_emitter", heavy=False))
        if h.interior:
            # hearth inside at the chimney foot
            f.set(cx, 1, cz, S("campfire", facing="north", lit=True, signal_fire=False))
            f.set(cx, 2, cz, F(h.chimney))
    if h.interior:
        _interior(f, W, D, h, door_x)
    return {"door": (door_x, 1, -1), "ridge": ridge, "eave": eave}


def _interior(f, W, D, h, door_x):
    seed = h.seed
    r = lambda k: hash2(seed, k, 733)  # noqa: E731
    # bed in a back corner, table with chairs, chest/barrel, lantern from the ceiling, carpet
    bx = 1 if r(1) < 0.5 else W - 2
    if D >= 5 and W >= 5:
        foot, head = B.bed_states(("red", "blue", "green", "yellow", "white")[int(r(2) * 5)], "south")
        if f.get(bx, 1, D - 3) == "minecraft:air" and f.get(bx, 1, D - 2) == "minecraft:air":
            f.set(bx, 1, D - 3, foot)
            f.set(bx, 1, D - 2, head)
    tx = W - 2 if bx == 1 else 1
    if W >= 5 and D >= 5:
        f.set(tx, 1, 2, B.fence("spruce"))
        f.set(tx, 2, 2, S("spruce_pressure_plate"))
        if f.get(tx, 1, 1) == "minecraft:air":
            f.set(tx, 1, 1, B.stairs("spruce", "north"))
        if f.get(tx, 1, D - 2) == "minecraft:air":
            f.set(tx, 1, D - 2, B.barrel("up"))
    if W >= 5:
        f.set((W - 1) // 2, h.fh - 1, (D - 1) // 2, B.lantern(hanging=True))
        cx0, cx1 = max(1, door_x - 1), min(W - 2, door_x + 1)
        for x in range(cx0, cx1 + 1):
            for z in range(1, max(2, D - 3)):
                if f.get(x, 1, z) == "minecraft:air":
                    f.set(x, 1, z, B.carpet(("red", "brown", "green", "blue")[int(r(3) * 4)]))
    if h.floors >= 2:
        # ladder up in a corner
        lx = W - 2 if bx == 1 else 1
        for y in range(1, h.fh + 1):
            f.set(lx, y, D - 2, B.S("ladder", facing="north"))
        f.set(lx, h.fh, D - 2, B.S("ladder", facing="north"))


# ====================================================================================== barns, silos, sheds
def barn(f, W, D, wall="spruce_planks", frame="dark_oak", roof="dark_oak", red=False, seed=0):
    """A big barn: log frame, plank walls (red mangrove if ``red``), tall double doors in the front gable,
    a hay loft and a steep gable roof with the ridge into the plot."""
    wall = "mangrove_planks" if red else wall
    H = 6
    f.fill(0, 1, 0, W - 1, H + W // 2 + 2, D - 1, AIR)
    f.fill(0, 0, 0, W - 1, 0, D - 1, F("cobblestone"))
    f.fill(1, 0, 1, W - 2, 0, D - 2, F("coarse_dirt"))
    SH.walls(f, 0, 0, W - 1, D - 1, 1, H, F(wall))
    for x in range(W):
        if x in (0, W - 1) or x % 4 == 0:
            f.fill(x, 1, 0, x, H, 0, B.log(frame))
            f.fill(x, 1, D - 1, x, H, D - 1, B.log(frame))
    for z in range(D):
        if z in (0, D - 1) or z % 4 == 0:
            f.fill(0, 1, z, 0, H, z, B.log(frame))
            f.fill(W - 1, 1, z, W - 1, H, z, B.log(frame))
    f.fill(0, H, 0, W - 1, H, 0, B.log(frame, "x"))
    f.fill(0, H, D - 1, W - 1, H, D - 1, B.log(frame, "x"))
    f.fill(0, H, 0, 0, H, D - 1, B.log(frame, "z"))
    f.fill(W - 1, H, 0, W - 1, H, D - 1, B.log(frame, "z"))
    # loft floor over the back half
    f.fill(1, 4, D // 2, W - 2, 4, D - 2, F("spruce_planks"))
    for x in range(1, W - 1):
        for z in range(D // 2, D - 1):
            if hash2(x, z, seed) < 0.55:
                f.set(x, 5, z, B.S("hay_block", axis="x" if hash2(z, x, seed) < 0.5 else "z"))
    # double doors (open, wide) in the front
    c = W // 2
    for x in range(c - 1, c + 1 + (W % 2 == 0 and 0)):
        f.fill(x, 1, 0, x, 4, 0, AIR)
    f.fill(c - 1, 5, 0, c + 1, 5, 0, B.log(frame, "x"))
    # side windows (small)
    for z in range(2, D - 2, 4):
        f.set(0, 3, z, B.S("glass_pane"))
        f.set(W - 1, 3, z, B.S("glass_pane"))
    ridge = SH.gable_roof(f, 0, 0, W - 1, D - 1, H + 1, roof, axis="z", overhang=1, pitch=1, gable_state=F(wall),
                          end_overhang=1)
    # loft door in the front gable with a hoist beam
    f.set(c, H + 2, 0, AIR)
    f.set(c, H + 3, 0, AIR)
    f.set(c, ridge - 1, -1, B.log(frame, "z"))
    f.set(c, ridge - 2, -1, B.chain())
    # inside: stalls and hay
    for x in (1, W - 2):
        for z in range(2, D // 2):
            if z % 3 == 0:
                f.set(x, 1, z, B.fence("spruce"))
    f.set(W - 2, 1, 1, B.S("hay_block", axis="y"))
    f.set(1, 1, 1, B.barrel("up"))
    f.set(c, H - 1, D // 2 - 1, B.lantern(hanging=True))
    return ridge


def silo(P, cx, y, cz, r=2.5, h=12, wall="bricks", roof="waxed_oxidized_cut_copper"):
    """Round silo standing on the block at y: brick drum with bands, conical copper roof."""
    SH.cylinder(P, cx, cz, r, y, y + h, F(wall), hollow=True, thickness=1.2)
    SH.cylinder(P, cx, cz, r - 1.0, y + 1, y + h, AIR)
    SH.cylinder(P, cx, cz, r - 0.6, y, y, F("cobblestone"))
    for yy in range(y + 3, y + h, 4):
        SH.cylinder(P, cx, cz, r, yy, yy, F("polished_andesite"), hollow=True, thickness=1.2)
    SH.cone(P, cx, cz, r + 0.8, y + h + 1, int(r * 2) + 2, roof, finial=False)
    P.set(cx, y + h + int(r * 2) + 3, cz, B.S("lightning_rod", facing="up"))


def shed(f, W, D, wall="spruce_planks", roof="spruce", open_front=True):
    """Lean-to shed: three walls, a slanting roof falling to the back."""
    H = 3
    f.fill(0, 1, 0, W - 1, H + 1, D - 1, AIR)
    f.fill(0, 0, 0, W - 1, 0, D - 1, F("coarse_dirt"))
    for z in range(D):
        hz = H + 1 if z == 0 else H
        f.fill(0, 1, z, 0, hz, z, F(wall))
        f.fill(W - 1, 1, z, W - 1, hz, z, F(wall))
    f.fill(0, 1, D - 1, W - 1, H, D - 1, F(wall))
    for x in (0, W - 1):
        f.fill(x, 1, 0, x, H + 1, 0, B.log("spruce"))
    if not open_front:
        f.fill(1, 1, 0, W - 2, H, 0, F(wall))
    for x in range(-1, W + 1):
        f.set(x, H + 2, -1, B.slab(roof, "bottom"))
        for z in range(0, D + 1):
            f.set(x, H + 1 if z < D // 2 + 1 else H, z, B.slab(roof, "top" if z < D // 2 + 1 else "top"))


# ====================================================================================== towers, windmills
def windmill(P, cx, y, cz, facing="south", r=3.0, h=12, body="white_plaster", base="cobblestone", roof="dark_oak",
             sails=7.0, seed=0, band="dark_oak_planks"):
    """Tower windmill standing on the block at y: a stone footing, a round body tapering from r + 1.6 to r with
    timber bands and small windows, a door, a dark conical cap, and the turning sails
    (``eternalrealm:windmill_sails``) on an axle housing that sticks out of the cap towards ``facing`` far enough
    for the sail disc to clear the tower."""
    fx, fz = K.DIRS[facing]
    rb = r + 1.6
    for k in range(h):
        rr = rb - (rb - r) * (k / max(1, h - 1))
        if k < 3:
            st = F(base)
        elif k % 4 == 3:
            st = F(band)
        else:
            st = F(body)
        SH.cylinder(P, cx, cz, rr, y + 1 + k, y + 1 + k, st, hollow=True, thickness=1.4)
        SH.cylinder(P, cx, cz, rr - 1.3, y + 1 + k, y + 1 + k, AIR)
    SH.cylinder(P, cx, cz, rb + 0.6, y, y, F(base))
    SH.cylinder(P, cx, cz, rb + 0.6, y + 1, y + 1, F(base), hollow=True, thickness=1.0)
    # small windows round the body
    for k, ang in ((5, 0.6), (5, 3.7), (9, 2.2), (9, 5.2)):
        rr = rb - (rb - r) * (k / max(1, h - 1))
        wx, wz = cx + round((rr - 0.3) * math.cos(ang)), cz + round((rr - 0.3) * math.sin(ang))
        P.set(wx, y + 1 + k, wz, B.S("glass_pane"))
    # door on the side opposite the sails
    bx, bz = K.DIRS[K.OPP[facing]]
    dxp, dzp = cx + round((rb + 0.3) * bx), cz + round((rb + 0.3) * bz)
    lo, hi = B.door_states("spruce", K.OPP[facing], "left")
    P.set(dxp, y + 2, dzp, lo)
    P.set(dxp, y + 3, dzp, hi)
    P.set(dxp, y + 1, dzp, F(base))
    P.set(dxp + bx, y + 1, dzp + bz, B.stairs("cobblestone", facing, "bottom"))
    # floors inside
    for yy in (y + 5, y + 9):
        if yy < y + h:
            SH.cylinder(P, cx, cz, rb - 1.6, yy, yy, F("spruce_planks"))
            P.set(cx, yy, cz, AIR)
    # cap
    top = y + h
    SH.cylinder(P, cx, cz, r + 0.8, top, top, F(f"{roof}_planks"))
    SH.cone(P, cx, cz, r + 1.6, top + 1, int(r) + 5, roof, finial=False, profile=1.15)
    P.set(cx, top + int(r) + 6, cz, B.fence(roof))
    # axle housing towards the sails
    reach = int(math.ceil(r + 1.6)) + 1
    for i in range(1, reach + 1):
        P.set(cx + fx * i, top + 1, cz + fz * i, F(f"{roof}_planks"))
        P.set(cx + fx * i, top + 2, cz + fz * i, B.slab(roof, "bottom"))
    hub = (cx + fx * (reach + 1), top + 1, cz + fz * (reach + 1))
    P.set(*hub, B.S("windmill_sails", facing=facing))
    P.be(*hub, BX.sails_nbt(sails))
    # keep the sail disc clear: radius ``sails`` in the plane 1..2 blocks in front of the hub
    ax, az = abs(fx), abs(fz)
    R = int(math.ceil(sails)) + 1
    for d in (1, 2):
        px, pz = hub[0] + fx * d, hub[2] + fz * d
        for a in range(-R, R + 1):
            for b in range(-R, R + 1):
                if a * a + b * b <= (sails + 0.5) ** 2:
                    x = px + (a if az else 0)
                    z = pz + (a if ax else 0)
                    yy = hub[1] + b
                    if P.inside(x, yy, z) and P.get_idx(x, yy, z) == 0 and yy > P.top(x, z):
                        P.set(x, yy, z, AIR)
    return hub


def round_tower(P, cx, y, cz, r, h, wall="stone_bricks", roof="spruce", cone=True, windows=True, door=None,
                floors=(), battlement=False):
    SH.cylinder(P, cx, cz, r, y + 1, y + h, F(wall), hollow=True, thickness=1.2)
    SH.cylinder(P, cx, cz, r - 1.0, y + 1, y + h, AIR)
    SH.cylinder(P, cx, cz, r + 0.6, y, y, F(wall))
    for yy in floors:
        SH.cylinder(P, cx, cz, r - 0.8, yy, yy, F("spruce_planks"))
    if cone:
        SH.cone(P, cx, cz, r + 1.2, y + h + 1, int(r * 2) + 3, roof, finial=False)
    if battlement:
        SH.crenellate_ring(P, cx, cz, r, y + h + 1, F(wall))
    return y + h


# ====================================================================================== bridges
def deck_path(a, b):
    """Integer cells along the segment a-b (Bresenham-ish, every block)."""
    (x0, z0), (x1, z1) = a, b
    n = int(max(abs(x1 - x0), abs(z1 - z0)))
    return [(round(x0 + (x1 - x0) * i / max(n, 1)), round(z0 + (z1 - z0) * i / max(n, 1))) for i in range(n + 1)]


def bridge_cells(a, b, half):
    """Cells of a straight deck of half width ``half`` from a to b, with the parameter t (0..1) of each."""
    (ax, az), (bx, bz) = a, b
    L = math.hypot(bx - ax, bz - az)
    ux, uz = (bx - ax) / L, (bz - az) / L
    out = {}
    xs = range(int(math.floor(min(ax, bx) - half - 1)), int(math.ceil(max(ax, bx) + half + 1)) + 1)
    zs = range(int(math.floor(min(az, bz) - half - 1)), int(math.ceil(max(az, bz) + half + 1)) + 1)
    for x in xs:
        for z in zs:
            px, pz = x + 0.0 - ax, z + 0.0 - az
            t = px * ux + pz * uz
            s = -px * uz + pz * ux
            if -0.5 <= t <= L + 0.5 and abs(s) <= half + 0.01:
                out[(x, z)] = (t / L, s)
    return out, (ux, uz), L
