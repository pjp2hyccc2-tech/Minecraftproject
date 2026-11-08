"""42 weapon sprites (6 families x 7 tiers), 32x32, drawn along the diagonal.

Tiers: 1 iron, 2 steel, 3 desert, 4 frost, 5 flame dragon, 6 celestial, 7 abyss.
"""
from __future__ import annotations

import math

import numpy as np

import palette as P
from px import Canvas, mix, hexc, xy, grow
from wbase import (N, Sprite, am, pos, disc, disc_ts, shade_blade, shade_bar, shade_ball, shade_shaft,
                   wrap_band, gem, sparkle, outline, neighbours_out, poly_ts, sweep_ts, curve, mirror_s)

TIERS = 7


# =========================================================================== helpers
def put(c: Canvas, t: int, s: int, col) -> None:
    p = xy(t, s, c.w)
    if p is not None:
        c.put(p[0], p[1], col)


def tip_line(t: int, s: int, tip: float, k: float, center: float = -0.5) -> bool:
    """Pointed tip: line s may reach t <= tip - k*|s - center|."""
    return t <= tip - k * abs(s - center)


def grip_layer(sp: Sprite, t0: int, t1: int, ramp, band=None, lines=(-1, 0)):
    g = sp.layer()
    m = am(lambda t, s: t0 <= t <= t1 and s in lines)
    shade_shaft(g, m, ramp, band=band)
    outline(g, ramp)
    return g


def pommel_disc(sp: Sprite, t: float, s: float, r: float, ramp, spec=True):
    l = sp.layer()
    m = disc_ts(t, s, r)
    shade_ball(l, m, ramp, lo=1, spec=spec)
    outline(l, ramp)
    return l, m


# =========================================================================== motifs
RUNE_GLYPHS = [
    [(0, 0), (1, -1), (1, 1), (2, 0)],            # diamond
    [(0, -1), (1, 0), (2, -1), (3, 0)],           # zig
    [(0, 1), (1, 0), (2, 1), (2, -1)],            # fork
    [(0, 0), (2, 0), (1, -1)],                    # chevron
]


def runes(c: Canvas, t0: int, t1: int, center, ramp, gap: int = 5, phase: int = 0, glow=None):
    """Glowing rune marks along a centre line between t0 and t1."""
    t = t0
    k = phase
    while t + 3 <= t1:
        g = RUNE_GLYPHS[k % len(RUNE_GLYPHS)]
        for dt, ds in g:
            tt = t + dt
            ss = int(round(center(tt))) + ds
            if (tt + ss) % 2 == 0:
                ss += 1 if ds <= 0 else -1
            put(c, tt, ss, ramp[4] if (dt + k) % 2 else ramp[5])
        t += gap
        k += 1


def star4(c: Canvas, cx: int, cy: int, ramp, r: int = 2):
    """Small four-point star (pixel coords)."""
    for k in range(1, r + 1):
        col = ramp[4] if k < r else ramp[3]
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            c.put(cx + dx, cy + dy, col)
    c.put(cx, cy, ramp[5])


def eye_gem(c: Canvas, cx: float, cy: float, ramp, pupil):
    m = gem(c, cx, cy, ramp, r=1.6)
    # vertical slit pupil through the centre column
    ys, xs = np.nonzero(m)
    mx = int(round(xs.mean()))
    for x, y in zip(xs.tolist(), ys.tolist()):
        if x == mx and ys.min() < y < ys.max():
            c.put(x, y, pupil)
    return m


# =========================================================================== longsword
LS_TIP = 27
LS_GUARD = -14          # guard centre line (t)
LS_GRIP = (-23, -16)    # grip t range


def ls_blade_mask(tip=LS_TIP, half=2, k=2.0, base=LS_GUARD + 1):
    return am(lambda t, s: base <= t and abs(s) <= half and t <= tip - k * abs(s))


def cross_guard(sp, ramp, reach=7, tg=LS_GUARD, thick=1, bend=None, extra=None, round_ends=True):
    """3-line cross guard centred on t = tg; ``bend(|s|)`` shifts the ends along t."""
    g = sp.layer()

    def f(t, s):
        a = abs(s)
        if a > reach:
            return False
        d = bend(a) if bend else 0
        th = thick
        if round_ends and a == reach:
            th = max(0, thick - 1)
        return tg + d - th <= t <= tg + d + th

    gm = am(f)

    def tone(t, s, base_tone):
        d = bend(abs(s)) if bend else 0
        rel = t - (tg + d)
        v = 3 + (1 if rel > 0 else -1 if rel < 0 else 0)
        v += 1 if s < -reach // 2 else -1 if s > reach // 2 else 0
        if extra:
            e = extra(t, s, v)
            if e is not None:
                return e
        return max(1, min(5, v))

    shade_bar(g, gm, ramp, extra=tone)
    outline(g, ramp)
    return g, gm


def longsword(tier: int) -> Canvas:
    return [ls1, ls2, ls3, ls4, ls5, ls6, ls7][tier - 1]()


def blade_tones(edge_hi, lit, ridge, dark, edge_dark):
    """Build an ``extra`` callback that maps the generic tones to explicit colours."""
    table = {5: edge_hi, 4: lit, 3: dark, 2: edge_dark, 1: edge_dark}

    def f(t, s, tone):
        return table.get(tone)
    return f


def ls1() -> Canvas:
    """Iron: plain straight blade, simple bar guard, round pommel."""
    sp = Sprite()
    b = sp.layer()
    shade_blade(b, ls_blade_mask(tip=27), P.IRON, center=lambda t: 0)
    outline(b, P.IRON)
    grip_layer(sp, -23, -15, P.LEATHER, band=wrap_band(2))
    cross_guard(sp, P.IRON_DARK, reach=6)
    pommel_disc(sp, -25.5, -0.5, 1.7, P.IRON_DARK)
    return sp.done()


def ls2() -> Canvas:
    """Steel: polished blade with a fuller, curled quillons with ball ends, langet, wheel pommel."""
    sp = Sprite()
    b = sp.layer()

    def fuller(t, s, tone):
        if -12 <= t <= 14:
            return {-2: None, -1: 4, 0: 1, 1: 4, 2: None}.get(s)
        return None
    shade_blade(b, ls_blade_mask(tip=27, k=2.5), P.STEEL, center=lambda t: 0, extra=fuller)
    outline(b, P.STEEL)
    grip_layer(sp, -23, -15, P.LEATHER_DARK, band=lambda t, s: (P.SILVER[3] if (t + s) % 4 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 6 and LS_GUARD - 1 <= t <= LS_GUARD + 1 - (1 if abs(s) == 6 else 0))
    for sgn in (-1, 1):
        gm |= sweep_ts([(LS_GUARD, sgn * 5.5), (LS_GUARD + 0.5, sgn * 7.5), (LS_GUARD + 2.5, sgn * 8.5)], [1.2, 1.1, 0.9])
        gm |= disc_ts(LS_GUARD + 3.2, sgn * 8.2, 1.15)
    shade_bar(g, gm, P.STEEL, base=3, extra=lambda t, s, tone: min(5, tone + (1 if t > LS_GUARD else 0)))
    outline(g, P.STEEL)
    l = sp.layer()
    lm = am(lambda t, s: abs(s) * 1.2 + abs(t - LS_GUARD - 0.5) * 0.8 <= 2.9)
    shade_bar(l, lm, P.SILVER, base=3)
    outline(l, P.STEEL)
    pl, pm = pommel_disc(sp, -25.5, -0.5, 2.1, P.STEEL)
    x, y = pos(-25.5, -0.5)
    pl.put(int(round(x)), int(round(y)), P.STEEL[1])
    return sp.done()


def ls3() -> Canvas:
    """Desert scimitar: curved, widening blade with gold spine, gold guard with turquoise."""
    sp = Sprite()
    b = sp.layer()
    t0, tip = LS_GUARD + 1, 25

    def bend(t):
        return 0.0 if t < -6 else 3.0 * ((t + 6) / (tip + 6)) ** 2

    def lo(t):   # cutting edge (upper-left, convex)
        w = 2.0 + 1.3 * min(1.0, max(0.0, (t + 4) / 18.0))
        if t > tip - 8:
            w *= max(0.0, (tip - t) / 8.0) ** 0.7
        return bend(t) - w

    def hi(t):   # spine
        return bend(t) + 1.8 - (max(0, t - (tip - 3)) * 0.6)

    m = am(lambda t, s: t0 <= t <= tip and lo(t) - 0.01 <= s <= hi(t) + 0.01)

    def ex(t, s, tone):
        if s >= hi(t) - 1.0 and t < tip - 1:
            return P.GOLD[4] if tone >= 4 else P.GOLD[3]
        return None
    shade_blade(b, m, P.FROST_STEEL, center=lambda t: bend(t) - 0.5, extra=ex)
    outline(b, P.SANDSTONE.out)
    grip_layer(sp, -22, -15, P.SANDSTONE, band=wrap_band(2, depth=-1))
    # gold guard with drooping ends
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 5 and LS_GUARD - 1 <= t <= LS_GUARD + 1 - (1 if abs(s) == 5 else 0))
    for sgn in (-1, 1):
        gm |= sweep_ts([(LS_GUARD, sgn * 4.5), (LS_GUARD - 1, sgn * 6.5), (LS_GUARD - 3, sgn * 7.2)], [1.3, 1.0, 0.7])
    shade_bar(g, gm, P.GOLD, base=3)
    outline(g, P.GOLD)
    gl = sp.layer()
    x, y = pos(LS_GUARD, 0)
    gem(gl, x + 0.5, y + 0.5, P.TURQUOISE, r=1.6)
    outline(gl, P.GOLD.out)
    # bird-head pommel hooking toward the lower right
    p = sp.layer()
    pm = sweep_ts([(-22, -0.3), (-25, 0.2), (-26.5, 2.0), (-25.8, 4.0)], [1.5, 1.6, 1.5, 1.0])
    shade_bar(p, pm, P.GOLD, base=3)
    x, y = pos(-25.6, 1.0)
    p.put(int(round(x)), int(round(y)), P.TURQUOISE[3])
    outline(p, P.GOLD)
    return sp.done()


def ice_blade(sp, tip=28, spikes=(), half=2, k=3.0, base=LS_GUARD + 1):
    b = sp.layer()
    m = am(lambda t, s: base <= t and abs(s) <= half and t <= tip - k * abs(s))
    for (ta, sa, tb, sb, tc, sc) in spikes:
        m |= poly_ts([(ta, sa), (tb, sb), (tc, sc)])

    def ex(t, s, tone):
        if s == 0 and t < tip - 2:
            return 5 if (t % 6) in (1, 3) else 4
        return None
    shade_blade(b, m, P.ICE, center=lambda t: 0, extra=ex)
    outline(b, P.ICE)
    return b, m


def ls4() -> Canvas:
    """Frost: crystal blade with ice spikes, crystal-cluster guard, shard pommel."""
    sp = Sprite()
    ice_blade(sp, tip=28, spikes=[(-6, -1.5, -1, -1.5, 2, -5.5), (3, 1.5, 8, 1.5, 11, 5.0),
                                  (11, -1.5, 15, -1.5, 18, -4.5)])
    grip_layer(sp, -23, -15, P.ICE_DEEP, band=lambda t, s: (P.FUR_WHITE[3] if (t + s) % 4 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 2.5 and LS_GUARD - 2 <= t <= LS_GUARD + 1 + (1 if abs(s) < 1 else 0))
    gm |= sweep_ts([(LS_GUARD - 0.5, -2), (LS_GUARD + 1.0, -5.5), (LS_GUARD + 4.0, -8.5)], [1.5, 1.1, 0.3])
    gm |= sweep_ts([(LS_GUARD - 1.0, 2), (LS_GUARD + 0.0, 5.0), (LS_GUARD + 2.5, 7.0)], [1.4, 1.0, 0.3])
    gm |= sweep_ts([(LS_GUARD - 1.5, -2), (LS_GUARD - 3.5, -4.5)], [1.0, 0.3])
    shade_bar(g, gm, P.ICE, base=3, grad=1.5)
    outline(g, P.ICE)
    p = sp.layer()
    pm = poly_ts([(-22, -1.8), (-22, 1.8), (-25, 2.2), (-29.5, 0.2), (-25, -2.2)])
    shade_bar(p, pm, P.ICE, base=4, grad=1.5)
    outline(p, P.ICE)
    return sp.done()


def ls5() -> Canvas:
    """Flame dragon: wavy flamberge with molten edges, forward-swept horns, dragon-eye gem."""
    sp = Sprite()
    b = sp.layer()
    tip = 27

    def wave(t, ph):
        if t < LS_GUARD + 3 or t > tip - 6:
            return 0.0
        return 1.4 * max(0.0, math.sin((t + ph) * math.pi / 5.0))

    def f(t, s):
        if t < LS_GUARD + 1:
            return False
        return -2 - wave(t, 0) <= s <= 2 + wave(t, 5) and t <= tip - 2.0 * abs(s)

    m = am(f)

    def ex(t, s, tone):
        if tone == 5:
            return P.FLAME[5]
        if tone == 2 or tone == 1:
            return P.FLAME[2]
        if s == 0:
            return P.DRAGON[2]
        if tone == 4:
            return P.DRAGON[4] if s > -2 else P.FLAME[3]
        return P.DRAGON[3]
    shade_blade(b, m, P.DRAGON, center=lambda t: 0, extra=ex)
    outline(b, P.DRAGON)
    grip_layer(sp, -23, -15, P.DRAGON_DARK, band=lambda t, s: (P.GOLD[2] if (t + s) % 4 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 3 and LS_GUARD - 1 <= t <= LS_GUARD + 1)
    for sgn in (-1, 1):
        gm |= sweep_ts([(LS_GUARD, sgn * 2.5), (LS_GUARD + 0.5, sgn * 6.0), (LS_GUARD + 3.5, sgn * 8.5),
                        (LS_GUARD + 7.0, sgn * 8.8)], [1.7, 1.4, 0.9, 0.3])
    shade_bar(g, gm, P.BONE, base=3, grad=1.4)
    outline(g, P.DRAGON.out)
    gl = sp.layer()
    x, y = pos(LS_GUARD, 0)
    eye_gem(gl, x + 0.5, y + 0.5, P.FLAME, P.DRAGON.out)
    outline(gl, P.DRAGON.out)
    p = sp.layer()
    x, y = pos(-25.5, -0.5)
    pm = disc(x + 0.5, y + 0.5, 1.6)
    shade_ball(p, pm, P.FLAME, lo=2)
    outline(p, P.DRAGON.out)
    t2 = sp.layer()
    tm = sweep_ts([(-23.5, -1.5), (-25, -3.2), (-27, -2.6)], [0.8, 0.7, 0.3]) | \
        sweep_ts([(-23.5, 1.5), (-25, 3.2), (-27, 2.6)], [0.8, 0.7, 0.3])
    shade_bar(t2, tm, P.BONE, base=3)
    outline(t2, P.DRAGON.out)
    return sp.done()


def wing_poly(tg, sgn, scale=1.0):
    pts = [(1.5, 1.5), (3.0, 4.5), (5.5, 7.5), (8.0, 9.6), (5.6, 10.0), (4.8, 8.4), (2.2, 9.0),
           (2.6, 6.8), (-0.4, 6.8), (0.8, 4.6), (-1.8, 4.0), (-1.4, 1.5)]
    return poly_ts([(tg + a * scale, sgn * b * scale) for a, b in pts])


def ls6() -> Canvas:
    """Celestial: white blade with gold inlay, feathered gold wing guard, star pommel."""
    sp = Sprite()
    b = sp.layer()
    tip = 28

    def f(t, s):
        if t < LS_GUARD + 1:
            return False
        w = 2 if t < 12 else 2 + 0.9 * max(0.0, math.sin((t - 12) / 11.0 * math.pi))
        return abs(s) <= w + 0.1 and t <= tip - 2.3 * abs(s)

    m = am(f)

    def ex(t, s, tone):
        if s == 0 and LS_GUARD + 2 <= t <= 16:
            return P.HOLY_GOLD[4] if t % 4 == 1 else P.HOLY_GOLD[3]
        return None
    shade_blade(b, m, P.HOLY_WHITE, center=lambda t: 0, extra=ex)
    outline(b, P.HOLY_WHITE)
    grip_layer(sp, -23, -15, P.HOLY_WHITE, band=lambda t, s: (P.HOLY_GOLD[3] if (t + s) % 4 in (0, 1) else None))
    w = sp.layer()
    wm = wing_poly(LS_GUARD, 1) | wing_poly(LS_GUARD, -1) | am(lambda t, s: abs(s) <= 2 and LS_GUARD - 1 <= t <= LS_GUARD + 1)

    def wt(t, s, tone):
        a = abs(s)
        if a >= 3 and t < LS_GUARD + a * 0.62 - 1.2:
            return P.HOLY_WHITE[min(5, tone + 1)]
        return None
    shade_bar(w, wm, P.HOLY_GOLD, base=3, grad=1.2, extra=wt)
    outline(w, P.HOLY_GOLD)
    p = sp.layer()
    x, y = pos(-25.5, -0.5)
    cx, cy = int(round(x)), int(round(y))
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)):
        p.put(cx + dx, cy + dy, P.HOLY_GOLD[4])
    p.put(cx, cy, P.HOLY_GOLD[5])
    p.put(cx + 2, cy, P.HOLY_GOLD[2])
    p.put(cx, cy + 2, P.HOLY_GOLD[2])
    p.put(cx + 1, cy, P.HOLY_GOLD[3])
    outline(p, P.HOLY_GOLD)
    s2 = sp.layer()
    star4(s2, 22, 3, P.HOLY_GOLD, r=1)
    return sp.done()


def ls7() -> Canvas:
    """Abyss: black barbed blade with glowing violet runes, hooked tip, down-swept horns, eye."""
    sp = Sprite()
    b = sp.layer()
    tip = 27

    def tooth(t):
        if t < LS_GUARD + 4 or t > tip - 7:
            return 0.0
        return max(0.0, 2.2 - ((t - 1) % 6) * 0.55)

    def hook(t):
        return 0.0 if t < tip - 9 else -((t - (tip - 9)) / 9.0) ** 2 * 2.2

    def f(t, s):
        if t < LS_GUARD + 1:
            return False
        c = hook(t)
        return -2 + c <= s <= 2 + c + tooth(t) and t <= tip - 2.0 * abs(s - c)

    m = am(f)

    def ex(t, s, tone):
        if tone == 5:
            return P.VOID[3]
        if tone == 4:
            return P.ABYSS[4]
        if tone == 3:
            return P.ABYSS[2]
        return P.ABYSS[1]
    shade_blade(b, m, P.ABYSS, center=hook, extra=ex)
    runes(b, LS_GUARD + 3, tip - 6, hook, P.VOID_GLOW, gap=5)
    outline(b, P.ABYSS)
    grip_layer(sp, -23, -15, P.ABYSS, band=lambda t, s: (P.VOID[3] if (t + s) % 6 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 3 and LS_GUARD - 1 <= t <= LS_GUARD + 1)
    for sgn in (-1, 1):
        gm |= sweep_ts([(LS_GUARD, sgn * 2.5), (LS_GUARD + 0.5, sgn * 5.5), (LS_GUARD - 1.5, sgn * 8.0),
                        (LS_GUARD - 5.0, sgn * 8.6)], [1.7, 1.4, 0.9, 0.3])
    shade_bar(g, gm, P.ABYSS, base=3, grad=1.4)
    outline(g, P.ABYSS)
    gl = sp.layer()
    x, y = pos(LS_GUARD, 0)
    eye_gem(gl, x + 0.5, y + 0.5, P.VOID_GLOW, P.ABYSS.out)
    outline(gl, P.ABYSS.out)
    p = sp.layer()
    pm = poly_ts([(-22, -1.8), (-22, 1.8), (-24.5, 2.6), (-29.5, 0.2), (-24.5, -2.6)])
    shade_bar(p, pm, P.VOID, base=3, grad=1.5)
    outline(p, P.ABYSS.out)
    return sp.done()


# =========================================================================== greatsword
GS_GUARD = -12
GS_TIP = 29


def gs_mask(tip=GS_TIP, half=3, k=1.8, base=GS_GUARD + 1):
    return am(lambda t, s: base <= t and abs(s) <= half and t <= tip - k * abs(s))


def greatsword(tier: int) -> Canvas:
    return [gs1, gs2, gs3, gs4, gs5, gs6, gs7][tier - 1]()


def gs_grip(sp, ramp, band, t0=-25, t1=-14):
    return grip_layer(sp, t0, t1, ramp, band=band, lines=(-1, 0))


def gs1() -> Canvas:
    """Iron: broad plain blade, heavy straight guard, long grip, block pommel."""
    sp = Sprite()
    b = sp.layer()
    shade_blade(b, gs_mask(tip=29, k=1.6), P.IRON, center=lambda t: 0)
    outline(b, P.IRON)
    gs_grip(sp, P.LEATHER, wrap_band(2))
    cross_guard(sp, P.IRON_DARK, reach=8, tg=GS_GUARD)
    p = sp.layer()
    pm = am(lambda t, s: -29 <= t <= -26 and abs(s + 0.5) <= 2)
    shade_bar(p, pm, P.IRON_DARK, base=3)
    outline(p, P.IRON_DARK)
    return sp.done()


def gs2() -> Canvas:
    """Steel zweihander: fuller, parrying lugs on the ricasso, curled guard, banded grip."""
    sp = Sprite()
    b = sp.layer()

    def fuller(t, s, tone):
        if -4 <= t <= 17:
            return {-1: 4, 0: 1, 1: 4}.get(s)
        if GS_GUARD + 1 <= t < -4 and abs(s) <= 2:
            return {-2: 5, -1: 4, 0: 3, 1: 3, 2: 2}.get(s)
        return None
    m = gs_mask(tip=29, k=2.2)
    shade_blade(b, m, P.STEEL, center=lambda t: 0, extra=fuller)
    outline(b, P.STEEL)
    # parrying lugs
    lg = sp.layer()
    lm = np.zeros((N, N), bool)
    for sgn in (-1, 1):
        lm |= sweep_ts([(-5.5, sgn * 3.0), (-6.0, sgn * 5.0), (-7.8, sgn * 5.8)], [1.1, 0.9, 0.5])
    shade_bar(lg, lm, P.STEEL, base=3)
    outline(lg, P.STEEL)
    gs_grip(sp, P.LEATHER_DARK, lambda t, s: (P.STEEL[3] if (t + s) % 5 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 7 and GS_GUARD - 1 <= t <= GS_GUARD + 1)
    for sgn in (-1, 1):
        gm |= sweep_ts([(GS_GUARD, sgn * 6.5), (GS_GUARD - 0.5, sgn * 9.0), (GS_GUARD - 2.8, sgn * 10.0),
                        (GS_GUARD - 4.2, sgn * 8.8)], [1.3, 1.2, 1.0, 0.8])
    shade_bar(g, gm, P.STEEL, base=3, extra=lambda t, s, tone: min(5, tone + (1 if t > GS_GUARD else 0)))
    outline(g, P.STEEL)
    p = sp.layer()
    pm = poly_ts([(-25.5, -1.6), (-25.5, 1.4), (-27.0, 2.6), (-29.3, 0.0), (-27.0, -2.8)])
    shade_bar(p, pm, P.STEEL, base=3, grad=1.4)
    outline(p, P.STEEL)
    return sp.done()


def gs3() -> Canvas:
    """Desert: broad top-heavy falchion blade with clipped point, gold spine and studs,
    sandstone guard with a large turquoise."""
    sp = Sprite()
    b = sp.layer()
    tip = 28

    def lo(t):   # cutting edge bellies out toward the tip
        w = 3.0 + 2.0 * max(0.0, min(1.0, (t + 2) / 22.0))
        if t > tip - 7:
            w -= (t - (tip - 7)) * 0.95
        return -w

    def hi(t):   # straight spine, clipped near the tip
        h = 2.6
        if t > tip - 4:
            h -= (t - (tip - 4)) * 1.4
        return h

    m = am(lambda t, s: GS_GUARD + 1 <= t <= tip and lo(t) - 0.01 <= s <= hi(t) + 0.01)

    def ex(t, s, tone):
        if s >= hi(t) - 1.0 and t < tip - 3:
            return P.GOLD[4] if tone >= 4 else P.GOLD[3]
        if s == -1 and t % 6 == 1 and 0 < t < tip - 8:
            return P.TURQUOISE[4]
        return None
    shade_blade(b, m, P.FROST_STEEL, center=lambda t: -0.5, extra=ex)
    outline(b, P.SANDSTONE.out)
    gs_grip(sp, P.SANDSTONE, wrap_band(2, depth=-1))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 8 and GS_GUARD - 2 <= t <= GS_GUARD + 1 - (1 if abs(s) >= 7 else 0))
    for sgn in (-1, 1):
        gm |= disc_ts(GS_GUARD - 0.5, sgn * 8.6, 1.5)
    shade_bar(g, gm, P.GOLD, base=3, extra=lambda t, s, tone: (P.SANDSTONE[min(5, tone)] if abs(s) <= 6 and t == GS_GUARD - 2 else None))
    outline(g, P.GOLD)
    gl = sp.layer()
    x, y = pos(GS_GUARD - 0.5, 0)
    gem(gl, x + 0.5, y + 0.5, P.TURQUOISE, r=2.0)
    outline(gl, P.GOLD.out)
    p = sp.layer()
    pm = am(lambda t, s: abs(s + 0.5) * 1.3 + abs(t + 27.5) <= 3.0)
    shade_bar(p, pm, P.GOLD, base=3)
    x, y = pos(-27.5, -0.5)
    p.put(int(round(x)), int(round(y)), P.TURQUOISE[4])
    outline(p, P.GOLD)
    return sp.done()


def gs4() -> Canvas:
    """Frost: massive crystal blade with big jagged facets, icicle guard, crystal pommel."""
    sp = Sprite()
    b = sp.layer()
    tip = 29

    def f(t, s):
        if t < GS_GUARD + 1:
            return False
        return abs(s) <= 3 and t <= tip - 2.4 * abs(s)

    m = am(f)
    for tri in [(-9, -2.5, -3, -2.5, 1, -7.5), (-2, 2.5, 4, 2.5, 8, 7.0), (6, -2.5, 11, -2.5, 15, -6.5),
                (12, 2.5, 16, 2.5, 19, 5.0)]:
        m |= poly_ts([(tri[0], tri[1]), (tri[2], tri[3]), (tri[4], tri[5])])

    def ex(t, s, tone):
        if s in (-1, 0) and t < tip - 3 and (t - s) % 7 in (0, 1):
            return 5
        return None
    shade_blade(b, m, P.ICE, center=lambda t: 0, extra=ex)
    outline(b, P.ICE)
    gs_grip(sp, P.ICE_DEEP, lambda t, s: (P.FUR_WHITE[3] if (t + s) % 4 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 4 and GS_GUARD - 2 <= t <= GS_GUARD + 1)
    gm |= sweep_ts([(GS_GUARD - 1, -3.5), (GS_GUARD + 1, -7.5), (GS_GUARD + 5, -10.0)], [1.8, 1.2, 0.3])
    gm |= sweep_ts([(GS_GUARD - 1, 3.5), (GS_GUARD + 0, 7.5), (GS_GUARD + 3, 9.5)], [1.8, 1.2, 0.3])
    gm |= sweep_ts([(GS_GUARD - 1, -3), (GS_GUARD - 4, -6)], [1.2, 0.3])
    gm |= sweep_ts([(GS_GUARD - 1, 3), (GS_GUARD - 3.5, 5.5)], [1.1, 0.3])
    shade_bar(g, gm, P.ICE, base=3, grad=1.5)
    outline(g, P.ICE)
    p = sp.layer()
    pm = poly_ts([(-24.5, -2.2), (-24.5, 2.0), (-27, 2.6), (-30.3, 0.0), (-27, -2.8)])
    shade_bar(p, pm, P.ICE, base=4, grad=1.5)
    outline(p, P.ICE)
    return sp.done()


def gs5() -> Canvas:
    """Flame dragon: fang-shaped blade with a toothed spine and molten edge, dragon-head guard."""
    sp = Sprite()
    b = sp.layer()
    tip = 28

    def bend(t):
        return 0.0 if t < 0 else -2.6 * (t / tip) ** 2

    def lo(t):
        w = 3.2 + 0.6 * math.sin(max(0, t + 10) / 38.0 * math.pi)
        if t > tip - 9:
            w *= max(0.0, (tip - t) / 9.0) ** 0.8
        return bend(t) - w

    def hi(t):
        teeth = 0.0
        if GS_GUARD + 4 < t < tip - 8:
            teeth = max(0.0, 2.0 - ((t + 2) % 5) * 0.6)
        base = 2.4
        if t > tip - 6:
            base -= (t - (tip - 6)) * 0.55
        return bend(t) + base + teeth

    m = am(lambda t, s: GS_GUARD + 1 <= t <= tip and lo(t) - 0.01 <= s <= hi(t) + 0.01)

    def ex(t, s, tone):
        if tone == 5:
            return P.FLAME[5]
        c = bend(t)
        if s < c - 1.5 and tone >= 4:
            return P.FLAME[3]
        if abs(s - c) < 0.6:
            return P.DRAGON[2]
        if tone <= 2:
            return P.DRAGON[1]
        if (t + int(s)) % 4 == 0 and s > c + 0.6:
            return P.DRAGON[2]
        return P.DRAGON[3] if s > c else P.DRAGON[4]
    shade_blade(b, m, P.DRAGON, center=bend, extra=ex)
    outline(b, P.DRAGON)
    gs_grip(sp, P.DRAGON_DARK, lambda t, s: (P.GOLD[2] if (t + s) % 4 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 4 and GS_GUARD - 2 <= t <= GS_GUARD + 1)
    for sgn in (-1, 1):
        gm |= sweep_ts([(GS_GUARD, sgn * 3.5), (GS_GUARD + 0.5, sgn * 7.0), (GS_GUARD + 4.0, sgn * 10.0),
                        (GS_GUARD + 8.0, sgn * 10.2)], [2.0, 1.6, 1.0, 0.3])
    shade_bar(g, gm, P.DRAGON_DARK, base=3, grad=1.4,
              extra=lambda t, s, tone: (P.BONE[min(5, tone + 1)] if abs(s) >= 8 else None))
    outline(g, P.DRAGON.out)
    gl = sp.layer()
    x, y = pos(GS_GUARD - 0.5, 0)
    eye_gem(gl, x + 0.5, y + 0.5, P.FLAME, P.DRAGON.out)
    outline(gl, P.DRAGON.out)
    p = sp.layer()
    pm = poly_ts([(-24.5, -2.0), (-24.5, 2.0), (-26.5, 2.8), (-27.5, 1.0), (-30.0, 1.6), (-28.5, -0.4),
                  (-30.0, -2.2), (-27.0, -2.8)])
    shade_bar(p, pm, P.FLAME, base=3, grad=1.4)
    outline(p, P.DRAGON.out)
    return sp.done()


def gs6() -> Canvas:
    """Celestial: white blade with gold edges and a cross inlay, great gold wings, halo pommel."""
    sp = Sprite()
    b = sp.layer()
    tip = 29

    def f(t, s):
        if t < GS_GUARD + 1:
            return False
        w = 3.0 if t < 16 else 3.0 - (t - 16) * 0.12
        return abs(s) <= w + 0.05 and t <= tip - 2.2 * abs(s)

    m = am(f)

    def ex(t, s, tone):
        if tone == 2:
            return P.HOLY_GOLD[3]
        if s == 0 and GS_GUARD + 2 <= t <= 20:
            return P.HOLY_GOLD[4]
        if t in (-4, -3) and abs(s) <= 2:
            return P.HOLY_GOLD[4] if t == -3 else P.HOLY_GOLD[3]
        return None
    shade_blade(b, m, P.HOLY_WHITE, center=lambda t: 0, extra=ex)
    outline(b, P.HOLY_WHITE)
    gs_grip(sp, P.HOLY_WHITE, lambda t, s: (P.HOLY_GOLD[3] if (t + s) % 4 in (0, 1) else None))
    w = sp.layer()
    wm = wing_poly(GS_GUARD, 1, 1.2) | wing_poly(GS_GUARD, -1, 1.2) | \
        am(lambda t, s: abs(s) <= 3 and GS_GUARD - 1 <= t <= GS_GUARD + 1)

    def wt(t, s, tone):
        a = abs(s)
        if a >= 3 and t < GS_GUARD + a * 0.62 - 1.4:
            return P.HOLY_WHITE[min(5, tone + 1)]
        return None
    shade_bar(w, wm, P.HOLY_GOLD, base=3, grad=1.2, extra=wt)
    outline(w, P.HOLY_GOLD)
    gl = sp.layer()
    x, y = pos(GS_GUARD, 0)
    gem(gl, x + 0.5, y + 0.5, P.SKY, r=1.4)
    outline(gl, P.HOLY_GOLD.out)
    p = sp.layer()
    x, y = pos(-27.3, -0.5)
    ring = disc(x + 0.5, y + 0.5, 2.3) & ~disc(x + 0.5, y + 0.5, 1.0)
    shade_bar(p, ring, P.HOLY_GOLD, base=4)
    outline(p, P.HOLY_GOLD)
    s2 = sp.layer()
    star4(s2, 21, 3, P.HOLY_GOLD, r=1)
    star4(s2, 28, 11, P.HOLY_GOLD, r=1)
    return sp.done()


def gs7() -> Canvas:
    """Abyss: huge black blade with forked tip, glowing violet cracks, spiked guard with eye."""
    sp = Sprite()
    b = sp.layer()
    tip = 29

    def f(t, s):
        if t < GS_GUARD + 1:
            return False
        w = 3.0 + (1.0 if (t % 7) in (0, 1, 2) and -6 < t < 18 else 0.0)
        inside = abs(s) <= w and t <= tip - 1.6 * abs(s)
        # fork notch at the tip
        notch = t > tip - 9 and abs(s) <= (t - (tip - 9)) * 0.35
        return inside and not notch

    m = am(f)
    for sgn in (-1, 1):
        m |= sweep_ts([(tip - 9, sgn * 2.2), (tip - 4, sgn * 3.0), (tip - 0.5, sgn * 3.4)], [1.4, 1.0, 0.3])

    def ex(t, s, tone):
        if tone == 5:
            return P.VOID[3]
        if tone == 4:
            return P.ABYSS[3]
        if tone == 3:
            return P.ABYSS[2]
        return P.ABYSS[1]
    shade_blade(b, m, P.ABYSS, center=lambda t: 0, extra=ex)
    # glowing crack running up the blade
    tcur, scur = GS_GUARD + 2, 0
    k = 0
    while tcur < tip - 9:
        put(b, tcur, scur, P.VOID_GLOW[3] if k % 3 else P.VOID_GLOW[4])
        k += 1
        tcur += 1
        scur = scur + (1 if (k % 5) in (1, 2) else -1)
        scur = max(-1, min(1, scur))
        if (tcur + scur) % 2 == 0:
            scur += 1 if scur <= 0 else -1
    outline(b, P.ABYSS)
    gs_grip(sp, P.ABYSS, lambda t, s: (P.VOID[3] if (t + s) % 6 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 4 and GS_GUARD - 2 <= t <= GS_GUARD + 1)
    for sgn in (-1, 1):
        gm |= sweep_ts([(GS_GUARD - 0.5, sgn * 3.5), (GS_GUARD + 0.5, sgn * 7.0), (GS_GUARD - 1.5, sgn * 10.0),
                        (GS_GUARD - 6.0, sgn * 10.5)], [2.0, 1.6, 1.0, 0.3])
        gm |= sweep_ts([(GS_GUARD + 1, sgn * 5.5), (GS_GUARD + 4.5, sgn * 6.2)], [1.0, 0.3])
    shade_bar(g, gm, P.ABYSS, base=3, grad=1.4)
    outline(g, P.ABYSS)
    gl = sp.layer()
    x, y = pos(GS_GUARD - 0.5, 0)
    eye_gem(gl, x + 0.5, y + 0.5, P.VOID_GLOW, P.ABYSS.out)
    outline(gl, P.ABYSS.out)
    p = sp.layer()
    pm = poly_ts([(-24.5, -2.0), (-24.5, 2.0), (-26.5, 3.4), (-27.2, 1.0), (-30.3, 0.0), (-27.2, -1.2),
                  (-26.5, -3.6)])
    shade_bar(p, pm, P.VOID, base=3, grad=1.5)
    outline(p, P.ABYSS.out)
    return sp.done()


# =========================================================================== twin blades
TB_GUARD = -15
TB_TIP = 15


def tb_mask(tip=TB_TIP, half=1, k=2.0, base=TB_GUARD + 1):
    return am(lambda t, s: base <= t and abs(s) <= half and t <= tip - k * abs(s))


def twin_blades(tier: int) -> Canvas:
    d = [tb1, tb2, tb3, tb4, tb5, tb6, tb7][tier - 1]
    front = d()
    back = Canvas(N)
    back.a = front.a[:, ::-1].copy()
    out = Canvas(N)
    out.paste(back)
    out.paste(front)
    return out


def tb_grip(sp, ramp, band, t0=-23, t1=-17):
    return grip_layer(sp, t0, t1, ramp, band=band)


def tb1() -> Canvas:
    sp = Sprite()
    b = sp.layer()
    shade_blade(b, tb_mask(), P.IRON, center=lambda t: 0)
    outline(b, P.IRON)
    tb_grip(sp, P.LEATHER, wrap_band(2))
    cross_guard(sp, P.IRON_DARK, reach=4, tg=TB_GUARD, thick=1)
    pommel_disc(sp, -24.8, -0.5, 1.4, P.IRON_DARK)
    return sp.done()


def tb2() -> Canvas:
    sp = Sprite()
    b = sp.layer()
    m = am(lambda t, s: TB_GUARD + 1 <= t and abs(s) <= 1.5 and t <= TB_TIP + 1 - 2.2 * abs(s))

    def ex(t, s, tone):
        if s == 0 and t < 4:
            return 1
        return None
    shade_blade(b, m, P.STEEL, center=lambda t: 0, extra=ex)
    outline(b, P.STEEL)
    tb_grip(sp, P.LEATHER_DARK, lambda t, s: (P.SILVER[3] if (t + s) % 4 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 3 and TB_GUARD - 1 <= t <= TB_GUARD + 1)
    for sgn in (-1, 1):
        gm |= sweep_ts([(TB_GUARD, sgn * 3.0), (TB_GUARD + 1.0, sgn * 5.0), (TB_GUARD + 2.8, sgn * 5.4)], [1.1, 1.0, 0.8])
    shade_bar(g, gm, P.STEEL, base=3, extra=lambda t, s, tone: min(5, tone + (1 if t > TB_GUARD else 0)))
    outline(g, P.STEEL)
    p = sp.layer()
    x, y = pos(-25.0, -0.5)
    ring = disc(x + 0.5, y + 0.5, 1.9) & ~disc(x + 0.5, y + 0.5, 0.6)
    shade_bar(p, ring, P.STEEL, base=3)
    outline(p, P.STEEL)
    return sp.done()


def tb3() -> Canvas:
    """Jambiya: curved double-edged dagger, gold hilt, turquoise, crescent pommel."""
    sp = Sprite()
    b = sp.layer()
    tip = 14

    def bend(t):
        return 0.0 if t < -8 else 2.6 * ((t + 8) / (tip + 8)) ** 2

    def half(t):
        h = 2.0
        if t > tip - 8:
            h *= max(0.0, (tip - t) / 8.0) ** 0.8
        return h

    m = am(lambda t, s: TB_GUARD + 1 <= t <= tip and abs(s - bend(t)) <= half(t) + 0.05)

    def ex(t, s, tone):
        if abs(s - bend(t)) < 0.6 and t < tip - 3:
            return P.GOLD[3] if t % 2 else P.GOLD[4]
        return None
    shade_blade(b, m, P.FROST_STEEL, center=bend, extra=ex)
    outline(b, P.SANDSTONE.out)
    tb_grip(sp, P.SANDSTONE, wrap_band(2, depth=-1))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 3 and TB_GUARD - 1 <= t <= TB_GUARD + 1)
    for sgn in (-1, 1):
        gm |= disc_ts(TB_GUARD, sgn * 3.6, 1.3)
    shade_bar(g, gm, P.GOLD, base=3)
    outline(g, P.GOLD)
    gl = sp.layer()
    x, y = pos(TB_GUARD, 0)
    gem(gl, x + 0.5, y + 0.5, P.TURQUOISE, r=1.3)
    outline(gl, P.GOLD.out)
    p = sp.layer()
    pm = sweep_ts([(-23.0, -3.6), (-25.6, -1.2), (-25.6, 1.2), (-23.0, 3.6)], [0.9, 1.3, 1.3, 0.9])
    shade_bar(p, pm, P.GOLD, base=3)
    outline(p, P.GOLD)
    return sp.done()


def tb4() -> Canvas:
    """Icicle dagger: tapering crystal with side shards."""
    sp = Sprite()
    b = sp.layer()
    tip = 16
    m = am(lambda t, s: TB_GUARD + 1 <= t and abs(s) <= 2 and t <= tip - 3.0 * abs(s))
    m |= poly_ts([(-10, -1.5), (-6, -1.5), (-3, -4.6)])
    m |= poly_ts([(-4, 1.5), (0, 1.5), (3, 4.2)])

    def ex(t, s, tone):
        if s == 0 and t < tip - 2:
            return 5 if (t % 6) in (1, 3) else 4
        return None
    shade_blade(b, m, P.ICE, center=lambda t: 0, extra=ex)
    outline(b, P.ICE)
    tb_grip(sp, P.ICE_DEEP, lambda t, s: (P.FUR_WHITE[3] if (t + s) % 4 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 2 and TB_GUARD - 1 <= t <= TB_GUARD + 1)
    gm |= sweep_ts([(TB_GUARD, -2), (TB_GUARD + 1.5, -4.5), (TB_GUARD + 3.5, -5.8)], [1.3, 1.0, 0.3])
    gm |= sweep_ts([(TB_GUARD, 2), (TB_GUARD + 0.5, 4.5), (TB_GUARD + 2.0, 5.5)], [1.3, 1.0, 0.3])
    shade_bar(g, gm, P.ICE, base=3, grad=1.5)
    outline(g, P.ICE)
    p = sp.layer()
    pm = poly_ts([(-22, -1.6), (-22, 1.6), (-24, 2.0), (-27.5, 0.2), (-24, -2.0)])
    shade_bar(p, pm, P.ICE, base=4, grad=1.5)
    outline(p, P.ICE)
    return sp.done()


def tb5() -> Canvas:
    """Dragon kris: wavy red blade with molten edge, claw guard."""
    sp = Sprite()
    b = sp.layer()
    tip = 15

    def c(t):
        return 0.0 if t < TB_GUARD + 3 or t > tip - 4 else 1.3 * math.sin((t + 2) * math.pi / 5.0)

    m = am(lambda t, s: TB_GUARD + 1 <= t and abs(s - c(t)) <= 1.6 and t <= tip - 2.0 * abs(s))

    def ex(t, s, tone):
        if tone == 5:
            return P.FLAME[5]
        if tone == 4:
            return P.FLAME[3]
        if tone <= 2:
            return P.DRAGON[1]
        return P.DRAGON[3]
    shade_blade(b, m, P.DRAGON, center=c, extra=ex)
    outline(b, P.DRAGON)
    tb_grip(sp, P.DRAGON_DARK, lambda t, s: (P.GOLD[2] if (t + s) % 4 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 2 and TB_GUARD - 1 <= t <= TB_GUARD + 1)
    for sgn in (-1, 1):
        gm |= sweep_ts([(TB_GUARD, sgn * 2.0), (TB_GUARD + 0.5, sgn * 4.5), (TB_GUARD + 3.0, sgn * 6.0),
                        (TB_GUARD + 5.0, sgn * 5.6)], [1.4, 1.1, 0.7, 0.3])
    shade_bar(g, gm, P.BONE, base=3, grad=1.3)
    outline(g, P.DRAGON.out)
    gl = sp.layer()
    x, y = pos(TB_GUARD, 0)
    gem(gl, x + 0.5, y + 0.5, P.FLAME, r=1.2)
    outline(gl, P.DRAGON.out)
    p = sp.layer()
    x, y = pos(-25.0, -0.5)
    shade_ball(p, disc(x + 0.5, y + 0.5, 1.5), P.FLAME, lo=2)
    outline(p, P.DRAGON.out)
    return sp.done()


def tb6() -> Canvas:
    """Celestial feather blade: leaf/feather shaped white blade with gold quill, wing guard."""
    sp = Sprite()
    b = sp.layer()
    tip = 16

    def half(t):
        u = (t - TB_GUARD) / (tip - TB_GUARD)
        return 0.9 + 1.6 * math.sin(min(1.0, u) * math.pi) ** 0.8

    m = am(lambda t, s: TB_GUARD + 1 <= t <= tip and abs(s) <= half(t))
    # feather barb notches on the lower-right side
    for tn in (-6, 0, 6):
        m &= ~am(lambda t, s, tn=tn: tn <= t <= tn + 1 and s >= 1.0 + (t - tn))

    def ex(t, s, tone):
        if s == 0 and t < tip - 2:
            return P.HOLY_GOLD[4] if t % 4 == 1 else P.HOLY_GOLD[3]
        return None
    shade_blade(b, m, P.HOLY_WHITE, center=lambda t: 0, extra=ex)
    outline(b, P.HOLY_WHITE)
    tb_grip(sp, P.HOLY_WHITE, lambda t, s: (P.HOLY_GOLD[3] if (t + s) % 4 in (0, 1) else None))
    w = sp.layer()
    wm = wing_poly(TB_GUARD, 1, 0.65) | wing_poly(TB_GUARD, -1, 0.65) | \
        am(lambda t, s: abs(s) <= 1 and TB_GUARD - 1 <= t <= TB_GUARD + 1)
    shade_bar(w, wm, P.HOLY_GOLD, base=3, grad=1.2)
    outline(w, P.HOLY_GOLD)
    p = sp.layer()
    x, y = pos(-24.8, -0.5)
    cx, cy = int(round(x)), int(round(y))
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        p.put(cx + dx, cy + dy, P.HOLY_GOLD[4])
    p.put(cx, cy, P.HOLY_GOLD[5])
    outline(p, P.HOLY_GOLD)
    return sp.done()


def tb7() -> Canvas:
    """Abyss: hooked, barbed black blade with violet runes, spiked guard."""
    sp = Sprite()
    b = sp.layer()
    tip = 15

    def hook(t):
        return 0.0 if t < tip - 8 else -((t - (tip - 8)) / 8.0) ** 2 * 2.6

    def tooth(t):
        if t < TB_GUARD + 3 or t > tip - 6:
            return 0.0
        return max(0.0, 2.0 - ((t + 1) % 5) * 0.6)

    m = am(lambda t, s: TB_GUARD + 1 <= t and -1.5 + hook(t) <= s <= 1.5 + hook(t) + tooth(t)
           and t <= tip - 2.0 * abs(s - hook(t)))

    def ex(t, s, tone):
        if tone == 5:
            return P.VOID[3]
        if tone == 4:
            return P.ABYSS[3]
        if tone == 3:
            return P.ABYSS[2]
        return P.ABYSS[1]
    shade_blade(b, m, P.ABYSS, center=hook, extra=ex)
    for tt in range(TB_GUARD + 3, tip - 5, 3):
        ss = 0 if tt % 2 else -1
        put(b, tt, ss, P.VOID_GLOW[3] if tt % 2 else P.VOID_GLOW[4])
    outline(b, P.ABYSS)
    tb_grip(sp, P.ABYSS, lambda t, s: (P.VOID[3] if (t + s) % 6 in (0, 1) else None))
    g = sp.layer()
    gm = am(lambda t, s: abs(s) <= 2 and TB_GUARD - 1 <= t <= TB_GUARD + 1)
    for sgn in (-1, 1):
        gm |= sweep_ts([(TB_GUARD, sgn * 2.0), (TB_GUARD + 0.5, sgn * 4.0), (TB_GUARD - 1.5, sgn * 6.0),
                        (TB_GUARD - 4.0, sgn * 6.2)], [1.4, 1.1, 0.7, 0.3])
    shade_bar(g, gm, P.ABYSS, base=3, grad=1.3)
    outline(g, P.ABYSS)
    gl = sp.layer()
    x, y = pos(TB_GUARD, 0)
    gem(gl, x + 0.5, y + 0.5, P.VOID_GLOW, r=1.2)
    outline(gl, P.ABYSS.out)
    p = sp.layer()
    pm = poly_ts([(-22.5, -1.6), (-22.5, 1.6), (-24.0, 2.4), (-27.5, 0.0), (-24.0, -2.4)])
    shade_bar(p, pm, P.VOID, base=3, grad=1.5)
    outline(p, P.ABYSS.out)
    return sp.done()


# =========================================================================== spear
def spear(tier: int) -> Canvas:
    return [sp1, sp2, sp3, sp4, sp5, sp6, sp7][tier - 1]()


def shaft_layer(sp, t0, t1, ramp, band=None, lines=(-1, 0)):
    l = sp.layer()
    m = am(lambda t, s: t0 <= t <= t1 and s in lines)
    shade_shaft(l, m, ramp, band=band)
    outline(l, ramp)
    return l


def collar(sp, t0, t1, half, ramp, extra=None):
    l = sp.layer()
    m = am(lambda t, s: t0 <= t <= t1 and abs(s + 0.5) <= half)
    shade_bar(l, m, ramp, base=3, extra=extra)
    outline(l, ramp)
    return l


def head_layer(sp, m, ramp, center=lambda t: 0, extra=None, out=None):
    h = sp.layer()
    shade_blade(h, m, ramp, center=center, extra=extra)
    outline(h, out or ramp)
    return h


def sp1() -> Canvas:
    sp = Sprite()
    shaft_layer(sp, -28, 10, P.WOOD)
    collar(sp, -29, -27, 1.5, P.IRON_DARK)

    def f(t, s):
        if t < 9 or t > 29:
            return False
        u = (t - 9) / 20.0
        w = 2.8 * math.sin(min(1.0, u * 1.25) * math.pi * 0.62) if u < 0.8 else 2.8 * 0.93 * (1 - u) / 0.2
        return abs(s) <= max(0.6, w) and t <= 29 - 2.0 * abs(s)
    head_layer(sp, am(f), P.IRON)
    collar(sp, 7, 10, 1.6, P.IRON_DARK)
    return sp.done()


def sp2() -> Canvas:
    sp = Sprite()
    shaft_layer(sp, -27, 9, P.WOOD, band=lambda t, s: (P.STEEL[3] if t in (-16, -15, -4, -3) else None))
    b = sp.layer()
    bm = am(lambda t, s: -30 <= t <= -27 and abs(s + 0.5) <= max(0.5, 1.5 - (-27 - t) * 0.5))
    shade_bar(b, bm, P.STEEL, base=3)
    outline(b, P.STEEL)

    def f(t, s):
        if t < 10 or t > 30:
            return False
        if t < 15:
            w = 1.0 + (t - 10) * 0.42
        else:
            w = 3.1 - (t - 15) * 0.21
        return abs(s) <= w
    def ridge(t, s, tone):
        if s == 0:
            return 4
        if s == 1:
            return 2
        return None
    head_layer(sp, am(f), P.STEEL, extra=ridge)
    # boar-spear lugs
    l = sp.layer()
    lm = am(lambda t, s: 6 <= t <= 7 and abs(s + 0.5) <= 4.5)
    for sgn in (-1, 1):
        lm |= disc_ts(6.5, sgn * 4.8 - 0.5, 1.0)
    shade_bar(l, lm, P.STEEL, base=3, extra=lambda t, s, tone: min(5, tone + (1 if t == 7 else 0)))
    outline(l, P.STEEL)
    collar(sp, 8, 10, 1.6, P.STEEL)
    return sp.done()


def sp3() -> Canvas:
    """Desert partisan: broad head with curled side lobes, gold socket, turquoise, red tassel."""
    sp = Sprite()
    shaft_layer(sp, -27, 8, P.SANDSTONE, band=lambda t, s: (P.GOLD[3] if t in (-18, -17, -6, -5) else None))
    collar(sp, -30, -27, 1.6, P.GOLD)

    def f(t, s):
        if t < 10 or t > 29:
            return False
        w = 1.6 + 0.8 * math.sin((t - 10) / 19.0 * math.pi)
        return abs(s) <= w and t <= 29 - 2.0 * abs(s)
    m = am(f)
    for sgn in (-1, 1):
        m |= sweep_ts([(10.5, sgn * 1.5), (12.0, sgn * 4.5), (15.0, sgn * 5.5), (16.5, sgn * 4.2)], [1.3, 1.2, 0.9, 0.6])

    def ex(t, s, tone):
        if s == 0 and 12 <= t <= 24:
            return P.GOLD[4] if t % 2 else P.GOLD[3]
        return None
    head_layer(sp, m, P.FROST_STEEL, extra=ex, out=P.SANDSTONE.out)
    c = collar(sp, 6, 10, 1.8, P.GOLD)
    x, y = pos(8, -0.5)
    g = sp.layer()
    gem(g, x + 0.5, y + 0.5, P.TURQUOISE, r=1.2)
    # tassel hanging below the socket
    ta = sp.layer()
    x, y = pos(6.5, 1.5)
    tm = np.zeros((N, N), bool)
    for dy in range(0, 5):
        for dx in range(0, 2 + (1 if dy >= 3 else 0)):
            xx, yy = int(x) + dx, int(y) + dy
            if 0 <= xx < N and 0 <= yy < N:
                tm[yy, xx] = True
    shade_bar(ta, tm, P.CLOTH_RED, base=3, grad=0.5)
    for dy in range(2, 5, 2):
        ta.put(int(x) + 1, int(y) + dy, P.CLOTH_RED[2])
    outline(ta, P.CLOTH_RED)
    sp.layers.remove(ta)
    sp.layers.insert(0, ta)
    return sp.done()


def sp4() -> Canvas:
    """Frost: long icicle head with crystal barbs, frosted socket, crystal butt."""
    sp = Sprite()
    shaft_layer(sp, -26, 8, P.ICE_DEEP, band=lambda t, s: (P.FUR_WHITE[3] if (t + s) % 6 in (0, 1) else None))
    m = am(lambda t, s: 8 <= t and abs(s) <= 2 and t <= 30 - 2.6 * abs(s))
    m |= poly_ts([(9, -1.5), (13, -1.5), (16, -5.0)])
    m |= poly_ts([(13, 1.5), (17, 1.5), (20, 4.5)])

    def ex(t, s, tone):
        if s == 0 and t < 27:
            return 5 if (t % 6) in (1, 3) else 4
        return None
    head_layer(sp, m, P.ICE, extra=ex)
    c = sp.layer()
    cm = am(lambda t, s: 5 <= t <= 9 and abs(s + 0.5) <= 2.0)
    cm |= sweep_ts([(7, -2), (9.5, -4.0)], [1.0, 0.3]) | sweep_ts([(6, 2), (8.0, 3.8)], [1.0, 0.3])
    shade_bar(c, cm, P.ICE, base=3, grad=1.4)
    outline(c, P.ICE)
    p = sp.layer()
    pm = poly_ts([(-25.5, -1.8), (-25.5, 1.6), (-27.5, 2.0), (-30.5, 0.0), (-27.5, -2.2)])
    shade_bar(p, pm, P.ICE, base=4, grad=1.5)
    outline(p, P.ICE)
    return sp.done()


def sp5() -> Canvas:
    """Flame dragon glaive: broad curved single-edged blade, back spike, scaled red shaft."""
    sp = Sprite()
    shaft_layer(sp, -27, 7, P.DRAGON_DARK, band=lambda t, s: (P.GOLD[2] if (t + s) % 4 in (0, 1) else None))
    collar(sp, -30, -27, 1.6, P.OBSIDIAN)

    def lo(t):   # big curved edge on the upper-left
        u = (t - 7) / 22.0
        return -(1.5 + 4.2 * math.sin(min(1.0, u) * math.pi) ** 0.9) - 1.6 * u * u

    def hi(t):
        u = (t - 7) / 22.0
        return 1.5 - 3.2 * u * u

    m = am(lambda t, s: 7 <= t <= 29 and lo(t) <= s <= hi(t) and t <= 29 - 0.0 * s)
    m |= sweep_ts([(10, 1.0), (11.5, 4.0), (10.0, 6.5)], [1.4, 0.9, 0.3])

    def ex(t, s, tone):
        if tone == 5:
            return P.FLAME[5]
        if tone == 4 and s < lo(t) + 2.2:
            return P.FLAME[3]
        if tone <= 2:
            return P.DRAGON[1]
        if (t + s) % 4 == 1 and s > lo(t) + 2:
            return P.DRAGON[2]
        return None
    head_layer(sp, m, P.DRAGON, center=lambda t: (lo(t) + hi(t)) / 2, extra=ex)
    collar(sp, 5, 8, 1.8, P.OBSIDIAN)
    g = sp.layer()
    x, y = pos(6.5, -0.5)
    eye_gem(g, x + 0.5, y + 0.5, P.FLAME, P.DRAGON.out)
    outline(g, P.DRAGON.out)
    return sp.done()


def sp6() -> Canvas:
    """Celestial lance: long gleaming point, gold wings at the socket, star butt."""
    sp = Sprite()
    shaft_layer(sp, -26, 9, P.HOLY_WHITE, band=lambda t, s: (P.HOLY_GOLD[3] if t in (-16, -15, -5, -4) else None))
    m = am(lambda t, s: 10 <= t and abs(s) <= 2 and t <= 30 - 2.4 * abs(s))

    def ex(t, s, tone):
        if s == 0 and t < 24:
            return P.HOLY_GOLD[4] if t % 4 == 1 else P.HOLY_GOLD[3]
        return None
    head_layer(sp, m, P.HOLY_WHITE, extra=ex)
    w = sp.layer()
    wm = wing_poly(8, 1, 0.75) | wing_poly(8, -1, 0.75) | am(lambda t, s: 6 <= t <= 10 and abs(s + 0.5) <= 2.0)
    shade_bar(w, wm, P.HOLY_GOLD, base=3, grad=1.2)
    outline(w, P.HOLY_GOLD)
    p = sp.layer()
    x, y = pos(-27.5, -0.5)
    cx, cy = int(round(x)), int(round(y))
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (0, -2)):
        p.put(cx + dx, cy + dy, P.HOLY_GOLD[4])
    p.put(cx, cy, P.HOLY_GOLD[5])
    outline(p, P.HOLY_GOLD)
    s2 = sp.layer()
    star4(s2, 22, 3, P.HOLY_GOLD, r=1)
    return sp.done()


def sp7() -> Canvas:
    """Abyss harpoon: barbed black head with back hooks and runes, banded black shaft."""
    sp = Sprite()
    shaft_layer(sp, -27, 9, P.ABYSS, band=lambda t, s: (P.VOID[3] if (t + s) % 7 in (0, 1) else None))
    m = am(lambda t, s: 9 <= t and abs(s) <= 2 and t <= 29 - 1.8 * abs(s))
    for sgn in (-1, 1):
        m |= sweep_ts([(14, sgn * 1.5), (12.5, sgn * 4.0), (9.0, sgn * 5.2)], [1.3, 0.9, 0.3])
        m |= sweep_ts([(21, sgn * 1.0), (19.5, sgn * 3.2), (17.0, sgn * 4.0)], [1.0, 0.7, 0.3])

    def ex(t, s, tone):
        if tone == 5:
            return P.VOID[3]
        if tone == 4:
            return P.ABYSS[3]
        if tone == 3:
            return P.ABYSS[2]
        return P.ABYSS[1]
    h = head_layer(sp, m, P.ABYSS, extra=ex)
    for tt, ss in ((12, -1), (15, 0), (18, -1), (21, 0), (24, -1)):
        put(h, tt, ss, P.VOID_GLOW[4] if tt % 2 else P.VOID_GLOW[3])
    collar(sp, 7, 10, 1.8, P.ABYSS)
    g = sp.layer()
    x, y = pos(8.5, -0.5)
    gem(g, x + 0.5, y + 0.5, P.VOID_GLOW, r=1.1)
    p = sp.layer()
    pm = poly_ts([(-25.5, -1.8), (-25.5, 1.6), (-27.0, 2.4), (-30.5, 0.0), (-27.0, -2.6)])
    shade_bar(p, pm, P.VOID, base=3, grad=1.5)
    outline(p, P.ABYSS.out)
    return sp.done()


# =========================================================================== staff
ORB_T = 21.0


def staff(tier: int) -> Canvas:
    return [st1, st2, st3, st4, st5, st6, st7][tier - 1]()


def orb(sp, t, s, r, ramp, out=None, glow=None):
    l = sp.layer()
    x, y = pos(t, s)
    m = disc(x + 0.5, y + 0.5, r)
    shade_ball(l, m, ramp, lo=1)
    if glow is not None:
        l.outline(glow)
    outline(l, out or ramp)
    return l, m


def st1() -> Canvas:
    """Iron: wooden staff, iron cap with two prongs around a small grey-blue orb."""
    sp = Sprite()
    shaft_layer(sp, -29, 13, P.WOOD, band=lambda t, s: (-1 if t in (-12, -11, -10) else None))
    orb(sp, ORB_T, -0.5, 2.6, P.STEEL_DARK)
    pr = sp.layer()
    pm = am(lambda t, s: 12 <= t <= 15 and abs(s + 0.5) <= 1.5)
    for sgn in (-1, 1):
        pm |= sweep_ts([(14, sgn * 1.6 - 0.5), (17, sgn * 4.6 - 0.5), (21.5, sgn * 4.6 - 0.5)], [0.9, 0.8, 0.6])
    shade_bar(pr, pm, P.IRON, base=3)
    outline(pr, P.IRON)
    return sp.done()


def st2() -> Canvas:
    """Steel: banded staff, three-claw steel head with a sapphire orb."""
    sp = Sprite()
    shaft_layer(sp, -29, 12, P.WOOD, band=lambda t, s: (P.STEEL[3] if t in (-20, -19, -8, -7, 4, 5) else None))
    cap = sp.layer()
    cm = am(lambda t, s: -30 <= t <= -27 and abs(s + 0.5) <= 1.5)
    shade_bar(cap, cm, P.STEEL, base=3)
    outline(cap, P.STEEL)
    orb(sp, ORB_T, -0.5, 3.1, P.SKY)
    cl = sp.layer()
    m = am(lambda t, s: 11 <= t <= 15 and abs(s + 0.5) <= 1.8 - (0.6 if t == 11 else 0))
    for sgn in (-1, 1):
        m |= sweep_ts([(14.5, sgn * 1.8 - 0.5), (17.5, sgn * 5.6 - 0.5), (22.0, sgn * 5.6 - 0.5), (24.5, sgn * 3.2 - 0.5)],
                      [1.1, 1.0, 0.8, 0.4])
    m |= sweep_ts([(15, -0.5), (17.0, -0.5)], [0.9, 0.6])
    shade_bar(cl, m, P.STEEL, base=3)
    outline(cl, P.STEEL)
    return sp.done()


def st3() -> Canvas:
    """Desert: gold crescent cradling a turquoise orb, sandstone shaft with gold bands."""
    sp = Sprite()
    shaft_layer(sp, -29, 12, P.SANDSTONE, band=lambda t, s: (P.GOLD[3] if t in (-20, -19, -8, -7) else None))
    cap = sp.layer()
    cm = am(lambda t, s: -30 <= t <= -27 and abs(s + 0.5) <= 1.6)
    shade_bar(cap, cm, P.GOLD, base=3)
    outline(cap, P.GOLD)
    orb(sp, ORB_T - 0.5, -0.5, 2.8, P.TURQUOISE)
    cr = sp.layer()
    outer = am(lambda t, s: (t - ORB_T + 1.5) ** 2 + (s + 0.5) ** 2 <= 9.2 ** 2)
    inner = am(lambda t, s: (t - ORB_T - 1.0) ** 2 + (s + 0.5) ** 2 <= 7.4 ** 2)
    cm = outer & ~inner & am(lambda t, s: t >= 11)
    cm |= am(lambda t, s: 10 <= t <= 13 and abs(s + 0.5) <= 1.6)
    shade_bar(cr, cm, P.GOLD, base=3, grad=1.2)
    outline(cr, P.GOLD)
    # pendant charm
    pe = sp.layer()
    x, y = pos(11.5, 2.5)
    for dy in range(0, 3):
        pe.put(int(x), int(y) + dy, P.GOLD[3])
    pe.put(int(x), int(y) + 3, P.TURQUOISE[4])
    outline(pe, P.GOLD)
    sp.layers.remove(pe)
    sp.layers.insert(0, pe)
    return sp.done()


def st4() -> Canvas:
    """Frost: ice shards bloom around a pale glowing orb, frosted shaft."""
    sp = Sprite()
    shaft_layer(sp, -29, 12, P.FROST_STEEL, band=lambda t, s: (P.ICE[2] if (t + s) % 6 in (0, 1) else None))
    cap = sp.layer()
    cm = poly_ts([(-26.5, -1.8), (-26.5, 1.0), (-28.5, 1.6), (-31, -0.5), (-28.5, -2.6)])
    shade_bar(cap, cm, P.ICE, base=4, grad=1.4)
    outline(cap, P.ICE)
    sh = sp.layer()
    m = np.zeros((N, N), bool)
    for (a, b2, ln, w) in [((12, -1.0), (19, -7.0), 0, 1.4), ((13, 0.0), (17, 6.0), 0, 1.3),
                           ((15, -1.0), (27, -3.5), 0, 1.2), ((15, 0.0), (26, 3.0), 0, 1.2),
                           ((16, -0.5), (29, -0.5), 0, 1.0)]:
        m |= sweep_ts([a, ((a[0] + b2[0]) / 2, (a[1] + b2[1]) / 2), b2], [w, w * 0.8, 0.2])
    m |= am(lambda t, s: 11 <= t <= 14 and abs(s + 0.5) <= 1.8)
    shade_bar(sh, m, P.ICE, base=3, grad=1.6)
    outline(sh, P.ICE)
    orb(sp, ORB_T - 1.0, -0.5, 2.6, P.ICE, out=P.ICE[1])
    return sp.done()


def st5() -> Canvas:
    """Flame dragon: bone talons clutching a blazing orb with flame tongues, scaled red shaft."""
    sp = Sprite()
    shaft_layer(sp, -29, 12, P.DRAGON_DARK, band=lambda t, s: (P.GOLD[2] if (t + s) % 4 in (0, 1) else None))
    cap = sp.layer()
    cm = am(lambda t, s: -30 <= t <= -27 and abs(s + 0.5) <= 1.6)
    shade_bar(cap, cm, P.OBSIDIAN, base=3)
    outline(cap, P.OBSIDIAN)
    fl = sp.layer()
    fm = np.zeros((N, N), bool)
    for (ts0, ts1) in [((24, -2.5), (29.5, -4.5)), ((25, -0.5), (30.5, -0.5)), ((24, 1.5), (28.5, 3.8))]:
        fm |= sweep_ts([ts0, ((ts0[0] + ts1[0]) / 2, (ts0[1] + ts1[1]) / 2 + 0.6), ts1], [1.5, 1.0, 0.2])
    shade_bar(fl, fm, P.FLAME, base=3, grad=1.0)
    outline(fl, P.DRAGON.out)
    orb(sp, ORB_T, -0.5, 3.0, P.FLAME, out=P.DRAGON.out)
    cl = sp.layer()
    m = am(lambda t, s: 11 <= t <= 14 and abs(s + 0.5) <= 1.8)
    for sgn in (-1, 1):
        m |= sweep_ts([(13.5, sgn * 1.6 - 0.5), (16.5, sgn * 5.2 - 0.5), (20.5, sgn * 5.0 - 0.5), (22.5, sgn * 3.0 - 0.5)],
                      [1.2, 1.0, 0.7, 0.3])
    shade_bar(cl, m, P.BONE, base=3, grad=1.2)
    outline(cl, P.DRAGON.out)
    return sp.done()


def st6() -> Canvas:
    """Celestial: winged gold halo around a white star orb, white shaft with gold."""
    sp = Sprite()
    shaft_layer(sp, -29, 12, P.HOLY_WHITE, band=lambda t, s: (P.HOLY_GOLD[3] if t in (-20, -19, -8, -7) else None))
    cap = sp.layer()
    x, y = pos(-28.0, -0.5)
    cx, cy = int(round(x)), int(round(y))
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (0, -2)):
        cap.put(cx + dx, cy + dy, P.HOLY_GOLD[4])
    cap.put(cx, cy, P.HOLY_GOLD[5])
    outline(cap, P.HOLY_GOLD)
    w = sp.layer()
    wm = wing_poly(12.5, 1, 0.8) | wing_poly(12.5, -1, 0.8)
    shade_bar(w, wm, P.HOLY_WHITE, base=3, grad=1.0)
    outline(w, P.HOLY_GOLD.out)
    ring = sp.layer()
    rm = am(lambda t, s: 3.0 ** 2 <= (t - ORB_T) ** 2 + (s + 0.5) ** 2 <= 4.6 ** 2)
    rm |= am(lambda t, s: 11 <= t <= 16.5 and abs(s + 0.5) <= 1.6)
    shade_bar(ring, rm, P.HOLY_GOLD, base=3, grad=1.2)
    outline(ring, P.HOLY_GOLD)
    o = sp.layer()
    x, y = pos(ORB_T, -0.5)
    om = disc(x + 0.5, y + 0.5, 2.1)
    shade_ball(o, om, P.HOLY_WHITE, lo=2)
    sx, sy = pos(ORB_T + 7.5, -0.5)
    star4(o, int(round(sx)), int(round(sy)), P.HOLY_GOLD, r=2)
    s2 = sp.layer()
    star4(s2, 19, 3, P.HOLY_GOLD, r=1)
    return sp.done()


def st7() -> Canvas:
    """Abyss: thorny black branches caging a violet void orb, rune-lit black shaft."""
    sp = Sprite()
    shaft_layer(sp, -29, 12, P.ABYSS, band=lambda t, s: (P.VOID[3] if (t + s) % 7 in (0, 1) else None))
    cap = sp.layer()
    cm = poly_ts([(-26.5, -1.8), (-26.5, 1.0), (-28.0, 2.0), (-31, -0.5), (-28.0, -3.0)])
    shade_bar(cap, cm, P.VOID, base=3, grad=1.5)
    outline(cap, P.ABYSS.out)
    orb(sp, ORB_T, -0.5, 3.0, P.VOID_GLOW, out=P.VOID[0])
    th = sp.layer()
    m = am(lambda t, s: 11 <= t <= 14 and abs(s + 0.5) <= 1.8)
    for sgn in (-1, 1):
        m |= sweep_ts([(13.5, sgn * 1.6 - 0.5), (17.0, sgn * 5.8 - 0.5), (22.0, sgn * 5.4 - 0.5), (26.5, sgn * 1.5 - 0.5),
                       (28.5, sgn * 2.8 - 0.5)], [1.2, 1.0, 0.8, 0.6, 0.2])
        m |= sweep_ts([(16.5, sgn * 5.2 - 0.5), (16.0, sgn * 8.0 - 0.5)], [0.8, 0.2])
        m |= sweep_ts([(22.5, sgn * 5.0 - 0.5), (24.0, sgn * 7.4 - 0.5)], [0.7, 0.2])
    shade_bar(th, m, P.ABYSS, base=3, grad=1.3)
    outline(th, P.ABYSS)
    return sp.done()


# =========================================================================== longbow
def longbow(tier: int) -> Canvas:
    return [bw1, bw2, bw3, bw4, bw5, bw6, bw7][tier - 1]()


BOW_TIP = 25
STRING_S = 4.0


def limb_path(depth=12.5, tip=BOW_TIP, recurve=0.0, steps=26):
    """Bow centre line in axis space: grip at t=0 bulging to -s, tips at t=+-tip."""
    pts = []
    for i in range(steps + 1):
        t = -tip + 2 * tip * i / steps
        u = t / tip
        s = STRING_S - depth * (1 - u * u)
        if recurve:
            k = max(0.0, abs(u) - 0.78) / 0.22
            s -= recurve * k * k
        pts.append((t, s))
    return pts


def limb_widths(path, grip=1.8, tip=0.7):
    out = []
    n = len(path) - 1
    for i in range(n + 1):
        u = abs(2 * i / n - 1)
        out.append(grip * (1 - u) + tip * u)
    return out


def string_layer(sp, col, t0=-BOW_TIP + 1.5, t1=BOW_TIP - 1.5, s=STRING_S, glow=None):
    l = sp.layer()
    for t in range(int(math.floor(t0)), int(math.ceil(t1)) + 1):
        for ss in (int(math.floor(s)), int(math.ceil(s))):
            if (t + ss) % 2:
                put(l, t, ss, col)
    sp.layers.remove(l)
    sp.layers.insert(0, l)
    return l


def bow_grip(sp, ramp, t0=-3, t1=3, half=1.6, extra=None, ds=0.0):
    l = sp.layer()
    path = limb_path()
    s0 = STRING_S - 12.5 + ds
    m = am(lambda t, s: t0 <= t <= t1 and abs(s - s0) <= half)
    shade_bar(l, m, ramp, base=3, extra=extra)
    outline(l, ramp)
    return l


def bw1() -> Canvas:
    sp = Sprite()
    string_layer(sp, P.STRING[3])
    l = sp.layer()
    path = limb_path()
    m = sweep_ts(path, limb_widths(path, 1.6, 0.8))
    shade_bar(l, m, P.WOOD, base=3, grad=1.0)
    outline(l, P.WOOD)
    bow_grip(sp, P.LEATHER, extra=lambda t, s, tone: tone - (1 if t % 2 == 0 else 0))
    n = sp.layer()
    for sgn in (-1, 1):
        nm = am(lambda t, s, sgn=sgn: sgn * t >= BOW_TIP - 3 and abs(s - STRING_S) <= 1.6 and sgn * t <= BOW_TIP + 0.5)
        shade_bar(n, nm, P.IRON, base=3)
    outline(n, P.IRON)
    return sp.done()


def bw2() -> Canvas:
    """Steel recurve: steel-capped limbs with curled tips, leather grip with steel fittings."""
    sp = Sprite()
    string_layer(sp, P.STRING[4], t0=-BOW_TIP + 3.5, t1=BOW_TIP - 3.5, s=STRING_S - 0.5)
    l = sp.layer()
    path = limb_path(depth=12.0, recurve=4.0)
    m = sweep_ts(path, limb_widths(path, 1.9, 0.9))

    def ex(t, s, tone):
        return P.STEEL[tone] if abs(t) >= 9 else None
    shade_bar(l, m, P.WOOD, base=3, grad=1.0, extra=ex)
    outline(l, P.STEEL.out)
    bow_grip(sp, P.LEATHER_DARK, t0=-4, t1=4, half=1.9,
             extra=lambda t, s, tone: (P.STEEL[4] if abs(t) >= 4 else tone - (1 if t % 2 == 0 else 0)))
    return sp.done()


def bw3() -> Canvas:
    """Desert composite: sandstone/horn limbs with gold bands and strong curls, turquoise gem."""
    sp = Sprite()
    string_layer(sp, P.STRING[3], t0=-BOW_TIP + 4.5, t1=BOW_TIP - 4.5, s=STRING_S - 1.0)
    l = sp.layer()
    path = limb_path(depth=12.0, recurve=6.5)
    m = sweep_ts(path, limb_widths(path, 2.0, 1.0))
    for sgn in (-1, 1):
        m |= disc_ts(sgn * (BOW_TIP + 0.5), STRING_S - 6.0, 1.3)

    def ex(t, s, tone):
        if abs(t) in (8, 9, 16, 17):
            return P.GOLD[min(5, tone + 1)]
        if abs(t) >= 22:
            return P.GOLD[tone]
        return None
    shade_bar(l, m, P.SANDSTONE, base=3, grad=1.0, extra=ex)
    outline(l, P.SANDSTONE.out)
    bow_grip(sp, P.GOLD, t0=-4, t1=4, half=2.0)
    g = sp.layer()
    x, y = pos(0, STRING_S - 12.0)
    gem(g, x + 0.5, y + 0.5, P.TURQUOISE, r=1.4)
    outline(g, P.GOLD.out)
    return sp.done()


def bw4() -> Canvas:
    """Frost: crystal limbs with ice spikes on the back, glowing cyan string."""
    sp = Sprite()
    string_layer(sp, P.ICE[4])
    l = sp.layer()
    path = limb_path(depth=12.5)
    m = sweep_ts(path, limb_widths(path, 1.9, 0.8))
    for tt in (-17, -9, 9, 17):
        u = tt / BOW_TIP
        s0 = STRING_S - 12.5 * (1 - u * u)
        d = 1 if tt > 0 else -1
        m |= sweep_ts([(tt, s0 - 0.5), (tt + d * 2.0, s0 - 3.6)], [1.2, 0.2])
    shade_bar(l, m, P.ICE, base=3, grad=1.4)
    outline(l, P.ICE)
    bow_grip(sp, P.ICE_DEEP, extra=lambda t, s, tone: (P.FUR_WHITE[3] if t % 3 == 0 else None))
    return sp.done()


def bw5() -> Canvas:
    """Flame dragon: wing-membrane limbs with scalloped spikes, dragon-eye grip, ember string."""
    sp = Sprite()
    string_layer(sp, P.FLAME[3])
    l = sp.layer()
    path = limb_path(depth=12.0, recurve=2.0)
    m = sweep_ts(path, limb_widths(path, 1.8, 0.8))
    for tt in (-20, -14, -8, 8, 14, 20):
        u = tt / BOW_TIP
        s0 = STRING_S - 12.0 * (1 - u * u)
        d = 1 if tt > 0 else -1
        m |= sweep_ts([(tt - d * 1.5, s0 - 0.5), (tt + d * 1.8, s0 - 3.4)], [1.3, 0.2])

    def ex(t, s, tone):
        if tone >= 5:
            return P.FLAME[4]
        return None
    shade_bar(l, m, P.DRAGON, base=3, grad=1.4, extra=ex)
    outline(l, P.DRAGON)
    bow_grip(sp, P.OBSIDIAN, t0=-4, t1=4, half=2.0)
    g = sp.layer()
    x, y = pos(0, STRING_S - 12.0)
    eye_gem(g, x + 0.5, y + 0.5, P.FLAME, P.DRAGON.out)
    outline(g, P.DRAGON.out)
    return sp.done()


def bw6() -> Canvas:
    """Celestial: feathered wing limbs, gold grip with a star, golden string."""
    sp = Sprite()
    string_layer(sp, P.HOLY_GOLD[4])
    l = sp.layer()
    path = limb_path(depth=12.0)
    m = sweep_ts(path, limb_widths(path, 1.6, 0.7))
    # feathers hanging off the back of each limb
    for tt in (-21, -16, -11, -6, 6, 11, 16, 21):
        u = tt / BOW_TIP
        s0 = STRING_S - 12.0 * (1 - u * u)
        d = 1 if tt > 0 else -1
        m |= sweep_ts([(tt, s0 - 0.5), (tt - d * 1.2, s0 - 3.6 + abs(tt) * 0.06)], [1.3, 0.4])

    def ex(t, s, tone):
        u = t / BOW_TIP
        s0 = STRING_S - 12.0 * (1 - u * u)
        if s >= s0 - 1.0:
            return P.HOLY_GOLD[tone]
        return P.HOLY_WHITE[min(5, tone + 1)]
    shade_bar(l, m, P.HOLY_WHITE, base=3, grad=1.0, extra=ex)
    outline(l, P.HOLY_GOLD.out)
    bow_grip(sp, P.HOLY_GOLD, t0=-3, t1=3, half=1.8)
    s2 = sp.layer()
    x, y = pos(0, STRING_S - 12.0 - 4.5)
    star4(s2, int(round(x)), int(round(y)), P.HOLY_GOLD, r=2)
    return sp.done()


def bw7() -> Canvas:
    """Abyss: black thorned limbs with violet runes, eye grip, glowing violet string."""
    sp = Sprite()
    string_layer(sp, P.VOID_GLOW[3])
    l = sp.layer()
    path = limb_path(depth=12.5, recurve=3.0)
    m = sweep_ts(path, limb_widths(path, 1.8, 0.8))
    for tt in (-19, -12, 12, 19):
        u = tt / BOW_TIP
        s0 = STRING_S - 12.5 * (1 - u * u)
        d = 1 if tt > 0 else -1
        m |= sweep_ts([(tt, s0 - 0.5), (tt - d * 2.2, s0 - 3.6)], [1.1, 0.2])
        m |= sweep_ts([(tt + d * 2, s0 + 0.5), (tt + d * 3.5, s0 + 2.6)], [0.9, 0.2])

    def ex(t, s, tone):
        if abs(t) in (5, 15, 23) and tone >= 3:
            return P.VOID_GLOW[4]
        return None
    shade_bar(l, m, P.ABYSS, base=3, grad=1.4, extra=ex)
    outline(l, P.ABYSS)
    bow_grip(sp, P.ABYSS, t0=-4, t1=4, half=2.0)
    g = sp.layer()
    x, y = pos(0, STRING_S - 12.5)
    eye_gem(g, x + 0.5, y + 0.5, P.VOID_GLOW, P.ABYSS.out)
    outline(g, P.ABYSS.out)
    return sp.done()


FAMILIES = {
    "longsword": longsword,
    "greatsword": greatsword,
    "twin_blades": twin_blades,
    "spear": spear,
    "staff": staff,
    "longbow": longbow,
}

ITEM_IDS = {
    "longsword": "iron_longsword",
    "greatsword": "iron_greatsword",
    "twin_blades": "iron_twin_blades",
    "spear": "iron_spear",
    "staff": "staff",
    "longbow": "longbow",
}
