"""Shared prop materials (rs_kit.Mat): painters with pixel-art edge lighting and wear, keyed so that equal-sized faces
of the same material share one atlas region."""
import zlib

import numpy as np

from common import C, ramp, pal, rng, grid
from rs_kit import Mat
from rs_lib import (blank, from_idx, noise, dl, tint, rect, px, hline, vline, frame, bevel, wear, specks, grime,
                    m_wood, m_brushed, m_vinyl, m_paint, m_plastic, m_granite, m_glass, m_fabric, m_cardboard,
                    m_concrete, m_flat, WOOD_DARK, WOOD_TABLE, WOOD_LIGHT, VINYL_RED, STEEL, ALU, CHROME, GREYPAINT,
                    WHITEPLASTIC, BEIGE, BLACKPLASTIC, GRANITE, CONCRETE, CARDBOARD, PAPERW, RUBBER, ORANGE, BROWN,
                    CREAM, GREEN_LEAF, TERRACOTTA)


def _seed(key, w, h, face):
    return zlib.crc32(f'{key}|{w}|{h}|{face}'.encode()) % 100000 + 17


def _finish(a, w, h, face, bev=True, wear_amt=0.0, seed=0, light=0.05, dark=-0.06):
    if bev and w >= 3 and h >= 3:
        if face == 'h':
            bevel(a, top=0.03, bottom=-0.03, left=0.02, right=-0.02)
        else:
            bevel(a, top=light, bottom=dark, left=0.015, right=-0.025)
    elif bev and h <= 2 and w >= 2 and face != 'h':
        dl(a[:1], 0.03)
    if wear_amt:
        wear(a, seed + 3, amount=wear_amt)
    return a


def wood(key, palette=WOOD_TABLE, base=None, amp=0.5, bev=True, wear_amt=0.04, along=None):
    def fn(w, h, face, **_):
        al = along
        if al is None:
            al = 'y' if (face != 'h' and h > w) else 'x'
        a = m_wood(w, h, palette, _seed(key, w, h, face), along=al, base=base, amp=amp, lines=0.7)
        return _finish(a, w, h, face, bev, wear_amt, _seed(key, w, h, face))
    return Mat(key, fn)


def flat(key, palette, base=None, amp=0.5, beta=1.2, bev=True, wear_amt=0.0, dither=0.0):
    def fn(w, h, face, **_):
        a = m_flat(w, h, palette, _seed(key, w, h, face), base=base, amp=amp, beta=beta, dither=dither)
        return _finish(a, w, h, face, bev, wear_amt, _seed(key, w, h, face))
    return Mat(key, fn)


def solid(key, color, bev=False):
    def fn(w, h, face, **_):
        a = blank(w, h, color)
        return _finish(a, w, h, face, bev)
    return Mat(key, fn)


def brushed(key, palette=STEEL, base=None, along=None, smudge=0.0, bev=True):
    def fn(w, h, face, **_):
        al = along or ('x' if w >= h or face == 'h' else 'y')
        a = m_brushed(w, h, palette, _seed(key, w, h, face), along=al, base=base, smudge=smudge)
        return _finish(a, w, h, face, bev, 0.0)
    return Mat(key, fn)


def vinyl(key, palette=VINYL_RED, base=None, sheen=0.8, cracks=0.0, bev=True, piping=False):
    def fn(w, h, face, **_):
        a = m_vinyl(w, h, palette, _seed(key, w, h, face), base=base, sheen=sheen if face != 'h' else 0.35,
                    cracks=cracks)
        if piping and h >= 3 and w >= 3:
            frame(a, 0, 0, w - 1, h - 1, palette[1])
        return _finish(a, w, h, face, bev, 0.0, light=0.07, dark=-0.08)
    return Mat(key, fn)


def paint(key, palette=GREYPAINT, base=None, scuffs=0.05, rust=0.0, bev=True):
    def fn(w, h, face, **_):
        a = m_paint(w, h, palette, _seed(key, w, h, face), base=base, scuffs=scuffs, rust=rust)
        return _finish(a, w, h, face, bev)
    return Mat(key, fn)


def plastic(key, palette=WHITEPLASTIC, base=None, yellow=0.0, bev=True):
    def fn(w, h, face, **_):
        a = m_plastic(w, h, palette, _seed(key, w, h, face), base=base, yellow=yellow)
        return _finish(a, w, h, face, bev)
    return Mat(key, fn)


def glass(key, tint_c='#9fb9bd', alpha=70, edge=None):
    def fn(w, h, face, **_):
        return m_glass(w, h, tint_c, alpha, _seed(key, w, h, face), edge=edge)
    return Mat(key, fn)


def fabric(key, palette, base=None):
    def fn(w, h, face, **_):
        a = m_fabric(w, h, palette, _seed(key, w, h, face), base=base)
        return _finish(a, w, h, face, True)
    return Mat(key, fn)


def cardboard(key, base=3.0, tape=True):
    def fn(w, h, face, **_):
        a = m_cardboard(w, h, _seed(key, w, h, face), base=base)
        if tape and face == 'h' and w >= 4:
            rect(a, 0, h // 2, w - 1, h // 2, '#b39b6a')
        elif tape and face != 'h' and h >= 4:
            rect(a, w // 2, 0, w // 2, min(1, h - 1), '#b39b6a')
        return _finish(a, w, h, face, True)
    return Mat(key, fn)


def concrete(key, base=3.6):
    def fn(w, h, face, **_):
        a = m_concrete(w, h, _seed(key, w, h, face), base=base)
        return _finish(a, w, h, face, True)
    return Mat(key, fn)


def granite(key):
    def fn(w, h, face, **_):
        a = m_granite(w, h, GRANITE, _seed(key, w, h, face))
        return _finish(a, w, h, face, True)
    return Mat(key, fn)


def custom(key, fn, per_face=False):
    """fn(w, h, face) -> array (full control)."""
    return Mat(key, lambda w, h, face, **_: fn(w, h, face), per_face=per_face)


# ------------------------------------------------------------------------------------------------ common instances
DARKWOOD = wood('darkwood', WOOD_DARK, base=3.2)
TABLEWOOD = wood('tablewood', WOOD_TABLE, base=3.0, wear_amt=0.05)
LIGHTWOOD = wood('lightwood', WOOD_LIGHT, base=3.4)
VINYL = vinyl('vinyl')
VINYL_SEAT = vinyl('vinyl_seat', piping=True)
STEEL_M = brushed('steel')
STEEL_DIRTY = brushed('steel_dirty', smudge=0.3)
ALU_M = brushed('alu', ALU)
CHROME_M = brushed('chrome', CHROME, base=3.2)
GREY_M = paint('grey', GREYPAINT)
GREY_DARK = paint('grey_dark', GREYPAINT, base=1.8)
WHITE_M = plastic('white', WHITEPLASTIC)
BEIGE_M = plastic('beige', BEIGE, base=3.2)
BLACK_M = plastic('black', BLACKPLASTIC, base=2.5)
RUBBER_M = flat('rubber', RUBBER, base=2.0, amp=0.3)
GRANITE_M = granite('granite')
GLASS_M = glass('glass')
FROST_GLASS = glass('frost_glass', '#c9d3d1', 168)
CARD_M = cardboard('card')
CONCRETE_M = concrete('concrete')
ORANGE_M = paint('orange', ORANGE, base=4.0, scuffs=0.03)
BROWN_M = paint('brown', BROWN, base=3.0, scuffs=0.03)
CREAM_M = paint('cream', CREAM, base=4.5, scuffs=0.02)
