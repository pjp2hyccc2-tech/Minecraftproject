"""The Azurewing Griffin, rebuilt after the owner's picture (2026-11-11): an armoured eagle-headed mount on huge
layered wings.

What the picture shows and where it is here:
  * wings: a thick gold armoured leading edge in riveted segments with a gem at the elbow and the wrist; under it a
    row of dark navy coverts, a row of pale coverts, then long flight feathers in alternating pale and navy, the
    secondaries hanging back from the arm, the primaries fanned from the hand like fingers, the outer ones gold-tipped;
  * head: white plumage, a gold brow band over glowing blue eyes in a navy mask, a gold crown with a big blue gem and
    three gold horns, a big hooked gold beak, and a crest of long pale feathers flaring back from head and neck;
  * body: navy plumage, a leather harness with gold fittings (a breast strap with a big diamond gem and one on each
    shoulder, a girth with gold buckles), a brown saddle with a high cantle, navy banners with a gold fleur cross and
    gold edges on both flanks, a small pennant under the breast, a lantern on the right;
  * legs: pale feathered thighs, gold scaled armour on the shins and toes, long black talons.

Conventions as in ermodel: absolute rest-pose coordinates, +Y down, ground at y=24, front -Z, right side -X. The bones
the Java model drives keep their names (base, body, neck, head, crest_*, wing_r/l, wing_r2/l2, prim_r*/l*, tail,
tail2, legs, saddle, banners, lantern); MountModel's wing shapes use prim_*, wing_*2, crest_* and tail2.
"""
import math

from ermodel import C, P, Rig, Variant, anim
from erpaint import mix, rgb, shade
import rig_mounts as base

GRIFFIN_SLOTS = ['idle', 'walk', 'fold', 'flap', 'glide']
GOLD, GOLD_DARK, GOLD_LIGHT = base.GOLD, base.GOLD_DARK, base.GOLD_LIGHT
GEM, GEM_GLOW, EYE_GLOW = base.GEM, base.GEM_GLOW, base.EYE_GLOW
AMBER, AMBER_GLOW = base.AMBER, base.AMBER_GLOW

# primaries (fan angle outward, length) and secondaries (length) of each wing
PRIMARIES = ((2, 18), (8, 20), (15, 22), (23, 24), (32, 25), (42, 26), (53, 26), (65, 24), (78, 21))
SECONDARIES = (14, 15, 15, 16, 16, 16, 15, 15)
# the crest and the hackles: rings of long pale feathers around the head and the neck, all pointing back and lying
# flat; each ring (radius, width, length, pitch, rolls around the head in degrees: 0 on top, 90 at the side)
CREST_CENTER = (-10.5, -12.0)   # (y, z) of the head's axis where the crest grows
CREST = ((4.0, 3, 10, 16, (0, 34, 68, 100, 126)),
         (3.0, 3, 8, 6, (17, 51, 84)))
HACKLE_CENTER = (-3.5, -11.0)   # (y, z) on the neck's axis
HACKLES = ((4.0, 3, 10, 14, (0, 40, 80, 114)),
           (3.0, 3, 7, 4, (20, 60, 97)))


def X(s, a, w):
    """Box x for a box spanning |x| from a to a + w on side s (-1 right, +1 left)."""
    return -(a + w) if s < 0 else a


# ================================================================================================ geometry

def wing(side):
    """The arm with three rows of coverts and the secondaries, the hand with two rows and the fanned primaries;
    a thick riveted gold edge in front with plates and gems. Every feather is its own part (they bend in flight)."""
    s = -1 if side == 'r' else 1
    mirror = side == 'l'

    def tg(t):
        return t if side == 'r' else None

    def sh(t):
        return t if side == 'l' else None

    def cube(x, y, z, w, h, d, mat, t):
        return C(x, y, z, w, h, d, mat, tag=tg(t), share=sh(t), mirror=mirror)

    secondaries = []
    for i, length in enumerate(SECONDARIES):
        cx = 8.0 + 2.0 * i
        lift = 0.18 * (i % 2)
        mat = 'feather:flight_w' if i % 2 == 0 else 'feather:flight_v'
        secondaries.append(P('sec_%s%d' % (side, i + 1), (s * cx, -0.4 - lift, 9.0),
                             cube(X(s, cx - 2.0, 4), -0.4 - lift, 9.0, 4, 1, length, mat, 'sec%d' % i),
                             rot=(2 + i * 0.5, s * (4 - i * 1.2), 0)))
    primaries = []
    for i, (fan, length) in enumerate(PRIMARIES):
        cx = 23.0 + 1.7 * i
        lift = 0.15 * (i % 2)
        mat = 'feather:flight_g' if i >= 7 else ('feather:flight_w' if i % 2 == 0 else 'feather:flight_v')
        primaries.append(P('prim_%s%d' % (side, i + 1), (s * cx, -0.5 - lift, 1.0),
                           cube(X(s, cx - 2.0, 4), -0.5 - lift, 1.0, 4, 1, length, mat, 'pf%d' % i),
                           rot=(2, s * fan, 0)))
    hand = P('wing_%s2' % side, (s * 22.0, -1.0, -7.5),
             cube(X(s, 22, 15), -2.5, -10.0, 15, 3, 3, 'gold:edge', 'edge2'),
             cube(X(s, 23, 3), -3.5, -10.5, 3, 1, 4, 'gold:plate', 'plate3'),
             cube(X(s, 28, 3), -3.5, -10.5, 3, 1, 4, 'gold:plate', 'plate4'),
             cube(X(s, 33, 3), -3.5, -10.5, 3, 1, 4, 'gold:plate', 'plate5'),
             cube(X(s, 21, 4), -4.0, -11.0, 4, 4, 4, 'gold:wrist', 'wrist'),
             cube(X(s, 22, 2), -5.0, -10.0, 2, 1, 2, 'gem:gem', 'wgem2'),
             cube(X(s, 22, 2), -3.0, -11.5, 2, 2, 1, 'gem:gem', 'wgem3'),
             cube(X(s, 22, 15), -1.6, -7.0, 15, 1, 5, 'feather:covert_n', 'hcov1'),
             cube(X(s, 22, 15), -1.3, -3.0, 15, 1, 5, 'feather:covert_w', 'hcov2'),
             cube(X(s, 22, 15), -1.0, 1.0, 15, 1, 4, 'feather:covert_n', 'hcov3'),
             *primaries)
    return P('wing_%s' % side, (s * 6.0, -1.0, -6.0),
             cube(X(s, 6, 16), -2.5, -9.0, 16, 3, 3, 'gold:edge', 'edge1'),
             cube(X(s, 8, 3), -3.5, -9.5, 3, 1, 4, 'gold:plate', 'plate1'),
             cube(X(s, 16, 3), -3.5, -9.5, 3, 1, 4, 'gold:plate', 'plate2'),
             cube(X(s, 12, 2), -4.0, -8.5, 2, 1, 2, 'gem:gem', 'wgem1'),
             cube(X(s, 6, 16), -1.6, -6.0, 16, 1, 5, 'feather:covert_n', 'cov1'),
             cube(X(s, 6, 16), -1.3, -2.0, 16, 1, 5, 'feather:covert_w', 'cov2'),
             cube(X(s, 6, 16), -1.0, 2.0, 16, 1, 5, 'feather:covert_n', 'cov3'),
             cube(X(s, 6, 16), -0.7, 6.0, 16, 1, 5, 'feather:covert_w', 'cov4'),
             *secondaries, hand)


def leg(name, x, z, hind, side):
    """An eagle's leg: a pale feathered thigh, a feather cuff, a tarsus in gold scale armour, a gold-plated foot with
    three long toes forward and one back, each toe in two segments ending in a hooked black talon. The hind legs are
    the same on a heavier navy haunch."""
    mirror = side == 'l'
    pre = name.split('_leg')[0] + '_'

    def tg(t):
        return (('h' if hind else 'f') + t) if side == 'r' else None

    def sh(t):
        return (('h' if hind else 'f') + t) if side == 'l' else None

    def cube(cx, y, cz, w, h, d, mat, t):
        return C(cx, y, cz, w, h, d, mat, tag=tg(t), share=sh(t), mirror=mirror)

    toes = []
    for i, dx in enumerate((-1.5, 0.0, 1.5)):
        tx = x + dx
        toes.append(P(pre + 'toe%d' % (i + 1), (tx, 21.0, z - 1.5),
                      cube(tx - 1.0, 20.5, z - 4.5, 2, 2, 3, 'gold:toe', 'toe'),
                      cube(tx - 0.75, 21.0, z - 6.5, 2, 2, 2, 'gold:toe', 'toe2'),
                      cube(tx - 0.5, 21.5, z - 8.0, 1, 1, 2, 'plain:claw', 'claw'),
                      cube(tx - 0.5, 22.5, z - 8.5, 1, 1, 1, 'plain:claw', 'claw_tip'),
                      rot=(-8, -dx * 9.0, 0)))
    toes.append(P(pre + 'toe4', (x, 21.5, z + 1.0),
                  cube(x - 0.75, 21.0, z + 0.5, 2, 2, 3, 'gold:toe', 'toeb'),
                  cube(x - 0.5, 22.0, z + 3.5, 1, 1, 2, 'plain:claw', 'clawb'),
                  cube(x - 0.5, 23.0, z + 4.5, 1, 1, 1, 'plain:claw', 'clawb_tip')))
    tarsus = (cube(x - 2.5, 11.5, z - 2.5, 5, 3, 5, 'feather:cuff', 'cuff'),
              cube(x - 1.5, 13.0, z - 1.5, 3, 7, 3, 'gold:greave', 's'),
              cube(x - 2.0, 14.0, z - 2.0, 4, 2, 4, 'gold:greave', 'band1'),
              cube(x - 2.0, 17.0, z - 2.0, 4, 2, 4, 'gold:greave', 'band2'),
              cube(x - 2.0, 19.5, z - 2.5, 4, 2, 4, 'gold:foot', 'f'))
    if hind:
        shin = P(name.replace('_leg', '_shin'), (x, 12.0, z), *tarsus, *toes)
        return P(name, (x, 5.0, z), cube(x - 3.0, 2.0, z - 3.5, 6, 10, 7, 'feather:haunch', 'u'), shin)
    shin = P(name.replace('_leg', '_shin'), (x, 12.0, z), *tarsus, *toes)
    return P(name, (x, 6.0, z), cube(x - 3.0, 4.0, z - 3.0, 6, 9, 6, 'feather:thigh', 'u'), shin)


def ring(prefix, center, spec):
    """Rings of feathers around an axis: each feather starts on the surface ({@code radius} out, rolled around the
    axis), points back and lies down by its pitch; side feathers stand on edge against the head or neck."""
    cy, cz = center
    parts = []
    n = 0
    for radius, width, length, pitch, rolls in spec:
        for roll in rolls:
            for side in ((1,) if roll == 0 else (-1, 1)):
                n += 1
                # no two alike: a little longer or shorter, flaring a little more or less, turned a little
                ln = length + (n * 7) % 5 - 2
                flare = pitch + (n * 5) % 9 - 4
                turn = ((n * 3) % 7 - 3) * 2
                parts.append(P('%s_%d' % (prefix, n), (0.0, cy, cz),
                               C(-width / 2.0, cy - radius - 0.5, cz, width, 1, ln, 'feather:crest'),
                               rot=(flare, turn, roll * side)))
    return parts


def crest():
    return ring('crest', CREST_CENTER, CREST)


def hackles():
    return ring('hackle', HACKLE_CENTER, HACKLES)


def geometry():
    head = P('head', (0, -8, -11),
             C(-4, -14, -17, 8, 7, 8, 'feather:head', tag='skull'),
             C(-4.5, -11, -16, 9, 4, 6, 'feather:cheek', tag='cheek'),
             C(-4.5, -14.5, -17.5, 9, 2, 6, 'gold:crown', tag='crown'),
             C(-2.5, -17.5, -17.8, 5, 4, 1, 'gold:crown', tag='crown_front'),
             C(-1.5, -16.5, -18.4, 3, 3, 1, 'gem:gem', tag='crown_gem'),
             P('horn_c', (0, -16, -14), C(-1, -22, -14.5, 2, 6, 2, 'gold:horn', tag='horn'), rot=(-30, 0, 0)),
             P('horn_r', (-3, -15.5, -13), C(-3.5, -20.5, -13.5, 1, 5, 2, 'gold:horn', tag='hornr'), rot=(-36, 0, -22)),
             P('horn_l', (3, -15.5, -13), C(2.5, -20.5, -13.5, 1, 5, 2, 'gold:horn', share='hornr', mirror=True),
               rot=(-36, 0, 22)),
             C(-2, -13, -21, 4, 4, 4, 'gold:beak', tag='beak'),
             C(-1.5, -12.5, -23, 3, 3, 2, 'gold:beak', tag='beak2'),
             C(-1, -11.5, -24, 2, 3, 1, 'gold:beak_tip', tag='hook'),
             C(-1, -9, -23.5, 2, 1, 1, 'gold:beak_tip', tag='hook2'),
             C(-1.5, -9.5, -21, 3, 1, 3, 'gold:beak_low', tag='jaw'),
             C(-2.5, -13.5, -18, 5, 2, 2, 'gold:cere', tag='cere'),
             *crest())
    neck = P('neck', (0, 0, -10),
             C(-3.5, -9, -14, 7, 11, 7, 'feather:neck'),
             C(-4.5, -3, -14.5, 9, 6, 8, 'feather:ruff', tag='ruff'),
             head, *hackles(), rot=(22, 0, 0))
    tail = P('tail', (0, 1, 11),
             C(-4, 0.5, 11, 8, 1, 8, 'feather:tail'),
             P('tail2', (0, 1, 18.5),
               C(-5.5, 0.6, 18.5, 2, 1, 8, 'feather:flight_w', tag='tf1'),
               C(-3, 0.4, 18.5, 2, 1, 9, 'feather:flight_v', tag='tf2'),
               C(-1, 0.6, 18.5, 2, 1, 10, 'feather:flight_w', tag='tf3'),
               C(1, 0.4, 18.5, 2, 1, 9, 'feather:flight_v', share='tf2', mirror=True),
               C(3.5, 0.6, 18.5, 2, 1, 8, 'feather:flight_w', share='tf1', mirror=True)),
             rot=(-12, 0, 0))
    saddle = P('saddle', (0, -2, 0),
               C(-4.5, -3, -4, 9, 2, 9, 'leather:saddle'),
               C(-3, -5, -5, 6, 2, 2, 'leather:saddle_rim', tag='pommel'),
               C(-4, -7, 4, 8, 4, 2, 'leather:saddle_rim', tag='cantle'),
               C(-6, -2, -3, 1, 5, 7, 'leather:saddle', tag='flap'),
               C(5, -2, -3, 1, 5, 7, 'leather:saddle', share='flap', mirror=True))
    body = P('body', (0, 6, 0),
             C(-6, -1, -10, 12, 11, 20, 'feather:body'),
             C(-6.5, -2, -13, 13, 11, 4, 'feather:breast', tag='breast'),
             C(-5.5, -2, 9, 11, 9, 4, 'feather:body', tag='rump'),
             C(-5, 9, -8, 10, 2, 16, 'feather:belly', tag='belly'),
             # the harness: a breast strap with a big diamond gem, shoulder gems, a girth with buckles
             C(-7, 0.5, -13.6, 14, 2, 1, 'leather:strap', tag='strap_front'),
             C(-7, 0.5, -13, 1, 2, 9, 'leather:strap', tag='strap_side'),
             C(6, 0.5, -13, 1, 2, 9, 'leather:strap', share='strap_side', mirror=True),
             C(-2.5, -0.5, -14.4, 5, 5, 1, 'gold:setting', tag='gem_frame'),
             C(-1.5, 0.5, -14.9, 3, 3, 1, 'gem:gem', tag='chest_gem'),
             C(-7.6, -0.5, -11.5, 1, 4, 4, 'gold:setting', tag='shoulder_frame'),
             C(6.6, -0.5, -11.5, 1, 4, 4, 'gold:setting', share='shoulder_frame', mirror=True),
             C(-8.1, 0.5, -10.5, 1, 2, 2, 'gem:gem', tag='shoulder_gem'),
             C(7.1, 0.5, -10.5, 1, 2, 2, 'gem:gem', share='shoulder_gem', mirror=True),
             C(-6.5, -1.5, -2, 13, 12, 2, 'leather:strap', tag='girth'),
             C(-7, 4, -2.5, 1, 3, 3, 'gold:buckle', tag='buckle'),
             C(6, 4, -2.5, 1, 3, 3, 'gold:buckle', share='buckle', mirror=True),
             saddle,
             P('banner_r', (-6.5, 2, 2), C(-7, 2, -0.5, 1, 13, 6, 'cloth:banner', tag='banner'),
               C(-7.4, 1.4, -1, 1, 1, 7, 'gold:rod', tag='rod')),
             P('banner_l', (6.5, 2, 2), C(6, 2, -0.5, 1, 13, 6, 'cloth:banner', share='banner', mirror=True),
               C(6.4, 1.4, -1, 1, 1, 7, 'gold:rod', share='rod', mirror=True)),
             P('pennant', (0, 3, -14.5), C(-2, 4, -15, 4, 8, 1, 'cloth:pennant', tag='pennant')),
             P('lantern', (-7.5, 2, -6), C(-8, 2, -6.5, 1, 3, 1, 'chain:chain'),
               C(-9, 5, -7.5, 3, 4, 3, 'gold:lantern'), C(-9.5, 4.5, -8, 4, 1, 4, 'gold:lantern_cap'),
               C(-8.5, 9, -7, 2, 1, 2, 'gold:lantern_cap', tag='lantern_foot')),
             neck, tail, wing('r'), wing('l'),
             leg('front_right_leg', -3.5, -6, False, 'r'),
             leg('front_left_leg', 3.5, -6, False, 'l'),
             leg('hind_right_leg', -3.5, 7, True, 'r'),
             leg('hind_left_leg', 3.5, 7, True, 'l'))
    return [P('base', (0, 24, 0), body)]


# ================================================================================================ animations

def count(spec):
    """Parts made from a crest or hackle table: one for a feather on top (roll 0), two for a side one."""
    return sum(1 if roll == 0 else 2 for ring_spec in spec for roll in ring_spec[4])


def animations(primaries=PRIMARIES, secondaries=SECONDARIES, crest_n=None, hackle_n=None, absent=(), extra=None,
               more=()):
    """Idle, walk, fold, flap and glide. Breeds (rig_griffin_breeds.py) pass their own feathers, crest and hackle
    counts, the bones they do not have ({@code absent}: banners, pennant, lantern), {@code extra} channels per
    animation name for their own parts and {@code more} animations of their own."""
    PRIMARIES, SECONDARIES = primaries, secondaries  # noqa: N806 (the tables below read these names)
    extra = extra or {}

    def keep(name, bones):
        out = {b: v for b, v in bones.items() if b not in absent}
        out.update(extra.get(name, {}))
        return out

    A = []
    crest_sway = {}
    for i in range(1, (count(CREST) if crest_n is None else crest_n) + 1):
        crest_sway['crest_%d' % i] = dict(rot=[(0, 0, 0, 0), (1.5, 3 + (i % 3), 0, 0), (3.0, 0, 0, 0)])
    for i in range(1, (count(HACKLES) if hackle_n is None else hackle_n) + 1):
        crest_sway['hackle_%d' % i] = dict(rot=[(0, 0, 0, 0), (1.5, 2 + (i % 2), 0, 0), (3.0, 0, 0, 0)])
    A.append(anim('idle', 3.0, loop=True, **keep('idle', dict(
                  body=dict(scale=[(0, 1, 1, 1), (1.5, 1.015, 1.025, 1.0), (3.0, 1, 1, 1)]),
                  head=dict(rot=[(0, 0, 0, 0), (0.6, 4, 14, 0), (1.2, 0, 14, 0), (1.5, -3, -8, 0), (2.4, 0, -8, 0),
                                 (3.0, 0, 0, 0)]),
                  tail=dict(rot=[(0, 0, 5, 0), (1.5, 0, -5, 0), (3.0, 0, 5, 0)]),
                  banner_r=dict(rot=[(0, 0, 0, 2), (1.5, 0, 0, -2), (3.0, 0, 0, 2)]),
                  banner_l=dict(rot=[(0, 0, 0, -2), (1.5, 0, 0, 2), (3.0, 0, 0, -2)]),
                  pennant=dict(rot=[(0, 3, 0, 0), (1.5, -3, 0, 0), (3.0, 3, 0, 0)]),
                  lantern=dict(rot=[(0, 3, 0, 4), (1.5, -3, 0, -4), (3.0, 3, 0, 4)]),
                  **crest_sway))))
    w = {}
    for leg_name, off in (('front_right', 0.0), ('hind_left', 0.0), ('front_left', 0.5), ('hind_right', 0.5)):
        keys, shin = [], []
        for i in range(5):
            t = i / 4.0
            ph = 2 * math.pi * (i / 4.0 + off)
            keys.append((t, 22 * math.sin(ph), 0, 0))
            shin.append((t, 18 * max(0.0, math.cos(ph)), 0, 0))
        w[leg_name + '_leg'] = dict(rot=keys)
        w[leg_name + '_shin'] = dict(rot=shin)
    A.append(anim('walk', 1.0, loop=True, **keep('walk', dict(
                  body=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0.3, 0), (0.5, 0, 0, 0), (0.75, 0, 0.3, 0), (1.0, 0, 0, 0)]),
                  neck=dict(rot=[(0, 4, 0, 0), (0.25, -3, 0, 0), (0.5, 4, 0, 0), (0.75, -3, 0, 0), (1.0, 4, 0, 0)]),
                  **w))))
    # folded: each wing turns back along the flank, rolled so its gold edge runs along the top and its feathers
    # hang behind; the chord closes up, the hand folds short, and every flight feather turns to lie along the bones
    folded = {}
    for side, s in (('r', -1), ('l', 1)):
        folded['wing_' + side] = dict(rot=[(0, -70, -s * 80, s * 14)], pos=[(0, 0, -5, 0)],
                                      scale=[(0, 1.0, 1.0, 0.55)])
        folded['wing_%s2' % side] = dict(scale=[(0, 0.45, 1.0, 1.0)])
        for i, (fan, _) in enumerate(PRIMARIES):
            folded['prim_%s%d' % (side, i + 1)] = dict(rot=[(0, 0, s * (90 - fan), 0)],
                                                        scale=[(0, 1.0, 1.0, 0.45)])
        for i in range(len(SECONDARIES)):
            folded['sec_%s%d' % (side, i + 1)] = dict(rot=[(0, 0, s * (90 - (4 - i * 1.2)), 0)],
                                                       scale=[(0, 1.0, 1.0, 0.5)])
    A.append(anim('fold', 1.0, loop=True, **keep('fold', {b: {ch: keys + [(1.0,) + keys[0][1:]]
                                                              for ch, keys in v.items()}
                                                          for b, v in folded.items()})))
    tuck = dict(front_right_leg=dict(rot=[(0, 55, 0, 0)]), front_left_leg=dict(rot=[(0, 55, 0, 0)]),
                front_right_shin=dict(rot=[(0, -70, 0, 0)]), front_left_shin=dict(rot=[(0, -70, 0, 0)]),
                hind_right_leg=dict(rot=[(0, 60, 0, 0)]), hind_left_leg=dict(rot=[(0, 60, 0, 0)]),
                hind_right_shin=dict(rot=[(0, 30, 0, 0)]), hind_left_shin=dict(rot=[(0, 30, 0, 0)]))

    def held(spec, length):
        return {b: dict(rot=v['rot'] + [(length,) + v['rot'][0][1:]]) for b, v in spec.items()}

    L = 0.7
    flap = dict(
        wing_r=dict(rot=[(0, 0, 18, 42), (L * 0.5, 0, -8, -34), (L, 0, 18, 42)]),
        wing_l=dict(rot=[(0, 0, -18, -42), (L * 0.5, 0, 8, 34), (L, 0, -18, -42)]),
        wing_r2=dict(rot=[(0, 0, 0, -14), (L * 0.25, 0, 0, 10), (L * 0.5, 0, 0, -10), (L * 0.75, 0, 0, -26),
                          (L, 0, 0, -14)]),
        wing_l2=dict(rot=[(0, 0, 0, 14), (L * 0.25, 0, 0, -10), (L * 0.5, 0, 0, 10), (L * 0.75, 0, 0, 26),
                          (L, 0, 0, 14)]),
        body=dict(pos=[(0, 0, -0.8, 0), (L * 0.5, 0, 0.9, 0), (L, 0, -0.8, 0)]),
        tail=dict(rot=[(0, -6, 0, 0), (L * 0.5, 6, 0, 0), (L, -6, 0, 0)]),
        banner_r=dict(rot=[(0, 30, 0, 0), (L * 0.5, 38, 0, 0), (L, 30, 0, 0)]),
        banner_l=dict(rot=[(0, 30, 0, 0), (L * 0.5, 38, 0, 0), (L, 30, 0, 0)]),
        pennant=dict(rot=[(0, 40, 0, 0), (L * 0.5, 50, 0, 0), (L, 40, 0, 0)]))
    # the flight feathers bend with the beat: up on the down-stroke, trailing on the up-stroke
    for i in range(len(PRIMARIES)):
        bend = 4 + i
        flap['prim_r%d' % (i + 1)] = dict(rot=[(0, -bend, 0, 0), (L * 0.5, bend, 0, 0), (L, -bend, 0, 0)])
        flap['prim_l%d' % (i + 1)] = dict(rot=[(0, -bend, 0, 0), (L * 0.5, bend, 0, 0), (L, -bend, 0, 0)])
    A.append(anim('flap', L, loop=True, **keep('flap', dict(**flap, **held(tuck, L)))))
    G = 2.4
    glide = dict(
        wing_r=dict(rot=[(0, 0, 0, 8), (G * 0.5, 0, 0, 12), (G, 0, 0, 8)]),
        wing_l=dict(rot=[(0, 0, 0, -8), (G * 0.5, 0, 0, -12), (G, 0, 0, -8)]),
        wing_r2=dict(rot=[(0, 0, 0, -4), (G * 0.5, 0, 0, -8), (G, 0, 0, -4)]),
        wing_l2=dict(rot=[(0, 0, 0, 4), (G * 0.5, 0, 0, 8), (G, 0, 0, 4)]),
        tail=dict(rot=[(0, 0, 4, 0), (G * 0.5, 0, -4, 0), (G, 0, 4, 0)]),
        banner_r=dict(rot=[(0, 40, 0, 0), (G * 0.5, 46, 0, 0), (G, 40, 0, 0)]),
        banner_l=dict(rot=[(0, 40, 0, 0), (G * 0.5, 46, 0, 0), (G, 40, 0, 0)]),
        pennant=dict(rot=[(0, 55, 0, 0), (G * 0.5, 60, 0, 0), (G, 55, 0, 0)]))
    for i in range(len(PRIMARIES)):
        flutter = 2 + (i % 3)
        glide['prim_r%d' % (i + 1)] = dict(rot=[(0, 0, 0, 0), (G * 0.5, flutter, 0, 0), (G, 0, 0, 0)])
        glide['prim_l%d' % (i + 1)] = dict(rot=[(0, 0, 0, 0), (G * 0.5, flutter, 0, 0), (G, 0, 0, 0)])
    A.append(anim('glide', G, loop=True, **keep('glide', dict(**glide, **held(tuck, G)))))
    A.extend(more)
    return A


# ================================================================================================ paint

def long_feather(fc, color, edge, tip=None, glow_fc=None, glow=None):
    """A flight feather seen from above or below (fine y 0 is its trailing end): blocky barbs in short bands that
    darken toward the covered base, a dark shaft, a lit leading edge, a stair-stepped point; the outer primaries
    get a gold-plated end."""
    w, h = fc.fw, fc.fh
    light = mix(color, (255, 255, 255), 0.22)
    for y in range(h):
        t = y / max(1, h - 1)              # 0 at the tip, 1 at the covered base
        band = (y // 4) % 2
        base = mix(color, edge, 0.45 * t)
        for x in range(w):
            c = base
            if band:
                c = shade(c, 0.94)
            if x == w // 2 or (w > 6 and x == w // 2 - 1 and y % 3 == 0):
                c = shade(base, 0.7)
            elif x == 0:
                c = mix(c, light, 0.6)
            elif x == w - 1:
                c = shade(c, 0.82)
            if (x * 3 + y * 5) % 11 == 0:
                c = mix(c, light, 0.35)
            fc.fpx(x, y, c)
        if y % 4 == 3:
            for x in range(w):
                if x != w // 2:
                    fc.fpx(x, y, shade(fc.fget(x, y), 0.86))
    if tip is not None:
        span = max(3, h // 4)
        for y in range(span):
            for x in range(w):
                c = mix(GOLD_LIGHT, GOLD, y / span) if x != w // 2 else GOLD_DARK
                if y == span - 1:
                    c = GOLD_DARK
                fc.fpx(x, y, c)
            if glow_fc is not None:
                for x in range(w):
                    glow_fc.fpx(x, y, glow)
    # the point, in steps of whole pixels
    steps = w // 2
    for k in range(steps):
        for x in range(w):
            if x < steps - k or x >= w - (steps - k):
                fc.fclear(x, k)


def covert_row(fc, color, dark, notch=True):
    """A row of short overlapping coverts (fine y 0 is the back edge): each covert a 5-pixel shingle with its own
    shade, a shaft, a lit edge, a stepped tip; the covered front darker."""
    w, h = fc.fw, fc.fh
    light = mix(color, (255, 255, 255), 0.2)
    for x in range(w):
        cell, pos = divmod(x, 5)
        tone = (0.92, 1.0, 0.96, 1.04)[cell % 4]
        for y in range(h):
            t = y / max(1, h - 1)
            c = shade(mix(color, dark, 0.5 * t), tone)
            if pos == 2:
                c = shade(c, 0.8)
            elif pos == 0:
                c = mix(c, light, 0.4)
            elif pos == 4:
                c = shade(c, 0.85)
            fc.fpx(x, y, c)
    if notch:
        for x in range(w):
            pos = x % 5
            depth = 2 if pos in (0, 4) else 1 if pos in (1, 3) else 0
            for k in range(depth):
                fc.fclear(x, k)


def riveted(fc, color, step=6):
    """Gold armour in segments: a darker seam every {@code step} fine pixels with a rivet either side, a lit top edge."""
    w, h = fc.fw, fc.fh
    for x in range(0, w, step):
        for y in range(h):
            fc.fpx(x, y, GOLD_DARK)
        if h >= 4:
            for rx in (x + 2, x + step - 2):
                if 0 <= rx < w:
                    fc.fpx(rx, 1, GOLD_LIGHT)
                    fc.fpx(rx, h - 2, GOLD_LIGHT)
    for x in range(w):
        fc.fpx(x, 0, mix(color, GOLD_LIGHT, 0.6))


def fleur(fc, cx, cy, color, edge):
    """Our fleur cross: a cross whose arms end in three petals (fine pixels)."""
    pts = set()
    for d in range(-5, 6):
        pts.add((cx, cy + d))
    for d in range(-3, 4):
        pts.add((cx + d, cy - 1))
    for ax, ay in ((0, -6), (0, 6), (-4, -1), (4, -1)):
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1 if ay < 0 else 1)):
            pts.add((cx + ax + dx, cy + ay + dy))
    for (x, y) in pts:
        fc.fpx(x, y, color)
    for (x, y) in pts:
        for dx, dy in ((1, 1),):
            if (x + dx, y + dy) not in pts:
                fc.fpx(x + dx, y + dy, edge)


def paint(p):
    k = p.k
    wing_spec = p.variant.extra['wing']
    white = rgb(wing_spec['white'])
    pale = rgb(wing_spec['pale'])
    accent = rgb(wing_spec['accent'])
    navy = rgb(wing_spec['navy'])
    glow_wings = p.variant.extra.get('glow_wings', False)
    accent_glow = tuple(int(v * 0.85) for v in accent)
    # ---------------------------------------------------------------- the wings
    for side in ('r',):
        for part, tags, color, dark in (('wing_' + side, ('cov1', 'cov3'), navy, shade(navy, 0.6)),
                                        ('wing_' + side, ('cov2', 'cov4'), white, pale),
                                        ('wing_%s2' % side, ('hcov1', 'hcov3'), navy, shade(navy, 0.6)),
                                        ('wing_%s2' % side, ('hcov2',), white, pale)):
            for tag in tags:
                for name in ('top', 'bottom'):
                    covert_row(p.face(part, name, tag=tag), color, dark)
                for name in ('back',):
                    fc = p.face(part, name, tag=tag)
                    for x in range(fc.fw):
                        for y in range(fc.fh):
                            fc.fpx(x, y, shade(color, 0.8))
        for i in range(len(SECONDARIES)):
            color = accent if i % 2 else white
            edge = navy if i % 2 else pale
            for name in ('top', 'bottom'):
                gc = p.face('sec_%s%d' % (side, i + 1), name, tag='sec%d' % i, glow=True)
                long_feather(p.face('sec_%s%d' % (side, i + 1), name, tag='sec%d' % i), color, edge)
                if glow_wings and i % 2:
                    for y in range(gc.fh):
                        for x in range(gc.fw):
                            gc.fpx(x, y, accent_glow)
        for i in range(len(PRIMARIES)):
            gold_tip = i >= 6
            color = white if gold_tip else (accent if i % 2 else white)
            edge = pale if not (i % 2) or gold_tip else navy
            for name in ('top', 'bottom'):
                fc = p.face('prim_%s%d' % (side, i + 1), name, tag='pf%d' % i)
                gc = p.face('prim_%s%d' % (side, i + 1), name, tag='pf%d' % i, glow=True)
                long_feather(fc, color, edge, tip=GOLD if gold_tip else None)
                if glow_wings and (i % 2) and not gold_tip:
                    for y in range(gc.fh):
                        for x in range(gc.fw):
                            gc.fpx(x, y, accent_glow)
        # the leading edge: riveted gold segments; plates and gems
        for part, tag in (('wing_' + side, 'edge1'), ('wing_%s2' % side, 'edge2')):
            for name in ('top', 'front', 'back', 'right', 'left', 'bottom'):
                riveted(p.face(part, name, tag=tag), GOLD)
        for part, tag in (('wing_' + side, 'plate1'), ('wing_' + side, 'plate2'), ('wing_%s2' % side, 'plate3'),
                          ('wing_%s2' % side, 'plate4'), ('wing_%s2' % side, 'plate5')):
            for name in ('top', 'front', 'right', 'left'):
                base.border(p.face(part, name, tag=tag), GOLD_DARK, 1, 'tblr')
        for name in ('top', 'front', 'right', 'left', 'back', 'bottom'):
            base.border(p.face('wing_%s2' % side, name, tag='wrist'), GOLD_DARK, 1, 'tblr')
            for part, tag in (('wing_' + side, 'wgem1'), ('wing_%s2' % side, 'wgem2'), ('wing_%s2' % side, 'wgem3')):
                base.gem_face(p.face(part, name, tag=tag), p.face(part, name, tag=tag, glow=True))
        fc = p.face('wing_%s2' % side, 'top', tag='wrist')
        base.star(fc, fc.fw // 2, fc.fh // 2, 2, GOLD_LIGHT)
    # ---------------------------------------------------------------- the crest and hackles: pointed pale feathers
    for prefix, spec in (('crest', CREST), ('hackle', HACKLES)):
        for i in range(1, count(spec) + 1):
            for name in ('top', 'bottom'):
                long_feather(p.face('%s_%d' % (prefix, i), name), white if i % 3 else mix(white, pale, 0.5), pale)
    # ---------------------------------------------------------------- the legs: gold scales and plated toes
    for pre in ('f', 'h'):
        for tag in ('s', 'band1', 'band2', 'f'):
            for name in ('front', 'right', 'left', 'back'):
                fc = p.face('%s_shin' % ('front_right' if pre == 'f' else 'hind_right'), name, tag=pre + tag)
                for y in range(0, fc.fh, 3):
                    for x in range(fc.fw):
                        fc.fpx(x, y, GOLD_DARK if (x + y // 3) % 4 else GOLD)
                base.border(fc, GOLD_LIGHT, 1, 't')
        for tag in ('toe', 'toe2', 'toeb'):
            for name in ('top', 'right', 'left', 'front'):
                fc = p.face('%s_toe%s' % ('front_right' if pre == 'f' else 'hind_right', '1' if tag != 'toeb' else '4'),
                            name, tag=pre + tag)
                for x in range(0, fc.fw, 3):
                    for y in range(fc.fh):
                        fc.fpx(x, y, GOLD_DARK)
                base.border(fc, GOLD_LIGHT, 1, 't')
    # ---------------------------------------------------------------- the head
    for name in ('right', 'left'):
        skull = p.face('head', name, tag='skull')
        gs = p.face('head', name, tag='skull', glow=True)
        front = name == 'right'
        # the navy mask around the eye, the glowing eye under the brow band, a pale streak behind it
        ex = 2 if front else skull.fw - 7
        for y in range(1, 7):
            for x in range(max(0, ex - 2), min(skull.fw, ex + 8)):
                skull.fpx(x, y, mix(navy, accent, 0.25 if (x + y) % 3 else 0.0))
        for dy in range(2):
            for dx in range(4):
                skull.fpx(ex + 1 + dx, 3 + dy, EYE_GLOW)
                gs.fpx(ex + 1 + dx, 3 + dy, EYE_GLOW)
        skull.fpx(ex + (1 if front else 4), 3, (240, 252, 255))
    for name in ('front', 'right', 'left', 'top'):
        base.gem_face(p.face('head', name, tag='crown_gem'), p.face('head', name, tag='crown_gem', glow=True))
    for tag in ('crown', 'crown_front'):
        for name in ('front', 'right', 'left', 'back', 'top'):
            fc = p.face('head', name, tag=tag)
            for x in range(0, fc.fw, 4):
                fc.fpx(x, fc.fh - 1, GOLD_DARK)
            base.border(fc, GOLD_LIGHT, 1, 't')
            if fc.fw >= 6 and fc.fh >= 3:
                fc.fpx(1, fc.fh // 2, GOLD_LIGHT)
                fc.fpx(fc.fw - 2, fc.fh // 2, GOLD_LIGHT)
    for part, tag in (('horn_c', 'horn'), ('horn_r', 'hornr')):
        for name in ('front', 'right', 'left', 'back'):
            fc = p.face(part, name, tag=tag)
            for y in range(fc.fh):
                for x in range(fc.fw):
                    fc.fpx(x, y, mix(GOLD_LIGHT, GOLD_DARK, y / max(1, fc.fh - 1)))
            fc.fclear(0, 0)
            fc.fclear(fc.fw - 1, 0)
    # the beak: gold, darker hooked tip, the cere at its base, the gape line
    for tag in ('beak', 'beak2'):
        for name in ('right', 'left', 'top', 'front'):
            base.border(p.face('head', name, tag=tag), GOLD_LIGHT, 1, 't')
    for tag in ('hook', 'hook2'):
        for name in ('right', 'left', 'front', 'bottom', 'back'):
            fc = p.face('head', name, tag=tag)
            for y in range(fc.fh):
                for x in range(fc.fw):
                    fc.fpx(x, y, mix(GOLD, GOLD_DARK, y / max(1, fc.fh - 1)))
    for name in ('right', 'left'):
        fc = p.face('head', name, tag='beak')
        for x in range(fc.fw):
            fc.fpx(x, fc.fh - 1, GOLD_DARK)
        fc.fpx(fc.fw - 3 if name == 'left' else 2, 2, GOLD_DARK)
    # ---------------------------------------------------------------- the harness, banners, lantern
    for name in ('front',):
        base.diamond(p.face('body', name, tag='chest_gem'), p.face('body', name, tag='chest_gem', glow=True))
        fc = p.face('body', name, tag='gem_frame')
        diamond_frame(fc)
    for name in ('right', 'left'):
        base.diamond(p.face('body', name, tag='shoulder_gem'), p.face('body', name, tag='shoulder_gem', glow=True))
        diamond_frame(p.face('body', name, tag='shoulder_frame'))
    for tag in ('strap_front', 'strap_side', 'girth'):
        for name in ('front', 'right', 'left', 'back', 'top', 'bottom'):
            fc = p.face('body', name, tag=tag)
            for x in range(1, fc.fw - 1, 5):
                fc.fpx(x, fc.fh // 2, GOLD)
            if fc.fh >= 3:
                for x in range(fc.fw):
                    fc.fpx(x, 0, shade(fc.fget(x, 0), 0.8))
    for name in ('right', 'left', 'front', 'back'):
        fc = p.face('body', name, tag='buckle')
        base.border(fc, GOLD_DARK, 1, 'tblr')
        for y in range(1, fc.fh - 1):
            fc.fpx(fc.fw // 2, y, shade(GOLD, 0.6))
    # the saddle: gold piping
    for part, tag in (('saddle', None),):
        for name in ('right', 'left', 'front', 'back'):
            base.border(p.face('saddle', name, index=0), GOLD, 1, 't')
    for tag in ('pommel', 'cantle', 'flap'):
        for name in ('right', 'left', 'front', 'back', 'top'):
            base.border(p.face('saddle', name, tag=tag), GOLD, 1, 'tblr')
    for name in ('right', 'left'):
        fc = p.face('banner_r', name, tag='banner')
        base.border(fc, GOLD, 2, 'lr')
        base.border(fc, GOLD, 1, 't')
        fleur(fc, fc.fw // 2, fc.fh // 2 - 2 * k, GOLD_LIGHT, GOLD_DARK)
        base.pointed_bottom(fc, 3 * k, step=4 * k)
        for x in range(fc.fw):
            fc.fpx(x, fc.fh - 3 * k - 1, GOLD)
    for name in ('front', 'back'):
        fc = p.face('banner_r', name, tag='banner')
        base.pointed_bottom(fc, 3 * k, step=max(2, fc.fw))
    for name in ('front', 'back'):
        fc = p.face('pennant', name, tag='pennant')
        base.border(fc, GOLD, 1, 'lrt')
        fleur(fc, fc.fw // 2, fc.fh // 2 - k, GOLD_LIGHT, GOLD_DARK)
        base.pointed_bottom(fc, 3 * k, step=fc.fw // 2)
    base.lantern_faces(p, 'lantern', 1)


def diamond_frame(fc):
    """A gold diamond setting: the corners cut away, a darker inner ring."""
    w, h = fc.fw, fc.fh
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for y in range(h):
        for x in range(w):
            d = abs(x - cx) / max(0.5, cx + 0.5) + abs(y - cy) / max(0.5, cy + 0.5)
            if d > 1.02:
                fc.fclear(x, y)
            elif d > 0.78:
                fc.fpx(x, y, GOLD_LIGHT if y < cy else GOLD)
            else:
                fc.fpx(x, y, GOLD_DARK)


# ================================================================================================ variants

def variant(cid, spec):
    body, head = spec['body'], spec['head']
    white, pale, accent = spec['white'], spec['pale'], spec['accent']
    pal = {
        'body': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'breast': {'base': body[1], 'style': 'feather', 'light': mix_hex(body[1], white, 0.2)},
        'belly': {'base': pale, 'style': 'feather', 'light': white},
        'ruff': {'base': white, 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'neck': {'base': white, 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'head': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'cheek': {'base': white, 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'crest': {'base': white, 'style': 'feather', 'light': head[1], 'tip': pale},
        'covert_n': {'base': spec['navy'], 'style': 'feather'},
        'covert_w': {'base': white, 'style': 'feather'},
        'flight_w': {'base': white, 'style': 'feather'},
        'flight_v': {'base': accent, 'style': 'feather'},
        'flight_g': {'base': white, 'style': 'feather'},
        'tail': {'base': white, 'style': 'feather'},
        'thigh': {'base': white, 'style': 'feather', 'light': head[1]},
        'haunch': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'shin': {'base': '#c89a3a', 'style': 'scale'},
        'cuff': {'base': white, 'style': 'feather', 'light': head[1]},
        'toe': {'base': '#e8b648', 'style': 'gold'},
        'foot': {'base': '#e2b046', 'style': 'gold'},
        'claw': '#18161c',
        'beak': {'base': '#f0c050', 'style': 'gold'},
        'beak_low': {'base': '#c0902c', 'style': 'gold'},
        'beak_tip': {'base': '#d09a30', 'style': 'gold'},
        'cere': {'base': '#f6d880', 'style': 'gold'},
        'crown': {'base': '#eab84a', 'style': 'gold'},
        'horn': {'base': '#eab84a', 'style': 'gold'},
        'edge': {'base': '#e6b444', 'style': 'gold'},
        'plate': {'base': '#f0c254', 'style': 'gold'},
        'wrist': {'base': '#eab84a', 'style': 'gold'},
        'setting': {'base': '#eab84a', 'style': 'gold'},
        'buckle': {'base': '#eab84a', 'style': 'gold'},
        'rod': {'base': '#d8a43c', 'style': 'gold'},
        'greave': {'base': '#e2b046', 'style': 'gold'},
        'gem': {'base': '#4a9cff', 'style': 'gem'},
        'strap': {'base': '#6a3e1e', 'style': 'leather'},
        'saddle': {'base': '#7a4826', 'style': 'leather'},
        'saddle_rim': {'base': '#5a3218', 'style': 'leather'},
        'banner': {'base': spec['banner'], 'style': 'cloth'},
        'pennant': {'base': spec['banner'], 'style': 'cloth'},
        'chain': {'base': '#8a7a5a', 'style': 'chain'},
        'lantern': {'base': '#c8962e', 'style': 'gold'},
        'lantern_cap': {'base': '#c8962e', 'style': 'gold'},
    }
    return Variant(cid, palette=pal, paint=paint, glow=True,
                   extra=dict(scale=1.0, hitbox=(1.5, 1.8), seat='saddle', glow_wings=spec.get('glow_wings', False),
                              wing=dict(white=white, pale=pale, accent=accent, navy=spec['navy'])))


def mix_hex(a, b, t):
    ca, cb = rgb(a), rgb(b)
    return '#%02x%02x%02x' % tuple(int(round(x + (y - x) * t)) for x, y in zip(ca, cb))


def rig():
    return Rig('mount_griffin', 'GriffinMount', geometry(), animations(),
               [variant(cid, spec) for cid, spec in base.GRIFFIN_COATS], GRIFFIN_SLOTS, texel=2)
