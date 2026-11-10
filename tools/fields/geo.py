"""Plane geometry on the field map: polylines (roads, rivers), their crossings, and the field borders along roads.

Coordinates are world blocks (x east, z south). Polylines come from ``probe.layout()`` (the Java FieldLayout).
"""
from __future__ import annotations

import math

import numpy as np

from . import probe


def seg_len(a, b):
    return math.hypot(b[0] - a[0], b[1] - a[1])


def polyline_length(pts):
    return sum(seg_len(pts[i], pts[i + 1]) for i in range(len(pts) - 1))


def resample(pts, step=1.0):
    """Points every ``step`` blocks along the polyline with their unit direction: [(x, z, dx, dz, s), ...]."""
    out = []
    s0 = 0.0
    carry = 0.0
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        L = seg_len(a, b)
        if L == 0:
            continue
        ux, uz = (b[0] - a[0]) / L, (b[1] - a[1]) / L
        t = carry
        while t <= L:
            out.append((a[0] + ux * t, a[1] + uz * t, ux, uz, s0 + t))
            t += step
        carry = t - L
        s0 += L
    return out


def point_at(pts, s):
    """Point and direction at arc length ``s`` along a polyline."""
    acc = 0.0
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        L = seg_len(a, b)
        if acc + L >= s or i == len(pts) - 2:
            t = min(max(s - acc, 0.0), L)
            ux, uz = (b[0] - a[0]) / L, (b[1] - a[1]) / L
            return a[0] + ux * t, a[1] + uz * t, ux, uz
        acc += L
    raise ValueError


def nearest(pts, x, z):
    """(px, pz, ux, uz, s, distance) of the nearest point of a polyline."""
    best = None
    acc = 0.0
    for i in range(len(pts) - 1):
        a, b = pts[i], pts[i + 1]
        L = seg_len(a, b)
        ux, uz = ((b[0] - a[0]) / L, (b[1] - a[1]) / L) if L else (1.0, 0.0)
        t = max(0.0, min(L, (x - a[0]) * ux + (z - a[1]) * uz))
        px, pz = a[0] + ux * t, a[1] + uz * t
        d = math.hypot(x - px, z - pz)
        if best is None or d < best[5]:
            best = (px, pz, ux, uz, acc + t, d)
        acc += L
    return best


def _seg_intersect(p, p2, q, q2):
    r = (p2[0] - p[0], p2[1] - p[1])
    s = (q2[0] - q[0], q2[1] - q[1])
    den = r[0] * s[1] - r[1] * s[0]
    if abs(den) < 1e-12:
        return None
    t = ((q[0] - p[0]) * s[1] - (q[1] - p[1]) * s[0]) / den
    u = ((q[0] - p[0]) * r[1] - (q[1] - p[1]) * r[0]) / den
    if 0 <= t <= 1 and 0 <= u <= 1:
        return t, u
    return None


def crossings(a_pts, b_pts):
    """Intersections of two polylines: [(x, z, s_along_a, s_along_b, angle_deg), ...]."""
    out = []
    sa = 0.0
    for i in range(len(a_pts) - 1):
        la = seg_len(a_pts[i], a_pts[i + 1])
        sb = 0.0
        for j in range(len(b_pts) - 1):
            lb = seg_len(b_pts[j], b_pts[j + 1])
            hit = _seg_intersect(a_pts[i], a_pts[i + 1], b_pts[j], b_pts[j + 1])
            if hit:
                t, u = hit
                x = a_pts[i][0] + (a_pts[i + 1][0] - a_pts[i][0]) * t
                z = a_pts[i][1] + (a_pts[i + 1][1] - a_pts[i][1]) * t
                ax, az = (a_pts[i + 1][0] - a_pts[i][0]) / la, (a_pts[i + 1][1] - a_pts[i][1]) / la
                bx, bz = (b_pts[j + 1][0] - b_pts[j][0]) / lb, (b_pts[j + 1][1] - b_pts[j][1]) / lb
                ang = math.degrees(math.acos(max(-1.0, min(1.0, abs(ax * bx + az * bz)))))
                out.append((x, z, sa + la * t, sb + lb * u, 90.0 - ang))
            sb += lb
        sa += la
    return out


def river_half_width(river, s_frac):
    a, b = river["halfWidth"]
    return a + (b - a) * s_frac


def road_river_crossings():
    """Every place a road crosses a river: dicts with road, river, position, direction, river half width."""
    L = probe.layout()
    out = []
    for road in L["roads"]:
        for river in L["rivers"]:
            for (x, z, sr, sv, ang) in crossings(road["points"], river["points"]):
                total = polyline_length(river["points"])
                _, _, ux, uz = point_at(road["points"], sr)
                _, _, rx, rz = point_at(river["points"], sv)
                out.append({"road": road["id"], "river": river["id"], "x": x, "z": z, "s": sr,
                            "dir": (ux, uz), "river_dir": (rx, rz), "half": river_half_width(river, sv / total),
                            "level": river["level"], "angle": ang})
    return out


def road_border_crossings(step=1.0):
    """Every place a road passes from one field into another (outside the capital)."""
    L = probe.layout()
    out = []
    for road in L["roads"]:
        samples = resample(road["points"], step)
        info = probe.points([(p[0], p[1]) for p in samples])
        f = info["field"]
        for k in range(1, len(samples)):
            if f[k] != f[k - 1]:
                x, z, ux, uz, s = samples[k]
                out.append({"road": road["id"], "x": x, "z": z, "s": s, "dir": (ux, uz), "from": int(f[k - 1]),
                            "to": int(f[k])})
    return out


def junctions():
    """Road junctions: points shared by the end of one road and another road."""
    L = probe.layout()
    out = []
    roads = L["roads"]
    for r in roads:
        for end in (r["points"][0], r["points"][-1]):
            for o in roads:
                if o is r:
                    continue
                n = nearest(o["points"], end[0], end[1])
                if n[5] < 1.0:
                    out.append({"x": end[0], "z": end[1], "roads": sorted({r["id"], o["id"]})})
    uniq = {}
    for j in out:
        key = (round(j["x"]), round(j["z"]))
        uniq.setdefault(key, set()).update(j["roads"])
    return [{"x": k[0], "z": k[1], "roads": sorted(v)} for k, v in uniq.items()]


def capital_distance(x, z):
    """Python port of FieldLayout.capitalDistance (rounded rectangle)."""
    c = probe.layout()["capital"]
    x0, z0, x1, z1, r = c[0], c[1], c[2], c[3], c[4]
    cx, cz = (x0 + x1) / 2.0, (z0 + z1) / 2.0
    hx, hz = (x1 - x0) / 2.0 - r, (z1 - z0) / 2.0 - r
    qx, qz = abs(x - cx) - hx, abs(z - cz) - hz
    outside = math.hypot(max(qx, 0.0), max(qz, 0.0))
    return outside + min(max(qx, qz), 0.0) - r


def capital_distance_grid(xs, zs):
    c = probe.layout()["capital"]
    x0, z0, x1, z1, r = c[0], c[1], c[2], c[3], c[4]
    cx, cz = (x0 + x1) / 2.0, (z0 + z1) / 2.0
    hx, hz = (x1 - x0) / 2.0 - r, (z1 - z0) / 2.0 - r
    qx, qz = np.abs(xs - cx) - hx, np.abs(zs - cz) - hz
    outside = np.hypot(np.maximum(qx, 0.0), np.maximum(qz, 0.0))
    return outside + np.minimum(np.maximum(qx, qz), 0.0) - r
