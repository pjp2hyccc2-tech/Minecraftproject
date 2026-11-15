"""FIREDRAGON boss rig: b6 灼炎の竜王 (the dragon king of the searing flame), from the concept sheet.

A volcanic dragon on four legs: scales of dark basalt split by glowing lava seams; cream belly and throat plates in
ridges; a heavy horned head (two great cream horns sweeping back and curling up, a crest of spikes, cheek spikes,
burning eyes, rows of teeth, fire in the open mouth); a thick two-part neck; spikes down the spine and the tail; the
tail ends in a tongue of flame; vast wings of glowing orange membrane between dark bones and finger struts, a claw at
the joint; thick legs with cream claws. Later forms burn: the seams widen, the horns grow, the scales turn to magma.

The skeleton keeps the bone names and pivots of the shared DRAGON rig, so its quadruped clips (lateral-sequence walk
by four-leg IK, rearing slam with tucked front feet, tail sweep, fire breath, wing gust, death on the side) drive this
body too, with the timings of the old b6 look (BossRigData); the clips gain motion for the new parts (horn and spike
sway, the flame of the tail flickering, the wing struts)."""
import contextlib
import math

import eranim
import rig_dragon as D
from boss_kit import crackle, h, ragged, trim
from boss_motion import build, finish_rig
from eranim import Chain, Gait
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = D.SLOTS
SNOUT = (0.0, -2.0, -48.5)
TAIL_TIP = (0.0, -2.0, 66.0)
CLAW_FR = (-8.0, 24.0, -14.0)


def leg(name, x, front, mirror):
    kw = (lambda t: dict(share=t, mirror=True)) if mirror else (lambda t: dict(tag=t))
    claws = lambda z0, fx: [C(fx - 3.5, 22, z0 - 2, 2, 2, 3, 'bone:claw', **kw(name[:5] + 'c1')),
                            C(fx - 1, 22, z0 - 2.5, 2, 2, 3, 'bone:claw', **kw(name[:5] + 'c2')),
                            C(fx + 1.5, 22, z0 - 2, 2, 2, 3, 'bone:claw', **kw(name[:5] + 'c3'))]
    if front:
        z = -6
        upper = C(x - 3, 1, z - 3.5, 6, 11, 7, 'scale:leg', **kw('fu'))
        spike = C(x - 1, 9, z + 2.5, 2, 3, 3, 'bone:spur', **kw('fsp'))
        shin = C(x - 2.5, 12, z - 2.5, 5, 9, 5, 'scale:shin', **kw('fs'))
        foot = C(x - 3.5, 21, z - 5.5, 7, 3, 8, 'scale:foot', **kw('ff'))
        return P(name, (x, 3, z), upper, spike,
                 P(name + '_shin', (x, 12, z), shin,
                   P(name + '_foot', (x, 21, z), foot, *claws(z - 5.5, x))))
    z = 12
    upper = C(x - 3.5, -1, z - 4.5, 7, 13, 10, 'scale:thigh', **kw('hu'))
    shin = C(x - 2.5, 12, z - 1, 5, 9, 6, 'scale:shin2', **kw('hs'))
    foot = C(x - 3.5, 21, z - 4, 7, 3, 9, 'scale:foot', **kw('hf'))
    return P(name, (x, 2, z), upper,
             P(name + '_shin', (x, 12, z + 1), shin, P(name + '_foot', (x, 21, z + 1), foot, *claws(z - 3, x))))


def wing(side):
    s = -1 if side == 'r' else 1
    name = 'right' if s < 0 else 'left'
    kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    bx = -29 if s < 0 else 7
    tx = -49 if s < 0 else 29
    struts = [C(-24 if s < 0 else 23, -5.4, -4, 1, 1, 18, 'scale:strut', **kw('st1')),
              C(-17 if s < 0 else 16, -5.4, -4, 1, 1, 16, 'scale:strut2', **kw('st2')),
              C(-11 if s < 0 else 10, -5.4, -4, 1, 1, 12, 'scale:strut3', **kw('st3'))]
    tip_struts = [C(-44 if s < 0 else 43, -5.4, -4, 1, 1, 15, 'scale:strut4', **kw('ts1')),
                  C(-37 if s < 0 else 36, -5.4, -4, 1, 1, 17, 'scale:strut5', **kw('ts2'))]
    tip = P('%s_wing_tip' % name, (29 * s, -5, -4),
            C(tx, -6.5, -5, 20, 2, 2, 'scale:wingbone2', **kw('tipbone')),
            C(tx, -5, -4, 20, 0, 18, 'membrane:membrane2', **kw('tipmem')), *tip_struts,
            C(tx - (2 if s < 0 else -18), -7.5, -6, 2, 3, 2, 'bone:wingclaw2', **kw('wc2')),
            rot=(0, 0, -50 if s < 0 else 50))
    return P('%s_wing' % name, (7 * s, -5, -4),
             C(bx, -7, -5.5, 22, 3, 3, 'scale:wingbone', **kw('bone')),
             C(bx, -5, -4, 22, 0, 20, 'membrane:membrane', **kw('mem')), *struts,
             C(-31 if s < 0 else 29, -9, -5.5, 2, 3, 2, 'bone:wingclaw', **kw('wc')),
             tip, rot=(0, 30 if s < 0 else -30, 35 if s < 0 else -35))


def horn(s):
    name = 'r' if s < 0 else 'l'
    kw = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    x = 4.0 * s
    tip = P('horn_%s3' % name, (x, -8, -14), C(x - 0.5, -9, -14, 1, 1, 6, 'bone:horn3', **kw('hn3')), rot=(-42, 0, 0))
    mid = P('horn_%s2' % name, (x, -7.5, -21), C(x - 1, -8.5, -21, 2, 2, 7, 'bone:horn2', **kw('hn2')), tip,
            rot=(-30, 8 * s, 0))
    return P('horn_' + name, (x, -7, -30), C(x - 1.5, -9, -30, 3, 3, 9, 'bone:horn', **kw('hn1')), mid,
             rot=(22, -15 if s < 0 else 15, 0))


def geometry():
    head = P('head', (0, -2, -27),
             C(-5.5, -8, -38, 11, 9, 11, 'scale:head', tag='skull'),
             C(-4, -5, -48, 8, 6, 10, 'scale:snout', tag='snout'),
             C(-3, -6, -49, 6, 3, 2, 'scale:nose'),
             C(-5.8, -9, -37.5, 3, 2, 6, 'scale:brow', tag='brow'),
             C(2.8, -9, -37.5, 3, 2, 6, 'scale:brow', share='brow', mirror=True),
             C(-3.5, 1, -47.5, 7, 1, 9, 'bone:teeth'),
             C(-6.6, -3, -33, 2, 2, 4, 'bone:cheek', tag='ck'), C(4.6, -3, -33, 2, 2, 4, 'bone:cheek', share='ck', mirror=True),
             C(-1, -11, -36, 2, 3, 3, 'bone:crest', tag='cr'), C(-3, -10.5, -31, 2, 3, 3, 'bone:crest', share='cr'),
             C(1, -10.5, -31, 2, 3, 3, 'bone:crest', share='cr'),
             horn(-1), horn(1),
             P('jaw', (0, 1, -30), C(-3.5, 1, -46, 7, 3, 16, 'scale:jaw', tag='jaw'),
               C(-3, 0, -45, 6, 1, 13, 'bone:teeth2'), C(-1, 4, -44, 2, 2, 3, 'bone:chin')),
             rot=(45, 0, 0))
    neck = P('neck1', (0, -2, -10), C(-5, -7, -19, 10, 10, 9, 'scale:neck', tag='neck1'),
             C(-4, 2, -19, 8, 1, 9, 'bone:throat', tag='th1'),
             C(-1, -10, -17, 2, 3, 3, 'bone:spine', tag='nsp'), C(-1, -10, -13, 2, 3, 3, 'bone:spine', share='nsp'),
             P('neck2', (0, -2, -19), C(-4.5, -6.5, -27, 9, 9, 8, 'scale:neck2', tag='neck2'),
               C(-3.5, 1.5, -27, 7, 1, 8, 'bone:throat2'), C(-1, -9.5, -25, 2, 3, 3, 'bone:spine', share='nsp'),
               head, rot=(-15, 0, 0)), rot=(-30, 0, 0))
    tail = P('tail1', (0, -1, 16), C(-5.5, -5.5, 16, 11, 9, 12, 'scale:tail', tag='tail1'),
             C(-1, -9, 18, 2, 4, 3, 'bone:tspine', tag='tsp'), C(-1, -8.5, 24, 2, 3, 3, 'bone:tspine2', tag='tsp2'),
             P('tail2', (0, -1, 28), C(-4.5, -4.5, 28, 9, 7, 12, 'scale:tail2', tag='tail2'),
               C(-1, -7.5, 30, 2, 3, 3, 'bone:tspine2', share='tsp2'), C(-1, -7, 35, 2, 3, 3, 'bone:tspine2', share='tsp2'),
               P('tail3', (0, -1, 40), C(-3.5, -3.5, 40, 7, 5, 12, 'scale:tail3', tag='tail3'),
                 C(-0.5, -5.5, 42, 1, 2, 3, 'bone:tspine3', tag='tsp3'), C(-0.5, -5.5, 47, 1, 2, 3, 'bone:tspine3', share='tsp3'),
                 P('tail_tip', (0, -1, 52), C(-2.5, -2.5, 52, 5, 4, 8, 'scale:tail4', tag='tail4'),
                   P('tail_flame', (0, -1, 59), C(-3.5, -4.5, 59, 7, 7, 5, 'flame:flame'),
                     C(-2, -6, 63, 4, 5, 4, 'flame:flame2'), C(-1, -5, 66, 2, 3, 2, 'flame:flame3'))),
                 rot=(-4, 0, 0)), rot=(-6, 0, 0)), rot=(-8, 0, 0))
    spines = [C(-1, -11, -9, 2, 4, 3, 'bone:spine', tag='bsp1'),
              C(-1.5, -12, -3, 3, 5, 3, 'bone:spine2', tag='bsp2'), C(-1.5, -12.5, 3, 3, 6, 3, 'bone:spine3', tag='bsp3'),
              C(-1.5, -12, 9, 3, 5, 3, 'bone:spine2', share='bsp2'), C(-1, -10.5, 14, 2, 4, 3, 'bone:spine4', tag='bsp4'),
              C(-7.5, -9, -8, 2, 3, 2, 'bone:sspine', tag='ssp'), C(5.5, -9, -8, 2, 3, 2, 'bone:sspine', share='ssp'),
              C(-7.5, -9, 2, 2, 3, 2, 'bone:sspine', share='ssp'), C(5.5, -9, 2, 2, 3, 2, 'bone:sspine', share='ssp')]
    body = P('body', (0, 4, 4), C(-9, -7, -11, 18, 14, 28, 'scale:body', tag='body'),
             C(-7.5, -5, -15, 15, 12, 4, 'scale:chest', tag='chest'),
             C(-7, 6.5, -13, 14, 1, 26, 'bone:belly', tag='belly'),
             C(-6.5, -4, -15.6, 13, 10, 1, 'bone:breast', tag='breast'),
             *spines, neck, tail, wing('r'), wing('l'),
             leg('front_right_leg', -8, True, False), leg('front_left_leg', 8, True, True),
             leg('hind_right_leg', -8, False, False), leg('hind_left_leg', 8, False, True))
    return [P('base', (0, 24, 0), body)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = D.CHAINS + [
    Chain(['tail_flame'], rest='back', axes='xy', hang=0.0, freq=2.6, damp=0.3, inertia=6.0,
          limits={'x': (-30.0, 30.0), 'y': (-35.0, 35.0)}),
]


@contextlib.contextmanager
def dragon_points():
    """The shared dragon clips read the snout, tail tip and claw from their module: point them at this body."""
    old = (D.SNOUT, D.TAIL_TIP, D.CLAW_FR)
    D.SNOUT, D.TAIL_TIP, D.CLAW_FR = SNOUT, TAIL_TIP, CLAW_FR
    try:
        yield
    finally:
        D.SNOUT, D.TAIL_TIP, D.CLAW_FR = old


def flicker(c, amp=1.0):
    """The flame of the tail and the horns' tips never stand still."""
    T = c.length
    n = max(12, int(T * 10))
    if c.loop:
        f = lambda t, k, ph: math.sin(2 * math.pi * (k * t / T + ph))
    else:
        f = lambda t, k, ph: math.sin(2 * math.pi * (k * t / 1.1 + ph))
    k1 = max(1, round(T / 0.55)) if c.loop else 1
    c.fn('tail_flame', 'scale', lambda t: (1.0 + 0.12 * amp * f(t, k1, 0.0), 1.0 + 0.18 * amp * f(t, k1, 0.3),
                                           1.0 + 0.1 * amp * f(t, k1, 0.6)), n)


def animations(r):
    x = D.DragonCtx(r, r.variants[0])
    built = []
    with dragon_points():
        for fn in (D.idle, D.walk, D.melee_slam, D.melee_sweep, D.cast, D.roar, D.charge, D.summon, D.phase_change,
                   D.stagger, D.death):
            clip = fn(x)
            if fn is not D.death:
                flicker(clip, 1.0 if fn not in (D.roar, D.phase_change, D.cast) else 1.6)
            built.append(build(clip, x, CHAINS))
    return built


# --------------------------------------------------------------------------------------------- looks

SCALE = rgb('#2c211d')
LAVA = (255, 136, 30)
LAVA_HOT = (255, 214, 90)
CREAM = rgb('#e8cc9c')


def plates(fc, color, every=3):
    for y in range(fc.fh):
        for x in range(fc.fw):
            base = color if (y // every) % 2 == 0 else shade(color, 0.88)
            if y % every == 0:
                base = shade(color, 0.7)
            fc.fpx(x, y, base)


def basalt(fc, gfc, seed, glow, hot=0.35):
    """Blocky basalt scales: staggered blocks with their own shade, the joints between them either dark or full of
    glowing lava (about {@code hot} of them), as on the concept sheet."""
    bw, bh = 6, 4
    for row in range(0, fc.fh // bh + 2):
        off = (row % 2) * (bw // 2) + int(h(seed, row, 'o') * 2)
        y0 = row * bh
        col = 0
        x0 = -off
        while x0 < fc.fw:
            w = bw + int(h(seed, row, col, 'w') * 3) - 1
            f = 0.82 + 0.36 * h(seed, row, col, 'f')
            for y in range(y0, min(fc.fh, y0 + bh)):
                for x in range(max(0, x0), min(fc.fw, x0 + w)):
                    c = fc.fget(x, y)
                    fc.fpx(x, y, tuple(min(255, int(v * f)) for v in c))
                    if y == y0 and f > 1.0:
                        fc.fpx(x, y, lighten(fc.fget(x, y), 0.12))
            lit = h(seed, row, col, 'lit') < hot
            for y in range(y0, min(fc.fh, y0 + bh)):
                if 0 <= x0 < fc.fw:
                    fc.fpx(x0, y, glow if lit else shade(SCALE, 0.5))
                    if lit:
                        gfc.fpx(x0, y, glow)
            for x in range(max(0, x0), min(fc.fw, x0 + w)):
                if 0 <= y0 < fc.fh:
                    lit2 = h(seed, row, col, x // 3, 'h') < hot
                    fc.fpx(x, y0, glow if lit2 else shade(SCALE, 0.5))
                    if lit2:
                        gfc.fpx(x, y0, shade(glow, 0.9))
            x0 += w
            col += 1


def lava(p, part, tag=None, index=0, count=3, length=10, seed=0, glow=LAVA, hot=0.3):
    for side in ('front', 'right', 'back', 'left', 'top'):
        fc = p.face(part, side, tag=tag, index=index)
        gfc = p.face(part, side, tag=tag, index=index, glow=True)
        basalt(fc, gfc, (part, tag, side, seed), glow, hot)
        n = max(1, int(count * fc.fw * fc.fh / 900.0))
        crackle(fc, (part, tag, side, seed), n, length, glow, gfc, lighten(glow, 0.25), branch=0.5)


def variants():
    scale = {'base': '#33251f', 'style': 'plain'}
    cream = {'base': '#e8cc9c', 'style': 'bone'}
    pal = {
        'head': scale, 'snout': scale, 'nose': scale, 'brow': dict(scale, base='#241a17'), 'jaw': scale,
        'neck': scale, 'neck2': scale, 'body': scale, 'chest': scale, 'leg': scale, 'shin': dict(scale, base='#271d1a'),
        'shin2': dict(scale, base='#271d1a'), 'thigh': scale, 'foot': dict(scale, base='#211714'), 'tail': scale,
        'tail2': scale, 'tail3': dict(scale, base='#271d1a'), 'tail4': dict(scale, base='#241a17'),
        'wingbone': dict(scale, base='#251b18'), 'wingbone2': dict(scale, base='#251b18'),
        'strut': dict(scale, base='#2a1e1a'), 'strut2': dict(scale, base='#2a1e1a'), 'strut3': dict(scale, base='#2a1e1a'),
        'strut4': dict(scale, base='#2a1e1a'), 'strut5': dict(scale, base='#2a1e1a'),
        'membrane': {'base': '#ff8c24', 'vein': (150, 50, 14), 'style': 'membrane'},
        'membrane2': {'base': '#ffa02e', 'vein': (150, 50, 14), 'style': 'membrane'},
        'teeth': cream, 'teeth2': cream, 'cheek': cream, 'crest': dict(cream, base='#d8b884'), 'horn': cream,
        'horn2': dict(cream, base='#efdcb4'), 'horn3': dict(cream, base='#f4e6c8'), 'chin': cream, 'claw': cream,
        'spur': cream, 'wingclaw': cream, 'wingclaw2': cream, 'throat': cream, 'throat2': cream, 'belly': cream,
        'breast': cream,
        'spine': {'base': '#1e1513', 'style': 'bone'}, 'spine2': {'base': '#1e1513', 'style': 'bone'},
        'spine3': {'base': '#1e1513', 'style': 'bone'}, 'spine4': {'base': '#1e1513', 'style': 'bone'},
        'sspine': {'base': '#1e1513', 'style': 'bone'}, 'tspine': {'base': '#1e1513', 'style': 'bone'},
        'tspine2': {'base': '#1e1513', 'style': 'bone'}, 'tspine3': {'base': '#1e1513', 'style': 'bone'},
        'flame': {'base': '#ff8020', 'style': 'flame'}, 'flame2': {'base': '#ffb040', 'style': 'flame'},
        'flame3': {'base': '#ffe080', 'style': 'flame'},
    }

    def deco(p, form=0):
        glow = LAVA if form < 3 else LAVA_HOT
        seams = 2 + form
        for part, tag in (('body', 'body'), ('body', 'chest'), ('neck1', 'neck1'), ('neck2', 'neck2'),
                          ('head', 'skull'), ('head', 'snout'), ('jaw', 'jaw'), ('tail1', 'tail1'), ('tail2', 'tail2'),
                          ('tail3', 'tail3'), ('tail_tip', 'tail4'), ('front_right_leg', 'fu'),
                          ('front_right_leg_shin', 'fs'), ('hind_right_leg', 'hu'), ('hind_right_leg_shin', 'hs')):
            lava(p, part, tag=tag, count=seams, length=8 + 2 * form, seed=form, glow=glow, hot=0.22 + 0.1 * form)
        for part, tag in (('body', 'belly'), ('body', 'breast'), ('neck1', 'th1'), ('neck2', None)):
            index = 1 if (part == 'neck2' and tag is None) else 0
            for side in ('front', 'right', 'back', 'left', 'top', 'bottom'):
                plates(p.face(part, side, tag=tag, index=index), CREAM, every=3)
        bf = p.face('body', 'front', tag='breast')
        gbf = p.face('body', 'front', tag='breast', glow=True)
        for y in range(0, bf.fh, 3):                     # the breast plates glow between their ridges
            for x in range(2, bf.fw - 2):
                if (x + y) % 4 == 0:
                    gbf.fpx(x, y + 1, shade(glow, 0.7))
        for side in ('right', 'left'):                   # burning eyes with a slit pupil
            s = p.face('head', side, tag='skull')
            g = p.face('head', side, tag='skull', glow=True)
            ex = 4 if side == 'right' else s.fw - 8
            for dx in range(4):
                for dy in range(2):
                    s.fpx(ex + dx, 5 + dy, LAVA_HOT)
                    g.fpx(ex + dx, 5 + dy, (255, 250, 200))
            s.fpx(ex + 2, 5, (40, 12, 8))
            s.fpx(ex + 2, 6, (40, 12, 8))
            for dx in range(-1, 5):
                s.fpx(ex + dx, 4, (16, 10, 8))
        sn = p.face('head', 'front', tag='snout')
        for (x, y) in ((3, 3), (4, 3), (11, 3), (12, 3)):
            sn.fpx(x, y, (20, 8, 6))
        for part in ('jaw',):
            top = p.face(part, 'top', tag='jaw')
            gtop = p.face(part, 'top', tag='jaw', glow=True)
            for y in range(top.fh):
                for x in range(2, top.fw - 2):
                    top.fpx(x, y, (170, 40, 20))
                    if (x + y) % 2 == 0:
                        gtop.fpx(x, y, (255, 150, 40))
        for part, tags in (('head', ('hn1',)),):
            pass
        for name in ('horn_r', 'horn_r2', 'horn_r3'):
            for side in ('front', 'right', 'back', 'left', 'top', 'bottom'):
                fc = p.face(name, side, index=0)
                for y in range(fc.fh):
                    for x in range(fc.fw):
                        if (x + 2 * y) % 6 == 0:
                            fc.fpx(x, y, shade(fc.fget(x, y), 0.84))
        for part in ('right_wing', 'right_wing_tip'):
            for side in ('top', 'bottom'):
                mf = p.face(part, side, index=1)
                gm = p.face(part, side, index=1, glow=True)
                for y in range(mf.fh):
                    for x in range(mf.fw):
                        c = mf.fget(x, y)
                        if max(c) > 120:
                            gm.fpx(x, y, shade(lighten(c, 0.2), 0.75 + 0.05 * form))
        for name, idxs in (('body', range(4, 13)), ('tail1', (1, 2)), ('tail2', (1, 2)), ('tail3', (1, 2)),
                           ('neck1', (2, 3))):
            for i in idxs:
                for side in ('front', 'right', 'back', 'left'):
                    g = p.face(name, side, index=i, glow=True)
                    for y in range(g.fh):
                        g.fpx(0, y, shade(glow, 0.8))
                        g.fpx(g.fw - 1, y, shade(glow, 0.8))
        for part in ('tail_flame',):
            for i in range(3):
                for side in ('front', 'right', 'back', 'left', 'top', 'bottom'):
                    fc = p.face(part, side, index=i)
                    g = p.face(part, side, index=i, glow=True)
                    for y in range(fc.fh):
                        for x in range(fc.fw):
                            c = fc.fget(x, y)
                            g.fpx(x, y, c)
        if form >= 2:
            for name in ('horn_r', 'horn_r2', 'horn_r3'):
                for side in ('front', 'right', 'back', 'left', 'top'):
                    fc = p.face(name, side, index=0)
                    g = p.face(name, side, index=0, glow=True)
                    crackle(fc, (name, side, form), 1, 6, glow, g, lighten(glow, 0.3))
    burning = dict(pal, membrane={'base': '#ff7a1c', 'vein': (120, 30, 10), 'style': 'membrane'},
                   membrane2={'base': '#ff9024', 'vein': (120, 30, 10), 'style': 'membrane'})
    ember = {'base': '#33201a', 'style': 'magma', 'hot': (255, 150, 40)}
    molten = dict(burning, head=ember, snout=ember, neck=ember, neck2=ember, body=ember, chest=ember, tail=ember,
                  tail2=ember, leg=ember, thigh=ember)
    horns2 = {'horn_r': dict(scale=(1.2, 1.2, 1.3)), 'horn_l': dict(scale=(1.2, 1.2, 1.3))}
    horns3 = {'horn_r': dict(scale=(1.3, 1.3, 1.45)), 'horn_l': dict(scale=(1.3, 1.3, 1.45)),
              'right_wing': dict(scale=(1.12, 1.0, 1.12)), 'left_wing': dict(scale=(1.12, 1.0, 1.12)),
              'tail_flame': dict(scale=(1.4, 1.4, 1.4))}
    return [Variant('b6', palette=pal, paint=deco, glow=True, extra=dict(scale=2.6, hitbox=(6.4, 6.1))),
            Variant('b6_p1', palette=burning, paint=lambda p: deco(p, 1), glow=True, extra=dict(scale=2.6)),
            Variant('b6_p2', palette=burning, poses=horns2, paint=lambda p: deco(p, 2), glow=True,
                    extra=dict(scale=2.6)),
            Variant('b6_p3', palette=molten, poses=horns3, paint=lambda p: deco(p, 3), glow=True,
                    extra=dict(scale=2.6))]


TRAILS = [('tail_tip', (0.0, -1.0, 56.0), (0.0, -1.0, 62.0)), ('front_right_leg_foot', (-8.0, 22.0, -10.0), CLAW_FR)]


def rig():
    vs = variants()
    base = Rig('firedragon', 'FiredragonBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('firedragon', 'FiredragonBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.2
    return finish_rig(r, D.GAIT.walk_speed(), TRAILS)
