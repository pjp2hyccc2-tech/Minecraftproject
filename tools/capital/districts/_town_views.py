#!/usr/bin/env python3
"""Town preview helper (not a district: files starting with "_" are not run by build.py).

    python3 tools/capital/districts/_town_views.py town_aerial,guild_front --scale 0.5
    python3 tools/capital/districts/_town_views.py --list
    python3 tools/capital/districts/_town_views.py --check          # gimmick keep-free volumes only
    python3 tools/capital/districts/_town_views.py --slice guild:1 # plan slice of a district at y=1

Builds the whole capital (no export), then renders the town cameras below with ``capital.preview``. By default
the animated gimmicks the game draws around their blocks (flags, windmill sails, clock dials, water wheels,
fountain jets) are stood in by temporary blocks on a copy of the world so the previews show roughly what a
player will see; ``--raw`` renders the world as exported.
"""
from __future__ import annotations

import math
import os
import re
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(os.path.dirname(HERE)))

import numpy as np  # noqa: E402

from capital import blocks as B  # noqa: E402
from capital import layout as L  # noqa: E402
from capital import preview as P  # noqa: E402
from capital.world import DIR_VEC, X0, Y0, Z0, left_of  # noqa: E402

SUN_AM = (40, -150)


def views():
    s = P.sun_dir
    return {
        "town_aerial": dict(pos=(-30, 118, 236), look=(0, 0, -30), fov=60, sun=s(40, -150), fog=3000,
                            label="Arseria from over the lake (cf. the owner's layout reference)"),
        "town_aerial_w": dict(pos=(-215, 95, 150), look=(-30, 0, 0), fov=60, sun=s(38, -140), fog=3000,
                              label="From the south-west: farms, windmills, walls"),
        "town_aerial_e": dict(pos=(215, 90, 140), look=(40, 0, 0), fov=60, sun=s(38, -160), fog=3000,
                              label="From the south-east: harbour, market, cathedral"),
        "plaza_spawn": dict(pos=(0, 3.2, 25), look=(0, 14, -60), fov=70, sun=s(36, -160), fog=2600,
                            label="Spawn: looking north at the fountain and the castle"),
        "plaza_guild": dict(pos=(14, 3.5, 6), look=(-40, 14, -14), fov=70, sun=s(40, -130), fog=2600,
                            label="The adventurers' guild across the plaza"),
        "plaza_market": dict(pos=(-12, 4, -6), look=(50, 2, 22), fov=72, sun=s(40, -150), fog=2600,
                             label="From the plaza towards the market"),
        "plaza_south": dict(pos=(-14, 5, -18), look=(14, 4, 40), fov=72, sun=s(40, -150), fog=2600,
                            label="From the plaza looking south"),
        "cathedral_s": dict(pos=(30, 4, -20), look=(62, 26, -66), fov=70, sun=s(40, -150), fog=2600,
                            label="The cathedral from the south"),
        "main_gate": dict(pos=(6, 7, 128), look=(0, 18, 76), fov=64, sun=s(40, -150), fog=2600,
                          label="The south gate from the lake bridge"),
        "main_gate_in": dict(pos=(3, 3.5, 50), look=(0, 14, 86), fov=68, sun=s(40, -150), fog=2600,
                             label="The south gate from inside"),
        "harbour": dict(pos=(30, 14, 150), look=(80, 4, 96), fov=66, sun=s(40, -150), fog=2600,
                        label="The harbour"),
        "farms": dict(pos=(-165, 30, 160), look=(-128, 8, 60), fov=66, sun=s(40, -150), fog=2600,
                      label="Farms and windmills south-west of the walls"),
        "arena": dict(pos=(-104, 42, -6), look=(-137, 2, -48), fov=66, sun=s(40, -150), fog=2600,
                      label="The arena outside the west gate"),
        "watermill": dict(pos=(108, 10, 64), look=(128, 2, 40), fov=66, sun=s(40, -150), fog=2600,
                          label="The watermill on the river"),
        "residential_w": dict(pos=(-62, 4, -2), look=(-100, 6, 30), fov=70, sun=s(40, -150), fog=2600,
                              label="West quarter"),
        "west_aerial": dict(pos=(-48, 34, 36), look=(-86, 4, -8), fov=66, sun=s(40, -150), fog=2600,
                            label="West quarter from above"),
        "west_street": dict(pos=(-60, 3.6, 0), look=(-92, 7, -9), fov=70, sun=s(40, -150), fog=2600,
                            label="West avenue houses"),
        "upper_town": dict(pos=(-30, 14, -36), look=(-70, 6, -76), fov=70, sun=s(40, -150), fog=2600,
                           label="Upper town at the foot of the castle cliff"),
        "south_quarter": dict(pos=(-6, 4, 46), look=(-60, 6, 64), fov=70, sun=s(40, -150), fog=2600,
                              label="South-west quarter"),
        "inn_street": dict(pos=(6, 4, 46), look=(60, 6, 62), fov=70, sun=s(40, -150), fog=2600,
                           label="Inn quarter"),
    }


def district_views():
    """Extra cameras (interiors) declared by district modules as a module-level ``VIEWS`` dict."""
    import importlib
    import pkgutil
    from capital import districts as pkg
    out = {}
    for m in sorted(pkgutil.iter_modules(pkg.__path__), key=lambda m: m.name):
        if m.name.startswith("_"):
            continue
        mod = importlib.import_module(f"capital.districts.{m.name}")
        for k, v in getattr(mod, "VIEWS", {}).items():
            kw = dict(v)
            if isinstance(kw.get("sun"), tuple):
                kw["sun"] = P.sun_dir(*kw["sun"])
            out[k] = kw
    return out


# ====================================================================================== gimmick dressing
FLAG_COLORS = {
    "kingdom": ("blue", "yellow"), "guild": ("red", "yellow"), "pennant": ("blue", "yellow"),
    "cathedral": ("white", "yellow"), "market": ("red", "white"), "harbour": ("light_blue", "white"),
}


def _num(snbt, key, default):
    m = re.search(key + r":([-0-9.]+)f?", snbt)
    return float(m.group(1)) if m else default


def _design(snbt):
    m = re.search(r'Design:"([a-z_]+)"', snbt)
    return m.group(1) if m else "kingdom"


def gimmicks(w):
    """[(kind, (x, y, z), facing, snbt)] for every gimmick block in the world."""
    out = []
    ids = {}
    kinds = ("flag", "clock_face", "windmill_sails", "water_wheel", "fountain_spray", "smoke_emitter")
    for i, st in enumerate(w.palette):
        if st.startswith("eternalrealm:") and st.split(":")[1].split("[")[0] in kinds:
            ids[i] = st
    if not ids:
        return out
    m = np.isin(w.a, np.array(list(ids), np.uint16))
    xi, zi, yi = np.nonzero(m)
    for i, j, k in zip(xi, zi, yi):
        st = ids[int(w.a[i, j, k])]
        x, y, z = int(i + X0), int(k + Y0), int(j + Z0)
        bid, props = B.parse(st)
        out.append((bid.split(":")[1], (x, y, z), props.get("facing", "up"), w.block_entities.get((x, y, z), ""),
                    props))
    return out


def keep_free(kind, pos, facing, snbt, props):
    """Blocks that must stay free (air/water) for the gimmick's moving part."""
    x, y, z = pos
    pts = []
    if kind == "flag":
        W = _num(snbt, "Width", 3.0)
        H = _num(snbt, "Height", 2.0)
        fx, _, fz = DIR_VEC[facing]
        lx, _, lz = DIR_VEC[left_of(facing)]
        for a in range(1, int(math.ceil(W + 0.5)) + 1):
            for dy in range(0, int(math.ceil(H))):
                for s in (-1, 0, 1):
                    pts.append((x + fx * a + lx * s, y - dy, z + fz * a + lz * s))
    elif kind in ("windmill_sails", "water_wheel", "clock_face"):
        R = _num(snbt, "Radius", 7.0) if kind != "clock_face" else _num(snbt, "Size", 2.5)
        depth = (1, 2) if kind == "windmill_sails" else ((1,) if kind == "clock_face" else
                                                           tuple(range(1, int(math.ceil(0.15 + _num(snbt, "Width", 2.0))) + 1)))
        fx, _, fz = DIR_VEC[facing]
        lx, _, lz = DIR_VEC[left_of(facing)]
        Ri = int(math.ceil(R))
        for d in depth:
            for u in range(-Ri, Ri + 1):
                for v in range(-Ri, Ri + 1):
                    if u * u + v * v <= (R + 0.3) ** 2:
                        pts.append((x + fx * d + lx * u, y + v, z + fz * d + lz * u))
    return pts


def check_gimmicks(w, verbose=True):
    bad = []
    n = 0
    for kind, pos, facing, snbt, props in gimmicks(w):
        if kind in ("fountain_spray", "smoke_emitter"):
            continue
        n += 1
        hits = []
        for p in keep_free(kind, pos, facing, snbt, props):
            st = w.get(*p)
            if st in ("minecraft:air", "eternalrealm:void") or st.startswith("minecraft:water") or "light_shaft" in st:
                continue
            if kind == "water_wheel" and "water" in st:
                continue
            hits.append((p, st))
        if hits:
            bad.append((kind, pos, facing, hits))
    if verbose:
        print(f"gimmicks: {n} checked, {len(bad)} with blocks in their keep-free volume")
        for kind, pos, facing, hits in bad[:60]:
            print(f"  {kind} at {pos} facing {facing}: {len(hits)} blocks, e.g. "
                  + ", ".join(f"{p}={s.split(':')[1][:28]}" for p, s in hits[:4]))
    return bad


def _fix_preview_colors(w):
    """Blocks missing from the preview colour table render magenta: map them to look-alikes (preview only)."""
    from capital import colors as C
    alike = {"magma_block": "orange_terracotta", "lava_cauldron": "cauldron", "raw_iron_block": "granite",
             "raw_gold_block": "gold_block", "raw_copper_block": "copper_block", "honeycomb_block": "yellow_wool"}
    for i, st in enumerate(list(w.palette)):
        if i < 2:
            continue
        try:
            m = C.material(st)
        except Exception:
            continue
        if m["kind"] != "air" and tuple(m["top"]) == (255, 0, 255):
            name = st.split(":")[1].split("[")[0]
            rep = alike.get(name, "stone")
            w.palette[i] = B.S(rep)


def dress(w):
    """Draw stand-ins for the animated gimmicks into the world (use on a copy)."""
    _fix_preview_colors(w)
    for kind, pos, facing, snbt, props in gimmicks(w):
        x, y, z = pos
        if kind == "flag":
            W = _num(snbt, "Width", 3.0)
            H = _num(snbt, "Height", 2.0)
            d = _design(snbt)
            c1, c2 = FLAG_COLORS.get(d, ("blue", "yellow"))
            fx, _, fz = DIR_VEC[facing]
            n = max(1, int(round(W)))
            hh = max(1, int(round(H)))
            for a in range(1, n + 1):
                hgt = hh if d != "pennant" else max(1, int(round(hh * (1 - (a - 1) / max(n, 1)))))
                wave = int(round(0.6 * math.sin(a * 1.3 + x * 0.7 + z * 0.3)))
                lx, _, lz = DIR_VEC[left_of(facing)]
                for dy in range(hgt):
                    c = c1
                    if d == "market":
                        c = c1 if a % 2 else c2
                    elif d in ("kingdom", "guild", "cathedral", "harbour"):
                        mid = n // 2 + 1
                        if a == mid and dy == hgt // 2:
                            c = c2
                    elif d == "pennant" and dy == 0:
                        c = c2
                    p = (x + fx * a + lx * wave, y - dy, z + fz * a + lz * wave)
                    if w.is_empty(*p):
                        w.set(*p, B.wool(c))
        elif kind == "windmill_sails":
            R = _num(snbt, "Radius", 7.0)
            fx, _, fz = DIR_VEC[facing]
            lx, _, lz = DIR_VEC[left_of(facing)]
            for ang in (45, 135, 225, 315):
                a = math.radians(ang)
                for r10 in range(10, int(R * 10) + 1, 5):
                    r = r10 / 10.0
                    for wdt in (0.0, 0.9, 1.8):
                        u = r * math.cos(a) - wdt * math.sin(a)
                        v = r * math.sin(a) + wdt * math.cos(a)
                        p = (x + fx + lx * int(round(u)), y + int(round(v)), z + fz + lz * int(round(u)))
                        if w.is_empty(*p):
                            w.set(*p, B.wool("white") if wdt > 0 else B.S("spruce_planks"))
            w.set(x + fx, y, z + fz, B.log("spruce", "x" if fx else "z"))
        elif kind == "clock_face":
            R = _num(snbt, "Size", 2.5)
            fx, _, fz = DIR_VEC[facing]
            lx, _, lz = DIR_VEC[left_of(facing)]
            Ri = int(math.ceil(R))
            for u in range(-Ri, Ri + 1):
                for v in range(-Ri, Ri + 1):
                    dd = math.hypot(u, v)
                    if dd <= R + 0.3:
                        st = B.full("gilded_trim") if dd > R - 0.7 else B.S("white_concrete")
                        if (u == 0 and 0 <= v <= R - 1) or (v == 0 and 0 <= u <= R - 1.5):
                            st = B.S("black_concrete")
                        p = (x + fx + lx * u, y + v, z + fz + lz * u)
                        if w.is_empty(*p):
                            w.set(*p, st)
        elif kind == "water_wheel":
            R = _num(snbt, "Radius", 3.0)
            Wd = _num(snbt, "Width", 2.0)
            fx, _, fz = DIR_VEC[facing]
            lx, _, lz = DIR_VEC[left_of(facing)]
            Ri = int(math.ceil(R))
            for d in range(1, int(math.ceil(0.15 + Wd)) + 1):
                for u in range(-Ri, Ri + 1):
                    for v in range(-Ri, Ri + 1):
                        dd = math.hypot(u, v)
                        rim = abs(dd - R) < 0.6
                        spoke = (u == 0 or v == 0) and dd < R
                        if rim or spoke:
                            p = (x + fx * d + lx * u, y + v, z + fz * d + lz * u)
                            cur = w.get(*p)
                            if w.is_empty(*p) or "water" in cur:
                                w.set(*p, B.S("spruce_planks") if rim else B.log("dark_oak"))
        elif kind == "fountain_spray":
            pw = int(props.get("power", "2"))
            if facing == "up":
                hgt = {1: 2, 2: 3, 3: 5}[pw]
                for k in range(hgt):
                    p = (x, y + k, z)
                    if w.is_empty(*p) or "fountain_spray" in w.get(*p):
                        w.set(*p, B.glass("light_blue"))
            else:
                fx, _, fz = DIR_VEC[facing]
                reach = {1: 2, 2: 3, 3: 4}[pw]
                for k in range(reach + 1):
                    dy = int(round(0.5 * k - 0.35 * k * k / max(1, reach) * 1.6))
                    p = (x + fx * k, y + dy, z + fz * k)
                    if w.is_empty(*p) or "fountain_spray" in w.get(*p):
                        w.set(*p, B.glass("light_blue"))


# ====================================================================================== main
def build():
    from capital import build as BLD
    t0 = time.time()
    w, T = BLD.build_world(log=lambda *a: None)
    print(f"built in {time.time() - t0:.1f}s, non-air {w.count_nonair():,}, clipped {w.clipped_writes}")
    return w, T


def main(argv=None):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="?", default="")
    ap.add_argument("--scale", type=float, default=0.5)
    ap.add_argument("--raw", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--map", action="store_true")
    ap.add_argument("--slice", default="")
    a = ap.parse_args(argv)
    vs = views()
    vs.update(district_views())
    if a.list:
        print("\n".join(sorted(vs)))
        return 0
    w, T = build()
    check_gimmicks(w)
    if a.check:
        return 0
    if a.slice:
        for item in a.slice.split(","):
            nm, _, yy = item.partition(":")
            d = L.DISTRICTS[nm]
            x0, z0, x1, z1 = d.bbox()
            print(P.plan_slice(w, int(yy or 1), (x0, z0, x1, z1), name=f"slice_{nm}_{yy}", scale=12))
    if not a.raw:
        dress(w)
    if a.map:
        P.topdown(w, "map_top", scale=3)
    names = [n for n in a.names.split(",") if n]
    if not names:
        return 0
    scene = P.Scene(w)
    for n in names:
        if n not in vs:
            print("unknown view", n)
            continue
        P.render(scene, n, scale=a.scale, **vs[n])
    return 0


if __name__ == "__main__":
    sys.exit(main())
