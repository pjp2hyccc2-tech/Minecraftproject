"""The Moonmane Charger, rebuilt after the owner's picture (2026-11-11): a black armoured warhorse with a wild white
mane and tail.

What the picture shows and where it is here:
  * a stocky black horse with a long head, tall ears and glowing blue eyes;
  * a big white mane of shards growing from the top of the head between the ears and flowing back down the whole
    crest, long locks hanging over the sides of the neck, a long white tail of shards falling in waves;
  * a brown bridle with gold rings and a hanging rein, a white-and-gold plate down the face with a gold diamond and
    blue gem on the brow;
  * a white plate collar, a pointed white chest plate with gold rims and a big blue diamond gem in a gold frame,
    spiked white shoulder plates;
  * navy caparison panels with gold edges, a white fleur cross and a jagged hem; a brown saddle with a high cantle, a
    girth with a big gold buckle, saddlebags with gold-riveted straps, a white band over the rump;
  * thick legs with white-and-gold knee plates, white cannon guards in two tiers, gold fetlock rings, big black hooves.

Conventions as in ermodel: absolute rest-pose coordinates, +Y down, ground at y=24, front -Z, right side -X. The bones
the Java model drives keep their names (base, body, neck, head, ear_r/l, mane_*, lock_*, tail, tail2-4, legs and
shins, saddle); MountModel's mane shapes use mane_*, lock_*, tail3 and tail4. Every shard of hair is a stepped blade
(three blocks narrowing to the tip).
"""
import math

from ermodel import C, P, Rig, Variant, anim
from erpaint import mix, rgb, shade
import rig_mounts as base

HORSE_SLOTS = ['idle', 'walk', 'gallop', 'jump']
GOLD, GOLD_DARK, GOLD_LIGHT = base.GOLD, base.GOLD_DARK, base.GOLD_LIGHT
GEM, GEM_GLOW, EYE_GLOW = base.GEM, base.GEM_GLOW, base.EYE_GLOW
WHITE = rgb('#f2f0f4')

# The head sits on top of the neck, so the poll (between the ears) is the top of the crest: the head is built in its
# own coordinates and then moved up and back by HEAD_SHIFT (x, y, z).
HEAD_SHIFT = (0.0, -3.0, 6.0)
# how far the rest frames look down (degrees about x): the neck 26, the head 30 more
NECK_TILT, HEAD_TILT = 26.0, 56.0

# The white mane grows from the top of the head between the ears (the owner, 2026-11-11) and runs down the crest to
# the withers. Each shard is a flat block pointing away from its root:
#   (x, y, z, width, thickness, length, elevation, swing, face)
# x, y, z: the root in the rest frame of the part it grows on (POLL: the head, before HEAD_SHIFT; CREST: the neck);
# elevation: degrees above the backward horizontal in the world (90 straight up, more leans forward, below 0 hangs);
# swing: degrees out to the shard's side; face: how far its flat face turns from facing up to facing out.
# x < 0 entries are mirrored into x > 0 ones unless x is 0.
POLL = (
    (0.0, -22.4, -15.2, 2, 2, 6, 76, 0, 0),         # a short spike between the ears
    (0.0, -22.4, -13.2, 5, 2, 10, 40, 0, 0),        # the tuft, up and back
    (1.3, -22.4, -14.2, 3, 2, 8, 46, 20, 30),       # flaring out over the ears
    (0.8, -22.4, -16.4, 2, 2, 7, 112, 14, 180),     # flicked up and forward between the ears
    (0.0, -20.0, -12.4, 6, 2, 11, 24, 0, 0),        # down the back of the poll
    (1.9, -20.5, -12.4, 4, 2, 10, 18, 26, 40),
    (2.2, -17.5, -12.4, 4, 2, 9, -8, 24, 56),
)
CREST = (
    (0.0, -15.5, -6.8, 6, 2, 12, 20, 0, 0),
    (2.0, -14.5, -6.8, 5, 2, 11, 8, 28, 45),
    (0.0, -12.8, -6.8, 6, 2, 12, 14, 0, 0),
    (2.2, -11.5, -6.8, 5, 2, 11, 0, 30, 52),
    (0.0, -10.0, -6.8, 6, 2, 12, 10, 0, 0),
    (2.4, -8.5, -6.8, 5, 2, 10, -8, 30, 58),
    (0.0, -7.0, -6.8, 6, 2, 11, 8, 0, 0),
    (2.4, -5.5, -6.8, 5, 2, 9, -14, 28, 62),
    (0.0, -4.0, -6.8, 5, 2, 10, 4, 0, 0),
    (2.2, -2.5, -6.8, 4, 2, 8, -18, 26, 64),
    (0.0, -1.0, -6.8, 4, 2, 8, 0, 0, 0),
)
# long locks hanging from the crest down the sides of the neck (blocks hanging down from their root):
#   (x, y, z, thickness, width, length, lean back from straight down, out from the neck), in the neck's rest frame
LOCKS = (
    (3.8, -15.0, -9.0, 1, 4, 13, 12, 6),
    (3.9, -13.0, -8.2, 1, 4, 15, 16, 8),
    (3.9, -10.5, -7.8, 1, 4, 14, 20, 9),
    (3.9, -8.0, -7.4, 1, 3, 12, 24, 10),
    (3.9, -5.5, -7.2, 1, 3, 10, 28, 10),
)
# the tail: blades hanging from the tail's segments, fanning out to the sides:
#   (segment, x, y, z, width, thickness, length, lean back from straight down, out to the side, turned to face out)
# in the segment's rest frame; x > 0 entries are mirrored. TAIL_TILT: how far each segment's frame is turned about x.
TAIL_TILT = {'tail': 26.0, 'tail2': 38.0, 'tail3': 32.0, 'tail4': 28.0, 'tail_end': 22.0}
TAIL = (
    ('tail2', 0.0, -1.0, 16.0, 5, 2, 10, 62, 0, 0),
    ('tail2', 2.2, -0.5, 15.0, 4, 2, 10, 54, 18, 24),
    ('tail2', 3.2, 2.0, 13.8, 4, 2, 9, 44, 32, 40),
    ('tail3', 0.0, 6.0, 15.2, 5, 2, 10, 42, 0, 0),
    ('tail3', 2.4, 6.5, 14.6, 4, 2, 10, 36, 16, 22),
    ('tail3', 3.6, 7.5, 13.6, 4, 2, 9, 28, 28, 38),
    ('tail4', 0.0, 12.0, 15.6, 4, 2, 9, 30, 0, 0),
    ('tail4', 2.2, 12.5, 15.0, 4, 2, 9, 24, 14, 20),
    ('tail4', 3.4, 13.0, 14.0, 3, 2, 8, 18, 24, 34),
    ('tail_end', 0.0, 17.5, 15.0, 3, 2, 7, 18, 0, 0),
    ('tail_end', 1.8, 17.5, 14.6, 3, 1, 6, 14, 12, 16),
)


# ================================================================================================ geometry

def leg(name, x, z, hind, side):
    """A thick black leg: forearm or gaskin, a white plate with a gold frame on the knee or hock, a cannon in two tiers
    of white guards, a gold fetlock ring with a little white feathering, a big black hoof."""
    mirror = side == 'l'

    def tg(t):
        return (('h' if hind else 'f') + t) if side == 'r' else None

    def sh(t):
        return (('h' if hind else 'f') + t) if side == 'l' else None

    def cube(cx, y, cz, w, h, d, mat, t):
        return C(cx, y, cz, w, h, d, mat, tag=tg(t), share=sh(t), mirror=mirror)

    knee_y = 13.0
    shin = P(name.replace('_leg', '_shin'), (x, knee_y, z),
             cube(x - 2.0, knee_y, z - 2.0, 4, 8, 4, 'fur:coat', 's'),
             cube(x - 2.5, knee_y + 2.0, z - 2.7, 5, 4, 1, 'metal:greave', 'g1'),
             cube(x - 2.5, knee_y + 1.5, z - 2.5, 5, 1, 5, 'gold:ring', 'band'),
             cube(x - 2.5, 19.0, z - 2.5, 5, 1, 5, 'gold:ring', 'ring'),
             cube(x - 2.5, 20.0, z - 2.5, 5, 1, 5, 'hair:fetlock', 'fet'),
             cube(x - 2.5, 21.0, z - 3.0, 5, 3, 6, 'plain:hoof', 'hoof'))
    if hind:
        upper = (cube(x - 3.0, 0.0, z - 3.5, 6, 13, 7, 'fur:coat', 'u'),
                 cube(x - 2.5, 10.0, z + 3.2, 5, 3, 1, 'metal:plate', 'kp'))
        return P(name, (x, 3.0, z), *upper, shin)
    upper = (cube(x - 2.5, 4.0, z - 3.0, 5, 9, 6, 'fur:coat', 'u'),
             cube(x - 2.5, 10.5, z - 3.6, 5, 4, 1, 'metal:plate', 'kp'))
    return P(name, (x, 5.0, z), *upper, shin)


def taper(w, d, ln):
    """The blocks of a stepped blade (width, thickness, length), narrowing toward the tip."""
    if ln <= 4 or w <= 1:
        return [(w, d, ln)]
    l1 = max(2, int(round(ln * 0.5)))
    l2 = max(1, int(round(ln * 0.3)))
    l3 = ln - l1 - l2
    w2 = max(1, int(round(w * 0.65)))
    w3 = max(1, int(round(w * 0.35)))
    segs = [(w, d, l1), (w2, d, l2)]
    if l3 > 0:
        segs.append((w3, max(1, d - 1), l3))
    return segs


def blade(px, y, z, w, d, ln, hanging=None, mat='hair:mane'):
    """A shard of hair as stepped blocks: along +z from its root, or hanging along +y with its flat face toward x
    (hanging='x': its thickness along x, its width along z) or toward z (hanging='z')."""
    cubes = []
    off = 0
    for (sw, sd, sl) in taper(w, d, ln):
        if hanging == 'x':
            cubes.append(C(px - sd / 2.0, y + off, z - sw / 2.0, sd, sl, sw, mat))
        elif hanging == 'z':
            cubes.append(C(px - sw / 2.0, y + off, z - sd / 2.0, sw, sl, sd, mat))
        else:
            cubes.append(C(px - sw / 2.0, y - sd / 2.0, z + off, sw, sd, sl, mat))
        off += sl
    return cubes


def locks():
    """The hanging locks lock_1... (mirrored pairs)."""
    parts = []
    n = 0
    for (x, y, z, t, wd, ln, lean, out) in LOCKS:
        for side in (-1, 1):
            n += 1
            px = x * side
            parts.append(P('lock_%d' % n, (px, y, z), *blade(px, y, z, wd, t, ln, hanging='x'),
                           rot=(lean - NECK_TILT, 0, -out * side)))
    return parts


def aim(elevation, swing, face, tilt):
    """Euler angles (degrees, applied x, then y, then z as ermodel and the game do) that point a shard (its length
    along local +z, its flat top face along local -y) {elevation} degrees above the backward horizontal, {swing}
    degrees out toward +x, its flat face turned {face} degrees from up toward +x, on a part whose rest frame is
    turned {tilt} degrees about x."""
    e, s_, f = math.radians(elevation), math.radians(swing), math.radians(face)
    d = (math.sin(s_) * abs(math.cos(e)), -math.sin(e), math.cos(s_) * math.cos(e))
    up = (0.0, -1.0, 0.0) if abs(math.sin(e)) < 0.999 else (0.0, 0.0, 1.0)
    dot = sum(a * b for a, b in zip(up, d))
    n0 = [u - dot * v for u, v in zip(up, d)]
    ln = math.sqrt(sum(c * c for c in n0))
    n0 = [c / ln for c in n0]
    side = (d[1] * n0[2] - d[2] * n0[1], d[2] * n0[0] - d[0] * n0[2], d[0] * n0[1] - d[1] * n0[0])
    n = [a * math.cos(f) + b * math.sin(f) for a, b in zip(n0, side)]
    zc = d
    yc = [-c for c in n]
    xc = (yc[1] * zc[2] - yc[2] * zc[1], yc[2] * zc[0] - yc[0] * zc[2], yc[0] * zc[1] - yc[1] * zc[0])
    # into the part's frame: undo its tilt about x
    t = math.radians(-tilt)
    ct, st = math.cos(t), math.sin(t)

    def untilt(v):
        return (v[0], ct * v[1] - st * v[2], st * v[1] + ct * v[2])

    xc, yc, zc = untilt(xc), untilt(yc), untilt(zc)
    # R = [xc yc zc] (columns) = Rz(c) Ry(b) Rx(a)
    r20, r21, r22 = xc[2], yc[2], zc[2]
    r10, r00 = xc[1], xc[0]
    b = -math.asin(max(-1.0, min(1.0, r20)))
    a = math.atan2(r21, r22)
    c = math.atan2(r10, r00)
    return tuple(round(math.degrees(v), 2) for v in (a, b, c))


def shards(table, tilt, first):
    """The mane shards of one table, named mane_{first + 1}...; returns the parts and the last number."""
    parts = []
    n = first
    for (x, y, z, w, d, ln, elevation, swing, face) in table:
        for side in ((1,) if x == 0.0 else (-1, 1)):
            n += 1
            px = x * side
            parts.append(P('mane_%d' % n, (px, y, z), *blade(px, y, z, w, d, ln),
                           rot=aim(elevation, swing * side, face * side, tilt)))
    return parts, n


def shift(part, dx, dy, dz):
    """Moves a part, its cubes and all its children."""
    part.pivot = (part.pivot[0] + dx, part.pivot[1] + dy, part.pivot[2] + dz)
    for c in part.cubes:
        c.x, c.y, c.z = c.x + dx, c.y + dy, c.z + dz
    for ch in part.children:
        shift(ch, dx, dy, dz)


def tail():
    blades = {seg: [] for seg in TAIL_TILT}
    n = 0
    for (seg, x, y, z, w, t, ln, lean, out, turn) in TAIL:
        for side in ((1,) if x == 0.0 else (-1, 1)):
            n += 1
            px = x * side
            blades[seg].append(P('tail_blade_%d' % n, (px, y, z),
                                 *blade(px, y, z, w, t, ln, hanging='z', mat='hair:tail'),
                                 rot=(lean - TAIL_TILT[seg], turn * side, -out * side)))
    return P('tail', (0, -3, 12),
             C(-2.5, -4, 11.5, 5, 4, 4, 'hair:tail', tag='root'),
             P('tail2', (0, -1.5, 14),
               C(-2.5, -2.5, 12.5, 5, 9, 4, 'hair:tail'),
               P('tail3', (0, 6, 14),
                 C(-2.5, 5.5, 12.5, 5, 7, 3, 'hair:tail'),
                 P('tail4', (0, 12.5, 14.5),
                   C(-2, 12, 13, 4, 6, 3, 'hair:tail'),
                   P('tail_end', (0, 18, 14.5),
                     *blades['tail_end'], rot=(-6, 0, 0)),
                   *blades['tail4'], rot=(-4, 0, 0)),
                 *blades['tail3'], rot=(-6, 0, 0)),
               *blades['tail2'], rot=(12, 0, 0)),
             rot=(26, 0, 0))


def tail_count():
    return sum(1 if entry[1] == 0.0 else 2 for entry in TAIL)


def geometry():
    poll, n = shards(POLL, HEAD_TILT, 0)
    crest, n = shards(CREST, NECK_TILT, n)
    head = P('head', (0, -14, -13),
             C(-4.5, -22, -21, 9, 9, 9, 'fur:coat', tag='skull'),
             C(-3.5, -21, -30, 7, 7, 9, 'fur:muzzle', tag='face'),
             C(-3, -20, -32, 6, 6, 2, 'fur:muzzle', tag='nose'),
             C(-3.5, -14.5, -28, 7, 2, 7, 'fur:muzzle', tag='jaw'),
             # the plate down the face and the brow jewel
             C(-2, -21.6, -29, 4, 1, 8, 'metal:chanfron', tag='chanfron'),
             C(-2, -22.6, -23.5, 4, 1, 4, 'gold:setting', tag='brow_frame'),
             C(-1, -23.1, -22.5, 2, 1, 2, 'gem:gem', tag='brow_gem'),
             # the bridle: noseband, cheek band, rings at the bit and the cheek
             C(-4, -21.5, -27.5, 8, 8, 1, 'leather:bridle', tag='noseband'),
             C(-5, -22.5, -20.5, 10, 11, 1, 'leather:bridle', tag='cheekband'),
             C(-4.8, -15.5, -28, 1, 2, 2, 'gold:ring', tag='bit'),
             C(3.8, -15.5, -28, 1, 2, 2, 'gold:ring', share='bit', mirror=True),
             C(-5.3, -17, -21, 1, 2, 2, 'gold:ring', tag='rosette'),
             C(4.3, -17, -21, 1, 2, 2, 'gold:ring', share='rosette', mirror=True),
             # the roots of the mane between the ears and down the back of the poll
             C(-2, -23, -15.5, 4, 1, 4, 'hair:mane', tag='poll'),
             C(-2, -22.5, -12.5, 4, 8, 1, 'hair:mane', tag='poll_back'),
             # the ears stand up (the head looks 56 degrees down)
             P('ear_r', (-3, -22, -14), C(-4, -27, -14.5, 2, 5, 2, 'fur:coat', tag='ear'), rot=(-46, 0, -8)),
             P('ear_l', (3, -22, -14), C(2, -27, -14.5, 2, 5, 2, 'fur:coat', share='ear', mirror=True),
               rot=(-46, 0, 8)),
             # the reins hang in a loop from the bit rings back toward the chest
             P('reins', (0, -14.5, -27),
               C(-5.1, -14.5, -27.5, 1, 13, 1, 'leather:rein', tag='rein'),
               C(4.1, -14.5, -27.5, 1, 13, 1, 'leather:rein', share='rein', mirror=True),
               C(-5.1, -2.0, -27.5, 10, 1, 1, 'leather:rein', tag='rein_loop'),
               rot=(38, 0, 0)),
             *poll,
             rot=(HEAD_TILT - NECK_TILT, 0, 0))
    shift(head, *HEAD_SHIFT)
    neck = P('neck', (0, -2, -10),
             C(-4, -16, -15, 8, 17, 9, 'fur:coat', tag='neck'),
             C(-4.5, -4, -16, 9, 5, 10, 'metal:collar', tag='collar'),
             # the roots of the mane: a white ridge along the crest
             C(-2, -16.5, -8, 4, 16, 3, 'hair:mane', tag='crest'),
             head, *crest, *locks(), rot=(NECK_TILT, 0, 0))
    saddle = P('saddle', (0, -5, 0),
               C(-4.5, -6, -4, 9, 2, 9, 'leather:saddle'),
               C(-3, -8, -5, 6, 2, 2, 'leather:saddle_rim', tag='pommel'),
               C(-4, -9, 4, 8, 3, 2, 'leather:saddle_rim', tag='cantle'),
               C(-6.6, -5, -3.5, 1, 4, 8, 'leather:saddle', tag='skirt'),
               C(5.6, -5, -3.5, 1, 4, 8, 'leather:saddle', share='skirt', mirror=True))
    body = P('body', (0, 3, 0),
             C(-6.5, -4.5, -12, 13, 13, 24, 'fur:coat', tag='barrel'),
             C(-5.5, -3.5, -15, 11, 10, 3, 'fur:coat', tag='chest'),
             C(-5.5, -4.5, 9, 11, 11, 5, 'fur:coat', tag='rump'),
             # the chest plate with its jewel and the spiked shoulder plates
             C(-5.5, -4, -16.5, 11, 9, 1, 'metal:plate', tag='peytral'),
             C(-3.5, 5, -16.5, 7, 2, 1, 'metal:plate', tag='peytral_point'),
             C(-1.5, 7, -16.5, 3, 1, 1, 'metal:plate', tag='peytral_tip'),
             C(-2.5, -2, -17.2, 5, 7, 1, 'gold:setting', tag='chest_frame'),
             C(-1.5, -1, -17.7, 3, 5, 1, 'gem:gem', tag='chest_gem'),
             C(-6.5, 5, -16.5, 2, 2, 1, 'metal:plate', tag='spike'),
             C(4.5, 5, -16.5, 2, 2, 1, 'metal:plate', share='spike', mirror=True),
             P('pauldron_r', (-6.5, -4, -11),
               C(-7.8, -4.5, -14, 2, 6, 6, 'metal:plate', tag='pauldron'),
               C(-8.2, 1.5, -13.5, 2, 2, 2, 'metal:plate', tag='pspike1'),
               C(-8.2, 1.5, -10.5, 2, 2, 2, 'metal:plate', tag='pspike2'), rot=(0, 0, 10)),
             P('pauldron_l', (6.5, -4, -11),
               C(5.8, -4.5, -14, 2, 6, 6, 'metal:plate', share='pauldron', mirror=True),
               C(6.2, 1.5, -13.5, 2, 2, 2, 'metal:plate', share='pspike1', mirror=True),
               C(6.2, 1.5, -10.5, 2, 2, 2, 'metal:plate', share='pspike2', mirror=True), rot=(0, 0, -10)),
             # caparison panels, blanket, girth, saddlebags, the band over the rump
             C(-6.5, -4.5, -6, 13, 1, 13, 'cloth:blanket', tag='blanket'),
             C(-7, -3.5, -8, 1, 14, 7, 'cloth:caparison', tag='cap_front'),
             C(6, -3.5, -8, 1, 14, 7, 'cloth:caparison', share='cap_front', mirror=True),
             C(-7, -3, 2, 1, 12, 9, 'cloth:caparison', tag='cap_rear'),
             C(6, -3, 2, 1, 12, 9, 'cloth:caparison', share='cap_rear', mirror=True),
             C(-7.3, -4, -1, 1, 13, 2, 'leather:strap', tag='girth'),
             C(6.3, -4, -1, 1, 13, 2, 'leather:strap', share='girth', mirror=True),
             C(-6, 8, -1, 12, 1, 2, 'leather:strap', tag='girth_under'),
             C(-7.8, 2.5, -1.5, 1, 4, 3, 'gold:buckle', tag='buckle'),
             C(6.8, 2.5, -1.5, 1, 4, 3, 'gold:buckle', share='buckle', mirror=True),
             C(-8.5, -3.5, 5, 2, 6, 6, 'leather:bag', tag='bag'),
             C(6.5, -3.5, 5, 2, 6, 6, 'leather:bag', share='bag', mirror=True),
             C(-6.5, -5, 11, 13, 1, 2, 'metal:band', tag='band'),
             C(-6.8, -4.5, 11, 1, 7, 2, 'metal:band', tag='band_side'),
             C(5.8, -4.5, 11, 1, 7, 2, 'metal:band', share='band_side', mirror=True),
             saddle, neck, tail(),
             leg('front_right_leg', -3.5, -8, False, 'r'),
             leg('front_left_leg', 3.5, -8, False, 'l'),
             leg('hind_right_leg', -3.5, 8, True, 'r'),
             leg('hind_left_leg', 3.5, 8, True, 'l'))
    return [P('base', (0, 24, 0), body)]


# ================================================================================================ animations

def mane_count():
    return sum(1 if entry[0] == 0.0 else 2 for entry in POLL + CREST)


def lock_count():
    return 2 * len(LOCKS)


def animations(mane_n=None, lock_n=None, reins=True, extra=None, more=()):
    """The four gaits. Breeds (rig_horse_breeds.py) pass their own mane and lock counts, whether they wear reins,
    {@code extra} channels per animation name for their own parts and {@code more} animations of their own."""
    A = []
    n = mane_count() if mane_n is None else mane_n
    locks_n = lock_count() if lock_n is None else lock_n
    extra = extra or {}
    sway = {}
    for i in range(1, n + 1):
        a = 3 + (i % 3)
        sway['mane_%d' % i] = dict(rot=[(0, 0, 0, 0), (1.6, a if i % 2 else -a, 0, 0), (3.2, 0, 0, 0)])
    for i in range(1, locks_n + 1):
        sway['lock_%d' % i] = dict(rot=[(0, 0, 0, 0), (1.6, 4 if i % 4 < 2 else -3, 0, 0), (3.2, 0, 0, 0)])
    idle = dict(body=dict(scale=[(0, 1, 1, 1), (1.6, 1.015, 1.02, 1.0), (3.2, 1, 1, 1)]),
                neck=dict(rot=[(0, 0, 0, 0), (1.0, -3, 2, 0), (2.0, 1, -2, 0), (3.2, 0, 0, 0)]),
                head=dict(rot=[(0, 0, 0, 0), (0.8, 3, 4, 0), (1.8, -2, -3, 0), (3.2, 0, 0, 0)]),
                ear_r=dict(rot=[(0, 0, 0, 0), (1.1, 0, 0, 0), (1.2, -20, 0, -10), (1.4, 0, 0, 0), (3.2, 0, 0, 0)]),
                ear_l=dict(rot=[(0, 0, 0, 0), (2.2, 0, 0, 0), (2.3, -20, 0, 10), (2.5, 0, 0, 0), (3.2, 0, 0, 0)]),
                tail=dict(rot=[(0, 0, 6, 0), (1.6, 0, -6, 0), (3.2, 0, 6, 0)]),
                tail2=dict(rot=[(0, 0, 0, 3), (1.6, 0, 0, -3), (3.2, 0, 0, 3)]),
                tail3=dict(rot=[(0, 0, 0, -4), (1.6, 0, 0, 4), (3.2, 0, 0, -4)]),
                reins=dict(rot=[(0, 0, 0, 0), (1.6, 4, 0, 0), (3.2, 0, 0, 0)]),
                # a paw at the ground now and then, as in the picture
                front_right_leg=dict(rot=[(0, 0, 0, 0), (2.2, 0, 0, 0), (2.45, -38, 0, 0), (2.75, -38, 0, 0),
                                          (3.0, 0, 0, 0), (3.2, 0, 0, 0)]),
                front_right_shin=dict(rot=[(0, 0, 0, 0), (2.2, 0, 0, 0), (2.45, 62, 0, 0), (2.75, 62, 0, 0),
                                           (3.0, 0, 0, 0), (3.2, 0, 0, 0)]),
                **sway)
    if not reins:
        del idle['reins']
    idle.update(extra.get('idle', {}))
    A.append(anim('idle', 3.2, loop=True, **idle))
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
    for leg_name, off in (('hind_right', 0.0), ('front_right', 0.25), ('hind_left', 0.5), ('front_left', 0.75)):
        keys, shin = leg_cycle(off, 1.0, swing, 34 if leg_name.startswith('front') else 26)
        w[leg_name + '_leg'] = dict(rot=keys)
        w[leg_name + '_shin'] = dict(rot=shin)
    A.append(anim('walk', 1.0, loop=True,
                  body=dict(pos=[(0, 0, 0, 0), (0.25, 0, 0.35, 0), (0.5, 0, 0, 0), (0.75, 0, 0.35, 0), (1.0, 0, 0, 0)]),
                  neck=dict(rot=[(0, 3, 0, 0), (0.25, -2, 0, 0), (0.5, 3, 0, 0), (0.75, -2, 0, 0), (1.0, 3, 0, 0)]),
                  tail=dict(rot=[(0, 0, 8, 0), (0.5, 0, -8, 0), (1.0, 0, 8, 0)]),
                  **w, **extra.get('walk', {})))
    g = {}
    for leg_name, off, amp in (('front_right', 0.0, 46), ('front_left', 0.08, 46), ('hind_right', 0.45, 40),
                               ('hind_left', 0.53, 40)):
        keys, shin = [], []
        for i in range(9):
            t = 0.5 * i / 8.0
            ph = 2 * math.pi * (i / 8.0 + off)
            keys.append((t, amp * math.sin(ph), 0, 0))
            shin.append((t, (50 if leg_name.startswith('front') else 30) * max(0.0, -math.cos(ph)), 0, 0))
        g[leg_name + '_leg'] = dict(rot=keys)
        g[leg_name + '_shin'] = dict(rot=shin)
    stream = dict(('mane_%d' % i, dict(rot=[(0, -18, 0, 0), (0.25, -26, 0, 0), (0.5, -18, 0, 0)]))
                  for i in range(1, n + 1))
    # the hanging locks fly back
    stream.update(('lock_%d' % i, dict(rot=[(0, 34, 0, 0), (0.25, 46, 0, 0), (0.5, 34, 0, 0)]))
                  for i in range(1, locks_n + 1))
    gallop = dict(body=dict(rot=[(0, -6, 0, 0), (0.25, 5, 0, 0), (0.5, -6, 0, 0)],
                            pos=[(0, 0, 0.9, 0), (0.125, 0, 0.2, 0), (0.25, 0, 0, 0), (0.375, 0, 0.6, 0),
                                 (0.5, 0, 0.9, 0)]),
                  neck=dict(rot=[(0, 14, 0, 0), (0.25, 2, 0, 0), (0.5, 14, 0, 0)]),
                  head=dict(rot=[(0, -10, 0, 0), (0.25, -2, 0, 0), (0.5, -10, 0, 0)]),
                  reins=dict(rot=[(0, 20, 0, 0), (0.25, 28, 0, 0), (0.5, 20, 0, 0)]),
                  tail=dict(rot=[(0, 24, 0, 0), (0.25, 32, 0, 0), (0.5, 24, 0, 0)]),
                  tail2=dict(rot=[(0, -30, 0, 0), (0.25, -38, 0, 0), (0.5, -30, 0, 0)]),
                  tail3=dict(rot=[(0, -18, 0, 0), (0.25, -10, 0, 0), (0.5, -18, 0, 0)]),
                  **stream, **g)
    if not reins:
        del gallop['reins']
    gallop.update(extra.get('gallop', {}))
    A.append(anim('gallop', 0.5, loop=True, **gallop))
    A.append(anim('jump', 0.6,
                  body=dict(rot=[(0, 0, 0, 0), (0.15, -14, 0, 0), (0.6, -8, 0, 0)]),
                  neck=dict(rot=[(0, 0, 0, 0), (0.15, 10, 0, 0), (0.6, 8, 0, 0)]),
                  front_right_leg=dict(rot=[(0, 0, 0, 0), (0.15, -50, 0, 0), (0.6, -40, 0, 0)]),
                  front_left_leg=dict(rot=[(0, 0, 0, 0), (0.15, -46, 0, 0), (0.6, -38, 0, 0)]),
                  front_right_shin=dict(rot=[(0, 0, 0, 0), (0.15, 80, 0, 0), (0.6, 70, 0, 0)]),
                  front_left_shin=dict(rot=[(0, 0, 0, 0), (0.15, 76, 0, 0), (0.6, 66, 0, 0)]),
                  hind_right_leg=dict(rot=[(0, 0, 0, 0), (0.15, 40, 0, 0), (0.6, 30, 0, 0)]),
                  hind_left_leg=dict(rot=[(0, 0, 0, 0), (0.15, 36, 0, 0), (0.6, 28, 0, 0)]),
                  tail=dict(rot=[(0, 0, 0, 0), (0.15, 45, 0, 0), (0.6, 40, 0, 0)]), **extra.get('jump', {})))
    A.extend(more)
    return A


# ================================================================================================ paint

def plate_rim(fc, width=1, color=GOLD, edge=GOLD_DARK):
    """A white plate's gold rim with a darker inner line and rivets in the corners."""
    w, h = fc.fw, fc.fh
    base.border(fc, color, width, 'tblr')
    if w > 2 * width + 2 and h > 2 * width + 2:
        for x in range(width, w - width):
            fc.fpx(x, width, edge)
        for (x, y) in ((width + 1, width + 1), (w - width - 2, width + 1), (width + 1, h - width - 2),
                       (w - width - 2, h - width - 2)):
            fc.fpx(x, y, GOLD_LIGHT)


def fleur(fc, cx, cy, size, color, edge):
    """Our fleur cross: a cross whose arms end in three petals (fine pixels)."""
    pts = set()
    for d in range(-size, size + 1):
        pts.add((cx, cy + d))
    for d in range(-size + 2, size - 1):
        pts.add((cx + d, cy - 1))
    for ax, ay in ((0, -size), (0, size), (-(size - 2), -1), (size - 2, -1)):
        for dx, dy in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            pts.add((cx + ax + dx, cy + ay + dy))
    for (x, y) in pts:
        fc.fpx(x, y, color)
    for (x, y) in sorted(pts):
        if (x + 1, y + 1) not in pts:
            fc.fpx(x + 1, y + 1, edge)


def shard(fc, color, light, dark):
    """A mane or tail shard: long facets in bands, a lit edge, a darker root, a stepped point at the tip
    (fine y 0 is the tip on the top and bottom faces)."""
    w, h = fc.fw, fc.fh
    for y in range(h):
        t = y / max(1, h - 1)
        for x in range(w):
            c = mix(color, dark, 0.35 * t)
            if (x // 2) % 2:
                c = shade(c, 0.92)
            if x == 0:
                c = mix(c, light, 0.6)
            if (x * 5 + y * 3) % 13 == 0:
                c = mix(c, light, 0.4)
            fc.fpx(x, y, c)
    for k in range(min(h, max(1, w // 2))):
        cut = w // 2 - k
        for x in range(w):
            if x < cut or x >= w - cut:
                fc.fclear(x, k)


BLADE_SHADE = (0.9, 1.0, 1.08)   # root, middle and tip blocks of a blade


def paint(p):
    k = p.k
    extra = p.variant.extra
    hair = rgb(extra['hair'])
    hair_light = mix(hair, (255, 255, 255), 0.4)
    hair_dark = shade(hair, 0.7)
    if extra.get('pattern'):
        base.coat_pattern(p, extra['pattern'], extra['pattern_color'])
    # ---------------------------------------------------------------- the mane and the tail shards
    blades = (['mane_%d' % i for i in range(1, mane_count() + 1)] + ['lock_%d' % i for i in range(1, lock_count() + 1)]
              + ['tail_blade_%d' % i for i in range(1, tail_count() + 1)])
    for part in blades:
        for i, cube in enumerate(p.rig.parts[part].cubes):
            f = BLADE_SHADE[min(i, len(BLADE_SHADE) - 1)]
            for name in ('top', 'bottom', 'right', 'left', 'front', 'back'):
                fc = p.face(part, name, index=i)
                for y in range(fc.fh):
                    for x in range(fc.fw):
                        fc.fpx(x, y, shade(fc.fget(x, y), f))
    if extra.get('glow_hair'):
        base.glow_hair(p, extra['hair'])
    # ---------------------------------------------------------------- the caparison: gold edges, fleur, jagged hem
    for tag in ('cap_front', 'cap_rear'):
        for name in ('right',):
            fc = p.face('body', name, tag=tag)
            w, h = fc.fw, fc.fh
            base.border(fc, GOLD, 2, 'lr')
            base.border(fc, GOLD, 1, 't')
            for x in range(w):
                fc.fpx(x, h - 3 * k - 1, GOLD)
                fc.fpx(x, h - 3 * k - 2, GOLD_DARK)
            base.pointed_bottom(fc, 3 * k, step=4 * k)
            fleur(fc, w // 2, h // 2 - k, 5, (240, 240, 246), (150, 150, 170))
        for name in ('front', 'back'):
            base.pointed_bottom(p.face('body', name, tag=tag), 3 * k, step=2)
    for name in ('top',):
        fc = p.face('body', name, tag='blanket')
        base.border(fc, GOLD, 1, 'tblr')
    # ---------------------------------------------------------------- plates: gold rims, the jewels
    for tag in ('peytral', 'peytral_point', 'peytral_tip', 'spike'):
        for name in ('front', 'right', 'left', 'top'):
            plate_rim(p.face('body', name, tag=tag))
    for part, tag in (('pauldron_r', 'pauldron'), ('pauldron_r', 'pspike1'), ('pauldron_r', 'pspike2')):
        for name in ('right', 'front', 'back', 'top', 'bottom', 'left'):
            plate_rim(p.face(part, name, tag=tag))
    for name in ('front',):
        base.diamond(p.face('body', name, tag='chest_gem'), p.face('body', name, tag='chest_gem', glow=True))
        diamond_frame(p.face('body', name, tag='chest_frame'))
    for name in ('top', 'front', 'right', 'left'):
        base.gem_face(p.face('head', name, tag='brow_gem'), p.face('head', name, tag='brow_gem', glow=True))
        fc = p.face('head', name, tag='brow_frame')
        base.border(fc, GOLD_DARK, 1, 'tblr')
    for name in ('top', 'front', 'right', 'left'):
        plate_rim(p.face('head', name, tag='chanfron'))
    for name in ('front', 'right', 'left', 'back', 'top'):
        plate_rim(p.face('neck', name, tag='collar'))
    for tag in ('band', 'band_side'):
        for name in ('front', 'right', 'left', 'back', 'top'):
            fc = p.face('body', name, tag=tag)
            for x in range(1, fc.fw - 1, 4):
                fc.fpx(x, fc.fh // 2, GOLD)
    # ---------------------------------------------------------------- leather: the bridle, girth, bags, saddle
    for tag in ('noseband', 'cheekband'):
        for name in ('front', 'back', 'right', 'left', 'top', 'bottom'):
            fc = p.face('head', name, tag=tag)
            for x in range(1, fc.fw - 1, 4):
                fc.fpx(x, fc.fh // 2, GOLD)
    for tag in ('girth', 'girth_under'):
        for name in ('right', 'left', 'front', 'back', 'bottom'):
            fc = p.face('body', name, tag=tag)
            for y in range(1, fc.fh - 1, 4):
                fc.fpx(fc.fw // 2, y, GOLD)
    for name in ('right', 'front', 'back', 'left'):
        fc = p.face('body', name, tag='buckle')
        base.border(fc, GOLD_DARK, 1, 'tblr')
        for y in range(1, fc.fh - 1):
            fc.fpx(fc.fw // 2, y, shade(GOLD, 0.6))
    for name in ('right', 'front', 'back', 'left', 'top'):
        fc = p.face('body', name, tag='bag')
        for x in range(fc.fw):
            fc.fpx(x, 2 * k, shade(rgb('#5a3418'), 0.8))
        for y in range(fc.fh):
            fc.fpx(fc.fw // 2, y, rgb('#4a2a12'))
        fc.fpx(fc.fw // 2, 3 * k, GOLD)
        fc.fpx(fc.fw // 2 + 1, 3 * k, GOLD)
        base.border(fc, GOLD_DARK, 1, 't')
    for name in ('right', 'left', 'front', 'back'):
        base.border(p.face('saddle', name, index=0), GOLD, 1, 't')
    for tag in ('pommel', 'cantle', 'skirt'):
        for name in ('right', 'left', 'front', 'back', 'top'):
            base.border(p.face('saddle', name, tag=tag), GOLD, 1, 'tblr')
    # ---------------------------------------------------------------- the head: glowing eyes, nostrils, mouth
    for name in ('right', 'left'):
        skull = p.face('head', name, tag='skull')
        gs = p.face('head', name, tag='skull', glow=True)
        # a big glowing blue eye just behind the cheek band (fine x 0 of the right face is the back of the skull)
        ex = skull.fw - 8 if name == 'right' else 4
        glowing_eye(skull, gs, ex, 4)
    # the ears: gold rims down the front edges, a dark inside
    for name in ('front',):
        fc = p.face('ear_r', name, tag='ear')
        for y in range(fc.fh):
            for x in range(1, fc.fw - 1):
                fc.fpx(x, y, (24, 22, 30))
            fc.fpx(0, y, GOLD)
            fc.fpx(fc.fw - 1, y, GOLD_DARK)
        for x in range(fc.fw):
            fc.fpx(x, fc.fh - 1, GOLD)
    for name in ('right', 'left'):
        fc = p.face('ear_r', name, tag='ear')
        for y in range(fc.fh):
            fc.fpx(0 if name == 'left' else fc.fw - 1, y, GOLD)
        face = p.face('head', name, tag='face')
        face.fpx(2 if name == 'right' else face.fw - 3, face.fh - 4, (12, 12, 16))
    front = p.face('head', 'front', tag='face')
    for x in (2, front.fw - 3):
        front.fpx(x, front.fh - 4, (10, 10, 14))
        front.fpx(x, front.fh - 5, (10, 10, 14))
    # ---------------------------------------------------------------- the legs: rims on the plates
    for pre in ('f', 'h'):
        leg_name = 'front_right_leg' if pre == 'f' else 'hind_right_leg'
        shin_name = leg_name.replace('_leg', '_shin')
        for name in ('front', 'right', 'left', 'top'):
            plate_rim(p.face(leg_name, name, tag=pre + 'kp'))
        for name in ('front', 'right', 'left', 'top'):
            fc = p.face(shin_name, name, tag=pre + 'g1')
            plate_rim(fc)
            if name == 'front':
                for y in range(2, fc.fh - 2):
                    fc.fpx(fc.fw // 2, y, GOLD_LIGHT)
            base.pointed_bottom(fc, 2, step=fc.fw)


def glowing_eye(fc, glow_fc, x, y):
    """A big glowing eye (4 by 3 fine pixels): a bright blue iris with a white glint, a deep blue lower lid."""
    for dy in range(3):
        for dx in range(4):
            c = EYE_GLOW if dy < 2 else shade(EYE_GLOW, 0.6)
            fc.fpx(x + dx, y + dy, c)
            glow_fc.fpx(x + dx, y + dy, c)
    for (dx, dy) in ((0, 0), (1, 0)):
        fc.fpx(x + dx, y + dy, (235, 250, 255))
        glow_fc.fpx(x + dx, y + dy, (235, 250, 255))
    for dx in range(-1, 5):
        fc.fpx(x + dx, y - 1, (10, 10, 14))


def shard_down(fc, color, light, dark):
    """A hanging tail shard seen from the side (fine y grows downward to the tip): light top, stepped tip below."""
    w, h = fc.fw, fc.fh
    for y in range(h):
        t = y / max(1, h - 1)
        for x in range(w):
            c = mix(dark, color, 0.5 + 0.5 * t)
            if (x // 2) % 2:
                c = shade(c, 0.93)
            if x == 0:
                c = mix(c, light, 0.5)
            fc.fpx(x, y, c)
    for k in range(min(h, max(1, w // 2))):
        cut = w // 2 - k
        for x in range(w):
            if x < cut or x >= w - cut:
                fc.fclear(x, h - 1 - k)


def diamond_frame(fc):
    """A gold diamond setting: the corners cut away, a darker inner ring."""
    w, h = fc.fw, fc.fh
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for y in range(h):
        for x in range(w):
            d = abs(x - cx) / max(0.5, cx + 0.5) + abs(y - cy) / max(0.5, cy + 0.5)
            if d > 1.02:
                fc.fclear(x, y)
            elif d > 0.75:
                fc.fpx(x, y, GOLD_LIGHT if y < cy else GOLD)
            else:
                fc.fpx(x, y, GOLD_DARK)


# ================================================================================================ variants

def variant(cid, spec):
    coat, muzzle = spec['coat'], spec['muzzle']
    pal = {
        'coat': {'base': coat[0], 'style': 'fur', 'dark': coat[1], 'light': coat[2]},
        'muzzle': {'base': muzzle[0], 'style': 'fur', 'dark': muzzle[1], 'light': muzzle[2]},
        'mane': {'base': spec['mane'], 'style': 'hair'},
        'tail': {'base': spec['tail'], 'style': 'hair'},
        'fetlock': {'base': spec['fetlock'], 'style': 'hair'},
        'hoof': '#17161c',
        'caparison': {'base': spec['caparison'], 'style': 'cloth'},
        'blanket': {'base': spec['blanket'], 'style': 'cloth'},
        'saddle': {'base': '#6e3e1e', 'style': 'leather'},
        'saddle_rim': {'base': '#55301a', 'style': 'leather'},
        'bag': {'base': '#6a3c1e', 'style': 'leather'},
        'bridle': {'base': '#5a321a', 'style': 'leather'},
        'rein': {'base': '#5a321a', 'style': 'leather'},
        'strap': {'base': '#5e341a', 'style': 'leather'},
        'plate': {'base': '#e8e6ee', 'style': 'metal', 'rivets': False},
        'chanfron': {'base': '#e8e6ee', 'style': 'metal', 'rivets': False},
        'collar': {'base': '#e2e0ea', 'style': 'metal', 'rivets': False},
        'greave': {'base': '#e4e2ec', 'style': 'metal', 'rivets': False},
        'band': {'base': '#dedce6', 'style': 'metal', 'rivets': False},
        'setting': {'base': '#eab84a', 'style': 'gold'},
        'ring': {'base': '#eab84a', 'style': 'gold'},
        'buckle': {'base': '#eab84a', 'style': 'gold'},
        'gem': {'base': '#4a9cff', 'style': 'gem'},
    }
    return Variant(cid, palette=pal, paint=paint, glow=True,
                   extra=dict(scale=1.0, hitbox=(1.3, 1.75), seat='saddle', pattern=spec.get('pattern'),
                              pattern_color=spec.get('pattern_color'), glow_hair=spec.get('glow_hair', False),
                              hair=spec['mane']))


def rig():
    return Rig('mount_horse', 'HorseMount', geometry(), animations(),
               [variant(cid, spec) for cid, spec in base.HORSE_COATS], HORSE_SLOTS, texel=2)
