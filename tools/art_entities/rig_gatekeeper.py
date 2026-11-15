"""GATEKEEPER boss rig: b8 終焉の門番 (the gatekeeper of the end: the guardian of the tower of the end), from the concept
sheet.

An ancient construct-knight of obsidian, deepslate and stone bricks, trimmed with gold blocks and lit by amethyst:
a block helm with a face plate, two slits of purple light and a crown of four gold prongs (the outer pair taller and
leaning out); an amethyst star-core in the chest; huge stepped pauldrons of stone bricks with gold edges; massive
gauntlets and fists with gold bands; a gold belt with an amethyst; a crimson banner with a pale tree-rune hanging in
front and a dark crimson cape behind; thick legs with gold knee bands and block boots. In its right hand the
gatekeeper's greatsword: a broad blade of obsidian and purpur with a cracked amethyst core of light, a gold cross-guard
with block ends and a gold pommel. Every block surface is a mosaic of the sheet's palette (obsidian, deepslate, stone
brick, gold, amethyst, purpur). Later forms: the core and the crown burn, veins of amethyst, floating shards, cracks.

Motion: colossal and deliberate. Actions: a two-handed overhead blow that cracks the floor (slam, 地砕き), a vast
level sweep (sweep, 薙ぎ払い), dark power gathered in the left fist and hurled (cast), the blade driven into the floor
ahead to send the shockwave (charge), the sword planted with both hands on the pommel as the shades rise (summon,
闇の召喚), a bellow (roar), the gate's roar with the core blazing (phase change) and a death on its knees over the
planted sword before it falls. Timings are those of the old b8 look (BossRigData)."""
import math

import eranim
from boss_kit import crackle, diamond, frect, gem, h, line, ragged, ramp, trim
from boss_motion import Ctx, build, feet, finish_rig, gait
from eranim import Chain, Clip, Gait, envelope, shake
from erpaint import lighten, mix, rgb, shade
from ermodel import C, P, Rig, Variant

SLOTS = ['idle', 'walk', 'melee_slam', 'melee_sweep', 'cast', 'roar', 'charge', 'summon', 'phase_change', 'stagger',
         'death']
LEGS = (('right_leg', 'right_shin', 'right_foot'), ('left_leg', 'left_shin', 'left_foot'))
BLADE_BASE = (-15.0, 16.0, 0.0)       # the blade (rest coordinates of part greatsword)
BLADE_TIP = (-15.0, 48.0, 0.0)
BLADE_MID = (-15.0, 34.0, 0.0)
FIST_L = (15.0, 8.5, -4.5)

OBSIDIAN = rgb('#1c1626')
DEEPSLATE = rgb('#3d3b44')
STONE = rgb('#7c7a84')
STONE_LIGHT = rgb('#9a98a2')
GOLD = rgb('#e2b04a')
AMETHYST = rgb('#a35cf0')
PURPUR = rgb('#c8a8e6')
CRIMSON = rgb('#7a1e34')
GLOW = (190, 110, 255)


def prongs():
    """The crown of four gold prongs: the inner pair upright over the brow, the outer pair taller, leaning out."""
    return P('prongs', (0, -31, -1),
             P('prong_ri', (-3, -31, -3.5), C(-4, -37, -4.5, 2, 6, 2, 'gold:prong', tag='pi'),
               C(-4, -38, -4.5, 2, 1, 2, 'gold:prong_tip', tag='pit'), rot=(0, 0, -6)),
             P('prong_li', (3, -31, -3.5), C(2, -37, -4.5, 2, 6, 2, 'gold:prong', share='pi', mirror=True),
               C(2, -38, -4.5, 2, 1, 2, 'gold:prong_tip', share='pit', mirror=True), rot=(0, 0, 6)),
             P('prong_ro', (-5, -30, 0), C(-6.5, -39, -1, 3, 9, 2, 'gold:prong2', tag='po'),
               C(-8, -42, -1, 2, 4, 2, 'gold:prong3', tag='po3'), rot=(0, 0, -16)),
             P('prong_lo', (5, -30, 0), C(3.5, -39, -1, 3, 9, 2, 'gold:prong2', share='po', mirror=True),
               C(6, -42, -1, 2, 4, 2, 'gold:prong3', share='po3', mirror=True), rot=(0, 0, 16)))


def sword():
    cx = -15.0
    return P('greatsword', (cx, 8.5, 0),
             C(cx - 1, 2, -1, 2, 10, 2, 'leather:grip'),
             C(cx - 1.5, 0, -1.5, 3, 2, 3, 'gold:pommel'),
             C(cx - 1.5, 12, -7, 3, 2, 14, 'gold:guard', tag='guard'),
             C(cx - 2, 10, -8.5, 4, 5, 2, 'gold:guard_end', tag='ge'), C(cx - 2, 10, 6.5, 4, 5, 2, 'gold:guard_end', share='ge'),
             C(cx - 1.5, 12.5, -1.5, 3, 3, 3, 'gem:guard_gem'),
             C(cx - 1, 14, -4, 2, 30, 8, 'stone:blade', tag='blade'),
             C(cx - 1, 44, -3, 2, 3, 6, 'stone:blade_tip', tag='tip1'),
             C(cx - 1, 47, -1.5, 2, 2, 3, 'stone:blade_tip2', tag='tip2'),
             rot=(-46, 0, 0))


def arm(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 15.0 * s
    kids = [sword()] if s < 0 else []
    fist = P(name + '_hand', (cx, 5, 0), C(cx - 4.5, 5, -4.5, 9, 7, 9, 'stone:fist', **k('fist')),
             C(cx - 4.5, 10, -5.2, 9, 2, 1, 'gold:knuckles', **k('knk')), *kids)
    fore = P(name + '_forearm', (cx, -6, 0),
             C(cx - 4.5, -6, -5, 9, 11, 10, 'stone:gauntlet', **k('gaunt')),
             C(cx - 4.5, -5, -5, 9, 2, 10, 'gold:gband', inflate=0.35, **k('gb1')),
             C(cx - 4.5, 2, -5, 9, 1, 10, 'gold:gband2', inflate=0.35, **k('gb2')),
             C(cx - 5.5 if s < 0 else cx + 2.5, -3, -2, 3, 4, 4, 'stone:nub4', **k('nb4')),
             fist, rot=(-12 if s < 0 else -8, 0, 0))
    xo = lambda x0, w: x0 if s < 0 else -x0 - w          # mirror a box's left edge to the other side
    return P(name + '_arm', (cx, -18, 0),
             C(xo(-26, 13), -29, -8.5, 13, 9, 17, 'stone:paul_top', **k('pt')),
             C(xo(-27, 14), -23, -8, 14, 6, 16, 'stone:paul_mid', **k('pm')),
             C(xo(-25, 12), -18, -7, 12, 4, 14, 'stone:paul_low', **k('pl')),
             C(xo(-26.5, 14), -29.5, -9, 14, 1, 18, 'gold:paul_rim', **k('pr')),
             C(xo(-27.5, 15), -18.5, -8.5, 15, 1, 17, 'gold:paul_rim2', **k('pr2')),
             C(xo(-24, 4), -32, -5, 4, 3, 5, 'stone:nub', **k('nb1')),
             C(xo(-19, 3), -31, 2, 3, 2, 4, 'stone:nub2', **k('nb2')),
             C(xo(-28, 3), -24, -3, 3, 4, 5, 'stone:nub3', **k('nb3')),
             C(cx - 4, -17, -4, 8, 11, 8, 'stone:uarm', **k('uarm')),
             fore, rot=((-8, 0, 8) if s < 0 else (-4, 0, -8)))


def leg(s):
    name = 'right' if s < 0 else 'left'
    k = (lambda t: dict(tag=t)) if s < 0 else (lambda t: dict(share=t, mirror=True))
    cx = 4.8 * s
    foot = P(name + '_foot', (cx, 20, 0), C(cx - 4, 20, -6, 8, 4, 11, 'stone:boot', **k('boot')),
             C(cx - 4, 19.5, -4.5, 8, 1, 9, 'gold:cuff', inflate=0.3, **k('cuff')))
    shin = P(name + '_shin', (cx, 14, 0), C(cx - 3.5, 14, -3.5, 7, 6, 7, 'stone:shin', **k('shin')),
             C(cx - 4, 12.5, -4.5, 8, 3, 2, 'gold:kneeplate', **k('kp')),
             C(cx - 3.5, 13, -3.5, 7, 1, 7, 'gold:kband', inflate=0.35, **k('kb')), foot)
    return P(name + '_leg', (cx, 6.5, 0), C(cx - 4, 6, -4, 8, 8, 8, 'stone:thigh', **k('thigh')),
             C(cx - 5 if s < 0 else cx + 2, 8, -2, 3, 3, 4, 'stone:nub5', **k('nb5')), shin)


def geometry():
    head = P('head', (0, -22, -1),
             C(-4.5, -31, -5, 9, 9, 9, 'stone:helm', tag='helm'),
             C(-3.5, -29, -5.7, 7, 6, 1, 'stone:face', tag='face'),
             C(-5, -32, -5.5, 10, 1, 10, 'gold:circlet'),
             prongs())
    core = P('core', (0, -15, -6.2), C(-1.5, -18.5, -6.9, 3, 7, 1, 'gem:core_v'), C(-3.5, -16.5, -6.9, 7, 3, 1, 'gem:core_h'),
             C(-1, -16, -7.6, 2, 2, 1, 'glowcore:core_c'))
    shards = P('shards', (0, -14, 0),
               C(-19, -34, 4, 2, 4, 2, 'gem:shard', tag='sd'), C(17, -32, 2, 2, 4, 2, 'gem:shard', share='sd'),
               C(-24, -6, 6, 2, 4, 2, 'gem:shard', share='sd'), C(22, -10, 7, 2, 4, 2, 'gem:shard', share='sd'),
               C(-2, -40, 8, 3, 5, 3, 'gem:shard2', tag='sd2'), C(-14, -22, 11, 3, 5, 3, 'gem:shard2', share='sd2'),
               C(12, -24, 11, 3, 5, 3, 'gem:shard2', share='sd2'), opt=True)
    cape = P('cape', (0, -20, 6.4), C(-9, -20, 6.2, 18, 10, 1, 'cloth:cape'),
             P('cape_mid', (0, -10, 6.4), C(-9.5, -10, 6.2, 19, 10, 1, 'cloth:cape2'),
               P('cape_low', (0, 0, 6.4), C(-10, 0, 6.2, 20, 12, 1, 'cloth:cape3'))), rot=(4, 0, 0))
    chest = P('chest', (0, -6, 0),
              C(-10, -22, -6, 20, 16, 12, 'stone:chest', tag='chest'),
              C(-6, -24, -5, 12, 2, 10, 'stone:gorget'),
              C(-10.5, -9, -6.5, 21, 2, 13, 'gold:chest_band', inflate=0.1),
              C(-4.5, -19.5, -6.6, 9, 9, 1, 'stone:core_frame'),
              core, head, arm(-1), arm(1), cape, shards)
    torso = P('torso', (0, 2, 0), C(-7, -6, -4.5, 14, 8, 9, 'stone:waist'), chest)
    banner = P('banner', (0, 3, -5.4), C(-4, 3, -5.9, 8, 9, 1, 'cloth:banner'),
               P('banner2', (0, 12, -5.4), C(-4, 12, -5.9, 8, 8, 1, 'cloth:banner2')))
    hips = P('hips', (0, 4, 0),
             C(-7.5, 2, -4.5, 15, 6, 9, 'stone:pelvis'),
             C(-8, 1, -5, 16, 2, 10, 'gold:belt'),
             C(-2, 0.5, -5.6, 4, 3, 1, 'gold:buckle'),
             C(-1, 1, -6.2, 2, 2, 1, 'gem:belt_gem'),
             banner, torso, leg(-1), leg(1))
    return [P('base', (0, 24, 0), hips)]


# ------------------------------------------------------------------------------------------------ motion

CHAINS = [
    Chain(['cape', 'cape_mid', 'cape_low'], rest='down', axes='xz', hang=0.9, freq=1.1, damp=0.3, inertia=10.0,
          falloff=1.15, limits={'x': (-4.0, 100.0), 'z': (-30.0, 30.0)},
          drag={'walk': (14.0, 0.0, 0.0), 'charge': (12.0, 0.0, 0.0), 'roar': (5.0, 0.0, 0.0)},
          per_clip={'death': {'gain': 0.0, 'limits': {'x': (-90.0, 100.0), 'z': (-30.0, 30.0)}}}),
    Chain(['banner', 'banner2'], rest='down', axes='xz', hang=0.85, freq=1.45, damp=0.3, inertia=8.0,
          limits={'x': (-80.0, 20.0), 'z': (-25.0, 25.0)}, drag={'walk': (-6.0, 0.0, 0.0)}),
]
GAIT = Gait(1.5, stride=13.0, duty=0.64, lift=3.6, heel=7.0, toe=12.0)
DANGLE = tuple(b for ch in CHAINS for b in ch.bones)


def idle(x):
    T = 4.0
    c = Clip('idle', T, loop=True)
    b = lambda t: 0.5 - 0.5 * math.cos(2 * math.pi * t / T)
    sway = lambda t: math.sin(2 * math.pi * t / T + 0.6)
    c.fn('base', 'pos', lambda t: (0.45 * sway(t), 0.35 * b(t) - 0.15, 0.0), 16)
    c.fn('hips', 'rot', lambda t: (0.0, 1.0 * math.sin(2 * math.pi * t / T + 1.4), 1.0 * sway(t)), 16)
    c.fn('chest', 'rot', lambda t: (-1.6 * b(t), -0.8 * math.sin(2 * math.pi * t / T + 1.4), -0.7 * sway(t)), 16)
    c.fn('chest', 'scale', lambda t: (1.0 + 0.014 * b(t), 1.0 + 0.01 * b(t), 1.0 + 0.02 * b(t)), 16)
    look = [(0.0, 0.0, 's'), (0.8, 0.0, 's'), (1.4, 7.0, 's'), (2.2, 7.0, 's'), (2.8, -6.0, 's'), (3.5, -6.0, 's'),
            (T, 0.0, 's')]
    c.fn('head', 'rot', lambda t: (-1.0 * b(t - 0.25) + 0.4, eranim.eased_value(look, t), 0.0), 32)
    c.fn('right_arm', 'rot', lambda t: (0.6 * math.sin(2 * math.pi * t / T), 0.0, 1.0 * b(t - 0.15)), 12)
    c.fn('left_arm', 'rot', lambda t: (-0.8 * math.sin(2 * math.pi * t / T), 0.0, -1.4 * b(t - 0.15)), 12)
    c.fn('core', 'scale', lambda t: (1.0 + 0.12 * b(2 * t), 1.0 + 0.12 * b(2 * t), 1.0), 16)
    c.fn('shards', 'pos', lambda t: (0.0, 1.2 * math.sin(2 * math.pi * t / T * 2), 0.0), 16)
    c.fn('shards', 'rot', lambda t: (0.0, 10.0 * math.sin(2 * math.pi * t / T), 0.0), 16)
    regrip = [(0.0, 0.0, 's'), (2.4, 0.0, 's'), (2.65, -5.0, 'o'), (2.9, 2.0, 's'), (3.2, 0.0, 's'), (T, 0.0, 's')]
    c.fn('right_hand', 'rot', lambda t: (eranim.eased_value(regrip, t), 0.0, 0.0), 32)
    feet(c, x, samples=16)
    return c


def walk(x):
    T = GAIT.length
    c = Clip('walk', T, loop=True)
    w = lambda t, k=1.0, ph=0.0: math.cos(2 * math.pi * (k * t / T - ph))
    s1 = lambda t: math.sin(2 * math.pi * t / T)
    c.fn('base', 'pos', lambda t: (-1.3 * w(t, 1, 0.25), -0.6 - 1.0 * w(t, 2, 0.08), 0.0), 24)
    c.fn('hips', 'rot', lambda t: (1.0 * w(t, 2, 0.1), -6.0 * w(t), 4.5 * s1(t)), 24)
    c.fn('torso', 'rot', lambda t: (4.0 + 1.0 * w(t, 2, 0.15), 3.5 * w(t, 1, 0.06), -2.5 * s1(t - 0.06)), 24)
    c.fn('chest', 'rot', lambda t: (1.0 * w(t, 2, 0.2), 3.0 * w(t, 1, 0.1), -2.0 * s1(t - 0.1)), 24)
    c.fn('head', 'rot', lambda t: (-3.0 - 1.0 * w(t, 2, 0.25), -2.0 * w(t, 1, 0.12), 1.2 * s1(t - 0.14)), 24)
    c.fn('right_arm', 'rot', lambda t: (5.0 * w(t, 1, 0.1), 0.0, 1.0 * w(t, 2, 0.1)), 24)
    c.fn('left_arm', 'rot', lambda t: (-16.0 * w(t, 1, 0.1), 0.0, -2.0 * w(t, 2, 0.1)), 24)
    c.fn('left_forearm', 'rot', lambda t: (-4.0 - 8.0 * max(0.0, w(t, 1, 0.16)), 0.0, 0.0), 24)
    c.fn('greatsword', 'rot', lambda t: (-2.0 - 2.0 * w(t, 1, 0.2), 0.0, 0.0), 24)
    gait(c, x, GAIT)
    return c


def melee_slam(x):
    """地砕き: the greatsword heaved up over the prongs in both hands, a pause at the top, then brought down with the
    whole body and buried in the floor ahead (impact), the floor cracking around it."""
    A, B, Cc, I, F, R, E = 0.18, 0.72, 0.9, 1.1, 1.38, 1.8, 2.25
    c = Clip('melee_slam', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0.6), (B, 0, 0.8, 1.6), (Cc, 0, 1.0, 1.7, 'o'), (I, 0, -4.6, -2.8, 'I'),
                   (F, 0, -5.0, -3.0, 'O'), (R, 0, -2.8, -1.6), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, -4, 0, 0), (I, 10, 0, 0, 'I'), (F, 12, 0, 0, 'O'), (R, 6, 0, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (A, 6, 0, 0), (B, -20, 0, 0), (Cc, -22, 0, 0, 'o'), (I, 26, 0, 0, 'I'),
                    (F, 28, 0, 0, 'O'), (R, 12, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -12, 0, 0), (I, -18, 0, 0, 'I'), (F, -20, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 12, 0, 4), (B, -165, 0, -18), (Cc, -170, 0, -20, 'o'), (I, -66, 0, -10, 'I'),
                        (F, -62, 0, -10, 'O'), (R, -40, 0, -6), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -26, 0, 0), (I, 0, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('greatsword', [(0, 0, 0, 0), (A, 20, 0, 0), (B, 150, 0, 6), (Cc, 156, 0, 6, 'o'), (I, -10, 0, 0, 'I'),
                         (F, -14, 0, 0, 'O'), (R, -6, 0, 0), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, 10, 0, -4), (B, -165, 0, 26), (Cc, -170, 0, 28, 'o'), (I, -70, 0, 26, 'I'),
                       (F, -66, 0, 26, 'O'), (R, -40, 0, 12), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, -6, 0, 0, 'I'), (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.25, 0.65, (-5.0, 1.0), 3.0), (1.85, 2.15, (0.0, 0.0), 2.5)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'greatsword', 'x', 'greatsword', BLADE_TIP, 25.0, -90.0, 60.0)
    return c


def melee_sweep(x):
    """薙ぎ払い: the blade drawn far back on the right, then swept flat across the front (impact at the centre) and
    round to the left, the whole body turning behind it."""
    A, B, I, F, R, E = 0.15, 0.55, 0.8, 1.05, 1.4, 1.85
    c = Clip('melee_sweep', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.6, 0.4), (B, -1.2, -1.8, 1.2), (I, 1.0, -2.2, -1.8, 'I'),
                   (F, 1.6, -2.0, -2.2, 'O'), (R, 0.8, -1.2, -1.2), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 0, 22, 0), (I, 0, -12, 0, 'I'), (F, 0, -26, 0, 'b'), (R, 0, -12, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 4, 30, -5), (I, 8, -16, 4, 'I'), (F, 6, -34, 5, 'b'), (R, 2, -16, 2),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, 4, -36, 0), (I, 0, 16, 0, 'I'), (F, -2, 36, 0, 'b'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, 10, 0, 6), (B, -60, 70, 40), (I, -82, -6, 8, 'I'), (F, -76, -70, 4, 'O'),
                        (R, -40, -40, 2), (E, 0, 0, 0)])
    c.rot('right_forearm', [(0, 0, 0, 0), (B, -30, 0, 0), (I, -4, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('greatsword', [(0, 0, 0, 0), (B, 50, 0, -10), (I, 60, 0, -20, 'I'), (F, 54, 0, -24, 'O'), (R, 24, 0, -10),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -70, 0, 30), (I, -80, 0, -10, 'I'), (F, -60, 0, -50, 'O'), (R, -20, 0, -20),
                       (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.5, 0.78, (-6.0, 2.0), 3.0), (1.4, 1.72, (0.0, 0.0), 2.5)],
         right_plan=[(0.12, 0.42, (3.0, 1.0), 2.0), (1.45, 1.75, (0.0, 0.0), 2.0)])
    return c


def cast(x):
    """Dark power gathered in the left fist held high, the core flaring, then hurled forward (release at impact)."""
    A, B, I, F, R, E = 0.15, 0.55, 0.8, 1.05, 1.4, 1.85
    c = Clip('cast', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 0.6, 1.0), (I, 0, -1.6, -1.6, 'I'), (F, 0, -1.8, -1.8, 'O'),
                   (R, 0, -0.9, -0.9), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -12, -16, 0), (I, 10, 14, 0, 'I'), (F, 12, 16, 0, 'b'), (R, 6, 6, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -14, 10, 0), (I, -4, -10, 0, 'I'), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (A, -20, 0, -6), (B, -170, 0, -16), (I, -86, 0, 6, 'I'), (F, -90, 0, 6, 'b'),
                       (R, -50, 0, 2), (E, 0, 0, 0)])
    c.rot('left_forearm', [(0, 0, 0, 0), (B, -20, 0, 0), (I, 0, 0, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, 14, 0, 10), (I, 18, 0, 12), (R, 6, 0, 4), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (B, 1.6, 1.6, 1.2), (I, 0.9, 0.9, 1.0, 'I'), (F, 1.2, 1.2, 1.1, 'b'), (E, 1, 1, 1)])
    feet(c, x, left_plan=[(0.6, 0.8, (-4.0, 0.5), 2.5), (1.45, 1.75, (0.0, 0.0), 2.0)])
    c.muzzle_part, c.muzzle_point = 'left_hand', FIST_L
    return c


def roar(x):
    T = 1.2
    c = Clip('roar', T, loop=True)
    tr, tr2 = shake(6.0, 1.0, 1), shake(7.5, 1.0, 2)
    c.fn('base', 'pos', lambda t: (0.0, -1.0 + 0.3 * math.sin(2 * math.pi * t / T), 0.8), 12)
    c.fn('chest', 'rot', lambda t: (-12.0 + 0.8 * tr(t), 0.0, 1.2 * tr2(t)), 24)
    c.fn('head', 'rot', lambda t: (-18.0 + 1.0 * tr2(t), 0.0, 1.0 * tr(t)), 24)
    c.fn('right_arm', 'rot', lambda t: (-26.0 + 2.0 * tr(t), 0.0, 28.0 + 2.0 * tr2(t)), 14)
    c.fn('greatsword', 'rot', lambda t: (24.0, 0.0, -6.0 + 2.0 * tr(t)), 8)
    c.fn('left_arm', 'rot', lambda t: (-30.0 + 2.0 * tr2(t), 0.0, -50.0 - 3.0 * tr(t)), 14)
    c.fn('core', 'scale', lambda t: (1.3 + 0.1 * tr(t), 1.3 + 0.1 * tr(t), 1.1), 14)
    feet(c, x, right_plan=[(-1.0, -1.0, (3.0, 1.5), 0.0)], left_plan=[(-1.0, -1.0, (-3.0, 1.0), 0.0)], samples=12)
    return c


def charge(x):
    """The shockwave: a lunge that drives the point of the greatsword into the floor far ahead (impact) and sends
    the wave on."""
    A, B, I, F, R, E = 0.15, 0.55, 0.85, 1.12, 1.42, 1.8
    c = Clip('charge', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (A, 0, -0.8, 0.6), (B, 0, -2.6, 2.4), (I, 0, -3.4, -7.0, 'I'), (F, 0, -3.6, -7.4, 'O'),
                   (R, 0, -2.0, -4.6), (E, 0, 0, 0)])
    c.rot('hips', [(0, 0, 0, 0), (B, 8, 16, 0), (I, 14, -8, 0, 'I'), (F, 14, -10, 0), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, 16, 16, 0), (I, 28, -12, 0, 'I'), (F, 30, -14, 0, 'O'), (R, 14, -6, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -20, -18, 0), (I, -30, 12, 0, 'I'), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (B, -110, 0, 10), (I, -70, 0, 4, 'I'), (F, -68, 0, 4, 'O'), (R, -40, 0, 2),
                        (E, 0, 0, 0)])
    c.rot('greatsword', [(0, 0, 0, 0), (B, 30, 0, 0), (I, -20, 0, 0, 'I'), (F, -22, 0, 0), (R, -10, 0, 0),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -110, 0, -16), (I, -72, 0, 10, 'I'), (F, -70, 0, 10, 'O'), (R, -36, 0, 4),
                       (E, 0, 0, 0)])
    feet(c, x, left_plan=[(0.55, 0.82, (-10.0, 1.0), 3.5), (1.4, 1.72, (0.0, 0.0), 2.5)],
         right_plan=[(0.1, 0.42, (3.0, 1.0), 1.5), (1.42, 1.74, (0.0, 0.0), 1.5)])
    for t in (I, F):
        eranim.reach_y(c, x.rig, x.v, t, 'greatsword', 'x', 'greatsword', BLADE_TIP, 25.0, -100.0, 40.0)
    return c


def summon(x):
    """闇の召喚: the greatsword raised and planted point-down before it, both hands closing on the pommel, the head
    bowed as the shades rise (impact)."""
    A, B, Cc, I, F, R, E = 0.25, 0.75, 1.0, 1.25, 1.6, 2.0, 2.45
    c = Clip('summon', E, impact=I)
    c.pos('base', [(0, 0, 0, 0), (B, 0, 0.8, 0.6), (Cc, 0, 1.0, 0.6, 'o'), (I, 0, -2.0, -1.0, 'I'), (F, 0, -2.2, -1.0),
                   (R, 0, -1.2, -0.5), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (B, -12, 0, 0), (I, 14, 0, 0, 'I'), (F, 16, 0, 0), (R, 8, 0, 0), (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (B, -16, 0, 0), (I, 22, 0, 0, 'I'), (F, 24, 0, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (A, -60, 0, 0), (B, -150, 0, -14), (Cc, -154, 0, -14, 'o'), (I, -70, 0, -26, 'I'),
                        (F, -68, 0, -26), (R, -40, 0, -12), (E, 0, 0, 0)])
    c.rot('greatsword', [(0, 0, 0, 0), (A, 40, 0, 0), (B, 64, 0, 0), (I, 74, 0, 4, 'I'), (F, 74, 0, 4), (R, 40, 0, 0),
                         (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (B, -60, 0, 10), (I, -70, 0, 30, 'I'), (F, -68, 0, 30), (R, -36, 0, 12),
                       (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (I, 1.5, 1.5, 1.2, 'O'), (R, 1.1, 1.1, 1.0), (E, 1, 1, 1)])
    feet(c, x, right_plan=[(0.9, 1.15, (2.0, 1.0), 2.0), (2.0, 2.3, (0.0, 0.0), 2.0)])
    eranim.rest_on_floor(c, x.rig, x.v, (I, F), 'greatsword', axis='x', clearance=-1.5)
    return c


def phase_change(x):
    """The gate's roar: the sword planted, both fists clenched and the body rearing up as the core and the prongs
    blaze (impact), trembling."""
    E, I = 3.0, 1.8
    c = Clip('phase_change', E, impact=I)
    tr = shake(6.0, 1.0, 3)
    hold = lambda t: envelope(t, I, 2.5, 0.2)
    c.pos('base', [(0, 0, 0, 0), (0.3, 0, -0.6, 1.2, 'O'), (0.95, 0, -4.0, 0.6), (1.35, 0, -4.2, 0.5),
                   (1.78, 0, 1.2, -0.4, 'O'), (2.5, 0, 0.8, -0.3), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.3, -14, 0, 0, 'O'), (0.95, 26, 0, 0), (1.35, 30, 0, 0), (1.78, -24, 0, 0, 'O'),
                    (2.5, -20, 0, 0), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.8 * tr(t) * hold(t)), 48)
    c.rot('head', [(0, 0, 0, 0), (0.3, -16, 0, 0, 'O'), (0.95, 20, 0, 0), (1.78, -32, 0, 0, 'O'), (2.5, -28, 0, 0),
                   (E, 0, 0, 0)])
    for side, sz in (('right', 1), ('left', -1)):
        c.rot('%s_arm' % side, [(0, 0, 0, 0), (0.3, -26, 0, 20 * sz, 'O'), (0.95, -60, 0, -10 * sz),
                                (1.35, -62, 0, -12 * sz), (1.78, -60, 0, 80 * sz, 'O'), (2.5, -56, 0, 74 * sz),
                                (E, 0, 0, 0)])
        c.rot('%s_forearm' % side, [(0, 0, 0, 0), (0.95, -50, 0, 0), (1.78, -70, 0, 0, 'O'), (2.5, -66, 0, 0),
                                    (E, 0, 0, 0)])
        c.add('%s_arm' % side, 'rot', lambda t, sz=sz: (1.5 * tr(t + 0.1 * sz) * hold(t), 0.0, 0.0), 48)
    c.rot('greatsword', [(0, 0, 0, 0), (0.95, 40, 0, 0), (1.78, 30, 0, 10, 'O'), (E, 0, 0, 0)])
    c.scl('core', [(0, 1, 1, 1), (0.95, 0.7, 0.7, 0.9), (1.78, 1.9, 1.9, 1.4, 'O'), (2.5, 1.5, 1.5, 1.2),
                   (E, 1, 1, 1)])
    c.scl('prongs', [(0, 1, 1, 1), (0.95, 0.95, 0.95, 0.95), (1.78, 1.15, 1.2, 1.15, 'O'), (2.5, 1.1, 1.12, 1.1),
                     (E, 1, 1, 1)])
    feet(c, x, right_plan=[(0.3, 0.85, (6.0, 1.0), 2.5), (1.32, 1.6, (1.0, 1.0), 2.0), (2.55, 2.85, (0.0, 0.0), 1.5)],
         left_plan=[(0.25, 0.6, (-2.0, 1.0), 1.5), (1.35, 1.6, (-1.0, 1.5), 1.5), (2.55, 2.85, (0.0, 0.0), 1.5)])
    return c


def stagger(x):
    E = 1.4
    c = Clip('stagger', E, impact=0.1)
    tr = shake(5.0, 1.0, 5)
    c.pos('base', [(0, 0, 0, 0), (0.1, 0, -0.8, 2.6, 'O'), (0.4, 0, -1.2, 3.2), (0.75, 0, -0.5, 1.6), (E, 0, 0, 0)])
    c.rot('chest', [(0, 0, 0, 0), (0.1, -18, 6, 8, 'O'), (0.4, -8, -4, 3), (0.7, 6, 2, -2), (1.0, 2, 0, 0),
                    (E, 0, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.1, -22, 12, 6, 'O'), (0.4, -2, -8, 0), (0.7, 8, 4, 0), (E, 0, 0, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.1, -20, 0, 26, 'O'), (0.4, -10, 0, 12), (0.8, -2, 0, 2), (E, 0, 0, 0)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.1, -36, 0, -46, 'O'), (0.4, -16, 0, -24), (0.8, -4, 0, -6), (E, 0, 0, 0)])
    c.add('chest', 'rot', lambda t: (0.0, 0.0, 1.5 * tr(t) * envelope(t, 0.12, 0.6, 0.12)), 24)
    feet(c, x, right_plan=[(0.06, 0.34, (5.0, 1.0), 2.5), (0.8, 1.15, (0.0, 0.0), 2.0)],
         left_plan=[(0.3, 0.55, (2.5, 0.0), 1.5), (0.95, 1.25, (0.0, 0.0), 1.5)])
    return c


def death(x):
    """It plants the greatsword and sinks to its knees over it, holding the hilt, the core fading; then the hands
    slip and it falls forward past the blade (impact)."""
    E, I = 3.6, 1.8
    c = Clip('death', E, impact=I)
    tr = shake(5.0, 1.0, 7)
    c.pos('base', [(0, 0, 0, 0), (0.45, 0, -0.6, 1.2, 'O'), (1.1, 0, -7.6, 0.6, 'I'), (1.2, 0, -7.2, 0.6, 'O'),
                   (E, 0, -7.2, 0.6)])
    c.rot('hips', [(0, 0, 0, 0), (0.45, -4, 0, 0, 'O'), (1.1, 4, 0, 0), (1.35, 8, 0, 0), (I, 28, 0, 0, 'I'),
                   (1.95, 24, 0, 0, 'b'), (E, 26, 0, 0)])
    c.rot('torso', [(0, 0, 0, 0), (0.45, -14, 0, 4, 'O'), (1.1, 10, 0, -4), (1.35, 14, 0, -2), (I, 44, 0, 0, 'I'),
                    (1.95, 40, 0, 0, 'b'), (E, 42, 0, 0)])
    c.add('torso', 'rot', lambda t: (0.0, 0.0, 1.8 * tr(t) * envelope(t, 0.3, 1.0, 0.2)), 36)
    c.rot('chest', [(0, 0, 0, 0), (1.1, 6, 0, 0), (I, 16, 0, 0, 'I'), (E, 16, 0, 0)])
    c.rot('head', [(0, 0, 0, 0), (0.45, -22, 0, 0, 'O'), (1.1, 22, 0, 0), (I, -6, -28, 0, 'I'), (E, -6, -30, 0)])
    c.rot('right_arm', [(0, 0, 0, 0), (0.45, -40, 0, 10, 'O'), (1.1, -50, 0, 14), (1.35, -52, 0, 14),
                        (I, -110, 0, 30, 'I'), (1.95, -104, 0, 34, 'b'), (E, -106, 0, 32)])
    c.rot('left_arm', [(0, 0, 0, 0), (0.45, -30, 0, -20, 'O'), (1.1, -50, 0, 16), (1.35, -50, 0, 18),
                       (I, -70, 0, -50, 'I'), (1.95, -64, 0, -46, 'b'), (E, -66, 0, -48)])
    c.rot('greatsword', [(0, 0, 0, 0), (0.45, 40, 0, 0), (1.1, 60, 0, 0), (1.35, 62, 0, 0), (1.6, 30, 0, 50, 'I'),
                         (I, 20, 0, 86, 'I'), (1.95, 22, 0, 82, 'b'), (E, 22, 0, 84)])
    c.scl('core', [(0, 1, 1, 1), (0.45, 1.4, 1.4, 1.2, 'O'), (1.1, 0.7, 0.7, 0.9), (E, 0.5, 0.5, 0.8)])
    c.rot('cape', [(0, 0, 0, 0), (0.45, 14, 0, 0, 'O'), (1.1, 4, 0, 0), (1.35, 6, 0, 0), (I, -2, 0, 0, 'I'),
                   (2.0, 2, 0, 0, 'b'), (E, 0, 0, 0)])
    c.rot('cape_mid', [(0, 0, 0, 0), (0.45, 10, 0, 0), (1.1, 6, 0, 0), (I, -44, 0, 0, 'I'), (2.05, -38, 0, 0, 'b'),
                       (E, -40, 0, 0)])
    c.rot('cape_low', [(0, 0, 0, 0), (0.45, 8, 0, 0), (1.1, 4, 0, 0), (I, -42, 0, 6, 'I'), (2.1, -34, 0, 4, 'b'),
                       (E, -36, 0, 4)])
    feet(c, x, right_plan=[(0.5, 1.1, (6.6, 0.4), 1.5)], left_plan=[(0.55, 1.12, (6.4, 0.4), 1.5)],
         right_holds=[(1.1, E, 64.0)], left_holds=[(1.12, E, 64.0)])
    for t in (1.1, 1.2, 1.35, I, 1.95, E):
        eranim.ground(c, x.rig, x.v, t, skip=DANGLE + ('greatsword', 'shards'))
    eranim.rest_on_floor(c, x.rig, x.v, (I, 1.95, E), 'greatsword', axis='x')
    eranim.rest_on_floor(c, x.rig, x.v, (I, 2.1, E), 'cape_low', axis='x')
    return c


def animations(r):
    x = Ctx(r, r.variants[0], LEGS)
    return [build(fn(x), x, CHAINS) for fn in (idle, walk, melee_slam, melee_sweep, cast, roar, charge, summon,
                                                 phase_change, stagger, death)]


# --------------------------------------------------------------------------------------------- looks

def mosaic(fc, seed, cell=4, mix_light=0.3):
    """A face built of smaller blocks: cells of obsidian, deepslate and stone brick (lighter at the top of the body
    part), each with a dark joint and a lit upper edge, the way the sheet's knight is assembled."""
    pal = (OBSIDIAN, DEEPSLATE, DEEPSLATE, STONE, STONE_LIGHT)
    for cy in range(0, fc.fh, cell):
        for cx in range(0, fc.fw, cell):
            r = h(seed, cx, cy)
            light = mix_light * (1.0 - cy / max(1, fc.fh))
            idx = min(len(pal) - 1, int((r * 0.75 + light) * len(pal)))
            base = pal[idx]
            for y in range(cy, min(fc.fh, cy + cell)):
                for x in range(cx, min(fc.fw, cx + cell)):
                    n = 0.92 + 0.16 * h(seed, x, y)
                    col = tuple(min(255, int(v * n)) for v in base)
                    if y == cy:
                        col = lighten(col, 0.18)
                    if x == cx or y == cy + cell - 1:
                        col = shade(col, 0.6)
                    fc.fpx(x, y, col)


def gold_edges(fc):
    trim(fc, GOLD, rows=(0, -1), cols=(0, -1), shine=True)


def amethyst_line(fc, gfc, pts, glow):
    for (x0, y0, x1, y1) in pts:
        line(fc, x0, y0, x1, y1, lighten(AMETHYST, 0.2), gfc, glow)


def paint_body(p, glow, form):
    for part, tag in (('chest', 'chest'), ('torso', None), ('hips', None), ('right_arm', 'pt'), ('right_arm', 'pm'),
                      ('right_arm', 'pl'), ('right_arm', 'uarm'), ('right_forearm', 'gaunt'), ('right_hand', 'fist'),
                      ('right_leg', 'thigh'), ('right_shin', 'shin'), ('right_foot', 'boot'), ('head', 'helm'),
                      ('chest', None), ('right_arm', 'nb1'), ('right_arm', 'nb2'), ('right_arm', 'nb3'),
                      ('right_forearm', 'nb4'), ('right_leg', 'nb5')):
        index = 1 if (part == 'chest' and tag is None) else 0
        for side in ('front', 'right', 'back', 'left', 'top', 'bottom'):
            mosaic(p.face(part, side, tag=tag, index=index), (part, tag, side, form))
    for tag in ('pt', 'pm', 'pl'):
        for side in ('front', 'right', 'back', 'left'):
            fc = p.face('right_arm', side, tag=tag)
            trim(fc, GOLD, rows=(-1,), shine=True)
    f = p.face('head', 'front', tag='face')
    g = p.face('head', 'front', tag='face', glow=True)
    for y in range(f.fh):
        for x in range(f.fw):
            f.fpx(x, y, shade(OBSIDIAN, 0.8 + 0.1 * ((x + y) % 2)))
    for (x0, x1) in ((1, 5), (8, 12)):
        for x in range(x0, x1 + 1):
            f.fpx(x, 4, glow)
            f.fpx(x, 5, shade(glow, 0.8))
            g.fpx(x, 4, lighten(glow, 0.4))
            g.fpx(x, 5, glow)
        g.fpx((x0 + x1) // 2, 4, (255, 255, 255))
    for y in range(7, f.fh - 1, 2):
        f.fpx(f.fw // 2 - 1, y, shade(OBSIDIAN, 0.5))
        f.fpx(f.fw // 2, y, shade(OBSIDIAN, 0.5))
    fr = p.face('chest', 'front', index=3)
    gfr = p.face('chest', 'front', index=3, glow=True)
    for y in range(fr.fh):
        for x in range(fr.fw):
            fr.fpx(x, y, shade(OBSIDIAN, 0.9))
    gold_edges(fr)
    ch = p.face('chest', 'front', tag='chest')
    gch = p.face('chest', 'front', tag='chest', glow=True)
    amethyst_line(ch, gch, [(20, 6, 20, 2), (20, 24, 20, 30), (11, 15, 5, 15), (29, 15, 35, 15)], glow)
    if form >= 1:
        amethyst_line(ch, gch, [(5, 15, 3, 25), (35, 15, 37, 25), (20, 30, 16, 31), (20, 30, 24, 31)], glow)


def paint_sword(p, glow, form):
    for side in ('right', 'left'):
        b = p.face('greatsword', side, tag='blade')
        gb = p.face('greatsword', side, tag='blade', glow=True)
        for y in range(b.fh):
            for x in range(b.fw):
                d = abs(x - (b.fw - 1) / 2.0) / (b.fw / 2.0)
                col = mix(OBSIDIAN, PURPUR, min(1.0, max(0.0, (d - 0.35) * 2.2)))
                if d > 0.88:
                    col = lighten(PURPUR, 0.35)
                b.fpx(x, y, col)
        cx = b.fw // 2
        for y in range(2, b.fh - 1):
            wob = 1 if h('blade', y // 4) > 0.6 else (-1 if h('blade', y // 4) < 0.25 else 0)
            for dx in (0, 1):
                b.fpx(cx - 1 + dx + wob, y, AMETHYST)
                gb.fpx(cx - 1 + dx + wob, y, glow)
        crackle(b, ('blade', side, form), 2 + form, 6, lighten(AMETHYST, 0.2), gb, glow, branch=0.6)
        for t in ('tip1', 'tip2'):
            tf = p.face('greatsword', side, tag=t)
            for y in range(tf.fh):
                for x in range(tf.fw):
                    tf.fpx(x, y, mix(OBSIDIAN, PURPUR, 0.5))
            gt = p.face('greatsword', side, tag=t, glow=True)
            for y in range(tf.fh):
                gt.fpx(tf.fw // 2, y, glow)
                tf.fpx(tf.fw // 2, y, AMETHYST)
    for side in ('front', 'back', 'top', 'right', 'left'):
        gf = p.face('greatsword', side, tag='guard')
        trim(gf, lighten(GOLD, 0.25), rows=(0,), shine=True)


def paint_banner(p, tear=1.0):
    for part in ('banner', 'banner2'):
        for side in ('front', 'back'):
            f = p.face(part, side)
            ramp(f, CRIMSON, shade(CRIMSON, 0.8), keep=0.6)
            trim(f, GOLD, cols=(0, -1), shine=True)
    top = p.face('banner', 'front')
    trim(top, GOLD, rows=(0, 1), shine=True)
    rune = (226, 214, 220)
    f1, f2 = p.face('banner', 'front'), p.face('banner2', 'front')
    cx = f1.fw // 2
    # the pale tree-rune: a trunk with three pairs of branches and roots, across both segments
    for y in range(5, f1.fh):
        f1.fpx(cx, y, rune)
        f1.fpx(cx - 1, y, rune)
    for (dy, spread) in ((6, 3), (10, 4), (14, 5)):
        for k in range(1, spread + 1):
            f1.fpx(cx - 1 - k, dy - k // 2, rune)
            f1.fpx(cx + k, dy - k // 2, rune)
    for y in range(0, 7):
        f2.fpx(cx, y, rune)
        f2.fpx(cx - 1, y, rune)
    for k in range(1, 4):
        f2.fpx(cx - 1 - k, 6 + k // 2, rune)
        f2.fpx(cx + k, 6 + k // 2, rune)
    for side in ('front', 'back', 'right', 'left'):
        ragged(p.face('banner2', side), int(5 * tear), ('banner', side), min_depth=1)
    for part, top_c, bottom_c in (('cape', shade(CRIMSON, 0.8), shade(CRIMSON, 0.7)),
                                  ('cape_mid', shade(CRIMSON, 0.7), shade(CRIMSON, 0.6)),
                                  ('cape_low', shade(CRIMSON, 0.6), shade(CRIMSON, 0.5))):
        for side in ('back', 'right', 'left'):
            ramp(p.face(part, side), top_c, bottom_c, keep=0.6)
        ramp(p.face(part, 'front'), shade(top_c, 0.5), shade(bottom_c, 0.5), keep=0.5)
    for side in ('back', 'front', 'right', 'left'):
        ragged(p.face('cape_low', side), int(8 * tear), ('cape', side), min_depth=1)
    bot = p.face('cape_low', 'bottom')
    for y in range(bot.fh):
        for x in range(bot.fw):
            bot.fclear(x, y)


def variants():
    stone = {'base': '#55535c', 'style': 'plain'}
    gold = {'base': '#e2b04a', 'style': 'gold'}
    pal = {
        'helm': stone, 'face': stone, 'chest': stone, 'gorget': stone, 'core_frame': stone, 'waist': stone,
        'pelvis': stone, 'paul_top': stone, 'paul_mid': stone, 'paul_low': stone, 'uarm': stone, 'gauntlet': stone,
        'fist': stone, 'thigh': stone, 'shin': stone, 'boot': stone, 'nub': stone, 'nub2': stone, 'nub3': stone,
        'nub4': stone, 'nub5': stone,
        'circlet': gold, 'prong': gold, 'prong_tip': dict(gold, base='#f2c860'), 'prong2': gold,
        'prong3': dict(gold, base='#f2c860'), 'chest_band': gold, 'paul_rim': gold, 'paul_rim2': gold, 'gband': gold,
        'gband2': gold, 'knuckles': gold, 'belt': gold, 'buckle': gold, 'cuff': gold, 'kneeplate': gold,
        'kband': gold, 'pommel': gold, 'guard': gold, 'guard_end': gold,
        'grip': {'base': '#3a2a3a', 'style': 'leather'},
        'core_v': {'base': '#b46cff', 'style': 'gem'}, 'core_h': {'base': '#b46cff', 'style': 'gem'},
        'core_c': {'base': '#e0b8ff', 'style': 'glowcore'}, 'belt_gem': {'base': '#b46cff', 'style': 'gem'},
        'guard_gem': {'base': '#b46cff', 'style': 'gem'}, 'shard': {'base': '#a35cf0', 'style': 'crystal'},
        'shard2': {'base': '#8a48e0', 'style': 'crystal'},
        'blade': {'base': '#2a2234', 'style': 'plain'}, 'blade_tip': {'base': '#2a2234', 'style': 'plain'},
        'blade_tip2': {'base': '#2a2234', 'style': 'plain'},
        'banner': {'base': '#7a1e34', 'style': 'cloth'}, 'banner2': {'base': '#701c30', 'style': 'cloth'},
        'cape': {'base': '#5e1a2a', 'style': 'cloth'}, 'cape2': {'base': '#541826', 'style': 'cloth'},
        'cape3': {'base': '#4a1522', 'style': 'cloth'},
    }

    def deco(p, form=0):
        glow = GLOW if form < 3 else (220, 150, 255)
        paint_body(p, glow, form)
        paint_sword(p, glow, form)
        paint_banner(p, tear=1.0 + 0.25 * form)
        for idx in (0, 1):
            g = p.face('core', 'front', index=idx, glow=True)
            for y in range(g.fh):
                for x in range(g.fw):
                    g.fpx(x, y, glow)
        gc = p.face('core', 'front', index=2, glow=True)
        for y in range(gc.fh):
            for x in range(gc.fw):
                gc.fpx(x, y, (255, 240, 255))
        gm = p.face('hips', 'front', index=3, glow=True)
        for y in range(gm.fh):
            for x in range(gm.fw):
                gm.fpx(x, y, glow)
        if form >= 2:
            for part in ('prong_ri', 'prong_ro'):
                for side in ('front', 'right', 'back', 'left'):
                    g = p.face(part, side, index=1, glow=True)
                    for y in range(g.fh):
                        for x in range(g.fw):
                            g.fpx(x, y, glow)
            for i in range(7):
                for side in ('front', 'right', 'back', 'left', 'top'):
                    g = p.face('shards', side, index=i, glow=True)
                    for y in range(g.fh):
                        for x in range(g.fw):
                            if (x + y) % 2 == 0:
                                g.fpx(x, y, glow)
        if form >= 3:
            for part, tag in (('chest', 'chest'), ('right_arm', 'pt'), ('right_leg', 'thigh'),
                              ('right_forearm', 'gaunt'), ('torso', None)):
                for side in ('front', 'right', 'back', 'left'):
                    crackle(p.face(part, side, tag=tag), (part, side, 'f3'), 2, 9, lighten(AMETHYST, 0.2),
                            p.face(part, side, tag=tag, glow=True), glow)
    return [Variant('b8', palette=pal, paint=deco, glow=True, extra=dict(scale=2.6, hitbox=(3.8, 8.0))),
            Variant('b8_p1', palette=pal, paint=lambda p: deco(p, 1), glow=True, extra=dict(scale=2.6)),
            Variant('b8_p2', show={'shards'}, palette=pal, paint=lambda p: deco(p, 2), glow=True,
                    extra=dict(scale=2.6)),
            Variant('b8_p3', show={'shards'}, palette=pal, poses={'shards': dict(scale=(1.2, 1.2, 1.2)),
                                                                  'prongs': dict(scale=(1.1, 1.15, 1.1))},
                    paint=lambda p: deco(p, 3), glow=True, extra=dict(scale=2.6))]


TRAILS = [('greatsword', BLADE_MID, BLADE_TIP)]


def rig():
    vs = variants()
    base = Rig('gatekeeper', 'GatekeeperBoss', geometry(), [], vs, SLOTS, texel=2)
    r = Rig('gatekeeper', 'GatekeeperBoss', geometry(), animations(base), vs, SLOTS, texel=2)
    r.preview_ppu = 2.8
    return finish_rig(r, GAIT.walk_speed(), TRAILS)
