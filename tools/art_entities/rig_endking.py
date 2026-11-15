"""ENDKING boss rig: final 終焉の王 (the king of the end, the raid boss of 終焉の玉座), from the concept sheet.

A towering king in dark armour and a floor-length robe that spreads like a cone: a faceless helm with burning violet
eyes under a tall gold crown of seven spires tipped with violet gems; immense stepped pauldrons rimmed and crenellated
in gold; a breastplate with a violet star-core in a gold cross; a gold belt; the robe of twelve strips in crimson,
violet and iron with gold edges, torn at the hem; a vast crimson cape with two flaring wings of cloth; gauntlets with
gold bands. In the right hand the end's greatsword (a long blade of violet-black crystal veined with glowing runes, a
gold guard set with a gem); above the raised left palm the orb of ruin, a violet voxel sphere girdled by a turning
ring. Later forms: the crown rises and floats, cracks of violet light, a halo of black blades.

Motion: slow, absolute. The robe strips, the cape and its wings move with every gesture (spring chains); the orb bobs
and its ring sways. Actions: 終焉の斬撃, a two-handed overhead cut whose impact cracks the floor (slam); a vast level
sweep (sweep); the orb raised and cast down, calling the pillars of the void (cast, from the orb); a gliding thrust
(charge); the sword planted, the orb lifted high (summon); a terrible command with arms spread (roar); the orb pressed
into the core and the power bursting out (phase change); and its own long death: the orb fades, it sinks to its
knees leaning on the sword (1.6 s), then falls forward at 4.5 s as the crown rolls away. Timings are those of the old
final look (BossRigData), so the defeat sequence lines up."""
import math

import eranim
from boss_kit import crackle, diamond, gem, h, line, ragged, ramp, trim
from boss_motion import Ctx, build, finish_rig
from eranim import Chain, Clip, envelope, shake
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
BLADE_TIP = (-14.0, 52.0, 0.0)          # rest coordinates of part greatsword
BLADE_MID = (-14.0, 36.0, 0.0)
ORB = (16.0, 7.0, -9.5)                 # centre of the orb (rest coordinates of part orb, in the hand)
STRIPS = 12
IRON = rgb('#3a3442')
IRON_DARK = rgb('#231e2a')
VIOLET = rgb('#5a2a86')
CRIMSON = rgb('#8a1c3c')
GOLD = rgb('#e2b04a')
GLOW = (200, 110, 255)


def robe():
    """Twelve strips round the waist, each of two segments that flare outward to a cone reaching the floor."""
    out = []
    for i in range(STRIPS):
        phi = 360.0 * i / STRIPS
        rad = math.radians(phi)
        r0 = 6.2
        px, pz = -r0 * math.sin(rad), -r0 * math.cos(rad)
        mat = ('cloth:robe_crimson' if i == 0 else 'cloth:robe_violet' if i in (1, 11, 5, 7) else
               'cloth:robe_iron' if i in (2, 10, 4, 8) else 'cloth:robe_back')
        lower = P('robe_%db' % i, (px, 12, pz), C(px - 3.5, 12, pz - 0.5, 7, 12, 1, mat + '2'), rot=(-8, 0, 0))
        out.append(P('robe_%d' % i, (px, 0, pz), C(px - 2.5, 0, pz - 0.5, 5, 12, 1, mat), lower, rot=(-14, phi, 0)))
    return out


def crown():
    spires = []
    specs = [(-1, -6.0, 9, 2), (-4.5, -5.6, 6, 1), (3.5, -5.6, 6, 1), (-5.6, -2.5, 7, 1), (4.6, -2.5, 7, 1),
             (-5.6, 1.5, 6, 1), (4.6, 1.5, 6, 1), (-2.5, 4.6, 7, 1), (1.5, 4.6, 7, 1)]
    for k, (x, z, hgt, w) in enumerate(specs):
        spires.append(C(x, -34 - hgt, z, w, hgt, 1, 'gold:spire%d' % (0 if k == 0 else 1)))
        spires.append(C(x - (0 if w == 2 else 0), -35 - hgt, z, w, 1, 1, 'gem:spire_gem'))
    return P('crown', (0, -32, -0.5), C(-5, -34, -5.5, 10, 2, 10, 'gold:crown_band'),
             C(-1.5, -33.8, -6.2, 3, 3, 1, 'gem:crown_gem'), *spires)


def sword():
    cx = -14.0
    return P('greatsword', (cx, 5.5, 0),
             C(cx - 1, -2, -1, 2, 12, 2, 'leather:grip'),
             C(cx - 1.5, -4, -1.5, 3, 2, 3, 'gold:pommel'),
             C(cx - 1.5, 10, -6, 3, 2, 12, 'gold:guard', tag='guard'),
             C(cx - 1.5, 9, -7, 3, 4, 2, 'gold:guard_end', tag='ge'), C(cx - 1.5, 9, 5, 3, 4, 2, 'gold:guard_end', share='ge'),
             C(cx - 2, 10, -1.5, 4, 3, 3, 'gem:guard_gem'),
             C(cx - 1, 12, -3, 2, 36, 6, 'crystal:blade', tag='blade'),
             C(cx - 1, 48, -2, 2, 3, 4, 'crystal:blade_tip', tag='tip1'),
             C(cx - 1, 51, -1, 2, 2, 2, 'crystal:blade_tip2', tag='tip2'),
             rot=(-42, 0, 0))


def orb():
    """The orb of ruin above the raised left palm: three crossed blocks round a hot core, girdled by a ring of gold
    segments (placed in the hand's frame so that it floats over the palm in the rest pose)."""
    ox, oy, oz = ORB
    ring = []
    for k in range(8):
        ring.append(P('orb_ring_%d' % k, ORB, C(ox - 1.5, oy - 0.5, oz - 7, 3, 1, 1, 'gold:ring_seg',
                                                tag='rs' if k == 0 else None, share=None if k == 0 else 'rs'),
                      rot=(0, 45 * k, 0)))
    return P('orb', ORB,
             C(ox - 3, oy - 3, oz - 2, 6, 6, 4, 'void:orb', tag='o1'), C(ox - 2, oy - 3, oz - 3, 4, 6, 6, 'void:orb2', tag='o2'),
             C(ox - 3, oy - 2, oz - 3, 6, 4, 6, 'void:orb3', tag='o3'), C(ox - 1, oy - 1, oz - 1, 2, 2, 2, 'glowcore:orb_core'),
             P('orb_ring', ORB, *ring, rot=(18, 0, 10)))


def pauldron(s):
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    xo = lambda x0, w: x0 if s < 0 else -x0 - w
    cubes = [C(xo(-22, 13), -29, -8, 13, 7, 16, 'iron:paul1', **k('p1')),
             C(xo(-24, 13), -23, -7.5, 13, 5, 15, 'iron:paul2', **k('p2')),
             C(xo(-25, 12), -18, -7, 12, 4, 14, 'iron:paul3', **k('p3')),
             ]
    for i, z in enumerate((-7.5, -3, 1.5, 6)):
        cubes.append(C(xo(-21.5 + i * 0.0, 2), -32, z, 2, 3, 2, 'gold:merlon', **k('m%d' % i)))
    return cubes


def arm(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 14.0 * s
    kids = [sword()] if s < 0 else [orb()]
    hand = P(name + '_hand', (cx, 3, 0), C(cx - 2.5, 3, -3, 5, 5, 6, 'iron:hand', **k('hand')), *kids)
    fore = P(name + '_forearm', (cx, -8, 0),
             C(cx - 3.5, -8, -4, 7, 11, 8, 'iron:gauntlet', **k('gaunt')),
             C(cx - 3.5, -7, -4, 7, 2, 8, 'gold:gband', inflate=0.35, **k('gb1')),
             C(cx - 3.5, 1, -4, 7, 1, 8, 'gold:gband2', inflate=0.35, **k('gb2')),
             hand, rot=((-14, 0, 0) if s < 0 else (-58, 0, 0)))
    return P(name + '_arm', (cx, -19, 0), *pauldron(s),
             C(cx - 3, -18, -3.5, 6, 10, 7, 'iron:uarm', **k('uarm')),
             fore, rot=((-10, 0, 8) if s < 0 else (-38, 0, -14)))


def geometry():
    head = P('head', (0, -24, -1),
             C(-4, -32, -4.5, 8, 8, 8, 'iron:helm', tag='helm'),
             C(-4.5, -29.5, -5, 9, 2, 1, 'iron:brow'),
             C(-3, -27.5, -5.2, 6, 3, 1, 'iron:visor', tag='visor'),
             crown())
    halo = P('halo', (0, -26, 9), *[P('halo_%d' % k, (0, -26, 9),
                                      C(-1, -41, 8.5, 2, 9, 1, 'iron:halo_blade', tag='hb' if k == 0 else None,
                                        share=None if k == 0 else 'hb'), rot=(0, 0, 360.0 * k / 10)) for k in range(10)],
             opt=True)
    core = P('core', (0, -16, -6.8), C(-1.5, -19.5, -7.5, 3, 7, 1, 'gem:core_v'), C(-3.5, -17.5, -7.5, 7, 3, 1, 'gem:core_h'),
             C(-1, -17, -8.2, 2, 2, 1, 'glowcore:core_c'))
    cape = P('cape', (0, -24, 6.5), C(-10, -24, 6.3, 20, 12, 1, 'cloth:cape'),
             P('cape_mid', (0, -12, 6.6), C(-12, -12, 6.3, 24, 14, 1, 'cloth:cape2'),
               P('cape_low', (0, 2, 6.6), C(-15, 2, 6.3, 30, 21, 1, 'cloth:cape3'))), rot=(6, 0, 0))
    wing_r = P('cape_wing_r', (-9, -24, 6), C(-19, -24, 5.8, 10, 16, 1, 'cloth:cape_wing', tag='cw'),
               P('cape_wing_r2', (-14, -8, 6), C(-21, -8, 5.8, 12, 16, 1, 'cloth:cape_wing2', tag='cw2')),
               rot=(8, 26, -14))
    wing_l = P('cape_wing_l', (9, -24, 6), C(9, -24, 5.8, 10, 16, 1, 'cloth:cape_wing', share='cw', mirror=True),
               P('cape_wing_l2', (14, -8, 6), C(9, -8, 5.8, 12, 16, 1, 'cloth:cape_wing2', share='cw2', mirror=True)),
               rot=(8, -26, 14))
    chest = P('chest', (0, -8, 0),
              C(-9, -24, -6, 18, 16, 12, 'iron:chest', tag='chest'),
              C(-5, -21, -6.7, 10, 10, 1, 'iron:plate', tag='plate'),
              C(-6, -26, -5, 12, 2, 10, 'gold:gorget'),
              C(-9.5, -11, -6.5, 19, 1, 13, 'gold:rib', inflate=0.1),
              core, head, halo, arm(-1), arm(1), cape, wing_r, wing_l)
    torso = P('torso', (0, 0, 0), C(-6, -8, -4.5, 12, 8, 9, 'iron:waist'), chest)
    hips = P('hips', (0, 1, 0),
             C(-6.5, -1, -5, 13, 2, 10, 'gold:belt'),
             C(-1.5, -1.5, -5.6, 3, 3, 1, 'gem:belt_gem'),
             P('robe_core', (0, 0, 0), C(-6, 0, -6, 12, 12, 12, 'iron:robe_core')),
             P('robe_core2', (0, 24, 0), C(-8.5, 12, -8.5, 17, 12, 17, 'iron:robe_core2')),
             *robe(), torso)
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

def _chains():
    out = [Chain(['cape', 'cape_mid', 'cape_low'], rest='down', axes='xz', hang=0.9, freq=1.0, damp=0.3, inertia=11.0,
                 falloff=1.15, limits={'x': (-4.0, 100.0), 'z': (-30.0, 30.0)},
                 drag={'walk': (12.0, 0.0, 0.0), 'charge': (14.0, 0.0, 0.0), 'roar': (6.0, 0.0, 0.0)},
                 per_clip={'death': {'gain': 0.0, 'limits': {'x': (-95.0, 100.0), 'z': (-30.0, 30.0)}}}),
           Chain(['cape_wing_r', 'cape_wing_r2'], rest='down', axes='xz', hang=0.8, freq=1.2, damp=0.3, inertia=9.0,
                 limits={'x': (-10.0, 70.0), 'z': (-30.0, 40.0)}, drag={'walk': (10.0, 0.0, 0.0)}),
           Chain(['cape_wing_l', 'cape_wing_l2'], rest='down', axes='xz', hang=0.8, freq=1.25, damp=0.3, inertia=9.0,
                 limits={'x': (-10.0, 70.0), 'z': (-40.0, 30.0)}, drag={'walk': (10.0, 0.0, 0.0)})]
    for i in range(STRIPS):
        out.append(Chain(['robe_%d' % i, 'robe_%db' % i], rest='down', axes='xz', hang=0.7, freq=1.3 + 0.02 * i,
                         damp=0.4, inertia=6.0, limits={'x': (-14.0, 14.0), 'z': (-8.0, 8.0)}, key_rate=8,
                         floor=False))
    return out


CHAINS = _chains()
DANGLE = tuple(b for ch in CHAINS for b in ch.bones)


def idle(x):
    T = 4.0
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (0.3 * math.sin(2 * math.pi * t / T + 0.6), 0.3 * b(t) - 0.1, 0.0), 16)
    c.fn('torso', 'rot', lambda t: (-0.8 * b(t), 1.0 * math.sin(2 * math.pi * t / T + 1.4), 0.6 * math.sin(
        2 * math.pi * t / T)), 16)
    c.fn('chest', 'rot', lambda t: (-1.4 * b(t), 0.0, 0.0), 16)
    c.fn('chest', 'scale', lambda t: (1.0 + 0.012 * b(t), 1.0 + 0.008 * b(t), 1.0 + 0.018 * b(t)), 16)
    look = [(0.0, 0.0, 's'), (1.0, 0.0, 's'), (1.6, 6.0, 's'), (2.4, 6.0, 's'), (3.0, -5.0, 's'), (3.6, -5.0, 's'),
            (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-1.0 * b(t - 0.3), eranim.eased_value(look, t), 0.0), 32)
    c.fn('crown', 'pos', lambda t: (0.0, 0.5 * math.sin(2 * math.pi * t / T * 2), 0.0), 16)
    c.fn('crown', 'rot', lambda t: (0.0, 6.0 * math.sin(2 * math.pi * t / T), 0.0), 16)
    c.fn('orb', 'pos', lambda t: (0.0, 1.0 * math.sin(2 * math.pi * t / T * 2), 0.0), 16)
    c.fn('orb', 'rot', lambda t: (0.0, 30.0 * math.sin(2 * math.pi * t / T), 0.0), 16)
    c.fn('orb_ring', 'rot', lambda t: (6.0 * math.sin(2 * math.pi * t / T), 40.0 * math.sin(2 * math.pi * t / T + 0.5),
                                       0.0), 16)
    c.fn('core', 'scale', lambda t: (1.0 + 0.12 * b(2 * t), 1.0 + 0.12 * b(2 * t), 1.0), 16)
    c.fn('halo', 'rot', lambda t: (0.0, 0.0, 10.0 * math.sin(2 * math.pi * t / T)), 16)
    c.fn('right_arm', 'rot', lambda t: (0.6 * math.sin(2 * math.pi * t / T), 0.0, 0.8 * b(t)), 12)
    c.fn('left_arm', 'rot', lambda t: (-1.2 * math.sin(2 * math.pi * t / T + 0.4), 0.0, -1.0 * b(t)), 12)
    return c


def walk(x):
    T = 1.5
    c = Clip('walk', T, loop=True)
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (0.5 * s1(t), -0.4 - 0.5 * math.cos(4 * math.pi * t / T), 0.0), 18)
    c.fn('hips', 'rot', lambda t: (3.0, 5.0 * s1(t), 2.0 * s1(t + 0.2)), 18)
    c.fn('torso', 'rot', lambda t: (2.0, -3.0 * s1(t), -1.0 * s1(t)), 18)
    c.fn('head', 'rot', lambda t: (-3.0, -1.5 * s1(t), 0.0), 18)
    c.fn('right_arm', 'rot', lambda t: (4.0 * s1(t), 0.0, 1.0), 18)
    c.fn('left_arm', 'rot', lambda t: (-2.0 * s1(t), 0.0, 0.0), 18)
    c.fn('greatsword', 'rot', lambda t: (-2.0 - 2.0 * s1(t), 0.0, 0.0), 18)
    return c


def melee_slam(x):
    """終焉の斬撃: the greatsword raised high in both hands (the orb left hovering at the shoulder), then a cut that
    splits the floor before it (impact)."""
    A, B, Cc, I, F, R, E = 0.18, 0.72, 0.9, 1.1, 1.38, 1.8, 2.25
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0.6), (B, 0, 1.0, 1.6), (Cc, 0, 1.2, 1.7, 'o'), (I, 0, -3.0, -2.6, 'I'),
                   (F, 0, -3.2, -2.8, 'O'), (R, 0, -1.8, -1.4), (E, 0, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (A, 4, 0, 0), (B, -14, 0, 0), (Cc, -16, 0, 0, 'o'), (I, 20, 0, 0, 'I'),
                    (F, 22, 0, 0, 'O'), (R, 10, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -10, 0, 0), (I, 12, 0, 0, 'I'), (F, 14, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -12, 0, 0), (I, -16, 0, 0, 'I'), (F, -18, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 12, 0, 4), (B, -165, 0, -18), (Cc, -170, 0, -20, 'o'), (I, -66, 0, -10, 'I'),
                        (F, -62, 0, -10, 'O'), (R, -40, 0, -6), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -26, 0, 0), (I, 0, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('greatsword', [(0, 0, 0, 0), (A, 20, 0, 0), (B, 140, 0, 6), (Cc, 146, 0, 6, 'o'), (I, -20, 0, 0, 'I'),
                         (F, -24, 0, 0, 'O'), (R, -10, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 20, 0, 0), (B, -128, 0, 30), (Cc, -132, 0, 32, 'o'), (I, -34, 0, 34, 'I'),
                       (F, -30, 0, 34, 'O'), (R, -20, 0, 14), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, 30, 0, 0), (I, 40, 0, 0, 'I'), (R, 20, 0, 0), (E, 0, 0, 0)])
    c.scl('orb', [(0, 1, 1, 1), (B, 0.8, 0.8, 0.8), (I, 1.25, 1.25, 1.25, 'O'), (R, 1.0, 1.0, 1.0), (E, 1, 1, 1)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'greatsword', 'x', 'greatsword', BLADE_TIP, 25.0, -100.0, 60.0)
    return c


def melee_sweep(x):
    """A sweep of the end: the blade drawn back on the right and swept level across all before it (impact), the orb
    flung wide in the left hand."""
    A, B, I, F, R, E = 0.15, 0.55, 0.8, 1.05, 1.4, 1.85
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, -1.0, -0.8, 1.0), (I, 0.8, -1.4, -1.6, 'I'), (F, 1.4, -1.2, -1.8, 'O'),
                   (R, 0.6, -0.6, -0.8), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 24, 0), (I, 0, -12, 0, 'I'), (F, 0, -28, 0, 'b'), (R, 0, -12, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 4, 26, -5), (I, 8, -14, 4, 'I'), (F, 6, -30, 5, 'b'), (R, 2, -14, 2),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -30, 0), (I, 0, 14, 0, 'I'), (F, -2, 30, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 6), (B, -60, 70, 40), (I, -82, -6, 8, 'I'), (F, -76, -70, 4, 'O'),
                        (R, -40, -40, 2), (E, 0, 0, 0)])
    c.rot('greatsword', [(0, 0, 0, 0), (B, 40, 0, -10), (I, 50, 0, -20, 'I'), (F, 44, 0, -24, 'O'), (R, 20, 0, -10),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, 10, 0, 20), (I, -10, 0, -40, 'I'), (F, -6, 0, -50, 'O'), (R, 0, 0, -20),
                       (E, 0, 0, 0)])
    return c


def cast(x):
    """The orb raised high in the left hand, swelling, then cast down towards the foe (release at impact), the
    pillars of the void falling."""
    A, B, I, F, R, E = 0.15, 0.55, 0.8, 1.05, 1.4, 1.85
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 0.8, 0.8), (I, 0, -0.6, -1.2, 'I'), (F, 0, -0.8, -1.4, 'O'), (R, 0, -0.4, -0.6),
                   (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -12, -14, 0), (I, 8, 8, 0, 'I'), (F, 10, 10, 0, 'b'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -20, 10, 0), (I, -4, -6, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 10, 0, 0), (B, -130, 0, 10), (I, -30, 0, 6, 'I'), (F, -26, 0, 6, 'b'),
                       (R, -10, 0, 2), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, 30, 0, 0), (I, 20, 0, 0, 'I'), (E, 0, 0, 0)])
    c.scl('orb', [(0, 1, 1, 1), (B, 1.6, 1.6, 1.6), (I, 0.9, 0.9, 0.9, 'I'), (F, 1.15, 1.15, 1.15, 'b'), (E, 1, 1, 1)])
    c.rot('orb_ring', [(0, 0, 0, 0), (B, 0, 180, 0), (I, 0, 360, 0, 'I'), (E, 0, 360, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 10, 0, 10), (I, 6, 0, 6), (E, 0, 0, 0)])
    c.muzzle_part, c.muzzle_point = 'orb', ORB
    return c


def roar(x):
    T = 1.2
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(6.0, 1.0, 1), shake(7.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, 0.6 + 0.3 * math.sin(2 * math.pi * t / T), 0.6), 12)
    c.fn('chest', 'rot', lambda t: (-12.0 + 0.8 * tr(t), 0.0, 1.0 * tr2(t)), 24)
    c.fn('head', 'rot', lambda t: (-16.0 + 1.0 * tr2(t), 0.0, 1.0 * tr(t)), 24)
    c.fn('right_arm', 'rot', lambda t: (-30.0 + 2.0 * tr(t), 0.0, 34.0 + 2.0 * tr2(t)), 14)
    c.fn('greatsword', 'rot', lambda t: (20.0, 0.0, -6.0 + 2.0 * tr(t)), 8)
    c.fn('left_arm', 'rot', lambda t: (-80.0 + 2.0 * tr2(t), 0.0, -30.0 - 3.0 * tr(t)), 14)
    c.fn('orb', 'scale', lambda t: (1.3 + 0.08 * tr(t), 1.3 + 0.08 * tr(t), 1.3 + 0.08 * tr(t)), 14)
    c.fn('core', 'scale', lambda t: (1.3 + 0.1 * tr2(t), 1.3 + 0.1 * tr2(t), 1.1), 14)
    return c


def charge(x):
    """A gliding thrust: the king sweeps forward with the greatsword levelled like a spear (impact)."""
    A, B, I, F, R, E = 0.15, 0.55, 0.85, 1.12, 1.42, 1.8
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0.6), (B, 0, -1.2, 2.4), (I, 0, -1.4, -9.0, 'I'), (F, 0, -1.4, -9.6, 'O'),
                   (R, 0, -0.8, -6.0), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 6, 16, 0), (I, 14, -8, 0, 'I'), (F, 14, -10, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 10, 14, 0), (I, 16, -10, 0, 'I'), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, -12, 0), (I, -20, 8, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 30, 0, 14), (I, -90, 0, 4, 'I'), (F, -94, 0, 4, 'O'), (R, -50, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -10, 0, -20), (I, 30, 0, -30, 'I'), (F, 34, 0, -32), (E, 0, 0, 0)])
    for t in (I, F):
        eranim.aim(c, x.rig, x.v, t, 'greatsword', 'x', 'greatsword', BLADE_MID, BLADE_TIP, -4.0, -160.0, 80.0,
                   forward=True)
    return c


def summon(x):
    """The sword planted before it, the orb lifted high above the crown as the servants of the end rise (impact)."""
    A, B, Cc, I, F, R, E = 0.25, 0.75, 1.0, 1.25, 1.6, 2.0, 2.45
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 0.6, 0.4), (I, 0, 1.4, 0, 'O'), (F, 0, 1.2, 0), (R, 0, 0.6, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -8, 0, 0), (I, -14, 0, 0, 'O'), (F, -12, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -16, 0, 0), (I, -24, 0, 0, 'O'), (F, -22, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, -40, 0, 0), (B, -70, 0, 0), (I, -46, 0, -6, 'I'), (F, -44, 0, -6), (R, -30, 0, 0),
                        (E, 0, 0, 0)])
    c.rot('greatsword', [(0, 0, 0, 0), (A, 30, 0, 0), (B, 60, 0, 0), (I, 70, 0, 0, 'I'), (F, 70, 0, 0), (R, 30, 0, 0),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -100, 0, 10), (I, -150, 0, 4, 'O'), (F, -148, 0, 4), (R, -70, 0, 2),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (I, 50, 0, 0, 'O'), (F, 48, 0, 0), (E, 0, 0, 0)])
    c.scl('orb', [(0, 1, 1, 1), (I, 1.5, 1.5, 1.5, 'O'), (F, 1.4, 1.4, 1.4), (R, 1.1, 1.1, 1.1), (E, 1, 1, 1)])
    for t in (I, F):                                    # the point driven into the floor before it
        eranim.reach_y(c, x.rig, x.v, t, 'greatsword', 'x', 'greatsword', BLADE_TIP, 25.5, 20.0, 150.0)
    return c


def phase_change(x):
    """The orb is pressed into the core: the king bows over it, then the power bursts out (impact) with the arms flung
    wide and the cape and crown lifting."""
    E, I = 3.0, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(6.0, 1.0, 3)
    hold = lambda t: envelope(t, I, 2.5, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.4, 0, -0.6, 0.8, 'O'), (1.3, 0, -2.4, 0.6), (1.75, 0, 2.0, -0.4, 'O'),
                   (2.5, 0, 1.6, -0.3), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.4, 10, 0, 0, 'O'), (1.3, 26, 0, 0), (1.75, -22, 0, 0, 'O'), (2.5, -18, 0, 0),
                    (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.6 * tr(t) * hold(t)), 48)
    c.rot('head', [(0, 0, 0, 0), (0.4, 14, 0, 0), (1.3, 24, 0, 0), (1.75, -30, 0, 0, 'O'), (2.5, -26, 0, 0),
                   (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.4, 10, 0, 30), (1.3, 4, 0, 40), (1.75, -50, 0, -90, 'O'), (2.5, -46, 0, -84),
                       (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (0.4, -40, 0, 0), (1.3, -50, 0, 0), (1.75, 40, 0, 0, 'O'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.4, -20, 0, 10), (1.3, -26, 0, 14), (1.75, -50, 0, 80, 'O'), (2.5, -46, 0, 74),
                        (E, 0, 0, 0)])
    c.scl('orb', [(0, 1, 1, 1), (0.4, 1.0, 1.0, 1.0), (1.3, 0.4, 0.4, 0.4), (1.75, 1.8, 1.8, 1.8, 'O'),
                  (2.5, 1.4, 1.4, 1.4), (E, 1, 1, 1)])
    c.scl('core', [(0, 1, 1, 1), (1.3, 1.5, 1.5, 1.2), (1.75, 2.1, 2.1, 1.5, 'O'), (2.5, 1.6, 1.6, 1.3), (E, 1, 1, 1)])
    c.pos('crown', [(0, 0, 0, 0), (1.3, 0, -0.5, 0), (1.75, 0, 3.0, 0, 'O'), (2.5, 0, 2.4, 0), (E, 0, 0, 0)])
    return c


def stagger(x):
    E = 1.4
    c = Clip('stagger', E, impact=0.1)
    tr = shake(5.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.1, 0, 0.0, 2.6, 'O'), (0.4, 0, -0.4, 3.0), (0.75, 0, -0.2, 1.4), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.1, -16, 6, 8, 'O'), (0.4, -6, -4, 3), (0.7, 6, 2, -2), (1.0, 2, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.1, -20, 12, 6, 'O'), (0.4, -2, -8, 0), (0.7, 8, 4, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.1, -20, 0, 26, 'O'), (0.4, -10, 0, 12), (0.8, -2, 0, 2), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.1, 20, 0, -36, 'O'), (0.4, 10, 0, -16), (0.8, 2, 0, -4), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.12, 0.6, 0.12)), 24)
    return c


def death(x):
    """The long end: the orb dims and falls from the hand, the king reels and sinks to its knees in its robe,
    leaning on the planted sword (1.6 s, the first fall); it holds, trembling, the cape settling; then the hands slip
    and it falls forward (4.5 s), the crown rolling away."""
    E, I, F2 = 5.6, 1.6, 4.5
    c = Clip('death', E, impact=I)
    tr = shake(5.0, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.45, 0, 0.4, 1.2, 'O'), (I, 0, -9.0, 0.6, 'I'), (1.75, 0, -8.6, 0.6, 'O'),
                   (4.1, 0, -8.6, 0.6), (F2, 0, -9.6, 0.4, 'I'), (E, 0, -9.6, 0.4)])
    c.scl('robe_core2', [(0, 1, 1, 1), (I, 1.25, 0.4, 1.25, 'I'), (E, 1.25, 0.4, 1.25)])
    c.scl('robe_core', [(0, 1, 1, 1), (I, 1.15, 0.6, 1.15, 'I'), (E, 1.15, 0.6, 1.15)])
    c.rot('torso', [(0, 0, 0, 0), (0.45, -14, 0, 4, 'O'), (I, 10, 0, -2, 'I'), (1.75, 6, 0, -2, 'b'), (4.1, 8, 0, -2),
                    (F2, 70, 0, 0, 'I'), (4.65, 64, 0, 0, 'b'), (E, 66, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 1.6 * tr(t) * (envelope(t, 0.2, 0.9, 0.2) + envelope(t, 2.2, 3.9, 0.3))),
          60)
    c.rot('chest', [(0, 0, 0, 0), (I, 8, 0, 0, 'I'), (4.1, 10, 0, 0), (F2, 16, 0, 0, 'I'), (E, 16, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.45, -24, 0, 0, 'O'), (I, 24, 0, 0, 'I'), (4.1, 28, 0, 0), (F2, -4, -28, 0, 'I'),
                   (E, -4, -30, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.45, -30, 0, 10, 'O'), (I, -50, 0, 12, 'I'), (4.1, -50, 0, 12),
                        (F2, -110, 0, 30, 'I'), (4.65, -104, 0, 34, 'b'), (E, -106, 0, 32)])
    c.rot('greatsword', [(0, 0, 0, 0), (0.45, 20, 0, 0), (I, 54, 0, 0, 'I'), (4.1, 54, 0, 0), (4.3, 30, 0, 50, 'I'),
                         (F2, 20, 0, 86, 'I'), (4.65, 22, 0, 82, 'b'), (E, 22, 0, 84)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.45, 20, 0, -20, 'O'), (I, -60, 0, 20, 'I'), (4.1, -60, 0, 20),
                       (F2, -70, 0, -50, 'I'), (4.65, -64, 0, -46, 'b'), (E, -66, 0, -48)])
    c.rot('left_forearm', [(0, 0, 0, 0), (I, 50, 0, 0), (E, 50, 0, 0)])
    c.scl('orb', [(0, 1, 1, 1), (0.45, 1.3, 1.3, 1.3, 'O'), (1.2, 0.3, 0.3, 0.3, 'I'), (1.3, 0.01, 0.01, 0.01),
                  (E, 0.01, 0.01, 0.01)])
    c.scl('core', [(0, 1, 1, 1), (0.45, 1.4, 1.4, 1.2, 'O'), (I, 0.8, 0.8, 0.9), (F2, 0.5, 0.5, 0.8), (E, 0.4, 0.4, 0.8)])
    c.rot('crown', [(0, 0, 0, 0), (4.3, 0, 0, 0), (F2, -20, 0, 30, 'I'), (5.0, -60, 20, 70, 'O'), (5.3, -86, 30, 88, 'b'),
                    (E, -86, 30, 88)])
    c.pos('crown', [(0, 0, 0, 0), (4.3, 0, 0, 0), (F2, 0, 1.0, -1.5, 'I'), (5.0, -2.0, 1.0, -7.0, 'O'),
                    (5.3, -3.0, -1.6, -8.5, 'b'), (E, -3.0, -1.6, -8.5)])
    c.rot('cape', [(0, 0, 0, 0), (0.45, 16, 0, 0, 'O'), (I, 4, 0, 0, 'I'), (4.1, 4, 0, 0), (F2, -2, 0, 0, 'I'),
                   (E, 0, 0, 0)])
    c.rot('cape_mid', [(0, 0, 0, 0), (0.45, 10, 0, 0), (I, 6, 0, 0), (4.1, 6, 0, 0), (F2, -44, 0, 0, 'I'),
                       (4.75, -38, 0, 0, 'b'), (E, -40, 0, 0)])
    c.rot('cape_low', [(0, 0, 0, 0), (0.45, 8, 0, 0), (I, 30, 0, 0, 'I'), (4.1, 30, 0, 0), (F2, -42, 0, 6, 'I'),
                       (4.8, -34, 0, 4, 'b'), (E, -36, 0, 4)])
    for t in (I, 1.75, 4.1, F2, 4.65, E):
        eranim.ground(c, x.rig, x.v, t, skip=DANGLE + ('greatsword', 'crown', 'orb', 'halo'))
    for t in (I, 1.75, 4.1):                            # kneeling, leaning on the planted sword
        eranim.reach_y(c, x.rig, x.v, t, 'greatsword', 'x', 'greatsword', BLADE_TIP, 25.0, 0.0, 150.0)
    eranim.rest_on_floor(c, x.rig, x.v, (F2, 4.65, E), 'greatsword', axis='x')
    eranim.rest_on_floor(c, x.rig, x.v, (5.3, E), 'crown', axis='z')
    eranim.rest_on_floor(c, x.rig, x.v, (F2, 4.8, E), 'cape_low', axis='x')
    return c


def animations(r):
    x = Ctx(r, r.variants[0])
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

def plates(fc, seed, color, light, cell=4):
    """Armour of dark stone blocks (as on the sheet): cells of iron, violet-black and slate, each with a dark joint and
    a lit upper edge, lighter towards the top of the part."""
    pal = (IRON_DARK, IRON, IRON, rgb('#4a4256'), rgb('#5a5266'))
    for cy in range(0, fc.fh, cell):
        for cx in range(0, fc.fw, cell):
            r = h(seed, cx, cy)
            idx = min(len(pal) - 1, int((r * 0.8 + 0.25 * (1.0 - cy / max(1, fc.fh))) * len(pal)))
            base = pal[idx]
            for y in range(cy, min(fc.fh, cy + cell)):
                for x in range(cx, min(fc.fw, cx + cell)):
                    col = tuple(min(255, int(v * (0.92 + 0.16 * h(seed, x, y)))) for v in base)
                    if y == cy:
                        col = lighten(col, 0.16)
                    if x == cx or y == cy + cell - 1:
                        col = shade(col, 0.6)
                    fc.fpx(x, y, col)


def paint_armour(p, glow, form):
    for part, tag in (('chest', 'chest'), ('torso', None), ('right_arm', 'p1'), ('right_arm', 'p2'), ('right_arm', 'p3'),
                      ('right_arm', 'uarm'), ('right_forearm', 'gaunt'), ('right_hand', 'hand'), ('head', 'helm')):
        for side in ('front', 'right', 'back', 'left'):
            plates(p.face(part, side, tag=tag), (part, side), IRON, lighten(IRON, 0.5))
    for tag in ('p1', 'p2', 'p3'):
        top = p.face('right_arm', 'top', tag=tag)
        plates(top, (tag, 'top'), IRON, lighten(IRON, 0.5))
        trim(top, GOLD, rows=(0, 1, -1, -2), cols=(0, 1, -1, -2), shine=True)
        for side in ('front', 'right', 'back', 'left'):
            f = p.face('right_arm', side, tag=tag)
            g = p.face('right_arm', side, tag=tag, glow=True)
            trim(f, GOLD, rows=(0, 1, -1, -2), shine=True)
            if form >= 2:
                crackle(f, (tag, side), 1, 6, lighten(VIOLET, 0.4), g, glow)
    pl = p.face('chest', 'front', tag='plate')
    for y in range(pl.fh):
        for x in range(pl.fw):
            pl.fpx(x, y, mix(rgb('#6a6474'), rgb('#8a8494'), (x + y) % 3 / 3.0))
    trim(pl, GOLD, rows=(0, -1), cols=(0, -1), shine=True)
    for y in range(pl.fh):
        pl.fpx(pl.fw // 2 - 1, y, GOLD)
        pl.fpx(pl.fw // 2, y, GOLD)
    for x in range(pl.fw):
        pl.fpx(x, pl.fh // 2 - 1, GOLD)
        pl.fpx(x, pl.fh // 2, GOLD)
    v = p.face('head', 'front', tag='visor')
    gv = p.face('head', 'front', tag='visor', glow=True)
    for y in range(v.fh):
        for x in range(v.fw):
            v.fpx(x, y, IRON_DARK)
    for (x0, x1) in ((1, 4), (7, 10)):
        for x in range(x0, x1 + 1):
            v.fpx(x, 2, glow)
            gv.fpx(x, 2, lighten(glow, 0.4))
            gv.fpx(x, 3, glow)
        gv.fpx((x0 + x1) // 2, 2, (255, 255, 255))
    hf = p.face('head', 'front', tag='helm')
    for y in range(hf.fh):
        for x in range(hf.fw):
            hf.fpx(x, y, shade(IRON_DARK, 0.9 + 0.1 * ((x * 3 + y) % 2)))
    ch = p.face('chest', 'front', tag='chest')
    gch = p.face('chest', 'front', tag='chest', glow=True)
    if form >= 2:
        for side in ('front', 'right', 'back', 'left'):
            for part, tag in (('chest', 'chest'), ('torso', None), ('right_forearm', 'gaunt')):
                crackle(p.face(part, side, tag=tag), (part, side, 'f2'), 2, 8, lighten(VIOLET, 0.45),
                        p.face(part, side, tag=tag, glow=True), glow)


def paint_crown(p, glow):
    band = p.face('crown', 'front', index=0)
    for side in ('front', 'right', 'back', 'left'):
        f = p.face('crown', side, index=0)
        g = p.face('crown', side, index=0, glow=True)
        trim(f, lighten(GOLD, 0.3), rows=(0,), shine=True)
        for x in range(2, f.fw - 2, 5):
            gem(f, x, 1, 2, 2, lighten(VIOLET, 0.3), g, glow)
    gf = p.face('crown', 'front', index=1)
    diamond(gf, gf.fw // 2, gf.fh // 2, 2, lighten(VIOLET, 0.4), p.face('crown', 'front', index=1, glow=True),
            lighten(glow, 0.3))
    n = len(p.rig.parts['crown'].cubes)
    for i in range(2, n):
        c = p.rig.parts['crown'].cubes[i]
        if c.mat.startswith('gem:'):
            for side in ('front', 'right', 'back', 'left', 'top'):
                g = p.face('crown', side, index=i, glow=True)
                for y in range(g.fh):
                    for x in range(g.fw):
                        g.fpx(x, y, lighten(glow, 0.3))


def paint_robe(p, glow, form, tear=1.0):
    cols = {'robe_crimson': CRIMSON, 'robe_violet': VIOLET, 'robe_iron': IRON, 'robe_back': shade(VIOLET, 0.7)}
    for i in range(STRIPS):
        for part in ('robe_%d' % i, 'robe_%db' % i):
            mat = p.rig.parts[part].cubes[0].mat.split(':')[1].rstrip('2')
            base = cols[mat]
            for side in ('front', 'back'):
                f = p.face(part, side)
                g = p.face(part, side, glow=True)
                ramp(f, lighten(base, 0.08) if part.endswith('b') is False else base,
                     shade(base, 0.7) if part.endswith('b') else base, keep=0.55)
                trim(f, GOLD, cols=(0, -1), shine=True)
                if form >= 2:
                    for y in range(2, f.fh - 2, 4):
                        g.fpx(f.fw // 2, y, shade(glow, 0.7))
            if part.endswith('b'):
                for side in ('front', 'back', 'right', 'left'):
                    ragged(p.face(part, side), int(4 * tear), (part, side), min_depth=1)
                trim(p.face(part, 'front'), GOLD, rows=(-6,), shine=True)


def paint_cape(p, tear=1.0):
    for part, top_c, bottom_c in (('cape', CRIMSON, shade(CRIMSON, 0.9)), ('cape_mid', shade(CRIMSON, 0.9),
                                                                         shade(CRIMSON, 0.8)),
                                  ('cape_low', shade(CRIMSON, 0.8), mix(CRIMSON, VIOLET, 0.5)),
                                  ('cape_wing_r', CRIMSON, shade(CRIMSON, 0.85)),
                                  ('cape_wing_r2', shade(CRIMSON, 0.85), mix(CRIMSON, VIOLET, 0.4))):
        for side in ('back', 'right', 'left'):
            ramp(p.face(part, side), top_c, bottom_c, keep=0.6)
        ramp(p.face(part, 'front'), shade(top_c, 0.5), shade(bottom_c, 0.5), keep=0.5)
    for part, depth in (('cape_low', 9), ('cape_wing_r2', 7), ('cape_mid', 2)):
        for side in ('back', 'front', 'right', 'left'):
            ragged(p.face(part, side), int(depth * tear), (part, side), min_depth=1 if depth > 2 else 0)
    for part in ('cape_low', 'cape_wing_r2'):
        bot = p.face(part, 'bottom')
        for y in range(bot.fh):
            for x in range(bot.fw):
                bot.fclear(x, y)
    trim(p.face('cape_low', 'back'), GOLD, rows=(-11,), shine=True)


def paint_sword(p, glow, form):
    """The end's greatsword as on the sheet: a lilac crystal blade with bright lavender edges, a dark violet body and a
    burning violet fuller, runes down its length; a gold guard."""
    edge, body, deep = rgb('#d8c0ff'), rgb('#7a46b8'), rgb('#2e1648')
    for side in ('right', 'left'):
        b = p.face('greatsword', side, tag='blade')
        gb = p.face('greatsword', side, tag='blade', glow=True)
        for y in range(b.fh):
            for x in range(b.fw):
                d = abs(x - (b.fw - 1) / 2.0) / (b.fw / 2.0)
                if d > 0.78:
                    col = edge if (y + x) % 5 else lighten(edge, 0.3)
                    gb.fpx(x, y, shade(glow, 0.45))
                elif d > 0.5:
                    col = mix(body, edge, (d - 0.5) / 0.3 * 0.5)
                elif d > 0.2:
                    col = mix(deep, body, (d - 0.2) / 0.3)
                else:
                    col = mix(rgb('#b060ff'), rgb('#e0a0ff'), h('fuller', side, y // 3))
                    gb.fpx(x, y, glow)
                b.fpx(x, y, shade(col, 0.88 + 0.12 * h('blade', side, x, y // 2)))
        cx = b.fw // 2
        for y in range(3, b.fh - 4, 7):                 # runes across the fuller
            for (dx, dy) in ((0, 0), (0, 1), (0, 2), (-1, 1), (1, 1)) if (y // 7) % 2 == 0 else \
                    ((-1, 0), (1, 0), (0, 1), (-1, 2), (1, 2)):
                b.fpx(cx + dx, y + dy, (255, 235, 255))
                gb.fpx(cx + dx, y + dy, (255, 225, 255))
        crackle(b, ('blade', side, form), 1 + form, 6, lighten(body, 0.4), gb, glow, branch=0.5)
        for t in ('tip1', 'tip2'):
            tf = p.face('greatsword', side, tag=t)
            tg = p.face('greatsword', side, tag=t, glow=True)
            for y in range(tf.fh):
                for x in range(tf.fw):
                    d = abs(x - (tf.fw - 1) / 2.0) / (tf.fw / 2.0)
                    tf.fpx(x, y, edge if d > 0.5 else mix(rgb('#b060ff'), edge, 0.3))
                    tg.fpx(x, y, shade(glow, 0.5))
    for t in ('blade', 'tip1', 'tip2'):
        for side in ('front', 'back'):
            f = p.face('greatsword', side, tag=t)
            g = p.face('greatsword', side, tag=t, glow=True)
            for y in range(f.fh):
                for x in range(f.fw):
                    f.fpx(x, y, lighten(edge, 0.15) if (x + y) % 4 else (255, 245, 255))
                    g.fpx(x, y, shade(glow, 0.55))
    for side in ('front', 'back', 'top', 'right', 'left'):
        trim(p.face('greatsword', side, tag='guard'), lighten(GOLD, 0.25), rows=(0,), shine=True)


def paint_orb(p, glow, form):
    for tag in ('o1', 'o2', 'o3'):
        for side in ('front', 'right', 'back', 'left', 'top', 'bottom'):
            f = p.face('orb', side, tag=tag)
            g = p.face('orb', side, tag=tag, glow=True)
            for y in range(f.fh):
                for x in range(f.fw):
                    r = h(tag, side, x // 2, y // 2)
                    col = lighten(VIOLET, 0.5) if r < 0.3 else (VIOLET if r < 0.7 else shade(VIOLET, 0.6))
                    f.fpx(x, y, col)
                    if r < 0.45:
                        g.fpx(x, y, glow if r < 0.2 else shade(glow, 0.7))


def variants():
    iron = {'base': '#3a3442', 'style': 'plain'}
    gold = {'base': '#e2b04a', 'style': 'gold'}
    cloth = {'base': '#8a1c3c', 'style': 'cloth'}
    pal = {
        'helm': iron, 'brow': dict(iron, base='#2c2734'), 'visor': iron, 'chest': iron, 'plate': iron, 'waist': iron,
        'paul1': iron, 'paul2': dict(iron, base='#352f3d'), 'paul3': dict(iron, base='#302a38'), 'uarm': iron,
        'gauntlet': iron, 'hand': dict(iron, base='#2c2734'), 'robe_core': {'base': '#2a2232', 'style': 'cloth'},
        'robe_core2': {'base': '#26202e', 'style': 'cloth'}, 'halo_blade': {'base': '#1c1822', 'style': 'metal'},
        'crown_band': gold, 'spire0': gold, 'spire1': dict(gold, base='#ecbe58'), 'gorget': gold, 'rib': gold,
        'belt': gold, 'merlon': gold, 'gband': gold, 'gband2': gold,
        'pommel': gold, 'guard': gold, 'guard_end': gold, 'ring_seg': gold,
        'grip': {'base': '#2a1a32', 'style': 'leather'},
        'crown_gem': {'base': '#b46cff', 'style': 'gem'}, 'spire_gem': {'base': '#c88aff', 'style': 'gem'},
        'core_v': {'base': '#b46cff', 'style': 'gem'}, 'core_h': {'base': '#b46cff', 'style': 'gem'},
        'core_c': {'base': '#f0d0ff', 'style': 'glowcore'}, 'belt_gem': {'base': '#b46cff', 'style': 'gem'},
        'guard_gem': {'base': '#b46cff', 'style': 'gem'},
        'blade': {'base': '#7a46b8', 'style': 'plain'}, 'blade_tip': {'base': '#7a46b8', 'style': 'plain'},
        'blade_tip2': {'base': '#7a46b8', 'style': 'plain'},
        'orb': {'base': '#5a2a86', 'style': 'void'}, 'orb2': {'base': '#5a2a86', 'style': 'void'},
        'orb3': {'base': '#5a2a86', 'style': 'void'}, 'orb_core': {'base': '#f0d0ff', 'style': 'glowcore'},
        'robe_crimson': cloth, 'robe_crimson2': cloth, 'robe_violet': dict(cloth, base='#5a2a86'),
        'robe_violet2': dict(cloth, base='#5a2a86'), 'robe_iron': dict(cloth, base='#3a3442'),
        'robe_iron2': dict(cloth, base='#3a3442'), 'robe_back': dict(cloth, base='#3e1e5e'),
        'robe_back2': dict(cloth, base='#3e1e5e'),
        'cape': cloth, 'cape2': cloth, 'cape3': cloth, 'cape_wing': cloth, 'cape_wing2': cloth,
    }

    def deco(p, form=0):
        glow = GLOW if form < 2 else (220, 140, 255)
        paint_armour(p, glow, form)
        paint_crown(p, glow)
        paint_robe(p, glow, form, tear=1.0 + 0.2 * form)
        paint_cape(p, tear=1.0 + 0.2 * form)
        paint_sword(p, glow, form)
        paint_orb(p, glow, form)
        for idx in (0, 1):
            g = p.face('core', 'front', index=idx, glow=True)
            for y in range(g.fh):
                for x in range(g.fw):
                    g.fpx(x, y, glow)
        gc = p.face('core', 'front', index=2, glow=True)
        for y in range(gc.fh):
            for x in range(gc.fw):
                gc.fpx(x, y, (255, 240, 255))
        gb = p.face('hips', 'front', index=1, glow=True)
        for y in range(gb.fh):
            for x in range(gb.fw):
                gb.fpx(x, y, glow)
        if form >= 3:
            for k in range(10):
                for side in ('front', 'back', 'right', 'left'):
                    g = p.face('halo_%d' % k, side, glow=True)
                    for y in range(g.fh):
                        g.fpx(g.fw // 2, y, glow)
    floating = {'crown': dict(pos=(0, 6, 0))}
    return [Variant('final', palette=pal, paint=deco, glow=True, extra=dict(scale=2.7, hitbox=(4.2, 8.9))),
            Variant('final_p1', palette=pal, poses=floating, paint=lambda p: deco(p, 1), glow=True,
                    extra=dict(scale=2.7)),
            Variant('final_p2', palette=pal, poses=dict(floating, orb=dict(scale=(1.3, 1.3, 1.3))),
                    paint=lambda p: deco(p, 2), glow=True, extra=dict(scale=2.7)),
            Variant('final_p3', show={'halo'}, palette=pal,
                    poses=dict(floating, orb=dict(scale=(1.45, 1.45, 1.45)), crown=dict(pos=(0, 7, 0),
                                                                                         scale=(1.1, 1.1, 1.1))),
                    paint=lambda p: deco(p, 3), glow=True, extra=dict(scale=2.7))]


TRAILS = [('greatsword', BLADE_MID, BLADE_TIP)]


def rig():
    vs = variants()
    base = Rig('endking', 'EndkingBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('endking', 'EndkingBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.6
    return finish_rig(r, 0.0, TRAILS)
