"""The named weapons of the armory: 189 sprites, 32x32, drawn along the diagonal like weapons.py.

Every design is built from parts of its weapon type (blade or head shape, guard, grip, pommel, shaft, limbs) and its
own materials and marks; the table SPECS (named_weapon_specs.py) gives each design its parts, so neighbours never
share a silhouette and colour scheme. Twin blades get a single held blade and the crossed pair for the icon.
"""
from __future__ import annotations

import math

import numpy as np

import palette as P
from px import Canvas, Ramp, mix, hexc
from wbase import (N, Sprite, am, pos, disc, disc_ts, shade_blade, shade_bar, shade_ball, shade_shaft, wrap_band,
                   gem, sparkle, outline, poly_ts, sweep_ts, curve)
from weapons import (put, grip_layer, pommel_disc, cross_guard, runes, star4, orb, shaft_layer, collar, limb_path,
                     limb_widths, string_layer, limb_spike, BOW_TIP, STRING_S)

# --------------------------------------------------------------------------- extra materials
JADE = Ramp("06261a", "0f5a3a", "1b8a5a", "2fb87a", "6ee0a8", "d2ffe8")
CORAL = Ramp("3a0e14", "8a2a30", "c24a48", "e8786a", "f8a898", "fff0e8")
PEARL = Ramp("2e2a3a", "8a8aa8", "b8b8d0", "dcdcec", "f2f2fa", "ffffff")
RUST = Ramp("241008", "5a2410", "8a3c18", "b05a28", "d07e46", "eaa878")
AMBER = Ramp("2e1404", "7a3c08", "b2620c", "e08e1a", "f8bc4a", "fff0a0")
THUNDER = Ramp("2a2406", "7a6a10", "bfa81a", "eedc3a", "fff27a", "ffffe0")
MOONSILVER = Ramp("161a2e", "4a5478", "7a86b0", "a9b4d8", "d4dcf4", "ffffff")
DARKWOOD = Ramp("120a06", "2a1a10", "40281a", "583824", "704a30", "8c6040")
NIGHT = Ramp("05060c", "10141e", "1c2232", "2a3246", "3c465e", "5a6680")
SUNSTONE = Ramp("3a1806", "8a3e0e", "c8661a", "f09a34", "ffc866", "fff0c0")
COPPER = Ramp("2a1208", "6a2e14", "9a4a22", "c46c34", "e0965a", "f6c69a")
STRAW = Ramp("3a2c0c", "7a6220", "a88a34", "d2b44e", "eedc7e", "fff6c6")
INK = Ramp("06040c", "140e22", "221838", "33264e", "4a3a6a", "6a5a8a")
SEA = Ramp("061a2a", "0e3a5a", "16608a", "2a8ab4", "5ab8da", "b4ecff")
ROSE = Ramp("2a0818", "6a1640", "a02a62", "d04a8a", "f082b4", "ffd0e6")
LIME = Ramp("142606", "2e5a0c", "4a8a14", "6eba22", "a2e04a", "e2ffb0")
ASH = Ramp("16141a", "3a363e", "5a5560", "7c7682", "a09aa6", "cac4ce")
CRIMSON_STEEL = Ramp("1c060a", "4a1018", "7a1c26", "a83038", "d05a5e", "f2a0a0")
NAVY = Ramp("060a1c", "101a3a", "1a2a5a", "2a3a7a", "4a5aa0", "7a8ac8")
TEAL_STEEL = Ramp("061a1e", "15434a", "236c74", "3a969c", "6cc4c6", "c8f4f2")
VIOLET = Ramp("12061e", "321050", "521c80", "7430b0", "a060dc", "dcbcff")

MATERIALS = {name: value for name, value in vars(P).items() if isinstance(value, Ramp)}
MATERIALS.update({name: value for name, value in globals().items() if isinstance(value, Ramp)})

#: element → glow ramp (gems, runes, edges)
ELEMENT_GLOW = {"FIRE": P.FLAME, "FROST": P.ICE, "THUNDER": THUNDER, "VENOM": P.SLIME, "HOLY": P.HOLY_GOLD,
                "SHADOW": P.VOID_GLOW, "BLOOD": P.BLOOD, "EARTH": AMBER, "NONE": P.SKY}


def mat(name):
    if name is None or isinstance(name, Ramp):
        return name
    return MATERIALS[name]


# =========================================================================== blades
def blade_mask(style: str, base: int, tip: int, half: float):
    """Mask and centre line of a blade from t = base to the tip (axis space, s across)."""
    length = tip - base

    def u_of(t):
        return (t - base) / max(1.0, length)

    center = lambda t: 0.0
    if style == "straight":
        m = am(lambda t, s: base <= t and abs(s) <= half and t <= tip - 2.0 * abs(s))
    elif style == "slim":
        m = am(lambda t, s: base <= t and abs(s) <= max(1, half - 1) and t <= tip - 3.0 * abs(s))
    elif style == "broad":
        m = am(lambda t, s: base <= t and abs(s) <= half + 1 and t <= tip - 1.4 * abs(s))
    elif style == "leaf":
        def leaf(t, s):
            u = u_of(t)
            h = half + (1.0 if 0.45 < u < 0.85 else 0.0)
            return base <= t and abs(s) <= h and t <= tip - 1.6 * abs(s)
        m = am(leaf)
    elif style in ("saber", "scimitar", "katana"):
        bend = {"saber": 3.0, "scimitar": 4.5, "katana": 2.4}[style]
        center = lambda t: bend * u_of(t) ** 2
        widen = 1.0 if style == "scimitar" else 0.0

        def curved(t, s):
            u = u_of(t)
            c = bend * u ** 2
            h = half - (1 if style == "katana" else 0) + widen * u
            return base <= t and abs(s - c) <= h and t <= tip - 2.2 * abs(s - c) + (1 if style == "scimitar" else 0)
        m = am(curved)
    elif style == "falchion":
        def falchion(t, s):
            u = u_of(t)
            h = half + 1.2 * u
            back = -half
            return base <= t and back <= s <= h and t <= tip - 1.2 * max(0.0, s) + 0.0 * s and \
                t <= tip + 2 - 3.0 * max(0.0, -s)
        m = am(falchion)
    elif style == "cleaver":
        m = am(lambda t, s: base <= t <= tip - 1 and -half <= s <= half + 1 and t <= tip + 1 - 0.8 * (s + half))
    elif style == "khopesh":
        path = [(base, 0.0), (base + length * 0.45, 0.0), (base + length * 0.8, -1.5), (tip - 1, 2.5),
                (tip - 4, 5.5)]
        widths = [half, half, half + 0.6, half, 0.6]
        pts = []
        for i in range(len(path) - 1):
            seg = curve(path[i], ((path[i][0] + path[i + 1][0]) / 2, (path[i][1] + path[i + 1][1]) / 2),
                        path[i + 1], 4)
            pts += seg if i == 0 else seg[1:]
        ws = list(np.interp(np.linspace(0, len(widths) - 1, len(pts)), range(len(widths)), widths))
        m = sweep_ts(pts, ws)
        center = lambda t: 0.0
    elif style == "flame":
        def wavy(t, s):
            h = half + (1 if math.sin((t - base) * 0.85) > 0.2 else 0)
            return base <= t and abs(s) <= h and t <= tip - 2.0 * abs(s)
        m = am(wavy)
    elif style == "serrated":
        def teeth(t, s):
            if base <= t and abs(s) <= half and t <= tip - 2.0 * abs(s):
                return True
            return base + 2 <= t <= tip - 6 and s == half + 1 and (t - base) % 3 == 0
        m = am(teeth)
    elif style == "crystal":
        def facets(t, s):
            u = u_of(t)
            h = half + (1 if (t - base) % 6 in (2, 3) and u < 0.8 else 0)
            return base <= t and abs(s) <= h and t <= tip - 1.5 * abs(s)
        m = am(facets)
    elif style == "fang":
        center = lambda t: -2.6 * u_of(t) ** 1.6

        def fang(t, s):
            c = -2.6 * u_of(t) ** 1.6
            h = half + 0.8 * (1 - u_of(t))
            return base <= t and abs(s - c) <= h and t <= tip - 1.8 * abs(s - c)
        m = am(fang)
    elif style == "slab":
        m = am(lambda t, s: base <= t <= tip - 2 and abs(s) <= half + 1 and t <= tip - 0.5 * abs(s))
    elif style == "jagged":
        def jag(t, s):
            if base <= t and abs(s) <= half and t <= tip - 1.8 * abs(s):
                return True
            k = (t - base) % 5
            return base + 3 <= t <= tip - 5 and ((s == half + 1 and k in (0, 1)) or (s == -half - 1 and k == 3))
        m = am(jag)
    elif style == "executioner":
        hole = disc_ts(tip - 6, 0.5, 1.6)

        def heads(t, s):
            return base <= t <= tip - 1 and -half - 1 <= s <= half + 1 and t <= tip + 2 - 0.9 * (s + half + 1)
        m = am(heads) & ~hole
    elif style == "needle":
        m = am(lambda t, s: base <= t and abs(s) <= max(0, half - 1) and t <= tip)
    elif style == "kris":
        center = lambda t: 0.9 * math.sin((t - base) * 0.75)

        def kris(t, s):
            c = 0.9 * math.sin((t - base) * 0.75)
            return base <= t and abs(s - c) <= half and t <= tip - 2.0 * abs(s - c)
        m = am(kris)
    elif style == "sickle":
        center = lambda t: 4.0 * u_of(t) ** 2.2

        def sickle(t, s):
            c = 4.0 * u_of(t) ** 2.2
            return base <= t and abs(s - c) <= half and t <= tip - 1.5 * abs(s - c) + 1
        m = am(sickle)
    elif style == "kukri":
        center = lambda t: -1.8 * math.sin(math.pi * u_of(t))

        def kukri(t, s):
            u = u_of(t)
            c = -1.8 * math.sin(math.pi * u)
            h = half + 0.9 * math.sin(math.pi * min(1.0, u * 1.2))
            return base <= t and abs(s - c) <= h and t <= tip - 1.8 * abs(s - c)
        m = am(kukri)
    elif style == "claw":
        # a wolf's claw: thick at the guard, hooking hard towards the tip
        center = lambda t: 9.5 * min(1.0, max(0.0, u_of(t))) ** 2.1

        def claw(t, s):
            u = min(1.0, max(0.0, u_of(t)))
            c = 9.5 * u ** 2.1
            h = 0.6 + (half + 1.2) * (1 - u) ** 0.8
            return base <= t and abs(s - c) <= h and t <= tip - 1.2 * abs(s - c) + 1
        m = am(claw)
    elif style == "segmented":
        # a centipede's body: plates with notches between them and a leg spur on every plate
        def seg(t, s):
            k = (t - base) % 4
            h = half - (1 if k == 0 and t > base + 1 else 0)
            if base <= t and abs(s) <= h and t <= tip - 2.0 * abs(s):
                return True
            return base + 2 <= t <= tip - 5 and abs(s) == half + 1 and k == 2
        m = am(seg)
    elif style == "fin":
        # a lake serpent's fin: a straight edge in front, spines along the back
        def fin(t, s):
            if base <= t and -half <= s <= half and t <= tip - 2.0 * abs(s):
                return True
            k = (t - base) % 4
            spine = (s == half + 1 and k in (0, 1, 2)) or (s == half + 2 and k in (0, 1)) or (s == half + 3 and k == 0)
            return base + 2 <= t <= tip - 4 and spine and t <= tip - 1.2 * s
        m = am(fin)
    elif style == "pincer":
        # a crab's claw: a heavy trunk splitting into a long fixed finger and a shorter hooked one
        split = base + int(length * 0.55)
        m = am(lambda t, s: base <= t <= split and abs(s) <= half + 1 and t <= split + 2 - 0.3 * abs(s))
        upper = curve((split - 1, half - 0.5), (split + (tip - split) * 0.55, half + 2.6), (tip, 0.6), 8)
        lower = curve((split - 1, -half + 0.5), (split + (tip - split) * 0.45, -half - 1.8), (tip - 4, -1.2), 8)
        m |= sweep_ts(upper, list(np.linspace(half - 0.2, 0.45, len(upper))))
        m |= sweep_ts(lower, list(np.linspace(half - 0.6, 0.45, len(lower))))
    elif style == "flared":
        # an idol's ceremonial blade: narrow at the guard, flaring wide before a short point
        def flared(t, s):
            u = u_of(t)
            h = half - 0.6 + 2.0 * u ** 1.4 if u < 0.82 else (half + 1.4) * (1 - u) / 0.18 + 0.4
            return base <= t and abs(s) <= max(0.6, h) and t <= tip - 1.1 * abs(s)
        m = am(flared)
    elif style == "feather":
        # a storm bird's feather: a quill down the middle, vanes notched along both sides
        def feather(t, s):
            u = u_of(t)
            h = half + 0.9 * math.sin(math.pi * min(1.0, u * 1.08))
            if not (base <= t and abs(s) <= h and t <= tip - 1.5 * abs(s)):
                return False
            return not (abs(s) >= h - 0.5 and (t - base) % 3 == 0 and 0.12 < u < 0.86)
        m = am(feather)
    else:
        raise ValueError(style)
    return m, center


def paint_blade(sp: Sprite, m, center, metal: Ramp, fuller=None, edge=None, veins=None, base=0, tip=28,
                ridge=None, band=None):
    """Shades a blade; ``fuller`` darkens the centre line, ``edge`` lights the cutting edge, ``veins`` draws glowing
    cracks, ``band`` = (t0, t1, ramp) colours a cross band (e.g. a gold collar near the guard)."""
    layer = sp.layer()

    def extra(t, s, tone):
        rel = s - center(t)
        if band and band[0] <= t <= band[1]:
            return band[2][max(1, min(5, tone + 1))]
        if veins is not None and base + 3 <= t <= tip - 4 and abs(rel) <= 1:
            if (t * 7 + int(round(rel)) * 3) % 9 in (0, 4):
                return veins[4]
        if fuller is not None and base + 2 <= t <= tip - 6 and abs(rel) < 0.6:
            return fuller if isinstance(fuller, tuple) else metal[1]
        if ridge is not None and base + 1 <= t <= tip - 2 and abs(rel) < 0.6:
            return ridge
        if edge is not None and tone == 2:
            return edge[4]
        return None
    shade_blade(layer, m, metal, center=center, extra=extra)
    outline(layer, metal)
    return layer


# =========================================================================== guards and pommels
def guard(sp: Sprite, kind: str, trim: Ramp, tg: int, reach: int = 6, gem_ramp=None):
    if kind == "none":
        return
    if kind == "bar":
        cross_guard(sp, trim, reach=reach, tg=tg)
    elif kind == "crescent_up":
        cross_guard(sp, trim, reach=reach, tg=tg, bend=lambda a: int(round(0.08 * a * a)))
    elif kind == "crescent_down":
        cross_guard(sp, trim, reach=reach, tg=tg, bend=lambda a: -int(round(0.08 * a * a)))
    elif kind == "short":
        cross_guard(sp, trim, reach=max(3, reach - 2), tg=tg, thick=1)
    elif kind == "tsuba":
        g = sp.layer()
        gm = am(lambda t, s: abs(t - tg) <= 1 and abs(s) <= 3 - (1 if abs(t - tg) == 1 else 0))
        shade_bar(g, gm, trim, base=3)
        outline(g, trim)
    elif kind == "disc":
        g = sp.layer()
        gm = am(lambda t, s: abs(t - tg) <= 1 and abs(s) <= 4) | disc_ts(tg, -0.5, 2.6)
        shade_bar(g, gm, trim, base=3)
        outline(g, trim)
    elif kind == "wing":
        g = sp.layer()
        gm = am(lambda t, s: abs(t - tg) <= 1 and abs(s) <= 3)
        for sgn in (-1, 1):
            gm |= sweep_ts([(tg, sgn * 2.5), (tg + 2.0, sgn * 5.5), (tg + 5.0, sgn * 7.0)], [1.4, 1.1, 0.5])
            gm |= sweep_ts([(tg - 0.5, sgn * 3.0), (tg + 1.0, sgn * 6.5), (tg + 3.0, sgn * 8.0)], [0.9, 0.7, 0.4])
        shade_bar(g, gm, trim, base=3, extra=lambda t, s, tone: min(5, tone + (1 if t > tg + 1 else 0)))
        outline(g, trim)
    elif kind == "claw":
        g = sp.layer()
        gm = am(lambda t, s: abs(t - tg) <= 1 and abs(s) <= 3)
        for sgn in (-1, 1):
            gm |= sweep_ts([(tg, sgn * 2.5), (tg + 1.0, sgn * 5.0), (tg + 3.5, sgn * 5.8)], [1.2, 0.8, 0.3])
            gm |= sweep_ts([(tg, sgn * 2.5), (tg - 1.5, sgn * 4.5), (tg - 3.5, sgn * 4.6)], [1.0, 0.6, 0.3])
        shade_bar(g, gm, trim, base=3)
        outline(g, trim)
    elif kind == "basket":
        g = sp.layer()
        gm = am(lambda t, s: abs(t - tg) <= 1 and abs(s) <= 3)
        gm |= sweep_ts(curve((tg, 2.5), (tg - 4.0, 6.5), (tg - 10.0, 2.0), 10), [1.0] * 11)
        shade_bar(g, gm, trim, base=3)
        outline(g, trim)
    elif kind == "ring":
        g = sp.layer()
        ring = disc_ts(tg, -0.5, 3.4) & ~disc_ts(tg, -0.5, 2.0)
        gm = ring | am(lambda t, s: abs(t - tg) <= 0 and abs(s) <= 5)
        shade_bar(g, gm, trim, base=3)
        outline(g, trim)
    else:
        raise ValueError(kind)
    if gem_ramp is not None:
        l = sp.layer()
        x, y = pos(tg, -0.5)
        gem(l, x + 0.5, y + 0.5, gem_ramp, r=1.2)
        outline(l, trim.out)


def pommel(sp: Sprite, kind: str, trim: Ramp, t: float, gem_ramp=None):
    if kind == "disc":
        pommel_disc(sp, t, -0.5, 1.8, trim)
    elif kind == "gem":
        pommel_disc(sp, t, -0.5, 1.9, trim)
        l = sp.layer()
        x, y = pos(t, -0.5)
        gem(l, x + 0.5, y + 0.5, gem_ramp or P.SKY, r=1.0)
        outline(l, trim.out)
    elif kind == "ring":
        l = sp.layer()
        m = disc_ts(t - 0.5, -0.5, 2.2) & ~disc_ts(t - 0.5, -0.5, 1.0)
        shade_bar(l, m, trim, base=3)
        outline(l, trim)
    elif kind == "spike":
        l = sp.layer()
        m = am(lambda tt, s: t - 3 <= tt <= t + 1 and abs(s + 0.5) <= (tt - (t - 3)) * 0.6)
        shade_bar(l, m, trim, base=3)
        outline(l, trim)
    elif kind == "fang":
        l = sp.layer()
        m = sweep_ts(curve((t + 1.0, -0.5), (t - 1.5, -0.5), (t - 3.0, 2.0), 6), [1.3, 1.2, 1.0, 0.8, 0.6, 0.4, 0.2])
        shade_bar(l, m, trim, base=3)
        outline(l, trim)
    elif kind == "orb":
        orb(sp, t, -0.5, 1.9, gem_ramp or P.SKY, out=trim.out)
    elif kind == "tassel":
        pommel_disc(sp, t, -0.5, 1.4, trim)
        l = sp.layer()
        m = sweep_ts([(t - 1.0, 0.5), (t - 3.0, 2.5), (t - 5.0, 3.5)], [0.8, 0.9, 0.6])
        shade_bar(l, m, gem_ramp or P.CLOTH_RED, base=3)
        outline(l, (gem_ramp or P.CLOTH_RED).out)
    elif kind != "none":
        raise ValueError(kind)


def sparkles(c: Canvas, spots, ramp: Ramp):
    for t, s, r in spots:
        x, y = pos(t, s)
        star4(c, int(round(x)), int(round(y)), ramp, r)


# =========================================================================== swords
SWORD_GUARD = -14
GREAT_GUARD = -12
TWIN_GUARD = -15


def sword_like(spec: dict, kind: str) -> Canvas:
    """Longsword, greatsword or one twin blade from its spec."""
    tg = {"sword": SWORD_GUARD, "great": GREAT_GUARD, "twin": TWIN_GUARD}[kind]
    tip = spec.get("tip", {"sword": 27, "great": 29, "twin": 15}[kind])
    half = spec.get("half", {"sword": 2, "great": 3, "twin": 1}[kind])
    metal = mat(spec["metal"])
    trim = mat(spec.get("trim", "IRON_DARK"))
    grip = mat(spec.get("grip", "LEATHER"))
    glow = mat(spec.get("glow"))
    sp = Sprite()
    base = tg + 1
    m, center = blade_mask(spec["blade"], base, tip, half)
    fuller = spec.get("fuller")
    if fuller is True:
        fuller = metal[1]
    elif isinstance(fuller, str):
        fuller = mat(fuller)[4]
    band = None
    if spec.get("band"):
        band = (base, base + 2, mat(spec["band"]))
    paint_blade(sp, m, center, metal, fuller=fuller, edge=mat(spec.get("edge")), veins=mat(spec.get("veins")),
                base=base, tip=tip, band=band)
    grip_t = {"sword": (-23, -15), "great": (-25, -13), "twin": (-23, -16)}[kind]
    grip_layer(sp, grip_t[0], grip_t[1], grip, band=spec.get("wrap", wrap_band(2)))
    guard(sp, spec.get("guard", "bar"), trim, tg, spec.get("reach", {"sword": 6, "great": 7, "twin": 4}[kind]),
          mat(spec.get("gem")))
    pommel(sp, spec.get("pommel", "disc"), trim, {"sword": -25.5, "great": -27.0, "twin": -24.8}[kind],
           mat(spec.get("pgem", spec.get("gem"))))
    canvas = sp.done()
    if spec.get("runes"):
        runes(canvas, base + 3, tip - 7, center, mat(spec["runes"]), gap=spec.get("rune_gap", 5))
    if spec.get("sparkle"):
        sparkles(canvas, [(tip - 2, -3.0, 1)], mat(spec["sparkle"]))
    if glow is not None:
        canvas = glow_rim(canvas, glow, keep=lambda t, s: t >= base - 1)
    return canvas


def glow_rim(c: Canvas, ramp: Ramp, keep=None) -> Canvas:
    """An inner light: the dark outline of the charged part (``keep(t, s)``, default everything) turns into the
    glow's deep tone (pixel-art coloured outline), so the weapon reads as charged without noisy dots."""
    out = c.copy()
    mask = c.mask()
    h, w = mask.shape
    deep = mix(ramp[1], ramp[0], 0.35)
    for y in range(h):
        for x in range(w):
            if not mask[y, x]:
                continue
            if keep is not None and not keep(x - y, x + y - (w - 1)):
                continue
            r, g, b, a = (int(v) for v in c.a[y, x])
            if r + g + b < 110:
                inside = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                             if 0 <= x + dx < w and 0 <= y + dy < h and mask[y + dy, x + dx])
                if inside < 4:
                    out.put(x, y, deep)
    return out


def twin_pair(single: Canvas) -> Canvas:
    back = Canvas(N)
    back.a = single.a[:, ::-1].copy()
    out = Canvas(N)
    out.paste(back)
    out.paste(single)
    return out


# =========================================================================== spears
def spear_head(style: str, t0: int, tip: int):
    """Mask and centre of a spear head between t0 (socket) and the tip."""
    L = tip - t0
    center = lambda t: 0.0
    if style == "leaf":
        def f(t, s):
            u = (t - t0) / L
            w = 2.8 * math.sin(min(1.0, u * 1.25) * math.pi * 0.62) if u < 0.8 else 2.8 * 0.93 * (1 - u) / 0.2
            return t0 <= t <= tip and abs(s) <= max(0.6, w) and t <= tip - 2.0 * abs(s)
        return am(f), center
    if style == "pike":
        return am(lambda t, s: t0 <= t <= tip and abs(s) <= (1 if t < tip - 4 else 0.6) and
                  t <= tip - 3.0 * abs(s)), center
    if style == "broad":
        def f(t, s):
            u = (t - t0) / L
            w = 3.6 if u < 0.25 else 3.6 - 3.2 * (u - 0.25) / 0.75
            return t0 <= t <= tip and abs(s) <= max(0.6, w) and t <= tip - 1.6 * abs(s)
        return am(f), center
    if style == "winged":
        def f(t, s):
            u = (t - t0) / L
            core = abs(s) <= max(0.6, 2.4 * (1 - u)) + 0.6 and t <= tip - 2.0 * abs(s)
            wing = t0 <= t <= t0 + 4 and abs(s) <= 5.5 - (t - t0) * 0.6
            return t0 <= t <= tip and (core or wing)
        return am(f), center
    if style == "halberd":
        m = am(lambda t, s: t0 <= t <= tip and abs(s) <= 1.0 and t <= tip - 2.5 * abs(s))
        m |= poly_ts([(t0 + 1, 0.0), (t0 + 1.5, 6.5), (t0 + 4, 7.8), (t0 + 10, 7.2), (t0 + 11.5, 0.0)])
        m |= poly_ts([(t0 + 4, 0.0), (t0 + 4.5, -4.5), (t0 + 6, -5.0), (t0 + 8, 0.0)])
        return m, center
    if style == "glaive":
        center = lambda t: 2.4 * ((t - t0) / L) ** 2

        def f(t, s):
            u = (t - t0) / L
            c = 2.4 * u * u
            h = 1.0 + 1.6 * math.sin(math.pi * min(1.0, u * 1.1))
            return t0 <= t <= tip and -1.0 <= s - c <= h and t <= tip - 1.5 * abs(s - c)
        return am(f), center
    if style == "trident":
        m = am(lambda t, s: t0 <= t <= t0 + 2 and abs(s) <= 4)
        for k in (-3, 0, 3):
            length = tip if k == 0 else tip - 3
            m |= am(lambda t, s, k=k, length=length: t0 + 1 <= t <= length and abs(s - k) <= 0.6
                    and t <= length - 2 * abs(s - k))
            m |= am(lambda t, s, k=k, length=length: length - 4 <= t <= length - 3 and abs(s - k) <= 1.6)
        return m, center
    if style == "pitchfork":
        m = am(lambda t, s: t0 <= t <= t0 + 1 and abs(s) <= 3)
        for k in (-2.5, 2.5):
            m |= am(lambda t, s, k=k: t0 + 1 <= t <= tip - 1 and abs(s - k) <= 0.6)
        return m, center
    if style == "harpoon":
        m = am(lambda t, s: t0 <= t <= tip and abs(s) <= 0.8 and t <= tip - 2.5 * abs(s))
        for tb in (tip - 5, tip - 10):
            for sgn in (-1, 1):
                m |= sweep_ts([(tb, sgn * 0.8), (tb - 1.5, sgn * 2.6), (tb - 3.0, sgn * 3.2)], [0.8, 0.6, 0.3])
        return m, center
    if style == "crescent":
        m = am(lambda t, s: t0 <= t <= tip - 4 and abs(s) <= 0.9)
        ring = disc_ts(tip - 5, 0.0, 6.0) & ~disc_ts(tip - 2.5, 0.0, 5.0)
        m |= ring & am(lambda t, s: t >= tip - 9)
        return m, center
    if style == "hook":
        m = am(lambda t, s: t0 <= t <= tip and abs(s) <= 0.8 and t <= tip - 2.5 * abs(s))
        m |= sweep_ts(curve((tip - 6, 0.0), (tip - 2.5, 4.5), (tip - 7.0, 4.8), 8), [0.9] * 9)
        return m, center
    if style == "tusk":
        # a boar spear whose lugs are two curved tusks
        def f(t, s):
            u = (t - t0) / L
            w = 2.3 * (1 - u) + 0.6
            return t0 + 1 <= t <= tip and abs(s) <= w and t <= tip - 2.0 * abs(s)
        m = am(f)
        for sgn in (-1, 1):
            path = curve((t0 - 0.5, sgn * 1.2), (t0 - 0.5, sgn * 7.0), (t0 + 8.5, sgn * 6.4), 10)
            m |= sweep_ts(path, list(np.linspace(1.7, 0.35, len(path))))
        return m, center
    if style == "fang":
        # one long curved fang
        center = lambda t: -2.4 * ((t - t0) / L) ** 1.7

        def f(t, s):
            u = min(1.0, max(0.0, (t - t0) / L))
            c = -2.4 * u ** 1.7
            h = 3.2 * (1 - u) ** 0.8 + 0.4
            return t0 <= t <= tip and abs(s - c) <= h and t <= tip - 1.4 * abs(s - c)
        return am(f), center
    if style == "flamelance":
        def f(t, s):
            u = (t - t0) / L
            w = 2.0 + (1 if math.sin(t * 0.9) > 0.3 else 0) - 1.6 * u
            return t0 <= t <= tip and abs(s) <= max(0.6, w) and t <= tip - 1.8 * abs(s)
        return am(f), center
    raise ValueError(style)


def spear(spec: dict) -> Canvas:
    sp = Sprite()
    shaft_t0 = spec.get("butt", -28)
    socket = spec.get("socket", 9)
    tip = spec.get("tip", 29)
    shaft = mat(spec.get("shaft", "WOOD"))
    trim = mat(spec.get("trim", "IRON_DARK"))
    metal = mat(spec["metal"])
    bands = spec.get("bands", ())
    shaft_layer(sp, shaft_t0, socket + 1, shaft,
                band=(lambda t, s: (trim[3] if t in bands else None)) if bands else None)
    collar(sp, shaft_t0 - 1, shaft_t0 + 1, 1.5, trim)
    if spec.get("ribbon"):
        r = sp.layer()
        rib = mat(spec["ribbon"])
        rm = sweep_ts([(socket - 2, 1.0), (socket - 5, 3.0), (socket - 9, 3.5)], [0.9, 0.9, 0.5])
        rm |= sweep_ts([(socket - 2, 1.0), (socket - 6, 1.8), (socket - 10, 1.5)], [0.8, 0.8, 0.4])
        shade_bar(r, rm, rib, base=3)
        outline(r, rib)
    m, center = spear_head(spec["head"], socket + 1, tip)
    paint_blade(sp, m, center, metal, fuller=(metal[1] if spec.get("fuller") else None),
                edge=mat(spec.get("edge")), veins=mat(spec.get("veins")), base=socket + 1, tip=tip)
    collar(sp, socket - 2, socket + 1, spec.get("collar", 1.6), trim)
    canvas = sp.done()
    if spec.get("gem"):
        x, y = pos(socket - 0.5, -0.5)
        g = Canvas(N)
        gem(g, x + 0.5, y + 0.5, mat(spec["gem"]), r=1.1)
        canvas.paste(g)
    if spec.get("runes"):
        runes(canvas, socket + 3, tip - 6, center, mat(spec["runes"]), gap=4)
    if spec.get("sparkle"):
        sparkles(canvas, [(tip - 2, -3.0, 1)], mat(spec["sparkle"]))
    if spec.get("glow"):
        canvas = glow_rim(canvas, mat(spec["glow"]), keep=lambda t, s: t >= socket - 2)
    return canvas


# =========================================================================== staves
HEAD_T = 21.0


def staff(spec: dict) -> Canvas:
    sp = Sprite()
    shaft = mat(spec.get("shaft", "WOOD"))
    trim = mat(spec.get("trim", "GOLD"))
    core = mat(spec.get("core", "SKY"))
    head = spec["head"]
    top = spec.get("top", 13)
    bands = spec.get("bands", ())
    if head == "branch":
        top = 17
    shaft_layer(sp, -29, top, shaft, band=(lambda t, s: (trim[3] if t in bands else None)) if bands else None)
    cap = sp.layer()
    cm = am(lambda t, s: -30 <= t <= -27 and abs(s + 0.5) <= 1.5)
    shade_bar(cap, cm, trim, base=3)
    outline(cap, trim)
    t = HEAD_T
    if head == "orb_claw":
        orb(sp, t, -0.5, 3.5, core)
        pr = sp.layer()
        pm = am(lambda tt, s: 12 <= tt <= 15 and abs(s + 0.5) <= 1.7)
        for sgn in (-1, 1):
            pm |= sweep_ts([(14, sgn * 1.6 - 0.5), (17, sgn * 5.2 - 0.5), (22, sgn * 5.0 - 0.5), (25, sgn * 3.0 - 0.5)],
                           [1.1, 0.9, 0.7, 0.4])
        shade_bar(pr, pm, trim, base=3)
        outline(pr, trim)
    elif head == "crook":
        c = sp.layer()
        path = curve((12, -0.5), (28, -1.5), (25, 6.0), 12) + curve((25, 6.0), (21, 8.5), (17.5, 6.0), 6)[1:]
        cm = sweep_ts(path, [1.6 - 0.6 * i / (len(path) - 1) for i in range(len(path))])
        shade_bar(c, cm, shaft, base=3)
        outline(c, shaft)
        orb(sp, 18.0, 5.0, 1.9, core, out=trim.out)
        bd = sp.layer()
        bm = am(lambda tt, s: 12 <= tt <= 14 and abs(s + 0.5) <= 1.6)
        shade_bar(bd, bm, trim, base=3)
        outline(bd, trim)
    elif head == "lantern":
        frame = sp.layer()
        fm = am(lambda tt, s: 12 <= tt <= 14 and abs(s + 0.5) <= 1.4)
        box = am(lambda tt, s: 14 <= tt <= 25 and abs(s + 0.5) <= 3.6)
        inner = am(lambda tt, s: 16 <= tt <= 23 and abs(s + 0.5) <= 2.0)
        fm |= box & ~inner
        fm |= am(lambda tt, s: 25 <= tt <= 27 and abs(s + 0.5) <= 1.6 - (tt - 25) * 0.4)
        fm |= disc_ts(28.3, -0.5, 1.2) & ~disc_ts(28.3, -0.5, 0.4)
        shade_bar(frame, fm, trim, base=3)
        outline(frame, trim)
        light = sp.layer()
        shade_ball(light, inner, core, lo=2)
        bars = Canvas(N)
        for tt in range(16, 24):
            put(bars, tt, -1 if (tt % 2) else 0, trim[2])
        sp.layers.append(bars)
    elif head == "crystal":
        cr = sp.layer()
        cm = poly_ts([(12, -0.5), (17, -4.8), (26, -2.2), (30, -0.5), (26, 1.8), (17, 4.0)])
        shade_blade(cr, cm, core, center=lambda tt: -0.5)
        outline(cr, core)
        sh = sp.layer()
        sm = poly_ts([(14, -2.5), (18, -7.0), (21, -6.2), (18, -2.0)]) | poly_ts([(14, 2.0), (18, 6.5), (21, 5.8),
                                                                                 (18, 1.5)])
        shade_blade(sh, sm, core, center=lambda tt: 0)
        outline(sh, core)
        collar(sp, 10, 13, 2.0, trim)
    elif head == "branch":
        br = sp.layer()
        bm = sweep_ts(curve((15, -0.5), (21, -3.5), (25, -7.0), 6), [1.3, 1.1, 1.0, 0.8, 0.7, 0.6, 0.4])
        bm |= sweep_ts(curve((15, -0.5), (21, 3.0), (24, 6.5), 6), [1.3, 1.1, 1.0, 0.8, 0.7, 0.6, 0.4])
        bm |= sweep_ts([(16, -0.5), (22, -0.5), (27.5, 0.0)], [1.1, 0.8, 0.4])
        shade_bar(br, bm, shaft, base=3)
        outline(br, shaft)
        lv = sp.layer()
        leaf = mat(spec.get("leaf", "LEAF"))
        lm = disc_ts(26.0, -7.5, 1.3) | disc_ts(24.5, 7.0, 1.3) | disc_ts(28.5, 0.3, 1.2)
        shade_ball(lv, lm, leaf, lo=1)
        outline(lv, leaf)
        orb(sp, 20.5, -0.5, 2.0, core, out=shaft.out)
    elif head == "heartroot":
        roots = sp.layer()
        rm = Canvas(N).mask()
        for sgn in (-1, 1):
            path = curve((13.5, -0.5), (17.0, sgn * 12.0 - 0.5), (28.0, -0.5), 14)
            rm |= sweep_ts(path, [1.3 - 0.6 * i / 14 for i in range(15)])
        rm |= sweep_ts([(11.0, -0.5), (14.5, -0.5)], [1.5, 1.4])
        shade_bar(roots, rm, shaft, base=3)
        outline(roots, shaft)
        orb(sp, 20.0, -0.5, 2.0, core)
        lv = sp.layer()
        leaf = mat(spec.get("leaf", "LEAF"))
        lm = disc_ts(19.5, -7.2, 1.1) | disc_ts(17.5, 6.0, 1.1) | disc_ts(29.0, 0.5, 1.0)
        shade_ball(lv, lm, leaf, lo=1)
        outline(lv, leaf)
    elif head == "ring":
        rg = sp.layer()
        rm = disc_ts(t, -0.5, 5.2) & ~disc_ts(t, -0.5, 3.6)
        rm |= am(lambda tt, s: 12 <= tt <= 16 and abs(s + 0.5) <= 1.2)
        shade_bar(rg, rm, trim, base=3)
        outline(rg, trim)
        orb(sp, t, -0.5, 2.2, core)
    elif head == "bell":
        bell = sp.layer()
        bm = am(lambda tt, s: 13 <= tt <= 27 and abs(s + 0.5) <= 1.2 + 4.2 * ((tt - 13) / 14.0) ** 1.4)
        rim = am(lambda tt, s: 25 <= tt <= 27 and abs(s + 0.5) <= 5.6)
        mouth = am(lambda tt, s: 26 <= tt <= 28 and abs(s + 0.5) <= 3.0)
        shade_bar(bell, (bm | rim) & ~mouth, core, base=3, grad=1.2)
        outline(bell, core)
        band = Canvas(N)
        for tt in (17, 18):
            for ss in range(-5, 5):
                if (tt + ss) % 2 and abs(ss + 0.5) <= 1.2 + 4.2 * ((tt - 13) / 14.0) ** 1.4:
                    put(band, tt, ss, trim[4] if ss < 0 else trim[3])
        sp.layers.append(band)
        orb(sp, 27.5, -0.5, 1.3, trim, out=core.out)
    elif head == "skull":
        sk = sp.layer()
        bone = mat(spec.get("bone", "BONE"))
        sm = disc_ts(21.0, -0.5, 3.9) | am(lambda tt, s: 15 <= tt <= 18 and abs(s + 0.5) <= 2.0)
        shade_ball(sk, sm, bone, lo=1)
        outline(sk, bone)
        eyes = Canvas(N)
        for ds in (-2.0, 1.0):
            x, y = pos(20.5, ds)
            eyes.put(int(round(x)), int(round(y)), core[4])
            eyes.put(int(round(x)) + 1, int(round(y)), core[3])
        sp.layers.append(eyes)
        horn = sp.layer()
        hm = sweep_ts(curve((23.0, -3.5), (27.0, -6.5), (28.5, -3.5), 6), [1.1, 0.9, 0.8, 0.6, 0.5, 0.3, 0.2])
        hm |= sweep_ts(curve((23.0, 2.5), (27.0, 5.5), (28.5, 2.5), 6), [1.1, 0.9, 0.8, 0.6, 0.5, 0.3, 0.2])
        shade_bar(horn, hm, bone, base=3)
        outline(horn, bone)
    elif head == "sceptre":
        sc = sp.layer()
        sm = am(lambda tt, s: 11 <= tt <= 18 and abs(s + 0.5) <= 1.8 - (0.7 if tt % 3 == 0 else 0))
        sm |= poly_ts([(16, -0.5), (20, -5.0), (23.5, -3.5), (29, -0.5), (23.5, 2.5), (20, 4.0)])
        shade_bar(sc, sm, trim, base=3)
        outline(sc, trim)
        orb(sp, 22.0, -0.5, 2.1, core, out=trim.out)
    elif head == "lotus":
        lo = sp.layer()
        petal = mat(spec.get("leaf", "ROSE"))
        lm = Canvas(N).mask()
        for ang in (-70, -35, 0, 35, 70):
            a = math.radians(ang)
            lm |= sweep_ts([(16, -0.5), (16 + 4 * math.cos(a), -0.5 + 4 * math.sin(a)),
                            (16 + 7.5 * math.cos(a), -0.5 + 7.5 * math.sin(a))], [1.2, 1.7, 0.4])
        shade_bar(lo, lm, petal, base=3)
        outline(lo, petal)
        orb(sp, 19.0, -0.5, 2.0, core)
    elif head == "sunflower":
        sf = sp.layer()
        petal = mat(spec.get("leaf", "GOLD"))
        pm = Canvas(N).mask()
        for k in range(10):
            a = k * math.pi / 5
            pm |= sweep_ts([(21.0, -0.5), (21.0 + 5.6 * math.cos(a), -0.5 + 5.6 * math.sin(a))], [1.6, 0.5])
        shade_bar(sf, pm, petal, base=3)
        outline(sf, petal)
        orb(sp, 21.0, -0.5, 2.6, core, out=P.WOOD.out)
    elif head == "star":
        st = sp.layer()
        sm = Canvas(N).mask()
        for k in range(5):
            a = math.pi / 2 + k * 2 * math.pi / 5
            sm |= sweep_ts([(21.5, -0.5), (21.5 + 6.4 * math.sin(a), -0.5 + 6.4 * math.cos(a))], [2.0, 0.3])
        sm |= am(lambda tt, s: 12 <= tt <= 17 and abs(s + 0.5) <= 1.1)
        shade_bar(st, sm, trim, base=3)
        outline(st, trim)
        orb(sp, 21.5, -0.5, 1.9, core, out=trim.out)
    elif head == "moon":
        mn = sp.layer()
        mm = disc_ts(t, -0.5, 5.6) & ~disc_ts(t + 2.0, 2.0, 4.6)
        mm |= am(lambda tt, s: 12 <= tt <= 17 and abs(s + 0.5) <= 1.1)
        shade_bar(mn, mm, trim, base=3)
        outline(mn, trim)
        orb(sp, t + 1.0, 1.4, 2.0, core, out=trim.out)
    elif head == "fork":
        fk = sp.layer()
        fm = am(lambda tt, s: 12 <= tt <= 16 and abs(s + 0.5) <= 1.6)
        for sgn in (-1, 1):
            fm |= sweep_ts([(15.0, sgn * 1.0 - 0.5), (19.0, sgn * 4.6 - 0.5), (28.0, sgn * 3.6 - 0.5)], [1.1, 1.0, 0.4])
        shade_bar(fk, fm, trim, base=3)
        outline(fk, trim)
        orb(sp, 21.5, -0.5, 2.3, core)
    elif head == "cage":
        cg = sp.layer()
        cgm = disc_ts(t, -0.5, 5.0) & ~disc_ts(t, -0.5, 3.6)
        cgm &= am(lambda tt, s: (tt + s) % 4 in (0, 1) or abs(tt - t) > 3.0)
        cgm |= am(lambda tt, s: 12 <= tt <= 16 and abs(s + 0.5) <= 1.2) | disc_ts(t + 5.0, -0.5, 1.2)
        orb(sp, t, -0.5, 3.1, core)
        shade_bar(cg, cgm, trim, base=3)
        outline(cg, trim)
        sp.layers.remove(cg)
        sp.layers.append(cg)
    elif head == "post":
        pole = sp.layer()
        pm = am(lambda tt, s: 11 <= tt <= 23 and abs(s + 0.5) <= 1.3)
        pm |= am(lambda tt, s: 17 <= tt <= 18 and abs(s + 0.5) <= 6.0)
        shade_bar(pole, pm, shaft, base=3)
        outline(pole, shaft)
        hat = sp.layer()
        hm = poly_ts([(22, -5.5), (22, 4.5), (29, -0.5)])
        shade_bar(hat, hm, mat(spec.get("leaf", "STRAW")), base=3)
        outline(hat, mat(spec.get("leaf", "STRAW")))
        orb(sp, 20.0, -0.5, 1.4, core, out=shaft.out)
    elif head == "shell":
        # a tortoise shell seen from above: plates around a central scute with a glowing heart
        shell = mat(spec.get("leaf", "MOSS"))
        sh = sp.layer()
        shm = disc_ts(t, -0.5, 5.6) | am(lambda tt, s: 12 <= tt <= 17 and abs(s + 0.5) <= 1.3)
        shade_bar(sh, shm, shell, base=4)
        outline(sh, shell)
        seams = sp.layer()
        sm = disc_ts(t, -0.5, 3.1) & ~disc_ts(t, -0.5, 2.2)
        for k in range(6):
            a = k * math.pi / 3 + math.pi / 6
            sm |= sweep_ts([(t + 3.0 * math.cos(a), -0.5 + 3.0 * math.sin(a)),
                            (t + 5.2 * math.cos(a), -0.5 + 5.2 * math.sin(a))], [0.5, 0.5])
        sm &= disc_ts(t, -0.5, 5.2)
        shade_bar(seams, sm, trim, base=1)
        orb(sp, t, -0.5, 1.8, core, out=shell.out)
    elif head == "crown":
        # a scarecrow king's sceptre: straw tassels, a burning orb and a small crown on top
        straw = mat(spec.get("leaf", "STRAW"))
        st = sp.layer()
        stm = Canvas(N).mask()
        for k in (-2.4, -0.8, 0.8, 2.4):
            stm |= sweep_ts([(15.0, -0.5 + k * 0.5), (12.0, -0.5 + k * 1.2), (8.5, -0.5 + k * 1.5)], [0.9, 0.8, 0.5])
        shade_bar(st, stm, straw, base=3)
        outline(st, straw)
        nk = sp.layer()
        nm = am(lambda tt, s: 13 <= tt <= 17 and abs(s + 0.5) <= 1.4)
        shade_bar(nk, nm, trim, base=3)
        outline(nk, trim)
        orb(sp, 20.0, -0.5, 3.1, core)
        cr = sp.layer()
        crm = am(lambda tt, s: 23 <= tt <= 24.5 and abs(s + 0.5) <= 3.0)
        for k in (-2.6, -0.5, 1.6):
            crm |= am(lambda tt, s, k=k: 24 <= tt <= 28 and abs(s - k) <= max(0.0, 1.2 - (tt - 24) * 0.3))
        shade_bar(cr, crm, trim, base=3)
        outline(cr, trim)
    else:
        raise ValueError(head)
    canvas = sp.done()
    if spec.get("sparkle"):
        sparkles(canvas, [(26.0, -4.0, 1)], mat(spec["sparkle"]))
    if spec.get("glow"):
        canvas = glow_rim(canvas, mat(spec["glow"]), keep=lambda t, s: t >= 11)
    return canvas


# =========================================================================== bows
def bow(spec: dict) -> Canvas:
    sp = Sprite()
    depth = spec.get("depth", 12.5)
    recurve = spec.get("recurve", 0.0)
    string_ramp = mat(spec.get("string", "STRING"))
    inset = 1.5 + recurve * 0.5
    string_layer(sp, string_ramp[spec.get("string_tone", 3)], t0=-BOW_TIP + inset, t1=BOW_TIP - inset,
                 s=STRING_S - recurve * 0.12)
    limb = sp.layer()
    path = limb_path(depth=depth, recurve=recurve)
    m = sweep_ts(path, limb_widths(path, spec.get("grip_w", 1.9), spec.get("tip_w", 0.9)))
    material = mat(spec["metal"])
    accent = mat(spec.get("trim", "GOLD"))
    bands = spec.get("bands", ())
    tips = spec.get("tips", "plain")
    if tips == "curl":
        for sgn in (-1, 1):
            m |= disc_ts(sgn * (BOW_TIP + 0.5), STRING_S - depth * 0.08 - recurve, 1.3)
    if tips == "blade":
        for sgn in (-1, 1):
            m |= sweep_ts([(sgn * (BOW_TIP - 2), STRING_S - 2.0 - recurve), (sgn * (BOW_TIP + 3.0),
                                                                              STRING_S - 4.5 - recurve)], [1.2, 0.2])

    def ex(t, s, tone):
        if abs(t) in bands:
            return accent[min(5, tone + 1)]
        if spec.get("capped") and abs(t) >= BOW_TIP - 4:
            return accent[tone]
        return None
    shade_bar(limb, m, material, base=3, grad=1.0, extra=ex)
    outline(limb, material.out)
    if spec.get("spikes"):
        sk = sp.layer()
        km = Canvas(N).mask()
        for tt in spec["spikes"]:
            km |= limb_spike(tt, depth, length=3.0, lean=0.35, width=1.1, recurve=recurve)
        shade_bar(sk, km, mat(spec.get("spike_mat", spec["metal"])), base=3)
        outline(sk, mat(spec.get("spike_mat", spec["metal"])).out)
    if tips == "feather":
        fe = sp.layer()
        fr = mat(spec.get("feather", "LINEN"))
        fm = Canvas(N).mask()
        for sgn in (-1, 1):
            base = (sgn * (BOW_TIP - 1.0), STRING_S - 1.5 - recurve)
            fm |= sweep_ts([base, (base[0] - sgn * 1.0, base[1] - 3.0), (base[0] - sgn * 2.5, base[1] - 4.5)],
                           [0.9, 1.0, 0.4])
        shade_bar(fe, fm, fr, base=3)
        outline(fe, fr)
    grip = sp.layer()
    s0 = STRING_S - depth
    gm = am(lambda t, s: -3 <= t <= 3 and abs(s - s0) <= 1.8)
    shade_bar(grip, gm, mat(spec.get("grip", "LEATHER")), base=3,
              extra=lambda t, s, tone: tone - (1 if t % 2 == 0 else 0))
    outline(grip, mat(spec.get("grip", "LEATHER")))
    canvas = sp.done()
    if spec.get("gem"):
        x, y = pos(0, s0 - 0.2)
        g = Canvas(N)
        gem(g, x + 0.5, y + 0.5, mat(spec["gem"]), r=1.3)
        canvas.paste(g)
    if tips == "gem":
        for sgn in (-1, 1):
            x, y = pos(sgn * (BOW_TIP - 0.5), STRING_S - 1.5 - recurve)
            g = Canvas(N)
            gem(g, x + 0.5, y + 0.5, mat(spec.get("gem", "SKY")), r=1.1)
            canvas.paste(g)
    if spec.get("runes"):
        rr = mat(spec["runes"])
        for tt in range(-18, 19, 6):
            u = tt / BOW_TIP
            s = STRING_S - depth * (1 - u * u)
            if abs(tt) > 4:
                put(canvas, tt, int(round(s)) - (1 if (tt + int(round(s))) % 2 == 0 else 0), rr[4])
    if spec.get("sparkle"):
        sparkles(canvas, [(BOW_TIP - 1, STRING_S - 6.0, 1)], mat(spec["sparkle"]))
    if spec.get("glow"):
        canvas = glow_rim(canvas, mat(spec["glow"]), keep=lambda t, s: abs(t) >= 5)
    return canvas


# =========================================================================== entry points
def render(spec: dict) -> Canvas:
    kind = spec["kind"]
    if kind == "sword":
        return sword_like(spec, "sword")
    if kind == "great":
        return sword_like(spec, "great")
    if kind == "twin":
        return twin_pair(sword_like(spec, "twin"))
    if kind == "spear":
        return spear(spec)
    if kind == "staff":
        return staff(spec)
    if kind == "bow":
        return bow(spec)
    raise ValueError(kind)


def render_held(spec: dict) -> Canvas:
    """The single blade a hand holds (twin blades only)."""
    return sword_like(spec, "twin")
