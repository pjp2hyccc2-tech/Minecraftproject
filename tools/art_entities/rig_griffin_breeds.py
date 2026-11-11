"""The five griffin breeds (2026-11-11, the owner: 「馬とグリフォンの種類をもう5体ずつ増やして」). Each is its own rig
(mount_griffin_<breed>) on the Azurewing Griffin's skeleton (rig_griffin.py: the same bones, gaits, wing fold and
seat), with its own head, wings, body, legs, tail and trappings, and five coats (four common, one rare). Own designs.

  gold   Goldmane Griffin    the old kind of griffin: an eagle's head and talons on a lion's body, a great shaggy
                             mane, a lion's tail with a dark tuft, hawk-barred wings, a desert harness of bronze,
                             turquoise and tassels; dust flies when it runs
  owl    Nighthush Griffin   owl-faced: a round facial disc with huge glowing eyes, ear tufts, mottled soft plumage,
                             broad rounded wings with fringed edges, feathered legs, a moon lantern and star charms
  storm  Stormplume Griffin  a falcon of the storm: long pointed wings veined with glowing lightning, a jagged crest,
                             a forked tail, silver plates studded with storm crystals; sparks fly from its wingtips
  frost  Rimecrest Griffin   ice-crowned: a crest of ice crystals, frosted feathers tipped with crystals, frosted
                             silver armour grown over with ice; snow falls from its wings
  void   Hollowstar Griffin  night-sky plumage strewn with stars, crystal horns, wings edged with a violet glow, rune
                             lines on the body and three crystal shards circling over its back

Conventions as in ermodel and rig_griffin: absolute rest-pose coordinates, +Y down, ground at y=24, front -Z, right
side -X. The bones the Java model drives keep the Azurewing's names (base, body, neck, head, crest_*, hackle_*, wing_r/l,
wing_r2/l2, prim_*, sec_*, tail, tail2, the legs and shins, saddle).
"""
import math

from ermodel import C, P, Rig, Variant, anim
from erpaint import mix, rgb, shade
import breed_paint as bp
import rig_griffin as griffin
import rig_mounts as base

SLOTS = ['idle', 'walk', 'fold', 'flap', 'glide']
GOLD, GOLD_DARK, GOLD_LIGHT = base.GOLD, base.GOLD_DARK, base.GOLD_LIGHT
X = griffin.X


# ================================================================================================ shared builders

def wing(side, spec):
    """A wing as the Azurewing's (arm with coverts and secondaries, hand with coverts and fanned primaries), from
    {@code spec}: primaries ((fan, length), ...), secondaries (lengths), edge (the leading edge's material; armour
    adds plates, a wrist guard and gems), flight (materials of the flight feathers by index), tips (an extra block at
    the end of every primary: crystals)."""
    s = -1 if side == 'r' else 1
    mirror = side == 'l'
    prims, secs = spec['primaries'], spec['secondaries']
    edge = spec.get('edge', 'gold:edge')
    armour = spec.get('armour', False)

    def tg(t):
        return t if side == 'r' else None

    def sh(t):
        return t if side == 'l' else None

    def cube(x, y, z, w, h, d, mat, t):
        return C(x, y, z, w, h, d, mat, tag=tg(t), share=sh(t), mirror=mirror)

    flight = spec.get('flight', lambda i, n, primary: 'feather:flight_w' if i % 2 == 0 else 'feather:flight_v')
    secondaries = []
    for i, length in enumerate(secs):
        cx = 8.0 + 2.0 * i
        lift = 0.18 * (i % 2)
        secondaries.append(P('sec_%s%d' % (side, i + 1), (s * cx, -0.4 - lift, 9.0),
                             cube(X(s, cx - 2.0, 4), -0.4 - lift, 9.0, 4, 1, length, flight(i, len(secs), False),
                                  'sec%d' % i),
                             rot=(2 + i * 0.5, s * (4 - i * 1.2), 0)))
    primaries = []
    for i, (fan, length) in enumerate(prims):
        cx = 23.0 + 1.7 * i
        lift = 0.15 * (i % 2)
        cubes = [cube(X(s, cx - 2.0, 4), -0.5 - lift, 1.0, 4, 1, length, flight(i, len(prims), True), 'pf%d' % i)]
        if spec.get('tips'):
            cubes.append(cube(X(s, cx - 1.5, 3), -1.0 - lift, 1.0 + length, 3, 2, 3, spec['tips'], 'pt%d' % i))
        primaries.append(P('prim_%s%d' % (side, i + 1), (s * cx, -0.5 - lift, 1.0), *cubes, rot=(2, s * fan, 0)))
    hand_cubes = [cube(X(s, 22, 15), -2.5, -10.0, 15, 3, 3, edge, 'edge2'),
                  cube(X(s, 22, 15), -1.6, -7.0, 15, 1, 5, 'feather:covert_n', 'hcov1'),
                  cube(X(s, 22, 15), -1.3, -3.0, 15, 1, 5, 'feather:covert_w', 'hcov2'),
                  cube(X(s, 22, 15), -1.0, 1.0, 15, 1, 4, 'feather:covert_n', 'hcov3')]
    arm_cubes = [cube(X(s, 6, 16), -2.5, -9.0, 16, 3, 3, edge, 'edge1'),
                 cube(X(s, 6, 16), -1.6, -6.0, 16, 1, 5, 'feather:covert_n', 'cov1'),
                 cube(X(s, 6, 16), -1.3, -2.0, 16, 1, 5, 'feather:covert_w', 'cov2'),
                 cube(X(s, 6, 16), -1.0, 2.0, 16, 1, 5, 'feather:covert_n', 'cov3'),
                 cube(X(s, 6, 16), -0.7, 6.0, 16, 1, 5, 'feather:covert_w', 'cov4')]
    if armour:
        plate = spec.get('plate', 'gold:plate')
        hand_cubes += [cube(X(s, 23, 3), -3.5, -10.5, 3, 1, 4, plate, 'plate3'),
                       cube(X(s, 29, 3), -3.5, -10.5, 3, 1, 4, plate, 'plate4'),
                       cube(X(s, 21, 4), -4.0, -11.0, 4, 4, 4, plate, 'wrist'),
                       cube(X(s, 22, 2), -5.0, -10.0, 2, 1, 2, 'gem:gem', 'wgem2')]
        arm_cubes += [cube(X(s, 9, 3), -3.5, -9.5, 3, 1, 4, plate, 'plate1'),
                      cube(X(s, 15, 3), -3.5, -9.5, 3, 1, 4, plate, 'plate2'),
                      cube(X(s, 12, 2), -4.0, -8.5, 2, 1, 2, 'gem:gem', 'wgem1')]
    for extra in spec.get('arm_extra', ()):
        ex, ey, ez, ew, eh, ed, emat, etag = extra
        arm_cubes.append(cube(X(s, ex, ew), ey, ez, ew, eh, ed, emat, etag))
    hand = P('wing_%s2' % side, (s * 22.0, -1.0, -7.5), *hand_cubes, *primaries)
    return P('wing_%s' % side, (s * 6.0, -1.0, -6.0), *arm_cubes, *secondaries, hand)


def ring(prefix, center, spec, mat, jitter=True):
    """Rings of feathers (or tufts, or crystals) around an axis, as rig_griffin.ring with a material."""
    cy, cz = center
    parts = []
    n = 0
    for radius, width, length, pitch, rolls in spec:
        for roll in rolls:
            for side in ((1,) if roll == 0 else (-1, 1)):
                n += 1
                ln = length + ((n * 7) % 5 - 2 if jitter else 0)
                flare = pitch + ((n * 5) % 9 - 4 if jitter else 0)
                turn = ((n * 3) % 7 - 3) * 2 if jitter else 0
                parts.append(P('%s_%d' % (prefix, n), (0.0, cy, cz),
                               C(-width / 2.0, cy - radius - 0.5, cz, width, 1, max(1, ln), mat),
                               rot=(flare, turn, roll * side)))
    return parts


def ring_count(spec):
    return sum(1 if roll == 0 else 2 for ring_spec in spec for roll in ring_spec[4])


def eagle_leg(name, x, z, hind, side, spec):
    """An eagle's leg as the Azurewing's: thigh, cuff, tarsus with bands, foot, three toes forward and one back with
    talons. spec: materials thigh, haunch, cuff, tarsus, band, foot, toe, claw."""
    mirror = side == 'l'
    pre = name.split('_leg')[0] + '_'

    def cube(cx, y, cz, w, h, d, mat, t):
        return C(cx, y, cz, w, h, d, mat, tag=(('h' if hind else 'f') + t) if side == 'r' else None,
                 share=(('h' if hind else 'f') + t) if side == 'l' else None, mirror=mirror)

    toes = []
    for i, dx in enumerate((-1.5, 0.0, 1.5)):
        tx = x + dx
        toes.append(P(pre + 'toe%d' % (i + 1), (tx, 21.0, z - 1.5),
                      cube(tx - 1.0, 20.5, z - 4.5, 2, 2, 3, spec['toe'], 'toe'),
                      cube(tx - 0.75, 21.0, z - 6.5, 2, 2, 2, spec['toe'], 'toe2'),
                      cube(tx - 0.5, 21.5, z - 8.0, 1, 1, 2, spec['claw'], 'claw'),
                      cube(tx - 0.5, 22.5, z - 8.5, 1, 1, 1, spec['claw'], 'claw_tip'),
                      rot=(-8, -dx * 9.0, 0)))
    toes.append(P(pre + 'toe4', (x, 21.5, z + 1.0),
                  cube(x - 0.75, 21.0, z + 0.5, 2, 2, 3, spec['toe'], 'toeb'),
                  cube(x - 0.5, 22.0, z + 3.5, 1, 1, 2, spec['claw'], 'clawb'),
                  cube(x - 0.5, 23.0, z + 4.5, 1, 1, 1, spec['claw'], 'clawb_tip')))
    tarsus = [cube(x - 2.5, 11.5, z - 2.5, 5, 3, 5, spec['cuff'], 'cuff'),
              cube(x - 1.5, 13.0, z - 1.5, 3, 7, 3, spec['tarsus'], 's'),
              cube(x - 2.0, 14.0, z - 2.0, 4, 2, 4, spec['band'], 'band1'),
              cube(x - 2.0, 17.0, z - 2.0, 4, 2, 4, spec['band'], 'band2'),
              cube(x - 2.0, 19.5, z - 2.5, 4, 2, 4, spec['foot'], 'f')]
    shin = P(name.replace('_leg', '_shin'), (x, 12.0, z), *tarsus, *toes)
    if hind:
        return P(name, (x, 5.0, z), cube(x - 3.0, 2.0, z - 3.5, 6, 10, 7, spec['haunch'], 'u'), shin)
    return P(name, (x, 6.0, z), cube(x - 3.0, 4.0, z - 3.0, 6, 9, 6, spec['thigh'], 'u'), shin)


def lion_leg(name, x, z, side):
    """A lion's hind leg: a heavy thigh, a hock, a long cannon and a broad paw with four toes and dark pads."""
    mirror = side == 'l'

    def cube(cx, y, cz, w, h, d, mat, t):
        return C(cx, y, cz, w, h, d, mat, tag=t if side == 'r' else None, share=t if side == 'l' else None,
                 mirror=mirror)

    paw = [cube(x - 2.5, 21.0, z - 4.5, 5, 3, 6, 'fur:paw', 'paw'),
           cube(x - 2.5, 22.0, z - 5.5, 5, 2, 1, 'fur:paw', 'paw_toes')]
    shin = P(name.replace('_leg', '_shin'), (x, 12.0, z),
             cube(x - 2.0, 12.0, z - 1.0, 4, 9, 4, 'fur:lion', 'ls'), *paw)
    return P(name, (x, 5.0, z), cube(x - 3.0, 2.0, z - 3.5, 6, 10, 7, 'fur:lion', 'lu'), shin)


def griffin_head(skull_mat='feather:head', cheek_mat='feather:cheek', beak='gold', crown=None, extra=(), crest=(),
                 parts=()):
    """The eagle head of the Azurewing (skull, cheeks, the hooked beak in three blocks, the cere), with a breed's
    crown or circlet, extra cubes, crest feathers and parts (horns)."""
    cubes = [C(-4, -14, -17, 8, 7, 8, skull_mat, tag='skull'),
             C(-4.5, -11, -16, 9, 4, 6, cheek_mat, tag='cheek'),
             C(-2, -13, -21, 4, 4, 4, beak + ':beak', tag='beak'),
             C(-1.5, -12.5, -23, 3, 3, 2, beak + ':beak', tag='beak2'),
             C(-1, -11.5, -24, 2, 3, 1, beak + ':beak_tip', tag='hook'),
             C(-1, -9, -23.5, 2, 1, 1, beak + ':beak_tip', tag='hook2'),
             C(-1.5, -9.5, -21, 3, 1, 3, beak + ':beak_low', tag='jaw'),
             C(-2.5, -13.5, -18, 5, 2, 2, beak + ':cere', tag='cere')]
    if crown:
        cubes += crown
    cubes += list(extra)
    return P('head', (0, -8, -11), *cubes, *parts, *crest)


def assemble(head, neck_cubes, hackles, body_cubes, body_parts, tail_part, wings, legs, saddle):
    neck = P('neck', (0, 0, -10),
             C(-3.5, -9, -14, 7, 11, 7, 'feather:neck', tag='neck_core'),
             C(-4.5, -3, -14.5, 9, 6, 8, 'feather:ruff', tag='ruff'),
             *neck_cubes, head, *hackles, rot=(22, 0, 0))
    body = P('body', (0, 6, 0), *body_cubes, saddle, neck, tail_part, *wings, *body_parts, *legs)
    return [P('base', (0, 24, 0), body)]


def standard_saddle(rim='leather:saddle_rim', seat='leather:saddle', flap='leather:saddle'):
    return P('saddle', (0, -2, 0),
             C(-4.5, -3, -4, 9, 2, 9, seat),
             C(-3, -5, -5, 6, 2, 2, rim, tag='pommel'),
             C(-4, -7, 4, 8, 4, 2, rim, tag='cantle'),
             C(-6, -2, -3, 1, 5, 7, flap, tag='flap'),
             C(5, -2, -3, 1, 5, 7, flap, share='flap', mirror=True))


def feather_body(belly='feather:belly', body='feather:body', breast='feather:breast'):
    return [C(-6, -1, -10, 12, 11, 20, body, tag='barrel'),
            C(-6.5, -2, -13, 13, 11, 4, breast, tag='breast'),
            C(-5.5, -2, 9, 11, 9, 4, body, tag='rump'),
            C(-5, 9, -8, 10, 2, 16, belly, tag='belly')]


def eagle_legs(spec):
    return [eagle_leg('front_right_leg', -3.5, -6, False, 'r', spec),
            eagle_leg('front_left_leg', 3.5, -6, False, 'l', spec),
            eagle_leg('hind_right_leg', -3.5, 7, True, 'r', spec),
            eagle_leg('hind_left_leg', 3.5, 7, True, 'l', spec)]


def feather_tail(lengths=(8, 9, 10, 9, 8), mats=None, rot=-12, tips=None):
    """The fan tail: a covert block and five tail feathers (tail2), as the Azurewing's."""
    mats = mats or ['feather:flight_w', 'feather:flight_v', 'feather:flight_w', 'feather:flight_v', 'feather:flight_w']
    xs = (-5.5, -3.0, -1.0, 1.0, 3.5)
    ys = (0.6, 0.4, 0.6, 0.4, 0.6)
    tags = ('tf1', 'tf2', 'tf3', None, None)
    shares = (None, None, None, 'tf2', 'tf1')
    cubes = []
    for i in range(5):
        cubes.append(C(xs[i], ys[i], 18.5, 2, 1, lengths[i], mats[i], tag=tags[i], share=shares[i], mirror=i > 2))
        if tips:
            cubes.append(C(xs[i], ys[i] - 0.5, 18.5 + lengths[i], 2, 2, 2, tips, tag=('tt%d' % i) if i < 3 else None,
                           share=('tt%d' % (4 - i)) if i > 2 else None, mirror=i > 2))
    return P('tail', (0, 1, 11), C(-4, 0.5, 11, 8, 1, 8, 'feather:tail', tag='tail_core'),
             P('tail2', (0, 1, 18.5), *cubes), rot=(rot, 0, 0))


def riveted(fc, color, step=6):
    """Armour in segments of {@code color}: darker seams, rivets either side, a lit top edge."""
    color = bp.col(color)
    dark, light = shade(color, 0.62), mix(color, (255, 255, 255), 0.55)
    w, h = fc.fw, fc.fh
    for x in range(0, w, step):
        for y in range(h):
            fc.fpx(x, y, dark)
        if h >= 4:
            for rx in (x + 2, x + step - 2):
                if 0 <= rx < w:
                    fc.fpx(rx, 1, light)
                    fc.fpx(rx, h - 2, light)
    for x in range(w):
        fc.fpx(x, 0, mix(color, (255, 255, 255), 0.4))


def faces(p, part, tag=None, index=0, names=('front', 'back', 'right', 'left', 'top', 'bottom')):
    for n in names:
        yield p.face(part, n, tag=tag, index=index), p.face(part, n, tag=tag, index=index, glow=True), n


def paint_wings(p, prims, secs, white, pale, accent, navy, glow=None, flight_style=None):
    """The Azurewing's wing paint (coverts in shingle rows, long feathers) in a breed's colours; {@code flight_style}
    (fc, gc, index, primary, color) adds a breed's marks on every flight feather."""
    for part, tags, color, dark in (('wing_r', ('cov1', 'cov3'), navy, shade(navy, 0.6)),
                                    ('wing_r', ('cov2', 'cov4'), white, pale),
                                    ('wing_r2', ('hcov1', 'hcov3'), navy, shade(navy, 0.6)),
                                    ('wing_r2', ('hcov2',), white, pale)):
        for tag in tags:
            for name in ('top', 'bottom'):
                griffin.covert_row(p.face(part, name, tag=tag), color, dark)
            fc = p.face(part, 'back', tag=tag)
            bp.fill(fc, shade(color, 0.8))
    for i in range(len(secs)):
        color = accent if i % 2 else white
        edge = navy if i % 2 else pale
        for name in ('top', 'bottom'):
            fc = p.face('sec_r%d' % (i + 1), name, tag='sec%d' % i)
            gc = p.face('sec_r%d' % (i + 1), name, tag='sec%d' % i, glow=True)
            griffin.long_feather(fc, color, edge)
            if flight_style:
                flight_style(fc, gc, i, False, color)
    for i in range(len(prims)):
        color = accent if i % 2 else white
        edge = navy if i % 2 else pale
        for name in ('top', 'bottom'):
            fc = p.face('prim_r%d' % (i + 1), name, tag='pf%d' % i)
            gc = p.face('prim_r%d' % (i + 1), name, tag='pf%d' % i, glow=True)
            griffin.long_feather(fc, color, edge)
            if flight_style:
                flight_style(fc, gc, i, True, color)


def griffin_eyes(p, color, mask=None, glow=True, size=(4, 2)):
    """The eyes on the skull's sides (where the Azurewing's are), in a mask if given."""
    for name in ('right', 'left'):
        skull = p.face('head', name, tag='skull')
        gs = p.face('head', name, tag='skull', glow=True)
        front = name == 'right'
        ex = 2 if front else skull.fw - 7
        if mask is not None:
            for y in range(1, 7):
                for x in range(max(0, ex - 2), min(skull.fw, ex + 8)):
                    skull.fpx(x, y, mask)
        w, h = size
        for dy in range(h):
            for dx in range(w):
                skull.fpx(ex + 1 + dx, 3 + dy, bp.col(color))
                if glow:
                    gs.fpx(ex + 1 + dx, 3 + dy, bp.col(color))
        skull.fpx(ex + (1 if front else w), 3, (240, 252, 255))


def beak_paint(p, light, dark):
    for tag in ('beak', 'beak2'):
        for name in ('right', 'left', 'top', 'front'):
            base.border(p.face('head', name, tag=tag), light, 1, 't')
    for tag in ('hook', 'hook2'):
        for name in ('right', 'left', 'front', 'bottom', 'back'):
            fc = p.face('head', name, tag=tag)
            for x, y in bp.each(fc):
                fc.fpx(x, y, mix(bp.col(light), bp.col(dark), y / max(1, fc.fh - 1)))
    for name in ('right', 'left'):
        fc = p.face('head', name, tag='beak')
        for x in range(fc.fw):
            fc.fpx(x, fc.fh - 1, dark)


def talons(p, claw_c):
    for pre in ('front_right', 'hind_right'):
        for t in ('toe1', 'toe4'):
            if '%s_%s' % (pre, t) not in p.rig.parts:
                continue
            for c_i, c in enumerate(p.rig.parts['%s_%s' % (pre, t)].cubes):
                if c.mat.endswith('claw'):
                    for name in ('front', 'top', 'right', 'left', 'bottom'):
                        fc = p.face('%s_%s' % (pre, t), name, index=c_i)
                        for x, y in bp.each(fc):
                            fc.fpx(x, y, mix(bp.col(claw_c), (255, 255, 255), 0.25 if y == 0 else 0.0))


# ================================================================================================ gold (lion)

GOLD_CREST = ((3.6, 3, 7, 14, (0, 44)),)
GOLD_MANE = ((4.6, 4, 9, 18, (0, 30, 60, 90, 120, 150)), (3.6, 4, 8, 6, (15, 45, 75, 105, 135, 165)))
GOLD_PRIMARIES = griffin.PRIMARIES
GOLD_SECONDARIES = griffin.SECONDARIES


def lion_tail():
    """A lion's tail: hanging from the rump, curling up at the end, a dark tuft (tail2 carries the tuft)."""
    return P('tail', (0, 1, 11),
             C(-1, 0, 11, 2, 2, 8, 'fur:lion', tag='lt1'),
             P('tail2', (0, 1, 19),
               C(-1, 0.5, 19, 2, 2, 6, 'fur:lion', tag='lt2'),
               C(-1.5, 0.0, 24, 3, 3, 5, 'hair:tuft', tag='tuft'),
               rot=(34, 0, 0)),
             rot=(-68, 0, 0))


def gold_geometry():
    crown = [C(-4.5, -14.5, -17.5, 9, 1, 6, 'gold:circlet', tag='circlet'),
             C(-1, -16.0, -17.9, 2, 2, 1, 'gem:turq', tag='circlet_gem')]
    head = griffin_head(beak='horn', crown=crown, crest=ring('crest', griffin.CREST_CENTER, GOLD_CREST, 'feather:crest'))
    hackles = ring('hackle', griffin.HACKLE_CENTER, GOLD_MANE, 'fur:mane')
    body_cubes = [C(-6, -1, -10, 12, 11, 20, 'fur:lion', tag='barrel'),
                  C(-6.5, -2, -13, 13, 11, 4, 'feather:breast', tag='breast'),
                  C(-5.5, -2, 9, 11, 9, 4, 'fur:lion', tag='rump'),
                  C(-5, 9, -8, 10, 2, 16, 'fur:belly', tag='belly'),
                  # the desert harness: a bronze-and-turquoise breast strap with tassels, a striped blanket
                  C(-7, 0.5, -13.6, 14, 2, 1, 'leather:strap', tag='strap_front'),
                  C(-7, 0.5, -13, 1, 2, 9, 'leather:strap', tag='strap_side'),
                  C(6, 0.5, -13, 1, 2, 9, 'leather:strap', share='strap_side', mirror=True),
                  C(-2.0, -0.5, -14.4, 4, 4, 1, 'gold:bronze', tag='medallion'),
                  C(-1.0, 0.5, -14.9, 2, 2, 1, 'gem:turq', tag='medal_gem'),
                  C(-6.5, -1.5, -5, 13, 1, 11, 'cloth:desert', tag='blanket'),
                  C(-7.0, -1.0, -4.5, 1, 7, 10, 'cloth:desert', tag='drape'),
                  C(6.0, -1.0, -4.5, 1, 7, 10, 'cloth:desert', share='drape', mirror=True),
                  C(-6.5, -1.5, -2, 13, 12, 2, 'leather:strap', tag='girth')]
    tassels = []
    for i, z in enumerate((-12.0, -9.0)):
        for side, s in (('r', -1), ('l', 1)):
            x = 7.2 * s
            tassels.append(P('tassel_%s%d' % (side, i + 1), (x, 2.5, z),
                             C(x - 0.5, 2.5, z - 0.5, 1, 4, 1, 'cloth:tassel', tag=('tassel%d' % i) if s < 0 else None,
                               share=None if s < 0 else 'tassel%d' % i, mirror=s > 0),
                             C(x - 0.5, 1.5, z - 0.5, 1, 1, 1, 'gold:bronze', tag=('tbead%d' % i) if s < 0 else None,
                               share=None if s < 0 else 'tbead%d' % i, mirror=s > 0)))
    spec = dict(primaries=GOLD_PRIMARIES, secondaries=GOLD_SECONDARIES, edge='feather:edge')
    legs = [eagle_leg('front_right_leg', -3.5, -6, False, 'r', GOLD_LEG),
            eagle_leg('front_left_leg', 3.5, -6, False, 'l', GOLD_LEG),
            lion_leg('hind_right_leg', -3.5, 7, 'r'), lion_leg('hind_left_leg', 3.5, 7, 'l')]
    return assemble(head, [], hackles, body_cubes, tassels, lion_tail(), [wing('r', spec), wing('l', spec)], legs,
                    standard_saddle())


GOLD_LEG = dict(thigh='feather:thigh', haunch='feather:thigh', cuff='feather:cuff', tarsus='scale:tarsus',
                band='scale:tarsus', foot='scale:tarsus', toe='scale:toe', claw='plain:claw')


def gold_paint(p):
    e = p.variant.extra
    white, pale, accent, navy = rgb(e['wing']), rgb(e['pale']), rgb(e['accent']), rgb(e['covert'])
    glow_mane = e.get('glow')

    def barred(fc, gc, i, primary, color):
        bp.barring(fc, shade(rgb(e['bar']), 0.9), period=4, phase=i % 2, strength=0.6)
        if glow_mane and primary and i >= len(GOLD_PRIMARIES) - 3:
            for x, y in bp.each(fc):
                if y < 4 and bp.opaque(fc, x, y):
                    gc.fpx(x, y, shade(rgb(e['glow_col']), 0.8))

    paint_wings(p, GOLD_PRIMARIES, GOLD_SECONDARIES, white, pale, accent, navy, flight_style=barred)
    for part, tag in (('wing_r', 'edge1'), ('wing_r2', 'edge2')):
        for fc, gc, name in faces(p, part, tag=tag):
            griffin.covert_row(fc, shade(navy, 1.1), shade(navy, 0.7)) if name in ('top', 'bottom') else \
                bp.fill(fc, shade(navy, 0.9))
    # the mane: shaggy tufts, darker at the roots; a glowing sunfire mane
    mane, mane_l = rgb(e['mane']), mix(rgb(e['mane']), rgb(e['lion'][2]), 0.5)
    for i in range(1, ring_count(GOLD_MANE) + 1):
        for fc, gc, name in faces(p, 'hackle_%d' % i, names=('top', 'bottom', 'right', 'left')):
            bp.fur_tufts(fc, mane, mane_l, shade(mane, 0.6), (p.variant.id, i, name), tip_at_top=True)
            if glow_mane:
                bp.glow_copy(fc, gc, 0.75)
    for i in range(1, ring_count(GOLD_CREST) + 1):
        for name in ('top', 'bottom'):
            griffin.long_feather(p.face('crest_%d' % i, name), rgb(e['head'][0]), shade(rgb(e['head'][0]), 0.7))
    for fc, gc, name in faces(p, 'neck', tag='ruff'):
        bp.fur_tufts(fc, mane, mane_l, shade(mane, 0.6), (p.variant.id, 'ruff', name))
        if glow_mane:
            bp.glow_copy(fc, gc, 0.6)
    # the lion: a lighter belly and inner legs, dark paws with pads, the tail's tuft
    lion = rgb(e['lion'][0])
    for name in ('right', 'left'):
        fc = p.face('body', name, tag='barrel')
        for x, y in bp.each(fc):
            t = y / max(1, fc.fh - 1)
            if t > 0.65:
                fc.fpx(x, y, mix(fc.fget(x, y), rgb(e['belly']), (t - 0.65) * 1.8))
    for part, tag in (('hind_right_shin', 'paw'), ('hind_right_shin', 'paw_toes')):
        for fc, gc, name in faces(p, part, tag=tag):
            if name == 'bottom' or tag == 'paw_toes':
                for x, y in bp.each(fc):
                    if (x // 2) % 3 != 2:
                        fc.fpx(x, y, mix(rgb(e['pad']), fc.fget(x, y), 0.3 if name != 'bottom' else 0.0))
    for fc, gc, name in faces(p, 'tail2', tag='tuft'):
        bp.fur_tufts(fc, mane, mane_l, shade(mane, 0.6), (p.variant.id, 'tuft', name))
        if glow_mane:
            bp.glow_copy(fc, gc, 0.7)
    # the head: eyes, beak, the bronze circlet
    griffin_eyes(p, e['eye'], mask=shade(rgb(e['head'][0]), 0.75), glow=True, size=(3, 2))
    beak_paint(p, rgb(e['beak_light']), rgb(e['beak_dark']))
    talons(p, (30, 26, 24))
    bronze = rgb(e['bronze'])
    for part, tag in (('head', 'circlet'), ('body', 'medallion')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.trim(fc, mix(bronze, (255, 255, 255), 0.35), 1, 't')
    for part, tag in (('head', 'circlet_gem'), ('body', 'medal_gem')):
        for fc, gc, name in faces(p, part, tag=tag, names=('front', 'top', 'right', 'left')):
            bp.gem(fc, gc, rgb(e['turq']), mix(rgb(e['turq']), (255, 255, 255), 0.3))
    # the desert cloth: bands and a row of diamonds; tassels
    stripe, stripe2 = rgb(e['stripe']), rgb(e['stripe2'])
    for tag, names in (('blanket', ('top',)), ('drape', ('right',))):
        for name in names:
            fc = p.face('body', name, tag=tag)
            for x, y in bp.each(fc):
                band = (y // 3) % 4 if tag == 'drape' else (x // 3) % 4
                if band == 1:
                    fc.fpx(x, y, stripe)
                elif band == 3:
                    fc.fpx(x, y, stripe2)
            for cx in range(3, fc.fw - 2, 6):
                cy = fc.fh // 2
                for d in range(3):
                    for dx, dy in ((d, 2 - d), (-d, 2 - d), (d, d - 2), (-d, d - 2)):
                        fc.fpx(cx + dx, cy + dy, bronze)
            if tag == 'drape':
                base.pointed_bottom(fc, 3, step=3)
    for i in range(2):
        for fc, gc, name in faces(p, 'tassel_r%d' % (i + 1), tag='tassel%d' % i):
            for x, y in bp.each(fc):
                fc.fpx(x, y, mix(stripe, (255, 255, 255), 0.2 * (y % 2)))
        for fc, gc, name in faces(p, 'tassel_r%d' % (i + 1), tag='tbead%d' % i):
            bp.fill(fc, bronze)
    for tag in ('strap_front', 'strap_side', 'girth'):
        for fc, gc, name in faces(p, 'body', tag=tag, names=('front', 'right', 'left', 'top')):
            for x in range(1, fc.fw - 1, 5):
                fc.fpx(x, fc.fh // 2, bronze)
    for tag in ('pommel', 'cantle', 'flap'):
        for fc, gc, name in faces(p, 'saddle', tag=tag, names=('right', 'left', 'front', 'back', 'top')):
            bp.trim(fc, bronze, 1, 'tblr')


GOLD_COATS = [
    ('tawny', dict(lion=('#c8964a', '#9a6c2c', '#e0b46a'), mane='#7a4a1e', head=('#b07a3a', '#d09a52', '#8a5a26'),
                   cheek='#f0e2c4', wing='#e8d4a8', pale='#b89a6a', accent='#9a6a34', covert='#6a4420', bar='#3e2610',
                   belly='#f0d8a8', pad='#2a1e18', beak='#4a3a2c', beak_light='#8a7a66', beak_dark='#1e1814',
                   eye='#ffb43a', bronze='#c08a3a', turq='#3ac8b8', stripe='#b03a2a', stripe2='#e8a83a',
                   cloth='#e8d0a0')),
    ('sable', dict(lion=('#5a3e2a', '#3c281a', '#74543a'), mane='#1e1612', head=('#4a3424', '#644a34', '#2e2016'),
                   cheek='#c8b090', wing='#8a7056', pale='#5a4632', accent='#4a3424', covert='#2e2016', bar='#140e0a',
                   belly='#a08466', pad='#140e0a', beak='#2a221c', beak_light='#5e5246', beak_dark='#100c0a',
                   eye='#ffd040', bronze='#a87a3a', turq='#4ad8a8', stripe='#2a4a7a', stripe2='#c8a04a',
                   cloth='#3a2a20')),
    ('ivory', dict(lion=('#e8e0d0', '#c0b8a6', '#fbf6ec'), mane='#f6f0e2', head=('#f4f0e8', '#ffffff', '#d6d0c4'),
                   cheek='#ffffff', wing='#f8f6f0', pale='#d4d0c6', accent='#bcb4a4', covert='#a49c8c', bar='#8a8274',
                   belly='#fffaf0', pad='#5a5450', beak='#8a8076', beak_light='#d8d0c4', beak_dark='#4a443e',
                   eye='#7ad0ff', bronze='#d8c08a', turq='#5ab8e8', stripe='#3a6ab0', stripe2='#e8e0c8',
                   cloth='#f0ead8')),
    ('russet', dict(lion=('#b4562a', '#86381a', '#d0743e'), mane='#5a2410', head=('#a04a22', '#c0623a', '#7a3418'),
                    cheek='#f4d8c0', wing='#e8b88a', pale='#b4805a', accent='#8a3e1e', covert='#6a2a12', bar='#3a1408',
                    belly='#f0c8a0', pad='#2a140c', beak='#3a2a20', beak_light='#7a6656', beak_dark='#140c08',
                    eye='#ffd06a', bronze='#c8963e', turq='#3ab890', stripe='#2a6a3a', stripe2='#e8c84a',
                    cloth='#d8b47a')),
    ('sunfire', dict(lion=('#e8b84a', '#c0902c', '#ffd878'), mane='#fff0a0', head=('#f0c860', '#ffe08a', '#c89a3a'),
                     cheek='#fff8e0', wing='#fff4d0', pale='#f0d088', accent='#ffc84a', covert='#e09a2a', bar='#a8601a',
                     belly='#fff4d0', pad='#6a3a1a', beak='#c88a2a', beak_light='#ffe08a', beak_dark='#7a4a14',
                     eye='#ffffff', bronze='#ffd870', turq='#7affe8', stripe='#ff7a2a', stripe2='#fff0a0',
                     cloth='#fff0c8', glow=True, glow_col='#ffe890')),
]


def gold_variant(cid, spec):
    lion = spec['lion']
    head = spec['head']
    pal = {
        'lion': {'base': lion[0], 'style': 'fur', 'dark': lion[1], 'light': lion[2]},
        'belly': {'base': spec['belly'], 'style': 'fur'},
        'paw': {'base': lion[1], 'style': 'fur', 'dark': spec['pad'], 'light': lion[0]},
        'mane': {'base': spec['mane'], 'style': 'fur'},
        'tuft': {'base': spec['mane'], 'style': 'hair'},
        'body': {'base': lion[0], 'style': 'fur', 'dark': lion[1], 'light': lion[2]},
        'breast': {'base': head[0], 'style': 'feather', 'light': head[1]},
        'ruff': {'base': spec['mane'], 'style': 'fur'},
        'neck': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'head': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'cheek': {'base': spec['cheek'], 'style': 'feather', 'light': '#ffffff'},
        'crest': {'base': head[0], 'style': 'feather'},
        'covert_n': {'base': spec['covert'], 'style': 'feather'},
        'covert_w': {'base': spec['wing'], 'style': 'feather'},
        'edge': {'base': spec['covert'], 'style': 'feather'},
        'flight_w': {'base': spec['wing'], 'style': 'feather'},
        'flight_v': {'base': spec['accent'], 'style': 'feather'},
        'tail': {'base': spec['wing'], 'style': 'feather'},
        'thigh': {'base': head[0], 'style': 'feather', 'light': head[1]},
        'cuff': {'base': spec['cheek'], 'style': 'feather'},
        'tarsus': {'base': '#d8b040', 'style': 'scale'},
        'toe': {'base': '#d0a838', 'style': 'scale'},
        'claw': '#1c1814',
        'beak': {'base': spec['beak'], 'style': 'plain'},
        'beak_low': {'base': shade_hex(spec['beak'], 0.8), 'style': 'plain'},
        'beak_tip': {'base': spec['beak_dark'], 'style': 'plain'},
        'cere': {'base': spec['beak_light'], 'style': 'plain'},
        'circlet': {'base': spec['bronze'], 'style': 'gold'},
        'bronze': {'base': spec['bronze'], 'style': 'gold'},
        'turq': {'base': spec['turq'], 'style': 'gem'},
        'strap': {'base': '#6a3e1e', 'style': 'leather'},
        'saddle': {'base': '#7a4826', 'style': 'leather'},
        'saddle_rim': {'base': '#5a3218', 'style': 'leather'},
        'desert': {'base': spec['cloth'], 'style': 'cloth'},
        'tassel': {'base': spec['stripe'], 'style': 'plain'},
    }
    extra = dict(spec, scale=1.0, hitbox=(1.5, 1.8), seat='saddle')
    return Variant(cid, palette=pal, paint=gold_paint, glow=True, extra=extra)


def shade_hex(c, f):
    r, g, b = shade(rgb(c), f)
    return '#%02x%02x%02x' % (r, g, b)


def gold_rig():
    return Rig('mount_griffin_gold', 'GriffinGoldMount', gold_geometry(),
               griffin.animations(primaries=GOLD_PRIMARIES, secondaries=GOLD_SECONDARIES,
                                  crest_n=ring_count(GOLD_CREST), hackle_n=ring_count(GOLD_MANE),
                                  absent=('banner_r', 'banner_l', 'pennant', 'lantern'),
                                  extra={'idle': {'tail2': dict(rot=[(0, 0, 0, 0), (1.5, 12, 0, 0), (3.0, 0, 0, 0)])}}),
               [gold_variant(cid, spec) for cid, spec in GOLD_COATS], SLOTS, texel=2)


# ================================================================================================ owl

OWL_TUFTS = ((4.6, 3, 8, -52, (34,)), (4.0, 2, 7, -44, (48,)))        # ear tufts standing up and out
OWL_HACKLES = ((4.0, 3, 8, 14, (0, 40, 80, 114)), (3.0, 3, 6, 4, (20, 60, 97)))
OWL_PRIMARIES = ((2, 16), (10, 17), (19, 18), (28, 18), (38, 17), (48, 16), (58, 15), (68, 13), (78, 11))
OWL_SECONDARIES = (14, 15, 15, 15, 15, 15, 14, 14)
OWL_LEG = dict(thigh='feather:legfeather', haunch='feather:legfeather', cuff='feather:legfeather',
               tarsus='feather:legfeather', band='feather:legfeather', foot='feather:legfeather', toe='plain:toe',
               claw='plain:claw')


def owl_tufts():
    """Two ear tufts on each side of the crown, standing up and out (crest_1-4: the royal plumes lengthen them)."""
    parts = []
    n = 0
    for (x, z, w, ln, lean, out) in ((3.4, -15.5, 3, 6, 44, 30), (2.2, -13.5, 2, 4, 36, 22)):
        for s in (-1, 1):
            n += 1
            px = x * s
            parts.append(P('crest_%d' % n, (px, -15.0, z), C(px - w / 2.0, -15.5, z, w, 1, ln, 'feather:crest'),
                           rot=(lean, 0, out * s)))
    return parts


def owl_geometry():
    disc = [C(-5, -14.5, -18.0, 10, 9, 1, 'feather:disc', tag='disc'),
            C(-5.5, -15.0, -17.5, 1, 10, 3, 'feather:rim', tag='rim'),
            C(4.5, -15.0, -17.5, 1, 10, 3, 'feather:rim', share='rim', mirror=True),
            C(-4.5, -15.5, -17.5, 9, 1, 3, 'feather:rim', tag='rim_top'),
            C(-0.5, -11.5, -19.0, 1, 3, 1, 'plain:owlbeak', tag='owl_beak')]
    head = P('head', (0, -8, -11),
             C(-4.5, -15, -17, 9, 9, 8, 'feather:head', tag='skull'),
             C(-5, -11, -16, 10, 4, 6, 'feather:cheek', tag='cheek'),
             *disc, *owl_tufts())
    hackles = ring('hackle', griffin.HACKLE_CENTER, OWL_HACKLES, 'feather:hackle')
    body_cubes = feather_body() + [
        # dark leather with silver crescents; a moon lantern and star charms
        C(-7, 0.5, -13.6, 14, 2, 1, 'leather:strap', tag='strap_front'),
        C(-7, 0.5, -13, 1, 2, 9, 'leather:strap', tag='strap_side'),
        C(6, 0.5, -13, 1, 2, 9, 'leather:strap', share='strap_side', mirror=True),
        C(-2.0, -0.5, -14.4, 4, 4, 1, 'metal:silver', tag='crescent'),
        C(-6.5, -1.5, -2, 13, 12, 2, 'leather:strap', tag='girth'),
        C(-6.5, -1.5, -5, 13, 1, 11, 'cloth:nightcloth', tag='blanket'),
    ]
    lantern = P('lantern', (-7.5, 2, -6), C(-8, 2, -6.5, 1, 3, 1, 'chain:chain'),
                C(-9, 5, -7.5, 3, 4, 3, 'metal:silver_dark', tag='lamp'),
                C(-9.5, 4.5, -8, 4, 1, 4, 'metal:silver_dark', tag='lamp_cap'),
                C(-8.5, 9, -7, 2, 1, 2, 'metal:silver_dark', tag='lamp_foot'))
    charms = []
    for i, z in enumerate((-1.0, 2.5)):
        x = 7.2
        charms.append(P('charm_%d' % (i + 1), (x, 3.0, z), C(x - 0.5, 3.0, z - 0.5, 1, 3, 1, 'chain:chain'),
                        C(x - 1.0, 6.0, z - 1.0, 1, 2, 2, 'metal:star', tag='star%d' % i)))
    spec = dict(primaries=OWL_PRIMARIES, secondaries=OWL_SECONDARIES, edge='feather:edge')
    return assemble(head, [], hackles, body_cubes, [lantern] + charms,
                    feather_tail(lengths=(7, 8, 8, 8, 7), mats=['feather:tailf'] * 5, rot=-6),
                    [wing('r', spec), wing('l', spec)], eagle_legs(OWL_LEG), standard_saddle())


def owl_paint(p):
    e = p.variant.extra
    white, pale, accent, navy = rgb(e['wing']), rgb(e['pale']), rgb(e['accent']), rgb(e['covert'])
    fleck_l, fleck_d = rgb(e['fleck_light']), rgb(e['fleck_dark'])
    glowing = e.get('glow')

    def soft(fc, gc, i, primary, color):
        bp.barring(fc, shade(color, 0.7), period=5, phase=i % 3, strength=0.45)
        bp.mottle(fc, fleck_l, fleck_d, (p.variant.id, 'f', i, primary, fc.face), density=0.06)
        # a fringed, silent trailing edge: every other pixel of the edge cut
        for x in range(fc.fw):
            if x % 2 and bp.opaque(fc, x, fc.fh - 1):
                fc.fclear(x, fc.fh - 1)
        if glowing:
            for x, y in bp.each(fc):
                if bp.opaque(fc, x, y) and fc.fget(x, y)[0] > 200:
                    gc.fpx(x, y, shade(rgb(e['glow_col']), 0.6))

    paint_wings(p, OWL_PRIMARIES, OWL_SECONDARIES, white, pale, accent, navy, flight_style=soft)
    for part, tag in (('wing_r', 'edge1'), ('wing_r2', 'edge2')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.fill(fc, shade(navy, 1.05))
            bp.mottle(fc, fleck_l, fleck_d, (p.variant.id, part, name), density=0.2)
            # the comb of the leading edge
            if name in ('front', 'top'):
                for x in range(0, fc.fw, 2):
                    fc.fpx(x, 0, mix(fc.fget(x, 0), fleck_l, 0.6))
    # the plumage: mottled all over, chevrons on the breast
    for part, tag in (('body', 'barrel'), ('body', 'rump'), ('neck', 'neck_core'), ('neck', 'ruff'), ('head', 'skull'),
                      ('head', 'cheek')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.mottle(fc, fleck_l, fleck_d, (p.variant.id, part, name), density=0.12)
            if glowing:
                for x, y in bp.each(fc):
                    if fc.fget(x, y) == fleck_l:
                        gc.fpx(x, y, shade(rgb(e['glow_col']), 0.7))
    for fc, gc, name in faces(p, 'body', tag='breast', names=('front', 'right', 'left')):
        for x, y in bp.each(fc):
            if (y + abs(x - fc.fw // 2) // 2) % 5 == 0:
                fc.fpx(x, y, mix(fc.fget(x, y), fleck_d, 0.6))
    for part in ['hackle_%d' % i for i in range(1, ring_count(OWL_HACKLES) + 1)]:
        for name in ('top', 'bottom'):
            griffin.long_feather(p.face(part, name), rgb(e['head'][0]), shade(rgb(e['head'][0]), 0.7))
            bp.mottle(p.face(part, name), fleck_l, fleck_d, (p.variant.id, part, name), density=0.1)
    for part in ['crest_%d' % i for i in range(1, 5)]:
        for name in ('top', 'bottom', 'right', 'left'):
            bp.fur_tufts(p.face(part, name), rgb(e['head'][0]), fleck_l, fleck_d, (p.variant.id, part, name),
                         tip_at_top=True)
    # the facial disc: pale feathers radiating from the eyes, a dark rim; huge glowing eyes; a small dark beak
    disc = p.face('head', 'front', tag='disc')
    gd = p.face('head', 'front', tag='disc', glow=True)
    dc = rgb(e['disc'])
    rim_c = rgb(e['rim'])
    cx = (disc.fw - 1) / 2.0
    for x, y in bp.each(disc):
        # the heart: two lobes around the eyes meeting in a point at the chin
        lobes = min(math.hypot(x - (cx - 4.5), (y - 7.0) * 0.85), math.hypot(x - (cx + 4.5), (y - 7.0) * 0.85))
        point = abs(x - cx) * 1.2 + max(0.0, y - 11.0) * 1.6
        inside = lobes < 5.6 or (y > 7 and point < 7.0 - (y - 7) * 0.4)
        if not inside:
            disc.fpx(x, y, rim_c)
            continue
        edge = lobes > 4.8 and not (y > 7 and point < 5.5 - (y - 7) * 0.4)
        ang = math.atan2(y - 7.0, x - (cx - 4.5 if x < cx else cx + 4.5))
        c = mix(dc, shade(dc, 0.86), 0.5 + 0.5 * math.sin(ang * 7))
        if abs(x - cx) < 1.0 and y < 12:
            c = shade(dc, 0.8)                      # the ridge between the eyes
        disc.fpx(x, y, shade(rim_c, 1.15) if edge else c)
    eye_c = rgb(e['eye'])
    for ex in (int(cx - 4.5), int(cx + 4.5)):
        for x, y in bp.each(disc):
            d = math.hypot(x - ex - 0.5, y - 6.5)
            if d < 3.2:
                c = (10, 10, 14) if d < 1.4 else eye_c if d < 2.6 else shade(eye_c, 0.55)
                disc.fpx(x, y, c)
                if d >= 1.4:
                    gd.fpx(x, y, c)
        disc.fpx(ex - 1, 5, (255, 255, 255))
        gd.fpx(ex - 1, 5, (255, 255, 255))
    for part, tag in (('head', 'rim'), ('head', 'rim_top')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.fill(fc, rgb(e['rim']))
            bp.mottle(fc, fleck_l, fleck_d, (p.variant.id, tag, name), density=0.15)
    for fc, gc, name in faces(p, 'head', tag='owl_beak'):
        bp.fill(fc, rgb(e['beak']))
    for part in ('front_right_shin', 'hind_right_shin', 'front_right_leg', 'hind_right_leg'):
        for c_i, c in enumerate(p.rig.parts[part].cubes):
            if c.mat == 'feather:legfeather':
                for name in ('front', 'right', 'left', 'back'):
                    fc = p.face(part, name, index=c_i)
                    bp.fur_tufts(fc, rgb(e['leg']), mix(rgb(e['leg']), (255, 255, 255), 0.3), shade(rgb(e['leg']), 0.75),
                                 (p.variant.id, part, c_i, name))
    talons(p, (24, 22, 26))
    # silver crescents, the moon lantern, star charms
    silver = rgb(e['silver'])
    for fc, gc, name in faces(p, 'body', tag='crescent', names=('front',)):
        for x, y in bp.each(fc):
            d1 = math.hypot(x - 3.5, y - 3.5)
            d2 = math.hypot(x - 5.0, y - 2.6)
            if d1 < 3.6 and d2 > 2.8:
                fc.fpx(x, y, mix(silver, (255, 255, 255), 0.3))
                gc.fpx(x, y, shade(rgb(e['moon']), 0.6))
            else:
                fc.fpx(x, y, shade(rgb(e['strap']), 0.9))
    base.lantern_faces(p, 'lantern', 1, frame=shade(silver, 0.6), light=rgb(e['moon']), glow=rgb(e['moon']))
    for i in range(2):
        for fc, gc, name in faces(p, 'charm_%d' % (i + 1), tag='star%d' % i, names=('right', 'left', 'front', 'back')):
            for x, y in bp.each(fc):
                if (x, y) in ((0, 0), (fc.fw - 1, 0), (0, fc.fh - 1), (fc.fw - 1, fc.fh - 1)):
                    fc.fclear(x, y)
                else:
                    fc.fpx(x, y, rgb(e['moon']))
                    gc.fpx(x, y, shade(rgb(e['moon']), 0.8))
    fc = p.face('body', 'top', tag='blanket')
    bp.stars(fc, (p.variant.id, 'blanket'), colors=(silver,), density=0.05, glow=None)
    bp.trim(fc, silver, 1)
    for tag in ('strap_front', 'strap_side', 'girth'):
        for fc, gc, name in faces(p, 'body', tag=tag, names=('front', 'right', 'left', 'top')):
            for x in range(1, fc.fw - 1, 5):
                fc.fpx(x, fc.fh // 2, silver)


OWL_COATS = [
    ('barn', dict(body=('#d8b07a', '#ecc896'), head=('#e8c890', '#f8e0b0', '#b48a56'), disc='#fbf4ea', rim='#a87a46',
                  wing='#e8c890', pale='#c49a62', accent='#d8b07a', covert='#a87a46', fleck_light='#f8f0e0',
                  fleck_dark='#5a4028', leg='#f4ead8', eye='#1a1210', beak='#d8c8b0', silver='#d8dce8',
                  moon='#a8d8ff', strap='#3a2a20', cloth='#2a2234', belly='#f6ead2')),
    ('tawny', dict(body=('#7a5634', '#946a42'), head=('#8a6440', '#a47c52', '#5e4228'), disc='#c8a47a', rim='#4a3220',
                   wing='#9a7448', pale='#6e5032', accent='#7a5634', covert='#4e3622', fleck_light='#e8d4b0',
                   fleck_dark='#2a1c12', leg='#c8a47a', eye='#ffb03a', beak='#3a3028', silver='#c8ccd8',
                   moon='#ffd890', strap='#2a1e16', cloth='#3a2416', belly='#c8a47a')),
    ('snowy', dict(body=('#f2f2f4', '#ffffff'), head=('#f8f8fa', '#ffffff', '#d8d8de'), disc='#ffffff', rim='#d8d8e0',
                   wing='#fafafc', pale='#d4d6de', accent='#eeeef2', covert='#c8cad4', fleck_light='#ffffff',
                   fleck_dark='#3a3a44', leg='#ffffff', eye='#ffd23a', beak='#2a2a30', silver='#e0e6f0',
                   moon='#c8e8ff', strap='#2a2e3a', cloth='#3a4a6a', belly='#ffffff')),
    ('ashen', dict(body=('#7c7c84', '#94949c'), head=('#8c8c94', '#a8a8b0', '#626268'), disc='#c8c8ce', rim='#4a4a52',
                   wing='#9a9aa2', pale='#6e6e76', accent='#84848c', covert='#55555c', fleck_light='#e4e4ea',
                   fleck_dark='#2a2a30', leg='#b8b8c0', eye='#ff8a2a', beak='#2a2a2e', silver='#d0d4de',
                   moon='#ffb87a', strap='#26262c', cloth='#3a3a46', belly='#b0b0b8')),
    ('moonlit', dict(body=('#1e2448', '#2a3260'), head=('#283060', '#364080', '#181c3a'), disc='#c8d4ff', rim='#141838',
                     wing='#2e3870', pale='#1c2248', accent='#28306a', covert='#161a3a', fleck_light='#e8f0ff',
                     fleck_dark='#0c0e20', leg='#3a4480', eye='#c8f0ff', beak='#c8d0f0', silver='#e0e8ff',
                     moon='#d8f0ff', strap='#10122a', cloth='#0e1028', belly='#3a4480', glow=True,
                     glow_col='#c8e0ff')),
]


def owl_variant(cid, spec):
    body, head = spec['body'], spec['head']
    pal = {
        'body': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'breast': {'base': spec['belly'], 'style': 'feather', 'light': '#ffffff'},
        'belly': {'base': spec['belly'], 'style': 'feather'},
        'neck': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'ruff': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'head': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'cheek': {'base': spec['disc'], 'style': 'feather'},
        'disc': {'base': spec['disc'], 'style': 'plain'},
        'rim': {'base': spec['rim'], 'style': 'feather'},
        'owlbeak': spec['beak'],
        'crest': {'base': head[0], 'style': 'feather'},
        'hackle': {'base': head[0], 'style': 'feather'},
        'covert_n': {'base': spec['covert'], 'style': 'feather'},
        'covert_w': {'base': spec['wing'], 'style': 'feather'},
        'edge': {'base': spec['covert'], 'style': 'feather'},
        'flight_w': {'base': spec['wing'], 'style': 'feather'},
        'flight_v': {'base': spec['accent'], 'style': 'feather'},
        'tail': {'base': spec['wing'], 'style': 'feather'},
        'tailf': {'base': spec['wing'], 'style': 'feather'},
        'legfeather': {'base': spec['leg'], 'style': 'feather'},
        'toe': '#3a3a40',
        'claw': '#141418',
        'strap': {'base': spec['strap'], 'style': 'leather'},
        'silver': {'base': spec['silver'], 'style': 'metal', 'rivets': False},
        'silver_dark': {'base': shade_hex(spec['silver'], 0.6), 'style': 'metal', 'rivets': False},
        'star': {'base': spec['moon'], 'style': 'plain'},
        'chain': {'base': '#8a8a96', 'style': 'chain'},
        'saddle': {'base': '#3a2e2a', 'style': 'leather'},
        'saddle_rim': {'base': '#2a2220', 'style': 'leather'},
        'nightcloth': {'base': spec['cloth'], 'style': 'cloth'},
    }
    extra = dict(spec, scale=1.0, hitbox=(1.5, 1.8), seat='saddle')
    return Variant(cid, palette=pal, paint=owl_paint, glow=True, extra=extra)


def owl_rig():
    head_tilt = {'head': dict(rot=[(0, 0, 0, 0), (0.6, 0, 0, 0), (0.8, 0, 0, 22), (1.6, 0, 0, 22), (1.8, 0, 0, 0),
                                   (3.0, 0, 0, 0)])}
    return Rig('mount_griffin_owl', 'GriffinOwlMount', owl_geometry(),
               griffin.animations(primaries=OWL_PRIMARIES, secondaries=OWL_SECONDARIES,
                                  crest_n=4, hackle_n=ring_count(OWL_HACKLES),
                                  absent=('banner_r', 'banner_l', 'pennant'), extra={'idle': head_tilt}),
               [owl_variant(cid, spec) for cid, spec in OWL_COATS], SLOTS, texel=2)


# ================================================================================================ storm

STORM_CREST = ((4.0, 2, 12, 10, (0, 30, 60, 92)), (3.0, 2, 9, 2, (15, 45, 76)))
STORM_HACKLES = ((4.0, 3, 9, 14, (0, 40, 80, 114)), (3.0, 3, 7, 4, (20, 60, 97)))
STORM_PRIMARIES = ((2, 21), (6, 23), (11, 25), (16, 27), (21, 29), (27, 30), (34, 29), (42, 26), (51, 22))
STORM_SECONDARIES = (13, 14, 14, 15, 15, 15, 14, 14)
STORM_LEG = dict(thigh='feather:thigh', haunch='feather:haunch', cuff='feather:cuff', tarsus='scale:tarsus',
                 band='metal:silver', foot='metal:silver', toe='scale:toe', claw='plain:claw')


def storm_geometry():
    crown = [C(-4.5, -14.5, -17.5, 9, 1, 5, 'metal:silver', tag='circlet'),
             C(-1.5, -16.0, -17.9, 3, 3, 1, 'gem:storm_gem', tag='circlet_gem')]
    head = griffin_head(beak='slate', crown=crown,
                        crest=ring('crest', griffin.CREST_CENTER, STORM_CREST, 'feather:crest'))
    hackles = ring('hackle', griffin.HACKLE_CENTER, STORM_HACKLES, 'feather:hackle')
    body_cubes = feather_body() + [
        # silver plates studded with storm crystals on the shoulders and the breast
        C(-7, 0.5, -13.6, 14, 2, 1, 'leather:strap', tag='strap_front'),
        C(-7, 0.5, -13, 1, 2, 9, 'leather:strap', tag='strap_side'),
        C(6, 0.5, -13, 1, 2, 9, 'leather:strap', share='strap_side', mirror=True),
        C(-3.0, -1.5, -14.5, 6, 6, 1, 'metal:silver', tag='breastplate'),
        C(-1.0, -0.5, -15.0, 2, 3, 1, 'gem:storm_gem', tag='breast_gem'),
        C(-7.8, -2.0, -12.0, 2, 5, 6, 'metal:silver', tag='shoulder'),
        C(5.8, -2.0, -12.0, 2, 5, 6, 'metal:silver', share='shoulder', mirror=True),
        C(-8.3, -0.5, -10.0, 1, 2, 2, 'gem:storm_gem', tag='shoulder_gem'),
        C(7.3, -0.5, -10.0, 1, 2, 2, 'gem:storm_gem', share='shoulder_gem', mirror=True),
        C(-6.5, -1.5, -2, 13, 12, 2, 'leather:strap', tag='girth'),
        C(-6.5, -1.5, -5, 13, 1, 11, 'cloth:stormcloth', tag='blanket'),
    ]
    banners = [P('banner_r', (-6.5, 2, 2), C(-7, 2, -0.5, 1, 11, 5, 'cloth:stormcloth', tag='banner'),
                 C(-7.4, 1.4, -1, 1, 1, 6, 'metal:silver', tag='rod')),
               P('banner_l', (6.5, 2, 2), C(6, 2, -0.5, 1, 11, 5, 'cloth:stormcloth', share='banner', mirror=True),
                 C(6.4, 1.4, -1, 1, 1, 6, 'metal:silver', share='rod', mirror=True))]
    spec = dict(primaries=STORM_PRIMARIES, secondaries=STORM_SECONDARIES, edge='metal:silver_edge', armour=True,
                plate='metal:silver')
    # a forked tail: the outer feathers long
    tail = feather_tail(lengths=(14, 9, 8, 9, 14), mats=['feather:flight_v', 'feather:flight_w', 'feather:flight_w',
                                                           'feather:flight_w', 'feather:flight_v'], rot=-10)
    return assemble(head, [], hackles, body_cubes, banners, tail, [wing('r', spec), wing('l', spec)],
                    eagle_legs(STORM_LEG), standard_saddle())


def storm_paint(p):
    e = p.variant.extra
    white, pale, accent, navy = rgb(e['wing']), rgb(e['pale']), rgb(e['accent']), rgb(e['covert'])
    bolt_c, bolt_g = rgb(e['bolt']), rgb(e['bolt_glow'])

    def lightning(fc, gc, i, primary, color):
        if (i % 2 == 0) == primary or i >= 6:
            bp.bolt(fc, bolt_c, (p.variant.id, 'bolt', i, primary, fc.face), glow=gc, glow_col=bolt_g)

    paint_wings(p, STORM_PRIMARIES, STORM_SECONDARIES, white, pale, accent, navy, flight_style=lightning)
    silver = rgb(e['silver'])
    for part, tag in (('wing_r', 'edge1'), ('wing_r2', 'edge2')):
        for fc, gc, name in faces(p, part, tag=tag):
            riveted(fc, silver)
    for part, tag in (('wing_r', 'plate1'), ('wing_r', 'plate2'), ('wing_r2', 'plate3'), ('wing_r2', 'plate4'),
                      ('wing_r2', 'wrist')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.trim(fc, shade(silver, 0.6), 1)
    for part, tag in (('wing_r', 'wgem1'), ('wing_r2', 'wgem2'), ('body', 'breast_gem'), ('body', 'shoulder_gem'),
                      ('head', 'circlet_gem')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.gem(fc, gc, rgb(e['gem']), bolt_g)
    # the jagged crest: sharp feathers with glowing tips
    for i in range(1, ring_count(STORM_CREST) + 1):
        for name in ('top', 'bottom'):
            fc = p.face('crest_%d' % i, name)
            gc = p.face('crest_%d' % i, name, glow=True)
            griffin.long_feather(fc, rgb(e['head'][0]), rgb(e['head'][2]))
            for x, y in bp.each(fc):
                if y < 3 and bp.opaque(fc, x, y):
                    fc.fpx(x, y, bolt_c)
                    gc.fpx(x, y, bolt_g)
    for i in range(1, ring_count(STORM_HACKLES) + 1):
        for name in ('top', 'bottom'):
            griffin.long_feather(p.face('hackle_%d' % i, name), rgb(e['head'][0]), rgb(e['head'][2]))
    # the plumage: a white streak behind the eye, a barred pale underside
    for fc, gc, name in faces(p, 'body', tag='belly', names=('bottom', 'right', 'left')):
        bp.barring(fc, shade(rgb(e['body'][0]), 0.8), period=3, strength=0.5)
    for fc, gc, name in faces(p, 'body', tag='breast', names=('front',)):
        bp.barring(fc, shade(rgb(e['body'][0]), 0.85), period=4, strength=0.4)
    griffin_eyes(p, e['eye'], mask=rgb(e['mask']), glow=True)
    for name in ('right', 'left'):
        skull = p.face('head', name, tag='skull')
        for x in range(skull.fw):
            for y in (8, 9):
                if 0 <= y < skull.fh:
                    skull.fpx(x, y, mix(skull.fget(x, y), (250, 250, 255), 0.75))
    beak_paint(p, rgb(e['beak_light']), rgb(e['beak_dark']))
    talons(p, (20, 22, 28))
    for part, tag in (('body', 'breastplate'), ('body', 'shoulder'), ('head', 'circlet')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.trim(fc, mix(silver, (255, 255, 255), 0.5), 1, 'tb')
            if fc.fw >= 6:
                bp.bolt(fc, bolt_c, (p.variant.id, 'plate', part, tag, name), glow=gc, glow_col=bolt_g)
    for name in ('right', 'left'):
        fc = p.face('banner_r', name, tag='banner')
        gc = p.face('banner_r', name, tag='banner', glow=True)
        bp.trim(fc, silver, 1, 'tlr')
        bp.bolt(fc, bolt_c, (p.variant.id, 'banner'), glow=gc, glow_col=bolt_g)
        base.pointed_bottom(fc, 4, step=fc.fw // 2)
    for name in ('front', 'back'):
        base.pointed_bottom(p.face('banner_r', name, tag='banner'), 4, step=2)
    fc = p.face('body', 'top', tag='blanket')
    bp.trim(fc, silver, 1)
    for x in range(3, fc.fw - 3, 8):
        for t in range(fc.fh - 4):
            fc.fpx(x + (t // 2) % 2, 2 + t, bolt_c)
    for tag in ('strap_front', 'strap_side', 'girth'):
        for fc, gc, name in faces(p, 'body', tag=tag, names=('front', 'right', 'left', 'top')):
            for x in range(1, fc.fw - 1, 5):
                fc.fpx(x, fc.fh // 2, silver)


STORM_COATS = [
    ('thunder', dict(body=('#4a5262', '#5e6878'), head=('#56606e', '#6e7a8a', '#343a46'), wing='#5a6474', pale='#3e4654',
                     accent='#2e3646', covert='#262c38', bolt='#bff6ff', bolt_glow='#7ae8ff', gem='#4ad8ff',
                     silver='#c8d0dc', eye='#9ff4ff', mask='#1e222c', beak_light='#9aa4b4', beak_dark='#1a1e26',
                     cloth='#1e2a40', belly='#c8ccd6')),
    ('squall', dict(body=('#c8ced8', '#dde2ea'), head=('#e4e8ee', '#f6f8fa', '#a8b0bc'), wing='#e6eaf0', pale='#b4bcc8',
                    accent='#8a96a8', covert='#6e7a8e', bolt='#4a8aff', bolt_glow='#6aa8ff', gem='#4a8aff',
                    silver='#e8ecf2', eye='#5ab0ff', mask='#3a4458', beak_light='#d0d6de', beak_dark='#3a4250',
                    cloth='#3a5a8a', belly='#ffffff')),
    ('tempest', dict(body=('#2e2448', '#40345e'), head=('#3a2e58', '#504276', '#221a36'), wing='#443a66', pale='#2e2648',
                     accent='#5a3a8a', covert='#1c1630', bolt='#f0c8ff', bolt_glow='#c88aff', gem='#b06aff',
                     silver='#c8bcdc', eye='#e0b0ff', mask='#140e22', beak_light='#9a8ab4', beak_dark='#160e22',
                     cloth='#241a3c', belly='#8a7aa8')),
    ('gale', dict(body=('#2e6a6e', '#3e868a'), head=('#3a7a7e', '#56a0a4', '#22484c'), wing='#4a9094', pale='#2e6266',
                  accent='#1e4a52', covert='#163a40', bolt='#ffffff', bolt_glow='#c8fff8', gem='#7affe8',
                  silver='#d0e4e4', eye='#d0fff8', mask='#0e2628', beak_light='#9cc4c4', beak_dark='#102426',
                  cloth='#163a40', belly='#c8e4e2')),
    ('fulgur', dict(body=('#16161c', '#24242c'), head=('#202028', '#30303a', '#0e0e12'), wing='#26262e', pale='#16161c',
                    accent='#3a3220', covert='#101014', bolt='#fff2a0', bolt_glow='#ffd84a', gem='#ffd84a',
                    silver='#e8c860', eye='#ffe680', mask='#08080a', beak_light='#e8c860', beak_dark='#3a3010',
                    cloth='#14141a', belly='#3a3a44')),
]


def storm_variant(cid, spec):
    body, head = spec['body'], spec['head']
    pal = {
        'body': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'breast': {'base': spec['belly'], 'style': 'feather'},
        'belly': {'base': spec['belly'], 'style': 'feather'},
        'neck': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'ruff': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'head': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'cheek': {'base': head[0], 'style': 'feather', 'light': head[1]},
        'crest': {'base': head[0], 'style': 'feather'},
        'hackle': {'base': head[0], 'style': 'feather'},
        'covert_n': {'base': spec['covert'], 'style': 'feather'},
        'covert_w': {'base': spec['wing'], 'style': 'feather'},
        'flight_w': {'base': spec['wing'], 'style': 'feather'},
        'flight_v': {'base': spec['accent'], 'style': 'feather'},
        'tail': {'base': spec['wing'], 'style': 'feather'},
        'thigh': {'base': head[0], 'style': 'feather', 'light': head[1]},
        'haunch': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'cuff': {'base': spec['belly'], 'style': 'feather'},
        'tarsus': {'base': '#8a92a0', 'style': 'scale'},
        'toe': {'base': '#7a828e', 'style': 'scale'},
        'claw': '#14161c',
        'beak': {'base': '#4a5260', 'style': 'plain'},
        'beak_low': {'base': '#3a404c', 'style': 'plain'},
        'beak_tip': {'base': spec['beak_dark'], 'style': 'plain'},
        'cere': {'base': spec['beak_light'], 'style': 'plain'},
        'silver': {'base': spec['silver'], 'style': 'metal', 'rivets': False},
        'silver_edge': {'base': spec['silver'], 'style': 'metal', 'rivets': False},
        'storm_gem': {'base': spec['gem'], 'style': 'gem'},
        'gem': {'base': spec['gem'], 'style': 'gem'},
        'strap': {'base': '#2a2e38', 'style': 'leather'},
        'saddle': {'base': '#3a3640', 'style': 'leather'},
        'saddle_rim': {'base': '#2a2630', 'style': 'leather'},
        'stormcloth': {'base': spec['cloth'], 'style': 'cloth'},
    }
    extra = dict(spec, scale=1.0, hitbox=(1.5, 1.8), seat='saddle')
    return Variant(cid, palette=pal, paint=storm_paint, glow=True, extra=extra)


def storm_rig():
    return Rig('mount_griffin_storm', 'GriffinStormMount', storm_geometry(),
               griffin.animations(primaries=STORM_PRIMARIES, secondaries=STORM_SECONDARIES,
                                  crest_n=ring_count(STORM_CREST), hackle_n=ring_count(STORM_HACKLES),
                                  absent=('pennant', 'lantern')),
               [storm_variant(cid, spec) for cid, spec in STORM_COATS], SLOTS, texel=2)


# ================================================================================================ frost

FROST_CREST = ((4.0, 2, 9, 30, (0, 28, 56, 86)), (3.2, 2, 7, 18, (14, 42, 72)))
FROST_HACKLES = ((4.0, 3, 9, 14, (0, 40, 80, 114)), (3.0, 3, 7, 4, (20, 60, 97)))
FROST_PRIMARIES = griffin.PRIMARIES
FROST_SECONDARIES = griffin.SECONDARIES
FROST_LEG = dict(thigh='feather:thigh', haunch='feather:haunch', cuff='feather:cuff', tarsus='metal:frostsilver',
                 band='crystal:ice', foot='metal:frostsilver', toe='metal:frostsilver', claw='crystal:ice')


def frost_geometry():
    crown = [C(-4.5, -14.5, -17.5, 9, 1, 6, 'metal:frostsilver', tag='circlet'),
             C(-1.5, -17.0, -17.9, 3, 3, 1, 'crystal:ice', tag='circlet_gem')]
    head = griffin_head(beak='frost', crown=crown,
                        crest=ring('crest', griffin.CREST_CENTER, FROST_CREST, 'crystal:crest'))
    hackles = ring('hackle', griffin.HACKLE_CENTER, FROST_HACKLES, 'feather:hackle')
    body_cubes = feather_body() + [
        C(-7, 0.5, -13.6, 14, 2, 1, 'leather:strap', tag='strap_front'),
        C(-7, 0.5, -13, 1, 2, 9, 'leather:strap', tag='strap_side'),
        C(6, 0.5, -13, 1, 2, 9, 'leather:strap', share='strap_side', mirror=True),
        C(-2.5, -0.5, -14.4, 5, 5, 1, 'metal:frostsilver', tag='breastplate'),
        C(-1.5, 0.5, -14.9, 3, 3, 1, 'crystal:ice', tag='breast_crystal'),
        C(-6.5, -1.5, -2, 13, 12, 2, 'leather:strap', tag='girth'),
        C(-6.5, -1.5, -5, 13, 1, 11, 'cloth:frostcloth', tag='blanket'),
    ]
    # ice grown over the shoulders: crystal clusters
    clusters = []
    for side, s in (('r', -1), ('l', 1)):
        x = 6.5 * s
        cubes = []
        for i, (dx, dy, dz, w, h, d) in enumerate(((0.0, -4.0, -12.0, 2, 4, 2), (0.8, -3.0, -10.0, 1, 3, 1),
                                                     (-0.5, -2.5, -13.5, 1, 2, 1))):
            cubes.append(C(x + dx * s - w / 2.0, dy, dz, w, h, d, 'crystal:ice', tag=('cl%d' % i) if s < 0 else None,
                           share=None if s < 0 else 'cl%d' % i, mirror=s > 0))
        clusters.append(P('ice_%s' % side, (x, -1.0, -11.0), *cubes, rot=(0, 0, -16 * s)))
    spec = dict(primaries=FROST_PRIMARIES, secondaries=FROST_SECONDARIES, edge='metal:frostsilver', armour=True,
                plate='crystal:ice', tips='crystal:tip')
    tail = feather_tail(tips='crystal:tip')
    return assemble(head, [], hackles, body_cubes, clusters, tail, [wing('r', spec), wing('l', spec)],
                    eagle_legs(FROST_LEG), standard_saddle())


def frost_paint(p):
    e = p.variant.extra
    white, pale, accent, navy = rgb(e['wing']), rgb(e['pale']), rgb(e['accent']), rgb(e['covert'])
    ice, ice_l, ice_d, ice_g = rgb(e['ice']), mix(rgb(e['ice']), (255, 255, 255), 0.6), shade(rgb(e['ice']), 0.6), \
        rgb(e['ice_glow'])
    strong = e.get('strong', 0.0)

    def frosted(fc, gc, i, primary, color):
        bp.frost(fc, (255, 255, 255), (p.variant.id, 'frost', i, primary, fc.face), density=0.8)

    paint_wings(p, FROST_PRIMARIES, FROST_SECONDARIES, white, pale, accent, navy, flight_style=frosted)
    for i in range(len(FROST_PRIMARIES)):
        for fc, gc, name in faces(p, 'prim_r%d' % (i + 1), tag='pt%d' % i):
            bp.crystal(fc, ice, ice_l, ice_d, (p.variant.id, 'tip', i, name), glow=gc,
                       glow_col=shade(ice_g, 0.5 + strong * 0.5))
    for i in range(3):
        for fc, gc, name in faces(p, 'tail2', tag='tt%d' % i):
            bp.crystal(fc, ice, ice_l, ice_d, (p.variant.id, 'ttip', i, name), glow=gc,
                       glow_col=shade(ice_g, 0.5 + strong * 0.5))
    silver = rgb(e['silver'])
    for part, tag in (('wing_r', 'edge1'), ('wing_r2', 'edge2')):
        for fc, gc, name in faces(p, part, tag=tag):
            riveted(fc, silver)
            bp.frost(fc, (255, 255, 255), (p.variant.id, part, name), density=1.0)
    for part, tag in (('wing_r', 'plate1'), ('wing_r', 'plate2'), ('wing_r2', 'plate3'), ('wing_r2', 'plate4'),
                      ('wing_r2', 'wrist'), ('wing_r', 'wgem1'), ('wing_r2', 'wgem2'), ('head', 'circlet_gem'),
                      ('body', 'breast_crystal'), ('ice_r', 'cl0'), ('ice_r', 'cl1'), ('ice_r', 'cl2')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.crystal(fc, ice, ice_l, ice_d, (p.variant.id, part, tag, name), glow=gc,
                       glow_col=shade(ice_g, 0.45 + strong * 0.5))
    # the crest: shards of ice
    for i in range(1, ring_count(FROST_CREST) + 1):
        for fc, gc, name in faces(p, 'crest_%d' % i):
            bp.crystal(fc, ice, ice_l, ice_d, (p.variant.id, 'crest', i, name), glow=gc,
                       glow_col=shade(ice_g, 0.55 + strong * 0.45))
            if name in ('top', 'bottom'):
                for x, y in bp.each(fc):
                    if y < 3 and abs(x - fc.fw / 2.0 + 0.5) > (y + 1) * 0.6:
                        fc.fclear(x, y)
    for i in range(1, ring_count(FROST_HACKLES) + 1):
        for name in ('top', 'bottom'):
            griffin.long_feather(p.face('hackle_%d' % i, name), rgb(e['head'][0]), rgb(e['head'][2]))
            bp.frost(p.face('hackle_%d' % i, name), (255, 255, 255), (p.variant.id, 'hk', i, name))
    for part, tag in (('body', 'barrel'), ('body', 'breast'), ('body', 'rump'), ('neck', 'ruff'), ('head', 'skull')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.frost(fc, (255, 255, 255), (p.variant.id, part, name), density=0.7)
    griffin_eyes(p, e['eye'], mask=rgb(e['mask']), glow=True)
    beak_paint(p, rgb(e['beak_light']), rgb(e['beak_dark']))
    for pre in ('front_right', 'hind_right'):
        for t in ('toe1', 'toe4'):
            for c_i, c in enumerate(p.rig.parts['%s_%s' % (pre, t)].cubes):
                if c.mat.startswith('crystal'):
                    for name in ('front', 'top', 'right', 'left', 'bottom'):
                        fc = p.face('%s_%s' % (pre, t), name, index=c_i)
                        bp.crystal(fc, ice, ice_l, ice_d, (p.variant.id, pre, t, c_i, name))
    for pre in ('f', 'h'):
        shin = 'front_right_shin' if pre == 'f' else 'hind_right_shin'
        for tag in ('band1', 'band2'):
            for fc, gc, name in faces(p, shin, tag=pre + tag):
                bp.crystal(fc, ice, ice_l, ice_d, (p.variant.id, shin, tag, name), glow=gc,
                           glow_col=shade(ice_g, 0.4 + strong * 0.5))
    for part, tag in (('body', 'breastplate'), ('head', 'circlet')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.trim(fc, mix(silver, (255, 255, 255), 0.5), 1, 'tb')
            bp.frost(fc, (255, 255, 255), (p.variant.id, tag, name))
    fc = p.face('body', 'top', tag='blanket')
    bp.trim(fc, silver, 1)
    for cx in range(4, fc.fw - 3, 8):
        for a in range(0, 360, 60):
            for r in range(1, 4):
                fc.fpx(int(round(cx + math.cos(math.radians(a)) * r)), int(round(fc.fh / 2 + math.sin(math.radians(a)) * r)),
                       (240, 248, 255))
    for tag in ('strap_front', 'strap_side', 'girth'):
        for fc, gc, name in faces(p, 'body', tag=tag, names=('front', 'right', 'left', 'top')):
            for x in range(1, fc.fw - 1, 5):
                fc.fpx(x, fc.fh // 2, silver)


FROST_COATS = [
    ('glacier', dict(body=('#cfe2f0', '#e6f2fa'), head=('#eef6fc', '#ffffff', '#b4cede'), wing='#e8f4fc', pale='#b8d4e8',
                     accent='#8ec4e8', covert='#6aa4cc', ice='#8ad8ff', ice_glow='#9fe8ff', silver='#d8e8f4',
                     eye='#aef0ff', mask='#5a8ab0', beak_light='#e0f0fa', beak_dark='#5a7a94', cloth='#2a5a8a',
                     belly='#f4faff')),
    ('polar', dict(body=('#f2f6fa', '#ffffff'), head=('#ffffff', '#ffffff', '#d4dee8'), wing='#ffffff', pale='#d8e2ec',
                   accent='#e4ecf4', covert='#b4c4d6', ice='#c8eeff', ice_glow='#d8f4ff', silver='#eef4fa',
                   eye='#8ad8ff', mask='#a4b8cc', beak_light='#ffffff', beak_dark='#8a9aac', cloth='#a8c4e0',
                   belly='#ffffff')),
    ('deepice', dict(body=('#2a4a7a', '#3a5e94'), head=('#4a6ea4', '#6a8ec0', '#2a4470'), wing='#4a72aa', pale='#2e4c7c',
                     accent='#5ab0e8', covert='#1e3460', ice='#4ae0ff', ice_glow='#6af0ff', silver='#a8c4e4',
                     eye='#7af4ff', mask='#142440', beak_light='#a8c8e8', beak_dark='#1a2c48', cloth='#142848',
                     belly='#8aa8d0')),
    ('dawnfrost', dict(body=('#f0dce6', '#fbecf2'), head=('#fff4f8', '#ffffff', '#e0c8d4'), wing='#fff2f6',
                       pale='#e8ccd8', accent='#f8b8d0', covert='#c898b4', ice='#ffc8e8', ice_glow='#ffd8f0',
                       silver='#f4e0ea', eye='#ffd0f0', mask='#b08aa4', beak_light='#fff0f4', beak_dark='#9a7a8a',
                       cloth='#8a5a7a', belly='#fff8fa')),
    ('starfrost', dict(body=('#d8ecff', '#eef6ff'), head=('#f0f8ff', '#ffffff', '#c0d8f0'), wing='#eef6ff', pale='#c4dcf4',
                       accent='#9ad0ff', covert='#7ab0e0', ice='#bff8ff', ice_glow='#dffcff', silver='#e4f2ff',
                       eye='#ffffff', mask='#6a9ad0', beak_light='#ffffff', beak_dark='#6a8ab0', cloth='#3a6aa8',
                       belly='#ffffff', strong=1.0)),
]


def frost_variant(cid, spec):
    body, head = spec['body'], spec['head']
    pal = {
        'body': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'breast': {'base': spec['belly'], 'style': 'feather'},
        'belly': {'base': spec['belly'], 'style': 'feather'},
        'neck': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'ruff': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'head': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'cheek': {'base': head[0], 'style': 'feather', 'light': head[1]},
        'hackle': {'base': head[0], 'style': 'feather'},
        'covert_n': {'base': spec['covert'], 'style': 'feather'},
        'covert_w': {'base': spec['wing'], 'style': 'feather'},
        'flight_w': {'base': spec['wing'], 'style': 'feather'},
        'flight_v': {'base': spec['accent'], 'style': 'feather'},
        'tail': {'base': spec['wing'], 'style': 'feather'},
        'thigh': {'base': head[0], 'style': 'feather', 'light': head[1]},
        'haunch': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'cuff': {'base': spec['belly'], 'style': 'feather'},
        'beak': {'base': '#c8dcec', 'style': 'plain'},
        'beak_low': {'base': '#a8bccc', 'style': 'plain'},
        'beak_tip': {'base': spec['beak_dark'], 'style': 'plain'},
        'cere': {'base': spec['beak_light'], 'style': 'plain'},
        'frostsilver': {'base': spec['silver'], 'style': 'metal', 'rivets': False},
        'ice': {'base': spec['ice'], 'style': 'crystal'},
        'crest': {'base': spec['ice'], 'style': 'crystal'},
        'tip': {'base': spec['ice'], 'style': 'crystal'},
        'gem': {'base': spec['ice'], 'style': 'crystal'},
        'strap': {'base': '#3a4a5e', 'style': 'leather'},
        'saddle': {'base': '#4a5a70', 'style': 'leather'},
        'saddle_rim': {'base': '#3a4658', 'style': 'leather'},
        'frostcloth': {'base': spec['cloth'], 'style': 'cloth'},
    }
    extra = dict(spec, scale=1.0, hitbox=(1.5, 1.8), seat='saddle')
    return Variant(cid, palette=pal, paint=frost_paint, glow=True, extra=extra)


def frost_rig():
    return Rig('mount_griffin_frost', 'GriffinFrostMount', frost_geometry(),
               griffin.animations(primaries=FROST_PRIMARIES, secondaries=FROST_SECONDARIES,
                                  crest_n=ring_count(FROST_CREST), hackle_n=ring_count(FROST_HACKLES),
                                  absent=('banner_r', 'banner_l', 'pennant', 'lantern')),
               [frost_variant(cid, spec) for cid, spec in FROST_COATS], SLOTS, texel=2)


# ================================================================================================ void

VOID_CREST = ((4.0, 3, 11, 16, (0, 34, 68, 100)), (3.0, 3, 8, 6, (17, 51, 84)))
VOID_HACKLES = ((4.0, 3, 10, 14, (0, 40, 80, 114)), (3.0, 3, 7, 4, (20, 60, 97)))
VOID_PRIMARIES = ((2, 19), (8, 21), (15, 23), (23, 25), (32, 26), (42, 27), (53, 27), (65, 25), (78, 22))
VOID_SECONDARIES = griffin.SECONDARIES
VOID_LEG = dict(thigh='feather:thigh', haunch='feather:haunch', cuff='feather:cuff', tarsus='crystal:voidcrystal',
                band='metal:voidmetal', foot='metal:voidmetal', toe='metal:voidmetal', claw='crystal:voidcrystal')
VOID_SHARDS = 3


def void_horns():
    """Two crystal horns sweeping back from the brow."""
    parts = []
    for side, s in (('r', -1), ('l', 1)):
        x = 2.6 * s
        segs = [(2, 2, 4, None), (1, 1, 4, (-20, 8 * s, 0))]
        root = None
        built = []
        z = -16.5
        for i, (w, d, ln, rel) in enumerate(segs):
            built.append(P('vhorn_%s%d' % (side, i + 1), (x, -14.5, z),
                           C(x - w / 2.0, -14.5 - d / 2.0, z, w, d, ln, 'crystal:voidcrystal',
                             tag=('vh%d' % i) if s < 0 else None, share=None if s < 0 else 'vh%d' % i, mirror=s > 0),
                           rot=(64, 22 * s, 0) if i == 0 else rel))
            z += ln
        built[0].children.append(built[1])
        built[1].parent = built[0]
        parts.append(built[0])
    return parts


def void_orbit():
    """Three crystal shards circling over the back ('orbit' turns, each shard on its arm)."""
    shards = []
    for i in range(VOID_SHARDS):
        a = 2 * math.pi * i / VOID_SHARDS
        x, z = 7.0 * math.cos(a), 7.0 * math.sin(a) + 2.0
        shards.append(C(x - 1.0, -14.0, z - 1.0, 2, 4, 2, 'crystal:voidcrystal', tag='shard%d' % i))
        shards.append(C(x - 0.5, -15.5, z - 0.5, 1, 1, 1, 'crystal:voidcrystal', tag='shard_tip%d' % i))
        shards.append(C(x - 0.5, -10.0, z - 0.5, 1, 1, 1, 'crystal:voidcrystal', tag='shard_end%d' % i))
    return P('orbit', (0.0, -12.0, 2.0), *shards)


def void_geometry():
    crown = [C(-4.5, -14.5, -17.5, 9, 1, 6, 'metal:voidmetal', tag='circlet'),
             C(-1.5, -16.0, -17.9, 3, 3, 1, 'crystal:voidcrystal', tag='circlet_gem')]
    head = griffin_head(beak='void', crown=crown, parts=void_horns(),
                        crest=ring('crest', griffin.CREST_CENTER, VOID_CREST, 'feather:crest'))
    hackles = ring('hackle', griffin.HACKLE_CENTER, VOID_HACKLES, 'feather:hackle')
    body_cubes = feather_body() + [
        C(-7, 0.5, -13.6, 14, 2, 1, 'leather:strap', tag='strap_front'),
        C(-7, 0.5, -13, 1, 2, 9, 'leather:strap', tag='strap_side'),
        C(6, 0.5, -13, 1, 2, 9, 'leather:strap', share='strap_side', mirror=True),
        C(-2.5, -0.5, -14.4, 5, 5, 1, 'metal:voidmetal', tag='breastplate'),
        C(-1.5, 0.5, -14.9, 3, 3, 1, 'crystal:voidcrystal', tag='breast_crystal'),
        C(-6.5, -1.5, -2, 13, 12, 2, 'leather:strap', tag='girth'),
        C(-6.5, -1.5, -5, 13, 1, 11, 'cloth:voidcloth', tag='blanket'),
    ]
    spec = dict(primaries=VOID_PRIMARIES, secondaries=VOID_SECONDARIES, edge='metal:voidmetal', armour=True,
                plate='metal:voidmetal')
    tail = feather_tail(lengths=(10, 12, 14, 12, 10), rot=-14)
    return assemble(head, [], hackles, body_cubes, [void_orbit()], tail, [wing('r', spec), wing('l', spec)],
                    eagle_legs(VOID_LEG), standard_saddle())


def void_paint(p):
    e = p.variant.extra
    white, pale, accent, navy = rgb(e['wing']), rgb(e['pale']), rgb(e['accent']), rgb(e['covert'])
    glow_c = rgb(e['glow'])
    star_cols = [rgb(c) for c in e['stars']]

    def starry(fc, gc, i, primary, color):
        bp.stars(fc, (p.variant.id, 'f', i, primary, fc.face), colors=star_cols, density=0.05, glow=gc)
        # the edge of every flight feather glows
        for y in range(fc.fh):
            for x in (0, fc.fw - 1):
                if bp.opaque(fc, x, y):
                    fc.fpx(x, y, glow_c)
                    gc.fpx(x, y, shade(glow_c, 0.85))

    paint_wings(p, VOID_PRIMARIES, VOID_SECONDARIES, white, pale, accent, navy, flight_style=starry)
    metal = rgb(e['metal'])
    crystal_c, crystal_l, crystal_d = rgb(e['crystal']), mix(rgb(e['crystal']), (255, 255, 255), 0.55), \
        shade(rgb(e['crystal']), 0.55)
    for part, tag in (('wing_r', 'edge1'), ('wing_r2', 'edge2')):
        for fc, gc, name in faces(p, part, tag=tag):
            riveted(fc, metal)
            bp.runes(fc, glow_c, (p.variant.id, part, name), glow=gc, glow_col=shade(glow_c, 0.9), spacing=8)
    for part, tag in (('wing_r', 'plate1'), ('wing_r', 'plate2'), ('wing_r2', 'plate3'), ('wing_r2', 'plate4'),
                      ('wing_r2', 'wrist')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.trim(fc, glow_c, 1)
            for x in range(fc.fw):
                gc.fpx(x, 0, shade(glow_c, 0.7))
    crystals = [('wing_r', 'wgem1'), ('wing_r2', 'wgem2'), ('head', 'circlet_gem'), ('body', 'breast_crystal')]
    crystals += [('vhorn_r1', 'vh0'), ('vhorn_r2', 'vh1')]
    crystals += [('orbit', '%s%d' % (t, i)) for i in range(VOID_SHARDS) for t in ('shard', 'shard_tip', 'shard_end')]
    for part, tag in crystals:
        for fc, gc, name in faces(p, part, tag=tag):
            bp.crystal(fc, crystal_c, crystal_l, crystal_d, (p.variant.id, part, tag, name), glow=gc,
                       glow_col=shade(glow_c, 0.75))
    # the plumage: a night sky; rune lines on the flanks; the crest tipped with glow
    for part, tag in (('body', 'barrel'), ('body', 'breast'), ('body', 'rump'), ('neck', 'ruff'), ('neck', 'neck_core'),
                      ('head', 'skull'), ('head', 'cheek')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.stars(fc, (p.variant.id, part, name), colors=star_cols, density=0.035, glow=gc)
    for name in ('right', 'left'):
        fc = p.face('body', name, tag='barrel')
        gc = p.face('body', name, tag='barrel', glow=True)
        bp.runes(fc, glow_c, (p.variant.id, 'flank', name), glow=gc, glow_col=shade(glow_c, 0.85), spacing=6)
    for prefix, spec in (('crest', VOID_CREST), ('hackle', VOID_HACKLES)):
        for i in range(1, ring_count(spec) + 1):
            for name in ('top', 'bottom'):
                fc = p.face('%s_%d' % (prefix, i), name)
                gc = p.face('%s_%d' % (prefix, i), name, glow=True)
                griffin.long_feather(fc, rgb(e['head'][0]), rgb(e['head'][2]))
                for x, y in bp.each(fc):
                    if y < 4 and bp.opaque(fc, x, y):
                        fc.fpx(x, y, mix(glow_c, fc.fget(x, y), y / 4.0))
                        gc.fpx(x, y, shade(glow_c, 0.9 - y * 0.15))
    for i in range(3):
        for fc, gc, name in faces(p, 'tail2', tag='tf%d' % (i + 1), names=('top', 'bottom')):
            griffin.long_feather(fc, white if i % 2 == 0 else accent, navy)
            starry(fc, gc, i, True, white)
    griffin_eyes(p, e['eye'], mask=rgb(e['mask']), glow=True)
    beak_paint(p, rgb(e['beak_light']), rgb(e['beak_dark']))
    for pre in ('front_right', 'hind_right'):
        for t in ('toe1', 'toe4'):
            for c_i, c in enumerate(p.rig.parts['%s_%s' % (pre, t)].cubes):
                if c.mat.startswith('crystal'):
                    for name in ('front', 'top', 'right', 'left', 'bottom'):
                        fc = p.face('%s_%s' % (pre, t), name, index=c_i)
                        gc = p.face('%s_%s' % (pre, t), name, index=c_i, glow=True)
                        bp.crystal(fc, crystal_c, crystal_l, crystal_d, (p.variant.id, pre, t, c_i, name), glow=gc,
                                   glow_col=shade(glow_c, 0.6))
    for pre in ('f', 'h'):
        shin = 'front_right_shin' if pre == 'f' else 'hind_right_shin'
        for fc, gc, name in faces(p, shin, tag=pre + 's'):
            bp.crystal(fc, crystal_c, crystal_l, crystal_d, (p.variant.id, shin, name), glow=gc,
                       glow_col=shade(glow_c, 0.5))
    for part, tag in (('body', 'breastplate'), ('head', 'circlet')):
        for fc, gc, name in faces(p, part, tag=tag):
            bp.trim(fc, glow_c, 1, 'tb')
    fc = p.face('body', 'top', tag='blanket')
    gc = p.face('body', 'top', tag='blanket', glow=True)
    bp.stars(fc, (p.variant.id, 'blanket'), colors=star_cols, density=0.06, glow=gc)
    bp.trim(fc, glow_c, 1)
    for tag in ('strap_front', 'strap_side', 'girth'):
        for fc, gc, name in faces(p, 'body', tag=tag, names=('front', 'right', 'left', 'top')):
            for x in range(1, fc.fw - 1, 5):
                fc.fpx(x, fc.fh // 2, glow_c)
                gc.fpx(x, fc.fh // 2, shade(glow_c, 0.7))


VOID_COATS = [
    ('nebula', dict(body=('#1c1430', '#2a1e46'), head=('#241a3c', '#34265a', '#120c20'), wing='#2a1e48', pale='#160e28',
                    accent='#3e2a6a', covert='#100a1c', glow='#c88aff', crystal='#a060ff', metal='#3a3050',
                    stars=['#ffffff', '#e8c8ff', '#ffc8f0'], eye='#f0d8ff', mask='#0a0612', beak_light='#7a6a9a',
                    beak_dark='#120c1c', cloth='#140e24', belly='#2a2040')),
    ('eclipse', dict(body=('#141210', '#22201c'), head=('#1c1a16', '#2c2a24', '#0c0a08'), wing='#201e1a', pale='#100e0c',
                     accent='#3a2e14', covert='#0a0908', glow='#ffc84a', crystal='#ffb030', metal='#3a3428',
                     stars=['#fff4d0', '#ffd88a', '#ffffff'], eye='#ffe08a', mask='#060504', beak_light='#9a8a5a',
                     beak_dark='#141008', cloth='#100e0a', belly='#2a2620')),
    ('abyssal', dict(body=('#0c1a2a', '#142a40'), head=('#12243a', '#1c3654', '#08121e'), wing='#14283e', pale='#0a1624',
                     accent='#163c5a', covert='#060e18', glow='#5af0ff', crystal='#2ad0ff', metal='#203448',
                     stars=['#ffffff', '#c8f8ff', '#9ff0ff'], eye='#c8fcff', mask='#04080e', beak_light='#5a7a9a',
                     beak_dark='#08101a', cloth='#0a1420', belly='#1c3048')),
    ('rift', dict(body=('#2a0e24', '#401636'), head=('#34122c', '#4c1c42', '#1a0814'), wing='#3a1232', pale='#200a1c',
                  accent='#5a1a4a', covert='#140610', glow='#ff7ad8', crystal='#ff4ab8', metal='#402038',
                  stars=['#ffffff', '#ffd0f0', '#ffb0e0'], eye='#ffd8f4', mask='#0c040a', beak_light='#9a5a8a',
                  beak_dark='#1a0814', cloth='#1c0818', belly='#3a1a34')),
    ('starborn', dict(body=('#0e1430', '#18204a'), head=('#141c40', '#202c5c', '#0a0e22'), wing='#18224e', pale='#0c1230',
                      accent='#283a7a', covert='#080c20', glow='#ffffff', crystal='#c8e0ff', metal='#2a3460',
                      stars=['#ffffff', '#ffd8a0', '#a8d8ff', '#ffb0d8', '#c8ffc8'], eye='#ffffff', mask='#04060e',
                      beak_light='#a8b4e0', beak_dark='#0a0e1e', cloth='#0a0e24', belly='#202a58')),
]


def void_variant(cid, spec):
    body, head = spec['body'], spec['head']
    pal = {
        'body': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'breast': {'base': spec['belly'], 'style': 'feather'},
        'belly': {'base': spec['belly'], 'style': 'feather'},
        'neck': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'ruff': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'head': {'base': head[0], 'style': 'feather', 'light': head[1], 'dark': head[2]},
        'cheek': {'base': head[0], 'style': 'feather', 'light': head[1]},
        'crest': {'base': head[0], 'style': 'feather'},
        'hackle': {'base': head[0], 'style': 'feather'},
        'covert_n': {'base': spec['covert'], 'style': 'feather'},
        'covert_w': {'base': spec['wing'], 'style': 'feather'},
        'flight_w': {'base': spec['wing'], 'style': 'feather'},
        'flight_v': {'base': spec['accent'], 'style': 'feather'},
        'tail': {'base': spec['wing'], 'style': 'feather'},
        'thigh': {'base': head[0], 'style': 'feather', 'light': head[1]},
        'haunch': {'base': body[0], 'style': 'feather', 'light': body[1]},
        'cuff': {'base': spec['belly'], 'style': 'feather'},
        'beak': {'base': spec['beak_light'], 'style': 'plain'},
        'beak_low': {'base': shade_hex(spec['beak_light'], 0.8), 'style': 'plain'},
        'beak_tip': {'base': spec['beak_dark'], 'style': 'plain'},
        'cere': {'base': spec['beak_light'], 'style': 'plain'},
        'voidmetal': {'base': spec['metal'], 'style': 'metal', 'rivets': False},
        'voidcrystal': {'base': spec['crystal'], 'style': 'crystal'},
        'gem': {'base': spec['crystal'], 'style': 'crystal'},
        'strap': {'base': '#1e1a28', 'style': 'leather'},
        'saddle': {'base': '#2a2234', 'style': 'leather'},
        'saddle_rim': {'base': '#1e1828', 'style': 'leather'},
        'voidcloth': {'base': spec['cloth'], 'style': 'cloth'},
    }
    extra = dict(spec, scale=1.0, hitbox=(1.5, 1.8), seat='saddle')
    return Variant(cid, palette=pal, paint=void_paint, glow=True, extra=extra)


def void_rig():
    orbit = {'orbit': dict(rot=[(0, 0, 0, 0), (3.0, 0, 120, 0)], pos=[(0, 0, 0, 0), (1.5, 0, 0.6, 0), (3.0, 0, 0, 0)],
                           lin=True)}
    return Rig('mount_griffin_void', 'GriffinVoidMount', void_geometry(),
               griffin.animations(primaries=VOID_PRIMARIES, secondaries=VOID_SECONDARIES,
                                  crest_n=ring_count(VOID_CREST), hackle_n=ring_count(VOID_HACKLES),
                                  absent=('banner_r', 'banner_l', 'pennant', 'lantern'), extra={'idle': orbit}),
               [void_variant(cid, spec) for cid, spec in VOID_COATS], SLOTS, texel=2)


def rigs():
    return [gold_rig(), owl_rig(), storm_rig(), frost_rig(), void_rig()]
