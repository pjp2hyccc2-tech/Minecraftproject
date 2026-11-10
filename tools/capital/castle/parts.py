"""Reusable castle parts: foundations, round towers, curtain walls, buttressed gothic walls, vaults."""
from __future__ import annotations

import math

import numpy as np

from .. import blocks as B
from .. import shapes as S
from ..noise import hash2, hash3
from ..world import X0, Z0

AIR = "minecraft:air"
CB = "castle_bricks"
SM = "smooth_castle_stone"
GOLD = "gilded_trim"
ROOF = "royal_roof_tiles"
PALE = "pale_roof_tiles"


def ground(T, x, z):
    return int(T.H[x - X0, z - Z0])


def foundation(w, T, xs, zs, top_y, state=None, plinth=None):
    """Fill each column (xs, zs) from the terrain surface up to ``top_y`` (masonry footing on slopes)."""
    state = state or B.full("stone_bricks")
    for x, z in zip(np.ravel(xs), np.ravel(zs)):
        g = ground(T, int(x), int(z))
        if g < top_y:
            w.fill(int(x), max(g - 1, -6), int(z), int(x), top_y, int(z), state)


def disk_xz(cx, cz, r, even=False):
    R = int(math.ceil(r)) + 1
    dx, dz = np.meshgrid(np.arange(-R, R + 1), np.arange(-R, R + 1), indexing="ij")
    fx = dx + (0.5 if even else 0.0)
    fz = dz + (0.5 if even else 0.0)
    m = np.sqrt(fx * fx + fz * fz) <= r + 0.35
    return cx + dx[m], cz + dz[m]


def mixed_bricks(w, xs, ys, zs, seed=0, base=CB, accent="smooth_castle_stone", ratio=0.0):
    """Castle bricks with a sprinkle of accent blocks (subtle texture variation)."""
    xs, ys, zs = np.ravel(xs), np.ravel(ys), np.ravel(zs)
    if ratio <= 0:
        w.set_many(xs, ys, zs, B.full(base))
        return
    u = hash3(xs, ys, zs, seed)
    a = u < ratio
    w.set_many(xs[~a], ys[~a], zs[~a], B.full(base))
    w.set_many(xs[a], ys[a], zs[a], B.full(accent))


# ============================================================================== round towers
def round_tower(w, T, cx, cz, r, base_y, top_y, cone_h=None, roof=ROOF, parapet=True, hollow=True,
                floors=(), windows=True, slit_every=7, courses=(8, 16), banner=None, even=False, dome=False,
                cone_profile=1.25, finial=True, wall_mat=CB, interior_floor_mat="spruce_planks"):
    """Round tower: footing to the ground, body, string courses, slit windows, corbelled crenellated
    parapet (or eaves) with a gilded band, and a conical roof. Returns the roof tip y."""
    xs, zs = disk_xz(cx, cz, r, even)
    foundation(w, T, xs, zs, base_y - 1)
    S.cylinder(w, cx, cz, r, base_y, top_y, B.full(wall_mat), even=even)
    # plinth
    S.cylinder(w, cx, cz, r + 1, base_y, base_y + 1, B.full("stone_bricks"), hollow=True, even=even)
    S.cylinder(w, cx, cz, r + 1, base_y + 2, base_y + 2, B.slab("stone_bricks", "bottom"), hollow=True,
               even=even)
    for c in courses:
        if base_y + c < top_y - 2:
            S.cylinder(w, cx, cz, r, base_y + c, base_y + c, B.full(SM), hollow=True, even=even)
    if hollow:
        S.cylinder(w, cx, cz, r - 1.6, base_y + 1, top_y - 1, AIR, even=even)
        for fy in floors:
            S.cylinder(w, cx, cz, r - 1.6, fy, fy, B.full(interior_floor_mat), even=even)
    if windows:
        _tower_slits(w, cx, cz, r, base_y + 5, top_y - 3, slit_every, even)
    if parapet:
        S.corbel_ring(w, cx, cz, r + 1, top_y - 1, SM, even=even)
        S.cylinder(w, cx, cz, r + 1, top_y, top_y, B.full(SM), even=even)
        S.cylinder(w, cx, cz, r + 1, top_y + 1, top_y + 1, B.full(wall_mat), hollow=True, even=even)
        S.crenellate_ring(w, cx, cz, r + 1, top_y + 2, B.full(wall_mat), period=2, even=even)
        gx, gz = S.ring_positions(cx, cz, r + 1, even)
        w.set_many(gx, np.full(gx.size, top_y), gz, B.full(GOLD))
        rb = r - 1.0
        y0 = top_y + 1
    else:
        S.cylinder(w, cx, cz, r, top_y, top_y, B.full(GOLD), hollow=True, even=even)
        rb = r + 0.9
        y0 = top_y + 1
    if cone_h:
        if dome:
            S.dome(w, cx, y0, cz, rb, B.full(roof), ry=cone_h, hollow=True, thickness=1.2)
            tip = y0 + cone_h + 1
            if finial:
                w.set(cx, tip, cz, B.S("gilded_finial"))
            return tip
        tip = S.cone(w, cx, cz, rb, y0, cone_h, roof, even=even, profile=cone_profile, finial=finial)
        # gilded band at the foot of the roof
        gx, gz = S.ring_positions(cx, cz, rb, even)
        w.set_many(gx, np.full(gx.size, y0), gz, B.full(GOLD))
        return tip
    return top_y


def _tower_slits(w, cx, cz, r, y0, y1, every, even=False):
    for k, (dx, dz) in enumerate(((1, 0), (0, 1), (-1, 0), (0, -1))):
        rr = int(round(r))
        x = cx + dx * rr + (1 if even and dx > 0 else 0)
        z = cz + dz * rr + (1 if even and dz > 0 else 0)
        for y in range(y0 + (k % 2) * 3, y1, every):
            w.set(x, y, z, B.pane())
            w.set(x, y + 1, z, B.pane())
            w.set(x - dx, y, z - dz, AIR)
            w.set(x - dx, y + 1, z - dz, AIR)


# ============================================================================== curtain wall
def curtain_wall(w, T, pts, base_y, top_y, center, thick=1.6, closed=False, mat=CB):
    """Thick wall along a polyline with an outer crenellated parapet, a walkway, corbels and slits."""
    xs_all = [p[0] for p in pts]
    zs_all = [p[1] for p in pts]
    x0, x1 = min(xs_all) - 4, max(xs_all) + 4
    z0, z1 = min(zs_all) - 4, max(zs_all) + 4
    X, Z = np.meshgrid(np.arange(x0, x1 + 1), np.arange(z0, z1 + 1), indexing="ij")
    d = np.full(X.shape, 1e9)
    tpar = np.zeros(X.shape)
    seg_out = np.zeros(X.shape + (2,))
    segs = list(zip(pts[:-1], pts[1:]))
    if closed:
        segs.append((pts[-1], pts[0]))
    for (ax, az), (bx, bz) in segs:
        vx, vz = bx - ax, bz - az
        L2 = vx * vx + vz * vz
        t = np.clip(((X - ax) * vx + (Z - az) * vz) / L2, 0, 1)
        px, pz = ax + t * vx, az + t * vz
        dd = np.sqrt((X - px) ** 2 + (Z - pz) ** 2)
        better = dd < d
        d = np.where(better, dd, d)
        tpar = np.where(better, t * math.sqrt(L2), tpar)
        nx, nz = vz, -vx
        mx, mz = (ax + bx) / 2 - center[0], (az + bz) / 2 - center[1]
        if nx * mx + nz * mz < 0:
            nx, nz = -nx, -nz
        nl = math.hypot(nx, nz)
        seg_out[better] = (nx / nl, nz / nl)
    body = d <= thick
    bx_, bz_ = X[body], Z[body]
    foundation(w, T, bx_, bz_, base_y - 1)
    for y in range(base_y, top_y + 1):
        w.set_many(bx_, np.full(bx_.size, y), bz_, B.full(mat))
    w.set_many(bx_, np.full(bx_.size, base_y), bz_, B.full("stone_bricks"))
    w.set_many(bx_, np.full(bx_.size, base_y + 1), bz_, B.full("stone_bricks"))
    # outer edge: columns of the body whose outward neighbour is outside the body
    ox = np.rint(seg_out[..., 0]).astype(int)
    oz = np.rint(seg_out[..., 1]).astype(int)
    outer = []
    inner = []
    for i, j in zip(*np.nonzero(body)):
        x, z = int(X[i, j]), int(Z[i, j])
        ni, nj = i + ox[i, j], j + oz[i, j]
        mi, mj = i - ox[i, j], j - oz[i, j]
        out_free = not (0 <= ni < X.shape[0] and 0 <= nj < X.shape[1] and body[ni, nj])
        in_free = not (0 <= mi < X.shape[0] and 0 <= mj < X.shape[1] and body[mi, mj])
        if out_free:
            outer.append((x, z, tpar[i, j], ox[i, j], oz[i, j]))
        elif in_free:
            inner.append((x, z))
    for (x, z, tp, dx, dz) in outer:
        w.set(x, top_y + 1, z, B.full(mat))
        if int(round(tp)) % 2 == 0:
            w.set(x, top_y + 2, z, B.full(mat))
        # corbels under a slight overhang look: smooth string course and slits
        w.set(x, top_y - 1, z, B.full(SM))
        w.set(x, base_y + 8, z, B.full(SM))
        if int(round(tp)) % 9 == 4:
            for yy in (base_y + 10, base_y + 11):
                w.set(x, yy, z, AIR)
                w.set(x - dx, yy, z - dz, AIR)
    for k, (x, z) in enumerate(inner):
        w.set(x, top_y + 1, z, B.slab(SM, "bottom"))
    # lanterns along the wall walk (night silhouette), every ~10 blocks on the inner parapet
    for k, (x, z) in enumerate(inner):
        if k % 10 == 5:
            w.set(x, top_y + 1, z, B.full(SM))
            w.set(x, top_y + 2, z, B.lantern())
    return body


# ============================================================================== walls with bays
def gothic_wall_bays(w, axis, wall_coord, out_sign, u_list, y0, y_top, win_w, win_h, win_y0, mat=CB,
                     buttress_u=(), b_depth=2, b_width=2, glass=None, mullion=None, frame=None,
                     pinnacle_h=6, sill=None, wall_thick=2, keystone=True, sharp=1.1):
    """Windows and buttresses on one wall plane.

    axis 'x': the wall runs along x at z = wall_coord (u = x); axis 'z': runs along z at x = wall_coord (u = z).
    ``out_sign`` = +1 if outside is +z / +x. ``u_list`` = window centres (u). The outer face is ``wall_coord``;
    the wall extends ``wall_thick`` blocks inward."""
    glass = glass or B.pane()
    for u in u_list:
        u0 = u - win_w // 2
        w0 = wall_coord - out_sign * (wall_thick - 1)
        S.gothic_window(w, axis, u0, win_y0, w0, win_w, win_h, glass=glass,
                        mullion=mullion, frame=None, depth=wall_thick, step=out_sign, sharp=sharp,
                        head_trim=B.full(GOLD) if keystone else None)
        if frame:
            # frame on the outer face only
            m = S.pointed_arch_mask(win_w, win_h, sharp)
            pad = np.pad(m, 1)
            g = pad.copy()
            g[1:, :] |= pad[:-1, :]
            g[:-1, :] |= pad[1:, :]
            g[:, 1:] |= pad[:, :-1]
            g[:, :-1] |= pad[:, 1:]
            ring = g & ~pad
            ring[:, 0] = False
            us, vs = np.nonzero(ring)
            if axis == "x":
                w.set_many(u0 - 1 + us, win_y0 - 1 + vs, np.full(us.size, wall_coord), frame)
            else:
                w.set_many(np.full(us.size, wall_coord), win_y0 - 1 + vs, u0 - 1 + us, frame)
        if sill:
            for k in range(win_w):
                if axis == "x":
                    w.set(u0 + k, win_y0 - 1, wall_coord + out_sign, sill)
                else:
                    w.set(wall_coord + out_sign, win_y0 - 1, u0 + k, sill)
    face_out = {("x", 1): "south", ("x", -1): "north", ("z", 1): "east", ("z", -1): "west"}[(axis, out_sign)]
    for u in buttress_u:
        for k in range(1, b_depth + 1):
            top = y_top - (k - 1) * 3
            for s in range(b_width):
                uu = u - b_width // 2 + s
                c = wall_coord + out_sign * k
                if axis == "x":
                    w.fill(uu, y0, c, uu, top, c, B.full(mat))
                    w.set(uu, top + 1, c, B.stairs(mat, _opp(face_out)))
                else:
                    w.fill(c, y0, uu, c, top, uu, B.full(mat))
                    w.set(c, top + 1, uu, B.stairs(mat, _opp(face_out)))
            # set-off string at mid height
        if pinnacle_h:
            c = wall_coord + out_sign
            uu = u - b_width // 2
            if axis == "x":
                S.pinnacle(w, uu, y_top + 1, c, pinnacle_h)
                if b_width > 1:
                    S.pinnacle(w, uu + 1, y_top + 1, c, pinnacle_h)
            else:
                S.pinnacle(w, c, y_top + 1, uu, pinnacle_h)
                if b_width > 1:
                    S.pinnacle(w, c, y_top + 1, uu + 1, pinnacle_h)


def _opp(f):
    return {"north": "south", "south": "north", "east": "west", "west": "east"}[f]


# ============================================================================== vaults
def pointed_vault(w, a0, a1, s0, s1, y_spring, material=SM, axis="z", shell=2, k=0.62, ribs=(), rib_state=None,
                  smooth=True):
    """Pointed barrel vault over the interior span s0..s1 (across) running a0..a1 (along ``axis``).

    Height above the spring at offset u from the near wall follows two arcs with radius k*span.
    Places ``shell`` layers of ``material`` above the surface; the stepped underside is smoothed with
    upside-down stairs. ``ribs`` = along-coordinates of transverse ribs (rib_state, 1 block deeper)."""
    span = s1 - s0 + 1
    R = k * span
    prof = []
    for i in range(span):
        u = i + 0.5
        uu = min(u, span - u)
        h = math.sqrt(max(R * R - (uu - R) ** 2, 0.0))
        prof.append(h)
    hs = [int(math.floor(y_spring + h)) for h in prof]
    mat_full = B.full(material)
    for i in range(span):
        s = s0 + i
        h = hs[i]
        top = max(hs) + shell
        for (a_lo, a_hi) in ((a0, a1),):
            if axis == "z":
                w.fill(s, h, a_lo, s, h + shell, a_hi, mat_full)
            else:
                w.fill(a_lo, h, s, a_hi, h + shell, s, mat_full)
        if smooth and B.has_stairs(material):
            left = i < span / 2
            # upside-down stair at the surface where the next block towards the wall is lower
            jw = i - 1 if left else i + 1
            if 0 <= jw < span and hs[jw] < h:
                face = ("west" if axis == "z" else "north") if left else ("east" if axis == "z" else "south")
                st = B.stairs(material, face, "top")
                if axis == "z":
                    w.fill(s, h - 1, a0, s, h - 1, a1, st)
                else:
                    w.fill(a0, h - 1, s, a1, h - 1, s, st)
    if ribs and rib_state:
        for a in ribs:
            for i in range(span):
                s = s0 + i
                h = hs[i]
                if axis == "z":
                    w.set(s, h - 1, a, rib_state)
                    w.set(s, h, a, rib_state)
                else:
                    w.set(a, h - 1, s, rib_state)
                    w.set(a, h, s, rib_state)
    return hs


def window_light(w, axis, wall_inner, in_sign, u, y, facing, length=16.0, width=2.0, height=6.0, pitch=36.0):
    """Light-shaft emitter one block inside a window (wall_inner = interior face coordinate)."""
    from .. import deco
    if axis == "x":
        deco.light_shaft(w, u, y, wall_inner, facing, length, width, height, pitch)
    else:
        deco.light_shaft(w, wall_inner, y, u, facing, length, width, height, pitch)
