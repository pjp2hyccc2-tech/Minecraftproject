"""Hunter (player) rig for the Epic-Fight style combat animations.

Vanilla 64x64 skin UV layout on a 17-bone rig: root > hips > (legs with knees, chest > head, arms with elbows).
Exports assets/huntcraft/hcmodels/hunter.json (wide arms) and hunter_slim.json (3px arms) plus the combat action
timeline assets/huntcraft/hcmodels/hunter_actions.json (hit frames, cancel windows, root motion - the single
source of truth for the server side action system, see gear/weapon/action/ActionDefs.java).

Usage (from anywhere):
    python3 tools/models/hunter.py                   # export json
    python3 tools/models/hunter.py --preview [anim ...] [--weapon gs] [--views 3]
                                                     # + contact sheets in tools/preview/hunter_<anim>.png
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from hcmodel import Model  # noqa: E402
from hcmodel.model import rot_x, rot_y, rot_z, translate, scale_m, part_matrix  # noqa: E402
from hcmodel import rig as R  # noqa: E402

ASSETS = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'huntcraft')
PREVIEW_DIR = os.path.join(os.path.dirname(__file__), '..', 'preview')

# ------------------------------------------------------------------------------------------------ rig
BONES = ['root', 'hips', 'leg_r', 'leg_r_lower', 'leg_l', 'leg_l_lower', 'chest', 'head',
         'arm_r', 'arm_r_lower', 'hand_r', 'arm_l', 'arm_l_lower', 'hand_l']
MIRROR_PAIRS = [('leg_l', 'leg_r'), ('leg_l_lower', 'leg_r_lower'), ('arm_l', 'arm_r'), ('arm_l_lower', 'arm_r_lower')]
L_UPPER_ARM, L_LOWER_ARM = 4.0, 6.0      # shoulder pivot -> elbow pivot, elbow pivot -> hand point
L_UPPER_LEG, L_LOWER_LEG = 6.0, 6.0


def build(slim=False):
    name = 'hunter_slim' if slim else 'hunter'
    m = Model(name, tex_w=64, tex_h=64)
    m.config.update({'head_bones': [], 'walk_speed': 1.0, 'run_speed': 1.0, 'walk_scale': 1.0})
    root = m.bone('root', (0, 24, 0))
    hips = root.bone('hips', (0, -12, 0))
    hips.cube((-4, -5, -2), (8, 5, 4), 'skin', uv=(16, 23))
    hips.cube((-4, -5, -2), (8, 5, 4), 'skin', uv=(16, 39), inflate=0.25)
    # legs
    for side, x, uv, uvo in (('r', -1.9, (0, 16), (0, 32)), ('l', 1.9, (16, 48), (0, 48))):
        leg = hips.bone('leg_' + side, (x, 0, 0))
        leg.cube((-2, 0, -2), (4, 6, 4), 'skin', uv=uv)
        leg.cube((-2, 0, -2), (4, 6, 4), 'skin', uv=uvo, inflate=0.25)
        low = leg.bone('leg_%s_lower' % side, (0, 6, 0))
        low.cube((-2, 0, -2), (4, 6, 4), 'skin', uv=(uv[0], uv[1] + 6))
        low.cube((-2, 0, -2), (4, 6, 4), 'skin', uv=(uvo[0], uvo[1] + 6), inflate=0.25)
    # chest + head
    chest = hips.bone('chest', (0, -5, 0))
    chest.cube((-4, -7, -2), (8, 7, 4), 'skin', uv=(16, 16))
    chest.cube((-4, -7, -2), (8, 7, 4), 'skin', uv=(16, 32), inflate=0.25)
    head = chest.bone('head', (0, -7, 0))
    head.cube((-4, -8, -4), (8, 8, 8), 'skin', uv=(0, 0))
    head.cube((-4, -8, -4), (8, 8, 8), 'skin', uv=(32, 0), inflate=0.5)
    # arms
    w = 3 if slim else 4
    py = -4.5 if slim else -5
    for side, x, uv, uvo in (('r', -5, (40, 16), (40, 32)), ('l', 5, (32, 48), (48, 48))):
        ox = (-2 if slim else -3) if side == 'r' else -1
        arm = chest.bone('arm_' + side, (x, py, 0))
        arm.cube((ox, -2, -2), (w, 6, 4), 'skin', uv=uv)
        arm.cube((ox, -2, -2), (w, 6, 4), 'skin', uv=uvo, inflate=0.25)
        low = arm.bone('arm_%s_lower' % side, (0, 4, 0))
        low.cube((ox, 0, -2), (w, 6, 4), 'skin', uv=(uv[0], uv[1] + 6))
        low.cube((ox, 0, -2), (w, 6, 4), 'skin', uv=(uvo[0], uvo[1] + 6), inflate=0.25)
        low.bone('hand_' + side, (0, 6, 0))      # wrist joint: items are rendered from here
    return m


# ------------------------------------------------------------------------------------------------ weapons
# Sprite pixels (32px icons) of the grip and the tip / head, the third person display transform of the item
# model (tools/weapons/gen_art.py) and how far along the weapon the second hand holds (sprite units).
# Keys = WeaponClass ids used by the mod.
WEAPONS = {
    'great_sword': dict(grip=(6.0, 26.0), tip=(28.0, 3.0), scale=1.9, hand2=0.085, width=5, thick=1),
    'long_sword': dict(grip=(6.0, 26.0), tip=(28.0, 4.0), scale=1.7, hand2=0.075, width=3, thick=1),
    'sword_and_shield': dict(grip=(5.0, 24.5), tip=(22.0, 7.0), scale=1.15, hand2=0.0, width=3, thick=1),
    'dual_blades': dict(grip=(5.5, 25.5), tip=(27.0, 4.0), scale=1.1, hand2=0.0, width=3, thick=1),
    'hammer': dict(grip=(7.0, 25.0), tip=(24.0, 9.0), scale=1.7, hand2=0.17, width=4, thick=4),
    'lance': dict(grip=(5.5, 26.5), tip=(29.0, 3.0), scale=2.1, hand2=0.0, width=3, thick=3),
    'hunting_horn': dict(grip=(7.0, 25.0), tip=(25.0, 8.0), scale=1.6, hand2=0.14, width=5, thick=5),
    'bow': dict(grip=(16.0, 16.0), tip=(26.0, 6.0), scale=1.035, hand2=0.0, width=2, thick=2, bow=True),
}
SHORT = {'gs': 'great_sword', 'ls': 'long_sword', 'sns': 'sword_and_shield', 'db': 'dual_blades', 'hm': 'hammer',
         'ln': 'lance', 'hh': 'hunting_horn', 'bow': 'bow'}
TP_ROT, TP_T, TP_S = (0, -90, 55), (0, 4.0, 0.5), 0.85
VANILLA_GRIP = (3.5 / 16 - 0.5, 0.5 - 12.5 / 16, 0.0)


def _joml_xyz(rx, ry, rz):
    a, b, g = (math.radians(x) for x in (rx, ry, rz))
    return rot_x(a) @ rot_y(b) @ rot_z(g)


def _keep_grip(rot, t0, s0, g0, s1, g1):
    Rm = _joml_xyz(*rot)[:3, :3]
    t = np.array(t0) / 16.0 + Rm @ (s0 * np.array(g0) - s1 * np.array(g1))
    return np.clip(t * 16.0, -80, 80)


def item_matrix(cls, left=False):
    """4x4 (blocks) from the HAND bone frame to item sprite unit space ((px/32-0.5, 0.5-py/32, 0)).
    Same chain as HeldItems.java: vanilla ItemInHandLayer offsets, then the item model display transform."""
    w = WEAPONS[cls]
    sg = -1.0 if left else 1.0
    if w.get('bow'):
        rot = (-80, 260, -40)
        tr = np.array([-1, -2, 2.5]) / 16.0
        s = 0.9 * 1.15
    else:
        gx, gy = w['grip']
        g = (gx / 32 - 0.5, 0.5 - gy / 32, 0.0)
        rot = TP_ROT
        tr = _keep_grip(TP_ROT, TP_T, TP_S, VANILLA_GRIP, w['scale'], g) / 16.0
        s = w['scale']
    rx, ry, rz = rot
    if left:
        ry, rz = -ry, -rz
    m = translate(-sg / 16.0, 0.0, -0.125) @ rot_x(math.radians(-90)) @ rot_y(math.radians(180))
    m = m @ translate(sg * tr[0], tr[1], tr[2]) @ _joml_xyz(rx, ry, rz) @ scale_m(s, s, s) @ translate(-0.5, -0.5, -0.5)
    return m


def sprite_point(px, py):
    return np.array([px / 32.0, 1.0 - py / 32.0, 0.5, 1.0])


def weapon_points_px(cls, left=False):
    """(grip, tip) in the hand bone frame, model pixels."""
    m = item_matrix(cls, left)
    w = WEAPONS[cls]
    g = (m @ sprite_point(*w['grip']))[:3] * 16.0
    t = (m @ sprite_point(*w['tip']))[:3] * 16.0
    return g, t


def hand2_point_px(cls):
    """Where the second hand holds the weapon (hand frame of the main hand, px)."""
    g, t = weapon_points_px(cls)
    w = WEAPONS[cls]
    d = t - g
    return g + d / max(1e-6, np.linalg.norm(d)) * (w['hand2'] * w['scale'] * 16.0)


# ------------------------------------------------------------------------------------------------ preview
def preview_skin():
    """Procedural 64x64 skin used only for contact sheets (players use their own skins in game)."""
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    skin, skin_d = (214, 170, 132, 255), (190, 146, 110, 255)
    hair = (62, 40, 28, 255)
    coat, coat_d = (84, 58, 40, 255), (64, 44, 30, 255)
    pants, pants_d = (58, 66, 80, 255), (44, 50, 62, 255)
    boot = (40, 30, 24, 255)
    belt = (150, 120, 50, 255)

    def box(u, v, dx, dy, dz, top, bottom, side, side2=None):
        side2 = side2 or side
        d.rectangle([u + dz, v, u + dz + dx - 1, v + dz - 1], fill=top)
        d.rectangle([u + dz + dx, v, u + 2 * dz + dx - 1, v + dz - 1], fill=bottom)
        d.rectangle([u, v + dz, u + 2 * dz + 2 * dx - 1, v + dz + dy - 1], fill=side)
        d.rectangle([u + dz, v + dz, u + dz + dx - 1, v + dz + dy - 1], fill=side2)

    box(0, 0, 8, 8, 8, hair, skin, skin_d, skin)
    d.rectangle([8, 8, 15, 10], fill=hair)            # fringe
    d.rectangle([9, 12, 10, 13], fill=(40, 60, 120, 255))   # eyes
    d.rectangle([13, 12, 14, 13], fill=(40, 60, 120, 255))
    d.rectangle([11, 15, 12, 15], fill=(160, 90, 80, 255))  # mouth
    box(32, 0, 8, 8, 8, hair, (0, 0, 0, 0), (0, 0, 0, 0))   # hat layer: hair top only
    box(16, 16, 8, 12, 4, coat, pants_d, coat, coat_d)     # body
    d.rectangle([20, 26, 27, 27], fill=belt)
    box(40, 16, 4, 12, 4, coat, skin, coat_d, coat)        # right arm
    d.rectangle([40, 26, 55, 31], fill=skin_d)             # hands
    box(32, 48, 4, 12, 4, coat, skin, coat_d, coat)        # left arm
    d.rectangle([32, 58, 47, 63], fill=skin_d)
    box(0, 16, 4, 12, 4, pants, boot, pants, pants_d)      # right leg
    d.rectangle([0, 26, 15, 31], fill=boot)
    box(16, 48, 4, 12, 4, pants, boot, pants, pants_d)     # left leg
    d.rectangle([16, 58, 31, 63], fill=boot)
    return img


WEAPON_COLORS = {'great_sword': (200, 205, 215), 'long_sword': (210, 215, 225), 'sword_and_shield': (200, 200, 210),
                 'dual_blades': (205, 205, 215), 'hammer': (120, 110, 100), 'lance': (190, 195, 205),
                 'hunting_horn': (170, 130, 80), 'bow': (140, 100, 60)}


def add_weapon_proxy(model, cls, left=False):
    """Preview only: a plank/rod along the weapon axis attached to the lower arm (UV in the extra texture area)."""
    w = WEAPONS[cls]
    g, t = weapon_points_px(cls, left)
    d = t - g
    length = float(np.linalg.norm(d))
    dn = d / max(1e-6, length)
    y_axis = -dn                      # local -Y points to the tip
    helper = np.array([1.0, 0, 0]) if abs(y_axis[0]) < 0.9 else np.array([0, 0, 1.0])
    z_axis = R.normalize(np.cross(helper, y_axis))
    x_axis = np.cross(y_axis, z_axis)
    Rm = np.stack([x_axis, y_axis, z_axis], axis=1)
    rot = R.euler_zyx(Rm)
    parent = model.bones['hand_%s' % ('l' if left else 'r')]
    b = parent.bone('weapon_%s' % ('l' if left else 'r'), tuple(float(v) for v in g), rot)
    wd, th = w['width'], w['thick']
    if cls == 'hammer' or cls == 'hunting_horn':
        b.cube((-1, -length, -1), (2, length - 2, 2), 'wood', uv=(64, 0))
        b.cube((-wd / 2, -length - 3, -th / 2), (wd, 6, th), 'metal', uv=(80, 0))
    elif cls == 'bow':
        b.cube((-1, -length, -1), (2, 2 * length, 2), 'wood', uv=(64, 0))
    else:
        b.cube((-wd / 2, -length, -th / 2), (wd, length - 2, th), 'metal', uv=(80, 0))
        b.cube((-1, -3, -1), (2, 5, 2), 'wood', uv=(64, 0))
    return b


def add_shield_proxy(model):
    arm = model.bones['arm_l_lower']
    b = arm.bone('shield_l', (0, 3, 0), (0, 0, 0))
    b.cube((-6, -4, -3.5), (1, 8, 8), 'metal', uv=(80, 0))
    return b


def preview_texture(model, skin):
    tex = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    tex.paste(skin, (0, 0))
    d = ImageDraw.Draw(tex)
    d.rectangle([64, 0, 79, 63], fill=(110, 80, 50, 255))
    d.rectangle([80, 0, 127, 63], fill=(205, 210, 220, 255))
    d.rectangle([96, 8, 127, 40], fill=(170, 175, 190, 255))
    model.tex_w, model.tex_h = 128, 64
    return tex


def contact_sheet(model, tex, anim, out_path, frames=8, views=((35, 18), (-60, 10), (0, 8)), size=220, stance=None, span=3.4):
    from hcmodel.render import render, label
    rows = []
    fit = (np.array([0.0, 0.85]), span)
    for yaw, pitch in views:
        row = []
        for k in range(frames):
            t = anim.length * k / (frames if anim.loop else max(1, frames - 1))
            layers = [(anim, t, 1.0)]
            if stance is not None:
                layers.insert(0, (stance, t, 1.0))
            im, _ = render(model, tex, layers, size, yaw, pitch, fit)
            row.append(label(im, '%s %.2fs' % (anim.name, t)))
        rows.append(row)
    sheet = Image.new('RGB', (frames * size, len(rows) * size), (20, 22, 26))
    for ri, r in enumerate(rows):
        for ci, im in enumerate(r):
            sheet.paste(im, (ci * size, ri * size))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    sheet.save(out_path)
    return sheet


# ------------------------------------------------------------------------------------------------ main
def export_all():
    import hunter_anims
    out = {}
    for slim in (False, True):
        m = build(slim)
        hunter_anims.build_animations(m)
        m.export(ASSETS, paint=False)
        out[m.name] = m
    actions = hunter_anims.export_actions()
    with open(os.path.join(ASSETS, 'hcmodels', 'hunter_actions.json'), 'w') as f:
        json.dump(actions, f, separators=(',', ':'))
    m = out['hunter']
    print('hunter: bones', len(m.bones), 'cubes', sum(1 for _ in m.all_cubes()), 'anims', len(m.animations),
          'actions', len(actions['actions']))
    return out


if __name__ == '__main__':
    models = export_all()
    if '--preview' in sys.argv:
        import hunter_anims
        wsel = None
        views = 3
        size, frames = 220, 8
        skip = set()
        for i, a in enumerate(sys.argv):
            if a == '--weapon':
                wsel = SHORT.get(sys.argv[i + 1], sys.argv[i + 1])
                skip.add(i + 1)
            if a == '--views':
                views = int(sys.argv[i + 1])
                skip.add(i + 1)
            if a == '--size':
                size = int(sys.argv[i + 1])
                skip.add(i + 1)
            if a == '--frames':
                frames = int(sys.argv[i + 1])
                skip.add(i + 1)
        args = [a for i, a in enumerate(sys.argv) if i > 0 and not a.startswith('--') and i not in skip]
        m = models['hunter']
        names = args or list(m.animations.keys())
        skin = preview_skin()
        for n in names:
            a = m.animations[n]
            cls = wsel or hunter_anims.weapon_for(n)
            pm = build(False)
            hunter_anims.build_animations(pm)
            if cls:
                add_weapon_proxy(pm, cls)
                if cls in ('sword_and_shield', 'lance'):
                    add_shield_proxy(pm)
                if cls == 'dual_blades':
                    add_weapon_proxy(pm, cls, left=True)
            tex = preview_texture(pm, skin)
            vs = ((35, 18), (-60, 10), (0, 8))[:views]
            contact_sheet(pm, tex, pm.animations[n], os.path.join(PREVIEW_DIR, 'hunter_%s.png' % n), frames=frames, views=vs, size=size)
            print('preview', n, cls)
