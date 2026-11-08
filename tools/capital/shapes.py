"""Shape primitives. Every function takes a target ``t`` (a ``World`` or a ``Frame``) first and works in
that target's coordinates, so a whole building can be drawn in a rotated ``Frame``.

Conventions: boxes are inclusive; ``y0`` is the lowest block; stairs "facing" is the side of the tall back
(on a roof slope the stairs face the ridge).
"""
from __future__ import annotations

import math

import numpy as np

from . import blocks as B

AIR = "minecraft:air"


# ============================================================================== basic solids
def box(t, x0, y0, z0, x1, y1, z1, state, **kw):
    return t.fill(x0, y0, z0, x1, y1, z1, state, **kw)


def hollow_box(t, x0, y0, z0, x1, y1, z1, state, floor=True, ceiling=True, clear_inside=True):
    x0, x1 = sorted((x0, x1))
    y0, y1 = sorted((y0, y1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y0, z0, x1, y1, z0, state)
    t.fill(x0, y0, z1, x1, y1, z1, state)
    t.fill(x0, y0, z0, x0, y1, z1, state)
    t.fill(x1, y0, z0, x1, y1, z1, state)
    if floor:
        t.fill(x0, y0, z0, x1, y0, z1, state)
    if ceiling:
        t.fill(x0, y1, z0, x1, y1, z1, state)
    if clear_inside and x1 - x0 > 1 and z1 - z0 > 1:
        ya = y0 + (1 if floor else 0)
        yb = y1 - (1 if ceiling else 0)
        if yb >= ya:
            t.fill(x0 + 1, ya, z0 + 1, x1 - 1, yb, z1 - 1, AIR)


def walls(t, x0, z0, x1, z1, y0, y1, state):
    """Four perimeter walls of a rectangle (no floor/ceiling)."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y0, z0, x1, y1, z0, state)
    t.fill(x0, y0, z1, x1, y1, z1, state)
    t.fill(x0, y0, z0, x0, y1, z1, state)
    t.fill(x1, y0, z0, x1, y1, z1, state)


def line(t, p0, p1, state, **kw):
    """3D line (DDA, inclusive)."""
    x0, y0, z0 = p0
    x1, y1, z1 = p1
    n = int(max(abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)))
    if n == 0:
        t.set(x0, y0, z0, state)
        return
    s = np.linspace(0.0, 1.0, n + 1)
    xs = np.rint(x0 + (x1 - x0) * s).astype(np.int64)
    ys = np.rint(y0 + (y1 - y0) * s).astype(np.int64)
    zs = np.rint(z0 + (z1 - z0) * s).astype(np.int64)
    t.set_many(xs, ys, zs, state, **kw)


def disk_offsets(r: float, hollow=False, thickness=1.0):
    """Integer (dx, dz) offsets of a disk of radius r centred on a block (or between blocks if r has .5 tricks)."""
    R = int(math.ceil(r)) + 1
    dx, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    d = np.sqrt(dx * dx + dz * dz)
    m = d <= r + 0.3
    if hollow:
        m &= d > r + 0.3 - thickness - 0.0
    return dx[m], dz[m]


def _disk_mask_xz(cx, cz, r, even=False, R=None):
    """Offsets for a disk; ``even`` centres the disk on the corner between 4 blocks (cx, cz is the block SE of it)."""
    R = R if R is not None else int(math.ceil(r)) + 1
    dx, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    if even:
        fx, fz = dx + 0.5, dz + 0.5
    else:
        fx, fz = dx.astype(float), dz.astype(float)
    d = np.sqrt(fx * fx + fz * fz)
    return dx, dz, d


def cylinder(t, cx, cz, r, y0, y1, state, hollow=False, thickness=1.0, even=False, **kw):
    dx, dz, d = _disk_mask_xz(cx, cz, r, even)
    m = d <= r + 0.35
    if hollow:
        m &= d > r + 0.35 - thickness
    xs, zs = (cx + dx[m]), (cz + dz[m])
    ys = np.arange(min(y0, y1), max(y0, y1) + 1)
    X = np.repeat(xs[None, :], ys.size, 0)
    Z = np.repeat(zs[None, :], ys.size, 0)
    Y = np.repeat(ys[:, None], xs.size, 1)
    return t.set_many(X, Y, Z, state, **kw)


def ring_positions(cx, cz, r, even=False):
    """Positions on the outline of a disk (for parapets, corbels...)."""
    dx, dz, d = _disk_mask_xz(cx, cz, r, even)
    inside = d <= r + 0.35
    # boundary: inside with at least one 4-neighbour outside
    pad = np.pad(inside, 1)
    nb = pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
    b = inside & ~nb
    return cx + dx[b], cz + dz[b]


def _inward_facing(dx, dz):
    """Stairs facing (tall side) pointing from offset (dx, dz) towards the centre."""
    out = np.empty(dx.shape, dtype=object)
    ax = np.abs(dx) >= np.abs(dz)
    out[ax & (dx > 0)] = "west"
    out[ax & (dx <= 0)] = "east"
    out[~ax & (dz > 0)] = "north"
    out[~ax & (dz <= 0)] = "south"
    return out


def _outward_facing(dx, dz):
    inv = {"west": "east", "east": "west", "north": "south", "south": "north"}
    return np.vectorize(inv.get, otypes=[object])(_inward_facing(dx, dz))


# ============================================================================== spires and roofs
def cone(t, cx, cz, r, y0, height, material, stair_edges=True, even=False, hollow=False, finial=True,
         tip_state=None, profile=1.0, curve=0.0):
    """Round conical spire/roof. ``material`` is a roof material (full/stairs). The radius goes from ``r`` at
    ``y0`` to 0 at ``y0 + height``; ``profile`` > 1 makes it concave (bulging at the base), ``curve`` adds an
    ogee flare at the bottom. Places a gilded finial (or ``tip_state``) on top."""
    full = B.full(material)
    top_y = y0 + height
    prev = None
    layers = []
    for i in range(height):
        f = i / float(height)
        rr = r * (1.0 - f) ** profile
        if curve:
            rr += curve * r * max(0.0, 0.25 - f) * 4.0
        dx, dz, d = _disk_mask_xz(cx, cz, max(rr, 0.0), even, R=int(math.ceil(r * 1.3)) + 2)
        m = d <= rr + 0.35
        if not m.any():
            m = (dx == 0) & (dz == 0)
        layers.append((dx, dz, d, m))
    for i, (dx, dz, d, m) in enumerate(layers):
        y = y0 + i
        mm = m.copy()
        if hollow and i < height - 3:
            pad = np.pad(m, 1)
            core = pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
            core2 = np.pad(core, 1)
            core = core & core2[:-2, 1:-1] & core2[2:, 1:-1] & core2[1:-1, :-2] & core2[1:-1, 2:]
            mm &= ~core
        if stair_edges and B.has_stairs(material) and i + 1 < len(layers):
            nm = layers[i + 1][3]
            exposed = m & ~nm
            # never put stairs where the cone is one block wide
            if m.sum() > 1:
                fs = _inward_facing(dx, dz)
                for fac in ("north", "south", "east", "west"):
                    sel = exposed & (fs == fac)
                    if sel.any():
                        t.set_many(cx + dx[sel], np.full(sel.sum(), y), cz + dz[sel], B.stairs(material, fac))
                mm &= ~exposed
        if mm.any():
            t.set_many(cx + dx[mm], np.full(mm.sum(), y), cz + dz[mm], full)
    if finial and not even:
        t.set(cx, top_y, cz, tip_state or B.S("gilded_finial"))
    return top_y


def square_spire(t, cx, cz, half, y0, height, material, rib=None, finial=True, tip_state=None, even=False):
    """Steep square pyramid spire with stair edges and optional gilded corner ribs. ``half`` = half width
    (base spans cx-half..cx+half). Shrinks one block per side every ``height / half`` levels."""
    full = B.full(material)
    steps = max(1, half)
    per = height / float(steps + 1)
    for i in range(height):
        h = half - int(i / per)
        if h < 0:
            break
        x0, x1 = cx - h, cx + h + (1 if even else 0)
        z0, z1 = cz - h, cz + h + (1 if even else 0)
        y = y0 + i
        nxt = half - int((i + 1) / per)
        last_of_size = nxt < h
        if h == 0 and not even:
            t.set(cx, y, cz, full)
            continue
        t.fill(x0, y, z0, x1, y, z1, full)
        if last_of_size and B.has_stairs(material):
            # perimeter stairs facing inward
            t.fill(x0 + 1, y, z0, x1 - 1, y, z0, B.stairs(material, "south"))
            t.fill(x0 + 1, y, z1, x1 - 1, y, z1, B.stairs(material, "north"))
            t.fill(x0, y, z0 + 1, x0, y, z1 - 1, B.stairs(material, "east"))
            t.fill(x1, y, z0 + 1, x1, y, z1 - 1, B.stairs(material, "west"))
            for (x, z, f, sh) in ((x0, z0, "south", "outer_right"), (x1, z0, "south", "outer_left"),
                                  (x0, z1, "north", "outer_left"), (x1, z1, "north", "outer_right")):
                t.set(x, y, z, B.stairs(material, f, "bottom", sh))
        if rib:
            for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
                t.set(x, y, z, rib)
    top = y0 + height
    if finial:
        if even:
            pass
        else:
            t.set(cx, top, cz, tip_state or B.S("gilded_finial"))
    return top


def octagon_offsets(r: int):
    """Offsets of a filled octagon of 'radius' r (flat sides)."""
    R = r
    dx, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    cut = int(round(r * 0.42))
    m = (np.abs(dx) + np.abs(dz)) <= 2 * R - cut
    return dx, dz, m


def octagon_prism(t, cx, cz, r, y0, y1, state, hollow=False):
    dx, dz, m = octagon_offsets(r)
    if hollow:
        pad = np.pad(m, 1)
        core = pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
        m = m & ~core
    xs, zs = cx + dx[m], cz + dz[m]
    for y in range(y0, y1 + 1):
        t.set_many(xs, np.full(xs.size, y), zs, state)


def gable_roof(t, x0, z0, x1, z1, y0, material, axis="x", overhang=1, pitch=1, gable_state=None,
               ridge_state=None, inner_state=None, hollow=True, eave_trim=None, end_overhang=None):
    """Gable roof over the rectangle [x0..x1]x[z0..z1] whose eaves start at ``y0``.

    ``axis`` is the ridge direction. Slopes are stairs facing the ridge (``pitch`` blocks rise per block
    run). ``gable_state`` fills the triangular gable ends (walls) under the roof; ``ridge_state`` caps an
    odd-width ridge (default: full roof block); ``eave_trim`` puts a row of upside-down stairs (e.g. gilded
    trim) under the eaves. Returns the ridge y.
    """
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    full = B.full(material)
    eo = overhang if end_overhang is None else end_overhang
    if axis == "x":
        a0, a1 = x0 - eo, x1 + eo        # along ridge
        s0, s1 = z0 - overhang, z1 + overhang  # across
    else:
        a0, a1 = z0 - eo, z1 + eo
        s0, s1 = x0 - overhang, x1 + overhang
    width = s1 - s0 + 1
    half = width // 2
    odd = width % 2 == 1

    def put(a_lo, a_hi, s, y, state):
        if axis == "x":
            t.fill(a_lo, y, s, a_hi, y, s, state)
        else:
            t.fill(s, y, a_lo, s, y, a_hi, state)

    def put_gable(s, ylo, yhi):
        if gable_state is None or yhi < ylo:
            return
        if axis == "x":
            for a in (x0, x1):
                t.fill(a, ylo, s, a, yhi, s, gable_state)
        else:
            for a in (z0, z1):
                t.fill(s, ylo, a, s, yhi, a, gable_state)

    lo_face = "south" if axis == "x" else "east"   # stairs at s small face increasing s (toward ridge)
    hi_face = "north" if axis == "x" else "west"
    ridge_y = y0
    for k in range(half + (1 if odd else 0)):
        y_top = y0 + pitch * (k + 1) - 1
        y_bot = y0 + pitch * k
        sl, sh = s0 + k, s1 - k
        is_ridge = odd and k == half
        if is_ridge:
            for yy in range(y_bot, y_top + 1):
                put(a0, a1, sl, yy, full)
            put(a0, a1, sl, y_top + 1, ridge_state or B.slab(material, "bottom"))
            put_gable(sl, y0, y_top)
            ridge_y = y_top + 1
            break
        # slope blocks
        for yy in range(y_bot, y_top):
            put(a0, a1, sl, yy, full)
            put(a0, a1, sh, yy, full)
        put(a0, a1, sl, y_top, B.stairs(material, lo_face))
        put(a0, a1, sh, y_top, B.stairs(material, hi_face))
        if inner_state and k > 0:
            put(a0, a1, sl, y_bot - 1, inner_state)
            put(a0, a1, sh, y_bot - 1, inner_state)
        if not hollow and k > 0:
            pass
        # gable walls under this step (inside the outer wall line only)
        if k >= overhang:
            put_gable(sl, y0, y_top - 1)
            put_gable(sh, y0, y_top - 1)
        ridge_y = y_top
    if not odd:
        # even width: the two top rows meet; cap with a ridge row of slabs on top
        k = half - 1
        y_top = y0 + pitch * (k + 1) - 1
        if ridge_state:
            put(a0, a1, s0 + k, y_top + 1, ridge_state)
            put(a0, a1, s1 - k, y_top + 1, ridge_state)
            ridge_y = y_top + 1
    if eave_trim:
        # upside-down stairs of ``eave_trim`` material under both eaves, facing outward
        put(a0, a1, s0, y0 - 1, B.stairs(eave_trim, hi_face, "top"))
        put(a0, a1, s1, y0 - 1, B.stairs(eave_trim, lo_face, "top"))
    return ridge_y


def hip_roof(t, x0, z0, x1, z1, y0, material, overhang=1, pitch=1, ridge_state=None, fill_core=False):
    """Hipped roof: every level the rectangle shrinks by one on all sides."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    full = B.full(material)
    ax0, az0, ax1, az1 = x0 - overhang, z0 - overhang, x1 + overhang, z1 + overhang
    k = 0
    y = y0
    while ax0 <= ax1 and az0 <= az1:
        for p in range(pitch):
            yy = y + p
            last = p == pitch - 1
            if not last:
                for (a, b, c, d) in ((ax0, az0, ax1, az0), (ax0, az1, ax1, az1), (ax0, az0, ax0, az1),
                                     (ax1, az0, ax1, az1)):
                    t.fill(a, yy, b, c, yy, d, full)
                continue
            if ax1 - ax0 < 1 or az1 - az0 < 1:
                t.fill(ax0, yy, az0, ax1, yy, az1, full)
                if ridge_state:
                    t.fill(ax0, yy + 1, az0, ax1, yy + 1, az1, ridge_state)
                continue
            t.fill(ax0 + 1, yy, az0, ax1 - 1, yy, az0, B.stairs(material, "south"))
            t.fill(ax0 + 1, yy, az1, ax1 - 1, yy, az1, B.stairs(material, "north"))
            t.fill(ax0, yy, az0 + 1, ax0, yy, az1 - 1, B.stairs(material, "east"))
            t.fill(ax1, yy, az0 + 1, ax1, yy, az1 - 1, B.stairs(material, "west"))
            t.set(ax0, yy, az0, B.stairs(material, "south", "bottom", "outer_right"))
            t.set(ax1, yy, az0, B.stairs(material, "south", "bottom", "outer_left"))
            t.set(ax0, yy, az1, B.stairs(material, "north", "bottom", "outer_left"))
            t.set(ax1, yy, az1, B.stairs(material, "north", "bottom", "outer_right"))
            if fill_core and ax1 - ax0 > 1 and az1 - az0 > 1:
                t.fill(ax0 + 1, yy, az0 + 1, ax1 - 1, yy, az1 - 1, full)
        ax0, az0, ax1, az1 = ax0 + 1, az0 + 1, ax1 - 1, az1 - 1
        y += pitch
        k += 1
    return y


def dome(t, cx, cy, cz, r, state, ry=None, hollow=True, thickness=1.0, top_only=True):
    """(Half) ellipsoid dome centred at (cx, cy, cz) with horizontal radius r and vertical radius ry."""
    ry = ry or r
    R = int(math.ceil(max(r, ry))) + 1
    dx, dy, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(0 if top_only else -R, R + 1), np.arange(-R, R + 1),
                             indexing="ij")
    d = np.sqrt((dx / (r + 0.35)) ** 2 + (dy / (ry + 0.35)) ** 2 + (dz / (r + 0.35)) ** 2)
    m = d <= 1.0
    if hollow:
        d2 = np.sqrt((dx / max(r + 0.35 - thickness, 0.5)) ** 2 + (dy / max(ry + 0.35 - thickness, 0.5)) ** 2 +
                     (dz / max(r + 0.35 - thickness, 0.5)) ** 2)
        m &= d2 > 1.0
    return t.set_many(cx + dx[m], cy + dy[m], cz + dz[m], state)


# ============================================================================== arches & windows
def pointed_arch_mask(width: int, height: int, sharp=1.0):
    """Boolean mask [u, v] (u across, v up from the sill) of a pointed (gothic) opening ``width`` x ``height``.
    ``sharp`` 1.0 = equilateral arch; larger = more pointed (lancet)."""
    w = width
    R = w * sharp
    apex = math.sqrt(max(R * R - (R - w / 2.0) ** 2, 0.0))
    spring = max(0.0, height - apex)
    u = np.arange(w)[:, None] + 0.5
    v = np.arange(height)[None, :] + 0.5
    lc, rc = R, w - R   # circle centres (x) at the spring line: left arc centred right, etc.
    in_l = (u - (w - R)) ** 2 + (v - spring) ** 2 <= R * R + 0.25
    in_r = (u - R) ** 2 + (v - spring) ** 2 <= R * R + 0.25
    m = (v < spring) | (in_l & in_r)
    m = np.broadcast_to(m, (w, height)).copy()
    # keep the apex symmetric and at least one block in the top centre row
    if w % 2 == 1:
        m[w // 2, :] |= (np.arange(height) < height)
    return m


def round_arch_mask(width: int, height: int):
    w = width
    r = w / 2.0
    spring = max(0.0, height - r)
    u = np.arange(w)[:, None] + 0.5
    v = np.arange(height)[None, :] + 0.5
    m = (v < spring) | ((u - r) ** 2 + (v - spring) ** 2 <= r * r + 0.2)
    return np.broadcast_to(m, (w, height)).copy()


def _plane_points(axis, u0, v0, w0, mask):
    """Map a 2D mask in a wall plane to coordinates. axis 'x': wall runs along x (u->x, v->y, at z=w0);
    axis 'z': wall runs along z (u->z, v->y, at x=w0)."""
    us, vs = np.nonzero(mask)
    if axis == "x":
        return u0 + us, v0 + vs, np.full(us.size, w0)
    return np.full(us.size, w0), v0 + vs, u0 + us


def carve(t, axis, u0, y0, w0, mask, state, depth=1, step=1):
    """Write ``state`` into the masked area of a wall plane, ``depth`` layers deep (stepping +step in w)."""
    for d in range(depth):
        xs, ys, zs = _plane_points(axis, u0, y0, w0 + d * step, mask)
        t.set_many(xs, ys, zs, state)


def gothic_window(t, axis, u0, y0, w0, width, height, glass=None, frame=None, mullion=None, depth=1, step=1,
                  sill=None, sharp=1.0, head_trim=None, tracery=True):
    """Pointed window in a wall plane. ``(u0, y0, w0)`` is the bottom-left corner of the opening (u along the
    wall, w across it). ``glass`` fills the outer layer (default glass pane), ``mullion`` (default the frame
    state) splits windows of width >= 3; ``frame`` outlines the opening on the wall face; ``sill`` goes under it."""
    glass = glass or B.pane()
    m = pointed_arch_mask(width, height, sharp)
    # clear the opening through the full depth, put glass in the middle layer
    for d in range(depth):
        xs, ys, zs = _plane_points(axis, u0, y0, w0 + d * step, m)
        t.set_many(xs, ys, zs, AIR)
    mid = w0 + (depth // 2) * step
    xs, ys, zs = _plane_points(axis, u0, y0, mid, m)
    t.set_many(xs, ys, zs, glass)
    if tracery and width >= 3 and mullion:
        # central mullion up to the spring line, then glass in the head
        spring_rows = [v for v in range(height) if m[:, v].all()]
        top_full = max(spring_rows) if spring_rows else 0
        if width % 2 == 1:
            cols = [width // 2] if width < 7 else [width // 2 - 2, width // 2 + 2]
            if width == 5:
                cols = [2]
        else:
            cols = [width // 2 - 1, width // 2]
        for c in cols:
            for v in range(0, max(1, top_full - (1 if height > 6 else 0))):
                pt = _plane_points(axis, u0 + c, y0 + v, mid, np.ones((1, 1), bool))
                t.set_many(*pt, mullion)
    if frame:
        # outline: blocks around the opening on the outer face
        pad = np.pad(m, 1)
        grown = pad.copy()
        grown[1:, :] |= pad[:-1, :]
        grown[:-1, :] |= pad[1:, :]
        grown[:, 1:] |= pad[:, :-1]
        grown[:, :-1] |= pad[:, 1:]
        ring = grown & ~pad
        ring[:, 0] = False  # not below the sill row
        xs, ys, zs = _plane_points(axis, u0 - 1, y0 - 1, w0, ring)
        t.set_many(xs, ys, zs, frame)
    if sill:
        xs, ys, zs = _plane_points(axis, u0, y0 - 1, w0, np.ones((width, 1), bool))
        t.set_many(xs, ys, zs, sill)
    if head_trim:
        # a gilded keystone above the apex
        xs, ys, zs = _plane_points(axis, u0 + width // 2, y0 + height, w0, np.ones((1, 1), bool))
        t.set_many(xs, ys, zs, head_trim)
    return m


def round_window(t, axis, u0, y0, w0, width, height, glass=None, depth=1, step=1):
    m = round_arch_mask(width, height)
    for d in range(depth):
        xs, ys, zs = _plane_points(axis, u0, y0, w0 + d * step, m)
        t.set_many(xs, ys, zs, AIR)
    xs, ys, zs = _plane_points(axis, u0, y0, w0 + (depth // 2) * step, m)
    t.set_many(xs, ys, zs, glass or B.pane())
    return m


def rose_window(t, axis, cu, cy, w0, radius, frame_state, colors=("blue", "light_blue", "yellow", "red", "purple"),
                spokes=8, depth=1, step=1, glass_kind="block"):
    """Circular stained-glass rose window centred at (cu, cy) in a wall plane, with stone tracery."""
    R = int(math.ceil(radius)) + 1
    du, dv = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    d = np.sqrt(du * du + dv * dv)
    ang = (np.degrees(np.arctan2(dv, du)) + 360.0) % 360.0
    inside = d <= radius + 0.3
    ring = inside & (d > radius - 0.7)
    hub = d <= max(1.0, radius * 0.22)
    sector = 360.0 / spokes
    spoke = np.abs(((ang + sector / 2) % sector) - sector / 2) * np.pi / 180.0 * np.maximum(d, 0.1) < 0.55
    spoke &= (d > radius * 0.25) & (d < radius - 0.5)
    inner_ring = (np.abs(d - radius * 0.62) < 0.5)
    stone = ring | (spoke & ~hub) | (inner_ring & (radius >= 6))
    for dd in range(depth):
        w = w0 + dd * step
        pts = (cu + du[inside], cy + dv[inside])
        if axis == "x":
            t.set_many(pts[0], pts[1], np.full(pts[0].size, w), AIR)
        else:
            t.set_many(np.full(pts[0].size, w), pts[1], pts[0], AIR)
    w = w0 + (depth // 2) * step
    glass_m = inside & ~stone
    # colour bands: hub, inner petals, outer petals alternate by sector
    band = np.where(hub, 0, np.where(d < radius * 0.62, 1, 2))
    sec = (ang // sector).astype(int)
    groups = {}
    for b_, name in ((0, colors[2 % len(colors)]),):
        groups.setdefault(name, np.zeros_like(glass_m))
        groups[name] |= glass_m & (band == b_)
    for b_ in (1, 2):
        for parity in (0, 1):
            name = colors[(b_ * 2 + parity) % len(colors)] if b_ == 2 else colors[parity % len(colors)]
            sel = glass_m & (band == b_) & ((sec % 2) == parity)
            groups.setdefault(name, np.zeros_like(glass_m))
            groups[name] |= sel
    def put(mask, state):
        if not mask.any():
            return
        if axis == "x":
            t.set_many(cu + du[mask], cy + dv[mask], np.full(int(mask.sum()), w), state)
        else:
            t.set_many(np.full(int(mask.sum()), w), cy + dv[mask], cu + du[mask], state)
    for name, mask in groups.items():
        put(mask, B.glass(name) if glass_kind == "block" else B.pane(name))
    put(stone & inside, frame_state)


# ============================================================================== wall details
def crenellate(t, x0, z0, x1, z1, y, state, period=2, phase=0, cap=None):
    """Merlons along the perimeter of a rectangle at height y (every other block)."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    pts = []
    for x in range(x0, x1 + 1):
        pts += [(x, z0), (x, z1)]
    for z in range(z0 + 1, z1):
        pts += [(x0, z), (x1, z)]
    for (x, z) in pts:
        k = (x - x0) + (z - z0)
        if (k + phase) % period == 0:
            t.set(x, y, z, state)
            if cap:
                t.set(x, y + 1, z, cap)


def crenellate_line(t, p0, p1, y, state, period=2, phase=0, cap=None):
    (x0, z0), (x1, z1) = p0, p1
    n = max(abs(x1 - x0), abs(z1 - z0))
    for i in range(n + 1):
        if (i + phase) % period == 0:
            x = x0 + (0 if n == 0 else round((x1 - x0) * i / n))
            z = z0 + (0 if n == 0 else round((z1 - z0) * i / n))
            t.set(x, y, z, state)
            if cap:
                t.set(x, y + 1, z, cap)


def crenellate_ring(t, cx, cz, r, y, state, period=2, even=False, cap=None):
    xs, zs = ring_positions(cx, cz, r, even)
    ang = np.arctan2(zs - cz, xs - cx)
    order = np.argsort(ang)
    for i, k in enumerate(order):
        if i % period == 0:
            t.set(int(xs[k]), y, int(zs[k]), state)
            if cap:
                t.set(int(xs[k]), y + 1, int(zs[k]), cap)


def corbel_ring(t, cx, cz, r, y, material, even=False):
    """Upside-down stairs around a ring at height y (machicolation / corbel table under a wider parapet)."""
    xs, zs = ring_positions(cx, cz, r, even)
    dx, dz = xs - cx, zs - cz
    fs = _outward_facing(dx, dz)
    for fac in ("north", "south", "east", "west"):
        sel = fs == fac
        if sel.any():
            t.set_many(xs[sel], np.full(int(sel.sum()), y), zs[sel], B.stairs(material, fac, "top"))


def corbel_rect(t, x0, z0, x1, z1, y, material):
    """Upside-down stairs facing outward around a rectangle's outline (one block outside the given box)."""
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y, z0 - 1, x1, y, z0 - 1, B.stairs(material, "north", "top"))
    t.fill(x0, y, z1 + 1, x1, y, z1 + 1, B.stairs(material, "south", "top"))
    t.fill(x0 - 1, y, z0, x0 - 1, y, z1, B.stairs(material, "west", "top"))
    t.fill(x1 + 1, y, z0, x1 + 1, y, z1, B.stairs(material, "east", "top"))


def rect_ring(t, x0, z0, x1, z1, y, state):
    x0, x1 = sorted((x0, x1))
    z0, z1 = sorted((z0, z1))
    t.fill(x0, y, z0, x1, y, z0, state)
    t.fill(x0, y, z1, x1, y, z1, state)
    t.fill(x0, y, z0, x0, y, z1, state)
    t.fill(x1, y, z0, x1, y, z1, state)


def buttress(t, x, z, y0, height, out_dir, width=1, depth=2, material="castle_bricks", cap_material=None,
             pinnacle=True, pinnacle_height=5, set_back_every=0):
    """Stepped buttress against a wall at (x, z) (the wall face block), projecting ``depth`` blocks towards
    ``out_dir``, ``width`` blocks wide (centred), ``height`` tall, sloped top with stairs, and a pinnacle."""
    from .world import DIR_VEC, left_of
    ox, _, oz = DIR_VEC[out_dir]
    lx, _, lz = DIR_VEC[left_of(out_dir)]
    full = B.full(material)
    cap = cap_material or material
    hw = (width - 1) // 2
    for k in range(1, depth + 1):
        # each layer outward ends a bit lower
        top = y0 + height - 1 - (k - 1) * 2
        if set_back_every:
            top = y0 + height - 1 - (k - 1) * set_back_every
        for s in range(-hw, width - hw):
            bx, bz = x + ox * k + lx * s, z + oz * k + lz * s
            t.fill(bx, y0, bz, bx, top, bz, full)
            if B.has_stairs(cap):
                t.set(bx, top + 1, bz, B.stairs(cap, {"north": "south", "south": "north", "east": "west",
                                                      "west": "east"}[out_dir]))
    if pinnacle:
        px, pz = x + ox, z + oz
        t.set(px, y0 + height, pz, full)
        for i in range(1, pinnacle_height - 1):
            t.set(px, y0 + height + i, pz, B.wall("castle_bricks"))
        t.set(px, y0 + height + pinnacle_height - 1, pz, B.S("gilded_finial"))


def pinnacle(t, x, y, z, height=5, base=None, shaft=None, tip=None):
    """Small 1x1 pinnacle: base block, wall shaft, gilded finial."""
    base = base or B.full("castle_bricks")
    shaft = shaft or B.wall("castle_bricks")
    tip = tip or B.S("gilded_finial")
    t.set(x, y, z, base)
    for i in range(1, height - 1):
        t.set(x, y + i, z, shaft)
    t.set(x, y + height - 1, z, tip)


def pinnacle3(t, cx, y, cz, height=9, material="castle_bricks", roof="royal_roof_tiles", gold=True):
    """3x3 pinnacle turret: a short shaft with a square spire and finial."""
    full = B.full(material)
    t.fill(cx - 1, y, cz - 1, cx + 1, y + 2, cz + 1, full)
    if gold:
        rect_ring(t, cx - 1, cz - 1, cx + 1, cz + 1, y + 3, B.slab("gilded_trim", "bottom"))
    t.set(cx, y + 3, cz, full)
    for i in range(4, height - 1):
        t.set(cx, y + i, cz, B.wall(material) if i < 5 else B.full(roof))
    # small cross of stairs
    t.set(cx + 1, y + 4, cz, B.stairs(roof, "west"))
    t.set(cx - 1, y + 4, cz, B.stairs(roof, "east"))
    t.set(cx, y + 4, cz + 1, B.stairs(roof, "north"))
    t.set(cx, y + 4, cz - 1, B.stairs(roof, "south"))
    t.set(cx, y + 4, cz, B.full(roof))
    for i in range(5, height - 1):
        t.set(cx, y + i, cz, B.full(roof))
    t.set(cx, y + height - 1, cz, B.S("gilded_finial"))


def balustrade(t, x0, z0, x1, z1, y, post=None, rail=None, cap=None, post_every=4):
    """Straight balustrade along a line at height y: posts every ``post_every`` blocks, walls between, and
    an optional slab cap on the posts."""
    post = post or B.full("smooth_castle_stone")
    rail = rail or B.wall("castle_bricks")
    n = max(abs(x1 - x0), abs(z1 - z0))
    for i in range(n + 1):
        x = x0 + (0 if n == 0 else round((x1 - x0) * i / n))
        z = z0 + (0 if n == 0 else round((z1 - z0) * i / n))
        if i % post_every == 0 or i == n:
            t.set(x, y, z, post)
            if cap:
                t.set(x, y + 1, z, cap)
        else:
            t.set(x, y, z, rail)


def stair_run(t, x, y, z, direction, count, width, material, side=None, fill_below=None, landing_every=0):
    """Straight flight of stairs climbing towards ``direction`` from (x, y, z) (first step), ``width`` wide
    (extending to the right of the walking direction), ``count`` steps. Returns (x, y, z) after the last step."""
    from .world import DIR_VEC, right_of
    dx, _, dz = DIR_VEC[direction]
    rx, _, rz = DIR_VEC[right_of(direction)]
    full = B.full(fill_below) if fill_below else None
    for i in range(count):
        sx, sy, sz = x + dx * i, y + i, z + dz * i
        for w in range(width):
            px, pz = sx + rx * w, sz + rz * w
            t.set(px, sy, pz, B.stairs(material, direction))
            if full and sy - 1 >= y:
                t.fill(px, y, pz, px, sy - 1, pz, full)
    return x + dx * count, y + count, z + dz * count


def statue_figure(t, x, y, z, facing="south", material="smooth_castle_stone", wings=False, gold_crown=True,
                  plinth="carved_castle_stone"):
    """Small stylised standing statue (plinth + body + head), optionally winged (angel)."""
    from .world import DIR_VEC, left_of, right_of
    fx, _, fz = DIR_VEC[facing]
    lx, _, lz = DIR_VEC[left_of(facing)]
    full = B.full(material)
    t.fill(x - 1, y, z - 1, x + 1, y, z + 1, B.full(plinth) if plinth in B.MATERIALS else B.S(plinth))
    t.set(x, y + 1, z, full)                       # legs/robe
    t.set(x, y + 2, z, full)                       # torso
    t.set(x, y + 3, z, B.S("chiseled_quartz_block") if material == "quartz_block" else full)  # head
    t.set(x + lx, y + 2, z + lz, B.stairs(material, right_of(facing), "top"))  # arms
    t.set(x - lx, y + 2, z - lz, B.stairs(material, left_of(facing), "top"))
    if gold_crown:
        t.set(x, y + 4, z, B.slab("gilded_trim", "bottom"))
    if wings:
        bx, bz = x - fx, z - fz
        for s in (1, -1):
            t.set(bx + lx * s, y + 2, bz + lz * s, B.full(material))
            t.set(bx + lx * s * 2, y + 3, bz + lz * s * 2, B.full(material))
            t.set(bx + lx * s, y + 3, bz + lz * s, B.full(material))
            t.set(bx + lx * s * 2, y + 4, bz + lz * s * 2, B.wall("castle_bricks"))
