"""Connection shapes, computed the way Minecraft 1.20.1 computes them on a neighbour update.

World generation places blocks without neighbour updates (and a piece may cross chunk borders), so every fence,
wall, pane/iron bars, stair corner and fence gate ``in_wall`` is finalised here from its neighbours: the piece's own
blocks where it sets them, the real terrain elsewhere (solid below the terrain top, water up to the water top).
"""
from __future__ import annotations

import numpy as np

from capital import blocks as B
from capital.colors import material

DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
EXCEPTIONS = ("leaves", "barrier", "carved_pumpkin", "jack_o_lantern", "melon", "pumpkin", "shulker_box")
POST_OVERRIDE = ("torch", "soul_torch", "redstone_torch", "lantern", "soul_lantern", "_banner", "_sign",
                 "pressure_plate", "flag", "end_rod", "lightning_rod", "candle", "skull", "head")


def _name(state):
    return B.parse(state)[0].split(":", 1)[1]


def _schema(state):
    return B.REGISTRY.get(B.parse(state)[0], "plain")


def _is_exception(state):
    n = _name(state)
    return n.endswith("leaves") or any(n == e or n.endswith("_" + e) for e in EXCEPTIONS)


def sturdy_towards(state, towards):
    """Whether ``state``'s face pointing in direction ``towards`` is a full sturdy face."""
    sch = _schema(state)
    n = _name(state)
    if n in ("air", "cave_air", "void", "water", "lava", "light"):
        return False
    props = B.parse(state)[1]
    if sch == "stairs":
        return props.get("facing") == towards
    if sch == "slab":
        return props.get("type") == "double"
    m = material(state)
    if m["kind"] == "solid" and m["box"] is None:
        return True
    if m["kind"] == "glass" and _schema(state) == "plain":          # glass and stained glass blocks
        return True
    return False


def _fence_family(state):
    n = _name(state)
    if _schema(state) != "fence":
        return None
    return "nether" if n == "nether_brick_fence" else "wood"


def _gate_connects(state, direction):
    return _schema(state) == "fence_gate" and B.parse(state)[1].get("facing") in (CW[direction], CCW[direction])


def finalize(P, log=None):
    """Recompute connection properties of every shaped block in piece ``P`` (in place)."""
    pal = P.palette
    sch = [B.REGISTRY.get(B.parse(s)[0], "plain") if i > 1 else "" for i, s in enumerate(pal)]
    shaped = np.array([s in ("fence", "wall", "pane", "stairs", "fence_gate") for s in sch], bool)
    if not shaped.any():
        return 0
    cells = np.argwhere(shaped[P.a])
    changed = 0

    def eff(x, y, z):
        return P.effective(x, y, z)

    # walls depend on what is above (walls above first: top-down order), stairs on each other (two passes)
    order = sorted(map(tuple, cells), key=lambda c: -c[2])
    for _pass in range(2):
        for (i, j, k) in order:
            x, y, z = i + P.X0, k + P.Y0, j + P.Z0
            st = P.palette[P.a[i, j, k]]
            s = _schema(st)
            bid, props = B.parse(st)
            new = dict(props)
            if s == "fence":
                fam = _fence_family(st)
                for d, (dx, dz) in DIRS.items():
                    nb = eff(x + dx, y, z + dz)
                    ok = (_fence_family(nb) == fam) or _gate_connects(nb, d) or \
                        (not _is_exception(nb) and sturdy_towards(nb, OPP[d]))
                    new[d] = "true" if ok else "false"
            elif s == "pane":
                for d, (dx, dz) in DIRS.items():
                    nb = eff(x + dx, y, z + dz)
                    ok = _schema(nb) in ("pane", "wall") or (not _is_exception(nb) and sturdy_towards(nb, OPP[d]))
                    new[d] = "true" if ok else "false"
            elif s == "wall":
                above = eff(x, y + 1, z)
                above_s = _schema(above)
                above_p = B.parse(above)[1]
                for d, (dx, dz) in DIRS.items():
                    nb = eff(x + dx, y, z + dz)
                    ok = _schema(nb) == "wall" or _schema(nb) == "pane" or _gate_connects(nb, d) or \
                        (not _is_exception(nb) and sturdy_towards(nb, OPP[d]))
                    if not ok:
                        new[d] = "none"
                        continue
                    tall = sturdy_towards(above, "down") or (above_s == "wall" and above_p.get(d, "none") != "none")
                    new[d] = "tall" if tall else "low"
                n_, s_, e_, w_ = (new[d] != "none" for d in ("north", "south", "east", "west"))
                if above_s == "wall" and above_p.get("up") == "true":
                    up = True
                elif (not n_ and not s_ and not e_ and not w_) or (n_ != s_) or (e_ != w_):
                    up = True
                elif (new["north"] == "tall" and new["south"] == "tall") or (new["east"] == "tall" and
                                                                              new["west"] == "tall"):
                    up = False
                else:
                    an = _name(above)
                    up = any(an.endswith(p) or an == p.strip("_") for p in POST_OVERRIDE) or \
                        sturdy_towards(above, "down")
                new["up"] = "true" if up else "false"
            elif s == "fence_gate":
                f = props.get("facing", "north")
                in_wall = False
                for d in (CW[f], CCW[f]):
                    dx, dz = DIRS[d]
                    if _schema(eff(x + dx, y, z + dz)) == "wall":
                        in_wall = True
                new["in_wall"] = "true" if in_wall else "false"
            elif s == "stairs":
                new["shape"] = _stair_shape(eff, x, y, z, props)
            if new != props:
                ns = B.canon(B.fmt(bid, new))
                P.a[i, j, k] = P.idx(ns)
                changed += 1
    if log:
        log(f"  connections: {len(order)} shaped blocks, {changed} updated")
    return changed


def _stair_props(state):
    if _schema(state) != "stairs":
        return None
    return B.parse(state)[1]


def _stair_shape(eff, x, y, z, props):
    f = props.get("facing", "north")
    half = props.get("half", "bottom")
    dx, dz = DIRS[f]
    front = _stair_props(eff(x + dx, y, z + dz))          # the block behind the tall side ("facing" side)
    if front and front.get("half", "bottom") == half:
        f1 = front.get("facing", "north")
        if _axis(f1) != _axis(f) and _can_take(eff, x, y, z, props, OPP[f1]):
            return "outer_left" if f1 == CCW[f] else "outer_right"
    bx, bz = DIRS[OPP[f]]
    back = _stair_props(eff(x + bx, y, z + bz))
    if back and back.get("half", "bottom") == half:
        f2 = back.get("facing", "north")
        if _axis(f2) != _axis(f) and _can_take(eff, x, y, z, props, f2):
            return "inner_left" if f2 == CCW[f] else "inner_right"
    return "straight"


def _axis(d):
    return "x" if d in ("east", "west") else "z"


def _can_take(eff, x, y, z, props, face):
    dx, dz = DIRS[face]
    o = _stair_props(eff(x + dx, y, z + dz))
    return o is None or o.get("facing") != props.get("facing") or o.get("half", "bottom") != props.get("half",
                                                                                                         "bottom")
