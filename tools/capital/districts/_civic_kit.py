"""Shared kit for the civic-centre districts (plaza, guild, shop ring, cathedral, north-east quarter, upper town).

Everything works on a target ``t`` (a ``World`` or a ``Frame``) in that target's coordinates. ``Face`` describes a
vertical wall plane so windows, doors, banners and signs can be written once for any orientation.

Palette of the civic centre: cream-white castle stone (castle_bricks / smooth_castle_stone / carved stone), grey
stone-brick plinths, white plaster with dark-oak timbering on upper floors, royal-blue roofs with gilded eaves,
ridges and finials, blue crest banners.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import deco
from capital import shapes as S
from capital.noise import hash2, hash3
from capital.world import DIR_VEC, OPPOSITE, left_of, right_of

AIR = "minecraft:air"
CB = "castle_bricks"
SM = "smooth_castle_stone"
GOLD = "gilded_trim"
ROOF = "royal_roof_tiles"
PALE = "pale_roof_tiles"
CARVED = "carved_castle_stone"
PLASTER = "white_plaster"
SB = "stone_bricks"

OPP = OPPOSITE


def F(m):
    """Full block of a material family or a plain block id."""
    return B.full(m)


def put(t, x, y, z, state, **kw):
    t.set(x, y, z, state, **kw)


def rect(t, x0, z0, x1, z1, y, state):
    t.fill(x0, y, z0, x1, y, z1, state)


# ====================================================================================== faces
class Face:
    """A vertical wall plane in target coordinates.

    axis "x": the wall runs along x at z = c (u is x); axis "z": the wall runs along z at x = c (u is z).
    ``out`` = +1 when the outside lies towards +z (axis x) / +x (axis z), else -1. ``d`` is the offset along the
    outward normal: d = 0 is the outer face block of the wall, d = 1 just outside, d = -1 one block inside.
    """

    def __init__(self, t, axis, c, out):
        self.t, self.axis, self.c, self.out = t, axis, int(c), int(out)
        if axis == "x":
            self.out_dir = "south" if out > 0 else "north"
            self.u_dir = "east"
        else:
            self.out_dir = "east" if out > 0 else "west"
            self.u_dir = "south"
        self.in_dir = OPP[self.out_dir]
        self.u_neg = OPP[self.u_dir]

    def p(self, u, y, d=0):
        if self.axis == "x":
            return (int(u), int(y), self.c + self.out * int(d))
        return (self.c + self.out * int(d), int(y), int(u))

    def set(self, u, y, d, state, **kw):
        x, yy, z = self.p(u, y, d)
        return self.t.set(x, yy, z, state, **kw)

    def fill(self, u0, y0, d0, u1, y1, d1, state, **kw):
        a = self.p(u0, y0, d0)
        b = self.p(u1, y1, d1)
        return self.t.fill(a[0], a[1], a[2], b[0], b[1], b[2], state, **kw)

    def get(self, u, y, d=0):
        return self.t.get(*self.p(u, y, d))

    def is_empty(self, u, y, d=0):
        return self.t.is_empty(*self.p(u, y, d))

    def mask(self, u0, y0, d, m, state):
        """Write ``state`` where the 2D mask m[u, v] is set (u from u0, v from y0) in the plane at offset d."""
        us, vs = np.nonzero(m)
        if us.size == 0:
            return
        if self.axis == "x":
            self.t.set_many(u0 + us, y0 + vs, np.full(us.size, self.c + self.out * d), state)
        else:
            self.t.set_many(np.full(us.size, self.c + self.out * d), y0 + vs, u0 + us, state)

    def be(self, u, y, d, snbt):
        self.t.be(*self.p(u, y, d), snbt)

    def banner(self, u, y, d=1, nbt=None, color="blue"):
        """Kingdom wall banner hanging on the wall (cloth facing out) at row y (cloth hangs to y-1)."""
        deco.kingdom_wall_banner(self.t, *self.p(u, y, d), facing=self.out_dir, color=color, nbt=nbt)

    def sign(self, u, y, d, lines, wood="dark_oak", color="black", glowing=False):
        deco.sign_text(self.t, *self.p(u, y, d), B.wall_sign(wood, self.out_dir), lines, color=color, glowing=glowing)

    def hanging_sign(self, u, y, d, lines, wood="dark_oak", color="black", glowing=True):
        """Wall hanging sign (on a bracket) sticking out of the wall; readable along the street."""
        st = B.S(f"{wood}_wall_hanging_sign", facing=left_of(self.out_dir))
        deco.sign_text(self.t, *self.p(u, y, d), st, lines, color=color, glowing=glowing)

    def item_frame(self, u, y, d, item, rotation=0, glow=False):
        deco.item_frame(self.t, *self.p(u, y, d), self.out_dir, item, rotation, glow)

    def light_shaft(self, u, y, d, length=14.0, width=2.0, height=4.0, pitch=38.0):
        """Beam emitter at (u, y, d) shining into the building (d should be inside, e.g. -2)."""
        deco.light_shaft(self.t, *self.p(u, y, d), self.in_dir, length, width, height, pitch)


def arch_mask(w, h, kind=None, sharp=1.1):
    if kind == "round":
        return S.round_arch_mask(w, h)
    if kind == "pointed":
        return S.pointed_arch_mask(w, h, sharp)
    if kind == "segment":
        m = np.ones((w, h), bool)
        if w >= 3 and h >= 3:
            m[0, h - 1] = m[w - 1, h - 1] = False
        return m
    return np.ones((w, h), bool)


def ring_of(m, grow=1):
    """Outline (thickness ``grow``) around a mask; returned mask is padded by ``grow`` on every side; the bottom
    rows (below the sill) are excluded."""
    pad = np.pad(m, grow)
    cur = pad.copy()
    for _ in range(grow):
        g = cur.copy()
        g[1:, :] |= cur[:-1, :]
        g[:-1, :] |= cur[1:, :]
        g[:, 1:] |= cur[:, :-1]
        g[:, :-1] |= cur[:, 1:]
        cur = g
    r = cur & ~pad
    r[:, :grow] = False
    return r


# ====================================================================================== windows & doors
def window(f, u0, y0, w=2, h=3, *, glass=None, arch=None, depth=2, recess=1, frame=None, sill=SM,
           shutters=None, flowers=None, hood=None, keystone=None, mullion=None, sharp=1.1, transom=None,
           bars=False):
    """Window in face ``f``: columns u0..u0+w-1, rows y0..y0+h-1 (``arch`` shapes the head).

    The opening is cleared through ``depth`` wall layers; glass goes ``recess`` blocks behind the outer face.
    ``frame``: state of a surround ring on the outer face; ``sill``: material of a bracketed sill (upside-down
    stair, one block out); ``shutters``: wood -> open trapdoors beside the opening; ``flowers``: seed -> a window
    box under the sill row; ``hood``: material of a drip-mould above; ``keystone``: state above the head centre;
    ``mullion``: state for a centre post (odd widths >= 3) or the two centre columns (even widths >= 4);
    ``transom``: row index (from y0) of a horizontal bar."""
    glass = glass or B.pane()
    m = arch_mask(w, h, arch, sharp)
    for d in range(depth):
        f.mask(u0, y0, -d, m, AIR)
    f.mask(u0, y0, -recess, m, glass)
    if mullion and w >= 3:
        cols = [w // 2] if w % 2 else [w // 2 - 1, w // 2]
        if w >= 6:
            cols = [w // 3, w - 1 - w // 3]
        for c in cols:
            for v in range(h):
                if m[c, v] and (v < h - 1 or arch is None):
                    f.set(u0 + c, y0 + v, -recess, mullion)
    if transom is not None:
        for c in range(w):
            if m[c, transom]:
                f.set(u0 + c, y0 + transom, -recess, mullion or F(SM))
    if bars:
        for c in range(w):
            for v in range(h):
                if m[c, v]:
                    f.set(u0 + c, y0 + v, 0, B.S("iron_bars"))
    if frame:
        f.mask(u0 - 1, y0 - 1, 0, ring_of(m), frame)
    if sill:
        for c in range(w):
            f.set(u0 + c, y0 - 1, 1, B.stairs(sill, f.in_dir, "top"))
    if hood:
        top = h
        if arch in ("round", "pointed"):
            f.set(u0 + w // 2, y0 + h, 1, B.slab(hood, "bottom"))
            if w % 2 == 0:
                f.set(u0 + w // 2 - 1, y0 + h, 1, B.slab(hood, "bottom"))
        else:
            for c in range(-1, w + 1):
                f.set(u0 + c, y0 + top, 1, B.stairs(hood, f.in_dir, "bottom") if 0 <= c < w else
                      B.stairs(hood, f.in_dir, "top"))
    if keystone:
        f.set(u0 + (w - 1) // 2, y0 + h, 0, keystone)
        if w % 2 == 0 and w > 2:
            f.set(u0 + w // 2, y0 + h, 0, keystone)
    if shutters:
        for v in range(h):
            if m[0, v] and (arch is None or v < h - 1):
                f.set(u0 - 1, y0 + v, 1, B.trapdoor(shutters, f.out_dir, "bottom", True))
                f.set(u0 + w, y0 + v, 1, B.trapdoor(shutters, f.out_dir, "bottom", True))
    if flowers is not None:
        window_box(f, u0, u0 + w - 1, y0 - 1, seed=flowers)
    return m


def window_box(f, u0, u1, y, seed=0, wood="spruce"):
    """Flower box hanging under a window: blooming leaves on a bracket with a board in front."""
    for u in range(u0, u1 + 1):
        k = hash2(u, y, seed)
        f.set(u, y, 1, B.leaves("flowering_azalea" if k < 0.6 else "azalea"))
        f.set(u, y, 2, B.trapdoor(wood, f.out_dir, "bottom", True))
        f.set(u, y - 1, 1, B.stairs(wood, f.in_dir, "top"))


def doorway(f, u0, y0, w=2, h=3, *, wood="spruce", arch="round", depth=2, door_d=None, surround=None,
            open_=False, keystone=None, steps=None, iron=False, lintel_h=None):
    """Door opening (doors in the inner layer by default). ``w`` 1 or 2 doors; taller openings get a transom /
    fanlight above the doors (glass)."""
    oh = h if lintel_h is None else lintel_h
    m = arch_mask(w, oh, arch)
    for d in range(depth):
        f.mask(u0, y0, -d, m, AIR)
    dd = -(depth - 1) if door_d is None else door_d
    left = left_of(f.in_dir)
    wood_id = "iron" if iron else wood
    for i in range(min(w, 2) if w <= 2 else 0):
        if w == 1:
            hinge = "left"
        else:
            at_left = (i == 1) if left == f.u_dir else (i == 0)
            hinge = "left" if at_left else "right"
        lo, hi = B.door_states(wood_id, f.in_dir, hinge, open_)
        f.set(u0 + i, y0, dd, lo)
        f.set(u0 + i, y0 + 1, dd, hi)
    if w > 2:   # wide opening: double door in the middle, the rest stays open
        mid = u0 + (w - 2) // 2
        for i in range(2):
            at_left = (i == 1) if left == f.u_dir else (i == 0)
            lo, hi = B.door_states(wood_id, f.in_dir, "left" if at_left else "right", open_)
            f.set(mid + i, y0, dd, lo)
            f.set(mid + i, y0 + 1, dd, hi)
    # fanlight above the doors
    for c in range(w):
        for v in range(2, oh):
            if m[c, v]:
                f.set(u0 + c, y0 + v, dd, B.pane())
    if surround:
        f.mask(u0 - 1, y0 - 1, 0, ring_of(m), surround)
    if keystone:
        f.set(u0 + (w - 1) // 2, y0 + oh, 0, keystone)
        if w % 2 == 0:
            f.set(u0 + w // 2, y0 + oh, 0, keystone)
    if steps:
        for c in range(-1, w + 1):
            f.set(u0 + c, y0 - 1, 1, B.slab(steps, "bottom") if c in (-1, w) else B.stairs(steps, f.in_dir))
    return m


def canopy(f, u0, u1, y, mat=ROOF, d=1, brackets=SM, gold=True):
    """Small pent roof over a door: a row of stairs sloping away from the wall with brackets."""
    for u in range(u0, u1 + 1):
        f.set(u, y, d, B.stairs(mat, f.in_dir))
    for u in (u0, u1):
        f.set(u, y - 1, d, B.stairs(brackets, f.in_dir, "top"))
    if gold:
        f.set((u0 + u1) // 2, y + 1, d - 1 if d > 1 else d, B.S("gilded_finial"), only_empty=True)


def awning(f, u0, u1, y, depth=2, colors=("blue", "white"), valance=True, stripe_every=1):
    """Striped cloth awning sloping down and out from the wall at row y (on the wall face) over u0..u1."""
    for k in range(depth):
        for u in range(u0, u1 + 1):
            c = colors[((u - u0) // stripe_every) % len(colors)]
            f.set(u, y - k, 1 + k, B.wool(c))
    if valance:
        for u in range(u0, u1 + 1):
            c = colors[((u - u0) // stripe_every) % len(colors)]
            f.set(u, y - depth + 1, 1 + depth, B.S(f"{c}_wall_banner", facing=f.out_dir))


# ====================================================================================== roofs
class Roof:
    """Pitch-1/2 gable roof over a rectangle (inclusive wall lines) with the ridge along ``axis``."""

    def __init__(self, t, x0, z0, x1, z1, y0, mat=ROOF, axis="x", overhang=1, pitch=1, gable=None,
                 trim=GOLD, ridge=None, end_overhang=None, under=None):
        self.t = t
        self.x0, self.x1 = sorted((x0, x1))
        self.z0, self.z1 = sorted((z0, z1))
        self.y0, self.mat, self.axis, self.o, self.pitch = y0, mat, axis, overhang, pitch
        self.eo = overhang if end_overhang is None else end_overhang
        self.ridge_y = S.gable_roof(t, self.x0, self.z0, self.x1, self.z1, y0, mat, axis=axis, overhang=overhang,
                                    pitch=pitch, gable_state=gable,
                                    ridge_state=ridge if ridge is not None else (B.slab(GOLD) if trim else None),
                                    eave_trim=trim, end_overhang=self.eo)
        if under:
            self.underside(under)

    def span(self):
        if self.axis == "x":
            return self.z0 - self.o, self.z1 + self.o
        return self.x0 - self.o, self.x1 + self.o

    def surface_y(self, s):
        """Top y of the roof slope at the across-coordinate s (z for axis x, x for axis z)."""
        s0, s1 = self.span()
        k = min(s - s0, s1 - s)
        return self.y0 + self.pitch * (k + 1) - 1

    def underside(self, state):
        """Fill under each slope step with ``state`` down to the eave line (a solid attic for smaller houses)."""
        s0, s1 = self.span()
        for s in range(s0 + 1, s1):
            yt = self.surface_y(s) - 1
            if yt < self.y0:
                continue
            if self.axis == "x":
                if self.z0 <= s <= self.z1:
                    self.t.fill(self.x0, self.y0, s, self.x1, yt, s, state, only_empty=True)
            else:
                if self.x0 <= s <= self.x1:
                    self.t.fill(s, self.y0, self.z0, s, yt, self.z1, state, only_empty=True)


def dormer(t, roof: Roof, side, a, width=3, wall=PLASTER, frame="dark_oak", mat=None, glass=None, h=2,
           finial=True, set_back=0):
    """Gabled wall dormer on a pitch-1 ``Roof`` slope. ``side`` +1 = the slope facing +z (axis x) / +x (axis z);
    ``a`` = centre along the ridge axis; ``width`` odd (3 or 5). The dormer front stands on the wall line
    (moved in by ``set_back``); its window opens into the attic."""
    mat = mat or roof.mat
    hw = width // 2
    wall_line = (roof.z1 if roof.axis == "x" else roof.x1) if side > 0 else (roof.z0 if roof.axis == "x" else roof.x0)
    sf = wall_line - side * set_back
    yb = roof.surface_y(sf)            # base row (replaces the slope stair at the front line)
    ye = yb + h + 2                    # eave row of the dormer roof
    wall_st = B.full(wall) if wall in B.MATERIALS else B.S(wall)
    beam_axis = "x" if roof.axis == "x" else "z"

    def setp(al, s, y, st, **kw):
        if roof.axis == "x":
            t.set(al, y, s, st, **kw)
        else:
            t.set(s, y, al, st, **kw)
    # body: cheeks + cleared interior, walking inward until buried in the main roof
    s = sf
    for _ in range(16):
        surf = roof.surface_y(s)
        if surf >= ye + hw:
            break
        for al in range(a - hw, a + hw + 1):
            edge = abs(al - a) == hw
            for y in range(min(surf, yb), ye):
                if s == sf:
                    if edge:
                        setp(al, s, y, B.log(frame) if frame else wall_st)
                    elif y == yb:
                        setp(al, s, y, wall_st)
                    elif y <= yb + h:
                        setp(al, s, y, glass or B.pane())
                    else:
                        setp(al, s, y, B.log(frame, beam_axis) if frame else wall_st)
                elif edge:
                    if y >= surf:
                        setp(al, s, y, wall_st)
                        if y == ye - 1:
                            setp(al, s, ye, wall_st)
                elif y >= surf and y > yb:
                    setp(al, s, y, AIR)
        s -= side
    # gable triangle over the front
    for k in range(hw + 1):
        for al in range(a - hw + k, a + hw - k + 1):
            setp(al, sf, ye + k, wall_st)
    # small gable roof, ridge perpendicular to the main ridge, overhanging the front by one
    lo_f, hi_f = ("east", "west") if roof.axis == "x" else ("south", "north")
    s = sf + side
    for _ in range(18):
        surf = roof.surface_y(s)
        any_ = False
        for k in range(hw + 2):
            y = ye + k
            if y <= surf:
                continue
            any_ = True
            l, r = a - hw - 1 + k, a + hw + 1 - k
            if l >= r:
                setp(a, s, y, F(mat))
            else:
                setp(l, s, y, B.stairs(mat, lo_f))
                setp(r, s, y, B.stairs(mat, hi_f))
                if k >= 1 and s != sf + side:
                    setp(l, s, y - 1, F(mat), only_empty=True)
                    setp(r, s, y - 1, F(mat), only_empty=True)
        if not any_:
            break
        s -= side
    setp(a, sf + side, ye + hw + 2, B.slab(GOLD, "bottom"))
    if finial:
        setp(a, sf + side, ye + hw + 3, B.S("gilded_finial"))
    return yb


def _towards(axis, sign, along=False):
    if axis == "x":
        return "south" if sign > 0 else "north"
    return "east" if sign > 0 else "west"


def chimney(t, x, z, y0, top, mat="bricks", smoke=False, w=1, d=1, cap=SB):
    """Chimney stack (w x d) from y0 to top with a corbelled cap and a lit campfire (smoke) or a slab on top."""
    t.fill(x, y0, z, x + w - 1, top, z + d - 1, F(mat))
    for xx in range(x, x + w):
        t.set(xx, top, z - 1, B.stairs(cap, "south", "top"), only_empty=True)
        t.set(xx, top, z + d, B.stairs(cap, "north", "top"), only_empty=True)
    for zz in range(z, z + d):
        t.set(x - 1, top, zz, B.stairs(cap, "east", "top"), only_empty=True)
        t.set(x + w, top, zz, B.stairs(cap, "west", "top"), only_empty=True)
    for xx in range(x, x + w):
        for zz in range(z, z + d):
            if smoke and (xx, zz) == (x, z):
                t.set(xx, top + 1, zz, B.S("campfire", lit=True, signal_fire=False))
            else:
                t.set(xx, top + 1, zz, B.slab(cap, "bottom"))


# ====================================================================================== street furniture
def lamp(t, x, y, z, height=4, arms=True, gold=True, soul=False):
    """Ornate iron lamp post on the floor block at y: stone foot, blackstone shaft with a gilded collar,
    a cross of four hanging lanterns and a crowning lantern."""
    t.set(x, y + 1, z, B.full("polished_blackstone"))
    t.set(x, y + 2, z, B.wall("polished_blackstone"))
    for i in range(3, height + 1):
        t.set(x, y + i, z, B.wall("polished_blackstone"))
    if gold:
        t.set(x, y + 2, z, B.S("chiseled_polished_blackstone"))
    top = y + height + 1
    t.set(x, top, z, B.full("polished_blackstone"))
    if arms:
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            t.set(x + dx, top, z + dz, B.slab("polished_blackstone", "top"))
            t.set(x + dx, top - 1, z + dz, B.lantern(hanging=True, soul=soul))
    t.set(x, top + 1, z, B.lantern(soul=soul))
    if gold:
        t.set(x, top + 2, z, B.S("gilded_finial"))


def mast(t, x, y, z, height=9, sides=("north", "south"), banner_y=None, base=SM, nbt=None):
    """Banner mast on the floor block at y: stone pedestal, blackstone pole, a solid collar block carrying
    crest wall banners on ``sides``, and a gilded finial."""
    t.set(x, y + 1, z, F(base))
    t.set(x, y + 2, z, B.slab(base, "bottom"))
    t.set(x, y + 2, z, B.full("polished_blackstone"))
    for i in range(3, height):
        t.set(x, y + i, z, B.wall("polished_blackstone"))
    by = y + height - 2 if banner_y is None else banner_y
    t.set(x, by, z, F(GOLD))
    t.set(x, by + 1, z, B.full("polished_blackstone"))
    for i in range(by + 2, y + height + 1):
        t.set(x, i, z, B.wall("polished_blackstone"))
    t.set(x, y + height + 1, z, B.S("gilded_finial"))
    for s in sides:
        vx, _, vz = DIR_VEC[s]
        deco.kingdom_wall_banner(t, x + vx, by + 1, z + vz, facing=s, nbt=nbt)
    return by


def stall(t, x, y, z, facing="south", w=3, colors=("blue", "white"), goods=(), seed=0, wood="spruce"):
    """Market stall on the floor block at y: front counter towards ``facing``, fence posts, striped wool roof
    with a banner valance, goods on the counter. (x, z) = left-back corner seen from the customer."""
    f = facing
    fx, _, fz = DIR_VEC[f]
    rx, _, rz = DIR_VEC[left_of(f)]   # the stall extends to the customer's right = seller's left
    rx, rz = -rx, -rz
    depth = 3
    pts = []
    for i in range(w):
        for k in range(depth):
            pts.append((x + rx * i + fx * k, z + rz * i + fz * k, i, k))
    for (px, pz, i, k) in pts:
        corner = i in (0, w - 1) and k in (0, depth - 1)
        if corner:
            for yy in range(y + 1, y + 4):
                t.set(px, yy, pz, B.fence(wood))
        if k == depth - 1 and not corner:
            t.set(px, y + 1, pz, B.S("barrel", facing="up"))
        # roof: two-step slope towards the front
        c = colors[i % len(colors)]
        t.set(px, y + 4 + (1 if k == 0 else 0), pz, B.wool(c))
    # front valance
    for i in range(w):
        px, pz = x + rx * i + fx * depth, z + rz * i + fz * depth
        c = colors[i % len(colors)]
        t.set(px, y + 4, pz, B.S(f"{c}_wall_banner", facing=f))
    # counter goods
    gi = 0
    for i in range(w):
        px, pz = x + rx * i + fx * (depth - 1), z + rz * i + fz * (depth - 1)
        if i in (0, w - 1):
            t.set(px, y + 1, pz, B.S("barrel", facing="up"))
        g = goods[gi % len(goods)] if goods else None
        gi += 1
        if g:
            t.set(px, y + 2, pz, g)
    # back crates
    for i in range(1, w - 1):
        px, pz = x + rx * i, z + rz * i
        t.set(px, y + 1, pz, B.S("barrel", facing="north") if hash2(px, pz, seed) < 0.5 else B.chest(f))


def bench(t, x, y, z, facing="south", length=3, wood="dark_oak", arms="dark_oak"):
    deco.bench(t, x, y, z, facing=facing, length=length, wood=wood)


def planter_tree(t, x, y, z, kind="oak", seed=0, rim=SM, height=None, crown=None, r=1):
    """Square stone planter (2r+1) with grass, flowers along the edge and a small tree."""
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            edge = abs(dx) == r or abs(dz) == r
            t.set(x + dx, y + 1, z + dz, B.slab(rim, "top") if edge else B.S("grass_block"))
            if not edge:
                t.set(x + dx, y, z + dz, B.S("dirt"))
    if r >= 2:
        for dx in range(-r + 1, r):
            for dz in range(-r + 1, r):
                if (dx, dz) != (0, 0) and hash2(x + dx, z + dz, seed) < 0.5:
                    t.set(x + dx, y + 2, z + dz, B.S(("allium", "azure_bluet", "cornflower", "white_tulip")[
                        int(hash2(z + dz, x + dx, seed) * 4)]))
    if kind == "cypress":
        deco.tree_cypress(t, x, y + 1, z, seed=seed, height=height)
    elif kind == "azalea":
        deco.bush(t, x, y + 1, z, seed=seed, r=1.6, kind="flowering_azalea")
    else:
        deco.tree_oak(t, x, y + 1, z, seed=seed, height=height or 4, crown=crown or 2.2, kind=kind)


def paving(t, xs, zs, y, mix=None, seed=0):
    """Pave columns (arrays) at height y with a weighted hash mix [(state, weight), ...]."""
    mix = mix or [("stone_bricks", 40), ("polished_andesite", 22), ("andesite", 14), ("cobblestone", 12),
                  ("cracked_stone_bricks", 6), ("mossy_stone_bricks", 3), ("smooth_stone", 3)]
    xs = np.asarray(xs).ravel()
    zs = np.asarray(zs).ravel()
    w = np.array([m[1] for m in mix], float)
    c = np.cumsum(w / w.sum())
    u = hash2(xs, zs, seed)
    k = np.searchsorted(c, u, side="right").clip(0, len(mix) - 1)
    for i, (name, _) in enumerate(mix):
        sel = k == i
        if sel.any():
            t.set_many(xs[sel], np.full(int(sel.sum()), y), zs[sel], B.S(name))


# ====================================================================================== clock face
def clock_face(f, uc, yc, d=1, r=4, hour=10, minute=10, face="white_concrete", rim=GOLD, hands="black_concrete",
               marks="black_concrete", back=None):
    """Circular clock on face ``f`` centred at (uc, yc), standing ``d`` blocks proud of the wall plane."""
    R = r + 1
    for du in range(-R, R + 1):
        for dv in range(-R, R + 1):
            dist = math.hypot(du, dv)
            if dist > r + 0.45:
                continue
            u, y = uc + du, yc + dv
            if dist > r - 0.55:
                st = F(rim)
            else:
                st = B.S(face)
            f.set(u, y, d, st)
            if back is not None:
                f.set(u, y, d - 1, back)
    # hour marks: 12 positions (cardinal ones gilded)
    for k in range(12):
        a = math.radians(90 - k * 30)
        rr = r - 1.25
        u, y = uc + int(round(rr * math.cos(a))), yc + int(round(rr * math.sin(a)))
        f.set(u, y, d, F(rim) if k % 3 == 0 else B.S(marks))
    # hands
    def hand(angle_deg, length):
        a = math.radians(angle_deg)
        n = int(length * 3)
        for i in range(1, n + 1):
            s = i / 3.0
            u, y = uc + int(round(s * math.cos(a))), yc + int(round(s * math.sin(a)))
            f.set(u, y, d, B.S(hands))
    hand(90 - hour * 30 - minute * 0.5, r - 2.4)
    hand(90 - minute * 6, r - 1.4)
    f.set(uc, yc, d, F(rim))


# ====================================================================================== interiors
def rug(t, x0, z0, x1, z1, y, field="blue", border="yellow", inner=None):
    """Carpet rug on the floor block at y (carpet at y+1) with a border and an optional inner border."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y + 1, z0, x1, y + 1, z1, B.carpet(field))
    S.rect_ring(t, x0, z0, x1, z1, y + 1, B.carpet(border))
    if inner and x1 - x0 >= 4 and z1 - z0 >= 4:
        S.rect_ring(t, x0 + 2, z0 + 2, x1 - 2, z1 - 2, y + 1, B.carpet(inner))


def plank_floor(t, x0, z0, x1, z1, y, wood="spruce", accent="dark_oak", every=4, axis="x"):
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y, z0, x1, y, z1, F(f"{wood}_planks"))
    if accent:
        if axis == "x":
            for z in range(z0, z1 + 1, every):
                t.fill(x0, y, z, x1, y, z, F(f"{accent}_planks"))
        else:
            for x in range(x0, x1 + 1, every):
                t.fill(x, y, z0, x, y, z1, F(f"{accent}_planks"))


def beams(t, x0, z0, x1, z1, y, axis="x", every=3, wood="dark_oak"):
    """Exposed ceiling beams (logs) at height y across the room."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    if axis == "x":
        for z in range(z0, z1 + 1, every):
            t.fill(x0, y, z, x1, y, z, B.log(wood, "x"))
    else:
        for x in range(x0, x1 + 1, every):
            t.fill(x, y, z0, x, y, z1, B.log(wood, "z"))


def fireplace(f, uc, y0, w=3, h=4, d=-1, mat="bricks", mantel=SM, chimney_to=None, fire="campfire"):
    """Fireplace built into the inside of wall face ``f`` (``d`` <= -1 is the room side): hearth with a fire,
    brick surround, mantel shelf with candles. The hearth recess goes into the wall (towards d=0)."""
    hw = w // 2
    for u in range(uc - hw - 1, uc + hw + 2):
        for y in range(y0, y0 + h + 1):
            f.set(u, y, d, F(mat))
    for u in range(uc - hw, uc + hw + 1):
        for y in range(y0, y0 + 2):
            f.set(u, y, d, AIR)
    f.set(uc, y0, d, B.S(fire, lit=True))
    for u in range(uc - hw - 1, uc + hw + 2):
        f.set(u, y0 + 2, d - 1, B.stairs(mantel, f.out_dir, "top"))
    f.set(uc - hw - 1, y0 + 3, d - 1, B.candle(3, "white"))
    f.set(uc + hw + 1, y0 + 3, d - 1, B.candle(2, "white"))
    for u in range(uc - hw, uc + hw + 1):
        f.set(u, y0 - 1, d - 1, F("polished_blackstone_bricks"))


def shelf(t, x0, z0, x1, z1, y0, y1, facing, seed=0, kinds=None):
    """Shop shelving: a column/row of barrels, chests and bookshelves with goods (flower pots, candles)."""
    kinds = kinds or ["barrel", "bookshelf", "chiseled", "barrel"]
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                k = kinds[int(hash3(x, y, z, seed) * len(kinds))]
                if k == "barrel":
                    t.set(x, y, z, B.S("barrel", facing=facing))
                elif k == "chiseled":
                    occ = {f"slot_{i}_occupied": hash3(x + i, y, z, seed + 1) < 0.7 for i in range(6)}
                    t.set(x, y, z, B.S("chiseled_bookshelf", facing=facing, **occ))
                elif k == "chest":
                    t.set(x, y, z, B.chest(facing))
                else:
                    t.set(x, y, z, B.S(k))


def counter(t, x0, z0, x1, z1, y, top="dark_oak", front=None, facing="south"):
    """Shop counter on the floor block at y: barrels/planks body with a slab top (front panel trapdoors)."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y + 1, z0, x1, y + 1, z1, F(f"{top}_planks") if front is None else front)
    t.fill(x0, y + 2, z0, x1, y + 2, z1, B.slab(top, "bottom"))


def bed(t, x, y, z, facing="north", color="blue"):
    foot, head = B.bed_states(color, facing)
    vx, _, vz = DIR_VEC[facing]
    t.set(x, y + 1, z, foot)
    t.set(x + vx, y + 1, z + vz, head)


def table_set(t, x, y, z, chairs="spruce", top="spruce", cloth=None, candle=True):
    """Small square table (fence leg + pressure-plate/carpet top) with up to four chairs."""
    t.set(x, y + 1, z, B.fence(top))
    t.set(x, y + 2, z, B.S(f"{top}_pressure_plate") if not cloth else B.carpet(cloth))
    if candle:
        pass
    for (dx, dz, fac) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        t.set(x + dx, y + 1, z + dz, B.stairs(chairs, OPP[fac]))


def wall_trophies(f, u0, u1, y, d=-1, seed=0, items=("skeleton_wall_skull", "wither_skeleton_wall_skull",
                                                       "zombie_wall_head", "creeper_wall_head")):
    for u in range(u0, u1 + 1, 2):
        k = items[int(hash2(u, y, seed) * len(items))]
        f.set(u, y, d, B.S(k, facing=f.in_dir))


def potted(t, x, y, z, seed=0):
    plants = ["fern", "azalea_bush", "flowering_azalea_bush", "red_tulip", "white_tulip", "cornflower",
              "blue_orchid", "allium", "oxeye_daisy", "lily_of_the_valley"]
    t.set(x, y, z, B.potted(plants[int(hash2(x, z, seed) * len(plants))]))


# ====================================================================================== gothic parts
def gargoyle(f, u, y, d=1, mat="stone_bricks", head="polished_andesite"):
    """Small crouching gargoyle projecting from a cornice on face ``f``: body (stairs), head, wings (walls)."""
    f.set(u, y, d, B.stairs(mat, f.in_dir, "bottom"))
    f.set(u, y, d + 1, B.stairs(mat, f.in_dir, "top"))
    f.set(u, y + 1, d + 1, B.wall(mat))
    f.set(u, y + 1, d, B.stairs(mat, f.out_dir, "bottom"))


def statue(t, x, y, z, facing="south", mat="smooth_quartz", wings=False, gold=True):
    S.statue_figure(t, x, y, z, facing=facing, material=mat, wings=wings, gold_crown=gold)


def stained_lancet(f, u0, y0, w, h, colors, depth=2, recess=1, mullion=None, frame=None, sharp=1.2, seed=0,
                   pattern="bands", sill=None, keystone=None):
    """Pointed stained-glass window with a pattern (``bands``: horizontal colour bands with a centre medallion
    column; ``medallion``: concentric diamond medallions; ``figure``: a light central figure on a blue field)."""
    m = window(f, u0, y0, w, h, glass=B.pane("blue"), arch="pointed", depth=depth, recess=recess, frame=frame,
               sill=sill, mullion=mullion, sharp=sharp, keystone=keystone)
    n = len(colors)
    for c in range(w):
        for v in range(h):
            if not m[c, v]:
                continue
            cur = f.get(u0 + c, y0 + v, -recess)
            if "pane" not in cur or (mullion and cur == B.canon(mullion)):
                continue
            if pattern == "bands":
                k = (v // 2 + (1 if abs(c - (w - 1) / 2.0) < 1 else 0) * 2) % n
            elif pattern == "medallion":
                cyc = 5
                vv = v % cyc
                k = (abs(c - (w - 1) / 2.0) + abs(vv - 2)) // 1
                k = int(k + v // cyc) % n
            elif pattern == "figure":
                mid = abs(c - (w - 1) / 2.0) < 0.6
                k = 1 if mid and 2 < v < h - 3 else (0 if (v + c) % 3 else 2)
                k %= n
            else:
                k = int(hash2(u0 + c, y0 + v, seed) * n)
            f.set(u0 + c, y0 + v, -recess, B.pane(colors[k]))
    return m


def flying_buttress(t, axis, a, s_pier, s_wall, y_pier_top, y_wall, mat=CB, cap=SM, pinnacle_h=6, step=1):
    """Flying buttress at along-coordinate ``a``: a slanted arch from the top of an outer pier (at across-
    coordinate s_pier, top y_pier_top) up to the clerestory wall (s_wall, y_wall). axis "z": the nave runs along z,
    across = x. The flyer is a 1-wide band of blocks with an upside-down stair soffit and a stair top."""
    n = abs(s_wall - s_pier)
    sgn = 1 if s_wall > s_pier else -1
    face_up = ("east" if sgn > 0 else "west") if axis == "z" else ("south" if sgn > 0 else "north")
    face_dn = OPP[face_up]

    def P(s, y):
        return (s, y, a) if axis == "z" else (a, y, s)
    for i in range(1, n):
        s = s_pier + sgn * i
        y = y_pier_top + int(round((y_wall - y_pier_top) * i / n))
        t.set(*P(s, y), F(mat))
        t.set(*P(s, y + 1), B.stairs(cap, face_up))
        t.set(*P(s, y - 1), B.stairs(mat, face_dn, "top"))
    # pinnacle on the pier
    if pinnacle_h:
        S.pinnacle(t, *P(s_pier, y_pier_top + 1), height=pinnacle_h)
