"""MOUNT rigs: the Moonmane Charger (warhorse), the Azurewing Griffin and the Starwheel Arcbike.

One rig per mount (rig names mount_horse, mount_griffin, mount_bike; one variant each). Own designs after the owner's
three reference pictures: a black warhorse with a white shard mane and tail, blue caparison with gold trim, white-gold
plate and blue gems; an armoured griffin with an eagle head under a gold crown, blue-and-white wings with gold leading
edges, gold talons, banners and a lantern; a pale-stone-and-gold motorcycle on glowing blue wheels with lantern lights,
crystals, saddlebags, a banner and a flag. The emblem is our own four-point star.

Conventions as in ermodel: absolute rest-pose coordinates, +Y down, ground at y=24, front -Z, right side -X.
Slots (Java order, see MountModel): horse idle/walk/gallop/jump, griffin idle/walk/fold/flap/glide, bike idle/ride.
Each variant's extra holds the hitbox (width, height in blocks), the seat part and, for the bike, the wheel radius;
generate_mounts.py measures the seat and writes MountBodies.java from them.
"""
import math

import numpy as np

from ermodel import C, P, Rig, Variant, anim
from erpaint import lighten, mix, rgb, shade

HORSE_SLOTS = ['idle', 'walk', 'gallop', 'jump']
GRIFFIN_SLOTS = ['idle', 'walk', 'fold', 'flap', 'glide']
BIKE_SLOTS = ['idle', 'ride']

GOLD = rgb('#e8b84a')
GOLD_DARK = rgb('#9a6a1c')
GOLD_LIGHT = rgb('#fff0a8')
BLUE = rgb('#2c44a8')
BLUE_DARK = rgb('#1a2668')
GEM = rgb('#4a9cff')
GEM_GLOW = (90, 180, 255)
EYE_GLOW = (120, 210, 255)
AMBER = (255, 196, 92)
AMBER_GLOW = (255, 170, 60)
WHITE = rgb('#eef1f7')


# ================================================================================================ paint helpers

def border(fc, color, width=1, sides='tblr', fine=True):
    """A trim along the edges of a face (fine pixels when the texture is painted at 2x)."""
    w, h = (fc.fw, fc.fh) if fine else (fc.w, fc.h)
    put = fc.fpx if fine else fc.px
    for i in range(width):
        if 't' in sides:
            for x in range(w):
                put(x, i, color)
        if 'b' in sides:
            for x in range(w):
                put(x, h - 1 - i, color)
        if 'l' in sides:
            for y in range(h):
                put(i, y, color)
        if 'r' in sides:
            for y in range(h):
                put(w - 1 - i, y, color)


def star(fc, cx, cy, size, color, edge=None, glow_fc=None, glow=None):
    """Our emblem: a four-point star centred at fine pixel (cx, cy), arms {@code size} long."""
    for dy in range(-size, size + 1):
        for dx in range(-size, size + 1):
            ax, ay = abs(dx), abs(dy)
            inside = (ax == 0 and ay <= size) or (ay == 0 and ax <= size) or (ax + ay <= max(1, size // 2))
            if not inside:
                continue
            c = color
            if edge is not None and (ax + ay == max(1, size // 2) or ax == size or ay == size):
                c = edge
            fc.fpx(cx + dx, cy + dy, c)
            if glow_fc is not None:
                glow_fc.fpx(cx + dx, cy + dy, glow)


def pointed_bottom(fc, depth, step=None):
    """Cuts the bottom edge of a cloth face into points (banner tabs): transparent between the tips."""
    w, h = fc.fw, fc.fh
    step = step or w
    for x in range(w):
        phase = (x % step) / max(1, step - 1)
        cut = int(round(depth * abs(phase * 2.0 - 1.0)))
        for k in range(cut):
            fc.fclear(x, h - 1 - k)


def gem_face(fc, glow_fc, color=GEM, glow=GEM_GLOW):
    """A faceted gem filling a face, its glow on the emissive layer."""
    w, h = fc.fw, fc.fh
    for y in range(h):
        for x in range(w):
            t = (x + y) / max(1, w + h - 2)
            fc.fpx(x, y, shade(color, 1.25 - 0.5 * t))
            glow_fc.fpx(x, y, tuple(int(v * (0.75 + 0.25 * (1 - t))) for v in glow))
    fc.fpx(0, 0, (255, 255, 255))
    glow_fc.fpx(0, 0, (220, 240, 255))


def lantern_faces(p, part, index, frame=GOLD_DARK, light=AMBER, glow=AMBER_GLOW):
    """Panes of a lantern box: a gold frame, warm light inside (also on the emissive layer)."""
    for fc, gc in zip(p.all_faces(part, index), p.all_faces(part, index, glow=True)):
        w, h = fc.fw, fc.fh
        for y in range(h):
            for x in range(w):
                edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
                bar = fc.face not in ('top', 'bottom') and (x == w // 2 and w > 4)
                if edge or bar:
                    fc.fpx(x, y, frame if not edge or (x + y) % 2 else shade(frame, 1.3))
                else:
                    c = mix(light, (255, 245, 210), 0.35 if (x + y) % 3 == 0 else 0.0)
                    fc.fpx(x, y, c)
                    gc.fpx(x, y, glow)


def eye(fc, glow_fc, x, y, color=EYE_GLOW, size=2):
    """A glowing eye of {@code size} fine pixels at fine (x, y)."""
    for dy in range(size):
        for dx in range(size):
            fc.fpx(x + dx, y + dy, color)
            glow_fc.fpx(x + dx, y + dy, color)
    fc.fpx(x, y, (235, 250, 255))


def gold_trim(p, part, index=0, sides='tb', faces=('front', 'back', 'right', 'left'), width=1):
    for name in faces:
        border(p.face(part, name, index), GOLD, width, sides)


# ================================================================================================ the horse

def horse_leg(name, x, z, hind, tag=None, share=None, mirror=False):
    """Forearm or thigh with a white-gold guard at the knee or hock, a long cannon in a white-gold greave, a black
    hoof."""
    shin_name = name.replace('_leg', '_shin')
    t = (lambda s: tag and tag + s)
    sh = (lambda s: share and share + s)
    if hind:
        top, knee_y = 4, 12
        upper = C(x - 2.5, 1, z - 3, 5, 11, 6, 'fur:coat', tag=t('u'), share=sh('u'), mirror=mirror)
        guard = C(x - 2.5, 10.5, z - 2.5, 5, 3, 5, 'metal:greave', tag=t('k'), share=sh('k'), mirror=mirror)
    else:
        top, knee_y = 5, 13
        upper = C(x - 2, 4, z - 2, 4, 9, 4, 'fur:coat', tag=t('u'), share=sh('u'), mirror=mirror)
        guard = C(x - 2.5, 11, z - 2.5, 5, 3, 5, 'metal:greave', tag=t('k'), share=sh('k'), mirror=mirror)
    shin = P(shin_name, (x, knee_y, z),
             C(x - 1.5, knee_y, z - 1.5, 3, 22 - knee_y, 3, 'fur:coat', tag=t('s'), share=sh('s'), mirror=mirror),
             C(x - 2, 15.5, z - 2, 4, 5, 4, 'metal:greave', tag=t('g'), share=sh('g'), mirror=mirror),
             C(x - 2, 21, z - 2, 4, 1, 4, 'hair:fetlock', tag=t('f'), share=sh('f'), mirror=mirror),
             C(x - 2, 22, z - 2.5, 4, 2, 5, 'plain:hoof', tag=t('h'), share=sh('h'), mirror=mirror))
    return P(name, (x, top, z), upper, guard, shin)


def horse_geometry():
    shards = []
    # the mane: shards falling back and down both sides of the neck from the crest, a few flaring up on top
    for i, (y, side, rx, ry, rz, width, length) in enumerate((
            (-15, 1, -40, 8, 38, 3, 7), (-14, -1, -36, -8, -40, 3, 7), (-11, 1, -50, 10, 46, 3, 8),
            (-10, -1, -48, -10, -48, 3, 8), (-7, 1, -58, 8, 50, 3, 8), (-6, -1, -56, -8, -52, 3, 8),
            (-3, 1, -64, 6, 48, 3, 7), (-2, -1, -62, -6, -50, 3, 7), (-15.5, 0, -6, 0, 0, 2, 7),
            (-11.5, 0, -14, 0, 0, 2, 7), (-7.5, 0, -22, 0, 0, 2, 6))):
        shards.append(P('mane_%d' % (i + 1), (0.8 * side, y, -4.5),
                        C(-width / 2.0 + 0.8 * side, y - 1, -4.5, width, 2, length, 'hair:mane'), rot=(rx, ry, rz)))
    head = P('head', (0, -13, -10),
             C(-3, -17, -17, 6, 6, 8, 'fur:coat'),
             C(-2.5, -16, -23, 5, 5, 6, 'fur:muzzle'),
             C(-3, -16.5, -21.5, 6, 2, 6, 'metal:chanfron'),
             C(-1, -17.5, -18.5, 2, 1, 2, 'gem:gem'),
             C(-1.5, -18.5, -15.5, 3, 2, 4, 'hair:mane'),
             P('ear_r', (-2, -17, -11), C(-2.75, -20, -11.5, 2, 3, 1, 'fur:coat', tag='ear'), rot=(0, 0, -12)),
             P('ear_l', (2, -17, -11), C(0.75, -20, -11.5, 2, 3, 1, 'fur:coat', share='ear', mirror=True),
               rot=(0, 0, 12)),
             rot=(16, 0, 0))
    neck = P('neck', (0, -1, -9),
             C(-3.5, -14, -13.5, 7, 14, 8, 'fur:coat'),
             C(-1.5, -16, -6.5, 3, 17, 3, 'hair:mane'),
             C(-4, -5, -14, 8, 5, 7, 'metal:plate'),
             head, *shards, rot=(18, 0, 0))
    tail = P('tail', (0, -2, 11), C(-2, -3, 10, 4, 4, 4, 'hair:tail'),
             P('tail2', (0, -1.5, 13), C(-3, -2.5, 12, 6, 9, 4, 'hair:tail'),
               P('tail3', (0, 6.5, 13.5), C(-3.5, 6, 12, 7, 8, 4, 'hair:tail'),
                 P('tail4', (0, 13.5, 14), C(-3, 13.5, 12.5, 6, 5, 3, 'hair:tail'),
                   P('tail_strand_1', (-2, 18, 14), C(-3.5, 18, 13, 3, 5, 2, 'hair:tail'), rot=(0, 0, -14)),
                   P('tail_strand_2', (0, 18, 14), C(-1, 18, 13.5, 2, 6, 2, 'hair:tail'), rot=(-8, 0, 0)),
                   P('tail_strand_3', (2, 18, 14), C(0.5, 18, 13, 3, 4, 2, 'hair:tail'), rot=(0, 0, 16)),
                   rot=(-10, 0, 0)),
                 P('tail_shard_1', (-3, 8, 14), C(-5, 8, 13, 2, 2, 6, 'hair:tail'), rot=(-50, -20, -30)),
                 P('tail_shard_2', (3, 10, 14), C(3, 10, 13, 2, 2, 6, 'hair:tail'), rot=(-56, 20, 30)),
                 rot=(-14, 0, 0)),
               P('tail_shard_3', (-2.5, 1, 14), C(-4.5, 1, 13, 2, 2, 6, 'hair:tail'), rot=(-30, -24, -20)),
               P('tail_shard_4', (2.5, 2, 14), C(2.5, 2, 13, 2, 2, 6, 'hair:tail'), rot=(-36, 24, 20)),
               rot=(38, 0, 0)))
    body = P('body', (0, 3, 0),
             C(-5, -3, -11, 10, 10, 22, 'fur:coat'),
             C(-5.5, -3.5, -5, 11, 3, 11, 'cloth:blanket'),
             P('saddle', (0, -4, 0), C(-4, -5, -4, 8, 2, 9, 'leather:saddle'),
               C(-2.5, -7, -4.5, 5, 2, 2, 'leather:saddle_rim'), C(-3.5, -7, 3.5, 7, 2, 2, 'leather:saddle_rim')),
             C(-6, -2, -10, 1, 11, 19, 'cloth:caparison', tag='cap'),
             C(5, -2, -10, 1, 11, 19, 'cloth:caparison', share='cap', mirror=True),
             C(-5.5, -2.5, -13, 11, 8, 3, 'fur:coat', tag='chest'),
             C(-6, -2, -14.5, 12, 7, 2, 'metal:plate', tag='peytral'),
             C(-1.5, -0.5, -15.5, 3, 4, 1, 'gem:gem', tag='chest_gem'),
             C(-6.5, -3, -10.5, 2, 6, 6, 'metal:plate', tag='pauldron'),
             C(4.5, -3, -10.5, 2, 6, 6, 'metal:plate', share='pauldron', mirror=True),
             C(-7.5, -1.5, 5, 2, 5, 5, 'leather:bag', tag='bag'),
             C(5.5, -1.5, 5, 2, 5, 5, 'leather:bag', share='bag', mirror=True),
             neck, tail,
             horse_leg('front_right_leg', -3, -7, False, tag='fl'),
             horse_leg('front_left_leg', 3, -7, False, share='fl', mirror=True),
             horse_leg('hind_right_leg', -3, 7, True, tag='hl'),
             horse_leg('hind_left_leg', 3, 7, True, share='hl', mirror=True))
    return [P('base', (0, 24, 0), body)]


def horse_animations():
    A = []
    A.append(anim('idle', 3.2, loop=True,
                  body=dict(scale=[(0, 1, 1, 1), (1.6, 1.015, 1.02, 1.0), (3.2, 1, 1, 1)]),
                  neck=dict(rot=[(0, 0, 0, 0), (1.0, -3, 2, 0), (2.0, 1, -2, 0), (3.2, 0, 0, 0)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.8, 3, 4, 0), (1.8, -2, -3, 0), (3.2, 0, 0, 0)]),
                  ear_r=dict(rot=[(0, 0, 0, 0), (1.1, 0, 0, 0), (1.2, -20, 0, -10), (1.4, 0, 0, 0), (3.2, 0, 0, 0)]),
                  ear_l=dict(rot=[(0, 0, 0, 0), (2.2, 0, 0, 0), (2.3, -20, 0, 10), (2.5, 0, 0, 0), (3.2, 0, 0, 0)]),
                  tail=dict(rot=[(0, 0, 6, 0), (1.6, 0, -6, 0), (3.2, 0, 6, 0)]),
                  tail2=dict(rot=[(0, 0, 0, 3), (1.6, 0, 0, -3), (3.2, 0, 0, 3)]),
                  tail3=dict(rot=[(0, 0, 0, -4), (1.6, 0, 0, 4), (3.2, 0, 0, -4)]),
                  mane_1=dict(rot=[(0, 0, 0, 0), (1.6, 4, 2, 0), (3.2, 0, 0, 0)]),
                  mane_3=dict(rot=[(0, 0, 0, 0), (1.6, -3, -2, 0), (3.2, 0, 0, 0)]),
                  mane_5=dict(rot=[(0, 0, 0, 0), (1.6, 3, 2, 0), (3.2, 0, 0, 0)])))
    # walk: a four-beat walk (right hind, right fore, left hind, left fore), head nodding
    swing = 18

    def leg_cycle(offset, length, amp, knee):
        keys, shin = [], []
        for i in range(5):
            t = length * i / 4.0
            a = math.sin(2 * math.pi * (i / 4.0 + offset))
            keys.append((t, amp * a, 0, 0))
            shin.append((t, knee * max(0.0, math.cos(2 * math.pi * (i / 4.0 + offset))), 0, 0))
        return keys, shin

    w = {}
    for leg, off in (('hind_right', 0.0), ('front_right', 0.25), ('hind_left', 0.5), ('front_left', 0.75)):
        keys, shin = leg_cycle(off, 1.0, swing, 34 if leg.startswith('front') else 26)
        w[leg + '_leg'] = dict(rot=keys)
        w[leg + '_shin'] = dict(rot=shin)
    A.append(anim('walk', 1.0, loop=True,
                  body=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0.35, 0), (0.5, 0, 0, 0), (0.75, 0, 0.35, 0), (1.0, 0, 0, 0)]),
                  neck=dict(rot=[(0, 3, 0, 0), (0.25, -2, 0, 0), (0.5, 3, 0, 0), (0.75, -2, 0, 0), (1.0, 3, 0, 0)]),
                  tail=dict(rot=[(0, 0, 8, 0), (0.5, 0, -8, 0), (1.0, 0, 8, 0)]),
                  **w))
    # gallop: fores reach together, hinds drive; the body rocks, mane and tail stream back
    g = {}
    for leg, off, amp in (('front_right', 0.0, 46), ('front_left', 0.08, 46), ('hind_right', 0.45, 40),
                          ('hind_left', 0.53, 40)):
        keys, shin = [], []
        for i in range(9):
            t = 0.5 * i / 8.0
            ph = 2 * math.pi * (i / 8.0 + off)
            keys.append((t, amp * math.sin(ph), 0, 0))
            shin.append((t, (50 if leg.startswith('front') else 30) * max(0.0, -math.cos(ph)), 0, 0))
        g[leg + '_leg'] = dict(rot=keys)
        g[leg + '_shin'] = dict(rot=shin)
    stream = dict(('mane_%d' % i, dict(rot=[(0, 20, 0, 0), (0.25, 28, 0, 0), (0.5, 20, 0, 0)])) for i in range(1, 12))
    A.append(anim('gallop', 0.5, loop=True,
                  body=dict(rot=[(0, -6, 0, 0), (0.25, 5, 0, 0), (0.5, -6, 0, 0)],
                            pos=[(0, 0, 0.9, 0), (0.125, 0, 0.2, 0), (0.25, 0, 0, 0), (0.375, 0, 0.6, 0),
                                 (0.5, 0, 0.9, 0)]),
                  neck=dict(rot=[(0, 14, 0, 0), (0.25, 2, 0, 0), (0.5, 14, 0, 0)]),
                  head=dict(rot=[(0, -10, 0, 0), (0.25, -2, 0, 0), (0.5, -10, 0, 0)]),
                  tail=dict(rot=[(0, 24, 0, 0), (0.25, 32, 0, 0), (0.5, 24, 0, 0)]),
                  tail2=dict(rot=[(0, 30, 0, 0), (0.25, 22, 0, 0), (0.5, 30, 0, 0)]),
                  tail3=dict(rot=[(0, 18, 0, 0), (0.25, 10, 0, 0), (0.5, 18, 0, 0)]),
                  **stream, **g))
    A.append(anim('jump', 0.6,
                  body=dict(rot=[(0, 0, 0, 0), (0.15, -14, 0, 0), (0.6, -8, 0, 0)]),
                  neck=dict(rot=[(0, 0, 0, 0), (0.15, 10, 0, 0), (0.6, 8, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.15, -50, 0, 0), (0.6, -40, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0), (0.15, -46, 0, 0), (0.6, -38, 0, 0)]),
                  front_right_shin=dict(rot=[(0, 0, 0, 0), (0.15, 80, 0, 0), (0.6, 70, 0, 0)]),
                  front_left_shin=dict(rot=[(0, 0, 0, 0), (0.15, 76, 0, 0), (0.6, 66, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0), (0.15, 40, 0, 0), (0.6, 30, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0), (0.15, 36, 0, 0), (0.6, 28, 0, 0)]),
                  tail=dict(rot=[(0, 0, 0, 0), (0.15, 45, 0, 0), (0.6, 40, 0, 0)])))
    return A


def diamond(fc, glow_fc, color=GEM, glow=GEM_GLOW):
    """A cut gem: a diamond shape (transparent corners) with a light upper facet."""
    w, h = fc.fw, fc.fh
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for y in range(h):
        for x in range(w):
            d = abs(x - cx) / max(0.5, cx + 0.5) + abs(y - cy) / max(0.5, cy + 0.5)
            if d > 1.0:
                fc.fclear(x, y)
                continue
            upper = y < cy
            fc.fpx(x, y, shade(color, 1.3 if upper and x < cx else 1.05 if upper else 0.8))
            glow_fc.fpx(x, y, glow)


def plate_trim(p, part, tag=None, index=0, faces=('front', 'right', 'left', 'top', 'back', 'bottom')):
    for name in faces:
        border(p.face(part, name, index=index, tag=tag), GOLD, 1, 'tblr')


def horse_paint(p):
    k = p.k
    # caparison: blue, a gold-trimmed front panel with the star and a rear panel, pointed tabs at the hem
    for name in ('right', 'left'):
        fc = p.face('body', name, tag='cap')
        w, h = fc.fw, fc.fh
        split = int(w * 0.42) if name == 'left' else w - int(w * 0.42)
        for y in range(h):
            fc.fpx(split, y, GOLD)
            fc.fpx(split + 1, y, GOLD_DARK)
        border(fc, GOLD, 1, 'tblr')
        for x in range(w):
            fc.fpx(x, h - 3 * k - 1, GOLD)
        pointed_bottom(fc, 3 * k, step=6 * k)
        front_mid = (split + w) // 2 if name == 'left' else split // 2
        star(fc, front_mid, h // 2 - 2 * k, 5, WHITE, edge=GOLD)
    for name in ('front', 'back'):
        pointed_bottom(p.face('body', name, tag='cap'), 3 * k, step=max(2, p.face('body', name, tag='cap').fw))
    blanket = p.face('body', 'top', index=1)
    border(blanket, GOLD, 1, 'tblr')
    for name in ('right', 'left', 'front', 'back'):
        border(p.face('body', name, index=1), GOLD, 1, 'b')
    # white plate with gold rims: chest, shoulders, the collar of the neck, the head's chanfron, the legs' guards
    plate_trim(p, 'body', tag='peytral')
    plate_trim(p, 'body', tag='pauldron')
    plate_trim(p, 'neck', index=2)
    plate_trim(p, 'head', index=2, faces=('top', 'front', 'right', 'left'))
    for leg in ('front_right_leg', 'hind_right_leg'):
        plate_trim(p, leg, index=1, faces=('front', 'back', 'right', 'left'))
        fc = p.face(leg, 'front', index=1)
        star(fc, fc.fw // 2, fc.fh // 2, 1, GOLD_LIGHT)
    for leg in ('front_right_shin', 'hind_right_shin'):
        plate_trim(p, leg, index=1, faces=('front', 'back', 'right', 'left'))
        fc = p.face(leg, 'front', index=1)
        for y in range(2, fc.fh - 2):
            fc.fpx(fc.fw // 2, y, GOLD)
    # the gems: chest (a big cut gem) and forehead
    for name in ('front',):
        diamond(p.face('body', name, tag='chest_gem'), p.face('body', name, tag='chest_gem', glow=True))
    for name in ('right', 'left', 'top', 'bottom', 'back'):
        gem_face(p.face('body', name, tag='chest_gem'), p.face('body', name, tag='chest_gem', glow=True))
    fc = p.face('body', 'front', tag='peytral')
    cx, cy = fc.fw // 2, 7
    for dy in range(-6, 7):
        for dx in range(-6, 7):
            if abs(dx) + abs(dy) in (5, 6):
                fc.fpx(cx + dx - (1 if dx < 0 else 0), cy + dy, GOLD if abs(dx) + abs(dy) == 5 else GOLD_DARK)
    for name in ('top', 'front', 'right', 'left'):
        gem_face(p.face('head', name, index=3), p.face('head', name, index=3, glow=True))
    # head: glowing eyes, a gold bridle with a gem at the cheek, nostrils
    for name in ('right', 'left'):
        skull = p.face('head', name, index=0)
        ex = skull.fw - 6 if name == 'right' else 4
        eye(skull, p.face('head', name, index=0, glow=True), ex, 5, size=2)
        for x in range(skull.fw):
            skull.fpx(x, skull.fh - 3, GOLD_DARK)
        muzzle = p.face('head', name, index=1)
        gm = p.face('head', name, index=1, glow=True)
        for x in range(muzzle.fw):
            muzzle.fpx(x, 3, GOLD)
        cx = muzzle.fw - 3 if name == 'right' else 2
        for y in range(muzzle.fh):
            muzzle.fpx(cx, y, GOLD)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if abs(dx) + abs(dy) < 2:
                    muzzle.fpx(cx + dx, 3 + dy, GEM)
                    gm.fpx(cx + dx, 3 + dy, GEM_GLOW)
        muzzle.fpx(1 if name == 'right' else muzzle.fw - 2, muzzle.fh - 4, (10, 10, 14))
    front = p.face('head', 'front', index=1)
    for x in range(front.fw):
        front.fpx(x, 3, GOLD)
    for x in (2, front.fw - 3):
        front.fpx(x, front.fh - 4, (10, 10, 14))
    # saddlebags: straps and buckles
    for name in ('right', 'left', 'front', 'back'):
        fc = p.face('body', name, tag='bag')
        for x in range(fc.fw):
            fc.fpx(x, 2, shade(rgb('#5a3418'), 0.8))
        fc.fpx(fc.fw // 2, 3, GOLD)
        fc.fpx(fc.fw // 2, 4, GOLD)


def horse_variant():
    pal = {
        'coat': {'base': '#25242e', 'style': 'fur', 'dark': '#14141a', 'light': '#3a3a4a'},
        'muzzle': {'base': '#302f3a', 'style': 'fur', 'dark': '#1c1b24', 'light': '#46455a'},
        'mane': {'base': '#eef1f7', 'style': 'hair'},
        'tail': {'base': '#eaeef6', 'style': 'hair'},
        'fetlock': {'base': '#e6eaf2', 'style': 'hair'},
        'hoof': '#1a1a20',
        'caparison': {'base': '#2a3f9c', 'style': 'cloth'},
        'blanket': {'base': '#1e2c74', 'style': 'cloth'},
        'saddle': {'base': '#7a4826', 'style': 'leather'},
        'saddle_rim': {'base': '#5a3218', 'style': 'leather'},
        'bag': {'base': '#6e4022', 'style': 'leather'},
        'plate': {'base': '#e2e6ee', 'style': 'metal', 'rivets': False},
        'chanfron': {'base': '#e4e8f0', 'style': 'metal', 'rivets': False},
        'greave': {'base': '#dfe3ec', 'style': 'metal', 'rivets': False},
        'gem': {'base': '#4a9cff', 'style': 'gem'},
    }
    return Variant('horse', palette=pal, paint=horse_paint, glow=True,
                   extra=dict(scale=1.0, hitbox=(1.3, 1.75), seat='saddle'))


def horse_rig():
    return Rig('mount_horse', 'HorseMount', horse_geometry(), horse_animations(), [horse_variant()], HORSE_SLOTS,
               texel=2)


# ================================================================================================ the griffin

def griffin_wing(side):
    """Arm with coverts, a row of secondaries and a gold armoured leading bone with a gem; the hand with its own
    coverts, a gold wrist plate, bone and gem, and six long primaries fanned out (each its own part).
    side 'r' (-X) or 'l' (+X)."""
    s = -1 if side == 'r' else 1
    mirror = side == 'l'

    def X(a, w):
        """Box x for a box spanning |x| from a to a + w on this side."""
        return -(a + w) if s < 0 else a

    def tg(t):
        return t if side == 'r' else None

    def sh(t):
        return t if side == 'l' else None

    primaries = []
    for i, (r, length, fan) in enumerate(((20, 15, 2), (22.5, 17, 10), (25, 19, 20), (27.5, 20, 31), (30, 19, 44),
                                          (32, 16, 58))):
        primaries.append(P('prim_%s%d' % (side, i + 1), (s * r, 0, -0.5),
                           C(X(r - 1.5, 3), -0.5, -1, 3, 1, length, 'feather:%s' % ('pw' if i % 2 else 'pb'),
                             tag=tg('pf%d' % i), share=sh('pf%d' % i), mirror=mirror),
                           rot=(0, s * fan, 0)))
    secondaries = [C(X(4 + 3 * i, 3), -0.5, 1.5, 3, 1, 9 + i, 'feather:%s' % ('sw' if i % 2 else 'sb'),
                     tag=tg('sf%d' % i), share=sh('sf%d' % i), mirror=mirror) for i in range(5)]
    hand = P('wing_%s2' % side, (19 * s, 0, -5),
             C(X(19, 13), -1, -6, 13, 2, 6, 'feather:hand', tag=tg('hand'), share=sh('hand'), mirror=mirror),
             C(X(19, 14), -2, -8, 14, 3, 3, 'gold:edge', tag=tg('edge2'), share=sh('edge2'), mirror=mirror),
             C(X(18, 4), -2.5, -8.5, 4, 4, 4, 'gold:wrist', tag=tg('wrist'), share=sh('wrist'), mirror=mirror),
             C(X(27, 2), -3, -9, 2, 1, 2, 'gem:gem', tag=tg('wgem2'), share=sh('wgem2'), mirror=mirror),
             *primaries)
    return P('wing_%s' % side, (5 * s, 0, -4),
             C(X(5, 14), -1, -6, 14, 2, 8, 'feather:covert', tag=tg('cov'), share=sh('cov'), mirror=mirror),
             C(X(5, 14), -2, -8, 14, 3, 3, 'gold:edge', tag=tg('edge1'), share=sh('edge1'), mirror=mirror),
             C(X(11, 2), -3, -8.5, 2, 1, 2, 'gem:gem', tag=tg('wgem1'), share=sh('wgem1'), mirror=mirror),
             *secondaries, hand)


def griffin_leg(name, x, z, hind, tag=None, share=None, mirror=False):
    shin_name = name.replace('_leg', '_shin')
    t = (lambda s: tag and tag + s)
    sh = (lambda s: share and share + s)
    if hind:
        upper = C(x - 2.5, 4, z - 2.5, 5, 9, 5, 'feather:thigh', tag=t('u'), share=sh('u'), mirror=mirror)
        shin = P(shin_name, (x, 13, z),
                 C(x - 1.5, 13, z - 1.5, 3, 8, 3, 'feather:leg', tag=t('s'), share=sh('s'), mirror=mirror),
                 C(x - 2, 21, z - 3.5, 4, 3, 5, 'gold:foot', tag=t('f'), share=sh('f'), mirror=mirror),
                 C(x - 2, 22.5, z - 5, 1, 1, 2, 'plain:claw', tag=t('c1'), share=sh('c1'), mirror=mirror),
                 C(x + 1, 22.5, z - 5, 1, 1, 2, 'plain:claw', tag=t('c2'), share=sh('c2'), mirror=mirror))
        return P(name, (x, 6, z), upper, shin)
    upper = C(x - 2, 7, z - 2, 4, 6, 4, 'feather:leg', tag=t('u'), share=sh('u'), mirror=mirror)
    shin = P(shin_name, (x, 13, z),
             C(x - 1, 13, z - 1, 2, 8, 2, 'scale:shin', tag=t('s'), share=sh('s'), mirror=mirror),
             C(x - 2, 21, z - 3, 4, 3, 4, 'gold:foot', tag=t('f'), share=sh('f'), mirror=mirror),
             C(x - 2, 22, z - 5, 1, 2, 2, 'plain:claw', tag=t('c1'), share=sh('c1'), mirror=mirror),
             C(x - 0.5, 22, z - 5, 1, 2, 2, 'plain:claw', tag=t('c2'), share=sh('c2'), mirror=mirror),
             C(x + 1, 22, z - 5, 1, 2, 2, 'plain:claw', tag=t('c3'), share=sh('c3'), mirror=mirror),
             C(x - 0.5, 21.5, z + 1, 1, 2, 2, 'plain:claw', tag=t('c4'), share=sh('c4'), mirror=mirror))
    return P(name, (x, 8, z), upper, shin)


def griffin_geometry():
    tufts = [P('crest_%d' % (i + 1), (x, y, z), C(x - w / 2.0, y - 0.5, z, w, 1, ln, 'feather:tuft'), rot=(rx, ry, rz))
             for i, (x, y, z, w, ln, rx, ry, rz) in enumerate(((0, -12.5, -9, 4, 7, 24, 0, 0),
                                                                (0, -10.5, -8.5, 6, 6, 8, 0, 0),
                                                                (-3, -11, -10, 2, 6, 20, 30, -20),
                                                                (3, -11, -10, 2, 6, 20, -30, 20),
                                                                (-3.5, -8.5, -11, 2, 5, 0, 38, -10),
                                                                (3.5, -8.5, -11, 2, 5, 0, -38, 10)))]
    head = P('head', (0, -7, -10),
             C(-3.5, -13, -15, 7, 6, 8, 'feather:head', tag='skull'),
             C(-4, -12.5, -16.5, 8, 2, 4, 'feather:brow', tag='brow'),
             C(-4, -10, -14.5, 8, 4, 6, 'feather:cheek', tag='cheek'),
             C(-2, -11.5, -19.5, 4, 3, 3, 'gold:beak', tag='beak'),
             C(-1.5, -11, -21.5, 3, 3, 2, 'gold:beak', tag='beak2'),
             C(-1, -10, -22.5, 2, 4, 1, 'gold:beak_tip', tag='hook'),
             C(-1.5, -8.5, -19.5, 3, 1, 3, 'gold:beak_low', tag='jaw'),
             C(-4, -14.5, -15.5, 8, 2, 7, 'gold:crown', tag='crown'),
             C(-0.5, -18.5, -14, 1, 4, 2, 'gold:crown', tag='spike'),
             C(-4.5, -16.5, -13, 1, 3, 3, 'gold:crown', tag='wing_crest'),
             C(3.5, -16.5, -13, 1, 3, 3, 'gold:crown', share='wing_crest', mirror=True),
             C(-1, -15, -16.5, 2, 2, 1, 'gem:gem', tag='crown_gem'),
             *tufts)
    neck = P('neck', (0, 1, -8),
             C(-3, -8, -12, 6, 10, 6, 'feather:neck'),
             C(-3.5, -4, -12.5, 7, 6, 7, 'feather:ruff'),
             head, rot=(22, 0, 0))
    tail = P('tail', (0, 2, 9), C(-4, 1.5, 9, 8, 1, 9, 'feather:tail'),
             P('tail2', (0, 2, 17.5), C(-5, 1.5, 17.5, 10, 1, 6, 'feather:tailtip')), rot=(-12, 0, 0))
    body = P('body', (0, 6, 0),
             C(-5, 0, -9, 10, 9, 18, 'feather:body'),
             C(-5.5, -1, -11, 11, 9, 4, 'feather:ruff'),
             C(-2.5, 1.5, -11.5, 5, 5, 1, 'gold:plate'),
             C(-1, 2.5, -12.5, 2, 2, 1, 'gem:gem'),
             P('saddle', (0, -1, 0), C(-4, -2, -3, 8, 2, 8, 'leather:saddle'),
               C(-2.5, -4, -3.5, 5, 2, 2, 'leather:saddle_rim'), C(-3.5, -4, 3.5, 7, 2, 2, 'leather:saddle_rim')),
             C(-5.5, -0.5, -4, 11, 1, 1, 'gold:strap'),
             C(-5.5, -0.5, 4, 11, 1, 1, 'gold:strap'),
             P('banner_r', (-5.5, 1, 4), C(-6, 1, 1.5, 1, 11, 5, 'cloth:banner', tag='banner')),
             P('banner_l', (5.5, 1, 4), C(5, 1, 1.5, 1, 11, 5, 'cloth:banner', share='banner', mirror=True)),
             P('lantern', (-6, 1, -10), C(-6.5, 1, -10.5, 1, 2, 1, 'chain:chain'),
               C(-7.5, 3, -11.5, 3, 4, 3, 'gold:lantern'), C(-7, 7, -11, 2, 1, 2, 'gold:lantern_cap')),
             neck, tail, griffin_wing('r'), griffin_wing('l'),
             griffin_leg('front_right_leg', -3, -6, False, tag='fl'),
             griffin_leg('front_left_leg', 3, -6, False, share='fl', mirror=True),
             griffin_leg('hind_right_leg', -3, 6, True, tag='hl'),
             griffin_leg('hind_left_leg', 3, 6, True, share='hl', mirror=True))
    return [P('base', (0, 24, 0), body)]


def griffin_animations():
    A = []
    A.append(anim('idle', 3.0, loop=True,
                  body=dict(scale=[(0, 1, 1, 1), (1.5, 1.015, 1.025, 1.0), (3.0, 1, 1, 1)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, 4, 14, 0), (1.2, 0, 14, 0), (1.5, -3, -8, 0), (2.4, 0, -8, 0),
                                 (3.0, 0, 0, 0)]),
                  crest_1=dict(rot=[(0, 0, 0, 0), (1.5, 6, 0, 0), (3.0, 0, 0, 0)]),
                  crest_2=dict(rot=[(0, 0, 0, 0), (1.5, -5, 0, 0), (3.0, 0, 0, 0)]),
                  tail=dict(rot=[(0, 0, 5, 0), (1.5, 0, -5, 0), (3.0, 0, 5, 0)]),
                  banner_r=dict(rot=[(0, 0, 0, 2), (1.5, 0, 0, -2), (3.0, 0, 0, 2)]),
                  banner_l=dict(rot=[(0, 0, 0, -2), (1.5, 0, 0, 2), (3.0, 0, 0, -2)]),
                  lantern=dict(rot=[(0, 3, 0, 4), (1.5, -3, 0, -4), (3.0, 3, 0, 4)])))
    w = {}
    for leg, off in (('front_right', 0.0), ('hind_left', 0.0), ('front_left', 0.5), ('hind_right', 0.5)):
        keys, shin = [], []
        for i in range(5):
            t = i / 4.0
            ph = 2 * math.pi * (i / 4.0 + off)
            keys.append((t, 22 * math.sin(ph), 0, 0))
            shin.append((t, 18 * max(0.0, math.cos(ph)), 0, 0))
        w[leg + '_leg'] = dict(rot=keys)
        w[leg + '_shin'] = dict(rot=shin)
    A.append(anim('walk', 1.0, loop=True,
                  body=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0.3, 0), (0.5, 0, 0, 0), (0.75, 0, 0.3, 0), (1.0, 0, 0, 0)]),
                  neck=dict(rot=[(0, 4, 0, 0), (0.25, -3, 0, 0), (0.5, 4, 0, 0), (0.75, -3, 0, 0), (1.0, 4, 0, 0)]),
                  **w))
    # folded: each wing swings down along the flank with its feathers trailing back, the hand folds back up along the
    # arm (a bird's Z-fold) and the primaries close their fan
    folded = dict(wing_r=dict(rot=[(0, 0, -6, -90)], pos=[(0, -2.5, 0, 0)]),
                  wing_l=dict(rot=[(0, 0, 6, 90)], pos=[(0, 2.5, 0, 0)]),
                  wing_r2=dict(rot=[(0, 0, 0, 166)]), wing_l2=dict(rot=[(0, 0, 0, -166)]))
    for i, fan in enumerate((2, 10, 20, 31, 44, 58)):
        folded['prim_r%d' % (i + 1)] = dict(rot=[(0, 0, fan, 0)])
        folded['prim_l%d' % (i + 1)] = dict(rot=[(0, 0, -fan, 0)])
    A.append(anim('fold', 1.0, loop=True, **{b: {ch: keys + [(1.0,) + keys[0][1:]] for ch, keys in v.items()}
                                             for b, v in folded.items()}))
    tuck = dict(front_right_leg=dict(rot=[(0, 55, 0, 0)]), front_left_leg=dict(rot=[(0, 55, 0, 0)]),
                front_right_shin=dict(rot=[(0, -70, 0, 0)]), front_left_shin=dict(rot=[(0, -70, 0, 0)]),
                hind_right_leg=dict(rot=[(0, 60, 0, 0)]), hind_left_leg=dict(rot=[(0, 60, 0, 0)]),
                hind_right_shin=dict(rot=[(0, 30, 0, 0)]), hind_left_shin=dict(rot=[(0, 30, 0, 0)]))

    def held(spec, length):
        return {b: dict(rot=v['rot'] + [(length,) + v['rot'][0][1:]]) for b, v in spec.items()}
    L = 0.7
    A.append(anim('flap', L, loop=True,
                  wing_r=dict(rot=[(0, 0, 0, 42), (L * 0.5, 0, -6, -34), (L, 0, 0, 42)]),
                  wing_l=dict(rot=[(0, 0, 0, -42), (L * 0.5, 0, 6, 34), (L, 0, 0, -42)]),
                  wing_r2=dict(rot=[(0, 0, 0, -14), (L * 0.25, 0, 0, 10), (L * 0.5, 0, 0, -10), (L * 0.75, 0, 0, -26),
                                    (L, 0, 0, -14)]),
                  wing_l2=dict(rot=[(0, 0, 0, 14), (L * 0.25, 0, 0, -10), (L * 0.5, 0, 0, 10), (L * 0.75, 0, 0, 26),
                                    (L, 0, 0, 14)]),
                  body=dict(pos=[(0, 0, -0.8, 0), (L * 0.5, 0, 0.9, 0), (L, 0, -0.8, 0)]),
                  tail=dict(rot=[(0, -6, 0, 0), (L * 0.5, 6, 0, 0), (L, -6, 0, 0)]),
                  banner_r=dict(rot=[(0, 30, 0, 0), (L * 0.5, 38, 0, 0), (L, 30, 0, 0)]),
                  banner_l=dict(rot=[(0, 30, 0, 0), (L * 0.5, 38, 0, 0), (L, 30, 0, 0)]),
                  **held(tuck, L)))
    G = 2.4
    A.append(anim('glide', G, loop=True,
                  wing_r=dict(rot=[(0, 0, 0, 8), (G * 0.5, 0, 0, 12), (G, 0, 0, 8)]),
                  wing_l=dict(rot=[(0, 0, 0, -8), (G * 0.5, 0, 0, -12), (G, 0, 0, -8)]),
                  wing_r2=dict(rot=[(0, 0, 0, -4), (G * 0.5, 0, 0, -8), (G, 0, 0, -4)]),
                  wing_l2=dict(rot=[(0, 0, 0, 4), (G * 0.5, 0, 0, 8), (G, 0, 0, 4)]),
                  tail=dict(rot=[(0, 0, 4, 0), (G * 0.5, 0, -4, 0), (G, 0, 4, 0)]),
                  banner_r=dict(rot=[(0, 40, 0, 0), (G * 0.5, 46, 0, 0), (G, 40, 0, 0)]),
                  banner_l=dict(rot=[(0, 40, 0, 0), (G * 0.5, 46, 0, 0), (G, 40, 0, 0)]),
                  **held(tuck, G)))
    return A


def wing_feathers(fc, rows, tip_cut):
    """Feather rows across a wing plate's top or bottom face. {@code rows}: (colour, depth) bands from the leading
    edge back, scaled to the face; on these faces fine y 0 is the trailing (back) edge, which is cut into tips."""
    w, h = fc.fw, fc.fh
    bands = []
    y = 0
    for color, depth in rows:
        bands.append((y, y + depth, color))
        y += depth
    total = y
    for fy in range(h):
        pos = int((h - 1 - fy) * total / max(1, h))
        color = next((c for y0, y1, c in bands if y0 <= pos < y1), bands[-1][2])
        for fx in range(w):
            c = shade(color, 1.0 if (fx // 2) % 2 else 0.86)
            if fx % 4 == 0:
                c = shade(color, 0.72)
            fc.fpx(fx, fy, c)
    for fx in range(w):
        for k in range(min(tip_cut, fx % 4)):
            fc.fclear(fx, k)


def feather(fc, color):
    """One long feather seen from above or below: a shaft down the middle, a pointed tip at the trailing end
    (fine y 0 on these faces), a paler base."""
    w, h = fc.fw, fc.fh
    for y in range(h):
        for x in range(w):
            c = shade(color, 1.0 - 0.18 * (y / max(1, h - 1)))
            if x == w // 2:
                c = shade(color, 0.7)
            fc.fpx(x, y, c)
    for y in range(3):
        cut = 2 - y
        for x in range(w):
            if x < cut or x >= w - cut:
                fc.fclear(x, y)


def griffin_paint(p):
    k = p.k
    white = WHITE
    pale = rgb('#c9d2ea')
    blue = rgb('#3150b8')
    navy = rgb('#22306e')
    # wings: coverts in white and blue rows; secondaries and primaries alternately blue and white with pointed tips
    for side in ('r', 'l'):
        for name in ('top', 'bottom'):
            wing_feathers(p.face('wing_' + side, name, index=0), [(pale, 4), (blue, 5), (white, 5), (navy, 4)], 0)
            wing_feathers(p.face('wing_%s2' % side, name, index=0), [(pale, 4), (blue, 5), (white, 5)], 0)
    for i in range(5):
        for name in ('top', 'bottom'):
            feather(p.face('wing_r', name, tag='sf%d' % i), blue if i % 2 == 0 else white)
    for i in range(6):
        for name in ('top', 'bottom'):
            feather(p.face('prim_r%d' % (i + 1), name, index=0), blue if i % 2 == 0 else white)
    # the leading bones: gold plates with darker seams; the wrist plate and the gems
    for part, tag in (('wing_r', 'edge1'), ('wing_r2', 'edge2')):
        for name in ('top', 'front', 'bottom', 'back', 'right', 'left'):
            fc = p.face(part, name, tag=tag)
            for x in range(0, fc.fw, 6):
                for y in range(fc.fh):
                    fc.fpx(x, y, GOLD_DARK)
            border(fc, GOLD_LIGHT, 1, 't')
    for name in ('top', 'front', 'right', 'left', 'back', 'bottom'):
        border(p.face('wing_r2', name, tag='wrist'), GOLD_DARK, 1, 'tblr')
        gem_face(p.face('wing_r', name, tag='wgem1'), p.face('wing_r', name, tag='wgem1', glow=True))
        gem_face(p.face('wing_r2', name, tag='wgem2'), p.face('wing_r2', name, tag='wgem2', glow=True))
    fc = p.face('wing_r2', 'top', tag='wrist')
    star(fc, fc.fw // 2, fc.fh // 2, 2, GOLD_LIGHT)
    # crown gem, chest gem; the crown's engraved band
    for name in ('front', 'right', 'left', 'top'):
        gem_face(p.face('head', name, tag='crown_gem'), p.face('head', name, tag='crown_gem', glow=True))
        gem_face(p.face('body', name, index=3), p.face('body', name, index=3, glow=True))
    for name in ('front', 'right', 'left', 'back'):
        fc = p.face('head', name, tag='crown')
        for x in range(0, fc.fw, 3):
            fc.fpx(x, fc.fh - 1, GOLD_DARK)
        border(fc, GOLD_LIGHT, 1, 't')
    # the eagle's face: a stern brow with its shadow, glowing eyes just under it, a dark stripe back from the eye
    for name in ('bottom',):
        fc = p.face('head', name, tag='brow')
        for y in range(fc.fh):
            for x in range(fc.fw):
                fc.fpx(x, y, shade(pale, 0.7))
    for name in ('right', 'left'):
        fc = p.face('head', name, tag='cheek')
        gc = p.face('head', name, tag='cheek', glow=True)
        front = name == 'right'
        ex = fc.fw - 5 if front else 2
        for dx in range(3):
            for dy in range(2):
                fc.fpx(ex + dx, dy, EYE_GLOW)
                gc.fpx(ex + dx, dy, EYE_GLOW)
        fc.fpx(ex + (2 if front else 0), 0, (240, 252, 255))
        stripe = range(0, ex) if front else range(ex + 3, fc.fw)
        for x in stripe:
            fc.fpx(x, 1, shade(navy, 1.1))
        brow = p.face('head', name, tag='brow')
        for x in range(brow.fw):
            brow.fpx(x, brow.fh - 1, shade(pale, 0.75))
    # the beak: gold, a darker hooked tip, the cere at its base
    for tag in ('beak', 'beak2'):
        for name in ('right', 'left', 'top', 'front'):
            fc = p.face('head', name, tag=tag)
            border(fc, GOLD_LIGHT, 1, 't')
    for name in ('right', 'left', 'front', 'bottom', 'back'):
        fc = p.face('head', name, tag='hook')
        for y in range(fc.fh):
            for x in range(fc.fw):
                fc.fpx(x, y, mix(GOLD, GOLD_DARK, y / max(1, fc.fh - 1)))
    for name in ('right', 'left'):
        fc = p.face('head', name, tag='beak')
        fc.fpx(fc.fw - 2 if name == 'left' else 1, 1, GOLD_DARK)
        fc.fpx(fc.fw - 3 if name == 'left' else 2, 1, GOLD_DARK)
    # banners: blue with gold trim, the star, pointed tabs
    for name in ('right', 'left'):
        fc = p.face('banner_r', name, tag='banner')
        border(fc, GOLD, 1, 'tblr')
        star(fc, fc.fw // 2, fc.fh // 2 - k, 3, GOLD_LIGHT, edge=GOLD)
        pointed_bottom(fc, 3 * k, step=fc.fw)
    for name in ('front', 'back'):
        pointed_bottom(p.face('banner_r', name, tag='banner'), 3 * k, step=max(2, p.face('banner_r', name, tag='banner').fw))
    # the lantern
    lantern_faces(p, 'lantern', 1)
    # chest ruff a little paler at the bottom; harness straps on the ruff
    for name in ('front',):
        fc = p.face('body', name, index=1)
        for x in range(fc.fw):
            fc.fpx(x, 2 * k, GOLD)
    # tail tips blue
    for name in ('top', 'bottom'):
        fc = p.face('tail2', name, index=0)
        for fx in range(fc.fw):
            for fy in range(fc.fh // 2):
                fc.fpx(fx, fy, shade(blue, 1.0 if (fx // 2) % 2 else 0.85))
            if fx % 3 == 0:
                fc.fclear(fx, 0)


def griffin_variant():
    pal = {
        'body': {'base': '#26346f', 'style': 'feather', 'light': '#3a4c96'},
        'ruff': {'base': '#eef1f8', 'style': 'feather', 'light': '#ffffff', 'dark': '#b8c2dc'},
        'neck': {'base': '#eef1f8', 'style': 'feather', 'light': '#ffffff', 'dark': '#b8c2dc'},
        'head': {'base': '#f2f4fa', 'style': 'feather', 'light': '#ffffff', 'dark': '#c0c8de'},
        'tuft': {'base': '#e8ecf6', 'style': 'feather', 'light': '#ffffff'},
        'covert': {'base': '#eef1f8', 'style': 'feather'},
        'hand': {'base': '#eef1f8', 'style': 'feather'},
        'pw': {'base': '#eef1f8', 'style': 'feather'},
        'pb': {'base': '#3150b8', 'style': 'feather'},
        'sw': {'base': '#eef1f8', 'style': 'feather'},
        'sb': {'base': '#3150b8', 'style': 'feather'},
        'tail': {'base': '#eef1f8', 'style': 'feather'},
        'tailtip': {'base': '#eef1f8', 'style': 'feather'},
        'thigh': {'base': '#e8ecf6', 'style': 'feather', 'light': '#ffffff'},
        'leg': {'base': '#dfe4f0', 'style': 'feather'},
        'shin': {'base': '#d8a640', 'style': 'scale'},
        'foot': {'base': '#e2b046', 'style': 'gold'},
        'claw': '#18161c',
        'beak': {'base': '#f0c050', 'style': 'gold'},
        'beak_low': {'base': '#c0902c', 'style': 'gold'},
        'beak_tip': {'base': '#d09a30', 'style': 'gold'},
        'brow': {'base': '#f4f6fb', 'style': 'feather', 'light': '#ffffff', 'dark': '#c0c8de'},
        'cheek': {'base': '#eef1f8', 'style': 'feather', 'light': '#ffffff', 'dark': '#b8c2dc'},
        'wrist': {'base': '#eab84a', 'style': 'gold'},
        'crown': {'base': '#eab84a', 'style': 'gold'},
        'edge': {'base': '#e6b444', 'style': 'gold'},
        'plate': {'base': '#e6b444', 'style': 'gold'},
        'strap': {'base': '#d8a43c', 'style': 'gold'},
        'gem': {'base': '#4a9cff', 'style': 'gem'},
        'saddle': {'base': '#7a4826', 'style': 'leather'},
        'saddle_rim': {'base': '#5a3218', 'style': 'leather'},
        'banner': {'base': '#2a3f9c', 'style': 'cloth'},
        'chain': {'base': '#8a7a5a', 'style': 'chain'},
        'lantern': {'base': '#c8962e', 'style': 'gold'},
        'lantern_cap': {'base': '#c8962e', 'style': 'gold'},
    }
    return Variant('griffin', palette=pal, paint=griffin_paint, glow=True,
                   extra=dict(scale=1.0, hitbox=(1.5, 1.8), seat='saddle'))


def griffin_rig():
    return Rig('mount_griffin', 'GriffinMount', griffin_geometry(), griffin_animations(), [griffin_variant()],
               GRIFFIN_SLOTS, texel=2)


# ================================================================================================ the bike

WHEEL_R = 7.0


TYRE = 5          # tyre width (model units)
FENDER_R = 8.5    # radius of the middle of the fenders around the wheel centres


def wheel(name, cy, cz):
    """A wide wheel in the y-z plane around (cy, cz): four boxes make the round tyre (their side faces are painted as
    one disc, transparent outside the radius; the tread faces are the chunky tyre), a gold hub sticks out both
    sides."""
    x = -TYRE / 2.0
    return P(name, (0, cy, cz),
             C(x, cy - 7, cz - 3, TYRE, 14, 6, 'plain:tyre'),
             C(x, cy - 3, cz - 7, TYRE, 6, 14, 'plain:tyre', inflate=0.01),
             C(x, cy - 6, cz - 5, TYRE, 12, 10, 'plain:tyre', inflate=0.02),
             C(x, cy - 5, cz - 6, TYRE, 10, 12, 'plain:tyre', inflate=0.03),
             C(x - 0.5, cy - 1.5, cz - 1.5, TYRE + 1, 3, 3, 'gold:hub'))


def fender(prefix, cy, cz, angles, tag=None, share=None):
    """A curved fender over a wheel: segments around the wheel centre, each a stone plate turned to its angle
    (0 = on top, positive = towards the front)."""
    parts = []
    for i, angle in enumerate(angles):
        first = i == 0
        parts.append(P('%s_%d' % (prefix, i + 1), (0, cy, cz),
                       C(-3.5, cy - FENDER_R - 1.5, cz - 3.5, 7, 3, 7, 'stone:fender',
                         tag=(tag if first else None), share=(None if first else tag) if share is None else share),
                       rot=(angle, 0, 0)))
    return parts


def bike_geometry():
    rake = -24
    fcy, fcz = 18.5, -11      # front wheel centre in the fork's own (unturned) frame
    fork = P('fork', (0, 1, -11),
             C(-5, -3, -12, 2, 21, 2, 'gold:fork', tag='tube'),
             C(3, -3, -12, 2, 21, 2, 'gold:fork', share='tube', mirror=True),
             C(-5.5, 9, -12.5, 3, 9, 3, 'gold:slider', tag='slider'),
             C(2.5, 9, -12.5, 3, 9, 3, 'gold:slider', share='slider', mirror=True),
             C(-5.5, -2, -12.5, 11, 2, 3, 'gold:clamp', tag='clamp'),
             C(-3.5, -1, -14.5, 7, 5, 5, 'stone:head', tag='head'),
             P('headlight', (0, 1.5, -15), C(-3.5, -2, -20, 7, 7, 5, 'gold:lamp'),
               C(-4, -3, -20.5, 8, 1, 6, 'gold:lantern_cap'), C(-1, -4, -18.5, 2, 1, 2, 'gold:lantern_cap')),
             P('bars', (0, -3, -11),
               C(-3, -6, -11.5, 1, 3, 1, 'gold:bar'), C(2, -6, -11.5, 1, 3, 1, 'gold:bar'),
               C(-8, -7, -11.5, 16, 1, 1, 'gold:bar'),
               C(-10.5, -7.5, -11, 3, 2, 2, 'leather:grip', tag='grip'),
               C(7.5, -7.5, -11, 3, 2, 2, 'leather:grip', share='grip', mirror=True),
               rot=(-rake, 0, 0)),
             *fender('ffender', fcy, fcz, (-16, 14, 44, 74, 104), tag='fseg'),
             wheel('front_wheel', fcy, fcz),
             rot=(rake, 0, 0))
    flag = P('flag', (0, -11.5, 21.5), C(-0.5, -11.5, 21.5, 1, 6, 9, 'cloth:flag'))

    def lantern(side):
        s = -1 if side == 'r' else 1
        x0 = -7 if s < 0 else 4
        return P('lantern_' + side, (5 * s, 2, -8),
                 C(-5.5 if s < 0 else 4.5, 2, -8.5, 1, 2, 1, 'chain:chain', tag=('chain' if s < 0 else None),
                   share=(None if s < 0 else 'chain'), mirror=s > 0),
                 C(x0, 4, -9.5, 3, 4, 3, 'gold:lantern', tag=('lamp' if s < 0 else None),
                   share=(None if s < 0 else 'lamp'), mirror=s > 0),
                 C(x0 + 0.5, 8, -9, 2, 1, 2, 'gold:lantern_cap', tag=('lcap' if s < 0 else None),
                   share=(None if s < 0 else 'lcap'), mirror=s > 0))
    frame = P('frame', (0, 12, 0),
              C(-4.5, 7, -9, 9, 10, 16, 'mech:engine', tag='engine'),
              C(-6.5, 5, -6, 2, 6, 5, 'mech:cyl', tag='cyl'),
              C(4.5, 5, -6, 2, 6, 5, 'mech:cyl', share='cyl', mirror=True),
              C(-5.5, 11, 0, 1, 4, 5, 'crystal:core', tag='crys'),
              C(4.5, 11, 0, 1, 4, 5, 'crystal:core', share='crys', mirror=True),
              C(-4.5, 0, -11, 9, 7, 12, 'stone:tank', tag='tank'),
              C(-5, 1.5, -10, 1, 4, 10, 'gold:plate', tag='tplate'),
              C(4, 1.5, -10, 1, 4, 10, 'gold:plate', share='tplate', mirror=True),
              C(-1.5, -0.5, -10.5, 3, 1, 11, 'gold:band', tag='band'),
              C(-1, -1, -9, 2, 1, 7, 'gem:gem_strip', tag='gemstrip'),
              C(-4, 4, 1, 8, 2, 12, 'leather:seat', tag='seat'),
              C(-4, 2, 11, 8, 2, 3, 'leather:seat', tag='seatback'),
              C(-4.5, 6, 2, 9, 5, 7, 'stone:cover', tag='cover'),
              C(-8, 5, 9, 3, 6, 7, 'leather:bag', tag='bag'),
              C(5, 5, 9, 3, 6, 7, 'leather:bag', share='bag', mirror=True),
              C(-7.5, 14, -4, 2, 2, 20, 'gold:pipe', tag='pipe'),
              C(5.5, 14, -4, 2, 2, 20, 'gold:pipe', share='pipe', mirror=True),
              P('banner', (-8.25, 6, 12.5), C(-8.5, 6, 10, 1, 10, 6, 'cloth:banner')),
              lantern('r'), lantern('l'),
              P('flag_pole', (0, 7.5, 21), C(-0.5, -12, 20.5, 1, 19, 1, 'gold:pole'),
                C(-1, -13, 20, 2, 1, 2, 'gold:pole'), flag),
              *fender('rfender', 17, 15, (40, 10, -20, -50, -80, -108), tag='rseg'),
              fork,
              wheel('rear_wheel', 17, 15))
    return [P('base', (0, 24, 0), frame)]


def bike_animations():
    A = []
    A.append(anim('idle', 1.2, loop=True,
                  frame=dict(pos=[(0, 0, 0, 0), (0.1, 0, 0.12, 0), (0.2, 0, 0, 0), (0.3, 0, 0.1, 0), (0.4, 0, 0, 0),
                                  (0.5, 0, 0.12, 0), (0.6, 0, 0, 0), (0.7, 0, 0.1, 0), (0.8, 0, 0, 0),
                                  (0.9, 0, 0.12, 0), (1.0, 0, 0, 0), (1.1, 0, 0.1, 0), (1.2, 0, 0, 0)]),
                  flag=dict(rot=[(0, 0, 8, 0), (0.6, 0, -8, 0), (1.2, 0, 8, 0)]),
                  banner=dict(rot=[(0, 0, 0, 2), (0.6, 0, 0, -2), (1.2, 0, 0, 2)]),
                  lantern_r=dict(rot=[(0, 2, 0, 2), (0.6, -2, 0, -2), (1.2, 2, 0, 2)]),
                  lantern_l=dict(rot=[(0, -2, 0, -2), (0.6, 2, 0, 2), (1.2, -2, 0, -2)])))
    R = 0.6
    A.append(anim('ride', R, loop=True,
                  frame=dict(pos=[(0, 0, 0, 0), (R * 0.25, 0, 0.2, 0), (R * 0.5, 0, 0, 0), (R * 0.75, 0, 0.15, 0),
                                  (R, 0, 0, 0)]),
                  flag=dict(rot=[(0, 0, 18, 6), (R * 0.25, 0, -6, -4), (R * 0.5, 0, 20, 4), (R * 0.75, 0, -4, -6),
                                 (R, 0, 18, 6)]),
                  banner=dict(rot=[(0, 34, 0, 0), (R * 0.5, 42, 0, 0), (R, 34, 0, 0)]),
                  lantern_r=dict(rot=[(0, 22, 0, 0), (R * 0.5, 28, 0, 0), (R, 22, 0, 0)]),
                  lantern_l=dict(rot=[(0, 22, 0, 0), (R * 0.5, 28, 0, 0), (R, 22, 0, 0)])))
    return A


def disc(r, angle):
    """Colour, alpha and glow of a wheel side at radius r (model units) and angle (radians)."""
    if r > WHEEL_R + 0.05:
        return None, 0, None
    if r > 5.2:
        tread = (int((angle + math.pi) / (2 * math.pi) * 24) % 2) == 0
        edge = r > WHEEL_R - 0.45
        c = (30, 28, 36) if tread else (46, 42, 52)
        return (shade(c, 0.8) if edge else c), 255, None
    if r > 4.4:
        return GOLD if r > 4.8 else GOLD_DARK, 255, None
    # the star hub: four gold points over the glowing blue
    a = (angle + math.pi / 4) % (math.pi / 2) - math.pi / 4
    arm = 4.0 * max(0.0, 1.0 - abs(a) / 0.42)
    if r < 1.3 or r < arm * (1.0 - abs(a) / 0.5):
        return (GOLD_LIGHT if r < 0.7 else GOLD), 255, None
    spoke = (int((angle + math.pi) / (2 * math.pi) * 16) % 2) == 0
    base = (80, 150, 255) if r > 3.4 else (40, 90, 220)
    c = shade(base, 1.0 if spoke else 0.78)
    return c, 255, tuple(int(v * (1.0 if r > 3.4 else 0.8)) for v in (90, 170, 255))


def paint_wheel(p, part):
    k = p.k
    cube0 = p.rig.parts[part].cubes[0]
    cy, cz = cube0.y + 7.0, cube0.z + 3.0
    for c in p.rig.parts[part].cubes[:4]:
        for fname in ('right', 'left'):
            u, v, fw, fh = c.faces()[fname]
            for fy in range(fh * k):
                for fx in range(fw * k):
                    y = c.y + (fy + 0.5) / k
                    z = (c.z + c.d - (fx + 0.5) / k) if fname == 'right' else (c.z + (fx + 0.5) / k)
                    dy, dz = y - cy, z - cz
                    col, alpha, glow = disc(math.hypot(dy, dz), math.atan2(dy, dz))
                    py, px = v * k + fy, u * k + fx
                    if alpha == 0:
                        p.img[py, px, 3] = 0
                        continue
                    p.img[py, px, :3] = col
                    p.img[py, px, 3] = 255
                    if glow is not None:
                        p.glow[py, px, :3] = glow
                        p.glow[py, px, 3] = 255
        # the chunky tread on the running faces: raised blocks across the tyre
        for fname in ('front', 'back', 'top', 'bottom'):
            fc = p.face(part, fname, index=p.rig.parts[part].cubes.index(c))
            for y in range(fc.fh):
                for x in range(fc.fw):
                    block = (y // 2) % 2 == 0 and 1 <= x < fc.fw - 1
                    fc.fpx(x, y, (48, 44, 54) if block else (24, 22, 28))
    hub = p.rig.parts[part].cubes[4]
    for fname in ('right', 'left'):
        fc = p.face(part, fname, index=4)
        star(fc, fc.fw // 2, fc.fh // 2, fc.fw // 2, GOLD_LIGHT, edge=GOLD)


def round_lamp(fc, glow_fc, rim=GOLD, light=AMBER, glow=AMBER_GLOW):
    """A round lamp on a square face: transparent corners, a gold rim, warm light inside."""
    w, h = fc.fw, fc.fh
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    r = min(w, h) / 2.0
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy)
            if d > r:
                fc.fclear(x, y)
            elif d > r - 1.6:
                fc.fpx(x, y, rim if (x + y) % 3 else GOLD_LIGHT)
            else:
                t = d / max(1.0, r - 1.6)
                fc.fpx(x, y, mix((255, 250, 225), light, t))
                glow_fc.fpx(x, y, glow)


def bike_paint(p):
    k = p.k
    stone_line = shade(rgb('#d9d4ca'), 0.82)
    # tank: pale stone, gold rims, the star; the gold side plates engraved; the top band
    for name in ('right', 'left', 'top', 'front', 'back'):
        border(p.face('frame', name, tag='tank'), GOLD, 1, 'tblr')
    for name in ('right', 'left'):
        fc = p.face('frame', name, tag='tplate')
        for x in range(1, fc.fw - 1, 3):
            for y in range(1, fc.fh - 1):
                fc.fpx(x, y, GOLD_DARK)
        star(fc, fc.fw // 2, fc.fh // 2, 3, GOLD_LIGHT, edge=GOLD_DARK)
        cover = p.face('frame', name, tag='cover')
        border(cover, GOLD, 1, 'tblr')
        for y in range(3, cover.fh - 2, 3):
            for x in range(2, cover.fw - 2):
                cover.fpx(x, y, stone_line)
    for name in ('top', 'right', 'left', 'front', 'back'):
        gem_face(p.face('frame', name, tag='gemstrip'), p.face('frame', name, tag='gemstrip', glow=True))
    # the engine: dark plates with glowing blue vents; the cylinders with blue-lit fins; the crystal core
    for name in ('right', 'left', 'front', 'back'):
        fc = p.face('frame', name, tag='engine')
        gc = p.face('frame', name, tag='engine', glow=True)
        for y in range(4, fc.fh - 4, 4):
            for x in range(4, fc.fw - 4):
                if (x // 6) % 2 == 0:
                    fc.fpx(x, y, (70, 150, 255))
                    gc.fpx(x, y, (60, 140, 255))
        border(fc, shade(rgb('#3a3a4c'), 0.6), 1, 'tblr')
    for name in ('right', 'left', 'front', 'back', 'top'):
        fc = p.face('frame', name, tag='cyl')
        gc = p.face('frame', name, tag='cyl', glow=True)
        for y in range(1, fc.fh - 1, 2):
            for x in range(fc.fw):
                fc.fpx(x, y, (60, 120, 230))
                gc.fpx(x, y, (50, 110, 230))
    for name in ('right', 'left', 'front', 'back', 'top', 'bottom'):
        fc = p.face('frame', name, tag='crys')
        gc = p.face('frame', name, tag='crys', glow=True)
        for y in range(fc.fh):
            for x in range(fc.fw):
                gc.fpx(x, y, tuple(int(min(255, v * 0.95)) for v in fc.fget(x, y)))
    # fenders: pale stone with gold edges
    for prefix, count in (('ffender', 5), ('rfender', 6)):
        for name in ('top', 'right', 'left', 'bottom'):
            fc = p.face(prefix + '_1', name, index=0)
            border(fc, GOLD, 1, 'lr' if name in ('top', 'bottom') else 'tb')
    for name in ('top', 'front', 'right', 'left'):
        border(p.face('fork', name, tag='head'), GOLD, 1, 'tblr')
    # the headlight: a round lamp; the side lanterns
    lantern_faces(p, 'headlight', 0)
    round_lamp(p.face('headlight', 'front', index=0), p.face('headlight', 'front', index=0, glow=True))
    lantern_faces(p, 'lantern_r', 1)
    # saddlebags: straps and buckles; the seat's stitching
    for name in ('right', 'left', 'front', 'back'):
        fc = p.face('frame', name, tag='bag')
        for x in range(fc.fw):
            fc.fpx(x, 2, shade(rgb('#5a3418'), 0.8))
        fc.fpx(fc.fw // 2, 3, GOLD)
        fc.fpx(fc.fw // 2, 4, GOLD)
    for tag in ('seat', 'seatback'):
        seat = p.face('frame', 'top', tag=tag)
        for y in range(1, seat.fh - 1, 2):
            seat.fpx(1, y, lighten(rgb('#6e3c22'), 0.3))
            seat.fpx(seat.fw - 2, y, lighten(rgb('#6e3c22'), 0.3))
    # the banner on the right bag and the flag: blue, gold trim, the star, cut ends
    for name in ('right', 'left'):
        fc = p.face('banner', name, index=0)
        border(fc, GOLD, 1, 'tblr')
        star(fc, fc.fw // 2, fc.fh // 2 - k, 3, GOLD_LIGHT, edge=GOLD)
        pointed_bottom(fc, 3 * k, step=fc.fw)
        flag = p.face('flag', name, index=0)
        border(flag, GOLD, 1, 'tb')
        star(flag, flag.fw // 2, flag.fh // 2, 3, GOLD_LIGHT, edge=GOLD)
        w, h = flag.fw, flag.fh
        for y in range(h):
            depth = int(round(5 * (1.0 - abs(y - (h - 1) / 2.0) / ((h - 1) / 2.0))))
            for d in range(depth):
                x = (w - 1 - d) if name == 'left' else d
                flag.fclear(x, y)
    for name in ('front', 'back'):
        pointed_bottom(p.face('banner', name, index=0), 3 * k, step=max(2, p.face('banner', name, index=0).fw))
    paint_wheel(p, 'front_wheel')
    paint_wheel(p, 'rear_wheel')


def bike_variant():
    pal = {
        'tank': {'base': '#d9d4ca', 'style': 'stone'},
        'head': {'base': '#d9d4ca', 'style': 'stone'},
        'fender': {'base': '#dcd7cc', 'style': 'stone'},
        'cover': {'base': '#cfc9bd', 'style': 'stone'},
        'engine': {'base': '#33323f', 'style': 'mech'},
        'cyl': {'base': '#2c2b38', 'style': 'mech'},
        'core': {'base': '#4a8cff', 'style': 'crystal'},
        'seat': {'base': '#6e3c22', 'style': 'leather'},
        'bag': {'base': '#6a3e22', 'style': 'leather'},
        'grip': {'base': '#2a2228', 'style': 'leather'},
        'fork': {'base': '#e2b046', 'style': 'gold'},
        'slider': {'base': '#d8a43c', 'style': 'gold'},
        'clamp': {'base': '#e2b046', 'style': 'gold'},
        'bar': {'base': '#e2b046', 'style': 'gold'},
        'plate': {'base': '#e2b046', 'style': 'gold'},
        'band': {'base': '#e2b046', 'style': 'gold'},
        'pipe': {'base': '#c8962e', 'style': 'gold'},
        'pole': {'base': '#c8962e', 'style': 'gold'},
        'hub': {'base': '#e8b84a', 'style': 'gold'},
        'lamp': {'base': '#c8962e', 'style': 'gold'},
        'lantern': {'base': '#c8962e', 'style': 'gold'},
        'lantern_cap': {'base': '#b8862a', 'style': 'gold'},
        'gem_strip': {'base': '#4a9cff', 'style': 'gem'},
        'tyre': '#26242c',
        'banner': {'base': '#2a3f9c', 'style': 'cloth'},
        'flag': {'base': '#2a3f9c', 'style': 'cloth'},
        'chain': {'base': '#8a7a5a', 'style': 'chain'},
    }
    return Variant('bike', palette=pal, paint=bike_paint, glow=True,
                   extra=dict(scale=1.0, hitbox=(1.1, 1.4), seat=('frame', 'seat'), wheel_radius=WHEEL_R))


def bike_rig():
    return Rig('mount_bike', 'BikeMount', bike_geometry(), bike_animations(), [bike_variant()], BIKE_SLOTS, texel=2)


def rigs():
    return [horse_rig(), griffin_rig(), bike_rig()]
