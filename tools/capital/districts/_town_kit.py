"""Town kit: gimmick blocks (flags, smoke, clock faces, windmill sails, water wheels, fountain jets), paving,
and the parametric half-timbered house used by every residential district.

Conventions follow ``_civic_kit`` (targets are ``World`` or ``Frame``). Houses are drawn in a local frame whose
front wall is at local z = 0 facing local north; ``frame_for(w, rect, front)`` maps such a frame onto a world
rectangle with the front facing a world direction.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2, hash3
from capital.world import DIR_VEC, OPPOSITE, Frame, left_of, right_of

from . import _civic_kit as K

AIR = "minecraft:air"
OPP = OPPOSITE
F = K.F
LEADED = B.S("leaded_glass_pane")       # diamond-lattice windows (houses, guild, inn, shops)

# ====================================================================================== gimmicks
FLAG_DESIGNS = ("kingdom", "guild", "pennant", "cathedral", "market", "harbour")


def flag(t, x, y, z, facing, design="kingdom", width=3.0, height=2.0, wind=None):
    """Animated cloth flag block at (x, y, z) (top of a pole / end of a bracket). The cloth hangs from the
    block's top edge ``height`` blocks down and streams ``width`` blocks towards ``facing``."""
    assert design in FLAG_DESIGNS
    t.set(x, y, z, B.S("flag", facing=facing))
    nbt = f'{{Width:{float(width):.1f}f,Height:{float(height):.1f}f,Design:"{design}"'
    if wind is not None:
        nbt += f",Wind:{float(wind):.1f}f"
    t.be(x, y, z, nbt + "}")


def flagpole(t, x, y, z, length, facing, design="kingdom", width=3.0, height=2.0, pole="fence", wood="dark_oak",
             wind=None):
    """Pole standing on the block at y (shaft y+1 .. y+length-1) with the flag block on top (y+length)."""
    if pole == "fence":
        st = B.fence(wood)
    elif pole == "wall":
        st = B.wall("polished_blackstone")
    elif pole == "iron":
        st = B.S("iron_bars")
    else:
        st = B.chain("y")
    for i in range(1, length):
        t.set(x, y + i, z, st)
    flag(t, x, y + length, z, facing, design, width, height, wind)
    return y + length


def smoke(t, x, y, z, heavy=False):
    """Invisible smoke emitter in the air block directly above a chimney top."""
    t.set(x, y, z, B.S("smoke_emitter", heavy=heavy))


def clock(t, x, y, z, facing, size=2.5):
    """Clock dial block set flush into a wall (facing out); the game draws the dial and hands in front."""
    t.set(x, y, z, B.S("clock_face", facing=facing))
    t.be(x, y, z, f"{{Size:{float(size):.2f}f}}")


def sails(t, x, y, z, facing, radius=7.0):
    """Windmill hub bearing set into the cap wall (facing out); four sails turn ~1 block in front."""
    t.set(x, y, z, B.S("windmill_sails", facing=facing))
    t.be(x, y, z, f"{{Radius:{float(radius):.1f}f}}")


def water_wheel(t, x, y, z, facing, radius=3.0, width=2.0):
    t.set(x, y, z, B.S("water_wheel", facing=facing))
    t.be(x, y, z, f"{{Radius:{float(radius):.1f}f,Width:{float(width):.1f}f}}")


def spray(t, x, y, z, power=2, facing="up"):
    t.set(x, y, z, B.S("fountain_spray", power=power, facing=facing))


# ====================================================================================== frames & ground
FRONT_ROT = {"north": 0, "east": 1, "south": 2, "west": 3}


def frame_for(w, rect, front, y=0):
    """Frame mapping a local box (x 0..W-1 along the front, z 0..D-1 into the plot, front wall at z = 0 facing
    local north) onto the world rectangle ``rect`` = (x0, z0, x1, z1) with the front facing ``front``.
    Returns (frame, W, D)."""
    x0, z0, x1, z1 = rect
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    if front == "north":
        return Frame(w, (x0, y, z0), 0), x1 - x0 + 1, z1 - z0 + 1
    if front == "south":
        return Frame(w, (x1, y, z1), 2), x1 - x0 + 1, z1 - z0 + 1
    if front == "east":
        return Frame(w, (x1, y, z0), 1), z1 - z0 + 1, x1 - x0 + 1
    return Frame(w, (x0, y, z1), 3), z1 - z0 + 1, x1 - x0 + 1


COBBLE_MIX = [("cobbled_paving", 70), ("cobblestone", 8), ("stone_bricks", 8), ("andesite", 6),
              ("mossy_cobblestone", 4), ("polished_andesite", 4)]
LANE_MIX = [("cobbled_paving", 52), ("dirt_path", 12), ("cobblestone", 10), ("gravel", 8), ("mossy_cobblestone", 8),
            ("coarse_dirt", 5), ("andesite", 5)]
SQUARE_MIX = [("plaza_flagstones", 70), ("smooth_castle_stone", 8), ("polished_diorite", 6), ("stone_bricks", 6),
              ("polished_andesite", 6), ("cracked_stone_bricks", 4)]
QUAY_MIX = [("cobbled_paving", 60), ("stone_bricks", 14), ("polished_andesite", 10), ("andesite", 8),
            ("mossy_stone_bricks", 4), ("cracked_stone_bricks", 4)]


def pave(w, x0, z0, x1, z1, mix=None, seed=0, y=0, only_ground=True):
    """Pave a rectangle at height y (replacing grass/dirt only when ``only_ground``)."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    X, Z = np.meshgrid(np.arange(x0, x1 + 1), np.arange(z0, z1 + 1), indexing="ij")
    from capital.world import X0 as WX0, Z0 as WZ0
    bx0, by0, bz0, bx1, by1, bz1 = w._bounds()
    inb = (X >= bx0) & (X <= bx1) & (Z >= bz0) & (Z <= bz1)
    keep = np.zeros(X.shape, bool)
    keep[inb] = w._colmask()[X[inb] - WX0, Z[inb] - WZ0]
    X, Z = X[keep], Z[keep]
    mix = mix or COBBLE_MIX
    wts = np.array([m[1] for m in mix], float)
    c = np.cumsum(wts / wts.sum())
    u = hash2(X, Z, seed)
    k = np.searchsorted(c, u, side="right").clip(0, len(mix) - 1)
    for i, (name, _) in enumerate(mix):
        sel = k == i
        if not sel.any():
            continue
        xs, zs = X[sel], Z[sel]
        if only_ground:
            keep = np.array([w.get(int(a), y, int(b)).split("[")[0] in ("minecraft:grass_block", "minecraft:dirt",
                                                                         "minecraft:sand", "minecraft:gravel",
                                                                         "minecraft:coarse_dirt")
                             for a, b in zip(xs, zs)], bool)
            xs, zs = xs[keep], zs[keep]
        w.set_many(xs, np.full(xs.size, y), zs, B.S(name))
        w.fill(int(x0), y + 1, int(z0), int(x1), y + 1, int(z1), AIR, replace=[B.S("grass"), B.S("fern")])


def clear_above(w, x0, z0, x1, z1, y0=1, y1=24):
    w.fill(x0, y0, z0, x1, y1, z1, AIR)


# ====================================================================================== house
ROOFS = {
    "blue": "royal_roof_tiles", "pale": "pale_roof_tiles", "red": "terracotta_roof_tiles",
    "terracotta": "terracotta_roof_tiles",
    "brown": "spruce", "slate": "deepslate_tiles", "dark": "dark_oak",
}
ROOF_MIX = ("blue", "blue", "blue", "pale", "red", "red", "terracotta", "slate", "blue", "brown", "pale", "blue")
SHUTTERS = ("spruce", "dark_oak", "mangrove", "birch", "spruce", "jungle", "dark_oak")
PLINTHS = ("stone_bricks", "cobblestone", "castle_bricks", "stone_bricks", "polished_andesite", "castle_bricks")
PLASTERS = ("white_plaster",) * 8 + ("smooth_sandstone", "smooth_sandstone", "white_terracotta", "calcite")


def st_of(m):
    return B.full(m) if (m in B.MATERIALS or m in B.ALIASES) else B.S(m)


class HouseSpec:
    """Parameters of a townhouse; ``HouseSpec.random(seed, W, D, ...)`` picks a coherent variant."""

    def __init__(self, **kw):
        self.floors = 2
        self.fh = 4
        self.ground = "stone_bricks"      # ground-floor wall material
        self.ground_plaster = False       # ground floor timbered plaster instead of stone
        self.timber = "dark_oak"
        self.plaster = "white_plaster"
        self.roof = "royal_roof_tiles"
        self.roof_axis = "x"              # ridge along the street (x) or into the plot (z: gable to the street)
        self.jetty = True
        self.shutters = "spruce"
        self.flowers = True
        self.door = "spruce"
        self.dormers = True
        self.chimney = True
        self.interior = True
        self.trim = None                  # gilded eave trim for posh houses
        self.sign = None                  # hanging sign lines (shops)
        self.shop = None                  # None or awning colours (c1, c2): shop front on the ground floor
        self.balcony = False
        self.win2 = True                  # 2-tall upper windows (else 1 tall with flower boxes)
        self.seed = 0
        self.posh = False
        for k, v in kw.items():
            setattr(self, k, v)

    @classmethod
    def random(cls, seed, W, D, posh=False, roofs=ROOF_MIX, floors=None):
        u = lambda k: hash2(seed, k, 9177)  # noqa: E731
        sp = cls(seed=seed)
        sp.floors = floors or (2 if u(1) < 0.5 else 3)
        if D <= 6 or W <= 5:
            sp.floors = min(sp.floors, 2)
        sp.roof = ROOFS[roofs[int(u(2) * len(roofs))]]
        sp.roof_axis = "z" if (u(3) < 0.45 and W <= 9) else "x"
        sp.ground = PLINTHS[int(u(4) * len(PLINTHS))]
        sp.ground_plaster = u(5) < 0.3
        sp.timber = "dark_oak" if u(6) < 0.72 else "spruce"
        sp.jetty = u(7) < 0.72
        sp.shutters = SHUTTERS[int(u(8) * len(SHUTTERS))] if u(18) < 0.8 else None
        sp.flowers = u(9) < 0.8
        sp.door = ("spruce", "dark_oak", "oak", "spruce", "mangrove", "birch")[int(u(10) * 6)]
        sp.dormers = u(11) < 0.65
        sp.chimney = u(12) < 0.85
        sp.plaster = PLASTERS[int(u(13) * len(PLASTERS))]
        sp.balcony = u(15) < 0.15
        sp.win2 = u(17) < 0.5
        if posh:
            sp.posh = True
            sp.ground = "castle_bricks"
            sp.ground_plaster = False
            sp.trim = "gilded_trim"
            sp.plaster = "white_plaster"
            sp.roof = ROOFS["blue"] if u(16) < 0.75 else ROOFS["pale"]
            sp.shutters = None if u(19) < 0.5 else "dark_oak"
        return sp


def house(f, W, D, sp: HouseSpec, nb_left=True, nb_right=True):
    """Draw a townhouse in local frame ``f``: plot x 0..W-1, z 0..D-1, front wall at z = 0 (facing local north),
    ground floor block at y = 0. Upper floors may project one block at the front (jetty, z = -1) and the roof
    eave one more (z = -2), so keep two blocks in front of the plot free. ``nb_left``/``nb_right``: a
    neighbour house touches the side x = 0 / x = W-1 (no side windows, no side roof overhang there).
    Returns a dict of anchors (door, eave, ridge, z_front)."""
    seed = sp.seed
    fh, nf = sp.fh, sp.floors
    eave = fh * nf
    jet = 1 if (sp.jetty and nf >= 2) else 0
    zt = -jet                                        # front wall line of the upper floors
    tim, tim_x, tim_z = B.log(sp.timber), B.log(sp.timber, "x"), B.log(sp.timber, "z")
    plaster = st_of(sp.plaster)
    ground = F(sp.ground)
    plinth = F("polished_andesite") if sp.ground != "polished_andesite" else F("stone_bricks")
    floor_st = F("spruce_planks") if hash2(seed, 1, 3) < 0.6 else F("oak_planks")
    pane = LEADED
    PANE = B.canon(pane)

    def u(k):
        return hash2(seed, k, 4421)

    # ------------------------------------------------------------------ shell
    f.fill(0, 1, zt, W - 1, eave + 1, D - 1, AIR)
    f.fill(0, 0, 0, W - 1, 0, D - 1, floor_st)
    f.fill(0, 0, 0, W - 1, 0, 0, plinth)
    for k in range(nf):
        y0 = k * fh
        zf = zt if k >= 1 else 0
        timbered = k >= 1 or sp.ground_plaster
        st = plaster if timbered else ground
        S.walls(f, 0, zf, W - 1, D - 1, y0 + 1, y0 + fh - 1, st)
        if k >= 1:
            f.fill(0, y0, zf, W - 1, y0, D - 1, floor_st)
        if timbered:
            for x in range(W):
                if x in (0, W - 1) or x % 3 == 0:
                    f.fill(x, y0 + 1, zf, x, y0 + fh - 1, zf, tim)
                    f.fill(x, y0 + 1, D - 1, x, y0 + fh - 1, D - 1, tim)
            for z in range(zf, D):
                if z in (zf, D - 1) or (z - zf) % 3 == 0:
                    f.fill(0, y0 + 1, z, 0, y0 + fh - 1, z, tim)
                    f.fill(W - 1, y0 + 1, z, W - 1, y0 + fh - 1, z, tim)
            if k == 0:
                S.walls(f, 0, 0, W - 1, D - 1, 1, 1, plinth)          # stone plinth course
        else:
            for x in (0, W - 1):                                      # quoins
                for y in range(1, fh):
                    q = plinth if (y % 2 == 1) else F("stone_bricks") if sp.ground != "stone_bricks" else ground
                    f.set(x, y, 0, q)
                    f.set(x, y, D - 1, q)
        if k >= 1:                                                    # girt all round at the floor line
            f.fill(0, y0, zf, W - 1, y0, zf, tim_x)
            f.fill(0, y0, D - 1, W - 1, y0, D - 1, tim_x)
            f.fill(0, y0, zf, 0, y0, D - 1, tim_z)
            f.fill(W - 1, y0, zf, W - 1, y0, D - 1, tim_z)
            if jet and k == 1:
                for x in range(W):
                    if x in (0, W - 1) or x % 3 == 0:
                        f.set(x, y0 - 1, -1, B.stairs(sp.timber, "south", "top"))
    # eave beam and attic floor
    S.rect_ring(f, 0, zt, W - 1, D - 1, eave, tim_x)
    f.fill(0, eave, zt, 0, eave, D - 1, tim_z)
    f.fill(W - 1, eave, zt, W - 1, eave, D - 1, tim_z)
    f.fill(1, eave, zt + 1, W - 2, eave, D - 2, floor_st)
    if sp.posh:
        # castle-stone corner quoins all the way up
        for x in (0, W - 1):
            for z in (zt, D - 1):
                for y in range(fh + 1, eave):
                    f.set(x, y, z, F("castle_bricks") if y % 2 else F("smooth_castle_stone"))

    # ------------------------------------------------------------------ ground floor front
    if W % 2:
        door_x = W // 2
    else:
        door_x = W // 2 - (1 if u(5) < 0.5 else 0)
    if W >= 7 and u(6) < 0.45 and not sp.shop:
        door_x = 2 if u(7) < 0.5 else W - 3
    lo, hi = B.door_states(sp.door, "south", "left" if u(8) < 0.5 else "right")
    f.set(door_x, 1, 0, lo)
    f.set(door_x, 2, 0, hi)
    f.set(door_x, 3, 0, pane if fh >= 4 and u(9) < 0.5 else (tim_x if sp.ground_plaster else ground))
    f.set(door_x, 0, -1, B.stairs("stone_bricks" if not sp.posh else "smooth_castle_stone", "south"))
    if not jet:
        # small hood over the door on brackets
        rm = sp.roof if B.has_stairs(sp.roof) else "spruce"
        f.set(door_x, 4 if fh > 4 else 3, -1, B.slab(rm, "bottom")) if False else None
        f.set(door_x, 3, -1, B.stairs(rm, "south", "bottom"))
    if u(10) < 0.6:
        side = door_x + 1 if door_x + 1 <= W - 2 else door_x - 1
        if f.get(side, 2, 0) != PANE:
            f.set(side, 2, -1, B.lantern(hanging=False)) if False else None
    if sp.shop:
        c1, c2 = sp.shop
        for x in range(1, W - 1):
            if abs(x - door_x) >= 1 and x % 3 != 0 or (not sp.ground_plaster and x != door_x):
                if x == door_x:
                    continue
                f.set(x, 1, 0, pane)
                f.set(x, 2, 0, pane)
                f.set(x, 0, 1, F("dark_oak_planks"))
        # striped awning over the shop front (sloping out under the jetty)
        for x in range(0, W):
            c = c1 if x % 2 == 0 else c2
            f.set(x, 3, -1, B.wool(c))
            if not jet:
                f.set(x, 3, -2, B.S(f"{c}_wall_banner", facing="north")) if False else None
    else:
        for x in range(1, W - 1):
            if abs(x - door_x) < 2 or (x % 3 == 0 and sp.ground_plaster):
                continue
            if (x - door_x) % 2 == 0 and abs(x - door_x) > 2 and W < 7:
                continue
            f.set(x, 2, 0, pane)
            if sp.shutters:
                for sx in (x - 1, x + 1):
                    if 0 < sx < W - 1 and sx != door_x and f.get(sx, 2, 0) != PANE:
                        f.set(sx, 2, -1, B.trapdoor(sp.shutters, "north", "bottom", True))
            f.set(x, 1, -1, B.trapdoor("spruce", "north", "top", False)) if False else None
            if sp.flowers and u(20 + x) < 0.7:
                f.set(x, 1, -1, B.potted(("red_tulip", "cornflower", "allium", "white_tulip", "azure_bluet")
                                         [int(u(30 + x) * 5)])) if False else None
    # ------------------------------------------------------------------ upper floors
    for k in range(1, nf):
        y0 = k * fh
        zf = zt
        tall = sp.win2 if k < nf - 1 or u(40) < 0.5 else (not sp.win2)
        for x in range(1, W - 1):
            if x % 3 == 0:
                continue
            if u(50 + x * 7 + k) < 0.08:
                continue
            f.set(x, y0 + 2, zf, pane)
            if tall:
                f.set(x, y0 + 1, zf, pane)
            elif sp.flowers:
                st = B.leaves("flowering_azalea" if u(60 + x + k) < 0.65 else "azalea")
                f.set(x, y0 + 1, zf - 1, st)
                f.set(x, y0, zf - 1, B.trapdoor("spruce", "north", "top", False))
        if sp.shutters:
            for x in range(0, W):
                if x % 3 == 0 and 0 < x < W - 1 or x in (0, W - 1):
                    for nx in (x - 1, x + 1):
                        if 0 <= nx < W and f.get(nx, y0 + 2, zf) == PANE:
                            f.set(x, y0 + 2, zf - 1, B.trapdoor(sp.shutters, "north", "bottom", True))
                            if tall:
                                f.set(x, y0 + 1, zf - 1, B.trapdoor(sp.shutters, "north", "bottom", True))
                            break
        for x in range(1, W - 1):                                    # back windows
            if x % 3 and u(70 + x + k) < 0.45:
                f.set(x, y0 + 2, D - 1, pane)
        for (xs, nb) in ((0, nb_left), (W - 1, nb_right)):          # side windows if free
            if nb:
                continue
            for z in range(zf + 1, D - 1):
                if (z - zf) % 3 and u(80 + z + xs + k) < 0.5:
                    f.set(xs, y0 + 2, z, pane)
    if sp.balcony and nf >= 2:
        y0 = fh
        for x in range(max(1, door_x - 1), min(W - 2, door_x + 1) + 1):
            f.set(x, y0, zt - 1, B.slab("spruce", "top"))
            f.set(x, y0 + 1, zt - 1, B.fence("spruce"))
        lo2, hi2 = B.door_states("spruce", "north", "left")
        f.set(door_x, y0 + 1, zt, lo2)
        f.set(door_x, y0 + 2, zt, hi2)

    # ------------------------------------------------------------------ roof
    z_lo = zt
    ov = 1
    if sp.roof_axis == "x":
        roof = K.Roof(f, 0, z_lo, W - 1, D - 1, eave + 1, sp.roof, axis="x", overhang=ov, gable=plaster,
                      trim=sp.trim, end_overhang=0)
        ridge = roof.ridge_y
        for x in (0, W - 1):                                         # timbered gables
            for z in range(z_lo, D):
                if (z - z_lo) % 3 == 0:
                    for y in range(eave + 1, ridge + 1):
                        if f.get(x, y, z) == B.canon(plaster):
                            f.set(x, y, z, tim)
        if sp.dormers and W >= 7 and ridge - eave >= 7:
            for a in ([W // 2] if W < 12 else [W // 3, W - 1 - W // 3]):
                if abs(a - door_x) <= 0 or True:
                    K.dormer(f, roof, -1, a, width=3, wall="white_plaster", frame=sp.timber,
                             finial=bool(sp.trim), mat=sp.roof)
    else:
        roof = K.Roof(f, 0, z_lo, W - 1, D - 1, eave + 1, sp.roof, axis="z", overhang=0 if nb_left and nb_right
                      else 1, gable=plaster, trim=sp.trim, end_overhang=1)
        ridge = roof.ridge_y
        for z in (z_lo, D - 1):
            for x in range(W):
                if x % 3 == 0 or x in (0, W - 1):
                    for y in range(eave + 1, ridge + 1):
                        if f.get(x, y, z) == B.canon(plaster):
                            f.set(x, y, z, tim)
            cx = (W - 1) // 2
            for y in (eave + 2, eave + 3):
                for xx in ([cx] if W % 2 else [cx, cx + 1]):
                    if f.get(xx, y, z) == B.canon(plaster):
                        f.set(xx, y, z, pane)
            if z == z_lo:
                # hoist beam under the gable peak
                yb = ridge - 2
                if yb > eave + 3 and W % 2:
                    f.set(cx, yb, z - 1, tim_z)
                    f.set(cx, yb - 1, z - 1, B.chain("y"))
    # ------------------------------------------------------------------ chimney + smoke
    if sp.chimney:
        if sp.roof_axis == "x":
            cx = 1 if u(90) < 0.5 else W - 2
            cz = (z_lo + D - 1) // 2 + 1
        else:
            cx = (W - 1) // 2 + (1 if u(91) < 0.5 else -1) if W >= 6 else (W - 1) // 2
            cz = D - 3 if D >= 7 else D - 2
        top = ridge + 1
        mat = F("bricks") if u(92) < 0.55 else F("stone_bricks")
        f.fill(cx, eave + 1, cz, cx, top, cz, mat)
        f.set(cx, top + 1, cz, B.wall("cobblestone")) if False else None
        smoke(f, cx, top + 1, cz)
    if sp.trim:
        # gilded finials on the gable peaks
        if sp.roof_axis == "x":
            for x in (0, W - 1):
                f.set(x, ridge + 1, (z_lo + D - 1) // 2, B.S("gilded_finial"), only_empty=True)
        else:
            f.set((W - 1) // 2, ridge + 1, z_lo - 1, B.S("gilded_finial"), only_empty=True)
    if sp.interior:
        _interior(f, W, D, sp, door_x, zt)
    return dict(door=(door_x, 1, 0), eave=eave, ridge=ridge, z_front=zt)


def _interior(f, W, D, sp, door_x, zt):
    seed = sp.seed
    fh, nf = sp.fh, sp.floors
    u = lambda k: hash2(seed, k, 5531)  # noqa: E731
    lx = 1 if door_x > 2 else W - 2
    # ladder in a back corner through the floors
    for k in range(nf):
        y0 = k * fh
        if k < nf - 1:
            for y in range(y0 + 1, y0 + fh + 1):
                f.set(lx, y, D - 2, B.S("ladder", facing="north"))
    if W < 5 or D < 5:
        return
    # ground floor: table with chairs, hearth/stove, barrels, rug, lantern
    tx = (W - 1) // 2 + (1 if door_x <= (W - 1) // 2 else -1)
    tz = max(2, D // 2)
    if 0 < tx < W - 1 and tz < D - 2:
        f.set(tx, 1, tz, B.fence("spruce"))
        f.set(tx, 2, tz, B.S("spruce_pressure_plate") if u(1) < 0.5 else B.carpet("white"))
        for dx in (-1, 1):
            if 0 < tx + dx < W - 1 and f.is_empty(tx + dx, 1, tz):
                f.set(tx + dx, 1, tz, B.stairs("spruce", "east" if dx > 0 else "west"))
    bx = W - 2 if lx == 1 else 1
    for (zz, st) in ((1, (B.barrel("up"), B.S("crafting_table"), B.S("smoker", facing="south"),
                          B.S("furnace", facing="south"))[int(u(2) * 4)]),
                     (2, B.barrel("up") if u(3) < 0.5 else B.S("composter")),
                     (D - 2, B.chest("north"))):
        if 0 < zz < D - 1 and f.is_empty(bx, 1, zz):
            f.set(bx, 1, zz, st)
    if f.is_empty(door_x, 1, 1):
        f.set(door_x, 1, 1, B.carpet(("red", "blue", "brown", "green", "cyan")[int(u(4) * 5)]))
    f.set((W - 1) // 2, fh - 1, max(2, D // 2), B.lantern(hanging=True)) if         f.is_empty((W - 1) // 2, fh - 1, max(2, D // 2)) else None
    for k in range(1, nf):
        y0 = k * fh
        if D >= 6:
            color = ("red", "blue", "white", "light_blue", "green", "yellow")[int(u(10 + k) * 6)]
            foot, head = B.bed_states(color, "south")
            if f.is_empty(bx, y0 + 1, D - 3) and f.is_empty(bx, y0 + 1, D - 2):
                f.set(bx, y0 + 1, D - 3, foot)
                f.set(bx, y0 + 1, D - 2, head)
            cx = bx + (1 if bx == 1 else -1)
            if f.is_empty(cx, y0 + 1, D - 2) and cx != lx:
                f.set(cx, y0 + 1, D - 2, B.chest("north"))
        mx, mz = (W - 1) // 2, max(1, (D - 1) // 2)
        if f.is_empty(mx, y0 + 1, mz):
            f.set(mx, y0 + 1, mz, B.carpet(("blue", "red", "brown", "light_gray")[int(u(20 + k) * 4)]))
        if f.is_empty(bx, y0 + 1, zt + 1):
            f.set(bx, y0 + 1, zt + 1, (B.S("bookshelf"), B.barrel("up"), B.S("flower_pot"),
                                       B.S("loom", facing="south"))[int(u(30 + k) * 4)])
        f.set(mx, y0 + fh - 1, mz, B.lantern(hanging=True)) if f.is_empty(mx, y0 + fh - 1, mz) else None


# ====================================================================================== rows of houses
def row(w, x0, z0, x1, z1, front, seed=0, min_w=5, max_w=9, posh=False, roofs=None, specs=None, interior=True,
        floors=None, inset=2, end_gap=(1, 1), alley_every=0):
    """Fill the strip (x0, z0, x1, z1) with attached houses whose fronts face ``front``. The first ``inset``
    blocks on the front side stay free for jetties/eaves (a pavement). Returns [(rect, spec), ...]."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    along_x = front in ("north", "south")
    if front == "north":
        z0 += inset
    elif front == "south":
        z1 -= inset
    elif front == "west":
        x0 += inset
    else:
        x1 -= inset
    a0, a1 = (x0, x1) if along_x else (z0, z1)
    a0 += end_gap[0]
    a1 -= end_gap[1]
    out = []
    plots = []
    a = a0
    i = 0
    while a <= a1:
        rem = a1 - a + 1
        if rem < min_w:
            break
        wdt = min_w + int(hash2(a, i, seed) * (max_w - min_w + 1))
        if rem - wdt < min_w:
            wdt = rem if rem <= max_w + 3 else wdt
        wdt = min(wdt, rem)
        plots.append((a, wdt))
        a += wdt
        i += 1
        if alley_every and i % alley_every == 0:
            a += 3
            plots.append(None)
    for i, p in enumerate(plots):
        if p is None:
            continue
        a, wdt = p
        rect = (a, z0, a + wdt - 1, z1) if along_x else (x0, a, x1, a + wdt - 1)
        D = (z1 - z0 + 1) if along_x else (x1 - x0 + 1)
        sp = (specs[i] if specs and i < len(specs) and specs[i] is not None else
              HouseSpec.random(seed * 131 + i * 7 + a, wdt, D, posh=posh, floors=floors,
                               **({"roofs": roofs} if roofs else {})))
        sp.interior = interior
        fr, Wd, Dd = frame_for(w, rect, front)
        # which local sides have neighbours (local x = 0 side depends on the frame orientation)
        prev_ok = i > 0 and plots[i - 1] is not None
        next_ok = i + 1 < len(plots) and plots[i + 1] is not None
        lx0 = fr.pos(0, 0, 0)
        # local x=0 lies at the low end of the strip for fronts north/east, at the high end for south/west
        low_is_x0 = front in ("north", "east")
        nb_left = prev_ok if low_is_x0 else next_ok
        nb_right = next_ok if low_is_x0 else prev_ok
        house(fr, Wd, Dd, sp, nb_left=nb_left, nb_right=nb_right)
        out.append((rect, sp))
    return out


# ====================================================================================== civic hall shell
class HallSpec:
    """Stone public building: ``storeys`` x ``sh`` high, pilasters, arched windows, portal, roof."""

    def __init__(self, **kw):
        self.storeys = 2
        self.sh = 5                   # storey height (floor to floor)
        self.wall = "castle_bricks"
        self.pilaster = "smooth_castle_stone"
        self.plinth = "polished_andesite"
        self.upper = None             # None = same stone; "timber" = half-timbered upper storey
        self.roof = "royal_roof_tiles"
        self.roof_kind = "gable_x"    # gable_x (ridge along the front), gable_z (gable to the front), hip
        self.trim = "gilded_trim"
        self.portal_w = 3
        self.portal_h = 5
        self.portal_x = None          # centre of the portal (default: middle)
        self.door = "dark_oak"
        self.window_every = 4
        self.dormers = True
        self.banner_nbt = None        # crest banners beside the portal
        self.banner_color = "blue"
        self.sign = None              # (lines, color) on the portal lintel
        self.floor = "polished_andesite"
        self.chimney = False
        self.side_windows = True
        self.back_windows = True
        self.ceiling = True
        for k, v in kw.items():
            setattr(self, k, v)


def civic_hall(f, W, D, sp: HallSpec):
    """Draw the shell of a public building in frame ``f`` (front wall at z = 0 facing local north, plot
    x 0..W-1, z 0..D-1). Returns anchors: door (x, z), floors (list of floor y), eave, ridge, roof."""
    sh, ns = sp.sh, sp.storeys
    eave = sh * ns
    wall = F(sp.wall)
    pil = F(sp.pilaster)
    f.fill(0, 1, 0, W - 1, eave + 2, D - 1, AIR)
    f.fill(0, -1, 0, W - 1, -1, D - 1, B.S("stone"))
    f.fill(0, 0, 0, W - 1, 0, D - 1, F(sp.floor))
    S.walls(f, 0, 0, W - 1, D - 1, 1, eave - 1, wall)
    S.walls(f, 0, 0, W - 1, D - 1, 1, 1, F(sp.plinth))
    floors = [0]
    for k in range(1, ns):
        y = k * sh
        floors.append(y)
        f.fill(1, y, 1, W - 2, y, D - 2, F("spruce_planks"))
        S.walls(f, 0, 0, W - 1, D - 1, y, y, pil)                       # string course
        f.fill(0, y, -1, W - 1, y, -1, B.stairs(sp.pilaster if B.has_stairs(sp.pilaster) else "stone_bricks",
                                                 "south", "top"))
    if sp.upper == "timber" and ns >= 2:
        y0 = sh
        S.walls(f, 0, 0, W - 1, D - 1, y0 + 1, eave - 1, F("white_plaster"))
        for x in range(W):
            if x % 3 == 0 or x == W - 1:
                f.fill(x, y0 + 1, 0, x, eave - 1, 0, B.log("dark_oak"))
                f.fill(x, y0 + 1, D - 1, x, eave - 1, D - 1, B.log("dark_oak"))
        for z in range(D):
            if z % 3 == 0 or z == D - 1:
                f.fill(0, y0 + 1, z, 0, eave - 1, z, B.log("dark_oak"))
                f.fill(W - 1, y0 + 1, z, W - 1, eave - 1, z, B.log("dark_oak"))
    S.walls(f, 0, 0, W - 1, D - 1, eave, eave, pil)                       # cornice course
    if sp.ceiling:
        f.fill(1, eave, 1, W - 2, eave, D - 2, F("dark_oak_planks"))
    # pilasters on the front and corners
    px = sp.portal_x if sp.portal_x is not None else (W - 1) // 2
    pw = sp.portal_w
    p0 = px - pw // 2
    cols = sorted(set([0, W - 1] + [x for x in range(0, W, sp.window_every)]))
    for x in cols:
        if p0 - 1 <= x <= p0 + pw:
            continue
        f.fill(x, 1, -1, x, eave - 1, -1, pil)
        f.set(x, eave - 1, -1, B.stairs(sp.pilaster if B.has_stairs(sp.pilaster) else "stone_bricks", "south",
                                        "top"))
    # windows between pilasters on every storey (front, back, sides)
    fr = K.Face(f, "x", 0, -1)
    bk = K.Face(f, "x", D - 1, 1)
    for k in range(ns):
        y0 = k * sh + 2
        hgt = sh - 3 if sh >= 5 else 2
        for i in range(len(cols) - 1):
            a, b = cols[i], cols[i + 1]
            if b - a < 3:
                continue
            mid = (a + b) // 2
            if k == 0 and abs(mid - px) <= pw // 2 + 1:
                continue
            ww = 2 if (b - a) >= 4 else 1
            u0 = mid - (ww - 1) // 2 if ww == 1 else (a + b + 1) // 2 - 1
            K.window(fr, u0, y0, ww, hgt, arch="round" if hgt >= 3 else None, depth=1, recess=0, sill=sp.pilaster
                     if B.has_stairs(sp.pilaster) else "stone_bricks", keystone=F(sp.trim) if sp.trim else None,
                     glass=LEADED)
            if sp.back_windows:
                K.window(bk, u0, y0, ww, hgt, arch="round" if hgt >= 3 else None, depth=1, recess=0, sill=None,
                         glass=LEADED)
        if sp.side_windows:
            for (xs, out) in ((0, -1), (W - 1, 1)):
                fs = K.Face(f, "z", xs, out)
                for z in range(2, D - 2, sp.window_every):
                    K.window(fs, z, y0, 2 if D > 8 else 1, hgt, arch="round" if hgt >= 3 else None, depth=1,
                             recess=0, sill=None, glass=LEADED)
    # portal
    m = S.round_arch_mask(pw, sp.portal_h)
    fr.mask(p0, 1, 0, m, AIR)
    fr.mask(p0 - 1, 0, 0, K.ring_of(m), pil)
    for x in range(p0 - 1, p0 + pw + 1):
        fr.set(x, 0, 1, B.stairs("stone_bricks", "south")) if False else None
    nd = min(pw, 2)
    dx0 = px - (nd - 1) // 2 if nd == 1 else px - (1 if pw % 2 == 0 else 1)
    if pw >= 2:
        dx0 = p0 + (pw - 2) // 2
        for i, hinge in ((0, "right"), (1, "left")):
            lo, hi = B.door_states(sp.door, "south", hinge)
            f.set(dx0 + i, 1, 0, lo)
            f.set(dx0 + i, 2, 0, hi)
        for x in range(p0, p0 + pw):
            if not (dx0 <= x <= dx0 + 1):
                f.set(x, 1, 0, F(sp.wall))
                f.set(x, 2, 0, F(sp.wall))
    else:
        lo, hi = B.door_states(sp.door, "south", "left")
        f.set(px, 1, 0, lo)
        f.set(px, 2, 0, hi)
    for x in range(p0, p0 + pw):
        for y in range(3, sp.portal_h + 1):
            if f.is_empty(x, y, 0):
                f.set(x, y, 0, LEADED)
    if sp.trim:
        f.set(px, sp.portal_h + 1, 0, F(sp.trim))
    # steps in front of the portal
    for x in range(p0 - 1, p0 + pw + 1):
        f.set(x, 0, -1, B.stairs("stone_bricks", "south"))
    # banners and lamps beside the portal
    for x in (p0 - 2, p0 + pw + 1):
        if sp.banner_nbt:
            deco.kingdom_wall_banner(f, x, sh - 1 if ns > 1 else 4, -2, facing="north", color=sp.banner_color,
                                     nbt=sp.banner_nbt) if f.get(x, sh - 1, -1) == B.canon(pil) else \
                deco.kingdom_wall_banner(f, x, sh - 1 if ns > 1 else 4, -1, facing="north", color=sp.banner_color,
                                         nbt=sp.banner_nbt)
        f.set(x, 3, -1, B.lantern()) if False else None
    if sp.sign:
        lines, color = sp.sign
        deco.sign_text(f, px, sp.portal_h + 2, -1, B.S("dark_oak_wall_sign", facing="north"), lines,
                       color=color, glowing=True)
    # roof
    if sp.roof_kind == "hip":
        top = S.hip_roof(f, 0, 0, W - 1, D - 1, eave + 1, sp.roof, overhang=1)
        roof = None
        ridge = top
        if sp.trim:
            f.fill(-1, eave, -1, W, eave, -1, B.stairs(sp.trim, "north", "top")) if False else None
    else:
        axis = "x" if sp.roof_kind == "gable_x" else "z"
        roof = K.Roof(f, 0, 0, W - 1, D - 1, eave + 1, sp.roof, axis=axis, overhang=1, gable=wall,
                      trim=sp.trim, end_overhang=1)
        ridge = roof.ridge_y
        if sp.dormers and axis == "x" and W >= 9:
            for a in range(3, W - 3, 5 if W > 14 else W):
                if abs(a - px) >= 2 or W < 14:
                    K.dormer(f, roof, -1, a if W >= 14 else (W - 1) // 2, width=3, wall=sp.wall, frame=None,
                             finial=bool(sp.trim), glass=LEADED)
                    if W < 14:
                        break
    if sp.chimney:
        cx, cz = W - 3, D - 3
        f.fill(cx, eave + 1, cz, cx, ridge + 1, cz, F("bricks"))
        smoke(f, cx, ridge + 2, cz)
    return dict(door=(px, 0), floors=floors, eave=eave, ridge=ridge, roof=roof, portal=(p0, pw))


# ====================================================================================== market stalls
AWNINGS = (("red", "white"), ("blue", "white"), ("yellow", "white"), ("green", "white"), ("orange", "white"),
           ("purple", "white"), ("light_blue", "white"), ("red", "yellow"), ("lime", "white"), ("pink", "white"),
           ("cyan", "white"), ("magenta", "white"))
GOODS = {
    "fruit": ["melon", "pumpkin", "hay_block", "oak_leaves"],
    "veg": ["carrots", "potatoes", "beetroots", "pumpkin"],
    "flowers": ["potted_red_tulip", "potted_cornflower", "potted_allium", "potted_white_tulip",
                "potted_azure_bluet", "potted_oxeye_daisy", "potted_blue_orchid", "potted_poppy"],
    "cloth": ["red_wool", "blue_wool", "yellow_wool", "white_wool", "light_blue_wool", "purple_wool"],
    "fish": ["dried_kelp_block", "barrel", "prismarine", "water_cauldron"],
    "pots": ["decorated_pot", "flower_pot", "decorated_pot", "cauldron"],
    "bread": ["hay_block", "cake", "barrel", "bee_nest"],
    "potions": ["brewing_stand", "candle", "amethyst_cluster", "glass"],
    "arms": ["anvil", "grindstone", "barrel", "smithing_table"],
    "books": ["bookshelf", "lectern", "chiseled_bookshelf", "candle"],
}


def _goods_state(name, facing):
    if name in ("carrots", "potatoes"):
        return B.S(name, age=7)
    if name == "beetroots":
        return B.S(name, age=3)
    if name in ("oak_leaves",):
        return B.leaves("oak")
    if name in ("hay_block", "bee_nest"):
        return B.S(name, axis="y") if name == "hay_block" else B.S(name, facing=facing, honey_level=5)
    if name == "barrel":
        return B.barrel("up")
    if name == "candle":
        return B.candle(3, "white")
    if name == "amethyst_cluster":
        return B.S(name, facing="up")
    if name in ("anvil", "grindstone", "lectern"):
        if name == "grindstone":
            return B.S(name, face="floor", facing=facing)
        return B.S(name, facing=facing)
    if name == "chiseled_bookshelf":
        return B.S(name, facing=facing, slot_0_occupied=True, slot_1_occupied=True, slot_3_occupied=True)
    if name == "water_cauldron":
        return B.S(name, level=3)
    if name == "brewing_stand":
        return B.S(name, has_bottle_0=True, has_bottle_1=True, has_bottle_2=True)
    if name == "decorated_pot":
        return B.S(name, facing=facing)
    if name == "cake":
        return B.S("cake")
    return B.S(name)


def market_stall(f, x0, z0, W=5, D=4, colors=("red", "white"), goods="fruit", seed=0, wood="spruce", sign=None):
    """Market stall in frame ``f`` with its counter along the front (local z = z0, facing local north):
    corner posts, a striped wool awning sloping to the front with a banner valance, goods on the counter,
    crates and barrels behind. Occupies x0..x0+W-1, z0-1 (valance) .. z0+D-1."""
    c1, c2 = colors
    x1, z1 = x0 + W - 1, z0 + D - 1
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        f.fill(x, 1, z, x, 3 if z == z0 else 4, z, B.fence(wood))
    # awning: back row at y 5, middle at y 5/4, front at y 4 (stripes along x)
    for x in range(x0, x1 + 1):
        c = c1 if (x - x0) % 2 == 0 else c2
        for z in range(z0, z1 + 1):
            y = 4 if z == z0 else 5
            if z == z0 or z == z1 or True:
                f.set(x, y, z, B.wool(c) if z != z0 else B.S(f"{c}_carpet") if False else B.wool(c))
        f.set(x, 4, z0 - 1, B.S(f"{c}_wall_banner", facing="north"))
    # counter
    f.fill(x0 + 1, 1, z0, x1 - 1, 1, z0, B.S("barrel", facing="north") if hash2(x0, z0, seed) < 0.3 else
           F(f"{wood}_planks"))
    f.fill(x0 + 1, 2, z0, x1 - 1, 2, z0, AIR)
    items = GOODS.get(goods, GOODS["fruit"])
    for i, x in enumerate(range(x0 + 1, x1)):
        g = items[int(hash2(x, z0, seed + 1) * len(items))]
        if g in ("carrots", "potatoes", "beetroots"):
            f.set(x, 1, z0, B.S("farmland", moisture=7)) if False else f.set(x, 1, z0, B.S("composter", level=8))
            continue
        f.set(x, 2, z0, _goods_state(g, "north"))
    # behind: crates, barrels, a chest
    for x in range(x0 + 1, x1):
        k = hash2(x, z1, seed + 2)
        if k < 0.4:
            f.set(x, 1, z1, B.barrel("up"))
            if k < 0.15:
                f.set(x, 2, z1, B.barrel("up"))
        elif k < 0.6:
            f.set(x, 1, z1, B.chest("north"))
        elif k < 0.75:
            f.set(x, 1, z1, B.S("hay_block", axis="y"))
    if sign:
        deco.sign_text(f, x0 + W // 2, 3, z0 - 1, B.S(f"{wood}_wall_sign", facing="north"), sign, color="black")


# ====================================================================================== people spots
_PASSABLE_SUFFIX = ("_carpet", "light_shaft", "smoke_emitter", "fountain_spray", "pressure_plate")


def _passable(st):
    if st in ("minecraft:air", "minecraft:cave_air"):
        return True
    name = st.split(":")[1].split("[")[0]
    return name.endswith(_PASSABLE_SUFFIX) or name in ("grass", "fern", "light")


def _floor_ok(st):
    if st in ("minecraft:air", "eternalrealm:void", "minecraft:cave_air"):
        return False
    name = st.split(":")[1].split("[")[0]
    if name in ("water", "lava") or name.endswith(("_fence", "_wall", "_leaves", "_fence_gate", "_pane", "_bars",
                                                   "_sign", "_banner", "lantern", "_door", "_trapdoor",
                                                   "_carpet", "chain", "fountain_spray", "smoke_emitter",
                                                   "light_shaft")):
        return False
    return not _passable(st)


def standable(w, x, y, z):
    return _floor_ok(w.get(x, y - 1, z)) and _passable(w.get(x, y, z)) and _passable(w.get(x, y + 1, z))


def room_around(w, x, y, z, r=3):
    n = 0
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if standable(w, x + dx, y, z + dz):
                n += 1
    return n


def people_spot(w, name, x, y, z, yaw=0.0, need=0, r=3, search=6):
    """Place a person spot at (x, y, z) or the nearest standable position (same y, within ``search``) that has
    at least ``need`` standable cells within ``r``. Returns the position used (or None)."""
    best = None
    for d in range(0, search + 1):
        cands = [(x + dx, z + dz) for dx in range(-d, d + 1) for dz in range(-d, d + 1)
                 if max(abs(dx), abs(dz)) == d]
        cands.sort(key=lambda p: (p[0] - x) ** 2 + (p[1] - z) ** 2)
        for (cx, cz) in cands:
            if standable(w, cx, y, cz) and (need <= 0 or room_around(w, cx, y, cz, r) >= need):
                best = (cx, y, cz)
                break
        if best:
            break
    if best is None:
        w.notes.append(f"no free position for spot {name} near {(x, y, z)}")
        return None
    w.spot(name, best, yaw)
    return best


def crowd(w, area, pts, start=1, need=26):
    """crowd.<area>.<n> spots (townsfolk wander a little around them: keep ~6 blocks of floor around)."""
    n = start
    for p in pts:
        x, y, z = p if len(p) == 3 else (p[0], 1, p[1])
        yaw = float(int(hash2(x, z, 515) * 8) * 45 - 180)
        if people_spot(w, f"crowd.{area}.{n}", x, y, z, yaw, need=need):
            n += 1
    return n


def guard(w, post, n, x, y, z, yaw):
    return people_spot(w, f"guard.{post}.{n}", x, y, z, yaw, need=0, search=3)


def bracket_flag(w, x, y, z, out_dir, design="kingdom", width=2.5, height=1.5, wood="dark_oak"):
    """Flag on a wall bracket: a short horizontal pole out of the wall at (x, y, z) (the air block in front of
    the wall) with the flag block at its end; the cloth streams towards ``out_dir`` away from the wall."""
    vx, vz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[out_dir]
    w.set(x, y, z, B.fence(wood))
    w.set(x, y - 1, z, B.stairs(wood, OPP[out_dir], "top"))
    flag(w, x + vx, y, z + vz, out_dir, design, width, height)
