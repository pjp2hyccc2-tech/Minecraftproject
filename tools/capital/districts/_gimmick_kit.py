"""Gimmick kit: placement helpers for the canal town decorations drawn by the client.

    from capital.districts import _gimmick_kit as GK
    GK.bunting(t, x, y, z, to=(0, 0, 9), style="pennant", colors=("red", "yellow", "blue"), sag=1.2)
    GK.bunting_chain(t, [(x0, y, z0), (x1, y, z1), (x2, y, z2)], style="lanterns")      # zigzag over a canal
    GK.secret_door(t, x, y, z, facing="south", look="bookshelf")
    GK.shop_sign(t, x, y, z, facing="east", icon="potion", attach="wall")
    GK.floating_lantern(t, x, y, z)                                                    # on the canal at night
    GK.canal_lanterns(t, x0, z0, x1, z1, water_y, every=3.5)

Targets are a ``World`` or a ``Frame`` (states, positions and the bunting's offset are rotated/mirrored with the frame;
the block entity data is always written in world terms). See docs/modules/capital.md "Moving decorations" for what
each block looks like, what must stay free around it and how ``_town_views.py --check`` verifies that.

Files starting with "_" are not run as districts. The numbers here match the Java side (``world/town``:
``BuntingLine``, ``BuntingStyle``) so the checks agree with what the game draws.
"""
from __future__ import annotations

import math

from capital import blocks as B

# ====================================================================================== bunting
BUNTING_STYLES = ("pennant", "flags", "lanterns", "laundry", "garland")
#: how far the pieces hang below the cord (BuntingStyle.hang)
BUNTING_HANG = {"pennant": 0.62, "flags": 0.45, "lanterns": 0.6, "laundry": 0.8, "garland": 0.18}
#: colours used when none are given (BuntingStyle.defaultColors), as dye names
BUNTING_COLORS = {
    "pennant": ("red", "yellow", "blue", "white", "green"),
    "flags": ("blue", "white", "red"),
    "lanterns": ("orange", "red", "yellow", "pink"),
    "laundry": ("white", "light_blue", "light_gray", "yellow", "pink", "blue"),
    "garland": ("pink", "white", "yellow", "light_blue"),
}
MAX_REACH = 32          # each of Tx, Ty, Tz
MAX_SAG = 8.0
END_MARGIN = 0.32       # bare cord next to each hook (BuntingLayout.END_MARGIN)
H4 = ("north", "south", "east", "west")


def _to_world(t, x, y, z):
    """World position of a local position (a World is its own frame)."""
    if hasattr(t, "pos"):
        return t.pos(x, y, z)
    return int(x), int(y), int(z)


def _dye(c) -> int:
    if isinstance(c, str):
        if c not in B.DYE:
            raise ValueError(f"unknown dye colour {c!r} (use one of {', '.join(B.COLORS)})")
        return B.DYE[c]
    i = int(c)
    if not 0 <= i <= 15:
        raise ValueError(f"dye id {c!r} out of 0..15")
    return i


def _horizontal(dx, dz) -> str:
    """The horizontal direction closest to (dx, dz)."""
    if abs(dx) >= abs(dz):
        return "east" if dx >= 0 else "west"
    return "south" if dz >= 0 else "north"


def bunting_nbt(dx, dy, dz, sag=1.2, style="pennant", colors=None, wind=1.0) -> str:
    """Block-entity SNBT of a string to the block (dx, dy, dz) away, in WORLD terms."""
    if style not in BUNTING_STYLES:
        raise ValueError(f"unknown bunting style {style!r} (use one of {', '.join(BUNTING_STYLES)})")
    ids = [_dye(c) for c in (colors if colors else BUNTING_COLORS[style])]
    return (f"{{Tx:{int(dx)},Ty:{int(dy)},Tz:{int(dz)},Sag:{float(sag):.2f}f,Style:{B.snbt_str(style)},"
            f"Colors:[I;{','.join(str(i) for i in ids)}],Wind:{float(wind):.2f}f}}")


def bunting(t, x, y, z, to=(8, 0, 0), style="pennant", colors=None, sag=1.2, wind=1.0, facing="east"):
    """A string of bunting from the anchor at (x, y, z) to the block ``to`` = (dx, dy, dz) away (local terms).

    The anchor is a small iron eye: put it in the air cell in front of a wall (``facing`` = away from the wall), on a
    post top or under an eave; it finds what it is fixed to by itself. The cord runs from the anchor's centre to the
    centre of the far block (put that in the air too: a small hook is drawn there, fixed to the wall beyond it, the
    post below or the eave above, or to another anchor), sagging ``sag`` blocks at mid-span (0..8, at most the
    horizontal span). ``style``: pennant, flags, lanterns (lit at night), laundry or garland; ``colors``: dye names or
    ids taken in turn (None: the style's own mix); ``wind`` scales the town wind (0..3; 0.3 under arcades).
    ``facing=None`` points the anchor along the string. Each of dx, dy, dz is at most 32. Returns the far end's
    position in local terms.
    """
    dx, dy, dz = (int(v) for v in to)
    ax, ay, az = _to_world(t, x, y, z)
    bx, by, bz = _to_world(t, x + dx, y + dy, z + dz)
    wdx, wdy, wdz = bx - ax, by - ay, bz - az
    if (wdx, wdy, wdz) == (0, 0, 0):
        raise ValueError("bunting needs a far end (to != (0, 0, 0)); use bunting_hook() for a bare hook")
    if max(abs(wdx), abs(wdy), abs(wdz)) > MAX_REACH:
        raise ValueError(f"bunting reaches at most {MAX_REACH} blocks along each axis, got {to}")
    if not 0.0 <= float(sag) <= MAX_SAG:
        raise ValueError(f"bunting sag must be 0..{MAX_SAG}, got {sag}")
    if not 0.0 <= float(wind) <= 3.0:
        raise ValueError(f"bunting wind must be 0..3, got {wind}")
    if facing is None or facing == "auto":
        facing = _horizontal(dx, dz) if (dx, dz) != (0, 0) else "north"
    if facing not in H4:
        raise ValueError(f"bad facing {facing!r}")
    t.set(x, y, z, B.S("bunting", facing=facing))
    t.be(x, y, z, bunting_nbt(wdx, wdy, wdz, sag, style, colors, wind))
    return x + dx, y + dy, z + dz


def bunting_hook(t, x, y, z, facing="east"):
    """A bare iron eye (an anchor without a string of its own), e.g. where two strings meet on a post."""
    t.set(x, y, z, B.S("bunting", facing=facing))
    t.be(x, y, z, "{Tx:0,Ty:0,Tz:0}")


def default_sag(span: float) -> float:
    """A pleasant sag for a horizontal span (what the block uses when placed by hand)."""
    return round(0.25 + 0.06 * span, 2)


def bunting_chain(t, points, style="pennant", colors=None, sag=None, wind=1.0):
    """Strings through consecutive points: an anchor at every point but the last, each strung to the next (zigzags
    across a street or along a canal; corners can sit on posts). ``sag`` None: :func:`default_sag` per span."""
    pts = [tuple(int(v) for v in p) for p in points]
    if len(pts) < 2:
        raise ValueError("bunting_chain needs at least two points")
    for (x0, y0, z0), (x1, y1, z1) in zip(pts, pts[1:]):
        span = math.hypot(x1 - x0, z1 - z0)
        bunting(t, x0, y0, z0, to=(x1 - x0, y1 - y0, z1 - z0), style=style, colors=colors,
                sag=default_sag(span) if sag is None else sag, wind=wind, facing=None)
    return pts


# ---------------------------------------------------------------------------------------- the cord (as in Java)
def _asinh(v):
    return math.copysign(math.log(abs(v) + math.sqrt(v * v + 1.0)), v)


def _sag_of(span, rise, a):
    h = span / (2.0 * a)
    q = rise / (2.0 * a * math.sinh(h))
    return a * math.sqrt(1.0 + q * q) * (math.cosh(h) - 1.0)


def bunting_curve(dx, dy, dz, sag, step=0.1):
    """Points along the rest cord (relative to the anchor's centre) every ``step`` blocks of arc length, and the cord's
    sag actually used (``BuntingLine``: clamped to 0..8 and to the horizontal span, straight if flatter than 0.01 or
    nearly vertical)."""
    span = math.hypot(dx, dz)
    chord = math.sqrt(dx * dx + dy * dy + dz * dz)
    hx, hz = (dx / span, dz / span) if span > 1e-9 else (1.0, 0.0)
    want = min(max(float(sag), 0.0), min(MAX_SAG, span))
    if span < 0.25 or want < 0.01:
        n = max(1, int(math.ceil(chord / step)))
        return [(dx * i / n, dy * i / n, dz * i / n) for i in range(n + 1)], 0.0
    lo, hi = math.log(span * 1e-3), math.log(span * 1e7)
    for _ in range(90):
        mid = 0.5 * (lo + hi)
        if _sag_of(span, dy, math.exp(mid)) > want:
            lo = mid
        else:
            hi = mid
    a = math.exp(0.5 * (lo + hi))
    h = span / (2.0 * a)
    mu = _asinh(dy / (2.0 * a * math.sinh(h)))
    x0 = span / 2.0 - a * mu
    c = -a * math.cosh(x0 / a)
    s0 = math.sinh(-x0 / a)
    length = a * (math.sinh((span - x0) / a) - s0)
    n = max(1, int(math.ceil(length / step)))
    pts = []
    for i in range(n + 1):
        s = length * i / n
        x = x0 + a * _asinh(s / a + s0)
        y = a * math.cosh((x - x0) / a) + c
        pts.append((hx * x, y, hz * x))
    return pts, _sag_of(span, dy, a)


def parse_bunting(snbt: str) -> dict:
    """The fields of a bunting SNBT written by :func:`bunting_nbt` (defaults like the block entity's)."""
    import re
    out = {"Tx": 0, "Ty": 0, "Tz": 0, "Sag": 1.2, "Style": "pennant", "Colors": [], "Wind": 1.0}
    for k in ("Tx", "Ty", "Tz"):
        m = re.search(k + r":(-?\d+)", snbt or "")
        if m:
            out[k] = max(-MAX_REACH, min(MAX_REACH, int(m.group(1))))
    for k in ("Sag", "Wind"):
        m = re.search(k + r":(-?[0-9.]+)f?", snbt or "")
        if m:
            out[k] = float(m.group(1))
    m = re.search(r'Style:"([a-z_]+)"', snbt or "")
    if m:
        out["Style"] = m.group(1) if m.group(1) in BUNTING_STYLES else "pennant"
    m = re.search(r"Colors:\[I;([-0-9,\s]*)\]", snbt or "")
    if m and m.group(1).strip():
        out["Colors"] = [int(v) for v in m.group(1).split(",") if v.strip()]
    return out


def bunting_cells(anchor, snbt):
    """Cells the string passes through or its pieces hang in, at rest (``anchor`` = the anchor block, world terms),
    without the anchor's and the far hook's own cells. Returns (cells, far_cell)."""
    d = parse_bunting(snbt)
    ax, ay, az = anchor
    far = (ax + d["Tx"], ay + d["Ty"], az + d["Tz"])
    if far == (ax, ay, az):
        return set(), far
    pts, _ = bunting_curve(d["Tx"], d["Ty"], d["Tz"], d["Sag"], 0.1)
    hang = BUNTING_HANG.get(d["Style"], 0.6)
    cells = set()
    total = len(pts) - 1
    step = sum(math.dist(pts[i], pts[i + 1]) for i in range(total)) / max(1, total)
    for i, (px, py, pz) in enumerate(pts):
        wx, wy, wz = ax + 0.5 + px, ay + 0.5 + py, az + 0.5 + pz
        cells.add((math.floor(wx), math.floor(wy), math.floor(wz)))
        if min(i, total - i) * step < END_MARGIN:
            continue                    # no pieces on the bare cord next to each hook
        k = 0.25
        while k <= hang + 1e-9:
            cells.add((math.floor(wx), math.floor(wy - k), math.floor(wz)))
            k += 0.25
    cells.discard((ax, ay, az))
    cells.discard(far)
    return cells, far


# ====================================================================================== secret door
SECRET_LOOKS = B.SCHEMAS["secret_door"]["look"]


def secret_door(t, x, y, z, facing, look):
    """A two-high secret door at (x, y, z) and (x, y + 1, z) that looks exactly like the wall it stands in.

    ``facing`` = the side it is found from (its front, n/s/e/w); the passage lies behind. ``look`` must match the
    wall: bookshelf, stone_bricks, castle_bricks, white_plaster, mossy_stone_bricks, dark_oak_planks, spruce_planks.
    Using it slides the panel one block sideways into the wall (to the right seen from the front, or to the left if
    only there is wall), so keep a solid full block on at least one side, on both levels (the same look, ideally).
    Leave the doorway's front and back free. It closes again after five seconds once nobody stands in it."""
    if look not in SECRET_LOOKS:
        raise ValueError(f"unknown secret door look {look!r} (use one of {', '.join(SECRET_LOOKS)})")
    if facing not in H4:
        raise ValueError(f"bad facing {facing!r}")
    t.set(x, y, z, B.S("secret_door", facing=facing, half="lower", open=False, look=look))
    t.set(x, y + 1, z, B.S("secret_door", facing=facing, half="upper", open=False, look=look))


# ====================================================================================== shop sign
SHOP_ICONS = B.SCHEMAS["shop_sign"]["icon"]
SHOP_ATTACH = B.SCHEMAS["shop_sign"]["attach"]


def shop_sign(t, x, y, z, facing, icon, attach="wall"):
    """A hanging shop board with a painted picture of the trade, swinging gently in the wind.

    ``attach="wall"``: (x, y, z) is the air cell in front of a wall and ``facing`` points away from the wall; a
    wrought-iron bracket runs along the top of the cell and the board (1 x 0.75 blocks) hangs under it edge-on to
    the wall, its lower edge 1.5 pixels into the cell below. ``attach="ceiling"``: the board hangs on two short
    chains from the block above, its picture facing ``facing`` (and its back the other way). ``icon``: sword,
    shield, potion, bread, anvil, book, gem, flower, shirt, key, fish, mug, scroll, map, hammer, ring."""
    if icon not in SHOP_ICONS:
        raise ValueError(f"unknown shop sign icon {icon!r} (use one of {', '.join(SHOP_ICONS)})")
    if attach not in SHOP_ATTACH:
        raise ValueError(f"bad attach {attach!r}")
    if facing not in H4:
        raise ValueError(f"bad facing {facing!r}")
    t.set(x, y, z, B.S("shop_sign", facing=facing, icon=icon, attach=attach))


# ====================================================================================== floating lantern
def floating_lantern(t, x, y, z):
    """A candle lantern floating on a canal: (x, y, z) is the AIR block right above the water surface block. It bobs,
    rocks, turns and drifts within its block, gives light 9 day and night, and after dusk (always, under a roof or
    underground) its paper glows with a halo and a shimmer of light on the water. Boats pass through it."""
    t.set(x, y, z, B.S("floating_lantern"))


def canal_lanterns(t, x0, z0, x1, z1, water_y, every=3.5, seed=0):
    """Scatter floating lanterns over the open water of a rectangle whose water surface blocks are at ``water_y``:
    about one per ``every`` x ``every`` blocks, never two side by side, only where the block above the water is air.
    Returns the positions (local terms)."""
    from capital.noise import hash2
    x0, x1 = sorted((int(x0), int(x1)))
    z0, z1 = sorted((int(z0), int(z1)))
    placed = []
    chance = 1.0 / max(1.0, float(every)) ** 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if float(hash2(x, z, 7919 + int(seed))) >= chance:
                continue
            if not t.get(x, water_y, z).startswith("minecraft:water") or not t.is_empty(x, water_y + 1, z):
                continue
            if any(abs(px - x) <= 1 and abs(pz - z) <= 1 for px, _, pz in placed):
                continue
            floating_lantern(t, x, water_y + 1, z)
            placed.append((x, water_y + 1, z))
    return placed
