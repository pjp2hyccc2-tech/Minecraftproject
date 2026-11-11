"""Castle exterior: terrace, viaduct, gatehouse, curtain walls and towers, keep, great hall, star court,
chapel, library + observatory, barracks, training yard, gardens and cloisters."""
from __future__ import annotations

import math

import numpy as np

from .. import blocks as B
from .. import deco
from .. import shapes as S
from ..noise import hash2, hash3
from . import parts as Pt
from . import plan as P

AIR = "minecraft:air"
CB, SM, GOLD, ROOF, PALE = Pt.CB, Pt.SM, Pt.GOLD, Pt.ROOF, Pt.PALE
CARVED = "carved_castle_stone"


def full(m):
    return B.full(m) if m in B.MATERIALS or m in B.ALIASES else B.S(m)


# ============================================================================== terrace & courts
def terrace(w, T):
    t = P.TERRACE
    x0, x1, z0, z1 = t["x0"], t["x1"], t["z0"], t["z1"]
    py, ty = P.PY, P.TY
    # fill + retaining wall
    w.fill(x0, py, z1, x1, ty - 1, z0, B.full("stone"))
    for (a, b, c, d) in ((x0, z0, x1, z0), (x0, z1, x1, z1), (x0, z1, x0, z0), (x1, z1, x1, z0)):
        w.fill(a, py, b, c, ty - 1, d, B.full("stone_bricks"))
    # mossy/cracked texture on the retaining wall
    for x in range(x0, x1 + 1):
        for z in (z0,):
            for y in range(py, ty):
                u = hash3(x, y, z, 31)
                if u < 0.12:
                    w.set(x, y, z, B.S("mossy_stone_bricks"))
                elif u < 0.2:
                    w.set(x, y, z, B.S("cracked_stone_bricks"))
    w.fill(x0, ty, z1, x1, ty, z0, B.full("smooth_stone"))
    # paving pattern: smooth stone with polished diorite squares
    X, Z = np.meshgrid(np.arange(x0 + 1, x1), np.arange(z1 + 1, z0), indexing="ij")
    chk = ((X // 3 + Z // 3) % 2 == 0)
    w.set_many(X[chk], np.full(int(chk.sum()), ty), Z[chk], B.S("polished_diorite"))
    # coping and balustrade around the edge (gaps for stairs added later)
    S.rect_ring(w, x0, z0, x1, z1, ty, B.full(SM))
    for (a, b, c, d) in ((x0, z0, x1, z0), (x0, z1, x0, z0), (x1, z1, x1, z0)):
        S.balustrade(w, a, b, c, d, ty + 1, post=B.full(SM), rail=B.wall(CB), cap=None, post_every=4)
    # front grand stair (6 steps) and side stairs
    s = P.TERRACE_STAIR
    for i in range(6):
        z = z0 + 6 - i     # -102 .. -107
        y = py + 1 + i
        w.fill(s["x0"], py, z, s["x1"], y - 1, z, B.full("stone_bricks"))
        w.fill(s["x0"], y, z, s["x1"], y, z, B.stairs("smooth_castle_stone", "north"))
        w.fill(s["x0"], y + 1, z, s["x1"], y + 4, z, AIR)
        for xx in (s["x0"] - 1, s["x1"] + 1):
            w.fill(xx, py, z, xx, y, z, B.full(CB))
            w.set(xx, y + 1, z, B.wall(CB))
    w.fill(s["x0"], ty, z0, s["x1"], ty + 1, z0, AIR)
    w.fill(s["x0"], ty, z0, s["x1"], ty, z0, B.full("smooth_stone"))
    for xx in (s["x0"] - 1, s["x1"] + 1):
        w.fill(xx, py + 1, z0 + 6, xx, py + 2, z0 + 6, B.full(SM))
        deco.lamp_post(w, xx, py + 2, z0 + 6, height=3)
        S.statue_figure(w, xx + (-2 if xx < 0 else 2), py, z0 + 5, facing="south", wings=True)
    # west stair down to the training yard, east stair down to the gardens
    for (xa, dirn) in ((x0, -1), (x1, 1)):
        for i in range(6):
            x = xa + dirn * (i + 1)
            y = ty - i
            for z in range(-122, -117):
                w.fill(x, py, z, x, y - 1, z, B.full("stone_bricks"))
                w.set(x, y, z, B.stairs("smooth_castle_stone", "east" if dirn < 0 else "west"))
                w.fill(x, y + 1, z, x, y + 4, z, AIR)
        w.fill(xa, ty + 1, -122, xa, ty + 1, -118, AIR)
        w.fill(xa, ty, -122, xa, ty, -118, B.full("smooth_stone"))
    # forecourt paving between the gate and the stair (plateau level)
    for x in range(-30, 31):
        for z in range(-103, -99):
            u = hash2(x, z, 5)
            w.set(x, py, z, B.S("stone_bricks") if u < 0.55 else B.S("polished_andesite") if u < 0.8 else
                  B.S("andesite"))
    # gravel paths around the outer bailey
    for z in range(-100, -96):
        for x in range(-64, 65):
            if T.H[x - Pt.X0, z - Pt.Z0] == py and abs(x) > 8:
                w.set(x, py, z, B.S("gravel") if hash2(x, z, 6) < 0.7 else B.S("coarse_dirt"))


# ============================================================================== viaduct
def _deck_profile():
    """z -> (top block y, is_stair) for the viaduct deck."""
    prof = {}
    flights = [(-47, 1, 6), (-57, 7, 6), (-67, 13, 6), (-77, 19, 6), (-85, 25, 2)]
    z = -47
    y_land = 0
    fl = {f[0]: f for f in flights}
    while z >= P.VIADUCT_TOP_Z - 1:
        if z in fl:
            _, y1, n = fl[z]
            for k in range(n):
                prof[z - k] = (y1 + k, True)
            y_land = y1 + n - 1
            z -= n
            continue
        prof[z] = (y_land, False)
        z -= 1
    return prof


def viaduct(w, T):
    prof = _deck_profile()
    arches = [(-57, -62, 4), (-67, -72, 10), (-77, -82, 16)]
    xo = P.VIADUCT_X + 2   # outer face x = +-7
    for z, (y, stair) in sorted(prof.items(), reverse=True):
        g = min(Pt.ground(T, 0, z), 0)
        # masonry body
        w.fill(-xo, g - 2, z, xo, y - 1, z, B.full(CB))
        w.fill(-xo, g - 2, z, xo, min(y - 1, g + 1), z, B.full("stone_bricks"))
        # deck surface
        if stair:
            w.fill(-5, y, z, 5, y, z, B.stairs("smooth_castle_stone", "north"))
        else:
            w.fill(-5, y, z, 5, y, z, B.full("smooth_castle_stone"))
            w.fill(-1, y, z, 1, y, z, B.full("polished_diorite"))
        w.fill(-5, y + 1, z, 5, y + 6, z, AIR)
        # parapet columns and outer cornice
        for s in (-1, 1):
            w.set(6 * s, y, z, B.full(CB))
            w.set(6 * s, y + 1, z, B.wall(CB) if stair or z % 4 else B.full(SM))
            w.set(7 * s, y, z, B.full(SM))
            w.set(7 * s, y - 1, z, B.stairs(SM, "east" if s > 0 else "west", "top"))
            w.fill(6 * s, y + 2, z, 7 * s, y + 6, z, AIR)
    # side faces: plinth, carved band under the cornice, pilaster strips at the piers
    for z, (y, stair) in prof.items():
        for s in (-1, 1):
            x = xo * s
            if y - 2 >= 1:
                w.set(x, y - 2, z, B.full(CARVED))
            if z % 5 == 0:
                w.fill(x, 0, z, x, y - 3, z, B.full(SM))
            w.set(x, 0, z, B.full("stone_bricks"))
    # arches through the viaduct (people walk under it between the two upper towns)
    for (za, zb, crown) in arches:
        width = za - zb + 1
        m = S.pointed_arch_mask(width, crown, sharp=1.05)
        us, vs = np.nonzero(m)
        for x in range(-xo, xo + 1):
            w.set_many(np.full(us.size, x), 1 + vs, zb + us, AIR)
        # archivolt on both faces
        pad = np.pad(m, 1)
        g2 = pad.copy()
        g2[1:, :] |= pad[:-1, :]
        g2[:-1, :] |= pad[1:, :]
        g2[:, 1:] |= pad[:, :-1]
        g2[:, :-1] |= pad[:, 1:]
        ring = g2 & ~pad
        ring[:, 0] = False
        ru, rv = np.nonzero(ring)
        for x in (-xo, xo):
            w.set_many(np.full(ru.size, x), rv, zb - 1 + ru, B.full(SM))
        # keystone
        for x in (-xo, xo):
            w.set(x, crown + 1, zb + width // 2, B.full(GOLD))
        # path under the arch
        for x in range(-xo - 2, xo + 3):
            for z in range(zb, za + 1):
                w.set(x, 0, z, B.S("stone_bricks") if hash2(x, z, 9) < 0.6 else B.S("cobblestone"))
    # buttresses on the landings, with pinnacles, banners and lamps
    landings = [(-53, -56), (-63, -66), (-73, -76)]
    for (za, zb) in landings:
        y = prof[za][0]
        zc = (za + zb) // 2
        for s in (-1, 1):
            x = 8 * s
            w.fill(x, -1, zb, x, y + 1, za, B.full(CB))
            w.fill(x + s, -1, zb + 1, x + s, y - 2, za - 1, B.full(CB))
            w.set(x + s, y - 1, zc, B.stairs(CB, "west" if s > 0 else "east"))
            S.pinnacle(w, x, y + 2, zc, 5)
            deco.kingdom_wall_banner(w, x + s, y - 3, zc, facing="east" if s > 0 else "west")
            w.set(6 * s, y + 1, zc, B.full(SM))
            deco.lamp_post(w, 6 * s, y + 1, zc, height=3)
    # the side door on the first landing to the Guild Gate Terrace (top block y 6, west of x -10): a 2-wide
    # passage through the west parapet and buttress at z -54..-53, railed on both sides
    ly = prof[-53][0]
    w.fill(-9, ly, -54, -6, ly, -53, B.full(SM))
    w.fill(-9, ly + 1, -54, -6, ly + 4, -53, AIR)
    for z in (-55, -52):
        for x in (-9, -8, -7):
            if w.get(x, ly + 1, z) == AIR:
                w.set(x, ly, z, B.full(CB))
                w.set(x, ly + 1, z, B.wall(CB))
    # statues flanking the foot of the stair and at the top
    for s in (-1, 1):
        w.fill(8 * s, 0, -48, 8 * s, 1, -50, B.full(CB))
        S.statue_figure(w, 8 * s, 1, -49, facing="south", wings=False, material="smooth_castle_stone")
        w.set(6 * s, 1, -47, B.full(SM))
        deco.lamp_post(w, 6 * s, 1, -47, height=3)


# ============================================================================== gatehouse
def gatehouse(w, T):
    g = P.GATE
    zf, zb, h = g["z_front"], g["z_back"], g["half"]
    py = P.PY
    top = g["top"]
    # footing on the cliff and the block
    xs, zs = np.meshgrid(np.arange(-h, h + 1), np.arange(zb, zf + 1), indexing="ij")
    Pt.foundation(w, T, xs, zs, py - 1)
    w.fill(-h, py, zb, h, top, zf, B.full(CB))
    w.fill(-h, py, zf, h, py + 1, zb, B.full("stone_bricks"))
    # towers
    for s in (-1, 1):
        Pt.round_tower(w, T, s * g["tower_x"], g["tower_z"], g["tower_r"], py, top + 10, cone_h=28,
                       floors=(py + 9, py + 17), slit_every=6)
        for by in (top - 2, top - 4, top - 6, top - 8):
            deco.kingdom_wall_banner(w, s * g["tower_x"], by, g["tower_z"] + int(g["tower_r"]) + 1, facing="south")
    # passage
    pw = 2 * g["passage"] + 1
    m = S.pointed_arch_mask(pw, 11, sharp=1.1)
    us, vs = np.nonzero(m)
    for z in range(zb, zf + 1):
        w.set_many(-g["passage"] + us, py + 1 + vs, np.full(us.size, z), AIR)
    w.fill(-g["passage"], py, zb, g["passage"], py, zf, B.full("stone_bricks"))
    w.fill(-1, py, zb, 1, py, zf, B.full("polished_andesite"))
    # raised portcullis (iron bars in the arch head) and the inner doors' frame
    sel = vs >= 7
    w.set_many(-g["passage"] + us[sel], py + 1 + vs[sel], np.full(int(sel.sum()), zf - 2), B.S("iron_bars"))
    # archivolts front and back
    pad = np.pad(m, 2)
    def grow(a):
        b = a.copy()
        b[1:, :] |= a[:-1, :]
        b[:-1, :] |= a[1:, :]
        b[:, 1:] |= a[:, :-1]
        b[:, :-1] |= a[:, 1:]
        return b
    r1 = grow(pad) & ~pad
    r2 = grow(grow(pad)) & ~grow(pad)
    r1[:, :2] = False
    r2[:, :2] = False
    for (ring, mat) in ((r1, B.full(SM)), (r2, B.full(CARVED))):
        ru, rv = np.nonzero(ring)
        for z in (zf, zb):
            w.set_many(-g["passage"] - 2 + ru, py - 1 + rv, np.full(ru.size, z), mat)
    w.set(0, py + 13, zf, B.full(GOLD))
    # crest panel + frame + banners + frieze on the front
    w.fill(-2, py + 14, zf, 2, py + 18, zf, B.full(GOLD))
    w.fill(-1, py + 15, zf, 1, py + 17, zf, B.S("castle_crest_stone"))
    for s in (-1, 1):
        deco.kingdom_wall_banner(w, 4 * s, py + 17, zf + 1, facing="south")
        deco.kingdom_wall_banner(w, 4 * s, py + 17, zb - 1, facing="north")
    w.fill(-h, py + 19, zf, h, py + 19, zf, B.full(CARVED))
    w.fill(-h, top - 1, zf + 1, h, top - 1, zf + 1, B.stairs(SM, "south", "top"))
    w.fill(-h, top - 1, zb - 1, h, top - 1, zb - 1, B.stairs(SM, "north", "top"))
    w.fill(-h, top, zf, h, top, zb, B.full(SM))
    w.fill(-h, top, zf, h, top, zf, B.full(GOLD))
    S.crenellate(w, -h, zb, h, zf, top + 1, B.full(CB))
    # central belfry with a square spire
    w.fill(-3, top + 1, -98, 3, top + 7, -92, B.full(CB))
    w.fill(-2, top + 2, -97, 2, top + 6, -93, AIR)
    for (a, b) in ((0, -92), (0, -98)):
        w.fill(a - 1, top + 3, b, a + 1, top + 6, b, AIR)
        w.set(a, top + 6, b, B.full(CB))
    for (a, b) in ((-3, -95), (3, -95)):
        w.fill(a, top + 3, b - 1, a, top + 6, b + 1, AIR)
        w.set(a, top + 6, b, B.full(CB))
    w.set(0, top + 6, -95, B.S("bell", attachment="ceiling", facing="north"))
    w.fill(-4, top + 7, -99, 4, top + 7, -91, B.full(GOLD))
    S.square_spire(w, 0, -95, 4, top + 8, 20, ROOF)
    # guard niches with ceremonial armour stands inside the passage
    for s in (-1, 1):
        x = (g["passage"] + 1) * s
        for z in (-95, -98):
            w.fill(x, py + 1, z, x, py + 3, z, AIR)
            w.set(x, py + 4, z, B.stairs(SM, "east" if s > 0 else "west", "top"))
            deco.armor_stand(w, x, py, z, yaw=90.0 if s > 0 else -90.0, armor="iron",
                             weapon="minecraft:iron_sword", offhand=deco.blue_shield())
    # spots for the gate guards (NPCs) and the gate itself
    w.spot("castle.gate", (0, py + 1, zf + 2), 0.0)
    w.spot("castle.gate_guard_w", (-4, py + 1, zf + 1), 0.0)
    w.spot("castle.gate_guard_e", (4, py + 1, zf + 1), 0.0)


# ============================================================================== curtain wall
def curtain(w, T):
    pts = P.WALL_PTS
    west = pts[:6]
    east = pts[6:]
    for part in (west, east):
        Pt.curtain_wall(w, T, part, P.PY, P.WALL_TOP, center=(0, -140))
    for (x, z, r, top, ch) in P.TOWERS:
        Pt.round_tower(w, T, x, z, r, P.PY, top, cone_h=ch, floors=(P.PY + 8, P.WALL_TOP))
        # door from the wall walk into the tower
        if hash2(x, z, 3) < 2:
            deco.kingdom_wall_banner(w, x + int(math.copysign(int(r) + 1, x)), top - 5, z, facing="east" if x > 0 else "west")


# ============================================================================== keep
def keep(w, T):
    k = P.KEEP
    x0, x1, z0, z1, top = k["x0"], k["x1"], k["z1"], k["z0"], k["top"]   # z0=-188 (north), z1=-160 (south)
    py = P.PY
    xs, zs = np.meshgrid(np.arange(x0 - 3, x1 + 4), np.arange(z0 - 3, z1 + 1), indexing="ij")
    Pt.foundation(w, T, xs, zs, py - 1)
    w.fill(x0, py, z0, x1, top, z1, B.full(CB))
    w.fill(x0 + 3, py + 1, z0 + 3, x1 - 3, top - 1, z1 - 3, AIR)
    w.fill(x0, py, z0, x1, py + 2, z1, B.full("stone_bricks"))
    # floors: throne room floor = terrace level, two upper floors, roof deck
    w.fill(x0 + 3, py, z0 + 3, x1 - 3, P.TY, z1 - 3, B.full("stone"))
    for fy in (60, 72):
        w.fill(x0 + 3, fy, z0 + 3, x1 - 3, fy, z1 - 3, B.full("dark_oak_planks"))
    w.fill(x0 + 3, top, z0 + 3, x1 - 3, top, z1 - 3, B.full("stone_bricks"))
    # string courses, gilded band, machicolations and crenellated parapet
    for y in (P.TY + 1, 60, 72):
        S.rect_ring(w, x0, z0, x1, z1, y, B.full(SM))
    S.rect_ring(w, x0, z0, x1, z1, top - 1, B.full(GOLD))
    S.corbel_rect(w, x0, z0, x1, z1, top - 1, SM)
    S.rect_ring(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top, B.full(SM))
    S.rect_ring(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top + 1, B.full(CB))
    S.crenellate(w, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top + 2, B.full(CB))
    # clasping corner buttresses
    for (cx, cz, sx, sz) in ((x0, z0, -1, -1), (x1, z0, 1, -1), (x0, z1, -1, 1), (x1, z1, 1, 1)):
        for d in (1, 2):
            w.fill(cx + sx * d, py, cz, cx + sx * d, 66 - d * 4, cz + sz * 2, B.full(CB))
            w.fill(cx, py, cz + sz * d, cx + sx * 2, 66 - d * 4, cz + sz * d, B.full(CB))
    # throne-room windows (east & west), upper windows
    for (xw, sgn) in ((x1, 1), (x0, -1)):
        Pt.gothic_wall_bays(w, "z", xw, sgn, [-167, -174, -181], py, 56, 5, 18, 36, glass=B.pane(),
                            mullion=B.wall(CB), frame=B.full(SM), wall_thick=3, pinnacle_h=0, sharp=1.25)
        for zc in (-165, -171, -177, -183):
            for yy in (63, 75):
                S.gothic_window(w, "z", zc - 1, yy, xw - sgn * 2, 2, 6, glass=B.pane(), depth=3, step=sgn)
    for (zw, sgn) in ((z0, -1), (z1, 1)):
        for xc in (-10, -4, 4, 10):
            for yy in (63, 75):
                S.gothic_window(w, "x", xc - 1 if xc > 0 else xc, yy, zw - sgn * 2, 2, 6, glass=B.pane(), depth=3,
                                step=sgn)
    # north window behind the throne (stained glass), south door + rose
    S.gothic_window(w, "x", -3, 38, z0 + 2, 7, 19, glass=B.pane("blue"), mullion=B.pillar(), depth=3, step=-1,
                    sharp=1.1, head_trim=B.full(GOLD))
    stain_rect(w, "x", z0 + 1, -3, 3, 38, 56, ("blue", "light_blue", "yellow", "white"), pattern="lancet")
    door = S.pointed_arch_mask(5, 8, 1.1)
    us, vs = np.nonzero(door)
    for z in range(z1 - 2, z1 + 1):
        w.set_many(-2 + us, P.TY + 1 + vs, np.full(us.size, z), AIR)
    S.rose_window(w, "x", 0, 49, z1, 5, B.full(SM), colors=("blue", "light_blue", "yellow", "red", "purple"),
                  depth=3, step=-1)
    for s in (-1, 1):
        deco.kingdom_wall_banner(w, 9 * s, 68, z1 + 1, facing="south")
        deco.kingdom_wall_banner(w, 9 * s, 68, z0 - 1, facing="north")
        deco.kingdom_wall_banner(w, x1 + 1 if s > 0 else x0 - 1, 68, -174, facing="east" if s > 0 else "west")
    # corner turrets (full height, slender)
    for (cx, cz) in ((x0 - 1, z0 - 1), (x1 + 1, z0 - 1), (x0 - 1, z1 + 1), (x1 + 1, z1 + 1)):
        Pt.round_tower(w, T, cx, cz, 3.6, py, top + 12, cone_h=19, floors=(), windows=True, slit_every=8,
                       courses=(8, 34, 46, 58), parapet=True, hollow=False)
    # central tower: octagonal lantern stage, belfry, great spire
    cx, cz = 0, -174
    s1 = top + 12
    S.octagon_prism(w, cx, cz, 8, top - 4, s1, B.full(CB))
    S.octagon_prism(w, cx, cz, 6, top - 3, s1 - 1, AIR)
    S.octagon_prism(w, cx, cz, 8, top + 2, top + 2, B.full(SM), hollow=True)
    for (dx, dz) in ((8, 0), (-8, 0), (0, 8), (0, -8)):
        if dx:
            S.gothic_window(w, "z", cz - 1, top + 3, cx + dx, 3, 8, glass=B.pane(), depth=1)
        else:
            S.gothic_window(w, "x", cx - 1, top + 3, cz + dz, 3, 8, glass=B.pane(), depth=1)
    S.octagon_prism(w, cx, cz, 8, s1, s1, B.full(GOLD))
    S.octagon_prism(w, cx, cz, 9, s1 + 1, s1 + 1, B.full(SM), hollow=True)
    ex, ez = S.octagon_ring(9)
    ang = np.arctan2(ez, ex)
    for i in np.argsort(ang)[::2]:
        w.set(cx + int(ex[i]), s1 + 2, cz + int(ez[i]), B.full(CB))
    for (px, pz) in ((9, 0), (-9, 0), (0, 9), (0, -9), (6, 6), (-6, 6), (6, -6), (-6, -6)):
        S.pinnacle(w, cx + px, s1 + 2, cz + pz, 7)
    # belfry stage
    b0 = s1 + 1
    S.octagon_prism(w, cx, cz, 5, b0, b0 + 7, B.full(CB))
    S.octagon_prism(w, cx, cz, 4, b0, b0 + 6, AIR)
    for (dx, dz) in ((5, 0), (-5, 0), (0, 5), (0, -5)):
        if dx:
            w.fill(cx + dx, b0 + 2, cz - 1, cx + dx, b0 + 5, cz + 1, AIR)
        else:
            w.fill(cx - 1, b0 + 2, cz + dz, cx + 1, b0 + 5, cz + dz, AIR)
    w.set(cx, b0 + 6, cz, B.S("bell", attachment="ceiling", facing="north"))
    S.octagon_prism(w, cx, cz, 6, b0 + 8, b0 + 8, B.full(GOLD))
    tip = S.cone(w, cx, cz, 5.4, b0 + 9, 22, ROOF, profile=1.2)
    # gold bands on the spire
    for y in (b0 + 13, b0 + 18, b0 + 23):
        for xx in range(cx - 7, cx + 8):
            for zz in range(cz - 7, cz + 8):
                if "royal_roof_tile" in w.get(xx, y, zz):
                    w.set(xx, y, zz, B.full(GOLD) if "stairs" not in w.get(xx, y, zz) else
                          B.stairs(GOLD, B.parse(w.get(xx, y, zz))[1]["facing"]))
    return tip


def stain_rect(w, axis, wall_coord, u0, u1, y0, y1, colors, pattern="lancet"):
    """Recolour every glass pane in a wall rectangle with a stained-glass pattern."""
    for u in range(u0, u1 + 1):
        for y in range(y0, y1 + 1):
            x, z = (u, wall_coord) if axis == "x" else (wall_coord, u)
            for dw in (-1, 0, 1):
                xx, zz = (x, z + dw) if axis == "x" else (x + dw, z)
                st = w.get(xx, y, zz)
                if "pane" not in st:
                    continue
                if pattern == "lancet":
                    k = ((y // 2) + abs(u - (u0 + u1) // 2)) % len(colors)
                else:
                    k = ((u + y) // 2) % len(colors)
                w.set(xx, y, zz, B.pane(colors[k]))


# ============================================================================== great hall
def great_hall(w, T):
    h = P.HALL
    x0, x1, zs, zn = h["x0"], h["x1"], h["z0"], h["z1"]     # zs=-114 (south), zn=-150 (north)
    y0 = P.TY + 1
    eave = h["eave"]
    w.fill(x0, y0, zn, x1, eave - 1, zs, B.full(CB))
    w.fill(x0 + 2, y0, zn + 2, x1 - 2, eave - 1, zs - 2, AIR)
    w.fill(x0, y0, zn, x1, y0 + 1, zs, B.full("stone_bricks"))
    w.fill(x0 + 2, y0, zn + 2, x1 - 2, y0 + 1, zs - 2, AIR)
    S.rect_ring(w, x0, zn, x1, zs, eave - 1, B.full(GOLD))
    bays = [-120, -126, -132, -138, -144]
    buttr = [-117, -123, -129, -135, -141, -147]
    for (xw, sgn) in ((x1, 1), (x0, -1)):
        Pt.gothic_wall_bays(w, "z", xw, sgn, bays, y0, eave - 2, 5, h["win_h"], h["win_y"], glass=B.pane(),
                            mullion=B.wall(CB),
                            frame=B.full(SM), buttress_u=buttr, b_depth=3, b_width=1, wall_thick=2,
                            pinnacle_h=7, sill=B.slab(SM, "top"), sharp=1.25)
    # south facade: portal + rose window + corner turrets
    portal = S.pointed_arch_mask(7, 11, 1.1)
    us, vs = np.nonzero(portal)
    for z in (zs, zs - 1):
        w.set_many(-3 + us, y0 + vs, np.full(us.size, z), AIR)
    _archivolt(w, "x", -3, y0, zs, 7, 11, (B.full(SM), B.full(CARVED), B.full(SM)))
    w.set(0, y0 + 14, zs, B.full(GOLD))
    S.rose_window(w, "x", 0, 52, zs, 7, B.full(SM), colors=("blue", "light_blue", "yellow", "red", "purple"),
                  depth=2, step=-1)
    w.fill(-12, 44, zs, 12, 44, zs, B.full(CARVED))
    for s in (-1, 1):
        for by in (y0 + 14, y0 + 12, y0 + 10):
            deco.kingdom_wall_banner(w, 6 * s, by, zs + 1, facing="south")
        S.statue_figure(w, 9 * s, P.TY, zs + 2, facing="south", wings=False, material="smooth_castle_stone")
        facade_tower(w, T, 15 * s, zs + 2, y0, eave)
        Pt.round_tower(w, T, (x1 + 1) * s if s > 0 else x0 - 1, zn - 1, 2.6, y0, eave + 18, cone_h=20,
                       hollow=False, windows=False, courses=(10, 22, 29), parapet=False)
    # roof
    ridge = S.gable_roof(w, x0, zn, x1, zs, eave, ROOF, axis="z", overhang=1, pitch=1,
                         gable_state=B.full(CB), ridge_state=B.slab(GOLD), eave_trim=GOLD)
    # dormers
    for zc in (-123, -141):
        for s in (-1, 1):
            _dormer(w, s, zc, eave + 4)
    # north gable crest + pinnacles on the gable ends
    for z in (zs, zn):
        S.pinnacle(w, 0, ridge + 1, z, 5)
    # lantern (glazed cupola) at the centre of the ridge
    cx, cz = 0, -132
    S.octagon_prism(w, cx, cz, 4, ridge - 3, ridge + 7, B.full(SM), hollow=True)
    S.octagon_prism(w, cx, cz, 3, eave + 6, ridge + 7, AIR)
    for (dx, dz) in ((4, 0), (-4, 0), (0, 4), (0, -4), (3, 2), (-3, 2), (3, -2), (-3, -2), (2, 3), (-2, 3),
                     (2, -3), (-2, -3)):
        w.fill(cx + dx, ridge, cz + dz, cx + dx, ridge + 5, cz + dz, B.pane())
    S.octagon_prism(w, cx, cz, 4, ridge + 6, ridge + 6, B.full(GOLD))
    S.octagon_prism(w, cx, cz, 3, ridge + 7, ridge + 7, B.glass())
    S.octagon_prism(w, cx, cz, 4, ridge + 7, ridge + 7, B.full(SM), hollow=True)
    for (dx, dz) in ((4, 0), (-4, 0), (0, 4), (0, -4)):
        S.pinnacle(w, cx + dx, ridge + 8, cz + dz, 4)
    w.spot("castle.great_hall", (0, P.TY + 1, -118), 180.0)
    return ridge


def facade_tower(w, T, cx, cz, y0, eave):
    """Square-to-octagonal facade tower flanking the great hall's south front (cf. the concept's central
    gothic front): buttressed square shaft, octagonal belfry with lancets, tall spire with gold bands."""
    top_sq = eave + 12
    w.fill(cx - 4, y0, cz - 4, cx + 4, top_sq, cz + 4, B.full(CB))
    w.fill(cx - 3, y0 + 1, cz - 3, cx + 3, top_sq - 1, cz + 3, AIR)
    for y in (y0 + 11, eave - 1, top_sq):
        S.rect_ring(w, cx - 4, cz - 4, cx + 4, cz + 4, y, B.full(SM))
    S.rect_ring(w, cx - 4, cz - 4, cx + 4, cz + 4, top_sq - 1, B.full(GOLD))
    for (sx, sz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for d in (1, 2):
            w.fill(cx + sx * (4 + d), y0, cz + sz * 4, cx + sx * (4 + d), top_sq - 6 - d * 5, cz + sz * 4,
                   B.full(CB))
            w.fill(cx + sx * 4, y0, cz + sz * (4 + d), cx + sx * 4, top_sq - 6 - d * 5, cz + sz * (4 + d),
                   B.full(CB))
        S.pinnacle(w, cx + sx * 4, top_sq + 1, cz + sz * 4, 7)
    for (axis, wc, sgn) in (("x", cz + 4, 1), ("x", cz - 4, -1), ("z", cx + 4, 1), ("z", cx - 4, -1)):
        for (yy, hh) in ((y0 + 14, 9), (eave + 1, 9)):
            if axis == "x":
                S.gothic_window(w, "x", cx - 1, yy, wc, 3, hh, glass=B.pane(), depth=1)
            else:
                S.gothic_window(w, "z", cz - 1, yy, wc, 3, hh, glass=B.pane(), depth=1)
    S.crenellate(w, cx - 4, cz - 4, cx + 4, cz + 4, top_sq + 1, B.wall(CB), period=2)
    # octagonal belfry and spire
    b0 = top_sq + 1
    S.octagon_prism(w, cx, cz, 3, b0, b0 + 8, B.full(CB))
    S.octagon_prism(w, cx, cz, 2, b0, b0 + 7, AIR)
    for (dx, dz) in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        w.fill(cx + dx, b0 + 2, cz + dz, cx + dx, b0 + 6, cz + dz, AIR)
    S.octagon_prism(w, cx, cz, 4, b0 + 9, b0 + 9, B.full(GOLD))
    tip = S.cone(w, cx, cz, 3.6, b0 + 10, 24, ROOF, profile=1.25)
    for y in (b0 + 14, b0 + 20):
        for xx in range(cx - 4, cx + 5):
            for zz in range(cz - 4, cz + 5):
                st = w.get(xx, y, zz)
                if "royal_roof_tile" in st:
                    w.set(xx, y, zz, B.full(GOLD) if "stairs" not in st else B.stairs(GOLD, B.parse(st)[1]["facing"]))
    return tip


def _archivolt(w, axis, u0, y0, wc, width, height, mats):
    m = S.pointed_arch_mask(width, height, 1.1)
    cur = np.pad(m, len(mats))
    for k, mat in enumerate(mats):
        g2 = cur.copy()
        g2[1:, :] |= cur[:-1, :]
        g2[:-1, :] |= cur[1:, :]
        g2[:, 1:] |= cur[:, :-1]
        g2[:, :-1] |= cur[:, 1:]
        ring = g2 & ~cur
        ring[:, :len(mats)] = False
        ru, rv = np.nonzero(ring)
        if axis == "x":
            w.set_many(u0 - len(mats) + ru, y0 - len(mats) + rv, np.full(ru.size, wc), mat)
        else:
            w.set_many(np.full(ru.size, wc), y0 - len(mats) + rv, u0 - len(mats) + ru, mat)
        cur = g2


def _dormer(w, side, zc, y):
    """Small gabled dormer on the east (side=1) / west (side=-1) slope of the hall roof."""
    x = 9 * side
    w.fill(x, y, zc - 1, x + 3 * side, y + 3, zc + 1, B.full(CB))
    w.fill(x, y + 1, zc, x + 3 * side, y + 2, zc, AIR)
    w.set(x + 3 * side, y + 1, zc, B.pane())
    w.set(x + 3 * side, y + 2, zc, B.pane())
    for dz, f in ((-2, "south"), (2, "north")):
        w.fill(x, y + 3, zc + dz, x + 4 * side, y + 3, zc + dz, B.stairs(ROOF, f))
    w.fill(x, y + 4, zc - 1, x + 4 * side, y + 4, zc - 1, B.stairs(ROOF, "south"))
    w.fill(x, y + 4, zc + 1, x + 4 * side, y + 4, zc + 1, B.stairs(ROOF, "north"))
    w.fill(x, y + 5, zc, x + 4 * side, y + 5, zc, B.slab(GOLD))
    w.set(x + 4 * side, y + 6, zc, B.S("gilded_finial"))


# ============================================================================== star court
def star_court(w, T):
    c = P.COURT
    ty = P.TY
    x0, x1, z0, z1 = c["x0"], c["x1"], c["z1"], c["z0"]
    w.fill(x0, ty, z0, x1, ty, z1, B.full("polished_diorite"))
    w.fill(x0, ty + 1, z0, x1, ty + 12, z1, AIR)
    sg = P.STARGATE
    cx, cz = sg["cx"], sg["cz"]
    X, Z = np.meshgrid(np.arange(x0, x1 + 1), np.arange(z0, z1 + 1), indexing="ij")
    d = np.sqrt((X - cx) ** 2 + (Z - cz) ** 2)
    ang = np.degrees(np.arctan2(Z - cz, X - cx)) % 45.0
    ray = (np.minimum(ang, 45 - ang) < 6) & (d > 3.5) & (d < 12)
    w.set_many(X[ray], np.full(int(ray.sum()), ty), Z[ray], B.S("lapis_block"))
    ringm = (d > 2.6) & (d <= 3.6)
    w.set_many(X[ringm], np.full(int(ringm.sum()), ty), Z[ringm], B.full(GOLD))
    hole = d <= 2.6
    w.set_many(X[hole], np.full(int(hole.sum()), ty), Z[hole], B.glass())
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        deco.lamp_post(w, x, ty, z, height=3)
    for s in (-1, 1):
        deco.bench(w, 9 * s - 1, ty, -152, facing="north", length=3)
        w.set(11 * s, ty + 1, -157, B.potted("azalea_bush"))
    w.spot("castle.star_court", (0, ty + 1, -152), 180.0)


# ============================================================================== chapel
def chapel(w, T):
    c = P.CHAPEL
    x0, x1, zs, zn = c["x0"], c["x1"], c["z0"], c["z1"]    # zs=-120 south, zn=-136 north
    y0 = P.TY + 1
    eave = c["eave"]
    w.fill(x0, y0, zn, x1, eave - 1, zs, B.full(CB))
    w.fill(x0 + 2, y0, zn + 2, x1 - 2, eave - 1, zs - 2, AIR)
    w.fill(x0, y0, zn, x1, y0 + 1, zs, B.full("stone_bricks"))
    w.fill(x0 + 2, y0, zn + 2, x1 - 2, y0 + 1, zs - 2, AIR)
    S.rect_ring(w, x0, zn, x1, zs, eave - 1, B.full(GOLD))
    lan = [35, 41, 47, 53]
    but = [32, 38, 44, 50, 56]
    glass_cols = ("blue", "red", "yellow", "light_blue", "purple")
    for (zw, sgn) in ((zs, 1), (zn, -1)):
        Pt.gothic_wall_bays(w, "x", zw, sgn, lan, y0, eave - 2, 5, 10, 35, glass=B.pane("blue"),
                            mullion=B.wall(CB), frame=B.full(SM), buttress_u=but, b_depth=3, b_width=1,
                            pinnacle_h=6, sharp=1.25)
        for u in lan:
            stain_rect(w, "x", zw - sgn, u - 2, u + 2, 35, 45, glass_cols, pattern="lancet")
    # east window (morning light) and west rose window (afternoon light)
    S.gothic_window(w, "z", -131, 34, x1 - 1, 7, 12, glass=B.pane("blue"), mullion=B.wall(CB), depth=2, step=1,
                    head_trim=B.full(GOLD))
    stain_rect(w, "z", x1 - 1, -131, -125, 34, 46, ("red", "yellow", "blue", "light_blue", "white", "purple"),
               pattern="lancet")
    S.rose_window(w, "z", -128, 41, x0, 4, B.full(SM), colors=("purple", "blue", "yellow", "red", "light_blue"),
                  depth=2, step=1)
    door = S.pointed_arch_mask(5, 7, 1.1)
    us, vs = np.nonzero(door)
    for x in (x0, x0 + 1):
        w.set_many(np.full(us.size, x), y0 + vs, -130 + us, AIR)
    _archivolt(w, "z", -130, y0, x0, 5, 7, (B.full(SM), B.full(CARVED)))
    ridge = S.gable_roof(w, x0, zn, x1, zs, eave, ROOF, axis="x", overhang=1, pitch=1, gable_state=B.full(CB),
                         ridge_state=B.slab(GOLD), eave_trim=GOLD)
    S.square_spire(w, 45, -128, 2, ridge - 2, 30, ROOF)
    w.fill(43, ridge - 6, -130, 47, ridge - 2, -126, B.full(CB))
    w.fill(44, ridge - 5, -130, 46, ridge - 3, -126, AIR)
    w.fill(43, ridge - 5, -129, 47, ridge - 3, -127, AIR)
    w.fill(42, ridge - 1, -131, 48, ridge - 1, -125, B.full(GOLD))
    S.pinnacle(w, x0, ridge + 1, -128, 5)
    S.pinnacle(w, x1, ridge + 1, -128, 5)
    w.spot("castle.chapel", (34, P.TY + 1, -128), -90.0)


# ============================================================================== library + observatory
def library(w, T):
    c = P.LIBRARY
    x0, x1, zs, zn = c["x0"], c["x1"], c["z0"], c["z1"]
    y0 = P.TY + 1
    eave = c["eave"]
    w.fill(x0, y0, zn, x1, eave - 1, zs, B.full(CB))
    w.fill(x0 + 2, y0, zn + 2, x1 - 2, eave - 1, zs - 2, AIR)
    w.fill(x0, y0, zn, x1, y0 + 1, zs, B.full("stone_bricks"))
    w.fill(x0 + 2, y0, zn + 2, x1 - 2, y0 + 1, zs - 2, AIR)
    S.rect_ring(w, x0, zn, x1, zs, eave - 1, B.full(GOLD))
    bays = [-122, -128, -134, -140]
    buttr = [-119, -125, -131, -137, -143]
    for (xw, sgn) in ((x1, 1), (x0, -1)):
        Pt.gothic_wall_bays(w, "z", xw, sgn, bays, y0, eave - 2, 5, 10, 34, glass=B.pane(), mullion=B.wall(CB),
                            frame=B.full(SM), buttress_u=buttr, b_depth=2, b_width=1, pinnacle_h=6,
                            sill=B.slab(SM, "top"), sharp=1.25)
    door = S.pointed_arch_mask(5, 7, 1.1)
    us, vs = np.nonzero(door)
    for z in (zs, zs - 1):
        w.set_many(-46 + us, y0 + vs, np.full(us.size, z), AIR)
    _archivolt(w, "x", -46, y0, zs, 5, 7, (B.full(SM), B.full(CARVED)))
    S.rose_window(w, "x", -44, 44, zs, 3, B.full(SM), colors=("light_blue", "blue", "yellow"), depth=2, step=-1)
    ridge = S.gable_roof(w, x0, zn, x1, zs, eave, ROOF, axis="z", overhang=1, pitch=1, gable_state=B.full(CB),
                         ridge_state=B.slab(GOLD), eave_trim=GOLD)
    for zc in (-125, -137):
        for s in (-1, 1):
            xd = -44 + 9 * s
            w.fill(xd, eave + 3, zc - 1, xd + 2 * s, eave + 6, zc + 1, B.full(CB))
            w.set(xd + 2 * s, eave + 4, zc, B.pane())
            w.set(xd + 2 * s, eave + 5, zc, B.pane())
            w.fill(xd, eave + 7, zc - 1, xd + 3 * s, eave + 7, zc + 1, B.slab(ROOF))
    # observatory tower for the scholar Nox
    o = P.OBSERVATORY
    tip = Pt.round_tower(w, T, o["x"], o["z"], o["r"], P.TY + 1, o["top"], cone_h=9, dome=True, roof=PALE,
                         floors=(P.TY + 9, P.TY + 18, P.TY + 27, P.TY + 36, P.TY + 45), slit_every=6,
                         courses=(9, 18, 27, 36, 45))
    # gilded ribs on the dome and an observation slit facing south
    ox, oz, top = o["x"], o["z"], o["top"]
    for k in range(8):
        a = k * math.pi / 4
        for i in range(0, 10):
            ang = i / 10 * math.pi / 2
            rr = (o["r"] - 0.4) * math.cos(ang)
            yy = top + 1 + int(round(9 * math.sin(ang)))
            w.set(ox + int(round(rr * math.cos(a))), yy, oz + int(round(rr * math.sin(a))), B.full(GOLD))
    for i in range(0, 9):
        ang = i / 9 * math.pi / 2
        rr = (o["r"] - 0.4) * math.cos(ang)
        yy = top + 1 + int(round(9 * math.sin(ang)))
        for dz in (0, -1):
            for dxx in (0, 1):
                zz = oz + int(round(rr)) + dz
                if i < 8:
                    w.set(ox + dxx, yy, zz, AIR)
    w.spot("castle.observatory", (ox, top - 7, oz - 2), 180.0)
    return tip


# ============================================================================== barracks, yard, gardens
def barracks(w, T):
    b = P.BARRACKS
    x0, x1, zs, zn = b["x0"], b["x1"], b["z0"], b["z1"]    # zs=-128 (south), zn=-158
    py = P.PY
    eave = b["eave"]
    xs, zz = np.meshgrid(np.arange(x0, x1 + 1), np.arange(zn, zs + 1), indexing="ij")
    Pt.foundation(w, T, xs, zz, py)
    w.fill(x0, py, zn, x1, py, zs, B.full("stone_bricks"))
    w.fill(x0, py + 1, zn, x1, 33, zs, B.full(CB))
    w.fill(x0 + 1, py + 1, zn + 1, x1 - 1, 32, zs - 1, AIR)
    # half-timbered upper floor
    w.fill(x0, 34, zn, x1, eave - 1, zs, B.S("white_plaster"))
    w.fill(x0 + 1, 34, zn + 1, x1 - 1, eave - 1, zs - 1, AIR)
    w.fill(x0 + 1, 33, zn + 1, x1 - 1, 33, zs - 1, B.full("spruce_planks"))
    for x in range(x0, x1 + 1, 4):
        for z in (zn, zs):
            w.fill(x, 34, z, x, eave - 1, z, B.log("dark_oak"))
    for z in range(zn, zs + 1, 4):
        for x in (x0, x1):
            w.fill(x, 34, z, x, eave - 1, z, B.log("dark_oak"))
    for (a, b2, c, d) in ((x0, zn, x1, zn), (x0, zs, x1, zs), (x0, zn, x0, zs), (x1, zn, x1, zs)):
        w.fill(a, eave - 1, b2, c, eave - 1, d, B.log("dark_oak", "x" if b2 == d else "z"))
        w.fill(a, 34, b2, c, 34, d, B.log("dark_oak", "x" if b2 == d else "z"))
    # windows
    for z in range(zn + 2, zs - 1, 4):
        for x in (x0, x1):
            w.fill(x, py + 3, z, x, py + 4, z, B.pane())
            w.fill(x, 36, z, x, 37, z, B.pane())
    for x in range(x0 + 3, x1, 4):
        for z in (zn, zs):
            w.fill(x, 36, z, x, 37, z, B.pane())
    # door on the south end, facing the yard
    lo, hi = B.door_states("spruce", facing="north")
    w.set(-79, py + 1, zs, lo)
    w.set(-79, py + 2, zs, hi)
    w.fill(-80, py + 3, zs + 1, -78, py + 3, zs + 1, B.slab("spruce", "top"))
    deco.kingdom_wall_banner(w, -76, 32, zs + 1, facing="south")
    deco.kingdom_wall_banner(w, -82, 32, zs + 1, facing="south")
    ridge = S.gable_roof(w, x0, zn, x1, zs, eave, PALE, axis="z", overhang=1, pitch=1,
                         gable_state=B.S("white_plaster"), ridge_state=B.slab(GOLD))
    for zc in (-136, -150):
        w.fill(-84, ridge - 4, zc, -83, ridge + 2, zc + 1, B.full("stone_bricks"))
        w.set(-84, ridge + 3, zc, B.S("campfire", lit=True))
    w.spot("castle.barracks", (-79, py + 1, zs - 2), 0.0)


def inside_walls(x, z, margin=4.0):
    """True if (x, z) lies inside the curtain-wall polygon, at least ``margin`` from every wall segment."""
    pts = P.WALL_PTS
    inside = False
    n = len(pts)
    dmin = 1e9
    for i in range(n):
        (ax, az), (bx, bz) = pts[i], pts[(i + 1) % n]
        if (az > z) != (bz > z):
            xc = ax + (z - az) * (bx - ax) / (bz - az)
            if x < xc:
                inside = not inside
        vx, vz = bx - ax, bz - az
        t = max(0.0, min(1.0, ((x - ax) * vx + (z - az) * vz) / float(vx * vx + vz * vz)))
        dmin = min(dmin, math.hypot(x - ax - t * vx, z - az - t * vz))
    for (tx, tz, r, _, _) in P.TOWERS:
        dmin = min(dmin, math.hypot(x - tx, z - tz) - r + 1.5)
    return inside and dmin >= margin


def yard(w, T):
    y = P.YARD
    py = P.PY
    for x in range(y["x0"], y["x1"] + 1):
        for z in range(y["z1"], y["z0"] + 1):
            if Pt.ground(T, x, z) >= py - 1 and inside_walls(x, z, 3):
                u = hash2(x, z, 77)
                w.set(x, py, z, B.S("sand") if u < 0.55 else B.S("gravel") if u < 0.8 else B.S("coarse_dirt"))
                w.fill(x, py + 1, z, x, py + 4, z, AIR)
    # training dummies (fence + hay + carved pumpkin) and archery targets
    for i, (x, z) in enumerate(((-82, -118), (-78, -118), (-74, -118), (-80, -113), (-76, -113))):
        w.set(x, py + 1, z, B.fence("spruce"))
        w.set(x, py + 2, z, B.S("hay_block", axis="y"))
        w.set(x, py + 3, z, B.S("carved_pumpkin", facing="south"))
        w.set(x + 1, py + 2, z, B.fence("spruce"))
        w.set(x - 1, py + 2, z, B.fence("spruce"))
    for z in (-112, -116, -120):
        w.set(-62, py + 1, z, B.S("hay_block", axis="y"))
        w.set(-62, py + 2, z, B.S("target"))
    # weapon racks (armour stands) along the south wall of the barracks
    for i, x in enumerate((-87, -84, -73, -70)):
        deco.armor_stand(w, x, py, -126, yaw=0.0, armor="chainmail", weapon="minecraft:iron_sword")
    for x in (-88, -88):
        w.set(x, py + 1, -124, B.S("barrel", facing="up"))
    w.set(-71, py + 1, -124, B.S("grindstone", face="floor", facing="south"))
    w.spot("story.arcelia_knight_captain", (-79, py + 1, -121), 0.0)


def gardens(w, T):
    g = P.GARDEN
    py = P.PY
    x0, x1, z0, z1 = g["x0"], g["x1"], g["z1"], g["z0"]
    ok = np.zeros((x1 - x0 + 1, z1 - z0 + 1), bool)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if Pt.ground(T, x, z) != py or not inside_walls(x, z, 3):
                continue
            ok[x - x0, z - z0] = True
            w.fill(x, py + 1, z, x, py + 5, z, AIR)
            w.set(x, py, z, B.S("grass_block"))

    def okp(x, z):
        return x0 <= x <= x1 and z0 <= z <= z1 and ok[x - x0, z - z0]

    def put(x, y, z, st):
        if okp(x, z):
            w.set(x, y, z, st)
    cx, cz = 77, -138
    # paths: a cross and a ring
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not okp(x, z):
                continue
            d = math.hypot(x - cx, z - cz)
            if abs(x - cx) <= 1 or abs(z - cz) <= 1 or abs(d - 11) < 1.0:
                w.set(x, py, z, B.S("gravel"))
    # parterres: hedge-bordered beds with flowers, quarter by quarter
    fl = ("cornflower", "white_tulip", "azure_bluet", "lily_of_the_valley", "allium", "blue_orchid")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not okp(x, z):
                continue
            d = math.hypot(x - cx, z - cz)
            if abs(x - cx) <= 1 or abs(z - cz) <= 1 or abs(d - 11) < 1.0 or d < 6:
                continue
            if not (okp(x + 1, z) and okp(x - 1, z) and okp(x, z + 1) and okp(x, z - 1)):
                continue
            edge = abs(x - cx) == 2 or abs(z - cz) == 2 or abs(d - 11) < 1.6 or d < 7
            if edge:
                w.set(x, py + 1, z, B.leaves("azalea"))
            elif (x + z) % 6 == 0:
                w.set(x, py + 1, z, B.leaves("flowering_azalea"))
            else:
                w.set(x, py + 1, z, B.S(fl[(x * 7 + z * 3) % len(fl)]))
    for (dx, dz) in ((-6, -6), (6, -6), (-6, 6), (6, 6), (-9, -14), (9, -14), (-9, 14), (9, 14)):
        if okp(cx + dx, cz + dz):
            w.set(cx + dx, py + 1, cz + dz, B.S("air"))
            deco.tree_cypress(w, cx + dx, py, cz + dz, seed=dx * 31 + dz)
    # fountain in the middle
    S.cylinder(w, cx, cz, 4.5, py, py + 1, B.full(SM))
    S.cylinder(w, cx, cz, 3.5, py + 1, py + 1, B.S("water"))
    S.cylinder(w, cx, cz, 3.5, py, py, B.full("smooth_stone"))
    w.fill(cx, py + 1, cz, cx, py + 3, cz, B.full(SM))
    w.set(cx, py + 4, cz, B.S("water"))
    w.fluid_tick(cx, py + 4, cz)
    # pavilion (round, blue dome) towards the north
    px, pz = 76, -157
    S.cylinder(w, px, pz, 5, py, py, B.full("polished_diorite"))
    S.cylinder(w, px, pz, 5, py + 1, py + 12, AIR)
    for k in range(8):
        a = k * math.pi / 4
        x, z = px + int(round(4 * math.cos(a))), pz + int(round(4 * math.sin(a)))
        w.fill(x, py + 1, z, x, py + 5, z, B.pillar())
    S.cylinder(w, px, pz, 5, py + 6, py + 6, B.full(SM))
    S.cylinder(w, px, pz, 5, py + 6, py + 6, B.full(GOLD), hollow=True)
    S.dome(w, px, py + 7, pz, 4.5, B.full(PALE), ry=5, hollow=True)
    w.set(px, py + 13, pz, B.S("gilded_finial"))
    deco.chandelier(w, px, py + 7, pz, drop=2)
    deco.bench(w, px - 1, py, pz + 2, facing="south", length=3)
    # lamps and benches along the cross paths
    for (dx, dz, f) in ((3, -8, "west"), (-3, 8, "east"), (8, 3, "north"), (-8, -3, "south")):
        if okp(cx + dx, cz + dz):
            deco.lamp_post(w, cx + dx, py, cz + dz, height=3)
    w.spot("castle.garden", (cx, py + 1, cz + 6), 180.0)


# ============================================================================== cloisters
def cloisters(w, T):
    """Covered galleries from the great hall's side doors to the library (west) and the chapel (east)."""
    ty = P.TY
    for (xa, xb) in ((-29, -14), (14, 27)):
        z0, z1 = -134, -130
        w.fill(xa, ty + 1, z0, xb, ty + 6, z1, B.full(CB))
        w.fill(xa, ty + 1, z0 + 1, xb, ty + 5, z1 - 1, AIR)
        w.fill(xa, ty, z0 + 1, xb, ty, z1 - 1, B.full("polished_andesite"))
        w.fill(xa, ty, -132, xb, ty, -132, B.full("smooth_stone"))
        # open arcade on the south side, small windows on the north
        for x in range(xa + 1, xb, 3):
            w.fill(x, ty + 1, z1, x + 1, ty + 4, z1, AIR)
            w.set(x, ty + 4, z1, B.stairs(SM, "east", "top"))
            w.set(x + 1, ty + 4, z1, B.stairs(SM, "west", "top"))
            w.fill(x, ty + 3, z0, x + 1, ty + 4, z0, B.pane())
        w.fill(xa, ty + 6, z0 - 1, xb, ty + 6, z0 - 1, B.stairs(ROOF, "south"))
        w.fill(xa, ty + 6, z1 + 1, xb, ty + 6, z1 + 1, B.stairs(ROOF, "north"))
        w.fill(xa, ty + 7, z0, xb, ty + 7, z0, B.stairs(ROOF, "south"))
        w.fill(xa, ty + 7, z1, xb, ty + 7, z1, B.stairs(ROOF, "north"))
        w.fill(xa, ty + 7, z0 + 1, xb, ty + 7, z1 - 1, B.full(ROOF))
        w.fill(xa, ty + 8, -132, xb, ty + 8, -132, B.slab(GOLD))
        for x in range(xa + 2, xb - 1, 5):
            deco.hanging_lantern(w, x, ty + 6, -132)
    # doors through the hall's side walls and into the library/chapel
    for x in (-13, -12, 12, 13, -30, -31, 28, 29):
        w.fill(x, ty + 1, -133, x, ty + 4, -131, AIR)


def gargoyle(w, x, y, z, out):
    """Small grey gargoyle spout projecting from a cornice towards ``out``."""
    from ..world import DIR_VEC
    dx, _, dz = DIR_VEC[out]
    back = {"north": "south", "south": "north", "east": "west", "west": "east"}[out]
    w.set(x, y, z, B.stairs("stone_bricks", out, "top"))
    w.set(x + dx, y, z + dz, B.wall("stone_bricks"))
    w.set(x + 2 * dx, y, z + 2 * dz, B.stairs("stone_bricks", back, "bottom"))


def details(w, T):
    """Gargoyles on the corners, planters and lanterns along the terrace balustrade."""
    k = P.KEEP
    for (x, z, o1, o2) in ((k["x0"] - 2, k["z1"] - 2, "west", "north"), (k["x1"] + 2, k["z1"] - 2, "east", "north"),
                           (k["x0"] - 2, k["z0"] + 2, "west", "south"), (k["x1"] + 2, k["z0"] + 2, "east", "south")):
        gargoyle(w, x, k["top"] - 3, z + (2 if o2 == "south" else -2) * 0, o1)
    h = P.HALL
    for z in (-117, -147):
        gargoyle(w, h["x1"] + 1, h["eave"] - 2, z, "east")
        gargoyle(w, h["x0"] - 1, h["eave"] - 2, z, "west")
    # front bailey: flower beds and cypresses between the curtain wall and the terrace
    for x in range(-56, 57):
        for z in range(-106, -98):
            if abs(x) < 33 or not inside_walls(x, z, 3.5):
                continue
            if w.get(x, P.PY, z).split("[")[0] != "minecraft:grass_block":
                continue
            if z in (-104, -105) and x % 12 not in (0, 1):
                w.set(x, P.PY + 1, z, B.S(("cornflower", "white_tulip", "azure_bluet", "poppy")[(x // 3) % 4]))
            elif z == -103 and x % 12 == 6:
                deco.tree_cypress(w, x, P.PY, z, seed=x)
            elif z == -101 and x % 16 == 8:
                deco.lamp_post(w, x, P.PY, z, height=3)
    t = P.TERRACE
    for x in range(t["x0"] + 4, t["x1"] - 3, 8):
        if abs(x) <= 10:
            continue
        w.set(x, P.TY + 2, t["z0"], B.potted("red_tulip" if x % 16 else "azure_bluet"))
    for x in range(t["x0"] + 8, t["x1"] - 3, 16):
        if abs(x) <= 12:
            continue
        w.set(x, P.TY + 2, t["z0"], B.lantern())


def build(w, T):
    terrace(w, T)
    viaduct(w, T)
    curtain(w, T)
    gatehouse(w, T)
    keep(w, T)
    great_hall(w, T)
    star_court(w, T)
    chapel(w, T)
    library(w, T)
    barracks(w, T)
    yard(w, T)
    gardens(w, T)
    cloisters(w, T)
    details(w, T)
