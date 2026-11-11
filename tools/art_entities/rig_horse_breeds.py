"""The five horse breeds (2026-11-11, the owner: 「馬とグリフォンの種類をもう5体ずつ増やして」). Each is its own rig
(mount_horse_<breed>) on the Moonmane Charger's skeleton (rig_horse.py: the same bones, gaits and seat), with its own
build, mane, tail, trappings and parts, and five coats (four common, one rare). Own designs.

  bloom  Bloomantler Leaper   a slender fawn with branching antlers in leaf and blossom, a mane of leaves, a woven vine
                              bridle, a flower garland and a leaf-embroidered blanket; petals fall from it
  tide   Tidemane Runner      a sea horse: a fin crest down the neck, sea-foam locks and tail, fin ears, scaled legs
                              with fin flaps, a shell chanfron with a pearl, a coral-and-pearl harness, glowing spots
  gale   Galefeather Courser  a winged courser: feathered wings folded along the flanks (spread in the glide), a
                              wind-swept mane, feathered fetlocks, silver sky tack with a wing emblem
  blaze  Blazemane Courser    a charcoal horse veined with glowing lava, a mane and tail of fire, burning hooves,
                              blackened barding with a horned chanfron, spiked pauldrons and a flame emblem
  halo   Halohorn Steed       a pearl-white unicorn with a spiral horn, a floating halo, a mane and tail of light,
                              long feathering at the fetlocks and silver-gold filigree barding with feather plumes

Conventions as in ermodel and rig_horse: absolute rest-pose coordinates, +Y down, ground at y=24, front -Z, right side
-X. Bones the Java model drives keep the warhorse's names (base, body, neck, head, ear_r/l, mane_*, lock_*, tail,
tail2-4, the legs and shins, saddle); the gale's wings are wing_r/wing_l and its extra slot is 'glide'.
"""
import math

from ermodel import C, P, Rig, Variant, anim
from erpaint import mix, rgb, shade
import breed_paint as bp
import rig_horse as horse
import rig_mounts as base

GOLD, GOLD_DARK, GOLD_LIGHT = base.GOLD, base.GOLD_DARK, base.GOLD_LIGHT
SLOTS = ['idle', 'walk', 'gallop', 'jump']
HEAD_SHIFT, NECK_TILT, HEAD_TILT = horse.HEAD_SHIFT, horse.NECK_TILT, horse.HEAD_TILT


# ================================================================================================ shared builders

def shards(table, tilt, first, mat):
    """Mane shards (as rig_horse.shards) of {@code mat}: mane_{first + 1}...; returns the parts and the last number."""
    parts = []
    n = first
    for (x, y, z, w, d, ln, elevation, swing, face) in table:
        for side in ((1,) if x == 0.0 else (-1, 1)):
            n += 1
            px = x * side
            parts.append(P('mane_%d' % n, (px, y, z), *horse.blade(px, y, z, w, d, ln, mat=mat),
                           rot=horse.aim(elevation, swing * side, face * side, tilt)))
    return parts, n


def count(table):
    return sum(1 if entry[0] == 0.0 else 2 for entry in table)


def locks(table, mat, upward=False):
    """Locks lock_1... hanging from the crest down the neck's sides (mirrored pairs); {@code upward}: they rise
    instead (flames)."""
    parts = []
    n = 0
    for (x, y, z, t, wd, ln, lean, out) in table:
        for side in (-1, 1):
            n += 1
            px = x * side
            cubes = horse.blade(px, y, z, wd, t, ln, hanging='x', mat=mat)
            if upward:
                # the same stepped blade, mirrored upward from its root
                cubes = [C(c.x, 2 * y - c.y - c.h, c.z, c.w, c.h, c.d, mat) for c in cubes]
            parts.append(P('lock_%d' % n, (px, y, z), *cubes, rot=(lean - NECK_TILT, 0, -out * side)))
    return parts


def tail(table, root_mat, tilts=None, root=(5, 4, 4), rest=26):
    """The tail as in rig_horse.tail (segments tail, tail2, tail3, tail4, tail_end) with blades of {@code root_mat}
    from {@code table} (segment, x, y, z, width, thickness, length, lean, out, turn)."""
    tilts = tilts or horse.TAIL_TILT
    blades = {seg: [] for seg in tilts}
    n = 0
    for (seg, x, y, z, w, t, ln, lean, out, turn) in table:
        for side in ((1,) if x == 0.0 else (-1, 1)):
            n += 1
            px = x * side
            blades[seg].append(P('tail_blade_%d' % n, (px, y, z),
                                 *horse.blade(px, y, z, w, t, ln, hanging='z', mat=root_mat),
                                 rot=(lean - tilts[seg], turn * side, -out * side)))
    rw, rh, rd = root
    return P('tail', (0, -3, 12),
             C(-rw / 2.0, -4, 11.5, rw, rh, rd, root_mat, tag='root'),
             P('tail2', (0, -1.5, 14),
               C(-2.5, -2.5, 12.5, 5, 9, 4, root_mat),
               P('tail3', (0, 6, 14),
                 C(-2.5, 5.5, 12.5, 5, 7, 3, root_mat),
                 P('tail4', (0, 12.5, 14.5),
                   C(-2, 12, 13, 4, 6, 3, root_mat),
                   P('tail_end', (0, 18, 14.5), *blades['tail_end'], rot=(-6, 0, 0)),
                   *blades['tail4'], rot=(-4, 0, 0)),
                 *blades['tail3'], rot=(-6, 0, 0)),
               *blades['tail2'], rot=(12, 0, 0)),
             rot=(rest, 0, 0))


def tail_count(table):
    return sum(1 if entry[1] == 0.0 else 2 for entry in table)


def mirror_cube(c, tag_r, tag_l=None):
    """The left twin of a right-side cube (x mirrored, sharing its UV)."""
    return C(-(c.x + c.w), c.y, c.z, c.w, c.h, c.d, c.mat, share=tag_r, mirror=True, inflate=c.inflate)


def pair(x, y, z, w, h, d, mat, tag, inflate=0.0):
    """A right-side cube (x is its right edge, negative) and its mirrored left twin."""
    right = C(x, y, z, w, h, d, mat, tag=tag, inflate=inflate)
    return [right, C(-(x + w), y, z, w, h, d, mat, share=tag, mirror=True, inflate=inflate)]


def leg(name, x, z, hind, side, spec):
    """A breed's leg: upper (forearm or gaskin), shin (cannon), fetlock and hoof, with optional knee wraps, greaves,
    feathering and fin flaps from {@code spec}. Bones {name} and {name with _shin} as the warhorse's."""
    mirror = side == 'l'
    pre = 'h' if hind else 'f'

    def cube(cx, y, cz, w, h, d, mat, t, inflate=0.0):
        return C(cx, y, cz, w, h, d, mat, tag=(pre + t) if side == 'r' else None,
                 share=(pre + t) if side == 'l' else None, mirror=mirror, inflate=inflate)

    knee = 13.0
    sw = spec.get('shin', 4)
    hw, hh, hd = spec.get('hoof', (5, 3, 6))
    fet = spec.get('fetlock', (5, 1, 5))
    shin = [cube(x - sw / 2.0, knee, z - sw / 2.0, sw, 8, sw, spec.get('shin_mat', 'fur:coat'), 's'),
            cube(x - fet[0] / 2.0, 20.0, z - fet[2] / 2.0, fet[0], fet[1], fet[2], spec.get('fet_mat', 'hair:fetlock'),
                 'fet'),
            cube(x - hw / 2.0, 24.0 - hh, z - hd / 2.0 - 0.5, hw, hh, hd, spec.get('hoof_mat', 'plain:hoof'), 'hoof')]
    for extra in spec.get('shin_extra', ()):
        ex, ey, ez, ew, eh, ed, emat, etag = extra
        shin.append(cube(x + ex, ey, z + ez, ew, eh, ed, emat, etag))
    shin_part = P(name.replace('_leg', '_shin'), (x, knee, z), *shin,
                  *[p for p in spec.get('shin_parts', lambda *_: ())(x, z, hind, side)])
    if hind:
        uw, ud = spec.get('hind', (6, 7))
        upper = [cube(x - uw / 2.0, 0.0, z - ud / 2.0, uw, 13, ud, spec.get('upper_mat', 'fur:coat'), 'u')]
        top = 3.0
    else:
        uw, ud = spec.get('front', (5, 6))
        upper = [cube(x - uw / 2.0, 4.0, z - ud / 2.0, uw, 9, ud, spec.get('upper_mat', 'fur:coat'), 'u')]
        top = 5.0
    for extra in spec.get('upper_extra', ()):
        ex, ey, ez, ew, eh, ed, emat, etag, which = extra
        if which in ('both', 'hind' if hind else 'front'):
            upper.append(cube(x + ex, ey, z + ez, ew, eh, ed, emat, etag))
    return P(name, (x, top, z), *upper, shin_part)


def legs(spec):
    return [leg('front_right_leg', -3.5, -8, False, 'r', spec), leg('front_left_leg', 3.5, -8, False, 'l', spec),
            leg('hind_right_leg', -3.5, 8, True, 'r', spec), leg('hind_left_leg', 3.5, 8, True, 'l', spec)]


def ears(w=2, h=5, d=2, tilt=-46, splay=8, mat='fur:coat', extra=()):
    """The ears standing on the poll (as the warhorse's)."""
    return [P('ear_r', (-3, -22, -14), C(-3 - w / 2.0 - 0.5, -22 - h, -14.5, w, h, d, mat, tag='ear'), *extra,
              rot=(tilt, 0, -splay)),
            P('ear_l', (3, -22, -14), C(3 - w / 2.0 + 0.5, -22 - h, -14.5, w, h, d, mat, share='ear', mirror=True),
              rot=(tilt, 0, splay))]


def skull(width=9, face_w=7, face_len=9):
    """Skull, face, nose and jaw (head coordinates before HEAD_SHIFT)."""
    fx = face_w / 2.0
    return [C(-width / 2.0, -22, -21, width, 9, 9, 'fur:coat', tag='skull'),
            C(-fx, -21, -21 - face_len, face_w, 7, face_len, 'fur:muzzle', tag='face'),
            C(-fx + 0.5, -20, -23 - face_len, face_w - 1, 6, 2, 'fur:muzzle', tag='nose'),
            C(-fx, -14.5, -19 - face_len, face_w, 2, 7, 'fur:muzzle', tag='jaw')]


def assemble(head_cubes, head_parts, neck_cubes, neck_parts, body_cubes, body_parts, tail_part, leg_spec, saddle,
             poll, crest, lock_parts, neck=(8, 9)):
    """The warhorse's skeleton with a breed's pieces: head (moved up onto the neck by HEAD_SHIFT), neck, body, tail
    and legs. Returns the root parts."""
    head = P('head', (0, -14, -13), *head_cubes, *head_parts, *poll, rot=(HEAD_TILT - NECK_TILT, 0, 0))
    horse.shift(head, *HEAD_SHIFT)
    nw, nd = neck
    neck_part = P('neck', (0, -2, -10), C(-nw / 2.0, -16, -15, nw, 17, nd, 'fur:coat', tag='neck'), *neck_cubes,
                  head, *crest, *lock_parts, *neck_parts, rot=(NECK_TILT, 0, 0))
    body = P('body', (0, 3, 0), *body_cubes, saddle, neck_part, tail_part, *body_parts, *legs(leg_spec))
    return [P('base', (0, 24, 0), body)]


def all_faces(p, part, tag=None, index=0, glow=False, names=('front', 'back', 'right', 'left', 'top', 'bottom')):
    return [p.face(part, n, tag=tag, index=index, glow=glow) for n in names]


def blade_parts(p, prefix, n):
    return ['%s_%d' % (prefix, i) for i in range(1, n + 1)]


def for_blades(p, parts, fn, names=('top', 'bottom', 'right', 'left', 'front', 'back')):
    """{@code fn(fc, glow, part, index, face)} on every face of every block of the blades."""
    for part in parts:
        for i, _ in enumerate(p.rig.parts[part].cubes):
            for name in names:
                fn(p.face(part, name, index=i), p.face(part, name, index=i, glow=True), part, i, name)


def palette(extra, **entries):
    """A variant palette: the common horse entries (coat and muzzle with their shades, hoof) plus breed entries."""
    coat, muzzle = extra['coat'], extra['muzzle']
    pal = {
        'coat': {'base': coat[0], 'style': 'fur', 'dark': coat[1], 'light': coat[2]},
        'muzzle': {'base': muzzle[0], 'style': 'fur', 'dark': muzzle[1], 'light': muzzle[2]},
    }
    pal.update(entries)
    return pal


def coat_spec(**kw):
    return kw


def chain(prefix, root, segments, first_rot, mat, tag=None):
    """A bent stick (an antler beam, a horn): nested parts {prefix}1, {prefix}2... each a block along its own +z,
    the first turned by {@code first_rot}, every next one by its own relative turn. segments: (width, thickness,
    length, relative rot, extra cubes as (dx, dy, dz, w, h, d, mat) from the segment's start). Returns the root part."""
    x, y, z = root
    parts = None
    built = []
    for i, (w, d, ln, rel, extras) in enumerate(segments):
        cubes = [C(x - w / 2.0, y - d / 2.0, z, w, d, ln, mat, tag=(tag + str(i)) if tag else None)]
        for (dx, dy, dz, ew, eh, ed, emat) in extras:
            cubes.append(C(x + dx, y + dy, z + dz, ew, eh, ed, emat))
        built.append((P('%s%d' % (prefix, i + 1), (x, y, z), *cubes, rot=first_rot if i == 0 else rel), ln))
        z += ln
    # nest from the tip back to the root
    for part, _ in reversed(built):
        if parts is not None:
            part.children.append(parts)
            parts.parent = part
        parts = part
    return parts


# ================================================================================================ bloom

BLOOM_POLL = (
    (0.0, -22.4, -13.0, 5, 1, 7, 46, 0, 0),
    (1.2, -22.4, -13.6, 4, 1, 6, 40, 26, 30),
    (0.0, -20.0, -12.4, 5, 1, 9, 32, 0, 0),
    (1.9, -20.5, -12.4, 4, 1, 8, 28, 30, 40),
    (2.2, -17.5, -12.4, 4, 1, 7, 6, 30, 56),
)
BLOOM_CREST = (
    (0.0, -15.5, -6.8, 5, 1, 9, 32, 0, 0),
    (1.8, -14.5, -6.8, 4, 1, 8, 20, 30, 45),
    (0.0, -12.8, -6.8, 5, 1, 9, 28, 0, 0),
    (2.0, -11.5, -6.8, 4, 1, 8, 14, 32, 52),
    (0.0, -10.0, -6.8, 5, 1, 9, 24, 0, 0),
    (2.2, -8.5, -6.8, 4, 1, 7, 6, 32, 58),
    (0.0, -7.0, -6.8, 5, 1, 8, 20, 0, 0),
    (2.2, -5.5, -6.8, 4, 1, 7, -2, 30, 62),
    (0.0, -4.0, -6.8, 4, 1, 7, 16, 0, 0),
    (0.0, -1.0, -6.8, 4, 1, 6, 12, 0, 0),
)
BLOOM_LOCKS = (
    (3.4, -14.0, -8.6, 1, 3, 10, 14, 7),
    (3.5, -11.0, -8.0, 1, 3, 12, 18, 8),
    (3.5, -8.0, -7.6, 1, 3, 9, 24, 9),
)
BLOOM_TAIL = (
    ('tail2', 0.0, -1.0, 16.0, 5, 1, 8, 62, 0, 0),
    ('tail2', 2.2, -0.5, 15.0, 4, 1, 8, 50, 22, 24),
    ('tail3', 0.0, 6.0, 15.2, 5, 1, 8, 40, 0, 0),
    ('tail3', 2.4, 6.5, 14.6, 4, 1, 8, 32, 20, 22),
    ('tail4', 0.0, 12.0, 15.6, 4, 1, 7, 28, 0, 0),
    ('tail4', 2.0, 12.5, 15.0, 3, 1, 7, 22, 16, 20),
    ('tail_end', 0.0, 17.5, 15.0, 3, 1, 6, 18, 0, 0),
)


def antler(side):
    """A branching antler of bark: a beam bending back and out in three segments, a brow tine and three tines
    rising from it, leaf clusters at the joints and a blossom on every tip."""
    s = -1 if side == 'r' else 1
    x = 2.3 * s
    lf = 'leaf:antler_leaf'
    segs = [
        (2, 2, 6, None, [(-1.5, -2.2, 3.5, 3, 1, 3, lf)]),
        (2, 2, 5, (-24, 18 * s, 0), [(-1.5, -2.0, 2.5, 3, 1, 3, lf)]),
        (1, 1, 5, (-18, 14 * s, 0), [(-1.0, -1.5, 4.0, 2, 2, 2, 'petal:blossom')]),
    ]
    beam = chain('antler_%s' % side, (x, -22.4, -16.0), segs,
                 horse.aim(70, 30 * s, 0, HEAD_TILT), 'bark:antler', tag='ab_' + side)
    joints = [beam, beam.children[0], beam.children[0].children[0]]
    for part, at, ln, rot, name, flower in ((joints[0], 0.5, 3, (88, 6 * s, 0), 'brow', 'petal:blossom2'),
                                            (joints[0], 4.0, 5, (52, -12 * s, 0), 'tine1', 'petal:blossom'),
                                            (joints[1], 3.0, 4, (56, -8 * s, 0), 'tine2', 'petal:blossom2'),
                                            (joints[2], 2.0, 3, (46, 10 * s, 0), 'tine3', 'petal:blossom')):
        px, py, pz = part.pivot
        tz = pz + at
        tine = P('%s_%s' % (name, side), (px, py, tz),
                 C(px - 0.5, py - 0.5, tz, 1, 1, ln, 'bark:antler'),
                 C(px - 1.0, py - 1.0, tz + ln - 1.0, 2, 2, 2, flower), rot=rot)
        tine.parent = part
        part.children.append(tine)
    return beam


def bloom_geometry():
    poll, n = shards(BLOOM_POLL, HEAD_TILT, 0, 'leaf:mane')
    crest, n = shards(BLOOM_CREST, NECK_TILT, n, 'leaf:mane')
    head_cubes = skull(width=8, face_w=6, face_len=9) + [
        # a woven vine bridle with flower rosettes; amber eyes are painted
        C(-3.5, -21.5, -27.5, 7, 8, 1, 'rope:vine', tag='noseband'),
        C(-4.5, -22.5, -20.5, 9, 10, 1, 'rope:vine', tag='cheekband'),
        C(-5.0, -18, -21, 1, 2, 2, 'petal:blossom', tag='rosette'),
        C(4.0, -18, -21, 1, 2, 2, 'petal:blossom', share='rosette', mirror=True),
        C(-2, -23, -15.5, 4, 1, 4, 'leaf:mane', tag='poll'),
    ]
    head_parts = ears(w=2, h=6, d=2, tilt=-40, splay=22) + [antler('r'), antler('l'),
        P('reins', (0, -14.5, -27),
          C(-4.6, -14.5, -27.5, 1, 13, 1, 'rope:vine', tag='rein'),
          C(3.6, -14.5, -27.5, 1, 13, 1, 'rope:vine', share='rein', mirror=True),
          C(-4.6, -2.0, -27.5, 9, 1, 1, 'rope:vine', tag='rein_loop'), rot=(38, 0, 0))]
    neck_cubes = [
        C(-2, -16.5, -8, 4, 16, 3, 'leaf:mane', tag='crest'),
        # the garland at the root of the neck: blossoms between leaves
        *pair(-4.6, -5.0, -15.5, 2, 2, 2, 'petal:blossom', 'g1'),
        *pair(-4.8, -4.4, -12.5, 1, 2, 3, 'leaf:garland', 'g2'),
        *pair(-4.6, -5.0, -10.0, 2, 2, 2, 'petal:blossom2', 'g3'),
        *pair(-4.6, -4.0, -7.4, 2, 2, 2, 'petal:blossom', 'g4'),
        C(-1.0, -5.0, -16.6, 2, 2, 2, 'petal:blossom2', tag='g5'),
        C(-3.4, -4.6, -16.4, 2, 2, 1, 'leaf:garland', tag='g6'),
        C(1.4, -4.6, -16.4, 2, 2, 1, 'leaf:garland', share='g6', mirror=True),
    ]
    saddle = P('saddle', (0, -4.5, 0),
               C(-4, -5.5, -4, 8, 2, 8, 'leather:saddle'),
               C(-2.5, -7.5, -5, 5, 2, 2, 'wood:saddle_wood', tag='pommel'),
               C(-3.5, -8.5, 3, 7, 3, 2, 'wood:saddle_wood', tag='cantle'),
               C(-2.5, -9.5, 3.5, 5, 1, 1, 'leaf:garland', tag='cantle_leaf'))
    body_cubes = [
        C(-6, -4, -11, 12, 12, 22, 'fur:coat', tag='barrel'),
        C(-5, -3, -14, 10, 9, 3, 'fur:coat', tag='chest'),
        C(-5, -4, 8, 10, 10, 5, 'fur:coat', tag='rump'),
        # the leaf-embroidered blanket with a leafy hem, the girth of plaited vine
        C(-6.5, -4.5, -6, 13, 1, 12, 'cloth:blanket', tag='blanket'),
        *pair(-7, -4.0, -5.5, 1, 8, 11, 'cloth:blanket', 'drape'),
        *pair(-6.6, -3.5, -1.0, 1, 12, 2, 'rope:vine', 'girth'),
        C(-5.5, 8, -1.0, 11, 1, 2, 'rope:vine', tag='girth_under'),
        *pair(-8.0, -3.0, 3.5, 2, 5, 5, 'leather:bag', 'bag'),
    ]
    leg_spec = dict(front=(4, 5), hind=(5, 6), shin=3, hoof=(4, 3, 5), fetlock=(4, 2, 4), fet_mat='leaf:fet',
                    hoof_mat='plain:hoof',
                    upper_extra=[(-2.5, 11.0, -3.0, 5, 2, 6, 'leaf:wrap', 'wrap', 'front'),
                                 (-3.0, 10.5, -3.5, 6, 2, 7, 'leaf:wrap', 'wrap', 'hind')],
                    shin_extra=[(-1.0, 19.0, -2.6, 2, 2, 1, 'petal:blossom', 'anklet')])
    lock_parts = locks(BLOOM_LOCKS, 'leaf:lock')
    return assemble(head_cubes, head_parts, neck_cubes, [], body_cubes, [],
                    tail(BLOOM_TAIL, 'leaf:tail'), leg_spec, saddle, poll, crest, lock_parts, neck=(7, 9))


# ================================================================================================ painting blades

def blade_faces(p, part, orient):
    """The broad faces of a stepped blade (rig_horse.blade) with where each fine pixel lies on the whole blade:
    yields (face, glow face, u(y): 0 at the root .. 1 at the tip, across(x): fine pixels from the blade's axis,
    width: the blade's root width in fine pixels). orient: 'z' (shards along +z), 'x' (locks hanging, flat to x),
    'zh' (tail blades hanging, flat to z)."""
    cubes = p.rig.parts[part].cubes
    k = p.k
    if orient == 'z':
        lengths = [c.d for c in cubes]
        names = ('top', 'bottom')
        root_w = cubes[0].w * k
    elif orient == 'x':
        lengths = [c.h for c in cubes]
        names = ('right', 'left')
        root_w = cubes[0].d * k
    else:
        lengths = [c.h for c in cubes]
        names = ('front', 'back')
        root_w = cubes[0].w * k
    total = float(sum(lengths)) * k
    offset = 0.0
    for i, c in enumerate(cubes):
        ln = lengths[i] * k
        for name in names:
            fc = p.face(part, name, index=i)
            gc = p.face(part, name, index=i, glow=True)
            if orient == 'z':
                # fine y 0 is the far (tip) end of the block
                def u_of(y, offset=offset, ln=ln):
                    return (offset + ln - (y + 0.5)) / total
            else:
                def u_of(y, offset=offset):
                    return (offset + y + 0.5) / total

            def across(x, fw=fc.fw):
                return x + 0.5 - fw / 2.0
            yield fc, gc, u_of, across, root_w
        offset += ln


def side_faces(p, part, orient):
    """The thin side faces of a blade's blocks (for edges)."""
    names = {'z': ('right', 'left', 'front', 'back'), 'x': ('front', 'back', 'top', 'bottom'),
             'zh': ('right', 'left', 'top', 'bottom')}[orient]
    for i, _ in enumerate(p.rig.parts[part].cubes):
        for name in names:
            yield p.face(part, name, index=i), p.face(part, name, index=i, glow=True)


def leaf_blade(p, part, orient, base_c, light, dark, vein, glow_col=None):
    """A whole blade painted as one leaf: a stalk at the root, widest two fifths up, a pointed tip; midrib, slanting
    side veins, a serrated edge."""
    base_c, light, dark, vein = bp.col(base_c), bp.col(light), bp.col(dark), bp.col(vein)
    for fc, gc, u_of, across, width in blade_faces(p, part, orient):
        half_w = width / 2.0
        for x, y in bp.each(fc):
            u = u_of(y)
            half = half_w * (math.sin(math.pi * min(1.0, max(0.0, u)) ** 0.85) ** 0.8)
            half = max(half, 0.9 if u < 0.12 else 0.0)
            a = across(x)
            if abs(a) > half + 0.2:
                fc.fclear(x, y)
                continue
            edge = abs(a) > half - 1.0
            c = mix(light, base_c, 0.5 + 0.5 * a / max(1.0, half)) if a < 0 else mix(base_c, dark, 0.7 * a / max(1.0, half))
            if abs(a) < 0.55:
                c = vein
            elif ((y * 1.0 + abs(a) * 1.2) % 4.0) < 0.9:
                c = mix(c, vein, 0.45)
            if edge:
                c = shade(c, 0.8)
                if (x * 3 + y) % 4 == 0 and u > 0.15:
                    fc.fclear(x, y)
                    continue
            fc.fpx(x, y, c)
            if glow_col is not None:
                gc.fpx(x, y, shade(bp.col(glow_col), 0.95 if abs(a) < 0.55 or edge else 0.45))
    for fc, gc in side_faces(p, part, orient):
        for x, y in bp.each(fc):
            fc.fpx(x, y, shade(dark, 0.9))
            if glow_col is not None:
                gc.fpx(x, y, shade(bp.col(glow_col), 0.6))


def blade_ramp(p, part, orient, ramp, glow=None, glow_dim=0.9, taper=True, flicker=0.0, keys=()):
    """A whole blade painted along a colour ramp from root (ramp[0]) to tip (ramp[-1]) with strands and a sheen;
    {@code taper} cuts a pointed tip; {@code glow}: the colours shine (flames, light)."""
    ramp = [bp.col(c) for c in ramp]
    r = bp.rng_of('ramp', part, *keys)
    phase = r.uniform(0, 6.28)
    for fc, gc, u_of, across, width in blade_faces(p, part, orient):
        half_w = width / 2.0
        for x, y in bp.each(fc):
            u = min(1.0, max(0.0, u_of(y)))
            a = across(x)
            if taper:
                half = half_w * (1.0 - u ** 1.6) + 0.6
                if flicker:
                    half += flicker * math.sin(u * 9.0 + phase + a * 0.4) * u
                if abs(a) > half:
                    fc.fclear(x, y)
                    continue
            t = u * (len(ramp) - 1)
            i = min(len(ramp) - 2, int(t))
            c = mix(ramp[i], ramp[i + 1], t - i)
            if int(a + half_w) % 3 == 0:
                c = shade(c, 0.92)
            if abs(a) < 0.6:
                c = mix(c, (255, 255, 255), 0.25)
            fc.fpx(x, y, c)
            if glow:
                gc.fpx(x, y, shade(c, glow_dim))
    for fc, gc in side_faces(p, part, orient):
        for x, y in bp.each(fc):
            c = shade(ramp[len(ramp) // 2], 0.85)
            fc.fpx(x, y, c)
            if glow:
                gc.fpx(x, y, shade(c, glow_dim * 0.8))


def leafy_box(fc, gc, base_c, light, dark, keys, glow_col=None):
    """A clump of small leaves over a box face (the tail's core, a garland, knee wraps)."""
    base_c, light, dark = bp.col(base_c), bp.col(light), bp.col(dark)
    r = bp.rng_of('leafy', *keys)
    for x, y in bp.each(fc):
        fc.fpx(x, y, mix(base_c, dark, 0.35))
    for _ in range(max(2, (fc.fw * fc.fh) // 10)):
        cx, cy = r.uniform(0, fc.fw), r.uniform(0, fc.fh)
        a = r.uniform(0, math.pi)
        ln = r.uniform(2.0, 3.5)
        for t in range(-int(ln), int(ln) + 1):
            for wd in (-1, 0, 1):
                if abs(wd) > (1.0 - abs(t) / (ln + 0.5)) * 1.6:
                    continue
                x = int(round(cx + math.cos(a) * t - math.sin(a) * wd * 0.8))
                y = int(round(cy + math.sin(a) * t + math.cos(a) * wd * 0.8))
                if 0 <= x < fc.fw and 0 <= y < fc.fh:
                    c = light if wd < 0 else base_c if wd == 0 else shade(base_c, 0.85)
                    fc.fpx(x, y, c)
                    if glow_col is not None:
                        gc.fpx(x, y, shade(bp.col(glow_col), 0.5))


def horse_eye(p, color, glow=False, part='head', tag='skull', y=4, size=(3, 2)):
    """The eyes on the skull's sides (where the warhorse's are)."""
    for name in ('right', 'left'):
        fc = p.face(part, name, tag=tag)
        gc = p.face(part, name, tag=tag, glow=True)
        ex = fc.fw - 8 if name == 'right' else 5
        w, h = size
        for dy in range(h):
            for dx in range(w):
                c = bp.col(color) if dy < h - 1 else shade(bp.col(color), 0.65)
                fc.fpx(ex + dx, y + dy, c)
                if glow:
                    gc.fpx(ex + dx, y + dy, c)
        fc.fpx(ex + (0 if name == 'right' else w - 1), y, (250, 250, 250))
        if glow:
            gc.fpx(ex + (0 if name == 'right' else w - 1), y, (250, 250, 250))
        for dx in range(-1, w + 1):
            fc.fpx(ex + dx, y - 1, shade(p.face(part, name, tag=tag).fget(ex + dx, y - 2), 0.45))


def muzzle_marks(p, nostril=(16, 12, 14)):
    for name in ('right', 'left'):
        face = p.face('head', name, tag='face')
        face.fpx(2 if name == 'right' else face.fw - 3, face.fh - 4, nostril)
    front = p.face('head', 'front', tag='face')
    for x in (2, front.fw - 3):
        front.fpx(x, front.fh - 4, nostril)
        front.fpx(x, front.fh - 5, nostril)
    nose = p.face('head', 'front', tag='nose')
    for x in (1, nose.fw - 2):
        nose.fpx(x, 3, nostril)
        nose.fpx(x, 4, nostril)


def upper_region(fc, share=0.55):
    """The upper part of a side face (where the back's markings go)."""
    return lambda x, y: y < fc.fh * share


# ================================================================================================ bloom paint

def bloom_paint(p):
    e = p.variant.extra
    glow = e.get('glow')
    leaf_a, leaf_b = rgb(e['leaf']), rgb(e['leaf2'])
    vein = shade(leaf_a, 0.62)
    gl = rgb(e['glow_col']) if glow else None
    n_mane = count(BLOOM_POLL) + count(BLOOM_CREST)
    for i, part in enumerate(blade_parts(p, 'mane', n_mane)):
        c = leaf_a if i % 3 else leaf_b
        leaf_blade(p, part, 'z', c, mix(c, (255, 255, 230), 0.35), shade(c, 0.62), shade(c, 0.6), gl)
    for i, part in enumerate(blade_parts(p, 'lock', 2 * len(BLOOM_LOCKS))):
        c = leaf_b if i % 2 else leaf_a
        leaf_blade(p, part, 'x', c, mix(c, (255, 255, 230), 0.35), shade(c, 0.62), shade(c, 0.6), gl)
    for i, part in enumerate(blade_parts(p, 'tail_blade', tail_count(BLOOM_TAIL))):
        c = leaf_a if i % 2 else leaf_b
        leaf_blade(p, part, 'zh', c, mix(c, (255, 255, 230), 0.35), shade(c, 0.62), shade(c, 0.6), gl)
    # the leaf clumps: tail core, crest root, poll, garland leaves, knee wraps, fetlock tufts
    for part, tag, idx in (('tail', 'root', 0), ('tail2', None, 0), ('tail3', None, 0), ('tail4', None, 0),
                           ('neck', 'crest', 0), ('head', 'poll', 0), ('front_right_leg', 'fwrap', 0),
                           ('hind_right_leg', 'hwrap', 0), ('front_right_shin', 'ffet', 0),
                           ('hind_right_shin', 'hfet', 0), ('neck', 'g2', 0), ('neck', 'g6', 0)):
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            leafy_box(p.face(part, name, tag=tag, index=idx), p.face(part, name, tag=tag, index=idx, glow=True),
                      leaf_a, mix(leaf_b, (255, 255, 220), 0.3), shade(leaf_a, 0.55), (p.variant.id, part, name), gl)
    # antlers: bark with moss and lichen, leaves at the joints, blossoms at the tips
    blossom, blossom2, heart = rgb(e['blossom']), rgb(e['blossom2']), rgb(e['heart'])
    for side in ('r', 'l'):
        for part in ('antler_%s1' % side, 'antler_%s2' % side, 'antler_%s3' % side, 'brow_' + side, 'tine1_' + side,
                     'tine2_' + side, 'tine3_' + side):
            cubes = p.rig.parts[part].cubes
            for i, c in enumerate(cubes):
                for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
                    fc = p.face(part, name, index=i)
                    gc = p.face(part, name, index=i, glow=True)
                    if c.mat == 'petal:blossom' or c.mat == 'petal:blossom2':
                        petal = blossom if c.mat.endswith('blossom') else blossom2
                        bp.petal_face(fc, petal, heart, (p.variant.id, part, i, name), gc,
                                      rgb(e['glow_col']) if glow else (shade(petal, 0.45) if e.get('soft_glow') else None))
                    elif c.mat.startswith('leaf'):
                        bp.leaf(fc, leaf_a, mix(leaf_a, (255, 255, 220), 0.3), shade(leaf_a, 0.6), shade(leaf_a, 0.55),
                                (p.variant.id, part, i, name), gc, gl, serrate=False)
                    else:
                        r = bp.rng_of('lichen', p.variant.id, part, i, name)
                        for x, y in bp.each(fc):
                            if r.random() < 0.12:
                                fc.fpx(x, y, mix(fc.fget(x, y), leaf_b, 0.6))
                        if name == 'top' or (name in ('front', 'back') and c.d > 2):
                            for x in range(fc.fw):
                                fc.fpx(x, 0, mix(fc.fget(x, 0), (255, 255, 255), 0.25))
    for tag in ('g1', 'g3', 'g4', 'g5', 'rosette', 'fanklet', 'hanklet'):
        part = 'neck' if tag.startswith('g') else 'head' if tag == 'rosette' else (
            'front_right_shin' if tag == 'fanklet' else 'hind_right_shin')
        cube = p.cube(part, tag=tag)
        petal = blossom2 if cube.mat.endswith('2') else blossom
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            bp.petal_face(p.face(part, name, tag=tag), petal, heart, (p.variant.id, tag, name),
                          p.face(part, name, tag=tag, glow=True), gl or (shade(petal, 0.4) if e.get('soft_glow') else None))
    # the coat: dapples along the back, a pale belly, the muzzle's chin and nostrils
    spot = rgb(e['spot'])
    sg = gl if glow else None
    for part, tag, names in (('body', 'barrel', ('top', 'right', 'left')), ('body', 'rump', ('top', 'right', 'left', 'back')),
                             ('neck', 'neck', ('back', 'right', 'left'))):
        for name in names:
            fc = p.face(part, name, tag=tag)
            region = (lambda x, y: True) if name in ('top', 'back') else upper_region(fc, 0.5)
            bp.spots(fc, spot, (p.variant.id, part, tag, name), density=0.2, radius=(1.0, 1.9), region=region,
                     glow=p.face(part, name, tag=tag, glow=True) if sg else None, glow_col=sg)
    belly = p.face('body', 'bottom', tag='barrel')
    bp.vgrad(belly, mix(rgb(e['coat'][0]), spot, 0.45), mix(rgb(e['coat'][0]), spot, 0.6))
    face = p.face('head', 'front', tag='face')
    for y in range(face.fh):
        half = 1 if y < face.fh // 3 else 2
        for x in range(face.fw // 2 - half, face.fw // 2 + half):
            face.fpx(x, y, mix(face.fget(x, y), spot, 0.8))
    for name in ('front', 'right', 'left', 'bottom'):
        nose = p.face('head', name, tag='nose')
        for x, y in bp.each(nose):
            nose.fpx(x, y, mix(nose.fget(x, y), spot, 0.35))
    muzzle_marks(p)
    horse_eye(p, e['eye'], glow=bool(glow))
    # the blanket: an embroidered border of leaves, a leaf in the middle, a leafy hem on the drapes
    trim_c = rgb(e['trim'])
    fc = p.face('body', 'top', tag='blanket')
    bp.trim(fc, trim_c, 1)
    for x in range(3, fc.fw - 3, 5):
        bp.blossom(fc, x, 3, leaf_b, trim_c, size=1)
        bp.blossom(fc, x, fc.fh - 4, leaf_b, trim_c, size=1)
    for name in ('right',):
        fc = p.face('body', name, tag='drape')
        bp.trim(fc, trim_c, 1, 'tlr')
        for x in range(2, fc.fw - 2, 6):
            for t in range(4):
                fc.fpx(x + t // 2, fc.fh // 2 - 3 + t, leaf_b)
            bp.blossom(fc, x + 1, fc.fh // 2 - 5, blossom, heart, size=1)
        base.pointed_bottom(fc, 4, step=4)
    for name in ('front', 'back'):
        base.pointed_bottom(p.face('body', name, tag='drape'), 4, step=2)
    for tag in ('pommel', 'cantle'):
        for name in ('front', 'back', 'right', 'left', 'top'):
            bp.trim(p.face('saddle', name, tag=tag), trim_c, 1, 't')
    for name in ('right', 'left', 'front', 'back'):
        bp.trim(p.face('saddle', name, index=0), trim_c, 1, 't')
    # the vine: little leaves along the bridle, girth and reins
    for part, tag in (('head', 'noseband'), ('head', 'cheekband'), ('body', 'girth'), ('reins', 'rein'),
                      ('reins', 'rein_loop')):
        for name in ('front', 'back', 'right', 'left'):
            fc = p.face(part, name, tag=tag)
            for y in range(1, fc.fh - 1, 4):
                fc.fpx(fc.fw // 2, y, leaf_b)
                if fc.fw > 2:
                    fc.fpx(min(fc.fw - 1, fc.fw // 2 + 1), y + 1, shade(leaf_b, 0.8))
    for name in ('right', 'front', 'back', 'left', 'top'):
        fc = p.face('body', name, tag='bag')
        bp.trim(fc, shade(rgb(e['saddle']), 0.7), 1, 't')
        bp.blossom(fc, fc.fw // 2, fc.fh // 2, blossom, heart, size=1)


BLOOM_COATS = [
    ('fawn', dict(coat=('#b07a48', '#7e5230', '#c99a66'), muzzle=('#6a4a34', '#4a3222', '#80583e'), spot='#f2e6d0',
                  leaf='#5e9a3a', leaf2='#8cbc4c', bark='#6b4a2e', blossom='#f4a6c0', blossom2='#fff0f4',
                  heart='#ffd34a', vine='#3f6a2a', blanket='#2f5a3a', trim='#d9b44a', saddle='#6e4a2a',
                  eye='#3a2414', soft_glow=True)),
    ('moss', dict(coat=('#5a4a32', '#3c3020', '#74603f'), muzzle=('#3e3224', '#2a2118', '#52422e'), spot='#d4dcae',
                  leaf='#3a6a2e', leaf2='#5f9440', bark='#4a3a28', blossom='#f8f8f0', blossom2='#e8f4ff',
                  heart='#f4d44a', vine='#2f5222', blanket='#3a4a2a', trim='#b8a060', saddle='#4e3a26',
                  eye='#20160c', soft_glow=True)),
    ('autumn', dict(coat=('#a4532a', '#73361a', '#c06c3c'), muzzle=('#6a3418', '#48220e', '#844422'), spot='#f6e2c4',
                    leaf='#c8622a', leaf2='#e8a23a', bark='#5a3a22', blossom='#f6d46a', blossom2='#fff4c8',
                    heart='#c03a2a', vine='#6a4a1e', blanket='#6a2a1a', trim='#e8b84a', saddle='#5a3018',
                    eye='#2a1408', soft_glow=True)),
    ('birch', dict(coat=('#d6d0c4', '#a8a294', '#ece8e0'), muzzle=('#8c8478', '#666054', '#a49c90'), spot='#5a5248',
                   leaf='#8ac46a', leaf2='#bce08e', bark='#e6e2d8', blossom='#c8a6ff', blossom2='#f0e6ff',
                   heart='#fff0a0', vine='#5a7a3a', blanket='#5a6a8a', trim='#e8e4d8', saddle='#7a6450',
                   eye='#2a2420', soft_glow=True)),
    ('spirit', dict(coat=('#1f4a4a', '#123030', '#2e6a66'), muzzle=('#163a3a', '#0c2424', '#22504e'), spot='#8fffe8',
                    leaf='#3ad6b0', leaf2='#8ff0d8', bark='#2a3a3a', blossom='#e8b0ff', blossom2='#f8e8ff',
                    heart='#ffffff', vine='#2a6a5a', blanket='#1a3a3a', trim='#8fffe8', saddle='#26343a',
                    eye='#8fffe8', glow=True, glow_col='#7ff0d8')),
]


def bloom_variant(cid, spec):
    pal = palette(spec,
                  mane={'base': spec['leaf'], 'style': 'leaf'},
                  lock={'base': spec['leaf'], 'style': 'leaf'},
                  tail={'base': spec['leaf'], 'style': 'leaf', 'ragged': False},
                  antler_leaf={'base': spec['leaf'], 'style': 'leaf', 'ragged': False},
                  garland={'base': spec['leaf2'], 'style': 'leaf', 'ragged': False},
                  wrap={'base': spec['leaf'], 'style': 'leaf', 'ragged': False},
                  fet={'base': spec['leaf2'], 'style': 'leaf'},
                  antler={'base': spec['bark'], 'style': 'bark', 'moss': spec['leaf2']},
                  blossom={'base': spec['blossom'], 'style': 'plain'},
                  blossom2={'base': spec['blossom2'], 'style': 'plain'},
                  vine={'base': spec['vine'], 'style': 'rope'},
                  saddle={'base': spec['saddle'], 'style': 'leather'},
                  saddle_wood={'base': mix_hex(spec['saddle'], '#a07850', 0.4), 'style': 'wood'},
                  bag={'base': spec['saddle'], 'style': 'leather'},
                  blanket={'base': spec['blanket'], 'style': 'cloth'},
                  hoof='#2a221c')
    extra = dict(spec, scale=1.0, hitbox=(1.25, 1.75), seat='saddle', glow_col=spec.get('glow_col', spec['blossom']))
    return Variant(cid, palette=pal, paint=bloom_paint, glow=True, extra=extra)


def mix_hex(a, b, t):
    ca, cb = rgb(a), rgb(b)
    return '#%02x%02x%02x' % tuple(int(round(x + (y - x) * t)) for x, y in zip(ca, cb))


def bloom_rig():
    n_mane = count(BLOOM_POLL) + count(BLOOM_CREST)
    return Rig('mount_horse_bloom', 'HorseBloomMount', bloom_geometry(),
               horse.animations(mane_n=n_mane, lock_n=2 * len(BLOOM_LOCKS)),
               [bloom_variant(cid, spec) for cid, spec in BLOOM_COATS], SLOTS, texel=2)


# ================================================================================================ tide

TIDE_POLL = (
    (0.0, -22.4, -14.6, 4, 1, 7, 66, 0, 90),
    (0.0, -21.0, -12.6, 5, 1, 9, 44, 0, 90),
    (1.9, -20.5, -12.4, 4, 2, 9, 18, 34, 40),
    (2.2, -17.5, -12.4, 4, 2, 8, -6, 30, 56),
)
TIDE_CREST = (
    (0.0, -15.5, -6.8, 7, 1, 11, 46, 0, 90),
    (2.0, -14.5, -6.8, 4, 2, 11, 6, 30, 45),
    (0.0, -12.5, -6.8, 7, 1, 11, 42, 0, 90),
    (2.2, -11.5, -6.8, 4, 2, 11, -2, 32, 52),
    (0.0, -9.5, -6.8, 7, 1, 11, 38, 0, 90),
    (2.4, -8.5, -6.8, 4, 2, 10, -10, 32, 58),
    (0.0, -6.5, -6.8, 6, 1, 10, 34, 0, 90),
    (2.4, -5.5, -6.8, 4, 2, 9, -16, 30, 62),
    (0.0, -3.5, -6.8, 6, 1, 9, 30, 0, 90),
    (0.0, -0.8, -6.8, 5, 1, 8, 26, 0, 90),
)
TIDE_LOCKS = (
    (3.6, -14.0, -8.6, 1, 4, 12, 14, 8),
    (3.7, -11.0, -8.0, 1, 4, 13, 18, 9),
    (3.7, -8.0, -7.6, 1, 3, 11, 24, 10),
    (3.6, -5.0, -7.2, 1, 3, 9, 28, 10),
)
TIDE_TAIL = (
    ('tail2', 0.0, -1.0, 16.0, 5, 2, 10, 62, 0, 0),
    ('tail2', 2.2, -0.5, 15.0, 4, 2, 10, 54, 18, 24),
    ('tail3', 0.0, 6.0, 15.2, 5, 2, 10, 42, 0, 0),
    ('tail3', 2.4, 6.5, 14.6, 4, 2, 10, 36, 16, 22),
    ('tail4', 0.0, 12.0, 15.6, 4, 2, 9, 30, 0, 0),
    ('tail4', 2.2, 12.5, 15.0, 4, 2, 9, 24, 14, 20),
    ('tail_end', 0.0, 17.0, 15.0, 7, 1, 8, 26, 0, 0),
    ('tail_end', 2.6, 17.0, 14.6, 5, 1, 7, 18, 30, 30),
)
TIDE_FINS = {'mane_%d' % i for i in (1, 2)} | {'mane_%d' % i for i in range(5, 100)}


def tide_fin_parts():
    """Which mane parts are fins (the plates standing in the middle); the others are foam."""
    names, n = [], 0
    for table in (TIDE_POLL, TIDE_CREST):
        for (x, *_rest) in table:
            for _ in ((1,) if x == 0.0 else (-1, 1)):
                n += 1
                names.append(('mane_%d' % n, x == 0.0))
    return names


def tide_geometry():
    poll, n = shards(TIDE_POLL, HEAD_TILT, 0, 'hair:foam')
    crest, n = shards(TIDE_CREST, NECK_TILT, n, 'hair:foam')
    fin_ear = [C(-3.5, -27.5, -15.5, 1, 6, 4, 'membrane:fin', tag='earfin')]
    head_cubes = skull(width=8, face_w=6, face_len=9) + [
        # a shell chanfron with a pearl, a bridle of coral beads
        C(-2.5, -21.6, -29, 5, 1, 8, 'shell:shell', tag='chanfron'),
        C(-1, -23.2, -23.0, 2, 2, 2, 'pearl:pearl', tag='brow_pearl'),
        C(-3.5, -21.5, -27.5, 7, 8, 1, 'rope:coral', tag='noseband'),
        C(-4.5, -22.5, -20.5, 9, 10, 1, 'rope:coral', tag='cheekband'),
        C(-5.0, -18, -21, 1, 2, 2, 'pearl:pearl', tag='rosette'),
        C(4.0, -18, -21, 1, 2, 2, 'pearl:pearl', share='rosette', mirror=True),
        # gills: three fine fins behind the cheeks
        C(-4.6, -19.5, -15.5, 1, 4, 3, 'membrane:gill', tag='gill'),
        C(3.6, -19.5, -15.5, 1, 4, 3, 'membrane:gill', share='gill', mirror=True),
    ]
    head_parts = [P('ear_r', (-3, -22, -14), *fin_ear, rot=(-30, 0, -28)),
                  P('ear_l', (3, -22, -14), C(2.5, -27.5, -15.5, 1, 6, 4, 'membrane:fin', share='earfin', mirror=True),
                    rot=(-30, 0, 28)),
                  P('reins', (0, -14.5, -27),
                    C(-4.6, -14.5, -27.5, 1, 13, 1, 'rope:coral', tag='rein'),
                    C(3.6, -14.5, -27.5, 1, 13, 1, 'rope:coral', share='rein', mirror=True),
                    C(-4.6, -2.0, -27.5, 9, 1, 1, 'rope:coral', tag='rein_loop'), rot=(38, 0, 0))]
    neck_cubes = [C(-1.5, -16.5, -8, 3, 16, 3, 'hair:foam', tag='crest'),
                  # a collar of shells and pearls
                  C(-4.0, -4.5, -15.6, 8, 3, 1, 'rope:coral', tag='collar'),
                  *pair(-4.4, -4.5, -15.0, 1, 3, 8, 'rope:coral', 'collar_side'),
                  C(-1.5, -2.5, -16.4, 3, 3, 1, 'shell:shell', tag='collar_shell')]
    saddle = P('saddle', (0, -5, 0),
               C(-4.5, -6, -4, 9, 2, 9, 'leather:saddle'),
               C(-3, -8, -5, 6, 2, 2, 'shell:shell', tag='pommel'),
               C(-4, -9, 4, 8, 3, 2, 'shell:shell', tag='cantle'),
               *pair(-6.6, -5, -3.5, 1, 4, 8, 'leather:saddle', 'skirt'))
    body_cubes = [
        C(-6, -4, -12, 12, 12, 24, 'fur:coat', tag='barrel'),
        C(-5, -3, -15, 10, 9, 3, 'fur:coat', tag='chest'),
        C(-5, -4, 9, 10, 10, 5, 'fur:coat', tag='rump'),
        # a wave-patterned saddle cloth with a scalloped hem, a breast strap with a shell, a conch at the saddle
        C(-6.5, -4.5, -6, 13, 1, 13, 'cloth:seacloth', tag='blanket'),
        *pair(-7, -4.0, -6.0, 1, 9, 12, 'cloth:seacloth', 'drape'),
        C(-6.0, -1.0, -15.6, 12, 2, 1, 'rope:coral', tag='breast'),
        C(-1.5, -1.5, -16.4, 3, 3, 1, 'shell:shell', tag='breast_shell'),
        *pair(-6.6, -3.5, -1.0, 1, 12, 2, 'rope:coral', 'girth'),
        C(-5.5, 8, -1.0, 11, 1, 2, 'rope:coral', tag='girth_under'),
        *pair(-8.6, -2.5, 4.0, 2, 3, 5, 'shell:conch', 'conch'),
        *pair(-8.1, -3.0, 8.5, 1, 2, 2, 'shell:conch', 'conch_tip'),
    ]
    leg_spec = dict(front=(5, 6), hind=(6, 7), shin=4, hoof=(5, 3, 6), fetlock=(5, 1, 5), fet_mat='hair:foam',
                    shin_mat='scale:legscale', hoof_mat='plain:hoof',
                    shin_extra=[(-0.5, 15.0, 1.5, 1, 6, 3, 'membrane:legfin', 'legfin')])
    return assemble(head_cubes, head_parts, neck_cubes, [], body_cubes, [],
                    tail(TIDE_TAIL, 'hair:foam'), leg_spec, saddle, poll, crest, locks(TIDE_LOCKS, 'hair:foam'),
                    neck=(7, 9))


def tide_paint(p):
    e = p.variant.extra
    deep, body_c, crest_c = rgb(e['deep']), rgb(e['water']), rgb(e['foam'])
    fin_c, rib = rgb(e['fin']), rgb(e['rib'])
    glow_c = rgb(e['glow_col'])
    shine = e.get('shine', 0.6)
    # the fin crest and the foam: plates in the middle, waves to the sides
    for part, is_fin in tide_fin_parts():
        if is_fin:
            for fc, gc, u_of, across, width in blade_faces(p, part, 'z'):
                # a membrane from the dark root to a pale free edge, rays running along it, the tip rounded off
                for x, y in bp.each(fc):
                    u = u_of(y)
                    a = across(x)
                    if u > 0.7 and abs(a) > (1.0 - u) / 0.3 * width * 0.5 + 0.5:
                        fc.fclear(x, y)
                        continue
                    t = min(1.0, u * 1.2)
                    c = mix(shade(fin_c, 0.7), mix(fin_c, (255, 255, 255), 0.55), t)
                    ray = int(a + width) % 3 == 0
                    if ray:
                        c = mix(c, rib, 0.75)
                    edge = abs(a) > width * 0.5 - 1.2
                    if edge:
                        c = mix(c, (255, 255, 255), 0.5)
                    fc.fpx(x, y, c)
                    if ray and int(y) % 3 == 0 or edge and u > 0.5:
                        gc.fpx(x, y, shade(glow_c, shine))
            for fc, gc in side_faces(p, part, 'z'):
                bp.fill(fc, shade(fin_c, 0.8))
        else:
            blade_ramp(p, part, 'z', [deep, body_c, mix(body_c, crest_c, 0.6), crest_c], keys=(p.variant.id,))
    for part in blade_parts(p, 'lock', 2 * len(TIDE_LOCKS)):
        blade_ramp(p, part, 'x', [deep, body_c, mix(body_c, crest_c, 0.5), crest_c], keys=(p.variant.id,))
    n_tail = tail_count(TIDE_TAIL)
    for i, part in enumerate(blade_parts(p, 'tail_blade', n_tail)):
        if p.rig.parts[part].parent.name == 'tail_end':
            for fc, gc, u_of, across, width in blade_faces(p, part, 'zh'):
                bp.fin(fc, fin_c, rib, mix(fin_c, (255, 255, 255), 0.45), (p.variant.id, part, fc.face), ribs=4,
                       glow=gc, glow_col=shade(glow_c, shine), root_at='top')
        else:
            blade_ramp(p, part, 'zh', [deep, body_c, mix(body_c, crest_c, 0.5), crest_c], keys=(p.variant.id,))
    for part, tag in (('tail', 'root'), ('tail2', None), ('tail3', None), ('tail4', None), ('neck', 'crest'),
                      ('front_right_shin', 'ffet'), ('hind_right_shin', 'hfet')):
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            fc = p.face(part, name, tag=tag)
            bp.vgrad(fc, mix(body_c, crest_c, 0.3), deep)
            bp.frost(fc, crest_c, (p.variant.id, part, name), density=1.5)
    # fins: ears, gills, leg fins
    for part, tag, ribs in (('ear_r', 'earfin', 3), ('head', 'gill', 2), ('front_right_shin', 'flegfin', 2),
                            ('hind_right_shin', 'hlegfin', 2)):
        for name in ('right', 'left'):
            bp.fin(p.face(part, name, tag=tag), fin_c, rib, mix(fin_c, (255, 255, 255), 0.45),
                   (p.variant.id, part, name), ribs=ribs, glow=p.face(part, name, tag=tag, glow=True),
                   glow_col=shade(glow_c, shine), root_at='bottom' if part.startswith('ear') else 'top')
        for name in ('front', 'back', 'top', 'bottom'):
            bp.fill(p.face(part, name, tag=tag), shade(fin_c, 0.8))
    # the coat: a paler belly, scaled legs, rows of glowing spots along the flanks, the muzzle
    coat = rgb(e['coat'][0])
    belly = p.face('body', 'bottom', tag='barrel')
    bp.vgrad(belly, mix(coat, crest_c, 0.35), mix(coat, crest_c, 0.5))
    # light through water: soft wavering caustic lines over the coat
    for part, tag, names in (('body', 'barrel', ('right', 'left', 'top')), ('body', 'rump', ('right', 'left', 'back')),
                             ('neck', 'neck', ('right', 'left', 'back')), ('body', 'chest', ('front', 'right', 'left'))):
        for name in names:
            fc = p.face(part, name, tag=tag)
            for x, y in bp.each(fc):
                w1 = math.sin(x * 0.55 + math.sin(y * 0.4) * 1.8)
                w2 = math.sin(y * 0.6 + math.sin(x * 0.33) * 2.1)
                if w1 * w2 > 0.72:
                    fc.fpx(x, y, mix(fc.fget(x, y), crest_c, 0.35))
    for name in ('right', 'left'):
        fc = p.face('body', name, tag='barrel')
        gc = p.face('body', name, tag='barrel', glow=True)
        for y in range(fc.fh):
            t = y / max(1, fc.fh - 1)
            for x in range(fc.fw):
                if t > 0.62:
                    fc.fpx(x, y, mix(fc.fget(x, y), crest_c, (t - 0.62) * 0.9))
        for i, x in enumerate(range(4, fc.fw - 3, 5)):
            y = int(fc.fh * 0.56 + 2.0 * math.sin(i * 0.9))
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                fc.fpx(x + dx, y + dy, glow_c)
                gc.fpx(x + dx, y + dy, shade(glow_c, shine))
    for shin in ('front_right_shin', 'hind_right_shin'):
        tag = ('f' if shin.startswith('front') else 'h') + 's'
        for name in ('front', 'right', 'left', 'back'):
            fc = p.face(shin, name, tag=tag)
            bp.fish_scales(fc, rgb(e['scale_c']), mix(rgb(e['scale_c']), (255, 255, 255), 0.45),
                           shade(rgb(e['scale_c']), 0.55), size=6)
    muzzle_marks(p, nostril=shade(deep, 0.5))
    horse_eye(p, e['eye'], glow=True)
    # shells and pearls
    shell, shell_d = rgb(e['shell']), shade(rgb(e['shell']), 0.7)
    for part, tag in (('head', 'chanfron'), ('neck', 'collar_shell'), ('body', 'breast_shell'), ('saddle', 'pommel'),
                      ('saddle', 'cantle')):
        for name in ('front', 'top', 'right', 'left', 'back'):
            fc = p.face(part, name, tag=tag)
            w, h = fc.fw, fc.fh
            cx = (w - 1) / 2.0
            for x, y in bp.each(fc):
                a = math.atan2(y + 2.0, x - cx)
                ridge = int(a * 6.0) % 2 == 0
                fc.fpx(x, y, mix(shell, (255, 255, 255), 0.25) if ridge else shell_d)
            bp.trim(fc, mix(shell, (255, 255, 255), 0.5), 1, 'tb')
    for part, tag in (('head', 'brow_pearl'), ('head', 'rosette')):
        for name in ('front', 'top', 'right', 'left', 'back', 'bottom'):
            bp.pearl(p.face(part, name, tag=tag), p.face(part, name, tag=tag, glow=True), shade(glow_c, 0.9))
    for name in ('right', 'left', 'front', 'back', 'top', 'bottom'):
        for tag in ('conch', 'conch_tip'):
            fc = p.face('body', name, tag=tag)
            for x, y in bp.each(fc):
                fc.fpx(x, y, mix(shell, (255, 200, 190), 0.5) if (x + 2 * y) % 5 < 2 else shell)
    # the coral bead ropes: beads with a light top
    for part, tag in (('head', 'noseband'), ('head', 'cheekband'), ('neck', 'collar'), ('neck', 'collar_side'),
                      ('body', 'breast'), ('body', 'girth'), ('body', 'girth_under'), ('reins', 'rein'),
                      ('reins', 'rein_loop')):
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            fc = p.face(part, name, tag=tag)
            for x, y in bp.each(fc):
                bead = (x + y) % 3
                fc.fpx(x, y, mix(rgb(e['coral']), (255, 255, 255), 0.35) if bead == 0 else
                       rgb(e['coral']) if bead == 1 else shade(rgb(e['coral']), 0.7))
    # the saddle cloth: rolling waves embroidered in white, a scalloped hem
    for name in ('right',):
        fc = p.face('body', name, tag='drape')
        for y0 in (fc.fh // 3, 2 * fc.fh // 3):
            for x in range(fc.fw):
                y = y0 + int(round(1.6 * math.sin(x * 0.7)))
                fc.fpx(x, y, crest_c)
                if math.sin(x * 0.7) > 0.8:
                    fc.fpx(x, y - 1, crest_c)
        bp.trim(fc, rgb(e['trim']), 1, 'tlr')
        for x in range(fc.fw):
            depth = int(round(2.5 * (1 - math.cos((x % 6) / 6.0 * 2 * math.pi)) / 2))
            for d in range(depth + 1):
                fc.fclear(x, fc.fh - 1 - d)
    fc = p.face('body', 'top', tag='blanket')
    bp.trim(fc, rgb(e['trim']), 1)
    for name in ('front', 'back'):
        base.pointed_bottom(p.face('body', name, tag='drape'), 3, step=2)


TIDE_COATS = [
    ('lagoon', dict(coat=('#2f7f8a', '#1d5662', '#47a0a8'), muzzle=('#25646e', '#174650', '#34808a'),
                    deep='#1b4f6a', water='#3aa6c0', foam='#eefcff', fin='#5fd0d8', rib='#2a8a9a',
                    scale_c='#2a7684', shell='#f0d8c8', coral='#e8706a', trim='#f2e2b0', eye='#9ff4ff',
                    glow_col='#7fe8ff', seacloth='#1f5a78')),
    ('deep', dict(coat=('#1e2f5a', '#121c3a', '#2c4478'), muzzle=('#172447', '#0e162e', '#22345e'),
                  deep='#101c3c', water='#2a4c9a', foam='#d8ecff', fin='#3a70c8', rib='#1e3c80',
                  scale_c='#1c2c58', shell='#d8d0e8', coral='#8a6ad8', trim='#c8d4ff', eye='#8fd8ff',
                  glow_col='#5ab8ff', seacloth='#141e44')),
    ('coral', dict(coat=('#c8786a', '#9a5248', '#e09484'), muzzle=('#a45e52', '#7a4038', '#bc7466'),
                   deep='#8a3a4a', water='#ea8a7a', foam='#fff4ee', fin='#ffb0a0', rib='#d06a62',
                   scale_c='#b4685c', shell='#fff0e0', coral='#ff5a5a', trim='#fff0d0', eye='#fff0b0',
                   glow_col='#ffd0a0', seacloth='#7a2f3a', shine=0.45)),
    ('pearl', dict(coat=('#cfdce4', '#a2b4c0', '#eaf2f6'), muzzle=('#a8b8c4', '#8698a6', '#c2d0da'),
                   deep='#6e8fa8', water='#a8d4e8', foam='#ffffff', fin='#b8e4f0', rib='#7ab0c8',
                   scale_c='#9fb8c8', shell='#fff8f0', coral='#7ac8d8', trim='#ffffff', eye='#a8f0ff',
                   glow_col='#c8f4ff', seacloth='#4a7a94', shine=0.45)),
    ('abyss', dict(coat=('#0c1424', '#060a14', '#16243c'), muzzle=('#0a1020', '#04070e', '#121c30'),
                   deep='#060c1c', water='#123058', foam='#7ffcff', fin='#1ab8d8', rib='#7ffcff',
                   scale_c='#0e1a30', shell='#5ae8f0', coral='#2ad8c8', trim='#7ffcff', eye='#7ffcff',
                   glow_col='#3afcff', seacloth='#08142a', shine=1.0)),
]


def tide_variant(cid, spec):
    pal = palette(spec,
                  foam={'base': spec['water'], 'style': 'hair'},
                  fin={'base': spec['fin'], 'style': 'plain'},
                  gill={'base': spec['fin'], 'style': 'plain'},
                  legfin={'base': spec['fin'], 'style': 'plain'},
                  legscale={'base': spec['scale_c'], 'style': 'scale'},
                  shell={'base': spec['shell'], 'style': 'plain'},
                  conch={'base': spec['shell'], 'style': 'plain'},
                  pearl={'base': '#f4f6ff', 'style': 'plain'},
                  coral={'base': spec['coral'], 'style': 'plain'},
                  saddle={'base': '#3a4a5a', 'style': 'leather'},
                  seacloth={'base': spec['seacloth'], 'style': 'cloth'},
                  hoof='#5a6a78')
    extra = dict(spec, scale=1.0, hitbox=(1.3, 1.75), seat='saddle')
    return Variant(cid, palette=pal, paint=tide_paint, glow=True, extra=extra)


def tide_rig():
    n_mane = count(TIDE_POLL) + count(TIDE_CREST)
    return Rig('mount_horse_tide', 'HorseTideMount', tide_geometry(),
               horse.animations(mane_n=n_mane, lock_n=2 * len(TIDE_LOCKS)),
               [tide_variant(cid, spec) for cid, spec in TIDE_COATS], SLOTS, texel=2)


# ================================================================================================ gale

GALE_POLL = (
    (0.0, -22.4, -13.2, 5, 2, 11, 30, 0, 0),
    (1.3, -22.4, -14.2, 3, 2, 10, 34, 24, 30),
    (0.0, -20.0, -12.4, 6, 2, 13, 14, 0, 0),
    (1.9, -20.5, -12.4, 4, 2, 12, 8, 34, 40),
    (2.2, -17.5, -12.4, 4, 2, 11, -10, 34, 56),
)
GALE_CREST = (
    (0.0, -15.5, -6.8, 6, 2, 14, 8, 0, 0),
    (2.0, -14.5, -6.8, 5, 2, 13, -2, 38, 45),
    (0.0, -12.8, -6.8, 6, 2, 14, 4, 0, 0),
    (2.2, -11.5, -6.8, 5, 2, 13, -8, 40, 52),
    (0.0, -10.0, -6.8, 6, 2, 13, 0, 0, 0),
    (2.4, -8.5, -6.8, 5, 2, 12, -14, 40, 58),
    (0.0, -7.0, -6.8, 6, 2, 12, -2, 0, 0),
    (2.4, -5.5, -6.8, 5, 2, 10, -18, 36, 62),
    (0.0, -4.0, -6.8, 5, 2, 11, -4, 0, 0),
    (0.0, -1.0, -6.8, 4, 2, 9, -6, 0, 0),
)
GALE_LOCKS = (
    (3.8, -15.0, -9.0, 1, 3, 14, 34, 14),
    (3.9, -12.0, -8.2, 1, 3, 15, 38, 16),
    (3.9, -9.0, -7.8, 1, 3, 13, 42, 16),
)
GALE_TAIL = (
    ('tail2', 0.0, -1.0, 16.0, 5, 2, 12, 72, 0, 0),
    ('tail2', 2.2, -0.5, 15.0, 4, 2, 12, 66, 20, 24),
    ('tail3', 0.0, 6.0, 15.2, 5, 2, 12, 56, 0, 0),
    ('tail3', 2.4, 6.5, 14.6, 4, 2, 11, 50, 18, 22),
    ('tail4', 0.0, 12.0, 15.6, 4, 2, 11, 44, 0, 0),
    ('tail4', 2.2, 12.5, 15.0, 3, 2, 10, 38, 16, 20),
    ('tail_end', 0.0, 17.5, 15.0, 3, 2, 9, 34, 0, 0),
)
# the wings: primaries (fan out, length) from the hand, secondaries (length) from the arm
GALE_PRIMARIES = ((4, 11), (14, 12), (25, 13), (37, 13), (50, 12), (63, 10))
GALE_SECONDARIES = (9, 10, 10, 10)
GALE_FOLD = 'wing'


def gale_wing(side):
    """A feathered wing at the shoulder, built spread (span along x): a silver-edged arm with two rows of coverts and
    four secondaries, the hand with a covert row and six primaries fanned like fingers. Folded along the flank (just
    outside it) by the idle animation, spread by the glide."""
    s = -1 if side == 'r' else 1
    mirror = side == 'l'
    a0 = 6.5            # the arm starts at the flank

    def X(a, w):
        return -(a + w) if s < 0 else a

    def cube(x, y, z, w, h, d, mat, t):
        return C(x, y, z, w, h, d, mat, tag=t if side == 'r' else None, share=t if side == 'l' else None,
                 mirror=mirror)

    secondaries = [P('sec_%s%d' % (side, i + 1), (s * (a0 + 1.0 + 2.0 * i), -3.9, -9.5),
                     cube(X(a0 - 1.0 + 2.0 * i, 4), -3.9, -9.5, 4, 1, ln,
                          'feather:flight_a' if i % 2 == 0 else 'feather:flight_b', 'gsec%d' % i),
                     rot=(2, s * (3 - i), 0))
                   for i, ln in enumerate(GALE_SECONDARIES)]
    primaries = [P('prim_%s%d' % (side, i + 1), (s * (a0 + 10.0 + 1.4 * i), -4.0, -12.0),
                   cube(X(a0 + 8.0 + 1.4 * i, 4), -4.0, -12.0, 4, 1, ln,
                        'feather:flight_a' if i % 2 == 0 else 'feather:flight_b', 'gprim%d' % i),
                   rot=(2, s * fan, 0))
                 for i, (fan, ln) in enumerate(GALE_PRIMARIES)]
    hand = P('wing_%s2' % side, (s * (a0 + 7.0), -4.5, -15.0),
             cube(X(a0 + 7, 9), -5.5, -16.0, 9, 2, 2, 'metal:wing_edge', 'gedge2'),
             cube(X(a0 + 7, 9), -4.7, -14.0, 9, 1, 3, 'feather:covert_a', 'ghcov'),
             *primaries)
    return P('wing_%s' % side, (s * a0, -4.5, -15.0),
             cube(X(a0, 8), -5.5, -16.0, 8, 2, 2, 'metal:wing_edge', 'gedge1'),
             cube(X(a0, 8), -4.7, -14.0, 8, 1, 3, 'feather:covert_b', 'gcov1'),
             cube(X(a0, 8), -4.4, -11.5, 8, 1, 3, 'feather:covert_a', 'gcov2'),
             *secondaries, hand)


def gale_fold():
    """The folded wings (constant channels of the idle): each wing swings back along the flank and hangs down,
    the hand folds along the arm, the feathers lie along the bones."""
    out = {}
    for side, s in (('r', -1), ('l', 1)):
        out['wing_' + side] = dict(rot=[(0, 0, -s * 92, -s * 78), (3.2, 0, -s * 92, -s * 78)])
        out['wing_%s2' % side] = dict(rot=[(0, 0, -s * 8, 0), (3.2, 0, -s * 8, 0)])
        for i, (fan, _) in enumerate(GALE_PRIMARIES):
            out['prim_%s%d' % (side, i + 1)] = dict(rot=[(0, 0, s * (fan - 4) * -1.0, 0),
                                                         (3.2, 0, s * (fan - 4) * -1.0, 0)])
    return out


def gale_glide():
    """The glide: the fold undone (the wings stand out at the shoulders, the feathers fan), a slow sway."""
    G = 2.4
    out = {}
    fold = gale_fold()
    for bone, ch in fold.items():
        rx, ry, rz = ch['rot'][0][1:]
        if bone.startswith('wing_') and not bone.endswith('2'):
            s = -1 if bone.endswith('r') else 1
            out[bone] = dict(rot=[(0, -rx, -ry, -rz + s * 6), (G * 0.5, -rx, -ry, -rz + s * 12), (G, -rx, -ry, -rz + s * 6)])
        else:
            out[bone] = dict(rot=[(0, -rx, -ry, -rz), (G * 0.5, -rx + 3, -ry, -rz), (G, -rx, -ry, -rz)])
    out['body'] = dict(rot=[(0, -6, 0, 0), (G, -6, 0, 0)])
    out['tail'] = dict(rot=[(0, 30, 0, 0), (G * 0.5, 36, 0, 0), (G, 30, 0, 0)])
    out['front_right_leg'] = dict(rot=[(0, -30, 0, 0), (G, -30, 0, 0)])
    out['front_left_leg'] = dict(rot=[(0, -28, 0, 0), (G, -28, 0, 0)])
    out['front_right_shin'] = dict(rot=[(0, 60, 0, 0), (G, 60, 0, 0)])
    out['front_left_shin'] = dict(rot=[(0, 56, 0, 0), (G, 56, 0, 0)])
    out['hind_right_leg'] = dict(rot=[(0, 34, 0, 0), (G, 34, 0, 0)])
    out['hind_left_leg'] = dict(rot=[(0, 30, 0, 0), (G, 30, 0, 0)])
    return anim('glide', G, loop=True, **out)


def gale_geometry():
    poll, n = shards(GALE_POLL, HEAD_TILT, 0, 'hair:mane')
    crest, n = shards(GALE_CREST, NECK_TILT, n, 'hair:mane')
    head_cubes = skull(width=9, face_w=7, face_len=9) + [
        C(-2, -21.6, -29, 4, 1, 8, 'metal:silver', tag='chanfron'),
        C(-1, -22.6, -23.0, 2, 1, 2, 'gem:gem', tag='brow_gem'),
        C(-4, -21.5, -27.5, 8, 8, 1, 'leather:bridle', tag='noseband'),
        C(-5, -22.5, -20.5, 10, 11, 1, 'leather:bridle', tag='cheekband'),
        C(-5.3, -17, -21, 1, 2, 2, 'metal:silver', tag='rosette'),
        C(4.3, -17, -21, 1, 2, 2, 'metal:silver', share='rosette', mirror=True),
        C(-2, -23, -15.5, 4, 1, 4, 'hair:mane', tag='poll'),
    ]
    # little wing plumes on the cheek band
    plume = [P('plume_r', (-5.2, -20.0, -20.5), *horse.blade(-5.2, -20.0, -20.5, 3, 1, 6, mat='feather:plume'),
               rot=horse.aim(40, -60, -60, HEAD_TILT)),
             P('plume_l', (5.2, -20.0, -20.5), *horse.blade(5.2, -20.0, -20.5, 3, 1, 6, mat='feather:plume'),
               rot=horse.aim(40, 60, 60, HEAD_TILT))]
    head_parts = ears(w=2, h=5, d=2, tilt=-46, splay=10) + plume + [
        P('reins', (0, -14.5, -27),
          C(-5.1, -14.5, -27.5, 1, 13, 1, 'leather:rein', tag='rein'),
          C(4.1, -14.5, -27.5, 1, 13, 1, 'leather:rein', share='rein', mirror=True),
          C(-5.1, -2.0, -27.5, 10, 1, 1, 'leather:rein', tag='rein_loop'), rot=(38, 0, 0))]
    neck_cubes = [C(-2, -16.5, -8, 4, 16, 3, 'hair:mane', tag='crest'),
                  C(-4.5, -4, -16, 9, 4, 10, 'metal:silver', tag='collar')]
    saddle = P('saddle', (0, -5, 0),
               C(-4.5, -6, -4, 9, 2, 9, 'leather:saddle'),
               C(-3, -8, -5, 6, 2, 2, 'leather:saddle_rim', tag='pommel'),
               C(-4, -9, 4, 8, 3, 2, 'leather:saddle_rim', tag='cantle'),
               C(-1.5, -10.0, 4.5, 3, 1, 1, 'metal:silver', tag='cantle_wing'))
    body_cubes = [
        C(-6, -4.5, -12, 12, 12, 24, 'fur:coat', tag='barrel'),
        C(-5, -3.5, -15, 10, 9, 3, 'fur:coat', tag='chest'),
        C(-5, -4.5, 9, 10, 10, 5, 'fur:coat', tag='rump'),
        # a silver breastplate with the wing emblem, a sky-blue saddle cloth, the girth
        C(-5.0, -3.5, -16.4, 10, 7, 1, 'metal:silver', tag='peytral'),
        C(-2.5, -2.5, -17.1, 5, 4, 1, 'gem:gem', tag='emblem'),
        C(-6.5, -5.0, -5, 13, 1, 11, 'cloth:skycloth', tag='blanket'),
        *pair(-6.6, -4.0, -1.0, 1, 12, 2, 'leather:strap', 'girth'),
        C(-5.5, 7.5, -1.0, 11, 1, 2, 'leather:strap', tag='girth_under'),
        C(-6.0, -5.2, 10.5, 12, 1, 2, 'metal:silver', tag='band'),
    ]
    leg_spec = dict(front=(5, 6), hind=(6, 7), shin=4, hoof=(5, 3, 6), fetlock=(6, 3, 6), fet_mat='feather:feathering',
                    hoof_mat='plain:hoof',
                    shin_extra=[(-2.5, 19.6, -2.5, 5, 1, 5, 'metal:silver', 'shoe_band')])
    return assemble(head_cubes, head_parts, neck_cubes, [], body_cubes, [gale_wing('r'), gale_wing('l')],
                    tail(GALE_TAIL, 'hair:tail'), leg_spec, saddle, poll, crest, locks(GALE_LOCKS, 'hair:mane'))


def gale_paint(p):
    e = p.variant.extra
    hair, hair_tip = rgb(e['hair']), rgb(e['hair_tip'])
    n_mane = count(GALE_POLL) + count(GALE_CREST)
    for part in blade_parts(p, 'mane', n_mane):
        blade_ramp(p, part, 'z', [shade(hair, 0.85), hair, mix(hair, hair_tip, 0.5), hair_tip], keys=(p.variant.id,))
    for part in blade_parts(p, 'lock', 2 * len(GALE_LOCKS)):
        blade_ramp(p, part, 'x', [shade(hair, 0.85), hair, hair_tip], keys=(p.variant.id,))
    for part in blade_parts(p, 'tail_blade', tail_count(GALE_TAIL)):
        blade_ramp(p, part, 'zh', [shade(hair, 0.85), hair, mix(hair, hair_tip, 0.6), hair_tip], keys=(p.variant.id,))
    # wings: feathers with a dark shaft, wind-lit edges, the outer ones tipped; coverts as shingles
    wa, wb, wtip = rgb(e['wing']), rgb(e['wing2']), rgb(e['wing_tip'])
    glow_tip = e.get('glow_tips')
    for i, (fan, ln) in enumerate(GALE_PRIMARIES):
        for name in ('top', 'bottom'):
            fc = p.face('prim_r%d' % (i + 1), name, tag='gprim%d' % i)
            gc = p.face('prim_r%d' % (i + 1), name, tag='gprim%d' % i, glow=True)
            import rig_griffin
            rig_griffin.long_feather(fc, wa if i % 2 == 0 else wb, shade(wb, 0.8))
            for y in range(max(2, fc.fh // 4)):
                for x in range(fc.fw):
                    if fc.img[fc.v * fc.k + y, fc.u * fc.k + x, 3]:
                        fc.fpx(x, y, mix(wtip, fc.fget(x, y), y / max(1, fc.fh // 4)))
                        if glow_tip:
                            gc.fpx(x, y, shade(wtip, 0.9))
    for i, ln in enumerate(GALE_SECONDARIES):
        for name in ('top', 'bottom'):
            import rig_griffin
            rig_griffin.long_feather(p.face('sec_r%d' % (i + 1), name, tag='gsec%d' % i), wa if i % 2 else wb,
                                     shade(wb, 0.8))
    import rig_griffin
    for part, tag, c in (('wing_r', 'gcov1', wb), ('wing_r', 'gcov2', wa), ('wing_r2', 'ghcov', wa)):
        for name in ('top', 'bottom'):
            rig_griffin.covert_row(p.face(part, name, tag=tag), c, shade(c, 0.75))
    for part, tag in (('wing_r', 'gedge1'), ('wing_r2', 'gedge2')):
        for name in ('top', 'front', 'back', 'right', 'left', 'bottom'):
            fc = p.face(part, name, tag=tag)
            for x in range(0, fc.fw, 5):
                for y in range(fc.fh):
                    fc.fpx(x, y, shade(rgb(e['silver']), 0.7))
            bp.trim(fc, mix(rgb(e['silver']), (255, 255, 255), 0.5), 1, 't')
    for side in ('r', 'l'):
        for name in ('top', 'bottom'):
            fc = p.face('plume_' + side, name, index=0)
            rig_griffin.long_feather(fc, wa, shade(wb, 0.8))
    # feathered fetlocks
    for shin in ('front_right_shin', 'hind_right_shin'):
        pre = 'f' if shin.startswith('front') else 'h'
        for name in ('front', 'back', 'right', 'left', 'top'):
            fc = p.face(shin, name, tag=pre + 'fet')
            bp.fur_tufts(fc, hair, mix(hair, (255, 255, 255), 0.4), shade(hair, 0.75), (p.variant.id, shin, name))
    # the coat: dapples (silver), a wind-swept blaze down the face, pale lower legs
    coat = rgb(e['coat'][0])
    if e.get('dapple'):
        for part, tag, names in (('body', 'barrel', ('right', 'left', 'top')), ('body', 'rump', ('right', 'left', 'back')),
                                 ('neck', 'neck', ('right', 'left'))):
            for name in names:
                fc = p.face(part, name, tag=tag)
                bp.spots(fc, rgb(e['dapple']), (p.variant.id, part, name), density=0.22, radius=(1.2, 2.2))
    face = p.face('head', 'front', tag='face')
    for y in range(face.fh):
        for x in range(face.fw // 2 - 2, face.fw // 2 + 2):
            face.fpx(x, y, mix(face.fget(x, y), (250, 250, 252), 0.85))
    muzzle_marks(p)
    horse_eye(p, e['eye'], glow=True)
    # silver tack with blue gems; the wing emblem on the breastplate
    silver = rgb(e['silver'])
    for part, tag in (('head', 'chanfron'), ('neck', 'collar'), ('body', 'peytral'), ('body', 'band'),
                      ('saddle', 'cantle_wing')):
        for name in ('front', 'top', 'right', 'left', 'back'):
            fc = p.face(part, name, tag=tag)
            bp.trim(fc, mix(silver, (255, 255, 255), 0.6), 1, 'tb')
    fc = p.face('body', 'front', tag='peytral')
    for i in range(6):
        for s in (-1, 1):
            x = fc.fw // 2 + s * (3 + i)
            for y in range(2, 2 + max(1, 6 - i)):
                fc.fpx(x, y + i // 2, mix(silver, (255, 255, 255), 0.7))
    for name in ('front', 'top', 'right', 'left'):
        bp.gem(p.face('body', name, tag='emblem'), p.face('body', name, tag='emblem', glow=True), rgb(e['gem']),
               mix(rgb(e['gem']), (255, 255, 255), 0.3))
        bp.gem(p.face('head', name, tag='brow_gem'), p.face('head', name, tag='brow_gem', glow=True), rgb(e['gem']),
               mix(rgb(e['gem']), (255, 255, 255), 0.3))
    fc = p.face('body', 'top', tag='blanket')
    bp.trim(fc, silver, 1)
    for x in range(4, fc.fw - 4, 7):
        for t in range(3):
            fc.fpx(x + t, 3 + t, silver)
            fc.fpx(x - t, 3 + t, silver)
    for part, tag in (('head', 'noseband'), ('head', 'cheekband'), ('body', 'girth')):
        for name in ('front', 'back', 'right', 'left'):
            fc = p.face(part, name, tag=tag)
            for x in range(1, fc.fw - 1, 4):
                fc.fpx(x, fc.fh // 2, silver)
    for shin in ('front_right_shin', 'hind_right_shin'):
        pre = 'f' if shin.startswith('front') else 'h'
        for name in ('front', 'right', 'left', 'back'):
            bp.trim(p.face(shin, name, tag=pre + 'shoe_band'), mix(silver, (255, 255, 255), 0.5), 1, 't')


GALE_COATS = [
    ('silver', dict(coat=('#a9adb6', '#7e838e', '#c8ccd4'), muzzle=('#6e727c', '#50545c', '#868a94'), dapple='#dfe3ea',
                    hair='#f2f5fa', hair_tip='#b9c8de', wing='#f4f7fc', wing2='#c9d6ea', wing_tip='#8fb4e0',
                    silver='#d8dee8', gem='#5ab0ff', cloth='#3a6ab0', eye='#9fe0ff')),
    ('dawn', dict(coat=('#e2c9a0', '#bca07a', '#f2dfbc'), muzzle=('#b4987a', '#8c745a', '#c8ad8e'), dapple='#fff2dc',
                  hair='#fff6e6', hair_tip='#f6c8a0', wing='#fff8ee', wing2='#f8dcc0', wing_tip='#f0a080',
                  silver='#f0d8a0', gem='#ff9a6a', cloth='#b0603a', eye='#ffe0a0')),
    ('slate', dict(coat=('#4a505c', '#30343c', '#646a78'), muzzle=('#363a44', '#24272e', '#4a4f5a'), dapple='#6e7686',
                   hair='#d0d8e4', hair_tip='#7c8ca4', wing='#d8dee8', wing2='#8e9cb4', wing_tip='#3e4a62',
                   silver='#b8c2d0', gem='#7ad0ff', cloth='#26324a', eye='#a8e8ff')),
    ('sunset', dict(coat=('#a0583a', '#743c24', '#bc7450'), muzzle=('#6e3c26', '#4e2a1a', '#86503a'), dapple='#c8805a',
                    hair='#ffe2c8', hair_tip='#ff8a6a', wing='#fff0e4', wing2='#ffc0a0', wing_tip='#d0506a',
                    silver='#f4c890', gem='#ff6a8a', cloth='#6a2a3a', eye='#ffd0a0')),
    ('moonlit', dict(coat=('#232a4e', '#151a34', '#343e6c'), muzzle=('#1c2240', '#11142a', '#283056'), dapple='#3e4a80',
                     hair='#e8eeff', hair_tip='#9fb8ff', wing='#eef2ff', wing2='#aebcf0', wing_tip='#f4f0ff',
                     silver='#c8d0ff', gem='#b8a8ff', cloth='#1a1e44', eye='#d8e4ff', glow_tips=True)),
]


def gale_variant(cid, spec):
    pal = palette(spec,
                  mane={'base': spec['hair'], 'style': 'hair'},
                  tail={'base': spec['hair'], 'style': 'hair'},
                  feathering={'base': spec['hair'], 'style': 'hair'},
                  flight_a={'base': spec['wing'], 'style': 'feather'},
                  flight_b={'base': spec['wing2'], 'style': 'feather'},
                  covert_a={'base': spec['wing'], 'style': 'feather'},
                  covert_b={'base': spec['wing2'], 'style': 'feather'},
                  plume={'base': spec['wing'], 'style': 'feather'},
                  wing_edge={'base': spec['silver'], 'style': 'metal', 'rivets': False},
                  silver={'base': spec['silver'], 'style': 'metal', 'rivets': False},
                  gem={'base': spec['gem'], 'style': 'gem'},
                  bridle={'base': '#4a3e52', 'style': 'leather'},
                  rein={'base': '#4a3e52', 'style': 'leather'},
                  strap={'base': '#4a3e52', 'style': 'leather'},
                  saddle={'base': '#5a4a3e', 'style': 'leather'},
                  saddle_rim={'base': '#463a30', 'style': 'leather'},
                  skycloth={'base': spec['cloth'], 'style': 'cloth'},
                  hoof='#3a3e48')
    extra = dict(spec, scale=1.0, hitbox=(1.3, 1.75), seat='saddle')
    return Variant(cid, palette=pal, paint=gale_paint, glow=True, extra=extra)


def gale_rig():
    n_mane = count(GALE_POLL) + count(GALE_CREST)
    return Rig('mount_horse_gale', 'HorseGaleMount', gale_geometry(),
               horse.animations(mane_n=n_mane, lock_n=2 * len(GALE_LOCKS), extra={'idle': gale_fold()},
                                more=[gale_glide()]),
               [gale_variant(cid, spec) for cid, spec in GALE_COATS], SLOTS + ['glide'], texel=2)


# ================================================================================================ blaze

BLAZE_POLL = (
    (0.0, -22.4, -15.0, 3, 2, 8, 96, 0, 0),
    (0.0, -22.4, -13.0, 5, 2, 10, 74, 0, 0),
    (1.4, -22.4, -14.0, 4, 2, 8, 82, 20, 30),
    (0.0, -20.0, -12.4, 6, 2, 11, 60, 0, 0),
    (1.9, -20.5, -12.4, 4, 2, 9, 56, 26, 40),
)
BLAZE_CREST = (
    (0.0, -15.5, -6.8, 6, 2, 12, 66, 0, 0),
    (2.0, -14.5, -6.8, 4, 2, 9, 56, 26, 45),
    (0.0, -12.8, -6.8, 6, 2, 11, 62, 0, 0),
    (2.2, -11.5, -6.8, 4, 2, 9, 52, 28, 52),
    (0.0, -10.0, -6.8, 6, 2, 12, 60, 0, 0),
    (2.4, -8.5, -6.8, 4, 2, 8, 48, 28, 58),
    (0.0, -7.0, -6.8, 6, 2, 10, 58, 0, 0),
    (2.4, -5.5, -6.8, 4, 2, 7, 44, 26, 62),
    (0.0, -4.0, -6.8, 5, 2, 9, 56, 0, 0),
    (0.0, -1.0, -6.8, 4, 2, 7, 54, 0, 0),
)
# flames licking up the sides of the neck
BLAZE_LOCKS = (
    (3.8, -13.0, -8.6, 1, 3, 6, 10, 12),
    (3.9, -9.5, -8.0, 1, 3, 7, 14, 14),
    (3.9, -6.0, -7.6, 1, 3, 6, 18, 14),
)
BLAZE_TAIL = (
    ('tail2', 0.0, -1.0, 16.0, 5, 2, 10, 92, 0, 0),
    ('tail2', 2.2, -0.5, 15.0, 4, 2, 9, 84, 20, 24),
    ('tail3', 0.0, 6.0, 15.2, 5, 2, 10, 78, 0, 0),
    ('tail3', 2.4, 6.5, 14.6, 4, 2, 9, 70, 18, 22),
    ('tail4', 0.0, 12.0, 15.6, 4, 2, 9, 64, 0, 0),
    ('tail4', 2.2, 12.5, 15.0, 3, 2, 8, 56, 16, 20),
    ('tail_end', 0.0, 17.5, 15.0, 3, 2, 8, 50, 0, 0),
)


def blaze_flicker(n_mane, n_locks, n_tail):
    """The fire's flicker (idle channels): every flame stretches and shrinks a little out of step."""
    out = {}
    for prefix, n, length in (('mane', n_mane, 3.2), ('lock', n_locks, 3.2), ('tail_blade', n_tail, 3.2)):
        for i in range(1, n + 1):
            a = 1.0 + 0.12 * (1 + i % 3)
            ph = (i * 0.37) % 1.0
            keys = []
            for k in range(9):
                t = length * k / 8.0
                f = 1.0 + (a - 1.0) * math.sin(2 * math.pi * (k / 8.0 * 4 + ph))
                keys.append((t, 1.0, 1.0, round(f, 3)) if prefix != 'lock' else (t, 1.0, round(f, 3), 1.0))
            keys[-1] = (length,) + keys[0][1:]
            out['%s_%d' % (prefix, i)] = dict(scale=keys)
    return out


def blaze_geometry():
    poll, n = shards(BLAZE_POLL, HEAD_TILT, 0, 'flame:mane')
    crest, n = shards(BLAZE_CREST, NECK_TILT, n, 'flame:mane')
    horn = []
    for side, s in (('r', -1), ('l', 1)):
        segs = [(2, 2, 3, None, []), (1, 1, 3, (-34, 10 * s, 0), [])]
        horn.append(chain('horn_%s' % side, (2.0 * s, -21.8, -24.5), segs,
                          horse.aim(56, 34 * s, 0, HEAD_TILT), 'metal:horn', tag='hn_' + side))
    head_cubes = skull(width=9, face_w=7, face_len=9) + [
        # a blackened chanfron with an ember gem, a chain bridle
        C(-3, -21.8, -29.5, 6, 1, 9, 'metal:iron', tag='chanfron'),
        C(-1, -23.0, -24.0, 2, 2, 2, 'gem:ember_gem', tag='brow_gem'),
        C(-4, -21.5, -27.5, 8, 8, 1, 'chain:chain', tag='noseband'),
        C(-5, -22.5, -20.5, 10, 11, 1, 'chain:chain', tag='cheekband'),
        C(-5.3, -17, -21, 1, 2, 2, 'metal:brass', tag='rosette'),
        C(4.3, -17, -21, 1, 2, 2, 'metal:brass', share='rosette', mirror=True),
        C(-2, -23, -15.5, 4, 1, 4, 'flame:mane', tag='poll'),
    ]
    head_parts = ears(w=2, h=5, d=2, tilt=-50, splay=12) + horn + [
        P('reins', (0, -14.5, -27),
          C(-5.1, -14.5, -27.5, 1, 13, 1, 'chain:chain', tag='rein'),
          C(4.1, -14.5, -27.5, 1, 13, 1, 'chain:chain', share='rein', mirror=True),
          C(-5.1, -2.0, -27.5, 10, 1, 1, 'chain:chain', tag='rein_loop'), rot=(38, 0, 0))]
    neck_cubes = [C(-2, -16.5, -8, 4, 16, 3, 'flame:mane', tag='crest'),
                  # the crinet: overlapping iron plates down the neck
                  C(-4.5, -4, -16, 9, 5, 10, 'metal:iron', tag='collar'),
                  *pair(-4.6, -14.0, -14.0, 1, 4, 6, 'metal:iron', 'crinet1'),
                  *pair(-4.6, -9.5, -14.5, 1, 4, 7, 'metal:iron', 'crinet2')]
    saddle = P('saddle', (0, -5, 0),
               C(-4.5, -6, -4, 9, 2, 9, 'leather:saddle'),
               C(-3, -8, -5, 6, 2, 2, 'leather:saddle_rim', tag='pommel'),
               C(-4, -9, 4, 8, 3, 2, 'leather:saddle_rim', tag='cantle'),
               *pair(-6.6, -5, -3.5, 1, 4, 8, 'leather:saddle', 'skirt'))

    def pauldron(side):
        s = -1 if side == 'r' else 1
        x0 = -7.8 if s < 0 else 5.8
        t = (lambda n: n) if s < 0 else (lambda n: None)
        sh = (lambda n: None) if s < 0 else (lambda n: n)
        return P('pauldron_' + side, (6.5 * s, -4, -11),
                 C(x0, -4.5, -14, 2, 6, 6, 'metal:iron', tag=t('pauldron'), share=sh('pauldron'), mirror=s > 0),
                 C(x0 + (-0.5 if s < 0 else 0.5), -6.5, -13.0, 2, 2, 2, 'metal:spike', tag=t('spike1'),
                   share=sh('spike1'), mirror=s > 0),
                 C(x0 + (-0.5 if s < 0 else 0.5), -6.5, -10.0, 2, 2, 2, 'metal:spike', tag=t('spike2'),
                   share=sh('spike2'), mirror=s > 0),
                 rot=(0, 0, 10 * s * -1))
    body_cubes = [
        C(-6.5, -4.5, -12, 13, 13, 24, 'fur:coat', tag='barrel'),
        C(-5.5, -3.5, -15, 11, 10, 3, 'fur:coat', tag='chest'),
        C(-5.5, -4.5, 9, 11, 11, 5, 'fur:coat', tag='rump'),
        # the peytral with the flame emblem, the crupper over the rump, a chain girth
        C(-5.5, -4, -16.5, 11, 9, 1, 'metal:iron', tag='peytral'),
        C(-2.5, -2.5, -17.2, 5, 6, 1, 'gem:ember_gem', tag='emblem'),
        C(-6.0, -5.2, 6.0, 12, 1, 8, 'metal:iron', tag='crupper'),
        *pair(-7.0, -4.8, 6.0, 1, 7, 8, 'metal:iron', 'crupper_side'),
        C(-6.5, -5.0, -5, 13, 1, 11, 'cloth:ashcloth', tag='blanket'),
        *pair(-7.0, -4.5, -4.5, 1, 10, 9, 'cloth:ashcloth', 'drape'),
        *pair(-7.3, -4, -1, 1, 13, 2, 'chain:chain', 'girth'),
        C(-6, 8, -1, 12, 1, 2, 'chain:chain', tag='girth_under'),
    ]
    leg_spec = dict(front=(5, 6), hind=(6, 7), shin=4, hoof=(5, 3, 6), fetlock=(5, 2, 5), fet_mat='flame:fetflame',
                    hoof_mat='magma:hoof',
                    upper_extra=[(-2.5, 10.5, -3.6, 5, 4, 1, 'metal:iron', 'kp', 'front'),
                                 (-2.5, 10.0, 3.2, 5, 3, 1, 'metal:iron', 'kp', 'hind')],
                    shin_extra=[(-2.5, 15.0, -2.7, 5, 4, 1, 'metal:iron', 'greave')])
    n_tail = tail_count(BLAZE_TAIL)
    return assemble(head_cubes, head_parts, neck_cubes, [], body_cubes, [pauldron('r'), pauldron('l')],
                    tail(BLAZE_TAIL, 'flame:tail', root=(5, 4, 4)), leg_spec, saddle, poll, crest,
                    locks(BLAZE_LOCKS, 'flame:mane', upward=True))


def blaze_paint(p):
    e = p.variant.extra
    ramp = [bp.col(c) for c in e['fire']]
    n_mane = count(BLAZE_POLL) + count(BLAZE_CREST)
    for part in blade_parts(p, 'mane', n_mane):
        blade_ramp(p, part, 'z', ramp, glow=True, glow_dim=0.95, flicker=1.2, keys=(p.variant.id,))
    for part in blade_parts(p, 'lock', 2 * len(BLAZE_LOCKS)):
        # the side flames rise: their tip is at the top (fine y 0)
        for fc, gc, u_of, across, width in blade_faces(p, part, 'x'):
            bp.flame(fc, (p.variant.id, part, fc.face), ramp=list(reversed(ramp)), glow=gc, tip_up=True)
        for fc, gc in side_faces(p, part, 'x'):
            bp.flame(fc, (p.variant.id, part, fc.face, 's'), ramp=list(reversed(ramp)), glow=gc, tongues=False)
    for part in blade_parts(p, 'tail_blade', tail_count(BLAZE_TAIL)):
        blade_ramp(p, part, 'zh', ramp, glow=True, glow_dim=0.95, flicker=1.2, keys=(p.variant.id,))
    for part, tag in (('tail', 'root'), ('tail2', None), ('tail3', None), ('tail4', None), ('neck', 'crest'),
                      ('head', 'poll'), ('front_right_shin', 'ffet'), ('hind_right_shin', 'hfet')):
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            fc = p.face(part, name, tag=tag)
            bp.flame(fc, (p.variant.id, part, name), ramp=list(reversed(ramp)), glow=p.face(part, name, tag=tag, glow=True),
                     tip_up=True, tongues=name not in ('top', 'bottom'))
    # the coat: charcoal veined with lava (the veins glow)
    hot, core = rgb(e['vein']), rgb(e['vein_core'])
    for part, tag, names in (('body', 'barrel', ('right', 'left', 'bottom', 'front', 'back')),
                             ('body', 'chest', ('front', 'right', 'left')), ('body', 'rump', ('right', 'left', 'back')),
                             ('neck', 'neck', ('right', 'left', 'front')), ('head', 'skull', ('right', 'left', 'top')),
                             ('head', 'face', ('right', 'left', 'top', 'front')),
                             ('front_right_leg', 'fu', ('front', 'right', 'left', 'back')),
                             ('hind_right_leg', 'hu', ('front', 'right', 'left', 'back')),
                             ('front_right_shin', 'fs', ('front', 'right', 'left', 'back')),
                             ('hind_right_shin', 'hs', ('front', 'right', 'left', 'back'))):
        for name in names:
            fc = p.face(part, name, tag=tag)
            bp.lava_veins(fc, (p.variant.id, part, name), glow=p.face(part, name, tag=tag, glow=True), hot=hot,
                          core=core, density=1.3)
    horse_eye(p, e['eye'], glow=True)
    muzzle_marks(p, nostril=hot)
    # burning hooves
    for shin in ('front_right_shin', 'hind_right_shin'):
        pre = 'f' if shin.startswith('front') else 'h'
        for name in ('front', 'back', 'right', 'left', 'bottom'):
            fc = p.face(shin, name, tag=pre + 'hoof')
            gc = p.face(shin, name, tag=pre + 'hoof', glow=True)
            for x, y in bp.each(fc):
                if y >= fc.fh - 2 or (x + y) % 5 == 0:
                    fc.fpx(x, y, core if y >= fc.fh - 1 else hot)
                    gc.fpx(x, y, core if y >= fc.fh - 1 else hot)
    # iron: brass rims, rivets, a flame engraving; the ember gems
    brass = rgb(e['brass'])
    for part, tag in (('head', 'chanfron'), ('neck', 'collar'), ('neck', 'crinet1'), ('neck', 'crinet2'),
                      ('body', 'peytral'), ('body', 'crupper'), ('body', 'crupper_side'), ('pauldron_r', 'pauldron'),
                      ('front_right_leg', 'fkp'), ('hind_right_leg', 'hkp'), ('front_right_shin', 'fgreave'),
                      ('hind_right_shin', 'hgreave')):
        for name in ('front', 'top', 'right', 'left', 'back'):
            fc = p.face(part, name, tag=tag)
            bp.trim(fc, brass, 1, 'tblr')
            if fc.fw >= 6 and fc.fh >= 6:
                for (x, y) in ((2, 2), (fc.fw - 3, 2), (2, fc.fh - 3), (fc.fw - 3, fc.fh - 3)):
                    fc.fpx(x, y, mix(brass, (255, 255, 255), 0.4))
    for name in ('front', 'right', 'left'):
        fc = p.face('body', name, tag='crupper_side')
        gc = p.face('body', name, tag='crupper_side', glow=True)
        bp.flame(fc, (p.variant.id, 'engrave', name), ramp=list(reversed(ramp)), glow=gc, tip_up=True)
        for x, y in bp.each(fc):
            if y < fc.fh // 2:
                fc.fpx(x, y, shade(rgb(e['iron']), 0.9))
                gc.fclear(x, y)
        bp.trim(fc, brass, 1, 'tblr')
    for part, tag in (('pauldron_r', 'spike1'), ('pauldron_r', 'spike2')):
        for name in ('front', 'back', 'right', 'left', 'top'):
            fc = p.face(part, name, tag=tag)
            for x, y in bp.each(fc):
                fc.fpx(x, y, mix(brass, rgb(e['iron']), y / max(1, fc.fh - 1)))
    for part, tag in (('body', 'emblem'), ('head', 'brow_gem')):
        for name in ('front', 'top', 'right', 'left'):
            bp.gem(p.face(part, name, tag=tag), p.face(part, name, tag=tag, glow=True), rgb(e['gem']),
                   mix(rgb(e['gem']), (255, 255, 255), 0.25))
    for side in ('r', 'l'):
        for i in range(2):
            for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
                fc = p.face('horn_%s%d' % (side, i + 1), name, index=0)
                for x, y in bp.each(fc):
                    fc.fpx(x, y, mix(rgb(e['iron']), brass, 0.25 + 0.5 * (i + (fc.fh - y) / max(1, fc.fh)) / 2))
    # the ash-cloth: a scorched hem with embers
    for name in ('right',):
        fc = p.face('body', name, tag='drape')
        gc = p.face('body', name, tag='drape', glow=True)
        bp.trim(fc, brass, 1, 'tlr')
        for x, y in bp.each(fc):
            if y > fc.fh - 5:
                fc.fpx(x, y, mix(fc.fget(x, y), (30, 20, 18), 0.5))
                if (x * 7 + y) % 9 == 0:
                    fc.fpx(x, y, hot)
                    gc.fpx(x, y, hot)
        base.pointed_bottom(fc, 4, step=3)
    for name in ('front', 'back'):
        base.pointed_bottom(p.face('body', name, tag='drape'), 4, step=2)
    bp.trim(p.face('body', 'top', tag='blanket'), brass, 1)
    for tag in ('pommel', 'cantle', 'skirt'):
        for name in ('right', 'left', 'front', 'back', 'top'):
            bp.trim(p.face('saddle', name, tag=tag), shade(hot, 0.8), 1, 'tblr')


BLAZE_COATS = [
    ('ember', dict(coat=('#2a2624', '#181514', '#3c3634'), muzzle=('#221e1c', '#141110', '#302a28'),
                   fire=['#ffe6a0', '#ffb43c', '#ff6a1e', '#d8301a', '#7a1a14'], vein='#ff7a1e', vein_core='#ffd27a',
                   iron='#3a3638', brass='#c08a3a', gem='#ff6a2a', eye='#ffb43c', ash='#3a2a26')),
    ('ashen', dict(coat=('#77706c', '#55504c', '#938c86'), muzzle=('#5e5854', '#44403c', '#746e6a'),
                   fire=['#fff0c8', '#ffc85a', '#ff8a2a', '#e04a1e', '#8a2a1a'], vein='#ff5a2a', vein_core='#ffc080',
                   iron='#4a4648', brass='#d0a050', gem='#ff4a2a', eye='#ff8a3a', ash='#5a5250')),
    ('brimstone', dict(coat=('#5a1a14', '#3a0e0a', '#7a2a1e'), muzzle=('#3e120e', '#280a08', '#561a14'),
                       fire=['#ffffd0', '#fff07a', '#ffc83a', '#ff8a1e', '#b04a14'], vein='#ffd040', vein_core='#ffffb0',
                       iron='#2a2222', brass='#e8b84a', gem='#ffd040', eye='#fff07a', ash='#2a1414')),
    ('soulfire', dict(coat=('#1c2230', '#10141e', '#2a3244'), muzzle=('#161b26', '#0c0f16', '#20283a'),
                      fire=['#e8ffff', '#9ff0ff', '#4ac8f0', '#2a7ad8', '#1a3a8a'], vein='#4ad8ff', vein_core='#d8ffff',
                      iron='#2a3040', brass='#8ab0d8', gem='#4ad8ff', eye='#9ff0ff', ash='#1a2232')),
    ('solar', dict(coat=('#e8d8b0', '#c8b484', '#f8ecd0'), muzzle=('#c8b48c', '#a8946c', '#dcc8a0'),
                   fire=['#ffffff', '#fffad0', '#ffe68a', '#ffc040', '#e88a20'], vein='#ffc040', vein_core='#ffffff',
                   iron='#f0e6d0', brass='#ffcc4a', gem='#ffe68a', eye='#ffffff', ash='#c8a86a')),
]


def blaze_variant(cid, spec):
    pal = palette(spec,
                  mane={'base': spec['fire'][2], 'style': 'plain'},
                  tail={'base': spec['fire'][2], 'style': 'plain'},
                  fetflame={'base': spec['fire'][2], 'style': 'plain'},
                  hoof={'base': '#2a1a14', 'style': 'magma', 'hot': rgb(spec['vein'])},
                  iron={'base': spec['iron'], 'style': 'metal', 'rivets': False},
                  spike={'base': spec['brass'], 'style': 'gold'},
                  horn={'base': spec['iron'], 'style': 'metal', 'rivets': False},
                  brass={'base': spec['brass'], 'style': 'gold'},
                  ember_gem={'base': spec['gem'], 'style': 'gem'},
                  chain={'base': '#5a524e', 'style': 'chain'},
                  saddle={'base': '#2a1e1a', 'style': 'leather'},
                  saddle_rim={'base': '#1e1412', 'style': 'leather'},
                  ashcloth={'base': spec['ash'], 'style': 'cloth'})
    extra = dict(spec, scale=1.0, hitbox=(1.3, 1.75), seat='saddle')
    return Variant(cid, palette=pal, paint=blaze_paint, glow=True, extra=extra)


def blaze_rig():
    n_mane = count(BLAZE_POLL) + count(BLAZE_CREST)
    n_tail = tail_count(BLAZE_TAIL)
    return Rig('mount_horse_blaze', 'HorseBlazeMount', blaze_geometry(),
               horse.animations(mane_n=n_mane, lock_n=2 * len(BLAZE_LOCKS),
                                extra={'idle': blaze_flicker(n_mane, 2 * len(BLAZE_LOCKS), n_tail)}),
               [blaze_variant(cid, spec) for cid, spec in BLAZE_COATS], SLOTS, texel=2)


# ================================================================================================ halo

HALO_POLL = (
    (0.0, -22.4, -13.2, 5, 2, 11, 40, 0, 0),
    (1.3, -22.4, -14.2, 3, 2, 9, 46, 22, 30),
    (0.0, -20.0, -12.4, 6, 2, 12, 22, 0, 0),
    (1.9, -20.5, -12.4, 4, 2, 11, 16, 28, 40),
    (2.2, -17.5, -12.4, 4, 2, 10, -6, 26, 56),
)
HALO_CREST = (
    (0.0, -15.5, -6.8, 6, 2, 13, 18, 0, 0),
    (2.0, -14.5, -6.8, 5, 2, 12, 6, 30, 45),
    (0.0, -12.8, -6.8, 6, 2, 13, 12, 0, 0),
    (2.2, -11.5, -6.8, 5, 2, 12, -2, 32, 52),
    (0.0, -10.0, -6.8, 6, 2, 13, 8, 0, 0),
    (2.4, -8.5, -6.8, 5, 2, 11, -10, 32, 58),
    (0.0, -7.0, -6.8, 6, 2, 12, 6, 0, 0),
    (2.4, -5.5, -6.8, 5, 2, 10, -16, 30, 62),
    (0.0, -4.0, -6.8, 5, 2, 11, 2, 0, 0),
    (2.2, -2.5, -6.8, 4, 2, 9, -20, 26, 64),
    (0.0, -1.0, -6.8, 4, 2, 9, -2, 0, 0),
)
HALO_LOCKS = (
    (3.8, -15.0, -9.0, 1, 4, 15, 12, 6),
    (3.9, -13.0, -8.2, 1, 4, 17, 16, 8),
    (3.9, -10.5, -7.8, 1, 4, 16, 20, 9),
    (3.9, -8.0, -7.4, 1, 3, 14, 24, 10),
    (3.9, -5.5, -7.2, 1, 3, 12, 28, 10),
    (3.8, -3.0, -7.0, 1, 3, 10, 32, 10),
)
HALO_TAIL = (
    ('tail2', 0.0, -1.0, 16.0, 5, 2, 12, 62, 0, 0),
    ('tail2', 2.2, -0.5, 15.0, 4, 2, 12, 54, 18, 24),
    ('tail2', 3.2, 2.0, 13.8, 4, 2, 11, 44, 32, 40),
    ('tail3', 0.0, 6.0, 15.2, 5, 2, 12, 42, 0, 0),
    ('tail3', 2.4, 6.5, 14.6, 4, 2, 12, 36, 16, 22),
    ('tail3', 3.6, 7.5, 13.6, 4, 2, 11, 28, 28, 38),
    ('tail4', 0.0, 12.0, 15.6, 4, 2, 11, 30, 0, 0),
    ('tail4', 2.2, 12.5, 15.0, 4, 2, 11, 24, 14, 20),
    ('tail4', 3.4, 13.0, 14.0, 3, 2, 10, 18, 24, 34),
    ('tail_end', 0.0, 17.5, 15.0, 3, 2, 9, 18, 0, 0),
    ('tail_end', 1.8, 17.5, 14.6, 3, 1, 8, 14, 12, 16),
)
HALO_RING = 12      # segments of the halo


def halo_horn():
    """The spiral horn: five segments narrowing to the tip, each turned a little further about its own length (the
    twist), rising forward from the brow."""
    segs = [(3, 3, 4, None, []), (3, 3, 3, (3, 0, 30), []), (2, 2, 4, (3, 0, 30), []), (2, 2, 3, (3, 0, 30), []),
            (1, 1, 4, (3, 0, 30), [])]
    return chain('horn', (0.0, -22.6, -20.5), segs, horse.aim(112, 0, 0, HEAD_TILT), 'pearl:horn', tag='hseg')


def halo_ring():
    """The halo floating over the poll: a ring of gold segments with a jewel every third, on a part that keeps it
    level ('halo') and a child that turns it ('halo_spin')."""
    r = 4.6
    cy, cz = -27.0, -8.5          # up and back from the poll in the head's frame: right above it in the world
    cubes = []
    for i in range(HALO_RING):
        a = 2 * math.pi * i / HALO_RING
        x, z = r * math.cos(a), r * math.sin(a) + cz
        mat = 'gem:halo_gem' if i % 3 == 0 else 'gold:halo'
        cubes.append(C(x - 1.0, cy, z - 1.0, 2, 1, 2, mat, tag='ring%d' % i))
    spin = P('halo_spin', (0.0, cy, cz), *cubes)
    return P('halo', (0.0, cy, cz), spin, rot=(-HEAD_TILT, 0, 0))


def halo_geometry():
    poll, n = shards(HALO_POLL, HEAD_TILT, 0, 'light:mane')
    crest, n = shards(HALO_CREST, NECK_TILT, n, 'light:mane')
    plumes = [P('plume_r', (-5.2, -21.0, -20.0), *horse.blade(-5.2, -21.0, -20.0, 3, 1, 8, mat='feather:plume'),
                rot=horse.aim(64, -30, -70, HEAD_TILT)),
              P('plume_l', (5.2, -21.0, -20.0), *horse.blade(5.2, -21.0, -20.0, 3, 1, 8, mat='feather:plume'),
                rot=horse.aim(64, 30, 70, HEAD_TILT))]
    head_cubes = skull(width=8, face_w=6, face_len=10) + [
        # the filigree chanfron, a white-gold bridle with pearls
        C(-2.5, -21.6, -30, 5, 1, 8, 'metal:filigree', tag='chanfron'),
        C(-4, -21.5, -28.5, 8, 8, 1, 'leather:white', tag='noseband'),
        C(-4.5, -22.5, -20.5, 9, 10, 1, 'leather:white', tag='cheekband'),
        C(-5.0, -18.5, -21, 1, 3, 3, 'gold:gold', tag='rosette'),
        C(4.0, -18.5, -21, 1, 3, 3, 'gold:gold', share='rosette', mirror=True),
        C(-2, -23, -15.5, 4, 1, 4, 'light:mane', tag='poll'),
    ]
    head_parts = ears(w=2, h=5, d=2, tilt=-46, splay=8) + plumes + [halo_horn(), halo_ring(),
        P('reins', (0, -14.5, -28),
          C(-4.6, -14.5, -28.5, 1, 13, 1, 'leather:white', tag='rein'),
          C(3.6, -14.5, -28.5, 1, 13, 1, 'leather:white', share='rein', mirror=True),
          C(-4.6, -2.0, -28.5, 9, 1, 1, 'leather:white', tag='rein_loop'), rot=(38, 0, 0))]
    neck_cubes = [C(-2, -16.5, -8, 4, 16, 3, 'light:mane', tag='crest'),
                  C(-4.5, -4, -16, 9, 4, 10, 'metal:filigree', tag='collar')]
    saddle = P('saddle', (0, -5, 0),
               C(-4.5, -6, -4, 9, 2, 9, 'leather:white'),
               C(-3, -8, -5, 6, 2, 2, 'gold:gold', tag='pommel'),
               C(-4, -9.5, 4, 8, 4, 2, 'gold:gold', tag='cantle'),
               *pair(-6.6, -5, -3.5, 1, 4, 8, 'leather:white', 'skirt'))
    body_cubes = [
        C(-6, -4.5, -12, 12, 13, 24, 'fur:coat', tag='barrel'),
        C(-5, -3.5, -15, 10, 10, 3, 'fur:coat', tag='chest'),
        C(-5, -4.5, 9, 10, 11, 5, 'fur:coat', tag='rump'),
        # the filigree peytral with the haloed sun, the white caparison with gold fringe
        C(-5.5, -4, -16.4, 11, 9, 1, 'metal:filigree', tag='peytral'),
        C(-2.5, -2.5, -17.1, 5, 5, 1, 'gem:halo_gem', tag='sun'),
        C(-6.5, -5.0, -6, 13, 1, 13, 'cloth:caparison', tag='blanket'),
        *pair(-7, -4.5, -7.0, 1, 12, 7, 'cloth:caparison', 'cap_front'),
        *pair(-7, -4.0, 2.0, 1, 11, 9, 'cloth:caparison', 'cap_rear'),
        *pair(-7.3, -4, -1, 1, 13, 2, 'leather:white', 'girth'),
        C(-6, 8.5, -1, 12, 1, 2, 'leather:white', tag='girth_under'),
        C(-6.0, -5.5, 11.0, 12, 1, 2, 'gold:gold', tag='band'),
    ]
    leg_spec = dict(front=(5, 6), hind=(6, 7), shin=4, hoof=(5, 3, 6), fetlock=(7, 4, 7), fet_mat='hair:feathering',
                    hoof_mat='gold:shoe')
    return assemble(head_cubes, head_parts, neck_cubes, [], body_cubes, [],
                    tail(HALO_TAIL, 'light:tail'), leg_spec, saddle, poll, crest, locks(HALO_LOCKS, 'light:mane'))


def halo_spin():
    """The halo turns slowly (a quarter turn per idle: the ring repeats every quarter)."""
    return {'halo_spin': dict(rot=[(0, 0, 0, 0), (3.2, 0, 90, 0)], lin=True),
            'horn1': dict(rot=[(0, 0, 0, 0), (1.6, -2, 0, 0), (3.2, 0, 0, 0)])}


def halo_paint(p):
    e = p.variant.extra
    ramp = [bp.col(c) for c in e['light']]
    dim = e.get('light_glow', 0.55)
    n_mane = count(HALO_POLL) + count(HALO_CREST)
    for part in blade_parts(p, 'mane', n_mane):
        blade_ramp(p, part, 'z', ramp, glow=True, glow_dim=dim, keys=(p.variant.id,))
    for part in blade_parts(p, 'lock', 2 * len(HALO_LOCKS)):
        blade_ramp(p, part, 'x', ramp, glow=True, glow_dim=dim, keys=(p.variant.id,))
    for part in blade_parts(p, 'tail_blade', tail_count(HALO_TAIL)):
        blade_ramp(p, part, 'zh', ramp, glow=True, glow_dim=dim, keys=(p.variant.id,))
    for part, tag in (('tail', 'root'), ('tail2', None), ('tail3', None), ('tail4', None), ('neck', 'crest'),
                      ('head', 'poll')):
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            bp.light_strands(p.face(part, name, tag=tag), ramp[:2], (p.variant.id, part, name),
                             glow=p.face(part, name, tag=tag, glow=True), dim=dim * 0.8)
    # the coat: a pearly sheen (a soft pastel gradient), long feathering at the fetlocks
    sheen = rgb(e['sheen'])
    for part, tag, names in (('body', 'barrel', ('right', 'left')), ('body', 'rump', ('right', 'left')),
                             ('neck', 'neck', ('right', 'left'))):
        for name in names:
            fc = p.face(part, name, tag=tag)
            for x, y in bp.each(fc):
                t = 0.5 + 0.5 * math.sin((x * 0.18 + y * 0.11))
                fc.fpx(x, y, mix(fc.fget(x, y), sheen, 0.22 * t))
    for shin in ('front_right_shin', 'hind_right_shin'):
        pre = 'f' if shin.startswith('front') else 'h'
        for name in ('front', 'back', 'right', 'left', 'top'):
            fc = p.face(shin, name, tag=pre + 'fet')
            bp.fur_tufts(fc, rgb(e['feather']), (255, 255, 255), shade(rgb(e['feather']), 0.8),
                         (p.variant.id, shin, name))
    horse_eye(p, e['eye'], glow=True)
    muzzle_marks(p, nostril=shade(rgb(e['muzzle'][0]), 0.6))
    # the horn: pearl with gold spiral bands, the tip shining
    gold = rgb(e['gold'])
    glow_c = rgb(e['glow'])
    for i in range(5):
        part = 'horn%d' % (i + 1)
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            fc = p.face(part, name, index=0)
            gc = p.face(part, name, index=0, glow=True)
            for x, y in bp.each(fc):
                band = (x + y) % 4 < 2
                c = mix(gold, (255, 250, 230), 0.25 * (y % 2)) if band else mix(rgb(e['horn']), (255, 255, 255),
                                                                                  0.3 * (x % 2))
                fc.fpx(x, y, c)
                if i >= 2:
                    gc.fpx(x, y, shade(glow_c, 0.9 if i == 4 else 0.6 if i == 3 else 0.35))
    # the halo: gold with jewels, shining
    for i in range(HALO_RING):
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            fc = p.face('halo_spin', name, tag='ring%d' % i)
            gc = p.face('halo_spin', name, tag='ring%d' % i, glow=True)
            if i % 3 == 0:
                bp.gem(fc, gc, rgb(e['gem']), mix(rgb(e['gem']), (255, 255, 255), 0.4))
            else:
                for x, y in bp.each(fc):
                    c = mix(gold, (255, 250, 220), 0.4 if y == 0 else 0.0)
                    fc.fpx(x, y, c)
                    gc.fpx(x, y, shade(glow_c, 0.85))
    # filigree on the white plates; the sun on the breast; feather plumes
    for part, tag in (('head', 'chanfron'), ('neck', 'collar'), ('body', 'peytral')):
        for name in ('front', 'top', 'right', 'left'):
            fc = p.face(part, name, tag=tag)
            bp.filigree(fc, gold, shade(gold, 0.6), (p.variant.id, part, name))
            bp.trim(fc, gold, 1, 'tblr')
    for name in ('front',):
        fc = p.face('body', name, tag='sun')
        gc = p.face('body', name, tag='sun', glow=True)
        cx, cy = (fc.fw - 1) / 2.0, (fc.fh - 1) / 2.0
        for x, y in bp.each(fc):
            d = math.hypot(x - cx, y - cy)
            ray = int((math.atan2(y - cy, x - cx) + math.pi) / (2 * math.pi) * 12) % 2 == 0
            c = (255, 255, 240) if d < 1.6 else rgb(e['gem']) if d < 3.0 else gold if ray else shade(gold, 0.6)
            fc.fpx(x, y, c)
            gc.fpx(x, y, shade(glow_c, 1.0 if d < 3.0 else 0.5))
    for side in ('r', 'l'):
        for name in ('top', 'bottom'):
            import rig_griffin
            rig_griffin.long_feather(p.face('plume_' + side, name, index=0), rgb(e['plume']),
                                     shade(rgb(e['plume']), 0.8), tip=gold)
    for tag in ('cap_front', 'cap_rear'):
        fc = p.face('body', 'right', tag=tag)
        bp.filigree(fc, gold, shade(gold, 0.6), (p.variant.id, tag), step=7)
        bp.trim(fc, gold, 2, 'lr')
        bp.trim(fc, gold, 1, 't')
        for x in range(fc.fw):
            for d in range(3):
                fc.fpx(x, fc.fh - 1 - d, gold if (x % 2 == 0 or d == 2) else shade(gold, 0.7))
            if x % 2:
                fc.fclear(x, fc.fh - 1)
        for name in ('front', 'back'):
            base.pointed_bottom(p.face('body', name, tag=tag), 2, step=2)
    bp.trim(p.face('body', 'top', tag='blanket'), gold, 1)
    for shin in ('front_right_shin', 'hind_right_shin'):
        pre = 'f' if shin.startswith('front') else 'h'
        for name in ('front', 'back', 'right', 'left'):
            fc = p.face(shin, name, tag=pre + 'hoof')
            for x in range(fc.fw):
                fc.fpx(x, 0, mix(gold, (255, 255, 255), 0.4))


HALO_COATS = [
    ('pearl', dict(coat=('#eceaf2', '#c4c0d0', '#ffffff'), muzzle=('#d4cfdc', '#b0aabc', '#e8e4ee'), sheen='#cfe8ff',
                   light=['#fff8e6', '#fff0c8', '#ffe8f4', '#e8f0ff'], feather='#f6f4fa', horn='#f4f0e8',
                   gold='#e8c050', gem='#7ab8ff', glow='#fff4d0', eye='#8fd8ff', plume='#ffffff',
                   caparison='#f4f2f8')),
    ('rose', dict(coat=('#f2d6dc', '#cfaab4', '#fff0f2'), muzzle=('#dcbcc4', '#b898a2', '#ecd2d8'), sheen='#ffe0f0',
                  light=['#fff0f4', '#ffd0e0', '#ffc0d8', '#ffe8c8'], feather='#fff2f4', horn='#fff0ee',
                  gold='#f0b880', gem='#ff7ab0', glow='#ffd8e8', eye='#ffb0d0', plume='#fff0f4',
                  caparison='#fbe8ee')),
    ('azure', dict(coat=('#dbe6f4', '#aebfd8', '#f4f8ff'), muzzle=('#c2d0e4', '#9cb0cc', '#d8e2f0'), sheen='#d0f0ff',
                   light=['#f0faff', '#c8eaff', '#a8d8ff', '#d8e0ff'], feather='#eef6ff', horn='#eef4ff',
                   gold='#c8d8f0', gem='#4aa8ff', glow='#d0ecff', eye='#7ad0ff', plume='#f0f8ff',
                   caparison='#e8f0fa')),
    ('twilight', dict(coat=('#bcaed6', '#8e80ac', '#d8ccee'), muzzle=('#a496c0', '#7c6e9a', '#bcb0d6'), sheen='#e0d0ff',
                      light=['#f4ecff', '#dcc8ff', '#c0a8ff', '#ffd8f0'], feather='#e8e0f8', horn='#f0ecff',
                      gold='#d8c0f0', gem='#b080ff', glow='#e8d8ff', eye='#d0b0ff', plume='#f4f0ff',
                      caparison='#e4dcf4')),
    ('prism', dict(coat=('#f4f4f8', '#cacad6', '#ffffff'), muzzle=('#dedee6', '#bcbcc8', '#eeeef2'), sheen='#ffe8ff',
                   light=['#ffb0c8', '#ffe08a', '#a8ffb8', '#9fd8ff', '#d0a8ff'], feather='#fafaff', horn='#ffffff',
                   gold='#ffd870', gem='#ff8af0', glow='#ffffff', eye='#ffffff', plume='#ffffff',
                   caparison='#f8f8fc', light_glow=0.9)),
]


def halo_variant(cid, spec):
    pal = palette(spec,
                  mane={'base': spec['light'][0], 'style': 'plain'},
                  tail={'base': spec['light'][0], 'style': 'plain'},
                  feathering={'base': spec['feather'], 'style': 'hair'},
                  horn={'base': spec['horn'], 'style': 'plain'},
                  filigree={'base': '#f6f4fa', 'style': 'metal', 'rivets': False},
                  gold={'base': spec['gold'], 'style': 'gold'},
                  halo={'base': spec['gold'], 'style': 'gold'},
                  shoe={'base': spec['gold'], 'style': 'gold'},
                  halo_gem={'base': spec['gem'], 'style': 'gem'},
                  white={'base': '#e8e2d8', 'style': 'leather'},
                  plume={'base': spec['plume'], 'style': 'feather'},
                  caparison={'base': spec['caparison'], 'style': 'cloth'})
    extra = dict(spec, scale=1.0, hitbox=(1.3, 1.85), seat='saddle')
    return Variant(cid, palette=pal, paint=halo_paint, glow=True, extra=extra)


def halo_rig():
    n_mane = count(HALO_POLL) + count(HALO_CREST)
    return Rig('mount_horse_halo', 'HorseHaloMount', halo_geometry(),
               horse.animations(mane_n=n_mane, lock_n=2 * len(HALO_LOCKS), extra={'idle': halo_spin()}),
               [halo_variant(cid, spec) for cid, spec in HALO_COATS], SLOTS, texel=2)


def rigs():
    return [bloom_rig(), tide_rig(), gale_rig(), blaze_rig(), halo_rig()]
