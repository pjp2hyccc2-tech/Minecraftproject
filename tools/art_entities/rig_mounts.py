"""MOUNT rigs: the Moonmane Charger (warhorse), the Azurewing Griffin and the Starwheel Arcbike.

One rig per mount (rig names mount_horse, mount_griffin, mount_bike). The horse (rig_horse.py) and the griffin
(rig_griffin.py) were rebuilt after the owner's pictures on 2026-11-11 and live in their own modules; this one keeps the
bike, the shared paint helpers and the coat tables (MountLooks: ten horse coats, eight griffin coats; the first is the
classic look, the pictures' colours). Own designs after
the owner's
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


# coat id -> coat (base, dark, light), muzzle (base, dark, light), mane, tail, fetlock, caparison, blanket, extra
HORSE_COATS = [
    ('night', dict(coat=('#25242e', '#14141a', '#3a3a4a'), muzzle=('#302f3a', '#1c1b24', '#46455a'), mane='#eef1f7',
                   tail='#eaeef6', fetlock='#e6eaf2', caparison='#2a3f9c', blanket='#1e2c74')),
    ('chestnut', dict(coat=('#8a4a24', '#5e2f14', '#a8643a'), muzzle=('#6e3a1c', '#4a240e', '#86502c'),
                      mane='#e0ac66', tail='#d8a25a', fetlock='#ecc892', caparison='#7a1f2a', blanket='#5a1420')),
    ('bay', dict(coat=('#6b3f22', '#45260f', '#87542f'), muzzle=('#3a2414', '#24150a', '#4e3220'), mane='#221a14',
                 tail='#221a14', fetlock='#2a2018', caparison='#2a6a3a', blanket='#1d4a28')),
    ('grey', dict(coat=('#b9bcc4', '#8f939c', '#d6d9df'), muzzle=('#8a8d96', '#64676f', '#a6a9b1'), mane='#f4f5f8',
                  tail='#eeeff3', fetlock='#f6f7fa', caparison='#4a3a8c', blanket='#33286a')),
    ('palomino', dict(coat=('#d9b062', '#b38a42', '#ecc985'), muzzle=('#c39a52', '#9c7638', '#d8b46e'),
                      mane='#fbf4e2', tail='#f6eedb', fetlock='#f8f1e0', caparison='#2a6fa0', blanket='#1d4f74')),
    ('dapple', dict(coat=('#8d939e', '#646a74', '#b3b8c1'), muzzle=('#565b64', '#3c4048', '#6e737c'), mane='#3a3d44',
                    tail='#3a3d44', fetlock='#2e3137', caparison='#8c2a2a', blanket='#641c1c', pattern='dapple',
                    pattern_color='#d4d8df')),
    ('pinto', dict(coat=('#4a3426', '#2e1f15', '#634635'), muzzle=('#3a291d', '#251a11', '#4e3828'), mane='#efebe3',
                   tail='#2c2119', fetlock='#efebe3', caparison='#c08a2a', blanket='#8a6018', pattern='pinto',
                   pattern_color='#efebe3')),
    ('ember', dict(coat=('#7a1c18', '#4e0f0c', '#9c2a22'), muzzle=('#561310', '#380a08', '#6e1c16'), mane='#1a1010',
                   tail='#1a1010', fetlock='#2a1414', caparison='#1c1c22', blanket='#121216')),
    ('frost', dict(coat=('#cfe6f4', '#a8c8de', '#eaf6fd'), muzzle=('#b4d2e6', '#90b4cc', '#cce2f2'), mane='#7fd0ff',
                   tail='#8ad6ff', fetlock='#dff3ff', caparison='#3a6ea8', blanket='#2a5486', glow_hair=True)),
    ('star', dict(coat=('#1a1d3a', '#0f1126', '#2a2f58'), muzzle=('#23264a', '#151834', '#30345e'), mane='#c2b4ff',
                  tail='#bba9ff', fetlock='#d8ccff', caparison='#4b2a8c', blanket='#33196a', pattern='stars',
                  pattern_color='#f4f0ff', glow_hair=True)),
]


def material_faces(p, materials):
    """Every painted face (fine-pixel canvases) of the cubes made of one of {@code materials}, with its glow face."""
    from erpaint import FaceCanvas
    out = []
    for c in p.rig.cubes:
        if c.share is not None or c.mat not in materials or not p.visible(c.part):
            continue
        for fname, rect in c.faces().items():
            out.append((FaceCanvas(p.img, rect, fname, p.k), FaceCanvas(p.glow, rect, fname, p.k), c, fname))
    return out


def coat_pattern(p, kind, color):
    """Dapples (light rings on a grid), pinto patches (big white islands) or star specks (glowing) on the coat."""
    from erpaint import seed_of
    color = rgb(color)
    for fc, gc, c, fname in material_faces(p, ('fur:coat', 'fur:muzzle')):
        rng = np.random.default_rng(seed_of('pattern', p.variant.id, c.part.name, c.x, c.y, c.z, fname))
        w, h = fc.fw, fc.fh
        if kind == 'dapple':
            cell = 6
            for cy in range(-cell, h + cell, cell):
                for cx in range(-cell, w + cell, cell):
                    ox = cx + rng.integers(0, cell)
                    oy = cy + rng.integers(0, cell)
                    r = 1.2 + rng.random() * 1.3
                    for y in range(int(oy - r - 1), int(oy + r + 2)):
                        for x in range(int(ox - r - 1), int(ox + r + 2)):
                            if 0 <= x < w and 0 <= y < h:
                                d = math.hypot(x - ox, y - oy)
                                if d <= r:
                                    fc.fpx(x, y, mix(fc.fget(x, y), color, 0.55 if d > r - 0.8 else 0.3))
        elif kind == 'pinto':
            a, b, ph1, ph2, ph3 = rng.random() * 0.25 + 0.18, rng.random() * 0.25 + 0.18, *(rng.random(3) * 6.28)
            for y in range(h):
                for x in range(w):
                    v = math.sin(x * a + ph1) + math.sin(y * b + ph2) + 0.8 * math.sin((x + y) * 0.13 + ph3)
                    if v > 0.75:
                        fc.fpx(x, y, shade(color, 0.92 + 0.08 * ((x * 7 + y * 3) % 3)))
        elif kind == 'stars':
            for y in range(h):
                for x in range(w):
                    if rng.random() < 0.018:
                        bright = rng.random() < 0.3
                        fc.fpx(x, y, color if bright else mix(color, fc.fget(x, y), 0.4))
                        gc.fpx(x, y, (200, 190, 255) if bright else (110, 100, 170))


def glow_hair(p, color):
    """The mane, tail and fetlocks shine (the emissive layer gets the hair's own colours, a little dimmed)."""
    color = rgb(color)
    for fc, gc, c, fname in material_faces(p, ('hair:mane', 'hair:tail', 'hair:fetlock')):
        for y in range(fc.fh):
            for x in range(fc.fw):
                if fc.img[fc.v * fc.k + y, fc.u * fc.k + x, 3] > 0:
                    base = fc.fget(x, y)
                    gc.fpx(x, y, tuple(int(min(255, v * 0.8)) for v in mix(base, color, 0.5)))


# ================================================================================================ the griffin

# coat id -> body (base, light), head (base, light, dark), white, pale, accent (the wing bars), navy, banner
GRIFFIN_COATS = [
    ('azure', dict(body=('#252a6e', '#363f94'), head=('#f1edf8', '#ffffff', '#c6bfe0'), white='#ebe6f6',
                   pale='#c3bbe0', accent='#3a48c0', navy='#1e2466', banner='#2a3a9c')),
    ('golden', dict(body=('#6a4418', '#8a5c26'), head=('#f6f1e4', '#ffffff', '#d4c6a6'), white='#c9984a',
                    pale='#a87a34', accent='#5e3612', navy='#3e220a', banner='#8c2a2a')),
    ('snow', dict(body=('#dfe4ec', '#f6f8fb'), head=('#ffffff', '#ffffff', '#d2d8e2'), white='#f8fafc',
                  pale='#d6dce6', accent='#8e9aae', navy='#5e687a', banner='#3a6ea8')),
    ('raven', dict(body=('#1c1a22', '#2c2934'), head=('#4a4752', '#5c5966', '#34313c'), white='#3a3742',
                   pale='#2c2a33', accent='#6a44a8', navy='#2a1a46', banner='#3a1a5a')),
    ('crimson', dict(body=('#6a1414', '#8a2420'), head=('#f4e6dc', '#fff4ec', '#d4bcae'), white='#b8402e',
                     pale='#923222', accent='#ffb04a', navy='#5a1010', banner='#1c1c22')),
    ('verdant', dict(body=('#1f5a3a', '#2e7a50'), head=('#eef6ee', '#ffffff', '#c4d8c4'), white='#4a9a5a',
                     pale='#367a44', accent='#e0c040', navy='#14402a', banner='#6a4a1a')),
    ('dawn', dict(body=('#c86a6a', '#e08a7a'), head=('#fff4e6', '#ffffff', '#e8cdb6'), white='#f2a6a0',
                  pale='#d88882', accent='#ffd070', navy='#a8485a', banner='#f0c860')),
    ('aurora', dict(body=('#1a2a4a', '#2a4070'), head=('#e8fff8', '#ffffff', '#b4dcd2'), white='#2a4a7a',
                    pale='#20385e', accent='#3affc0', navy='#2a1a6a', banner='#2a1a6a', glow_wings=True)),
]


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
    """The three first mounts, then the five horse and the five griffin breeds (rig_horse_breeds.py,
    rig_griffin_breeds.py)."""
    import rig_griffin
    import rig_griffin_breeds
    import rig_horse
    import rig_horse_breeds
    return [rig_horse.rig(), rig_griffin.rig(), bike_rig()] + rig_horse_breeds.rigs() + rig_griffin_breeds.rigs()
