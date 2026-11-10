"""The roads of the field map: bridges where they cross rivers (stone arches near the capital, timber further
out), the boardwalk where the west road skirts the moonshadow pond, border markers where a road passes from one
field into the next (white pillars with royal blue caps, pennants, banners in both fields' colours and a sign
facing each way naming the field you are entering and its level), signposts at the junctions and lamp posts along
the roads near the capital.
"""
from __future__ import annotations

import math

import numpy as np

from capital import blocks as B
from capital import shapes as SH
from capital.noise import hash2

from .. import geo
from .. import kit as K
from .. import probe
from ..world import Piece

AIR = K.AIR
F = K.F
S = K.S


def road(rid):
    return next(r for r in probe.layout()["roads"] if r["id"] == rid)


def crossing(rid, river):
    for c in geo.road_river_crossings():
        if c["road"] == rid and c["river"] == river:
            return c
    raise KeyError((rid, river))


def road_point(rid, s):
    return geo.point_at(road(rid)["points"], s)


def _ends(P_probe_top, pts, s_mid, deck_y, max_len=60):
    """Arc lengths before/after ``s_mid`` where the ground along the road reaches the deck."""
    lo = hi = None
    for d in range(1, max_len):
        if lo is None:
            x, z, _, _ = geo.point_at(pts, s_mid - d)
            if P_probe_top(x, z) >= deck_y - 1:
                lo = s_mid - d
        if hi is None:
            x, z, _, _ = geo.point_at(pts, s_mid + d)
            if P_probe_top(x, z) >= deck_y - 1:
                hi = s_mid + d
        if lo is not None and hi is not None:
            break
    return (lo if lo is not None else s_mid - max_len), (hi if hi is not None else s_mid + max_len)


def _top_fn():
    cache = {}

    def top(x, z):
        k = (int(round(x)), int(round(z)))
        if k not in cache:
            cache[k] = int(probe.points([k])["top"][0])
        return cache[k]
    return top


# ====================================================================================== bridges
def straight_bridge(pid, name, field, rid, river, style, deck_y=None, half=3, extra=3):
    """A straight bridge along the road over a river crossing. ``style``: 'stone' (arches, parapets, lamps) or
    'timber' (log trestles, plank deck, fence railings, lanterns) or 'white' (calcite arches)."""
    c = crossing(rid, river)
    pts = road(rid)["points"]
    s_mid = c["s"]
    top = _top_fn()
    if deck_y is None:
        # high enough for arches that spring from the water and rise half their span (at most 6)
        wet = [d for d in range(-30, 31) if probe.points([geo.point_at(pts, s_mid + d)[:2]])["water"][0] != -32768]
        span = (max(wet) - min(wet) + 3) if wet else 10
        rise = min(6, int(math.ceil(span / 2.0)) if span <= 15 else 5)
        deck_y = max(68, c["level"] + rise + 2)
    s0, s1 = _ends(top, pts, s_mid, deck_y)
    s0 -= extra
    s1 += extra
    ax, az, ux, uz = geo.point_at(pts, s0)
    bx, bz, _, _ = geo.point_at(pts, s1)
    # straighten: the bridge runs along the chord of the road between its ends
    L = math.hypot(bx - ax, bz - az)
    ux, uz = (bx - ax) / L, (bz - az) / L
    pad = half + 6
    P = Piece(pid, name, field, min(ax, bx) - pad, min(az, bz) - pad, max(ax, bx) + pad, max(az, bz) + pad,
              below=14, above=24, kind="bridge")
    cells, _, _ = _bridge_cells(P, (ax, az), (bx, bz), half + 1)
    level = c["level"]
    if style in ("stone", "white"):
        _stone_bridge(P, cells, L, deck_y, half, level, style, (ux, uz))
    else:
        _timber_bridge(P, cells, L, deck_y, half, level, (ux, uz))
    # ramps: ease the road up to the deck at both ends
    deck = np.zeros((P.NX, P.NZ), bool)
    for (x, z) in cells:
        deck[x - P.X0, z - P.Z0] = True
    band = K.polyline_mask(P, pts, half + 0.3)
    K.blend(P, deck, deck_y, width=6, skip=None,
            top=lambda x, z: "minecraft:dirt_path" if band[x - P.X0, z - P.Z0] else None)
    mid = (round((ax + bx) / 2), round((az + bz) / 2))
    P.probe(mid[0] + round(-uz * (half + 1)), deck_y + 1, mid[1] + round(ux * (half + 1)))
    nx, nz = -uz, ux
    P.view(pid, (mid[0] + nx * 34 + ux * 18, deck_y + 18, mid[1] + nz * 34 + uz * 18), (mid[0], deck_y - 2, mid[1]),
           fov=55)
    return P


def _bridge_cells(P, a, b, half):
    (ax, az), (bx, bz) = a, b
    L = math.hypot(bx - ax, bz - az)
    ux, uz = (bx - ax) / L, (bz - az) / L
    out = {}
    for x in range(P.X0, P.X1 + 1):
        for z in range(P.Z0, P.Z1 + 1):
            px, pz = x - ax, z - az
            t = px * ux + pz * uz
            s = -px * uz + pz * ux
            if -0.5 <= t <= L + 0.5 and abs(s) <= half + 0.45:
                out[(x, z)] = (t, s)
    return out, (ux, uz), L


def _stone_bridge(P, cells, L, deck_y, half, level, style, u):
    stone = "stone_bricks" if style == "stone" else "calcite"
    trim = "smooth_stone" if style == "stone" else "smooth_quartz"
    pave_mix = K.COBBLE_MIX if style == "stone" else [("polished_diorite", 40), ("calcite", 30), ("smooth_quartz", 30)]
    wall = B.wall("stone_brick") if style == "stone" else B.wall("diorite")
    # arches: spans placed symmetrically over the wet part
    wet = [t for (x, z), (t, s) in cells.items() if P.wet(x, z) and abs(s) < 1]
    if wet:
        w0, w1 = min(wet) - 1.5, max(wet) + 1.5
    else:
        w0, w1 = L * 0.35, L * 0.65
    span = w1 - w0
    n = 1 if span <= 21 else 3
    pier = 2.0
    arches = []
    if n == 1:
        arches = [(w0, w1)]
    else:
        side = (span - 2 * pier) * 0.2
        mid = span - 2 * pier - 2 * side
        a = w0
        arches = [(a, a + side), (a + side + pier, a + side + pier + mid), (w1 - side, w1)]
    spring = level

    def underside(t):
        for (a, b) in arches:
            if a <= t <= b:
                c, w = (a + b) / 2, (b - a) / 2
                rise = min(deck_y - 2 - spring, max(w * 0.95, 2.0))
                return spring + rise * math.sqrt(max(0.0, 1 - ((t - c) / w) ** 2))
        return None

    for (x, z), (t, s) in cells.items():
        edge = abs(s) > half - 0.05
        tg = P.top(x, z)
        us = underside(t)
        if us is None:
            bottom = min(tg, level) - 1
            P.fill(x, bottom, z, x, deck_y - 1, z, F(stone))
        else:
            ub = int(math.floor(us))
            P.fill(x, ub, z, x, deck_y - 1, z, F(stone))
            if not P.wet(x, z):
                P.fill(x, max(tg + 1, P.Y0), z, x, ub - 1, z, AIR)
            else:
                P.fill(x, tg + 1, z, x, min(ub - 1, level), z, S("water", level=0))
                if ub - 1 > level:
                    P.fill(x, level + 1, z, x, ub - 1, z, AIR)
        if edge:
            P.set(x, deck_y, z, F(trim))
            P.set(x, deck_y + 1, z, wall)
        else:
            K.mixed(P, x, deck_y, z, pave_mix, seed=17)
            P.fill(x, deck_y + 1, z, x, deck_y + 4, z, AIR)
    # lamp posts on the parapets at the ends and the middle
    ux, uz = u
    nx, nz = -uz, ux
    keys = sorted(cells.items(), key=lambda kv: kv[1][0])
    tmin, tmax = keys[0][1][0], keys[-1][1][0]
    for tt in (tmin + 1.5, (tmin + tmax) / 2, tmax - 1.5):
        for side in (-1, 1):
            best = min(cells.items(), key=lambda kv: (kv[1][0] - tt) ** 2 + (kv[1][1] - side * half) ** 2)
            (x, z) = best[0]
            P.set(x, deck_y + 1, z, F(stone))
            P.set(x, deck_y + 2, z, wall)
            P.set(x, deck_y + 3, z, B.lantern())


def _timber_bridge(P, cells, L, deck_y, half, level, u):
    for (x, z), (t, s) in cells.items():
        edge = abs(s) > half - 0.05
        tg = P.top(x, z)
        P.set(x, deck_y, z, F("spruce_planks") if not edge else B.log("spruce", "x" if abs(u[0]) > abs(u[1]) else "z"))
        if not edge:
            P.fill(x, deck_y + 1, z, x, deck_y + 4, z, AIR)
        else:
            P.set(x, deck_y + 1, z, B.fence("spruce"))
        # trestles every 4 blocks: log posts down to the bed with cross braces
        if int(round(t)) % 5 == 0 and (abs(abs(s) - half) < 0.6 or abs(s) < 0.6):
            P.fill(x, min(tg, level) - 1, z, x, deck_y - 1, z, B.log("spruce"))
        elif int(round(t)) % 5 == 0:
            P.set(x, deck_y - 1, z, B.log("spruce", "x" if abs(u[1]) > abs(u[0]) else "z"))
    ux, uz = u
    keys = sorted(cells.items(), key=lambda kv: kv[1][0])
    tmin, tmax = keys[0][1][0], keys[-1][1][0]
    for tt in np.arange(tmin + 2, tmax - 1, 9.0):
        for side in (-1, 1):
            best = min(cells.items(), key=lambda kv: (kv[1][0] - tt) ** 2 + (kv[1][1] - side * half) ** 2)
            (x, z) = best[0]
            P.set(x, deck_y + 1, z, B.log("spruce"))
            P.set(x, deck_y + 2, z, B.fence("spruce"))
            P.set(x, deck_y + 3, z, B.lantern())


# ====================================================================================== border markers
CAPITAL_TEXT = '{"translate":"map.eternalrealm.capital","bold":true}'


def border_marker(pid, rid, s, field_a, field_b, capital_side=False):
    """A gate where the road at arc length ``s`` passes from ``field_a`` into ``field_b``: two white castle-stone
    pillars with royal blue caps and pennants, joined by a lintel with the kingdom crest where the road runs close
    to a cardinal direction; banners in each field's colours on the faces seen when entering it, and signs facing
    both ways: 「ここより / <field> / 推奨Lv.」."""
    x, z, ux, uz = road_point(rid, s)
    hw = road(rid)["halfWidth"]
    axis_aligned = max(abs(ux), abs(uz)) >= math.cos(math.radians(31))
    if axis_aligned:
        # snap the gate to the axis: the pillars stand exactly across the road
        if abs(ux) >= abs(uz):
            ux, uz = (1.0 if ux > 0 else -1.0), 0.0
        else:
            ux, uz = 0.0, (1.0 if uz > 0 else -1.0)
    nx, nz = -uz, ux
    off = hw + 4.0 if axis_aligned else hw + 4.5
    P = Piece(pid, f"境界の門 {field_a}→{field_b} ({rid} road)", field_b, x - 17, z - 17, x + 17, z + 17, below=8,
              above=24, kind="border")
    pillars = []
    for side in (-1, 1):
        px, pz = int(round(x + nx * off * side)), int(round(z + nz * off * side))
        pillars.append((px, pz, side))
    y = max(P.top(px + a, pz + b) for (px, pz, _) in pillars for a in (-1, 0, 1) for b in (-1, 0, 1))
    face_to_a = K.facing_of(-ux, -uz)        # faces seen by people coming from field a (entering b)
    face_to_b = K.facing_of(ux, uz)          # faces seen by people coming from field b (entering a)
    for (px, pz, side) in pillars:
        for a in range(-2, 3):
            for b in range(-2, 3):
                xx, zz = px + a, pz + b
                t = P.top(xx, zz)
                inner = abs(a) <= 1 and abs(b) <= 1
                if inner or t <= y:
                    P.fill(xx, min(t, y) - 2, zz, xx, y, zz, F("stone_bricks"))
                if t > y and inner:
                    P.fill(xx, y + 1, zz, xx, t + 2, zz, AIR)
                if not inner and t <= y:
                    P.set(xx, y + 1, zz, B.slab("polished_andesite", "bottom"))
        P.fill(px - 1, y + 1, pz - 1, px + 1, y + 1, pz + 1, F("polished_andesite"))
        P.fill(px - 1, y + 2, pz - 1, px + 1, y + 7, pz + 1, F("castle_bricks"))
        for k in range(y + 2, y + 8):
            for (a, b) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                P.set(px + a, k, pz + b, F("smooth_castle_stone") if k % 2 == 0 else F("castle_bricks"))
        SH.corbel_rect(P, px - 1, pz - 1, px + 1, pz + 1, y + 8, "smooth_castle_stone")
        P.fill(px - 1, y + 8, pz - 1, px + 1, y + 8, pz + 1, F("smooth_castle_stone"))
        P.fill(px - 2, y + 9, pz - 2, px + 2, y + 9, pz + 2, B.slab("smooth_castle_stone", "bottom"))
        SH.square_spire(P, px, pz, 1, y + 9, 4, "royal_roof_tiles", finial=False)
        P.set(px, y + 13, pz, F("gilded_trim"))
        K.flagpole(P, px, y + 13, pz, 4, facing="east", design="pennant", width=4.5, height=1.0, wood="dark_oak")
        for face, fld in ((face_to_a, field_b), (face_to_b, field_a)):
            fx, fz = K.DIRS[face]
            bx, bz = px + fx * 2, pz + fz * 2
            color = K.FIELD_COLORS[fld][0]
            K.wall_banner(P, bx, y + 7, bz, color, face, K.banner_nbt_field(fld))
            lines_b = [K.BX.text_component(translate="sign.eternalrealm.entering"),
                       (CAPITAL_TEXT if (capital_side and fld == field_a) else K.field_name(fld)),
                       ("" if (capital_side and fld == field_a) else K.field_level(fld)), ""]
            K.place_sign(P, bx, y + 3, bz, lines_b, wood="dark_oak", facing=face, wall=True)
            # a lantern on a bracket beside the sign
            sx, sz = K.DIRS[K.CW[face]]
            if P.get_idx(bx + sx, y + 4, bz + sz) == 0:
                P.set(bx + sx, y + 5, bz + sz, B.wall("stone_bricks"))
                P.set(bx + sx, y + 4, bz + sz, B.lantern(hanging=True))
    if axis_aligned:
        # the lintel: a castle-stone beam on corbels, arched underneath, the crest in the middle
        (ax_, az_, _), (bx_, bz_, _) = pillars
        along = [(round(ax_ + (bx_ - ax_) * i / max(1, abs(bx_ - ax_) + abs(bz_ - az_))),
                  round(az_ + (bz_ - az_) * i / max(1, abs(bx_ - ax_) + abs(bz_ - az_))))
                 for i in range(abs(bx_ - ax_) + abs(bz_ - az_) + 1)]
        span = len(along) - 1
        for i, (lx, lz) in enumerate(along):
            d = min(i, span - i)
            for w in (-1, 0, 1):
                wx, wz = lx + round(ux * w), lz + round(uz * w)
                P.set(wx, y + 9, wz, F("castle_bricks"))
                P.set(wx, y + 8, wz, F("castle_bricks") if d <= 2 else F("smooth_castle_stone"))
                if d >= 2:
                    P.set(wx, y + 10, wz, B.slab("smooth_castle_stone", "bottom"))
                if 2 <= d <= 3:
                    P.set(wx, y + 7, wz, B.stairs("castle_bricks", K.facing_of(-(lx - (ax_ + bx_) / 2),
                                                                                 -(lz - (az_ + bz_) / 2)), "top"))
            if i == span // 2:
                for face in (face_to_a, face_to_b):
                    fx, fz = K.DIRS[face]
                    P.set(lx + fx, y + 9, lz + fz, B.S("castle_crest_stone"))
                P.set(lx, y + 7, lz, B.chain())
                P.set(lx, y + 6, lz, B.lantern(hanging=True))
    # paving between the pillars (the road continues as cobbles through the gate)
    for (xx, zz) in K.cells(K.segment_mask(P, (x - ux * 7, z - uz * 7), (x + ux * 7, z + uz * 7), hw + 0.6), P):
        if not P.wet(xx, zz):
            K.mixed(P, xx, P.top(xx, zz), zz, K.COBBLE_MIX, seed=29)
    P.probe(pillars[0][0], y + 1, pillars[0][1])
    P.view(pid, (x + nx * 24 - ux * 22, y + 15, z + nz * 24 - uz * 22), (x, y + 6, z), fov=55)
    return P


# ====================================================================================== signposts
ARROWS = {0: "↑ ", 1: "→ ", 2: "↓ ", 3: "← "}


def signpost(pid, name, field, x, z, dests, wood="dark_oak"):
    """A fingerpost at (x, z): a log post with a lantern and a wall sign on each side listing the destinations
    ``dests`` = [(direction vector (dx, dz), text component JSON), ...] with arrows relative to the reader."""
    P = Piece(pid, name, field, x - 4, z - 4, x + 4, z + 4, below=6, above=12, kind="signpost")
    y = P.top(x, z)
    for a in range(-1, 2):
        for b in range(-1, 2):
            K.mixed(P, x + a, P.top(x + a, z + b), z + b, K.COBBLE_MIX, seed=33)
    P.set(x, y + 1, z, B.log(wood))
    P.set(x, y + 2, z, B.log(wood))
    P.set(x, y + 3, z, B.log(wood))
    P.set(x, y + 4, z, B.fence(wood))
    P.set(x, y + 5, z, B.lantern())
    for face in ("north", "east", "south", "west"):
        fx, fz = K.DIRS[face]
        view = (-fx, -fz)                       # the reader looks at the sign, i.e. towards -face
        lines = []
        for (dv, text) in dests:
            ang = math.atan2(dv[0] * view[1] - dv[1] * view[0], dv[0] * view[0] + dv[1] * view[1])
            q = int(round(ang / (math.pi / 2))) % 4      # 0 ahead, 1 right? (screen: x right)
            arrow = {0: "↑ ", 1: "← ", 2: "↓ ", 3: "→ "}[q]
            lines.append('{"text":"","extra":[{"text":"' + arrow + '"},' + text + "]}")
        lines = lines[:4]
        K.place_sign(P, x + fx, y + 2 if len(lines) <= 2 else y + 2, z + fz, lines[:4], wood=wood, facing=face,
                     wall=True)
    P.probe(x, y + 1, z)
    return P


def dest(field=None, text=None, key=None):
    if field:
        return K.field_name(field).replace(',"bold":true', "")
    if key:
        return '{"translate":"' + key + '"}'
    return '{"text":"' + text + '"}'


# ====================================================================================== the pond boardwalk
def pond_boardwalk():
    """Where the west road crosses the edge of the moonshadow pond: a plank boardwalk on log piles, rope railings
    and two lanterns."""
    pts = road("west")["points"]
    samples = geo.resample(pts, 1.0)
    info = probe.points([(p[0], p[1]) for p in samples])
    wet = np.nonzero(info["water"] != -32768)[0]
    s0, s1 = samples[wet.min()][4] - 6, samples[wet.max()][4] + 6
    ax, az, ux, uz = geo.point_at(pts, s0)
    bx, bz, _, _ = geo.point_at(pts, s1)
    P = Piece("road_pond_boardwalk", "月影の池の木道 (pond boardwalk, west road)", 2, min(ax, bx) - 8, min(az, bz) - 8,
              max(ax, bx) + 8, max(az, bz) + 8, below=8, above=14, kind="bridge")
    cells, (ux, uz), L = _bridge_cells(P, (ax, az), (bx, bz), 2.5)
    deck = 70
    for (x, z), (t, s) in cells.items():
        edge = abs(s) > 2.0
        tg = P.top(x, z)
        P.set(x, deck, z, F("spruce_planks") if not edge else B.slab("spruce", "top"))
        P.fill(x, deck + 1, z, x, deck + 3, z, AIR)
        if edge and int(round(t)) % 4 == 0:
            P.fill(x, tg, z, x, deck - 1, z, B.log("spruce"))
            P.set(x, deck + 1, z, B.fence("spruce"))
        elif edge:
            P.set(x, deck + 1, z, B.fence("spruce"))
    for tt in (6.0, L - 6.0):
        for side in (-1, 1):
            best = min(cells.items(), key=lambda kv: (kv[1][0] - tt) ** 2 + (kv[1][1] - side * 2.5) ** 2)
            (x, z) = best[0]
            K.lantern_post(P, x, deck - 1, z, height=2, wood="spruce", soul=True)
    m = np.zeros((P.NX, P.NZ), bool)
    for (x, z) in cells:
        m[x - P.X0, z - P.Z0] = True
    band = K.polyline_mask(P, pts, 2.8)
    K.blend(P, m, deck, width=5, top=lambda x, z: "minecraft:dirt_path" if band[x - P.X0, z - P.Z0] else None)
    mid = ((ax + bx) / 2, (az + bz) / 2)
    P.probe(int(round(mid[0])), deck, int(round(mid[1])))
    return P


# ====================================================================================== lamps along the roads
def _busy_points():
    """Places along the roads that have their own set piece (bridges, gates, junctions, shrines)."""
    pts = [(c["x"], c["z"]) for c in geo.road_border_crossings()]
    pts += [(c["x"], c["z"]) for c in geo.road_river_crossings()]
    pts += [(j["x"], j["z"]) for j in geo.junctions()]
    pts += [(w["x"], w["z"]) for w in probe.layout()["waystones"]]
    for rid in ("west", "east"):
        x, z, _, _ = road_point(rid, 38)
        pts.append((x, z))
    return pts


def lamp_run(pid, rid, s_from, s_to, every=18, side_off=4.5, field=1, style="iron"):
    """Lamp posts along a road between two arc lengths, alternating sides (not at bridges, gates, junctions)."""
    pts = road(rid)["points"]
    busy = _busy_points()
    ax, az, _, _ = geo.point_at(pts, s_from)
    bx, bz, _, _ = geo.point_at(pts, s_to)
    # bbox of the run
    xs, zs = [], []
    s = s_from
    while s <= s_to:
        x, z, _, _ = geo.point_at(pts, s)
        xs.append(x)
        zs.append(z)
        s += 4
    P = Piece(pid, f"街道の灯り ({rid} road)", field, min(xs) - 8, min(zs) - 8, max(xs) + 8, max(zs) + 8, below=4,
              above=10, kind="road")
    k = 0
    s = s_from
    while s <= s_to:
        x, z, ux, uz = geo.point_at(pts, s)
        side = 1 if k % 2 == 0 else -1
        px, pz = int(round(x - uz * side_off * side)), int(round(z + ux * side_off * side))
        k += 1
        s += every
        if P.wet(px, pz) or geo.capital_distance(px, pz) < 26:
            continue
        if any(math.hypot(px - bx, pz - bz) < 22 for (bx, bz) in busy):
            continue
        nearest = geo.nearest(pts, px, pz)
        if nearest[5] < side_off - 1.2:
            continue
        y = P.top(px, pz)
        if style == "moon":
            K.lantern_post(P, px, y, pz, height=3, wood="dark_oak", soul=True,
                           arm=K.facing_of(uz * side, -ux * side))
        elif style == "iron":
            P.set(px, y, pz, F("polished_andesite"))
            P.set(px, y + 1, pz, F("polished_blackstone"))
            P.fill(px, y + 2, pz, px, y + 3, pz, B.wall("polished_blackstone"))
            P.set(px, y + 4, pz, B.slab("polished_blackstone", "top"))
            P.set(px, y + 5, pz, B.lantern())
        else:
            K.lantern_post(P, px, y, pz, height=3, wood="spruce",
                           arm=K.facing_of(uz * side, -ux * side))
    # probe: the first post
    for x in range(P.X0, P.X1 + 1):
        for z in range(P.Z0, P.Z1 + 1):
            col = P.a[x - P.X0, z - P.Z0]
            nz_ = np.nonzero(col > 1)[0]
            if nz_.size:
                yb = int(nz_[0]) + P.Y0
                st = P.get(x, yb + 1, z)
                if st.split("[")[0] in ("minecraft:polished_blackstone", "minecraft:spruce_log",
                                        "minecraft:dark_oak_log"):
                    P.probe(x, yb + 1, z)
                    return P
    return P


# ====================================================================================== the set
def build(only=None):
    out = []

    def want(pid):
        return not only or pid in only

    # bridges
    if want("bridge_east_river"):
        out.append(straight_bridge("bridge_east_river", "東街道の石橋 (east road over the falls river)", 8, "east",
                                   "falls_river", "stone", half=3))
    if want("bridge_meadow_brook"):
        out.append(straight_bridge("bridge_meadow_brook", "草原の小川の石橋 (south road over the meadow brook)", 1,
                                   "south", "meadow_brook", "stone", half=3))
    if want("bridge_lakeside_river"):
        out.append(straight_bridge("bridge_lakeside_river", "湖畔の木橋 (lakeside road over the falls river)", 9,
                                   "lakeside", "falls_river", "timber", deck_y=66, half=3))
    if want("road_pond_boardwalk"):
        out.append(pond_boardwalk())
    # border markers
    markers = [("border_west_capital", "west", 38, 1, 2, True), ("border_east_capital", "east", 38, 1, 8, True)]
    for c in geo.road_border_crossings():
        if c["s"] < 10:
            continue
        markers.append((f"border_{c['road']}_{c['from']}_{c['to']}", c["road"], c["s"], c["from"], c["to"], False))
    for (pid, rid, s, fa, fb, cap) in markers:
        if want(pid):
            out.append(border_marker(pid, rid, s, fa, fb, capital_side=cap))
    # signposts at the junctions without a shrine beside them
    if want("signpost_east_falls"):
        out.append(signpost("signpost_east_falls", "道標 東街道・滝道の分岐", 8, 426, 50, [
            ((-1, 0), '{"translate":"map.eternalrealm.capital"}'),
            ((0.94, 0.35), dest(field=8)),
            ((0.38, -0.93), dest(field=4))]))
    if want("signpost_moon_forest"):
        out.append(signpost("signpost_moon_forest", "道標 月影の森の辻", 2, -692, 62, [
            ((1, -0.1), '{"translate":"map.eternalrealm.capital"}'),
            ((-0.3, -1), dest(field=7)),
            ((-0.7, 0.7), dest(field=3))]))
    if want("signpost_farm"):
        out.append(signpost("signpost_farm", "道標 農場の辻", 8, 732, 238, [
            ((-0.9, -0.45), '{"translate":"map.eternalrealm.capital"}'),
            ((0.8, -0.6), dest(field=5)),
            ((0.53, 0.85), dest(field=10))]))
    # lamp posts along the roads near the capital
    runs = [("lamps_south", "south", 30, 300, 1), ("lamps_lakeside", "lakeside", 30, 300, 1),
            ("lamps_west", "west", 30, 300, 2), ("lamps_east", "east", 30, 300, 8)]
    for (pid, rid, a, b, fld) in runs:
        if want(pid):
            out.append(lamp_run(pid, rid, a, b, every=18, field=fld, style="iron"))
    # soul lanterns on dark oak posts along the roads through the moon forest
    for (pid, rid, a, b) in (("lamps_moon_west", "west", 320, 1050), ("lamps_moon_end", "end", 24, 474)):
        if want(pid):
            out.append(lamp_run(pid, rid, a, b, every=24, field=2, style="moon"))
    return out
