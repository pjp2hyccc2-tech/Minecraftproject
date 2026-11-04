"""Contestant skins (64x64 player layout), blood overlays and gore chunk textures.

Faces that are hidden on a living body (head bottom, body top/bottom, leg tops) are painted as raw flesh,
so the corpse renderer's cut surfaces (guillotine neck, saw halves) show meat for free.
"""
import math
import random

import numpy as np

from common import (box_uv, canvas, fill, fbm, hexc, mix, px, save, shade, splat)

HEAD = {'top': (8, 0, 8, 8), 'bottom': (16, 0, 8, 8), 'right': (0, 8, 8, 8), 'front': (8, 8, 8, 8), 'left': (16, 8, 8, 8), 'back': (24, 8, 8, 8)}
BODY = {'top': (20, 16, 8, 4), 'bottom': (28, 16, 8, 4), 'right': (16, 20, 4, 12), 'front': (20, 20, 8, 12), 'left': (28, 20, 4, 12), 'back': (32, 20, 8, 12)}
RARM = {'top': (44, 16, 4, 4), 'bottom': (48, 16, 4, 4), 'right': (40, 20, 4, 12), 'front': (44, 20, 4, 12), 'left': (48, 20, 4, 12), 'back': (52, 20, 4, 12)}
LARM = {'top': (36, 48, 4, 4), 'bottom': (40, 48, 4, 4), 'right': (32, 52, 4, 12), 'front': (36, 52, 4, 12), 'left': (40, 52, 4, 12), 'back': (44, 52, 4, 12)}
RLEG = {'top': (4, 16, 4, 4), 'bottom': (8, 16, 4, 4), 'right': (0, 20, 4, 12), 'front': (4, 20, 4, 12), 'left': (8, 20, 4, 12), 'back': (12, 20, 4, 12)}
LLEG = {'top': (20, 48, 4, 4), 'bottom': (24, 48, 4, 4), 'right': (16, 52, 4, 12), 'front': (20, 52, 4, 12), 'left': (24, 52, 4, 12), 'back': (28, 52, 4, 12)}


def shifted(part, dx, dy):
    return {k: (v[0] + dx, v[1] + dy, v[2], v[3]) for k, v in part.items()}


HAT = shifted(HEAD, 32, 0)
JACKET = shifted(BODY, 0, 16)
RSLEEVE = shifted(RARM, 0, 16)
LSLEEVE = shifted(LARM, 16, 0)
RPANTS = shifted(RLEG, 0, 16)
LPANTS = shifted(LLEG, -16, 0)
SIDES = ('right', 'front', 'left', 'back')


def face(a, rect, fn):
    x, y, w, h = rect
    for j in range(h):
        for i in range(w):
            c = fn(i, j, w, h)
            if c is not None:
                px(a, x + i, y + j, c)


def noisy(c, i, j, seed, amt=0.08):
    k = 1.0 + (((i * 73856093) ^ (j * 19349663) ^ seed) % 1000 / 1000.0 - 0.5) * 2 * amt
    return shade(c, k)


def meat(a, rect, seed, bone=2):
    x, y, w, h = rect
    r = random.Random(seed)
    for j in range(h):
        for i in range(w):
            edge = min(i, j, w - 1 - i, h - 1 - j)
            if edge == 0:
                c = (92, 6, 10, 255)
            else:
                c = (160 + r.randint(-25, 25), 18 + r.randint(0, 20), 26 + r.randint(0, 14), 255)
                if r.random() < 0.18:
                    c = (220, 120, 120, 255)
            px(a, x + i, y + j, c)
    if bone:
        bx, by = x + w // 2 - bone // 2, y + h // 2 - bone // 2
        for j in range(bone):
            for i in range(bone):
                px(a, bx + i, by + j, (236, 228, 205, 255) if (i + j) % 3 else (205, 196, 170, 255))


# ------------------------------------------------------------------------------------------------ painters

def paint_head(a, s, seed):
    skin = s['skin']
    for k in SIDES + ('top',):
        face(a, HEAD[k], lambda i, j, w, h: noisy(skin, i, j, seed, 0.04))
    meat(a, HEAD['bottom'], seed + 1, bone=2)
    # ears
    face(a, HEAD['right'], lambda i, j, w, h: shade(skin, 0.82) if (i in (3, 4) and j in (4, 5)) else None)
    face(a, HEAD['left'], lambda i, j, w, h: shade(skin, 0.82) if (i in (3, 4) and j in (4, 5)) else None)
    hair(a, s, seed)
    fc(a, s)


def hair(a, s, seed):
    hc = s['hair']
    style = s['hair_style']

    def hcol(i, j):
        return noisy(hc, i, j, seed + 7, 0.12)

    if style == 'bald':
        face(a, HEAD['top'], lambda i, j, w, h: noisy(shade(s['skin'], 1.06), i, j, seed, 0.05) if 1 <= i <= 6 and 1 <= j <= 6 else hcol(i, j))
        for k in ('right', 'left', 'back'):
            face(a, HEAD[k], lambda i, j, w, h: hcol(i, j) if 1 <= j <= 3 and not (k != 'back' and i in (3, 4) and j >= 3) else None)
        face(a, HEAD['front'], lambda i, j, w, h: hcol(i, j) if j == 1 and i in (0, 7) else None)
        return
    face(a, HEAD['top'], lambda i, j, w, h: hcol(i, j))
    long_hair = style in ('long', 'long_wavy')
    for k in ('right', 'left'):
        depth = 7 if long_hair else (2 if style != 'spiky' else 2)
        face(a, HEAD[k], lambda i, j, w, h, d=depth: hcol(i, j) if (j <= 1 or (j <= d and (i <= 2 or long_hair) and not (i in (3, 4) and 4 <= j <= 5 and not long_hair))) else None)
    face(a, HEAD['back'], lambda i, j, w, h: hcol(i, j) if j <= (7 if long_hair else 3) else None)
    if style == 'side_part':
        face(a, HEAD['front'], lambda i, j, w, h: hcol(i, j) if j == 0 or (j == 1 and i <= 4) else None)
    elif style == 'bowl':
        face(a, HEAD['front'], lambda i, j, w, h: hcol(i, j) if j <= 2 and not (j == 2 and 2 <= i <= 5) else None)
    elif style == 'slick':
        face(a, HEAD['front'], lambda i, j, w, h: shade(hcol(i, j), 1.15) if j == 0 else None)
    elif style in ('long', 'long_wavy'):
        face(a, HEAD['front'], lambda i, j, w, h: hcol(i, j) if j <= 1 or (j <= 6 and i in (0, 7)) else None)
    elif style == 'pompadour':
        face(a, HEAD['front'], lambda i, j, w, h: hcol(i, j) if j <= 1 else None)
    elif style == 'spiky':
        face(a, HEAD['front'], lambda i, j, w, h: hcol(i, j) if j == 0 or (j == 1 and i % 2 == 0) else None)
    else:
        face(a, HEAD['front'], lambda i, j, w, h: hcol(i, j) if j <= 1 else None)
    # volume in the hat layer
    if style == 'pompadour':
        face(a, HAT['top'], lambda i, j, w, h: shade(hcol(i, j), 1.1))
        face(a, HAT['front'], lambda i, j, w, h: shade(hcol(i, j), 1.15) if j <= 2 else None)
        face(a, HAT['right'], lambda i, j, w, h: hcol(i, j) if j <= 1 else None)
        face(a, HAT['left'], lambda i, j, w, h: hcol(i, j) if j <= 1 else None)
    if long_hair:
        face(a, HAT['back'], lambda i, j, w, h: shade(hcol(i, j), 1.05) if j <= 7 else None)
        for k in ('right', 'left'):
            face(a, HAT[k], lambda i, j, w, h: hcol(i, j) if i <= 2 and j >= 1 else None)
        face(a, HAT['top'], lambda i, j, w, h: shade(hcol(i, j), 1.08))
    if style == 'spiky':
        face(a, HAT['top'], lambda i, j, w, h: hcol(i, j) if (i + j) % 3 == 0 else None)


def fc(a, s):
    """Face on HEAD front (8x8 at 8,8)."""
    fx, fy = 8, 8
    skin = s['skin']
    dark = (30, 20, 20, 255)
    white = (240, 240, 240, 255)
    brow = shade(s['hair'], 0.7) if s['hair_style'] != 'bald' else (90, 90, 90, 255)
    mood = s.get('mood', 'scared')
    # eyebrows
    if mood == 'angry':
        for (i, j) in ((1, 3), (2, 3), (5, 3), (6, 3), (2, 2) if False else (1, 2), (6, 2)):
            px(a, fx + i, fy + j, brow)
        px(a, fx + 1, fy + 2, skin)
        px(a, fx + 6, fy + 2, skin)
        px(a, fx + 2, fy + 3, brow)
        px(a, fx + 5, fy + 3, brow)
    else:
        for (i, j) in ((1, 3), (2, 2), (5, 2), (6, 3)):
            px(a, fx + i, fy + j, brow)
    # eyes
    if s.get('sunglasses'):
        for i in range(1, 7):
            px(a, fx + i, fy + 4, (20, 20, 25, 255))
        px(a, fx + 2, fy + 4, (90, 90, 120, 255))
        px(a, fx + 5, fy + 4, (90, 90, 120, 255))
    else:
        eye = s.get('eye', (40, 30, 30, 255))
        px(a, fx + 1, fy + 4, white)
        px(a, fx + 2, fy + 4, eye)
        px(a, fx + 5, fy + 4, eye)
        px(a, fx + 6, fy + 4, white)
        if s.get('lashes'):
            px(a, fx + 1, fy + 3, dark)
            px(a, fx + 6, fy + 3, dark)
        if s.get('glasses'):
            g = s.get('glasses_color', (25, 25, 30, 255))
            for i in (0, 3, 4, 7):
                px(a, fx + i, fy + 4, g)
            for i in (1, 2, 5, 6):
                px(a, fx + i, fy + 3, g)
    # nose / cheeks
    px(a, fx + 3, fy + 5, shade(skin, 0.85))
    if s.get('blush'):
        px(a, fx + 1, fy + 5, (230, 120, 120, 255))
        px(a, fx + 6, fy + 5, (230, 120, 120, 255))
    # mouth
    lip = s.get('lip', (110, 40, 40, 255))
    if mood == 'angry':
        for i in (2, 3, 4, 5):
            px(a, fx + i, fy + 6, dark)
        px(a, fx + 3, fy + 6, white)
        px(a, fx + 4, fy + 6, white)
    else:
        px(a, fx + 3, fy + 6, dark)
        px(a, fx + 4, fy + 6, dark)
        px(a, fx + 3, fy + 7, lip)
        px(a, fx + 4, fy + 7, lip)
    if s.get('mustache'):
        for i in (2, 3, 4, 5):
            px(a, fx + i, fy + 5, shade(s['hair'], 0.8) if s['hair_style'] != 'bald' else (120, 120, 120, 255))
        px(a, fx + 3, fy + 5, shade(skin, 0.85))
    if s.get('stubble'):
        for i in (1, 2, 5, 6):
            px(a, fx + i, fy + 7, shade(skin, 0.72))
    if s.get('sweat'):
        px(a, fx + 0, fy + 3, (150, 210, 255, 255))
        px(a, fx + 7, fy + 2, (150, 210, 255, 255))
    if s.get('earrings'):
        px(a, 3, 14, (250, 210, 60, 255))
        px(a, 20, 14, (250, 210, 60, 255))


def paint_body(a, s, seed):
    top = s['top']
    kind = s['top_kind']
    skin = s['skin']
    meat(a, BODY['top'], seed + 3, bone=2)
    meat(a, BODY['bottom'], seed + 4, bone=2)

    def side_col(i, j):
        return noisy(top, i, j, seed + 11, 0.06)

    for k in ('right', 'left', 'back'):
        face(a, BODY[k], lambda i, j, w, h: side_col(i, j))
    if kind == 'suit':
        shirt = s.get('shirt', (235, 235, 238, 255))
        tie = s.get('tie', (170, 20, 30, 255))

        def f(i, j, w, h):
            if i in (3, 4) and j <= 6:
                if j >= 1 and i in (3, 4):
                    return noisy(tie, i, j, seed, 0.05) if (j >= 1) else shirt
            if (i == 2 and j <= 3) or (i == 5 and j <= 3) or (i in (3, 4) and j == 0):
                return shirt
            if j == 11:
                return shade(top, 0.8)
            if i == 3 and j in (7, 9):
                return (40, 40, 40, 255)
            return side_col(i, j)

        face(a, BODY['front'], f)
        face(a, BODY['back'], lambda i, j, w, h: shade(side_col(i, j), 0.95) if i in (3, 4) and j >= 8 else side_col(i, j))
        if s.get('pinstripe'):
            for k in ('front', 'back', 'right', 'left'):
                face(a, BODY[k], lambda i, j, w, h: shade(top, 1.35) if (i % 3 == 1 and not (k == 'front' and 2 <= i <= 5 and j <= 6)) else None)
    elif kind == 'blouse_jacket':
        blouse = s.get('shirt', (245, 245, 250, 255))
        face(a, BODY['front'], lambda i, j, w, h: blouse if (2 <= i <= 5 and j <= 5) or (3 <= i <= 4 and j <= 9) else (shade(top, 0.75) if j == 11 else side_col(i, j)))
        px(a, 23, 22, (200, 40, 60, 255))
    elif kind == 'tank':
        def f(i, j, w, h):
            if j <= 1 and (i <= 1 or i >= 6):
                return noisy(skin, i, j, seed, 0.05)
            if j == 0 and 2 <= i <= 5:
                return noisy(skin, i, j, seed, 0.05)
            if j == 1 and 3 <= i <= 4:
                return noisy(skin, i, j, seed, 0.05)
            return side_col(i, j)

        face(a, BODY['front'], f)
        face(a, BODY['back'], f)
        if s.get('haramaki'):
            for k in ('front', 'back', 'right', 'left'):
                face(a, BODY[k], lambda i, j, w, h: noisy((205, 185, 140, 255), i, j, seed, 0.05) if 7 <= j <= 10 else None)
        if s.get('muscles'):
            face(a, BODY['front'], lambda i, j, w, h: shade(top, 1.3) if (j in (3, 6) and i in (1, 2, 5, 6)) or (i in (3, 4) and 4 <= j <= 8 and j % 2 == 0) else None)
    elif kind == 'plaid':
        c1, c2 = s['top'], s.get('top2', (40, 90, 50, 255))

        def plaid(i, j, w, h):
            if i % 4 == 1 or j % 4 == 1:
                return shade(c2, 1.0)
            if (i % 4 == 1) and (j % 4 == 1):
                return shade(c2, 0.7)
            return noisy(c1, i, j, seed, 0.04)

        for k in ('front', 'back', 'right', 'left'):
            face(a, BODY[k], plaid)
        face(a, BODY['front'], lambda i, j, w, h: (230, 230, 230, 255) if (i in (3, 4) and j in (2, 5, 8)) and i == 3 else None)
        if s.get('backpack'):
            face(a, JACKET['back'], lambda i, j, w, h: noisy((40, 40, 50, 255), i, j, seed, 0.08) if 1 <= i <= 6 and 1 <= j <= 10 else None)
            face(a, JACKET['front'], lambda i, j, w, h: (35, 35, 45, 255) if i in (1, 6) and j <= 8 else None)
            face(a, JACKET['right'], lambda i, j, w, h: (35, 35, 45, 255) if j <= 1 else None)
            face(a, JACKET['left'], lambda i, j, w, h: (35, 35, 45, 255) if j <= 1 else None)
    elif kind == 'track':
        stripe = s.get('stripe', (240, 240, 240, 255))
        face(a, BODY['front'], lambda i, j, w, h: (220, 200, 40, 255) if i == 4 and j <= 10 else (stripe if j == 0 else side_col(i, j)))
        for k in ('right', 'left'):
            face(a, BODY[k], lambda i, j, w, h: stripe if i in (1, 2) else None)
        if s.get('kanji_back'):
            face(a, BODY['back'], lambda i, j, w, h: (200, 30, 40, 255) if (i in (2, 5) and 2 <= j <= 9) or (j in (3, 6, 9) and 2 <= i <= 5) else None)
    elif kind == 'gyaru':
        face(a, BODY['front'], lambda i, j, w, h: noisy(skin, i, j, seed, 0.04) if j <= 1 and 2 <= i <= 5 else (noisy(skin, i, j, seed, 0.04) if j >= 8 and j <= 9 else (shade(top, 1.15) if j in (2, 7) else side_col(i, j))))
        face(a, BODY['back'], lambda i, j, w, h: noisy(skin, i, j, seed, 0.04) if j in (8, 9) else None)
        face(a, BODY['right'], lambda i, j, w, h: noisy(skin, i, j, seed, 0.04) if j in (8, 9) else None)
        face(a, BODY['left'], lambda i, j, w, h: noisy(skin, i, j, seed, 0.04) if j in (8, 9) else None)
        for k in ('front', 'back', 'right', 'left'):
            face(a, BODY[k], lambda i, j, w, h: noisy(s['pants'], i, j, seed, 0.05) if j >= 10 else None)
        px(a, 23, 25, (255, 255, 255, 255))
    if s.get('chain'):
        for (i, j) in ((2, 1), (3, 2), (4, 2), (5, 1)):
            px(a, 20 + i, 20 + j, (240, 200, 60, 255))


def paint_arm(a, part, sleeve_part, s, seed, right):
    skin = s['skin']
    kind = s['arm_kind']
    top = s['top']
    sleeve_len = {'long': 10, 'short': 3, 'none': 0}[kind]
    for k in SIDES:
        def f(i, j, w, h, k=k):
            if j >= sleeve_len:
                c = noisy(skin, i, j, seed, 0.05)
                if s.get('muscles') and k in ('front', 'right') and j in (3, 4, 6) and i in (1, 2):
                    c = shade(skin, 1.12)
                if s.get('muscles') and j == 5 and i in (1, 2):
                    c = shade(skin, 0.85)
                return c
            c = noisy(top, i, j, seed + (5 if right else 9), 0.06)
            if s['top_kind'] == 'plaid' and (i % 4 == 1 or j % 4 == 1):
                c = s.get('top2', (40, 90, 50, 255))
            if s['top_kind'] == 'track' and k in ('right' if right else 'left',) and i in (1, 2):
                c = s.get('stripe', (240, 240, 240, 255))
            if s.get('pinstripe') and i % 3 == 1:
                c = shade(top, 1.35)
            if j == sleeve_len - 1 and kind == 'long':
                c = s.get('cuff', shade(top, 0.8))
            return c

        face(a, part[k], f)
    face(a, part['top'], lambda i, j, w, h: noisy(top if sleeve_len > 0 else skin, i, j, seed, 0.05))
    face(a, part['bottom'], lambda i, j, w, h: noisy(shade(skin, 0.9), i, j, seed, 0.05))
    if s.get('watch') and not right:
        face(a, part['front'], lambda i, j, w, h: (240, 200, 60, 255) if j == 9 else None)
        face(a, part['right'], lambda i, j, w, h: (240, 200, 60, 255) if j == 9 else None)
    if s.get('nails') :
        face(a, part['front'], lambda i, j, w, h: s['nails'] if j == 11 and i in (0, 2) else None)


def paint_leg(a, part, s, seed, right):
    kind = s['leg_kind']
    pants = s['pants']
    shoes = s.get('shoes', (30, 25, 25, 255))
    skin = s['skin']
    meat(a, part['top'], seed + (21 if right else 23), bone=1)
    for k in SIDES:
        def f(i, j, w, h, k=k):
            if j >= 10:
                c = noisy(shoes, i, j, seed, 0.06)
                if kind == 'heels' and j == 10 and k != 'back':
                    return noisy(skin if not s.get('stockings') else s['stockings'], i, j, seed, 0.04)
                if j == 11 and s.get('sole'):
                    c = s['sole']
                return c
            if kind == 'trousers':
                c = noisy(pants, i, j, seed + 3, 0.06)
                if k == 'front' and i == (2 if right else 1) and j <= 8:
                    c = shade(pants, 0.85)
                return c
            if kind == 'jeans':
                c = noisy(pants, i, j, seed + 3, 0.14)
                if k == ('right' if right else 'left') and i == 1:
                    c = shade(pants, 1.2)
                return c
            if kind == 'camo':
                n = (((i * 7 + j * 13 + seed) * 2654435761) >> 7) % 4
                return [(70, 85, 50, 255), (95, 110, 60, 255), (60, 50, 35, 255), (80, 95, 55, 255)][n]
            if kind in ('skirt', 'heels'):
                if j <= 3:
                    return noisy(pants, i, j, seed + 3, 0.06)
                return noisy(s.get('stockings', skin), i, j, seed, 0.04)
            if kind == 'track':
                c = noisy(pants, i, j, seed + 3, 0.06)
                if k == ('right' if right else 'left') and i in (1, 2):
                    c = s.get('stripe', (240, 240, 240, 255))
                return c
            return noisy(pants, i, j, seed, 0.06)

        face(a, part[k], f)
    face(a, part['bottom'], lambda i, j, w, h: shade(shoes, 0.6))
    if kind in ('skirt', 'heels'):
        pp = RPANTS if right else LPANTS
        for k in SIDES:
            face(a, pp[k], lambda i, j, w, h: noisy(shade(pants, 1.05), i, j, seed + 5, 0.05) if j <= 3 else None)


SPECS = {
    'salaryman': [
        dict(skin=hexc('e8bfa0'), hair=hexc('1c1a1a'), hair_style='side_part', top=hexc('2a3348'), top_kind='suit', tie=hexc('b0182a'),
             arm_kind='long', cuff=hexc('e8e8ea'), pants=hexc('262d3e'), leg_kind='trousers', shoes=hexc('141212'), sweat=True),
        dict(skin=hexc('e2b898'), hair=hexc('2b2420'), hair_style='side_part', top=hexc('3b3d42'), top_kind='suit', tie=hexc('1f4fa8'),
             arm_kind='long', cuff=hexc('e8e8ea'), pants=hexc('34363b'), leg_kind='trousers', shoes=hexc('2a1d14'), glasses=True, sweat=True),
    ],
    'office_lady': [
        dict(skin=hexc('f0c9ad'), hair=hexc('3a2418'), hair_style='long', top=hexc('3a3a44'), top_kind='blouse_jacket', arm_kind='long',
             cuff=hexc('f2f2f5'), pants=hexc('2e2e36'), leg_kind='heels', stockings=hexc('6b4f45'), shoes=hexc('141214'), lashes=True,
             lip=hexc('c03048'), earrings=True),
        dict(skin=hexc('f3cfb4'), hair=hexc('14100e'), hair_style='long', top=hexc('5a4a6a'), top_kind='blouse_jacket', arm_kind='long',
             cuff=hexc('f2f2f5'), pants=hexc('3d3346'), leg_kind='heels', stockings=hexc('d9b49b'), shoes=hexc('5a1a24'), lashes=True,
             lip=hexc('d0405a'), glasses=True, glasses_color=hexc('7a2030')),
    ],
    'yankee': [
        dict(skin=hexc('e0b090'), hair=hexc('e8c040'), hair_style='pompadour', top=hexc('5a2a7a'), top_kind='track', stripe=hexc('f0e050'),
             arm_kind='long', pants=hexc('4a2266'), leg_kind='track', shoes=hexc('f0f0f0'), sole=hexc('202020'), mood='angry', chain=True,
             kanji_back=True),
        dict(skin=hexc('d9a888'), hair=hexc('d06020'), hair_style='pompadour', top=hexc('1a1a1a'), top_kind='track', stripe=hexc('d02020'),
             arm_kind='long', pants=hexc('1a1a1a'), leg_kind='track', shoes=hexc('202020'), mood='angry', sunglasses=True, chain=True),
    ],
    'gyaru': [
        dict(skin=hexc('c98a5e'), hair=hexc('f2d070'), hair_style='long_wavy', top=hexc('ff5aa8'), top_kind='gyaru', arm_kind='none',
             pants=hexc('f0f0f0'), leg_kind='skirt', shoes=hexc('f8f0f0'), lashes=True, lip=hexc('ff7090'), earrings=True,
             eye=hexc('5a3a20'), nails=hexc('ff3090')),
        dict(skin=hexc('d69a6c'), hair=hexc('ff9ad0'), hair_style='long_wavy', top=hexc('202020'), top_kind='gyaru', arm_kind='none',
             pants=hexc('ff50a0'), leg_kind='skirt', shoes=hexc('202020'), lashes=True, lip=hexc('e04070'), earrings=True,
             eye=hexc('3a6ac0'), nails=hexc('40e0ff')),
    ],
    'otaku': [
        dict(skin=hexc('ecc6a8'), hair=hexc('201a18'), hair_style='bowl', top=hexc('b0262a'), top2=hexc('3a2a30'), top_kind='plaid',
             arm_kind='long', pants=hexc('3a5a8a'), leg_kind='jeans', shoes=hexc('e8e8e8'), sole=hexc('707070'), glasses=True, sweat=True,
             backpack=True),
        dict(skin=hexc('e9c2a2'), hair=hexc('2a2220'), hair_style='bowl', top=hexc('2f6a3a'), top2=hexc('203020'), top_kind='plaid',
             arm_kind='long', pants=hexc('8a7a5a'), leg_kind='trousers', shoes=hexc('3a3a3a'), glasses=True, sweat=True, backpack=True),
    ],
    'ojisan': [
        dict(skin=hexc('e6b494'), hair=hexc('8a8a8a'), hair_style='bald', top=hexc('f0f0ea'), top_kind='tank', haramaki=True,
             arm_kind='none', pants=hexc('5a5a5a'), leg_kind='trousers', shoes=hexc('6a4a2a'), blush=True, mustache=True),
        dict(skin=hexc('dcaa88'), hair=hexc('6a6a6a'), hair_style='bald', top=hexc('e8e4d8'), top_kind='tank', haramaki=True,
             arm_kind='none', pants=hexc('3a4a5a'), leg_kind='trousers', shoes=hexc('3a3a3a'), blush=True, stubble=True, glasses=True),
    ],
    'macho': [
        dict(skin=hexc('b67a50'), hair=hexc('141210'), hair_style='spiky', top=hexc('1a1a1a'), top_kind='tank', muscles=True,
             arm_kind='none', pants=hexc('556b3a'), leg_kind='camo', shoes=hexc('2a2218'), mood='angry'),
        dict(skin=hexc('c88a60'), hair=hexc('3a2a1a'), hair_style='spiky', top=hexc('b02020'), top_kind='tank', muscles=True,
             arm_kind='none', pants=hexc('2a2a3a'), leg_kind='trousers', shoes=hexc('1a1a1a'), mood='angry', stubble=True),
    ],
    'president': [
        dict(skin=hexc('e8c0a0'), hair=hexc('d8d8d8'), hair_style='slick', top=hexc('3a1018'), top_kind='suit', pinstripe=True,
             tie=hexc('e0b030'), shirt=hexc('f8f4e8'), arm_kind='long', cuff=hexc('f8f4e8'), pants=hexc('2a0c12'), leg_kind='trousers',
             shoes=hexc('3a1a0a'), watch=True, mustache=True),
        dict(skin=hexc('e2b898'), hair=hexc('2a2a2a'), hair_style='slick', top=hexc('101014'), top_kind='suit', pinstripe=True,
             tie=hexc('c09020'), shirt=hexc('f0f0f0'), arm_kind='long', cuff=hexc('f0f0f0'), pants=hexc('101014'), leg_kind='trousers',
             shoes=hexc('101010'), watch=True, glasses=True, glasses_color=hexc('c0a040')),
    ],
}

TYPES = ['salaryman', 'office_lady', 'yankee', 'gyaru', 'otaku', 'ojisan', 'macho', 'president']


def make_skin(spec, seed):
    a = canvas(64, 64, (0, 0, 0, 0))
    paint_head(a, spec, seed)
    paint_body(a, spec, seed)
    paint_arm(a, RARM, RSLEEVE, spec, seed, True)
    paint_arm(a, LARM, LSLEEVE, spec, seed + 50, False)
    paint_leg(a, RLEG, spec, seed, True)
    paint_leg(a, LLEG, spec, seed + 50, False)
    return a


def base_mask():
    m = np.zeros((64, 64), dtype=bool)
    for part in (HEAD, BODY, RARM, LARM, RLEG, LLEG):
        for k, (x, y, w, h) in part.items():
            if k in ('top', 'bottom') and part is not HEAD:
                continue
            m[y:y + h, x:x + w] = True
    return m


def blood_overlay(level, seed):
    a = canvas(64, 64, (0, 0, 0, 0))
    r = random.Random(seed)
    mask = base_mask()
    counts = {1: 9, 2: 22, 3: 48}
    rects = []
    for part in (HEAD, BODY, RARM, LARM, RLEG, LLEG):
        for k in SIDES:
            rects.append(part[k])
    weights = [w * h for (_, _, w, h) in rects]
    for n in range(counts[level]):
        x, y, w, h = r.choices(rects, weights=weights)[0]
        cx, cy = x + r.random() * w, y + r.random() * h * 0.8
        rad = r.choice([0.6, 0.9, 1.2, 1.6, 2.0]) * (1.0 + 0.25 * (level - 1))
        splat(a, cx, cy, rad, r, fresh=True, mask=mask, drips=r.randint(0, 2 + level))
    if level >= 2:
        # blood from the mouth / nose
        for (i, j) in ((11, 14), (12, 14), (11, 15), (12, 15), (12, 16) if False else (12, 15)):
            px(a, i, j, (150, 10, 12, 255))
    if level == 3:
        # neck & chest soaked
        for j in range(20, 25):
            for i in range(20, 28):
                if r.random() < 0.7:
                    px(a, i, j, (125 + r.randint(-20, 20), 6, 10, 255))
    return a


def gib_textures():
    # chunk 4x4x4 at (0,0), bone 2x7x2 at (0,8), caps 3x1x3 at (8,8) and (8,12)
    def meat_tex(seed, organ=False):
        a = canvas(32, 32, (0, 0, 0, 0))
        r = random.Random(seed)
        n = fbm(32, 32, seed, base=2.0, octaves=2)
        for f, (x, y, w, h) in box_uv(0, 0, 4, 4, 4).items():
            for j in range(h):
                for i in range(w):
                    k = n[y + j, x + i]
                    if organ:
                        c = (90 + 50 * k, 10 + 15 * k, 40 + 30 * k, 255)
                        if r.random() < 0.12:
                            c = (170, 90, 130, 255)
                    else:
                        c = (150 + 70 * k, 15 + 25 * k, 22 + 20 * k, 255)
                        if r.random() < 0.2:
                            c = (235, 200, 170, 255)
                        if f == 'up' and r.random() < 0.4:
                            c = (240, 232, 210, 255) if r.random() < 0.3 else c
                    px(a, x + i, y + j, c)
        return a

    save(meat_tex(31), 'entity/gib_meat.png')
    save(meat_tex(57, organ=True), 'entity/gib_organ.png')
    a = canvas(32, 32, (0, 0, 0, 0))
    r = random.Random(77)
    for (u, v, w, h, d) in ((0, 8, 2, 7, 2), (8, 8, 3, 1, 3), (8, 12, 3, 1, 3)):
        for f, (x, y, fw, fh) in box_uv(u, v, w, h, d).items():
            for j in range(fh):
                for i in range(fw):
                    c = (232 + r.randint(-14, 8), 224 + r.randint(-14, 8), 200 + r.randint(-14, 8), 255)
                    if r.random() < 0.12:
                        c = (160, 20, 24, 255)
                    px(a, x + i, y + j, c)
    save(a, 'entity/gib_bone.png')


def main():
    for t_i, t in enumerate(TYPES):
        for v, spec in enumerate(SPECS[t]):
            save(make_skin(spec, 1000 + t_i * 17 + v * 5), f'entity/contestant/{t}_{v}.png')
    for lvl in (1, 2, 3):
        save(blood_overlay(lvl, 400 + lvl), f'entity/blood_overlay_{lvl}.png')
    gib_textures()
    print('skins done')


if __name__ == '__main__':
    main()
