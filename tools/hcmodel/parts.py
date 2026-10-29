"""Reusable body-part builders (symmetric limbs, tails, spike rows, wings, eyes...).

All functions take a parent Bone and return the created bones so animations can address them.
Left side = +X (entity's left), right side = -X.
"""


def mirror_bone(parent, src, name, pivot, rot):
    """Creates a bone mirrored across X from `src` (pivot x negated, Y/Z rotations negated) and copies
    its cubes as mirrored cubes (sharing UVs). Recurses into children with suffix replacement."""
    px, py, pz = pivot
    b = parent.bone(name, (px, py, pz), rot)
    for c in src.cubes:
        ox, oy, oz = c.origin
        dx, dy, dz = c.size
        b.cube((-(ox + dx), oy, oz), c.size, c.mat, inflate=c.inflate, mirror_of=c, faces=c.faces, decals=c.decals)
    return b


def mirror_tree(parent, src, suffix_from='_l', suffix_to='_r'):
    """Mirrors bone `src` (and its children) onto `parent`. Names: '_l' -> '_r'."""
    px, py, pz = src.pivot
    rx, ry, rz = src.rot
    name = src.name.replace(suffix_from, suffix_to) if suffix_from in src.name else src.name + '_m'
    b = mirror_bone(parent, src, name, (-px, py, pz), (rx, -ry, -rz))
    for ch in src.children:
        mirror_tree(b, ch, suffix_from, suffix_to)
    return b


def spike_plane(bone, x, y, z, height, length, mat='ign_spike', tip='horn_ivory', tip_frac=0.35, glow_tip=None):
    """Vertical crossed spike (plane in the Y-Z plane) whose base sits at (x, y, z) pointing up (-Y)."""
    decals = [{'type': 'cutout', 'shape': 'spike'}]
    if tip:
        decals.append({'type': 'tip', 'axis': 'up', 'frac': tip_frac, 'mat': tip})
    return bone.cube((x, y - height, z - length / 2), (0, height, length), mat, decals=decals)


def spike_row(bone, z_from, z_to, count, y, height, length, mat='ign_spike', tip='horn_ivory', shrink=0.0, x=0):
    cubes = []
    for i in range(count):
        t = i / max(1, count - 1)
        z = z_from + (z_to - z_from) * t
        h = max(2, round(height * (1 - shrink * t)))
        cubes.append(spike_plane(bone, x, y, z, h, max(3, round(length * (1 - shrink * t * 0.5))), mat, tip))
    return cubes


def tail_chain(parent, name, pivot, rot, segments, mat, spikes=None, end_mat=None):
    """segments: list of (width, height, length, rot_x) from base to tip. Returns list of bones.
    Each segment's cube is centred on X, top at -height/2, extends +Z (backward)."""
    bones = []
    p = parent
    piv, r = pivot, rot
    for i, (w, h, ln, rx) in enumerate(segments):
        b = p.bone('%s%d' % (name, i + 1), piv, (rx, 0, 0) if i else r)
        m = end_mat if (end_mat and i == len(segments) - 1) else mat
        b.cube((-w / 2, -h / 2, 0), (w, h, ln + 2), m)
        if spikes:
            sm, smh, smt = spikes
            spike_plane(b, 0, -h / 2 + 1, ln * 0.5, max(2, round(smh * (1 - i / (len(segments) + 1)))), max(4, round(ln * 0.5)), sm, smt)
        bones.append(b)
        p = b
        piv = (0, 0, ln)
    return bones


def leg(parent, side, name, hip, thigh, shin, foot, mat, claw_mat='claw', thigh_rot=(-20, 0, 0), shin_rot=(50, 0, 0), foot_rot=(-30, 0, 0), toes=3):
    """Digitigrade hind leg. thigh/shin/foot = (w, h, d) sizes. Returns (thigh, shin, foot) bones.
    side: +1 = left (+X), -1 = right."""
    tw, th, td = thigh
    sw, sh, sd = shin
    fw, fh, fd = foot
    t = parent.bone('%s_thigh' % name, hip, thigh_rot)
    t.cube((-tw / 2, -tw * 0.35, -td / 2), (tw, th, td), mat)
    s = t.bone('%s_shin' % name, (0, th - tw * 0.55, td * 0.1), shin_rot)
    s.cube((-sw / 2, -1, -sd / 2), (sw, sh, sd), mat)
    f = s.bone('%s_foot' % name, (0, sh - 2, 0), foot_rot)
    f.cube((-fw / 2, -1, -fd + sd / 2), (fw, fh, fd), mat)
    tw2 = max(2, round(fw / toes))
    for k in range(toes):
        x = -fw / 2 + k * (fw / toes) + (fw / toes - tw2) / 2
        f.cube((x, 0, -fd + sd / 2 - 5), (tw2, max(2, fh - 1), 6), claw_mat, decals=[{'type': 'tip', 'axis': 'front', 'frac': 0.5, 'mat': 'horn_ivory'}])
    f.cube((-1.5, 0, sd / 2 - 1), (3, 2, 4), claw_mat)
    return t, s, f


def head_eyes(cube, color='#ffd23f', glow='#ffb020', at=(0.78, 0.38), size=3):
    cube.decals.append({'type': 'eye', 'face': 'side', 'at': at, 'size': size, 'color': color, 'glow': glow})
    return cube
